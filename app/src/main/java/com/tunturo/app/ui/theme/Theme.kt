package com.tunturo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Le theme de l'application, pose par lui seul : couleurs (cf. Color.kt), texte (Type.kt) et arrondis
 * (Shape.kt). Un ecran lit MaterialTheme.colorScheme, .typography et .shapes, et n'a pas de taille de
 * texte ni d'arrondi a lui.
 *
 * themePref : "system" | "light" | "dark".
 */
@Composable
fun TunturoTheme(themePref: String = "light", content: @Composable () -> Unit) {
    // Le theme d'essai de TM-3, s'il y en a un (cf. TrialTheme) ; sinon les couleurs de l'application.
    val trialTheme by TrialTheme.selected.collectAsState()
    MaterialTheme(
        colorScheme = TrialTheme.colorScheme(trialTheme, isDarkTheme(themePref)),
        typography = TunturoTypography,
        shapes = TunturoShapes,
    ) {
        // Les accents de marque ne s'allument qu'avec une palette d'essai (cf. BrandAccents).
        CompositionLocalProvider(LocalBrandAccents provides (trialTheme != TrialTheme.CURRENT), content = content)
    }
}

/** Le theme demande se resout-il en sombre ? "system" suit le reglage de l'appareil. */
@Composable
fun isDarkTheme(themePref: String?): Boolean = when (themePref) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}
