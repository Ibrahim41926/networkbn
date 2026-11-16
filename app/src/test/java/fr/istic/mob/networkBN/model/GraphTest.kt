package fr.istic.mob.networkBN.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * V2 : tests des RÈGLES D'INTÉGRITÉ du modèle (classe Kotlin pure, exécutés sans téléphone).
 * Lancer : ./gradlew testDebugUnitTest
 *
 * Toute opération refusée doit renvoyer null/false ET laisser le graphe inchangé.
 */
class GraphTest {

    private lateinit var graphe: Graph
    private lateinit var a: Objet
    private lateinit var b: Objet

    @Before
    fun preparer() {
        graphe = Graph()
        graphe.definirDimensionsPlan(1000, 800)
        a = graphe.addObjet("Box", 100f, 100f)!!
        b = graphe.addObjet("TV", 500f, 400f)!!
    }

    // ---------------- Objets ----------------

    @Test
    fun objet_etiquetteTrimeeEtCouleurParDefautVerte() {
        val objet = graphe.addObjet("  Prise  ", 10f, 10f)!!
        assertEquals("Prise", objet.etiquette)
        assertEquals(CouleurObjet.VERT, objet.couleur)
    }

    @Test
    fun objet_couleurChoisieConservee() {
        assertEquals(CouleurObjet.MAGENTA, graphe.addObjet("Lampe", 10f, 10f, CouleurObjet.MAGENTA)!!.couleur)
    }

    @Test
    fun objet_etiquetteVideOuEspacesRefusee() {
        assertNull(graphe.addObjet("", 10f, 10f))
        assertNull(graphe.addObjet("   ", 10f, 10f))
        assertEquals(2, graphe.objets.size)
    }

    @Test
    fun objet_horsDuPlanRefuse() {
        assertNull(graphe.addObjet("Dehors", -1f, 10f))
        assertNull(graphe.addObjet("Dehors", 1000f, 10f)) // x = largeur : déjà hors de l'image
        assertNull(graphe.addObjet("Dehors", 10f, 800f))
        assertEquals(2, graphe.objets.size)
    }

    @Test
    fun objet_deplacementBorneAuPlan() {
        assertTrue(graphe.modifiePosition(a, 5000f, -50f))
        assertEquals(999f, a.position.x)
        assertEquals(0f, a.position.y)
    }

    @Test
    fun identifiants_jamaisReutilises_memeApresReinitialisation() {
        val idMax = b.id
        graphe.resetAll()
        assertTrue(graphe.objets.isEmpty() && graphe.connexions.isEmpty())
        assertTrue(graphe.addObjet("Nouveau", 10f, 10f)!!.id > idMax)
    }

    // ---------------- Connexions ----------------

    @Test
    fun connexion_creeeAvecEtiquetteTrimeeEtBleuParDefaut() {
        val c = graphe.addConnexion(a, b, "  Wi-Fi ")!!
        assertEquals("Wi-Fi", c.etiquette)
        assertEquals(CouleurObjet.BLEU, c.couleur)
        assertEquals(0f, c.fleche)
    }

    @Test
    fun connexion_etiquetteObligatoire() {
        assertNull(graphe.addConnexion(a, b, ""))
        assertNull(graphe.addConnexion(a, b, "   "))
        assertTrue(graphe.connexions.isEmpty())
    }

    @Test
    fun connexion_boucleRefusee() {
        assertFalse(graphe.peutConnecter(a, a))
        assertNull(graphe.addConnexion(a, a, "Boucle"))
        assertTrue(graphe.connexions.isEmpty())
    }

    @Test
    fun connexion_doublonRefuse_dansLesDeuxSens() {
        assertNotNull(graphe.addConnexion(a, b, "Câble"))
        assertNull(graphe.addConnexion(a, b, "Doublon A-B"))
        assertNull(graphe.addConnexion(b, a, "Doublon B-A"))
        assertEquals(1, graphe.connexions.size)
    }

    @Test
    fun connexion_nonOrientee_trouveeDansLesDeuxSens() {
        val c = graphe.addConnexion(a, b, "Câble")!!
        assertSame(c, graphe.trouverConnexion(a.id, b.id))
        assertSame(c, graphe.trouverConnexion(b.id, a.id))
    }

    @Test
    fun connexion_versObjetHorsDuGrapheRefusee() {
        val autre = Graph().apply { definirDimensionsPlan(100, 100) }.addObjet("Ailleurs", 1f, 1f)!!
        assertNull(graphe.addConnexion(a, autre, "Impossible"))
    }

    // ---------------- Suppression et modification (menus) ----------------

    @Test
    fun suppressionObjet_supprimeAussiSesConnexions() {
        val c = graphe.addObjet("Prise", 800f, 100f)!!
        graphe.addConnexion(a, b, "1")
        graphe.addConnexion(b, c, "2")
        graphe.addConnexion(a, c, "3")

        assertTrue(graphe.supprimerObjet(b))

        assertEquals(listOf(a, c), graphe.objets)
        assertEquals(1, graphe.connexions.size)
        assertTrue(graphe.connexions.none { it.concerne(b) }) // aucune connexion orpheline
    }

    @Test
    fun modificationEtiquetteVide_refuseeEtRienNeChange() {
        val c = graphe.addConnexion(a, b, "Wi-Fi")!!
        assertFalse(graphe.modifierEtiquette(a, "  "))
        assertFalse(graphe.modifierEtiquette(c, ""))
        assertEquals("Box", a.etiquette)
        assertEquals("Wi-Fi", c.etiquette)
    }

    @Test
    fun modificationConnexion_couleurEtEpaisseur() {
        val c = graphe.addConnexion(a, b, "Wi-Fi")!!
        assertTrue(graphe.modifierCouleur(c, CouleurObjet.ROUGE))
        assertTrue(graphe.modifierEpaisseur(c, 14f))
        assertEquals(CouleurObjet.ROUGE, c.couleur)
        assertEquals(14f, c.epaisseur)
    }

    @Test
    fun epaisseurInvalide_refusee() {
        val c = graphe.addConnexion(a, b, "Wi-Fi")!!
        assertFalse(graphe.modifierEpaisseur(c, 0f))
        assertFalse(graphe.modifierEpaisseur(c, -3f))
        assertFalse(graphe.modifierEpaisseur(c, Float.NaN))
        assertEquals(Connexion.EPAISSEUR_PAR_DEFAUT, c.epaisseur)
    }

    // ---------------- Courbure ----------------

    @Test
    fun fleche_modifiableEtConserveeQuandUnObjetBouge() {
        val c = graphe.addConnexion(a, b, "Wi-Fi")!!
        assertTrue(graphe.modifierFleche(c, 60f))
        graphe.modifiePosition(b, 700f, 100f)
        assertEquals(60f, c.fleche)
    }

    @Test
    fun flecheNonFinie_refusee() {
        val c = graphe.addConnexion(a, b, "Wi-Fi")!!
        assertFalse(graphe.modifierFleche(c, Float.POSITIVE_INFINITY))
        assertFalse(graphe.modifierFleche(c, Float.NaN))
        assertEquals(0f, c.fleche)
    }

    @Test
    fun courbe_passeParP_aLaDistanceFlecheDuMilieu() {
        // A(100,100) -> B(500,100) : AB horizontal, normale verticale.
        graphe.modifiePosition(b, 500f, 100f)
        val c = graphe.addConnexion(a, b, "Wi-Fi")!!
        graphe.modifierFleche(c, 50f)

        val (mx, my) = c.milieu()
        val (px, py) = c.pointMilieuCourbe()
        val (cx, cy) = c.pointControle()
        assertEquals(300f, mx); assertEquals(100f, my)
        assertEquals(300f, px); assertEquals(150f, py) // P = M + fleche * n
        assertEquals(300f, cx); assertEquals(200f, cy) // C = M + 2 * fleche * n
    }

    // ---------------- Sauvegardes (restauration) ----------------

    @Test
    fun restaurerObjet_gardeSonIdEtLesSuivantsSontPlusGrands() {
        val vide = Graph().apply { definirDimensionsPlan(1000, 800) }
        assertEquals(42L, vide.restaurerObjet(42, "Box", 10f, 10f, CouleurObjet.NOIR)!!.id)
        assertNull(vide.restaurerObjet(42, "Doublon", 20f, 20f, CouleurObjet.NOIR)) // id déjà utilisé
        assertNull(vide.restaurerObjet(0, "Id invalide", 20f, 20f, CouleurObjet.NOIR))
        assertTrue(vide.addObjet("Nouveau", 30f, 30f)!!.id > 42)
    }
}
