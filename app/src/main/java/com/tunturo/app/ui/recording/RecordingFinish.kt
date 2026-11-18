package com.tunturo.app.ui.recording

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tunturo.app.R
import com.tunturo.app.data.db.FolderEntity
import com.tunturo.app.data.repo.TunturoRepository.RecordingFolder
import com.tunturo.app.domain.model.RoutingProfile
import com.tunturo.app.location.RecordingSession
import com.tunturo.app.ui.components.AnchoredBubble
import com.tunturo.app.ui.components.CompactOutlinedTextField
import com.tunturo.app.ui.settings.FieldRow
import com.tunturo.app.ui.settings.PickRow
import com.tunturo.app.ui.settings.ProvideSettingsPalette
import com.tunturo.app.ui.settings.RoutingProfilePicker
import com.tunturo.app.ui.settings.RowDivider
import com.tunturo.app.ui.settings.SettingsCard
import com.tunturo.app.ui.settings.SwitchLine
import com.tunturo.app.ui.settings.settingsPalette

/**
 * Largeur de la bulle : la moitie de celle des points d'interet. Deux boutons cote a cote y tiennent, et
 * rien d'autre n'a a y tenir.
 */
private val RecordingBubbleWidth = 168.dp

/**
 * La bulle du bouton d'enregistrement : "Pause" et "Terminer" en enregistrement, "Reprendre" et
 * "Terminer" en pause, cote a cote.
 *
 * La meme bulle que celle des points d'interet (cf. AnchoredBubble) : elle sort du bouton qui l'ouvre, sa
 * pointe le vise. Les deux gestes ont le dessin de ceux du calcul d'itineraire - une icone sur son
 * libelle -, un peu plus grands : on les touche en marchant.
 */
@Composable
fun RecordingMenuBubble(
    open: Boolean,
    paused: Boolean,
    tailFromBottom: Dp,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnchoredBubble(open, RecordingBubbleWidth, tailFromBottom, modifier) { bubbleModifier ->
        Card(
            modifier = bubbleModifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (paused) BubbleAction(Icons.Filled.PlayArrow, stringResource(R.string.recording_action_resume_short), onResume)
                else BubbleAction(Icons.Filled.Pause, stringResource(R.string.recording_action_pause_short), onPause)
                BubbleAction(Icons.Filled.Stop, stringResource(R.string.recording_action_finish), onFinish)
            }
        }
    }
}

/** Un geste de la bulle : l'icone sur son libelle, comme Enregistrer et Exporter du calcul d'itineraire. */
@Composable
private fun BubbleAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.width(72.dp).height(60.dp).clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Les dossiers proposes a la fin d'un enregistrement : "Mes parcours" d'abord, la racine, puis les autres. */
internal fun recordingFolderOptions(folders: List<FolderEntity>, recordingsFolderId: Long?, recordingsFolderName: String): List<RecordingFolder> =
    listOf(RecordingFolder.Recordings, RecordingFolder.Root) + folders
        .filter { it.id != recordingsFolderId && !(it.parentId == null && it.name == recordingsFolderName) }
        .map { RecordingFolder.Existing(it.id) }

/**
 * La boite de fin : le nom du parcours, pre-rempli, et sous "Details" ce qui se regle moins souvent - la
 * discipline, le lissage des altitudes, le dossier ("Mes parcours" par defaut).
 *
 * Le nom propose arrive un instant apres l'ouverture : il demande les communes de depart et d'arrivee (cf.
 * RecordingSession.suggestedName). Il ne remplace pas ce qu'on aurait deja commence a taper.
 *
 * Fermee sans enregistrer, le parcours reste en pause : rien n'est perdu.
 */
@Composable
fun RecordingFinishDialog(
    dark: Boolean,
    folders: List<FolderEntity>,
    recordingsFolderId: Long?,
    defaultProfile: RoutingProfile,
    smoothByDefault: Boolean,
    suggestName: suspend () -> String,
    onSave: (RecordingSession.FinishChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var typed by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var profile by remember { mutableStateOf(defaultProfile) }
    var smooth by remember { mutableStateOf(smoothByDefault) }
    var folder by remember { mutableStateOf<RecordingFolder>(RecordingFolder.Recordings) }
    LaunchedEffect(Unit) {
        val suggested = runCatching { suggestName() }.getOrNull() ?: return@LaunchedEffect
        if (!typed) name = suggested
    }
    val recordingsName = stringResource(R.string.recordings_folder_name)
    val rootName = stringResource(R.string.label_root)
    val options = remember(folders, recordingsFolderId, recordingsName) {
        recordingFolderOptions(folders, recordingsFolderId, recordingsName)
    }
    ProvideSettingsPalette(dark = dark) {
        val palette = settingsPalette
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = palette.screen,
            title = { Text(stringResource(R.string.recording_finish_title), color = palette.label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CompactOutlinedTextField(
                        value = name, onValueChange = { name = it; typed = true }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.recording_finish_name)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    val chevron by animateFloatAsState(if (detailsOpen) 180f else 0f, label = "details")
                    Row(
                        Modifier.clip(MaterialTheme.shapes.small)
                            .clickable(role = Role.Button) { detailsOpen = !detailsOpen }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.recording_finish_details), color = palette.accent,
                            style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(20.dp).rotate(chevron), tint = palette.accent)
                    }
                    AnimatedVisibility(
                        visible = detailsOpen,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        Column(Modifier.padding(top = 4.dp)) {
                            SettingsCard {
                                FieldRow(stringResource(R.string.recording_finish_discipline)) {
                                    RoutingProfilePicker(profile) { profile = it }
                                }
                                RowDivider()
                                SwitchLine(
                                    stringResource(R.string.settings_label_recording_smooth_elevation), smooth,
                                    info = stringResource(R.string.settings_recording_smooth_elevation_hint),
                                ) { smooth = it }
                                RowDivider()
                                PickRow(
                                    stringResource(R.string.recording_finish_folder), folder, options,
                                    optionLabel = { option ->
                                        when (option) {
                                            RecordingFolder.Recordings -> recordingsName
                                            RecordingFolder.Root -> rootName
                                            is RecordingFolder.Existing -> folders.firstOrNull { it.id == option.id }?.name.orEmpty()
                                        }
                                    },
                                ) { folder = it }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSave(RecordingSession.FinishChoice(name.trim(), folder, profile.key, smooth)) },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.action_save), color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = palette.accent) }
            },
        )
    }
}
