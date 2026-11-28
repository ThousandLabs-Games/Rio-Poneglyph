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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.thousandlabs.rioponeglyph.R

/**
 * Poneglyph Rio : la même pierre gravée que l'icône de l'application.
 *
 * Deux calques tirés de l'icône : la pierre (poneglyph_pierre) et la lumière de ses gravures
 * (poneglyph_lumiere), découpée dans la même image pour que les symboles soient identiques.
 *
 * @param revelation de 0 à 1 : la lumière descend du haut vers le bas et allume les gravures.
 * @param eclat de 0 à 1 : intensité supplémentaire de la lueur.
 * @param apaisement de 0 à 1 : la lueur retombe et la pierre retrouve son aspect normal.
 *   Valeurs par défaut : pierre immobile, identique à l'icône.
 */
@Composable
fun CubePoneglyph(
    modifier: Modifier = Modifier,
    revelation: Float = 1f,
    eclat: Float = 0f,
    apaisement: Float = 1f
) {
    // Pierre assombrie au départ, pleine luminosité à la fin.
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
                        // Front de lumière : visible au-dessus, fondu progressif, invisible en dessous.
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
                    }
            )
        }
    }
}
