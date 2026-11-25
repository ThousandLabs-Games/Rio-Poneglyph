package com.thousandlabs.rioponeglyph.coffre

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Importation par déplacement : chiffrement, relecture de contrôle, enregistrement,
 * et seulement ensuite suppression de l'original dans l'espace partagé.
 */
object Importation {

    private val EXT_VIDEO = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "3gp", "flv", "wmv", "mpg", "mpeg")
    private val EXT_IMAGE = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
    private const val MARGE_ESPACE = 150L * 1024 * 1024
    const val NOM_MANIFESTE = "poneglyph.json"

    internal class Source(val uri: Uri, val nom: String, val taille: Long, val mime: String)

    internal class Objet(val id: String, val taille: Long, val empreinte: String)

    // ------------------------------------------------------------------ Fichiers

    fun importerFichiers(context: Context, uris: List<Uri>, destination: String): Boolean =
        Operations.lancer("Importation") { suivi ->
            val resolver = context.contentResolver
            val sources = uris.map { infos(resolver, it) }
            val total = sources.sumOf { maxOf(0L, it.taille) }.coerceAtLeast(1L)
            var faits = 0L
            var importes = 0
            val echecs = ArrayList<String>()
            val restants = ArrayList<Uri>()

            sources.forEachIndexed { i, s ->
                val prefixe = "Fichier ${i + 1} sur ${sources.size} : ${s.nom}"
                suivi.maj(prefixe, faits.toFloat() / total, forcer = true)
                val crees = ArrayList<File>()
                try {
                    verifierEspace(context, s.taille)
                    val (base, ext) = separer(s.nom)
                    val type = typeDe(s.mime, ext)
                    val objet = chiffrerSource(context, s, crees) { n, etape ->
                        suivi.maj("$prefixe\n$etape", (faits + n).toFloat() / total)
                    }
                    var miniature: String? = null
                    var duree = 0L
                    when (type) {
                        TypeNoeud.VIDEO -> runCatching {
                            val r = MediaMetadataRetriever()
                            try {
                                r.setDataSource(context, s.uri)
                                duree = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                                val instant = if (duree > 0) minOf(duree * 100, 60_000_000L) else 1_000_000L
                                r.getScaledFrameAtTime(instant, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320)
                            } finally {
                                r.release()
                            }
                        }.getOrNull()?.let { miniature = enregistrerMiniature(it, crees) }
                        TypeNoeud.IMAGE -> runCatching { decoderReduit(resolver, s.uri, 320) }.getOrNull()
                            ?.let { miniature = enregistrerMiniature(it, crees) }
                        else -> Unit
                    }
                    Coffre.ajouter(
                        listOf(
                            Noeud(
                                id = objet.id, parent = destination, nom = base, type = type, extension = ext,
                                mime = s.mime, taille = objet.taille, empreinte = objet.empreinte,
                                miniature = miniature, duree = duree
                            )
                        )
                    )
                    importes++
                    // L'original n'est supprimé qu'une fois le fichier chiffré vérifié et enregistré.
                    if (!supprimerDocument(resolver, s.uri)) restants.add(s.uri)
                } catch (e: Exception) {
                    crees.forEach { it.delete() }
                    echecs.add("${s.nom} : ${e.message ?: "erreur inconnue"}")
                }
                faits += maxOf(0L, s.taille)
            }
            EtatOperation(
                titre = "Importation",
                bilan = bilan(importes, "fichier importé", "fichiers importés", echecs.size, restants.size),
                echecs = echecs,
                originauxRestants = restants
            )
        }

    // ------------------------------------------------------------------ Dossiers de pages

    private class Doc(val id: String, val nom: String, val mime: String, val taille: Long)

    /**
     * Importe un dossier de pages. Les images du dossier forment un chapitre ;
     * chaque sous-dossier contenant des images devient aussi un chapitre.
     */
    fun importerDossier(context: Context, arbre: Uri, destination: String): Boolean =
        Operations.lancer("Importation de pages") { suivi ->
            val resolver = context.contentResolver
            val racineId = DocumentsContract.getTreeDocumentId(arbre)
            val racineUri = DocumentsContract.buildDocumentUriUsingTree(arbre, racineId)
            val nomRacine = nomDocument(resolver, racineUri) ?: "Chapitre"
            val enfants = lister(resolver, arbre, racineId)

            val chapitres = ArrayList<Pair<String, List<Doc>>>()
            val manifestes = HashMap<String, JSONObject?>()
            fun ajouterChapitre(titreParDefaut: String, contenu: List<Doc>) {
                val images = contenu.filter { estImage(it) }
                if (images.isEmpty()) return
                val manifeste = contenu.firstOrNull { it.nom.equals(NOM_MANIFESTE, ignoreCase = true) }
                    ?.let { m -> runCatching { lireManifeste(resolver, arbre, m) }.getOrNull() }
                val titre = manifeste?.optString("titre")?.takeIf { it.isNotBlank() } ?: titreParDefaut
                chapitres.add(titre to ordonner(images, manifeste))
                manifestes[titre + "#" + chapitres.size] = manifeste
            }
            ajouterChapitre(nomRacine, enfants)
            enfants.filter { it.mime == DocumentsContract.Document.MIME_TYPE_DIR }
                .sortedWith(compareBy(TriNaturel) { it.nom })
                .forEach { sd -> ajouterChapitre(sd.nom, lister(resolver, arbre, sd.id)) }

            if (chapitres.isEmpty()) {
                return@lancer EtatOperation("Importation de pages", bilan = "Aucune image trouvée dans ce dossier.")
            }
            val totalPages = chapitres.sumOf { it.second.size }
            val totalOctets = chapitres.sumOf { c -> c.second.sumOf { maxOf(0L, it.taille) } }
            verifierEspace(context, totalOctets)

            // Plusieurs chapitres : ils sont regroupés dans un dossier au nom du dossier choisi.
            val parent = if (chapitres.size > 1) Coffre.creerDossier(destination, nomRacine).id else destination
            var pagesFaites = 0
            var chapitresImportes = 0
            val echecs = ArrayList<String>()

            chapitres.forEachIndexed { ci, (titre, images) ->
                val crees = ArrayList<File>()
                try {
                    val pages = images.mapIndexed { pi, doc ->
                        suivi.maj(
                            "Chapitre ${ci + 1} sur ${chapitres.size} : $titre\nPage ${pi + 1} sur ${images.size}",
                            pagesFaites.toFloat() / totalPages
                        )
                        val uri = DocumentsContract.buildDocumentUriUsingTree(arbre, doc.id)
                        val objet = chiffrerSource(context, Source(uri, doc.nom, doc.taille, doc.mime), crees) { _, _ -> }
                        pagesFaites++
                        Page(objet.id, doc.nom, objet.taille, objet.empreinte)
                    }
                    val premiere = images.first()
                    val miniature = runCatching {
                        decoderReduit(resolver, DocumentsContract.buildDocumentUriUsingTree(arbre, premiere.id), 320)
                    }.getOrNull()?.let { enregistrerMiniature(it, crees) }
                    val sens = manifestes.entries.firstOrNull { it.key == titre + "#" + (ci + 1) }?.value
                        ?.optString("sens").orEmpty()
                    Coffre.ajouter(
                        listOf(
                            Noeud(
                                id = Coffre.nouvelId(), parent = parent, nom = titre, type = TypeNoeud.CHAPITRE,
                                taille = pages.sumOf { it.taille }, miniature = miniature, pages = pages, sens = sens
                            )
                        )
                    )
                    chapitresImportes++
                } catch (e: Exception) {
                    crees.forEach { it.delete() }
                    echecs.add("$titre : ${e.message ?: "erreur inconnue"}")
                }
            }

            val manuel = ArrayList<String>()
            if (echecs.isEmpty()) {
                if (!supprimerDocument(resolver, racineUri)) manuel.add(nomRacine)
            } else if (chapitresImportes > 0) {
                manuel.add("$nomRacine (conservé car l'importation n'est pas complète)")
            }
            EtatOperation(
                titre = "Importation de pages",
                bilan = bilan(chapitresImportes, "chapitre importé", "chapitres importés", echecs.size, manuel.size),
                echecs = echecs,
                aSupprimerManuellement = manuel
            )
        }

    // ------------------------------------------------------------------ Outils

    internal fun chiffrerSource(
        context: Context,
        s: Source,
        crees: MutableList<File>,
        avancement: (Long, String) -> Unit
    ): Objet {
        val id = Coffre.nouvelId()
        val final = Coffre.fichierObjet(id)
        val temporaire = File(final.path + ".part")
        crees.add(temporaire)
        val cle = Coffre.cle()
        val resultat = context.contentResolver.openInputStream(s.uri)?.use { entree ->
            FormatChiffre.chiffrerVersFichier(cle, entree, temporaire) { n -> avancement(n / 2, "Chiffrement…") }
        } ?: throw IOException("fichier source inaccessible")
        if (s.taille >= 0 && resultat.taille != s.taille) throw IOException("copie incomplète")
        val controle = FormatChiffre.empreinteDechiffree(cle, temporaire) { n ->
            avancement(resultat.taille / 2 + n / 2, "Vérification de l'intégrité…")
        }
        if (controle.taille != resultat.taille || controle.empreinte != resultat.empreinte) {
            throw IOException("échec de la vérification d'intégrité")
        }
        if (!temporaire.renameTo(final)) throw IOException("validation impossible")
        crees.remove(temporaire)
        crees.add(final)
        return Objet(id, resultat.taille, resultat.empreinte)
    }

    internal fun enregistrerMiniature(image: Bitmap, crees: MutableList<File>): String {
        val sortie = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 82, sortie)
        image.recycle()
        val id = Coffre.nouvelId()
        val f = Coffre.fichierObjet(id)
        FormatChiffre.chiffrerOctets(Coffre.cle(), sortie.toByteArray(), f)
        crees.add(f)
        return id
    }

    internal fun decoderReduit(resolver: ContentResolver, uri: Uri, cote: Int): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        var echantillon = 1
        while (minOf(limites.outWidth, limites.outHeight) / (echantillon * 2) >= cote) echantillon *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = echantillon }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    internal fun verifierEspace(context: Context, taille: Long) {
        if (taille <= 0) return
        val libre = context.filesDir.usableSpace
        if (libre < taille + MARGE_ESPACE) throw IOException("espace insuffisant sur le téléphone")
    }

    private fun supprimerDocument(resolver: ContentResolver, uri: Uri): Boolean =
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (e: Exception) {
            false
        }

    private fun infos(resolver: ContentResolver, uri: Uri): Source {
        var nom: String? = null
        var taille = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val iNom = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val iTaille = c.getColumnIndex(OpenableColumns.SIZE)
                    if (iNom >= 0) nom = c.getString(iNom)
                    if (iTaille >= 0 && !c.isNull(iTaille)) taille = c.getLong(iTaille)
                }
            }
        }
        val mime = resolver.getType(uri) ?: ""
        return Source(uri, nom ?: (uri.lastPathSegment ?: "fichier"), taille, mime)
    }

    private fun nomDocument(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun lister(resolver: ContentResolver, arbre: Uri, parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(arbre, parentId)
        val colonnes = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val liste = ArrayList<Doc>()
        resolver.query(uri, colonnes, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                liste.add(
                    Doc(
                        id = c.getString(0),
                        nom = c.getString(1) ?: "",
                        mime = c.getString(2) ?: "",
                        taille = if (c.isNull(3)) -1L else c.getLong(3)
                    )
                )
            }
        }
        return liste
    }

    private fun lireManifeste(resolver: ContentResolver, arbre: Uri, doc: Doc): JSONObject? {
        if (doc.taille > 1_000_000) return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(arbre, doc.id)
        val texte = resolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: return null
        return JSONObject(texte)
    }

    /** Ordre du manifeste s'il est présent, sinon tri naturel des noms. */
    private fun ordonner(images: List<Doc>, manifeste: JSONObject?): List<Doc> {
        val tri = images.sortedWith(compareBy(TriNaturel) { it.nom })
        val ordre = manifeste?.optJSONArray("pages") ?: return tri
        val parNom = tri.associateBy { it.nom }
        val resultat = ArrayList<Doc>()
        for (i in 0 until ordre.length()) parNom[ordre.optString(i)]?.let { if (it !in resultat) resultat.add(it) }
        tri.forEach { if (it !in resultat) resultat.add(it) }
        return resultat
    }

    private fun estImage(d: Doc): Boolean =
        d.mime.startsWith("image/") || separer(d.nom).second.lowercase() in EXT_IMAGE

    fun separer(nom: String): Pair<String, String> {
        val point = nom.lastIndexOf('.')
        return if (point > 0 && point < nom.length - 1 && nom.length - point <= 6) {
            nom.substring(0, point) to nom.substring(point + 1)
        } else nom to ""
    }

    internal fun typeDe(mime: String, ext: String): TypeNoeud {
        val e = ext.lowercase()
        return when {
            mime.startsWith("video/") || e in EXT_VIDEO -> TypeNoeud.VIDEO
            mime.startsWith("image/") || e in EXT_IMAGE -> TypeNoeud.IMAGE
            else -> TypeNoeud.FICHIER
        }
    }

    fun mimeDe(n: Noeud): String = n.mime.ifEmpty {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.extension.lowercase()) ?: "application/octet-stream"
    }

    private fun bilan(reussis: Int, singulier: String, pluriel: String, echecs: Int, originaux: Int): String = buildString {
        append(if (reussis == 0) "Aucun élément importé." else "$reussis ${if (reussis > 1) pluriel else singulier}.")
        if (echecs > 0) append(" $echecs en échec : les originaux concernés n'ont pas été touchés.")
        if (originaux > 0) append(" $originaux original(aux) reste(nt) à supprimer de l'espace partagé.")
        if (reussis > 0 && echecs == 0 && originaux == 0) append(" Les originaux ont été supprimés de l'espace partagé.")
    }
}
