package com.antigravity.swipeclean

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.ui.components.PhotoViewer
import com.antigravity.swipeclean.ui.components.VideoPlayerView
import com.antigravity.swipeclean.ui.screens.BinScreen
import com.antigravity.swipeclean.ui.screens.HomeScreen
import com.antigravity.swipeclean.ui.screens.LaterScreen
import com.antigravity.swipeclean.ui.screens.SettingsScreen
import com.antigravity.swipeclean.ui.screens.SwipeScreen
import com.antigravity.swipeclean.ui.theme.DeleteRed
import com.antigravity.swipeclean.ui.theme.LaterBlue
import com.antigravity.swipeclean.ui.theme.OledBlack
import com.antigravity.swipeclean.ui.theme.ScrimDark
import com.antigravity.swipeclean.ui.theme.SurfaceBase
import com.antigravity.swipeclean.ui.theme.SurfaceElevated
import com.antigravity.swipeclean.ui.theme.SwipeCleanTheme
import com.antigravity.swipeclean.ui.theme.TextPrimary
import com.antigravity.swipeclean.ui.theme.TextSecondary

private const val MAX_DELETE_BATCH = 200
private const val PREFS_NAME = "swipeclean_prefs"
private const val KEY_PERMISSION_ASKED = "permission_asked"

enum class AppTab {
    MONTHS, SWIPE, LATER, BIN, STATS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        setContent {
            SwipeCleanTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContainer(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var currentTab by rememberSaveable { mutableStateOf(AppTab.MONTHS) }
    var hasPermissions by remember { mutableStateOf(checkMediaPermissions(context)) }
    var isPartialAccess by remember { mutableStateOf(checkPartialMediaAccess(context)) }
    var isPermanentlyDenied by remember {
        mutableStateOf(
            !hasPermissions &&
                prefs.getBoolean(KEY_PERMISSION_ASKED, false) &&
                isMediaPermissionPermanentlyDenied(activity)
        )
    }

    val trashedList by viewModel.trashedList.collectAsState()
    val laterList by viewModel.laterList.collectAsState()
    val inspectItem by viewModel.inspectItem.collectAsState()
    val showReviewDeletions by viewModel.showReviewDeletions.collectAsState()

    val requiredPermissions = remember { buildRequiredPermissions() }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        prefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
        val granted = checkMediaPermissions(context)
        val partial = checkPartialMediaAccess(context)
        hasPermissions = granted
        isPartialAccess = partial
        isPermanentlyDenied = !granted && isMediaPermissionPermanentlyDenied(activity)
        if (granted) {
            viewModel.loadData(forceLibraryScan = true)
        }
    }

    var pendingDeletionBatch by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var pendingRemainingCount by remember { mutableIntStateOf(0) }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val batch = pendingDeletionBatch
        val remaining = pendingRemainingCount
        pendingDeletionBatch = emptyList()
        pendingRemainingCount = 0

        if (result.resultCode == Activity.RESULT_OK && batch.isNotEmpty()) {
            viewModel.onPermanentDeletionCompleted(
                deletedItems = batch,
                remainingBatchCount = remaining,
                failedCount = 0
            )
        }
    }

    var pendingLegacyAfterWriteGrant by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val legacyWriteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val items = pendingLegacyAfterWriteGrant
        pendingLegacyAfterWriteGrant = emptyList()
        if (granted && items.isNotEmpty()) {
            val batch = items.take(MAX_DELETE_BATCH)
            viewModel.requestPermanentDeleteLegacy(batch, items.size - batch.size)
        } else if (!granted && items.isNotEmpty()) {
            Toast.makeText(
                context,
                "Storage write permission is required to delete files.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val grantedNow = checkMediaPermissions(context)
        val partialNow = checkPartialMediaAccess(context)
        val wasGranted = hasPermissions
        hasPermissions = grantedNow
        isPartialAccess = partialNow
        isPermanentlyDenied = !grantedNow &&
            prefs.getBoolean(KEY_PERMISSION_ASKED, false) &&
            isMediaPermissionPermanentlyDenied(activity)

        if (grantedNow) {
            if (!wasGranted) {
                viewModel.loadData(forceLibraryScan = true)
            } else {
                viewModel.onAppResumed()
            }
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            viewModel.loadData()
        }
    }

    BackHandler(enabled = hasPermissions && inspectItem == null && !showReviewDeletions && currentTab != AppTab.MONTHS) {
        currentTab = AppTab.MONTHS
    }

    if (!hasPermissions) {
        PermissionRequestScreen(
            isPermanentlyDenied = isPermanentlyDenied,
            onActionClick = {
                if (isPermanentlyDenied) {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                } else {
                    permissionLauncher.launch(requiredPermissions)
                }
            }
        )
        return
    }

    Scaffold(
        containerColor = OledBlack,
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceBase,
                tonalElevation = 0.dp
            ) {
                NavItem(
                    selected = currentTab == AppTab.MONTHS,
                    onClick = { currentTab = AppTab.MONTHS },
                    icon = Icons.Default.CalendarMonth,
                    label = "Months",
                    selectedColor = TextPrimary
                )
                NavItem(
                    selected = currentTab == AppTab.SWIPE,
                    onClick = { currentTab = AppTab.SWIPE },
                    icon = Icons.Default.Style,
                    label = "Swipe",
                    selectedColor = TextPrimary
                )
                NavItem(
                    selected = currentTab == AppTab.LATER,
                    onClick = { currentTab = AppTab.LATER },
                    icon = Icons.Default.Bookmark,
                    label = "Later",
                    badgeCount = laterList.size,
                    badgeColor = LaterBlue,
                    selectedColor = LaterBlue
                )
                NavItem(
                    selected = currentTab == AppTab.BIN,
                    onClick = { currentTab = AppTab.BIN },
                    icon = Icons.Default.Delete,
                    label = "Bin",
                    badgeCount = trashedList.size,
                    badgeColor = DeleteRed,
                    selectedColor = DeleteRed
                )
                NavItem(
                    selected = currentTab == AppTab.STATS,
                    onClick = { currentTab = AppTab.STATS },
                    icon = Icons.Default.BarChart,
                    label = "Stats",
                    selectedColor = TextPrimary
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OledBlack)
                .padding(paddingValues)
        ) {
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(durationMillis = 150),
                label = "TabCrossfade"
            ) { tab ->
                when (tab) {
                    AppTab.MONTHS -> {
                        HomeScreen(
                            viewModel = viewModel,
                            isPartialAccess = isPartialAccess,
                            onRequestFullAccess = { permissionLauncher.launch(requiredPermissions) },
                            onStartMonthSwipe = { monthKey, monthDisplay ->
                                viewModel.startMonthDeck(monthKey, monthDisplay)
                                currentTab = AppTab.SWIPE
                            },
                            onStartQuickClean = { category ->
                                viewModel.startQuickCleanDeck(category)
                                currentTab = AppTab.SWIPE
                            },
                            onStartOnThisDaySwipe = {
                                viewModel.startOnThisDayDeck()
                                currentTab = AppTab.SWIPE
                            }
                        )
                    }
                    AppTab.SWIPE -> {
                        SwipeScreen(
                            viewModel = viewModel,
                            isViewerOpen = inspectItem != null,
                            onNavigateBackToHome = { currentTab = AppTab.MONTHS }
                        )
                    }
                    AppTab.LATER -> {
                        LaterScreen(viewModel = viewModel)
                    }
                    AppTab.BIN -> {
                        BinScreen(
                            viewModel = viewModel,
                            onRequestPermanentDelete = { items ->
                                if (items.isEmpty()) return@BinScreen
                                val batch = items.take(MAX_DELETE_BATCH)
                                val remaining = items.size - batch.size

                                val pendingIntent = viewModel.createDeletePendingIntent(batch.map { it.uri })
                                if (pendingIntent != null) {
                                    pendingDeletionBatch = batch
                                    pendingRemainingCount = remaining
                                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                    deleteIntentLauncher.launch(request)
                                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    pendingLegacyAfterWriteGrant = items
                                    legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    viewModel.requestPermanentDeleteLegacy(batch, remaining)
                                }
                            }
                        )
                    }
                    AppTab.STATS -> {
                        SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    inspectItem?.let { item ->
        Dialog(
            onDismissRequest = { viewModel.setInspectItem(null) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OledBlack)
            ) {
                if (item.isVideo) {
                    VideoPlayerView(
                        mediaItem = item,
                        isActiveCard = true,
                        showTimeline = true,
                        isFullscreen = true
                    )
                } else {
                    PhotoViewer(
                        mediaItem = item,
                        enableZoom = true,
                        showMetadata = true,
                        isFullscreen = true
                    )
                }

                IconButton(
                    onClick = { viewModel.setInspectItem(null) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(14.dp)
                        .size(48.dp)
                        .background(ScrimDark, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close viewer",
                        tint = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    selectedColor: Color,
    badgeCount: Int = 0,
    badgeColor: Color = DeleteRed
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (badgeCount > 0) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = badgeColor,
                            contentColor = TextPrimary
                        ) {
                            Text(
                                text = badgeCount.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                ) {
                    Icon(imageVector = icon, contentDescription = label)
                }
            } else {
                Icon(imageVector = icon, contentDescription = label)
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = selectedColor,
            selectedTextColor = selectedColor,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary,
            indicatorColor = SurfaceElevated
        )
    )
}

@Composable
private fun PermissionRequestScreen(
    isPermanentlyDenied: Boolean,
    onActionClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .systemBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SwipeClean",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isPermanentlyDenied) {
                    "Media permission was denied. Enable photos and videos access in system settings to continue."
                } else {
                    "Review your photos and videos month by month. Swipe right to keep, left to bin, and up for later."
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TextPrimary,
                    contentColor = OledBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (isPermanentlyDenied) "Open settings" else "Allow photo and video access",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun buildRequiredPermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

private fun checkMediaPermissions(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            val img = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val vid = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val selected = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            img || vid || selected
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            val img = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val vid = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            img || vid
        }
        else -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}

private fun checkPartialMediaAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val img = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    val vid = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    val selected = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    return selected && !(img && vid)
}

private fun isMediaPermissionPermanentlyDenied(activity: Activity?): Boolean {
    if (activity == null) return false
    val corePerms = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return corePerms.all { perm ->
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
    }
}
