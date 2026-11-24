package com.antigravity.swipeclean.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.ui.theme.BorderSubtle
import com.antigravity.swipeclean.ui.theme.OledBlack
import com.antigravity.swipeclean.ui.theme.ScrimDark
import com.antigravity.swipeclean.ui.theme.SurfaceBase
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary
import com.antigravity.swipeclean.util.formatDuration
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    isActiveCard: Boolean = true,
    showTimeline: Boolean = true,
    isFullscreen: Boolean = false
) {
    val context = LocalContext.current

    if (!isActiveCard) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(if (isFullscreen) Modifier else Modifier.clip(RoundedCornerShape(20.dp)))
                .background(if (isFullscreen) OledBlack else SurfaceBase),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(mediaItem.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = mediaItem.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(ScrimDark, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Video preview",
                    tint = TextPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        return
    }

    var isPlaying by remember(mediaItem.stableKey) { mutableStateOf(true) }
    var isMuted by remember(mediaItem.stableKey) { mutableStateOf(true) }
    var currentPositionMs by remember(mediaItem.stableKey) { mutableLongStateOf(0L) }
    var totalDurationMs by remember(mediaItem.stableKey) { mutableLongStateOf(mediaItem.durationMs) }
    var isScrubbing by remember(mediaItem.stableKey) { mutableStateOf(false) }
    var scrubProgress by remember(mediaItem.stableKey) { mutableFloatStateOf(0f) }

    val exoPlayer = remember(mediaItem.stableKey) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(mediaItem.uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(mediaItem.durationMs)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(playerListener)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(playerListener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying, isScrubbing, exoPlayer) {
        while (isPlaying && !isScrubbing) {
            val state = runCatching { exoPlayer.playbackState }.getOrDefault(Player.STATE_IDLE)
            if (state == Player.STATE_IDLE) break
            currentPositionMs = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
            totalDurationMs = runCatching { exoPlayer.duration }.getOrDefault(mediaItem.durationMs).coerceAtLeast(1L)
            delay(100L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (isFullscreen) Modifier else Modifier.clip(RoundedCornerShape(20.dp)))
            .background(if (isFullscreen) OledBlack else SurfaceBase),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(ScrimDark, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = TextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        // Mute toggle at TopEnd (fullscreen Close sits at TopStart so they never overlap)
        IconButton(
            onClick = {
                isMuted = !isMuted
                exoPlayer.volume = if (isMuted) 0f else 1f
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                .padding(14.dp)
                .size(48.dp)
                .background(ScrimDark, CircleShape)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute video" else "Mute video",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        if (showTimeline) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        ScrimDark,
                        if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                    )
                    .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                val activePosMs = if (isScrubbing) (scrubProgress * totalDurationMs).toLong() else currentPositionMs

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(activePosMs),
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium
                    )

                    Slider(
                        value = if (isScrubbing) {
                            scrubProgress
                        } else if (totalDurationMs > 0L) {
                            (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        onValueChange = { newProgress ->
                            isScrubbing = true
                            scrubProgress = newProgress
                        },
                        onValueChangeFinished = {
                            val seekToMs = (scrubProgress * totalDurationMs).toLong()
                            exoPlayer.seekTo(seekToMs)
                            currentPositionMs = seekToMs
                            isScrubbing = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = TextPrimary,
                            activeTrackColor = TextPrimary,
                            inactiveTrackColor = BorderSubtle
                        )
                    )

                    Text(
                        text = formatDuration(totalDurationMs),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val details = listOfNotNull(
                        "Video",
                        mediaItem.formattedSize,
                        mediaItem.resolutionText.ifEmpty { null },
                        mediaItem.formattedDate.ifEmpty { null }
                    ).joinToString("  ·  ")

                    Text(
                        text = details,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
