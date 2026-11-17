package fr.istic.mob.networkBN.viewModel

/**
 * V2 : connexion valide (ni boucle ni doublon) qui attend son étiquette.
 * On garde les IDENTIFIANTS des deux objets : c'est le ViewModel qui la détient,
 * elle survit donc à une rotation d'écran.
 */
data class ConnexionEnAttente(
    val idSource: Long,
    val idCible: Long
)
