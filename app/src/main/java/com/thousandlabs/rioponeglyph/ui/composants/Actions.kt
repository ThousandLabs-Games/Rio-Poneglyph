package com.thousandlabs.rioponeglyph.ui.composants

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.thousandlabs.rioponeglyph.Session
import com.thousandlabs.rioponeglyph.coffre.Importation
import com.thousandlabs.rioponeglyph.coffre.Noeud
import com.thousandlabs.rioponeglyph.coffre.TriNaturel
import com.thousandlabs.rioponeglyph.coffre.TypeNoeud

enum class CritereTri(val libelle: String) { NOM("Nom"), DATE("Date d'ajout"), TAILLE("Taille"), TYPE("Type") }

/** Préférences d'affichage (tri), conservées entre les sessions. */
object PreferencesTri {
    private const val PREFS = "affichage"

    fun critere(context: Context): CritereTri = runCatching {
        CritereTri.valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tri", null) ?: "NOM")
    }.getOrDefault(CritereTri.NOM)

    fun croissant(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("croissant", true)

    fun enregistrer(context: Context, critere: CritereTri, croissant: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("tri", critere.name).putBoolean("croissant", croissant).apply()
    }
}

/** Dossiers toujours en tête, puis tri choisi. */
fun trier(elements: List<Noeud>, critere: CritereTri, croissant: Boolean): List<Noeud> {
    val base: Comparator<Noeud> = when (critere) {
        CritereTri.NOM -> compareBy(TriNaturel) { it.nom }
        CritereTri.DATE -> compareBy { it.ajoute }
        CritereTri.TAILLE -> compareBy { it.taille }
        CritereTri.TYPE -> compareBy<Noeud> { it.type.ordinal }.thenBy { it.extension.lowercase() }.thenBy(TriNaturel) { it.nom }
    }
    val ordonne = if (croissant) base else base.reversed()
    return elements.sortedWith(compareBy<Noeud> { it.type != TypeNoeud.DOSSIER }.then(ordonne))
}

/** Lanceurs des sélecteurs système d'importation (le verrouillage automatique est suspendu pendant le choix). */
class Importeurs(val fichiers: () -> Unit, val dossier: () -> Unit)

@Composable
fun rememberImporteurs(context: Context, destination: () -> String): Importeurs {
    val choixFichiers = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        Session.reprendreVerrouAuto()
        if (uris.isNotEmpty()) Importation.importerFichiers(context.applicationContext, uris, destination())
    }
    val choixDossier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        Session.reprendreVerrouAuto()
        if (uri != null) Importation.importerDossier(context.applicationContext, uri, destination())
    }
    return Importeurs(
        fichiers = {
            Session.suspendreVerrouAuto()
            try {
                choixFichiers.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                Session.reprendreVerrouAuto()
            }
        },
        dossier = {
            Session.suspendreVerrouAuto()
            try {
                choixDossier.launch(null)
            } catch (e: Exception) {
                Session.reprendreVerrouAuto()
            }
        }
    )
}
