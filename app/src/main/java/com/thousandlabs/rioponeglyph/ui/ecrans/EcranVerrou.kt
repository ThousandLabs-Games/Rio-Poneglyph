package com.thousandlabs.rioponeglyph.ui.ecrans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.securite.Biometrie
import com.thousandlabs.rioponeglyph.securite.GestionVerrou
import com.thousandlabs.rioponeglyph.securite.MethodeVerrou
import com.thousandlabs.rioponeglyph.securite.ResultatVerification
import com.thousandlabs.rioponeglyph.ui.composants.EnTete
import com.thousandlabs.rioponeglyph.ui.composants.LigneMessage
import com.thousandlabs.rioponeglyph.ui.composants.SaisieCode
import com.thousandlabs.rioponeglyph.ui.composants.formaterDuree
import com.thousandlabs.rioponeglyph.ui.composants.libelleIncorrect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EcranVerrou(
    verrou: GestionVerrou,
    onSucces: () -> Unit,
    titre: String = "Rio Poneglyph",
    sousTitre: String? = null,
    autoriserBiometrie: Boolean = true,
    onRetour: (() -> Unit)? = null
) {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val methode = remember { verrou.methode }
    var message by remember { mutableStateOf<String?>(null) }
    var erreur by remember { mutableStateOf(false) }
    var enCours by remember { mutableStateOf(false) }
    var reinitialisation by remember { mutableIntStateOf(0) }
    var resteMs by remember { mutableLongStateOf(verrou.resteBlocageMs()) }
    val biometriePossible = remember {
        autoriserBiometrie && verrou.biometrieActive && Biometrie.disponible(contexte)
    }
    val bloque = resteMs > 0L
    val sousTitreEffectif = sousTitre ?: when (methode) {
        MethodeVerrou.PIN -> "Saisissez votre code PIN"
        MethodeVerrou.SCHEMA -> "Tracez votre schéma"
        MethodeVerrou.MOT_DE_PASSE -> "Saisissez votre mot de passe"
    }

    fun signalerErreur() {
        erreur = true
        portee.launch {
            delay(900)
            erreur = false
            reinitialisation++
        }
    }

    fun lancerBiometrie() {
        val activite = Biometrie.activite(contexte) ?: return
        if (verrou.resteBlocageMs() > 0L) return
        Biometrie.demander(
            activite,
            titre = "Déverrouiller Rio Poneglyph",
            sousTitre = "Posez votre doigt sur le capteur",
            onSucces = {
                verrou.reinitialiserEchecs()
                onSucces()
            },
            onErreur = { message = it }
        )
    }

    fun soumettre(secret: String) {
        if (enCours || bloque) return
        portee.launch {
            enCours = true
            val resultat = withContext(Dispatchers.Default) { verrou.verifier(secret) }
            enCours = false
            when (resultat) {
                ResultatVerification.Succes -> {
                    message = null
                    onSucces()
                }
                is ResultatVerification.Echec -> {
                    message = if (resultat.essaisAvantBlocage <= 1) {
                        libelleIncorrect(methode) + " Dernier essai avant blocage temporaire."
                    } else {
                        libelleIncorrect(methode) + " Encore ${resultat.essaisAvantBlocage} essais avant blocage temporaire."
                    }
                    signalerErreur()
                }
                is ResultatVerification.Bloque -> {
                    message = null
                    resteMs = resultat.resteMs
                    signalerErreur()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (biometriePossible && !bloque) lancerBiometrie()
    }
    LaunchedEffect(bloque) {
        while (resteMs > 0L) {
            delay(500)
            resteMs = verrou.resteBlocageMs()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        val defilement = if (methode == MethodeVerrou.MOT_DE_PASSE) {
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
            EnTete(titre, sousTitreEffectif)
            Spacer(Modifier.height(20.dp))
            if (bloque) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Trop de tentatives. Réessayez dans ${formaterDuree(resteMs)}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(20.dp))
            } else {
                LigneMessage(message, erreur = true)
            }
            SaisieCode(
                methode = methode,
                actif = !enCours && !bloque && !erreur,
                erreur = erreur,
                reinitialisation = reinitialisation,
                onSoumettre = { soumettre(it) },
                onSchemaTropCourt = {
                    message = "Schéma trop court."
                    signalerErreur()
                }
            )
            if (biometriePossible) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { lancerBiometrie() }, enabled = !bloque) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Utiliser l'empreinte")
                }
            }
        }
        if (onRetour != null) {
            IconButton(onClick = onRetour, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
            }
        }
    }
}
