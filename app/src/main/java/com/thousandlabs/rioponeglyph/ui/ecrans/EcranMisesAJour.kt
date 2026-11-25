package com.thousandlabs.rioponeglyph.ui.ecrans

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.BuildConfig
import com.thousandlabs.rioponeglyph.reseau.AccesReseau
import com.thousandlabs.rioponeglyph.reseau.MisesAJour
import com.thousandlabs.rioponeglyph.reseau.Signature
import com.thousandlabs.rioponeglyph.reseau.VersionDistante
import com.thousandlabs.rioponeglyph.ui.composants.formaterDuree
import com.thousandlabs.rioponeglyph.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface EtatMaj {
    data object Repos : EtatMaj
    data object Recherche : EtatMaj
    data object AJour : EtatMaj
    data class Disponible(val v: VersionDistante) : EtatMaj
    data class Telechargement(val v: VersionDistante, val progression: Float) : EtatMaj
    data class Pret(val v: VersionDistante, val apk: File) : EtatMaj
    data class Erreur(val message: String) : EtatMaj
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranMisesAJour(onRetour: () -> Unit) {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val fin by AccesReseau.fin.collectAsState()
    var reste by remember { mutableLongStateOf(AccesReseau.resteMs()) }
    var etat by remember { mutableStateOf<EtatMaj>(EtatMaj.Repos) }

    LaunchedEffect(fin) {
        reste = AccesReseau.resteMs()
        while (reste > 0L) {
            delay(500)
            reste = AccesReseau.resteMs()
        }
    }

    fun rechercher() {
        etat = EtatMaj.Recherche
        portee.launch {
            etat = withContext(Dispatchers.IO) {
                runCatching { MisesAJour.derniereVersion() }.fold(
                    onSuccess = { v ->
                        if (MisesAJour.plusRecente(v.version, BuildConfig.VERSION_NAME)) EtatMaj.Disponible(v) else EtatMaj.AJour
                    },
                    onFailure = { EtatMaj.Erreur(it.message ?: "Recherche impossible.") }
                )
            }
        }
    }

    fun telecharger(v: VersionDistante) {
        etat = EtatMaj.Telechargement(v, 0f)
        portee.launch {
            etat = withContext(Dispatchers.IO) {
                runCatching {
                    MisesAJour.telecharger(contexte, v) { p -> etat = EtatMaj.Telechargement(v, p) }
                }.fold(
                    onSuccess = { apk ->
                        when (MisesAJour.verifierSignature(contexte, apk)) {
                            Signature.DIFFERENTE -> {
                                apk.delete()
                                EtatMaj.Erreur("Le fichier téléchargé n'est pas signé par Thousand Labs Games : installation bloquée.")
                            }
                            else -> EtatMaj.Pret(v, apk)
                        }
                    },
                    onFailure = { EtatMaj.Erreur(it.message ?: "Téléchargement interrompu.") }
                )
            }
            // Le téléchargement terminé, l'accès Internet est refermé immédiatement.
            if (etat is EtatMaj.Pret) AccesReseau.couper()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mises à jour") },
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
            Text("Version installée : ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Icon(
                        if (reste > 0L) Icons.Outlined.Wifi else Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = if (reste > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (reste > 0L) "Accès Internet actif : ${formaterDuree(reste)}" else "Accès Internet désactivé",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Rio Poneglyph ne communique qu'avec le dépôt officiel GitHub, uniquement pendant cette fenêtre.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (reste > 0L) {
                OutlinedButton(onClick = { AccesReseau.couper() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Couper l'accès maintenant")
                }
            } else {
                Button(
                    onClick = {
                        AccesReseau.activer()
                        rechercher()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Activer l'accès Internet (2 minutes)")
                }
            }

            when (val e = etat) {
                EtatMaj.Repos -> Text(
                    "Activez l'accès pour rechercher automatiquement une nouvelle version.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EtatMaj.Recherche -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Recherche de la dernière version…")
                }
                EtatMaj.AJour -> Text("Rio Poneglyph est à jour.", style = MaterialTheme.typography.titleSmall)
                is EtatMaj.Disponible -> {
                    Text("Version ${e.v.version} disponible", style = MaterialTheme.typography.titleMedium)
                    if (e.v.notes.isNotBlank()) {
                        Text(e.v.notes.replace("## ", "").replace("**", ""), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { telecharger(e.v) }, enabled = reste > 0L, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Télécharger (${Formats.taille(e.v.taille)})")
                    }
                    if (reste == 0L) {
                        Text("Réactivez l'accès Internet pour télécharger.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is EtatMaj.Telechargement -> {
                    Text("Téléchargement de la version ${e.v.version}…")
                    LinearProgressIndicator(progress = { e.progression }, modifier = Modifier.fillMaxWidth())
                }
                is EtatMaj.Pret -> {
                    Text("Version ${e.v.version} téléchargée et vérifiée.", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "L'installation se fait par-dessus l'application : votre coffre et vos réglages sont conservés.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            if (!MisesAJour.peutInstaller(contexte)) {
                                MisesAJour.ouvrirAutorisationInstallation(contexte)
                            } else {
                                MisesAJour.installer(contexte, e.apk)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (MisesAJour.peutInstaller(contexte)) "Installer" else "Autoriser l'installation")
                    }
                }
                is EtatMaj.Erreur -> Text(e.message, color = MaterialTheme.colorScheme.error)
            }

            TextButton(onClick = {
                try {
                    contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MisesAJour.URL_RELEASES)))
                } catch (ex: ActivityNotFoundException) {
                }
            }) { Text("Voir toutes les versions sur GitHub") }
        }
    }
}
