package com.tunturo.app.ui.theme

import androidx.compose.ui.graphics.toArgb
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Les couleurs qu'une trace de la bibliotheque ne peut pas prendre : celle du parcours calcule, et ses
 * voisines.
 *
 * Le parcours du calculateur d'itineraire et ses pastilles se dessinent dans la couleur primary du theme
 * (cf. PlannerEffects). Une trace importee ou repeinte dans une couleur proche s'y confondrait sur la
 * carte : on ne saurait plus lequel des deux traits est le trajet calcule. Demande de Luc, 2026-11-15.
 *
 * **"Proche" se mesure a l'oeil, pas au code hexadecimal** : l'ecart dans l'espace CIELAB (Delta E 1976),
 * ou une distance egale se voit a peu pres egale quelle que soit la teinte. En dessous de
 * [MinimumDistance], deux traits fins sur une carte se confondent ; au-dessus, ils se distinguent.
 * Seuil fixe avec Luc : le vert clair #7CB342 est a 26 de Mousse (de sa primaire sombre #B9CF83), et il
 * l'a juge "assez eloigne" ; sur Glacier, le bleu-gris #546E7A (15) et le cyan #1098AD (19) sont retires.
 *
 * Les deux primaires de la palette sont reservees, la claire et la sombre : le theme sombre dessine le
 * parcours dans la seconde, et une trace garde sa couleur d'un theme a l'autre.
 */
object PlannerColorGuard {

    /** L'ecart en dessous duquel une couleur de trace se confond avec le parcours calcule. */
    const val MinimumDistance = 20.0

    /** Les couleurs du parcours calcule, en "#RRGGBB" : la primaire claire puis la sombre. */
    val plannerColors: List<String> = listOf(TunturoLight, TunturoDark).map { hexOf(it.primary.toArgb()) }

    /** [color] est-elle trop proche de l'une des [reserved] ? Une couleur illisible n'est pas refusee. */
    fun isReserved(color: String, reserved: List<String> = plannerColors): Boolean {
        val lab = labOf(color) ?: return false
        return reserved.any { reservedColor ->
            val other = labOf(reservedColor) ?: return@any false
            distance(lab, other) < MinimumDistance
        }
    }

    /** Les [colors] qu'une trace peut prendre, dans leur ordre. */
    fun allowed(colors: List<String>, reserved: List<String> = plannerColors): List<String> =
        colors.filterNot { isReserved(it, reserved) }

    /** L'ecart Delta E 1976 entre deux couleurs "#RRGGBB", ou null si l'une ne se lit pas. */
    fun distance(first: String, second: String): Double? {
        val firstLab = labOf(first) ?: return null
        val secondLab = labOf(second) ?: return null
        return distance(firstLab, secondLab)
    }

    private fun distance(first: DoubleArray, second: DoubleArray): Double =
        sqrt((0..2).sumOf { index -> (first[index] - second[index]).pow(2) })

    private fun hexOf(argb: Int): String = "#%06X".format(0xFFFFFF and argb)

    /** La couleur dans l'espace CIELAB (illuminant D65), ou null si [color] n'est pas un "#RRGGBB". */
    private fun labOf(color: String): DoubleArray? {
        val digits = color.removePrefix("#")
        if (digits.length != 6) return null
        val rgb = digits.toLongOrNull(16) ?: return null
        val channels = listOf(16, 8, 0).map { shift -> linearize(((rgb shr shift) and 0xFF) / 255.0) }
        val (red, green, blue) = channels
        val x = (red * 0.4124 + green * 0.3576 + blue * 0.1805) / 0.95047
        val y = red * 0.2126 + green * 0.7152 + blue * 0.0722
        val z = (red * 0.0193 + green * 0.1192 + blue * 0.9505) / 1.08883
        val fx = labCurve(x)
        val fy = labCurve(y)
        val fz = labCurve(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun linearize(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    private fun labCurve(value: Double): Double =
        if (value > 0.008856) cbrt(value) else 7.787 * value + 16.0 / 116.0
}
