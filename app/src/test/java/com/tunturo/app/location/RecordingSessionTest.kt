package com.tunturo.app.location

import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.R
import com.tunturo.app.data.RecordingPrefs
import com.tunturo.app.data.db.AppDatabase
import com.tunturo.app.data.db.FolderEntity
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.data.repo.TunturoRepository
import com.tunturo.app.data.repo.TunturoRepository.RecordingFolder
import com.tunturo.app.domain.geo.RecordingPhase
import com.tunturo.app.elevation.ElevationResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * La fin d'un enregistrement : la pause ne range rien, "Terminer" range le parcours sous le nom, la
 * discipline, les altitudes et dans le dossier choisis - "Mes parcours" par defaut, cree une fois.
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

    private suspend fun finish(
        name: String = "Sortie",
        folder: RecordingFolder = RecordingFolder.Recordings,
        profile: String? = "gravel",
        smooth: Boolean = false,
    ): RecordingSession.SaveOutcome {
        session.pause()
        return session.save(RecordingSession.FinishChoice(name, folder, profile, smooth))
    }

    private suspend fun layerNamed(name: String) = db.layers().all().first().single { it.name == name }

    @Test fun `la pause ne range rien`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0)
        assertTrue(session.pause())
        assertEquals(RecordingPhase.PAUSED, TrackRecorder.recording.value.phase)
        assertTrue(db.layers().all().first().none { it.source == "record" })
    }

    @Test fun `terminer range le parcours et termine l'enregistrement`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0)
        val outcome = finish("Col du matin") as RecordingSession.SaveOutcome.Saved
        assertEquals("Col du matin", outcome.name)
        assertEquals(RecordingPhase.IDLE, TrackRecorder.recording.value.phase)
        val layer = layerNamed("Col du matin")
        assertEquals("record", layer.source)
        assertEquals("gravel", layer.routingProfile)
        assertTrue(layer.hasTime)
    }

    @Test fun `le premier enregistrement cree Mes parcours, le suivant le reutilise`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0)
        finish("Premier")
        TrackRecorder.start()
        record(0.0, 60.0)
        finish("Second")
        val folders = db.folders().all().first().filter { it.name == folderName && it.parentId == null }
        assertEquals(1, folders.size)
        assertEquals(folders.single().id, layerNamed("Premier").folderId)
        assertEquals(folders.single().id, layerNamed("Second").folderId)
    }

    @Test fun `le nom propose porte les communes de depart et d'arrivee et le jour`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0, 1_000.0)
        session.pause()
        assertEquals("Paris-Brest_$today", session.suggestedName())
    }

    @Test fun `un nom deja pris dans le dossier recoit un suffixe`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0)
        finish("Boucle")
        TrackRecorder.start()
        record(0.0, 50.0)
        val second = finish("Boucle") as RecordingSession.SaveOutcome.Saved
        assertEquals("Boucle_2", second.name)
    }

    @Test fun `le parcours se range dans le dossier choisi ou a la racine`() = runBlocking {
        val folderId = db.folders().insert(FolderEntity(name = "Vacances"))
        TrackRecorder.start()
        record(0.0, 50.0)
        finish("Plage", RecordingFolder.Existing(folderId))
        assertEquals(folderId, layerNamed("Plage").folderId)
        TrackRecorder.start()
        record(0.0, 50.0)
        finish("Racine", RecordingFolder.Root)
        assertNull(layerNamed("Racine").folderId)
    }

    @Test fun `le dossier supprime est recree`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0)
        finish("Un")
        val folder = db.folders().all().first().first { it.id == RecordingPrefs.folderId(ctx) }
        db.folders().delete(folder)
        TrackRecorder.start()
        record(0.0, 50.0)
        finish("Deux")
        val recreated = RecordingPrefs.folderId(ctx)!!
        assertNotEquals(folder.id, recreated)
        assertTrue(db.folders().all().first().any { it.id == recreated && it.name == folderName })
    }

    /** Un parcours deja range par la v0.36 (a chaque pause) se termine dans sa couche, reecrite en place. */
    @Test fun `un parcours deja range se termine dans sa couche`() = runBlocking {
        val dir = java.io.File(ctx.cacheDir, "recording-legacy").apply { deleteRecursively(); mkdirs() }
        TrackRecorder.attach(dir)
        TrackRecorder.start()
        record(0.0, 50.0)
        val first = finish("Ancien") as RecordingSession.SaveOutcome.Saved
        val layerId = layerNamed(first.name).id
        TrackRecorder.start()
        record(0.0, 50.0, 1_000.0)
        session.pause()
        java.io.File(dir, RecordingStore.META_FILE).writeText("""{"phase":"PAUSED","layerId":$layerId,"startedAtMs":1}""")
        TrackRecorder.resetForTest()
        TrackRecorder.writer = { it.run() }
        TrackRecorder.attach(dir)
        finish("Repris")
        val layer = db.layers().byId(layerId)!!
        assertEquals("Repris", layer.name)
        assertTrue("la distance grandit", layer.distance > 900)
        assertFalse(db.layers().all().first().any { it.name == "Ancien" })
    }

    @Test fun `moins de deux points ne rangent rien et terminent quand meme`() = runBlocking {
        TrackRecorder.start()
        record(0.0)
        assertEquals(RecordingSession.SaveOutcome.Empty, finish())
        assertEquals(RecordingPhase.IDLE, TrackRecorder.recording.value.phase)
    }

    @Test fun `le lissage prend les altitudes du modele de terrain et devient le reglage`() = runBlocking {
        repository.elevationFetcher = { url ->
            // Le service de l'IGN (la trace est en France), a autant d'altitudes que de points demandes.
            if ("geopf" in url || "elevation.json" in url) {
                val count = url.substringAfter("lon=").substringBefore("&").split("|").size
                ElevationResponse(200, """{"elevations": [${List(count) { 321.0 }.joinToString(", ")}]}""")
            } else ElevationResponse(500, null)
        }
        TrackRecorder.start()
        record(0.0, 50.0)
        finish("Lisse", smooth = true)
        assertTrue(repository.loadTrackLines(layerNamed("Lisse")).single().all { it.ele == 321.0 })
        assertTrue(db.settings().get()!!.recordingSmoothElevation)
    }

    /** Hors reseau, le parcours se range quand meme, avec l'altitude du GPS. */
    @Test fun `le lissage sans reseau garde l'altitude du GPS`() = runBlocking {
        repository.elevationFetcher = { ElevationResponse(500, null) }
        TrackRecorder.start()
        record(0.0, 50.0)
        finish("Sans reseau", smooth = true)
        assertTrue(repository.loadTrackLines(layerNamed("Sans reseau")).single().all { it.ele == 100.0 })
    }

    @Test fun `sans lissage l'altitude du GPS reste`() = runBlocking {
        TrackRecorder.start()
        record(0.0, 50.0)
        finish("Brut")
        assertTrue(repository.loadTrackLines(layerNamed("Brut")).single().all { it.ele == 100.0 })
    }
}
