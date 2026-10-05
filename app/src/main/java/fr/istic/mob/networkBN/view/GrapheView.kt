package fr.istic.mob.networkBN.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import fr.istic.mob.networkBN.R
import fr.istic.mob.networkBN.model.Graph
import fr.istic.mob.networkBN.model.Objet
import fr.istic.mob.networkBN.viewModel.ConnexionProvisoire
import kotlin.math.hypot

/**
 * Dessine le plan, les connexions et les objets, et capte les gestes.
 *
 *  - Le plan est dessiné en (0, 0) SANS mise à l'échelle :
 *    1 pixel image = 1 pixel canevas, donc coordonnées du doigt = coordonnées du plan.
 *  - La vue ne décide RIEN : elle transmet des gestes neutres à [ecouteur]
 *    (« glissement commencé ici, sur tel objet »). Le ViewModel les interprète selon le mode.
 */
class GrapheView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Gestes transmis à l'Activity, qui les relaie au ViewModel. */
    interface EcouteurGestes {
        fun appuiLong(x: Float, y: Float, objetTouche: Objet?)
        fun debutGlissement(x: Float, y: Float, objetTouche: Objet?)
        fun glissement(x: Float, y: Float)
        fun finGlissement(objetSousLeDoigt: Objet?)
        fun gesteAnnule()
    }

    var ecouteur: EcouteurGestes? = null

    // ---------------- Données à dessiner ----------------

    private val plan: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.plan)

    val largeurPlan: Int get() = plan.width
    val hauteurPlan: Int get() = plan.height

    var graphe: Graph? = null
        set(value) {
            field = value
            invalidate()
        }

    var connexionProvisoire: ConnexionProvisoire? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Objet en cours de déplacement, ou null. */
    var objetDeplace: Objet? = null
        set(value) {
            field = value
            invalidate()
        }

    // ---------------- Dimensions (dp / sp -> px) ----------------

    private fun dp(valeur: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valeur, resources.displayMetrics)

    private fun sp(valeur: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, valeur, resources.displayMetrics)

    private val rayonObjet = dp(16f)
    /** Zone de toucher plus large que le dessin, pour viser facilement au doigt. */
    private val rayonTouche = dp(28f)
    private val margeEtiquette = dp(4f)
    private val rayonBulle = dp(6f)

    // ---------------- Pinceaux ----------------

    private val pinceauConnexion = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val pinceauProvisoire = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(3f)
        color = Color.GRAY
        pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(6f)), 0f)
    }

    private val pinceauObjet = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val pinceauContour = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.WHITE
    }

    /** Anneau ambre semi-transparent autour de l'objet sélectionné (il garde sa couleur). */
    private val pinceauSelection = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(6f)
        color = Color.argb(170, 255, 193, 7)
    }

    private val pinceauBulle =Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(230, 255, 255, 255)
    }

    private val pinceauTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = sp(13f)
        textAlign = Paint.Align.CENTER
    }

    /** Réutilisé à chaque dessin (pas d'allocation dans onDraw). */
    private val rectBulle = RectF()

    // ---------------- Taille ----------------

    /** La vue a exactement la taille du plan ; les ScrollView parentes gèrent le défilement. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(plan.width, plan.height)
    }

    // ---------------- Dessin ----------------

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(plan, 0f, 0f, null)
        val g = graphe ?: return

        // 1. Connexions (sous les objets).
        for (connexion in g.connexions) {
            pinceauConnexion.color = connexion.couleur
            pinceauConnexion.strokeWidth = connexion.epaisseur
            val a = connexion.extremite1.position
            val b = connexion.extremite2.position
            canvas.drawLine(a.x, a.y, b.x, b.y, pinceauConnexion)
        }

        // 2. Connexion en cours de tracé.
        connexionProvisoire?.let {
            val depart = it.source.position
            canvas.drawLine(depart.x, depart.y, it.xDoigt, it.yDoigt, pinceauProvisoire)
        }

        // 3. Objets, dans l'ordre de la liste (dernier = premier plan).
        // Sélectionné = déplacé (mode modification) ou source de la connexion en cours.
        val selectionne = objetDeplace ?: connexionProvisoire?.source
        for (objet in g.objets) dessinerObjet(canvas, objet, objet == selectionne)
    }

    private fun dessinerObjet(canvas: Canvas, objet: Objet, selectionne: Boolean) {
        val x = objet.position.x
        val y = objet.position.y

        if (selectionne) {
            // Anneau à l'extérieur du contour blanc.
            val rayonAnneau = rayonObjet + pinceauContour.strokeWidth / 2 + pinceauSelection.strokeWidth / 2
            canvas.drawCircle(x, y, rayonAnneau, pinceauSelection)
        }
        pinceauObjet.color = objet.couleur.argb
        canvas.drawCircle(x, y, rayonObjet, pinceauObjet)
        canvas.drawCircle(x, y, rayonObjet, pinceauContour)

        // Étiquette dans une bulle, sous le cercle.
        val metriques = pinceauTexte.fontMetrics
        val largeurTexte = pinceauTexte.measureText(objet.etiquette)
        val hauteurTexte = metriques.descent - metriques.ascent
        val haut = y + rayonObjet + margeEtiquette
        rectBulle.set(
            x - largeurTexte / 2 - margeEtiquette,
            haut,
            x + largeurTexte / 2 + margeEtiquette,
            haut + hauteurTexte + margeEtiquette
        )
        canvas.drawRoundRect(rectBulle, rayonBulle, rayonBulle, pinceauBulle)
        canvas.drawText(objet.etiquette, x, haut + margeEtiquette / 2 - metriques.ascent, pinceauTexte)
    }

    // ---------------- Gestes ----------------

    /** Objet sous le point (x, y), le plus au premier plan d'abord ; null si fond. */
    fun objetSous(x: Float, y: Float): Objet? =
        graphe?.objets?.lastOrNull { hypot(it.position.x - x, it.position.y - y) <= rayonTouche }

    private val detecteurAppuiLong = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onLongPress(e: MotionEvent) {
                ecouteur?.appuiLong(e.x, e.y, objetSous(e.x, e.y))
            }
        }
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detecteurAppuiLong.onTouchEvent(event)
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val objet = objetSous(x, y)
                // Doigt posé sur un objet : les ScrollView parentes ne doivent pas défiler.
                if (objet != null) parent?.requestDisallowInterceptTouchEvent(true)
                ecouteur?.debutGlissement(x, y, objet)
            }
            MotionEvent.ACTION_MOVE -> ecouteur?.glissement(x, y)
            MotionEvent.ACTION_UP -> {
                ecouteur?.finGlissement(objetSous(x, y))
                performClick()
            }
            // Par exemple : une ScrollView parente a repris le geste pour défiler.
            MotionEvent.ACTION_CANCEL -> ecouteur?.gesteAnnule()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
