package com.tunturo.app.data

import android.content.Context
import androidx.core.content.edit

/**
 * Le dossier "Mes parcours", ou se rangent les enregistrements : son identifiant, pose a sa creation.
 *
 * En SharedPreferences et non dans les reglages, pour la raison qui en a sorti le drapeau de la
 * demonstration (cf. [DemoPrefs]) : la ligne des reglages se reecrit en entier depuis des copies, et un
 * identifiant perdu ferait creer un second dossier.
 */
object RecordingPrefs {
    private const val PREFS_NAME = "recording_prefs"
    private const val KEY_FOLDER_ID = "folder_id"

    fun folderId(context: Context): Long? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_FOLDER_ID, -1L).takeIf { it >= 0 }

    fun setFolderId(context: Context, folderId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putLong(KEY_FOLDER_ID, folderId)
        }
    }
}
