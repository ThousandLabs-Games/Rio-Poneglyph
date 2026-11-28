package com.thousandlabs.rioponeglyph

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** État de la session en mémoire uniquement : rien n'est conservé après fermeture. */
object Session {

    private val _deverrouille = MutableStateFlow(false)
    val deverrouille: StateFlow<Boolean> = _deverrouille.asStateFlow()

    @Volatile
    var introJouee: Boolean = false

    /** Permet de suspendre le verrouillage automatique (sélecteur de fichiers système, etc.). */
    private val suspensions = AtomicInteger(0)

    fun deverrouiller() {
        _deverrouille.value = true
    }

    fun verrouiller() {
        _deverrouille.value = false
    }

    fun suspendreVerrouAuto() {
        suspensions.incrementAndGet()
    }

    fun reprendreVerrouAuto() {
        suspensions.updateAndGet { if (it > 0) it - 1 else 0 }
    }

    /** Moment du passage en arrière-plan (0 si aucun verrouillage différé n'est en attente). */
    @Volatile
    private var arrierePlanDepuis = 0L

    fun surPassageArrierePlan() {
        if (suspensions.get() != 0 || !Reglages.verrouArrierePlan) return
        if (Reglages.delaiVerrou.ms == 0L) verrouiller()
        else arrierePlanDepuis = SystemClock.elapsedRealtime()
    }

    fun surRetourPremierPlan() {
        val depuis = arrierePlanDepuis
        arrierePlanDepuis = 0L
        if (depuis == 0L || !Reglages.verrouArrierePlan) return
        if (SystemClock.elapsedRealtime() - depuis >= Reglages.delaiVerrou.ms) verrouiller()
    }
}
