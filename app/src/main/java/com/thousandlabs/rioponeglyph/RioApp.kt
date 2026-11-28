package com.thousandlabs.rioponeglyph

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.thousandlabs.rioponeglyph.securite.GestionVerrou

class RioApp : Application() {

    lateinit var verrou: GestionVerrou
        private set

    override fun onCreate() {
        super.onCreate()
        Reglages.init(this)
        verrou = GestionVerrou(this)
        // Verrouillage quand l'application quitte l'écran (immédiat ou différé selon les réglages).
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, evenement ->
                when (evenement) {
                    Lifecycle.Event.ON_STOP -> Session.surPassageArrierePlan()
                    Lifecycle.Event.ON_START -> Session.surRetourPremierPlan()
                    else -> Unit
                }
            }
        )
    }
}
