package com.tunturo.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.BuildConfig
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
 * Le mode expert des reglages : ses options restent cachees, sept appuis sur le titre les montrent - une
 * alerte le dit -, et sept autres les cachent de nouveau. Le reglage est enregistre en base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTunturoApp::class)
class ExpertModeUiTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<TestTunturoApp>()

    private fun present(res: Int) =
        compose.onAllNodesWithText(app.getString(res), substring = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Sept appuis sur le titre "Reglages".
     *
     * **Le titre s'attend avant de le viser.** `setContent` puis `waitForIdle` ne garantissent pas que
     * l'ecran soit la : ses reglages viennent de la base, et la barre du haut ne parait qu'a leur arrivee.
     * Taper aussitot visait un noeud qui n'existait pas encore, une fois sur deux.
     */
    private fun septAppuis() {
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        repeat(ExpertTaps.TAPS) {
            compose.onNodeWithTag("settings_title", useUnmergedTree = true).performClick()
        }
    }

    private fun avatars() =
        compose.onAllNodesWithTag("settings_logo", useUnmergedTree = true).fetchSemanticsNodes()

    /**
     * Amene a l'ecran la ligne portant ce texte, et attend qu'elle y soit.
     *
     * **Necessaire depuis que le contenu des onglets est une liste paresseuse** : ce qui est hors de
     * l'ecran n'est pas compose, donc n'existe pas pour un test. Chercher un reglage sans y avoir defile
     * ne rend plus rien - ce qui est le prix, et le principe, de la liste.
     */
    private fun defileVers(texte: String) {
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(texte, substring = true))
        compose.waitForIdle()
    }

    /** Un appui sur l'avatar, une fois l'ecran compose : meme attente, meme raison (cf. [septAppuis]). */
    private fun unAppui() {
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        compose.onNodeWithTag("settings_logo", useUnmergedTree = true).performClick()
    }

    @Test fun `sept appuis sur le titre montrent puis cachent les options expertes`() {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = false))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        // Le recentrage a l'allumage est un reglage expert : on se place sur le repere de position, toujours
        // visible (sans titre hors mode expert).
        val expert = R.string.settings_sw_gps_recenter
        defileVers(app.getString(R.string.settings_label_gps_marker_style))
        assertTrue("cache hors mode expert", !present(expert))

        septAppuis()
        // L'alerte ne dure que trois secondes : on la lit avant de defiler.
        compose.waitUntil(5_000) { present(R.string.settings_expert_on) }
        defileVers(app.getString(R.string.settings_sw_gps_recenter))
        compose.waitUntil(5_000) { present(expert) }
        assertTrue("enregistre en base", runBlocking { app.repository.settings.get()!!.expertMode })

        septAppuis()
        compose.waitUntil(5_000) { present(R.string.settings_expert_off) }
        defileVers(app.getString(R.string.settings_label_gps_marker_style))
        compose.waitUntil(5_000) { !present(expert) }
    }

    /** Ecran des reglages avec le tableau de bord allume (ses champs ne s'affichent que dans ce cas). */
    private fun carteAvecTableauDeBord(expert: Boolean) {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = expert, offTrackAlertEnabled = true))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
    }

    /** Onglet Carte : champs du tableau de bord, points d'interet et profil altimetrique caches hors mode expert. */
    @Test fun `la carte cache ses groupes experts hors mode expert`() {
        carteAvecTableauDeBord(expert = false)
        // Le repere de position est la derniere carte hors mode expert : en y defilant, on est en bout de liste.
        defileVers(app.getString(R.string.settings_label_gps_marker_style))
        fun presentText(texte: String) =
            compose.onAllNodesWithText(texte, substring = true).fetchSemanticsNodes().isNotEmpty()
        assertFalse("champs affiches", presentText(app.getString(R.string.settings_section_dashboard_fields).uppercase()))
        assertFalse("points d'interet", present(R.string.settings_label_bubble_position))
        assertFalse("profil altimetrique", present(R.string.settings_group_elevation_profile))
    }

    @Test fun `la carte montre ses groupes experts en mode expert`() {
        carteAvecTableauDeBord(expert = true)
        // Les champs se deplient sous le tableau de bord, ligne a chevron en mode expert.
        defileVers(app.getString(R.string.settings_sw_track_follow))
        compose.onNodeWithText(app.getString(R.string.settings_sw_track_follow)).performScrollTo().performClick()
        compose.waitForIdle()
        defileVers(app.getString(R.string.settings_section_dashboard_fields).uppercase())
        defileVers(app.getString(R.string.settings_group_pois))
        defileVers(app.getString(R.string.settings_group_elevation_profile))
        assertTrue("profil altimetrique", present(R.string.settings_group_elevation_profile))
    }

    /**
     * Hors mode expert, l'onglet Carte n'a plus ni groupe ni rubrique : une carte par bouton de la carte,
     * dont celle de l'enregistrement, qui ecrit son reglage.
     */
    @Test fun `la carte simple n'a ni groupe ni rubrique, et porte l'interrupteur d'enregistrement`() {
        carteAvecTableauDeBord(expert = false)
        assertFalse("pas de groupe Boutons et gestes", present(R.string.settings_section_map_controls))
        defileVers(app.getString(R.string.settings_sw_recording))
        compose.onNodeWithText(app.getString(R.string.settings_sw_recording)).performClick()
        compose.waitUntil(5_000) { runBlocking { app.repository.settings.get()?.recordingButtonEnabled == false } }
        defileVers(app.getString(R.string.settings_label_gps_marker_style))
        assertFalse("pas de rubrique du repere",
            compose.onAllNodesWithText(app.getString(R.string.settings_group_gps))
                .fetchSemanticsNodes().isNotEmpty())
    }

    /**
     * L'alerte d'eloignement est une carte a elle, apres celle du tableau de bord : elle se voit tableau de
     * bord eteint, et son accordeon s'ouvre d'un tap.
     */
    @Test fun `l'alerte d'eloignement a sa carte, apres le tableau de bord`() {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = false, offTrackAlertEnabled = false))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        defileVers(app.getString(R.string.settings_section_off_track))
        assertFalse("replie", present(R.string.settings_sw_off_track_vibrate))
        compose.onNodeWithText(app.getString(R.string.settings_section_off_track)).performScrollTo().performClick()
        compose.waitUntil(5_000) { present(R.string.settings_sw_off_track_vibrate) }
        assertFalse("le tableau de bord reste eteint", runBlocking { app.repository.settings.get()!!.offTrackAlertEnabled })
    }

    /** Les points d'interet allumes : leur ligne a chevron, fermee a l'arrivee, s'ouvre d'un tap. */
    private fun ouvrirPointsInteret() {
        defileVers(app.getString(R.string.settings_sw_poi))
        compose.onNodeWithText(app.getString(R.string.settings_sw_poi), substring = true)
            .performScrollTo().performTouchInput { click(Offset(4f, centerY)) }
        compose.waitForIdle()
    }

    /** Ouvre l'onglet portant ce libelle, une fois l'ecran compose. */
    private fun onglet(res: Int) {
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        compose.onAllNodesWithText(app.getString(res))[0].performClick()
        compose.waitForIdle()
    }

    /** Ecran des reglages, mode expert allume ou eteint en base. */
    private fun reglagesEnMode(expert: Boolean) {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = expert, poiEnabled = true))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
    }

    /**
     * Parcours : un onglet tout entier expert, absent sinon. Systeme : unites, dossiers, sauvegarde et
     * personnalisation, caches hors mode expert.
     */
    @Test fun `parcours et systeme cachent leurs reglages experts`() {
        reglagesEnMode(expert = false)
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        assertFalse("onglet Parcours", present(R.string.settings_tab_routes))
        onglet(R.string.settings_tab_general)
        assertFalse("unites", present(R.string.settings_label_units))
        assertFalse("dossiers", present(R.string.settings_section_import_folder))
        assertFalse("sauvegarde", present(R.string.settings_backup_create))
    }

    /** GPS, en mode expert : une rubrique de deux lignes, le recentrage et le symbole, et rien d'autre. */
    @Test fun `la rubrique GPS porte le recentrage et le symbole`() {
        reglagesEnMode(expert = true)
        defileVers(app.getString(R.string.settings_sw_gps_recenter))
        assertTrue(present(R.string.settings_group_gps))
        assertTrue(present(R.string.settings_label_gps_marker_style))
    }

    @Test fun `parcours et systeme montrent leurs reglages experts en mode expert`() {
        reglagesEnMode(expert = true)
        onglet(R.string.settings_tab_routes)
        assertTrue("preferences du calcul", present(R.string.settings_line_route_prefs))
        assertFalse("coloration par pente, passee au calcul d'itineraire", present(R.string.settings_route_slope_line))
        assertFalse("altimetrie manquante, passee au profil", present(R.string.settings_label_fill_elevation))
        assertFalse("calcul du profil, passe au profil", present(R.string.settings_label_smoothing))
        onglet(R.string.settings_tab_general)
        assertTrue("unites", present(R.string.settings_label_units))
        assertTrue("dossiers", present(R.string.settings_section_folders))
        assertTrue("sauvegarde", present(R.string.settings_section_backup))
        assertFalse("titre du menu lateral, supprime", present(R.string.settings_label_side_title))
    }

    /** Le profil altimetrique, en mode expert : la grille, puis des lignes qui se deplient. */
    @Test fun `le profil altimetrique deplie le calcul et l'altimetrie manquante`() {
        reglagesEnMode(expert = true)
        defileVers(app.getString(R.string.settings_section_elevation_fill))
        assertTrue(present(R.string.settings_profile_grid))
        assertTrue(present(R.string.settings_section_profile_calc))
        assertFalse("replie", present(R.string.settings_label_fill_elevation))
        compose.onNodeWithText(app.getString(R.string.settings_section_elevation_fill)).performClick()
        compose.waitUntil(5_000) { present(R.string.settings_label_fill_elevation) }
    }

    /** Les points d'interet et l'enregistrement ont quitte l'onglet Trajets pour la carte et la boite de fin. */
    @Test fun `l'onglet Trajets n'a plus ni points d'interet ni enregistrement`() {
        reglagesEnMode(expert = true)
        onglet(R.string.settings_tab_routes)
        assertFalse("couloir des traces", present(R.string.settings_label_poi_corridor))
        assertFalse("lissage des altitudes", present(R.string.settings_label_recording_smooth_elevation))
    }

    /** Le couloir des traces, et en mode expert le complement OpenStreetMap, sous l'interrupteur des points d'interet. */
    @Test fun `les points d'interet deplient leurs reglages dans l'onglet Carte`() {
        reglagesEnMode(expert = false)
        ouvrirPointsInteret()
        defileVers(app.getString(R.string.settings_label_poi_corridor))
        assertTrue("couloir", present(R.string.settings_label_poi_corridor))
        assertFalse("complement OSM hors mode expert", present(R.string.settings_sw_poi_osm))
    }

    @Test fun `le complement OpenStreetMap se deplie sous les points d'interet en mode expert`() {
        reglagesEnMode(expert = true)
        ouvrirPointsInteret()
        defileVers(app.getString(R.string.settings_sw_poi_osm))
        assertTrue(present(R.string.settings_sw_poi_osm))
    }

    /** L'ordre des boutons : itineraire, enregistrement, tableau de bord, points d'interet, recherche, mesure, retouche. */
    @Test fun `les interrupteurs de la carte suivent l'ordre voulu`() {
        reglagesEnMode(expert = false)
        defileVers(app.getString(R.string.settings_sw_planner))
        fun top(res: Int) = compose.onAllNodesWithText(app.getString(res), substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().first().positionInRoot.y
        val ordre = listOf(
            R.string.settings_sw_planner, R.string.settings_sw_recording, R.string.settings_sw_track_follow,
            R.string.settings_section_off_track, R.string.settings_sw_poi, R.string.settings_sw_geocoding,
        ).map { top(it) }
        assertTrue("ordre $ordre", ordre.zipWithNext().all { (a, b) -> a < b })
        defileVers(app.getString(R.string.settings_sw_track_edit))
        assertTrue(top(R.string.settings_sw_measure) < top(R.string.settings_sw_track_edit))
    }

    /** Systeme, hors mode expert : pas de groupe Enregistrement. */
    @Test fun `le groupe Enregistrement est cache hors mode expert`() {
        reglagesEnMode(expert = false)
        onglet(R.string.settings_tab_general)
        assertFalse(present(R.string.settings_group_recording))
    }

    /** Systeme, en mode expert : un groupe Enregistrement et son interrupteur de reprise, allume (meme segment) par defaut. */
    @Test fun `le groupe Enregistrement regle la reprise apres une pause`() {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = true, recordingResumeMode = "same_segment"))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        onglet(R.string.settings_tab_general)
        assertTrue(present(R.string.settings_group_recording))
        compose.onNodeWithText(app.getString(R.string.recording_resume_same_segment), substring = true)
            .performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { app.repository.settings.get()?.recordingResumeMode == "new_segment" } }
    }

    private fun presentTitle(res: Int) =
        compose.onAllNodesWithText(app.getString(res).uppercase(), substring = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Les fonds de plan en tete de l'onglet Carte : deux lignes qui s'ouvrent en accordeon, et plus
     * d'onglet Tuiles. Le gestionnaire de couches reste un reglage expert.
     */
    @Test fun `les cartes en ligne s'ouvrent en accordeon avec leurs gestes`() {
        reglagesEnMode(expert = false)
        val online = runBlocking { app.repository.providers.all().first() }.first { it.type != "MBTILES" }
        fun shown() = compose.onAllNodesWithText(online.name).fetchSemanticsNodes().isNotEmpty()
        compose.waitUntil(5_000) { present(R.string.settings_line_online_maps) }
        assertTrue(present(R.string.settings_line_offline_maps))
        assertFalse("replie", shown())
        compose.onNodeWithText(app.getString(R.string.settings_line_online_maps), substring = true).performClick()
        compose.waitUntil(5_000) { shown() }
        assertTrue("exporter", present(R.string.action_export))
        assertFalse("le type ne s'ecrit plus", compose.onAllNodesWithText(online.type).fetchSemanticsNodes().isNotEmpty())
        // Un appui sur la ligne referme l'accordeon.
        // Dans la marge de gauche de la ligne : le milieu porte ses gestes, et le libelle son "i".
        compose.onNodeWithText(app.getString(R.string.settings_line_online_maps), substring = true)
            .performTouchInput { click(Offset(4f, centerY)) }
        compose.waitUntil(5_000) { !shown() }
    }

    @Test fun `les cartes hors ligne portent Importer, Telecharger et Composite`() {
        reglagesEnMode(expert = false)
        compose.waitUntil(5_000) { present(R.string.settings_line_offline_maps) }
        compose.onNodeWithText(app.getString(R.string.settings_line_offline_maps)).performClick()
        compose.waitUntil(5_000) { present(R.string.action_create_composite) }
        assertTrue(present(R.string.offline_action_download))
        assertTrue(present(R.string.action_import))
        compose.onNodeWithText(app.getString(R.string.settings_line_offline_maps)).performClick()
        compose.waitUntil(5_000) { !present(R.string.action_create_composite) }
    }

    @Test fun `l'onglet Parcours et le gestionnaire de couches ne se montrent qu'en mode expert`() {
        reglagesEnMode(expert = false)
        compose.waitUntil(5_000) { present(R.string.settings_line_online_maps) }
        assertFalse("onglet Parcours", present(R.string.settings_tab_routes))
        defileVers(app.getString(R.string.settings_label_gps_marker_style))
        assertFalse("gestionnaire de couches", present(R.string.settings_section_layer_manager))
    }

    @Test fun `en mode expert, le gestionnaire de couches ouvre le groupe Presentation`() {
        reglagesEnMode(expert = true)
        compose.waitUntil(5_000) { present(R.string.settings_tab_routes) }
        defileVers(app.getString(R.string.settings_section_layer_manager))
        assertTrue(present(R.string.settings_section_layer_manager))
    }

    /** La discipline par defaut et l'itineraire hors ligne se deplient sous le calcul d'itineraire. */
    @Test fun `le calcul d'itineraire deplie la discipline et les zones hors ligne`() {
        reglagesEnMode(expert = false)
        defileVers(app.getString(R.string.settings_sw_planner))
        assertFalse("replie a l'arrivee", present(R.string.routing_zones_add))
        compose.onNodeWithText(app.getString(R.string.settings_sw_planner)).performScrollTo().performClick()
        compose.waitUntil(5_000) { presentTitle(R.string.settings_section_default_discipline) }
        defileVers(app.getString(R.string.routing_zones_add))
        assertTrue(presentTitle(R.string.settings_section_default_discipline))
        assertTrue(presentTitle(R.string.settings_section_offline_routing))
    }

    /** L'affichage de la carte (echelle, rotation, fonds) n'apparait qu'en mode expert. */
    @Test fun `l'affichage de la carte ne s'ouvre qu'en mode expert`() {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = false))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        defileVers(app.getString(R.string.settings_sw_track_follow))
        assertFalse("cachee hors mode expert", present(R.string.settings_sw_scale))

        septAppuis()
        defileVers(app.getString(R.string.settings_sw_scale))
        assertTrue("affichee en mode expert", present(R.string.settings_sw_scale))
    }

    /**
     * Le titre du groupe courant se pose sous les onglets des que son titre est passe en haut, et se
     * retire quand on remonte. Sans lui, on regle des curseurs sans savoir a quoi ils se rapportent.
     */
    @Test fun `le groupe courant reste affiche en defilant`() {
        // Les groupes ne sont plus que dans le mode expert (cf. mapTab).
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = true))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        compose.onAllNodesWithTag("settings_group_bar").assertCountEquals(0)

        // On descend jusqu'a la rubrique GPS, dans le groupe Boutons et gestes : son titre est alors passe
        // au-dessus du bord.
        defileVers(app.getString(R.string.settings_sw_gps_recenter))
        compose.onAllNodesWithTag("settings_group_bar").assertCountEquals(1)
    }

    /** Un tap sur l'avatar ouvre son menu : "A propos" et "Aide". */
    @Test fun `l'avatar porte un menu`() {
        runBlocking { app.repository.ensureSeed() }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        compose.onAllNodesWithTag("menu_about").assertCountEquals(0)

        unAppui()
        compose.waitForIdle()
        compose.onAllNodesWithTag("menu_about").assertCountEquals(1)
        compose.onAllNodesWithTag("menu_help").assertCountEquals(1)
    }

    /** "A propos" dit la version installee et ou signaler un probleme. */
    @Test fun `a propos dit la version et le lien`() {
        runBlocking { app.repository.ensureSeed() }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        unAppui()
        compose.onNodeWithTag("menu_about").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.about_version, BuildConfig.VERSION_NAME)).assertIsDisplayed()
        compose.onNodeWithTag("about_issues_link").assertIsDisplayed()
    }

    /** L'avatar n'allume plus le mode expert : ses appuis ouvrent son menu, et rien d'autre. */
    @Test fun `sept appuis sur l'avatar ne touchent pas au mode expert`() {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = false))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        repeat(ExpertTaps.TAPS) { unAppui() }
        compose.waitForIdle()
        assertTrue(!runBlocking { app.repository.settings.get()!!.expertMode })
        assertTrue(!present(R.string.settings_expert_on))
    }
}
