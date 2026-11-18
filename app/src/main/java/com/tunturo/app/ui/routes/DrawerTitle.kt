package com.tunturo.app.ui.routes

/**
 * Le titre du menu lateral, autrefois reglable : il vaut desormais toujours "Tunturo" (le reglage a quitte
 * l'onglet Systeme le 2026-11-18). Ne reste ici que ce dont la mise a niveau de la base a encore besoin.
 *
 * Le titre par defaut etait "Bibliotheque", et le premier lancement l'ecrivait EN BASE dans la langue du
 * moment (cf. TunturoRepository.ensureSeed) : ceux qui ne l'ont jamais change le portent donc enregistre
 * comme s'ils l'avaient choisi. Ces anciens titres par defaut, dans les huit langues, valent "pas de titre
 * choisi" : ils cedent la place au nouveau sans qu'il faille toucher a la base.
 */
object DrawerTitle {

    /** Les anciens titres par defaut, tels que les ressources des huit langues les donnaient. */
    val LegacyDefaults = setOf("Bibliothèque", "Library", "Biblioteca", "Bibliothek", "Liburutegia")

}
