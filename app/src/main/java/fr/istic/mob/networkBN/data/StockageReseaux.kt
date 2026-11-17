package fr.istic.mob.networkBN.data

import java.io.File
import java.io.IOException

/**
 * V2 : accès aux fichiers des sauvegardes, dans le stockage interne privé de l'application
 * ([dossier], par exemple filesDir/reseaux). Une sauvegarde = un fichier « nom.json ».
 *
 * Opérations BLOQUANTES : à appeler hors du thread principal (le ViewModel utilise Dispatchers.IO).
 * Les noms ne sont pas traduits.
 */
class StockageReseaux(private val dossier: File) {

    /** Noms des sauvegardes, par ordre alphabétique ; liste vide s'il n'y en a aucune. */
    fun lister(): List<String> =
        dossier.listFiles { fichier -> fichier.isFile && fichier.name.endsWith(EXTENSION) }
            ?.map { it.name.removeSuffix(EXTENSION) }
            ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
            ?: emptyList()

    fun existe(nom: String): Boolean = fichier(nom).isFile

    /**
     * Écrit d'abord dans un fichier temporaire, puis le renomme : une sauvegarde existante
     * n'est jamais à moitié écrasée si l'écriture échoue.
     * @throws IOException si l'écriture échoue.
     */
    fun ecrire(nom: String, contenu: String) {
        if (!dossier.isDirectory && !dossier.mkdirs()) throw IOException("Dossier impossible à créer : $dossier")
        val temporaire = File(dossier, nom + EXTENSION + ".tmp")
        temporaire.writeText(contenu)
        if (!temporaire.renameTo(fichier(nom))) {
            temporaire.delete()
            throw IOException("Renommage impossible : $nom")
        }
    }

    /** @throws IOException si le fichier est absent ou illisible. */
    fun lire(nom: String): String = fichier(nom).readText()

    private fun fichier(nom: String) = File(dossier, nom + EXTENSION)

    companion object {
        private const val EXTENSION = ".json"

        /** Lettres (accents compris), chiffres, espaces, « - » et « _ » : jamais de chemin. */
        private val NOM_VALIDE = Regex("[\\p{L}\\p{N} _-]{1,40}")

        /** Vrai si [nom] (déjà trimé) peut servir de nom de sauvegarde. */
        fun nomValide(nom: String): Boolean = NOM_VALIDE.matches(nom)
    }
}
