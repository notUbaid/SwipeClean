package com.antigravity.swipeclean.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.antigravity.swipeclean.MainViewModel
import com.antigravity.swipeclean.data.model.MonthProgress
import com.antigravity.swipeclean.data.model.QuickCleanCategory
import com.antigravity.swipeclean.ui.theme.BorderSubtle
import com.antigravity.swipeclean.ui.theme.CautionAmber
import com.antigravity.swipeclean.ui.theme.CautionAmberTint
import com.antigravity.swipeclean.ui.theme.KeepGreen
import com.antigravity.swipeclean.ui.theme.KeepGreenTint
import com.antigravity.swipeclean.ui.theme.LaterBlue
import com.antigravity.swipeclean.ui.theme.OledBlack
import com.antigravity.swipeclean.ui.theme.SurfaceBase
import com.antigravity.swipeclean.ui.theme.SurfaceElevated
import com.antigravity.swipeclean.ui.theme.TextMuted
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary
import com.antigravity.swipeclean.util.MediaSortOrder
import com.antigravity.swipeclean.util.MediaTypeFilter
import com.antigravity.swipeclean.util.formatBytes

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isPartialAccess: Boolean,
    onSelectMoreMedia: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStartAllItemsSwipe: () -> Unit,
    onStartFilteredDeck: (MediaTypeFilter, MediaSortOrder, String) -> Unit,
    onStartMonthSwipe: (String, String) -> Unit,
    onStartQuickClean: (QuickCleanCategory) -> Unit,
    onStartOnThisDaySwipe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthList by viewModel.monthProgressList.collectAsState()
    val quickCleanGroups by viewModel.quickCleanGroups.collectAsState()
    val unreviewedSummary by viewModel.unreviewedSummary.collectAsState()
    val onThisDayItems by viewModel.onThisDayList.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scanError by viewModel.scanError.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SwipeClean",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )

                if (stats.streakDays >= 2) {
                    Box(
                        modifier = Modifier
                            .background(CautionAmberTint, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${stats.streakDays}-day streak",
                            color = CautionAmber,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading && monthList.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = TextPrimary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Scanning gallery",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                scanError != null && monthList.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "Couldn't read gallery",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = scanError ?: "Unknown error",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadData(forceLibraryScan = true, showSpinner = true) },
                                shape = RoundedCornerShape(12.dp),
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
                }
                monthList.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "No photos or videos found",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isPartialAccess) {
                                    "Only selected photos are visible. Pick photos or allow full access in system settings."
                                } else {
                                    "Your gallery has no indexed photos or videos."
                                },
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            if (isPartialAccess) {
                                Spacer(modifier = Modifier.height(18.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = onSelectMoreMedia,
                                        border = BorderStroke(1.dp, BorderSubtle),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Select photos", color = TextPrimary)
                                    }
                                    Button(
                                        onClick = onOpenAppSettings,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TextPrimary,
                                            contentColor = OledBlack
                                        ),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("App settings")
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isPartialAccess) {
                            item(key = "partial_access_banner") {
                                PartialAccessCard(
                                    onSelectMore = onSelectMoreMedia,
                                    onOpenSettings = onOpenAppSettings
                                )
                            }
                        }

                        if (unreviewedSummary.count > 0) {
                            item(key = "all_items_card") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onStartAllItemsSwipe() },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Start cleaning",
                                                color = TextPrimary,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${unreviewedSummary.count} unreviewed  ·  ${formatBytes(unreviewedSummary.totalSizeBytes)}",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Start cleaning all items",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (unreviewedSummary.count > 0 || onThisDayItems.isNotEmpty() || quickCleanGroups.isNotEmpty()) {
                            item(key = "quick_filter_strip") {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (unreviewedSummary.videoCount > 0) {
                                        item(key = "chip_videos") {
                                            HomeFilterChip(
                                                label = "Videos",
                                                detail = "${unreviewedSummary.videoCount}",
                                                onClick = {
                                                    onStartFilteredDeck(
                                                        MediaTypeFilter.VIDEOS,
                                                        MediaSortOrder.NEWEST,
                                                        "Videos"
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    if (unreviewedSummary.photoCount > 0) {
                                        item(key = "chip_photos") {
                                            HomeFilterChip(
                                                label = "Photos",
                                                detail = "${unreviewedSummary.photoCount}",
                                                onClick = {
                                                    onStartFilteredDeck(
                                                        MediaTypeFilter.PHOTOS,
                                                        MediaSortOrder.NEWEST,
                                                        "Photos"
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    if (unreviewedSummary.count > 0) {
                                        item(key = "chip_largest") {
                                            HomeFilterChip(
                                                label = "Largest first",
                                                detail = null,
                                                onClick = {
                                                    onStartFilteredDeck(
                                                        MediaTypeFilter.ALL,
                                                        MediaSortOrder.LARGEST,
                                                        "Largest files"
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    if (onThisDayItems.isNotEmpty()) {
                                        item(key = "chip_on_this_day") {
                                            HomeFilterChip(
                                                label = "On this day",
                                                detail = "${onThisDayItems.size}",
                                                onClick = onStartOnThisDaySwipe
                                            )
                                        }
                                    }
                                    items(quickCleanGroups, key = { "qc_${it.category.key}" }) { group ->
                                        HomeFilterChip(
                                            label = group.category.title,
                                            detail = "${group.count}  ·  ${group.formattedSize}",
                                            onClick = { onStartQuickClean(group.category) }
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "by_month_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "By month",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${monthList.count { it.isCompleted }} of ${monthList.size} done",
                                    color = TextMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        items(monthList, key = { it.monthKey }) { month ->
                            MonthProgressCard(
                                month = month,
                                onClick = {
                                    onStartMonthSwipe(month.monthKey, month.monthDisplay)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartialAccessCard(
    onSelectMore: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Limited media access",
                color = CautionAmber,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Only selected photos and videos are shown. Grant 'Allow all' in app settings to clean your full gallery.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onSelectMore,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Select more",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Open settings",
                        color = LaterBlue,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFilterChip(
    label: String,
    detail: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = SurfaceBase,
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
            if (detail != null) {
                Text(
                    text = detail,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MonthProgressCard(
    month: MonthProgress,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (month.isCompleted) SurfaceElevated else SurfaceBase
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (month.coverUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(month.coverUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (month.isCompleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(KeepGreenTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = KeepGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = month.monthDisplay,
                    color = if (month.isCompleted) KeepGreen else TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (month.isCompleted) {
                    "${month.totalCount} items reviewed"
                } else {
                    "${month.reviewedCount} of ${month.totalCount}  ·  ${month.formattedSize}"
                }
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (month.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = KeepGreen,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { month.progress },
                        modifier = Modifier.fillMaxSize(),
                        color = TextPrimary,
                        trackColor = BorderSubtle,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "${(month.progress * 100).toInt()}%",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
