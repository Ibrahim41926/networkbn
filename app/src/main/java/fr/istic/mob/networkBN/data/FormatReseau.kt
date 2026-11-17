package fr.istic.mob.networkBN.data

import fr.istic.mob.networkBN.model.CouleurObjet
import fr.istic.mob.networkBN.model.Graph
import fr.istic.mob.networkBN.model.Plan
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** V2 : le fichier n'est pas une sauvegarde valide (JSON illisible ou règles du graphe non respectées). */
class SauvegardeInvalideException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * V2 : format JSON d'une sauvegarde (bibliothèque org.json, intégrée à Android).
 *
 * On enregistre le MODÈLE ÉDITABLE, jamais le dessin (pas de Paint, Path, vue ni canevas) :
 * identifiant du plan, objets (id, étiquette, position, couleur) et connexions
 * (identifiants des extrémités dans l'ordre extremite1 / extremite2, étiquette, couleur,
 * épaisseur, flèche). Ni les gestes provisoires ni le défilement ne sont enregistrés.
 *
 * ```
 * { "format": "networkbn", "version": 1, "plan": "maison",
 *   "objets":     [ { "id": 1, "etiquette": "Box", "x": 120.0, "y": 80.0, "couleur": "green" } ],
 *   "connexions": [ { "objet1": 1, "objet2": 2, "etiquette": "Wi-Fi", "couleur": "blue",
 *                     "epaisseur": 8.0, "fleche": -40.0 } ] }
 * ```
 *
 * À la lecture, le graphe est reconstruit avec les RÈGLES de [Graph] (étiquettes non vides,
 * positions dans le plan, identifiants uniques, extrémités existantes, ni boucle ni doublon) :
 * au moindre refus, tout le fichier est rejeté et le réseau courant n'est pas touché.
 */
object FormatReseau {

    private const val FORMAT = "networkbn"
    private const val VERSION = 1

    fun versJson(plan: Plan, graphe: Graph): String {
        val objets = JSONArray()
        for (objet in graphe.objets) {
            objets.put(
                JSONObject()
                    .put("id", objet.id)
                    .put("etiquette", objet.etiquette)
                    .put("x", objet.position.x.toDouble())
                    .put("y", objet.position.y.toDouble())
                    .put("couleur", objet.couleur.id)
            )
        }
        val connexions = JSONArray()
        for (connexion in graphe.connexions) {
            connexions.put(
                JSONObject()
                    .put("objet1", connexion.extremite1.id)
                    .put("objet2", connexion.extremite2.id)
                    .put("etiquette", connexion.etiquette)
                    .put("couleur", connexion.couleur.id)
                    .put("epaisseur", connexion.epaisseur.toDouble())
                    .put("fleche", connexion.fleche.toDouble())
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("plan", plan.id)
            .put("objets", objets)
            .put("connexions", connexions)
            .toString(2)
    }

    /**
     * Reconstruit le plan et le graphe d'une sauvegarde.
     * [dimensions] = largeur et hauteur de l'image de chaque plan, pour borner les positions.
     * @throws SauvegardeInvalideException si le contenu est illisible ou incohérent.
     */
    fun depuisJson(texte: String, dimensions: Map<Plan, Pair<Int, Int>>): Pair<Plan, Graph> =
        try {
            lire(JSONObject(texte), dimensions)
        } catch (e: JSONException) {
            throw SauvegardeInvalideException("JSON illisible ou champ manquant", e)
        }

    private fun lire(racine: JSONObject, dimensions: Map<Plan, Pair<Int, Int>>): Pair<Plan, Graph> {
        if (racine.getString("format") != FORMAT) invalide("format inconnu")
        if (racine.getInt("version") != VERSION) invalide("version non gérée")
        val plan = Plan.depuisId(racine.getString("plan")) ?: invalide("plan inconnu")
        val (largeur, hauteur) = dimensions[plan] ?: invalide("image du plan introuvable")

        val graphe = Graph()
        graphe.definirDimensionsPlan(largeur, hauteur)

        val objets = racine.getJSONArray("objets")
        for (i in 0 until objets.length()) {
            val o = objets.getJSONObject(i)
            graphe.restaurerObjet(
                o.getLong("id"),
                o.getString("etiquette"),
                o.getDouble("x").toFloat(),
                o.getDouble("y").toFloat(),
                couleur(o)
            ) ?: invalide("objet n°$i refusé")
        }

        val connexions = racine.getJSONArray("connexions")
        for (i in 0 until connexions.length()) {
            val c = connexions.getJSONObject(i)
            val o1 = graphe.trouverObjet(c.getLong("objet1")) ?: invalide("connexion n°$i : extrémité inexistante")
            val o2 = graphe.trouverObjet(c.getLong("objet2")) ?: invalide("connexion n°$i : extrémité inexistante")
            // Ordre o1 / o2 conservé : le signe de la flèche reste du même côté.
            val connexion = graphe.addConnexion(o1, o2, c.getString("etiquette"), couleur(c))
                ?: invalide("connexion n°$i refusée (boucle, doublon ou étiquette vide)")
            if (!graphe.modifierEpaisseur(connexion, c.getDouble("epaisseur").toFloat())) {
                invalide("connexion n°$i : épaisseur invalide")
            }
            if (!graphe.modifierFleche(connexion, c.getDouble("fleche").toFloat())) {
                invalide("connexion n°$i : flèche invalide")
            }
        }
        return plan to graphe
    }

    private fun couleur(json: JSONObject): CouleurObjet =
        CouleurObjet.trouver(json.getString("couleur")) ?: invalide("couleur inconnue")

    private fun invalide(raison: String): Nothing = throw SauvegardeInvalideException(raison)
}
