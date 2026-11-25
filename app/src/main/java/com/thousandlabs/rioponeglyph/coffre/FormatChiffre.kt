package com.thousandlabs.rioponeglyph.coffre

import com.thousandlabs.rioponeglyph.util.EcritureAtomique
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Format des fichiers du coffre (.rpx) :
 * "RPX1" (4 octets) + nonce (16 octets) + contenu chiffré en AES-256-CTR.
 * Le mode CTR permet de lire n'importe quel passage (avance dans une vidéo)
 * sans jamais déchiffrer le fichier entier sur le stockage.
 */
object FormatChiffre {

    private val MAGIE = byteArrayOf(0x52, 0x50, 0x58, 0x31)
    const val TAILLE_ENTETE = 20
    private const val TAILLE_TAMPON = 1 shl 20
    private val alea = SecureRandom()

    class Resultat(val taille: Long, val empreinte: String)

    fun chiffreur(cle: ByteArray, nonce: ByteArray, position: Long, mode: Int): Cipher {
        val bloc = position / 16
        val iv = ajouterCompteur(nonce, bloc)
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(mode, SecretKeySpec(cle, "AES"), IvParameterSpec(iv))
        val reste = (position % 16).toInt()
        if (reste > 0) c.update(ByteArray(reste))
        return c
    }

    private fun ajouterCompteur(nonce: ByteArray, n: Long): ByteArray {
        val r = nonce.copyOf()
        var retenue = n
        var i = 15
        while (i >= 0 && retenue != 0L) {
            val somme = (r[i].toInt() and 0xFF) + (retenue and 0xFF).toInt()
            r[i] = somme.toByte()
            retenue = (retenue ushr 8) + (somme ushr 8)
            i--
        }
        return r
    }

    fun lireNonce(entete: ByteArray): ByteArray {
        if (entete.size < TAILLE_ENTETE) throw IOException("Fichier du coffre tronqué")
        for (i in 0 until 4) if (entete[i] != MAGIE[i]) throw IOException("Fichier du coffre non reconnu")
        return entete.copyOfRange(4, TAILLE_ENTETE)
    }

    fun lireNonce(raf: RandomAccessFile): ByteArray {
        raf.seek(0)
        val entete = ByteArray(TAILLE_ENTETE)
        raf.readFully(entete)
        return lireNonce(entete)
    }

    /** Chiffre [entree] dans [temporaire] (forcé sur le disque). Renvoie taille et empreinte du contenu clair. */
    fun chiffrerVersFichier(cle: ByteArray, entree: InputStream, temporaire: File, progression: (Long) -> Unit): Resultat {
        val nonce = ByteArray(16).also { alea.nextBytes(it) }
        val sha = MessageDigest.getInstance("SHA-256")
        val c = chiffreur(cle, nonce, 0, Cipher.ENCRYPT_MODE)
        val tampon = ByteArray(TAILLE_TAMPON)
        val sortie = ByteArray(TAILLE_TAMPON + 32)
        var total = 0L
        FileOutputStream(temporaire).use { flux ->
            flux.write(MAGIE)
            flux.write(nonce)
            while (true) {
                val n = entree.read(tampon)
                if (n < 0) break
                if (n == 0) continue
                sha.update(tampon, 0, n)
                val m = c.update(tampon, 0, n, sortie, 0)
                flux.write(sortie, 0, m)
                total += n
                progression(total)
            }
            val fin = c.doFinal()
            if (fin != null && fin.isNotEmpty()) flux.write(fin)
            flux.flush()
            flux.fd.sync()
        }
        return Resultat(total, hex(sha.digest()))
    }

    /** Relit un fichier du coffre en le déchiffrant en mémoire, pour en vérifier l'intégrité. */
    fun empreinteDechiffree(cle: ByteArray, fichier: File, progression: (Long) -> Unit = {}): Resultat {
        val sha = MessageDigest.getInstance("SHA-256")
        val tampon = ByteArray(TAILLE_TAMPON)
        var total = 0L
        FluxDechiffre(cle, fichier).use { flux ->
            while (true) {
                val n = flux.read(tampon, 0, tampon.size)
                if (n < 0) break
                sha.update(tampon, 0, n)
                total += n
                progression(total)
            }
        }
        return Resultat(total, hex(sha.digest()))
    }

    fun chiffrerOctets(cle: ByteArray, donnees: ByteArray, cible: File) {
        val nonce = ByteArray(16).also { alea.nextBytes(it) }
        val c = chiffreur(cle, nonce, 0, Cipher.ENCRYPT_MODE)
        val chiffre = c.doFinal(donnees)
        EcritureAtomique.ecrire(cible, MAGIE + nonce + chiffre)
    }

    fun dechiffrerOctets(cle: ByteArray, fichier: File): ByteArray =
        FluxDechiffre(cle, fichier).use { it.readBytes() }

    fun empreinteFlux(entree: InputStream): Resultat {
        val sha = MessageDigest.getInstance("SHA-256")
        val tampon = ByteArray(TAILLE_TAMPON)
        var total = 0L
        while (true) {
            val n = entree.read(tampon)
            if (n < 0) break
            sha.update(tampon, 0, n)
            total += n
        }
        return Resultat(total, hex(sha.digest()))
    }

    fun hex(octets: ByteArray): String {
        val sb = StringBuilder(octets.size * 2)
        for (b in octets) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4]).append("0123456789abcdef"[v and 0x0F])
        }
        return sb.toString()
    }
}

/** Lecture séquentielle déchiffrée d'un fichier du coffre (export, vérification, miniatures). */
class FluxDechiffre(cle: ByteArray, fichier: File) : InputStream() {

    private val flux = BufferedInputStream(FileInputStream(fichier), 1 shl 16)
    private val chiffreur: Cipher
    private val entree = ByteArray(1 shl 16)

    init {
        val entete = ByteArray(FormatChiffre.TAILLE_ENTETE)
        var lu = 0
        while (lu < entete.size) {
            val n = flux.read(entete, lu, entete.size - lu)
            if (n < 0) {
                flux.close()
                throw IOException("Fichier du coffre tronqué")
            }
            lu += n
        }
        chiffreur = FormatChiffre.chiffreur(cle, FormatChiffre.lireNonce(entete), 0, Cipher.DECRYPT_MODE)
    }

    override fun read(): Int {
        val un = ByteArray(1)
        while (true) {
            val n = read(un, 0, 1)
            if (n < 0) return -1
            if (n == 1) return un[0].toInt() and 0xFF
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val n = flux.read(entree, 0, minOf(len, entree.size))
        if (n < 0) return -1
        return chiffreur.update(entree, 0, n, b, off)
    }

    override fun close() {
        flux.close()
    }
}
