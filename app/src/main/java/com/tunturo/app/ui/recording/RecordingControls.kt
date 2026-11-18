package com.tunturo.app.ui.recording

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tunturo.app.R
import com.tunturo.app.TunturoApp
import com.tunturo.app.domain.geo.RecordedPoint
import com.tunturo.app.domain.geo.Recording
import com.tunturo.app.ui.components.MapController
import com.tunturo.app.domain.geo.RecordingPhase
import com.tunturo.app.domain.geo.ResumeMode
import com.tunturo.app.domain.geo.TrackRecording
import com.tunturo.app.location.RecordingSession
import com.tunturo.app.location.TrackRecorder
import com.tunturo.app.ui.location.LocationControls
import com.tunturo.app.ui.routes.OffTrackAlertColor

/** Ce que dit le bandeau de l'enregistrement, en bas de la carte. */
sealed interface RecordingNotice {
    data object Started : RecordingNotice
    data object Paused : RecordingNotice
    data object Resumed : RecordingNotice
    data class Saved(val name: String) : RecordingNotice
    data object Empty : RecordingNotice
    data object Failed : RecordingNotice
}

/**
 * Le bouton d'enregistrement de la carte et ce qui l'accompagne : sa bulle, la boite de fin, le bandeau.
 *
 * **Un bouton, deux etats** : il enregistre (fond allume), ou il n'enregistre pas.
 * - En enregistrement, l'appui ouvre une bulle a deux choix : "Pause" - le bouton s'eteint, le parcours
 *   attend sur le disque - et "Terminer", qui ouvre la boite de fin (nom, discipline, altitudes, dossier).
 * - En pause, la meme bulle propose "Reprendre" - selon le reglage, sans question (cf. [ResumeMode]) - et
 *   "Terminer".
 * - Sans enregistrement, l'appui en demarre un.
 *
 * Il ne tient pas l'enregistrement : c'est [TrackRecorder], hors de l'ecran, qui le tient, et
 * [RecordingSession] qui le range. S'obtient par [rememberRecordingControls].
 */
@Stable
class RecordingControls internal constructor(
    private val session: RecordingSession,
    private val recordingState: State<Recording>,
) {
    val phase: RecordingPhase get() = recordingState.value.phase

    /** Le bouton est allume : les positions s'enregistrent. */
    val recording: Boolean get() = phase == RecordingPhase.RECORDING

    /** Les segments a dessiner sur la carte, pause comprise (cf. TrackRecording.drawn). */
    val drawnSegments get() = TrackRecording.drawn(recordingState.value)

    /** La bulle "Pause / Terminer" est ouverte. */
    var menuOpen by mutableStateOf(false)
        private set

    /** La boite de fin est ouverte. */
    var finishDialogOpen by mutableStateOf(false)
        private set

    var notice by mutableStateOf<RecordingNotice?>(null)
        private set

    /** Compteur d'affichages : relance le delai d'effacement, meme quand le meme message revient. */
    var noticeStamp by mutableIntStateOf(0)
        private set

    fun showNotice(value: RecordingNotice) {
        notice = value
        noticeStamp++
    }

    fun dismissNotice() { notice = null }

    /** L'appui sur le bouton : la bulle en enregistrement comme en pause, le demarrage sinon. */
    fun onTap(location: LocationControls) {
        when (phase) {
            RecordingPhase.RECORDING, RecordingPhase.PAUSED -> menuOpen = !menuOpen
            RecordingPhase.IDLE -> withSensor(location) {
                session.start()
                showNotice(RecordingNotice.Started)
            }
        }
    }

    /** "Reprendre" dans la bulle de la pause. [mode] est celui du reglage. */
    fun onResume(location: LocationControls, mode: ResumeMode) {
        menuOpen = false
        withSensor(location) {
            session.resume(mode)
            showNotice(RecordingNotice.Resumed)
        }
    }

    fun closeMenu() { menuOpen = false }

    /** "Pause" dans la bulle. */
    fun onPause() {
        menuOpen = false
        if (session.pause()) showNotice(RecordingNotice.Paused)
    }

    /** "Terminer" dans la bulle : plus rien ne s'enregistre, et la boite de fin s'ouvre. */
    fun onFinish() {
        menuOpen = false
        session.pause()
        finishDialogOpen = true
    }

    /** "Enregistrer" dans la boite de fin. */
    fun onFinishConfirmed(choice: RecordingSession.FinishChoice) {
        finishDialogOpen = false
        session.finish(choice)
    }

    /** La boite de fin refermee sans rien ranger : le parcours reste en pause. */
    fun onFinishDismissed() { finishDialogOpen = false }

    /** Le nom propose par la boite de fin (cf. RecordingSession.suggestedName). */
    suspend fun suggestedName(): String = session.suggestedName()

    internal fun onOutcome(outcome: RecordingSession.SaveOutcome) {
        showNotice(
            when (outcome) {
                is RecordingSession.SaveOutcome.Saved -> RecordingNotice.Saved(outcome.name)
                RecordingSession.SaveOutcome.Empty -> RecordingNotice.Empty
                RecordingSession.SaveOutcome.Failed -> RecordingNotice.Failed
            },
        )
    }

    /** [action] une fois le capteur allume pour l'enregistrement, l'autorisation demandee s'il le faut. */
    private fun withSensor(location: LocationControls, action: () -> Unit) {
        location.withLocationPermission { if (location.startForRecording()) action() }
    }

    companion object {
        /** Duree d'affichage du bandeau. */
        const val NoticeMs = 3_000L
    }
}

@Composable
fun rememberRecordingControls(location: LocationControls): RecordingControls {
    val app = LocalContext.current.applicationContext as TunturoApp
    val session = app.recordingSession
    val recordingState = TrackRecorder.recording.collectAsState()
    val controls = remember(session, recordingState) { RecordingControls(session, recordingState) }
    LaunchedEffect(session, controls) { session.outcomes.collect { controls.onOutcome(it) } }
    // Un enregistrement en cours sans capteur - repris du disque apres la mort de l'application, ou le
    // service arrete par le systeme : le capteur se rallume, et l'enregistrement continue.
    LaunchedEffect(controls.recording, location.gpsActive) {
        if (controls.recording && !location.gpsActive && location.hasLocationPermission()) location.startForRecording()
    }
    return controls
}

/**
 * Le parcours enregistre, dessine en direct sur la carte : rouge comme le bouton qui l'enregistre, borde
 * de blanc comme les traits mis en evidence. Rejoue a chaque style (cf. MapOverlayEffects) et a chaque
 * point garde. Visible en pause aussi ; a la fin, la couche rangee dans la bibliotheque prend le relais.
 */
@Composable
fun RecordingLineEffect(controller: MapController, segments: List<List<RecordedPoint>>, styleTick: Int) {
    LaunchedEffect(segments, styleTick) {
        controller.setRouteHighlight(
            segments.map { segment -> segment.map { it.lon to it.lat } },
            key = RecordingLineKey, color = RecordingLineColor,
        )
    }
}

private const val RecordingLineKey = "recording"
private const val RecordingLineColor = "#B3261E"

/**
 * Le dessin du bouton : un rond rouge plein, sans cercle de contour, dans les deux etats - le symbole
 * "enregistrer" de tout magnetophone, et rien qui rappelle la cible du bouton de recentrage. L'etat se lit
 * au fond du bouton, allume pendant l'enregistrement comme celui des autres commandes actives.
 */
@Composable
fun RecordButtonIcon(phase: RecordingPhase) {
    Icon(
        Icons.Filled.FiberManualRecord,
        stringResource(
            when (phase) {
                RecordingPhase.RECORDING -> R.string.recording_action_pause
                RecordingPhase.PAUSED -> R.string.recording_action_paused_menu
                RecordingPhase.IDLE -> R.string.recording_action_record
            },
        ),
        tint = OffTrackAlertColor,
    )
}

/** Le bouton est sur la carte : le reglage l'y pose, et un enregistrement en cours l'y garde. */
fun recordButtonShown(enabledInSettings: Boolean, phase: RecordingPhase): Boolean =
    enabledInSettings || phase == RecordingPhase.RECORDING

/** Le texte du bandeau. */
@Composable
fun recordingNoticeText(notice: RecordingNotice): String = when (notice) {
    RecordingNotice.Started -> stringResource(R.string.recording_notice_started)
    RecordingNotice.Paused -> stringResource(R.string.recording_notice_paused)
    RecordingNotice.Resumed -> stringResource(R.string.recording_notice_resumed)
    is RecordingNotice.Saved -> stringResource(R.string.recording_notice_saved, notice.name)
    RecordingNotice.Empty -> stringResource(R.string.recording_notice_empty)
    RecordingNotice.Failed -> stringResource(R.string.recording_notice_failed)
}
