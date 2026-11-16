package com.tunturo.app.domain.geo

import com.tunturo.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le point courant du profil pose sur la carte.
 *
 * Retour de terrain : sur une trace de 12 000 km, le point tombait a plusieurs kilometres de la trace
 * dessinee. Le profil decime a deux mille points n'en garde qu'un tous les six kilometres, et le point
 * interpole entre deux d'entre eux se posait sur la corde qui les relie.
 */
class TrackCursorTest {

    /**
     * Une trace en creneaux : elle avance par pas de 70 m environ, et monte ou descend de 5 km tous les
     * quarante points. Decimee a 200 points, elle n'en garde qu'un tous les vingt : les cordes entre deux
     * echantillons coupent en biais a travers les creneaux - ce qui se passe, a plus grande echelle, sur un
     * parcours de plusieurs milliers de kilometres qui fait des detours.
     */
    private val crenellated: List<TrackPoint> = List(4_001) { index ->
        val lon = 2.0 + index * 0.0009
        val lat = 43.3 + if ((index / 40) % 2 == 0) 0.0 else 0.045
        TrackPoint(lon, lat)
    }
    private val coarse = TrackMath.compute(crenellated, maxPoints = 200).samples
    private val detailed = TrackMath.compute(crenellated, maxPoints = 0, ignoreStops = false).samples

    /** Distance du point a la trace REELLE, en metres. */
    private fun offTrackM(lon: Double, lat: Double): Double =
        requireNotNull(TrackEdit.locate(listOf(crenellated), lon, lat)).awayM

    @Test fun `les deux profils portent le meme kilometrage`() {
        assertEquals(coarse.last().x, detailed.last().x, 1e-6)
    }

    /** Le defaut lui-meme : interpole sur le profil decime, le point quitte la trace. */
    @Test fun `sur le profil decime le point tombe a cote de la trace`() {
        // Au milieu de la corde qui relie un echantillon bas (rang 20) a un echantillon haut (rang 40).
        val along = (coarse[1].x + coarse[2].x) / 2
        val point = TrackCursor.pointAt(along, coarse, null)
        assertNotNull(point)
        val awayM = offTrackM(point!!.lon, point.lat)
        assertTrue("a cote de la trace : $awayM m", awayM > 100.0)
    }

    /** La correction : sur les echantillons complets, le point est SUR la trace, a tout kilometrage. */
    @Test fun `sur les echantillons complets le point est sur la trace`() {
        for (fraction in listOf(0.0, 0.13, 0.37, 0.5, 0.81, 1.0)) {
            val point = TrackCursor.pointAt(detailed.last().x * fraction, coarse, detailed)!!
            assertTrue("fraction $fraction", offTrackM(point.lon, point.lat) < 1.0)
        }
    }

    /** Tant que les echantillons complets ne sont pas charges, le profil decime sert. */
    @Test fun `sans echantillons complets le profil decime sert`() {
        assertEquals(coarse, TrackCursor.reference(coarse, null))
        assertEquals(coarse, TrackCursor.reference(coarse, emptyList()))
        assertEquals(detailed, TrackCursor.reference(coarse, detailed))
    }

    /** Un tap sur la trace donne le kilometrage du point touche, et le point revient a l'endroit tape. */
    @Test fun `un tap sur la trace revient a l endroit tape`() {
        val tapped = crenellated[1_237]
        val along = TrackCursor.alongOf(tapped.lon, tapped.lat, coarse, detailed)!!
        val point = TrackCursor.pointAt(along, coarse, detailed)!!
        assertTrue(TrackMath.haversine(tapped.lon, tapped.lat, point.lon, point.lat) < 1.0)
    }
}
