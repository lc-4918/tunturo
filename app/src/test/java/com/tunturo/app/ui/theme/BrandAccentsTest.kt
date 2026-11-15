package com.tunturo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.ui.routes.MapChrome
import com.tunturo.app.ui.routes.rememberMapChrome
import com.tunturo.app.ui.settings.settingsPaletteOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La couleur de marque de TM-3 : boutons de la carte comme le bouton "Calculer" de la planche des
 * palettes, cartes des reglages teintees, entetes au fond des cartes (l'aplat de couleur a ete refuse),
 * selections et puces en couleur.
 */
@RunWith(RobolectricTestRunner::class)
class BrandAccentsTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `les cartes des reglages sont teintees, l'entete garde leur fond`() {
        val palette = settingsPaletteOf(TunturoLight, dark = false)
        assertEquals(TunturoLight.surfaceContainerLow, palette.card)
        assertEquals(TunturoLight.surfaceContainerHigh, palette.screen)
        assertEquals(palette.card, palette.header)
        assertEquals(TunturoLight.onSurface, palette.onHeader)
        val darkPalette = settingsPaletteOf(TunturoDark, dark = true)
        assertEquals(TunturoDark.surfaceContainerHigh, darkPalette.card)
        assertEquals(TunturoDark.surface, darkPalette.screen)
    }

    @Test fun `onglet retenu en primary, puce retenue en tertiaire`() {
        val palette = settingsPaletteOf(TunturoLight, dark = false)
        assertEquals(TunturoLight.primary, palette.selected)
        assertEquals(TunturoLight.onPrimary, palette.onSelected)
        assertEquals(TunturoLight.tertiaryContainer, palette.chipSelected)
        assertEquals(TunturoLight.onTertiaryContainer, palette.onChipSelected)
    }

    @Test fun `les boutons de la carte ont les couleurs du bouton Calculer, allumes celles du conteneur`() {
        var chrome: MapChrome? = null
        var secondaryContainer: Color? = null
        compose.setContent {
            TunturoTheme {
                chrome = rememberMapChrome(SettingsEntity())
                secondaryContainer = secondaryButtonColors().containerColor
            }
        }
        compose.waitForIdle()
        assertEquals(TunturoLight.primary, chrome!!.bg)
        assertEquals(TunturoLight.onPrimary, chrome!!.buttonForeground(lit = false))
        assertEquals(TunturoLight.onPrimaryContainer, chrome!!.buttonForeground(lit = true))
        assertEquals(TunturoLight.secondaryContainer, secondaryContainer)
    }

    @Test fun `les petits boutons prennent primary et onPrimary, translucides sur une image`() {
        var opaque: Color? = null
        var translucent: Color? = null
        var content: Color? = null
        compose.setContent {
            TunturoTheme {
                opaque = smallButtonBackground()
                translucent = smallButtonBackground(translucent = true)
                content = smallButtonContent()
            }
        }
        compose.waitForIdle()
        assertEquals(TunturoLight.primary, opaque)
        assertEquals(TunturoLight.primary.copy(alpha = TranslucentSmallButtonAlpha), translucent)
        assertEquals(TunturoLight.onPrimary, content)
    }

    @Test fun `le theme pose le clair ou le sombre de Mousse selon le reglage`() {
        var lightPrimary: Color? = null
        var darkPrimary: Color? = null
        compose.setContent {
            TunturoTheme("light") { lightPrimary = MaterialTheme.colorScheme.primary }
            TunturoTheme("dark") { darkPrimary = MaterialTheme.colorScheme.primary }
        }
        compose.waitForIdle()
        assertEquals(TunturoLight.primary, lightPrimary)
        assertEquals(TunturoDark.primary, darkPrimary)
    }
}
