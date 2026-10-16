package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SessionState
import com.example.ui.common.ConnectionRequestDialog
import com.example.ui.screens.DiscoveryScreen
import com.example.ui.screens.FilePickerScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.ReceivedFilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TransferCompleteScreen
import com.example.ui.screens.TransferProgressScreen
import com.example.ui.theme.NaqlTheme
import com.example.viewmodel.NaqlViewModel
import com.example.viewmodel.Screen

class MainActivity : ComponentActivity() {

    private val viewModel: NaqlViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            val imageLoader = coil.ImageLoader.Builder(this)
                .components {
                    add(coil.decode.VideoFrameDecoder.Factory())
                }
                .crossfade(true)
                .build()
            coil.Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            // Fallback
        }

        handleIncomingShareIntent(intent)

        setContent {
            NaqlTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        NaqlApp(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val collectedUris = mutableListOf<Uri>()

        // 1. EXTRA_STREAM for single item
        if (action == Intent.ACTION_SEND) {
            val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            if (streamUri != null) {
                collectedUris.add(streamUri)
            }
        }

        // 2. EXTRA_STREAM for multiple items
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            if (!streamUris.isNullOrEmpty()) {
                collectedUris.addAll(streamUris)
            }
        }

        // 3. ClipData (used by modern galleries, Google Photos, Files, WhatsApp)
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val itemUri = clipData.getItemAt(i)?.uri
                if (itemUri != null && !collectedUris.contains(itemUri)) {
                    collectedUris.add(itemUri)
                }
            }
        }

        // 4. Fallback: intent.data
        val dataUri = intent.data
        if (dataUri != null && !collectedUris.contains(dataUri)) {
            collectedUris.add(dataUri)
        }

        if (collectedUris.isNotEmpty()) {
            viewModel.handleInboundShare(collectedUris)
        }
    }
}

@Composable
fun NaqlApp(viewModel: NaqlViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val incomingRequest by viewModel.incomingConnectionRequest.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val availableStorage by viewModel.availableStorageBytes.collectAsStateWithLifecycle()
    val totalStorage by viewModel.totalStorageBytes.collectAsStateWithLifecycle()
    val historyList by viewModel.transferHistory.collectAsStateWithLifecycle()
    val receivedFiles by viewModel.receivedFilesList.collectAsStateWithLifecycle()

    // Permissions Request Launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadMediaForCategory(viewModel.selectedCategory.value)
    }

    LaunchedEffect(Unit) {
        val missing = viewModel.checkMissingPermissions(context)
        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    // Global Incoming Connection Request Dialog
    if (incomingRequest != null) {
        ConnectionRequestDialog(
            request = incomingRequest!!,
            onAccept = { viewModel.acceptIncomingConnection() },
            onReject = { viewModel.rejectIncomingConnection() }
        )
    }

    // Back Handler Navigation
    if (currentScreen != Screen.HOME && currentScreen != Screen.SPLASH) {
        BackHandler {
            when (currentScreen) {
                Screen.DISCOVERY -> viewModel.navigateTo(Screen.FILE_PICKER)
                Screen.TRANSFER_PROGRESS -> viewModel.cancelTransfer()
                Screen.TRANSFER_COMPLETE -> viewModel.dismissTransferComplete()
                else -> viewModel.navigateTo(Screen.HOME)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            Screen.SPLASH -> {
                SplashScreen(
                    onSplashFinished = {
                        if (viewModel.currentScreen.value == Screen.SPLASH) {
                            if (viewModel.selectedItems.value.isNotEmpty()) {
                                viewModel.navigateTo(Screen.FILE_PICKER)
                            } else {
                                viewModel.navigateTo(Screen.HOME)
                            }
                        }
                    }
                )
            }

            Screen.HOME -> {
                HomeScreen(
                    deviceName = deviceName,
                    availableStorage = availableStorage,
                    totalStorage = totalStorage,
                    recentHistory = historyList,
                    receivedFiles = receivedFiles,
                    onSendClick = { viewModel.navigateTo(Screen.FILE_PICKER) },
                    onReceiveClick = { viewModel.navigateTo(Screen.RECEIVE) },
                    onReceivedFilesClick = { viewModel.navigateTo(Screen.RECEIVED_FILES) },
                    onHistoryClick = { viewModel.navigateTo(Screen.HISTORY) },
                    onSettingsClick = { viewModel.navigateTo(Screen.SETTINGS) }
                )
            }

            Screen.FILE_PICKER -> {
                val category by viewModel.selectedCategory.collectAsStateWithLifecycle()
                val displayedItems by viewModel.displayedMediaItems.collectAsStateWithLifecycle()
                val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
                val isLoading by viewModel.isLoadingMedia.collectAsStateWithLifecycle()
                val viewMode by viewModel.fileViewMode.collectAsStateWithLifecycle()
                val sortOption by viewModel.fileSortOption.collectAsStateWithLifecycle()
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val previewItem by viewModel.previewItem.collectAsStateWithLifecycle()

                val hasStoragePermission = viewModel.hasStoragePermission(context)

                FilePickerScreen(
                    currentCategory = category,
                    availableItems = displayedItems,
                    selectedItems = selectedItems,
                    isLoading = isLoading,
                    viewMode = viewMode,
                    sortOption = sortOption,
                    searchQuery = searchQuery,
                    previewItem = previewItem,
                    hasStoragePermission = hasStoragePermission,
                    onRequestStoragePermission = {
                        val missing = viewModel.getStoragePermissions()
                        permissionsLauncher.launch(missing.toTypedArray())
                    },
                    onCategorySelected = { viewModel.setCategory(it) },
                    onViewModeChanged = { viewModel.setViewMode(it) },
                    onSortOptionChanged = { viewModel.setSortOption(it) },
                    onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                    onOpenPreview = { viewModel.openPreview(it) },
                    onClosePreview = { viewModel.closePreview() },
                    onOpenFile = { viewModel.previewOrOpenFile(it) },
                    onItemToggle = { viewModel.toggleItemSelection(it) },
                    onSelectAll = { viewModel.selectAll(it) },
                    onDeselectAll = { viewModel.deselectAll() },
                    onRefresh = { viewModel.loadMediaForCategory(category) },
                    onCustomUrisSelected = { viewModel.addCustomPickedUris(it) },
                    onNextClick = { viewModel.navigateTo(Screen.DISCOVERY) },
                    onBackClick = { viewModel.navigateTo(Screen.HOME) }
                )
            }

            Screen.DISCOVERY -> {
                val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
                val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
                val selectedEndpoints by viewModel.selectedDeviceEndpoints.collectAsStateWithLifecycle()
                val totalBytes = selectedItems.values.sumOf { it.size }

                DiscoveryScreen(
                    discoveredDevices = discoveredDevices,
                    selectedItemsCount = selectedItems.size,
                    totalBytes = totalBytes,
                    selectedEndpoints = selectedEndpoints,
                    onToggleDeviceSelection = { viewModel.toggleDeviceEndpointSelection(it.endpointId) },
                    onDeviceSelected = { device -> viewModel.sendToDevice(device) },
                    onSendToSelectedDevices = { devices -> viewModel.sendToSelectedDevices(devices) },
                    onBackClick = { viewModel.navigateTo(Screen.FILE_PICKER) }
                )
            }

            Screen.RECEIVE -> {
                ReceiveScreen(
                    deviceName = deviceName,
                    onStopReceivingClick = { viewModel.navigateTo(Screen.HOME) }
                )
            }

            Screen.TRANSFER_PROGRESS -> {
                TransferProgressScreen(
                    session = activeSession,
                    onCancelClick = {
                        viewModel.cancelTransfer()
                        viewModel.navigateTo(Screen.HOME)
                    }
                )
            }

            Screen.TRANSFER_COMPLETE -> {
                TransferCompleteScreen(
                    session = activeSession,
                    onViewReceivedFilesClick = { viewModel.navigateTo(Screen.RECEIVED_FILES) },
                    onBackHomeClick = { viewModel.dismissTransferComplete() }
                )
            }

            Screen.RECEIVED_FILES -> {
                ReceivedFilesScreen(
                    files = receivedFiles,
                    fileManager = viewModel.fileManager,
                    onOpenFile = { path -> viewModel.openReceivedFile(path) },
                    onShareFile = { path -> viewModel.shareReceivedFile(path) },
                    onDeleteFile = { entity -> viewModel.deleteReceivedFile(entity) },
                    onBackClick = { viewModel.navigateTo(Screen.HOME) }
                )
            }

            Screen.HISTORY -> {
                HistoryScreen(
                    historyList = historyList,
                    onClearHistoryClick = { viewModel.clearAllHistory() },
                    onBackClick = { viewModel.navigateTo(Screen.HOME) }
                )
            }

            Screen.SETTINGS -> {
                val missingPermissions = viewModel.checkMissingPermissions(context)
                val storagePath = viewModel.fileManager.getReceivedFilesDir().absolutePath

                SettingsScreen(
                    currentDeviceName = deviceName,
                    availableStorage = availableStorage,
                    totalStorage = totalStorage,
                    storagePath = storagePath,
                    missingPermissions = missingPermissions,
                    onSaveDeviceName = { viewModel.updateDeviceName(it) },
                    onRequestPermissions = {
                        val missing = viewModel.checkMissingPermissions(context)
                        if (missing.isNotEmpty()) {
                            permissionsLauncher.launch(missing.toTypedArray())
                        }
                    },
                    onBackClick = { viewModel.navigateTo(Screen.HOME) }
                )
            }
        }
    }
}
