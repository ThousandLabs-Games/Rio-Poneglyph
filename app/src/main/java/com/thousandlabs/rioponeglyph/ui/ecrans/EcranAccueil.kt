package com.thousandlabs.rioponeglyph.ui.ecrans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.coffre.Coffre
import com.thousandlabs.rioponeglyph.coffre.Noeud
import com.thousandlabs.rioponeglyph.coffre.Operations
import com.thousandlabs.rioponeglyph.ui.composants.CubePoneglyph
import com.thousandlabs.rioponeglyph.ui.composants.DialogueNom
import com.thousandlabs.rioponeglyph.ui.composants.LigneNoeud
import com.thousandlabs.rioponeglyph.ui.composants.detailNoeud
import com.thousandlabs.rioponeglyph.ui.composants.messageNonOuvrable
import com.thousandlabs.rioponeglyph.ui.composants.rememberImporteurs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranAccueil(onPlus: () -> Unit, onVerrouiller: () -> Unit, onOuvrir: (Noeud) -> Unit, onWillOfD: () -> Unit) {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val messages = remember { SnackbarHostState() }
    val version by Coffre.version.collectAsState()
    val racines = remember(version) { Coffre.racines() }
    var requete by remember { mutableStateOf("") }
    val resultats = remember(version, requete) { Coffre.rechercher(requete) }
    var menuImport by remember { mutableStateOf(false) }
    var nouveauDossier by remember { mutableStateOf(false) }
    var destinationImport by remember { mutableStateOf(Coffre.ID_VIDEOS) }
    val importeurs = rememberImporteurs(contexte) { destinationImport }

    fun signaler(texte: String) {
        portee.launch { messages.showSnackbar(texte) }
    }

    fun ouvrir(n: Noeud) {
        messageNonOuvrable(n)?.let { signaler(it) } ?: onOuvrir(n)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(messages) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CubePoneglyph(Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Rio Poneglyph")
                    }
                },
                actions = {
                    IconButton(onClick = onVerrouiller) {
                        Icon(Icons.Outlined.Lock, contentDescription = "Verrouiller maintenant")
                    }
                    IconButton(onClick = onPlus) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Réglages et outils")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { if (!Operations.enCours) menuImport = true else signaler("Une opération est déjà en cours.") },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Importer") }
                )
                DropdownMenu(expanded = menuImport, onDismissRequest = { menuImport = false }) {
                    DropdownMenuItem(
                        text = { Text("Vidéos ou fichiers vers Videos") },
                        onClick = { menuImport = false; destinationImport = Coffre.ID_VIDEOS; importeurs.fichiers() }
                    )
                    DropdownMenuItem(
                        text = { Text("Dossier de pages vers Pages") },
                        onClick = { menuImport = false; destinationImport = Coffre.ID_PAGES; importeurs.dossier() }
                    )
                    DropdownMenuItem(
                        text = { Text("Depuis Will of D") },
                        onClick = { menuImport = false; onWillOfD() }
                    )
                }
            }
        }
    ) { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            OutlinedTextField(
                value = requete,
                onValueChange = { requete = it },
                singleLine = true,
                placeholder = { Text("Rechercher dans tout le coffre") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (requete.isNotEmpty()) {
                        IconButton(onClick = { requete = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Effacer") }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (requete.isNotBlank()) {
                if (resultats.isEmpty()) {
                    Text(
                        "Aucun résultat.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(resultats, key = { it.id }) { n ->
                        val parent = n.parent?.let { Coffre.noeud(it)?.nom }
                        LigneNoeud(
                            noeud = n,
                            detail = listOfNotNull(parent, detailNoeud(n)).joinToString(" · "),
                            modeSelection = false,
                            selectionne = false,
                            onClic = { ouvrir(n) },
                            onClicLong = {}
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Vos dossiers",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { nouveauDossier = true }) {
                                Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Nouveau dossier")
                            }
                        }
                    }
                    items(racines, key = { it.id }) { d ->
                        val icone = when (d.id) {
                            Coffre.ID_VIDEOS -> Icons.Outlined.VideoLibrary
                            Coffre.ID_PAGES -> Icons.AutoMirrored.Outlined.MenuBook
                            else -> Icons.Outlined.Folder
                        }
                        val description = when (d.id) {
                            Coffre.ID_VIDEOS -> "Animés, films et séries"
                            Coffre.ID_PAGES -> "Mangas et webtoons"
                            else -> "Dossier personnel"
                        }
                        CarteDossier(icone, d.nom, description, detailNoeud(d)) { onOuvrir(d) }
                    }
                }
            }
        }
    }

    if (nouveauDossier) {
        DialogueNom("Nouveau dossier", "", "Créer", onValider = { nom ->
            nouveauDossier = false
            portee.launch {
                withContext(Dispatchers.IO) { runCatching { Coffre.creerDossier(null, nom) } }
                    .onFailure { signaler(it.message ?: "Création impossible") }
            }
        }, onAnnuler = { nouveauDossier = false })
    }
}

@Composable
private fun CarteDossier(icone: ImageVector, nom: String, description: String, contenu: String, onClic: () -> Unit) {
    Card(
        onClick = onClic,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(nom, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(contenu, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
