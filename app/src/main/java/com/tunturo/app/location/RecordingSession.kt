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
 * Les gestes de l'enregistrement - demarrer, mettre en pause, reprendre - et le rangement du parcours
 * dans la bibliotheque a chaque pause.
 *
 * Dans l'application et non dans un ecran : la pause peut venir de la notification, l'application en
 * arriere-plan, et le parcours doit se ranger quand meme. Le capteur, lui, reste l'affaire de l'ecran
 * (autorisations) et du service (cf. [LocationService]).
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
        /** Range dans "Mes parcours" sous [name]. */
        data class Saved(val name: String) : SaveOutcome

        /** Rien a ranger : moins de deux points enregistres. */
        data object Empty : SaveOutcome

        /** Le rangement a echoue (disque plein, base indisponible) : le parcours reste sur le disque. */
        data object Failed : SaveOutcome
    }

    private val _saving = MutableStateFlow(false)

    /** Un rangement est en cours. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _outcomes = MutableSharedFlow<SaveOutcome>(extraBufferCapacity = 4)

    /** Le resultat de chaque rangement, pour le bandeau de la carte. */
    val outcomes: SharedFlow<SaveOutcome> = _outcomes.asSharedFlow()

    /** Deux pauses rapprochees ne rangent pas le meme parcours en deux couches. */
    private val saveLock = Mutex()

    fun start() = TrackRecorder.start()

    fun resume(mode: ResumeMode) = TrackRecorder.resume(mode)

    /** Met en pause et range le parcours. Sans effet hors enregistrement. */
    fun pause(): Job? {
        if (!TrackRecorder.pause()) return null
        return scope.launch { save() }
    }

    /** Range dans la bibliotheque ce qui a ete enregistre jusqu'ici. */
    suspend fun save() = saveLock.withLock {
        _saving.value = true
        try {
            val outcome = runCatching { saveNow() }.getOrElse { SaveOutcome.Failed }
            _outcomes.tryEmit(outcome)
            outcome
        } finally {
            _saving.value = false
        }
    }

    private suspend fun saveNow(): SaveOutcome {
        val recording = TrackRecorder.recording.value
        val lines = TrackRecording.lines(recording)
        val (first, last) = TrackRecording.ends(recording) ?: return SaveOutcome.Empty
        val startTown = runCatching { townAt(first.lon, first.lat) }.getOrNull()
        val endTown = runCatching { townAt(last.lon, last.lat) }.getOrNull()
        val baseName = RecordingName.of(
            startTown, endTown, recording.startedAtMs ?: first.timeMs,
            fallback = ctx.getString(R.string.recording_default_name),
        )
        val saved = repository.saveRecording(lines, recording.layerId, recording.autoName, baseName)
            ?: return SaveOutcome.Empty
        TrackRecorder.savedAs(saved.layerId, saved.autoName)
        val layer = repository.layers.byId(saved.layerId)
        return SaveOutcome.Saved(layer?.name ?: saved.autoName)
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
