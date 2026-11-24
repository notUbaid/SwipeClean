package com.antigravity.swipeclean.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.ui.theme.DarkSurface
import com.antigravity.swipeclean.ui.theme.TextPrimary

@Composable
fun PhotoViewer(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    enableZoom: Boolean = true,
    showMetadata: Boolean = true
) {
    // Keying zoom scale and pan offset to mediaItem.id so new cards never inherit previous card's zoom!
    var scale by remember(mediaItem.id) { mutableFloatStateOf(1f) }
    var offset by remember(mediaItem.id) { mutableStateOf(Offset.Zero) }

    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface),
        contentAlignment = Alignment.Center
    ) {
        val gestureModifier = if (enableZoom) {
            Modifier
                .pointerInput(mediaItem.id) {
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
                .pointerInput(mediaItem.id) {
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
                .crossfade(true)
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

        // Metadata overlay chip
        if (showMetadata && scale == 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val details = listOfNotNull(
                    mediaItem.formattedSize,
                    mediaItem.resolutionText.ifEmpty { null },
                    mediaItem.formattedDate
                ).joinToString("  •  ")

                Text(
                    text = details,
                    color = TextPrimary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
