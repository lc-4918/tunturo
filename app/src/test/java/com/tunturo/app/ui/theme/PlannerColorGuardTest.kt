package com.tunturo.app.ui.theme

import com.tunturo.app.data.repo.TunturoRepository
import com.tunturo.app.ui.components.MapPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Une trace ne peut pas prendre la couleur du parcours calcule ni ses voisines : ni au choix de couleur
 * d'une couche ou d'un dossier, ni a l'import.
 */
class PlannerColorGuardTest {

    /** Mousse : primary claire #536525, sombre #B9CF83 (export du Theme Builder du 2026-11-15). */
    private val mousse = PlannerColorGuard.plannerColors("mousse")

    @Test fun `les couleurs reservees sont les deux primaires de la palette`() {
        assertEquals(listOf("#536525", "#B9CF83"), mousse)
    }

    /** Le vert clair, a 26 de la primaire sombre de Mousse, reste permis : Luc l'a juge assez eloigne. */
    @Test fun `la couleur du parcours et ses voisines sont reservees, les autres non`() {
        assertTrue(PlannerColorGuard.isReserved("#536525", mousse))
        assertTrue(PlannerColorGuard.isReserved("#5A6B2A", mousse))
        assertFalse(PlannerColorGuard.isReserved("#7CB342", mousse))
        assertFalse(PlannerColorGuard.isReserved("#2F9E44", mousse))
        assertFalse(PlannerColorGuard.isReserved("#E8590C", mousse))
    }

    @Test fun `l'ecart se mesure a l'oeil, en Delta E`() {
        assertEquals(0.0, PlannerColorGuard.distance("#536525", "#536525")!!, 1e-9)
        val lightGreen = PlannerColorGuard.distance("#7CB342", "#B9CF83")!!
        assertEquals(25.9, lightGreen, 0.5)
        assertTrue(PlannerColorGuard.distance("#E03131", "#536525")!! > PlannerColorGuard.MinimumDistance)
    }

    @Test fun `le choix de couleur des traces retire les couleurs reservees, et seulement elles`() {
        assertEquals(MapPalette, PlannerColorGuard.allowed(MapPalette, mousse))
        val glacier = PlannerColorGuard.allowed(MapPalette, PlannerColorGuard.plannerColors("glacier"))
        assertEquals(MapPalette - listOf("#546E7A", "#1098AD"), glacier)
    }

    @Test fun `une couleur illisible n'est pas refusee`() {
        assertFalse(PlannerColorGuard.isReserved("pas une couleur", mousse))
    }

    @Test fun `l'import ne donne jamais une couleur reservee`() {
        val glacier = PlannerColorGuard.plannerColors("glacier")
        val reservedInImport = TunturoRepository.Palette.colors.filter { PlannerColorGuard.isReserved(it, glacier) }
        assertTrue("la palette d'import doit avoir une couleur proche de Glacier", reservedInImport.isNotEmpty())
        val used = mutableListOf<String>()
        repeat(12) {
            val picked = TunturoRepository.Palette.pick(used, glacier)
            assertFalse(picked, PlannerColorGuard.isReserved(picked, glacier))
            used += picked
        }
    }
}
