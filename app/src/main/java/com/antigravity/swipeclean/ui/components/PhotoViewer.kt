package com.antigravity.swipeclean.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.ui.theme.OledBlack
import com.antigravity.swipeclean.ui.theme.ScrimDark
import com.antigravity.swipeclean.ui.theme.SurfaceBase
import com.antigravity.swipeclean.ui.theme.TextMuted
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary

@Composable
fun PhotoViewer(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    enableZoom: Boolean = false,
    showMetadata: Boolean = true,
    isFullscreen: Boolean = false
) {
    var scale by remember(mediaItem.stableKey) { mutableFloatStateOf(1f) }
    var offset by remember(mediaItem.stableKey) { mutableStateOf(Offset.Zero) }
    var useSoftwareFallback by remember(mediaItem.stableKey) { mutableStateOf(false) }

    val context = LocalContext.current
    val decodeCapPx = if (isFullscreen) 3072 else 1600

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (isFullscreen) Modifier else Modifier.clip(RoundedCornerShape(20.dp)))
            .background(if (isFullscreen) OledBlack else SurfaceBase),
        contentAlignment = Alignment.Center
    ) {
        val gestureModifier = if (enableZoom) {
            Modifier
                .pointerInput(mediaItem.stableKey) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
                .pointerInput(mediaItem.stableKey) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            val maxOffsetX = (size.width * (scale - 1f)) / 2f
                            val maxOffsetY = (size.height * (scale - 1f)) / 2f
                            val newX = (offset.x + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                            val newY = (offset.y + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                            offset = Offset(newX, newY)
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
        } else {
            Modifier
        }

        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(mediaItem.uri)
                .size(decodeCapPx)
                .precision(Precision.INEXACT)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .allowHardware(!useSoftwareFallback)
                .setParameter("sw", useSoftwareFallback)
                .crossfade(!isFullscreen && !useSoftwareFallback)
                .build(),
            onError = {
                if (!useSoftwareFallback) {
                    useSoftwareFallback = true
                }
            }
        )

        Image(
            painter = painter,
            contentDescription = mediaItem.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                CircularProgressIndicator(
                    color = TextSecondary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp)
                )
            }
            is AsyncImagePainter.State.Error -> {
                if (!useSoftwareFallback) {
                    CircularProgressIndicator(
                        color = TextSecondary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = "Preview unavailable",
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Preview unavailable",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mediaItem.displayName,
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> Unit
        }

        if (showMetadata && scale == 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (isFullscreen) {
                            Modifier.windowInsetsPadding(
                                WindowInsets.navigationBars.union(
                                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                                )
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(bottom = 14.dp, start = 16.dp, end = 16.dp)
                    .background(ScrimDark, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val details = listOfNotNull(
                    mediaItem.formattedSize,
                    mediaItem.resolutionText.ifEmpty { null },
                    mediaItem.formattedDate.ifEmpty { null }
                ).joinToString("  ·  ")

                Text(
                    text = details,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
