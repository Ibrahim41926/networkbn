package fr.istic.mob.networkBN.model

/**
 * Le réseau : objets + connexions, avec ses RÈGLES D'INTÉGRITÉ.
 * Toute opération refusée renvoie null/false et NE MODIFIE PAS le graphe.
 *
 *  1. Étiquette d'objet non vide (après suppression des espaces).
 *     V2 : idem pour l'étiquette d'une connexion.
 *  2. Identifiants stables et uniques (jamais réutilisés).
 *  3. Centre des objets toujours dans le cadre de l'image.
 *  4. Connexions non orientées : ni boucle (A–A) ni doublon (A–B ou B–A).
 *
 * Classe Kotlin pure, sans Android : testable avec JUnit.
 */
class Graph {

    // ---------------- Dimensions du plan ----------------

    var largeurPlan: Int = 0
        private set

    var hauteurPlan: Int = 0
        private set

    /** Appelé une fois l'image du plan chargée. */
    fun definirDimensionsPlan(largeur: Int, hauteur: Int) {
        require(largeur > 0 && hauteur > 0) { "Dimensions de plan invalides : $largeur x $hauteur" }
        largeurPlan = largeur
        hauteurPlan = hauteur
    }

    /** Vrai si (x, y) est sur l'image (les zones libres à droite / en bas n'en font pas partie). */
    fun estDansLePlan(x: Float, y: Float): Boolean =
        x >= 0f && y >= 0f && x < largeurPlan && y < hauteurPlan

    /** Ramène un point quelconque dans le cadre du plan. */
    fun bornerAuPlan(x: Float, y: Float): Coordonnee = Coordonnee(
        x.coerceIn(0f, (largeurPlan - 1).coerceAtLeast(0).toFloat()),
        y.coerceIn(0f, (hauteurPlan - 1).coerceAtLeast(0).toFloat())
    )

    // ---------------- Données ----------------

    private val _objets = mutableListOf<Objet>()
    private val _connexions = mutableListOf<Connexion>()

    /** Lecture seule. Ordre de la liste = ordre de dessin (dernier = premier plan). */
    val objets: List<Objet> get() = _objets
    val connexions: List<Connexion> get() = _connexions

    /** Jamais remis à zéro, même après resetAll(). */
    private var prochainId = 1L

    fun trouverObjet(id: Long): Objet? = _objets.firstOrNull { it.id == id }

    /** « Objet possède * Connexion », calculé à la demande. */
    fun connexionsDe(objet: Objet): List<Connexion> = _connexions.filter { it.concerne(objet) }

    // ---------------- Opérations ----------------

    /** @return l'objet créé, ou null si étiquette vide ou position hors du plan. */
    fun addObjet(
        etiquette: String,
        x: Float,
        y: Float,
        couleur: CouleurObjet = CouleurObjet.PAR_DEFAUT // V2
    ): Objet? {
        val propre = etiquette.trim()
        if (propre.isEmpty() || !estDansLePlan(x, y)) return null
        val objet = Objet(prochainId++, propre, Coordonnee(x, y), couleur)
        _objets.add(objet)
        return objet
    }

    /** Vérifie les règles d'une connexion sans rien modifier. */
    fun peutConnecter(o1: Objet, o2: Objet): Boolean =
        o1 != o2 &&                                // pas de boucle
                o1 in _objets && o2 in _objets &&      // objets du graphe
                _connexions.none { it.relie(o1, o2) }  // pas de doublon (A–B ou B–A)

    /**
     * V2 : [etiquette] obligatoire (trimée ; vide ou espaces = refus).
     * @return la connexion créée, ou null si refusée.
     */
    fun addConnexion(
        o1: Objet,
        o2: Objet,
        etiquette: String,
        couleur: CouleurObjet = Connexion.COULEUR_PAR_DEFAUT // V2
    ): Connexion? {
        val propre = etiquette.trim() // V2
        if (propre.isEmpty() || !peutConnecter(o1, o2)) return null
        val connexion = Connexion(o1, o2, propre, couleur = couleur)
        _connexions.add(connexion)
        return connexion
    }

    /**
     * Déplace le centre d'un objet, borné au cadre de l'image.
     * @return false si l'objet n'appartient pas au graphe.
     */
    fun modifiePosition(objet: Objet, x: Float, y: Float): Boolean {
        if (objet !in _objets) return false
        objet.position = bornerAuPlan(x, y)
        return true
    }

    /** Supprime objets et connexions. Le plan est conservé. */
    fun resetAll() {
        _connexions.clear()
        _objets.clear()
    }

    /**
     * Change la courbure d'une connexion (0 = droite).
     * @return false si la connexion n'appartient pas au graphe.
     */
    fun modifierFleche(connexion: Connexion, fleche: Float): Boolean {
        if (connexion !in _connexions) return false
        connexion.fleche = fleche
        return true
    }

    // ---------------- V2 : menus (suppression et modification) ----------------

    /** Connexion entre les objets d'identifiants [id1] et [id2], dans un sens ou dans l'autre. */
    fun trouverConnexion(id1: Long, id2: Long): Connexion? {
        val o1 = trouverObjet(id1) ?: return null
        val o2 = trouverObjet(id2) ?: return null
        return _connexions.firstOrNull { it.relie(o1, o2) }
    }

    /** Supprime l'objet ET toutes ses connexions : aucune connexion orpheline ne subsiste. */
    fun supprimerObjet(objet: Objet): Boolean {
        if (objet !in _objets) return false
        _connexions.removeAll { it.concerne(objet) }
        _objets.remove(objet)
        return true
    }

    fun supprimerConnexion(connexion: Connexion): Boolean = _connexions.remove(connexion)

    /** @return false (rien n'est modifié) si l'étiquette est vide ou l'objet hors du graphe. */
    fun modifierEtiquette(objet: Objet, etiquette: String): Boolean {
        val propre = etiquette.trim()
        if (propre.isEmpty() || objet !in _objets) return false
        objet.etiquette = propre
        return true
    }

    fun modifierCouleur(objet: Objet, couleur: CouleurObjet): Boolean {
        if (objet !in _objets) return false
        objet.couleur = couleur
        return true
    }

    /** @return false (rien n'est modifié) si l'étiquette est vide ou la connexion hors du graphe. */
    fun modifierEtiquette(connexion: Connexion, etiquette: String): Boolean {
        val propre = etiquette.trim()
        if (propre.isEmpty() || connexion !in _connexions) return false
        connexion.etiquette = propre
        return true
    }

    fun modifierCouleur(connexion: Connexion, couleur: CouleurObjet): Boolean {
        if (connexion !in _connexions) return false
        connexion.couleur = couleur
        return true
    }

    /** @return false si l'épaisseur n'est pas strictement positive ou la connexion hors du graphe. */
    fun modifierEpaisseur(connexion: Connexion, epaisseur: Float): Boolean {
        if (epaisseur <= 0f || connexion !in _connexions) return false
        connexion.epaisseur = epaisseur
        return true
    }

}