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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.swipeclean.MainViewModel
import com.antigravity.swipeclean.data.model.AlbumGroup
import com.antigravity.swipeclean.ui.theme.AccentIndigo
import com.antigravity.swipeclean.ui.theme.DarkBackground
import com.antigravity.swipeclean.ui.theme.DarkBorder
import com.antigravity.swipeclean.ui.theme.DarkSurface
import com.antigravity.swipeclean.ui.theme.DarkSurfaceElevated
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary

@Composable
fun AlbumsScreen(
    viewModel: MainViewModel,
    onAlbumSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups by viewModel.albumGroups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()

    val monthGroups = groups.filter { it.isMonth }
    val folderGroups = groups.filter { !it.isMonth && it.key != "ALL" }
    val allGroup = groups.firstOrNull { it.key == "ALL" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Clean by Category",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Select a month or folder to focus your swipe cleaning",
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // "All Media" Header Card
            allGroup?.let { group ->
                item {
                    AlbumItemCard(
                        group = group,
                        isSelected = selectedGroup?.key == group.key,
                        onClick = {
                            viewModel.selectGroup(group)
                            onAlbumSelected()
                        }
                    )
                }
            }

            // Month section
            if (monthGroups.isNotEmpty()) {
                item {
                    Text(
                        text = "MONTHS",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(monthGroups, key = { it.key }) { group ->
                    AlbumItemCard(
                        group = group,
                        isSelected = selectedGroup?.key == group.key,
                        onClick = {
                            viewModel.selectGroup(group)
                            onAlbumSelected()
                        }
                    )
                }
            }

            // Folder section
            if (folderGroups.isNotEmpty()) {
                item {
                    Text(
                        text = "FOLDERS & ALBUMS",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(folderGroups, key = { it.key }) { group ->
                    AlbumItemCard(
                        group = group,
                        isSelected = selectedGroup?.key == group.key,
                        onClick = {
                            viewModel.selectGroup(group)
                            onAlbumSelected()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumItemCard(
    group: AlbumGroup,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) AccentIndigo else DarkBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0x336366F1) else Color(0x18FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        group.key == "ALL" -> Icons.Default.PhotoLibrary
                        group.isMonth -> Icons.Default.CalendarMonth
                        else -> Icons.Default.Folder
                    },
                    contentDescription = null,
                    tint = if (isSelected) AccentIndigo else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.displayName,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${group.count} items  •  ${group.formattedSize}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = AccentIndigo,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
