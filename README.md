# Rio Poneglyph

Gestionnaire de fichiers sécurisé et hors ligne pour Android : coffre chiffré, lecteur vidéo et lecteur de pages (mangas, webtoons).

Édité par **Thousand Labs Games**. Application à usage personnel.

Dépôt officiel : https://github.com/ThousandLabs-Games/Rio-Poneglyph

## Installation

1. Ouvrez la page [Releases](https://github.com/ThousandLabs-Games/Rio-Poneglyph/releases) depuis le téléphone.
2. Téléchargez le fichier `Rio-Poneglyph-x.y.z.apk` de la dernière version.
3. Ouvrez-le et autorisez l'installation si Android le demande.

Les mises à jour s'installent par-dessus la version existante sans perte de données, tant qu'elles proviennent de ce dépôt. Depuis la version 1.0 : Réglages → Mises à jour → « Activer l'accès Internet (2 minutes) ».

## Avant de désinstaller

Le contenu du coffre est chiffré avec une clé liée à ce téléphone. Une désinstallation ou un effacement des données de l'application supprime définitivement le coffre. Utilisez d'abord **Réglages → Garde-fou du coffre → Exporter tout le coffre**.

## Sécurité des données

- Contenu chiffré en AES-256 (mode CTR, lecture vidéo à la volée sans fichier en clair).
- Clé du coffre protégée par le module de sécurité du téléphone (Android Keystore).
- Noms et arborescence chiffrés (AES-GCM), enregistrés deux fois de façon atomique.
- À l'importation, l'original n'est supprimé qu'après relecture complète et comparaison d'empreinte SHA-256.
- Renommer ou déplacer ne touche jamais au contenu chiffré.

Format des chapitres et échange avec Will of D : voir [FORMAT_PAGES.md](FORMAT_PAGES.md).

Will of D : https://github.com/ThousandLabs-Games/Will-of-D

## Fonctionnalités

| Domaine | État |
|---|---|
| Ouverture animée, verrouillage PIN / schéma / mot de passe / empreinte | Version 0.1 |
| Blocage temporaire, verrouillage automatique, captures bloquées | Version 0.1 |
| Coffre chiffré, importation par déplacement, dossiers et sous-dossiers | Version 0.2 |
| Lecteur vidéo intégré, visionneuse d'images | Version 0.2 |
| Garde-fou : exportation complète et vérification d'intégrité | Version 0.2 |
| Lecteur de pages : défilement vertical, planche par planche, aperçu | Version 1.0 |
| Récupération depuis Will of D, rangement par série, chapitre et épisode suivants | Version 1.1 |
| Mises à jour intégrées (accès Internet de 2 minutes, signature vérifiée) | Version 1.0 |
| Guide « Comment faire » et Nouveautés intégrés | Version 1.0 |
| Icône plein cadre et animation d'ouverture tirée de la même pierre | Version 1.1.1 |
| Réglages : style et durée d'animation, verrouillage en arrière-plan avec délai, confidentialité et fonctions des lecteurs activables | Version 1.2 |

## Confidentialité

- Aucune connexion Internet en usage normal. L'accès n'est ouvert que 2 minutes, à votre demande, depuis Réglages → Mises à jour, et uniquement vers GitHub.
- Aucune publicité, aucun suivi, aucun compte.
- Aucun service d'accessibilité.

## Compilation

La compilation est automatique (GitHub Actions) à chaque modification. Une Release est publiée automatiquement à chaque nouveau numéro de version.
