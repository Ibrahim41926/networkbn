package fr.istic.mob.networkBN.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.istic.mob.networkBN.model.Graph
import fr.istic.mob.networkBN.model.Objet

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
    private var objetSelectionne: Objet? = null

    /** Décalage doigt → centre, mémorisé au début du glissement (évite le « saut »). */
    private var decalageX = 0f // decalage entre le doigt et lobjet selectionner sur laxe x
    private var decalageY = 0f // decalage entre le doigt et lobjet selectionner sur laxe y

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
    fun validerEtiquette(texte: String, x: Float, y: Float): Boolean {
        if (modeCourant != ModeEdition.AJOUT_OBJET) return false   // 1. mode
        graphe.addObjet(texte, x, y) ?: return false                // 2. modèle (règles)
        publierGraphe()                                              // 3. publier
        return true
    }

    // ---------- Glissement ----------

    /** Le doigt se pose en (x, y). [objetTouche] = objet sous le doigt, ou null. */
    fun debutGlissement(x: Float, y: Float, objetTouche: Objet?) {
        annulerGeste()                                   // repart d'un état propre
        if (objetTouche == null) return                  // depuis le fond : aucune action
        if (objetTouche !in graphe.objets) return        // objet inconnu du graphe

        when (modeCourant) {
            ModeEdition.MODIFICATION -> {
                objetSelectionne = objetTouche
                decalageX = objetTouche.position.x - x
                decalageY = objetTouche.position.y - y
            }
            ModeEdition.AJOUT_CONNEXION -> {
                _connexionProvisoire.value = ConnexionProvisoire(objetTouche, x, y)
            }
            ModeEdition.AJOUT_OBJET -> Unit   // géré par peutAjouterObjet (appui long)
        }
    }

    /** Le doigt bouge en (x, y). */
    fun glissement(x: Float, y: Float) {
        when (modeCourant) {
            ModeEdition.MODIFICATION -> {
                val objet = objetSelectionne ?: return
                // Nouveau centre = doigt + décalage. Le modèle borne au cadre de l'image.
                if (graphe.modifiePosition(objet, x + decalageX, y + decalageY)) {
                    publierGraphe()
                }
            }
            ModeEdition.AJOUT_CONNEXION -> {
                val provisoire = _connexionProvisoire.value ?: return
                _connexionProvisoire.value = provisoire.copy(xDoigt = x, yDoigt = y)
            }
            ModeEdition.AJOUT_OBJET -> Unit
        }
    }

    /**
     * Le doigt se lève. [objetSousLeDoigt] = objet sous le doigt, ou null (fond).
     * En AJOUT_CONNEXION : crée la connexion si le modèle l'accepte
     * (pas de boucle, pas de doublon). Dans tous les cas, le geste se termine.
     */
    fun finGlissement(objetSousLeDoigt: Objet?) {
        val provisoire = _connexionProvisoire.value
        if (modeCourant == ModeEdition.AJOUT_CONNEXION &&
            provisoire != null &&
            objetSousLeDoigt != null
        ) {
            if (graphe.addConnexion(provisoire.source, objetSousLeDoigt) != null) {
                publierGraphe()
            }
        }
        annulerGeste()
    }

    /**
     * Geste interrompu (ACTION_CANCEL), fin de geste ou changement de mode :
     * efface la connexion provisoire et libère l'objet sélectionné.
     * Un objet déplacé garde sa dernière position valide.
     */
    fun annulerGeste() {
        objetSelectionne = null
        if (_connexionProvisoire.value != null) {  // le if est utile sinon on pousse le live data a prevnir toute les vues meme si il ya ps de connexion provisoire
            _connexionProvisoire.value = null
        }
    }

    // ---------- Réinitialisation ----------

    /** Après confirmation : supprime objets et connexions, garde le plan. */
    fun reinitialiser() {
        annulerGeste()
        graphe.resetAll()
        publierGraphe()
    }
}