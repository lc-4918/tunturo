package com.tunturo.app.ui.routes

/**
 * Le titre du menu lateral : celui que l'utilisateur a choisi, sinon le titre par defaut, "Tunturo".
 *
 * Le titre par defaut etait "Bibliotheque", et le premier lancement l'ecrivait EN BASE dans la langue du
 * moment (cf. TunturoRepository.ensureSeed) : ceux qui ne l'ont jamais change le portent donc enregistre
 * comme s'ils l'avaient choisi. Ces anciens titres par defaut, dans les huit langues, valent "pas de titre
 * choisi" : ils cedent la place au nouveau sans qu'il faille toucher a la base.
 */
object DrawerTitle {

    /** Les anciens titres par defaut, tels que les ressources des huit langues les donnaient. */
    val LegacyDefaults = setOf("Bibliothèque", "Library", "Biblioteca", "Bibliothek", "Liburutegia")

    /** Le titre a afficher : [custom], sauf s'il est vide ou un ancien titre par defaut. */
    fun resolve(custom: String, default: String): String =
        custom.takeUnless { it.isBlank() || it.trim() in LegacyDefaults } ?: default
}
