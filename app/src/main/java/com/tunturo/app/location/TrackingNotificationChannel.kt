package com.tunturo.app.location

/**
 * Le canal ou se pose la notification permanente du suivi.
 *
 * **Pourquoi deux canaux ordinaires.** Android impose la notification d'un service de localisation des que
 * le suivi tourne, application a l'ecran comprise : on ne peut pas attendre l'arriere-plan pour la poser,
 * sans l'autorisation de localisation "toujours", que Tunturo ne demande pas. Ce qu'on peut faire, c'est la
 * rendre discrete tant que l'application est sous les yeux - sans icone dans la barre d'etat, repliee au
 * bas du volet - et la rendre normale des que l'ecran s'eteint ou que l'application passe derriere : c'est
 * la qu'elle sert, a dire que la position est suivie et a l'arreter.
 *
 * Une alerte (suivi interrompu) passe avant tout : elle doit se voir et sonner.
 */
enum class TrackingNotificationChannel {
    /** Application a l'ecran : importance minimale, pas d'icone dans la barre d'etat. */
    QUIET,

    /** Ecran eteint ou application en arriere-plan : la notification ordinaire. */
    NORMAL,

    /** Le suivi a ete interrompu : canal d'alerte, qui sonne une fois. */
    ALERT;

    companion object {
        fun channelFor(appVisible: Boolean, alerting: Boolean): TrackingNotificationChannel = when {
            alerting -> ALERT
            appVisible -> QUIET
            else -> NORMAL
        }
    }
}
