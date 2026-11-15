package com.tunturo.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Les chemins d'images venus d'une sauvegarde Trailog (ou d'un autre profil Android) se retrouvent dans
 * le dossier `images` de Tunturo ; tout le reste est rendu tel quel.
 */
class LocalImagePathTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun imagesDirectory(): File = temporaryFolder.newFolder("files", "images")

    @Test fun `un chemin de Trailog mene a la photo restauree dans Tunturo`() {
        val directory = imagesDirectory()
        val restored = File(directory, "img_1786_photo.jpg").apply { writeText("jpeg") }
        val trailogPath = "/data/user/0/fr.lc4918.trailog/files/images/img_1786_photo.jpg"
        assertEquals(restored.path, LocalImagePath.resolve(trailogPath, directory))
    }

    @Test fun `un chemin d'un autre profil Android mene aussi a la photo`() {
        val directory = imagesDirectory()
        val restored = File(directory, "avatar.png").apply { writeText("png") }
        val otherProfilePath = "/data/user/10/com.tunturo.app/files/images/avatar.png"
        assertEquals(restored.path, LocalImagePath.resolve(otherProfilePath, directory))
    }

    @Test fun `un chemin qui existe est rendu tel quel`() {
        val directory = imagesDirectory()
        val existing = File(directory, "img_1_a.jpg").apply { writeText("jpeg") }
        assertEquals(existing.path, LocalImagePath.resolve(existing.path, directory))
    }

    @Test fun `une photo absente partout garde son chemin, l'infobulle dira introuvable`() {
        val directory = imagesDirectory()
        val deadPath = "/data/user/0/fr.lc4918.trailog/files/images/absente.jpg"
        assertEquals(deadPath, LocalImagePath.resolve(deadPath, directory))
    }

    @Test fun `une adresse web et un chemin hors du dossier images ne sont pas touches`() {
        val directory = imagesDirectory()
        File(directory, "photo.jpg").writeText("jpeg")
        assertEquals("https://example.org/files/images/photo.jpg",
            LocalImagePath.resolve("https://example.org/files/images/photo.jpg", directory))
        assertEquals("/sdcard/DCIM/photo.jpg", LocalImagePath.resolve("/sdcard/DCIM/photo.jpg", directory))
    }

    @Test fun `un nom qui remonte ou descend dans un sous-dossier n'est pas suivi`() {
        val directory = imagesDirectory()
        File(directory.parentFile, "secret.db").writeText("db")
        val climbing = "/data/user/0/fr.lc4918.trailog/files/images/../secret.db"
        assertEquals(climbing, LocalImagePath.resolve(climbing, directory))
        val nested = "/data/user/0/fr.lc4918.trailog/files/images/sub/photo.jpg"
        assertEquals(nested, LocalImagePath.resolve(nested, directory))
    }

    @Test fun `sans dossier connu, rien n'est redirige`() {
        val deadPath = "/data/user/0/fr.lc4918.trailog/files/images/photo.jpg"
        assertEquals(deadPath, LocalImagePath.resolve(deadPath, null))
    }
}
