package fr.istic.mob.networkBN.model

/**
 * V2 : plans intégrés à l'application.
 *
 *  - [id] : identifiant texte STABLE ("appartement", "maison"), indépendant de R.drawable
 *    et de la langue : c'est lui qui sera enregistré dans les sauvegardes.
 *  - L'image et le nom affiché sont associés côté vue (voir view/RessourcesPlan.kt) :
 *    le modèle reste une classe Kotlin pure, sans Android.
 *
 * L'ordre des constantes est l'ordre d'affichage dans la boîte « Nouveau réseau ».
 */
enum class Plan(val id: String) {
    APPARTEMENT("appartement"),
    MAISON("maison");

    companion object {
        val PAR_DEFAUT = APPARTEMENT

        /** Plan d'identifiant [id], ou null s'il est inconnu (fichier invalide...). */
        fun depuisId(id: String?): Plan? = entries.firstOrNull { it.id == id }
    }
}
