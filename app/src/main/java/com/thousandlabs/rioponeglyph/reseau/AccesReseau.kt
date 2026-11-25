package com.thousandlabs.rioponeglyph.reseau

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Seul point d'accès au réseau de l'application. Toute connexion est refusée
 * en dehors de la fenêtre de 2 minutes ouverte par l'utilisateur, et seulement
 * vers GitHub, en HTTPS.
 */
object AccesReseau {

    const val DUREE_MS = 120_000L
    private val HOTES_AUTORISES = setOf("api.github.com", "github.com")

    private val _fin = MutableStateFlow(0L)
    /** Fin de la fenêtre d'accès (horloge système depuis le démarrage). */
    val fin: StateFlow<Long> = _fin.asStateFlow()

    fun activer() {
        _fin.value = SystemClock.elapsedRealtime() + DUREE_MS
    }

    fun couper() {
        _fin.value = 0L
    }

    fun resteMs(): Long = (_fin.value - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    val actif: Boolean get() = resteMs() > 0L

    fun verifier() {
        if (!actif) throw IOException("L'accès Internet est désactivé.")
    }

    fun ouvrir(adresse: String): HttpsURLConnection {
        verifier()
        val url = URL(adresse)
        if (url.protocol != "https" || url.host !in HOTES_AUTORISES) throw IOException("Adresse non autorisée")
        return (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RioPoneglyph")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
    }
}
