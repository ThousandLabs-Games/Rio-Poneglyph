package com.thousandlabs.rioponeglyph

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.thousandlabs.rioponeglyph.ui.RioRoot
import com.thousandlabs.rioponeglyph.ui.theme.RioTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliquerConfidentialite(this)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val verrou = (application as RioApp).verrou
        setContent {
            RioTheme {
                RioRoot(verrou)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appliquerConfidentialite(this)
    }

    companion object {
        /** Captures d'écran interdites et aperçu masqué dans les applications récentes, si activé. */
        fun appliquerConfidentialite(activite: Activity) {
            if (Reglages.confidentialite) {
                activite.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activite.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            if (Build.VERSION.SDK_INT >= 33) activite.setRecentsScreenshotEnabled(!Reglages.confidentialite)
        }
    }
}
