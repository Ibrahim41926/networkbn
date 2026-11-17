package fr.istic.mob.networkBN.viewModel

/**
 * V2 : résultat d'Enregistrer / Ouvrir (et échec de l'export PNG), à afficher une fois par la Vue (qui choisit le texte traduit).
 * [nom] = nom de la sauvegarde concernée (jamais traduit), vide s'il n'y en a pas.
 */
data class MessageReseau(val type: Type, val nom: String = "") {
    enum class Type {
        ENREGISTRE,
        OUVERT,
        AUCUNE_SAUVEGARDE,
        ERREUR_ENREGISTREMENT,
        ERREUR_LECTURE,
        FICHIER_INVALIDE,
        ERREUR_EXPORT_IMAGE // V2 : export PNG impossible
    }
}
