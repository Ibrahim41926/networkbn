package fr.istic.mob.networkBN.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.istic.mob.networkBN.model.Connexion
import fr.istic.mob.networkBN.model.CouleurObjet
import fr.istic.mob.networkBN.model.Graph
import fr.istic.mob.networkBN.model.Objet
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

    /**
     * Geste en cours en mode MODIFICATION : l'objet tenu par le doigt (ou aucun).
     * V2 : stocké dans [objetDeplace] pour que la Vue puisse le mettre en évidence.
     */
    private var objetSelectionne: Objet?
        get() = _objetDeplace.value
        set(value) {
            if (_objetDeplace.value != value) _objetDeplace.value = value
        }

    /** Décalage doigt → centre, mémorisé au début du glissement (évite le « saut »). */
    private var decalageX = 0f // décalage entre le doigt et l'objet sélectionné sur l'axe x
    private var decalageY = 0f // décalage entre le doigt et l'objet sélectionné sur l'axe y

    /** V2 : geste en cours en mode MODIFICATION : connexion dont on tire l'étiquette (ou aucune). */
    private var connexionCourbee: Connexion? = null

    /** V2 : dernière position du doigt, pour calculer le déplacement (deltaX, deltaY). */
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

    /** V2 : objet en cours de déplacement (mode MODIFICATION), pour le mettre en évidence. */
    private val _objetDeplace = MutableLiveData<Objet?>(null)
    val objetDeplace: LiveData<Objet?> = _objetDeplace

    /**
     * V2 : connexion valide qui attend son étiquette (boîte « Nouvelle connexion » ouverte), ou null.
     * La Vue ouvre / ferme la boîte selon cette valeur.
     */
    private val _connexionEnAttente = MutableLiveData<ConnexionEnAttente?>(null)
    val connexionEnAttente: LiveData<ConnexionEnAttente?> = _connexionEnAttente

    /** V2 : vrai tant que la boîte est ouverte ; aucun geste ne doit alors modifier le graphe. */
    private val saisieConnexionEnCours: Boolean
        get() = _connexionEnAttente.value != null

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

    /** Changer de mode annule le geste en cours (V2 : et la connexion en attente, comme Annuler). */
    fun changerMode(nouveau: ModeEdition) {
        if (nouveau == modeCourant) return
        annulerConnexionEnAttente() // V2
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
    fun peutAjouterObjet(x: Float, y: Float, objetTouche: Objet?, connexionTouchee: Connexion?): Boolean =
        !saisieConnexionEnCours &&                      // V2 : pas pendant la saisie d'une connexion
            modeCourant == ModeEdition.AJOUT_OBJET &&   // bon mode ?
            objetTouche == null &&                      // pas sur un objet existant
            connexionTouchee == null &&                 // V2 : ni sur une étiquette de connexion
            graphe.estDansLePlan(x, y)                  // et sur l'image du plan

    /**
     * L'utilisateur a validé la boîte de saisie.
     * V2 : [couleur] = couleur choisie dans la boîte, transmise au modèle.
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
     * [objetTouche] = objet sous le doigt, ou null.
     * [connexionTouchee] = connexion dont l'étiquette est sous le doigt (seulement si aucun objet).
     */
    fun debutGlissement(x: Float, y: Float, objetTouche: Objet?, connexionTouchee: Connexion?) {
        if (saisieConnexionEnCours) return // V2 : gestes bloqués pendant la saisie
        annulerGeste()                     // on repart d'un état propre

        when (modeCourant) {
            ModeEdition.MODIFICATION -> {
                if (objetTouche != null && objetTouche in graphe.objets) {
                    // Priorité aux objets : on se prépare à déplacer
                    objetSelectionne = objetTouche
                    decalageX = objetTouche.position.x - x
                    decalageY = objetTouche.position.y - y
                } else if (connexionTouchee != null && connexionTouchee in graphe.connexions) {
                    // V2 : on tire l'étiquette pour courber la connexion
                    connexionCourbee = connexionTouchee
                    dernierX = x
                    dernierY = y
                }
            }
            ModeEdition.AJOUT_CONNEXION -> {
                if (objetTouche != null && objetTouche in graphe.objets) {
                    _connexionProvisoire.value = ConnexionProvisoire(objetTouche, x, y)
                }
            }
            ModeEdition.AJOUT_OBJET -> Unit // géré par peutAjouterObjet (appui long)
        }
    }

    /** Le doigt bouge en (x, y). */
    fun glissement(x: Float, y: Float) {
        if (saisieConnexionEnCours) return // V2 : gestes bloqués pendant la saisie
        when (modeCourant) {
            ModeEdition.MODIFICATION -> {
                // V2 : courbure d'une connexion (seule la partie perpendiculaire à AB compte)
                val connexion = connexionCourbee
                if (connexion != null) {
                    val (nx, ny) = connexion.normale()
                    val nouvelleFleche = connexion.fleche + (x - dernierX) * nx + (y - dernierY) * ny
                    dernierX = x
                    dernierY = y
                    if (graphe.modifierFleche(connexion, nouvelleFleche)) publierGraphe()
                    return
                }
                // Déplacement d'un objet : nouveau centre = doigt + décalage (le modèle borne au plan)
                val objet = objetSelectionne ?: return
                if (graphe.modifiePosition(objet, x + decalageX, y + decalageY)) publierGraphe()
            }
            ModeEdition.AJOUT_CONNEXION -> {
                // On recrée la connexion provisoire avec la nouvelle position du doigt
                val provisoire = _connexionProvisoire.value ?: return
                _connexionProvisoire.value = provisoire.copy(xDoigt = x, yDoigt = y)
            }
            ModeEdition.AJOUT_OBJET -> Unit // en mode ajout d'objet, le glissement ne produit rien
        }
    }

    /**
     * Le doigt se lève. [objetSousLeDoigt] = objet sous le doigt, ou null (fond).
     * En AJOUT_CONNEXION, si le modèle accepte la connexion (pas de boucle, pas de doublon) :
     * V2 : elle n'est PAS encore ajoutée. Elle passe « en attente » de son étiquette
     * et l'aperçu reste affiché entre les deux objets. Sinon, le geste se termine.
     */
    fun finGlissement(objetSousLeDoigt: Objet?) {
        if (saisieConnexionEnCours) return // V2 : gestes bloqués pendant la saisie
        val provisoire = _connexionProvisoire.value
        if (modeCourant == ModeEdition.AJOUT_CONNEXION &&
            provisoire != null &&
            objetSousLeDoigt != null &&
            graphe.peutConnecter(provisoire.source, objetSousLeDoigt) // V2 : valide (sans rien modifier)
        ) {
            // V2 : l'aperçu est figé sur le centre de l'objet cible, puis on attend l'étiquette.
            val cible = objetSousLeDoigt.position
            _connexionProvisoire.value = provisoire.copy(xDoigt = cible.x, yDoigt = cible.y)
            _connexionEnAttente.value = ConnexionEnAttente(provisoire.source.id, objetSousLeDoigt.id)
            return
        }

        // V2 : une connexion presque droite est redressée au relâchement.
        connexionCourbee?.let {
            if (abs(it.fleche) < SEUIL_REDRESSEMENT && graphe.modifierFleche(it, 0f)) publierGraphe()
        }

        annulerGeste()
    }

    // ---------- V2 : étiquette de la connexion en attente ----------

    /**
     * V2 : l'utilisateur a validé la boîte « Nouvelle connexion ».
     * [couleur] = couleur choisie dans la boîte.
     * @return false si l'étiquette est refusée (vide...) : la Vue garde la boîte ouverte.
     */
    fun validerEtiquetteConnexion(texte: String, couleur: CouleurObjet): Boolean {
        val attente = _connexionEnAttente.value ?: return true
        val source = graphe.trouverObjet(attente.idSource)
        val cible = graphe.trouverObjet(attente.idCible)
        if (source == null || cible == null) {   // objets disparus : on abandonne
            annulerConnexionEnAttente()
            return true
        }
        graphe.addConnexion(source, cible, texte, couleur) ?: return false // le modèle trime et refuse le vide
        _connexionEnAttente.value = null
        _connexionProvisoire.value = null
        publierGraphe()
        return true
    }

    /** V2 : Annuler, retour, clic hors de la boîte ou changement de mode : rien n'est créé. */
    fun annulerConnexionEnAttente() {
        if (!saisieConnexionEnCours) return
        _connexionEnAttente.value = null
        _connexionProvisoire.value = null
    }

    /**
     * Geste interrompu (ACTION_CANCEL), fin de geste ou changement de mode :
     * libère l'objet et la connexion tenus, efface la connexion provisoire.
     * Un objet déplacé garde sa dernière position valide.
     * V2 : pendant la saisie d'une connexion, l'aperçu reste affiché.
     */
    fun annulerGeste() {
        objetSelectionne = null
        connexionCourbee = null
        // Le if évite de notifier la Vue pour rien quand il n'y a pas d'aperçu.
        if (!saisieConnexionEnCours && _connexionProvisoire.value != null) {
            _connexionProvisoire.value = null
        }
    }

    // ---------- V2 : menus (appui long en mode MODIFICATION) ----------

    /** Appui long sur un objet : vrai si la Vue doit ouvrir son menu. */
    fun peutModifierObjet(objet: Objet): Boolean =
        !saisieConnexionEnCours && modeCourant == ModeEdition.MODIFICATION && objet in graphe.objets

    /** Appui long sur l'étiquette d'une connexion : vrai si la Vue doit ouvrir son menu. */
    fun peutModifierConnexion(connexion: Connexion): Boolean =
        !saisieConnexionEnCours && modeCourant == ModeEdition.MODIFICATION && connexion in graphe.connexions

    /**
     * L'utilisateur a validé le menu de l'objet [id].
     * @return false si l'étiquette est refusée (vide...) : rien n'est modifié, la Vue garde la boîte ouverte.
     */
    fun modifierObjet(id: Long, etiquette: String, couleur: CouleurObjet): Boolean {
        val objet = graphe.trouverObjet(id) ?: return true // objet disparu : rien à modifier
        if (!graphe.modifierEtiquette(objet, etiquette)) return false
        graphe.modifierCouleur(objet, couleur)
        publierGraphe()
        return true
    }

    /** Supprime l'objet [id] et toutes ses connexions. */
    fun supprimerObjet(id: Long) {
        val objet = graphe.trouverObjet(id) ?: return
        annulerGeste()
        if (graphe.supprimerObjet(objet)) publierGraphe()
    }

    /**
     * L'utilisateur a validé le menu de la connexion entre [id1] et [id2].
     * @return false si l'étiquette est refusée (vide...) : rien n'est modifié, la Vue garde la boîte ouverte.
     */
    fun modifierConnexion(id1: Long, id2: Long, etiquette: String, couleur: CouleurObjet, epaisseur: Float): Boolean {
        val connexion = graphe.trouverConnexion(id1, id2) ?: return true // connexion disparue
        if (!graphe.modifierEtiquette(connexion, etiquette)) return false
        graphe.modifierCouleur(connexion, couleur)
        graphe.modifierEpaisseur(connexion, epaisseur)
        publierGraphe()
        return true
    }

    /** Supprime la connexion entre [id1] et [id2]. */
    fun supprimerConnexion(id1: Long, id2: Long) {
        val connexion = graphe.trouverConnexion(id1, id2) ?: return
        annulerGeste()
        if (graphe.supprimerConnexion(connexion)) publierGraphe()
    }

    // ---------- Réinitialisation ----------

    /** Après confirmation : supprime objets et connexions, garde le plan. */
    fun reinitialiser() {
        annulerConnexionEnAttente() // V2 : abandonne une connexion en attente d'étiquette
        annulerGeste()              // annule le dernier geste
        graphe.resetAll()           // vide le graphe
        publierGraphe()             // prévient la Vue que le graphe a changé
    }

    private companion object {
        /** En dessous de cette flèche (en pixels du plan), on remet la connexion droite. */
        const val SEUIL_REDRESSEMENT = 20f
    }
}