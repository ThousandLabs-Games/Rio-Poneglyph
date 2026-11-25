package com.thousandlabs.rioponeglyph.ui.composants

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.coffre.Coffre
import com.thousandlabs.rioponeglyph.coffre.FormatChiffre
import com.thousandlabs.rioponeglyph.coffre.Noeud
import com.thousandlabs.rioponeglyph.coffre.TypeNoeud
import com.thousandlabs.rioponeglyph.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Miniatures déchiffrées, gardées uniquement en mémoire. */
object CacheMiniatures {
    private val cache = LruCache<String, ImageBitmap>(250)

    fun lire(id: String?): ImageBitmap? = id?.let { cache.get(it) }

    fun charger(id: String): ImageBitmap? {
        cache.get(id)?.let { return it }
        val image = runCatching {
            val octets = FormatChiffre.dechiffrerOctets(Coffre.cle(), Coffre.fichierObjet(id))
            BitmapFactory.decodeByteArray(octets, 0, octets.size)?.asImageBitmap()
        }.getOrNull()
        if (image != null) cache.put(id, image)
        return image
    }

    fun vider() = cache.evictAll()
}

fun iconeType(type: TypeNoeud): ImageVector = when (type) {
    TypeNoeud.DOSSIER -> Icons.Outlined.Folder
    TypeNoeud.VIDEO -> Icons.Outlined.Movie
    TypeNoeud.IMAGE -> Icons.Outlined.Image
    TypeNoeud.CHAPITRE -> Icons.AutoMirrored.Outlined.MenuBook
    TypeNoeud.FICHIER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

fun detailNoeud(n: Noeud): String = when (n.type) {
    TypeNoeud.DOSSIER -> Coffre.nombreEnfants(n.id).let { if (it <= 1) "$it élément" else "$it éléments" }
    TypeNoeud.VIDEO -> listOfNotNull(
        "Vidéo",
        n.duree.takeIf { it > 0 }?.let { Formats.duree(it) },
        Formats.taille(n.taille)
    ).joinToString(" · ")
    TypeNoeud.IMAGE -> "Image · ${n.extension.uppercase()} · ${Formats.taille(n.taille)}"
    TypeNoeud.CHAPITRE -> "Chapitre · ${n.pages.size} pages · ${Formats.taille(n.taille)}"
    TypeNoeud.FICHIER -> "${n.extension.uppercase().ifEmpty { "Fichier" }} · ${Formats.taille(n.taille)}"
}

/** Message à afficher si l'élément ne peut pas encore être ouvert, sinon null. */
fun messageNonOuvrable(n: Noeud): String? = when (n.type) {
    TypeNoeud.FICHIER -> "Ce type de fichier ne s'ouvre pas dans Rio Poneglyph. Vous pouvez l'exporter."
    else -> null
}

@Composable
fun Miniature(noeud: Noeud, taille: Dp = 56.dp) {
    val image by produceState(CacheMiniatures.lire(noeud.miniature), noeud.miniature) {
        val id = noeud.miniature
        if (id != null && value == null) value = withContext(Dispatchers.IO) { CacheMiniatures.charger(id) }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(taille)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(iconeType(noeud.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LigneNoeud(
    noeud: Noeud,
    detail: String,
    modeSelection: Boolean,
    selectionne: Boolean,
    onClic: () -> Unit,
    onClicLong: () -> Unit
) {
    val couleurs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selectionne) couleurs.primaryContainer.copy(alpha = 0.35f) else couleurs.background)
            .combinedClickable(onClick = onClic, onLongClick = onClicLong)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Miniature(noeud)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(noeud.nom, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = couleurs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (modeSelection) {
            Spacer(Modifier.width(10.dp))
            Icon(
                if (selectionne) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selectionne) couleurs.primary else couleurs.outline
            )
        }
    }
}
