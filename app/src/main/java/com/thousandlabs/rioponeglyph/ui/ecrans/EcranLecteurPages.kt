package com.thousandlabs.rioponeglyph.ui.ecrans

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.thousandlabs.rioponeglyph.Reglages
import com.thousandlabs.rioponeglyph.coffre.Coffre
import com.thousandlabs.rioponeglyph.coffre.FormatChiffre
import com.thousandlabs.rioponeglyph.coffre.Page
import com.thousandlabs.rioponeglyph.coffre.Positions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ModeLecture(val libelle: String) {
    DEFILEMENT("Défilement vertical"),
    PLANCHES("Planche par planche"),
    FICHE("Aperçu des pages")
}

/** Pages déchiffrées en mémoire uniquement, dans la limite d'une part de la mémoire de l'application. */
private object CachePages {
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 5).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    fun lire(cle: String): ImageBitmap? = cache.get(cle)

    fun charger(objet: String, largeurMax: Int): ImageBitmap? {
        val cle = "$objet@$largeurMax"
        cache.get(cle)?.let { return it }
        val image = runCatching {
            val octets = FormatChiffre.dechiffrerOctets(Coffre.cle(), Coffre.fichierObjet(objet))
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(octets, 0, octets.size, limites)
            if (limites.outWidth <= 0 || limites.outHeight <= 0) return@runCatching null
            var echantillon = 1
            while (limites.outWidth / (echantillon * 2) >= largeurMax ||
                (limites.outWidth.toLong() / echantillon) * (limites.outHeight.toLong() / echantillon) > 16_000_000L
            ) echantillon *= 2
            BitmapFactory.decodeByteArray(octets, 0, octets.size, BitmapFactory.Options().apply { inSampleSize = echantillon })
                ?.asImageBitmap()
        }.getOrNull()
        if (image != null) cache.put(cle, image)
        return image
    }
}

@Composable
private fun imagePage(objet: String, largeurMax: Int): ImageBitmap? {
    val image by produceState(CachePages.lire("$objet@$largeurMax"), objet, largeurMax) {
        if (value == null) value = withContext(Dispatchers.IO) { CachePages.charger(objet, largeurMax) }
    }
    return image
}

private fun Context.activiteLecteur(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun modeInitial(sens: String): Pair<ModeLecture, Boolean> = when (sens.lowercase()) {
    "gauche-droite" -> ModeLecture.PLANCHES to false
    "droite-gauche" -> ModeLecture.PLANCHES to true
    else -> ModeLecture.DEFILEMENT to false
}

@Composable
fun EcranLecteurPages(id: String, onRetour: () -> Unit, onSuivant: (com.thousandlabs.rioponeglyph.coffre.Noeud) -> Unit = {}) {
    val contexte = LocalContext.current
    val noeud = remember(id) { Coffre.noeud(id) }
    if (noeud == null || noeud.pages.isEmpty()) {
        LaunchedEffect(Unit) { onRetour() }
        return
    }
    val pages = noeud.pages
    val suivant = remember(id) { Coffre.suivant(id) }
    val clePosition = "pages:$id"
    val reprise = remember { Positions.lire(contexte, clePosition).toInt().coerceIn(0, pages.lastIndex) }
    val (modeDepart, rtlDepart) = remember { modeInitial(noeud.sens) }
    var mode by remember { mutableStateOf(modeDepart) }
    var dernierModeLecture by remember { mutableStateOf(if (modeDepart == ModeLecture.FICHE) ModeLecture.DEFILEMENT else modeDepart) }
    var droiteAGauche by remember { mutableStateOf(rtlDepart) }
    var pageCourante by remember { mutableIntStateOf(reprise) }
    var controles by remember { mutableStateOf(true) }
    val portee = rememberCoroutineScope()

    val densite = LocalDensity.current
    val largeurEcran = with(densite) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val largeurPage = largeurEcran.coerceIn(480, 1440)

    val etatListe = rememberLazyListState(reprise)
    val etatPager = rememberPagerState(reprise) { pages.size }

    // Plein écran pendant la lecture.
    DisposableEffect(Unit) {
        val fenetre = contexte.activiteLecteur()?.window
        val controleur = fenetre?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controleur?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controleur?.hide(WindowInsetsCompat.Type.systemBars())
        fenetre?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controleur?.show(WindowInsetsCompat.Type.systemBars())
            fenetre?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Synchronisation de la page courante avec le mode affiché.
    LaunchedEffect(mode) {
        when (mode) {
            ModeLecture.DEFILEMENT -> {
                etatListe.scrollToItem(pageCourante)
                snapshotFlow { etatListe.firstVisibleItemIndex }.collect { pageCourante = it }
            }
            ModeLecture.PLANCHES -> {
                etatPager.scrollToPage(pageCourante)
                snapshotFlow { etatPager.currentPage }.collect { pageCourante = it }
            }
            ModeLecture.FICHE -> Unit
        }
    }
    LaunchedEffect(pageCourante) { Positions.enregistrer(contexte, clePosition, pageCourante.toLong()) }

    fun allerA(index: Int) {
        val cible = index.coerceIn(0, pages.lastIndex)
        pageCourante = cible
        portee.launch {
            when (mode) {
                ModeLecture.DEFILEMENT -> etatListe.scrollToItem(cible)
                ModeLecture.PLANCHES -> etatPager.scrollToPage(cible)
                ModeLecture.FICHE -> Unit
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (mode) {
            ModeLecture.DEFILEMENT -> LazyColumn(
                state = etatListe,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { controles = !controles }) }
            ) {
                itemsIndexed(pages, key = { _, p -> p.objet }) { _, p -> PageVerticale(p, largeurPage) }
            }

            ModeLecture.PLANCHES -> HorizontalPager(
                state = etatPager,
                reverseLayout = droiteAGauche,
                beyondViewportPageCount = 1,
                key = { pages[it].objet },
                modifier = Modifier.fillMaxSize()
            ) { i ->
                PageZoomable(pages[i], largeurPage) { controles = !controles }
            }

            ModeLecture.FICHE -> LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 72.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(pages, key = { _, p -> p.objet }) { i, p ->
                    Vignette(p, i + 1, i == pageCourante) {
                        pageCourante = i
                        mode = dernierModeLecture
                    }
                }
            }
        }

        AnimatedVisibility(
            controles || mode == ModeLecture.FICHE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.4f))))
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(noeud.nom, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Page ${pageCourante + 1} sur ${pages.size} · ${mode.libelle}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    BoutonMode(Icons.Outlined.ViewDay, "Défilement vertical", mode == ModeLecture.DEFILEMENT) {
                        mode = ModeLecture.DEFILEMENT; dernierModeLecture = mode
                    }
                    BoutonMode(Icons.Outlined.AutoStories, "Planche par planche", mode == ModeLecture.PLANCHES) {
                        mode = ModeLecture.PLANCHES; dernierModeLecture = mode
                    }
                    BoutonMode(Icons.Outlined.GridView, "Aperçu des pages", mode == ModeLecture.FICHE) {
                        mode = ModeLecture.FICHE
                    }
                }
                if (mode == ModeLecture.PLANCHES) {
                    TextButton(onClick = { droiteAGauche = !droiteAGauche }) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(
                            if (droiteAGauche) "  Sens : de droite à gauche (manga)" else "  Sens : de gauche à droite",
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Continuité : à la dernière page, accès direct au chapitre suivant du même dossier.
        if (Reglages.chapitreSuivant && mode != ModeLecture.FICHE && suivant != null && pageCourante == pages.lastIndex) {
            Button(
                onClick = { onSuivant(suivant) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp, start = 24.dp, end = 24.dp)
            ) {
                Text("Chapitre suivant : ${suivant.nom}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
        }

        if (mode != ModeLecture.FICHE && pages.size > 1) {
            AnimatedVisibility(controles, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                var glissement by remember { mutableStateOf<Float?>(null) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Slider(
                        value = glissement ?: pageCourante.toFloat(),
                        onValueChange = { glissement = it },
                        onValueChangeFinished = {
                            glissement?.let { allerA(it.toInt()) }
                            glissement = null
                        },
                        valueRange = 0f..pages.lastIndex.toFloat(),
                        modifier = Modifier.graphicsLayer(scaleX = if (mode == ModeLecture.PLANCHES && droiteAGauche) -1f else 1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoutonMode(icone: androidx.compose.ui.graphics.vector.ImageVector, description: String, actif: Boolean, onClic: () -> Unit) {
    IconButton(
        onClick = onClic,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (actif) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent
        )
    ) {
        Icon(icone, contentDescription = description, tint = Color.White)
    }
}

@Composable
private fun PageVerticale(page: Page, largeur: Int) {
    val image = imagePage(page.objet, largeur)
    if (image != null) {
        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(480.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f)) }
    }
}

/** Planche avec zoom à deux doigts et double appui ; le glissement à un doigt tourne la page. */
@Composable
private fun PageZoomable(page: Page, largeur: Int, onAppui: () -> Unit) {
    val image = imagePage(page.objet, largeur)
    var echelle by remember { mutableFloatStateOf(1f) }
    var decalage by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onAppui() },
                    onDoubleTap = {
                        if (echelle > 1f) { echelle = 1f; decalage = Offset.Zero } else echelle = 2.5f
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val evenement = awaitPointerEvent()
                        val plusieurs = evenement.changes.count { it.pressed } > 1
                        if (plusieurs || echelle > 1f) {
                            echelle = (echelle * evenement.calculateZoom()).coerceIn(1f, 5f)
                            val maxX = (echelle - 1f) * size.width / 2f
                            val maxY = (echelle - 1f) * size.height / 2f
                            decalage = if (echelle == 1f) Offset.Zero else {
                                val d = decalage + evenement.calculatePan()
                                Offset(d.x.coerceIn(-maxX, maxX), d.y.coerceIn(-maxY, maxY))
                            }
                            evenement.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (evenement.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = echelle, scaleY = echelle, translationX = decalage.x, translationY = decalage.y)
            )
        } else {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun Vignette(page: Page, numero: Int, courante: Boolean, onClic: () -> Unit) {
    val image = imagePage(page.objet, 320)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClic)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (courante) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(if (courante) 3.dp else 0.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Text("$numero", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}
