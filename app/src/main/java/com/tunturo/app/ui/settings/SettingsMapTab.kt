package com.tunturo.app.ui.settings

import com.tunturo.app.ui.theme.brandedText
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.tunturo.app.R
import com.tunturo.app.ui.alert.DashboardField
import com.tunturo.app.ui.alert.DashboardFontDefaultSp
import com.tunturo.app.ui.alert.DashboardFontMaxSp
import com.tunturo.app.ui.alert.DashboardFontMinSp
import com.tunturo.app.data.db.MaxGpsMarkerSizeDp
import com.tunturo.app.data.db.MaxMapButtonSizeDp
import com.tunturo.app.data.db.MaxOffTrackAlertM
import com.tunturo.app.data.db.MinGpsMarkerSizeDp
import com.tunturo.app.data.db.MinMapButtonSizeDp
import com.tunturo.app.data.db.MinOffTrackAlertM
import com.tunturo.app.data.db.MaxTrackLineWidthDp
import com.tunturo.app.data.db.MinTrackLineWidthDp
import com.tunturo.app.data.db.OffTrackAlertStepM
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.data.db.CompositeEntity
import com.tunturo.app.data.db.ProviderEntity
import com.tunturo.app.domain.model.RoutingProfile
import com.tunturo.app.domain.model.BubblePosition
import com.tunturo.app.domain.model.GpsMarkerStyle
import com.tunturo.app.location.AlertWakeScreen
import com.tunturo.app.location.alertSoundTitle
import com.tunturo.app.ui.components.ColorPickerDialog
import kotlinx.coroutines.launch

/**
 * L'onglet Carte : ce qui se voit sur la carte et ce qui s'y allume.
 *
 * Les fonctions qui ne servent qu'a lui vivent avec lui - l'alerte d'eloignement, le repere de position,
 * la position des infobulles.
 */

/**
 * Onglet "Carte" : ce qui s'affiche sur la carte et par-dessus elle.
 *
 * Les groupes, dans l'ordre ou l'on decouvre la carte : ce qui s'y commande, le repere de position,
 * le dessin des traces, le tableau de bord, les points d'interet, puis ce qui decrit le relief parcouru.
 * Tous portent un titre de groupe, y compris le premier. "Affichage des boutons" reunit les interrupteurs
 * de la carte sous une seule rubrique sans titre ; l'affichage des boutons de carte (fonds, echelle,
 * rotation) n'apparait qu'en mode expert.
 *
 * Les libelles des interrupteurs ont perdu leur "Afficher" : le repeter a chaque ligne ne disait rien de
 * plus que le titre de groupe.
 */
/**
 * L'onglet Carte, en elements de liste.
 *
 * **Chaque bloc est un element** (cf. [SettingsTabScope]) : la liste ne compose que ce qui se voit, la
 * ou une colonne unique composait les cinq cents lignes de l'onglet en une seule image - pres d'une
 * seconde d'ecran fige a chaque ouverture.
 *
 * Les blocs qui OUVRENT un groupe le disent en passant leur titre : c'est ainsi que la barre de groupe
 * sait, a l'indice de l'element en tete, dans quel groupe on se trouve. Rien n'est a tenir a jour a
 * cote - l'indice se compte ici meme, au fil des blocs.
 */
internal fun LazyListScope.mapTab(
    cur: SettingsEntity, vm: SettingsViewModel, groupes: TabGroups,
    providers: List<ProviderEntity>, composites: List<CompositeEntity>, basemaps: BasemapSettingsState,
    /** On arrive pour les zones de l'itineraire hors ligne : le calcul d'itineraire s'ouvre d'office. */
    revealRoutingZones: Boolean = false,
) {
    val onglet = SettingsTabScope(this, groupes)
    onglet.run {
    // Les fonds de plan d'abord, en ligne puis hors ligne, sans titre au-dessus : c'est ce qu'on vient
    // chercher le plus souvent dans cet onglet (cf. basemapBlocs).
    basemapBlocs(cur, providers, composites, vm, basemaps)

    /*
     * Hors mode expert, ni groupe ni rubrique : une carte par bouton de la carte, espacees, et le repere de
     * position a la suite. Ce sont des interrupteurs qui se lisent d'un coup d'oeil ; des titres au-dessus
     * de six lignes ne disaient rien de plus qu'elles.
     *
     * En mode expert, la structure en groupes reste : ses rubriques fines en ont besoin pour se retrouver.
     *
     * Le suivi de la carte ("Suivre ma position") a quitte la liste sans retour : il se commande depuis la
     * carte, au bouton a trois etats du coin bas-droit (cf. MapBottomRightControls).
     */
    if (!cur.expertMode) {
        bloc {
            Column {
                Spacer(Modifier.height(MapCardGap))
                MapButtonCards(cur, vm, revealRoutingZones)
            }
        }
        bloc {
            Spacer(Modifier.height(MapCardGap))
            GpsMarkerSettings(cur, vm, expert = false)
        }
        publier()
        return
    }

    bloc(R.string.settings_section_map_controls) {
        GroupTitle(stringResource(R.string.settings_section_map_controls))
        SettingsCard {
            mapButtonRows(cur, vm, revealRoutingZones).forEachIndexed { index, row ->
                if (index > 0) RowDivider()
                row()
            }
        }
    }

    /*
     * Presentation : ce qui se dessine sur la carte sans etre un bouton - l'echelle, la rotation, et
     * le trait des traces. La trace etait une rubrique a elle, d'une seule ligne.
     */
    bloc(R.string.settings_section_map_dressing) {
        GroupTitle(stringResource(R.string.settings_section_map_dressing))
        SettingsCard {
            // Le gestionnaire de couches en tete : le panneau des fonds est ce qui se pose le plus sur la carte.
            LayerManagerLine(cur, vm)
            RowDivider()
            // Le bouton du gestionnaire de fonds n'est plus un interrupteur d'ici : il suit le mode d'acces
            // aux cartes, choisi a cote de celui du menu lateral (onglet Systeme, Interaction).
            SwitchLine(stringResource(R.string.settings_sw_scale), cur.showScale) { vm.save(cur.copy(showScale = it)) }
            RowDivider()
            SwitchLine(stringResource(R.string.settings_sw_rotation), cur.rotateGesturesEnabled) { vm.save(cur.copy(rotateGesturesEnabled = it)) }
            RowDivider()
            // Comment les traces et l'itineraire se dessinent : le trait de chaque trace garde sa couleur,
            // reglee dans le menu lateral ; ce qui est reglable ici vaut pour toutes.
            AccordionLine(stringResource(R.string.settings_group_track)) {
                StepperLine(
                    stringResource(R.string.settings_track_line_width), cur.trackLineWidth,
                    MinTrackLineWidthDp, MaxTrackLineWidthDp,
                    info = stringResource(R.string.settings_track_line_width_info),
                ) { vm.save(cur.copy(trackLineWidth = it)) }
                RowDivider()
                // Les chevrons du sens de parcours, eteints par defaut.
                SwitchLine(
                    stringResource(R.string.settings_track_direction), cur.trackDirectionArrows,
                    info = stringResource(R.string.settings_track_direction_info),
                ) { vm.save(cur.copy(trackDirectionArrows = it)) }
                RowDivider()
                // Venue de l'onglet Systeme : c'est le dessin des traces qu'elle allege, pas l'application.
                SwitchLine(
                    stringResource(R.string.settings_simplify_render), cur.simplifyRender,
                    // L'explication courte et la longue, ensemble derriere le "i" : ni l'une ni l'autre ne dit un etat.
                    info = stringResource(R.string.settings_sw_simplify_sub) + "\n\n" + stringResource(R.string.settings_simplify_render_hint),
                ) { vm.save(cur.copy(simplifyRender = it)) }
            }
        }
    }

    // GPS : deux lignes, le recentrage a l'allumage puis le repere qu'on voit.
    bloc(R.string.settings_group_gps) {
        GpsMarkerSettings(cur, vm, expert = true)
    }

    bloc(R.string.settings_group_pois) {
        SectionTitle(stringResource(R.string.settings_group_pois))
        SettingsCard {
            StepperLine(stringResource(R.string.settings_label_marker_size), cur.markerSize, 16, 80) {
                vm.save(cur.copy(markerSize = it))
            }
            RowDivider()
            AccordionLine(stringResource(R.string.settings_section_bubbles)) {
                StepperLine(stringResource(R.string.settings_font_size), cur.bubbleFont, 7, 28,
                    bold = cur.bubbleBold, onBold = { vm.save(cur.copy(bubbleBold = it)) }) { vm.save(cur.copy(bubbleFont = it)) }
                RowDivider()
                StepperLine(stringResource(R.string.font_title), cur.bubbleTitleFont, 7, 28,
                    bold = cur.bubbleTitleBold, onBold = { vm.save(cur.copy(bubbleTitleBold = it)) }) { vm.save(cur.copy(bubbleTitleFont = it)) }
                RowDivider()
                PickRow(
                    stringResource(R.string.settings_label_bubble_position),
                    BubblePosition.of(cur.bubblePosition), BubblePosition.entries,
                    optionLabel = { bubblePositionLabel(it) },
                ) { vm.save(cur.copy(bubblePosition = it.key)) }
                RowDivider()
                SliderRow(
                    stringResource(R.string.settings_label_opacity), "${cur.bubbleOpacityPct} %",
                    fractionOf(cur.bubbleOpacityPct, 30, 100),
                    { vm.save(cur.copy(bubbleOpacityPct = valueOf(it, 30, 100))) },
                )
                CardAction(stringResource(R.string.action_reset_defaults)) {
                    vm.save(cur.copy(
                        bubbleFont = 14, bubbleTitleFont = 16,
                        bubblePosition = BubblePosition.BOTTOM_LEFT.key, bubbleOpacityPct = 100,
                    ))
                }
            }
        }
    }

    bloc(R.string.settings_group_elevation_profile) {
        SectionTitle(stringResource(R.string.settings_group_elevation_profile))
        ProfileSettings(cur, vm)
    }

        publier()
    }
}

/** L'ecart entre deux cartes posees l'une sous l'autre, sans titre entre elles. */
internal val MapCardGap = 12.dp

/**
 * Les boutons de la carte, hors mode expert : une carte par interrupteur, espacees de [MapCardGap] - rien
 * avant la premiere, rien apres la derniere.
 */
@Composable private fun MapButtonCards(cur: SettingsEntity, vm: SettingsViewModel, revealRoutingZones: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(MapCardGap)) {
        mapButtonRows(cur, vm, revealRoutingZones).forEach { row -> SettingsCard { row() } }
    }
}

/**
 * Les interrupteurs des boutons de la carte, dans l'ordre ou on les lit - une carte chacun hors mode
 * expert, une seule carte en mode expert. Le calcul d'itineraire d'abord, puis ce qui sert pendant la
 * sortie (enregistrement, tableau de bord, points d'interet), puis les outils.
 */
private fun mapButtonRows(
    cur: SettingsEntity, vm: SettingsViewModel, revealRoutingZones: Boolean,
): List<@Composable ColumnScopeMarker.() -> Unit> = listOf(
    { PlannerSwitch(cur, vm, revealRoutingZones) },
    { RecordingSwitch(cur, vm) },
    { DashboardSwitch(cur, vm) },
    { OffTrackAlertSwitch(cur, vm) },
    { PoiSwitch(cur, vm) },
    {
        SwitchLine(stringResource(R.string.settings_sw_geocoding), cur.geocodingEnabled) { vm.save(cur.copy(geocodingEnabled = it)) }
    },
    {
        SwitchLine(stringResource(R.string.settings_sw_measure), cur.trackMeasureEnabled) { vm.save(cur.copy(trackMeasureEnabled = it)) }
    },
    {
        SwitchLine(
            stringResource(R.string.settings_sw_track_edit), cur.trackEditEnabled,
            info = stringResource(R.string.settings_sw_track_edit_sub),
        ) { vm.save(cur.copy(trackEditEnabled = it)) }
    },
)

/**
 * Le calcul d'itineraire : un interrupteur et un chevron, et dessous en accordeon ce qui le regle - la
 * discipline par defaut, les numeros des etapes (mode expert), les zones de l'itineraire hors ligne, et
 * tout en bas la coloration par pente de l'itineraire, venue de l'onglet Parcours.
 */
@Composable private fun ColumnScopeMarker.PlannerSwitch(cur: SettingsEntity, vm: SettingsViewModel, reveal: Boolean) {
    ToggleAccordionLine(
        stringResource(R.string.settings_sw_planner), cur.routePlannerEnabled,
        onEnabledChange = { vm.save(cur.copy(routePlannerEnabled = it)) },
        reveal = reveal,
    ) {
        InCardHeading(stringResource(R.string.settings_section_default_discipline))
        RoutingProfilePicker(RoutingProfile.of(cur.routingProfile)) { vm.save(cur.copy(routingProfile = it.key)) }
        // Libelles A, 1, 2, B devant les etapes du planificateur : mode expert seulement.
        if (cur.expertMode) {
            RowDivider()
            SwitchLine(stringResource(R.string.settings_sw_planner_step_numbers), cur.plannerStepNumbers) {
                vm.save(cur.copy(plannerStepNumbers = it))
            }
        }
        RowDivider()
        OfflineRoutingRows(vm)
        RowDivider()
        /*
         * La coloration par pente de l'itineraire CALCULE : un seul interrupteur, pour son trace sur la carte
         * ET l'aire de son profil - la couleur du trait se lit sur le profil, les deux vont ensemble. Celle
         * des traces se regle trace par trace, dans le menu de chaque couche.
         */
        SwitchLine(
            stringResource(R.string.settings_route_slope_line), cur.routeSlopeLine,
            info = stringResource(R.string.settings_route_slope_info),
        ) { vm.save(cur.copy(routeSlopeLine = it)) }
    }
}

/** Le bouton d'enregistrement de trace sur la carte (cf. recordButtonShown). */
@Composable private fun ColumnScopeMarker.RecordingSwitch(cur: SettingsEntity, vm: SettingsViewModel) {
    SwitchLine(stringResource(R.string.settings_sw_recording), cur.recordingButtonEnabled) {
        vm.save(cur.copy(recordingButtonEnabled = it))
    }
}

/**
 * L'interrupteur du tableau de bord, et en mode expert les champs affiches, en accordeon dessous.
 *
 * Ces reglages formaient un groupe a part, loin de l'interrupteur, et ne disaient rien tant qu'il etait
 * eteint, sinon ou l'allumer. Ils se deplient desormais sous lui, et seulement allume : des reglages qui ne
 * servent a rien valent mieux absents que grises.
 *
 * L'alerte AVANT les champs : c'est elle qu'on vient regler, tandis que les champs se cochent une fois
 * pour toutes.
 */
@Composable private fun ColumnScopeMarker.DashboardSwitch(cur: SettingsEntity, vm: SettingsViewModel) {
    // Le tableau de bord suit la position du telephone : il n'a pas de bouton de localisation a allumer
    // ni a couper, le capteur du telephone en est la seule condition.
    val label = stringResource(R.string.settings_sw_track_follow)
    val onChange: (Boolean) -> Unit = { vm.save(cur.copy(offTrackAlertEnabled = it)) }
    // En mode expert, ses champs se deplient sous lui : une ligne a interrupteur et chevron. Hors mode
    // expert, il n'a rien a deplier, et reste un simple interrupteur.
    if (cur.expertMode) {
        ToggleAccordionLine(label, cur.offTrackAlertEnabled, onEnabledChange = onChange) {
            DashboardFieldRows(cur, vm)
        }
    } else {
        SwitchLine(label, cur.offTrackAlertEnabled, onChange = onChange)
    }
}

/**
 * L'alerte d'eloignement : une ligne a elle, juste apres le tableau de bord - son interrupteur, et ses
 * reglages en accordeon. Elle vivait dans l'accordeon du tableau de bord ; elle a desormais sa carte.
 */
@Composable private fun ColumnScopeMarker.OffTrackAlertSwitch(cur: SettingsEntity, vm: SettingsViewModel) {
    ToggleAccordionLine(
        stringResource(R.string.settings_section_off_track), cur.offTrackAlertActive,
        onEnabledChange = { vm.save(cur.copy(offTrackAlertActive = it)) },
    ) {
        OffTrackAlertRows(cur, vm)
    }
}

/**
 * L'interrupteur des points d'interet, et ce qu'il commande en accordeon dessous : le couloir des traces,
 * le complement OpenStreetMap en mode expert, et l'attribution des donnees.
 *
 * Ces reglages vivaient dans l'onglet Trajets, loin du bouton qu'ils accompagnent. Les categories, elles,
 * se choisissent depuis la carte (cf. PoiFilterBubble).
 */
@Composable private fun ColumnScopeMarker.PoiSwitch(cur: SettingsEntity, vm: SettingsViewModel) {
    // Interrupteur et chevron, comme le calcul d'itineraire : allume, un tap ouvre ou ferme ses reglages.
    ToggleAccordionLine(
        stringResource(R.string.settings_sw_poi), cur.poiEnabled,
        onEnabledChange = { vm.save(cur.copy(poiEnabled = it)) },
        info = stringResource(R.string.settings_sw_poi_sub),
    ) {
        PoiRows(cur, vm)
    }
}

/**
 * Les champs du tableau de bord, un interrupteur chacun, sous un intitule dans la carte. Mode expert
 * seulement : le tableau de bord montre sinon ses champs par defaut.
 */
@Composable private fun ColumnScopeMarker.DashboardFieldRows(cur: SettingsEntity, vm: SettingsViewModel) {
    InCardHeading(stringResource(R.string.settings_section_dashboard_fields))
    val masques = DashboardField.hidden(cur.dashboardHidden)
    val tailles = DashboardField.fontSizes(cur.dashboardFontSizes)
    val libelles = DashboardField.entries.map { stringResource(it.settingsLabel) }
    val moins = stringResource(R.string.action_decrease)
    val plus = stringResource(R.string.action_increase)
    // La largeur de la carte, pour aligner les pas-a-pas d'une ligne a l'autre : c'est la ligne au
    // plus long libelle qui decide pour toutes (cf. alignedStepperMetrics).
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val metrics = alignedStepperMetrics(libelles, maxWidth)
        Column {
            DashboardField.entries.forEachIndexed { i, f ->
                if (i > 0) RowDivider()
                SwitchStepperRow(
                    label = libelles[i],
                    value = tailles[f] ?: DashboardFontDefaultSp,
                    min = DashboardFontMinSp, max = DashboardFontMaxSp,
                    checked = f !in masques, metrics = metrics,
                    decreaseLabel = moins, increaseLabel = plus,
                    onValue = { sp ->
                        vm.save(cur.copy(dashboardFontSizes = DashboardField.withFontSize(cur.dashboardFontSizes, f, sp)))
                    },
                    onToggle = { on ->
                        vm.save(cur.copy(dashboardHidden = DashboardField.withHidden(cur.dashboardHidden, f, hide = !on)))
                    },
                )
            }
        }
    }
    /*
     * Le defaire des champs : les neuf reaffiches, et leurs tailles revenues a celle d'origine. Les deux
     * ensemble et non deux liens - l'un sans l'autre laisserait une moitie de retour en arriere.
     */
    CardAction(stringResource(R.string.action_reset_defaults)) {
        vm.save(cur.copy(dashboardHidden = "", dashboardFontSizes = ""))
    }
}

/**
 * Alerte d'eloignement : l'ecart qui la declenche, et la sonnerie qui l'accompagne. Elle se commande depuis
 * la cloche du tableau de bord, qui ne parait que sur une trace suivie.
 *
 * Le son ne montre son choix que s'il est actif : une ligne de reglage qui ne sert a rien vaut mieux
 * absente que grisee.
 */
@Composable private fun ColumnScopeMarker.OffTrackAlertRows(cur: SettingsEntity, vm: SettingsViewModel) {
    val ctx = LocalContext.current
    var demanderReveil by remember { mutableStateOf(false) }
    // Le selecteur de sonnerie du systeme : c'est lui qui liste les sonneries du telephone et les fait
    // ecouter. En livrer un dans l'application reviendrait a redessiner un ecran que l'utilisateur connait.
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked: Uri? = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        vm.save(cur.copy(offTrackAlertSoundUri = picked?.toString().orEmpty()))
    }
    val soundLabel = remember(cur.offTrackAlertSoundUri) { alertSoundTitle(ctx, cur.offTrackAlertSoundUri) }
    val defaultSoundLabel = stringResource(R.string.settings_off_track_sound_default)

    run {
        // Le curseur va par pas de dix metres : au metre pres, on reglerait la precision du capteur, pas
        // la distance a laquelle on veut etre prevenu.
        SliderRow(
            label = stringResource(R.string.settings_label_off_track_distance),
            value = "${cur.offTrackAlertDistanceM} m",
            fraction = fractionOf(cur.offTrackAlertDistanceM / OffTrackAlertStepM,
                MinOffTrackAlertM / OffTrackAlertStepM, MaxOffTrackAlertM / OffTrackAlertStepM),
            steps = (MaxOffTrackAlertM - MinOffTrackAlertM) / OffTrackAlertStepM - 1,
            onFraction = {
                val steps = valueOf(it, MinOffTrackAlertM / OffTrackAlertStepM, MaxOffTrackAlertM / OffTrackAlertStepM)
                vm.save(cur.copy(offTrackAlertDistanceM = steps * OffTrackAlertStepM))
            },
        )
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_sw_off_track_sound), cur.offTrackAlertSound,
            info = stringResource(R.string.settings_off_track_hint),
        ) { on ->
            vm.save(cur.copy(offTrackAlertSound = on))
            // Le son s'allume : c'est le moment de dire que l'ecran, lui, ne s'allumera pas sans une
            // autorisation qu'Android ne donne plus d'office (cf. [AlertWakeScreen]).
            if (AlertWakeScreen.shouldAsk(on, AlertWakeScreen.granted(ctx))) demanderReveil = true
        }
        if (cur.offTrackAlertSound) {
            RowDivider()
            SetRow(
                stringResource(R.string.settings_label_off_track_sound),
                onClick = { soundPicker.launch(ringtonePickerIntent(ctx, cur.offTrackAlertSoundUri)) },
            ) {
                ValueText(soundLabel ?: defaultSoundLabel)
            }
        }
        RowDivider()
        // La vibration, a part du son : elle se sent la ou la sonnerie ne s'entend pas - vent, torrent,
        // telephone mis en silencieux (cf. AlertVibration).
        SwitchLine(
            stringResource(R.string.settings_sw_off_track_vibrate), cur.offTrackAlertVibrate,
            info = stringResource(R.string.settings_off_track_vibrate_hint),
        ) { on -> vm.save(cur.copy(offTrackAlertVibrate = on)) }
    }
    /*
     * "Pour que l'ecran s'allume, il faut l'autoriser."
     *
     * Le son reste allume quoi qu'on reponde : l'autorisation ne conditionne pas la sonnerie mais le seul
     * reveil de l'ecran, et refuser doit laisser une alerte qui sonne plutot qu'un reglage annule dans le
     * dos de qui vient de l'allumer (cf. AlertWakeScreen).
     */
    if (demanderReveil) {
        AlertDialog(
            onDismissRequest = { demanderReveil = false },
            title = { Text(stringResource(R.string.settings_off_track_wake_title)) },
            text = { Text(brandedText(stringResource(R.string.settings_off_track_wake_text))) },
            confirmButton = {
                TextButton(onClick = { demanderReveil = false; AlertWakeScreen.openSettings(ctx) }) {
                    Text(stringResource(R.string.settings_off_track_wake_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { demanderReveil = false }) {
                    Text(stringResource(R.string.update_action_later))
                }
            },
        )
    }
}

/**
 * Intention du selecteur de sonnerie, limite aux SONNERIES, ouvert sur le son deja retenu.
 *
 * Les sonneries et non les notifications : l'alerte sonne en boucle sur le flux sonnerie du telephone
 * (cf. AlertSound), et un son de notification - une seconde, souvent discret - n'a pas ete concu pour cela.
 * L'ecoute du selecteur passe alors par le meme curseur de volume que l'alerte, et dit donc vrai.
 */
private fun ringtonePickerIntent(ctx: android.content.Context, current: String): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ctx.getString(R.string.settings_label_off_track_sound))
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            current.takeIf { it.isNotBlank() }?.toUri())
    }

/**
 * GPS : en mode expert, une rubrique de deux lignes - recentrer a l'allumage, puis le symbole du repere ;
 * hors mode expert, la seule ligne du symbole, sans titre.
 *
 * Recentrer a l'allumage, ou rester ou l'on regarde : eteint par defaut, "ou suis-je" et "emmene-moi" sont
 * deux questions distinctes. Le ZOOM ne bouge jamais, recentrage demande ou non (cf. LocationControls.startGps).
 *
 * La couleur affichee est la couleur EFFECTIVE - celle reglee, ou celle propre au symbole tant qu'on n'a
 * rien choisi -, si bien que la pastille dit toujours ce qu'on verra sur la carte.
 */
@Composable private fun GpsMarkerSettings(cur: SettingsEntity, vm: SettingsViewModel, expert: Boolean) {
    val marker = GpsMarkerStyle.of(cur.gpsMarkerStyle)
    val color = cur.gpsMarkerColor.takeIf { it.isNotBlank() } ?: marker.defaultColor
    var pickColor by remember { mutableStateOf(false) }
    var pickStyle by remember { mutableStateOf(false) }
    var sizeBubble by remember { mutableStateOf(false) }
    if (expert) SectionTitle(stringResource(R.string.settings_group_gps))
    SettingsCard {
        if (expert) {
            SwitchLine(
                stringResource(R.string.settings_sw_gps_recenter), cur.gpsRecenterOnStart,
                info = stringResource(R.string.settings_sw_gps_recenter_sub),
            ) { vm.save(cur.copy(gpsRecenterOnStart = it)) }
            RowDivider()
        }
        /*
         * Une seule ligne, "Symbole" : son dessin, sa couleur et sa taille au bout, chacun se touchant a part.
         * Trois lignes pour trois proprietes d'un meme repere en faisaient une rubrique ; la taille, qui se
         * regle rarement, ouvre son curseur dans une bulle (cf. [SizeBubble]).
         */
        SetRow(stringResource(R.string.settings_label_gps_marker_style)) {
            // Changer de symbole rend sa couleur ET sa taille au nouveau : chacun a les siennes (bleu et 20 dp
            // pour la puce, rouge et 30 dp pour les fleches, qui doivent montrer une direction), et les heriter
            // du precedent donnerait une fleche bleue et minuscule a qui vient de quitter la puce.
            Box(Modifier.clip(MaterialTheme.shapes.small).clickable { pickStyle = true }) {
                PickValue(gpsMarkerLabel(marker), pickStyle, { pickStyle = false }) {
                    GpsMarkerStyle.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(gpsMarkerLabel(option)) },
                            onClick = {
                                pickStyle = false
                                vm.save(cur.copy(
                                    gpsMarkerStyle = option.key, gpsMarkerColor = "", gpsMarkerSizeDp = option.defaultSizeDp,
                                ))
                            },
                        )
                    }
                }
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable { pickColor = true },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color(color.toColorInt()))
                    .testTag("gps_marker_color"))
            }
            Box {
                Box(Modifier.clip(MaterialTheme.shapes.small).clickable { sizeBubble = true }.padding(horizontal = 4.dp, vertical = 6.dp)) {
                    ValueText("${cur.gpsMarkerSizeDp} dp")
                }
                if (sizeBubble) {
                    ValueBubble(
                        label = stringResource(R.string.settings_label_gps_marker_size),
                        value = "${cur.gpsMarkerSizeDp} dp",
                        fraction = fractionOf(cur.gpsMarkerSizeDp, MinGpsMarkerSizeDp, MaxGpsMarkerSizeDp),
                        steps = MaxGpsMarkerSizeDp - MinGpsMarkerSizeDp - 1,
                        onFraction = { vm.save(cur.copy(gpsMarkerSizeDp = valueOf(it, MinGpsMarkerSizeDp, MaxGpsMarkerSizeDp))) },
                        onDismiss = { sizeBubble = false },
                        tag = "gps_marker_size_bubble",
                    )
                }
            }
        }
    }
    if (pickColor) {
        ColorPickerDialog(
            current = color,
            onPick = { vm.save(cur.copy(gpsMarkerColor = it)); pickColor = false },
            onDismiss = { pickColor = false },
            // Le repere de position n'est pas une trace : toutes les couleurs lui restent permises.
            excludeReserved = false,
        )
    }
}

/** Libelle traduit d'un symbole de position. */
@Composable private fun gpsMarkerLabel(m: GpsMarkerStyle): String = stringResource(
    when (m) {
        GpsMarkerStyle.DOT -> R.string.gps_marker_dot
        GpsMarkerStyle.ARROW_OUTLINE -> R.string.gps_marker_arrow_outline
        GpsMarkerStyle.ARROW_FILLED -> R.string.gps_marker_arrow_filled
        GpsMarkerStyle.CROSSHAIR -> R.string.gps_marker_crosshair
    }
)

/** Libellé traduit d'un placement d'infobulle. */
@Composable private fun bubblePositionLabel(p: BubblePosition): String = stringResource(
    when (p) {
        BubblePosition.AUTO -> R.string.bubble_pos_auto
        BubblePosition.TOP_LEFT -> R.string.bubble_pos_top_left
        BubblePosition.TOP -> R.string.bubble_pos_top
        BubblePosition.TOP_RIGHT -> R.string.bubble_pos_top_right
        BubblePosition.MIDDLE_LEFT -> R.string.bubble_pos_middle_left
        BubblePosition.CENTER -> R.string.bubble_pos_center
        BubblePosition.MIDDLE_RIGHT -> R.string.bubble_pos_middle_right
        BubblePosition.BOTTOM_LEFT -> R.string.bubble_pos_bottom_left
        BubblePosition.BOTTOM -> R.string.bubble_pos_bottom
        BubblePosition.BOTTOM_RIGHT -> R.string.bubble_pos_bottom_right
    }
)

/** Select du placement de l'infobulle (même fond/bord compacts que [LanguagePicker]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BubblePositionPicker(current: BubblePosition, onSelect: (BubblePosition) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        Box(Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().clip(OutlinedTextFieldDefaults.shape)) {
            OutlinedTextFieldDefaults.Container(
                enabled = true, isError = false, interactionSource = interactionSource,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(bubblePositionLabel(current), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                ExposedDropdownMenuDefaults.TrailingIcon(open, modifier = Modifier.requiredSize(CompactIconSize))
            }
        }
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BubblePosition.entries.forEach { p ->
                DropdownMenuItem(text = { Text(bubblePositionLabel(p)) }, onClick = { open = false; onSelect(p) })
            }
        }
    }
}

/**
 * Les reglages des points d'interet, sous leur interrupteur (cf. [PoiSwitch]) : le couloir des traces, le
 * complement OpenStreetMap (mode expert) juste dessous, et l'attribution des donnees.
 */
@Composable private fun ColumnScopeMarker.PoiRows(cur: SettingsEntity, vm: SettingsViewModel) {
    /*
     * Le couloir des traces : au-dela de cette distance d'une trace AFFICHEE, un lieu n'est plus
     * montre (cf. ui/poi/PoiCorridor).
     *
     * Ce qu'il repare : le zoom de chargement etait haut - une ville et ses abords - parce qu'a
     * l'echelle d'une region la vue porte des milliers de lieux dont le service ne rend que les
     * premiers. Le couloir retire ce qui ne borde aucun trajet, et c'est lui qui rend le zoom plus bas
     * supportable (cf. PoiLoading.MIN_ZOOM).
     *
     * Eteint par defaut, ici comme en base : c'est un filtre qui RETIRE de la carte, et une couche
     * qui montre moins qu'on ne lui a demande sans l'avoir dit se lit comme une panne.
     *
     * Une ligne et sa valeur ("5 km"), dont le curseur s'ouvre dans une bulle d'un appui sur la valeur,
     * comme la taille du symbole de position : on le regle une fois, il n'a pas a tenir une ligne entiere.
     */
    // Le curseur parcourt les crans, et non les metres : "Sans limite" vaut zero en base, mais se
    // tient tout a DROITE, au-dela de la plus grande distance - c'est la plus large de toutes.
    val crans = PoiCorridorSteps
    val cran = crans.indexOf(cur.poiTrackCorridorM).takeIf { it >= 0 }
        ?: crans.indices.minBy { kotlin.math.abs(crans[it] - cur.poiTrackCorridorM) }
    var corridorBubble by remember { mutableStateOf(false) }
    val corridorLabel = stringResource(R.string.settings_label_poi_corridor)
    val corridorValue = poiCorridorLabel(cur.poiTrackCorridorM)
    SetRow(corridorLabel, info = stringResource(R.string.settings_poi_corridor_hint)) {
        Box {
            Box(Modifier.clip(MaterialTheme.shapes.small).clickable { corridorBubble = true }
                .padding(horizontal = 4.dp, vertical = 6.dp).testTag("poi_corridor_value")) {
                ValueText(corridorValue)
            }
            if (corridorBubble) {
                ValueBubble(
                    label = corridorLabel, value = corridorValue,
                    fraction = fractionOf(cran, 0, crans.lastIndex),
                    steps = crans.size - 2,
                    onFraction = { vm.save(cur.copy(poiTrackCorridorM = crans[valueOf(it, 0, crans.lastIndex)])) },
                    onDismiss = { corridorBubble = false },
                    tag = "poi_corridor_bubble",
                )
            }
        }
    }
    // Le choix des sources : mode expert seulement, le complement garde sinon son reglage.
    if (cur.expertMode) {
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_sw_poi_osm), cur.poiOsmComplement,
            info = stringResource(R.string.settings_sw_poi_osm_sub),
        ) {
            // Le cache n'a pas a etre vide : chaque cellule retient la source qui l'a servie
            // (cf. PoiCellEntity), et basculer le complement ne fait que demander les autres.
            vm.save(cur.copy(poiOsmComplement = it))
        }
    }
    // Sans filet au-dessus : un filet separe deux reglages, et l'attribution n'en est pas un - c'est
    // la mention que les donnees imposent, au bas de ce qui les affiche.
    Hint(stringResource(R.string.settings_poi_attribution))
}

/**
 * Bornes du couloir des traces, en metres : de zero - sans limite - a vingt kilometres.
 *
 * **Un curseur et non une liste de valeurs**, et le pas de cinq cents metres en est la raison : entre "le
 * long du chemin" et "dans le coin" il y a un continuum, et l'endroit ou l'on veut s'arreter depend du
 * pays qu'on traverse - deux kilometres en ville, dix dans une vallee vide. Une poignee de valeurs
 * imposees obligeait a choisir la moins fausse.
 *
 * **Zero retire la limite**, et se tient tout a DROITE du curseur, au-dela de vingt kilometres : c'est la
 * plus large des distances, et on la cherchait a l'oppose, apres le plus grand chiffre. On explore une
 * region ou l'on n'a aucune trace ouverte, et l'on veut voir ce qu'elle porte ; sans cette position, il
 * fallait laisser une distance qui ne voulait rien dire - et le couloir empeche desormais la requete
 * elle-meme (cf. PoiCorridor.crosses), ce qui viderait la carte pour de bon.
 */
private const val MaxPoiCorridorM = 20_000

/** Le pas du curseur : cinq cents metres. Au-dessous, on reglerait la precision d'un trace, pas la
 *  distance a laquelle on accepte de faire un detour. */
private const val PoiCorridorStepM = 500

/** Les crans du curseur, de gauche a droite : 500 m a 20 km, puis zero - sans limite. */
private val PoiCorridorSteps: List<Int> =
    (1..MaxPoiCorridorM / PoiCorridorStepM).map { it * PoiCorridorStepM } + 0

/** "Sans limite", "500 m", "5 km" : le metre sous le kilometre, le kilometre au-dela, avec sa decimale
 *  quand elle compte - "2,5 km" est une distance qu'on se represente, "2500 m" beaucoup moins. */
@Composable
private fun poiCorridorLabel(m: Int): String = when {
    m <= 0 -> stringResource(R.string.settings_poi_corridor_off)
    m < 1_000 -> "$m m"
    m % 1_000 == 0 -> "${m / 1_000} km"
    else -> "${m / 1_000},${(m % 1_000) / 100} km"
}

