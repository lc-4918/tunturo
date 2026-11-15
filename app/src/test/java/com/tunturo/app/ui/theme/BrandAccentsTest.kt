package com.tunturo.app.ui.theme

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.ui.routes.MapChrome
import com.tunturo.app.ui.routes.rememberMapChrome
import com.tunturo.app.ui.settings.settingsPaletteOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La nouvelle identite de TM-3, toujours active : boutons de la carte comme le bouton "Calculer" de la
 * planche des palettes, cartes des reglages teintees, entetes au fond des cartes (l'aplat de couleur a
 * ete refuse), selections et puces en couleur.
 */
@RunWith(RobolectricTestRunner::class)
class BrandAccentsTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun backToDefault() = TrialTheme.select(context, TrialTheme.DEFAULT)

    @Test fun `la nouvelle identite est toujours active`() {
        var accents: Boolean? = null
        compose.setContent { TunturoTheme { accents = LocalBrandAccents.current } }
        compose.waitForIdle()
        assertTrue(accents!!)
    }

    @Test fun `les cartes des reglages sont teintees, l'entete garde leur fond`() {
        val scheme = TrialTheme.colorScheme("ruska", dark = false)
        val palette = settingsPaletteOf(scheme, dark = false, brand = true)
        assertEquals(scheme.surfaceContainerLow, palette.card)
        assertEquals(scheme.surfaceContainerHigh, palette.screen)
        assertEquals(palette.card, palette.header)
        assertEquals(scheme.onSurface, palette.onHeader)
    }

    @Test fun `onglet retenu en primary, puce retenue en tertiaire`() {
        val scheme = TrialTheme.colorScheme("ruska", dark = false)
        val palette = settingsPaletteOf(scheme, dark = false, brand = true)
        assertEquals(scheme.primary, palette.selected)
        assertEquals(scheme.onPrimary, palette.onSelected)
        assertEquals(scheme.tertiaryContainer, palette.chipSelected)
        assertEquals(scheme.onTertiaryContainer, palette.onChipSelected)
    }

    @Test fun `les boutons de la carte ont les couleurs du bouton Calculer, allumes celles du conteneur`() {
        var chrome: MapChrome? = null
        var secondaryContainer: Color? = null
        var secondaryBorder: BorderStroke? = null
        TrialTheme.select(context, "ruska")
        compose.setContent {
            TunturoTheme {
                chrome = rememberMapChrome(SettingsEntity())
                secondaryContainer = secondaryButtonColors().containerColor
                secondaryBorder = secondaryButtonBorder(BorderStroke(1.dp, Color.Black))
            }
        }
        compose.waitForIdle()
        val scheme = TrialTheme.colorScheme("ruska", dark = false)
        assertEquals(scheme.primary, chrome!!.bg)
        assertEquals(scheme.onPrimary, chrome!!.buttonForeground(lit = false))
        assertEquals(scheme.onPrimaryContainer, chrome!!.buttonForeground(lit = true))
        assertEquals(scheme.secondaryContainer, secondaryContainer)
        assertNull(secondaryBorder)
    }

    @Test fun `les petits boutons prennent primary et onPrimary, translucides sur une image`() {
        var opaque: Color? = null
        var translucent: Color? = null
        var content: Color? = null
        TrialTheme.select(context, "mousse")
        compose.setContent {
            TunturoTheme {
                opaque = smallButtonBackground()
                translucent = smallButtonBackground(translucent = true)
                content = smallButtonContent()
            }
        }
        compose.waitForIdle()
        val scheme = TrialTheme.colorScheme("mousse", dark = false)
        assertEquals(scheme.primary, opaque)
        assertEquals(scheme.primary.copy(alpha = TranslucentSmallButtonAlpha), translucent)
        assertEquals(scheme.onPrimary, content)
    }

    @Test fun `la palette par defaut est Mousse`() {
        assertEquals("mousse", TrialTheme.DEFAULT)
    }

    @Test fun `le theme pose bien la palette choisie`() {
        TrialTheme.select(context, "glacier")
        var primary: Color? = null
        compose.setContent { TunturoTheme { primary = MaterialTheme.colorScheme.primary } }
        compose.waitForIdle()
        assertEquals(TrialTheme.colorScheme("glacier", dark = false).primary, primary)
    }
}
