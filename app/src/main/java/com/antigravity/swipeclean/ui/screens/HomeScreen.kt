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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.antigravity.swipeclean.MainViewModel
import com.antigravity.swipeclean.data.model.MonthProgress
import com.antigravity.swipeclean.data.model.QuickCleanCategory
import com.antigravity.swipeclean.data.model.QuickCleanGroup
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

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isPartialAccess: Boolean,
    onRequestFullAccess: () -> Unit,
    onStartMonthSwipe: (String, String) -> Unit,
    onStartQuickClean: (QuickCleanCategory) -> Unit,
    onStartOnThisDaySwipe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthList by viewModel.monthProgressList.collectAsState()
    val quickCleanGroups by viewModel.quickCleanGroups.collectAsState()
    val onThisDayItems by viewModel.onThisDayList.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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
                                text = "Your library has no media items, or access is restricted.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (isPartialAccess) {
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    onClick = onRequestFullAccess,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Allow all photos and videos",
                                        color = LaterBlue,
                                        style = MaterialTheme.typography.labelLarge
                                    )
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
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Limited media access enabled",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = onRequestFullAccess,
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text(
                                                text = "Allow all",
                                                color = LaterBlue,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (onThisDayItems.isNotEmpty()) {
                            item(key = "on_this_day") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onStartOnThisDaySwipe() },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "On this day",
                                                color = TextPrimary,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${onThisDayItems.size} items from past years",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Open deck",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (quickCleanGroups.isNotEmpty()) {
                            item(key = "quick_clean_header") {
                                Text(
                                    text = "Quick clean",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            item(key = "quick_clean_row") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    quickCleanGroups.forEach { group ->
                                        QuickCleanCard(
                                            group = group,
                                            modifier = Modifier.weight(1f),
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
private fun QuickCleanCard(
    group: QuickCleanGroup,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Text(
                text = group.category.title,
                color = TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${group.count} items",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
            Text(
                text = group.formattedSize,
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
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
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (month.isCompleted) KeepGreen.copy(alpha = 0.35f) else BorderSubtle
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
