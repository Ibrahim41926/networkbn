package fr.istic.mob.networkBN.viewModel
import fr.istic.mob.networkBN.model.Objet

data class ConnexionProvisoire(
    val source: Objet,     // l'objet d'où part le doigt
    val xDoigt: Float,     // où est le doigt maintenant (x)
    val yDoigt: Float      // où est le doigt maintenant (y)
)