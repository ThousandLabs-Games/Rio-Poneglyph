package com.thousandlabs.rioponeglyph.ui.ecrans

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.Session
import com.thousandlabs.rioponeglyph.coffre.Coffre
import com.thousandlabs.rioponeglyph.coffre.Exportation
import com.thousandlabs.rioponeglyph.coffre.Noeud
import com.thousandlabs.rioponeglyph.coffre.Operations
import com.thousandlabs.rioponeglyph.ui.composants.CritereTri
import com.thousandlabs.rioponeglyph.ui.composants.DialogueConfirmation
import com.thousandlabs.rioponeglyph.ui.composants.DialogueDestination
import com.thousandlabs.rioponeglyph.ui.composants.DialogueNom
import com.thousandlabs.rioponeglyph.ui.composants.LigneNoeud
import com.thousandlabs.rioponeglyph.ui.composants.PreferencesTri
import com.thousandlabs.rioponeglyph.ui.composants.detailNoeud
import com.thousandlabs.rioponeglyph.ui.composants.messageNonOuvrable
import com.thousandlabs.rioponeglyph.ui.composants.rememberImporteurs
import com.thousandlabs.rioponeglyph.ui.composants.trier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Transfert { DEPLACER, COPIER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranDossier(id: String, onRetour: () -> Unit, onOuvrir: (Noeud) -> Unit, onPlus: () -> Unit) {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val messages = remember { SnackbarHostState() }
    val version by Coffre.version.collectAsState()
    val dossier = remember(version, id) { Coffre.noeud(id) }
    if (dossier == null) {
        LaunchedEffect(Unit) { onRetour() }
        return
    }

    var critere by remember { mutableStateOf(PreferencesTri.critere(contexte)) }
    var croissant by remember { mutableStateOf(PreferencesTri.croissant(contexte)) }
    var recherche by remember { mutableStateOf<String?>(null) }
    val selection = remember(id) { mutableStateListOf<String>() }
    val elements = remember(version, id, critere, croissant, recherche) {
        val tous = trier(Coffre.enfants(id), critere, croissant)
        val r = recherche?.trim().orEmpty().lowercase()
        if (r.isEmpty()) tous else tous.filter { it.nomComplet.lowercase().contains(r) }
    }
    val chemin = remember(version, id) { Coffre.chemin(id).dropLast(1).joinToString(" / ") { it.nom } }

    var menuTri by remember { mutableStateOf(false) }
    var menuImport by remember { mutableStateOf(false) }
    var nouveauDossier by remember { mutableStateOf(false) }
    var aRenommer by remember { mutableStateOf<Noeud?>(null) }
    var transfert by remember { mutableStateOf<Transfert?>(null) }
    var confirmerSuppression by remember { mutableStateOf(false) }
    var choixSortie by remember { mutableStateOf(false) }
    var idsExport by remember { mutableStateOf(emptyList<String>()) }
    var retirerExport by remember { mutableStateOf(false) }

    // Retire de la sélection ce qui n'existe plus.
    LaunchedEffect(version) { selection.retainAll { Coffre.noeud(it) != null } }

    val importeurs = rememberImporteurs(contexte) { id }
    val choixExport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        Session.reprendreVerrouAuto()
        if (uri != null) {
            if (Exportation.exporter(contexte.applicationContext, idsExport, uri, retirerExport)) selection.clear()
        }
    }

    fun signaler(texte: String) {
        portee.launch { messages.showSnackbar(texte) }
    }

    fun executer(action: () -> Unit, message: String? = null) {
        portee.launch {
            val r = withContext(Dispatchers.IO) { runCatching { action() } }
            r.onSuccess { message?.let { signaler(it) } }
                .onFailure { signaler(it.message ?: "Opération impossible") }
        }
    }

    BackHandler(enabled = selection.isNotEmpty() || recherche != null) {
        if (selection.isNotEmpty()) selection.clear() else recherche = null
    }

    val modeSelection = selection.isNotEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(messages) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (modeSelection) {
                        IconButton(onClick = { selection.clear() }) { Icon(Icons.Outlined.Close, contentDescription = "Annuler la sélection") }
                    } else {
                        IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour") }
                    }
                },
                title = {
                    if (modeSelection) {
                        Text("${selection.size} sélectionné(s)")
                    } else {
                        Column {
                            Text(dossier.nom, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (chemin.isNotEmpty()) {
                                Text(
                                    chemin,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (modeSelection) {
                        IconButton(onClick = {
                            selection.clear(); selection.addAll(elements.map { it.id })
                        }) { Icon(Icons.Outlined.SelectAll, contentDescription = "Tout sélectionner") }
                        if (selection.size == 1) {
                            IconButton(onClick = { aRenommer = Coffre.noeud(selection.first()) }) {
                                Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Renommer")
                            }
                        }
                        IconButton(onClick = { transfert = Transfert.DEPLACER }) {
                            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "Déplacer")
                        }
                        IconButton(onClick = { transfert = Transfert.COPIER }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copier")
                        }
                        IconButton(onClick = { choixSortie = true }) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = "Exporter")
                        }
                        IconButton(onClick = { confirmerSuppression = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Supprimer")
                        }
                    } else {
                        IconButton(onClick = { recherche = if (recherche == null) "" else null }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Rechercher")
                        }
                        Box {
                            IconButton(onClick = { menuTri = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Trier") }
                            DropdownMenu(expanded = menuTri, onDismissRequest = { menuTri = false }) {
                                CritereTri.entries.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text((if (c == critere) "• " else "") + c.libelle) },
                                        onClick = {
                                            critere = c
                                            PreferencesTri.enregistrer(contexte, critere, croissant)
                                            menuTri = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (croissant) "Ordre : croissant" else "Ordre : décroissant") },
                                    onClick = {
                                        croissant = !croissant
                                        PreferencesTri.enregistrer(contexte, critere, croissant)
                                        menuTri = false
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { nouveauDossier = true }) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Nouveau sous-dossier")
                        }
                        IconButton(onClick = onPlus) { Icon(Icons.Outlined.Settings, contentDescription = "Réglages et outils") }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!modeSelection) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { if (!Operations.enCours) menuImport = true else signaler("Une opération est déjà en cours.") },
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text("Importer") }
                    )
                    DropdownMenu(expanded = menuImport, onDismissRequest = { menuImport = false }) {
                        DropdownMenuItem(
                            text = { Text("Fichiers (vidéos, images…)") },
                            onClick = { menuImport = false; importeurs.fichiers() }
                        )
                        DropdownMenuItem(
                            text = { Text("Dossier de pages (chapitre)") },
                            onClick = { menuImport = false; importeurs.dossier() }
                        )
                    }
                }
            }
        }
    ) { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            if (recherche != null) {
                OutlinedTextField(
                    value = recherche.orEmpty(),
                    onValueChange = { recherche = it },
                    singleLine = true,
                    placeholder = { Text("Rechercher dans ce dossier") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (elements.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (recherche.isNullOrBlank()) "Ce dossier est vide" else "Aucun résultat",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (recherche.isNullOrBlank()) {
                        Text(
                            "Appuyez sur Importer pour y déplacer des fichiers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                    items(elements, key = { it.id }) { n ->
                        val choisi = n.id in selection
                        LigneNoeud(
                            noeud = n,
                            detail = detailNoeud(n),
                            modeSelection = modeSelection,
                            selectionne = choisi,
                            onClic = {
                                when {
                                    modeSelection -> if (choisi) selection.remove(n.id) else selection.add(n.id)
                                    else -> messageNonOuvrable(n)?.let { signaler(it) } ?: onOuvrir(n)
                                }
                            },
                            onClicLong = { if (!choisi) selection.add(n.id) }
                        )
                    }
                }
            }
        }
    }

    if (nouveauDossier) {
        DialogueNom("Nouveau sous-dossier", "", "Créer", onValider = { nom ->
            nouveauDossier = false
            executer({ Coffre.creerDossier(id, nom) })
        }, onAnnuler = { nouveauDossier = false })
    }

    aRenommer?.let { n ->
        DialogueNom("Renommer", n.nom, "Renommer", onValider = { nom ->
            aRenommer = null
            executer({ Coffre.renommer(n.id, nom) }, "Renommé")
            selection.clear()
        }, onAnnuler = { aRenommer = null })
    }

    transfert?.let { t ->
        val ids = selection.toList()
        DialogueDestination(
            titre = if (t == Transfert.DEPLACER) "Déplacer vers" else "Copier vers",
            exclus = if (t == Transfert.DEPLACER) ids.toSet() else emptySet(),
            onChoisir = { destination ->
                transfert = null
                if (t == Transfert.DEPLACER) {
                    executer({
                        val n = Coffre.deplacer(ids, destination)
                        if (n == 0) throw IllegalStateException("Aucun élément n'a pu être déplacé à cet endroit")
                    }, "Déplacement effectué")
                    selection.clear()
                } else {
                    val lance = Operations.lancer("Copie") { suivi ->
                        val total = ids.sumOf { i -> Coffre.avecDescendants(i).sumOf { it.taille } }.coerceAtLeast(1L)
                        val n = Coffre.copier(ids, destination) { o -> suivi.maj("Copie en cours…", o.toFloat() / total) }
                        com.thousandlabs.rioponeglyph.coffre.EtatOperation("Copie", bilan = "$n élément(s) copié(s).")
                    }
                    if (lance) selection.clear() else signaler("Une opération est déjà en cours.")
                }
            },
            onAnnuler = { transfert = null }
        )
    }

    if (confirmerSuppression) {
        DialogueConfirmation(
            titre = "Supprimer du coffre",
            texte = "Supprimer définitivement ${selection.size} élément(s) et tout leur contenu ? " +
                "Cette action est irréversible. Pour les garder, utilisez plutôt Exporter.",
            bouton = "Supprimer",
            onConfirmer = {
                confirmerSuppression = false
                val ids = selection.toList()
                selection.clear()
                executer({ Coffre.supprimer(ids) }, "Suppression effectuée")
            },
            onAnnuler = { confirmerSuppression = false }
        )
    }

    if (choixSortie) {
        AlertDialog(
            onDismissRequest = { choixSortie = false },
            title = { Text("Exporter hors du coffre") },
            text = {
                Text(
                    "Copier : les fichiers restent aussi dans le coffre.\n\n" +
                        "Sortir : les fichiers sont retirés du coffre, uniquement après vérification de la copie.\n\n" +
                        "Vous allez ensuite choisir le dossier de destination sur le téléphone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    choixSortie = false
                    idsExport = selection.toList(); retirerExport = true
                    Session.suspendreVerrouAuto()
                    try { choixExport.launch(null) } catch (e: Exception) { Session.reprendreVerrouAuto() }
                }) { Text("Sortir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    choixSortie = false
                    idsExport = selection.toList(); retirerExport = false
                    Session.suspendreVerrouAuto()
                    try { choixExport.launch(null) } catch (e: Exception) { Session.reprendreVerrouAuto() }
                }) { Text("Copier") }
            }
        )
    }
}
