package com.thousandlabs.rioponeglyph.coffre

import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EtatOperation(
    val titre: String,
    val detail: String = "",
    val progression: Float? = null,
    val termine: Boolean = false,
    val bilan: String = "",
    val echecs: List<String> = emptyList(),
    /** Originaux que l'application n'a pas pu supprimer directement (suppression via Android). */
    val originauxRestants: List<Uri> = emptyList(),
    /** Originaux à supprimer à la main (dossiers notamment). */
    val aSupprimerManuellement: List<String> = emptyList()
)

/**
 * Opérations longues (importation, exportation, copie, vérification) : une seule à la fois,
 * exécutée hors de l'interface pour ne pas être interrompue par un changement d'écran.
 */
object Operations {

    private val _etat = MutableStateFlow<EtatOperation?>(null)
    val etat: StateFlow<EtatOperation?> = _etat.asStateFlow()
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val enCours: Boolean get() = _etat.value?.termine == false

    fun lancer(titre: String, travail: suspend (Suivi) -> EtatOperation): Boolean {
        if (enCours) return false
        _etat.value = EtatOperation(titre, detail = "Préparation…")
        portee.launch {
            val fin = try {
                travail(Suivi(titre))
            } catch (e: Throwable) {
                EtatOperation(titre, bilan = "Opération interrompue : ${e.message ?: e.javaClass.simpleName}")
            }
            _etat.value = fin.copy(termine = true)
        }
        return true
    }

    fun modifierBilan(transformation: (EtatOperation) -> EtatOperation) {
        _etat.value?.let { if (it.termine) _etat.value = transformation(it) }
    }

    fun fermer() {
        if (!enCours) _etat.value = null
    }

    /** Mise à jour de l'avancement, limitée à une dizaine par seconde. */
    class Suivi(private val titre: String) {
        private var derniere = 0L

        fun maj(detail: String, progression: Float?, forcer: Boolean = false) {
            val maintenant = SystemClock.elapsedRealtime()
            if (!forcer && maintenant - derniere < 120) return
            derniere = maintenant
            _etat.value = EtatOperation(titre, detail, progression?.coerceIn(0f, 1f))
        }
    }
}
