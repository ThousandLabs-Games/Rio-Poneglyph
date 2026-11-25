package com.thousandlabs.rioponeglyph.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.Session
import com.thousandlabs.rioponeglyph.coffre.Coffre
import com.thousandlabs.rioponeglyph.coffre.Noeud
import com.thousandlabs.rioponeglyph.coffre.TypeNoeud
import com.thousandlabs.rioponeglyph.securite.GestionVerrou
import com.thousandlabs.rioponeglyph.ui.composants.DialogueOperation
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranAccueil
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranConfiguration
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranDossier
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranGardeFou
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranGuide
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranImage
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranLecteurPages
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranMisesAJour
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranNouveautes
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranLecteurVideo
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranOuverture
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranPlus
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranVerrou
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranWillOfD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface Ecran {
    data object Accueil : Ecran
    data object Plus : Ecran
    data object GardeFou : Ecran
    data object VerifierAvantModification : Ecran
    data object NouvelleMethode : Ecran
    data class Dossier(val id: String) : Ecran
    data class Video(val id: String) : Ecran
    data class Image(val id: String) : Ecran
    data class Pages(val id: String) : Ecran
    data object MisesAJour : Ecran
    data object Guide : Ecran
    data object Nouveautes : Ecran
    data object WillOfD : Ecran
}

@Composable
fun RioRoot(verrou: GestionVerrou) {
    val contexte = LocalContext.current
    val deverrouille by Session.deverrouille.collectAsState()
    var introTerminee by remember { mutableStateOf(Session.introJouee) }
    var configure by remember { mutableStateOf(verrou.estConfigure) }
    var coffrePret by remember { mutableStateOf(Coffre.estCharge) }
    var erreurCoffre by remember { mutableStateOf<String?>(null) }
    // La pile est conservée pendant le verrouillage : retour au même écran après déverrouillage.
    val pile = remember { mutableStateListOf<Ecran>(Ecran.Accueil) }

    fun retour() {
        if (pile.size > 1) pile.removeAt(pile.lastIndex)
    }

    fun ouvrir(n: Noeud) {
        when (n.type) {
            TypeNoeud.DOSSIER -> pile.add(Ecran.Dossier(n.id))
            TypeNoeud.VIDEO -> pile.add(Ecran.Video(n.id))
            TypeNoeud.IMAGE -> pile.add(Ecran.Image(n.id))
            TypeNoeud.CHAPITRE -> pile.add(Ecran.Pages(n.id))
            else -> Unit
        }
    }

    /** Passe au chapitre ou à l'épisode suivant sans empiler d'écran supplémentaire. */
    fun remplacer(n: Noeud) {
        if (pile.size > 1) pile.removeAt(pile.lastIndex)
        ouvrir(n)
    }

    LaunchedEffect(deverrouille) {
        if (deverrouille && !Coffre.estCharge) {
            withContext(Dispatchers.IO) { runCatching { Coffre.ouvrir(contexte.applicationContext) } }
                .onSuccess { coffrePret = true; erreurCoffre = null }
                .onFailure { erreurCoffre = it.message ?: "erreur inconnue" }
        } else if (Coffre.estCharge) {
            coffrePret = true
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !introTerminee -> EcranOuverture {
                Session.introJouee = true
                introTerminee = true
            }

            !configure -> EcranConfiguration(
                verrou = verrou,
                modification = false,
                onTermine = {
                    configure = true
                    Session.deverrouiller()
                },
                onRetour = null
            )

            !deverrouille -> EcranVerrou(verrou = verrou, onSucces = { Session.deverrouiller() })

            erreurCoffre != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Text("Le coffre n'a pas pu être ouvert", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aucun fichier n'a été modifié ni supprimé. Détail : $erreurCoffre",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            !coffrePret -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> {
                BackHandler(enabled = pile.size > 1) { retour() }
                when (val ecran = pile.last()) {
                    Ecran.Accueil -> EcranAccueil(
                        onPlus = { pile.add(Ecran.Plus) },
                        onVerrouiller = { Session.verrouiller() },
                        onOuvrir = { ouvrir(it) },
                        onWillOfD = { pile.add(Ecran.WillOfD) }
                    )

                    is Ecran.Dossier -> EcranDossier(
                        id = ecran.id,
                        onRetour = { retour() },
                        onOuvrir = { ouvrir(it) },
                        onPlus = { pile.add(Ecran.Plus) }
                    )

                    is Ecran.Video -> EcranLecteurVideo(id = ecran.id, onRetour = { retour() }, onSuivant = { remplacer(it) })

                    is Ecran.Image -> EcranImage(id = ecran.id, onRetour = { retour() })

                    is Ecran.Pages -> EcranLecteurPages(id = ecran.id, onRetour = { retour() }, onSuivant = { remplacer(it) })

                    Ecran.MisesAJour -> EcranMisesAJour(onRetour = { retour() })

                    Ecran.Guide -> EcranGuide(onRetour = { retour() })

                    Ecran.Nouveautes -> EcranNouveautes(onRetour = { retour() })

                    Ecran.WillOfD -> EcranWillOfD(onRetour = { retour() })

                    Ecran.Plus -> EcranPlus(
                        verrou = verrou,
                        onRetour = { retour() },
                        onModifierMethode = { pile.add(Ecran.VerifierAvantModification) },
                        onGardeFou = { pile.add(Ecran.GardeFou) },
                        onMisesAJour = { pile.add(Ecran.MisesAJour) },
                        onGuide = { pile.add(Ecran.Guide) },
                        onNouveautes = { pile.add(Ecran.Nouveautes) }
                    )

                    Ecran.GardeFou -> EcranGardeFou(onRetour = { retour() })

                    Ecran.VerifierAvantModification -> EcranVerrou(
                        verrou = verrou,
                        titre = "Confirmez votre identité",
                        sousTitre = "Saisissez votre code actuel avant de le changer",
                        autoriserBiometrie = false,
                        onSucces = {
                            retour()
                            pile.add(Ecran.NouvelleMethode)
                        },
                        onRetour = { retour() }
                    )

                    Ecran.NouvelleMethode -> EcranConfiguration(
                        verrou = verrou,
                        modification = true,
                        onTermine = { retour() },
                        onRetour = { retour() }
                    )
                }
                DialogueOperation()
            }
        }
    }
}
