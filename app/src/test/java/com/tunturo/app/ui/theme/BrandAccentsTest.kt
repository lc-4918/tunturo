package com.tunturo.app.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.ui.settings.settingsPaletteOf
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Les accents de marque de l'essai TM-3 : eteints avec le thème actuel - l'application reste exactement
 * comme avant -, allumes avec une palette d'essai, ou entetes et selections prennent la couleur primaire.
 */
@RunWith(RobolectricTestRunner::class)
class BrandAccentsTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun backToCurrent() = TrialTheme.select(context, TrialTheme.CURRENT)

    @Test fun `sans accents, la palette des reglages garde ses roles d'avant`() {
        val palette = settingsPaletteOf(TunturoLight, dark = false)
        assertEquals(palette.card, palette.header)
        assertEquals(palette.label, palette.onHeader)
        assertEquals(palette.subtle, palette.onHeaderSubtle)
        assertEquals(palette.accentContainer, palette.selected)
        assertEquals(palette.accentStrong, palette.onSelected)
    }

    @Test fun `avec accents, entete et selection prennent la couleur de marque`() {
        val scheme = TrialTheme.colorScheme("ruska", dark = false)
        val palette = settingsPaletteOf(scheme, dark = false, brand = true)
        assertEquals(scheme.primaryContainer, palette.header)
        assertEquals(scheme.onPrimaryContainer, palette.onHeader)
        assertEquals(scheme.primary, palette.selected)
        assertEquals(scheme.onPrimary, palette.onSelected)
    }

    @Test fun `les accents ne s'allument qu'avec une palette d'essai`() {
        var accents: Boolean? = null
        var header: BrandHeader? = null
        TrialTheme.select(context, TrialTheme.CURRENT)
        compose.setContent {
            TunturoTheme {
                accents = LocalBrandAccents.current
                header = brandHeader()
            }
        }
        compose.waitForIdle()
        assertFalse(accents!!)
        assertNull(header)

        TrialTheme.select(context, "ambre")
        compose.waitForIdle()
        assertTrue(accents!!)
        assertEquals(TrialTheme.colorScheme("ambre", dark = false).primaryContainer, header!!.background)
    }

    @Test fun `avec accents, une puce retenue prend le tertiaire, sans accents l'aplat d'avant`() {
        val scheme = TrialTheme.colorScheme("ruska", dark = false)
        val branded = settingsPaletteOf(scheme, dark = false, brand = true)
        assertEquals(scheme.tertiaryContainer, branded.chipSelected)
        assertEquals(scheme.onTertiaryContainer, branded.onChipSelected)
        val plain = settingsPaletteOf(TunturoLight, dark = false)
        assertEquals(plain.accentContainer, plain.chipSelected)
        assertEquals(plain.accentStrong, plain.onChipSelected)
    }

    @Test fun `les boutons de la carte suivent la planche des palettes avec une palette d'essai`() {
        val settings = com.tunturo.app.data.db.SettingsEntity()
        var chrome: com.tunturo.app.ui.routes.MapChrome? = null
        var secondaryContainer: androidx.compose.ui.graphics.Color? = null
        var secondaryBorder: androidx.compose.foundation.BorderStroke? = null
        TrialTheme.select(context, "ruska")
        compose.setContent {
            TunturoTheme {
                chrome = com.tunturo.app.ui.routes.rememberMapChrome(settings)
                secondaryContainer = secondaryButtonColors().containerColor
                secondaryBorder = secondaryButtonBorder(androidx.compose.foundation.BorderStroke(
                    1.dp, androidx.compose.ui.graphics.Color.Black))
            }
        }
        compose.waitForIdle()
        val scheme = TrialTheme.colorScheme("ruska", dark = false)
        assertEquals(scheme.surfaceContainerHigh, chrome!!.bg)
        assertEquals(scheme.primary, chrome!!.fg)
        assertEquals(scheme.onPrimaryContainer, chrome!!.mainActionFg)
        assertEquals(scheme.tertiary, chrome!!.active)
        assertEquals(scheme.secondaryContainer, secondaryContainer)
        assertNull(secondaryBorder)
    }

    @Test fun `sans palette d'essai, les boutons de la carte restent noir sur blanc`() {
        val settings = com.tunturo.app.data.db.SettingsEntity()
        var chrome: com.tunturo.app.ui.routes.MapChrome? = null
        compose.setContent { TunturoTheme { chrome = com.tunturo.app.ui.routes.rememberMapChrome(settings) } }
        compose.waitForIdle()
        assertEquals(androidx.compose.ui.graphics.Color.White, chrome!!.bg)
        assertEquals(androidx.compose.ui.graphics.Color.Black, chrome!!.fg)
        assertEquals(chrome!!.fg, chrome!!.mainActionFg)
    }

    @Test fun `le theme pose bien la palette d'essai`() {
        TrialTheme.select(context, "glacier")
        var primary: androidx.compose.ui.graphics.Color? = null
        compose.setContent { TunturoTheme { primary = MaterialTheme.colorScheme.primary } }
        compose.waitForIdle()
        assertEquals(TrialTheme.colorScheme("glacier", dark = false).primary, primary)
    }
}
