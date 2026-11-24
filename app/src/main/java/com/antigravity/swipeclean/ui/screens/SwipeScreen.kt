package com.antigravity.swipeclean.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antigravity.swipeclean.MainViewModel
import com.antigravity.swipeclean.ui.components.PhotoViewer
import com.antigravity.swipeclean.ui.components.SwipeCard
import com.antigravity.swipeclean.ui.components.VideoPlayerView
import com.antigravity.swipeclean.ui.theme.AccentIndigo
import com.antigravity.swipeclean.ui.theme.DarkBackground
import com.antigravity.swipeclean.ui.theme.DarkBorder
import com.antigravity.swipeclean.ui.theme.DarkSurface
import com.antigravity.swipeclean.ui.theme.DarkSurfaceElevated
import com.antigravity.swipeclean.ui.theme.KeepGreen
import com.antigravity.swipeclean.ui.theme.TextMuted
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary
import com.antigravity.swipeclean.ui.theme.TrashRed
import com.antigravity.swipeclean.ui.theme.UndoYellow

@Composable
fun SwipeScreen(
    viewModel: MainViewModel,
    onNavigateBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deck by viewModel.mediaDeck.collectAsState()
    val activeTitle by viewModel.activeDeckTitle.collectAsState()
    val stagedDeletions by viewModel.stagedForDeletion.collectAsState()
    val showReviewDeletions by viewModel.showReviewDeletions.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val inspectItem by viewModel.inspectItem.collectAsState()

    val haptic = LocalHapticFeedback.current

    if (showReviewDeletions) {
        ReviewDeletionsScreen(
            stagedItems = stagedDeletions,
            onConfirmDeletions = { finalItems ->
                viewModel.commitStagedDeletions(finalItems)
            },
            onCancel = {
                viewModel.closeReviewDeletionsScreen()
            }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Bar: Back Button, Title, Staged Review Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBackToHome,
                        modifier = Modifier
                            .size(38.dp)
                            .background(DarkSurfaceElevated, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Months",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = activeTitle,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${deck.size} remaining",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Staged Deletions Review Button (if any)
                if (stagedDeletions.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x33EF4444))
                            .border(1.dp, TrashRed, RoundedCornerShape(16.dp))
                            .clickable { viewModel.openReviewDeletionsScreen() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = TrashRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Review (${stagedDeletions.size})",
                                color = TrashRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Card Stack Deck Region
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            color = AccentIndigo,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    deck.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Color(0x2210B981), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Done",
                                    tint = KeepGreen,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "Month Sorted! 🎉",
                                color = TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (stagedDeletions.isNotEmpty()) {
                                    "You have ${stagedDeletions.size} photos ready for review before moving to the 7-day bin."
                                } else {
                                    "You've reviewed every photo in this month. Awesome job!"
                                },
                                color = TextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            if (stagedDeletions.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(TrashRed)
                                        .clickable { viewModel.openReviewDeletionsScreen() }
                                        .padding(horizontal = 24.dp, vertical = 14.dp)
                                ) {
                                    Text(
                                        text = "Review & Clean (${stagedDeletions.size} photos)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(AccentIndigo)
                                        .clickable { onNavigateBackToHome() }
                                        .padding(horizontal = 24.dp, vertical = 14.dp)
                                ) {
                                    Text(
                                        text = "Back to Months",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // Underlying Card (preview behind top card)
                        if (deck.size > 1) {
                            androidx.compose.runtime.key(deck[1].id) {
                                SwipeCard(
                                    mediaItem = deck[1],
                                    isTopCard = false,
                                    onSwipeRight = {},
                                    onSwipeLeft = {}
                                )
                            }
                        }

                        // Top Active Card
                        val topItem = deck[0]
                        androidx.compose.runtime.key(topItem.id) {
                            SwipeCard(
                                mediaItem = topItem,
                                isTopCard = true,
                                onSwipeRight = { item ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.swipeRight(item)
                                },
                                onSwipeLeft = { item ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.swipeLeft(item)
                                },
                                onSwipeUp = { item ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.swipeUpBookmark(item)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Floating Controls (Trash, Undo, Bookmark, Zoom, Keep)
            if (deck.isNotEmpty()) {
                val currentItem = deck[0]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Trash Button (Swipe Left shortcut)
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.swipeLeft(currentItem)
                        },
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color(0x22EF4444), CircleShape)
                            .border(2.dp, TrashRed, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Trash",
                            tint = TrashRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Undo Button
                    IconButton(
                        onClick = {
                            if (canUndo) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.undo()
                            }
                        },
                        enabled = canUndo,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (canUndo) Color(0x22F59E0B) else Color(0x11FFFFFF),
                                CircleShape
                            )
                            .border(1.5.dp, if (canUndo) UndoYellow else DarkBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                            contentDescription = "Undo",
                            tint = if (canUndo) UndoYellow else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Bookmark / Decide Later (Swipe Up shortcut)
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.swipeUpBookmark(currentItem)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0x226366F1), CircleShape)
                            .border(1.5.dp, AccentIndigo, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Bookmark",
                            tint = AccentIndigo,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Zoom / Inspect Fullscreen Button
                    IconButton(
                        onClick = {
                            viewModel.setInspectItem(currentItem)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(DarkSurfaceElevated, CircleShape)
                            .border(1.5.dp, DarkBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Keep Button (Swipe Right shortcut)
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.swipeRight(currentItem)
                        },
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color(0x2210B981), CircleShape)
                            .border(2.dp, KeepGreen, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Keep",
                            tint = KeepGreen,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }

        // Fullscreen Inspect Modal Dialog
        inspectItem?.let { item ->
            Dialog(
                onDismissRequest = { viewModel.setInspectItem(null) },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    if (item.isVideo) {
                        VideoPlayerView(
                            mediaItem = item,
                            isActiveCard = true,
                            showTimeline = true
                        )
                    } else {
                        PhotoViewer(
                            mediaItem = item,
                            enableZoom = true,
                            showMetadata = true
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setInspectItem(null) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp)
                            .size(44.dp)
                            .background(Color(0x88000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
