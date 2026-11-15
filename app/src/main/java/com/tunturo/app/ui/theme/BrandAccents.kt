package com.tunturo.app.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * La couleur de marque aux endroits ou Material 3, de lui-meme, n'en met pas.
 *
 * L'essai des neuf palettes (TM-3) n'avait presque rien change a l'ecran : les boutons de la carte etaient
 * noirs sur blanc, et la couleur primaire ne servait qu'aux valeurs, curseurs et puces. Luc a choisi le
 * 2026-11-15 les endroits qui portent la couleur : boutons de la carte (cf. rememberMapChrome), petits
 * boutons ronds, boutons secondaires, selections et onglets des reglages (cf. settingsPaletteOf).
 */

/**
 * Les couleurs d'un bouton secondaire (Annuler...) : l'aplat secondaryContainer de la planche des
 * palettes, sans contour - l'aplat suffit a le dessiner.
 */
@Composable
fun secondaryButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

/** Opacite d'un petit bouton pose sur une image, une miniature ou dans une infobulle : la vue reste lisible dessous. */
const val TranslucentSmallButtonAlpha = 0.75f

/**
 * Le fond d'un petit bouton rond (croix, actualiser, agrandir, epingler...) : primary, comme le bouton
 * "Calculer" de la planche des palettes ; translucide quand il est pose sur une image ou une miniature.
 */
@Composable
fun smallButtonBackground(translucent: Boolean = false): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = if (translucent) TranslucentSmallButtonAlpha else 1f)

/** Le dessin d'un petit bouton rond : onPrimary. */
@Composable
fun smallButtonContent(): Color = MaterialTheme.colorScheme.onPrimary
