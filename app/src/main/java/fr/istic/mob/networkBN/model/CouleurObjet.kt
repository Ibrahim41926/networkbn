package fr.istic.mob.networkBN.model

/**
 * V2 : couleurs proposées à la création d'un objet ou d'une connexion.
 *
 *  - [id]   : identifiant stable ("red", "green"...), indépendant de la langue.
 *  - [argb] : valeur ARGB utilisée pour le dessin (pas de dépendance Android).
 *
 * L'ordre des constantes est l'ordre d'affichage des pastilles.
 */
enum class CouleurObjet(val id: String, val argb: Int) {
    ROUGE("red", 0xFFE53935.toInt()),
    VERT("green", 0xFF2E7D32.toInt()),
    BLEU("blue", 0xFF1E88E5.toInt()),
    ORANGE("orange", 0xFFFB8C00.toInt()),
    CYAN("cyan", 0xFF00ACC1.toInt()),
    MAGENTA("magenta", 0xFFD020D0.toInt()),
    NOIR("black", 0xFF000000.toInt());

    companion object {
        val PAR_DEFAUT = VERT

        /** Couleur d'identifiant [id], ou la couleur par défaut si inconnu. */
        fun depuisId(id: String?): CouleurObjet = entries.firstOrNull { it.id == id } ?: PAR_DEFAUT
    }
}
