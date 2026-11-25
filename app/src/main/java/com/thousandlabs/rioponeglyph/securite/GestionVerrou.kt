package com.thousandlabs.rioponeglyph.securite

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class MethodeVerrou(val libelle: String) {
    PIN("Code PIN"),
    SCHEMA("Schéma"),
    MOT_DE_PASSE("Mot de passe")
}

sealed interface ResultatVerification {
    data object Succes : ResultatVerification
    data class Echec(val essaisAvantBlocage: Int) : ResultatVerification
    data class Bloque(val resteMs: Long) : ResultatVerification
}

/**
 * Gestion du code de déverrouillage. Seule une empreinte dérivée (PBKDF2) est conservée,
 * jamais le code lui-même. Blocage temporaire croissant à partir de 5 échecs.
 */
class GestionVerrou(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("verrou", Context.MODE_PRIVATE)

    val estConfigure: Boolean
        get() = prefs.contains(K_EMPREINTE_CODE)

    val methode: MethodeVerrou
        get() = runCatching { MethodeVerrou.valueOf(prefs.getString(K_METHODE, null) ?: "PIN") }
            .getOrDefault(MethodeVerrou.PIN)

    var biometrieActive: Boolean
        get() = prefs.getBoolean(K_BIOMETRIE, false)
        set(valeur) {
            prefs.edit().putBoolean(K_BIOMETRIE, valeur).commit()
        }

    @Synchronized
    fun definir(methode: MethodeVerrou, secret: String) {
        val sel = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val empreinte = deriver(secret, sel, ITERATIONS)
        prefs.edit()
            .putString(K_METHODE, methode.name)
            .putString(K_SEL, encoder(sel))
            .putString(K_EMPREINTE_CODE, encoder(empreinte))
            .putInt(K_ITERATIONS, ITERATIONS)
            .putInt(K_ECHECS, 0)
            .putLong(K_FIN_BLOCAGE, 0L)
            .commit()
    }

    @Synchronized
    fun resteBlocageMs(): Long {
        val fin = prefs.getLong(K_FIN_BLOCAGE, 0L)
        val maintenant = System.currentTimeMillis()
        if (fin <= maintenant) return 0L
        val reste = fin - maintenant
        // Protection contre un changement de l'horloge du téléphone.
        if (reste > DUREES_BLOCAGE.last()) {
            prefs.edit().putLong(K_FIN_BLOCAGE, maintenant + DUREES_BLOCAGE.last()).commit()
            return DUREES_BLOCAGE.last()
        }
        return reste
    }

    @Synchronized
    fun verifier(secret: String): ResultatVerification {
        val reste = resteBlocageMs()
        if (reste > 0L) return ResultatVerification.Bloque(reste)

        val sel = decoder(prefs.getString(K_SEL, null)) ?: return ResultatVerification.Echec(SEUIL)
        val attendu = decoder(prefs.getString(K_EMPREINTE_CODE, null)) ?: return ResultatVerification.Echec(SEUIL)
        val iterations = prefs.getInt(K_ITERATIONS, ITERATIONS)

        if (MessageDigest.isEqual(deriver(secret, sel, iterations), attendu)) {
            reinitialiserEchecs()
            return ResultatVerification.Succes
        }

        val echecs = prefs.getInt(K_ECHECS, 0) + 1
        val maintenant = System.currentTimeMillis()
        val fin = if (echecs >= SEUIL) {
            maintenant + DUREES_BLOCAGE[minOf(echecs - SEUIL, DUREES_BLOCAGE.lastIndex)]
        } else 0L
        prefs.edit().putInt(K_ECHECS, echecs).putLong(K_FIN_BLOCAGE, fin).commit()

        return if (fin > 0L) ResultatVerification.Bloque(fin - maintenant)
        else ResultatVerification.Echec(SEUIL - echecs)
    }

    @Synchronized
    fun reinitialiserEchecs() {
        prefs.edit().putInt(K_ECHECS, 0).putLong(K_FIN_BLOCAGE, 0L).commit()
    }

    private fun deriver(secret: String, sel: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), sel, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encoder(octets: ByteArray): String = Base64.encodeToString(octets, Base64.NO_WRAP)

    private fun decoder(texte: String?): ByteArray? =
        texte?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }

    companion object {
        const val SEUIL = 5
        private const val ITERATIONS = 60_000
        private val DUREES_BLOCAGE = longArrayOf(
            30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L, 30 * 60_000L, 60 * 60_000L
        )
        private const val K_METHODE = "methode"
        private const val K_SEL = "sel"
        private const val K_EMPREINTE_CODE = "empreinte_code"
        private const val K_ITERATIONS = "iterations"
        private const val K_ECHECS = "echecs"
        private const val K_FIN_BLOCAGE = "fin_blocage"
        private const val K_BIOMETRIE = "biometrie"
    }
}
