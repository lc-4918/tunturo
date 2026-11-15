package com.tunturo.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Les deux jeux de couleurs de l'application : la palette **Mousse** de TM-3 (design commun Tunturo),
 * choisie par Luc le 2026-11-15 parmi les neuf essayees dans l'application. Couleur source #6B7F3A,
 * export du Material Theme Builder (style Tonal Spot) recopie tel quel, chaque role de Material 3 pose -
 * sans quoi les roles absents retombent sur le violet par defaut de Material.
 *
 * Une seule retouche a l'export : les TEXTES prennent la couleur de la palette, et non les gris presque
 * neutres de Material 3. onSurface et onBackground sont la teinte foncee de la primaire
 * (onPrimaryContainer), onSurfaceVariant la secondaire. Demande de Luc : toutes les couleurs de police
 * de l'application changent avec l'identite.
 *
 * Le meme jeu sert a Trailmap (variables --tm-* de front/src/styles.scss) : les deux se changent ensemble.
 */

internal val TunturoLight = lightColorScheme(
    primary = Color(0xFF536525),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5EC9C),
    onPrimaryContainer = Color(0xFF3C4D0F),
    inversePrimary = Color(0xFFB9CF83),
    secondary = Color(0xFF5A6147),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE6C5),
    onSecondaryContainer = Color(0xFF434A31),
    tertiary = Color(0xFF39665F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCECE3),
    onTertiaryContainer = Color(0xFF204E48),
    background = Color(0xFFFAFAEE),
    onBackground = Color(0xFF3C4D0F),
    surface = Color(0xFFFAFAEE),
    onSurface = Color(0xFF3C4D0F),
    surfaceVariant = Color(0xFFE2E4D4),
    onSurfaceVariant = Color(0xFF5A6147),
    surfaceTint = Color(0xFF536525),
    inverseSurface = Color(0xFF303129),
    inverseOnSurface = Color(0xFFF2F1E5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF76786B),
    outlineVariant = Color(0xFFC6C8B8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAFAEE),
    surfaceDim = Color(0xFFDBDBCF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F4E8),
    surfaceContainer = Color(0xFFEFEFE2),
    surfaceContainerHigh = Color(0xFFE9E9DD),
    surfaceContainerHighest = Color(0xFFE3E3D7),
)

internal val TunturoDark = darkColorScheme(
    primary = Color(0xFFB9CF83),
    onPrimary = Color(0xFF273500),
    primaryContainer = Color(0xFF3C4D0F),
    onPrimaryContainer = Color(0xFFD5EC9C),
    inversePrimary = Color(0xFF536525),
    secondary = Color(0xFFC3CAAA),
    onSecondary = Color(0xFF2C331C),
    secondaryContainer = Color(0xFF434A31),
    onSecondaryContainer = Color(0xFFDFE6C5),
    tertiary = Color(0xFFA1D0C7),
    onTertiary = Color(0xFF023731),
    tertiaryContainer = Color(0xFF204E48),
    onTertiaryContainer = Color(0xFFBCECE3),
    background = Color(0xFF12140D),
    onBackground = Color(0xFFD5EC9C),
    surface = Color(0xFF12140D),
    onSurface = Color(0xFFD5EC9C),
    surfaceVariant = Color(0xFF45483C),
    onSurfaceVariant = Color(0xFFC3CAAA),
    surfaceTint = Color(0xFFB9CF83),
    inverseSurface = Color(0xFFE3E3D7),
    inverseOnSurface = Color(0xFF303129),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF909284),
    outlineVariant = Color(0xFF45483C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF383A32),
    surfaceDim = Color(0xFF12140D),
    surfaceContainerLowest = Color(0xFF0D0F08),
    surfaceContainerLow = Color(0xFF1B1C15),
    surfaceContainer = Color(0xFF1F2019),
    surfaceContainerHigh = Color(0xFF292B23),
    surfaceContainerHighest = Color(0xFF34362D),
)
