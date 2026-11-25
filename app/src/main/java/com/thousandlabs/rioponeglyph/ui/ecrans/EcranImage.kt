package com.thousandlabs.rioponeglyph.ui.ecrans

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.thousandlabs.rioponeglyph.coffre.Coffre
import com.thousandlabs.rioponeglyph.coffre.FormatChiffre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface EtatImage {
    data object Chargement : EtatImage
    data class Prete(val image: ImageBitmap) : EtatImage
    data object Erreur : EtatImage
}

/** Visionneuse d'images : déchiffrement en mémoire uniquement. */
@Composable
fun EcranImage(id: String, onRetour: () -> Unit) {
    val etat by produceState<EtatImage>(EtatImage.Chargement, id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val octets = FormatChiffre.dechiffrerOctets(Coffre.cle(), Coffre.fichierObjet(id))
                val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(octets, 0, octets.size, limites)
                var echantillon = 1
                while (maxOf(limites.outWidth, limites.outHeight) / echantillon > 4096) echantillon *= 2
                val image = BitmapFactory.decodeByteArray(octets, 0, octets.size, BitmapFactory.Options().apply { inSampleSize = echantillon })
                if (image != null) EtatImage.Prete(image.asImageBitmap()) else EtatImage.Erreur
            }.getOrDefault(EtatImage.Erreur)
        }
    }
    var echelle by remember { mutableFloatStateOf(1f) }
    var decalage by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, deplacement, zoom, _ ->
                    echelle = (echelle * zoom).coerceIn(1f, 6f)
                    decalage = if (echelle == 1f) Offset.Zero else decalage + deplacement
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (echelle > 1f) { echelle = 1f; decalage = Offset.Zero } else echelle = 2.5f
                })
            },
        contentAlignment = Alignment.Center
    ) {
        when (val e = etat) {
            EtatImage.Chargement -> CircularProgressIndicator()
            EtatImage.Erreur -> Text("Impossible d'afficher cette image.", color = Color.White)
            is EtatImage.Prete -> Image(
                e.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = echelle, scaleY = echelle, translationX = decalage.x, translationY = decalage.y)
            )
        }
        IconButton(
            onClick = onRetour,
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(4.dp)
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour", tint = Color.White)
        }
    }
}
