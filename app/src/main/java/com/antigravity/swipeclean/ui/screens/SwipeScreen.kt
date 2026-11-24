package com.antigravity.swipeclean.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antigravity.swipeclean.MainViewModel
import com.antigravity.swipeclean.ui.components.SwipeCard
import com.antigravity.swipeclean.ui.theme.CautionAmber
import com.antigravity.swipeclean.ui.theme.CautionAmberTint
import com.antigravity.swipeclean.ui.theme.DeleteRed
import com.antigravity.swipeclean.ui.theme.DeleteRedTint
import com.antigravity.swipeclean.ui.theme.KeepGreen
import com.antigravity.swipeclean.ui.theme.KeepGreenTint
import com.antigravity.swipeclean.ui.theme.LaterBlue
import com.antigravity.swipeclean.ui.theme.LaterBlueTint
import com.antigravity.swipeclean.ui.theme.OledBlack
import com.antigravity.swipeclean.ui.theme.SurfaceBase
import com.antigravity.swipeclean.ui.theme.SurfaceElevated
import com.antigravity.swipeclean.ui.theme.TextMuted
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary
import com.antigravity.swipeclean.util.MediaSortOrder
import com.antigravity.swipeclean.util.MediaTypeFilter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwipeScreen(
    viewModel: MainViewModel,
    isViewerOpen: Boolean,
    onNavigateBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deck by viewModel.mediaDeck.collectAsState()
    val activeTitle by viewModel.activeDeckTitle.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val stagedDeletions by viewModel.stagedForDeletion.collectAsState()
    val showReviewDeletions by viewModel.showReviewDeletions.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scanError by viewModel.scanError.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    val hasCustomFilterOrSort = typeFilter != MediaTypeFilter.ALL || sortOrder != MediaSortOrder.NEWEST
    val subtitleText = buildList {
        add("${deck.size} left")
        if (typeFilter != MediaTypeFilter.ALL) add(typeFilter.label)
        if (sortOrder != MediaSortOrder.NEWEST) add(sortOrder.label)
    }.joinToString("  ·  ")

    val haptic = LocalHapticFeedback.current

    if (showReviewDeletions) {
        BackHandler { viewModel.closeReviewDeletionsScreen() }
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
            .background(OledBlack),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onNavigateBackToHome,
                        modifier = Modifier
                            .size(48.dp)
                            .background(SurfaceElevated, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to months",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeTitle,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitleText,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (hasCustomFilterOrSort) LaterBlueTint else SurfaceElevated,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Sort and filter",
                            tint = if (hasCustomFilterOrSort) LaterBlue else TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (stagedDeletions.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(DeleteRedTint)
                                .clickable { viewModel.openReviewDeletionsScreen() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Review (${stagedDeletions.size})",
                                color = DeleteRed,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading && deck.isEmpty() -> {
                        CircularProgressIndicator(
                            color = TextPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    scanError != null && deck.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Text(
                                text = "Couldn't read gallery",
                                color = TextPrimary,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = scanError ?: "Unknown error",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.loadData(forceLibraryScan = true, showSpinner = true) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TextPrimary,
                                    contentColor = OledBlack
                                ),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(text = "Retry", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    deck.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Text(
                                text = if (typeFilter != MediaTypeFilter.ALL && stagedDeletions.isEmpty()) {
                                    "No ${typeFilter.label.lowercase()} left"
                                } else {
                                    "All caught up"
                                },
                                color = TextPrimary,
                                style = MaterialTheme.typography.headlineSmall
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = when {
                                    stagedDeletions.isNotEmpty() ->
                                        "${stagedDeletions.size} items ready to review before moving to the bin."
                                    typeFilter != MediaTypeFilter.ALL ->
                                        "Nothing matching ${typeFilter.label.lowercase()} left in $activeTitle."
                                    else ->
                                        "Nothing left to review in $activeTitle."
                                },
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            if (stagedDeletions.isNotEmpty()) {
                                Button(
                                    onClick = { viewModel.openReviewDeletionsScreen() },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DeleteRed,
                                        contentColor = TextPrimary
                                    ),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Review staged (${stagedDeletions.size})",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                if (typeFilter != MediaTypeFilter.ALL) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { viewModel.clearDeckFilter() },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            text = "Show all media",
                                            color = LaterBlue,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            } else if (typeFilter != MediaTypeFilter.ALL) {
                                Button(
                                    onClick = { viewModel.clearDeckFilter() },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TextPrimary,
                                        contentColor = OledBlack
                                    ),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Show all media",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onNavigateBackToHome,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TextPrimary,
                                        contentColor = OledBlack
                                    ),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Back to months",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        if (deck.size > 1) {
                            key(deck[1].stableKey) {
                                SwipeCard(
                                    mediaItem = deck[1],
                                    isTopCard = false,
                                    isPlaybackActive = false,
                                    onSwipeRight = {},
                                    onSwipeLeft = {}
                                )
                            }
                        }

                        val topItem = deck[0]
                        key(topItem.stableKey) {
                            SwipeCard(
                                mediaItem = topItem,
                                isTopCard = true,
                                isPlaybackActive = !isViewerOpen,
                                onSwipeRight = { item ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.swipeRight(item)
                                },
                                onSwipeLeft = { item ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.swipeLeft(item)
                                },
                                onSwipeLater = { item ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.swipeLater(item)
                                },
                                onInspectClick = { item ->
                                    viewModel.setInspectItem(item)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (deck.isNotEmpty()) {
                val currentItem = deck[0]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.swipeLeft(currentItem)
                        },
                        modifier = Modifier
                            .size(58.dp)
                            .background(DeleteRedTint, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = DeleteRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }

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
                                if (canUndo) CautionAmberTint else SurfaceElevated,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                            contentDescription = "Undo",
                            tint = if (canUndo) CautionAmber else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.swipeLater(currentItem)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(LaterBlueTint, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Save for later",
                            tint = LaterBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setInspectItem(currentItem) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(SurfaceElevated, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Inspect fullscreen",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.swipeRight(currentItem)
                        },
                        modifier = Modifier
                            .size(58.dp)
                            .background(KeepGreenTint, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Keep",
                            tint = KeepGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceElevated,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sort and filter",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (hasCustomFilterOrSort) {
                        TextButton(
                            onClick = {
                                viewModel.clearDeckFilter()
                                showFilterSheet = false
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "Reset",
                                color = LaterBlue,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Media type",
                    color = TextSecondary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaTypeFilter.entries.forEach { option ->
                        val selected = option == typeFilter
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateDeckFilterAndSort(filter = option) },
                            label = {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            border = null,
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceBase,
                                labelColor = TextSecondary,
                                selectedContainerColor = LaterBlueTint,
                                selectedLabelColor = LaterBlue
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Sort by",
                    color = TextSecondary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaSortOrder.entries.forEach { option ->
                        val selected = option == sortOrder
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateDeckFilterAndSort(sort = option) },
                            label = {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            border = null,
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceBase,
                                labelColor = TextSecondary,
                                selectedContainerColor = LaterBlueTint,
                                selectedLabelColor = LaterBlue
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showFilterSheet = false },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPrimary,
                        contentColor = OledBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
