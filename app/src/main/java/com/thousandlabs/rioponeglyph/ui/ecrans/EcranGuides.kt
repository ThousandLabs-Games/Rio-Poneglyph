package com.thousandlabs.rioponeglyph.ui.ecrans

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private class Rubrique(val icone: ImageVector, val titre: String, val etapes: List<String>)

private val GUIDE = listOf(
    Rubrique(
        Icons.Outlined.FileDownload, "Importer des vidéos ou des fichiers",
        listOf(
            "Ouvrez le dossier de destination (Videos ou un sous-dossier), puis appuyez sur Importer.",
            "Choisissez « Fichiers », puis sélectionnez un ou plusieurs fichiers téléchargés.",
            "Chaque fichier est chiffré, relu et vérifié. L'original n'est supprimé de la Galerie et de Google Files qu'après cette vérification.",
            "Si Android demande une confirmation, appuyez sur « Supprimer les originaux » dans le bilan, puis acceptez.",
            "Gardez l'application ouverte pendant l'importation. En cas d'interruption, rien n'est perdu : l'original reste en place."
        )
    ),
    Rubrique(
        Icons.AutoMirrored.Outlined.MenuBook, "Importer un chapitre (manga, webtoon)",
        listOf(
            "Ouvrez Pages (ou un sous-dossier), appuyez sur Importer puis « Dossier de pages ».",
            "Choisissez le dossier qui contient les images du chapitre, puis « Utiliser ce dossier ».",
            "Si ce dossier contient plusieurs sous-dossiers d'images, chacun devient un chapitre, regroupés dans un dossier.",
            "Les pages sont classées dans l'ordre naturel des noms (2 avant 10), ou selon le fichier poneglyph.json s'il est présent.",
            "Le dossier d'origine est supprimé seulement si tous les chapitres ont été importés sans erreur."
        )
    ),
    Rubrique(
        Icons.Outlined.MoveToInbox, "Récupérer depuis Will of D",
        listOf(
            "Téléchargez vos vidéos et chapitres dans Will of D, le navigateur sécurisé de Thousand Labs Games.",
            "Dans Rio Poneglyph, appuyez sur Importer puis « Depuis Will of D ». La liste des éléments terminés s'affiche.",
            "Cochez ce que vous voulez récupérer, puis appuyez sur Récupérer. Chaque élément est chiffré et vérifié dans le coffre.",
            "Les vidéos vont dans Videos et les chapitres dans Pages, rangés dans un dossier par série. Ce dossier est retrouvé même si vous l'avez renommé ou déplacé.",
            "Une suite d'un chapitre déjà présent (page 2, partie suivante) est ajoutée au même chapitre, sans doublon.",
            "Une fois récupéré, l'élément disparaît de Will of D. L'échange se fait uniquement sur le téléphone, jamais par Internet, et Rio Poneglyph n'envoie rien vers Will of D."
        )
    ),
    Rubrique(
        Icons.Outlined.Folder, "Organiser vos dossiers",
        listOf(
            "Créez un dossier personnel depuis l'accueil, ou un sous-dossier avec l'icône de dossier en haut d'un dossier.",
            "Les sous-dossiers ne sont pas limités : par exemple Videos / Animés / One Piece / Arc Egghead.",
            "Triez par nom, date d'ajout, taille ou type, dans l'ordre croissant ou décroissant. Le tri est mémorisé.",
            "Recherchez par nom dans un dossier (loupe en haut) ou dans tout le coffre (barre de l'accueil)."
        )
    ),
    Rubrique(
        Icons.AutoMirrored.Outlined.DriveFileMove, "Renommer, déplacer, copier, supprimer",
        listOf(
            "Appuyez longuement sur un élément pour le sélectionner, puis touchez les autres pour les ajouter.",
            "Renommer change uniquement le nom affiché : le contenu chiffré et le format réel du fichier ne sont jamais modifiés.",
            "Déplacer est instantané, même pour de gros fichiers. Copier crée un double vérifié.",
            "Supprimer efface définitivement du coffre. Pour garder un fichier, utilisez plutôt Exporter."
        )
    ),
    Rubrique(
        Icons.Outlined.Movie, "Regarder une vidéo",
        listOf(
            "Touchez une vidéo : elle se lit directement depuis le coffre, sans copie en clair sur le téléphone.",
            "La lecture reprend automatiquement là où vous vous étiez arrêté.",
            "Touchez l'écran pour afficher les commandes. Faites glisser la barre pour aller à un moment précis.",
            "Double appui à gauche ou à droite : recul ou avance de 10 secondes.",
            "Glissement vertical à gauche : luminosité. Glissement vertical à droite : volume.",
            "Le bouton Vitesse permet de lire de 0,5x à 2x.",
            "À la fin d'une vidéo, l'épisode suivant du même dossier se lance après 5 secondes. Touchez Annuler pour rester."
        )
    ),
    Rubrique(
        Icons.AutoMirrored.Outlined.MenuBook, "Lire un chapitre",
        listOf(
            "Touchez un chapitre pour l'ouvrir. La lecture reprend à la dernière page lue.",
            "Trois modes en haut de l'écran : défilement vertical (webtoon), planche par planche, aperçu de toutes les pages.",
            "En mode planche par planche, choisissez le sens : de gauche à droite, ou de droite à gauche pour les mangas.",
            "Zoom à deux doigts ou par double appui sur une planche. Touchez l'écran pour afficher ou masquer les commandes.",
            "Dans l'aperçu, touchez une page pour y aller directement.",
            "À la dernière page, le bouton « Chapitre suivant » ouvre le chapitre suivant du même dossier."
        )
    ),
    Rubrique(
        Icons.Outlined.FileUpload, "Exporter ou sortir des fichiers",
        listOf(
            "Sélectionnez des éléments, puis touchez l'icône d'exportation.",
            "Copier : les fichiers sont recopiés en clair dans le dossier choisi et restent dans le coffre.",
            "Sortir : les fichiers sont recopiés, vérifiés, puis retirés du coffre.",
            "Les chapitres sont exportés en dossiers d'images avec leur fichier poneglyph.json."
        )
    ),
    Rubrique(
        Icons.Outlined.HealthAndSafety, "Avant de désinstaller",
        listOf(
            "La clé du coffre n'existe que dans ce téléphone : désinstaller ou effacer les données de l'application détruit le coffre.",
            "Ouvrez Réglages puis Garde-fou du coffre, et touchez « Exporter tout le coffre ».",
            "Vérifiez que les fichiers exportés s'ouvrent avant de désinstaller.",
            "« Vérifier l'intégrité du coffre » relit chaque fichier et signale tout problème."
        )
    ),
    Rubrique(
        Icons.Outlined.SystemUpdate, "Mettre à jour l'application",
        listOf(
            "Ouvrez Réglages puis Mises à jour, et touchez « Activer l'accès Internet (2 minutes) ».",
            "La recherche est automatique. Si une version est disponible, touchez Télécharger puis Installer.",
            "La signature du fichier est vérifiée avant l'installation. L'accès Internet se coupe dès la fin du téléchargement.",
            "La mise à jour s'installe par-dessus l'application : le coffre et les réglages sont conservés."
        )
    ),
    Rubrique(
        Icons.Outlined.Lock, "Sécurité et verrouillage",
        listOf(
            "L'application se verrouille dès qu'elle passe en arrière-plan. L'icône de cadenas la verrouille immédiatement.",
            "Après 5 tentatives échouées, l'accès est bloqué temporairement, de plus en plus longtemps.",
            "Pour changer de code, de schéma ou de mot de passe : Réglages, puis Méthode de déverrouillage.",
            "Les captures d'écran sont bloquées et l'aperçu est masqué dans les applications récentes."
        )
    )
)

private class Version(val numero: String, val points: List<String>)

private val NOUVEAUTES = listOf(
    Version(
        "1.1.0",
        listOf(
            "Récupération depuis Will of D : Importer puis « Depuis Will of D », échange local à sens unique protégé par la signature Thousand Labs.",
            "Rangement automatique par série dans Videos et Pages, et fusion sans doublon des suites d'un même chapitre.",
            "Lecteur de pages : bouton « Chapitre suivant » à la dernière page.",
            "Lecteur vidéo : épisode suivant automatique, recherche précise dans les vidéos issues de flux de streaming (format TS)."
        )
    ),
    Version(
        "1.0.0",
        listOf(
            "Lecteur de pages : défilement vertical, planche par planche (gauche-droite ou droite-gauche), aperçu des pages.",
            "Reprise à la dernière page lue, zoom à deux doigts et par double appui.",
            "Mises à jour intégrées : accès Internet limité à 2 minutes, téléchargement vérifié depuis le dépôt officiel.",
            "Guide « Comment faire » et écran Nouveautés."
        )
    ),
    Version(
        "0.2.0",
        listOf(
            "Coffre chiffré et importation par déplacement avec vérification d'intégrité.",
            "Dossiers et sous-dossiers, renommage sans risque, déplacement, copie, tri et recherche.",
            "Lecteur vidéo avec reprise, barre précise, gestes de luminosité et de volume, vitesse de lecture.",
            "Garde-fou : exportation complète et vérification du coffre."
        )
    ),
    Version(
        "0.1.0",
        listOf(
            "Animation d'ouverture du cube Rio Poneglyph.",
            "Déverrouillage par code PIN, schéma, mot de passe ou empreinte, blocage temporaire après 5 échecs.",
            "Verrouillage automatique, captures d'écran bloquées, aperçu masqué."
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcranTexte(titre: String, onRetour: () -> Unit, contenu: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titre) },
                navigationIcon = {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { marges ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(marges),
            content = contenu
        )
    }
}

@Composable
fun EcranGuide(onRetour: () -> Unit) {
    EcranTexte("Comment faire", onRetour) {
        items(GUIDE) { r -> CarteRubrique(r) }
    }
}

@Composable
private fun CarteRubrique(r: Rubrique) {
    var ouverte by remember { mutableStateOf(false) }
    Card(
        onClick = { ouverte = !ouverte },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(r.icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Text(r.titre, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (ouverte) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            if (ouverte) {
                r.etapes.forEachIndexed { i, e ->
                    Row(Modifier.padding(top = 10.dp)) {
                        Text("${i + 1}.", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                        Text(e, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun EcranNouveautes(onRetour: () -> Unit) {
    EcranTexte("Nouveautés", onRetour) {
        items(NOUVEAUTES) { v ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Version ${v.numero}", style = MaterialTheme.typography.titleSmall)
                    }
                    v.points.forEach { p ->
                        Row(Modifier.padding(top = 8.dp)) {
                            Text("–", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(18.dp))
                            Text(p, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
