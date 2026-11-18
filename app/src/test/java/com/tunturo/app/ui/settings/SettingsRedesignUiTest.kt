package com.tunturo.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.R
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.ui.routes.TestTunturoApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La refonte du menu Reglages (2026-11-18) : l'alerte d'eloignement sur sa ligne, le calcul d'itineraire
 * a interrupteur et chevron, la bulle des fonds de plan, l'onglet Systeme regroupe, et l'accordeon des
 * preferences du calcul.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTunturoApp::class)
class SettingsRedesignUiTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<TestTunturoApp>()

    private fun settings() = runBlocking { app.repository.settings.get() ?: SettingsEntity() }

    private fun present(res: Int) =
        compose.onAllNodesWithText(app.getString(res), substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun open(edit: (SettingsEntity) -> SettingsEntity) {
        runBlocking {
            app.repository.ensureSeed()
            app.repository.settings.upsert(edit(settings()))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("settings_logo", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text, substring = true))
        compose.waitForIdle()
    }

    private fun tab(res: Int) {
        compose.onAllNodesWithText(app.getString(res))[0].performClick()
        compose.waitForIdle()
    }

    // ---------- Onglet Carte ----------

    @Test fun `l'alerte eteinte s'allume d'un tap sur sa ligne, puis s'ouvre`() {
        open { it.copy(expertMode = false, offTrackAlertEnabled = true, offTrackAlertActive = false) }
        val alert = app.getString(R.string.settings_section_off_track)
        scrollTo(alert)
        compose.onNodeWithText(alert).performScrollTo().performClick()
        compose.waitUntil(5_000) { settings().offTrackAlertActive }
        // Allumer ouvre ce qui se regle.
        compose.waitUntil(5_000) { present(R.string.settings_sw_off_track_vibrate) }
    }

    @Test fun `le calcul d'itineraire allume s'ouvre et se referme sans s'eteindre`() {
        open { it.copy(expertMode = false, routePlannerEnabled = true) }
        val planner = app.getString(R.string.settings_sw_planner)
        scrollTo(planner)
        val discipline = app.getString(R.string.settings_section_default_discipline).uppercase()
        fun disciplineShown() = compose.onAllNodesWithText(discipline).fetchSemanticsNodes().isNotEmpty()
        assertFalse(disciplineShown())
        compose.onNodeWithText(planner).performScrollTo().performClick()
        compose.waitUntil(5_000) { disciplineShown() }
        compose.onNodeWithText(planner).performScrollTo().performClick()
        compose.waitUntil(5_000) { !disciplineShown() }
        assertTrue("toujours allume", settings().routePlannerEnabled)
    }

    @Test fun `en mode expert, la carte porte Affichage des boutons et Habillage de la carte`() {
        open { it.copy(expertMode = true) }
        scrollTo(app.getString(R.string.settings_section_map_controls))
        assertTrue(app.getString(R.string.settings_section_map_controls) == "Affichage des boutons")
        assertTrue(app.getString(R.string.settings_section_map_dressing) == "Présentation")
        scrollTo(app.getString(R.string.settings_section_map_dressing))
        assertTrue(present(R.string.settings_sw_scale))
    }

    /** Le chevron se pose a gauche de l'interrupteur, qui garde sa place au bout de la ligne. */
    @Test fun `le chevron du calcul d'itineraire est a gauche de son interrupteur`() {
        open { it.copy(expertMode = false, routePlannerEnabled = true) }
        scrollTo(app.getString(R.string.settings_sw_planner))
        val row = compose.onNodeWithText(app.getString(R.string.settings_sw_planner)).fetchSemanticsNode()
        val switch = compose.onNodeWithTag("settings_switch_${app.getString(R.string.settings_sw_planner)}", useUnmergedTree = true)
            .fetchSemanticsNode()
        val chevron = compose.onNodeWithTag("accordion_chevron_${app.getString(R.string.settings_sw_planner)}", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("interrupteur au bout", switch.boundsInRoot.right >= row.boundsInRoot.right - 40f)
        assertTrue("chevron avant l'interrupteur", chevron.boundsInRoot.right <= switch.boundsInRoot.left)
    }

    /** En mode expert, le tableau de bord a son chevron, et ses champs se deplient dessous. */
    @Test fun `en mode expert le tableau de bord deplie ses champs`() {
        open { it.copy(expertMode = true, offTrackAlertEnabled = true) }
        val dashboard = app.getString(R.string.settings_sw_track_follow)
        val fields = app.getString(R.string.settings_section_dashboard_fields).uppercase()
        fun fieldsShown() = compose.onAllNodesWithText(fields).fetchSemanticsNodes().isNotEmpty()
        scrollTo(dashboard)
        assertFalse(fieldsShown())
        compose.onNodeWithText(dashboard).performScrollTo().performClick()
        compose.waitUntil(5_000) { fieldsShown() }
        assertTrue("toujours allume", settings().offTrackAlertEnabled)
    }

    @Test fun `la ligne Symbole n'a plus de bouton d'information`() {
        open { it.copy(expertMode = false) }
        scrollTo(app.getString(R.string.settings_label_gps_marker_style))
        val hint = app.getString(R.string.settings_gps_marker_heading_hint)
        // Le "i" portait l'explication pour description (cf. InfoTip) : plus aucun noeud ne la porte.
        compose.onAllNodes(hasContentDescription(hint)).assertCountEquals(0)
    }

    /** Un tap sur un fond ouvre sa bulle ; "Editer" la remplace par l'editeur, le retour ramene l'apercu. */
    @Test fun `la bulle d'un fond passe de l'apercu a l'editeur et revient`() {
        open { it.copy(expertMode = false) }
        val online = runBlocking { app.repository.providers.all().first() }.first { it.type == "XYZ" }
        // Dans la marge de gauche de la ligne : le milieu porte ses gestes, et le libelle son "i".
        compose.onNodeWithText(app.getString(R.string.settings_line_online_maps), substring = true)
            .performTouchInput { click(Offset(4f, centerY)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(online.name).fetchSemanticsNodes().isNotEmpty() }
        // La ligne n'a plus de "i" : son ancienne explication n'est portee par aucun noeud.
        compose.onAllNodes(hasContentDescription(app.getString(R.string.settings_providers_subtitle))).assertCountEquals(0)
        // Les deux gestes sont dans la ligne elle-meme, plus dans un en-tete a part.
        assertTrue(present(R.string.action_import))
        assertTrue(present(R.string.action_export))
        compose.onNodeWithTag("provider_row_${online.id}").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("basemap_bubble_summary").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("basemap_thumbnail").assertExists()
        compose.onNodeWithText(app.getString(R.string.action_edit)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("basemap_bubble_editor").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(present(R.string.settings_field_url))
        assertTrue(present(R.string.settings_field_api_key))
        compose.onNodeWithTag("basemap_bubble_back").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("basemap_bubble_summary").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun `le bouton Activer de la bulle bascule le fond`() {
        open { it.copy(expertMode = false) }
        val online = runBlocking { app.repository.providers.all().first() }.first { it.type == "XYZ" }
        val before = online.enabled
        // Dans la marge de gauche de la ligne : le milieu porte ses gestes, et le libelle son "i".
        compose.onNodeWithText(app.getString(R.string.settings_line_online_maps), substring = true)
            .performTouchInput { click(Offset(4f, centerY)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(online.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("provider_row_${online.id}").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("basemap_bubble_summary").fetchSemanticsNodes().isNotEmpty() }
        val label = app.getString(if (before) R.string.action_deactivate else R.string.action_activate)
        compose.onNodeWithText(label).performClick()
        compose.waitUntil(5_000) {
            runBlocking { app.repository.providers.all().first() }.first { it.id == online.id }.enabled != before
        }
    }

    /** Ouvre l'accordeon des cartes hors ligne, en touchant la marge de gauche de sa ligne. */
    private fun openOfflineMaps() {
        compose.onNodeWithText(app.getString(R.string.settings_line_offline_maps))
            .performTouchInput { click(Offset(4f, centerY)) }
        compose.waitForIdle()
    }

    private fun bubbleShown() = compose.onAllNodesWithTag("basemap_bubble_summary").fetchSemanticsNodes().isNotEmpty()

    /** Un composite : plus de crayon ni de corbeille sur la ligne ; sa bulle dit ses couches et porte trois boutons. */
    @Test fun `la bulle d'un composite dit ses couches et porte trois boutons`() {
        open { it.copy(expertMode = false) }
        val providers = runBlocking { app.repository.providers.all().first() }
        val background = providers.first { it.type == "XYZ" }
        val foreground = providers.first { it.id != background.id && it.type != "MBTILES" }
        runBlocking {
            app.repository.composites.upsert(com.tunturo.app.data.db.CompositeEntity(
                id = 900, name = "Composite essai", backgroundProviderId = background.id,
                foregroundProviderId = foreground.id, foregroundOpacity = 0.4f, enabled = true, sortOrder = 0,
            ))
        }
        openOfflineMaps()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("composite_row_900").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("composite_row_900").performScrollTo()
        compose.onAllNodesWithContentDescription(app.getString(R.string.action_edit)).assertCountEquals(0)
        compose.onAllNodesWithContentDescription(app.getString(R.string.action_delete)).assertCountEquals(0)
        compose.onNodeWithTag("composite_row_900").performClick()
        compose.waitUntil(5_000) { bubbleShown() }
        compose.onNodeWithText(
            app.getString(R.string.basemap_composite_desc, foreground.name, background.name, 40), useUnmergedTree = true,
        ).assertExists()
        compose.onNodeWithText(app.getString(R.string.action_edit)).assertExists()
        compose.onNodeWithText(app.getString(R.string.action_deactivate)).assertExists()
        compose.onNodeWithText(app.getString(R.string.action_delete)).performClick()
        compose.waitUntil(5_000) {
            runBlocking { app.repository.composites.all().first() }.none { it.id == 900L }
        }
    }

    /** Une carte telechargee : sa bulle dit les zooms telecharges, et se supprime. */
    @Test fun `la bulle d'une carte telechargee dit ses zooms et se supprime`() {
        open { it.copy(expertMode = false) }
        runBlocking {
            app.repository.providers.upsert(com.tunturo.app.data.db.ProviderEntity(
                id = "mbtiles_essai", name = "Zone essai", groupName = "Local", type = "MBTILES",
                urlTemplate = "zone_essai.mbtiles", minZoom = 1, maxZoom = 9, builtin = false,
            ))
        }
        openOfflineMaps()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("provider_row_mbtiles_essai").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("provider_row_mbtiles_essai").performScrollTo().performClick()
        compose.waitUntil(5_000) { bubbleShown() }
        compose.onNodeWithText(app.getString(R.string.basemap_zoom_downloaded, 1, 9), substring = true, useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithText(app.getString(R.string.action_delete)).performClick()
        compose.waitUntil(5_000) {
            runBlocking { app.repository.providers.all().first() }.none { it.id == "mbtiles_essai" }
        }
    }

    /** Points d'interet : la distance a une trace se regle dans une bulle, le complement OSM vient dessous. */
    @Test fun `la distance a une trace s'ouvre en bulle, le complement OSM sous elle`() {
        open { it.copy(expertMode = true, poiEnabled = true) }
        val poi = app.getString(R.string.settings_sw_poi)
        scrollTo(poi)
        // Fermee a l'arrivee, la ligne a son chevron : un tap dans sa marge l'ouvre.
        compose.onNodeWithTag("accordion_chevron_$poi", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(poi, substring = true).performScrollTo().performTouchInput { click(Offset(4f, centerY)) }
        compose.waitForIdle()
        scrollTo(app.getString(R.string.settings_sw_poi_osm))
        val corridor = compose.onNodeWithText(app.getString(R.string.settings_label_poi_corridor), substring = true)
            .fetchSemanticsNode().boundsInRoot
        val osm = compose.onNodeWithText(app.getString(R.string.settings_sw_poi_osm), substring = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("le complement OSM sous la distance", osm.top >= corridor.bottom)
        compose.onAllNodesWithTag("poi_corridor_bubble").assertCountEquals(0)
        compose.onNodeWithTag("poi_corridor_value").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("poi_corridor_bubble").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Le gestionnaire de couches : deux lignes, dont les curseurs s'ouvrent en bulle au tap sur la valeur. */
    @Test fun `le gestionnaire de couches regle ses valeurs dans des bulles`() {
        open { it.copy(expertMode = true) }
        val line = app.getString(R.string.settings_section_layer_manager)
        scrollTo(app.getString(R.string.settings_section_map_dressing))
        scrollTo(line)
        // Une ligne en accordeon, fermee a l'arrivee, en tete du groupe Presentation.
        assertFalse(present(R.string.settings_label_panel_width))
        val title = compose.onNodeWithText(app.getString(R.string.settings_section_map_dressing)).fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithText(line).fetchSemanticsNode().boundsInRoot
        val scale = compose.onNodeWithText(app.getString(R.string.settings_sw_scale)).fetchSemanticsNode().boundsInRoot
        assertTrue("sous le titre, avant l'echelle", row.top >= title.bottom && row.bottom <= scale.top)
        compose.onNodeWithText(line).performScrollTo().performClick()
        compose.waitUntil(5_000) { present(R.string.settings_label_panel_width) }
        compose.onAllNodesWithTag("layer_manager_width_bubble").assertCountEquals(0)
        compose.onNodeWithTag("layer_manager_width_value").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("layer_manager_width_bubble").fetchSemanticsNodes().isNotEmpty() }
    }

    // ---------- Onglet Systeme ----------

    /** Les mises a jour se choisissent dans un select : Auto ou Manuel, et non plus deux puces. */
    @Test fun `les mises a jour se choisissent dans un select`() {
        open { it.copy(expertMode = false, updateCheckMode = "auto") }
        tab(R.string.settings_tab_general)
        val manual = app.getString(R.string.update_mode_manual)
        // Deux noeuds portent "Mises a jour" : le titre du groupe, puis la ligne a select.
        val row = compose.onAllNodesWithText(app.getString(R.string.settings_label_updates), substring = true)[1]
        row.performScrollTo()
        compose.onAllNodesWithText(manual).assertCountEquals(0)
        row.performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(manual).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(manual).performClick()
        compose.waitUntil(5_000) { settings().updateCheckMode == "manual" }
    }

    @Test fun `le systeme regroupe l'affichage et les interactions, sans titre du menu lateral`() {
        open { it.copy(expertMode = true) }
        tab(R.string.settings_tab_general)
        assertTrue(present(R.string.settings_section_map_buttons))
        compose.onNodeWithTag("map_buttons_size").assertExists()
        compose.onNodeWithTag("map_buttons_opacity").assertExists()
        assertTrue(present(R.string.settings_label_side_menu_access))
        assertTrue(present(R.string.settings_label_basemap_access))
        assertTrue(present(R.string.settings_section_tap_tolerance))
        assertFalse("tolerance repliee", present(R.string.settings_label_waypoints))
        assertFalse("titre du menu lateral supprime", present(R.string.settings_label_side_title))
        assertFalse("plus de groupe Application", present(R.string.settings_group_application))
    }

    @Test fun `le cache se deplie, les mises a jour ont leur groupe et la reinitialisation sa carte`() {
        open { it.copy(expertMode = false) }
        tab(R.string.settings_tab_general)
        val cache = app.getString(R.string.settings_section_cache)
        compose.onNodeWithText(cache).performScrollTo()
        assertFalse("cache replie", present(R.string.settings_clear_map_cache))
        compose.onNodeWithText(cache).performClick()
        compose.waitUntil(5_000) { present(R.string.settings_clear_map_cache) }
        compose.onNodeWithText(app.getString(R.string.action_reset_all_settings)).performScrollTo()
        assertTrue(compose.onAllNodesWithText(app.getString(R.string.settings_label_updates))
            .fetchSemanticsNodes().size >= 1)
    }

    @Test fun `la taille des boutons de la carte se regle dans une bulle`() {
        open { it.copy(expertMode = true) }
        tab(R.string.settings_tab_general)
        compose.onNodeWithTag("map_buttons_size").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("map_buttons_size_bubble").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ---------- Onglet Parcours ----------

    @Test fun `les preferences du calcul se deplient et se reinitialisent`() {
        open {
            it.copy(expertMode = true, routePrefsRoad = "roads,avoid,paved", routePrefsMtb = "roads,avoid,paved")
        }
        tab(R.string.settings_tab_routes)
        assertFalse(present(R.string.settings_section_route_prefs))
        val line = app.getString(R.string.settings_line_route_prefs)
        compose.onNodeWithText(line).performClick()
        compose.waitUntil(5_000) { present(R.string.settings_label_route_ways) }
        compose.onNodeWithText(app.getString(R.string.action_reset_defaults)).performScrollTo().performClick()
        val defaults = SettingsEntity()
        compose.waitUntil(5_000) {
            settings().routePrefsRoad == defaults.routePrefsRoad && settings().routePrefsMtb == defaults.routePrefsMtb
        }
    }
}
