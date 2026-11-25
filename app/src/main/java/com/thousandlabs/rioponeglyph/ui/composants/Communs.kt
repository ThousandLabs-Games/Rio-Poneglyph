package com.thousandlabs.rioponeglyph.ui.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.securite.MethodeVerrou

/** En-tête commun : cube, titre et sous-titre. */
@Composable
fun EnTete(titre: String, sousTitre: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CubePoneglyph(Modifier.size(92.dp))
        Spacer(Modifier.height(14.dp))
        Text(titre, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (sousTitre != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                sousTitre,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

/** Ligne de message (erreur ou information) de hauteur fixe pour éviter les sauts d'affichage. */
@Composable
fun LigneMessage(message: String?, erreur: Boolean) {
    Text(
        message ?: "",
        color = if (erreur) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .padding(horizontal = 24.dp)
    )
}

/** Zone de saisie adaptée à la méthode : clavier PIN, grille de schéma ou mot de passe. */
@Composable
fun SaisieCode(
    methode: MethodeVerrou,
    actif: Boolean,
    erreur: Boolean,
    reinitialisation: Int,
    onSoumettre: (String) -> Unit,
    onSchemaTropCourt: () -> Unit
) {
    var pin by remember(reinitialisation) { mutableStateOf("") }
    var motDePasse by remember(reinitialisation) { mutableStateOf("") }
    when (methode) {
        MethodeVerrou.PIN -> ClavierPin(
            valeur = pin,
            onChange = { pin = it },
            onValider = { onSoumettre(pin) },
            actif = actif,
            erreur = erreur
        )
        MethodeVerrou.SCHEMA -> GrilleSchema(
            actif = actif,
            erreur = erreur,
            reinitialisation = reinitialisation,
            onTermine = { points ->
                if (points.size < SCHEMA_MIN) onSchemaTropCourt() else onSoumettre(points.joinToString("-"))
            }
        )
        MethodeVerrou.MOT_DE_PASSE -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            ChampMotDePasse(
                valeur = motDePasse,
                onChange = { motDePasse = it },
                onValider = { onSoumettre(motDePasse) },
                actif = actif,
                erreur = erreur
            )
            Button(
                onClick = { onSoumettre(motDePasse) },
                enabled = actif && motDePasse.length >= MOT_DE_PASSE_MIN
            ) { Text("Valider") }
        }
    }
}

fun libelleIncorrect(methode: MethodeVerrou): String = when (methode) {
    MethodeVerrou.PIN -> "Code PIN incorrect."
    MethodeVerrou.SCHEMA -> "Schéma incorrect."
    MethodeVerrou.MOT_DE_PASSE -> "Mot de passe incorrect."
}

fun formaterDuree(ms: Long): String {
    val total = (ms + 999) / 1000
    val minutes = total / 60
    val secondes = total % 60
    return "%d:%02d".format(minutes, secondes)
}
