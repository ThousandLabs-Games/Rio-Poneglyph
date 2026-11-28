package com.thousandlabs.rioponeglyph.ui.ecrans

import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.thousandlabs.rioponeglyph.DelaiVerrou
import com.thousandlabs.rioponeglyph.DureeAnimation
import com.thousandlabs.rioponeglyph.MainActivity
import com.thousandlabs.rioponeglyph.Reglages
import com.thousandlabs.rioponeglyph.StyleAnimation
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.BuildConfig
import com.thousandlabs.rioponeglyph.securite.Biometrie
import com.thousandlabs.rioponeglyph.securite.GestionVerrou

const val URL_DEPOT = "https://github.com/ThousandLabs-Games/Rio-Poneglyph"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranPlus(verrou: GestionVerrou, onRetour: () -> Unit, onModifierMethode: () -> Unit, onGardeFou: () -> Unit,
    onMisesAJour: () -> Unit,
    onGuide: () -> Unit,
    onNouveautes: () -> Unit
) {
    val contexte = LocalContext.current
    val biometrieDisponible = remember { Biometrie.disponible(contexte) }
    var biometrie by remember { mutableStateOf(verrou.biometrieActive && biometrieDisponible) }
    var messageBiometrie by remember { mutableStateOf<String?>(null) }
    var choix by remember { mutableStateOf<Choix?>(null) }
    var apercu by remember { mutableStateOf(false) }

    when (choix) {
        Choix.STYLE -> DialogueChoix(
            "Style d'animation", StyleAnimation.entries.map { it to "${it.libelle}\n${it.detail}" },
            Reglages.styleAnimation, { Reglages.changerStyleAnimation(it) }, { choix = null }
        )
        Choix.DUREE -> DialogueChoix(
            "Durée de l'animation", DureeAnimation.entries.map { it to it.libelle },
            Reglages.dureeAnimation, { Reglages.changerDureeAnimation(it) }, { choix = null }
        )
        Choix.DELAI -> DialogueChoix(
            "Délai avant verrouillage", DelaiVerrou.entries.map { it to it.libelle },
            Reglages.delaiVerrou, { Reglages.changerDelaiVerrou(it) }, { choix = null }
        )
        null -> Unit
    }
    if (apercu) {
        Dialog(
            onDismissRequest = { apercu = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            EcranOuverture(onTermine = { apercu = false })
        }
    }

    fun basculerBiometrie(activer: Boolean) {
        if (!activer) {
            verrou.biometrieActive = false
            biometrie = false
            return
        }
        val activite = Biometrie.activite(contexte) ?: return
        Biometrie.demander(
            activite,
            titre = "Activer l'empreinte",
            sousTitre = "Posez votre doigt sur le capteur",
            onSucces = {
                verrou.biometrieActive = true
                biometrie = true
                messageBiometrie = null
            },
            onErreur = { messageBiometrie = it }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages et outils") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges)
                .verticalScroll(rememberScrollState())
        ) {
            TitreSection("Sécurité")
            LigneReglage(
                Icons.Outlined.Lock, "Méthode de déverrouillage", verrou.methode.libelle,
                onClic = onModifierMethode,
                fin = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
            )
            LigneReglage(
                Icons.Outlined.Fingerprint, "Déverrouillage par empreinte",
                when {
                    !biometrieDisponible -> "Aucune empreinte enregistrée sur ce téléphone"
                    messageBiometrie != null -> messageBiometrie!!
                    biometrie -> "Activé"
                    else -> "Désactivé"
                },
                onClic = if (biometrieDisponible) ({ basculerBiometrie(!biometrie) }) else null,
                fin = {
                    Switch(
                        checked = biometrie,
                        onCheckedChange = { basculerBiometrie(it) },
                        enabled = biometrieDisponible
                    )
                }
            )
            LigneInterrupteur(
                Icons.Outlined.Shield, "Verrouillage en arrière-plan",
                if (Reglages.verrouArrierePlan) "Le coffre se verrouille quand vous quittez l'application"
                else "Désactivé : le coffre reste ouvert jusqu'à fermeture ou verrouillage manuel",
                Reglages.verrouArrierePlan, Reglages::changerVerrouArrierePlan
            )
            LigneReglage(
                Icons.Outlined.LockClock, "Délai avant verrouillage", Reglages.delaiVerrou.libelle,
                onClic = { choix = Choix.DELAI },
                actif = Reglages.verrouArrierePlan,
                fin = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
            )
            LigneInterrupteur(
                Icons.Outlined.VisibilityOff, "Confidentialité",
                if (Reglages.confidentialite) "Captures d'écran bloquées, aperçu masqué dans les applications récentes"
                else "Désactivée : captures d'écran et aperçu autorisés",
                Reglages.confidentialite
            ) {
                Reglages.changerConfidentialite(it)
                Biometrie.activite(contexte)?.let { a -> MainActivity.appliquerConfidentialite(a) }
            }
            LigneReglage(Icons.Outlined.Timer, "Blocage temporaire", "Toujours actif : après 5 tentatives échouées, durée croissante")

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TitreSection("Ouverture")
            LigneInterrupteur(
                Icons.Outlined.AutoAwesome, "Animation d'ouverture",
                if (Reglages.animationOuverture) "Le poneglyph s'illumine au lancement" else "Désactivée : ouverture directe",
                Reglages.animationOuverture, Reglages::changerAnimationOuverture
            )
            LigneReglage(
                Icons.Outlined.Style, "Style d'animation",
                "${Reglages.styleAnimation.libelle} · ${Reglages.styleAnimation.detail}",
                onClic = { choix = Choix.STYLE },
                actif = Reglages.animationOuverture,
                fin = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
            )
            LigneReglage(
                Icons.Outlined.HourglassTop, "Durée de l'animation", Reglages.dureeAnimation.libelle,
                onClic = { choix = Choix.DUREE },
                actif = Reglages.animationOuverture,
                fin = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
            )
            LigneReglage(
                Icons.Outlined.PlayCircle, "Revoir l'animation", "Aperçu avec le style et la durée choisis",
                onClic = { apercu = true },
                fin = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TitreSection("Lecteurs")
            LigneInterrupteur(
                Icons.Outlined.SkipNext, "Épisode suivant automatique",
                "Lance la vidéo suivante du dossier après 5 secondes",
                Reglages.episodeSuivantAuto, Reglages::changerEpisodeSuivantAuto
            )
            LigneInterrupteur(
                Icons.Outlined.History, "Reprise des vidéos",
                "Reprend chaque vidéo là où vous l'aviez laissée",
                Reglages.repriseVideo, Reglages::changerRepriseVideo
            )
            LigneInterrupteur(
                Icons.Outlined.Swipe, "Gestes du lecteur vidéo",
                "Glisser à gauche pour la luminosité, à droite pour le volume",
                Reglages.gestesVideo, Reglages::changerGestesVideo
            )
            LigneInterrupteur(
                Icons.AutoMirrored.Outlined.MenuBook, "Bouton « Chapitre suivant »",
                "Affiché à la dernière page d'un chapitre",
                Reglages.chapitreSuivant, Reglages::changerChapitreSuivant
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TitreSection("Importation")
            LigneInterrupteur(
                Icons.Outlined.MoveToInbox, "Récupération depuis Will of D",
                "Option « Depuis Will of D » dans le menu Importer",
                Reglages.willOfD, Reglages::changerWillOfD
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TitreSection("Coffre")
            LigneReglage(
                Icons.Outlined.HealthAndSafety, "Garde-fou du coffre",
                "État du coffre, exportation complète et vérification avant désinstallation",
                onClic = onGardeFou,
                fin = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TitreSection("Mises à jour et aide")
            LigneReglage(
                Icons.Outlined.SystemUpdate, "Mises à jour",
                "Accès Internet temporaire (2 minutes) pour installer la dernière version",
                onClic = onMisesAJour,
                fin = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
            )
            LigneReglage(
                Icons.AutoMirrored.Outlined.HelpOutline, "Comment faire",
                "Guide d'utilisation pas à pas",
                onClic = onGuide,
                fin = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
            )
            LigneReglage(
                Icons.Outlined.NewReleases, "Nouveautés",
                "Contenu de chaque version",
                onClic = onNouveautes,
                fin = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TitreSection("À propos")
            LigneReglage(Icons.Outlined.Info, "Rio Poneglyph", "Version ${BuildConfig.VERSION_NAME}")
            LigneReglage(Icons.Outlined.Business, "Éditeur", "Thousand Labs Games")
            LigneReglage(
                Icons.Outlined.Code, "Dépôt GitHub officiel", "github.com/ThousandLabs-Games/Rio-Poneglyph",
                onClic = {
                    try {
                        contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_DEPOT)))
                    } catch (e: ActivityNotFoundException) {
                    }
                },
                fin = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
            )
        }
    }
}

private enum class Choix { STYLE, DUREE, DELAI }

@Composable
private fun <T> DialogueChoix(
    titre: String,
    options: List<Pair<T, String>>,
    actuel: T,
    onChoix: (T) -> Unit,
    onFermer: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(titre) },
        text = {
            Column {
                options.forEach { (valeur, libelle) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoix(valeur); onFermer() }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(selected = valeur == actuel, onClick = { onChoix(valeur); onFermer() })
                        Spacer(Modifier.width(8.dp))
                        val lignes = libelle.split("\n")
                        Column {
                            Text(lignes[0], style = MaterialTheme.typography.bodyLarge)
                            if (lignes.size > 1) {
                                Text(
                                    lignes[1],
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } }
    )
}

@Composable
private fun LigneInterrupteur(
    icone: ImageVector,
    titre: String,
    detail: String,
    valeur: Boolean,
    onChange: (Boolean) -> Unit
) {
    LigneReglage(
        icone, titre, detail,
        onClic = { onChange(!valeur) },
        fin = { Switch(checked = valeur, onCheckedChange = onChange) }
    )
}

@Composable
private fun TitreSection(titre: String) {
    Text(
        titre,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun LigneReglage(
    icone: ImageVector,
    titre: String,
    detail: String,
    onClic: (() -> Unit)? = null,
    actif: Boolean = true,
    fin: (@Composable () -> Unit)? = null
) {
    val base = Modifier
        .fillMaxWidth()
        .alpha(if (actif) 1f else 0.4f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onClic != null && actif) base.clickable(onClick = onClic) else base)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (fin != null) {
            Spacer(Modifier.width(12.dp))
            fin()
        }
    }
}
