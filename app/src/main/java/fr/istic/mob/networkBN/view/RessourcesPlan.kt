package fr.istic.mob.networkBN.view

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fr.istic.mob.networkBN.R
import fr.istic.mob.networkBN.model.Plan

/**
 * V2 : ressources Android associées à chaque [Plan] (le modèle ne connaît que son identifiant).
 * Images dans drawable-nodpi, décodées sans mise à l'échelle : 1 pixel image = 1 pixel canevas.
 */

/** Image du plan. */
val Plan.image: Int
    get() = when (this) {
        Plan.APPARTEMENT -> R.drawable.plan_appartement
        Plan.MAISON -> R.drawable.plan_maison
    }

/** Nom affiché (traduit) du plan. */
val Plan.nom: Int
    get() = when (this) {
        Plan.APPARTEMENT -> R.string.plan_appartement
        Plan.MAISON -> R.string.plan_maison
    }

private fun optionsSansMiseAEchelle() = BitmapFactory.Options().apply { inScaled = false }

/** Image du plan, en taille réelle. */
fun chargerImagePlan(resources: Resources, plan: Plan): Bitmap =
    BitmapFactory.decodeResource(resources, plan.image, optionsSansMiseAEchelle())

/** Largeur et hauteur du plan en pixels, sans décoder l'image. */
fun dimensionsPlan(resources: Resources, plan: Plan): Pair<Int, Int> {
    val options = optionsSansMiseAEchelle().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(resources, plan.image, options)
    return options.outWidth to options.outHeight
}
