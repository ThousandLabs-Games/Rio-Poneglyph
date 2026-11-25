package com.thousandlabs.rioponeglyph.ui.composants

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

const val SCHEMA_MIN = 4

/**
 * Grille de schéma 3 x 3. [reinitialisation] : changer cette valeur efface le tracé.
 */
@Composable
fun GrilleSchema(
    actif: Boolean,
    erreur: Boolean,
    reinitialisation: Int,
    onTermine: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    var points by remember(reinitialisation) { mutableStateOf(listOf<Int>()) }
    var doigt by remember(reinitialisation) { mutableStateOf<Offset?>(null) }
    val couleurs = MaterialTheme.colorScheme
    val teinte = if (erreur) couleurs.error else couleurs.primary

    Canvas(
        modifier
            .size(280.dp)
            .pointerInput(actif, reinitialisation) {
                if (!actif) return@pointerInput
                val cellule = size.width / 3f
                fun centre(i: Int) = Offset(cellule * (i % 3) + cellule / 2f, cellule * (i / 3) + cellule / 2f)
                fun noeudSous(p: Offset): Int? =
                    (0..8).firstOrNull { (centre(it) - p).getDistance() < cellule * 0.34f }

                fun ajouter(n: Int) {
                    if (n in points) return
                    val liste = points.toMutableList()
                    val precedent = liste.lastOrNull()
                    if (precedent != null) {
                        val lA = precedent / 3; val cA = precedent % 3
                        val lB = n / 3; val cB = n % 3
                        if ((lA + lB) % 2 == 0 && (cA + cB) % 2 == 0) {
                            val milieu = ((lA + lB) / 2) * 3 + (cA + cB) / 2
                            if (milieu != precedent && milieu != n && milieu !in liste) liste.add(milieu)
                        }
                    }
                    liste.add(n)
                    points = liste
                }

                detectDragGestures(
                    onDragStart = { depart ->
                        points = emptyList()
                        doigt = depart
                        noeudSous(depart)?.let { ajouter(it) }
                    },
                    onDrag = { changement, _ ->
                        changement.consume()
                        doigt = changement.position
                        noeudSous(changement.position)?.let { ajouter(it) }
                    },
                    onDragEnd = {
                        doigt = null
                        val trace = points
                        if (trace.isNotEmpty()) onTermine(trace)
                    },
                    onDragCancel = {
                        doigt = null
                        points = emptyList()
                    }
                )
            }
    ) {
        val cellule = size.width / 3f
        fun centre(i: Int) = Offset(cellule * (i % 3) + cellule / 2f, cellule * (i / 3) + cellule / 2f)
        val epaisseur = cellule * 0.06f
        for (i in 1 until points.size) {
            drawLine(teinte.copy(alpha = 0.8f), centre(points[i - 1]), centre(points[i]), epaisseur, StrokeCap.Round)
        }
        val dernier = points.lastOrNull()
        val position = doigt
        if (dernier != null && position != null) {
            drawLine(teinte.copy(alpha = 0.5f), centre(dernier), position, epaisseur, StrokeCap.Round)
        }
        for (i in 0..8) {
            val choisi = i in points
            drawCircle(if (choisi) teinte else couleurs.outline, cellule * 0.075f, centre(i))
            if (choisi) drawCircle(teinte.copy(alpha = 0.6f), cellule * 0.2f, centre(i), style = Stroke(cellule * 0.025f))
        }
    }
}
