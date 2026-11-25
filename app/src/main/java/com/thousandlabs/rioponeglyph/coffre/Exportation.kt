package com.thousandlabs.rioponeglyph.coffre

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Sortie des fichiers hors du coffre (vers un dossier choisi), avec relecture de contrôle.
 * En mode « retirer », un élément n'est effacé du coffre qu'une fois sa copie vérifiée.
 */
object Exportation {

    private const val PREFS = "garde_fou"
    private const val K_DERNIERE = "derniere_exportation_complete"

    fun derniereExportationComplete(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_DERNIERE, 0L)

    fun exporter(context: Context, ids: List<String>, arbre: Uri, retirer: Boolean, toutLeCoffre: Boolean = false): Boolean {
        val titre = if (retirer) "Sortie du coffre" else "Exportation"
        return Operations.lancer(titre) { suivi ->
            val resolver = context.contentResolver
            val cle = Coffre.cle()
            val dossierCible = DocumentsContract.buildDocumentUriUsingTree(arbre, DocumentsContract.getTreeDocumentId(arbre))
            val elements = ids.mapNotNull { Coffre.noeud(it) }
            val total = elements.sumOf { e -> Coffre.avecDescendants(e.id).sumOf { it.taille } }.coerceAtLeast(1L)
            var faits = 0L
            var reussis = 0
            val echecs = ArrayList<String>()

            fun ecrireObjet(objet: String, nom: String, mime: String, empreinte: String, parent: Uri) {
                val doc = DocumentsContract.createDocument(resolver, parent, mime, nom)
                    ?: throw IOException("création de « $nom » refusée")
                resolver.openOutputStream(doc, "w")?.use { sortie ->
                    FluxDechiffre(cle, Coffre.fichierObjet(objet)).use { entree ->
                        val tampon = ByteArray(1 shl 20)
                        while (true) {
                            val n = entree.read(tampon, 0, tampon.size)
                            if (n < 0) break
                            sortie.write(tampon, 0, n)
                            faits += n
                            suivi.maj("$nom", faits.toFloat() / total)
                        }
                        sortie.flush()
                    }
                } ?: throw IOException("écriture de « $nom » impossible")
                if (empreinte.isNotEmpty()) {
                    suivi.maj("Vérification : $nom", faits.toFloat() / total, forcer = true)
                    val relu = resolver.openInputStream(doc)?.use { FormatChiffre.empreinteFlux(it) }
                        ?: throw IOException("relecture de « $nom » impossible")
                    if (relu.empreinte != empreinte) throw IOException("la copie de « $nom » est altérée")
                }
            }

            fun exporterNoeud(n: Noeud, parent: Uri) {
                when (n.type) {
                    TypeNoeud.DOSSIER -> {
                        val dossier = creerDossier(resolver, parent, n.nom)
                        Coffre.enfants(n.id).sortedWith(compareBy(TriNaturel) { it.nom }).forEach { exporterNoeud(it, dossier) }
                    }
                    TypeNoeud.CHAPITRE -> {
                        val dossier = creerDossier(resolver, parent, n.nom)
                        n.pages.forEach { p ->
                            val mime = android.webkit.MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(Importation.separer(p.nom).second.lowercase()) ?: "image/jpeg"
                            ecrireObjet(p.objet, p.nom, mime, p.empreinte, dossier)
                        }
                        ecrireManifeste(resolver, dossier, n)
                    }
                    else -> ecrireObjet(n.id, n.nomComplet, Importation.mimeDe(n), n.empreinte, parent)
                }
            }

            for (e in elements) {
                try {
                    exporterNoeud(e, dossierCible)
                    if (retirer && !e.systeme) Coffre.supprimer(listOf(e.id))
                    reussis++
                } catch (ex: Exception) {
                    echecs.add("${e.nom} : ${ex.message ?: "erreur inconnue"}")
                }
            }
            if (toutLeCoffre && echecs.isEmpty()) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(K_DERNIERE, System.currentTimeMillis()).apply()
            }
            val bilan = buildString {
                append(
                    when {
                        reussis == 0 -> "Aucun élément exporté."
                        retirer -> "$reussis élément(s) sorti(s) du coffre après vérification."
                        else -> "$reussis élément(s) exporté(s) et vérifié(s)."
                    }
                )
                if (echecs.isNotEmpty()) append(" ${echecs.size} en échec : ces éléments restent dans le coffre.")
            }
            EtatOperation(titre, bilan = bilan, echecs = echecs)
        }
    }

    fun verifierIntegrite(): Boolean = Operations.lancer("Vérification du coffre") { suivi ->
        val cle = Coffre.cle()
        val elements = Coffre.tous().filter { !it.estDossier }
        val total = elements.sumOf { it.taille }.coerceAtLeast(1L)
        var faits = 0L
        var controles = 0
        val abimes = ArrayList<String>()
        for (n in elements) {
            val cibles = if (n.type == TypeNoeud.CHAPITRE) n.pages.map { it.objet to it.empreinte } else listOf(n.id to n.empreinte)
            var ok = true
            for ((objet, empreinte) in cibles) {
                val f = Coffre.fichierObjet(objet)
                val resultat = runCatching {
                    FormatChiffre.empreinteDechiffree(cle, f) { k -> suivi.maj(n.nomComplet, (faits + k).toFloat() / total) }
                }.getOrNull()
                if (resultat == null || (empreinte.isNotEmpty() && resultat.empreinte != empreinte)) ok = false
                faits += resultat?.taille ?: 0L
            }
            controles++
            if (!ok) abimes.add(n.nomComplet)
        }
        EtatOperation(
            "Vérification du coffre",
            bilan = if (abimes.isEmpty()) "$controles élément(s) vérifié(s) : aucun problème détecté."
            else "${abimes.size} élément(s) sur $controles présentent un problème.",
            echecs = abimes
        )
    }

    private fun creerDossier(resolver: ContentResolver, parent: Uri, nom: String): Uri =
        DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, nom)
            ?: throw IOException("création du dossier « $nom » refusée")

    private fun ecrireManifeste(resolver: ContentResolver, dossier: Uri, n: Noeud) {
        val json = JSONObject()
            .put("format", "rio-poneglyph-pages")
            .put("version", 1)
            .put("titre", n.nom)
            .put("sens", n.sens)
            .put("pages", JSONArray().apply { n.pages.forEach { put(it.nom) } })
        val doc = DocumentsContract.createDocument(resolver, dossier, "application/json", Importation.NOM_MANIFESTE) ?: return
        resolver.openOutputStream(doc, "w")?.use { it.write(json.toString(2).toByteArray(Charsets.UTF_8)) }
    }
}
