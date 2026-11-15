package com.tunturo.app.ui.settings

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.tunturo.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La fenetre d'attente de la sauvegarde : elle dit ce qui se passe, qu'il faut rester, et garde l'ecran
 * allume tant qu'elle est la - puis rend l'ecran a son etat d'avant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class BackupProgressDialogTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `la sauvegarde en cours se dit, avec la consigne de rester`() {
        compose.setContent { BackupProgressDialog(BackupWork.WRITING) }
        compose.onNodeWithText(context.getString(R.string.backup_writing)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.backup_keep_open)).assertIsDisplayed()
    }

    @Test fun `la restauration en cours a son propre titre`() {
        compose.setContent { BackupProgressDialog(BackupWork.RESTORING) }
        compose.onNodeWithText(context.getString(R.string.backup_restoring)).assertIsDisplayed()
    }

    @Test fun `l'ecran reste allume pendant le travail, puis revient a son etat`() {
        var work by mutableStateOf<BackupWork?>(BackupWork.WRITING)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            work?.let { BackupProgressDialog(it) }
        }
        compose.waitForIdle()
        assertTrue(view.keepScreenOn)
        work = null
        compose.waitForIdle()
        assertFalse(view.keepScreenOn)
        assertEquals(0, compose.onAllNodesWithTag("backup_progress").fetchSemanticsNodes().size)
    }
}
