package fr.istic.mob.networkBN.model

/**
 * Position d'un point dans le repère du PLAN.
 *
 * - Origine (0, 0) = coin haut-gauche de l'image du plan.
 * - Unité = pixel de l'image : 1 pixel image = 1 pixel canevas (aucune conversion en partie 1).
 * - Contrainte : x >= 0 et y >= 0, vérifiée à la construction.
 *   La borne supérieure (largeur/hauteur de l'image) est vérifiée par [Graph],
 *   car c'est lui qui connaît les dimensions du plan.
 *
 * Immuable : pour déplacer un objet, on lui affecte une NOUVELLE Coordonnee
 * via [Graph.modifiePosition].
 */
data class Coordonnee(val x: Float, val y: Float) {

    init {
        require(x >= 0f && y >= 0f) { "Coordonnée négative interdite : ($x, $y)" }
    }

    companion object {
        /** Origine du plan : coin haut-gauche de l'image. */
        val ORIGINE = Coordonnee(0f, 0f)
    }
}