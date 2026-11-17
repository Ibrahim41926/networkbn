package fr.istic.mob.networkBN.viewModel

/**
 * Les quatre modes EXCLUSIFS de l'éditeur.
 * Un seul est actif à la fois ; MODIFICATION est le mode initial.
 */
enum class ModeEdition {
    AJOUT_OBJET,
    AJOUT_CONNEXION,
    MODIFICATION,
    NAVIGATION // V2 : glisser fait défiler le plan, aucune autre action
}
