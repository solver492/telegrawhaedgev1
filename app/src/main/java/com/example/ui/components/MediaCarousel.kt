package com.example.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.ParsedMediaItem
import com.example.util.YouTubeHelper
import java.io.File

/**
 * Carrousel multimédia fluide pour messages Telegram et fiches Produits.
 * Supporte les photos (avec zoom plein écran), les vidéos YouTube (lecture inline iFrame)
 * et les vidéos MP4/WebM natives (lecture continue sans coupure).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCarousel(
    mediaItems: List<ParsedMediaItem>,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    contentScale: ContentScale = ContentScale.Crop,
    showIndicator: Boolean = true,
    onMediaTapped: ((Int) -> Unit)? = null
) {
    if (mediaItems.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { mediaItems.size })
    var fullScreenItemIndex by remember { mutableStateOf<Int?>(null) }
    var activeInlineVideoIndex by remember { mutableIntStateOf(-1) }

    // Si on change de page dans le carrousel, on arrête la lecture vidéo inline de la page précédente
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        if (activeInlineVideoIndex != pagerState.currentPage) {
            activeInlineVideoIndex = -1
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF151419))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = mediaItems[page]
            val isYouTube = remember(item.url) { YouTubeHelper.isYouTubeUrl(item.url) }
            val displayModel = remember(item, isYouTube) {
                if (isYouTube) {
                    item.getDisplayModel() ?: YouTubeHelper.getThumbnailUrl(item.url)
                } else {
                    item.getDisplayModel()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        if (!item.isVideo && !isYouTube) {
                            if (onMediaTapped != null) {
                                onMediaTapped(page)
                            } else {
                                fullScreenItemIndex = page
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo || isYouTube) {
                    if (activeInlineVideoIndex == page) {
                        // Lecteur vidéo inline actif
                        if (isYouTube) {
                            YouTubeIFramePlayer(
                                youtubeUrl = item.url ?: "",
                                onClose = { activeInlineVideoIndex = -1 },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            InlineVideoPlayer(
                                mediaItem = item,
                                onClose = { activeInlineVideoIndex = -1 },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Miniature vidéo avec bouton de lecture proéminent
                        VideoThumbnailView(
                            displayModel = displayModel,
                            isYouTube = isYouTube,
                            onPlayClicked = {
                                activeInlineVideoIndex = page
                            },
                            contentScale = contentScale
                        )
                    }
                } else {
                    // Item Photo
                    PhotoImageView(
                        displayModel = displayModel,
                        contentScale = contentScale
                    )
                }
            }
        }

        // Indicateur de position (ex: "2/4") & badges
        if (showIndicator && mediaItems.size > 1) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${mediaItems.size}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Indicateur de points en bas
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(mediaItems.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }

    // Modal plein écran haute résolution avec zoom & pan
    fullScreenItemIndex?.let { index ->
        val currentItem = mediaItems.getOrNull(index)
        if (currentItem != null && !currentItem.isVideo && !YouTubeHelper.isYouTubeUrl(currentItem.url)) {
            FullScreenMediaViewerDialog(
                mediaItem = currentItem,
                onDismiss = { fullScreenItemIndex = null }
            )
        }
    }
}

/**
 * Affichage d'une photo dans le carrousel
 */
@Composable
private fun PhotoImageView(
    displayModel: Any?,
    contentScale: ContentScale
) {
    val context = LocalContext.current
    var isError by remember(displayModel) { mutableStateOf(false) }

    if (displayModel != null && !isError) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(displayModel)
                .crossfade(true)
                .build(),
            contentDescription = "Photo carrousel",
            contentScale = contentScale,
            onError = {
                // Essai fallback si c'est un chemin qui n'a pas été résolu
                isError = true
            },
            onSuccess = {
                isError = false
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF222028)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aperçu média",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Miniature de vidéo avec icône de lecture et badge
 */
@Composable
private fun VideoThumbnailView(
    displayModel: Any?,
    onPlayClicked: () -> Unit,
    contentScale: ContentScale,
    isYouTube: Boolean = false
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Thumbnail image frame
        if (displayModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(displayModel)
                    .crossfade(true)
                    .build(),
                contentDescription = if (isYouTube) "Miniature vidéo YouTube" else "Miniature vidéo",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1C24))
            )
        }

        // Voile sombre
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )

        // Bouton de lecture central
        Surface(
            shape = CircleShape,
            color = if (isYouTube) Color(0xFFCC0000).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.75f),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
            modifier = Modifier
                .size(54.dp)
                .clickable { onPlayClicked() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Lire la vidéo",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Badge VIDÉO ou YOUTUBE en bas à gauche
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isYouTube) Color(0xFFFF0000).copy(alpha = 0.92f) else Color(0xFFE53935).copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isYouTube) "YOUTUBE" else "VIDÉO",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Lecteur vidéo YouTube inline exploitant un WebView avec iFrame HTML responsive.
 * Évite l'interruption par intent externe et permet la lecture directe dans le carrousel.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeIFramePlayer(
    youtubeUrl: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val videoId = remember(youtubeUrl) { YouTubeHelper.extractVideoId(youtubeUrl) ?: "" }
    val webViewHolder = remember { object { var webView: WebView? = null } }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewHolder.webView?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    destroy()
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (videoId.isNotBlank()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                        }
                        val html = YouTubeHelper.buildIFrameHtml(videoId, autoPlay = true)
                        loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
                        webViewHolder.webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "Vidéo YouTube indisponible",
                color = Color.White,
                fontSize = 13.sp
            )
        }

        if (isLoading && videoId.isNotBlank()) {
            CircularProgressIndicator(
                color = Color(0xFFFF0000),
                strokeWidth = 2.dp,
                modifier = Modifier.size(36.dp)
            )
        }

        // Bouton de fermeture en haut à droite
        IconButton(
            onClick = {
                try {
                    webViewHolder.webView?.apply {
                        stopLoading()
                        loadUrl("about:blank")
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Fermer le lecteur YouTube",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Lecteur vidéo inline utilisant VideoView natif optimisé.
 * Résout le bug de coupure après 3 secondes (suppression du MediaController système auto-hide,
 * gestion continue de la boucle de lecture, et contrôles Compose natifs).
 */
@Composable
fun InlineVideoPlayer(
    mediaItem: ParsedMediaItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }
    val videoViewHolder = remember { object { var videoView: VideoView? = null } }

    DisposableEffect(Unit) {
        onDispose {
            videoViewHolder.videoView?.stopPlayback()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    // Ne pas utiliser MediaController car son timeout interne de 3000ms
                    // coupe et perturbe la hiérarchie de fenêtres Compose.
                    val localCandidate = mediaItem.localPath?.let { File(it) }
                    if (localCandidate != null && localCandidate.exists() && localCandidate.length() > 0) {
                        setVideoPath(localCandidate.absolutePath)
                    } else if (!mediaItem.url.isNullOrBlank()) {
                        setVideoURI(Uri.parse(mediaItem.url))
                    } else {
                        val displayModel = mediaItem.getDisplayModel()
                        when (displayModel) {
                            is File -> setVideoPath(displayModel.absolutePath)
                            is String -> setVideoURI(Uri.parse(displayModel))
                            is Uri -> setVideoURI(displayModel)
                        }
                    }

                    setOnPreparedListener { mp ->
                        isBuffering = false
                        mp.isLooping = true
                        start()
                        isPlaying = true
                    }

                    // Boucle continue sans interruption : relance automatique à la fin
                    setOnCompletionListener { mp ->
                        try {
                            mp.seekTo(0)
                            mp.start()
                            isPlaying = true
                        } catch (e: Exception) {
                            start()
                        }
                    }

                    setOnInfoListener { _, what, _ ->
                        when (what) {
                            android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
                            android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> isBuffering = false
                            android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> isBuffering = false
                        }
                        true
                    }

                    setOnErrorListener { _, what, extra ->
                        isBuffering = false
                        android.util.Log.w("InlineVideoPlayer", "Erreur VideoView: what=$what, extra=$extra")
                        true // Géré gracieusement sans crash dialogue
                    }

                    videoViewHolder.videoView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Indicateur de chargement / buffering
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(36.dp)
            )
        }

        // Contrôles Compose superposés (Play / Pause / Replay)
        AnimatedVisibility(
            visible = showControls || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                modifier = Modifier
                    .size(52.dp)
                    .clickable {
                        val vv = videoViewHolder.videoView
                        if (vv != null) {
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Reprendre",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Bouton de fermeture / arrêt en haut à droite
        IconButton(
            onClick = {
                videoViewHolder.videoView?.stopPlayback()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Fermer le lecteur",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Visionneuse plein écran haute fidélité avec pinch-to-zoom et pan
 */
@Composable
fun FullScreenMediaViewerDialog(
    mediaItem: ParsedMediaItem,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // Image avec support du pinch-to-zoom et pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                val maxOffsetX = (size.width * (scale - 1)) / 2
                                val maxOffsetY = (size.height * (scale - 1)) / 2
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = mediaItem.getDisplayModel(),
                    contentDescription = "Image grand format",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }

            // Bouton Fermer (Placé après l'image pour être au-dessus du pointeur tactile)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fermer",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Bouton réinitialiser zoom
            if (scale > 1.05f) {
                IconButton(
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                ) {
                    Text(
                        "1x Réinitialiser",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
