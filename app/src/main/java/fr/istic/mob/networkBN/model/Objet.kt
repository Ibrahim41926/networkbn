package fr.istic.mob.networkBN.model

/**
 * Objet connecté posé sur le plan (box, TV, prise...).
 *
 *  - [id]        : identifiant STABLE et unique, attribué par [Graph] (jamais réutilisé).
 *  - [etiquette] : texte saisi par l'utilisateur (non vide, non traduit).
 *  - [position]  : centre de l'objet, dans le repère du plan.
 *  - [couleur]   : couleur ARGB stockée en Int (pas de dépendance Android).
 *
 * Constructeur `internal` : seul [Graph.addObjet] crée des objets.
 */
class Objet internal constructor(
    val id: Long,
    val etiquette: String,
    position: Coordonnee,
    val couleur: Int = COULEUR_PAR_DEFAUT
) {

    /**
     * Lecture publique, écriture réservée au module (via [Graph.modifiePosition]).
     * Les connexions référencent cet objet : elles suivent automatiquement.
     */
    var position: Coordonnee = position
        internal set

    /** Égalité par identifiant : deux objets de même étiquette restent distincts. */
    override fun equals(other: Any?): Boolean = other is Objet && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Objet(id=$id, etiquette='$etiquette', position=$position)"

    companion object {
        /** Vert (0xFF2E7D32). */
        val COULEUR_PAR_DEFAUT: Int = 0xFF2E7D32.toInt()
    }
}