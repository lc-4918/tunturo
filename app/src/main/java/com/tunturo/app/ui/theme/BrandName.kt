package com.tunturo.app.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tunturo.app.R

/**
 * La police du nom "Tunturo" : Outfit SemiBold, celle du mot-symbole de la maquette "Tunturo - identite"
 * (TM-3). Decision de Luc, 2026-11-15 : c'est la police officielle et UNIQUE du nom, partout ou il
 * s'affiche dans l'application - titre du menu lateral, "A propos", et jusque dans les phrases.
 *
 * Embarquee, comme Inter (cf. Type.kt) : l'application fonctionne hors ligne. Un seul fichier statique,
 * la seule graisse dont le nom se sert. Licence : assets/licenses/outfit-OFL.txt.
 *
 * Ce que la police ne peut pas atteindre : le nom sous l'icone du lanceur et dans les notifications, que
 * le systeme ecrit dans sa propre police.
 */
val BrandFontFamily = FontFamily(Font(R.font.outfit_semibold, FontWeight.SemiBold))

/** Le nom de la marque, tel qu'il s'ecrit. */
const val BrandName = "Tunturo"

/** Le style d'une occurrence du nom. */
val BrandNameStyle = SpanStyle(fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold)

/**
 * [text] avec chaque occurrence du nom dans sa police, quelle que soit sa casse ("Tunturo", "tunturo",
 * "TUNTURO") ; le reste du texte garde le style de l'endroit ou il s'affiche.
 */
fun brandedText(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    var start = text.indexOf(BrandName, ignoreCase = true)
    while (start >= 0) {
        addStyle(BrandNameStyle, start, start + BrandName.length)
        start = text.indexOf(BrandName, start + BrandName.length, ignoreCase = true)
    }
}
