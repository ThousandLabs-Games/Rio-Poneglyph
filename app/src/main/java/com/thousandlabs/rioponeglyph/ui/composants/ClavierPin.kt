package com.thousandlabs.rioponeglyph.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val PIN_MIN = 4
const val PIN_MAX = 8

@Composable
fun ClavierPin(
    valeur: String,
    onChange: (String) -> Unit,
    onValider: () -> Unit,
    actif: Boolean,
    erreur: Boolean = false
) {
    val couleurs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val cases = maxOf(PIN_MIN, valeur.length)
            repeat(cases) { i ->
                val remplie = i < valeur.length
                val teinte = if (erreur) couleurs.error else couleurs.primary
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (remplie) teinte else couleurs.surface)
                        .border(1.5.dp, if (remplie) teinte else couleurs.outline, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        val lignes = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("EFF", "0", "OK")
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            lignes.forEach { ligne ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    ligne.forEach { touche ->
                        when (touche) {
                            "EFF" -> Touche(actif && valeur.isNotEmpty(), { onChange(valeur.dropLast(1)) }) {
                                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Effacer")
                            }
                            "OK" -> Touche(actif && valeur.length >= PIN_MIN, onValider, accent = true) {
                                Icon(Icons.Outlined.Check, contentDescription = "Valider")
                            }
                            else -> Touche(actif && valeur.length < PIN_MAX, { onChange(valeur + touche) }) {
                                Text(touche, fontSize = 26.sp, color = couleurs.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Touche(
    actif: Boolean,
    onClic: () -> Unit,
    accent: Boolean = false,
    contenu: @Composable () -> Unit
) {
    val couleurs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (accent) couleurs.primaryContainer else couleurs.surfaceContainerHigh)
            .clickable(enabled = actif, onClick = onClic)
            .alpha(if (actif) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) { contenu() }
}
