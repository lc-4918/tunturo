package com.tunturo.app.location

import com.tunturo.app.domain.geo.RecordedPoint
import com.tunturo.app.domain.geo.RecordingPhase
import com.tunturo.app.domain.geo.ResumeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * L'enregistrement entre le service qui le nourrit, le bouton qui le commande et le disque d'ou il revient
 * apres la mort du processus. Une faute ici perd une sortie entiere, telephone en poche.
 */
class TrackRecorderTest {

    @get:Rule val folder = TemporaryFolder()

    private var clockMs = 1_000L

    @Before fun setUp() {
        TrackRecorder.resetForTest()
        TrackRecorder.writer = { it.run() }
        TrackRecorder.elapsedClock = { clockMs }
    }

    @After fun tearDown() = TrackRecorder.resetForTest()

    /** Une position a [northM] metres au nord, mesuree a [elapsedMs] (monotone) et [timeMs] (murale). */
    private fun fix(northM: Double, elapsedMs: Long, timeMs: Long = elapsedMs + 1_700_000_000_000L) = LocationHub.Fix(
        lat = 45.0 + northM / 111_195.0, lon = 6.0, accuracyM = 5f, bearingDeg = null, speedMps = 2f,
        altitudeM = 1000.0 + northM / 10, timeMs = timeMs, receivedAtMs = elapsedMs, elapsedAtMs = elapsedMs,
    )

    private fun attached(): File = folder.newFolder().also { TrackRecorder.attach(it) }

    @Test fun `les positions s'ajoutent pendant l'enregistrement, et seulement pendant`() {
        attached()
        assertFalse("pas encore demarre", TrackRecorder.add(fix(0.0, 2_000L)))
        TrackRecorder.start()
        assertTrue(TrackRecorder.add(fix(0.0, 2_000L)))
        assertTrue(TrackRecorder.add(fix(50.0, 4_000L)))
        assertTrue("la pause rend vrai : il y a un parcours a ranger", TrackRecorder.pause())
        assertFalse(TrackRecorder.add(fix(100.0, 6_000L)))
        assertEquals(2, TrackRecorder.recording.value.segments.single().size)
    }

    @Test fun `la derniere position connue, d'avant le demarrage, n'ouvre pas le parcours`() {
        attached()
        clockMs = 10_000L
        TrackRecorder.start()
        assertFalse(TrackRecorder.add(fix(0.0, 3_000L)))
        assertTrue(TrackRecorder.add(fix(0.0, 11_000L)))
    }

    @Test fun `la pause hors enregistrement ne range rien`() {
        attached()
        assertFalse(TrackRecorder.pause())
    }

    /** Le processus meurt en pleine sortie : tout revient, la phase et chaque segment. */
    @Test fun `l'enregistrement revient du disque apres la mort du processus`() {
        val dir = attached()
        TrackRecorder.start()
        TrackRecorder.add(fix(0.0, 2_000L))
        TrackRecorder.add(fix(50.0, 4_000L))
        TrackRecorder.pause()
        TrackRecorder.resume(ResumeMode.NEW_SEGMENT)
        TrackRecorder.add(fix(500.0, 8_000L))
        TrackRecorder.add(fix(600.0, 10_000L))
        val before = TrackRecorder.recording.value

        TrackRecorder.resetForTest()
        TrackRecorder.writer = { it.run() }
        TrackRecorder.attach(dir)
        val after = TrackRecorder.recording.value
        assertEquals(RecordingPhase.RECORDING, after.phase)
        // Les deux segments d'avant, et un troisieme ouvert par la reprise : rien ne relie la mort au retour.
        assertEquals(before.segments + listOf(emptyList<RecordedPoint>()), after.segments)
    }

    /** Termine, l'enregistrement quitte le disque : le prochain appui en demarre un neuf. */
    @Test fun `un enregistrement termine ne revient pas du disque`() {
        val dir = attached()
        TrackRecorder.start()
        TrackRecorder.add(fix(0.0, 2_000L))
        TrackRecorder.add(fix(50.0, 4_000L))
        TrackRecorder.pause()
        TrackRecorder.finished()
        assertEquals(RecordingPhase.IDLE, TrackRecorder.recording.value.phase)

        TrackRecorder.resetForTest()
        TrackRecorder.writer = { it.run() }
        TrackRecorder.attach(dir)
        assertEquals(RecordingPhase.IDLE, TrackRecorder.recording.value.phase)
        TrackRecorder.start()
        TrackRecorder.add(fix(900.0, 9_000L))
        assertEquals(1, TrackRecorder.recording.value.segments.single().size)
    }

    /** Un parcours en pause a la mise a jour, deja range par la v0.36, garde sa couche jusqu'a sa fin. */
    @Test fun `la couche d'un parcours deja range revient du disque`() {
        val dir = attached()
        TrackRecorder.start()
        TrackRecorder.add(fix(0.0, 2_000L))
        TrackRecorder.add(fix(50.0, 4_000L))
        TrackRecorder.pause()
        java.io.File(dir, RecordingStore.META_FILE).writeText(
            """{"phase":"PAUSED","layerId":12,"startedAtMs":1,"autoName":"Paris-Brest_20261117"}""",
        )
        TrackRecorder.resetForTest()
        TrackRecorder.attach(dir)
        assertEquals(12L, TrackRecorder.recording.value.layerId)
        assertEquals(RecordingPhase.PAUSED, TrackRecorder.recording.value.phase)
    }

    @Test fun `sans enregistrement sur le disque, rien ne revient`() {
        attached()
        assertEquals(RecordingPhase.IDLE, TrackRecorder.recording.value.phase)
    }

    /** La mort du processus peut couper la derniere ligne en pleine ecriture : elle seule se perd. */
    @Test fun `une ligne coupee du fichier des points est ignoree`() {
        val segments = RecordingStore.decodeSegments("45.0;6.0;1000.0;1\n#seg\n45.1;6.0;;2\n45.2;6.")
        assertEquals(2, segments.size)
        assertEquals(1, segments[0].size)
        assertEquals(listOf(RecordedPoint(45.1, 6.0, null, 2L)), segments[1])
    }

    @Test fun `les segments s'ecrivent et se relisent a l'identique`() {
        val segments = listOf(
            listOf(RecordedPoint(45.0, 6.0, 1000.5, 1L), RecordedPoint(45.1, 6.1, null, 2L)),
            listOf(RecordedPoint(45.2, 6.2, 990.0, 3L)),
        )
        assertEquals(segments, RecordingStore.decodeSegments(RecordingStore.encodeSegments(segments)))
    }
}
