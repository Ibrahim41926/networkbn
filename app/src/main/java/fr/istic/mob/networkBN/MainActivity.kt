package fr.istic.mob.networkBN

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.RadioGroup
import android.widget.ScrollView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import fr.istic.mob.networkBN.model.Connexion
import fr.istic.mob.networkBN.model.CouleurObjet
import fr.istic.mob.networkBN.model.Objet
import fr.istic.mob.networkBN.model.Plan
import fr.istic.mob.networkBN.view.GrapheView
import fr.istic.mob.networkBN.view.SelecteurCouleur
import fr.istic.mob.networkBN.view.dimensionsPlan
import fr.istic.mob.networkBN.view.nom
import fr.istic.mob.networkBN.viewModel.GraphViewModel
import fr.istic.mob.networkBN.viewModel.ModeEdition
 
/**
 * La Vue : affiche l'état du [GraphViewModel] et lui transmet les gestes.
 * Les boîtes de dialogue sont recréées après une rotation d'écran.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: GraphViewModel by viewModels()

    private lateinit var vueGraphe: GrapheView
    private lateinit var barreOutils: MaterialToolbar

    /** Position de l'objet en cours de création (boîte de saisie ouverte), ou null. */
    private var saisieX: Float? = null
    private var saisieY: Float? = null

    private var dialogueEtiquette: AlertDialog? = null
    private var champEtiquette: TextInputEditText? = null
    /** V2 : pastilles de la boîte « Nouvel objet » ouverte (couleur non mémorisée d'une ouverture à l'autre). */
    private var selecteurObjet: SelecteurCouleur? = null
    private var dialogueReinitialiser: AlertDialog? = null

    /** V2 : boîte « Nouvelle connexion » (ouverte tant que le ViewModel a une connexion en attente). */
    private var dialogueConnexion: AlertDialog? = null
    private var champConnexion: TextInputEditText? = null
    private var selecteurConnexion: SelecteurCouleur? = null
    /** V2 : texte et couleur saisis avant une rotation, remis dans la boîte recréée. */
    private var texteConnexionRestaure: String = ""
    private var couleurConnexionRestauree: CouleurObjet = Connexion.COULEUR_PAR_DEFAUT

    /** V2 : menu « Modifier l'objet » : identifiant de l'objet visé (null si fermé). */
    private var idObjetModifie: Long? = null
    private var dialogueModifObjet: AlertDialog? = null
    private var champModifObjet: TextInputEditText? = null
    private var selecteurModifObjet: SelecteurCouleur? = null

    /** V2 : menu « Modifier la connexion » : identifiants de ses deux extrémités (null si fermé). */
    private var idsConnexionModifiee: Pair<Long, Long>? = null
    private var dialogueModifConnexion: AlertDialog? = null
    private var champModifConnexion: TextInputEditText? = null
    private var selecteurModifConnexion: SelecteurCouleur? = null
    private var groupeEpaisseur: RadioGroup? = null

    /** V2 : Nouveau réseau : confirmation (réseau non enregistré), puis choix du plan. */
    private var dialogueConfirmationNouveau: AlertDialog? = null
    private var dialogueChoixPlan: AlertDialog? = null
    /** V2 : plan coché dans la boîte « Nouveau réseau » (null si fermée). */
    private var planChoisi: Plan? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        barreOutils = findViewById(R.id.barre_outils)
        setSupportActionBar(barreOutils)
        // En haut de l'app : noms du binôme en titre, mode actif en sous-titre.
        title = getString(
            R.string.titre_binome,
            Auteurs.PRENOM_AUTEUR_1, Auteurs.NOM_AUTEUR_1,
            Auteurs.PRENOM_AUTEUR_2, Auteurs.NOM_AUTEUR_2
        )

        // Écran bord à bord (obligatoire avec targetSdk 35+) : on évite les barres système.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.racine)) { vue, insets ->
            val barres = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            vue.setPadding(barres.left, barres.top, barres.right, barres.bottom)
            insets
        }

        vueGraphe = findViewById(R.id.vue_graphe)
        // V2 : les limites du graphe suivent la taille du plan courant (conservé par le ViewModel).
        val (largeur, hauteur) = dimensionsPlan(resources, viewModel.plan.value ?: Plan.PAR_DEFAUT)
        viewModel.definirPlan(largeur, hauteur)
        vueGraphe.ecouteur = ecouteurGestes

        observerViewModel()
        restaurerDialogues(savedInstanceState)
    }

    // ---------------- Observation du ViewModel ----------------

    private fun observerViewModel() {
        // Le ViewModel republie la même instance : l'observateur est appelé à chaque fois.
        viewModel.grapheObservable.observe(this) { vueGraphe.graphe = it }
        viewModel.plan.observe(this) { vueGraphe.plan = it } // V2 : plan affiché
        viewModel.connexionProvisoire.observe(this) { vueGraphe.connexionProvisoire = it }
        viewModel.objetDeplace.observe(this) { vueGraphe.objetDeplace = it } // V2 : anneau de sélection
        // V2 : la boîte « Nouvelle connexion » suit l'état du ViewModel (donc survit à la rotation).
        viewModel.connexionEnAttente.observe(this) { attente ->
            if (attente != null) {
                if (dialogueConnexion == null) ouvrirSaisieConnexion()
            } else {
                dialogueConnexion?.dismiss()
                fermerSaisieConnexion()
            }
        }
        viewModel.mode.observe(this) { mode ->
            barreOutils.subtitle = getString(R.string.sous_titre_mode, getString(libelleMode(mode)))

            invalidateOptionsMenu()
        }
    }

    private fun libelleMode(mode: ModeEdition): Int = when (mode) {
        ModeEdition.AJOUT_OBJET -> R.string.mode_ajout_objet
        ModeEdition.AJOUT_CONNEXION -> R.string.mode_ajout_connexion
        ModeEdition.MODIFICATION -> R.string.mode_modification
    }

    // ---------------- Gestes (vue -> ViewModel) ----------------

    private val ecouteurGestes = object : GrapheView.EcouteurGestes {
        override fun appuiLong(x: Float, y: Float, objetTouche: Objet?, connexionTouchee: Connexion?): Boolean {
            when {
                viewModel.peutAjouterObjet(x, y, objetTouche, connexionTouchee) -> ouvrirSaisieEtiquette(x, y)
                // V2 : menus en mode modification (objets prioritaires sur les connexions).
                objetTouche != null && viewModel.peutModifierObjet(objetTouche) -> ouvrirMenuObjet(objetTouche.id)
                connexionTouchee != null && viewModel.peutModifierConnexion(connexionTouchee) ->
                    ouvrirMenuConnexion(connexionTouchee.extremite1.id, connexionTouchee.extremite2.id)
                else -> return false
            }
            return true
        }

        override fun debutGlissement(x: Float, y: Float, objetTouche: Objet?, connexionTouchee: Connexion?) =
            viewModel.debutGlissement(x, y, objetTouche, connexionTouchee)

        override fun glissement(x: Float, y: Float) = viewModel.glissement(x, y)

        override fun finGlissement(objetSousLeDoigt: Objet?) =
            viewModel.finGlissement(objetSousLeDoigt)

        override fun gesteAnnule() = viewModel.annulerGeste()
    }

    // ---------------- Menu ----------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_principal, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val idCoche = when (viewModel.mode.value ?: ModeEdition.MODIFICATION) {
            ModeEdition.AJOUT_OBJET -> R.id.mode_ajout_objet
            ModeEdition.AJOUT_CONNEXION -> R.id.mode_ajout_connexion
            ModeEdition.MODIFICATION -> R.id.mode_modification
        }
        menu.findItem(idCoche)?.isChecked = true
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mode_ajout_objet -> viewModel.changerMode(ModeEdition.AJOUT_OBJET)
            R.id.mode_ajout_connexion -> viewModel.changerMode(ModeEdition.AJOUT_CONNEXION)
            R.id.mode_modification -> viewModel.changerMode(ModeEdition.MODIFICATION)
            R.id.action_nouveau_reseau -> demanderNouveauReseau() // V2
            R.id.action_reinitialiser -> ouvrirConfirmationReinitialiser()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---------------- Boîte de saisie d'étiquette ----------------

    private fun ouvrirSaisieEtiquette(
        x: Float,
        y: Float,
        texteInitial: String = "",
        couleurInitiale: CouleurObjet = CouleurObjet.PAR_DEFAUT // V2
    ) {
        saisieX = x
        saisieY = y

        val contenu = layoutInflater.inflate(R.layout.dialogue_etiquette, null)
        val cadre = contenu.findViewById<TextInputLayout>(R.id.cadre_etiquette)
        val champ = contenu.findViewById<TextInputEditText>(R.id.champ_etiquette)
        champ.setText(texteInitial)
        champEtiquette = champ
        // V2 : choix de la couleur (vert à l'ouverture, ou couleur restaurée après rotation).
        val selecteur = SelecteurCouleur(contenu.findViewById(R.id.pastilles_couleur), couleurInitiale)
        selecteurObjet = selecteur

        val dialogue = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.titre_ajout_objet)
            .setView(contenu)
            // Listener posé plus bas : la boîte ne doit pas se fermer si l'étiquette est refusée.
            .setPositiveButton(R.string.bouton_ok, null)
            .setNegativeButton(R.string.bouton_annuler) { _, _ -> fermerSaisieEtiquette() }
            .setOnCancelListener { fermerSaisieEtiquette() }
            .create()

        val valider = {
            if (viewModel.validerEtiquette(champ.text?.toString().orEmpty(), x, y, selecteur.couleur)) {
                dialogue.dismiss()
                fermerSaisieEtiquette()
            } else {
                cadre.error = getString(R.string.erreur_etiquette_vide)
            }
        }

        dialogue.setOnShowListener {
            dialogue.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { valider() }
        }
        champ.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                valider()
                true
            } else {
                false
            }
        }

        dialogue.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialogue.show()
        champ.requestFocus()
        dialogueEtiquette = dialogue
    }

    private fun fermerSaisieEtiquette() {
        saisieX = null
        saisieY = null
        dialogueEtiquette = null
        champEtiquette = null
        // V2 : la couleur n'est pas mémorisée pour la prochaine ouverture.
        selecteurObjet = null
    }

    // ---------------- V2 : boîte « Nouvelle connexion » ----------------

    private fun ouvrirSaisieConnexion() {
        val contenu = layoutInflater.inflate(R.layout.dialogue_etiquette_connexion, null)
        val cadre = contenu.findViewById<TextInputLayout>(R.id.cadre_etiquette_connexion)
        val champ = contenu.findViewById<TextInputEditText>(R.id.champ_etiquette_connexion)
        champ.setText(texteConnexionRestaure)
        texteConnexionRestaure = ""
        champConnexion = champ
        // V2 : choix de la couleur de la connexion (bleu à l'ouverture, ou restaurée après rotation).
        val selecteur = SelecteurCouleur(contenu.findViewById(R.id.pastilles_couleur_connexion), couleurConnexionRestauree)
        couleurConnexionRestauree = Connexion.COULEUR_PAR_DEFAUT
        selecteurConnexion = selecteur

        val dialogue = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.titre_ajout_connexion)
            .setView(contenu)
            // Listener posé plus bas : la boîte ne doit pas se fermer si l'étiquette est refusée.
            .setPositiveButton(R.string.bouton_ok, null)
            // Annuler, retour ou clic hors de la boîte : le ViewModel efface l'aperçu,
            // puis l'observateur ferme la boîte.
            .setNegativeButton(R.string.bouton_annuler) { _, _ -> viewModel.annulerConnexionEnAttente() }
            .setOnCancelListener { viewModel.annulerConnexionEnAttente() }
            .create()

        // Si accepté, l'observateur de connexionEnAttente ferme la boîte.
        val valider = {
            if (!viewModel.validerEtiquetteConnexion(champ.text?.toString().orEmpty(), selecteur.couleur)) {
                cadre.error = getString(R.string.erreur_etiquette_vide)
            }
        }

        dialogue.setOnShowListener {
            dialogue.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { valider() }
        }
        champ.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                valider()
                true
            } else {
                false
            }
        }

        dialogue.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialogue.show()
        champ.requestFocus()
        dialogueConnexion = dialogue
    }

    private fun fermerSaisieConnexion() {
        dialogueConnexion = null
        champConnexion = null
        selecteurConnexion = null // V2 : couleur non mémorisée pour la prochaine ouverture
    }

    // ---------------- V2 : menu d'un objet (appui long en mode modification) ----------------

    /**
     * Boîte « Modifier l'objet » : étiquette + couleur, boutons Supprimer / Annuler / OK.
     * [texteInitial] et [couleurInitiale] : valeurs saisies avant une rotation (sinon celles de l'objet).
     */
    private fun ouvrirMenuObjet(id: Long, texteInitial: String? = null, couleurInitiale: CouleurObjet? = null) {
        val objet = viewModel.grapheObservable.value?.trouverObjet(id) ?: return
        idObjetModifie = id

        val contenu = layoutInflater.inflate(R.layout.dialogue_etiquette, null)
        val cadre = contenu.findViewById<TextInputLayout>(R.id.cadre_etiquette)
        val champ = contenu.findViewById<TextInputEditText>(R.id.champ_etiquette)
        champ.setText(texteInitial ?: objet.etiquette)
        champModifObjet = champ
        val selecteur = SelecteurCouleur(contenu.findViewById(R.id.pastilles_couleur), couleurInitiale ?: objet.couleur)
        selecteurModifObjet = selecteur

        val dialogue = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.titre_modifier_objet)
            .setView(contenu)
            // Listener posé plus bas : la boîte ne doit pas se fermer si l'étiquette est refusée.
            .setPositiveButton(R.string.bouton_ok, null)
            .setNeutralButton(R.string.bouton_supprimer) { _, _ ->
                viewModel.supprimerObjet(id)
                fermerMenuObjet()
            }
            .setNegativeButton(R.string.bouton_annuler) { _, _ -> fermerMenuObjet() }
            .setOnCancelListener { fermerMenuObjet() }
            .create()

        val valider = {
            if (viewModel.modifierObjet(id, champ.text?.toString().orEmpty(), selecteur.couleur)) {
                dialogue.dismiss()
                fermerMenuObjet()
            } else {
                cadre.error = getString(R.string.erreur_etiquette_vide)
            }
        }

        dialogue.setOnShowListener {
            dialogue.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { valider() }
        }
        champ.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                valider()
                true
            } else {
                false
            }
        }

        dialogue.show()
        dialogueModifObjet = dialogue
    }

    private fun fermerMenuObjet() {
        idObjetModifie = null
        dialogueModifObjet = null
        champModifObjet = null
        selecteurModifObjet = null
    }

    // ---------------- V2 : menu d'une connexion (appui long sur son étiquette) ----------------

    /** Boutons d'épaisseur du layout, dans le même ordre que [Connexion.EPAISSEURS]. */
    private val boutonsEpaisseur = listOf(R.id.epaisseur_fine, R.id.epaisseur_moyenne, R.id.epaisseur_epaisse)

    /**
     * Boîte « Modifier la connexion » : étiquette + couleur + épaisseur, boutons Supprimer / Annuler / OK.
     * La connexion est désignée par les identifiants de ses extrémités [id1] et [id2].
     */
    private fun ouvrirMenuConnexion(
        id1: Long,
        id2: Long,
        texteInitial: String? = null,
        couleurInitiale: CouleurObjet? = null,
        epaisseurInitiale: Float? = null
    ) {
        val connexion = viewModel.grapheObservable.value?.trouverConnexion(id1, id2) ?: return
        idsConnexionModifiee = id1 to id2

        val contenu = layoutInflater.inflate(R.layout.dialogue_modifier_connexion, null)
        val cadre = contenu.findViewById<TextInputLayout>(R.id.cadre_modif_connexion)
        val champ = contenu.findViewById<TextInputEditText>(R.id.champ_modif_connexion)
        champ.setText(texteInitial ?: connexion.etiquette)
        champModifConnexion = champ
        val selecteur = SelecteurCouleur(
            contenu.findViewById(R.id.pastilles_modif_connexion), couleurInitiale ?: connexion.couleur
        )
        selecteurModifConnexion = selecteur
        val groupe = contenu.findViewById<RadioGroup>(R.id.groupe_epaisseur)
        // Épaisseur inconnue (ne devrait pas arriver) : on coche la moyenne.
        val indice = Connexion.EPAISSEURS.indexOf(epaisseurInitiale ?: connexion.epaisseur)
            .takeIf { it >= 0 } ?: Connexion.EPAISSEURS.indexOf(Connexion.EPAISSEUR_PAR_DEFAUT)
        groupe.check(boutonsEpaisseur[indice])
        groupeEpaisseur = groupe

        val dialogue = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.titre_modifier_connexion)
            .setView(contenu)
            // Listener posé plus bas : la boîte ne doit pas se fermer si l'étiquette est refusée.
            .setPositiveButton(R.string.bouton_ok, null)
            .setNeutralButton(R.string.bouton_supprimer) { _, _ ->
                viewModel.supprimerConnexion(id1, id2)
                fermerMenuConnexion()
            }
            .setNegativeButton(R.string.bouton_annuler) { _, _ -> fermerMenuConnexion() }
            .setOnCancelListener { fermerMenuConnexion() }
            .create()

        val valider = {
            if (viewModel.modifierConnexion(
                    id1, id2, champ.text?.toString().orEmpty(), selecteur.couleur, epaisseurChoisie(groupe)
                )
            ) {
                dialogue.dismiss()
                fermerMenuConnexion()
            } else {
                cadre.error = getString(R.string.erreur_etiquette_vide)
            }
        }

        dialogue.setOnShowListener {
            dialogue.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { valider() }
        }
        champ.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                valider()
                true
            } else {
                false
            }
        }

        dialogue.show()
        dialogueModifConnexion = dialogue
    }

    private fun epaisseurChoisie(groupe: RadioGroup): Float {
        val indice = boutonsEpaisseur.indexOf(groupe.checkedRadioButtonId)
        return Connexion.EPAISSEURS.getOrElse(indice) { Connexion.EPAISSEUR_PAR_DEFAUT }
    }

    private fun fermerMenuConnexion() {
        idsConnexionModifiee = null
        dialogueModifConnexion = null
        champModifConnexion = null
        selecteurModifConnexion = null
        groupeEpaisseur = null
    }

    // ---------------- V2 : Nouveau réseau ----------------

    /** Réseau modifié non enregistré : confirmation d'abord ; sinon, directement le choix du plan. */
    private fun demanderNouveauReseau() {
        if (viewModel.modifie.value == true) ouvrirConfirmationNouveau() else ouvrirChoixPlan()
    }

    private fun ouvrirConfirmationNouveau() {
        dialogueConfirmationNouveau = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.titre_reseau_non_enregistre)
            .setMessage(R.string.message_reseau_non_enregistre)
            .setPositiveButton(R.string.bouton_ok) { _, _ ->
                dialogueConfirmationNouveau = null
                ouvrirChoixPlan()
            }
            .setNegativeButton(R.string.bouton_annuler) { _, _ -> dialogueConfirmationNouveau = null }
            .setOnCancelListener { dialogueConfirmationNouveau = null }
            .show()
    }

    /** Boîte « Choisir un plan » : un bouton radio par plan, [planInitial] coché (plan courant par défaut). */
    private fun ouvrirChoixPlan(planInitial: Plan = viewModel.plan.value ?: Plan.PAR_DEFAUT) {
        planChoisi = planInitial
        val plans = Plan.entries
        val noms = plans.map { getString(it.nom) }.toTypedArray()

        dialogueChoixPlan = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.titre_choisir_plan)
            .setSingleChoiceItems(noms, plans.indexOf(planInitial)) { _, indice -> planChoisi = plans[indice] }
            .setPositiveButton(R.string.bouton_ok) { _, _ ->
                val plan = planChoisi ?: planInitial
                fermerChoixPlan()
                val (largeur, hauteur) = dimensionsPlan(resources, plan)
                viewModel.nouveauReseau(plan, largeur, hauteur)
                // Défilement remis en (0, 0). L'étape 6b le déplacera dans la vue.
                findViewById<ScrollView>(R.id.defilement_vertical).scrollTo(0, 0)
                findViewById<HorizontalScrollView>(R.id.defilement_horizontal).scrollTo(0, 0)
            }
            .setNegativeButton(R.string.bouton_annuler) { _, _ -> fermerChoixPlan() }
            .setOnCancelListener { fermerChoixPlan() }
            .show()
    }

    private fun fermerChoixPlan() {
        dialogueChoixPlan = null
        planChoisi = null
    }

    // ---------------- Confirmation de réinitialisation ----------------

    private fun ouvrirConfirmationReinitialiser() {
        dialogueReinitialiser = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.titre_reinitialiser)
            .setMessage(R.string.message_reinitialiser)
            .setPositiveButton(R.string.bouton_ok) { _, _ ->
                viewModel.reinitialiser()
                dialogueReinitialiser = null
            }
            .setNegativeButton(R.string.bouton_annuler) { _, _ -> dialogueReinitialiser = null }
            .setOnCancelListener { dialogueReinitialiser = null }
            .show()
    }

    // ---------------- Rotation d'écran ----------------

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val x = saisieX
        val y = saisieY
        if (x != null && y != null) {
            outState.putFloat(CLE_SAISIE_X, x)
            outState.putFloat(CLE_SAISIE_Y, y)
            outState.putString(CLE_SAISIE_TEXTE, champEtiquette?.text?.toString().orEmpty())
            selecteurObjet?.let { outState.putString(CLE_SAISIE_COULEUR, it.couleur.id) } // V2
        }
        outState.putBoolean(CLE_CONFIRMATION, dialogueReinitialiser != null)
        // V2 : la connexion en attente est dans le ViewModel ; seuls le texte et la couleur sont à sauver.
        champConnexion?.let { outState.putString(CLE_CONNEXION_TEXTE, it.text?.toString().orEmpty()) }
        selecteurConnexion?.let { outState.putString(CLE_CONNEXION_COULEUR, it.couleur.id) }
        // V2 : menus ouverts : la cible (identifiants) et ce qui a été saisi.
        idObjetModifie?.let { id ->
            outState.putLong(CLE_MENU_OBJET, id)
            outState.putString(CLE_MENU_OBJET_TEXTE, champModifObjet?.text?.toString().orEmpty())
            selecteurModifObjet?.let { outState.putString(CLE_MENU_OBJET_COULEUR, it.couleur.id) }
        }
        idsConnexionModifiee?.let { (id1, id2) ->
            outState.putLongArray(CLE_MENU_CONNEXION, longArrayOf(id1, id2))
            outState.putString(CLE_MENU_CONNEXION_TEXTE, champModifConnexion?.text?.toString().orEmpty())
            selecteurModifConnexion?.let { outState.putString(CLE_MENU_CONNEXION_COULEUR, it.couleur.id) }
            groupeEpaisseur?.let { outState.putFloat(CLE_MENU_CONNEXION_EPAISSEUR, epaisseurChoisie(it)) }
        }
        // V2 : Nouveau réseau : confirmation ouverte, ou plan coché dans la boîte de choix.
        outState.putBoolean(CLE_CONFIRMATION_NOUVEAU, dialogueConfirmationNouveau != null)
        planChoisi?.let { outState.putString(CLE_CHOIX_PLAN, it.id) }
    }

    private fun restaurerDialogues(etat: Bundle?) {
        etat ?: return
        if (etat.containsKey(CLE_SAISIE_X)) {
            ouvrirSaisieEtiquette(
                etat.getFloat(CLE_SAISIE_X),
                etat.getFloat(CLE_SAISIE_Y),
                etat.getString(CLE_SAISIE_TEXTE).orEmpty(),
                CouleurObjet.depuisId(etat.getString(CLE_SAISIE_COULEUR)) // V2
            )
        }
        if (etat.getBoolean(CLE_CONFIRMATION)) ouvrirConfirmationReinitialiser()
        // V2 : la boîte elle-même est rouverte par l'observateur de connexionEnAttente.
        texteConnexionRestaure = etat.getString(CLE_CONNEXION_TEXTE).orEmpty()
        etat.getString(CLE_CONNEXION_COULEUR)?.let { couleurConnexionRestauree = CouleurObjet.depuisId(it) }
        // V2 : menus d'objet et de connexion.
        if (etat.containsKey(CLE_MENU_OBJET)) {
            ouvrirMenuObjet(
                etat.getLong(CLE_MENU_OBJET),
                etat.getString(CLE_MENU_OBJET_TEXTE),
                etat.getString(CLE_MENU_OBJET_COULEUR)?.let { CouleurObjet.depuisId(it) }
            )
        }
        etat.getLongArray(CLE_MENU_CONNEXION)?.let { ids ->
            ouvrirMenuConnexion(
                ids[0],
                ids[1],
                etat.getString(CLE_MENU_CONNEXION_TEXTE),
                etat.getString(CLE_MENU_CONNEXION_COULEUR)?.let { CouleurObjet.depuisId(it) },
                if (etat.containsKey(CLE_MENU_CONNEXION_EPAISSEUR)) etat.getFloat(CLE_MENU_CONNEXION_EPAISSEUR) else null
            )
        }
        // V2 : Nouveau réseau.
        if (etat.getBoolean(CLE_CONFIRMATION_NOUVEAU)) ouvrirConfirmationNouveau()
        etat.getString(CLE_CHOIX_PLAN)?.let { id -> ouvrirChoixPlan(Plan.depuisId(id) ?: Plan.PAR_DEFAUT) }
    }

    override fun onDestroy() {
        // Évite les fuites de fenêtre ; l'état a déjà été sauvegardé dans onSaveInstanceState.
        dialogueEtiquette?.dismiss()
        dialogueReinitialiser?.dismiss()
        dialogueConnexion?.dismiss() // V2 : dismiss (pas cancel) : la connexion reste en attente
        dialogueModifObjet?.dismiss()     // V2
        dialogueModifConnexion?.dismiss() // V2
        dialogueConfirmationNouveau?.dismiss() // V2
        dialogueChoixPlan?.dismiss()           // V2
        super.onDestroy()
    }

    private companion object {
        const val CLE_SAISIE_X = "saisie_x"
        const val CLE_SAISIE_Y = "saisie_y"
        const val CLE_SAISIE_TEXTE = "saisie_texte"
        const val CLE_SAISIE_COULEUR = "saisie_couleur" // V2
        const val CLE_CONFIRMATION = "confirmation_reinitialiser"
        const val CLE_CONNEXION_TEXTE = "connexion_texte" // V2
        const val CLE_CONNEXION_COULEUR = "connexion_couleur" // V2
        const val CLE_MENU_OBJET = "menu_objet" // V2
        const val CLE_MENU_OBJET_TEXTE = "menu_objet_texte" // V2
        const val CLE_MENU_OBJET_COULEUR = "menu_objet_couleur" // V2
        const val CLE_MENU_CONNEXION = "menu_connexion" // V2
        const val CLE_MENU_CONNEXION_TEXTE = "menu_connexion_texte" // V2
        const val CLE_MENU_CONNEXION_COULEUR = "menu_connexion_couleur" // V2
        const val CLE_MENU_CONNEXION_EPAISSEUR = "menu_connexion_epaisseur" // V2
        const val CLE_CONFIRMATION_NOUVEAU = "confirmation_nouveau_reseau" // V2
        const val CLE_CHOIX_PLAN = "choix_plan" // V2
    }
}
