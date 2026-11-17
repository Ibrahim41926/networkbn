# NetworkBN

TP2 MOB - Réseau domestique, parties 1 et 2.

Binôme : Ibrahim BALDE et Rodrigue NGUETSA

Dépôt : https://gitlab2.istic.univ-rennes1.fr/ibalde/networkbn/-/tree/main?ref_type=heads (branche de la partie 2 : `version2_networkBN`)

## Lancer l'application

1. Cloner le dépôt : `git clone https://gitlab2.istic.univ-rennes1.fr/ibalde/networkbn.git`
2. Ouvrir le dossier dans Android Studio et attendre la synchronisation Gradle.
3. Lancer la configuration `app` sur un émulateur ou un téléphone (Android 7.0 minimum).

Le projet utilise Gradle 8.13, qui ne démarre pas avec un JDK 25 ou plus récent. Si la synchronisation échoue (message réduit à un numéro de version, ou `Unresolved reference 'R'`), choisir un JDK 17 à 21 dans *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK*, puis resynchroniser.

## Utilisation

Le mode actif est choisi dans le menu et affiché sous le titre (« Mode : … »).

| Mode | Geste | Effet |
|---|---|---|
| Ajouter des objets | Appui long sur le plan | Boîte « Nouvel objet » : étiquette et couleur |
| Ajouter des connexions | Glisser d'un objet vers un autre | Aperçu pendant le geste, puis boîte « Nouvelle connexion » : étiquette et couleur |
| Déplacer les objets | Glisser un objet | L'objet suit le doigt, ses connexions suivent |
| Déplacer les objets | Glisser l'étiquette d'une connexion | Courbe la connexion (presque droite au relâcher : elle redevient droite) |
| Déplacer les objets | Appui long sur un objet | Menu « Modifier l'objet » : étiquette, couleur, Supprimer |
| Déplacer les objets | Appui long sur l'étiquette d'une connexion | Menu « Modifier la connexion » : étiquette, couleur, épaisseur, Supprimer |
| Navigation | Glisser n'importe où | Fait défiler le plan |

Actions du menu : Nouveau réseau, Ouvrir, Enregistrer, Partager en image, Réinitialiser le réseau.

## Solution de dessin

On a choisi le dessin direct dans une vue personnalisée (3.1) : la classe `GrapheView` hérite de `View` et dessine le plan, les connexions, leurs étiquettes et les objets dans `onDraw`. Les plans sont dans `drawable-nodpi` et sont dessinés sans redimensionnement (1 pixel de l'image = 1 pixel du canevas). Le défilement est un simple décalage du canevas : dessin `xVue = xPlan − défilement`, toucher `xPlan = xVue + défilement`.

Une connexion est une courbe de Bézier quadratique (`Path.quadTo`). Sa courbure est mémorisée par une seule valeur, la flèche : distance signée entre le milieu M de AB et le point P de la courbe. Le point de contrôle est C = M + 2 × flèche × n (n : normale unitaire à AB), ce qui fait passer la courbe exactement par P. L'étiquette est dessinée près de P. Quand un objet bouge, M, n, P et C sont recalculés et la flèche est conservée.

## Architecture (MVVM)

- `model` : `Graph`, `Objet`, `Connexion`, `CouleurObjet`, `Plan`. Kotlin pur, sans Android. `Graph` porte toutes les règles du réseau ; une opération refusée renvoie `null`/`false` et ne modifie rien.
- `viewModel` : `GraphViewModel` interprète les gestes selon le mode et expose l'état en `LiveData` (graphe, mode, plan, défilement, connexion en attente d'étiquette, messages…). Il survit à la rotation ; les fichiers sont lus et écrits hors du thread principal (coroutines).
- `view` : `MainActivity` (menu et boîtes de dialogue), `GrapheView` (dessin et gestes neutres), `SelecteurCouleur` (pastilles de couleur).
- `data` : `FormatReseau` (JSON), `StockageReseaux` (fichiers des sauvegardes), `ExportImage` (PNG).

## Fonctionnalités réalisées

Partie 1 :
- Affichage du plan au lancement, sans objet ni connexion
- Ajout d'objets par appui long, avec une étiquette obligatoire
- Déplacement des objets, les connexions suivent
- Ajout de connexions par glissement, avec un aperçu pendant le geste
- Refus des boucles et des doublons (A–B et B–A), vérifié dans le modèle
- Réinitialisation avec confirmation, le plan reste affiché

Partie 2 :
- **Couleurs** : 7 couleurs (rouge, vert, bleu, orange, cyan, magenta, noir) au choix pour les objets (vert par défaut) et les connexions (bleu par défaut). Un objet sélectionné garde sa couleur et reçoit un anneau ambre.
- **Étiquette obligatoire pour une connexion** : la connexion n'est créée qu'après la saisie de son étiquette. Annuler, le bouton retour ou un changement de mode ne crée rien.
- **Connexions courbes** : on courbe une connexion en glissant son étiquette ; l'étiquette est affichée au milieu de la courbe, dans une bulle du contour de la couleur de la connexion.
- **Menus par appui long** : modifier l'étiquette et la couleur d'un objet ; l'étiquette, la couleur et l'épaisseur (fine, moyenne, épaisse) d'une connexion ; supprimer un objet (avec toutes ses connexions) ou une connexion.
- **Deux plans** (Appartement, Maison) au choix avec « Nouveau réseau », et un **mode Navigation** pour faire défiler les plans plus grands que l'écran.
- **Enregistrer / Ouvrir** au format JSON dans le stockage interne de l'application. Une confirmation est demandée avant de remplacer une sauvegarde existante, ou un réseau non enregistré. Un fichier invalide est refusé en entier et le réseau courant ne change pas.
- **Partager en image** : image PNG de tout le plan en taille réelle, envoyée par le partage Android (mail, messagerie, Drive…) via un `FileProvider`, sans permission de stockage.
- **Rotation** : le réseau, le mode, le plan, le défilement et les boîtes ouvertes (avec le texte et la couleur saisis) sont conservés.
- **Interface en français et en anglais**. Les étiquettes saisies et les noms de sauvegarde ne sont jamais traduits.

## Format des sauvegardes

On enregistre le modèle, jamais le dessin. Les couleurs et le plan sont enregistrés par un identifiant stable, indépendant de la langue et des ressources Android :

```json
{ "format": "networkbn", "version": 1, "plan": "maison",
  "objets":     [ { "id": 1, "etiquette": "Box", "x": 120.0, "y": 80.0, "couleur": "green" } ],
  "connexions": [ { "objet1": 1, "objet2": 2, "etiquette": "Wi-Fi", "couleur": "blue",
                    "epaisseur": 8.0, "fleche": -40.0 } ] }
```

À l'ouverture, le graphe est reconstruit avec les règles de `Graph` (étiquettes non vides, positions dans le plan, identifiants uniques, extrémités existantes, ni boucle ni doublon).

## Tests

### Tests automatiques

`app/src/test/.../model/GraphTest.kt` : 20 tests JUnit des règles du modèle (étiquettes obligatoires, positions bornées au plan, identifiants jamais réutilisés, boucles et doublons refusés dans les deux sens, suppression en cascade, épaisseur et flèche invalides refusées, calcul de M, P et C, restauration d'une sauvegarde).

```
./gradlew testDebugUnitTest
```

### Scénarios de test manuels

Avant de commencer : Nouveau réseau → Appartement.

| # | Scénario | Résultat attendu |
|---|---|---|
| 1 | Mode Ajouter des objets, appui long sur le plan, valider sans étiquette | Erreur sous le champ, la boîte reste ouverte |
| 2 | Saisir « Box », choisir le rouge, OK ; créer de même « TV » (couleur par défaut) | Box rouge, TV verte |
| 3 | Mode Ajouter des connexions, glisser de Box vers TV, Annuler | L'aperçu disparaît, aucune connexion |
| 4 | Recommencer, saisir « Wi-Fi », choisir le magenta, OK | Connexion magenta, « Wi-Fi » au milieu |
| 5 | Glisser de TV vers Box, puis de Box vers Box, puis de Box vers le fond | Refusés, aucune boîte ne s'ouvre |
| 6 | Mode Déplacer, glisser TV | TV suit le doigt avec un anneau ambre, la connexion suit |
| 7 | Glisser l'étiquette « Wi-Fi » sur le côté, puis la ramener presque au milieu | La connexion se courbe, puis redevient droite au relâcher |
| 8 | Appui long sur « Wi-Fi », mettre l'épaisseur Épaisse et l'étiquette « Câble », OK | Trait plus épais, nouvelle étiquette |
| 9 | Appui long sur TV, Supprimer | TV et sa connexion disparaissent |
| 10 | Ouvrir une boîte, taper du texte et choisir une couleur, tourner l'écran | La boîte revient avec le texte et la couleur |
| 11 | Nouveau réseau → Maison, mode Navigation, glisser | Le plan défile, sans dépasser ses bords |
| 12 | Enregistrer sous « Test », modifier le réseau, Ouvrir → confirmer → « Test » | Le réseau enregistré revient à l'identique (positions, couleurs, courbes) |
| 13 | Enregistrer de nouveau sous « Test » ; essayer aussi le nom « a/b » | Demande de remplacement ; « a/b » refusé (caractère invalide) |
| 14 | Partager en image, choisir une application (mail, Drive…) | L'image contient tout le plan avec le réseau, sans anneau de sélection |
| 15 | Passer le téléphone en anglais et relancer | Tous les textes de l'interface sont en anglais, les étiquettes saisies ne changent pas |
| 16 | Réinitialiser le réseau → OK | Objets et connexions supprimés, le plan reste |

## Limites connues

- Les sauvegardes sont dans le stockage interne de l'application : elles ne sont pas visibles depuis un gestionnaire de fichiers, ne peuvent pas être supprimées depuis l'application et disparaissent si on désinstalle l'application.
- L'image PNG n'est pas enregistrée dans la galerie : on passe par le partage Android (on peut l'y enregistrer dans Fichiers ou Drive). Seul le dernier export est gardé dans le cache.
- Seulement deux plans intégrés : on ne peut pas importer sa propre image de plan.
- Pas de zoom ; un plan plus petit que l'écran est affiché en haut à gauche, sans être centré.
- Les chevauchements d'objets et d'étiquettes ne sont pas gérés.
- Les textes anglais sont dans `values` et les textes français dans `values-fr`, donc la langue par défaut est l'anglais et pas le français.
