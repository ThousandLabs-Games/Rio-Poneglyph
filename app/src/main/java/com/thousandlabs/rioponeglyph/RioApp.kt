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
        verrou = GestionVerrou(this)
        // Verrouillage dès que l'application quitte l'écran.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, evenement ->
                if (evenement == Lifecycle.Event.ON_STOP) Session.surPassageArrierePlan()
            }
        )
    }
}
