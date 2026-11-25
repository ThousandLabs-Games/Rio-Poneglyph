package com.thousandlabs.rioponeglyph.ui.ecrans

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.thousandlabs.rioponeglyph.coffre.Coffre
import com.thousandlabs.rioponeglyph.coffre.Positions
import com.thousandlabs.rioponeglyph.lecteur.SourceChiffree
import com.thousandlabs.rioponeglyph.util.Formats
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val VITESSES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun Context.activite(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun libelleVitesse(v: Float): String =
    if (v == v.toInt().toFloat()) "${v.toInt()}x" else "${v.toString().replace('.', ',')}x"

@OptIn(UnstableApi::class)
@Composable
fun EcranLecteurVideo(id: String, onRetour: () -> Unit) {
    val contexte = LocalContext.current
    val activite = remember { contexte.activite() }
    val noeud = remember(id) { Coffre.noeud(id) }
    if (noeud == null) {
        LaunchedEffect(Unit) { onRetour() }
        return
    }

    val lecteur = remember(id) {
        ExoPlayer.Builder(contexte).build().apply {
            val cle = Coffre.cle()
            val fabrique = DataSource.Factory { SourceChiffree(cle) }
            val source = ProgressiveMediaSource.Factory(fabrique)
                .createMediaSource(MediaItem.fromUri(Uri.fromFile(Coffre.fichierObjet(id))))
            val reprise = Positions.lire(contexte, id)
            setMediaSource(source, if (noeud.duree <= 0 || reprise < noeud.duree - 5_000) reprise else 0L)
            prepare()
            playWhenReady = true
        }
    }

    fun enregistrerPosition() {
        val fin = lecteur.playbackState == Player.STATE_ENDED
        Positions.enregistrer(contexte, id, if (fin) 0L else lecteur.currentPosition)
    }

    DisposableEffect(lecteur) {
        onDispose {
            enregistrerPosition()
            lecteur.release()
        }
    }

    // Plein écran paysage pendant la lecture.
    DisposableEffect(Unit) {
        val fenetre = activite?.window
        val ancienneOrientation = activite?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activite?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controleur = fenetre?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controleur?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controleur?.hide(WindowInsetsCompat.Type.systemBars())
        fenetre?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activite?.requestedOrientation = ancienneOrientation
            controleur?.show(WindowInsetsCompat.Type.systemBars())
            fenetre?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            fenetre?.let { f ->
                f.attributes = f.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }

    // Pause et sauvegarde dès que l'application passe en arrière-plan.
    val cycle = LocalLifecycleOwner.current
    DisposableEffect(cycle) {
        val observateur = LifecycleEventObserver { _, evenement ->
            if (evenement == Lifecycle.Event.ON_STOP) {
                lecteur.pause()
                enregistrerPosition()
            }
        }
        cycle.lifecycle.addObserver(observateur)
        onDispose { cycle.lifecycle.removeObserver(observateur) }
    }

    var erreur by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lecteur) {
        val ecouteur = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                erreur = "Lecture impossible (${error.errorCodeName}). Le format de cette vidéo n'est peut-être pas pris en charge."
            }
        }
        lecteur.addListener(ecouteur)
        onDispose { lecteur.removeListener(ecouteur) }
    }

    var position by remember { mutableLongStateOf(0L) }
    var duree by remember { mutableLongStateOf(0L) }
    var enLecture by remember { mutableStateOf(true) }
    LaunchedEffect(lecteur) {
        var tours = 0
        while (true) {
            position = lecteur.currentPosition
            duree = lecteur.duration.takeIf { it > 0 } ?: 0L
            enLecture = lecteur.isPlaying
            delay(200)
            if (++tours % 50 == 0) enregistrerPosition()
        }
    }

    var controles by remember { mutableStateOf(true) }
    var activiteUtilisateur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(controles, activiteUtilisateur, enLecture) {
        if (controles && enLecture) {
            delay(3500)
            controles = false
        }
    }
    var indicateur by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(indicateur) {
        if (indicateur != null) {
            delay(900)
            indicateur = null
        }
    }
    var glissement by remember { mutableStateOf<Float?>(null) }
    var vitesse by remember { mutableFloatStateOf(1f) }
    var menuVitesse by remember { mutableStateOf(false) }

    val audio = remember { contexte.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val volumeMax = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    fun sauter(ecart: Long) {
        val cible = (lecteur.currentPosition + ecart).coerceIn(0L, if (duree > 0) duree else Long.MAX_VALUE)
        lecteur.seekTo(cible)
        indicateur = (if (ecart > 0) "+" else "−") + "${kotlin.math.abs(ecart) / 1000} s"
        activiteUtilisateur = System.nanoTime()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = lecteur
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Couche des gestes : appui, double appui, glissements verticaux.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controles = !controles
                            activiteUtilisateur = System.nanoTime()
                        },
                        onDoubleTap = { o -> sauter(if (o.x < size.width / 2f) -10_000L else 10_000L) }
                    )
                }
                .pointerInput(Unit) {
                    var cote = 0
                    var valeur = 0f
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            cote = if (o.x < size.width / 2f) -1 else 1
                            valeur = if (cote < 0) {
                                val actuelle = activite?.window?.attributes?.screenBrightness ?: -1f
                                if (actuelle >= 0f) actuelle else runCatching {
                                    Settings.System.getInt(contexte.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                                }.getOrDefault(0.5f)
                            } else {
                                audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / volumeMax
                            }
                        },
                        onDragEnd = { cote = 0 },
                        onDragCancel = { cote = 0 },
                        onVerticalDrag = { changement, delta ->
                            changement.consume()
                            valeur = (valeur - delta / (size.height * 0.8f)).coerceIn(0f, 1f)
                            if (cote < 0) {
                                activite?.window?.let { f ->
                                    f.attributes = f.attributes.apply { screenBrightness = valeur.coerceAtLeast(0.01f) }
                                }
                                indicateur = "Luminosité ${(valeur * 100).roundToInt()} %"
                            } else if (cote > 0) {
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (valeur * volumeMax).roundToInt(), 0)
                                indicateur = "Volume ${(valeur * 100).roundToInt()} %"
                            }
                        }
                    )
                }
        )

        indicateur?.let {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }

        erreur?.let {
            Text(
                it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(16.dp)
            )
        }

        AnimatedVisibility(controles, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Barre du haut
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour", tint = Color.White)
                    }
                    Text(
                        noeud.nom,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        TextButton(onClick = { menuVitesse = true; activiteUtilisateur = System.nanoTime() }) {
                            Text("Vitesse ${libelleVitesse(vitesse)}", color = Color.White)
                        }
                        DropdownMenu(expanded = menuVitesse, onDismissRequest = { menuVitesse = false }) {
                            VITESSES.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text((if (v == vitesse) "• " else "") + libelleVitesse(v)) },
                                    onClick = {
                                        vitesse = v
                                        lecteur.setPlaybackSpeed(v)
                                        menuVitesse = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Commandes centrales
                Row(
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    IconButton(onClick = { sauter(-10_000L) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.Replay10, contentDescription = "Reculer de 10 secondes", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(
                        onClick = {
                            if (lecteur.playbackState == Player.STATE_ENDED) lecteur.seekTo(0L)
                            if (lecteur.isPlaying) lecteur.pause() else lecteur.play()
                            activiteUtilisateur = System.nanoTime()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            if (enLecture) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (enLecture) "Pause" else "Lecture",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    IconButton(onClick = { sauter(10_000L) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.Forward10, contentDescription = "Avancer de 10 secondes", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Barre de progression
                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    val affichee = glissement?.toLong() ?: position
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Formats.duree(affichee), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(12.dp))
                        Slider(
                            value = (glissement ?: position.toFloat()).coerceIn(0f, duree.toFloat().coerceAtLeast(1f)),
                            onValueChange = {
                                glissement = it
                                activiteUtilisateur = System.nanoTime()
                            },
                            onValueChangeFinished = {
                                glissement?.let { lecteur.seekTo(it.toLong()) }
                                glissement = null
                            },
                            valueRange = 0f..duree.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(Formats.duree(duree), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
