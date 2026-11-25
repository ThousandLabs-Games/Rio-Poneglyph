package com.thousandlabs.rioponeglyph.lecteur

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.thousandlabs.rioponeglyph.coffre.FormatChiffre
import java.io.File
import java.io.RandomAccessFile
import javax.crypto.Cipher

/**
 * Source de lecture pour le lecteur vidéo : déchiffre à la volée, en mémoire,
 * uniquement le passage demandé. Rien n'est écrit en clair sur le stockage.
 */
@UnstableApi
class SourceChiffree(private val cle: ByteArray) : BaseDataSource(false) {

    private var fichier: RandomAccessFile? = null
    private var dechiffreur: Cipher? = null
    private var uri: Uri? = null
    private var restant = 0L
    private var tampon = ByteArray(0)
    private var ouvert = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val chemin = dataSpec.uri.path ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        val raf = RandomAccessFile(File(chemin), "r")
        try {
            val nonce = FormatChiffre.lireNonce(raf)
            val tailleClaire = raf.length() - FormatChiffre.TAILLE_ENTETE
            if (dataSpec.position > tailleClaire) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            raf.seek(FormatChiffre.TAILLE_ENTETE + dataSpec.position)
            dechiffreur = FormatChiffre.chiffreur(cle, nonce, dataSpec.position, Cipher.DECRYPT_MODE)
            restant = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                minOf(dataSpec.length, tailleClaire - dataSpec.position)
            } else {
                tailleClaire - dataSpec.position
            }
        } catch (e: Exception) {
            raf.close()
            throw e
        }
        fichier = raf
        ouvert = true
        transferStarted(dataSpec)
        return restant
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (restant <= 0L) return C.RESULT_END_OF_INPUT
        val aLire = minOf(length.toLong(), restant).toInt()
        if (tampon.size < aLire) tampon = ByteArray(aLire)
        val n = fichier?.read(tampon, 0, aLire) ?: return C.RESULT_END_OF_INPUT
        if (n <= 0) return C.RESULT_END_OF_INPUT
        val m = dechiffreur?.update(tampon, 0, n, buffer, offset) ?: return C.RESULT_END_OF_INPUT
        restant -= m
        bytesTransferred(m)
        return m
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            fichier?.close()
        } finally {
            fichier = null
            dechiffreur = null
            if (ouvert) {
                ouvert = false
                transferEnded()
            }
        }
    }
}
