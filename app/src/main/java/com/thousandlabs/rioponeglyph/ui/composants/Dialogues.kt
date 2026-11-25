package com.thousandlabs.rioponeglyph.ui.composants

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.thousandlabs.rioponeglyph.Session
import com.thousandlabs.rioponeglyph.coffre.Coffre
import com.thousandlabs.rioponeglyph.coffre.Operations
import com.thousandlabs.rioponeglyph.coffre.TriNaturel
import com.thousandlabs.rioponeglyph.coffre.TypeNoeud

@Composable
fun DialogueNom(titre: String, initial: String, libelleBouton: String, onValider: (String) -> Unit, onAnnuler: () -> Unit) {
    var valeur by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(titre) },
        text = {
            OutlinedTextField(
                value = valeur,
                onValueChange = { valeur = it },
                singleLine = true,
                label = { Text("Nom") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(onClick = { onValider(valeur.text) }, enabled = valeur.text.isNotBlank()) { Text(libelleBouton) }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}

@Composable
fun DialogueConfirmation(titre: String, texte: String, bouton: String, onConfirmer: () -> Unit, onAnnuler: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(titre) },
        text = { Text(texte) },
        confirmButton = { TextButton(onClick = onConfirmer) { Text(bouton, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}

/** Choix d'un dossier de destination à l'intérieur du coffre. */
@Composable
fun DialogueDestination(titre: String, exclus: Set<String>, onChoisir: (String) -> Unit, onAnnuler: () -> Unit) {
    var courant by remember { mutableStateOf<String?>(null) }
    val version by Coffre.version.collectAsState()
    val dossiers = remember(courant, version) {
        val liste = if (courant == null) Coffre.racines() else Coffre.enfants(courant).sortedWith(compareBy(TriNaturel) { it.nom })
        liste.filter { it.type == TypeNoeud.DOSSIER && it.id !in exclus }
    }
    val nomCourant = remember(courant, version) { courant?.let { Coffre.noeud(it)?.nom } ?: "Coffre" }
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(titre) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (courant != null) {
                        IconButton(onClick = { courant = Coffre.noeud(courant!!)?.parent }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Dossier parent")
                        }
                    }
                    Text(nomCourant, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (dossiers.isEmpty()) {
                    Text(
                        "Aucun sous-dossier.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(dossiers, key = { it.id }) { d ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { courant = d.id }
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(d.nom, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { courant?.let(onChoisir) }, enabled = courant != null) { Text("Choisir ce dossier") }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}

/** Suivi des opérations longues, affiché par-dessus l'application. */
@Composable
fun DialogueOperation() {
    val etat by Operations.etat.collectAsState()
    val e = etat ?: return
    val contexte = LocalContext.current
    val vue = LocalView.current
    val suppression = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { resultat ->
        Session.reprendreVerrouAuto()
        Operations.modifierBilan {
            if (resultat.resultCode == Activity.RESULT_OK) {
                it.copy(bilan = it.bilan + "\nLes originaux restants ont été supprimés.", originauxRestants = emptyList())
            } else {
                it.copy(bilan = it.bilan + "\nSuppression des originaux refusée : ils sont toujours dans l'espace partagé.")
            }
        }
    }
    DisposableEffect(e.termine) {
        vue.keepScreenOn = !e.termine
        onDispose { vue.keepScreenOn = false }
    }
    AlertDialog(
        onDismissRequest = { if (e.termine) Operations.fermer() },
        properties = DialogProperties(dismissOnBackPress = e.termine, dismissOnClickOutside = false),
        title = { Text(e.titre) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!e.termine) {
                    Text(e.detail, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(top = 14.dp))
                    val p = e.progression
                    if (p != null) {
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "${(p * 100).toInt()} %",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "Gardez l'application ouverte jusqu'à la fin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    Text(e.bilan)
                    if (e.echecs.isNotEmpty()) {
                        Text(
                            "Détails :",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        e.echecs.take(30).forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (e.aSupprimerManuellement.isNotEmpty()) {
                        Text(
                            "À supprimer vous-même dans Google Files :",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        e.aSupprimerManuellement.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        confirmButton = {
            if (e.termine) {
                Row {
                    if (e.originauxRestants.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                        TextButton(onClick = {
                            val medias = e.originauxRestants.mapNotNull {
                                runCatching { MediaStore.getMediaUri(contexte, it) }.getOrNull()
                            }
                            if (medias.isEmpty()) {
                                Operations.modifierBilan {
                                    it.copy(
                                        bilan = it.bilan + "\nAndroid ne permet pas de les supprimer d'ici : supprimez-les dans Google Files.",
                                        originauxRestants = emptyList()
                                    )
                                }
                            } else {
                                val demande = MediaStore.createDeleteRequest(contexte.contentResolver, medias)
                                Session.suspendreVerrouAuto()
                                try {
                                    suppression.launch(IntentSenderRequest.Builder(demande.intentSender).build())
                                } catch (ex: Exception) {
                                    Session.reprendreVerrouAuto()
                                }
                            }
                        }) { Text("Supprimer les originaux") }
                    }
                    TextButton(onClick = { Operations.fermer() }) { Text("Fermer") }
                }
            }
        }
    )
}
