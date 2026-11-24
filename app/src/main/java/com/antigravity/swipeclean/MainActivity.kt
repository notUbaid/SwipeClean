package com.antigravity.swipeclean

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.antigravity.swipeclean.data.repository.MediaRepository
import com.antigravity.swipeclean.ui.screens.BookmarksScreen
import com.antigravity.swipeclean.ui.screens.BinScreen
import com.antigravity.swipeclean.ui.screens.HomeScreen
import com.antigravity.swipeclean.ui.screens.SettingsScreen
import com.antigravity.swipeclean.ui.screens.SwipeScreen
import com.antigravity.swipeclean.ui.theme.AccentIndigo
import com.antigravity.swipeclean.ui.theme.DarkBackground
import com.antigravity.swipeclean.ui.theme.DarkSurface
import com.antigravity.swipeclean.ui.theme.SwipeCleanTheme
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary
import com.antigravity.swipeclean.ui.theme.TrashRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class AppTab {
    MONTHS, CLEAN, BOOKMARKS, BIN, SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var mediaRepository: MediaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaRepository = MediaRepository(this)

        setContent {
            SwipeCleanTheme {
                MainAppContainer(
                    viewModel = viewModel,
                    mediaRepository = mediaRepository
                )
            }
        }
    }
}

@Composable
fun MainAppContainer(
    viewModel: MainViewModel,
    mediaRepository: MediaRepository
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.MONTHS) }
    var hasPermissions by remember { mutableStateOf(checkMediaPermissions(context)) }

    val trashedList by viewModel.trashedList.collectAsState()
    val bookmarkedList by viewModel.bookmarkedList.collectAsState()

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        hasPermissions = granted
        if (granted) {
            viewModel.loadData()
        }
    }

    var pendingDeletionUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val deletedIds = pendingDeletionUris.mapNotNull { it.lastPathSegment?.toLongOrNull() }
            viewModel.onPermanentDeletionComplete(deletedIds)
            pendingDeletionUris = emptyList()
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            viewModel.loadData()
        }
    }

    if (!hasPermissions) {
        PermissionRequestScreen(
            onGrantClick = {
                permissionLauncher.launch(requiredPermissions)
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    tonalElevation = 8.dp
                ) {
                    // Months Dashboard Tab (Swipewipe Core)
                    NavigationBarItem(
                        selected = currentTab == AppTab.MONTHS,
                        onClick = { currentTab = AppTab.MONTHS },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Months"
                            )
                        },
                        label = { Text("Months", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentIndigo,
                            selectedTextColor = AccentIndigo,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0x336366F1)
                        )
                    )

                    // Clean / Swipe Tab
                    NavigationBarItem(
                        selected = currentTab == AppTab.CLEAN,
                        onClick = { currentTab = AppTab.CLEAN },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = "Clean"
                            )
                        },
                        label = { Text("Swipe", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentIndigo,
                            selectedTextColor = AccentIndigo,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0x336366F1)
                        )
                    )

                    // Bookmarks ("Decide Later") Tab
                    NavigationBarItem(
                        selected = currentTab == AppTab.BOOKMARKS,
                        onClick = { currentTab = AppTab.BOOKMARKS },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (bookmarkedList.isNotEmpty()) {
                                        Badge(
                                            containerColor = AccentIndigo,
                                            contentColor = Color.White
                                        ) {
                                            Text(bookmarkedList.size.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Bookmarks"
                                )
                            }
                        },
                        label = { Text("Saved", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentIndigo,
                            selectedTextColor = AccentIndigo,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0x336366F1)
                        )
                    )

                    // Bin Tab
                    NavigationBarItem(
                        selected = currentTab == AppTab.BIN,
                        onClick = { currentTab = AppTab.BIN },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (trashedList.isNotEmpty()) {
                                        Badge(
                                            containerColor = TrashRed,
                                            contentColor = Color.White
                                        ) {
                                            Text(trashedList.size.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Bin"
                                )
                            }
                        },
                        label = { Text("Bin", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TrashRed,
                            selectedTextColor = TrashRed,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0x33EF4444)
                        )
                    )

                    // Settings Tab
                    NavigationBarItem(
                        selected = currentTab == AppTab.SETTINGS,
                        onClick = { currentTab = AppTab.SETTINGS },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Stats"
                            )
                        },
                        label = { Text("Stats", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentIndigo,
                            selectedTextColor = AccentIndigo,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0x336366F1)
                        )
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Crossfade(targetState = currentTab, label = "TabCrossfade") { tab ->
                    when (tab) {
                        AppTab.MONTHS -> {
                            HomeScreen(
                                viewModel = viewModel,
                                onStartMonthSwipe = { monthKey, monthDisplay ->
                                    viewModel.startMonthDeck(monthKey, monthDisplay)
                                    currentTab = AppTab.CLEAN
                                },
                                onStartOnThisDaySwipe = {
                                    viewModel.startOnThisDayDeck()
                                    currentTab = AppTab.CLEAN
                                }
                            )
                        }
                        AppTab.CLEAN -> {
                            SwipeScreen(
                                viewModel = viewModel,
                                onNavigateBackToHome = { currentTab = AppTab.MONTHS }
                            )
                        }
                        AppTab.BOOKMARKS -> {
                            BookmarksScreen(
                                viewModel = viewModel
                            )
                        }
                        AppTab.BIN -> {
                            BinScreen(
                                viewModel = viewModel,
                                onRequestPermanentDelete = { uris ->
                                    val safeBatch = uris.take(200)
                                    pendingDeletionUris = safeBatch
                                    val pendingIntent = mediaRepository.createDeletePendingIntent(safeBatch)
                                    if (pendingIntent != null) {
                                        val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                        deleteIntentLauncher.launch(request)
                                    } else {
                                        CoroutineScope(Dispatchers.Main).launch {
                                            mediaRepository.deletePermanentlyLegacy(safeBatch)
                                            val ids = safeBatch.mapNotNull { it.lastPathSegment?.toLongOrNull() }
                                            viewModel.onPermanentDeletionComplete(ids)
                                        }
                                    }
                                }
                            )
                        }
                        AppTab.SETTINGS -> {
                            SettingsScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onGrantClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color(0x226366F1), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AccentIndigo,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to SwipeClean",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Clean your camera roll month by month. Swipe right to keep, swipe left to delete, and swipe up to bookmark. 100% free with no subscriptions or limits.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Grant Photos & Videos Access",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

private fun checkMediaPermissions(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val img = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val vid = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        val selected = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        img || vid || selected
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val img = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val vid = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        img || vid
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}
