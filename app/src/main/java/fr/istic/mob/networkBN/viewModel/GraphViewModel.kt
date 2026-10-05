package fr.istic.mob.networkBN.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.istic.mob.networkBN.model.CouleurObjet
import fr.istic.mob.networkBN.model.Graph
import fr.istic.mob.networkBN.model.Objet

import fr.istic.mob.networkBN.model.Connexion
import kotlin.math.abs

/**
 * Le « serveur » entre la Vue et le Modèle.
 *
 *  1. ÉTAT    : ce qu'il garde (graphe + état de l'éditeur).
 *  2. VITRINE : ce que la Vue observe (LiveData).
 *  3. GUICHET : les gestes que la Vue lui envoie.
 *
 * La Vue envoie des gestes NEUTRES (« glissement commencé ici, sur tel objet »).
 * C'est le ViewModel qui décide ce qu'ils signifient, SELON LE MODE.
 *
 * Aucune référence à une Activity, une View ou un Canvas.
 */
class GraphViewModel : ViewModel() {

    // =====================================================================
    // 1. ÉTAT (privé : seul le ViewModel le modifie)
    // =====================================================================

    /** Le réseau. */
    private val graphe = Graph()

    /** Geste en cours en mode MODIFICATION : l'objet tenu par le doigt (ou aucun). */
    private var objetSelectionne: Objet?
        get() = _objetDeplace.value
        set(value) {
            if (_objetDeplace.value != value) _objetDeplace.value = value
        }

    /** Décalage doigt → centre, mémorisé au début du glissement (évite le « saut »). */
    private var decalageX = 0f // decalage entre le doigt et lobjet selectionner sur laxe x
    private var decalageY = 0f // decalage entre le doigt et lobjet selectionner sur laxe y

    /** Geste en cours en mode MODIFICATION : connexion dont on tire l'étiquette (ou aucune). */
    private var connexionCourbee: Connexion? = null
    /** Dernière position du doigt, pour calculer le déplacement (deltaX, deltaY). */
    private var dernierX = 0f
    private var dernierY = 0f

    // =====================================================================
    // 2. VITRINE (ce que la Vue observe pour se dessiner)
    // =====================================================================

    /**
     * Pattern classique : MutableLiveData PRIVÉ (on écrit dedans),
     * LiveData PUBLIC (la Vue ne peut que lire).
     */
    private val _grapheObservable = MutableLiveData(graphe)
    val grapheObservable: LiveData<Graph> = _grapheObservable

    private val _mode = MutableLiveData(ModeEdition.MODIFICATION)
    val mode: LiveData<ModeEdition> = _mode

    private val _connexionProvisoire = MutableLiveData<ConnexionProvisoire?>(null)
    val connexionProvisoire: LiveData<ConnexionProvisoire?> = _connexionProvisoire

    /** Objet en cours de déplacement (mode MODIFICATION), pour le mettre en évidence. */
    private val _objetDeplace = MutableLiveData<Objet?>(null)
    val objetDeplace: LiveData<Objet?> = _objetDeplace

    /** Raccourci pour lire le mode actuel. */
    private val modeCourant: ModeEdition
        get() = _mode.value ?: ModeEdition.MODIFICATION

    /**
     * Prévient la Vue que le graphe a changé. On republie la même instance :
     * setValue notifie toujours les observateurs.
     */
    private fun publierGraphe() {
        _grapheObservable.value = graphe
    }

    // =====================================================================
    // 3. GUICHET (appelé par la Vue)
    // =====================================================================

    /** Appelé une fois, quand la Vue a chargé l'image du plan. */
    fun definirPlan(largeur: Int, hauteur: Int) {
        graphe.definirDimensionsPlan(largeur, hauteur)
        publierGraphe()
    }

    // ---------- Modes ----------

    /** Changer de mode annule le geste en cours. */
    fun changerMode(nouveau: ModeEdition) {
        if (nouveau == modeCourant) return
        annulerGeste()
        _mode.value = nouveau
    }

    // ---------- Ajout d'objet (appui long) ----------

    /**
     * Appelé lors d'un appui long en (x, y). [objetTouche] = objet sous le doigt, ou null (fond).
     * @return true si la Vue doit ouvrir la boîte de saisie d'étiquette.
     *
     * Le ViewModel DÉCIDE, mais c'est la Vue qui AFFICHE la boîte de dialogue.
     */
    fun peutAjouterObjet(x: Float, y: Float, objetTouche: Objet?): Boolean =
        modeCourant == ModeEdition.AJOUT_OBJET &&   // bon mode ?
                objetTouche == null &&                   // pas sur un objet existant
                graphe.estDansLePlan(x, y)               // et sur l'image du plan

    /**
     * L'utilisateur a validé la boîte de saisie.
     * @return false si refusé (étiquette vide...) : la Vue peut garder la boîte ouverte.
     */
    fun validerEtiquette(texte: String, x: Float, y: Float, couleur: CouleurObjet): Boolean {
        if (modeCourant != ModeEdition.AJOUT_OBJET) return false   // 1. mode
        graphe.addObjet(texte, x, y, couleur) ?: return false       // 2. modèle (règles)
        publierGraphe()                                              // 3. publier
        return true
    }

    // ---------- Glissement ----------

        /**
     * Le doigt se pose en (x, y).
     * [objetTouche] = objet sous le doigt, [connexionTouchee] = étiquette de connexion sous le doigt.
     * Les objets sont prioritaires : la Vue ne donne une connexion que si aucun objet n'est touché.
     */
    fun debutGlissement(x: Float, y: Float, objetTouche: Objet?, connexionTouchee: Connexion?) {
        annulerGeste()

        // Glissement d'une étiquette de connexion en mode MODIFICATION : on change la courbure.
        if (modeCourant == ModeEdition.MODIFICATION && objetTouche == null &&
            connexionTouchee != null && connexionTouchee in graphe.connexions
        ) {
            connexionCourbee = connexionTouchee
            dernierX = x
            dernierY = y
            return
        }

        if (objetTouche == null) return
        if (objetTouche !in graphe.objets) return

        when (modeCourant) {
            ModeEdition.MODIFICATION -> {
                objetSelectionne = objetTouche
                decalageX = objetTouche.position.x - x
                decalageY = objetTouche.position.y - y
            }
            ModeEdition.AJOUT_CONNEXION -> {
                _connexionProvisoire.value = ConnexionProvisoire(objetTouche, x, y)
            }
            ModeEdition.AJOUT_OBJET -> Unit
        }
    }


    /** Le doigt bouge en (x, y). */
    fun glissement(x: Float, y: Float) {
        when (modeCourant) {
                ModeEdition.MODIFICATION -> {
                val connexion = connexionCourbee
                if (connexion != null) {
                    // On garde seulement la partie du déplacement perpendiculaire à AB.
                    val (nx, ny) = connexion.normale()
                    val nouvelleFleche = connexion.fleche + (x - dernierX) * nx + (y - dernierY) * ny
                    dernierX = x
                    dernierY = y
                    if (graphe.modifierFleche(connexion, nouvelleFleche)) publierGraphe()
                    return
                }
                val objet = objetSelectionne ?: return
            }

            ModeEdition.AJOUT_CONNEXION -> { // lors du glissement
                                            // on recree la connexion provisoire en modifiant la position du doigt
                val provisoire = _connexionProvisoire.value ?: return
                _connexionProvisoire.value = provisoire.copy(xDoigt = x, yDoigt = y)
            }
            ModeEdition.AJOUT_OBJET -> Unit // en mode d'ajout dobjet le glissement ne produit rien
        }
    }

    /**
     * Le doigt se lève. [objetSousLeDoigt] = objet sous le doigt, ou null (fond).
     * En AJOUT_CONNEXION : crée la connexion si le modèle l'accepte
     * (pas de boucle, pas de doublon). Dans tous les cas, le geste se termine.
     */
    fun finGlissement(objetSousLeDoigt: Objet?) { // a la fin du glissement
        val provisoire = _connexionProvisoire.value
        if (modeCourant == ModeEdition.AJOUT_CONNEXION && // si nous sommes en mode dajout de connexion
            provisoire != null &&                       // et on a une connexion provisoire qui a ete dessiné
            objetSousLeDoigt != null                // que cette connexion provisoire arrive sur un autre objet
        ) {
            if (graphe.addConnexion(provisoire.source, objetSousLeDoigt) != null) { // et que cet objet est distint on ajoute une connexion dans le graphe
                publierGraphe()     // on signale ensuite la
            }
        }

        connexionCourbee?.let {
            if (abs(it.fleche) < SEUIL_REDRESSEMENT && graphe.modifierFleche(it, 0f)) publierGraphe()
        }

        annulerGeste()
    }

    /**
     * Geste interrompu (ACTION_CANCEL), fin de geste ou changement de mode :
     * efface la connexion provisoire et libère l'objet sélectionné.
     * Un objet déplacé garde sa dernière position valide.
     */
    fun annulerGeste() {
        connexionCourbee = null
        objetSelectionne = null
        if (_connexionProvisoire.value != null) {  // le if est utile sinon on pousse le live data a prevnir toute les vues meme si il ya ps de connexion provisoire
            _connexionProvisoire.value = null
        }
    }

    // ---------- Réinitialisation ----------

    /** Après confirmation : supprime objets et connexions, garde le plan. */
    fun reinitialiser() { // la renitialisation
        annulerGeste() // annule le dernier geste
        graphe.resetAll()   // vide le graphe
        publierGraphe() // on fait savoir a la view que le graphe a change
    }


    private companion object {
        /** En dessous de cette flèche (en pixels du plan), on remet la connexion droite. */
        const val SEUIL_REDRESSEMENT = 20f
    }

}