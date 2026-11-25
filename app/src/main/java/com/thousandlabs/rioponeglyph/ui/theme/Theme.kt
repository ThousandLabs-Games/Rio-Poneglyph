package com.thousandlabs.rioponeglyph.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette pierre / ardoise. */
object Pierre {
    val Fond = Color(0xFF0E1114)
    val Surface = Color(0xFF161A1F)
    val SurfaceHaute = Color(0xFF1F252C)
    val Contour = Color(0xFF3A434D)
    val FaceAvant = Color(0xFF2A333C)
    val FaceDessus = Color(0xFF38424C)
    val FaceCote = Color(0xFF1D2329)
    val Arete = Color(0xFF4C5864)
    val Lueur = Color(0xFFA9D4EC)
    val Texte = Color(0xFFE3E7EA)
    val TexteDiscret = Color(0xFF9AA4AD)
    val Erreur = Color(0xFFE07A6F)
}

private val Schema = darkColorScheme(
    primary = Color(0xFF8FB8CF),
    onPrimary = Color(0xFF0B1720),
    primaryContainer = Color(0xFF26404F),
    onPrimaryContainer = Color(0xFFD3E8F4),
    secondary = Color(0xFFA7B0B8),
    onSecondary = Color(0xFF151A1F),
    secondaryContainer = Color(0xFF2C343C),
    onSecondaryContainer = Color(0xFFDCE2E7),
    background = Pierre.Fond,
    onBackground = Pierre.Texte,
    surface = Pierre.Fond,
    onSurface = Pierre.Texte,
    surfaceVariant = Pierre.SurfaceHaute,
    onSurfaceVariant = Pierre.TexteDiscret,
    surfaceContainerLowest = Color(0xFF0A0D0F),
    surfaceContainerLow = Pierre.Surface,
    surfaceContainer = Color(0xFF1A1F24),
    surfaceContainerHigh = Pierre.SurfaceHaute,
    surfaceContainerHighest = Color(0xFF262D35),
    outline = Pierre.Contour,
    outlineVariant = Color(0xFF2A3139),
    error = Pierre.Erreur,
    onError = Color(0xFF2A0C08)
)

@Composable
fun RioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Schema, typography = Typography(), content = content)
}
