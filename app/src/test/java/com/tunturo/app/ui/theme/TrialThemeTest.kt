package com.tunturo.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le theme d'essai de TM-3 : chaque palette rend les couleurs exportees du Theme Builder, le thème actuel
 * reste celui de l'application, et le choix survit a une relecture.
 */
@RunWith(RobolectricTestRunner::class)
class TrialThemeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun backToCurrent() = TrialTheme.select(context, TrialTheme.CURRENT)

    @Test fun `le thème actuel et un identifiant inconnu gardent les couleurs de l'application`() {
        assertSame(TunturoLight, TrialTheme.colorScheme(TrialTheme.CURRENT, dark = false))
        assertSame(TunturoDark, TrialTheme.colorScheme(TrialTheme.CURRENT, dark = true))
        assertSame(TunturoLight, TrialTheme.colorScheme("palette-retiree", dark = false))
        assertSame(TunturoLight, TrialTheme.colorScheme(null, dark = false))
    }

    /** Valeurs relevees dans ruska.json et myrtille.json (exports du 2026-11-15). */
    @Test fun `une palette rend les couleurs de son export, en clair comme en sombre`() {
        val ruskaLight = TrialTheme.colorScheme("ruska", dark = false)
        assertEquals(Color(0xFF8D4E2C), ruskaLight.primary)
        assertEquals(Color(0xFFFFF8F6), ruskaLight.surface)
        assertEquals(Color(0xFFFCEAE3), ruskaLight.surfaceContainer)
        val ruskaDark = TrialTheme.colorScheme("ruska", dark = true)
        assertEquals(Color(0xFFFFB693), ruskaDark.primary)
        assertEquals(Color(0xFF1A120E), ruskaDark.surface)
        assertEquals(Color(0xFF65558F), TrialTheme.colorScheme("myrtille", dark = false).primary)
    }

    @Test fun `le choix propose le thème actuel puis les neuf palettes, sans doublon`() {
        assertEquals(TrialTheme.CURRENT, TrialTheme.choices.first())
        assertEquals(10, TrialTheme.choices.size)
        assertEquals(TrialTheme.choices.size, TrialTheme.choices.toSet().size)
        assertEquals("Ruska", TrialTheme.displayName("ruska"))
        assertEquals(null, TrialTheme.displayName(TrialTheme.CURRENT))
    }

    @Test fun `le choix s'applique aussitot et se relit au demarrage suivant`() {
        TrialTheme.select(context, "ambre")
        assertEquals("ambre", TrialTheme.selected.value)
        TrialTheme.select(context, TrialTheme.CURRENT)
        TrialTheme.select(context, "glacier")
        TrialTheme.load(context)
        assertEquals("glacier", TrialTheme.selected.value)
    }

    @Test fun `un identifiant inconnu revient au thème actuel`() {
        TrialTheme.select(context, "palette-retiree")
        assertEquals(TrialTheme.CURRENT, TrialTheme.selected.value)
    }
}
