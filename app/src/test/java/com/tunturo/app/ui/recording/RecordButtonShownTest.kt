package com.tunturo.app.ui.recording

import com.tunturo.app.domain.geo.RecordingPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le bouton d'enregistrement : le reglage le pose, et un enregistrement en cours le garde a l'ecran. */
class RecordButtonShownTest {

    @Test fun `le reglage pose le bouton`() {
        assertTrue(recordButtonShown(enabledInSettings = true, phase = RecordingPhase.IDLE))
        assertFalse(recordButtonShown(enabledInSettings = false, phase = RecordingPhase.IDLE))
    }

    /** Masquer le bouton n'enterre pas un enregistrement qui tourne : il faut pouvoir le mettre en pause. */
    @Test fun `un enregistrement en cours garde le bouton, meme masque`() {
        assertTrue(recordButtonShown(enabledInSettings = false, phase = RecordingPhase.RECORDING))
        assertFalse(recordButtonShown(enabledInSettings = false, phase = RecordingPhase.PAUSED))
    }
}
