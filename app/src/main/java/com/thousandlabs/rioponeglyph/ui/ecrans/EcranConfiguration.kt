package com.thousandlabs.rioponeglyph.ui.ecrans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Pattern
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.securite.Biometrie
import com.thousandlabs.rioponeglyph.securite.CleCoffre
import com.thousandlabs.rioponeglyph.securite.GestionVerrou
import com.thousandlabs.rioponeglyph.securite.MethodeVerrou
import com.thousandlabs.rioponeglyph.ui.composants.EnTete
import com.thousandlabs.rioponeglyph.ui.composants.LigneMessage
import com.thousandlabs.rioponeglyph.ui.composants.SaisieCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Etape { CHOIX, SAISIE, CONFIRMATION, EMPREINTE }

/**
 * Création (premier lancement) ou remplacement de la méthode de déverrouillage.
 */
@Composable
fun EcranConfiguration(
    verrou: GestionVerrou,
    modification: Boolean,
    onTermine: () -> Unit,
    onRetour: (() -> Unit)?
) {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    var etape by remember { mutableStateOf(Etape.CHOIX) }
    var methode by remember { mutableStateOf(MethodeVerrou.PIN) }
    var premiereSaisie by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var erreur by remember { mutableStateOf(false) }
    var enCours by remember { mutableStateOf(false) }
    var reinitialisation by remember { mutableIntStateOf(0) }

    fun allerA(nouvelle: Etape) {
        etape = nouvelle
        reinitialisation++
    }

    fun signalerErreur(texte: String) {
        message = texte
        erreur = true
        portee.launch {
            delay(900)
            erreur = false
            reinitialisation++
        }
    }

    fun enregistrer(secret: String) {
        portee.launch {
            enCours = true
            val resultat = withContext(Dispatchers.IO) {
                runCatching {
                    // La clé du coffre est préparée avant d'enregistrer le code :
                    // ainsi, aucun code n'est actif sans coffre valide.
                    CleCoffre.initialiser(contexte.applicationContext)
                    verrou.definir(methode, secret)
                }
            }
            enCours = false
            resultat.onSuccess {
                if (!verrou.biometrieActive && Biometrie.disponible(contexte)) allerA(Etape.EMPREINTE) else onTermine()
            }.onFailure {
                premiereSaisie = ""
                allerA(Etape.SAISIE)
                signalerErreur("Impossible de préparer le coffre chiffré. Veuillez réessayer.")
            }
        }
    }

    BackHandler(enabled = etape != Etape.CHOIX || onRetour != null) {
        when (etape) {
            Etape.CHOIX -> onRetour?.invoke()
            Etape.SAISIE -> { message = null; allerA(Etape.CHOIX) }
            Etape.CONFIRMATION -> { message = null; premiereSaisie = ""; allerA(Etape.SAISIE) }
            Etape.EMPREINTE -> onTermine()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        val defilement = if (etape == Etape.CHOIX || etape == Etape.EMPREINTE || methode == MethodeVerrou.MOT_DE_PASSE) {
            Modifier.verticalScroll(rememberScrollState())
        } else Modifier
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .then(defilement)
                .padding(vertical = 24.dp)
        ) {
            when (etape) {
                Etape.CHOIX -> ChoixMethode(modification) { choisie ->
                    methode = choisie
                    message = null
                    premiereSaisie = ""
                    allerA(Etape.SAISIE)
                }

                Etape.SAISIE, Etape.CONFIRMATION -> {
                    val confirmation = etape == Etape.CONFIRMATION
                    val titre = when (methode) {
                        MethodeVerrou.PIN -> if (confirmation) "Confirmez votre code PIN" else "Choisissez votre code PIN"
                        MethodeVerrou.SCHEMA -> if (confirmation) "Confirmez votre schéma" else "Tracez votre schéma"
                        MethodeVerrou.MOT_DE_PASSE -> if (confirmation) "Confirmez votre mot de passe" else "Choisissez votre mot de passe"
                    }
                    val consigne = when (methode) {
                        MethodeVerrou.PIN -> "Entre 4 et 8 chiffres"
                        MethodeVerrou.SCHEMA -> "Reliez au moins 4 points"
                        MethodeVerrou.MOT_DE_PASSE -> "Au moins 4 caractères"
                    }
                    EnTete(titre, if (confirmation) "Saisissez-le une seconde fois" else consigne)
                    Spacer(Modifier.height(20.dp))
                    LigneMessage(message, erreur = true)
                    if (enCours) {
                        CircularProgressIndicator(Modifier.padding(32.dp))
                    } else {
                        SaisieCode(
                            methode = methode,
                            actif = !erreur,
                            erreur = erreur,
                            reinitialisation = reinitialisation,
                            onSoumettre = { secret ->
                                if (!confirmation) {
                                    premiereSaisie = secret
                                    message = null
                                    allerA(Etape.CONFIRMATION)
                                } else if (secret == premiereSaisie) {
                                    message = null
                                    enregistrer(secret)
                                } else {
                                    premiereSaisie = ""
                                    allerA(Etape.SAISIE)
                                    signalerErreur("Les deux saisies ne correspondent pas. Recommencez.")
                                }
                            },
                            onSchemaTropCourt = { signalerErreur("Reliez au moins 4 points.") }
                        )
                    }
                }

                Etape.EMPREINTE -> {
                    Icon(
                        Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("Déverrouillage par empreinte", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Souhaitez-vous ouvrir Rio Poneglyph avec votre empreinte digitale ? " +
                            "Votre code restera toujours utilisable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    LigneMessage(message, erreur = true)
                    Button(onClick = {
                        val activite = Biometrie.activite(contexte) ?: return@Button
                        Biometrie.demander(
                            activite,
                            titre = "Activer l'empreinte",
                            sousTitre = "Posez votre doigt sur le capteur",
                            onSucces = {
                                verrou.biometrieActive = true
                                onTermine()
                            },
                            onErreur = { message = it }
                        )
                    }) { Text("Activer l'empreinte") }
                    TextButton(onClick = onTermine) { Text("Plus tard") }
                }
            }
        }
        if (onRetour != null || etape != Etape.CHOIX && etape != Etape.EMPREINTE) {
            IconButton(
                onClick = {
                    when (etape) {
                        Etape.CHOIX -> onRetour?.invoke()
                        Etape.SAISIE -> { message = null; allerA(Etape.CHOIX) }
                        Etape.CONFIRMATION -> { message = null; premiereSaisie = ""; allerA(Etape.SAISIE) }
                        Etape.EMPREINTE -> onTermine()
                    }
                },
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
            }
        }
    }
}

@Composable
private fun ChoixMethode(modification: Boolean, onChoix: (MethodeVerrou) -> Unit) {
    EnTete(
        if (modification) "Nouvelle méthode" else "Protégez votre coffre",
        "Choisissez comment déverrouiller Rio Poneglyph"
    )
    Spacer(Modifier.height(28.dp))
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        CarteMethode(Icons.Outlined.Dialpad, "Code PIN", "Entre 4 et 8 chiffres") { onChoix(MethodeVerrou.PIN) }
        CarteMethode(Icons.Outlined.Pattern, "Schéma", "Reliez au moins 4 points") { onChoix(MethodeVerrou.SCHEMA) }
        CarteMethode(Icons.Outlined.Password, "Mot de passe", "Au moins 4 caractères") { onChoix(MethodeVerrou.MOT_DE_PASSE) }
    }
    Spacer(Modifier.height(24.dp))
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(horizontal = 28.dp)
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Retenez bien ce code : il n'existe aucun moyen de le récupérer, " +
                "et sans lui l'accès au coffre est impossible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CarteMethode(icone: ImageVector, titre: String, detail: String, onClic: () -> Unit) {
    Card(
        onClick = onClic,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp)
        ) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(titre, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
