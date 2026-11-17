package com.tunturo.app.ui.recording

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tunturo.app.location.RecordingSession
import com.tunturo.app.location.TrackRecorder
import com.tunturo.app.ui.location.LocationControls
import com.tunturo.app.ui.routes.MapChrome
import com.tunturo.app.ui.routes.OffTrackAlertColor
import com.tunturo.app.ui.settings.ChoiceBlock
import com.tunturo.app.ui.settings.ProvideSettingsPalette
import com.tunturo.app.ui.settings.RowDivider
import com.tunturo.app.ui.settings.SettingsCard
import com.tunturo.app.ui.settings.settingsPalette

/** Ce que dit le bandeau de l'enregistrement, en bas de la carte. */
sealed interface RecordingNotice {
    data object Started : RecordingNotice
    data object Resumed : RecordingNotice
    data class Saved(val name: String) : RecordingNotice
    data object Empty : RecordingNotice
    data object Failed : RecordingNotice
}

/**
 * Le bouton d'enregistrement de la carte et ce qui l'accompagne : la boite de reprise, le bandeau.
 *
 * **Un bouton, deux etats** : il enregistre, ou il n'enregistre pas. En enregistrement, un appui met en
 * pause et range le parcours dans "Mes parcours". Hors enregistrement, un appui demarre - directement s'il
 * n'y a rien a reprendre, et sinon apres avoir demande comment continuer (cf. [ResumeMode]).
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

    /** Les segments enregistres, a dessiner sur la carte. */
    val segments get() = recordingState.value.segments

    /** La boite "Comment reprendre ?" est ouverte. */
    var showResumeDialog by mutableStateOf(false)

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

    /** L'appui sur le bouton. */
    fun onTap(location: LocationControls) {
        when (phase) {
            RecordingPhase.RECORDING -> session.pause()
            RecordingPhase.PAUSED -> showResumeDialog = true
            RecordingPhase.IDLE -> withSensor(location) {
                session.start()
                showNotice(RecordingNotice.Started)
            }
        }
    }

    /** Le choix de la boite de reprise. */
    fun onResume(mode: ResumeMode, location: LocationControls) {
        showResumeDialog = false
        withSensor(location) {
            session.resume(mode)
            showNotice(if (mode == ResumeMode.NEW_TRACK) RecordingNotice.Started else RecordingNotice.Resumed)
        }
    }

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
 * point garde. Rien a dessiner hors enregistrement et hors pause.
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

/** Le dessin du bouton : un rond rouge plein en enregistrement, le symbole "enregistrer" sinon. */
@Composable
fun RecordButtonIcon(recording: Boolean, chrome: MapChrome) {
    if (recording) {
        Icon(Icons.Filled.FiberManualRecord, stringResource(R.string.recording_action_pause), tint = OffTrackAlertColor)
    } else {
        Icon(Icons.Outlined.RadioButtonChecked, stringResource(R.string.recording_action_record), tint = chrome.fg)
    }
}

/** Le texte du bandeau. */
@Composable
fun recordingNoticeText(notice: RecordingNotice): String = when (notice) {
    RecordingNotice.Started -> stringResource(R.string.recording_notice_started)
    RecordingNotice.Resumed -> stringResource(R.string.recording_notice_resumed)
    is RecordingNotice.Saved -> stringResource(R.string.recording_notice_saved, notice.name)
    RecordingNotice.Empty -> stringResource(R.string.recording_notice_empty)
    RecordingNotice.Failed -> stringResource(R.string.recording_notice_failed)
}

/**
 * Comment reprendre : dans le meme segment, dans un nouveau segment du meme parcours, ou dans un nouveau
 * parcours. Chaque choix dit ce qu'il fait - la difference entre les deux premiers ne se devine pas.
 */
@Composable
fun RecordingResumeDialog(dark: Boolean, onPick: (ResumeMode) -> Unit, onDismiss: () -> Unit) {
    ProvideSettingsPalette(dark = dark) {
        val palette = settingsPalette
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = palette.screen,
            title = { Text(stringResource(R.string.recording_resume_title), color = palette.label) },
            text = {
                SettingsCard {
                    ChoiceBlock(stringResource(R.string.recording_resume_same_segment),
                        stringResource(R.string.recording_resume_same_segment_hint)) { onPick(ResumeMode.SAME_SEGMENT) }
                    RowDivider()
                    ChoiceBlock(stringResource(R.string.recording_resume_new_segment),
                        stringResource(R.string.recording_resume_new_segment_hint)) { onPick(ResumeMode.NEW_SEGMENT) }
                    RowDivider()
                    ChoiceBlock(stringResource(R.string.recording_resume_new_track),
                        stringResource(R.string.recording_resume_new_track_hint)) { onPick(ResumeMode.NEW_TRACK) }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = palette.accent) }
            },
        )
    }
}
