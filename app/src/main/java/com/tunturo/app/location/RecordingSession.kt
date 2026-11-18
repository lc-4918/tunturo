package com.tunturo.app.location

import android.content.Context
import com.tunturo.app.R
import com.tunturo.app.data.repo.TunturoRepository
import com.tunturo.app.domain.geo.RecordingName
import com.tunturo.app.domain.geo.ResumeMode
import com.tunturo.app.domain.geo.TrackRecording
import com.tunturo.app.geocode.PlaceSearch
import com.tunturo.app.geocode.Photon
import com.tunturo.app.geocode.offline.OfflinePlaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Les gestes de l'enregistrement - demarrer, mettre en pause, reprendre, terminer - et le rangement du
 * parcours dans la bibliotheque a sa fin.
 *
 * **La pause ne range plus rien** : le parcours attend sur le disque (cf. [RecordingStore]), qui survit a
 * la mort du processus. C'est "Terminer" qui le range, apres une boite ou l'on choisit son nom, sa
 * discipline, ses altitudes et son dossier.
 *
 * Dans l'application et non dans un ecran : la pause peut venir de la notification, l'application en
 * arriere-plan. Le capteur, lui, reste l'affaire de l'ecran (autorisations) et du service (cf.
 * [LocationService]).
 *
 * [townAt] dit la commune d'un point (lon, lat), ou null : remplacable par les tests.
 */
class RecordingSession(
    private val ctx: Context,
    private val repository: TunturoRepository,
    private val scope: CoroutineScope,
    private val townAt: suspend (Double, Double) -> String? = { lon, lat -> defaultTownAt(ctx, repository, lon, lat) },
) {

    /** Ce qu'a donne le rangement d'un parcours. */
    sealed interface SaveOutcome {
        /** Range dans la bibliotheque sous [name]. */
        data class Saved(val name: String) : SaveOutcome

        /** Rien a ranger : moins de deux points enregistres. L'enregistrement est termine quand meme. */
        data object Empty : SaveOutcome

        /** Le rangement a echoue (disque plein, base indisponible) : le parcours reste en pause, sur le disque. */
        data object Failed : SaveOutcome
    }

    /** Ce que la boite de fin a choisi. */
    data class FinishChoice(
        val name: String,
        val folder: TunturoRepository.RecordingFolder,
        val routingProfile: String?,
        val smoothElevation: Boolean,
    )

    private val _saving = MutableStateFlow(false)

    /** Un rangement est en cours. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _outcomes = MutableSharedFlow<SaveOutcome>(extraBufferCapacity = 4)

    /** Le resultat de chaque rangement, pour le bandeau de la carte. */
    val outcomes: SharedFlow<SaveOutcome> = _outcomes.asSharedFlow()

    /** Deux "Enregistrer" rapproches ne rangent pas le meme parcours en deux couches. */
    private val saveLock = Mutex()

    fun start() = TrackRecorder.start()

    fun resume(mode: ResumeMode) = TrackRecorder.resume(mode)

    /** Met en pause. Vrai si l'enregistrement etait en cours. */
    fun pause(): Boolean = TrackRecorder.pause()

    /**
     * Le nom propose dans la boite de fin : "Depart-Arrivee_AAAAMMJJ" (cf. [RecordingName]), ou le nom par
     * defaut et la date quand aucune commune ne se trouve.
     */
    suspend fun suggestedName(): String {
        val recording = TrackRecorder.recording.value
        val fallback = ctx.getString(R.string.recording_default_name)
        val (first, last) = TrackRecording.ends(recording)
            ?: return RecordingName.of(null, null, recording.startedAtMs ?: System.currentTimeMillis(), fallback)
        val startTown = runCatching { townAt(first.lon, first.lat) }.getOrNull()
        val endTown = runCatching { townAt(last.lon, last.lat) }.getOrNull()
        return RecordingName.of(startTown, endTown, recording.startedAtMs ?: first.timeMs, fallback)
    }

    /** Termine l'enregistrement : le range selon [choice], en arriere-plan. */
    fun finish(choice: FinishChoice): Job = scope.launch { save(choice) }

    /**
     * Range le parcours selon [choice], et termine l'enregistrement s'il n'y avait rien a ranger ou que
     * c'est fait. Un echec le laisse en pause : rien n'est perdu, "Terminer" se refait.
     */
    suspend fun save(choice: FinishChoice): SaveOutcome = saveLock.withLock {
        _saving.value = true
        try {
            TrackRecorder.pause()
            val outcome = runCatching { saveNow(choice) }.getOrElse { SaveOutcome.Failed }
            if (outcome != SaveOutcome.Failed) TrackRecorder.finished()
            _outcomes.tryEmit(outcome)
            outcome
        } finally {
            _saving.value = false
        }
    }

    private suspend fun saveNow(choice: FinishChoice): SaveOutcome {
        val recording = TrackRecorder.recording.value
        val lines = TrackRecording.lines(recording)
        if (lines.isEmpty()) return SaveOutcome.Empty
        // Le choix des altitudes devient celui de la prochaine fois : c'est le reglage, pose a la fin.
        repository.settings.get()?.let { s ->
            if (s.recordingSmoothElevation != choice.smoothElevation) {
                repository.settings.upsert(s.copy(recordingSmoothElevation = choice.smoothElevation))
            }
        }
        val saved = repository.saveRecording(
            lines, recording.layerId, choice.name, choice.folder, choice.routingProfile, choice.smoothElevation,
        ) ?: return SaveOutcome.Empty
        return SaveOutcome.Saved(saved.name)
    }

    companion object {
        /**
         * La commune d'un point : par le geocodeur s'il est permis (il passe de lui-meme a l'index hors
         * ligne sans reseau), sinon par le seul index hors ligne - eteindre le geocodage, c'est refuser
         * que la position parte vers un tiers.
         */
        private suspend fun defaultTownAt(ctx: Context, repository: TunturoRepository, lon: Double, lat: Double): String? {
            val settings = repository.settings.get()
            val places = if (settings?.geocodingEnabled == true) {
                val base = settings.geocodingUrl.takeIf { it.isNotBlank() } ?: Photon.DEFAULT_URL
                val lang = ctx.resources.configuration.locales[0].language
                PlaceSearch.reverse(ctx, base, lon, lat, lang)
            } else {
                val offline = OfflinePlaces.of(ctx)
                if (offline.available()) offline.reverse(lon, lat) else null
            }
            return places?.firstOrNull()?.town
        }
    }
}
