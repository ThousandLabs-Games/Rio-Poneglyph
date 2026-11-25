package com.thousandlabs.rioponeglyph.securite

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.thousandlabs.rioponeglyph.util.EcritureAtomique
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Clé de chiffrement du coffre (AES-256). Elle est elle-même chiffrée par une clé
 * maîtresse protégée par le module matériel du téléphone (Android Keystore).
 */
object CleCoffre {

    private const val ALIAS = "rio_poneglyph_cle_maitresse"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAILLE_IV = 12

    private fun fichier(context: Context) = File(context.filesDir, "coffre/cle.bin")

    /** Crée la clé du coffre si elle n'existe pas encore, sinon vérifie qu'elle est lisible. */
    @Synchronized
    fun initialiser(context: Context) {
        val f = fichier(context)
        if (f.exists()) {
            charger(context).fill(0)
            return
        }
        val cleCoffre = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val chiffreur = Cipher.getInstance(TRANSFORMATION)
            chiffreur.init(Cipher.ENCRYPT_MODE, cleMaitresse(creerSiAbsente = true))
            val chiffre = chiffreur.doFinal(cleCoffre)
            EcritureAtomique.ecrire(f, chiffreur.iv + chiffre)
        } finally {
            cleCoffre.fill(0)
        }
    }

    @Synchronized
    fun charger(context: Context): ByteArray {
        val donnees = fichier(context).readBytes()
        if (donnees.size <= TAILLE_IV) throw IOException("Clé du coffre illisible")
        val iv = donnees.copyOfRange(0, TAILLE_IV)
        val chiffre = donnees.copyOfRange(TAILLE_IV, donnees.size)
        val dechiffreur = Cipher.getInstance(TRANSFORMATION)
        dechiffreur.init(Cipher.DECRYPT_MODE, cleMaitresse(creerSiAbsente = false), GCMParameterSpec(128, iv))
        return dechiffreur.doFinal(chiffre)
    }

    private fun cleMaitresse(creerSiAbsente: Boolean): SecretKey {
        val magasin = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (magasin.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!creerSiAbsente) throw IOException("Clé maîtresse absente de ce téléphone")
        val generateur = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generateur.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generateur.generateKey()
    }
}
