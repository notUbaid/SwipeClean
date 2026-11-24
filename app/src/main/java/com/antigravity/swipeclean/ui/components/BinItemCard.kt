package com.antigravity.swipeclean.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.antigravity.swipeclean.data.model.TrashedItem
import com.antigravity.swipeclean.ui.theme.BorderSubtle
import com.antigravity.swipeclean.ui.theme.CautionAmber
import com.antigravity.swipeclean.ui.theme.CautionAmberTint
import com.antigravity.swipeclean.ui.theme.DeleteRed
import com.antigravity.swipeclean.ui.theme.DeleteRedTint
import com.antigravity.swipeclean.ui.theme.KeepGreen
import com.antigravity.swipeclean.ui.theme.KeepGreenTint
import com.antigravity.swipeclean.ui.theme.ScrimDark
import com.antigravity.swipeclean.ui.theme.SurfaceBase
import com.antigravity.swipeclean.ui.theme.SurfaceElevated
import com.antigravity.swipeclean.ui.theme.TextMuted
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary

@Composable
fun BinItemCard(
    trashedItem: TrashedItem,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = trashedItem.mediaItem
    val context = LocalContext.current
    val status = trashedItem.binStatus()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
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
                            .fillMaxSize()
                            .background(ScrimDark.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Video",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.formattedSize,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val badgeColor = when {
                        status.isExpired -> DeleteRed
                        status.daysRemaining <= 2 -> CautionAmber
                        else -> TextSecondary
                    }
                    val badgeBg = when {
                        status.isExpired -> DeleteRedTint
                        status.daysRemaining <= 2 -> CautionAmberTint
                        else -> SurfaceElevated
                    }

                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = status.label,
                            color = badgeColor,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.bucketName,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRestore,
                modifier = Modifier
                    .size(48.dp)
                    .background(KeepGreenTint, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Restore item",
                    tint = KeepGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onDeletePermanently,
                modifier = Modifier
                    .size(48.dp)
                    .background(DeleteRedTint, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Delete permanently",
                    tint = DeleteRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
