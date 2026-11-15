package com.tunturo.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * La couleur de marque montree a des endroits ou l'application, aujourd'hui, n'en met pas.
 *
 * **Pourquoi.** L'essai des neuf palettes (TM-3) n'a presque rien change a l'ecran : les boutons de la
 * carte sont noirs sur blanc par conception, les textes et les surfaces de Material 3 sont des gris a peine
 * teintes, et la couleur primaire ne servait qu'aux valeurs, curseurs et puces. Une palette ne pouvait donc
 * pas se juger. Luc a choisi le 2026-11-15 quatre endroits qui portent la couleur : les boutons de la carte,
 * le bouton d'action principal, les entetes des panneaux, les selections et onglets.
 *
 * **Toujours actifs depuis le 2026-11-15** : Luc ne veut pas garder l'ancien theme, la nouvelle identite
 * remplace tout. Le drapeau reste le temps de l'essai des palettes ; une fois la palette choisie, les
 * branches "sans accents" disparaitront avec lui.
 */
val LocalBrandAccents = staticCompositionLocalOf { true }

/**
 * Les couleurs d'un bouton secondaire (Annuler...) : l'aplat secondaryContainer de la planche des
 * palettes avec les accents de marque, celles d'un bouton en contour sinon.
 */
@Composable
fun secondaryButtonColors(): ButtonColors =
    if (LocalBrandAccents.current) {
        ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        ButtonDefaults.outlinedButtonColors()
    }

/** Le contour d'un bouton secondaire : [default] sans accents de marque, aucun avec (l'aplat suffit). */
@Composable
fun secondaryButtonBorder(default: BorderStroke?): BorderStroke? = if (LocalBrandAccents.current) null else default
