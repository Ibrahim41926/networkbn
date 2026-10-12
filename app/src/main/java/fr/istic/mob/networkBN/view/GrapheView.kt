package fr.istic.mob.networkBN.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import fr.istic.mob.networkBN.model.Connexion
import fr.istic.mob.networkBN.model.Graph
import fr.istic.mob.networkBN.model.Objet
import fr.istic.mob.networkBN.model.Plan
import fr.istic.mob.networkBN.viewModel.ConnexionProvisoire
import fr.istic.mob.networkBN.viewModel.Defilement
import kotlin.math.hypot

/**
 * Dessine le plan, les connexions et les objets, et capte les gestes.
 *
 *  - Le plan est dessiné SANS mise à l'échelle : 1 pixel image = 1 pixel canevas.
 *  - V2 : la vue gère elle-même le défilement ([defilement], fourni par le ViewModel) :
 *    dessin xVue = xPlan − défilement, toucher xPlan = xVue + défilement.
 *    L'écouteur ne reçoit QUE des coordonnées du plan.
 *  - La vue ne décide RIEN : elle transmet des gestes neutres à [ecouteur]
 *    (« glissement commencé ici, sur tel objet »). Le ViewModel les interprète selon le mode.
 */
class GrapheView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Gestes transmis à l'Activity, qui les relaie au ViewModel. */
    interface EcouteurGestes {
        /** @return true si l'appui long a ouvert une boîte : le geste en cours s'arrête alors. */
        fun appuiLong(x: Float, y: Float, objetTouche: Objet?, connexionTouchee: Connexion?): Boolean
        fun debutGlissement(x: Float, y: Float, objetTouche: Objet?, connexionTouchee: Connexion?)
        fun glissement(x: Float, y: Float)
        fun finGlissement(objetSousLeDoigt: Objet?)
        fun gesteAnnule()
    }

    var ecouteur: EcouteurGestes? = null

    /** V2 : appelé quand la taille de la vue change (premier affichage, rotation), pour borner le défilement. */
    var ecouteurTaille: ((largeur: Int, hauteur: Int) -> Unit)? = null

    // ---------------- Données à dessiner ----------------

    /** V2 : plan affiché, choisi par le ViewModel. */
    var plan: Plan = Plan.PAR_DEFAUT
        set(value) {
            if (field == value) return
            field = value
            image = chargerImagePlan(resources, value)
            invalidate()
        }

    /** V2 : point du plan affiché dans le coin haut-gauche de la vue, borné par le ViewModel. */
    var defilement: Defilement = Defilement.ORIGINE
        set(value) {
            field = value
            invalidate()
        }

    /** Image du plan courant, en taille réelle (1 pixel image = 1 pixel canevas). */
    private var image: Bitmap = chargerImagePlan(resources, plan)

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

    /** V2 : objet en cours de déplacement, ou null (pour dessiner l'anneau de sélection). */
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

    /** Décalage de l'étiquette d'une connexion par rapport au point P, pour ne pas cacher le trait. */
    private val decalageEtiquetteConnexion = dp(14f)

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

    /**
     * V2 : anneau ambre semi-transparent autour de l'objet sélectionné (il garde sa couleur).
     * L'orange étant devenu une couleur d'objet, il ne sert plus à marquer la sélection.
     */
    private val pinceauSelection = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(6f)
        color = Color.argb(170, 255, 193, 7)
    }

    private val pinceauBulle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(230, 255, 255, 255)
    }

    /** V2 : contour de la bulle d'étiquette d'une connexion (couleur de la connexion). */
    private val pinceauBordBulle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    private val pinceauTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = sp(13f)
        textAlign = Paint.Align.CENTER
    }

    /** Réutilisé à chaque dessin (pas d'allocation dans onDraw). */
    private val rectBulle = RectF()

    /** Chemin réutilisé pour dessiner chaque connexion (pas d'allocation dans onDraw). */
    private val chemin = Path()

    // ---------------- Taille ----------------

    /** V2 : la vue occupe la zone sous la barre d'outils (match_parent) ; le ViewModel re-borne le défilement. */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ecouteurTaille?.invoke(w, h)
    }

    // ---------------- Dessin ----------------

    /** V2 : toute la scène est décalée ensemble du défilement : xVue = xPlan − défilement. */
    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(-defilement.x, -defilement.y)
        dessinerScene(canvas)
        canvas.restore()
    }

    /** Plan, connexions, étiquettes, connexion provisoire et objets, en coordonnées du plan. */
    private fun dessinerScene(canvas: Canvas) {
        canvas.drawBitmap(image, 0f, 0f, null)
        val g = graphe ?: return

        // 1. Connexions (sous les objets) : courbe de Bézier quadratique A -> C -> B.
        //    Flèche nulle : C = M, la courbe est une ligne droite.
        for (connexion in g.connexions) {
            pinceauConnexion.color = connexion.couleur.argb
            pinceauConnexion.strokeWidth = connexion.epaisseur
            val a = connexion.extremite1.position
            val b = connexion.extremite2.position
            val (cx, cy) = connexion.pointControle()
            chemin.reset()
            chemin.moveTo(a.x, a.y)
            chemin.quadTo(cx, cy, b.x, b.y)
            canvas.drawPath(chemin, pinceauConnexion)
        }

        // 2. Étiquettes des connexions, près du point P (bord = couleur de la connexion).
        //    Après tous les traits, avant les objets.
        for (connexion in g.connexions) {
            val texte = connexion.etiquette
            val (ex, ey) = positionEtiquette(connexion)
            pinceauBordBulle.color = connexion.couleur.argb
            dessinerBulleCentree(canvas, texte, ex, ey, pinceauBordBulle)
        }

        // 3. Connexion en cours de tracé.
        connexionProvisoire?.let {
            val depart = it.source.position
            canvas.drawLine(depart.x, depart.y, it.xDoigt, it.yDoigt, pinceauProvisoire)
        }

        // 4. Objets, dans l'ordre de la liste (dernier = premier plan).
        //    Sélectionné = déplacé (mode modification) ou source de la connexion en cours.
        val selectionne = objetDeplace ?: connexionProvisoire?.source
        for (objet in g.objets) dessinerObjet(canvas, objet, objet == selectionne)
    }

    private fun dessinerObjet(canvas: Canvas, objet: Objet, selectionne: Boolean) {
        val x = objet.position.x
        val y = objet.position.y

        if (selectionne) {
            // V2 : anneau à l'extérieur du contour blanc (un trait est centré sur son cercle).
            val rayonAnneau = rayonObjet + pinceauContour.strokeWidth / 2 + pinceauSelection.strokeWidth / 2
            canvas.drawCircle(x, y, rayonAnneau, pinceauSelection)
        }
        pinceauObjet.color = objet.couleur.argb
        canvas.drawCircle(x, y, rayonObjet, pinceauObjet)
        canvas.drawCircle(x, y, rayonObjet, pinceauContour)

        // Étiquette dans une bulle, sous le cercle.
        dessinerBulle(canvas, objet.etiquette, x, y + rayonObjet + margeEtiquette)
    }

    /** Hauteur d'une bulle d'étiquette (texte + marge). */
    private fun hauteurBulle(): Float {
        val metriques = pinceauTexte.fontMetrics
        return metriques.descent - metriques.ascent + margeEtiquette
    }

    /**
     * Étiquette d'objet dans une bulle blanche centrée horizontalement sur [x], de bord supérieur [haut].
     */
    private fun dessinerBulle(canvas: Canvas, texte: String, x: Float, haut: Float) {
        val metriques = pinceauTexte.fontMetrics
        val largeurTexte = pinceauTexte.measureText(texte)
        rectBulle.set(
            x - largeurTexte / 2 - margeEtiquette,
            haut,
            x + largeurTexte / 2 + margeEtiquette,
            haut + hauteurBulle()
        )
        canvas.drawRoundRect(rectBulle, rayonBulle, rayonBulle, pinceauBulle)
        canvas.drawText(texte, x, haut + margeEtiquette / 2 - metriques.ascent, pinceauTexte)
    }

    /**
     * Centre de l'étiquette d'une connexion : P décalé du côté de la courbure.
     * Utilisé pour le dessin ET pour le toucher, donc les deux restent cohérents.
     */
    private fun positionEtiquette(connexion: Connexion): Pair<Float, Float> {
        val (px, py) = connexion.pointMilieuCourbe()
        val (nx, ny) = connexion.normale()
        val cote = if (connexion.fleche >= 0f) 1f else -1f
        return px + nx * cote * decalageEtiquetteConnexion to py + ny * cote * decalageEtiquetteConnexion
    }

    /** Texte dans une bulle blanche centrée sur (cx, cy), avec un contour optionnel. */
    private fun dessinerBulleCentree(canvas: Canvas, texte: String, cx: Float, cy: Float, bord: Paint? = null) {
        val metriques = pinceauTexte.fontMetrics
        val demiLargeur = pinceauTexte.measureText(texte) / 2 + margeEtiquette
        val demiHauteur = (metriques.descent - metriques.ascent) / 2 + margeEtiquette / 2
        rectBulle.set(cx - demiLargeur, cy - demiHauteur, cx + demiLargeur, cy + demiHauteur)
        canvas.drawRoundRect(rectBulle, rayonBulle, rayonBulle, pinceauBulle)
        bord?.let { canvas.drawRoundRect(rectBulle, rayonBulle, rayonBulle, it) }
        // Ligne de base choisie pour que le texte soit centré verticalement.
        canvas.drawText(texte, cx, cy - (metriques.ascent + metriques.descent) / 2, pinceauTexte)
    }

    // ---------------- Gestes ----------------

    /** V2 : point touché (coordonnées de la vue) converti en coordonnées du plan : xPlan = xVue + défilement. */
    private fun versPlanX(xVue: Float) = xVue + defilement.x
    private fun versPlanY(yVue: Float) = yVue + defilement.y

    /** Objet sous le point (x, y) du PLAN, le plus au premier plan d'abord ; null si fond. */
    fun objetSous(x: Float, y: Float): Objet? =
        graphe?.objets?.lastOrNull { hypot(it.position.x - x, it.position.y - y) <= rayonTouche }

    /** Connexion dont l'étiquette est sous le point (x, y) du PLAN, la plus au premier plan d'abord. */
    fun connexionSous(x: Float, y: Float): Connexion? =
        graphe?.connexions?.lastOrNull {
            val (ex, ey) = positionEtiquette(it)
            hypot(ex - x, ey - y) <= rayonTouche
        }

    /**
     * V2 : distance en dessous de laquelle le doigt est considéré immobile.
     * C'est aussi le seuil du GestureDetector : au-delà, l'appui long est annulé.
     */
    private val seuilGlissement = ViewConfiguration.get(context).scaledTouchSlop

    /** V2 : point de départ du geste (coordonnées de la vue), pour savoir si le doigt a dépassé le seuil. */
    private var xDepart = 0f
    private var yDepart = 0f
    private var glissementCommence = false

    /** V2 : vrai si l'appui long a ouvert une boîte : la suite du geste est ignorée. */
    private var appuiLongTraite = false

    private val detecteurAppuiLong = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onLongPress(e: MotionEvent) {
                val x = versPlanX(e.x) // V2 : coordonnées du plan
                val y = versPlanY(e.y)
                // Même priorité qu'au toucher : les objets, puis les étiquettes de connexion.
                val objet = objetSous(x, y)
                val connexion = if (objet == null) connexionSous(x, y) else null
                if (ecouteur?.appuiLong(x, y, objet, connexion) == true) {
                    appuiLongTraite = true
                    ecouteur?.gesteAnnule()
                }
            }
        }
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detecteurAppuiLong.onTouchEvent(event)
        // V2 : TOUJOURS convertir avant usage : l'écouteur ne reçoit que des coordonnées du plan.
        val x = versPlanX(event.x)
        val y = versPlanY(event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                xDepart = event.x
                yDepart = event.y
                glissementCommence = false
                appuiLongTraite = false
                // Priorité : les objets, puis les étiquettes de connexion.
                val objet = objetSous(x, y)
                val connexion = if (objet == null) connexionSous(x, y) else null
                ecouteur?.debutGlissement(x, y, objet, connexion)
            }
            MotionEvent.ACTION_MOVE -> {
                if (appuiLongTraite) return true
                // V2 : tant que le doigt reste sous le seuil (mesuré à l'écran), ce n'est pas
                // un glissement (l'appui long reste possible).
                if (!glissementCommence && hypot(event.x - xDepart, event.y - yDepart) < seuilGlissement) return true
                glissementCommence = true
                ecouteur?.glissement(x, y)
            }
            MotionEvent.ACTION_UP -> {
                if (!appuiLongTraite) ecouteur?.finGlissement(objetSous(x, y))
                performClick()
            }
            // Geste interrompu par le système.
            MotionEvent.ACTION_CANCEL -> ecouteur?.gesteAnnule()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}