package com.antigravity.swipeclean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.ui.theme.OledBlack
import com.antigravity.swipeclean.ui.theme.ScrimDark
import com.antigravity.swipeclean.ui.theme.SurfaceBase
import com.antigravity.swipeclean.ui.theme.TextPrimary

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

    val context = LocalContext.current

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

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(mediaItem.uri)
                .crossfade(!isFullscreen)
                .precision(Precision.INEXACT)
                .allowHardware(true)
                .build(),
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

        if (showMetadata && scale == 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
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
