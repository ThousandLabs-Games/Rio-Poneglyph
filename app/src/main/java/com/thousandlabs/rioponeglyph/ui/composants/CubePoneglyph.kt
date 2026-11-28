package com.thousandlabs.rioponeglyph.ui.composants

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.thousandlabs.rioponeglyph.R
import com.thousandlabs.rioponeglyph.StyleAnimation

/** Largeur du fondu d'allumage d'un symbole, dans l'échelle 0-255 de la carte d'ordre. */
private const val FONDU = 40f

/**
 * Poneglyph Rio : la même pierre gravée que l'icône de l'application.
 *
 * Calques tirés de l'icône : la pierre (poneglyph_pierre), la lumière de ses gravures
 * (poneglyph_lumiere) et, pour le style « symboles », la carte d'ordre d'allumage
 * (poneglyph_ordre : chaque symbole porte son moment d'allumage, la serrure en dernier).
 *
 * @param revelation de 0 à 1 : progression de l'allumage des gravures.
 * @param eclat de 0 à 1 : intensité supplémentaire de la lueur.
 * @param apaisement de 0 à 1 : la lueur retombe et la pierre retrouve son aspect normal.
 *   Valeurs par défaut : pierre immobile, identique à l'icône.
 */
@Composable
fun CubePoneglyph(
    modifier: Modifier = Modifier,
    revelation: Float = 1f,
    eclat: Float = 0f,
    apaisement: Float = 1f,
    style: StyleAnimation = StyleAnimation.VAGUE
) {
    val luminosite = (0.42f + 0.18f * revelation + 0.1f * eclat)
        .let { it + (1f - it) * apaisement }
        .coerceIn(0f, 1f)
    val intensiteLueur = ((0.85f + 0.15f * eclat) * (1f - apaisement)).coerceIn(0f, 1f)

    Box(modifier.clip(RoundedCornerShape(14))) {
        Image(
            painter = painterResource(R.drawable.poneglyph_pierre),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (luminosite < 0.999f) ColorFilter.colorMatrix(
                ColorMatrix().apply { setToScale(luminosite, luminosite, luminosite, 1f) }
            ) else null,
            modifier = Modifier.fillMaxSize()
        )
        if (intensiteLueur > 0f && revelation > 0f) {
            val ordre: ImageBitmap? =
                if (style == StyleAnimation.SYMBOLES) ImageBitmap.imageResource(R.drawable.poneglyph_ordre) else null
            Image(
                painter = painterResource(R.drawable.poneglyph_lumiere),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = intensiteLueur,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (ordre == null) {
                            // Vague : visible au-dessus du front, fondu, invisible en dessous.
                            val bande = size.height * 0.16f
                            val front = revelation * (size.height + bande)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startY = front - bande,
                                    endY = front
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        } else {
                            // Symboles : opacité = k × (progression − ordre du symbole), bornée à 0-255.
                            val k = 255f / FONDU
                            val masque = ColorMatrix(
                                floatArrayOf(
                                    0f, 0f, 0f, 0f, 0f,
                                    0f, 0f, 0f, 0f, 0f,
                                    0f, 0f, 0f, 0f, 0f,
                                    -k, 0f, 0f, 0f, k * revelation * 255f
                                )
                            )
                            drawImage(
                                image = ordre,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(ordre.width, ordre.height),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                colorFilter = ColorFilter.colorMatrix(masque),
                                blendMode = BlendMode.DstIn,
                                filterQuality = FilterQuality.None
                            )
                        }
                    }
            )
        }
    }
}
