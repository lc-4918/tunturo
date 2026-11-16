package com.tunturo.app.domain.geo

import com.tunturo.app.domain.model.Sample

/**
 * Le point courant du profil, pose sur la carte : ou il tombe, et quel kilometrage donne un tap.
 *
 * **Pourquoi pas les echantillons du profil.** Ils sont decimes a deux mille points pour le graphique.
 * Sur une trace de 12 000 km, cela fait un echantillon tous les six kilometres : interpole entre deux
 * d'entre eux, le point courant se posait sur la CORDE qui les relie, a plusieurs kilometres de la trace
 * dessinee des que celle-ci tournait (retour de terrain sur un Cap Nord).
 *
 * Les echantillons COMPLETS de la trace sont deja charges en arriere-plan a l'ouverture du profil (cf.
 * MainViewModel.loadHighlightSamples) : on s'en sert des qu'ils sont la, et l'on retombe sur le profil
 * decime en attendant. Les deux portent le meme kilometrage cumule - calcule sur la geometrie complete,
 * avant decimation (cf. TrackMath.compute) - si bien qu'un kilometrage lu sur l'un vaut sur l'autre.
 */
object TrackCursor {

    /** Les echantillons qui font foi : les complets s'ils sont la, le profil decime sinon. */
    fun reference(coarse: List<Sample>?, detailed: List<Sample>?): List<Sample>? =
        detailed?.takeIf { it.isNotEmpty() } ?: coarse

    /** Le point de la trace au kilometrage [alongM]. */
    fun pointAt(alongM: Double, coarse: List<Sample>?, detailed: List<Sample>?): Sample? =
        reference(coarse, detailed)?.let { TrackMath.sampleAt(it, alongM) }

    /** Le kilometrage du point de la trace le plus proche de (lon, lat). */
    fun alongOf(lon: Double, lat: Double, coarse: List<Sample>?, detailed: List<Sample>?): Double? =
        reference(coarse, detailed)?.let { TrackMeasure.project(it, lon, lat)?.alongM }
}
