package fr.istic.mob.networkBN.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import fr.istic.mob.networkBN.data.FormatReseau
import fr.istic.mob.networkBN.data.SauvegardeInvalideException
import fr.istic.mob.networkBN.data.StockageReseaux
import fr.istic.mob.networkBN.model.Connexion
import fr.istic.mob.networkBN.model.CouleurObjet
import fr.istic.mob.networkBN.model.Graph
import fr.istic.mob.networkBN.model.Objet
import fr.istic.mob.networkBN.model.Plan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
 * V2 : AndroidViewModel uniquement pour connaître le dossier interne des sauvegardes ;
 * les accès aux fichiers sont délégués à [StockageReseaux].
 */
class GraphViewModel(application: Application) : AndroidViewModel(application) {

    // =====================================================================
    // 1. ÉTAT (privé : seul le ViewModel le modifie)
    // =====================================================================

    /** Le réseau. V2 : remplacé en entier par Ouvrir. */
    private var graphe = Graph()

    /** V2 : sauvegardes dans le stockage interne privé de l'application. */
    private val stockage = StockageReseaux(File(application.filesDir, "reseaux"))

    /** V2 : nom de la dernière sauvegarde enregistrée ou ouverte (proposé par Enregistrer), ou null. */
    var nomReseau: String? = null
        private set

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

    /** V2 : taille de la vue en pixels (0 tant qu'elle n'est pas connue), pour borner le défilement. */
    private var largeurVue = 0
    private var hauteurVue = 0

    /** V2 : mode NAVIGATION : dernière position du doigt en coordonnées ÉCRAN. */
    private var dernierXEcran = 0f
    private var dernierYEcran = 0f

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

    /** V2 : plan courant (identifiant stable), conservé à la rotation. */
    private val _plan = MutableLiveData(Plan.PAR_DEFAUT)
    val plan: LiveData<Plan> = _plan

    /**
     * V2 : vrai si le réseau a changé depuis le dernier Nouveau réseau / Enregistrer / Ouvrir.
     * Sert à demander confirmation avant de remplacer le réseau.
     */
    private val _modifie = MutableLiveData(false)
    val modifie: LiveData<Boolean> = _modifie

    /** V2 : défilement de l'affichage (toujours borné), conservé à la rotation. */
    private val _defilement = MutableLiveData(Defilement.ORIGINE)
    val defilement: LiveData<Defilement> = _defilement

    /**
     * V2 : vrai pendant une lecture ou une écriture de fichier (indicateur de traitement).
     * Les actions incompatibles (gestes, Nouveau réseau, Ouvrir, Enregistrer...) sont alors bloquées.
     */
    private val _operationEnCours = MutableLiveData(false)
    val operationEnCours: LiveData<Boolean> = _operationEnCours

    /** V2 : noms des sauvegardes à proposer (boîte « Ouvrir » affichée), ou null. */
    private val _sauvegardes = MutableLiveData<List<String>?>(null)
    val sauvegardes: LiveData<List<String>?> = _sauvegardes

    /** V2 : message à afficher une fois (résultat d'Enregistrer / Ouvrir), ou null. */
    private val _message = MutableLiveData<MessageReseau?>(null)
    val message: LiveData<MessageReseau?> = _message

    /** V2 : vrai tant que la boîte est ouverte ; aucun geste ne doit alors modifier le graphe. */
    private val saisieConnexionEnCours: Boolean
        get() = _connexionEnAttente.value != null

    /** V2 : boîte « Nouvelle connexion » ouverte ou opération de fichier en cours. */
    private val gestesBloques: Boolean
        get() = saisieConnexionEnCours || _operationEnCours.value == true

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

    /** V2 : le modèle a changé : on le note (une seule notification) puis on publie. */
    private fun publierModification() {
        if (_modifie.value != true) _modifie.value = true
        publierGraphe()
    }

    /**
     * V2 : publie le défilement [x], [y] ramené dans les bornes, sur chaque axe :
     * 0 ≤ décalage ≤ max(0, taillePlan − tailleVue). Plan plus petit que la vue : 0, sans centrage.
     */
    private fun definirDefilement(x: Float, y: Float) {
        val maxX = (graphe.largeurPlan - largeurVue).coerceAtLeast(0).toFloat()
        val maxY = (graphe.hauteurPlan - hauteurVue).coerceAtLeast(0).toFloat()
        val borne = Defilement(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY))
        if (borne != _defilement.value) _defilement.value = borne
    }

    /** V2 : défilement actuel re-borné (après un changement de taille de la vue ou de plan). */
    private fun rebornerDefilement() {
        val actuel = _defilement.value ?: Defilement.ORIGINE
        definirDefilement(actuel.x, actuel.y)
    }

    // =====================================================================
    // 3. GUICHET (appelé par la Vue)
    // =====================================================================

    /** Appelé à la création de l'écran, avec la taille du plan courant. */
    fun definirPlan(largeur: Int, hauteur: Int) {
        graphe.definirDimensionsPlan(largeur, hauteur)
        rebornerDefilement() // V2
        publierGraphe()
    }

    /** V2 : la vue a changé de taille (premier affichage, rotation) : le défilement est re-borné. */
    fun definirTailleVue(largeur: Int, hauteur: Int) {
        largeurVue = largeur
        hauteurVue = hauteur
        rebornerDefilement()
    }

    /**
     * V2 : Nouveau réseau : graphe vide sur [nouveauPlan], de taille [largeur] x [hauteur] pixels.
     * Annule le geste et la connexion en attente ; le réseau n'est plus « modifié ».
     * Le défilement revient en (0, 0).
     */
    fun nouveauReseau(nouveauPlan: Plan, largeur: Int, hauteur: Int) {
        if (_operationEnCours.value == true) return
        val vide = Graph()
        vide.definirDimensionsPlan(largeur, hauteur)
        remplacerReseau(nouveauPlan, vide, nom = null)
    }

    /**
     * V2 : remplace tout le réseau (Nouveau réseau, Ouvrir) : geste et connexion en attente annulés,
     * défilement en (0, 0), réseau non « modifié ».
     */
    private fun remplacerReseau(nouveauPlan: Plan, nouveauGraphe: Graph, nom: String?) {
        annulerConnexionEnAttente()
        annulerGeste()
        graphe = nouveauGraphe
        nomReseau = nom
        _plan.value = nouveauPlan
        _modifie.value = false
        definirDefilement(0f, 0f)
        publierGraphe()
    }

    // ---------- V2 : Enregistrer / Ouvrir ----------

    /** Vrai si [nom] (trimé) peut servir de nom de sauvegarde : lettres, chiffres, espaces, - et _. */
    fun nomSauvegardeValide(nom: String): Boolean = StockageReseaux.nomValide(nom.trim())

    /** Vrai si une sauvegarde porte déjà ce nom (simple test d'existence, instantané). */
    fun sauvegardeExiste(nom: String): Boolean = stockage.existe(nom.trim())

    /**
     * Enregistre le réseau sous [nom] (remplace une sauvegarde existante : la Vue a déjà confirmé).
     * Le JSON est produit tout de suite (instantané du graphe), l'écriture se fait hors du thread principal.
     */
    fun enregistrer(nom: String) {
        if (_operationEnCours.value == true) return
        val propre = nom.trim()
        if (!StockageReseaux.nomValide(propre)) return
        val contenu = FormatReseau.versJson(_plan.value ?: Plan.PAR_DEFAUT, graphe)
        _operationEnCours.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { stockage.ecrire(propre, contenu) }
                nomReseau = propre
                _modifie.value = false
                _message.value = MessageReseau(MessageReseau.Type.ENREGISTRE, propre)
            } catch (e: IOException) {
                _message.value = MessageReseau(MessageReseau.Type.ERREUR_ENREGISTREMENT, propre)
            } finally {
                _operationEnCours.value = false
            }
        }
    }

    /** Lit la liste des sauvegardes ; la Vue ouvre la boîte « Ouvrir » quand [sauvegardes] est publiée. */
    fun demanderListeSauvegardes() {
        if (_operationEnCours.value == true) return
        _operationEnCours.value = true
        viewModelScope.launch {
            try {
                val noms = withContext(Dispatchers.IO) { stockage.lister() }
                if (noms.isEmpty()) {
                    _message.value = MessageReseau(MessageReseau.Type.AUCUNE_SAUVEGARDE)
                } else {
                    _sauvegardes.value = noms
                }
            } finally {
                _operationEnCours.value = false
            }
        }
    }

    /** Boîte « Ouvrir » fermée sans choix (Annuler, retour, clic hors de la boîte). */
    fun fermerListeSauvegardes() {
        _sauvegardes.value = null
    }

    /**
     * Ouvre la sauvegarde [nom]. Lecture et validation hors du thread principal ; le réseau courant
     * n'est remplacé que si TOUT le fichier est valide. Sinon : message, et rien ne change.
     * [dimensions] = taille de l'image de chaque plan (connue de la Vue), pour valider les positions.
     */
    fun ouvrir(nom: String, dimensions: Map<Plan, Pair<Int, Int>>) {
        if (_operationEnCours.value == true) return
        _sauvegardes.value = null
        _operationEnCours.value = true
        viewModelScope.launch {
            try {
                val (planLu, grapheLu) = withContext(Dispatchers.IO) {
                    FormatReseau.depuisJson(stockage.lire(nom), dimensions)
                }
                remplacerReseau(planLu, grapheLu, nom)
                _message.value = MessageReseau(MessageReseau.Type.OUVERT, nom)
            } catch (e: IOException) {
                _message.value = MessageReseau(MessageReseau.Type.ERREUR_LECTURE, nom)
            } catch (e: SauvegardeInvalideException) {
                _message.value = MessageReseau(MessageReseau.Type.FICHIER_INVALIDE, nom)
            } finally {
                _operationEnCours.value = false
            }
        }
    }

    /** La Vue a affiché [message] : il ne sera pas réaffiché (après une rotation par exemple). */
    fun messageAffiche() {
        _message.value = null
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
        !gestesBloques &&                               // V2 : ni saisie de connexion ni opération de fichier
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
        publierModification()                                        // 3. publier
        return true
    }

    // ---------- Glissement ----------

    /**
     * Le doigt se pose en (x, y).
     * [objetTouche] = objet sous le doigt, ou null.
     * [connexionTouchee] = connexion dont l'étiquette est sous le doigt (seulement si aucun objet).
     */
    fun debutGlissement(x: Float, y: Float, objetTouche: Objet?, connexionTouchee: Connexion?) {
        if (gestesBloques) return // V2 : bloqués pendant la saisie ou une opération de fichier
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
            ModeEdition.NAVIGATION -> {
                // V2 : n'importe où (fond, objet, étiquette) ; position du doigt à l'écran.
                val (xEcran, yEcran) = versEcran(x, y)
                dernierXEcran = xEcran
                dernierYEcran = yEcran
            }
        }
    }

    /**
     * V2 : point du plan (x, y) en coordonnées ÉCRAN : xVue = xPlan − défilement.
     * La vue a converti le toucher avec ce même défilement, donc on retrouve la position réelle du doigt.
     */
    private fun versEcran(x: Float, y: Float): Pair<Float, Float> {
        val d = _defilement.value ?: Defilement.ORIGINE
        return x - d.x to y - d.y
    }

    /** Le doigt bouge en (x, y). */
    fun glissement(x: Float, y: Float) {
        if (gestesBloques) return // V2 : bloqués pendant la saisie ou une opération de fichier
        when (modeCourant) {
            ModeEdition.MODIFICATION -> {
                // V2 : courbure d'une connexion (seule la partie perpendiculaire à AB compte)
                val connexion = connexionCourbee
                if (connexion != null) {
                    val (nx, ny) = connexion.normale()
                    val nouvelleFleche = connexion.fleche + (x - dernierX) * nx + (y - dernierY) * ny
                    dernierX = x
                    dernierY = y
                    if (graphe.modifierFleche(connexion, nouvelleFleche)) publierModification()
                    return
                }
                // Déplacement d'un objet : nouveau centre = doigt + décalage (le modèle borne au plan)
                val objet = objetSelectionne ?: return
                if (graphe.modifiePosition(objet, x + decalageX, y + decalageY)) publierModification()
            }
            ModeEdition.AJOUT_CONNEXION -> {
                // On recrée la connexion provisoire avec la nouvelle position du doigt
                val provisoire = _connexionProvisoire.value ?: return
                _connexionProvisoire.value = provisoire.copy(xDoigt = x, yDoigt = y)
            }
            ModeEdition.AJOUT_OBJET -> Unit // en mode ajout d'objet, le glissement ne produit rien
            ModeEdition.NAVIGATION -> {
                // V2 : le plan suit le doigt. On utilise la différence entre deux positions ÉCRAN
                // successives (en coordonnées du plan, le doigt « bougerait » avec le défilement).
                val (xEcran, yEcran) = versEcran(x, y)
                val d = _defilement.value ?: Defilement.ORIGINE
                definirDefilement(d.x - (xEcran - dernierXEcran), d.y - (yEcran - dernierYEcran))
                dernierXEcran = xEcran
                dernierYEcran = yEcran
            }
        }
    }

    /**
     * Le doigt se lève. [objetSousLeDoigt] = objet sous le doigt, ou null (fond).
     * En AJOUT_CONNEXION, si le modèle accepte la connexion (pas de boucle, pas de doublon) :
     * V2 : elle n'est PAS encore ajoutée. Elle passe « en attente » de son étiquette
     * et l'aperçu reste affiché entre les deux objets. Sinon, le geste se termine.
     */
    fun finGlissement(objetSousLeDoigt: Objet?) {
        if (gestesBloques) return // V2 : bloqués pendant la saisie ou une opération de fichier
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
            if (abs(it.fleche) < SEUIL_REDRESSEMENT && graphe.modifierFleche(it, 0f)) publierModification()
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
        publierModification()
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
        !gestesBloques && modeCourant == ModeEdition.MODIFICATION && objet in graphe.objets

    /** Appui long sur l'étiquette d'une connexion : vrai si la Vue doit ouvrir son menu. */
    fun peutModifierConnexion(connexion: Connexion): Boolean =
        !gestesBloques && modeCourant == ModeEdition.MODIFICATION && connexion in graphe.connexions

    /**
     * L'utilisateur a validé le menu de l'objet [id].
     * @return false si l'étiquette est refusée (vide...) : rien n'est modifié, la Vue garde la boîte ouverte.
     */
    fun modifierObjet(id: Long, etiquette: String, couleur: CouleurObjet): Boolean {
        val objet = graphe.trouverObjet(id) ?: return true // objet disparu : rien à modifier
        if (!graphe.modifierEtiquette(objet, etiquette)) return false
        graphe.modifierCouleur(objet, couleur)
        publierModification()
        return true
    }

    /** Supprime l'objet [id] et toutes ses connexions. */
    fun supprimerObjet(id: Long) {
        val objet = graphe.trouverObjet(id) ?: return
        annulerGeste()
        if (graphe.supprimerObjet(objet)) publierModification()
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
        publierModification()
        return true
    }

    /** Supprime la connexion entre [id1] et [id2]. */
    fun supprimerConnexion(id1: Long, id2: Long) {
        val connexion = graphe.trouverConnexion(id1, id2) ?: return
        annulerGeste()
        if (graphe.supprimerConnexion(connexion)) publierModification()
    }

    // ---------- Réinitialisation ----------

    /** Après confirmation : supprime objets et connexions, garde le plan. */
    fun reinitialiser() {
        if (_operationEnCours.value == true) return // V2
        annulerConnexionEnAttente() // V2 : abandonne une connexion en attente d'étiquette
        annulerGeste()              // annule le dernier geste
        graphe.resetAll()           // vide le graphe
        publierModification()       // V2 : réseau modifié, la Vue est prévenue
    }

    private companion object {
        /** En dessous de cette flèche (en pixels du plan), on remet la connexion droite. */
        const val SEUIL_REDRESSEMENT = 20f
    }
}