package fr.istic.mob.networkBN

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import fr.istic.mob.networkBN.model.Connexion
import fr.istic.mob.networkBN.model.CouleurObjet
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
    /** Couleur choisie dans la boîte de saisie ouverte (non mémorisée d'une ouverture à l'autre). */
    private var couleurChoisie: CouleurObjet = CouleurObjet.PAR_DEFAUT
    private var pastilles: Map<CouleurObjet, ImageView> = emptyMap()
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
        viewModel.objetDeplace.observe(this) { vueGraphe.objetDeplace = it }
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
        override fun appuiLong(x: Float, y: Float, objetTouche: Objet?) {
            if (viewModel.peutAjouterObjet(x, y, objetTouche)) ouvrirSaisieEtiquette(x, y)
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
        couleurInitiale: CouleurObjet = CouleurObjet.PAR_DEFAUT
    ) {
        saisieX = x
        saisieY = y

        val contenu = layoutInflater.inflate(R.layout.dialogue_etiquette, null)
        val cadre = contenu.findViewById<TextInputLayout>(R.id.cadre_etiquette)
        val champ = contenu.findViewById<TextInputEditText>(R.id.champ_etiquette)
        champ.setText(texteInitial)
        champEtiquette = champ
        couleurChoisie = couleurInitiale
        remplirPastilles(contenu.findViewById(R.id.pastilles_couleur))

        val dialogue = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.titre_ajout_objet)
            .setView(contenu)
            // Listener posé plus bas : la boîte ne doit pas se fermer si l'étiquette est refusée.
            .setPositiveButton(R.string.bouton_ok, null)
            .setNegativeButton(R.string.bouton_annuler) { _, _ -> fermerSaisieEtiquette() }
            .setOnCancelListener { fermerSaisieEtiquette() }
            .create()

        val valider = {
            if (viewModel.validerEtiquette(champ.text?.toString().orEmpty(), x, y, couleurChoisie)) {
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
        couleurChoisie = CouleurObjet.PAR_DEFAUT
        pastilles = emptyMap()
    }

    // ---------------- Pastilles de couleur ----------------

    private fun dpEnPx(valeur: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valeur, resources.displayMetrics).toInt()

    /** Une pastille ronde par couleur, réparties sur la largeur ; un clic sélectionne. */
    private fun remplirPastilles(ligne: LinearLayout) {
        val taille = dpEnPx(TAILLE_PASTILLE_DP)
        pastilles = CouleurObjet.entries.associateWith { couleur ->
            ImageView(this).apply {
                contentDescription = getString(nomCouleur(couleur))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                // La coche reste à l'intérieur du disque.
                val marge = dpEnPx(8f)
                setPadding(marge, marge, marge, marge)
                setOnClickListener {
                    couleurChoisie = couleur
                    rafraichirPastilles()
                }
                // Cellule de largeur égale, pastille de taille fixe centrée dedans.
                val cellule = FrameLayout(this@MainActivity)
                cellule.addView(this, FrameLayout.LayoutParams(taille, taille, Gravity.CENTER))
                ligne.addView(cellule, LinearLayout.LayoutParams(0, taille, 1f))
            }
        }
        rafraichirPastilles()
    }

    /** Met à jour l'aspect des pastilles selon [couleurChoisie]. */
    private fun rafraichirPastilles() {
        val couleurContour = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface, "colorOnSurface"
        )
        for ((couleur, pastille) in pastilles) {
            val choisie = couleur == couleurChoisie
            val disque = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(couleur.argb)
            }
            pastille.background = if (choisie) {
                // Contour épais, séparé du disque par un petit espace (visible même sur le noir).
                val anneau = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dpEnPx(3f), couleurContour)
                }
                LayerDrawable(arrayOf(anneau, disque)).apply {
                    val retrait = dpEnPx(5f)
                    setLayerInset(1, retrait, retrait, retrait, retrait)
                }
            } else {
                disque
            }
            pastille.setImageResource(if (choisie) R.drawable.ic_coche else 0)
            pastille.isSelected = choisie
        }
    }

    private fun nomCouleur(couleur: CouleurObjet): Int = when (couleur) {
        CouleurObjet.ROUGE -> R.string.couleur_rouge
        CouleurObjet.VERT -> R.string.couleur_vert
        CouleurObjet.BLEU -> R.string.couleur_bleu
        CouleurObjet.ORANGE -> R.string.couleur_orange
        CouleurObjet.CYAN -> R.string.couleur_cyan
        CouleurObjet.MAGENTA -> R.string.couleur_magenta
        CouleurObjet.NOIR -> R.string.couleur_noir
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
            outState.putString(CLE_SAISIE_COULEUR, couleurChoisie.id)
        }
        outState.putBoolean(CLE_CONFIRMATION, dialogueReinitialiser != null)
    }

    private fun restaurerDialogues(etat: Bundle?) {
        etat ?: return
        if (etat.containsKey(CLE_SAISIE_X)) {
            ouvrirSaisieEtiquette(
                etat.getFloat(CLE_SAISIE_X),
                etat.getFloat(CLE_SAISIE_Y),
                etat.getString(CLE_SAISIE_TEXTE).orEmpty(),
                CouleurObjet.depuisId(etat.getString(CLE_SAISIE_COULEUR))
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
        const val CLE_SAISIE_COULEUR = "saisie_couleur"
        const val TAILLE_PASTILLE_DP = 28f
        const val CLE_CONFIRMATION = "confirmation_reinitialiser"
    }
}
