package com.thousandlabs.rioponeglyph.ui.ecrans

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thousandlabs.rioponeglyph.ui.composants.CubePoneglyph
import com.thousandlabs.rioponeglyph.ui.theme.Pierre

/** Animation d'entrée : les glyphes du cube s'illuminent progressivement. */
@Composable
fun EcranOuverture(onTermine: () -> Unit) {
    val revelation = remember { Animatable(0f) }
    val eclat = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        revelation.animateTo(1f, tween(1400, easing = LinearOutSlowInEasing))
        eclat.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        eclat.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        onTermine()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Pierre.Fond),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CubePoneglyph(Modifier.size(220.dp), revelation.value, eclat.value)
            Spacer(Modifier.height(24.dp))
            Text(
                "Rio Poneglyph",
                style = MaterialTheme.typography.headlineMedium,
                color = Pierre.Texte,
                modifier = Modifier.alpha(revelation.value)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Thousand Labs Games",
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                color = Pierre.TexteDiscret,
                modifier = Modifier.alpha(revelation.value)
            )
        }
    }
}
