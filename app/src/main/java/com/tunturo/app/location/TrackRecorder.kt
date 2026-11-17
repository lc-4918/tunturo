package com.tunturo.app.location

import android.os.SystemClock
import com.tunturo.app.domain.geo.RecordedPoint
import com.tunturo.app.domain.geo.Recording
import com.tunturo.app.domain.geo.RecordingPhase
import com.tunturo.app.domain.geo.ResumeMode
import com.tunturo.app.domain.geo.TrackRecording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/**
 * L'enregistrement de la trace, tenu par le service de localisation - ecran eteint compris - et lu par la
 * carte, qui le dessine, et par le bouton qui le commande.
 *
 * Hors de la composition pour la meme raison que les compteurs (cf. [TripWatch]) : une sortie se passe
 * telephone en poche.
 *
 * Chaque changement s'ecrit sur le disque au fil de l'eau (cf. [RecordingStore]) : le parcours survit a la
 * mort du processus, et l'on n'y perd au pire que la derniere position.
 */
object TrackRecorder {

    private val _recording = MutableStateFlow(Recording())
    val recording: StateFlow<Recording> = _recording.asStateFlow()

    /** Le dossier ou l'enregistrement s'ecrit, null tant que l'application ne l'a pas donne. */
    @Volatile private var directory: File? = null

    /**
     * Les ecritures, une a la fois et hors du fil principal : les positions arrivent sur celui-ci, et une
     * ecriture par position n'a rien a y faire. Remplacable par les tests, qui ecrivent sur place.
     */
    internal var writer: (Runnable) -> Unit = Executors.newSingleThreadExecutor()::execute

    private val lock = Any()

    /** L'horloge monotone du telephone. Remplacable par les tests, qui tournent sans Android. */
    internal var elapsedClock: () -> Long = { SystemClock.elapsedRealtime() }

    /**
     * L'instant monotone du dernier demarrage ou de la derniere reprise : une position mesuree avant n'est
     * pas du parcours (cf. [TrackRecording.accept]). De la duree du processus : apres sa mort, la reprise
     * du disque le repose.
     */
    @Volatile private var notBeforeMs = 0L

    /**
     * Le dossier de l'application, et la reprise de ce qui s'y trouve. Appele au demarrage de l'application,
     * avant que quiconque lise l'enregistrement.
     */
    fun attach(dir: File) = synchronized(lock) {
        directory = dir
        val restored = RecordingStore.load(dir) ?: return@synchronized
        notBeforeMs = elapsedClock()
        val continued = TrackRecording.restored(restored)
        _recording.value = continued
        // Le segment ouvert par la reprise s'ecrit lui aussi : sans cela, une seconde mort le perdrait.
        if (continued.segments.size != restored.segments.size) write { RecordingStore.appendSegmentBreak(it) }
    }

    /** Demarre un enregistrement neuf. */
    fun start(nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        val before = _recording.value
        val after = TrackRecording.start(before, nowMs)
        if (after === before) return@synchronized
        notBeforeMs = elapsedClock()
        _recording.value = after
        write { RecordingStore.rewrite(it, after) }
    }

    /** Met en pause. Vrai si l'enregistrement etait en cours : il y a alors un parcours a ranger. */
    fun pause(): Boolean = synchronized(lock) {
        val before = _recording.value
        val after = TrackRecording.pause(before)
        if (after === before) return@synchronized false
        _recording.value = after
        write { RecordingStore.writeMeta(it, after) }
        true
    }

    /** Reprend selon [mode]. */
    fun resume(mode: ResumeMode, nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        val before = _recording.value
        val after = TrackRecording.resume(before, mode, nowMs)
        if (after === before) return@synchronized
        notBeforeMs = elapsedClock()
        _recording.value = after
        when (mode) {
            ResumeMode.NEW_TRACK -> write { RecordingStore.rewrite(it, after) }
            ResumeMode.NEW_SEGMENT -> write {
                RecordingStore.writeMeta(it, after)
                if (after.segments.size != before.segments.size) RecordingStore.appendSegmentBreak(it)
            }
            ResumeMode.SAME_SEGMENT -> write { RecordingStore.writeMeta(it, after) }
        }
    }

    /** Le parcours vient d'etre range dans la bibliotheque, dans la couche [layerId], sous [autoName]. */
    fun savedAs(layerId: Long, autoName: String) = synchronized(lock) {
        val after = _recording.value.copy(layerId = layerId, autoName = autoName)
        _recording.value = after
        write { RecordingStore.writeMeta(it, after) }
    }

    /** Une position de plus. Vrai quand elle a ete gardee. */
    fun add(fix: LocationHub.Fix): Boolean = synchronized(lock) {
        val current = _recording.value
        if (current.phase != RecordingPhase.RECORDING) return@synchronized false
        val point = TrackRecording.accept(
            current, fix.lat, fix.lon, fix.accuracyM, fix.altitudeM, fix.timeMs, fix.elapsedAtMs, notBeforeMs,
        ) ?: return@synchronized false
        _recording.value = TrackRecording.append(current, point)
        write { RecordingStore.appendPoint(it, point) }
        true
    }

    /** Pour les tests : un enregistrement vierge, sans dossier. */
    internal fun resetForTest() = synchronized(lock) {
        directory = null
        notBeforeMs = 0L
        _recording.value = Recording()
    }

    private fun write(action: (File) -> Unit) {
        val dir = directory ?: return
        writer(Runnable { runCatching { action(dir) } })
    }
}

/**
 * L'enregistrement sur le disque, en deux fichiers.
 *
 * - [META_FILE] : la phase, la couche ou le parcours est range, son debut et son nom - quelques octets,
 *   reecrits a chaque changement.
 * - [POINTS_FILE] : un point par ligne, "lat;lon;ele;temps", et une ligne [SEGMENT_BREAK] a chaque nouveau
 *   segment. On n'y fait qu'AJOUTER : une sortie de dix heures compte des milliers de points, et les
 *   reecrire tous a chaque position serait refaire sans cesse le meme travail.
 */
object RecordingStore {
    const val META_FILE = "recording.json"
    const val POINTS_FILE = "recording-points.txt"
    const val SEGMENT_BREAK = "#seg"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Meta(
        val phase: RecordingPhase = RecordingPhase.IDLE,
        val layerId: Long? = null,
        val startedAtMs: Long? = null,
        val autoName: String? = null,
    )

    fun writeMeta(dir: File, recording: Recording) {
        val meta = Meta(recording.phase, recording.layerId, recording.startedAtMs, recording.autoName)
        File(dir, META_FILE).writeText(json.encodeToString(Meta.serializer(), meta))
    }

    /** Tout reecrire : un parcours neuf commence. */
    fun rewrite(dir: File, recording: Recording) {
        writeMeta(dir, recording)
        File(dir, POINTS_FILE).writeText(encodeSegments(recording.segments))
    }

    fun appendPoint(dir: File, point: RecordedPoint) {
        File(dir, POINTS_FILE).appendText(encodePoint(point) + "\n")
    }

    fun appendSegmentBreak(dir: File) {
        File(dir, POINTS_FILE).appendText(SEGMENT_BREAK + "\n")
    }

    /** L'enregistrement du disque, ou null s'il n'y en a pas, ou plus rien a reprendre. */
    fun load(dir: File): Recording? {
        val metaFile = File(dir, META_FILE)
        if (!metaFile.exists()) return null
        val meta = runCatching { json.decodeFromString(Meta.serializer(), metaFile.readText()) }.getOrNull() ?: return null
        if (meta.phase == RecordingPhase.IDLE) return null
        val pointsFile = File(dir, POINTS_FILE)
        val segments = if (pointsFile.exists()) decodeSegments(pointsFile.readText()) else listOf(emptyList())
        return Recording(meta.phase, segments, meta.layerId, meta.startedAtMs, meta.autoName)
    }

    internal fun encodePoint(point: RecordedPoint): String =
        "${point.lat};${point.lon};${point.ele ?: ""};${point.timeMs}"

    internal fun encodeSegments(segments: List<List<RecordedPoint>>): String = buildString {
        segments.forEachIndexed { index, segment ->
            if (index > 0) append(SEGMENT_BREAK).append('\n')
            segment.forEach { append(encodePoint(it)).append('\n') }
        }
    }

    /**
     * Les segments du fichier. Une ligne illisible - la derniere, coupee par la mort du processus en
     * pleine ecriture - est ignoree, et non le fichier entier.
     */
    internal fun decodeSegments(text: String): List<List<RecordedPoint>> {
        val segments = mutableListOf(mutableListOf<RecordedPoint>())
        text.lineSequence().forEach { line ->
            if (line == SEGMENT_BREAK) segments.add(mutableListOf())
            else decodePoint(line)?.let { segments.last().add(it) }
        }
        return segments
    }

    private fun decodePoint(line: String): RecordedPoint? {
        val parts = line.split(';')
        if (parts.size != 4) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        val timeMs = parts[3].toLongOrNull() ?: return null
        return RecordedPoint(lat, lon, parts[2].toDoubleOrNull(), timeMs)
    }
}
