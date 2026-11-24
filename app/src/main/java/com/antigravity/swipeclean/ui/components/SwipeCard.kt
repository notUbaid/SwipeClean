package com.antigravity.swipeclean.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.ui.theme.AccentIndigo
import com.antigravity.swipeclean.ui.theme.DarkBorder
import com.antigravity.swipeclean.ui.theme.DarkSurface
import com.antigravity.swipeclean.ui.theme.KeepGreen
import com.antigravity.swipeclean.ui.theme.TrashRed
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeCard(
    mediaItem: MediaItem,
    isTopCard: Boolean,
    modifier: Modifier = Modifier,
    onSwipeRight: (MediaItem) -> Unit,
    onSwipeLeft: (MediaItem) -> Unit,
    onSwipeUp: (MediaItem) -> Unit = {},
    onCardClick: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val offsetX = remember(mediaItem.id) { Animatable(0f) }
    val offsetY = remember(mediaItem.id) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val swipeThresholdPx = with(density) { 100.dp.toPx() }
    val swipeUpThresholdPx = with(density) { 110.dp.toPx() }

    val haptic = LocalHapticFeedback.current
    var hasFiredThresholdHaptic by remember(mediaItem.id) { mutableStateOf(false) }

    val dragModifier = if (isTopCard) {
        Modifier.pointerInput(mediaItem.id) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch {
                        offsetX.snapTo(offsetX.value + dragAmount.x)
                        offsetY.snapTo(offsetY.value + dragAmount.y)

                        val isPastThreshold = abs(offsetX.value) >= swipeThresholdPx || offsetY.value <= -swipeUpThresholdPx
                        if (isPastThreshold && !hasFiredThresholdHaptic) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            hasFiredThresholdHaptic = true
                        } else if (!isPastThreshold && hasFiredThresholdHaptic) {
                            hasFiredThresholdHaptic = false
                        }
                    }
                },
                onDragEnd = {
                    hasFiredThresholdHaptic = false
                    coroutineScope.launch {
                        when {
                            // Swipe Up -> BOOKMARK / DECIDE LATER
                            offsetY.value < -swipeUpThresholdPx && abs(offsetX.value) < swipeThresholdPx * 1.2f -> {
                                offsetY.animateTo(
                                    targetValue = -screenHeightPx * 1.2f,
                                    animationSpec = tween(durationMillis = 240)
                                )
                                onSwipeUp(mediaItem)
                            }
                            // Swipe Right -> KEEP
                            offsetX.value > swipeThresholdPx -> {
                                offsetX.animateTo(
                                    targetValue = screenWidthPx * 1.4f,
                                    animationSpec = tween(durationMillis = 240)
                                )
                                onSwipeRight(mediaItem)
                            }
                            // Swipe Left -> TRASH
                            offsetX.value < -swipeThresholdPx -> {
                                offsetX.animateTo(
                                    targetValue = -screenWidthPx * 1.4f,
                                    animationSpec = tween(durationMillis = 240)
                                )
                                onSwipeLeft(mediaItem)
                            }
                            // Reset back to center with spring
                            else -> {
                                launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                                launch {
                                    offsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    val currentOffsetX = offsetX.value
    val currentOffsetY = offsetY.value

    val rotationZ = if (isTopCard) (currentOffsetX / 30f).coerceIn(-18f, 18f) else 0f
    val keepAlpha = (currentOffsetX / swipeThresholdPx).coerceIn(0f, 1f)
    val trashAlpha = (-currentOffsetX / swipeThresholdPx).coerceIn(0f, 1f)
    val bookmarkAlpha = if (currentOffsetY < 0 && abs(currentOffsetX) < swipeThresholdPx) {
        (-currentOffsetY / swipeUpThresholdPx).coerceIn(0f, 1f)
    } else 0f

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
                    scaleX = 0.95f
                    scaleY = 0.95f
                    translationY = 24f
                }
            }
            .then(dragModifier),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTopCard) 12.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (mediaItem.isVideo) {
                VideoPlayerView(
                    mediaItem = mediaItem,
                    isActiveCard = isTopCard,
                    showTimeline = true
                )
            } else {
                PhotoViewer(
                    mediaItem = mediaItem,
                    enableZoom = isTopCard,
                    showMetadata = true
                )
            }

            // KEEP stamp (Vibrant green pill with check icon, top-left)
            if (isTopCard && keepAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(24.dp)
                        .rotate(-15f)
                        .alpha(keepAlpha)
                        .background(Color(0x3310B981), RoundedCornerShape(16.dp))
                        .border(3.dp, KeepGreen, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = KeepGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "KEEP",
                            color = KeepGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            // TRASH stamp (Vibrant red pill with trash icon, top-right)
            if (isTopCard && trashAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .rotate(15f)
                        .alpha(trashAlpha)
                        .background(Color(0x33EF4444), RoundedCornerShape(16.dp))
                        .border(3.dp, TrashRed, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = TrashRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TRASH",
                            color = TrashRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            // BOOKMARK stamp (Vibrant purple pill, top-center on swipe up)
            if (isTopCard && bookmarkAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                        .alpha(bookmarkAlpha)
                        .background(Color(0x336366F1), RoundedCornerShape(16.dp))
                        .border(3.dp, AccentIndigo, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = AccentIndigo,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BOOKMARK",
                            color = AccentIndigo,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}
