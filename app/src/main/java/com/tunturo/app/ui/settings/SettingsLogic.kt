package com.tunturo.app.ui.settings

import com.tunturo.app.data.db.ProviderEntity
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.map.flagCodeFor
import com.tunturo.app.map.offline.TileMath

/**
 * Les regles de l'ecran des reglages qui ne dependent pas du dessin : elles se verifient sans composer.
 */

/**
 * Une ligne a interrupteur ET a accordeon : le calcul d'itineraire, l'alerte d'eloignement.
 *
 * | Interrupteur | Chevron | Tap sur la ligne          |
 * |--------------|---------|---------------------------|
 * | eteint       | masque  | allume l'interrupteur     |
 * | allume       | visible | ouvre ou ferme l'accordeon|
 *
 * Allumer ouvre l'accordeon : on allume pour regler, et ce qui se regle est dessous. Eteindre le referme :
 * des reglages qui ne servent a rien valent mieux absents que grises.
 */
internal object ToggleAccordion {

    data class State(val enabled: Boolean, val open: Boolean) {
        /** Le chevron ne se montre que si la ligne a de quoi se deplier. */
        val caretVisible: Boolean get() = enabled

        /** L'accordeon est deplie : allume et ouvert. */
        val expanded: Boolean get() = enabled && open
    }

    /** Un tap sur la ligne, hors interrupteur. */
    fun onRowTap(state: State): State =
        if (!state.enabled) State(enabled = true, open = true) else state.copy(open = !state.open)

    /** Un tap sur l'interrupteur lui-meme. */
    fun onToggle(state: State): State =
        if (state.enabled) State(enabled = false, open = false) else State(enabled = true, open = true)
}

/**
 * Qui voit quoi dans les reglages.
 *
 * **Les comptes n'existent pas encore.** L'onglet Parcours est reserve au mode expert du compte
 * administrateur ; tant qu'il n'y a qu'un compte, celui du telephone, il est administrateur. Le jour ou les
 * comptes arrivent, c'est [CurrentAccountIsAdmin] qu'on remplace, et rien d'autre.
 */
internal object SettingsAccess {

    /** Le compte en cours est-il administrateur ? Toujours, faute de comptes. */
    const val CurrentAccountIsAdmin = true

    fun routesTabVisible(settings: SettingsEntity, isAdmin: Boolean = CurrentAccountIsAdmin): Boolean =
        isAdmin && settings.expertMode
}

/**
 * Ou poser une bulle ouverte depuis une ligne : dessous si elle y tient, dessus sinon, et toujours dans la
 * fenetre. Horizontalement, centree sur la ligne et rabattue dans la fenetre.
 *
 * Toutes les mesures en pixels, en coordonnees de la fenetre.
 */
internal object BubblePlacement {

    fun top(anchorTop: Int, anchorBottom: Int, windowHeight: Int, bubbleHeight: Int, gap: Int): Int {
        val below = anchorBottom + gap
        if (below + bubbleHeight <= windowHeight) return below
        val above = anchorTop - gap - bubbleHeight
        if (above >= 0) return above
        // Ni dessous ni dessus : du cote le plus large, rabattue dans la fenetre.
        val roomBelow = windowHeight - anchorBottom
        return if (roomBelow >= anchorTop) (windowHeight - bubbleHeight).coerceAtLeast(0) else 0
    }

    fun start(anchorLeft: Int, anchorWidth: Int, windowWidth: Int, bubbleWidth: Int): Int {
        val centered = anchorLeft + (anchorWidth - bubbleWidth) / 2
        return centered.coerceIn(0, (windowWidth - bubbleWidth).coerceAtLeast(0))
    }
}

/**
 * L'apercu d'un fond de plan dans sa bulle : une tuile prise a un endroit representatif, et ce que le fond
 * est, en quelques mots.
 */
internal object BasemapPreview {

    /** Une tuile XYZ. */
    data class Tile(val x: Int, val y: Int, val z: Int)

    /** Ce qu'est un fond, pour le decrire. */
    enum class Kind { RASTER, VECTOR, OVERLAY, RELIEF, OFFLINE }

    /** Le niveau de l'apercu : celui d'une vallee et de ses sentiers, ramene a ce que le fond sert. */
    const val PreviewZoom = 13

    /** L'endroit par defaut : Grenoble et ses massifs, ou un fond de randonnee montre ce qu'il sait faire. */
    private val DefaultSpot = 45.1885 to 5.7245

    /** Un endroit parlant par pays, pour les fonds nationaux : un massif ou une region de randonnee. */
    private val CountrySpots = mapOf(
        "fr" to (45.1885 to 5.7245),
        "es" to (43.1900 to -4.8500),
        "hu" to (47.5500 to 18.9500),
        "sk" to (49.1700 to 20.1300),
        "at" to (47.5600 to 13.6500),
        "no" to (61.5000 to 8.5000),
        "be" to (50.2500 to 5.6000),
        "se" to (68.3500 to 18.8300),
        "hr" to (44.8800 to 15.6200),
        "ch" to (46.6200 to 8.0400),
        "de" to (47.4900 to 11.1000),
        "fi" to (60.1700 to 24.9400),
        "si" to (46.3700 to 14.1100),
        "cz" to (50.7300 to 15.7400),
        "gb" to (54.4600 to -3.0900),
        "pl" to (49.3000 to 19.9500),
        "pt" to (38.8000 to -9.3900),
    )

    /** Les types dont une tuile se demande par une simple URL. */
    private val TiledTypes = setOf("XYZ", "WMS", "WMTS")

    fun kind(provider: ProviderEntity): Kind = when {
        provider.type == "MBTILES" -> Kind.OFFLINE
        provider.type == "DEM" -> Kind.RELIEF
        provider.transparent -> Kind.OVERLAY
        provider.type == "VECTOR" || provider.type == "PMTILES" -> Kind.VECTOR
        else -> Kind.RASTER
    }

    /** La tuile de l'apercu, ou null quand le fond ne se montre pas par une tuile image. */
    fun tile(provider: ProviderEntity): Tile? {
        if (provider.type !in TiledTypes) return null
        val (lat, lon) = flagCodeFor(provider)?.let { CountrySpots[it] } ?: DefaultSpot
        val zoom = PreviewZoom.coerceIn(provider.minZoom, provider.maxZoom.coerceAtLeast(provider.minZoom))
        val (x, y) = TileMath.tileAt(lon, lat, zoom)
        return Tile(x, y, zoom)
    }
}
