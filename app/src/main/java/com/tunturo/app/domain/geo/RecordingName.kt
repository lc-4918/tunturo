package com.tunturo.app.domain.geo

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Le nom d'un parcours enregistre : la commune de depart, celle d'arrivee et le jour du depart, par
 * exemple "Paris-Brest_20261117".
 */
object RecordingName {

    private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * Le nom du parcours parti de [startTown] et arrive a [endTown], le jour de [startedAtMs].
     *
     * Une boucle - meme commune au depart et a l'arrivee - ne la nomme qu'une fois. Sans aucune commune
     * (ni reseau ni index hors ligne), [fallback] en tient lieu : "Parcours_20261117".
     */
    fun of(
        startTown: String?, endTown: String?, startedAtMs: Long, fallback: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val day = DAY_FORMAT.format(Instant.ofEpochMilli(startedAtMs).atZone(zone))
        val start = startTown?.trim()?.takeIf { it.isNotEmpty() }
        val end = endTown?.trim()?.takeIf { it.isNotEmpty() }
        val places = when {
            start != null && end != null && !start.equals(end, ignoreCase = true) -> "$start-$end"
            start != null -> start
            end != null -> end
            else -> fallback
        }
        return "${places}_$day"
    }

    /** [base], ou [base] suivi de "_2", "_3"... s'il est deja pris dans [taken]. */
    fun unique(base: String, taken: Collection<String>): String {
        if (base !in taken) return base
        var index = 2
        while ("${base}_$index" in taken) index++
        return "${base}_$index"
    }
}
