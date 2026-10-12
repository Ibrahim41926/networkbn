package fr.istic.mob.networkBN.viewModel

/**
 * V2 : défilement de l'affichage = point du plan affiché dans le coin haut-gauche de la vue.
 *
 *  - Dessin  : xVue  = xPlan − x
 *  - Toucher : xPlan = xVue  + x   (idem pour y)
 *
 * Toujours borné par le ViewModel : 0 ≤ x ≤ max(0, largeurPlan − largeurVue), idem pour y.
 */
data class Defilement(val x: Float, val y: Float) {
    companion object {
        val ORIGINE = Defilement(0f, 0f)
    }
}
