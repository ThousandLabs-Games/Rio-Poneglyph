package com.thousandlabs.rioponeglyph.util

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Écriture sans risque de corruption : le contenu est d'abord écrit dans un fichier
 * temporaire, forcé sur le disque, puis substitué au fichier cible en une seule opération.
 */
object EcritureAtomique {

    fun ecrire(cible: File, donnees: ByteArray) {
        val dossier = cible.parentFile ?: throw IOException("Dossier cible introuvable")
        if (!dossier.exists() && !dossier.mkdirs()) throw IOException("Impossible de créer le dossier")
        val temporaire = File(dossier, cible.name + ".tmp")
        FileOutputStream(temporaire).use { flux ->
            flux.write(donnees)
            flux.flush()
            flux.fd.sync()
        }
        if (!temporaire.renameTo(cible)) {
            temporaire.delete()
            throw IOException("Échec de la validation de l'écriture")
        }
    }
}
