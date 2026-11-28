package com.thousandlabs.rioponeglyph.ui.ecrans

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thousandlabs.rioponeglyph.Reglages
import com.thousandlabs.rioponeglyph.StyleAnimation
import com.thousandlabs.rioponeglyph.ui.composants.CubePoneglyph
import com.thousandlabs.rioponeglyph.ui.theme.Pierre
import kotlinx.coroutines.delay

/**
 * Animation d'entrée : la pierre de l'icône apparaît éteinte, ses gravures s'allument
 * (en vague ou symbole par symbole selon les réglages), puis la lueur retombe.
 * Un appui sur l'écran passe l'animation.
 */
@Composable
fun EcranOuverture(onTermine: () -> Unit) {
    val style = Reglages.styleAnimation
    val dureeMs = Reglages.dureeAnimation.revelationMs
    val revelation = remember { Animatable(0f) }
    val eclat = remember { Animatable(0f) }
    val apaisement = remember { Animatable(0f) }
    val terminer by rememberUpdatedState(onTermine)
    val fini = remember { booleanArrayOf(false) }
    fun finir() {
        if (fini[0]) return
        fini[0] = true
        terminer()
    }

    LaunchedEffect(Unit) {
        delay(200)
        // Les symboles s'allument à vitesse régulière ; la vague ralentit en fin de course.
        val courbe = if (style == StyleAnimation.SYMBOLES) LinearEasing else LinearOutSlowInEasing
        revelation.animateTo(1f, tween(dureeMs, easing = courbe))
        eclat.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        eclat.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        apaisement.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        finir()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Pierre.Fond)
            .pointerInput(Unit) { detectTapGestures { finir() } },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CubePoneglyph(Modifier.size(220.dp), revelation.value, eclat.value, apaisement.value, style)
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
        Text(
            "Touchez pour passer",
            fontSize = 12.sp,
            color = Pierre.TexteDiscret,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .alpha(0.7f)
        )
    }
}
