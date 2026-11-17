package com.tunturo.app.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Le nom d'un parcours enregistre : "Depart-Arrivee_AAAAMMJJ". */
class RecordingNameTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val startedAtMs = ZonedDateTime.of(2026, 11, 17, 9, 30, 0, 0, paris).toInstant().toEpochMilli()

    private fun name(start: String?, end: String?) =
        RecordingName.of(start, end, startedAtMs, fallback = "Parcours", zone = paris)

    @Test fun `le nom dit la commune de depart, celle d'arrivee et le jour`() {
        assertEquals("Paris-Brest_20261117", name("Paris", "Brest"))
    }

    @Test fun `une boucle ne nomme sa commune qu'une fois`() {
        assertEquals("Grenoble_20261117", name("Grenoble", "Grenoble"))
        assertEquals("Grenoble_20261117", name("Grenoble", "grenoble"))
    }

    @Test fun `une seule commune connue suffit a nommer`() {
        assertEquals("Paris_20261117", name("Paris", null))
        assertEquals("Brest_20261117", name(" ", "Brest"))
    }

    @Test fun `sans aucune commune, le nom de repli tient lieu de lieux`() {
        assertEquals("Parcours_20261117", name(null, null))
    }

    /** Le jour est celui du fuseau du telephone : un depart a 0 h 30 a Paris est encore la veille en UTC. */
    @Test fun `le jour est pris dans le fuseau du telephone`() {
        val justAfterMidnight = ZonedDateTime.of(2026, 11, 18, 0, 30, 0, 0, paris).toInstant().toEpochMilli()
        assertEquals("Paris_20261118", RecordingName.of("Paris", null, justAfterMidnight, "Parcours", paris))
    }

    @Test fun `un nom deja pris recoit un suffixe`() {
        assertEquals("A-B_20261117", RecordingName.unique("A-B_20261117", listOf("C_20261117")))
        assertEquals("A-B_20261117_2", RecordingName.unique("A-B_20261117", listOf("A-B_20261117")))
        assertEquals("A-B_20261117_3", RecordingName.unique("A-B_20261117", listOf("A-B_20261117", "A-B_20261117_2")))
    }
}
