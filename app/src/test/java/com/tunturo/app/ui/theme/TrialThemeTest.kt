package com.tunturo.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le theme d'essai de TM-3 : chaque palette rend les couleurs exportees du Theme Builder, textes compris
 * (pris dans la palette et non plus neutres), et le choix survit a une relecture. Plus de "thème actuel" :
 * la nouvelle identite remplace l'ancien theme.
 */
@RunWith(RobolectricTestRunner::class)
class TrialThemeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun backToDefault() = TrialTheme.select(context, TrialTheme.DEFAULT)

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

    @Test fun `les textes prennent les couleurs de la palette`() {
        val light = TrialTheme.colorScheme("ruska", dark = false)
        assertEquals(Color(0xFF703717), light.onSurface)
        assertEquals(Color(0xFF703717), light.onBackground)
        assertEquals(Color(0xFF765749), light.onSurfaceVariant)
        val dark = TrialTheme.colorScheme("ruska", dark = true)
        assertEquals(Color(0xFFFFDBCB), dark.onSurface)
        assertEquals(Color(0xFFE6BEAC), dark.onSurfaceVariant)
    }

    @Test fun `un identifiant inconnu donne la palette par defaut`() {
        assertEquals(TrialTheme.colorScheme(TrialTheme.DEFAULT, dark = false).primary,
            TrialTheme.colorScheme("current", dark = false).primary)
        assertEquals(TrialTheme.colorScheme(TrialTheme.DEFAULT, dark = true).primary,
            TrialTheme.colorScheme(null, dark = true).primary)
    }

    @Test fun `le choix propose les neuf palettes, sans doublon ni thème actuel`() {
        assertEquals(9, TrialTheme.choices.size)
        assertEquals(TrialTheme.choices.size, TrialTheme.choices.toSet().size)
        assertEquals(false, "current" in TrialTheme.choices)
        assertEquals("Ruska", TrialTheme.displayName("ruska"))
    }

    @Test fun `le choix s'applique aussitot et se relit au demarrage suivant`() {
        TrialTheme.select(context, "ambre")
        assertEquals("ambre", TrialTheme.selected.value)
        TrialTheme.select(context, "glacier")
        TrialTheme.load(context)
        assertEquals("glacier", TrialTheme.selected.value)
    }

    @Test fun `un identifiant inconnu, dont l'ancien thème actuel, revient a la palette par defaut`() {
        TrialTheme.select(context, "current")
        assertEquals(TrialTheme.DEFAULT, TrialTheme.selected.value)
    }
}
