package com.tunturo.app.ui.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import com.tunturo.app.ui.theme.smallButtonBackground
import com.tunturo.app.ui.theme.smallButtonContent
import com.tunturo.app.ui.offline.offlineDownloadAvailable
import com.tunturo.app.ui.offline.basemapLabel
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.tunturo.app.map.offline.TileHttp
import com.tunturo.app.map.offline.TileUrl
import java.util.Locale
import androidx.compose.ui.draw.alpha
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.tunturo.app.R
import com.tunturo.app.data.db.CompositeEntity
import com.tunturo.app.data.db.CompositeSortOrder
import com.tunturo.app.data.db.DefaultDemOpacityPct
import com.tunturo.app.data.db.ProviderEntity
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.map.flagAssetModel
import com.tunturo.app.map.flagCodeFor
import com.tunturo.app.map.offline.OfflineThumbnails
import java.io.File
import kotlinx.coroutines.launch
import com.tunturo.app.ui.theme.TunturoIcons

/**
 * Les fonds de plan, en tete de l'onglet Carte : les cartes en ligne (le catalogue des fournisseurs) et les
 * cartes hors ligne (MBTiles et composites), chacune une ligne qui s'ouvre en accordeon. L'onglet Tuiles,
 * qui les portait, a disparu : on y venait surtout depuis la carte, pour un fond qu'on allait regarder.
 *
 * **Chaque ligne d'accordeon est un element de la liste**, et son en-tete - les gestes qui l'alimentent -
 * reste colle en haut tant que ses lignes defilent dessous : la liste des fonds hors ligne peut etre
 * longue, et l'import ne doit pas se chercher au bout.
 */

/**
 * L'etat des fonds de plan de l'onglet : les deux accordeons, les boites ouvertes, et les gestes qui
 * passent par un selecteur du systeme.
 *
 * **Tenu par l'ecran, et non par un element de la liste** : un element qui defile hors de l'ecran est
 * oublie, et avec lui un selecteur de fichier ouvert depuis lui - le fichier choisi n'aurait plus personne
 * a qui revenir. Cf. [rememberBasemapSettings], [BasemapSettingsDialogs].
 */
@Stable
class BasemapSettingsState internal constructor(
    onlineOpen: MutableState<Boolean>,
    offlineOpen: MutableState<Boolean>,
) {
    var onlineOpen by onlineOpen
    var offlineOpen by offlineOpen
    var creatingComposite by mutableStateOf(false)
    var editingComposite by mutableStateOf<CompositeEntity?>(null)
    /** Le fond affiche ne se telecharge pas : la boite qui le dit, en le nommant. */
    var downloadRefused by mutableStateOf(false)
    /**
     * Le fond dont la bulle est ouverte, ou null. Un seul a la fois : en ouvrir une autre ferme la
     * precedente, comme la bulle des points d'interet se ferme quand on ouvre autre chose.
     */
    var bubbleProviderId by mutableStateOf<String?>(null)
    internal var importProviders: () -> Unit = {}
    internal var exportProviders: () -> Unit = {}
    internal var importMbtiles: () -> Unit = {}
    internal var downloadArea: () -> Unit = {}
}

@Composable
internal fun rememberBasemapSettings(
    vm: SettingsViewModel,
    onPickMbtiles: () -> Unit,
    onDownloadArea: () -> Unit,
): BasemapSettingsState {
    val onlineOpen = rememberSaveable { mutableStateOf(false) }
    val offlineOpen = rememberSaveable { mutableStateOf(false) }
    val state = remember { BasemapSettingsState(onlineOpen, offlineOpen) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportProviders(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.requestImportProviders(it) }
    }
    state.importProviders = { importLauncher.launch("application/json") }
    state.exportProviders = { exportLauncher.launch("tunturo_providers.json") }
    state.importMbtiles = onPickMbtiles
    state.downloadArea = onDownloadArea
    return state
}

/** Les boites des fonds de plan, posees par l'ecran (cf. [BasemapSettingsState]). */
@Composable
internal fun BasemapSettingsDialogs(
    state: BasemapSettingsState, cur: SettingsEntity,
    providers: List<ProviderEntity>, composites: List<CompositeEntity>, vm: SettingsViewModel,
) {
    /*
     * Pas de rond d'attente ici, et c'est une mesure qui l'a tranche : ouvrir le composeur est de la
     * COMPOSITION, pas une attente - le fil d'affichage est occupe, aucune image n'est produite, et un
     * temoin pose la resterait fige. Il ne dirait rien que l'ecran fige ne dise deja.
     */
    if (state.creatingComposite) {
        CompositeEditorDialog(null, providers,
            onSave = { vm.saveComposite(it); state.creatingComposite = false },
            onDismiss = { state.creatingComposite = false })
    }
    state.editingComposite?.let { c ->
        CompositeEditorDialog(c, providers,
            onSave = { vm.saveComposite(it); state.editingComposite = null },
            onDismiss = { state.editingComposite = null })
    }
    if (state.downloadRefused) {
        val nom = basemapLabel(cur.defaultBasemapId, providers, composites)
        AlertDialog(
            onDismissRequest = { state.downloadRefused = false },
            title = { Text(stringResource(R.string.offline_action_download)) },
            text = {
                Text(
                    if (nom.isBlank()) stringResource(R.string.offline_unavailable)
                    else stringResource(R.string.offline_unavailable_named, nom),
                )
            },
            confirmButton = {
                TextButton(onClick = { state.downloadRefused = false }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
    val pendingImport by vm.pendingProvidersImport.collectAsState()
    pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.cancelImportProviders() },
            title = { Text(stringResource(R.string.dialog_import_providers_title)) },
            text = { Text(stringResource(R.string.dialog_import_providers_text, pending.size)) },
            confirmButton = { TextButton(onClick = { vm.confirmImportProviders() }) { Text(stringResource(R.string.action_import)) } },
            dismissButton = { TextButton(onClick = { vm.cancelImportProviders() }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * Les deux lignes des fonds de plan, en blocs de l'onglet Carte : la ligne, l'en-tete collant de ses
 * gestes, une ligne par fond, et la fin de la section collante.
 */
internal fun SettingsTabScope.basemapBlocs(
    cur: SettingsEntity, providers: List<ProviderEntity>, composites: List<CompositeEntity>,
    vm: SettingsViewModel, state: BasemapSettingsState,
) {
    // Le Relief (type DEM) est un fournisseur standard (URL, activation, edition) : il suit les memes regles
    // que les autres et figure donc parmi les cartes en ligne, pas parmi les fonds MBTILES.
    val online = providers.filter { it.type != "MBTILES" }
    val mbtiles = providers.filter { it.type == "MBTILES" }

    // ---- Cartes en ligne : le catalogue, que l'on rouvre le plus souvent (activer un fond, une cle d'API).
    bloc(key = "basemaps-online") {
        CardSegment(top = true, bottom = !state.onlineOpen || online.isEmpty()) {
            SetRow(
                stringResource(R.string.settings_line_online_maps),
                onClick = { state.onlineOpen = !state.onlineOpen }, role = Role.Button,
            ) {
                /*
                 * Ouverte, la ligne porte ses deux gestes, a la hauteur d'une ligne ordinaire : ils
                 * avaient un en-tete a eux, une bande de plus entre la ligne et les fonds.
                 */
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.onlineOpen) {
                        // La fleche montante charge dans l'application (importer), la descendante en sort
                        // un fichier (exporter) : meme sens que dans le tiroir principal.
                        CompactHeaderAction(Icons.Filled.FileUpload, stringResource(R.string.action_import)) { state.importProviders() }
                        CompactHeaderAction(Icons.Filled.FileDownload, stringResource(R.string.action_export)) { state.exportProviders() }
                    }
                    AccordionChevron(state.onlineOpen)
                }
            }
        }
    }
    if (state.onlineOpen) {
        online.forEachIndexed { i, p ->
            bloc(key = "basemaps-online-${p.id}") {
                CardSegment(top = false, bottom = i == online.lastIndex) {
                    AccordionItem {
                        ProviderRow(
                            p, bubbleOpen = state.bubbleProviderId == p.id,
                            onOpenBubble = { state.bubbleProviderId = p.id },
                            onCloseBubble = { if (state.bubbleProviderId == p.id) state.bubbleProviderId = null },
                            onSave = vm::saveProvider,
                        )
                    }
                }
            }
        }
    }

    // ---- Cartes hors ligne : ce qu'on a soi-meme apporte - importe, telecharge ou compose.
    bloc(key = "basemaps-offline") {
        Column {
            Spacer(Modifier.height(MapCardGap))
            CardSegment(top = true, bottom = !state.offlineOpen) {
                SetRow(
                    stringResource(R.string.settings_line_offline_maps),
                    onClick = { state.offlineOpen = !state.offlineOpen }, role = Role.Button,
                ) { AccordionChevron(state.offlineOpen) }
            }
        }
    }
    if (state.offlineOpen) {
        val empty = mbtiles.isEmpty() && composites.isEmpty()
        enTeteCollant("basemaps-offline-header") {
            CardSegment(top = false, bottom = false) {
                // Les trois gestes repartis sur toute la largeur, a egale distance.
                AccordionHeader(arrangement = Arrangement.SpaceEvenly) {
                    HeaderAction(painterResource(R.drawable.ic_settings_import), stringResource(R.string.action_import)) {
                        state.importMbtiles()
                    }
                    // Un fond qu'on ne peut pas telecharger le dit, en se NOMMANT, plutot que d'ouvrir un
                    // cadrage qui n'aboutira a rien.
                    HeaderAction(rememberVectorPainter(Icons.Outlined.CloudDownload), stringResource(R.string.offline_action_download)) {
                        if (offlineDownloadAvailable(cur.defaultBasemapId, providers)) state.downloadArea()
                        else state.downloadRefused = true
                    }
                    HeaderAction(painterResource(R.drawable.ic_settings_layers), stringResource(R.string.action_create_composite)) {
                        state.creatingComposite = true
                    }
                }
            }
        }
        if (empty) {
            bloc(key = "basemaps-offline-empty") {
                CardSegment(top = false, bottom = true) {
                    AccordionItem {
                        Spacer(Modifier.height(10.dp))
                        Hint(stringResource(R.string.settings_offline_maps_empty))
                    }
                }
            }
        }
        val mbtilesDirPath = cur.mbtilesDir.ifBlank { null }
        mbtiles.forEachIndexed { i, p ->
            bloc(key = "basemaps-offline-${p.id}") {
                val ctx = LocalContext.current
                // Dossier reel des .mbtiles (miroir de TunturoRepository.mbtilesDir) : affiche dans l'editeur.
                val dir = mbtilesDirPath ?: File(ctx.filesDir, "mbtiles").absolutePath
                CardSegment(top = false, bottom = composites.isEmpty() && i == mbtiles.lastIndex) {
                    AccordionItem {
                        val onDelete: (() -> Unit)? = if (!p.builtin) { { vm.deleteProvider(p) } } else null
                        ProviderRow(
                            p, bubbleOpen = state.bubbleProviderId == p.id,
                            onOpenBubble = { state.bubbleProviderId = p.id },
                            onCloseBubble = { if (state.bubbleProviderId == p.id) state.bubbleProviderId = null },
                            onSave = vm::saveProvider, onDelete = onDelete, mbtilesDirPath = dir,
                        )
                    }
                }
            }
        }
        composites.forEachIndexed { i, c ->
            bloc(key = "basemaps-composite-${c.id}") {
                CardSegment(top = false, bottom = i == composites.lastIndex) {
                    AccordionItem {
                        val bubbleKey = "composite-${c.id}"
                        CompositeRow(
                            c, providers, bubbleOpen = state.bubbleProviderId == bubbleKey,
                            onOpenBubble = { state.bubbleProviderId = bubbleKey },
                            onCloseBubble = { if (state.bubbleProviderId == bubbleKey) state.bubbleProviderId = null },
                            onToggle = { vm.saveComposite(c.copy(enabled = it)) },
                            onEdit = { state.bubbleProviderId = null; state.editingComposite = c },
                            onDelete = { state.bubbleProviderId = null; vm.deleteComposite(c) },
                        )
                    }
                }
            }
        }
        finCollant("basemaps-offline-end")
    }
}

/** L'en-tete d'un accordeon a gestes : un filet, puis les gestes, cales a droite par defaut. */
@Composable
private fun ColumnScopeMarker.AccordionHeader(
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp, Alignment.End),
    actions: @Composable RowScope.() -> Unit,
) {
    Column(Modifier.padding(start = AccordionIndent)) {
        RowDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = arrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Une ligne d'accordeon, quand elle est son propre element de liste : en retrait, sous un filet. */
@Composable
private fun ColumnScopeMarker.AccordionItem(content: @Composable ColumnScopeMarker.() -> Unit) {
    Column(Modifier.padding(start = AccordionIndent)) {
        RowDivider()
        ColumnScopeMarker.content()
    }
}

/**
 * Le gestionnaire de couches, en mode expert : une ligne en accordeon, en tete du groupe Presentation de
 * l'onglet Carte. Dessous, la largeur et l'opacite du panneau des fonds, dont les curseurs s'ouvrent en
 * bulle.
 */
@Composable
internal fun ColumnScopeMarker.LayerManagerLine(cur: SettingsEntity, vm: SettingsViewModel) {
    AccordionLine(stringResource(R.string.settings_section_layer_manager)) {
        BubbleValueRow(
            stringResource(R.string.settings_label_panel_width), "${cur.basemapControlWidthPct} %",
            fractionOf(cur.basemapControlWidthPct, 20, 90),
            { vm.save(cur.copy(basemapControlWidthPct = valueOf(it, 20, 90))) },
            tag = "layer_manager_width",
        )
        RowDivider()
        BubbleValueRow(
            stringResource(R.string.settings_label_panel_opacity), "${cur.basemapControlOpacityPct} %",
            fractionOf(cur.basemapControlOpacityPct, 30, 100),
            { vm.save(cur.copy(basemapControlOpacityPct = valueOf(it, 30, 100))) },
            tag = "layer_manager_opacity",
        )
        CardAction(stringResource(R.string.action_reset_defaults)) {
            vm.save(cur.copy(basemapControlWidthPct = 70, basemapControlOpacityPct = 90))
        }
    }
}

/**
 * Ligne d'un composite : meme grammaire qu'un fond (cf. ProviderRow) - symbole, nom, interrupteur. Un tap
 * ouvre sa bulle : ses deux couches et l'opacite, puis Editer, Supprimer et (Des)activer.
 */
@Composable private fun ColumnScopeMarker.CompositeRow(
    c: CompositeEntity, providers: List<ProviderEntity>,
    bubbleOpen: Boolean, onOpenBubble: () -> Unit, onCloseBubble: () -> Unit,
    onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenBubble)
                .testTag("composite_row_${c.id}")
                .defaultMinSize(minHeight = 46.dp).padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_settings_layers), null, Modifier.size(16.dp), tint = settingsPalette.subtle)
            Text(c.name, style = MaterialTheme.typography.bodyMedium, color = settingsPalette.label, modifier = Modifier.weight(1f))
            SettingsSwitch(c.enabled, onToggle)
        }
        if (bubbleOpen) {
            RowBubble(onDismiss = onCloseBubble, modifier = Modifier.testTag("basemap_bubble")) {
                val background = providers.firstOrNull { it.id == c.backgroundProviderId }
                val foreground = providers.firstOrNull { it.id == c.foregroundProviderId }
                BasemapSummaryLayout(
                    name = c.name,
                    thumbnail = { CompositeThumbnail(background, foreground, c.foregroundOpacity) },
                    description = stringResource(
                        R.string.basemap_composite_desc,
                        foreground?.name ?: c.foregroundProviderId, background?.name ?: c.backgroundProviderId,
                        (c.foregroundOpacity * 100).roundToInt(),
                    ),
                    enabled = c.enabled, onEdit = onEdit, onDelete = onDelete, onToggle = { onToggle(!c.enabled) },
                )
            }
        }
    }
}

@Composable private fun CompositeEditorDialog(
    existing: CompositeEntity?, providers: List<ProviderEntity>,
    onSave: (CompositeEntity) -> Unit, onDismiss: () -> Unit,
) {
    // Le relief peut être utilisé en overlay (premier plan, ex. superposé à une carte pays) mais jamais
    // comme arrière-plan (tuiles DEM brutes illisibles seules, cf. StyleBuilder).
    val bgSelectable = providers.filter { it.type != "DEM" }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var opacityPct by remember { mutableIntStateOf(((existing?.foregroundOpacity ?: 0.5f) * 100).toInt()) }
    var fgId by remember { mutableStateOf(existing?.foregroundProviderId ?: providers.firstOrNull()?.id ?: "") }
    var bgId by remember { mutableStateOf(existing?.backgroundProviderId ?: bgSelectable.firstOrNull()?.id ?: "") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Meme fond et memes cartes que l'ecran des reglages (cf. la popup des fournisseurs).
        Surface(
            // Hauteur libre, bornee a ce que l'ecran offre : la popup ne porte que quatre champs, lui
            // en reserver 80 % laissait un grand vide sous le dernier.
            modifier = Modifier.fillMaxWidth(0.86f).heightIn(max = 620.dp),
            shape = MaterialTheme.shapes.extraLarge, color = settingsPalette.screen,
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(if (existing == null) R.string.dialog_composite_title
                    else R.string.dialog_composite_edit_title), style = MaterialTheme.typography.titleLarge,
                    color = settingsPalette.label)
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    FieldRow(stringResource(R.string.field_composite_name)) {
                        SettingsTextField(name, "") { name = it }
                    }
                    RowDivider()
                    SliderRow(
                        stringResource(R.string.settings_label_opacity), "$opacityPct %",
                        fractionOf(opacityPct, 0, 100),
                        { opacityPct = valueOf(it, 0, 100) },
                    )
                    RowDivider()
                    // Les deux couches suivent les deux premiers reglages, dans la meme carte et sous la
                    // meme legende : ce sont quatre champs d'un meme objet, non deux paires a rapprocher.
                    FieldRow(stringResource(R.string.label_foreground_layer), Icons.Filled.FlipToFront) {
                        LayerSelect(providers, fgId) { fgId = it }
                    }
                    RowDivider()
                    FieldRow(stringResource(R.string.label_background_layer), Icons.Filled.FlipToBack) {
                        LayerSelect(bgSelectable, bgId) { bgId = it }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (name.isNotBlank() && fgId.isNotBlank() && bgId.isNotBlank()) {
                            onSave(CompositeEntity(
                                id = existing?.id ?: 0, name = name,
                                backgroundProviderId = bgId, foregroundProviderId = fgId,
                                foregroundOpacity = opacityPct / 100f, enabled = existing?.enabled ?: true,
                                sortOrder = existing?.sortOrder ?: CompositeSortOrder, folderId = existing?.folderId,
                            ))
                        }
                    }) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/** Choix d'une couche : le cadre de champ de l'ecran, et le menu des fonds sous lui. */
@Composable private fun LayerSelect(items: List<ProviderEntity>, current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = items.firstOrNull { it.id == current }?.name ?: current
    Box {
        Row(
            fieldBoxModifier().clickable { open = true }.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = settingsPalette.label, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(16.dp), tint = settingsPalette.subtle)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { p -> DropdownMenuItem(text = { ProviderOptionLabel(p) }, onClick = { onSelect(p.id); open = false }) }
        }
    }
}

/** Libellé d'un fond dans un select : drapeau pour un fond national détecté + nom de la couche. */
@Composable private fun ProviderOptionLabel(p: ProviderEntity) {
    val code = flagCodeFor(p)
    if (code != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = flagAssetModel(code), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(p.name)
        }
    } else {
        Text(p.name)
    }
}

/** Miniature titrée (SPEC section 6), affichée en lecture seule côte à côte dans l'éditeur d'un MBTILES, avec
 *  un bouton d'agrandissement qui ouvre l'image en grand (~80 % de l'écran). */
@Composable private fun ThumbColumn(title: String, file: File, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val model = "file://${file.absolutePath}"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = settingsPalette.subtle)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth()) {
            AsyncImage(
                model = model, contentDescription = title, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall),
            )
            ThumbOverlayButton(Icons.Filled.Fullscreen, stringResource(R.string.offline_thumb_expand),
                Modifier.align(Alignment.TopEnd).padding(4.dp)) { expanded = true }
        }
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f)) {
                Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.small, color = settingsPalette.card) {
                    AsyncImage(
                        model = model, contentDescription = title, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
                ThumbOverlayButton(Icons.Filled.FullscreenExit, stringResource(R.string.action_close),
                    Modifier.align(Alignment.TopEnd).padding(8.dp)) { expanded = false }
            }
        }
    }
}

/** Bouton d'icône noire sur fond opaque à 30 %, superposé au coin d'une miniature. */
@Composable private fun ThumbOverlayButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(28.dp).background(smallButtonBackground(translucent = true), MaterialTheme.shapes.extraSmall),
    ) {
        Icon(icon, desc, tint = smallButtonContent(), modifier = Modifier.size(18.dp))
    }
}

/**
 * Un geste d'en-tete a la hauteur d'une ligne : l'icone sur son libelle, comme [HeaderAction], resserre
 * dans les 32 dp que laisse une ligne ordinaire - pose dans la ligne "Cartes en ligne", il ne doit pas la
 * faire grandir.
 */
@Composable
private fun CompactHeaderAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val color = settingsPalette.accent
    Column(
        Modifier.width(56.dp).height(32.dp).clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .testTag("header_action_$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.sp, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Ligne d'un fond dans une carte de reglages : drapeau, nom, interrupteur. Supprimer un fond importe se
 * fait depuis sa bulle.
 *
 * **Un tap sur la ligne ouvre sa bulle** (cf. [BasemapBubble]) : l'apercu du fond, ce qu'il est, et ses
 * reglages. Le crayon qui depliait l'editeur sous la ligne a disparu - il allongeait la liste de dix champs,
 * au milieu des autres fonds.
 */
@Composable private fun ColumnScopeMarker.ProviderRow(
    p: ProviderEntity, bubbleOpen: Boolean, onOpenBubble: () -> Unit, onCloseBubble: () -> Unit,
    onSave: (ProviderEntity) -> Unit, onDelete: (() -> Unit)? = null,
    mbtilesDirPath: String? = null,
) {
    val flagCode = flagCodeFor(p)
    // La bulle se pose par rapport a cette boite : sous la ligne ou au-dessus (cf. RowBubble).
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenBubble)
                .testTag("provider_row_${p.id}")
                .defaultMinSize(minHeight = 46.dp).padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (flagCode != null) {
                AsyncImage(model = flagAssetModel(flagCode), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(18.dp, 13.dp).clip(RoundedCornerShape(3.dp)))
            }
            // Le nom seul : le type du fond (XYZ, VECTOR...) ne disait rien a qui choisit une carte.
            Text(p.name, style = MaterialTheme.typography.bodyMedium, color = settingsPalette.label, modifier = Modifier.weight(1f))
            SettingsSwitch(p.enabled) { onSave(p.copy(enabled = it)) }
        }
        if (bubbleOpen) {
            RowBubble(onDismiss = onCloseBubble, modifier = Modifier.testTag("basemap_bubble")) {
                BasemapBubble(
                    p, onSave = onSave, mbtilesDirPath = mbtilesDirPath,
                    // Supprimer ferme la bulle : elle decrivait un fond qui n'existe plus.
                    onDelete = onDelete?.let { delete -> { onCloseBubble(); delete() } },
                )
            }
        }
    }
}

/** Les deux vues de la bulle d'un fond : son apercu, ou son editeur. */
internal enum class BasemapBubbleView { SUMMARY, EDIT }

/**
 * Le contenu de la bulle d'un fond : par defaut son apercu - miniature, nom, description, et les boutons
 * "Editer" et "Activer" -, et apres "Editer" son editeur, sous un en-tete a bouton retour.
 */
@Composable private fun BasemapBubble(
    p: ProviderEntity, onSave: (ProviderEntity) -> Unit, mbtilesDirPath: String?, onDelete: (() -> Unit)?,
) {
    var view by remember(p.id) { mutableStateOf(BasemapBubbleView.SUMMARY) }
    when (view) {
        BasemapBubbleView.SUMMARY -> BasemapSummaryLayout(
            name = p.name, thumbnail = { BasemapThumbnail(p) }, description = basemapDescription(p),
            enabled = p.enabled, onEdit = { view = BasemapBubbleView.EDIT }, onDelete = onDelete,
            onToggle = { onSave(p.copy(enabled = !p.enabled)) },
        )
        BasemapBubbleView.EDIT -> BasemapEditor(
            p, onBack = { view = BasemapBubbleView.SUMMARY },
            onSave = { onSave(it); view = BasemapBubbleView.SUMMARY },
            mbtilesDirPath = mbtilesDirPath,
        )
    }
}

/** Cote de la miniature d'un fond, dans sa bulle. */
private val BasemapThumbnailSize = 64.dp

/**
 * L'apercu d'un fond dans sa bulle, le meme pour tous - en ligne, hors ligne, composite : la miniature et
 * le nom cote a cote, la description, puis les boutons. Supprimer ne parait que pour ce qui se supprime :
 * un fond importe ou telecharge, un composite.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun BasemapSummaryLayout(
    name: String, thumbnail: @Composable () -> Unit, description: String,
    enabled: Boolean, onEdit: () -> Unit, onDelete: (() -> Unit)?, onToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("basemap_bubble_summary"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            thumbnail()
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Trois boutons ne tiennent pas toujours sur une ligne, en allemand surtout : ils passent alors a la
        // suivante plutot que de se tasser.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            if (onDelete != null) {
                OutlinedButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            // "Activer" range le fond parmi ceux du gestionnaire, comme l'interrupteur de sa ligne.
            Button(onClick = onToggle) {
                Text(stringResource(if (enabled) R.string.action_deactivate else R.string.action_activate))
            }
        }
    }
}

/**
 * La miniature d'un fond : une tuile prise a un endroit representatif (cf. [BasemapPreview]), ou l'apercu
 * precis d'une carte telechargee - celui que montre son editeur. Le relief, qui ne se montre pas en une
 * tuile, porte son icone ; les autres fonds sans image (vectoriels) laissent le cadre nu.
 */
@Composable private fun BasemapThumbnail(p: ProviderEntity) {
    val model = rememberThumbnailModel(p)
    ThumbnailFrame {
        if (p.type == "DEM") Icon(Icons.Filled.Terrain, null, Modifier.size(24.dp), tint = settingsPalette.subtle)
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize())
        }
    }
}

/**
 * La miniature d'un composite : ses deux couches superposees, l'arriere-plan dessous et le premier plan
 * dessus, a l'opacite du composite - ce qu'on verra sur la carte.
 */
@Composable private fun CompositeThumbnail(background: ProviderEntity?, foreground: ProviderEntity?, foregroundOpacity: Float) {
    val backgroundModel = background?.let { rememberThumbnailModel(it) }
    val foregroundModel = foreground?.let { rememberThumbnailModel(it) }
    ThumbnailFrame {
        if (backgroundModel == null && foregroundModel == null) {
            Icon(painterResource(R.drawable.ic_settings_layers), null, Modifier.size(24.dp), tint = settingsPalette.subtle)
        }
        if (backgroundModel != null) {
            AsyncImage(model = backgroundModel, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize())
        }
        if (foregroundModel != null) {
            AsyncImage(model = foregroundModel, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().alpha(foregroundOpacity.coerceIn(0f, 1f)))
        }
    }
}

/** Ce que la miniature d'un fond charge : son apercu enregistre, sa tuile d'apercu, ou rien. */
@Composable private fun rememberThumbnailModel(p: ProviderEntity): Any? {
    val ctx = LocalContext.current
    return remember(p.id, p.urlTemplate, p.apiKey, p.minZoom, p.maxZoom) {
        if (p.type == "MBTILES") {
            OfflineThumbnails.files(ctx, p.urlTemplate).second.takeIf { it.exists() }?.let { "file://${it.absolutePath}" }
        } else {
            BasemapPreview.tile(p)?.let { tile ->
                // Les serveurs de tuiles demandent qu'on se presente : le meme nom que pour le telechargement.
                ImageRequest.Builder(ctx)
                    .data(TileUrl.build(p, tile.x, tile.y, tile.z))
                    .httpHeaders(NetworkHeaders.Builder().set("User-Agent", TileHttp.USER_AGENT).build())
                    .build()
            }
        }
    }
}

/** Le cadre d'une miniature de bulle : un carre aux coins arrondis, sur le fond des champs. */
@Composable private fun ThumbnailFrame(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        Modifier.size(BasemapThumbnailSize).clip(MaterialTheme.shapes.small).background(settingsPalette.fieldBg)
            .testTag("basemap_thumbnail"),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Ce qu'est un fond, en quelques mots : sa nature, ce qu'il couvre, ses niveaux de zoom. */
@Composable private fun basemapDescription(p: ProviderEntity): String {
    val kind = BasemapPreview.kind(p)
    val locale = LocalConfiguration.current.locales[0]
    val parts = mutableListOf(
        stringResource(when (kind) {
            BasemapPreview.Kind.RASTER -> R.string.basemap_kind_raster
            BasemapPreview.Kind.VECTOR -> R.string.basemap_kind_vector
            BasemapPreview.Kind.OVERLAY -> R.string.basemap_kind_overlay
            BasemapPreview.Kind.RELIEF -> R.string.basemap_kind_relief
            BasemapPreview.Kind.OFFLINE -> R.string.basemap_kind_offline
        }),
    )
    if (kind == BasemapPreview.Kind.RASTER || kind == BasemapPreview.Kind.VECTOR) {
        val country = flagCodeFor(p)?.let { Locale.Builder().setRegion(it.uppercase()).build().getDisplayCountry(locale) }
        when {
            country != null -> parts += stringResource(R.string.basemap_coverage_country, country)
            p.groupName == "Monde" -> parts += stringResource(R.string.basemap_coverage_world)
        }
    }
    // Un fond vectoriel decrit ses niveaux dans son style, pas dans sa fiche : rien de juste a en dire. Une
    // carte telechargee dit les niveaux qu'elle contient.
    when (kind) {
        BasemapPreview.Kind.VECTOR -> Unit
        BasemapPreview.Kind.OFFLINE -> parts += stringResource(R.string.basemap_zoom_downloaded, p.minZoom, p.maxZoom)
        else -> parts += stringResource(R.string.basemap_zoom_range, p.minZoom, p.maxZoom)
    }
    return parts.joinToString(". ", postfix = ".")
}

/**
 * L'editeur d'un fond, dans sa bulle : un en-tete - le retour et le nom de la carte -, puis l'URL, la cle
 * d'API et la plage de zoom. "Enregistrer" n'apparait qu'une fois un champ change ; revenir sans
 * enregistrer annule.
 */
@Composable private fun BasemapEditor(
    p: ProviderEntity, onBack: () -> Unit, onSave: (ProviderEntity) -> Unit, mbtilesDirPath: String?,
) {
    var name by remember(p.id) { mutableStateOf(p.name) }
    var url by remember(p.id) { mutableStateOf(p.urlTemplate) }
    var key by remember(p.id) { mutableStateOf(p.apiKey ?: "") }
    var zoom by remember(p.id) { mutableStateOf(p.minZoom.toFloat()..p.maxZoom.toFloat()) }
    val isMbtiles = p.type == "MBTILES"
    Column(Modifier.fillMaxWidth().testTag("basemap_bubble_editor")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("basemap_bubble_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            with(ColumnScopeMarker) {
                if (isMbtiles) {
                    // Un MBTILES n'a ni URL ni cle : son fichier est fixe. Seul son nom se change.
                    FieldRow(stringResource(R.string.settings_field_name)) { SettingsTextField(name, "") { name = it } }
                } else {
                    FieldRow(stringResource(R.string.settings_field_url)) { SettingsTextField(url, "") { url = it } }
                    FieldRow(stringResource(R.string.settings_field_api_key)) { SettingsTextField(key, "") { key = it } }
                    // Plage de zoom servie par le fond, au curseur double : les niveaux vont de 0 a 22 et se
                    // choisissent l'un par rapport a l'autre. Meme libelle que l'ecran de telechargement.
                    RangeSliderRow(
                        label = stringResource(R.string.offline_config_zoom_label),
                        value = "${zoom.start.toInt()} - ${zoom.endInclusive.toInt()}",
                        range = zoom, bounds = 0f..22f, steps = 21,
                        onRange = { zoom = it },
                    )
                }
                // Force de l'ombrage, propre au relief : enregistree apres un court repos du doigt, chaque
                // valeur reconstruisant le style de la carte.
                if (p.type == "DEM") {
                    var opacity by remember(p.id, p.opacityPct) { mutableFloatStateOf(p.opacityPct.toFloat()) }
                    SliderRow(
                        stringResource(R.string.settings_label_opacity), "${opacity.toInt()} %",
                        fraction = opacity / 100f, onFraction = { opacity = it * 100f },
                    )
                    LaunchedEffect(opacity) {
                        kotlinx.coroutines.delay(200)
                        if (opacity.toInt() != p.opacityPct) onSave(p.copy(opacityPct = opacity.toInt()))
                    }
                    if (p.opacityPct != DefaultDemOpacityPct) {
                        CardAction(stringResource(R.string.action_reset_defaults)) {
                            onSave(p.copy(opacityPct = DefaultDemOpacityPct))
                        }
                    }
                }
                if (isMbtiles) MbtilesDetails(p, mbtilesDirPath)
            }
            val minZoom = zoom.start.toInt()
            val maxZoom = zoom.endInclusive.toInt()
            val dirty = name != p.name || url != p.urlTemplate ||
                key != (p.apiKey ?: "") || minZoom != p.minZoom || maxZoom != p.maxZoom
            if (dirty) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onSave(p.copy(name = name, urlTemplate = url, apiKey = key.ifBlank { null },
                            minZoom = minZoom, maxZoom = maxZoom))
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Ce qu'un MBTILES dit de lui en lecture seule : ses niveaux, son fichier, ses miniatures. */
@Composable private fun MbtilesDetails(p: ProviderEntity, mbtilesDirPath: String?) {
    // Plage de zoom reellement contenue dans le fichier (fixee au telechargement ou a l'import).
    Text(
        stringResource(R.string.settings_field_zoom_levels, p.minZoom, p.maxZoom),
        style = MaterialTheme.typography.bodyMedium, color = settingsPalette.subtle,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
    )
    // Chemin reel du fichier (urlTemplate ne porte que le nom, resolu via le dossier mbtiles).
    val fullPath = when {
        p.urlTemplate.startsWith("mbtiles://") -> p.urlTemplate.removePrefix("mbtiles://")
        mbtilesDirPath != null -> "$mbtilesDirPath/${p.urlTemplate}"
        else -> p.urlTemplate
    }
    Text(
        stringResource(R.string.settings_field_mbtiles_location, fullPath),
        style = MaterialTheme.typography.bodySmall, color = settingsPalette.subtle,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
    // Miniatures generees a la fin du telechargement (SPEC section 6), affichees si presentes.
    val ctx = LocalContext.current
    val (locationFile, detailFile) = remember(p.id) { OfflineThumbnails.files(ctx, p.urlTemplate) }
    if (locationFile.exists() || detailFile.exists()) {
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (locationFile.exists()) {
                ThumbColumn(stringResource(R.string.offline_thumb_location_title), locationFile, Modifier.fillMaxWidth(0.6f))
            }
            if (detailFile.exists()) {
                ThumbColumn(stringResource(R.string.offline_thumb_detail_title), detailFile, Modifier.fillMaxWidth())
            }
        }
    }
}
