package com.tunturo.app.data.repo

import java.io.File

/**
 * Retrouve une image locale dont le chemin enregistre ne mene plus nulle part.
 *
 * Les photos de points et l'avatar sont enregistres par leur chemin COMPLET, qui contient le dossier
 * prive de l'application, donc son nom de paquet : `/data/user/0/fr.lc4918.trailog/files/images/...`
 * pour une sauvegarde faite par Trailog. Restauree dans Tunturo, l'image est bien revenue dans le dossier
 * `images` de Tunturo, mais le chemin pointe toujours vers celui de Trailog, et l'infobulle affichait
 * "image introuvable". Meme chose pour une sauvegarde restauree sous un autre profil Android
 * (`/data/user/10/...`).
 *
 * On corrige a la lecture plutot qu'en reecrivant les donnees : les chemins vivent dans les fichiers
 * GeoJSON des couches comme dans la base (avatar), et une reecriture de masse au moment de la
 * restauration serait du code qui ne sert qu'une fois et dont la faute se paierait en donnees. Ici, un
 * chemin qui existe est rendu tel quel ; seul un chemin mort dont l'image se trouve dans notre dossier
 * est redirige.
 */
object LocalImagePath {

    /** Ce qui precede le nom du fichier dans un chemin d'image de l'application, quel que soit le paquet. */
    private const val IMAGES_SEGMENT = "/files/images/"

    /** Le dossier `images` de l'application en cours, pose au demarrage (cf. TunturoApp). */
    @Volatile var imagesDirectory: File? = null

    /** Le chemin a lire pour [path] : lui-meme s'il existe, sinon son homologue dans [directory] s'il y est. */
    fun resolve(path: String, directory: File? = imagesDirectory): String {
        if (directory == null || path.startsWith("http://") || path.startsWith("https://")) return path
        val segmentIndex = path.lastIndexOf(IMAGES_SEGMENT)
        if (segmentIndex < 0) return path
        val fileName = path.substring(segmentIndex + IMAGES_SEGMENT.length)
        // Un nom qui remonte (`..`) ou descend dans un sous-dossier n'est pas un nom que l'application a
        // ecrit : on ne s'en sert pas pour aller lire ailleurs.
        if (fileName.isEmpty() || fileName.contains("..") || fileName.contains('/')) return path
        if (File(path).exists()) return path
        val candidate = File(directory, fileName)
        return if (candidate.isFile) candidate.path else path
    }
}
