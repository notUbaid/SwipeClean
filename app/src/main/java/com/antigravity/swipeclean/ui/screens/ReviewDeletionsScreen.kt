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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.ui.theme.BorderSubtle
import com.antigravity.swipeclean.ui.theme.DeleteRed
import com.antigravity.swipeclean.ui.theme.DeleteRedTint
import com.antigravity.swipeclean.ui.theme.OledBlack
import com.antigravity.swipeclean.ui.theme.ScrimDark
import com.antigravity.swipeclean.ui.theme.SurfaceBase
import com.antigravity.swipeclean.ui.theme.SurfaceElevated
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary
import com.antigravity.swipeclean.util.formatBytes

@Composable
fun ReviewDeletionsScreen(
    stagedItems: List<MediaItem>,
    onConfirmDeletions: (List<MediaItem>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectionMap = remember(stagedItems) {
        mutableStateMapOf<String, Boolean>().apply {
            stagedItems.forEach { put(it.stableKey, true) }
        }
    }

    val selectedCount = selectionMap.values.count { it }
    val selectedTotalSize = stagedItems
        .filter { selectionMap[it.stableKey] == true }
        .sumOf { maxOf(0L, it.sizeBytes) }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Review deletions",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Tap any item to untick and keep it",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(48.dp)
                        .background(SurfaceElevated, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close review",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Selected for bin",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = formatBytes(selectedTotalSize),
                                color = if (selectedCount > 0) DeleteRed else TextPrimary,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(
                                    if (selectedCount > 0) DeleteRedTint else SurfaceBase,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$selectedCount of ${stagedItems.size}",
                                color = if (selectedCount > 0) DeleteRed else TextSecondary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Files remain in your gallery until deleted in the bin.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stagedItems, key = { it.stableKey }) { item ->
                    val isChecked = selectionMap[item.stableKey] ?: true

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceBase)
                            .clickable {
                                selectionMap[item.stableKey] = !isChecked
                            }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (item.isVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .background(ScrimDark, CircleShape)
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Video",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(ScrimDark, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.formattedSize,
                                color = TextPrimary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isChecked) DeleteRed else ScrimDark),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecked) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Selected to delete",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text(
                        text = "Keep swiping",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = {
                        val finalDeletions = stagedItems.filter { selectionMap[it.stableKey] == true }
                        onConfirmDeletions(finalDeletions)
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedCount > 0) DeleteRed else TextPrimary,
                        contentColor = if (selectedCount > 0) TextPrimary else OledBlack
                    )
                ) {
                    Text(
                        text = if (selectedCount > 0) "Move $selectedCount to bin" else "Keep all and finish",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
