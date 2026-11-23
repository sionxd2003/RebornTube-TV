package com.reborntube.tv

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.IOException
import kotlinx.coroutines.CancellationException

private val Ink = Color(0xFF282828)
private val Mantle = Color(0xFF1D2021)
private val Panel = Color(0xFF3C3836)
private val Surface1 = Color(0xFF504945)
private val Accent = Color(0xFFFE8019)
private val Success = Color(0xFFB8BB26)
private val SoftText = Color(0xFFD5C4A1)
private val GruvboxText = Color(0xFFEBDBB2)
private val GruvboxBlue = Color(0xFF83A598)
private val GruvboxRed = Color(0xFFFB4934)
private val GruvboxSurface = Color(0xFF32302F)

class MainActivity : ComponentActivity() {
    private var pairingCode by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startNewPairingSession()
        lifecycleScope.launch {
            PairingState.status.collect { status ->
                status.pairingCode?.let { pairingCode = it }
            }
        }
        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.darkColorScheme(
                    primary = Accent,
                    onPrimary = Ink,
                    primaryContainer = Surface1,
                    onPrimaryContainer = GruvboxText,
                    secondary = Success,
                    onSecondary = Ink,
                    tertiary = GruvboxBlue,
                    onTertiary = Ink,
                    background = Ink,
                    onBackground = GruvboxText,
                    surface = Panel,
                    onSurface = GruvboxText,
                    surfaceVariant = Surface1,
                    onSurfaceVariant = SoftText
                )
            ) {
                TvHome(code = pairingCode)
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        startNewPairingSession()
    }

    private fun startNewPairingSession() {
        val previousCode = PairingReceiver.getOrCreateCode(this)
        pairingCode = PairingReceiver.newCode(previousCode)
        PairingReceiver.saveCode(this, pairingCode)
        PairingState.update(ReceiverStatus("Iniciando RebornTube TV…", pairingCode = pairingCode))
        ContextCompat.startForegroundService(
            this,
            Intent(this, PairingService::class.java)
                .putExtra(PairingService.EXTRA_PAIRING_CODE, pairingCode)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun TvHome(code: String) {
    val context = LocalContext.current
    val activity = context as? Activity
    val status by PairingState.status.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentTitle by remember { mutableStateOf("") }
    var requestedVideo by remember { mutableStateOf<ReceivedVideo?>(null) }
    var recentVideos by remember { mutableStateOf(emptyList<ReceivedVideo>()) }
    var speedIndex by remember { mutableStateOf(1) }
    var selectedStream by remember { mutableStateOf(0) }
    var streams by remember { mutableStateOf(emptyList<StreamOption>()) }
    var subtitles by remember { mutableStateOf(emptyList<SubtitlesStream>()) }
    var selectedSubtitleIndex by remember { mutableIntStateOf(-1) }
    var subtitleCues by remember { mutableStateOf(emptyList<TvSubtitleCue>()) }
    var subtitleText by remember { mutableStateOf<String?>(null) }
    var subtitleError by remember { mutableStateOf<String?>(null) }
    var repeatEnabled by remember { mutableStateOf(false) }
    var hasMedia by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLive by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }
    var bottomOptionsFocused by remember { mutableStateOf(false) }
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    val rootFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    val bottomOptionsFocusRequester = remember { FocusRequester() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                hasMedia = player.mediaItemCount > 0
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) controlsVisible = true
            }

            override fun onPlayerError(error: PlaybackException) {
                val video = requestedVideo
                if (video != null && (
                        video.fastHlsUrl.isNotBlank() ||
                            video.fastVideoUrl.isNotBlank() ||
                            video.fastAudioUrl.isNotBlank()
                    )
                ) {
                    requestedVideo = video.copy(
                        fastVideoUrl = "",
                        fastAudioUrl = "",
                        fastHlsUrl = "",
                        startPositionMs = player.currentPosition.coerceAtLeast(video.startPositionMs)
                    )
                    playbackError = null
                } else {
                    playbackError = "No se pudo reproducir este video. Comprueba tu conexión e inténtalo otra vez."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    DisposableEffect(activity, hasMedia, isPlaying) {
        if (hasMedia && isPlaying) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(hasMedia, subtitleCues) {
        while (hasMedia) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            subtitleText = tvSubtitleAt(subtitleCues, positionMs)
            kotlinx.coroutines.delay(150)
        }
    }

    LaunchedEffect(hasMedia, subtitles, selectedSubtitleIndex) {
        val selected = subtitles.getOrNull(selectedSubtitleIndex)
        if (!hasMedia || selected == null) {
            subtitleCues = emptyList()
            subtitleText = null
            subtitleError = null
            return@LaunchedEffect
        }
        subtitleCues = emptyList()
        subtitleText = null
        subtitleError = null
        try {
            subtitleCues = loadTvSubtitles(selected)
            if (subtitleCues.isEmpty()) subtitleError = "No se encontraron subtítulos para esta pista."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            subtitleError = "No se pudieron cargar los subtítulos."
        }
    }

    LaunchedEffect(interaction, controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            kotlinx.coroutines.delay(4_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(hasMedia, controlsVisible) {
        if (hasMedia) {
            if (controlsVisible) playFocusRequester.requestFocus()
            else rootFocusRequester.requestFocus()
        }
    }

    fun revealControls() {
        controlsVisible = true
        interaction++
    }

    fun syncProgress(video: ReceivedVideo) {
        if (video.live || video.progressCallbackUrl.isBlank() || video.progressToken.isBlank()) return
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    sendTvProgress(video, position, duration)
                }
            } catch (error: IOException) {
                Log.w("RebornTubeTV", "No se pudo sincronizar el progreso con el celular.", error)
            }
        }
    }

    val lifecycleOwner = activity as? LifecycleOwner
    DisposableEffect(lifecycleOwner, player) {
        var resumeOnReturn = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeOnReturn = player.playWhenReady && player.mediaItemCount > 0
                    if (resumeOnReturn) {
                        requestedVideo?.let(::syncProgress)
                        player.pause()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (resumeOnReturn) {
                        resumeOnReturn = false
                        player.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    fun closeVideo() {
        requestedVideo?.let(::syncProgress)
        player.stop()
        player.clearMediaItems()
        hasMedia = false
        isPlaying = false
        playbackError = null
        scrubPositionMs = null
    }

    fun seekBy(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0))
        revealControls()
    }

    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
            requestedVideo?.let(::syncProgress)
        } else player.play()
        revealControls()
    }

    LaunchedEffect(hasMedia, requestedVideo) {
        val video = requestedVideo ?: return@LaunchedEffect
        if (!hasMedia || video.live || video.progressCallbackUrl.isBlank()) return@LaunchedEffect
        while (hasMedia) {
            syncProgress(video)
            delay(5_000)
        }
    }

    LaunchedEffect(status.stopPlayback) {
        if (status.stopPlayback) {
            closeVideo()
            requestedVideo = null
        }
    }

    val received = status.video
    LaunchedEffect(received) {
        if (received != null) {
            recentVideos = (listOf(received) + recentVideos.filterNot {
                it.videoId == received.videoId && it.videoUrl == received.videoUrl
            }).take(3)
            requestedVideo = received
        }
    }

    fun replay(video: ReceivedVideo) {
        requestedVideo = video.copy(receivedAt = System.nanoTime())
    }

    fun startPlayback(video: ReceivedVideo, option: StreamOption) {
        val item = MediaItem.Builder()
            .setUri(Uri.parse(option.videoUrl))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).build())
            .build()
        player.setMediaSource(option.source(context, item))
        player.prepare()
        if (!video.live && video.startPositionMs > 0) player.seekTo(video.startPositionMs)
        player.play()
    }

    LaunchedEffect(requestedVideo) {
        val video = requestedVideo ?: return@LaunchedEffect
        player.stop()
        player.clearMediaItems()
        hasMedia = false
        controlsVisible = true
        isLive = video.live
        playbackError = null
        subtitles = emptyList()
        selectedSubtitleIndex = -1
        subtitleCues = emptyList()
        subtitleText = null
        subtitleError = null
        currentTitle = video.title
        try {
            val fastOption = when {
                video.fastHlsUrl.isNotBlank() ->
                    StreamOption("Auto", video.fastHlsUrl, hls = true)
                video.fastVideoUrl.isNotBlank() ->
                    StreamOption(
                        video.fastQuality.ifBlank { "Enviado desde el celular" },
                        video.fastVideoUrl,
                        video.fastAudioUrl.takeIf { it.isNotBlank() }
                    )
                else -> null
            }
            if (fastOption != null) {
                streams = listOf(fastOption)
                selectedStream = 0
                startPlayback(video, fastOption)
                try {
                    val resolved = withContext(Dispatchers.IO) { resolveStream(video) }
                    val resolvedIndex = resolved.streams.indexOfFirst {
                        it.videoUrl == fastOption.videoUrl && it.audioUrl == fastOption.audioUrl
                    }
                    streams = if (resolvedIndex >= 0) {
                        resolved.streams
                    } else {
                        listOf(fastOption) + resolved.streams
                    }
                    selectedStream = if (resolvedIndex >= 0) resolvedIndex else 0
                    subtitles = resolved.subtitles
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("RebornTubeTV", "No se pudieron cargar las opciones adicionales del video.", error)
                }
            } else {
                val resolved = withContext(Dispatchers.IO) { resolveStream(video) }
                streams = resolved.streams
                subtitles = resolved.subtitles
                selectedStream = 0
                startPlayback(video, streams.first())
            }
        } catch (error: Exception) {
            playbackError = error.message ?: "No se pudo preparar el video."
        }

    }

    BackHandler(enabled = hasMedia, onBack = ::closeVideo)

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        if (hasMedia) {
            Box(
                Modifier.fillMaxSize()
                    .focusRequester(rootFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.MediaPlayPause -> {
                                togglePlayback()
                                true
                            }
                            Key.MediaPlay -> {
                                player.play()
                                revealControls()
                                true
                            }
                            Key.MediaPause -> {
                                player.pause()
                                revealControls()
                                true
                            }
                            Key.DirectionLeft -> {
                                if (controlsVisible) false else {
                                    seekBy(-10_000)
                                    true
                                }
                            }
                            Key.DirectionRight -> {
                                if (controlsVisible) false else {
                                    seekBy(10_000)
                                    true
                                }
                            }
                            Key.DirectionDown -> {
                                if (!controlsVisible) {
                                    revealControls()
                                    true
                                } else if (!bottomOptionsFocused) {
                                    bottomOptionsFocusRequester.requestFocus()
                                    true
                                } else true
                            }
                            Key.DirectionUp -> {
                                if (bottomOptionsFocused) {
                                    playFocusRequester.requestFocus()
                                    true
                                } else if (!controlsVisible) {
                                    revealControls()
                                    true
                                } else false
                            }
                            Key.DirectionCenter,
                            Key.Enter -> {
                                if (!controlsVisible) {
                                    revealControls()
                                    true
                                } else false
                            }
                            else -> false
                        }
                    }
                    .focusable()
            ) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = player
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.player = player }
                )
                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.7f),
                                    0.35f to Color.Transparent,
                                    0.7f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.82f)
                                )
                            )
                        )
                        Row(
                            Modifier.align(Alignment.TopStart).fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = Panel.copy(alpha = 0.9f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.Icon(
                                        painter = painterResource(R.drawable.ic_notification),
                                        contentDescription = "RebornTube TV",
                                        tint = Accent,
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "REBORN TUBE  ·  FIRE TV",
                                    color = Accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    maxLines = 1
                                )
                                Text(
                                    currentTitle,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isLive) {
                                Spacer(Modifier.width(16.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = GruvboxRed.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        "EN VIVO",
                                        color = GruvboxRed,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            if (playbackError != null) {
                                Spacer(Modifier.width(12.dp))
                                Text(playbackError!!, color = GruvboxRed, fontSize = 15.sp)
                            }
                        }
                        Row(
                            Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (playbackError != null) {
                                PlayerPill("Reintentar", "Reintentar reproducción") {
                                    requestedVideo?.let(::replay)
                                }
                            } else {
                                PlayerAction(Icons.Default.Replay10, "Retroceder 10 segundos") { seekBy(-10_000) }
                                PlayerAction(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (isPlaying) "Pausar" else "Reproducir",
                                    modifier = Modifier.focusRequester(playFocusRequester),
                                    large = true,
                                    onClick = ::togglePlayback
                                )
                                PlayerAction(Icons.Default.Forward10, "Avanzar 10 segundos") { seekBy(10_000) }
                            }
                        }
                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 22.dp)
                        ) {
                            if (!isLive && durationMs > 0) {
                                val shownPosition = (scrubPositionMs ?: positionMs).coerceIn(0, durationMs)
                                Slider(
                                    modifier = Modifier.height(30.dp),
                                    value = shownPosition.toFloat() / durationMs.toFloat(),
                                    onValueChange = {
                                        scrubPositionMs = (it * durationMs).toLong().coerceIn(0, durationMs)
                                        revealControls()
                                    },
                                    onValueChangeFinished = {
                                        scrubPositionMs?.let(player::seekTo)
                                        scrubPositionMs = null
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Accent,
                                        activeTrackColor = Accent,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.32f)
                                    )
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatTime(shownPosition), color = Color.White, fontSize = 14.sp)
                                    Text(formatTime(durationMs), color = Color.White, fontSize = 14.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                            } else if (isLive) {
                                Text("EN VIVO", color = Success, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
                            }
                            Row(
                                Modifier.fillMaxWidth()
                                    .onFocusChanged { bottomOptionsFocused = it.hasFocus }
                                    .focusGroup(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlayerPill(
                                    "${speeds[speedIndex]}×",
                                    "Velocidad",
                                    modifier = Modifier.focusRequester(bottomOptionsFocusRequester)
                                ) {
                                    speedIndex = (speedIndex + 1) % speeds.size
                                    player.setPlaybackSpeed(speeds[speedIndex])
                                    revealControls()
                                }
                                if (streams.size > 1) {
                                    PlayerPill(streams.getOrNull(selectedStream)?.label ?: "Calidad", "Calidad") {
                                        val position = player.currentPosition
                                        selectedStream = (selectedStream + 1) % streams.size
                                        val option = streams[selectedStream]
                                        val item = MediaItem.Builder()
                                            .setUri(Uri.parse(option.videoUrl))
                                            .setMediaMetadata(MediaMetadata.Builder().setTitle(currentTitle).build())
                                            .build()
                                        player.setMediaSource(option.source(context, item), position)
                                        player.prepare()
                                        player.play()
                                        revealControls()
                                    }
                                }
                                if (subtitles.isNotEmpty()) {
                                    PlayerAction(
                                        Icons.Default.ClosedCaption,
                                        subtitles.getOrNull(selectedSubtitleIndex)?.let {
                                            "Subtítulos: ${it.displayLanguageName}"
                                        } ?: "Activar subtítulos",
                                        selected = selectedSubtitleIndex >= 0
                                    ) {
                                        selectedSubtitleIndex =
                                            if (selectedSubtitleIndex >= subtitles.lastIndex) -1
                                            else selectedSubtitleIndex + 1
                                        revealControls()
                                    }
                                }
                                PlayerAction(
                                    Icons.Default.Repeat,
                                    if (repeatEnabled) "Desactivar repetición" else "Repetir",
                                    selected = repeatEnabled
                                ) {
                                    repeatEnabled = !repeatEnabled
                                    player.repeatMode = if (repeatEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                    revealControls()
                                }
                                PlayerAction(Icons.Default.Close, "Cerrar video", onClick = ::closeVideo)
                            }
                        }
                    }
                }
                subtitleText?.let { text ->
                    Text(
                        text = text,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .widthIn(max = 700.dp)
                            .padding(
                                start = 48.dp,
                                end = 48.dp,
                                bottom = if (controlsVisible) 138.dp else 30.dp
                            )
                            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                subtitleError?.takeIf { controlsVisible }?.let { message ->
                    Text(
                        message,
                        color = GruvboxRed,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 138.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            PairingScreen(
                code = status.pairingCode ?: code,
                status = status.message,
                address = status.address,
                error = playbackError,
                recentVideos = recentVideos,
                onReplay = ::replay,
                onRetry = { requestedVideo?.let(::replay) }
            )
        }
    }
}

private fun sendTvProgress(video: ReceivedVideo, positionMs: Long, durationMs: Long) {
    val connection = URL(video.progressCallbackUrl).openConnection() as HttpURLConnection
    try {
        val payload = listOf(
            "token" to video.progressToken,
            "videoUrl" to video.videoUrl,
            "positionMs" to positionMs.toString(),
            "durationMs" to durationMs.toString()
        ).joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8.name())}=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        connection.responseCode
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun PlayerAction(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val size = if (large) 72.dp else 46.dp
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size)
            .background(
                if (selected) Accent.copy(alpha = 0.28f) else Mantle.copy(alpha = 0.88f),
                CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) Accent else GruvboxText,
            modifier = Modifier.size(if (large) 40.dp else 25.dp)
        )
    }
}

@Composable
private fun PlayerPill(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.height(48.dp).clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(24.dp),
        color = if (focused) Accent else Panel.copy(alpha = 0.96f),
        contentColor = if (focused) Ink else GruvboxText,
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) GruvboxText else Surface1
        )
    ) {
        Box(
            Modifier.padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private fun formatTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private data class ResolvedVideo(
    val streams: List<StreamOption>,
    val subtitles: List<SubtitlesStream>
) {
}

private data class StreamOption(
    val label: String,
    val videoUrl: String,
    val audioUrl: String? = null,
    val hls: Boolean = false
) {
    @androidx.annotation.OptIn(UnstableApi::class)
    fun source(context: android.content.Context, item: MediaItem): MediaSource {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11; Fire TV) AppleWebKit/537.36 Chrome/131.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        val factory = DefaultDataSource.Factory(context, http)
        if (hls) return HlsMediaSource.Factory(factory).createMediaSource(item)
        val progressive = ProgressiveMediaSource.Factory(factory)
        val video = progressive.createMediaSource(item)
        val audio = audioUrl?.let {
            progressive.createMediaSource(MediaItem.fromUri(it))
        }
        return if (audio == null) video else MergingMediaSource(video, audio)
    }
}

private suspend fun resolveStream(video: ReceivedVideo): ResolvedVideo {
    val url = video.videoUrl.ifBlank {
        if (video.videoId.isNotBlank()) "https://www.youtube.com/watch?v=${video.videoId}"
        else error("El celular no envió un enlace de video.")
    }
    val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
    val youtube = host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
    if (!youtube) {
        require(url.startsWith("https://")) { "El enlace recibido no es seguro." }
        return ResolvedVideo(listOf(StreamOption("Auto", url, hls = url.contains(".m3u8", true))), emptyList())
    }
    val info = StreamInfo.getInfo(ServiceList.YouTube, url)
    val videoOnly = info.videoOnlyStreams
        .filter {
            (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.DASH) &&
                (it.format == MediaFormat.MPEG_4 || it.format == MediaFormat.WEBM)
        }
        .groupBy { it.height to it.fps }
        .map { (_, variants) -> variants.maxByOrNull { it.bitrate }!! }
        .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { it.height }.thenByDescending { it.fps })
    val audio = info.audioStreams
        .filter {
            (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.DASH) &&
                (it.format == MediaFormat.M4A || it.format == MediaFormat.WEBMA ||
                    it.format == MediaFormat.WEBMA_OPUS || it.format == MediaFormat.MP3 ||
                    it.format == MediaFormat.OPUS || it.format == MediaFormat.OGG)
        }
        .groupBy { it.audioTrackId ?: "default" }
        .map { (_, variants) -> variants.maxByOrNull { it.averageBitrate }!! }
        .sortedBy { if (it.audioTrackType == AudioTrackType.ORIGINAL) 0 else 1 }
        .firstOrNull()
    val options = when {
        video.live && !info.hlsUrl.isNullOrBlank() ->
            listOf(StreamOption("En vivo", info.hlsUrl, hls = true))
        videoOnly.isNotEmpty() && audio != null ->
            videoOnly.map { StreamOption("${it.height}p", it.content, audio.content) }
        !info.hlsUrl.isNullOrBlank() ->
            listOf(StreamOption("Auto", info.hlsUrl, hls = true))
        else -> {
            val muxed = info.videoStreams
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .maxByOrNull { it.height }
            muxed?.let { listOf(StreamOption("${it.height}p", it.content)) }.orEmpty()
        }
    }
    if (options.isEmpty()) error("No se encontró una pista de video reproducible.")
    return ResolvedVideo(options, subtitleTracks(info))
}

@Composable
private fun PairingScreen(
    code: String,
    status: String,
    address: String?,
    error: String?,
    recentVideos: List<ReceivedVideo>,
    onReplay: (ReceivedVideo) -> Unit,
    onRetry: () -> Unit
) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(Mantle, Ink, GruvboxSurface)))
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).size(500.dp)
                .background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.14f), Color.Transparent)))
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.brand_logo),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "REBORN TUBE  /  FIRE TV",
                    color = Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.4.sp
                )
            }
            Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
            ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(32.dp),
                color = GruvboxSurface,
                border = BorderStroke(1.dp, Surface1.copy(alpha = 0.72f)),
                tonalElevation = 8.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 30.dp)) {
                    Text(
                        "Tu reproductor,\nen pantalla grande",
                        color = GruvboxText,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Conecta RebornTube desde tu celular y disfruta el video aquí.",
                        color = SoftText,
                        fontSize = 18.sp,
                        lineHeight = 26.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = Mantle,
                        border = BorderStroke(1.dp, Surface1.copy(alpha = 0.6f))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "CÓDIGO DE EMPAREJAMIENTO",
                                    color = SoftText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.4.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("6 DÍGITOS", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                code.forEach { digit ->
                                    Surface(
                                        modifier = Modifier.weight(1f).height(64.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Panel
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                digit.toString(),
                                                color = GruvboxText,
                                                fontSize = 36.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    Spacer(Modifier.height(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(Success, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                status,
                                modifier = Modifier.weight(1f),
                                color = SoftText,
                                fontSize = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (address != null) {
                            Text(
                                "Red local: $address:8765",
                                modifier = Modifier.fillMaxWidth(),
                                color = SoftText,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (error != null) {
                            Text(error, color = GruvboxRed, fontSize = 14.sp)
                            Surface(
                                modifier = Modifier.padding(top = 4.dp).clickable(onClick = onRetry).focusable(),
                                shape = RoundedCornerShape(18.dp),
                                color = Surface1
                            ) {
                                Text(
                                    "Reintentar reproducción",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    color = GruvboxText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.width(300.dp),
                shape = RoundedCornerShape(28.dp),
                color = Panel.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, Surface1.copy(alpha = 0.7f))
            ) {
                Column(Modifier.fillMaxWidth().padding(26.dp)) {
                    Text(
                        "Conéctate en 3 pasos",
                        color = GruvboxText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(22.dp))
                    Instruction("1", "Conecta ambos dispositivos a la misma Wi-Fi")
                    Instruction("2", "Abre Transmitir en RebornTube")
                    Instruction("3", "Escribe el código que aparece aquí")
                    if (recentVideos.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "Reproducidos recientemente",
                            color = GruvboxText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        recentVideos.forEach { video ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    .clickable { onReplay(video) }.focusable(),
                                shape = RoundedCornerShape(14.dp),
                                color = Surface1
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        video.title,
                                        color = GruvboxText,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Reproducir de nuevo",
                                        color = SoftText,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Surface1.copy(alpha = 0.65f))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Tu código es privado y solo funciona en tu red local.",
                        color = SoftText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

}

@Composable
private fun Instruction(number: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(30.dp).background(Accent.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = GruvboxText, fontSize = 15.sp, lineHeight = 21.sp)
    }
}
