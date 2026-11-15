package com.tunturo.app.ui.theme

import com.tunturo.app.ui.routes.DrawerTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le nom "Tunturo" s'affiche toujours dans sa police (Outfit), jusque dans les phrases ; le titre du menu
 * lateral vaut "Tunturo" par defaut, anciens titres par defaut compris.
 */
class BrandNameTest {

    @Test fun `chaque occurrence du nom prend sa police, quelle que soit sa casse`() {
        val text = brandedText("Ouvrez Tunturo puis tunturo, et TUNTURO.")
        val ranges = text.spanStyles.filter { it.item == BrandNameStyle }.map { it.start to it.end }
        assertEquals(listOf(7 to 14, 20 to 27, 32 to 39), ranges)
        assertEquals("Ouvrez Tunturo puis tunturo, et TUNTURO.", text.text)
    }

    @Test fun `un texte sans le nom reste sans style`() {
        assertTrue(brandedText("Bibliothèque").spanStyles.isEmpty())
    }

    @Test fun `le nom seul est entierement dans sa police`() {
        val text = brandedText(BrandName)
        assertEquals(listOf(0 to BrandName.length), text.spanStyles.map { it.start to it.end })
    }

    @Test fun `le titre du menu lateral vaut Tunturo par defaut`() {
        assertEquals("Tunturo", DrawerTitle.resolve("", "Tunturo"))
        assertEquals("Tunturo", DrawerTitle.resolve("   ", "Tunturo"))
    }

    @Test fun `les anciens titres par defaut cedent la place, un titre choisi reste`() {
        listOf("Bibliothèque", "Library", "Biblioteca", "Bibliothek", "Liburutegia").forEach { legacy ->
            assertEquals(legacy, "Tunturo", DrawerTitle.resolve(legacy, "Tunturo"))
        }
        assertEquals("Mes sorties", DrawerTitle.resolve("Mes sorties", "Tunturo"))
    }
}
