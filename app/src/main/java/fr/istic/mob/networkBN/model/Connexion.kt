package fr.istic.mob.networkBN.model

/**
 * Connexion NON orientée entre deux objets.
 *
 * - Immuable.
 * - Extrémités = RÉFÉRENCES vers des [Objet] : si un objet bouge,
 *   la connexion suit sans rien recalculer.
 * - [etiquette], [epaisseur], [couleur] préparent la partie 2
 *   (étiquette à null en partie 1).
 *
 * Constructeur `internal` : seul [Graph.addConnexion] crée des connexions.
 */
class Connexion internal constructor(
    val extremite1: Objet,
    val extremite2: Objet,
    val etiquette: String? = null,
    val epaisseur: Float = EPAISSEUR_PAR_DEFAUT,
    val couleur: Int = COULEUR_PAR_DEFAUT
) {

    init {
        require(extremite1 != extremite2) { "Boucle interdite" }
    }

    /** Vrai si cette connexion relie a et b, dans un sens ou dans l'autre. */
    fun relie(a: Objet, b: Objet): Boolean =
        (extremite1 == a && extremite2 == b) || (extremite1 == b && extremite2 == a)

    /** Vrai si l'objet est l'une des deux extrémités. */
    fun concerne(objet: Objet): Boolean = extremite1 == objet || extremite2 == objet

    /** A–B est égale à B–A (non orientée). */
    override fun equals(other: Any?): Boolean =
        other is Connexion && relie(other.extremite1, other.extremite2)

    /** Symétrique : hash(A–B) == hash(B–A). */
    override fun hashCode(): Int = extremite1.hashCode() xor extremite2.hashCode()

    override fun toString(): String = "Connexion(${extremite1.id} — ${extremite2.id})"

    companion object {
        const val EPAISSEUR_PAR_DEFAUT: Float = 8f
        /** Bleu (0xFF2163B4). */
        val COULEUR_PAR_DEFAUT: Int = 0xFF2163B4.toInt()
    }
}