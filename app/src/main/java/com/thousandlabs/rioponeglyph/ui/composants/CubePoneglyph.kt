package com.thousandlabs.rioponeglyph.ui.composants

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.thousandlabs.rioponeglyph.ui.theme.Pierre
import kotlin.math.min
import kotlin.random.Random

private class Glyphe(val cx: Float, val cy: Float, val segments: List<Pair<Offset, Offset>>)

private const val GRILLE = 6

/** Glyphes gravés, générés une fois pour toutes (toujours identiques). */
private fun genererGlyphes(): List<Glyphe> {
    val alea = Random(1138)
    val points = listOf(0f, 0.5f, 1f)
    val resultat = mutableListOf<Glyphe>()
    for (ligne in 0 until GRILLE) {
        for (col in 0 until GRILLE) {
            val cx = (col + 0.5f) / GRILLE
            val cy = (ligne + 0.5f) / GRILLE
            val dx = cx - 0.5f
            val dy = cy - 0.47f
            if (dx * dx + dy * dy < 0.30f * 0.30f) continue // zone de la serrure centrale
            val nombre = 2 + alea.nextInt(2)
            val segments = List(nombre) {
                var a: Offset
                var b: Offset
                do {
                    a = Offset(points[alea.nextInt(3)], points[alea.nextInt(3)])
                    b = Offset(points[alea.nextInt(3)], points[alea.nextInt(3)])
                } while (a == b)
                a to b
            }
            resultat += Glyphe(cx, cy, segments)
        }
    }
    return resultat.shuffled(Random(7))
}

/**
 * Cube Rio Poneglyph dessiné en vectoriel.
 * @param revelation de 0 à 1 : apparition progressive des glyphes.
 * @param eclat de 0 à 1 : intensité supplémentaire de la lueur.
 */
@Composable
fun CubePoneglyph(modifier: Modifier = Modifier, revelation: Float = 1f, eclat: Float = 0f) {
    val glyphes = remember { genererGlyphes() }
    Canvas(modifier) {
        val s = min(size.width, size.height)
        val face = s * 0.64f
        val profondeur = s * 0.2f
        val ox = (size.width - face - profondeur) / 2f
        val oy = (size.height - face - profondeur) / 2f + profondeur

        val avant = Path().apply {
            moveTo(ox, oy); lineTo(ox + face, oy); lineTo(ox + face, oy + face); lineTo(ox, oy + face); close()
        }
        val dessus = Path().apply {
            moveTo(ox, oy); lineTo(ox + profondeur, oy - profondeur)
            lineTo(ox + face + profondeur, oy - profondeur); lineTo(ox + face, oy); close()
        }
        val cote = Path().apply {
            moveTo(ox + face, oy); lineTo(ox + face + profondeur, oy - profondeur)
            lineTo(ox + face + profondeur, oy + face - profondeur); lineTo(ox + face, oy + face); close()
        }
        drawPath(dessus, Pierre.FaceDessus)
        drawPath(cote, Pierre.FaceCote)
        drawPath(avant, Pierre.FaceAvant)
        val arete = Stroke(width = s * 0.006f)
        drawPath(dessus, Pierre.Arete, style = arete)
        drawPath(cote, Pierre.Arete, style = arete)
        drawPath(avant, Pierre.Arete, style = arete)

        // Glyphes de la face avant
        val cellule = face / GRILLE
        val marge = cellule * 0.24f
        val n = glyphes.size
        glyphes.forEachIndexed { i, g ->
            val seuil = i.toFloat() / n * 0.75f
            val alpha = ((revelation - seuil) / 0.25f).coerceIn(0f, 1f)
            if (alpha <= 0f) return@forEachIndexed
            val gx = ox + g.cx * face - cellule / 2f + marge
            val gy = oy + g.cy * face - cellule / 2f + marge
            val t = cellule - 2 * marge
            g.segments.forEach { (a, b) ->
                val p1 = Offset(gx + a.x * t, gy + a.y * t)
                val p2 = Offset(gx + b.x * t, gy + b.y * t)
                trait(p1, p2, alpha, eclat, cellule)
            }
        }

        // Serrure centrale gravée
        val alphaSerrure = ((revelation - 0.6f) / 0.4f).coerceIn(0f, 1f)
        if (alphaSerrure > 0f) {
            val c = Offset(ox + face / 2f, oy + face * 0.47f)
            val anneau = face * 0.24f
            cercle(c, anneau, alphaSerrure, eclat, cellule)
            cercle(c, anneau * 0.78f, alphaSerrure * 0.7f, eclat, cellule)
            val r = face * 0.075f
            val tete = Offset(c.x, c.y - face * 0.05f)
            cercle(tete, r, alphaSerrure, eclat, cellule)
            val haut = tete.y + r * 0.8f
            val bas = c.y + face * 0.13f
            trait(Offset(c.x - r * 0.45f, haut), Offset(c.x - r * 0.9f, bas), alphaSerrure, eclat, cellule)
            trait(Offset(c.x + r * 0.45f, haut), Offset(c.x + r * 0.9f, bas), alphaSerrure, eclat, cellule)
            trait(Offset(c.x - r * 0.9f, bas), Offset(c.x + r * 0.9f, bas), alphaSerrure, eclat, cellule)
        }
    }
}

private fun DrawScope.trait(p1: Offset, p2: Offset, alpha: Float, eclat: Float, cellule: Float) {
    drawLine(
        Pierre.Lueur.copy(alpha = (alpha * (0.18f + 0.3f * eclat)).coerceAtMost(1f)),
        p1, p2, strokeWidth = cellule * 0.2f, cap = StrokeCap.Round
    )
    drawLine(Pierre.Lueur.copy(alpha = alpha), p1, p2, strokeWidth = cellule * 0.055f, cap = StrokeCap.Round)
}

private fun DrawScope.cercle(c: Offset, rayon: Float, alpha: Float, eclat: Float, cellule: Float) {
    drawCircle(
        Pierre.Lueur.copy(alpha = (alpha * (0.18f + 0.3f * eclat)).coerceAtMost(1f)),
        rayon, c, style = Stroke(width = cellule * 0.2f)
    )
    drawCircle(Pierre.Lueur.copy(alpha = alpha), rayon, c, style = Stroke(width = cellule * 0.055f))
}
