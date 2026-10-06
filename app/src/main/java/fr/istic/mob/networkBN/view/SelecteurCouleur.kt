package fr.istic.mob.networkBN.view

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.color.MaterialColors
import fr.istic.mob.networkBN.R
import fr.istic.mob.networkBN.model.CouleurObjet

/**
 * V2 : rangée de pastilles rondes, une par [CouleurObjet], dans [ligne].
 * Utilisée par les boîtes « Nouvel objet » et « Nouvelle connexion ».
 *
 * La pastille choisie a un contour épais et une coche blanche ; un clic sélectionne.
 * [couleur] = couleur actuellement choisie (lue par l'Activity au clic sur OK).
 */
class SelecteurCouleur(private val ligne: LinearLayout, couleurInitiale: CouleurObjet) {

    var couleur: CouleurObjet = couleurInitiale
        private set

    private val contexte = ligne.context

    private fun dpEnPx(valeur: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, valeur, contexte.resources.displayMetrics
    ).toInt()

    private val pastilles: Map<CouleurObjet, ImageView>

    init {
        val taille = dpEnPx(TAILLE_PASTILLE_DP)
        pastilles = CouleurObjet.entries.associateWith { couleur ->
            ImageView(contexte).apply {
                contentDescription = contexte.getString(nomCouleur(couleur))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                // La coche reste à l'intérieur du disque.
                val marge = dpEnPx(8f)
                setPadding(marge, marge, marge, marge)
                setOnClickListener {
                    this@SelecteurCouleur.couleur = couleur
                    rafraichir()
                }
                // Cellule de largeur égale, pastille de taille fixe centrée dedans.
                val cellule = FrameLayout(contexte)
                cellule.addView(this, FrameLayout.LayoutParams(taille, taille, Gravity.CENTER))
                ligne.addView(cellule, LinearLayout.LayoutParams(0, taille, 1f))
            }
        }
        rafraichir()
    }

    /** Met à jour l'aspect des pastilles selon [couleur]. */
    private fun rafraichir() {
        val couleurContour = MaterialColors.getColor(
            ligne, com.google.android.material.R.attr.colorOnSurface
        )
        for ((couleur, pastille) in pastilles) {
            val choisie = couleur == this.couleur
            val disque = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(couleur.argb)
            }
            pastille.background = if (choisie) {
                // Contour épais, séparé du disque par un petit espace (visible même sur le noir).
                val anneau = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dpEnPx(3f), couleurContour) // V2 : épaisseur du contour (3 dp)
                }
                LayerDrawable(arrayOf(anneau, disque)).apply {
                    val retrait = dpEnPx(5f) // V2 : à augmenter si le contour est épaissi
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

    private companion object {
        const val TAILLE_PASTILLE_DP = 28f
    }
}
