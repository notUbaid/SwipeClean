package com.antigravity.swipeclean.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antigravity.swipeclean.MainViewModel
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.ui.components.BinItemCard
import com.antigravity.swipeclean.ui.theme.BorderSubtle
import com.antigravity.swipeclean.ui.theme.CautionAmber
import com.antigravity.swipeclean.ui.theme.DeleteRed
import com.antigravity.swipeclean.ui.theme.OledBlack
import com.antigravity.swipeclean.ui.theme.SurfaceElevated
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BinScreen(
    viewModel: MainViewModel,
    onRequestPermanentDelete: (List<MediaItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val trashedList by viewModel.trashedList.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val expiredItems = trashedList.filter { it.isExpired }.map { it.mediaItem }

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
                        text = "Bin",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Files stay in your gallery until deleted here",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${trashedList.size} items",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (trashedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Bin is empty",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Items marked for deletion stay here until you restore them or delete them permanently.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                                    text = "In bin",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stats.formattedBinSize,
                                    color = DeleteRed,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }

                            if (expiredItems.isNotEmpty()) {
                                Text(
                                    text = "${expiredItems.size} past 7 days",
                                    color = CautionAmber,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.restoreAllFromBin() },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "Restore all",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            if (expiredItems.isNotEmpty()) {
                                Button(
                                    onClick = { onRequestPermanentDelete(expiredItems) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CautionAmber,
                                        contentColor = OledBlack
                                    ),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Delete expired (${expiredItems.size})",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    onRequestPermanentDelete(trashedList.map { it.mediaItem })
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeleteRed,
                                    contentColor = TextPrimary
                                ),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "Delete all",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(trashedList, key = { it.mediaItem.stableKey }) { item ->
                        BinItemCard(
                            trashedItem = item,
                            onRestore = { viewModel.restoreFromBin(item) },
                            onDeletePermanently = {
                                onRequestPermanentDelete(listOf(item.mediaItem))
                            },
                            onClick = { viewModel.setInspectItem(item.mediaItem) }
                        )
                    }
                }
            }
        }
    }
}
