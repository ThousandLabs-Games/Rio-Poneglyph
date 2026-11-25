package com.thousandlabs.rioponeglyph.ui.ecrans

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.coffre.Operations
import com.thousandlabs.rioponeglyph.coffre.ReceptionWillOfD
import com.thousandlabs.rioponeglyph.coffre.ReceptionWillOfD.Etat
import com.thousandlabs.rioponeglyph.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranWillOfD(onRetour: () -> Unit) {
    val contexte = LocalContext.current
    val operation by Operations.etat.collectAsState()
    var actualisation by remember { mutableIntStateOf(0) }
    var etat by remember { mutableStateOf<Etat?>(null) }
    var choisis by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Nouvelle lecture du dépôt à l'ouverture, sur demande, et à la fin d'une récupération.
    val finOperation = operation?.termine == true || operation == null
    LaunchedEffect(actualisation, finOperation) {
        if (!finOperation) return@LaunchedEffect
        etat = null
        val lu = withContext(Dispatchers.IO) { ReceptionWillOfD.lister(contexte.applicationContext) }
        etat = lu
        choisis = (lu as? Etat.Disponible)?.depots?.map { it.id }?.toSet() ?: emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Depuis Will of D") },
                navigationIcon = {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    IconButton(onClick = { actualisation++ }) { Icon(Icons.Outlined.Refresh, contentDescription = "Actualiser") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Échange local à sens unique : les éléments sont chiffrés dans le coffre, vérifiés, puis retirés de Will of D. Rien n'est envoyé vers Will of D ni sur Internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (val e = etat) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                Etat.NonInstallee -> Message(
                    "Will of D n'est pas installée",
                    "Installez Will of D depuis le dépôt officiel de Thousand Labs Games, puis revenez ici."
                )

                Etat.SignatureDifferente -> Message(
                    "Accès refusé",
                    "Will of D et Rio Poneglyph ne portent pas la même signature. Installez les versions officielles des deux applications."
                )

                is Etat.Erreur -> Message("Lecture impossible", e.message)

                is Etat.Disponible -> {
                    if (e.depots.isEmpty()) {
                        Message("Rien à récupérer", "Les vidéos et chapitres téléchargés dans Will of D apparaîtront ici.")
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                "${e.depots.size} élément(s) · ${Formats.taille(e.depots.sumOf { it.taille })}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                choisis = if (choisis.size == e.depots.size) emptySet() else e.depots.map { it.id }.toSet()
                            }) { Text(if (choisis.size == e.depots.size) "Tout désélectionner" else "Tout sélectionner") }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(e.depots, key = { it.id }) { d ->
                                val coche = d.id in choisis
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { choisis = if (coche) choisis - d.id else choisis + d.id }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Checkbox(checked = coche, onCheckedChange = { choisis = if (coche) choisis - d.id else choisis + d.id })
                                    Icon(
                                        if (d.genre == ReceptionWillOfD.Genre.PAGES) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Movie,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(d.titre, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(
                                                d.serie.ifBlank { null },
                                                if (d.genre == ReceptionWillOfD.Genre.PAGES) "${d.fichiers} page(s)" else "Vidéo",
                                                Formats.taille(d.taille)
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Button(
                            enabled = choisis.isNotEmpty() && !Operations.enCours,
                            onClick = {
                                ReceptionWillOfD.recuperer(contexte.applicationContext, e.depots.filter { it.id in choisis })
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) { Text("Récupérer ${choisis.size} élément(s)") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(titre: String, texte: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Icon(
            Icons.Outlined.MoveToInbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(titre, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            texte,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
