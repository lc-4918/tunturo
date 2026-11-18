package com.tunturo.app.domain.geo

import com.tunturo.app.domain.model.TrackPoint
import kotlinx.serialization.Serializable

/** Ou en est l'enregistrement de la trace. */
enum class RecordingPhase {
    /** Rien en cours, et rien a reprendre. */
    IDLE,

    /** Les positions s'ajoutent au parcours. */
    RECORDING,

    /**
     * En pause : plus rien ne s'ajoute, le parcours attend sur le disque. L'appui suivant reprend (selon
     * le reglage, cf. [ResumeMode]) ; "Terminer" le range dans la bibliotheque.
     */
    PAUSED,
}

/**
 * Comment reprendre un enregistrement mis en pause : un reglage (Systeme > Enregistrement), et non plus
 * une question posee a chaque reprise. Un nouveau parcours ne se choisit plus ici : on termine celui-ci.
 */
enum class ResumeMode(val key: String) {
    /** Le meme segment continue : un trait droit relie le point de la pause a celui de la reprise. */
    SAME_SEGMENT("same_segment"),

    /** Le meme parcours, dans un nouveau segment : rien ne relie la pause a la reprise. */
    NEW_SEGMENT("new_segment");

    companion object {
        /** Le mode d'une cle de reglage ; le meme segment pour une cle inconnue, comme par defaut. */
        fun of(key: String?): ResumeMode = entries.firstOrNull { it.key == key } ?: SAME_SEGMENT
    }
}

/** Un point enregistre. [timeMs] est l'heure murale de la mesure : c'est elle que le parcours garde. */
@Serializable
data class RecordedPoint(val lat: Double, val lon: Double, val ele: Double? = null, val timeMs: Long) {
    fun toTrackPoint() = TrackPoint(lon = lon, lat = lat, ele = ele, timeMs = timeMs)
}

/**
 * L'enregistrement en cours ou en pause.
 *
 * [segments] n'est jamais vide hors de [RecordingPhase.IDLE] : le dernier segment est celui qui recoit les
 * points. [layerId] est la couche de la bibliotheque ou le parcours a deja ete range, null d'ordinaire :
 * jusqu'a la v0.36, chaque pause le rangeait, et un parcours alors en pause se termine dans sa couche.
 */
data class Recording(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val segments: List<List<RecordedPoint>> = emptyList(),
    val layerId: Long? = null,
    val startedAtMs: Long? = null,
)

/**
 * Les regles de l'enregistrement, sans Android : ce qu'on garde d'une position, et ce que deviennent les
 * segments a la pause et a la reprise.
 */
object TrackRecording {

    /** Une position moins precise ne s'enregistre pas : la meme borne que les compteurs (cf. [TripStats]). */
    const val MAX_ACCURACY_M = TripStats.MAX_ACCURACY_M

    /** Pas minimal entre deux points gardes, ou la precision si elle est moins bonne (cf. [TripStats]). */
    const val MIN_STEP_M = TripStats.MIN_STEP_M

    /** Demarre un enregistrement neuf. Sans effet sur un enregistrement deja en cours ou en pause. */
    fun start(recording: Recording, nowMs: Long): Recording =
        if (recording.phase != RecordingPhase.IDLE) recording
        else Recording(phase = RecordingPhase.RECORDING, segments = listOf(emptyList()), startedAtMs = nowMs)

    /** Met en pause un enregistrement en cours. */
    fun pause(recording: Recording): Recording =
        if (recording.phase != RecordingPhase.RECORDING) recording
        else recording.copy(phase = RecordingPhase.PAUSED)

    /** Reprend un enregistrement en pause, selon [mode]. */
    fun resume(recording: Recording, mode: ResumeMode): Recording {
        if (recording.phase != RecordingPhase.PAUSED) return recording
        return when (mode) {
            ResumeMode.SAME_SEGMENT -> recording.copy(phase = RecordingPhase.RECORDING)
            ResumeMode.NEW_SEGMENT -> recording.copy(phase = RecordingPhase.RECORDING, segments = withNewSegment(recording.segments))
        }
    }

    /**
     * Un enregistrement repris apres la mort du processus. En cours, il continue dans un nouveau segment :
     * entre la mort et la reprise, on a pu faire des kilometres, et un trait droit les remplacerait par
     * leur corde.
     */
    fun restored(recording: Recording): Recording =
        if (recording.phase == RecordingPhase.RECORDING) recording.copy(segments = withNewSegment(recording.segments))
        else recording

    /** Ouvre un segment vide, sauf si le dernier l'est deja. */
    private fun withNewSegment(segments: List<List<RecordedPoint>>): List<List<RecordedPoint>> =
        if (segments.lastOrNull()?.isEmpty() == true) segments else segments + listOf(emptyList())

    /**
     * Le point a garder d'une position, ou null s'il ne s'enregistre pas.
     *
     * Ne s'enregistrent pas : hors enregistrement ; une position trop floue ; une position MESUREE avant
     * le demarrage ou la reprise ([measuredAtMs] avant [notBeforeMs], deux instants monotones) - c'est la
     * derniere position connue que le systeme rend a l'abonnement, peut-etre celle de la veille, et elle
     * ouvrirait le parcours par un trait depuis un autre endroit ; une position qui n'est pas posterieure
     * au dernier point ; une position a moins de [MIN_STEP_M] (ou de sa precision) du dernier point du
     * segment - le tremblement du GPS a l'arret dessinerait sinon une pelote autour de soi.
     *
     * **L'instant de la mesure, et non son age a la reception** : l'economie d'energie de certains
     * telephones coupe le GPS ecran eteint, et le systeme livre au reveil, d'un coup, des positions
     * mesurees plusieurs minutes plus tot. Elles sont du parcours, et doivent s'y ajouter.
     */
    fun accept(
        recording: Recording,
        lat: Double, lon: Double, accuracyM: Float, altitudeM: Double?, timeMs: Long,
        measuredAtMs: Long, notBeforeMs: Long,
    ): RecordedPoint? {
        if (recording.phase != RecordingPhase.RECORDING) return null
        if (accuracyM > MAX_ACCURACY_M) return null
        if (measuredAtMs < notBeforeMs) return null
        val last = recording.segments.lastOrNull { it.isNotEmpty() }?.last()
        if (last != null && timeMs <= last.timeMs) return null
        val lastInSegment = recording.segments.lastOrNull()?.lastOrNull()
        if (lastInSegment != null) {
            val stepM = TrackMath.haversine(lastInSegment.lon, lastInSegment.lat, lon, lat)
            if (stepM < maxOf(MIN_STEP_M, accuracyM.toDouble())) return null
        }
        return RecordedPoint(lat, lon, altitudeM, timeMs)
    }

    /** Ajoute [point] au dernier segment. */
    fun append(recording: Recording, point: RecordedPoint): Recording {
        val segments = recording.segments.ifEmpty { listOf(emptyList()) }
        return recording.copy(segments = segments.dropLast(1) + listOf(segments.last() + point))
    }

    /**
     * Ce que la carte dessine en direct : les segments, en enregistrement comme en pause - la pause ne
     * range plus rien dans la bibliotheque, le trait en direct est donc le seul a montrer le parcours.
     * Une fois termine, c'est la couche rangee qui prend le relais.
     */
    fun drawn(recording: Recording): List<List<RecordedPoint>> =
        if (recording.phase == RecordingPhase.IDLE) emptyList() else recording.segments

    /** Les lignes a ranger dans la bibliotheque : un segment d'un seul point n'en est pas une. */
    fun lines(recording: Recording): List<List<TrackPoint>> =
        recording.segments.filter { it.size >= 2 }.map { segment -> segment.map { it.toTrackPoint() } }

    /** Le premier et le dernier point du parcours, ou null s'il n'y a rien a ranger. */
    fun ends(recording: Recording): Pair<RecordedPoint, RecordedPoint>? {
        val kept = recording.segments.filter { it.size >= 2 }
        if (kept.isEmpty()) return null
        return kept.first().first() to kept.last().last()
    }

    /**
     * Les altitudes du modele de terrain [smoothed] posees sur les lignes du GPS [gps], point par point.
     * La ou le modele n'a rien rendu - le reseau perdu en cours de route -, l'altitude du GPS reste.
     */
    fun mergeElevation(gps: List<List<TrackPoint>>, smoothed: List<List<TrackPoint>>): List<List<TrackPoint>> =
        gps.mapIndexed { lineIndex, line ->
            line.mapIndexed { pointIndex, point ->
                val modelEle = smoothed.getOrNull(lineIndex)?.getOrNull(pointIndex)?.ele
                if (modelEle != null) point.copy(ele = modelEle) else point
            }
        }
}
