package com.tunturo.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.R
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.ui.routes.TestTunturoApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'affichage des traces et la coloration par pente dans les reglages : la rubrique "Trace" de l'onglet
 * Carte, la rubrique de l'itineraire dans l'onglet Parcours, et le profil qui a perdu sa ligne du restant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTunturoApp::class)
class TrackDisplaySettingsUiTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<TestTunturoApp>()

    // Par morceau : un libelle porteur d'un "i" se termine par l'espace insecable qui l'y lie (cf. TextWithInfo).
    private fun present(texte: String) = compose.onAllNodesWithText(texte, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ouvrir(expert: Boolean) {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = expert))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(app.getString(R.string.settings_tab_map)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun defileVers(texte: String) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(texte, substring = true))
        compose.waitForIdle()
    }

    /** La rubrique Trace entiere est un reglage expert : hors mode expert, ni elle ni sa largeur de trait. */
    @Test fun `la rubrique Trace n'apparait qu'en mode expert`() {
        ouvrir(expert = false)
        defileVers(app.getString(R.string.settings_label_gps_marker_style))
        assertFalse("habillage de la carte", present(app.getString(R.string.settings_section_map_dressing)))
        assertFalse("largeur du trait", present(app.getString(R.string.settings_track_line_width)))
    }

    /**
     * En mode expert, la ligne "Trace", dans l'habillage de la carte, deplie la largeur du trait, le sens
     * de parcours et la simplification des traces, venue de l'onglet Systeme.
     */
    @Test fun `en mode expert, la ligne Trace deplie la largeur du trait et le sens de parcours`() {
        ouvrir(expert = true)
        defileVers(app.getString(R.string.settings_section_map_dressing))
        defileVers(app.getString(R.string.settings_sw_rotation))
        assertFalse("replie", present(app.getString(R.string.settings_track_line_width)))
        compose.onNodeWithText(app.getString(R.string.settings_group_track)).performScrollTo().performClick()
        compose.waitUntil(5_000) { present(app.getString(R.string.settings_track_line_width)) }
        assertTrue(present(app.getString(R.string.settings_track_direction)))
        defileVers(app.getString(R.string.settings_simplify_render))
        assertTrue(present(app.getString(R.string.settings_simplify_render)))
    }

    /**
     * L'affichage du profil n'a plus ni coloration par pente - elle suit l'option de la couche - ni
     * classes des pentes ; la ligne du restant a disparu aussi.
     */
    @Test fun `le profil ne regle plus la pente`() {
        // La rubrique du profil altimetrique est un reglage expert.
        ouvrir(expert = true)
        defileVers(app.getString(R.string.settings_profile_grid))
        assertFalse(present("Colorer l'aire par pente"))
        assertFalse(present("Classes des pentes"))
        assertFalse(present("Restant sur la trace"))
    }

    /**
     * Une seule ligne pour la pente de l'itineraire, tout en bas du calcul d'itineraire, qui dit dans son
     * "i" qu'elle vaut trace et profil.
     */
    @Test fun `le calcul d'itineraire regle la coloration de l'itineraire d'une seule ligne`() {
        ouvrir(expert = false)
        defileVers(app.getString(R.string.settings_sw_planner))
        compose.onNodeWithText(app.getString(R.string.settings_sw_planner)).performScrollTo().performClick()
        compose.waitForIdle()
        defileVers(app.getString(R.string.settings_route_slope_line))
        assertTrue(present("Colorer suivant la pente"))
        assertFalse("plus de ligne a part pour le profil", present("Trace de l'itinéraire calculé"))
        val info = app.getString(R.string.settings_route_slope_info)
        assertFalse(present(info))
        // Le "i" de la ligne porte l'explication pour description (cf. InfoTip).
        compose.onNode(hasTestTag("info_tip") and hasContentDescription(info)).performClick()
        compose.waitForIdle()
        assertTrue(present(info))
    }

    /** L'onglet Parcours finit par les services : l'altimetrie et les points d'interet sont partis dans l'onglet Carte. */
    @Test fun `l'onglet Parcours finit par les services`() {
        ouvrir(expert = true)
        compose.onNodeWithText(app.getString(R.string.settings_tab_routes)).performClick()
        compose.waitForIdle()
        fun absent(res: Int) = compose.onAllNodesWithText(app.getString(res)).fetchSemanticsNodes().isEmpty()
        assertFalse(absent(R.string.settings_section_services))
        assertTrue("plus d'altimetrie ici", absent(R.string.settings_section_elevation_fill))
        assertTrue("plus de points d'interet ici", absent(R.string.settings_section_poi))
    }
}
