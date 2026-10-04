# NetworkBN

TP2 MOB - Réseau domestique, partie 1.

Binôme : Ibrahim BALDE et Rodrigue NGUETSA

Dépôt : https://gitlab2.istic.univ-rennes1.fr/ibalde/networkbn/-/tree/main?ref_type=heads

## Lancer l'application

1. Cloner le dépôt : `git clone https://gitlab2.istic.univ-rennes1.fr/ibalde/networkbn.git`
2. Ouvrir le dossier dans Android Studio et attendre la synchronisation Gradle.
3. Lancer la configuration `app` sur un émulateur ou un téléphone (Android 7.0 minimum).

## Solution de dessin

On a choisi le dessin direct dans une vue personnalisée (3.1) : la classe `GrapheView` hérite de `View` et dessine le plan, les connexions et les objets dans `onDraw`. Le plan est dans `drawable-nodpi` et il est dessiné en (0, 0) sans redimensionnement.

Le projet suit une organisation MVVM : `Graph` (modèle), `GraphViewModel` (LiveData) et `MainActivity` / `GrapheView` (vue).

## Fonctionnalités réalisées

- Affichage du plan au lancement, sans objet ni connexion
- Trois modes (ajout d'objets, ajout de connexions, modification), choisis dans le menu. Le mode actif est affiché sous le titre.
- Ajout d'un objet par appui long sur le plan, avec saisie d'une étiquette (une étiquette vide est refusée)
- Déplacement des objets en mode modification, les connexions suivent
- Ajout de connexions par glissement d'un objet vers un autre, avec une connexion provisoire pendant le geste
- Refus des boucles et des doublons (A-B et B-A), vérifié dans le modèle
- Réinitialisation avec confirmation, le plan reste affiché
- Interface en français et en anglais

## Limites connues

- Pas de sauvegarde : le réseau est perdu quand on ferme l'application.
- Un seul plan, pas de suppression d'objet ou de connexion, pas de modification des étiquettes.
- Les textes anglais sont dans `values` et les textes français dans `values-fr`, donc la langue par défaut est l'anglais et pas le français.
- Les chevauchements d'objets et d'étiquettes ne sont pas gérés.
