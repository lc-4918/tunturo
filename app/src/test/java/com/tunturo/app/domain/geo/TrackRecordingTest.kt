package com.tunturo.app.domain.geo

import com.tunturo.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Les regles de l'enregistrement : ce qu'on garde d'une position, et ce que deviennent les segments a la
 * pause et a la reprise. Une faute ici dessine un trait qui n'a pas ete parcouru, ou perd un morceau de
 * sortie sans rien dire.
 */
class TrackRecordingTest {

    /** Un point a [northM] metres au nord du depart. */
    private fun accept(
        recording: Recording, northM: Double, timeMs: Long,
        accuracyM: Float = 5f, measuredAtMs: Long = timeMs, notBeforeMs: Long = 0L,
    ) = TrackRecording.accept(
        recording, 45.0 + northM / 111_195.0, 6.0, accuracyM, 1000.0, timeMs, measuredAtMs, notBeforeMs,
    )

    private fun recordingWith(vararg northM: Double): Recording {
        var recording = TrackRecording.start(Recording(), nowMs = 0L)
        northM.forEachIndexed { index, meters ->
            recording = TrackRecording.append(recording, accept(recording, meters, (index + 1) * 10_000L)!!)
        }
        return recording
    }

    @Test fun `le demarrage ouvre un premier segment vide`() {
        val recording = TrackRecording.start(Recording(), nowMs = 42L)
        assertEquals(RecordingPhase.RECORDING, recording.phase)
        assertEquals(listOf(emptyList<RecordedPoint>()), recording.segments)
        assertEquals(42L, recording.startedAtMs)
    }

    @Test fun `hors enregistrement, aucune position n'est gardee`() {
        assertNull(accept(Recording(), 0.0, 1_000L))
        assertNull(accept(TrackRecording.pause(recordingWith(0.0)), 50.0, 99_000L))
    }

    @Test fun `une position trop floue n'est pas gardee`() {
        assertNull(accept(recordingWith(), 0.0, 1_000L, accuracyM = 40f))
        assertNotNull(accept(recordingWith(), 0.0, 1_000L, accuracyM = 20f))
    }

    /** La derniere position connue rendue a l'abonnement : mesuree avant le demarrage, elle n'en est pas. */
    @Test fun `une position mesuree avant le demarrage n'est pas gardee`() {
        assertNull(accept(recordingWith(), 0.0, 1_000L, measuredAtMs = 4_000L, notBeforeMs = 5_000L))
    }

    /**
     * L'economie d'energie coupe le GPS ecran eteint, et le systeme livre au reveil des positions mesurees
     * plusieurs minutes plus tot : elles sont du parcours. Un filtre sur l'AGE a la reception les perdrait.
     */
    @Test fun `une salve livree au reveil est gardee`() {
        val recording = recordingWith(0.0)
        assertNotNull(accept(recording, 100.0, 20_000L, measuredAtMs = 20_000L, notBeforeMs = 5_000L))
    }

    @Test fun `le tremblement du GPS a l'arret n'ajoute pas de point`() {
        val recording = recordingWith(0.0)
        assertNull("trois metres", accept(recording, 3.0, 20_000L))
        assertNull("sous la precision", accept(recording, 8.0, 20_000L, accuracyM = 12f))
        assertNotNull("dix metres", accept(recording, 10.0, 20_000L))
    }

    @Test fun `une position qui n'est pas posterieure au dernier point n'est pas gardee`() {
        val recording = recordingWith(0.0)
        assertNull(accept(recording, 50.0, 10_000L))
    }

    @Test fun `continuer le meme segment ajoute les points au segment de la pause`() {
        val paused = TrackRecording.pause(recordingWith(0.0, 50.0))
        val resumed = TrackRecording.resume(paused, ResumeMode.SAME_SEGMENT)
        val point = accept(resumed, 500.0, 90_000L)!!
        val after = TrackRecording.append(resumed, point)
        assertEquals(1, after.segments.size)
        assertEquals(3, after.segments[0].size)
    }

    /** Le premier point du nouveau segment n'est pas compare au dernier de l'ancien : rien ne les relie. */
    @Test fun `continuer dans un nouveau segment ne relie pas la pause a la reprise`() {
        val paused = TrackRecording.pause(recordingWith(0.0, 50.0))
        val resumed = TrackRecording.resume(paused, ResumeMode.NEW_SEGMENT)
        assertEquals(RecordingPhase.RECORDING, resumed.phase)
        val after = TrackRecording.append(resumed, accept(resumed, 52.0, 90_000L)!!)
        assertEquals(2, after.segments.size)
        assertEquals(1, after.segments[1].size)
    }

    /** Le reglage de reprise se lit par sa cle ; une cle inconnue revient au defaut, le meme segment. */
    @Test fun `le mode de reprise se lit par sa cle`() {
        assertEquals(ResumeMode.SAME_SEGMENT, ResumeMode.of("same_segment"))
        assertEquals(ResumeMode.NEW_SEGMENT, ResumeMode.of("new_segment"))
        assertEquals(ResumeMode.SAME_SEGMENT, ResumeMode.of("new_track"))
        assertEquals(ResumeMode.SAME_SEGMENT, ResumeMode.of(null))
    }

    @Test fun `la pause et la reprise sont sans effet hors de leur phase`() {
        val idle = Recording()
        assertSame(idle, TrackRecording.pause(idle))
        assertSame(idle, TrackRecording.resume(idle, ResumeMode.SAME_SEGMENT))
        val running = recordingWith(0.0)
        assertSame(running, TrackRecording.start(running, 0L))
    }

    /** Repris apres la mort du processus : on a pu bouger entre-temps, le trait droit mentirait. */
    @Test fun `un enregistrement repris en cours continue dans un nouveau segment`() {
        val restored = TrackRecording.restored(recordingWith(0.0, 50.0))
        assertEquals(2, restored.segments.size)
        val paused = TrackRecording.pause(recordingWith(0.0, 50.0))
        assertSame(paused, TrackRecording.restored(paused))
    }

    @Test fun `un segment d'un seul point ne se range pas`() {
        val paused = TrackRecording.pause(recordingWith(0.0))
        assertEquals(emptyList<List<TrackPoint>>(), TrackRecording.lines(paused))
        assertNull(TrackRecording.ends(paused))
        val two = TrackRecording.pause(recordingWith(0.0, 50.0))
        assertEquals(1, TrackRecording.lines(two).size)
        assertEquals(2, TrackRecording.lines(two)[0].size)
    }

    @Test fun `les bouts du parcours sont le premier et le dernier point gardes`() {
        val first = TrackRecording.resume(TrackRecording.pause(recordingWith(0.0, 50.0)), ResumeMode.NEW_SEGMENT)
        var recording = TrackRecording.append(first, accept(first, 1_000.0, 90_000L)!!)
        recording = TrackRecording.append(recording, accept(recording, 1_100.0, 100_000L)!!)
        val (start, end) = TrackRecording.ends(recording)!!
        assertEquals(10_000L, start.timeMs)
        assertEquals(100_000L, end.timeMs)
    }

    /** La pause ne range plus rien : le trait en direct est seul a montrer le parcours, jusqu'a sa fin. */
    @Test fun `le trait en direct se dessine en enregistrement et en pause`() {
        val running = recordingWith(0.0, 50.0)
        assertEquals(running.segments, TrackRecording.drawn(running))
        val paused = TrackRecording.pause(running)
        assertEquals(paused.segments, TrackRecording.drawn(paused))
        val resumed = TrackRecording.resume(paused, ResumeMode.SAME_SEGMENT)
        assertEquals(resumed.segments, TrackRecording.drawn(resumed))
        assertEquals(emptyList<List<RecordedPoint>>(), TrackRecording.drawn(Recording()))
    }

    /** Le modele de terrain a repondu pour une partie seulement : le reste garde l'altitude du GPS. */
    @Test fun `le lissage garde l'altitude du GPS la ou le modele n'a rien rendu`() {
        val gps = listOf(listOf(TrackPoint(6.0, 45.0, 1000.0), TrackPoint(6.0, 45.1, 1010.0)))
        val model = listOf(listOf(TrackPoint(6.0, 45.0, 990.0), TrackPoint(6.0, 45.1, null)))
        val merged = TrackRecording.mergeElevation(gps, model)
        assertEquals(990.0, merged[0][0].ele!!, 0.0)
        assertEquals(1010.0, merged[0][1].ele!!, 0.0)
    }
}
