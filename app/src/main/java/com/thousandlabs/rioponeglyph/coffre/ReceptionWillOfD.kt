package com.thousandlabs.rioponeglyph.coffre

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * Réception des éléments déposés par Will of D.
 *
 * Échange local à sens unique : Rio Poneglyph lit la zone de dépôt de Will of D
 * (fournisseur protégé par une permission de signature Thousand Labs), chiffre chaque élément
 * dans le coffre, vérifie son intégrité, puis demande à Will of D de l'effacer.
 * Aucune donnée n'est jamais envoyée vers Will of D : la seule requête émise est cette demande d'effacement.
 */
object ReceptionWillOfD {

    const val AUTORITE = "com.thousandlabs.willofd.depot"
    const val PAQUET = "com.thousandlabs.willofd"
    private val BASE: Uri = Uri.parse("content://$AUTORITE")

    enum class Genre { VIDEO, PAGES }

    data class Depot(
        val id: String,
        val genre: Genre,
        val titre: String,
        val serie: String,
        val cleSerie: String,
        val cleElement: String,
        val numero: Double,
        val partie: Int,
        val sens: String,
        val taille: Long,
        val fichiers: Int,
        val ajoute: Long
    )

    sealed interface Etat {
        data class Disponible(val depots: List<Depot>) : Etat
        data object NonInstallee : Etat
        data object SignatureDifferente : Etat
        data class Erreur(val message: String) : Etat
    }

    private data class FichierDepot(val nom: String, val taille: Long, val mime: String)

    private fun uriElements(): Uri = BASE.buildUpon().appendPath("elements").build()
    private fun uriElement(id: String): Uri = uriElements().buildUpon().appendPath(id).build()
    private fun uriFichiers(id: String): Uri = uriElement(id).buildUpon().appendPath("fichiers").build()
    private fun uriFichier(id: String, nom: String): Uri = uriFichiers(id).buildUpon().appendPath(nom).build()

    fun installee(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PAQUET, 0) }.isSuccess

    /** Liste les éléments terminés et prêts à être récupérés. */
    fun lister(context: Context): Etat {
        if (!installee(context)) return Etat.NonInstallee
        return try {
            val curseur = context.contentResolver.query(uriElements(), null, null, null, null)
                ?: return Etat.Erreur("Will of D ne répond pas. Ouvrez Will of D une fois, puis réessayez.")
            val liste = curseur.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val genre = if (c.texte("type") == "pages") Genre.PAGES else Genre.VIDEO
                        add(
                            Depot(
                                id = c.texte("id"),
                                genre = genre,
                                titre = c.texte("titre").ifBlank { "Sans titre" },
                                serie = c.texte("serie"),
                                cleSerie = c.texte("cle_serie"),
                                cleElement = c.texte("cle_element"),
                                numero = c.reel("numero"),
                                partie = c.entier("partie").toInt(),
                                sens = c.texte("sens"),
                                taille = c.entier("taille"),
                                fichiers = c.entier("fichiers").toInt(),
                                ajoute = c.entier("ajoute")
                            )
                        )
                    }
                }
            }
            Etat.Disponible(liste.filter { it.id.isNotBlank() })
        } catch (e: SecurityException) {
            Etat.SignatureDifferente
        } catch (e: Exception) {
            Etat.Erreur(e.message ?: "lecture impossible")
        }
    }

    /** Récupère les éléments choisis, dans l'ordre de lecture (numéro, puis partie). */
    fun recuperer(context: Context, depots: List<Depot>): Boolean =
        Operations.lancer("Récupération depuis Will of D") { suivi ->
            val ordonnes = depots.sortedWith(
                compareBy<Depot>({ it.cleSerie }, { if (it.numero < 0) Double.MAX_VALUE else it.numero }, { it.partie }, { it.ajoute })
            )
            var reussis = 0
            val echecs = ArrayList<String>()
            val nonEffaces = ArrayList<String>()
            ordonnes.forEachIndexed { i, d ->
                val prefixe = "Élément ${i + 1} sur ${ordonnes.size} : ${d.titre}"
                suivi.maj(prefixe, i.toFloat() / ordonnes.size, forcer = true)
                try {
                    Importation.verifierEspace(context, d.taille)
                    when (d.genre) {
                        Genre.VIDEO -> recevoirVideo(context, d) { f -> suivi.maj(prefixe, (i + f) / ordonnes.size) }
                        Genre.PAGES -> recevoirPages(context, d) { f, page ->
                            suivi.maj("$prefixe\n$page", (i + f) / ordonnes.size)
                        }
                    }
                    reussis++
                    // Le dépôt n'est effacé qu'après chiffrement vérifié et enregistrement dans le coffre.
                    val efface = runCatching { context.contentResolver.delete(uriElement(d.id), null, null) > 0 }
                        .getOrDefault(false)
                    if (!efface) nonEffaces.add(d.titre)
                } catch (e: Exception) {
                    echecs.add("${d.titre} : ${e.message ?: "erreur inconnue"}")
                }
            }
            EtatOperation(
                titre = "Récupération depuis Will of D",
                bilan = buildString {
                    append(if (reussis == 0) "Aucun élément récupéré." else "$reussis élément(s) récupéré(s) et chiffré(s).")
                    if (echecs.isNotEmpty()) append(" ${echecs.size} en échec : ils restent intacts dans Will of D.")
                    if (nonEffaces.isNotEmpty()) append(" ${nonEffaces.size} n'ont pas pu être retirés de Will of D ; ils le seront à la prochaine ouverture de Will of D.")
                    if (reussis > 0 && echecs.isEmpty() && nonEffaces.isEmpty()) append(" Ils ont été retirés de Will of D.")
                },
                echecs = echecs
            )
        }

    // ------------------------------------------------------------------ Vidéos

    private fun recevoirVideo(context: Context, d: Depot, avancement: (Float) -> Unit) {
        val fichier = listerFichiers(context, d.id).firstOrNull() ?: throw IOException("aucun fichier vidéo")
        val uri = uriFichier(d.id, fichier.nom)
        val crees = ArrayList<File>()
        try {
            val parent = if (d.serie.isNotBlank()) {
                Coffre.dossierSerie(Coffre.ID_VIDEOS, d.cleSerie.ifBlank { "serie:" + d.serie.lowercase() }, d.serie).id
            } else Coffre.ID_VIDEOS
            val (_, ext) = Importation.separer(fichier.nom)
            val total = fichier.taille.coerceAtLeast(1L).toFloat()
            val objet = Importation.chiffrerSource(
                context, Importation.Source(uri, fichier.nom, fichier.taille, fichier.mime), crees
            ) { n, _ -> avancement((n / total).coerceIn(0f, 1f)) }

            var duree = 0L
            var miniature: String? = null
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    duree = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val instant = if (duree > 0) minOf(duree * 100, 60_000_000L) else 1_000_000L
                    r.getScaledFrameAtTime(instant, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320)
                } finally {
                    r.release()
                }
            }.getOrNull()?.let { miniature = Importation.enregistrerMiniature(it, crees) }

            Coffre.ajouter(
                listOf(
                    Noeud(
                        id = objet.id, parent = parent, nom = Coffre.nettoyerNom(d.titre), type = TypeNoeud.VIDEO,
                        extension = ext.lowercase(), mime = fichier.mime, taille = objet.taille,
                        empreinte = objet.empreinte, miniature = miniature, duree = duree,
                        cle = d.cleElement, numero = d.numero
                    )
                )
            )
        } catch (e: Exception) {
            crees.forEach { it.delete() }
            throw e
        }
    }

    // ------------------------------------------------------------------ Pages

    private fun recevoirPages(context: Context, d: Depot, avancement: (Float, String) -> Unit) {
        val images = listerFichiers(context, d.id).filter { f ->
            f.mime.startsWith("image/") || Importation.typeDe(f.mime, Importation.separer(f.nom).second) == TypeNoeud.IMAGE
        }
        if (images.isEmpty()) throw IOException("aucune page dans cet élément")
        val crees = ArrayList<File>()
        try {
            val pages = images.mapIndexed { i, f ->
                avancement(i.toFloat() / images.size, "Page ${i + 1} sur ${images.size}")
                val objet = Importation.chiffrerSource(
                    context, Importation.Source(uriFichier(d.id, f.nom), f.nom, f.taille, f.mime), crees
                ) { _, _ -> }
                Page(objet.id, f.nom, objet.taille, objet.empreinte)
            }

            // Suite d'un chapitre déjà présent (page 2, partie suivante…) : fusion dans le même chapitre.
            val existant = Coffre.parCle(d.cleElement, TypeNoeud.CHAPITRE)
            if (existant != null) {
                val ignores = Coffre.completerChapitre(existant.id, pages)
                ignores.forEach { Coffre.fichierObjet(it).delete() }
                return
            }

            val parent = if (d.serie.isNotBlank()) {
                Coffre.dossierSerie(Coffre.ID_PAGES, d.cleSerie.ifBlank { "serie:" + d.serie.lowercase() }, d.serie).id
            } else Coffre.ID_PAGES
            val miniature = runCatching {
                Importation.decoderReduit(context.contentResolver, uriFichier(d.id, images.first().nom), 320)
            }.getOrNull()?.let { Importation.enregistrerMiniature(it, crees) }
            Coffre.ajouter(
                listOf(
                    Noeud(
                        id = Coffre.nouvelId(), parent = parent, nom = Coffre.nettoyerNom(d.titre), type = TypeNoeud.CHAPITRE,
                        taille = pages.sumOf { it.taille }, miniature = miniature, pages = pages, sens = d.sens,
                        cle = d.cleElement, numero = d.numero
                    )
                )
            )
        } catch (e: Exception) {
            crees.forEach { it.delete() }
            throw e
        }
    }

    // ------------------------------------------------------------------ Outils

    private fun listerFichiers(context: Context, id: String): List<FichierDepot> {
        val c = context.contentResolver.query(uriFichiers(id), null, null, null, null)
            ?: throw IOException("élément introuvable dans Will of D")
        return c.use {
            buildList {
                while (it.moveToNext()) add(FichierDepot(it.texte("nom"), it.entier("taille"), it.texte("mime")))
            }
        }.filter { it.nom.isNotBlank() }
    }

    private fun Cursor.texte(colonne: String): String {
        val i = getColumnIndex(colonne)
        return if (i < 0 || isNull(i)) "" else getString(i) ?: ""
    }

    private fun Cursor.entier(colonne: String): Long {
        val i = getColumnIndex(colonne)
        return if (i < 0 || isNull(i)) 0L else getLong(i)
    }

    private fun Cursor.reel(colonne: String): Double {
        val i = getColumnIndex(colonne)
        return if (i < 0 || isNull(i)) -1.0 else getDouble(i)
    }
}
