package com.thousandlabs.rioponeglyph.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thousandlabs.rioponeglyph.Session
import com.thousandlabs.rioponeglyph.securite.GestionVerrou
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranAccueil
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranConfiguration
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranOuverture
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranPlus
import com.thousandlabs.rioponeglyph.ui.ecrans.EcranVerrou

private sealed interface Ecran {
    data object Accueil : Ecran
    data object Plus : Ecran
    data object VerifierAvantModification : Ecran
    data object NouvelleMethode : Ecran
}

@Composable
fun RioRoot(verrou: GestionVerrou) {
    val deverrouille by Session.deverrouille.collectAsState()
    var introTerminee by remember { mutableStateOf(Session.introJouee) }
    var configure by remember { mutableStateOf(verrou.estConfigure) }
    // La pile est conservée pendant le verrouillage : retour au même écran après déverrouillage.
    val pile = remember { mutableStateListOf<Ecran>(Ecran.Accueil) }

    fun retour() {
        if (pile.size > 1) pile.removeAt(pile.lastIndex)
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

            else -> {
                BackHandler(enabled = pile.size > 1) { retour() }
                when (pile.last()) {
                    Ecran.Accueil -> EcranAccueil(
                        onPlus = { pile.add(Ecran.Plus) },
                        onVerrouiller = { Session.verrouiller() }
                    )

                    Ecran.Plus -> EcranPlus(
                        verrou = verrou,
                        onRetour = { retour() },
                        onModifierMethode = { pile.add(Ecran.VerifierAvantModification) }
                    )

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
            }
        }
    }
}
