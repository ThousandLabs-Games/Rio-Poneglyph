# Format des dossiers de pages Rio Poneglyph

Format standard des chapitres (mangas, webtoons) lus par Rio Poneglyph. Il sera aussi produit par le futur Navigateur Sécurisé de Thousand Labs Games.

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
  "version": 1,
  "titre": "Chapitre 12 - Le titre",
  "serie": "Nom de la série",
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

L'exportation d'un chapitre hors du coffre recrée ce même format.
