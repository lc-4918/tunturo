package com.tunturo.app.location

import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.R
import com.tunturo.app.data.RecordingPrefs
import com.tunturo.app.data.db.AppDatabase
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.data.repo.TunturoRepository
import com.tunturo.app.domain.geo.ResumeMode
import com.tunturo.app.elevation.ElevationResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Le rangement d'un parcours enregistre dans "Mes parcours", a chaque pause : le dossier cree une fois,
 * la couche reecrite en place quand on continue, son nom qui suit l'arrivee tant qu'on ne l'a pas change.
 */
@RunWith(RobolectricTestRunner::class)
class RecordingSessionTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val repository = TunturoRepository(ctx)
    private val db = AppDatabase.get(ctx)

    /** Les communes, par latitude : au sud Paris, au nord Brest. Pas de reseau dans un test. */
    private val session = RecordingSession(ctx, repository, CoroutineScope(Dispatchers.Unconfined)) { _, lat ->
        if (lat < 45.005) "Paris" else "Brest"
    }

    private var clockMs = 1_000L
    private val folderName get() = ctx.getString(R.string.recordings_folder_name)
    private val today get() = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    @Before fun setUp() = runBlocking {
        TrackRecorder.resetForTest()
        TrackRecorder.writer = { it.run() }
        TrackRecorder.elapsedClock = { clockMs }
        val settings = db.settings().get() ?: SettingsEntity()
        db.settings().upsert(settings.copy(fillMissingElevation = false, recordingSmoothElevation = false))
    }

    @After fun tearDown() = TrackRecorder.resetForTest()

    /** Une position a [northM] metres au nord du depart. */
    private fun fix(northM: Double) = LocationHub.Fix(
        lat = 45.0 + northM / 111_195.0, lon = 2.35, accuracyM = 5f, bearingDeg = null, speedMps = 2f,
        altitudeM = 100.0, timeMs = System.currentTimeMillis() + clockMs, receivedAtMs = clockMs, elapsedAtMs = clockMs,
    ).also { clockMs += 2_000L }

    private fun record(vararg northM: Double) = northM.forEach { TrackRecorder.add(fix(it)) }

    private suspend fun pauseAndSave(): RecordingSession.SaveOutcome {
        TrackRecorder.pause()
        return session.save()
    }

    @Test fun `le premier enregistrement cree Mes parcours, le suivant le reutilise`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0)
        pauseAndSave()
        val folders = db.folders().all().first().filter { it.name == folderName && it.parentId == null }
        assertEquals(1, folders.size)
        val firstLayer = db.layers().byId(TrackRecorder.recording.value.layerId!!)!!
        assertEquals(folders.single().id, firstLayer.folderId)
        assertEquals("record", firstLayer.source)
        assertTrue(firstLayer.hasTime)

        TrackRecorder.resume(ResumeMode.NEW_TRACK)
        record(0.0, 60.0)
        pauseAndSave()
        val secondLayer = db.layers().byId(TrackRecorder.recording.value.layerId!!)!!
        assertNotEquals(firstLayer.id, secondLayer.id)
        assertEquals(firstLayer.folderId, secondLayer.folderId)
        assertEquals(1, db.folders().all().first().count { it.name == folderName && it.parentId == null })
    }

    @Test fun `le parcours porte les communes de depart et d'arrivee et le jour`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0, 1_000.0)
        val outcome = pauseAndSave() as RecordingSession.SaveOutcome.Saved
        assertTrue(outcome.name, outcome.name.startsWith("Paris-Brest_$today"))
    }

    @Test fun `le dossier supprime est recree`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0)
        pauseAndSave()
        val folder = db.folders().all().first().first { it.id == RecordingPrefs.folderId(ctx) }
        db.folders().delete(folder)
        TrackRecorder.resume(ResumeMode.NEW_TRACK)
        record(0.0, 50.0)
        pauseAndSave()
        val recreated = RecordingPrefs.folderId(ctx)!!
        assertNotEquals(folder.id, recreated)
        assertTrue(db.folders().all().first().any { it.id == recreated && it.name == folderName })
    }

    /** Continuer puis remettre en pause : la meme couche, avec tout ce qui a ete enregistre depuis le depart. */
    @Test fun `une pause apres Continuer reecrit la meme couche et suit l'arrivee`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0)
        val first = pauseAndSave() as RecordingSession.SaveOutcome.Saved
        assertTrue(first.name, first.name.startsWith("Paris_$today"))
        val layerId = TrackRecorder.recording.value.layerId!!
        val distanceBefore = db.layers().byId(layerId)!!.distance

        TrackRecorder.resume(ResumeMode.SAME_SEGMENT)
        record(1_000.0)
        val second = pauseAndSave() as RecordingSession.SaveOutcome.Saved
        assertEquals(layerId, TrackRecorder.recording.value.layerId)
        val layer = db.layers().byId(layerId)!!
        assertTrue("la distance grandit", layer.distance > distanceBefore + 900)
        assertTrue(second.name, layer.name.startsWith("Paris-Brest_$today"))
    }

    @Test fun `un parcours renomme a la main garde son nom`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0)
        pauseAndSave()
        val layerId = TrackRecorder.recording.value.layerId!!
        db.layers().rename(layerId, "Sortie du mardi")
        TrackRecorder.resume(ResumeMode.NEW_SEGMENT)
        record(1_000.0, 1_050.0)
        pauseAndSave()
        val layer = db.layers().byId(layerId)!!
        assertEquals("Sortie du mardi", layer.name)
        assertEquals(2, repository.loadTrackLines(layer).size)
    }

    @Test fun `moins de deux points ne rangent rien`() = runBlocking {
        TrackRecorder.start()
        record(0.0)
        assertEquals(RecordingSession.SaveOutcome.Empty, pauseAndSave())
    }

    @Test fun `le lissage prend les altitudes du modele de terrain`() = runBlocking {
        db.settings().upsert(db.settings().get()!!.copy(recordingSmoothElevation = true))
        repository.elevationFetcher = { url ->
            // Le service de l'IGN (la trace est en France), a autant d'altitudes que de points demandes.
            if ("geopf" in url || "elevation.json" in url) {
                val count = url.substringAfter("lon=").substringBefore("&").split("|").size
                ElevationResponse(200, """{"elevations": [${List(count) { 321.0 }.joinToString(", ")}]}""")
            } else ElevationResponse(500, null)
        }
        TrackRecorder.start()
        record(0.0, 50.0)
        pauseAndSave()
        val layer = db.layers().byId(TrackRecorder.recording.value.layerId!!)!!
        assertTrue(repository.loadTrackLines(layer).single().all { it.ele == 321.0 })
    }

    /** Hors reseau, le parcours se range quand meme, avec l'altitude du GPS. */
    @Test fun `le lissage sans reseau garde l'altitude du GPS`() = runBlocking {
        db.settings().upsert(db.settings().get()!!.copy(recordingSmoothElevation = true))
        repository.elevationFetcher = { ElevationResponse(500, null) }
        TrackRecorder.start()
        record(0.0, 50.0)
        pauseAndSave()
        val layer = db.layers().byId(TrackRecorder.recording.value.layerId!!)!!
        assertTrue(repository.loadTrackLines(layer).single().all { it.ele == 100.0 })
    }
}
