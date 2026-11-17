package com.tunturo.app.location

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le canal de la notification du suivi : discrete application a l'ecran, normale ecran eteint ou en
 * arriere-plan, et l'alerte d'un suivi interrompu avant tout.
 */
class TrackingNotificationChannelTest {

    @Test fun `application a l'ecran, la notification est discrete`() {
        assertEquals(TrackingNotificationChannel.QUIET, TrackingNotificationChannel.channelFor(appVisible = true, alerting = false))
    }

    @Test fun `ecran eteint ou application en arriere-plan, la notification est normale`() {
        assertEquals(TrackingNotificationChannel.NORMAL, TrackingNotificationChannel.channelFor(appVisible = false, alerting = false))
    }

    @Test fun `un suivi interrompu s'annonce sur le canal d'alerte, application a l'ecran ou non`() {
        assertEquals(TrackingNotificationChannel.ALERT, TrackingNotificationChannel.channelFor(appVisible = true, alerting = true))
        assertEquals(TrackingNotificationChannel.ALERT, TrackingNotificationChannel.channelFor(appVisible = false, alerting = true))
    }
}
