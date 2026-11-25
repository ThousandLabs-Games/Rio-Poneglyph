package com.thousandlabs.rioponeglyph.coffre

import android.content.Context
import com.thousandlabs.rioponeglyph.securite.CleCoffre
import com.thousandlabs.rioponeglyph.util.EcritureAtomique
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.text.Normalizer
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class Statistiques(val fichiers: Int, val dossiers: Int, val octets: Long)

/**
 * Index du coffre : arborescence logique (noms, dossiers) chiffrée en AES-GCM.
 * Les contenus sont stockés à plat dans « objets », sous un identifiant neutre.
 * Chaque enregistrement de l'index est écrit deux fois, de façon atomique
 * (fichier principal + secours), pour résister à une coupure.
 */
object Coffre {

    const val ID_VIDEOS = "videos"
    const val ID_PAGES = "pages"
    private const val NOM_INDEX = "index.rpi"
    private const val NOM_SECOURS = "index.rpi.bak"

    private val verrou = Any()
    private val noeuds = LinkedHashMap<String, Noeud>()
    private var dossierRacine: File? = null
    @Volatile
    private var cleCoffre: ByteArray? = null

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    val estCharge: Boolean get() = cleCoffre != null

    fun cle(): ByteArray = cleCoffre ?: throw IllegalStateException("Coffre non ouvert")

    private fun racine(): File = dossierRacine ?: throw IllegalStateException("Coffre non ouvert")

    fun dossierObjets(): File = File(racine(), "objets")

    fun fichierObjet(id: String): File = File(dossierObjets(), "$id.rpx")

    fun nouvelId(): String = UUID.randomUUID().toString()

    // ---------------------------------------------------------------- Ouverture

    fun ouvrir(context: Context) {
        synchronized(verrou) {
            if (cleCoffre != null) return
            val racine = File(context.filesDir, "coffre")
            val objets = File(racine, "objets")
            if (!objets.exists() && !objets.mkdirs()) throw IOException("Impossible de créer l'espace du coffre")
            CleCoffre.initialiser(context)
            val cle = CleCoffre.charger(context)
            val principal = File(racine, NOM_INDEX)
            val secours = File(racine, NOM_SECOURS)
            var fiable = false
            val liste: List<Noeud> = when {
                principal.exists() -> {
                    val lu = runCatching { lireIndex(cle, principal) }.getOrNull()
                    when {
                        lu != null -> { fiable = true; lu }
                        secours.exists() -> lireIndex(cle, secours)
                        else -> throw IOException("Index du coffre illisible")
                    }
                }
                secours.exists() -> lireIndex(cle, secours)
                else -> {
                    val existants = objets.listFiles()?.count { it.name.endsWith(".rpx") } ?: 0
                    if (existants > 0) throw IOException("Index du coffre introuvable alors que des fichiers chiffrés existent")
                    fiable = true
                    emptyList()
                }
            }
            noeuds.clear()
            liste.forEach { noeuds[it.id] = it }
            dossierRacine = racine
            cleCoffre = cle

            var modifie = !fiable
            if (ID_VIDEOS !in noeuds) {
                noeuds[ID_VIDEOS] = Noeud(ID_VIDEOS, null, "Videos", TypeNoeud.DOSSIER, ajoute = 1L, systeme = true)
                modifie = true
            }
            if (ID_PAGES !in noeuds) {
                noeuds[ID_PAGES] = Noeud(ID_PAGES, null, "Pages", TypeNoeud.DOSSIER, ajoute = 2L, systeme = true)
                modifie = true
            }
            if (modifie) sauvegarderInterne()
            // Le nettoyage n'a lieu que si l'index principal est certain d'être complet.
            if (fiable) nettoyerOrphelins()
            _version.value++
        }
    }

    private fun nettoyerOrphelins() {
        val references = HashSet<String>()
        noeuds.values.forEach { references.addAll(it.objets()) }
        dossierObjets().listFiles()?.forEach { f ->
            when {
                f.name.endsWith(".part") || f.name.endsWith(".tmp") -> f.delete()
                f.name.endsWith(".rpx") && f.name.removeSuffix(".rpx") !in references -> f.delete()
            }
        }
    }

    private fun lireIndex(cle: ByteArray, fichier: File): List<Noeud> {
        val donnees = fichier.readBytes()
        if (donnees.size <= 12) throw IOException("Index vide")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(cle, "AES"), GCMParameterSpec(128, donnees, 0, 12))
        val clair = c.doFinal(donnees, 12, donnees.size - 12)
        val racine = JSONObject(String(clair, Charsets.UTF_8))
        val tableau = racine.getJSONArray("noeuds")
        return List(tableau.length()) { Noeud.depuisJson(tableau.getJSONObject(it)) }
    }

    private fun sauvegarderInterne() {
        val tableau = JSONArray()
        noeuds.values.forEach { tableau.put(it.versJson()) }
        val clair = JSONObject().put("version", 1).put("noeuds", tableau).toString().toByteArray(Charsets.UTF_8)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cle(), "AES"), GCMParameterSpec(128, iv))
        val donnees = iv + c.doFinal(clair)
        EcritureAtomique.ecrire(File(racine(), NOM_SECOURS), donnees)
        EcritureAtomique.ecrire(File(racine(), NOM_INDEX), donnees)
    }

    /** Applique une modification ; en cas d'échec d'écriture, l'index en mémoire est restauré. */
    private fun <T> modifier(bloc: (MutableMap<String, Noeud>) -> T): T {
        synchronized(verrou) {
            val copie = LinkedHashMap(noeuds)
            try {
                val r = bloc(noeuds)
                sauvegarderInterne()
                _version.value++
                return r
            } catch (e: Exception) {
                noeuds.clear()
                noeuds.putAll(copie)
                throw e
            }
        }
    }

    // ---------------------------------------------------------------- Lecture

    fun noeud(id: String): Noeud? = synchronized(verrou) { noeuds[id] }

    fun enfants(parent: String?): List<Noeud> = synchronized(verrou) { noeuds.values.filter { it.parent == parent } }

    fun nombreEnfants(id: String): Int = synchronized(verrou) { noeuds.values.count { it.parent == id } }

    fun racines(): List<Noeud> = enfants(null).sortedWith(
        compareByDescending<Noeud> { it.systeme }.thenBy { it.ajoute }.thenBy(TriNaturel) { it.nom }
    )

    fun tous(): List<Noeud> = synchronized(verrou) { noeuds.values.toList() }

    fun chemin(id: String): List<Noeud> = synchronized(verrou) {
        val resultat = ArrayList<Noeud>()
        var courant = noeuds[id]
        var garde = 0
        while (courant != null && garde++ < 256) {
            resultat.add(0, courant)
            courant = courant.parent?.let { noeuds[it] }
        }
        resultat
    }

    /** Tous les éléments contenus (récursivement), y compris l'élément lui-même. */
    fun avecDescendants(id: String): List<Noeud> = synchronized(verrou) { descendantsInterne(noeuds, id) }

    private fun descendantsInterne(map: Map<String, Noeud>, id: String): List<Noeud> {
        val resultat = ArrayList<Noeud>()
        val file = ArrayDeque<String>()
        map[id]?.let { resultat.add(it); file.add(id) }
        val parParent = map.values.groupBy { it.parent }
        while (file.isNotEmpty()) {
            val p = file.removeFirst()
            parParent[p]?.forEach { e -> resultat.add(e); if (e.estDossier || e.type == TypeNoeud.CHAPITRE) file.add(e.id) }
        }
        return resultat
    }

    private fun normaliser(t: String): String =
        Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /**
     * Élément suivant du même type dans le même dossier (chapitre ou épisode suivant) :
     * ordre par numéro quand il est connu, sinon ordre naturel des noms.
     */
    fun suivant(id: String): Noeud? = synchronized(verrou) {
        val n = noeuds[id] ?: return null
        val freres = noeuds.values.filter { it.parent == n.parent && it.type == n.type }
            .sortedWith(ordreSuite)
        val i = freres.indexOfFirst { it.id == id }
        if (i < 0 || i >= freres.lastIndex) null else freres[i + 1]
    }

    /** Ordre de lecture : numéro (si connu) puis nom naturel. */
    val ordreSuite: Comparator<Noeud> = Comparator { a, b ->
        if (a.numero >= 0 && b.numero >= 0 && a.numero != b.numero) a.numero.compareTo(b.numero)
        else TriNaturel.compare(a.nom, b.nom)
    }

    /** Premier élément portant cette clé de continuité, où qu'il ait été déplacé. */
    fun parCle(cle: String, type: TypeNoeud): Noeud? {
        if (cle.isBlank()) return null
        return synchronized(verrou) { noeuds.values.firstOrNull { it.cle == cle && it.type == type } }
    }

    /** Dossier d'une série (retrouvé par sa clé même s'il a été renommé ou déplacé), créé au besoin. */
    fun dossierSerie(parentParDefaut: String, cle: String, nom: String): Noeud = modifier { map ->
        map.values.firstOrNull { it.type == TypeNoeud.DOSSIER && cle.isNotBlank() && it.cle == cle }
            ?: run {
                val propre = nettoyerNom(nom)
                val d = Noeud(nouvelId(), parentParDefaut, nomUnique(map, parentParDefaut, propre, "", null), TypeNoeud.DOSSIER, cle = cle)
                map[d.id] = d
                d
            }
    }

    /**
     * Ajoute à un chapitre existant les pages d'une suite téléchargée plus tard.
     * Les pages déjà présentes (même empreinte) sont ignorées ; renvoie les objets non retenus.
     */
    fun completerChapitre(id: String, ajout: List<Page>): List<String> = modifier { map ->
        val n = map[id] ?: throw IllegalArgumentException("Chapitre introuvable")
        val connues = n.pages.map { it.empreinte }.toHashSet()
        val retenues = ajout.filter { connues.add(it.empreinte) }
        val toutes = n.pages + retenues
        map[id] = n.copy(pages = toutes, taille = toutes.sumOf { it.taille })
        ajout.filter { it !in retenues }.map { it.objet }
    }

    fun rechercher(texte: String): List<Noeud> {
        val cible = normaliser(texte.trim())
        if (cible.isEmpty()) return emptyList()
        return tous().filter { normaliser(it.nomComplet).contains(cible) }
            .sortedWith(compareBy(TriNaturel) { it.nom })
    }

    fun statistiques(): Statistiques = synchronized(verrou) {
        var fichiers = 0
        var dossiers = 0
        var octets = 0L
        noeuds.values.forEach {
            if (it.estDossier) dossiers++ else { fichiers++; octets += it.taille }
        }
        Statistiques(fichiers, dossiers, octets)
    }

    // ---------------------------------------------------------------- Modifications

    fun nettoyerNom(nom: String): String {
        val propre = nom.replace(Regex("[\\u0000-\\u001F/\\\\]"), " ").trim().take(180)
        if (propre.isEmpty()) throw IllegalArgumentException("Le nom ne peut pas être vide")
        return propre
    }

    private fun nomUnique(map: Map<String, Noeud>, parent: String?, nom: String, extension: String, exclure: String?): String {
        val pris = map.values
            .filter { it.parent == parent && it.id != exclure && it.extension.equals(extension, ignoreCase = true) }
            .map { it.nom.lowercase() }
            .toHashSet()
        if (nom.lowercase() !in pris) return nom
        var i = 2
        while ("$nom ($i)".lowercase() in pris) i++
        return "$nom ($i)"
    }

    fun creerDossier(parent: String?, nom: String): Noeud = modifier { map ->
        if (parent != null && map[parent]?.estDossier != true) throw IllegalArgumentException("Dossier de destination introuvable")
        val propre = nettoyerNom(nom)
        val n = Noeud(nouvelId(), parent, nomUnique(map, parent, propre, "", null), TypeNoeud.DOSSIER)
        map[n.id] = n
        n
    }

    /** Ne modifie que le nom affiché : contenu chiffré, extension et type restent intacts. */
    fun renommer(id: String, nom: String) = modifier { map ->
        val n = map[id] ?: throw IllegalArgumentException("Élément introuvable")
        val propre = nettoyerNom(nom)
        map[id] = n.copy(nom = nomUnique(map, n.parent, propre, n.extension, id))
    }

    /** Déplace des éléments ; renvoie le nombre d'éléments réellement déplacés. */
    fun deplacer(ids: Collection<String>, destination: String): Int = modifier { map ->
        if (map[destination]?.estDossier != true) throw IllegalArgumentException("Dossier de destination introuvable")
        var deplaces = 0
        for (id in ids) {
            val n = map[id] ?: continue
            if (n.systeme || n.parent == destination) continue
            if (descendantsInterne(map, id).any { it.id == destination }) continue
            map[id] = n.copy(parent = destination, nom = nomUnique(map, destination, n.nom, n.extension, id))
            deplaces++
        }
        deplaces
    }

    /** Ajoute de nouveaux éléments dont les objets chiffrés sont déjà validés sur le disque. */
    fun ajouter(elements: List<Noeud>): List<Noeud> = modifier { map ->
        elements.map { e ->
            val final = e.copy(nom = nomUnique(map, e.parent, e.nom, e.extension, e.id))
            map[final.id] = final
            final
        }
    }

    /** Supprime définitivement des éléments (et leur contenu) du coffre. */
    fun supprimer(ids: Collection<String>): Int {
        val retires = modifier { map ->
            val liste = LinkedHashMap<String, Noeud>()
            ids.forEach { id ->
                val n = map[id] ?: return@forEach
                if (n.systeme) return@forEach
                descendantsInterne(map, id).forEach { liste[it.id] = it }
            }
            liste.keys.forEach { map.remove(it) }
            liste.values.toList()
        }
        // Les contenus ne sont effacés qu'une fois l'index enregistré sans eux.
        retires.forEach { n -> n.objets().forEach { fichierObjet(it).delete() } }
        return retires.size
    }

    /**
     * Copie des éléments (avec leur contenu) dans un autre dossier.
     * Les nouveaux fichiers chiffrés sont écrits et vérifiés avant d'être ajoutés à l'index.
     */
    fun copier(ids: Collection<String>, destination: String, progression: (Long) -> Unit = {}): Int {
        if (noeud(destination)?.estDossier != true) throw IllegalArgumentException("Dossier de destination introuvable")
        val plan = ArrayList<Noeud>()
        val correspondance = HashMap<String, String>()
        synchronized(verrou) {
            ids.forEach { id ->
                if (noeuds[id] == null || descendantsInterne(noeuds, id).any { it.id == destination }) return@forEach
                descendantsInterne(noeuds, id).forEach { n ->
                    if (n.id !in correspondance) {
                        correspondance[n.id] = nouvelId()
                        plan.add(n)
                    }
                }
            }
        }
        val racinesCopiees = ids.toSet()
        val crees = ArrayList<File>()
        val nouveaux = ArrayList<Noeud>()
        var octets = 0L
        try {
            for (n in plan) {
                val nouvelIdNoeud = correspondance.getValue(n.id)
                val nouvelleMiniature = n.miniature?.let { m ->
                    val idm = nouvelId()
                    copierObjet(m, idm, crees)
                    idm
                }
                val nouvellesPages = n.pages.map { p ->
                    val idp = nouvelId()
                    copierObjet(p.objet, idp, crees)
                    octets += p.taille
                    progression(octets)
                    p.copy(objet = idp)
                }
                if (n.type == TypeNoeud.VIDEO || n.type == TypeNoeud.IMAGE || n.type == TypeNoeud.FICHIER) {
                    copierObjet(n.id, nouvelIdNoeud, crees)
                    octets += n.taille
                    progression(octets)
                }
                val parent = if (n.id in racinesCopiees) destination else correspondance[n.parent] ?: destination
                nouveaux.add(
                    n.copy(
                        id = nouvelIdNoeud, parent = parent, miniature = nouvelleMiniature,
                        pages = nouvellesPages, systeme = false, ajoute = System.currentTimeMillis()
                    )
                )
            }
            ajouter(nouveaux)
        } catch (e: Exception) {
            crees.forEach { it.delete() }
            throw e
        }
        return racinesCopiees.count { it in correspondance }
    }

    private fun copierObjet(source: String, cible: String, crees: MutableList<File>) {
        val src = fichierObjet(source)
        val dst = fichierObjet(cible)
        val tmp = File(dst.path + ".part")
        FileInputStream(src).use { entree ->
            FileOutputStream(tmp).use { sortie ->
                entree.copyTo(sortie, 1 shl 20)
                sortie.flush()
                sortie.fd.sync()
            }
        }
        if (tmp.length() != src.length()) {
            tmp.delete()
            throw IOException("Copie incomplète")
        }
        if (!tmp.renameTo(dst)) {
            tmp.delete()
            throw IOException("Validation de la copie impossible")
        }
        crees.add(dst)
    }
}
