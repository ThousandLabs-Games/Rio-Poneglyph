# Format des dossiers de pages Rio Poneglyph

Format standard des chapitres (mangas, webtoons) lus par Rio Poneglyph. Il est aussi produit par Will of D, le navigateur sécurisé de Thousand Labs Games.

## Structure

```
Mon chapitre/
├── 001.jpg
├── 002.jpg
├── ...
└── poneglyph.json      (facultatif)
```

- Images acceptées : JPG, PNG, WEBP, GIF, BMP, HEIC, AVIF.
- Sans manifeste, les pages sont classées par ordre naturel du nom (2 avant 10).
- Un dossier contenant plusieurs sous-dossiers d'images est importé comme une série : un dossier dans le coffre, un chapitre par sous-dossier.

## Manifeste `poneglyph.json`

```json
{
  "format": "rio-poneglyph-pages",
  "version": 2,
  "titre": "Chapitre 12 - Le titre",
  "serie": "Nom de la série",
  "numero": 12,
  "partie": 1,
  "cle_serie": "serie:nom-de-la-serie",
  "cle_element": "chapitre:nom-de-la-serie:12",
  "sens": "vertical",
  "pages": ["001.jpg", "002.jpg", "003.jpg"]
}
```

| Champ | Rôle |
|---|---|
| `format` | Toujours `rio-poneglyph-pages` |
| `version` | Version du format (1) |
| `titre` | Nom du chapitre dans le coffre (sinon, nom du dossier) |
| `serie` | Facultatif, informatif |
| `sens` | Mode de lecture conseillé : `vertical` (webtoon), `gauche-droite`, `droite-gauche` (manga) |
| `pages` | Ordre exact des pages ; les images non listées sont ajoutées à la fin |
| `numero` | Version 2, facultatif : numéro du chapitre (ordre de lecture et chapitre suivant) |
| `partie` | Version 2, facultatif : partie d'un chapitre téléchargé en plusieurs fois (1, 2…) |
| `cle_serie` | Version 2, facultatif : identifiant stable de la série (dossier retrouvé même renommé) |
| `cle_element` | Version 2, facultatif : identifiant stable du chapitre ; deux envois de même clé sont fusionnés |

La version 1 reste lue telle quelle.

## Échange avec Will of D

Échange local, à sens unique, sans réseau.

- Will of D expose son dépôt par le fournisseur `content://com.thousandlabs.willofd.depot`, protégé par la permission `com.thousandlabs.permission.DEPOT_WILL_OF_D` (niveau signature : seules les applications signées avec la même clé Thousand Labs y ont accès).
- `elements` : liste des éléments terminés. Colonnes `id`, `type` (`video` ou `pages`), `titre`, `serie`, `cle_serie`, `cle_element`, `numero`, `partie`, `sens`, `taille`, `fichiers`, `ajoute`.
- `elements/{id}/fichiers` : fichiers de l'élément dans l'ordre de lecture. Colonnes `nom`, `taille` (exacte), `mime`.
- `elements/{id}/fichiers/{nom}` : lecture seule du fichier.
- Suppression de `elements/{id}` : demandée par Rio Poneglyph uniquement après chiffrement vérifié ; Will of D efface alors l'élément et le retire de sa liste.

Rio Poneglyph n'écrit jamais rien dans Will of D : la seule demande émise est cet effacement.

L'exportation d'un chapitre hors du coffre recrée ce même format.
