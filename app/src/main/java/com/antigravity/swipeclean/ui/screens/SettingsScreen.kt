package com.antigravity.swipeclean.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.antigravity.swipeclean.MainViewModel
import com.antigravity.swipeclean.ui.theme.BorderSubtle
import com.antigravity.swipeclean.ui.theme.KeepGreen
import com.antigravity.swipeclean.ui.theme.LaterBlue
import com.antigravity.swipeclean.ui.theme.OledBlack
import com.antigravity.swipeclean.ui.theme.SurfaceBase
import com.antigravity.swipeclean.ui.theme.SurfaceElevated
import com.antigravity.swipeclean.ui.theme.TextMuted
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.stats.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Stats",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Space freed and cleaning summary",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    value = stats.formattedFreedSize,
                    label = "Space freed",
                    detail = "${stats.lifetimeDeletedCount} deleted",
                    valueColor = KeepGreen,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = "${stats.totalKeptCount}",
                    label = "Kept",
                    detail = "Remembered",
                    valueColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    value = "${stats.binItemCount}",
                    label = "In bin",
                    detail = stats.formattedBinSize,
                    valueColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = "${stats.totalLaterCount}",
                    label = "Later",
                    detail = "Saved items",
                    valueColor = LaterBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Preferences",
                color = TextSecondary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceBase),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reset kept history",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Allow kept photos and videos to appear in swipe decks again.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Reset",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "All scanning and cleanup happen on your device. Dark theme only.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Reset kept history?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Kept items will return to your swipe decks. Items in the bin and later list will stay untouched.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetKeptHistory()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPrimary,
                        contentColor = OledBlack
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = "Reset")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetDialog = false },
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = "Cancel", color = TextPrimary)
                }
            },
            containerColor = SurfaceElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    detail: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
