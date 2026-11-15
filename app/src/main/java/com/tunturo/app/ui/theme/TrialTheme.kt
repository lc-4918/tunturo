package com.tunturo.app.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le theme d'essai de TM-3 : remplace les couleurs de l'application par l'une des neuf palettes du
 * Material Theme Builder, pour choisir celle de la marque sur les vraies vues - carte, panneaux, bulles,
 * reglages - plutot que sur des maquettes (cf. TrialColors.kt).
 *
 * **Temporaire.** Propose en mode expert seulement, et retire une fois la palette choisie.
 *
 * **En SharedPreferences, pas en base.** C'est un essai, pas un reglage : une colonne Room aurait exige
 * une migration pour une option destinee a disparaitre. La lecture est synchrone, donc la premiere image
 * porte deja le bon theme (meme raison que ThemePrefs).
 */
object TrialTheme {

    /** Le thème actuel de l'application, sans essai. */
    const val CURRENT = "current"

    /** Une palette d'essai : son identifiant enregistre, son nom affiche, et ses deux jeux. */
    class Palette(val id: String, val displayName: String, val light: ColorScheme, val dark: ColorScheme)

    /** Les palettes, dans l'ordre du choix. Les noms sont ceux des pistes de couleur, non traduits. */
    val palettes: List<Palette> = listOf(
        Palette("ruska", "Ruska", TrialRuskaLight, TrialRuskaDark),
        Palette("ambre", "Ambre", TrialAmbreLight, TrialAmbreDark),
        Palette("airelle", "Airelle", TrialAirelleLight, TrialAirelleDark),
        Palette("myrtille", "Myrtille", TrialMyrtilleLight, TrialMyrtilleDark),
        Palette("lichen", "Lichen", TrialLichenLight, TrialLichenDark),
        Palette("mousse", "Mousse", TrialMousseLight, TrialMousseDark),
        Palette("glacier", "Glacier", TrialGlacierLight, TrialGlacierDark),
        Palette("arctique", "Arctique", TrialArctiqueLight, TrialArctiqueDark),
        Palette("granite", "Granite", TrialGraniteLight, TrialGraniteDark),
    )

    /** Les identifiants proposes : le thème actuel en tete, puis les palettes. */
    val choices: List<String> = listOf(CURRENT) + palettes.map { it.id }

    /** Le nom affiche d'une palette, null pour le thème actuel (libelle traduit, cote ecran). */
    fun displayName(id: String): String? = palettes.firstOrNull { it.id == id }?.displayName

    /** Le jeu de couleurs a poser : celui de la palette [id], ou celui de l'application. */
    fun colorScheme(id: String?, dark: Boolean): ColorScheme {
        val palette = palettes.firstOrNull { it.id == id }
        return when {
            palette == null -> if (dark) TunturoDark else TunturoLight
            dark -> palette.dark
            else -> palette.light
        }
    }

    private const val PREFS_NAME = "trial_theme_prefs"
    private const val KEY_THEME = "trial_theme"

    private val selectedState = MutableStateFlow(CURRENT)

    /** La palette retenue, suivie par le theme de l'application. */
    val selected: StateFlow<String> = selectedState.asStateFlow()

    /** Relit le choix enregistre ; un identifiant inconnu (palette retiree) revient au thème actuel. */
    fun load(context: Context) {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_THEME, CURRENT)
        selectedState.value = stored?.takeIf { it in choices } ?: CURRENT
    }

    /** Retient [id] et l'applique aussitot. */
    fun select(context: Context, id: String) {
        val chosen = id.takeIf { it in choices } ?: CURRENT
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_THEME, chosen) }
        selectedState.value = chosen
    }
}
