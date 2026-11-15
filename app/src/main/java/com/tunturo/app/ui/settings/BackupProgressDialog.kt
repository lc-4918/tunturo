package com.tunturo.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.tunturo.app.R
import com.tunturo.app.ui.components.BusySpinner

/** Ce que l'ecran des reglages est en train d'ecrire ou de relire. */
enum class BackupWork { WRITING, RESTORING }

/**
 * La fenetre qui tient l'utilisateur pendant une sauvegarde ou une restauration.
 *
 * **Pourquoi elle existe** : une sauvegarde de 58 traces prend plusieurs minutes, et rien ne le disait.
 * Quitter l'application avant la fin laissait une archive coupee net, sans sa table des matieres, qui
 * ressemblait en tout a une vraie sauvegarde - jusqu'au jour ou l'on voulait la restaurer, et ou elle
 * etait refusee. C'est arrive (2026-11-15).
 *
 * **Elle ne se ferme pas** : ni le retour ni un appui a cote ne la congedient ; elle part quand le
 * travail est fini. **Elle garde l'ecran allume** : un ecran qui s'eteint met l'application en
 * arriere-plan, ou le systeme peut l'arreter. Le drapeau est celui de la VUE, pas de la fenetre : il
 * s'ajoute a celui que la carte pose pour le suivi GPS sans risquer de l'effacer en partant.
 */
@Composable
fun BackupProgressDialog(work: BackupWork) {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier.testTag("backup_progress"),
        title = {
            Text(stringResource(when (work) {
                BackupWork.WRITING -> R.string.backup_writing
                BackupWork.RESTORING -> R.string.backup_restoring
            }))
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Le rond fait 40 % de la largeur de ce qui l'accueille (cf. BusySpinner) : 64 dp ici.
                Box(Modifier.size(width = 160.dp, height = 72.dp)) { BusySpinner() }
                Text(stringResource(R.string.backup_keep_open), Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
    )
}
