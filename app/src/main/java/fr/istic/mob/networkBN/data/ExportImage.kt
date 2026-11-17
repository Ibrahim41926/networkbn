package fr.istic.mob.networkBN.data

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * V2 : écriture de l'image PNG du réseau à partager, dans [dossier]
 * (cache de l'application, déclaré dans le FileProvider : res/xml/chemins_partage.xml).
 *
 * Un seul export est gardé : les précédents sont supprimés à chaque écriture.
 * Opération BLOQUANTE : à appeler hors du thread principal (le ViewModel utilise Dispatchers.IO).
 */
class ExportImage(private val dossier: File) {

    /**
     * Écrit [image] dans « [nom].png ».
     * @return le fichier écrit.
     * @throws IOException si l'écriture échoue.
     */
    fun ecrirePng(image: Bitmap, nom: String): File {
        if (!dossier.isDirectory && !dossier.mkdirs()) throw IOException("Dossier impossible à créer : $dossier")
        dossier.listFiles()?.forEach { it.delete() }
        val fichier = File(dossier, "$nom.png")
        try {
            FileOutputStream(fichier).use { flux ->
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, flux)) {
                    throw IOException("Compression PNG impossible : $nom")
                }
            }
        } catch (e: IOException) {
            fichier.delete() // jamais d'image à moitié écrite
            throw e
        }
        return fichier
    }
}
