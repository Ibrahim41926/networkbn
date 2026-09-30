package fr.istic.mob.networkBN

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
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
import fr.istic.mob.networkBN.model.Objet
import fr.istic.mob.networkBN.view.GrapheView
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
    private var dialogueReinitialiser: AlertDialog? = null

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
        viewModel.definirPlan(vueGraphe.largeurPlan, vueGraphe.hauteurPlan)
        vueGraphe.ecouteur = ecouteurGestes

        observerViewModel()
        restaurerDialogues(savedInstanceState)
    }

    // ---------------- Observation du ViewModel ----------------

    private fun observerViewModel() {
        // Le ViewModel republie la même instance : l'observateur est appelé à chaque fois.
        viewModel.grapheObservable.observe(this) { vueGraphe.graphe = it }
        viewModel.connexionProvisoire.observe(this) { vueGraphe.connexionProvisoire = it }
        viewModel.mode.observe(this) { mode ->
            barreOutils.subtitle = getString(libelleMode(mode))
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
        override fun appuiLong(x: Float, y: Float, objetTouche: Objet?) {
            if (viewModel.peutAjouterObjet(x, y, objetTouche)) ouvrirSaisieEtiquette(x, y)
        }

        override fun debutGlissement(x: Float, y: Float, objetTouche: Objet?) =
            viewModel.debutGlissement(x, y, objetTouche)

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
            R.id.action_reinitialiser -> ouvrirConfirmationReinitialiser()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---------------- Boîte de saisie d'étiquette ----------------

    private fun ouvrirSaisieEtiquette(x: Float, y: Float, texteInitial: String = "") {
        saisieX = x
        saisieY = y

        val contenu = layoutInflater.inflate(R.layout.dialogue_etiquette, null)
        val cadre = contenu.findViewById<TextInputLayout>(R.id.cadre_etiquette)
        val champ = contenu.findViewById<TextInputEditText>(R.id.champ_etiquette)
        champ.setText(texteInitial)
        champEtiquette = champ

        val dialogue = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.titre_ajout_objet)
            .setView(contenu)
            // Listener posé plus bas : la boîte ne doit pas se fermer si l'étiquette est refusée.
            .setPositiveButton(R.string.bouton_ok, null)
            .setNegativeButton(R.string.bouton_annuler) { _, _ -> fermerSaisieEtiquette() }
            .setOnCancelListener { fermerSaisieEtiquette() }
            .create()

        val valider = {
            if (viewModel.validerEtiquette(champ.text?.toString().orEmpty(), x, y)) {
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
        }
        outState.putBoolean(CLE_CONFIRMATION, dialogueReinitialiser != null)
    }

    private fun restaurerDialogues(etat: Bundle?) {
        etat ?: return
        if (etat.containsKey(CLE_SAISIE_X)) {
            ouvrirSaisieEtiquette(
                etat.getFloat(CLE_SAISIE_X),
                etat.getFloat(CLE_SAISIE_Y),
                etat.getString(CLE_SAISIE_TEXTE).orEmpty()
            )
        }
        if (etat.getBoolean(CLE_CONFIRMATION)) ouvrirConfirmationReinitialiser()
    }

    override fun onDestroy() {
        // Évite les fuites de fenêtre ; l'état a déjà été sauvegardé dans onSaveInstanceState.
        dialogueEtiquette?.dismiss()
        dialogueReinitialiser?.dismiss()
        super.onDestroy()
    }

    private companion object {
        const val CLE_SAISIE_X = "saisie_x"
        const val CLE_SAISIE_Y = "saisie_y"
        const val CLE_SAISIE_TEXTE = "saisie_texte"
        const val CLE_CONFIRMATION = "confirmation_reinitialiser"
    }
}
