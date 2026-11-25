package com.thousandlabs.rioponeglyph.ui.ecrans

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.Session
import com.thousandlabs.rioponeglyph.coffre.Coffre
import com.thousandlabs.rioponeglyph.coffre.Exportation
import com.thousandlabs.rioponeglyph.coffre.Operations
import com.thousandlabs.rioponeglyph.util.Formats

/** Garde-fou : état du coffre et sortie complète avant toute désinstallation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranGardeFou(onRetour: () -> Unit) {
    val contexte = LocalContext.current
    val version by Coffre.version.collectAsState()
    val operation by Operations.etat.collectAsState()
    val stats = remember(version) { Coffre.statistiques() }
    val derniere = remember(version, operation) { Exportation.derniereExportationComplete(contexte) }
    val libre = remember(version) { contexte.filesDir.usableSpace }
    val vide = stats.fichiers == 0
    val protege = vide || derniere > 0L

    val choixDossier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        Session.reprendreVerrouAuto()
        if (uri != null) {
            Exportation.exporter(contexte.applicationContext, Coffre.racines().map { it.id }, uri, retirer = false, toutLeCoffre = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garde-fou du coffre") },
                navigationIcon = {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { marges ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (protege) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(16.dp)) {
                    Icon(
                        if (protege) Icons.Outlined.GppGood else Icons.Outlined.ReportProblem,
                        contentDescription = null,
                        tint = if (protege) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            when {
                                vide -> "Le coffre est vide"
                                derniere > 0L -> "Dernière sauvegarde complète : ${Formats.date(derniere)}"
                                else -> "Aucune sauvegarde complète du coffre"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "${stats.fichiers} fichier(s) · ${stats.dossiers} dossier(s) · ${Formats.taille(stats.octets)}\n" +
                                "Espace libre sur le téléphone : ${Formats.taille(libre)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text("Avant de désinstaller ou d'effacer les données", style = MaterialTheme.typography.titleMedium)
            Text(
                "Le contenu du coffre est chiffré avec une clé qui n'existe que dans ce téléphone, " +
                    "à l'intérieur de Rio Poneglyph. Désinstaller l'application, effacer ses données " +
                    "ou réinitialiser le téléphone détruit cette clé : les fichiers du coffre deviennent " +
                    "alors définitivement irrécupérables.\n\n" +
                    "Avant toute désinstallation, exportez tout le coffre vers un dossier du téléphone " +
                    "ou une carte SD, puis vérifiez que les fichiers s'ouvrent.\n\n" +
                    "Les mises à jour publiées sur le dépôt officiel s'installent par-dessus l'application " +
                    "et conservent le coffre.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    Session.suspendreVerrouAuto()
                    try { choixDossier.launch(null) } catch (e: Exception) { Session.reprendreVerrouAuto() }
                },
                enabled = !vide && !Operations.enCours,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Exporter tout le coffre")
            }
            Text(
                "Copie de tous les dossiers et fichiers, en clair, dans le dossier de votre choix. " +
                    "Chaque fichier est relu et vérifié. Le coffre n'est pas modifié.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { Exportation.verifierIntegrite() },
                enabled = !vide && !Operations.enCours,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Vérifier l'intégrité du coffre")
            }
            Text(
                "Relit chaque fichier chiffré et le compare à son empreinte enregistrée lors de l'importation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
