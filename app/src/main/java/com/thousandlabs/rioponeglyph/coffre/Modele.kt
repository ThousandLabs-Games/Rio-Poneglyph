package com.thousandlabs.rioponeglyph.coffre

import org.json.JSONArray
import org.json.JSONObject

enum class TypeNoeud { DOSSIER, VIDEO, IMAGE, CHAPITRE, FICHIER }

data class Page(val objet: String, val nom: String, val taille: Long, val empreinte: String)

/**
 * Élément du coffre. Le nom affiché est indépendant du fichier chiffré :
 * renommer ne touche jamais au contenu, ni à l'extension réelle.
 */
data class Noeud(
    val id: String,
    val parent: String?,
    val nom: String,
    val type: TypeNoeud,
    val extension: String = "",
    val mime: String = "",
    val taille: Long = 0L,
    val ajoute: Long = System.currentTimeMillis(),
    val empreinte: String = "",
    val miniature: String? = null,
    val duree: Long = 0L,
    val pages: List<Page> = emptyList(),
    val sens: String = "",
    val systeme: Boolean = false
) {
    val estDossier: Boolean get() = type == TypeNoeud.DOSSIER
    val nomComplet: String get() = if (extension.isEmpty()) nom else "$nom.$extension"

    /** Objets chiffrés appartenant à cet élément (contenu, miniature, pages). */
    fun objets(): List<String> = buildList {
        if (type == TypeNoeud.VIDEO || type == TypeNoeud.IMAGE || type == TypeNoeud.FICHIER) add(id)
        miniature?.let { add(it) }
        pages.forEach { add(it.objet) }
    }

    fun versJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("parent", parent ?: JSONObject.NULL)
        put("nom", nom)
        put("type", type.name)
        put("extension", extension)
        put("mime", mime)
        put("taille", taille)
        put("ajoute", ajoute)
        put("empreinte", empreinte)
        put("miniature", miniature ?: JSONObject.NULL)
        put("duree", duree)
        put("sens", sens)
        put("systeme", systeme)
        put("pages", JSONArray().apply {
            pages.forEach { p ->
                put(JSONObject().apply {
                    put("objet", p.objet)
                    put("nom", p.nom)
                    put("taille", p.taille)
                    put("empreinte", p.empreinte)
                })
            }
        })
    }

    companion object {
        fun depuisJson(o: JSONObject): Noeud {
            val pagesJson = o.optJSONArray("pages") ?: JSONArray()
            val pages = List(pagesJson.length()) { i ->
                val p = pagesJson.getJSONObject(i)
                Page(p.getString("objet"), p.optString("nom"), p.optLong("taille"), p.optString("empreinte"))
            }
            return Noeud(
                id = o.getString("id"),
                parent = if (o.isNull("parent")) null else o.getString("parent"),
                nom = o.getString("nom"),
                type = runCatching { TypeNoeud.valueOf(o.getString("type")) }.getOrDefault(TypeNoeud.FICHIER),
                extension = o.optString("extension"),
                mime = o.optString("mime"),
                taille = o.optLong("taille"),
                ajoute = o.optLong("ajoute"),
                empreinte = o.optString("empreinte"),
                miniature = if (o.isNull("miniature")) null else o.optString("miniature").ifEmpty { null },
                duree = o.optLong("duree"),
                pages = pages,
                sens = o.optString("sens"),
                systeme = o.optBoolean("systeme")
            )
        }
    }
}

/** Tri naturel : « Episode 2 » avant « Episode 10 ». */
object TriNaturel : Comparator<String> {
    private val morceaux = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val ma = morceaux.findAll(a.lowercase()).map { it.value }.toList()
        val mb = morceaux.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ma.size, mb.size)) {
            val x = ma[i]
            val y = mb[i]
            val r = if (x[0].isDigit() && y[0].isDigit()) {
                val xs = x.trimStart('0')
                val ys = y.trimStart('0')
                if (xs.length != ys.length) xs.length - ys.length else xs.compareTo(ys)
            } else x.compareTo(y)
            if (r != 0) return r
        }
        return ma.size - mb.size
    }
}
