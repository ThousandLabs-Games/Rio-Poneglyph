package com.thousandlabs.rioponeglyph.ui.ecrans

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
fun EcranPlus(verrou: GestionVerrou, onRetour: () -> Unit, onModifierMethode: () -> Unit, onGardeFou: () -> Unit) {
    val contexte = LocalContext.current
    val biometrieDisponible = remember { Biometrie.disponible(contexte) }
    var biometrie by remember { mutableStateOf(verrou.biometrieActive && biometrieDisponible) }
    var messageBiometrie by remember { mutableStateOf<String?>(null) }

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
            LigneReglage(Icons.Outlined.Timer, "Blocage temporaire", "Après 5 tentatives échouées, durée croissante")
            LigneReglage(Icons.Outlined.Shield, "Verrouillage automatique", "Dès que l'application passe en arrière-plan")
            LigneReglage(Icons.Outlined.VisibilityOff, "Confidentialité", "Captures d'écran bloquées, aperçu masqué dans les applications récentes")

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TitreSection("Coffre")
            LigneReglage(
                Icons.Outlined.HealthAndSafety, "Garde-fou du coffre",
                "État du coffre, exportation complète et vérification avant désinstallation",
                onClic = onGardeFou,
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
    fin: (@Composable () -> Unit)? = null
) {
    val base = Modifier.fillMaxWidth()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onClic != null) base.clickable(onClick = onClic) else base)
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
