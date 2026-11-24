package com.antigravity.swipeclean.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.ui.theme.DeleteRed
import com.antigravity.swipeclean.ui.theme.DeleteRedTint
import com.antigravity.swipeclean.ui.theme.KeepGreen
import com.antigravity.swipeclean.ui.theme.KeepGreenTint
import com.antigravity.swipeclean.ui.theme.LaterBlue
import com.antigravity.swipeclean.ui.theme.LaterBlueTint
import com.antigravity.swipeclean.ui.theme.SurfaceBase
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeCard(
    mediaItem: MediaItem,
    isTopCard: Boolean,
    isPlaybackActive: Boolean,
    modifier: Modifier = Modifier,
    onSwipeRight: (MediaItem) -> Unit,
    onSwipeLeft: (MediaItem) -> Unit,
    onSwipeLater: (MediaItem) -> Unit = {},
    onInspectClick: (MediaItem) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val reduceMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    val offsetX = remember(mediaItem.stableKey) { Animatable(0f) }
    val offsetY = remember(mediaItem.stableKey) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val swipeThresholdPx = with(density) { 100.dp.toPx() }
    val swipeUpThresholdPx = with(density) { 100.dp.toPx() }
    val minFlickDistancePx = with(density) { 36.dp.toPx() }
    val flickVelocityPx = with(density) { 550.dp.toPx() }

    val haptic = LocalHapticFeedback.current
    var hasFiredThresholdHaptic by remember(mediaItem.stableKey) { mutableStateOf(false) }
    var hasCommitted by remember(mediaItem.stableKey) { mutableStateOf(false) }
    val velocityTracker = remember(mediaItem.stableKey) { VelocityTracker() }

    fun resetCard() {
        coroutineScope.launch {
            val animDuration = if (reduceMotion) 0 else 200
            launch {
                if (reduceMotion) {
                    offsetX.snapTo(0f)
                } else {
                    offsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
            launch {
                if (reduceMotion) {
                    offsetY.snapTo(0f)
                } else {
                    offsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
            if (animDuration == 0) {
                hasFiredThresholdHaptic = false
            }
        }
    }

    val dragModifier = if (isTopCard) {
        Modifier.pointerInput(mediaItem.stableKey) {
            detectDragGestures(
                onDragStart = {
                    if (!hasCommitted) {
                        velocityTracker.resetTracking()
                    }
                },
                onDrag = { change, dragAmount ->
                    if (hasCommitted) return@detectDragGestures
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    coroutineScope.launch {
                        offsetX.snapTo(offsetX.value + dragAmount.x)
                        offsetY.snapTo(offsetY.value + dragAmount.y)

                        val isPastThreshold =
                            abs(offsetX.value) >= swipeThresholdPx ||
                                (offsetY.value <= -swipeUpThresholdPx && abs(offsetX.value) < swipeThresholdPx)
                        if (isPastThreshold && !hasFiredThresholdHaptic) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            hasFiredThresholdHaptic = true
                        } else if (!isPastThreshold && hasFiredThresholdHaptic) {
                            hasFiredThresholdHaptic = false
                        }
                    }
                },
                onDragCancel = {
                    hasFiredThresholdHaptic = false
                    if (!hasCommitted) resetCard()
                },
                onDragEnd = {
                    hasFiredThresholdHaptic = false
                    if (hasCommitted) return@detectDragGestures

                    val velocity = velocityTracker.calculateVelocity()
                    val exitMs = if (reduceMotion) 0 else 200
                    val x = offsetX.value
                    val y = offsetY.value

                    val isLaterSwipe =
                        (y < -swipeUpThresholdPx && abs(x) < swipeThresholdPx * 1.2f) ||
                            (velocity.y < -flickVelocityPx && y < -minFlickDistancePx && abs(x) < swipeThresholdPx)
                    val isKeepSwipe =
                        x > swipeThresholdPx || (velocity.x > flickVelocityPx && x > minFlickDistancePx)
                    val isDeleteSwipe =
                        x < -swipeThresholdPx || (velocity.x < -flickVelocityPx && x < -minFlickDistancePx)

                    coroutineScope.launch {
                        when {
                            isLaterSwipe -> {
                                hasCommitted = true
                                if (exitMs > 0) {
                                    offsetY.animateTo(-screenHeightPx * 1.2f, tween(exitMs))
                                }
                                onSwipeLater(mediaItem)
                            }
                            isKeepSwipe -> {
                                hasCommitted = true
                                if (exitMs > 0) {
                                    offsetX.animateTo(screenWidthPx * 1.35f, tween(exitMs))
                                }
                                onSwipeRight(mediaItem)
                            }
                            isDeleteSwipe -> {
                                hasCommitted = true
                                if (exitMs > 0) {
                                    offsetX.animateTo(-screenWidthPx * 1.35f, tween(exitMs))
                                }
                                onSwipeLeft(mediaItem)
                            }
                            else -> resetCard()
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    val accessibilityModifier = if (isTopCard) {
        val kindLabel = if (mediaItem.isVideo) "Video" else "Photo"
        Modifier.semantics {
            contentDescription = "$kindLabel, ${mediaItem.formattedSize}, ${mediaItem.formattedDate}"
            customActions = listOf(
                CustomAccessibilityAction("Keep") {
                    if (!hasCommitted) {
                        hasCommitted = true
                        onSwipeRight(mediaItem)
                    }
                    true
                },
                CustomAccessibilityAction("Delete") {
                    if (!hasCommitted) {
                        hasCommitted = true
                        onSwipeLeft(mediaItem)
                    }
                    true
                },
                CustomAccessibilityAction("Save for later") {
                    if (!hasCommitted) {
                        hasCommitted = true
                        onSwipeLater(mediaItem)
                    }
                    true
                }
            )
        }
    } else {
        Modifier
    }

    val currentOffsetX = offsetX.value
    val currentOffsetY = offsetY.value
    val rotationZ = if (isTopCard) (currentOffsetX / 30f).coerceIn(-16f, 16f) else 0f
    val keepAlpha = (currentOffsetX / swipeThresholdPx).coerceIn(0f, 1f)
    val deleteAlpha = (-currentOffsetX / swipeThresholdPx).coerceIn(0f, 1f)
    val laterAlpha = if (currentOffsetY < 0f && abs(currentOffsetX) < swipeThresholdPx) {
        (-currentOffsetY / swipeUpThresholdPx).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        modifier = modifier
            .fillMaxSize()
            .offset {
                if (isTopCard) {
                    IntOffset(currentOffsetX.roundToInt(), currentOffsetY.roundToInt())
                } else {
                    IntOffset(0, 0)
                }
            }
            .graphicsLayer {
                this.rotationZ = rotationZ
                if (!isTopCard) {
                    scaleX = 0.96f
                    scaleY = 0.96f
                    translationY = 18f
                }
            }
            .then(accessibilityModifier)
            .then(dragModifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTopCard) 8.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isTopCard && !mediaItem.isVideo) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!hasCommitted) onInspectClick(mediaItem)
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (mediaItem.isVideo) {
                VideoPlayerView(
                    mediaItem = mediaItem,
                    isActiveCard = isTopCard && isPlaybackActive,
                    showTimeline = true,
                    isFullscreen = false
                )
            } else {
                PhotoViewer(
                    mediaItem = mediaItem,
                    enableZoom = false,
                    showMetadata = true,
                    isFullscreen = false
                )
            }

            if (isTopCard && keepAlpha > 0.05f) {
                SwipeStamp(
                    icon = Icons.Default.Check,
                    label = "KEEP",
                    tint = KeepGreen,
                    background = KeepGreenTint,
                    alpha = keepAlpha,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(24.dp)
                        .rotate(-12f)
                )
            }

            if (isTopCard && deleteAlpha > 0.05f) {
                SwipeStamp(
                    icon = Icons.Default.Delete,
                    label = "DELETE",
                    tint = DeleteRed,
                    background = DeleteRedTint,
                    alpha = deleteAlpha,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .rotate(12f)
                )
            }

            if (isTopCard && laterAlpha > 0.05f) {
                SwipeStamp(
                    icon = Icons.Default.Bookmark,
                    label = "LATER",
                    tint = LaterBlue,
                    background = LaterBlueTint,
                    alpha = laterAlpha,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun SwipeStamp(
    icon: ImageVector,
    label: String,
    tint: Color,
    background: Color,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(background, RoundedCornerShape(12.dp))
            .border(2.dp, tint, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = tint,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
