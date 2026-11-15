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

    /** La palette posee par defaut : Mousse, pressentie par Luc le 2026-11-15. */
    const val DEFAULT = "mousse"

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

    /**
     * Les identifiants proposes. Plus de "thème actuel" : la nouvelle identite remplace entierement
     * l'ancien theme bleu (decision de Luc, 2026-11-15) ; reste a choisir laquelle des neuf.
     */
    val choices: List<String> = palettes.map { it.id }

    /** Le nom affiche d'une palette. */
    fun displayName(id: String): String = palettes.firstOrNull { it.id == id }?.displayName ?: id

    /** La palette [id], ou celle par defaut pour un identifiant inconnu. */
    private fun paletteOf(id: String?): Palette =
        palettes.firstOrNull { it.id == id } ?: palettes.first { it.id == DEFAULT }

    /**
     * Le jeu de couleurs a poser pour la palette [id].
     *
     * Les textes prennent la couleur de la palette, et non les gris presque neutres de Material 3 :
     * onSurface devient la teinte foncee de la primaire (onPrimaryContainer), onSurfaceVariant la
     * secondaire. Demande de Luc : toutes les couleurs de police de l'application doivent changer.
     */
    fun colorScheme(id: String?, dark: Boolean): ColorScheme {
        val palette = paletteOf(id)
        val scheme = if (dark) palette.dark else palette.light
        return scheme.copy(
            onSurface = scheme.onPrimaryContainer,
            onBackground = scheme.onPrimaryContainer,
            onSurfaceVariant = scheme.secondary,
        )
    }

    private const val PREFS_NAME = "trial_theme_prefs"
    private const val KEY_THEME = "trial_theme"

    private val selectedState = MutableStateFlow(DEFAULT)

    /** La palette retenue, suivie par le theme de l'application. */
    val selected: StateFlow<String> = selectedState.asStateFlow()

    /** Relit le choix enregistre ; un identifiant inconnu (palette retiree, ancien "current") donne la palette par defaut. */
    fun load(context: Context) {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_THEME, DEFAULT)
        selectedState.value = stored?.takeIf { it in choices } ?: DEFAULT
    }

    /** Retient [id] et l'applique aussitot. */
    fun select(context: Context, id: String) {
        val chosen = id.takeIf { it in choices } ?: DEFAULT
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_THEME, chosen) }
        selectedState.value = chosen
    }
}
