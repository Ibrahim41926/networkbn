package fr.istic.mob.networkBN.model
import kotlin.math.hypot

/**
 * Connexion NON orientée entre deux objets.
 *
 * - Extrémités, étiquette et couleur fixées à la création ; seule la [fleche] change
 *   (via [Graph.modifierFleche]).
 * - Extrémités = RÉFÉRENCES vers des [Objet] : si un objet bouge,
 *   la connexion suit sans rien recalculer.
 * - V2 : [etiquette] obligatoire, saisie par l'utilisateur (non vide, trimée, non traduite).
 * - V2 : [couleur] choisie à la création, parmi les mêmes couleurs que les objets ([CouleurObjet]).
 * - [epaisseur] prépare la partie 2.
 *
 * Constructeur `internal` : seul [Graph.addConnexion] crée des connexions.
 */
class Connexion internal constructor(
    val extremite1: Objet,
    val extremite2: Objet,
    val etiquette: String, // V2 : obligatoire (était String? = null en partie 1)
    val epaisseur: Float = EPAISSEUR_PAR_DEFAUT,
    val couleur: CouleurObjet = COULEUR_PAR_DEFAUT // V2 : choisie à la création (mêmes couleurs que les objets)
) {

    init {
        require(extremite1 != extremite2) { "Boucle interdite" }
    }

    /**
     * Distance signée entre le milieu M de AB et le point P de la courbe.
     * 0 = connexion droite. Le signe indique de quel côté de AB la courbe passe.
     * Modifiée uniquement par [Graph.modifierFleche].
     */
    var fleche: Float = 0f
        internal set

    /** Milieu M du segment AB. */
    fun milieu(): Pair<Float, Float> {
        val a = extremite1.position
        val b = extremite2.position
        return (a.x + b.x) / 2 to (a.y + b.y) / 2
    }

    /**
     * Normale unitaire à AB, calculée toujours dans l'ordre extremite1 -> extremite2.
     * (0, 0) si les deux objets sont presque au même endroit : la courbe redevient droite.
     */
    fun normale(): Pair<Float, Float> {
        val a = extremite1.position
        val b = extremite2.position
        val dx = b.x - a.x
        val dy = b.y - a.y
        val longueur = hypot(dx, dy)
        if (longueur < 1f) return 0f to 0f
        return -dy / longueur to dx / longueur
    }

    /** Point P de la courbe : P = M + fleche * n. */
    fun pointMilieuCourbe(): Pair<Float, Float> {
        val (mx, my) = milieu()
        val (nx, ny) = normale()
        return mx + fleche * nx to my + fleche * ny
    }

    /** Point de contrôle de Bézier : C = M + 2 * fleche * n (la courbe passe alors par P). */
    fun pointControle(): Pair<Float, Float> {
        val (mx, my) = milieu()
        val (nx, ny) = normale()
        return mx + 2 * fleche * nx to my + 2 * fleche * ny
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
        /** V2 : bleu, présélectionné dans la boîte « Nouvelle connexion ». */
        val COULEUR_PAR_DEFAUT: CouleurObjet = CouleurObjet.BLEU
    }
}