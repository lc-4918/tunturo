package com.tunturo.app.ui.settings

import com.tunturo.app.data.db.ProviderEntity
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.data.db.withDefaultRoutePrefs
import com.tunturo.app.map.offline.TileMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les regles de l'ecran des reglages qui se verifient sans composer (cf. SettingsLogic). */
class SettingsLogicTest {

    // ---------- Ligne a interrupteur et accordeon ----------

    @Test fun `eteinte, la ligne cache son chevron et un tap l'allume`() {
        val off = ToggleAccordion.State(enabled = false, open = false)
        assertFalse(off.caretVisible)
        assertFalse(off.expanded)
        val afterTap = ToggleAccordion.onRowTap(off)
        assertTrue(afterTap.enabled)
        assertTrue("allumer ouvre ce qui se regle", afterTap.expanded)
    }

    @Test fun `allumee, un tap sur la ligne ouvre et ferme l'accordeon`() {
        val closed = ToggleAccordion.State(enabled = true, open = false)
        assertTrue(closed.caretVisible)
        val opened = ToggleAccordion.onRowTap(closed)
        assertTrue(opened.enabled)
        assertTrue(opened.expanded)
        val closedAgain = ToggleAccordion.onRowTap(opened)
        assertTrue("la ligne reste allumee", closedAgain.enabled)
        assertFalse(closedAgain.expanded)
    }

    @Test fun `l'interrupteur eteint et referme, ou allume et ouvre`() {
        val open = ToggleAccordion.State(enabled = true, open = true)
        val off = ToggleAccordion.onToggle(open)
        assertFalse(off.enabled)
        assertFalse(off.open)
        val on = ToggleAccordion.onToggle(off)
        assertTrue(on.enabled)
        assertTrue(on.expanded)
    }

    @Test fun `un accordeon ouvert mais eteint ne se deplie pas`() {
        assertFalse(ToggleAccordion.State(enabled = false, open = true).expanded)
    }

    // ---------- Onglet Parcours ----------

    @Test fun `l'onglet Parcours demande le mode expert et le compte administrateur`() {
        val expert = SettingsEntity(expertMode = true)
        val simple = SettingsEntity(expertMode = false)
        assertTrue(SettingsAccess.routesTabVisible(expert, isAdmin = true))
        assertFalse(SettingsAccess.routesTabVisible(expert, isAdmin = false))
        assertFalse(SettingsAccess.routesTabVisible(simple, isAdmin = true))
        assertTrue("faute de comptes, le telephone est administrateur", SettingsAccess.routesTabVisible(expert))
    }

    // ---------- Placement d'une bulle ----------

    @Test fun `la bulle se pose sous la ligne quand elle y tient`() {
        assertEquals(212, BubblePlacement.top(anchorTop = 150, anchorBottom = 200, windowHeight = 1000, bubbleHeight = 300, gap = 12))
    }

    @Test fun `la bulle passe au-dessus quand la place manque dessous`() {
        assertEquals(838 - 12 - 300, BubblePlacement.top(anchorTop = 838, anchorBottom = 900, windowHeight = 1000, bubbleHeight = 300, gap = 12))
    }

    @Test fun `trop grande des deux cotes, la bulle reste dans la fenetre`() {
        val top = BubblePlacement.top(anchorTop = 100, anchorBottom = 150, windowHeight = 400, bubbleHeight = 380, gap = 12)
        assertTrue(top >= 0)
        assertTrue(top + 380 <= 400)
    }

    @Test fun `la bulle se centre sur la ligne et se rabat dans la fenetre`() {
        assertEquals(100, BubblePlacement.start(anchorLeft = 0, anchorWidth = 600, windowWidth = 600, bubbleWidth = 400))
        assertEquals(0, BubblePlacement.start(anchorLeft = -50, anchorWidth = 100, windowWidth = 600, bubbleWidth = 400))
        assertEquals(200, BubblePlacement.start(anchorLeft = 500, anchorWidth = 100, windowWidth = 600, bubbleWidth = 400))
    }

    // ---------- Apercu d'un fond de plan ----------

    private fun provider(
        id: String = "osm", type: String = "XYZ", group: String = "Monde",
        minZoom: Int = 0, maxZoom: Int = 19, transparent: Boolean = false,
    ) = ProviderEntity(id, "Fond", group, type, "https://example.test/{z}/{x}/{y}.png",
        minZoom = minZoom, maxZoom = maxZoom, transparent = transparent)

    @Test fun `l'apercu d'un fond mondial se prend a Grenoble, au zoom 13`() {
        val tile = BasemapPreview.tile(provider())!!
        assertEquals(BasemapPreview.PreviewZoom, tile.z)
        assertEquals(TileMath.tileAt(5.7245, 45.1885, 13), tile.x to tile.y)
    }

    @Test fun `l'apercu d'un fond national se prend dans son pays`() {
        val spain = BasemapPreview.tile(provider(id = "ign_es", type = "WMS", group = "Pays"))!!
        val france = BasemapPreview.tile(provider())!!
        assertTrue("l'Espagne est a l'ouest de Grenoble", spain.x < france.x)
    }

    @Test fun `le zoom de l'apercu reste dans ce que le fond sert`() {
        assertEquals(10, BasemapPreview.tile(provider(maxZoom = 10))!!.z)
        assertEquals(15, BasemapPreview.tile(provider(minZoom = 15, maxZoom = 18))!!.z)
    }

    @Test fun `un fond vectoriel, un relief ou un fichier hors ligne n'ont pas de tuile d'apercu`() {
        assertNull(BasemapPreview.tile(provider(type = "VECTOR")))
        assertNull(BasemapPreview.tile(provider(type = "DEM")))
        assertNull(BasemapPreview.tile(provider(type = "MBTILES")))
    }

    @Test fun `chaque fond se range dans sa famille`() {
        assertEquals(BasemapPreview.Kind.RASTER, BasemapPreview.kind(provider()))
        assertEquals(BasemapPreview.Kind.VECTOR, BasemapPreview.kind(provider(type = "VECTOR")))
        assertEquals(BasemapPreview.Kind.OVERLAY, BasemapPreview.kind(provider(transparent = true)))
        assertEquals(BasemapPreview.Kind.RELIEF, BasemapPreview.kind(provider(type = "DEM")))
        assertEquals(BasemapPreview.Kind.OFFLINE, BasemapPreview.kind(provider(type = "MBTILES")))
    }

    // ---------- Preferences du calcul ----------

    @Test fun `reinitialiser rend aux cinq disciplines leurs preferences d'origine`() {
        val defaults = SettingsEntity()
        val tuned = defaults.copy(
            routePrefsRoad = "roads,avoid,paved", routePrefsGravel = "roads,avoid,paved",
            routePrefsHybrid = "roads,avoid,paved", routePrefsMtb = "roads,avoid,paved",
            routePrefsFoot = "roads,avoid,paved", expertMode = true,
        )
        val reset = tuned.withDefaultRoutePrefs()
        assertEquals(defaults.routePrefsRoad, reset.routePrefsRoad)
        assertEquals(defaults.routePrefsGravel, reset.routePrefsGravel)
        assertEquals(defaults.routePrefsHybrid, reset.routePrefsHybrid)
        assertEquals(defaults.routePrefsMtb, reset.routePrefsMtb)
        assertEquals(defaults.routePrefsFoot, reset.routePrefsFoot)
        assertTrue("le reste ne bouge pas", reset.expertMode)
    }
}
