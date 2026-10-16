package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.data.FileManager
import com.example.data.ReceivedFileEntity
import com.example.data.TransferHistoryEntity
import com.example.model.ConnectionRequest
import com.example.model.FileCategory
import com.example.model.FileSortOption
import com.example.model.FileViewMode
import com.example.model.ItemTransferStatus
import com.example.model.NearbyDevice
import com.example.model.SessionState
import com.example.model.TransferItem
import com.example.model.TransferSession
import com.example.nearby.NearbyTransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class Screen {
    SPLASH,
    HOME,
    FILE_PICKER,
    DISCOVERY,
    RECEIVE,
    TRANSFER_PROGRESS,
    TRANSFER_COMPLETE,
    RECEIVED_FILES,
    HISTORY,
    SETTINGS
}

class NaqlViewModel(application: Application) : AndroidViewModel(application) {

    val fileManager = FileManager(application)
    val preferences = AppPreferences(application)

    private val database: AppDatabase = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "naql_database.db"
    ).fallbackToDestructiveMigration().build()

    private val transferDao = database.transferDao()

    val nearbyManager = NearbyTransferManager(
        context = application,
        fileManager = fileManager,
        coroutineScope = viewModelScope
    )

    // Navigation State
    private val _currentScreen = MutableStateFlow(Screen.SPLASH)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // File Picker States
    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _availableMediaItems = MutableStateFlow<List<TransferItem>>(emptyList())
    val availableMediaItems: StateFlow<List<TransferItem>> = _availableMediaItems.asStateFlow()

    private val _isLoadingMedia = MutableStateFlow(false)
    val isLoadingMedia: StateFlow<Boolean> = _isLoadingMedia.asStateFlow()

    private val _selectedItems = MutableStateFlow<Map<String, TransferItem>>(emptyMap())
    val selectedItems: StateFlow<Map<String, TransferItem>> = _selectedItems.asStateFlow()

    // View Mode & Sorting & Search
    private val _fileViewMode = MutableStateFlow(preferences.fileViewMode)
    val fileViewMode: StateFlow<FileViewMode> = _fileViewMode.asStateFlow()

    private val _fileSortOption = MutableStateFlow(preferences.fileSortOption)
    val fileSortOption: StateFlow<FileSortOption> = _fileSortOption.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Preview Item
    private val _previewItem = MutableStateFlow<TransferItem?>(null)
    val previewItem: StateFlow<TransferItem?> = _previewItem.asStateFlow()

    // Filtered & Sorted Display Items Flow
    val displayedMediaItems: StateFlow<List<TransferItem>> = combine(
        _availableMediaItems,
        _searchQuery,
        _fileSortOption
    ) { items, query, sortOption ->
        val filtered = if (query.isBlank()) {
            items
        } else {
            items.filter { it.name.contains(query, ignoreCase = true) }
        }

        when (sortOption) {
            FileSortOption.DATE_DESC -> filtered.sortedByDescending { it.dateModified }
            FileSortOption.DATE_ASC -> filtered.sortedBy { it.dateModified }
            FileSortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            FileSortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            FileSortOption.SIZE_DESC -> filtered.sortedByDescending { it.size }
            FileSortOption.SIZE_ASC -> filtered.sortedBy { it.size }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Multi-Device Selection in Discovery
    private val _selectedDeviceEndpoints = MutableStateFlow<Set<String>>(emptySet())
    val selectedDeviceEndpoints: StateFlow<Set<String>> = _selectedDeviceEndpoints.asStateFlow()

    // Database Flows
    val transferHistory: StateFlow<List<TransferHistoryEntity>> = transferDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val receivedFilesList: StateFlow<List<ReceivedFileEntity>> = transferDao.getAllReceivedFiles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Delegated Nearby Flows
    val discoveredDevices: StateFlow<List<NearbyDevice>> = nearbyManager.discoveredDevices
    val incomingConnectionRequest: StateFlow<ConnectionRequest?> = nearbyManager.incomingConnectionRequest
    val activeSession: StateFlow<TransferSession?> = nearbyManager.activeSession
    val isAdvertising: StateFlow<Boolean> = nearbyManager.isAdvertising
    val isDiscovering: StateFlow<Boolean> = nearbyManager.isDiscovering

    // Device Settings & Storage
    private val _deviceName = MutableStateFlow(preferences.deviceName)
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _availableStorageBytes = MutableStateFlow(fileManager.getAvailableStorageBytes())
    val availableStorageBytes: StateFlow<Long> = _availableStorageBytes.asStateFlow()

    private val _totalStorageBytes = MutableStateFlow(fileManager.getTotalStorageBytes())
    val totalStorageBytes: StateFlow<Long> = _totalStorageBytes.asStateFlow()

    init {
        nearbyManager.onTransferCompleted = { session ->
            handleSessionCompleted(session)
        }
        loadMediaForCategory(FileCategory.ALL)
        refreshStorageInfo()
    }

    fun navigateTo(screen: Screen) {
        if (screen == Screen.DISCOVERY) {
            startDiscovery()
        } else if (_currentScreen.value == Screen.DISCOVERY && screen != Screen.TRANSFER_PROGRESS) {
            nearbyManager.stopDiscovery()
            _selectedDeviceEndpoints.value = emptySet()
        }

        if (screen == Screen.RECEIVE) {
            startReceiving()
        } else if (_currentScreen.value == Screen.RECEIVE && screen != Screen.TRANSFER_PROGRESS) {
            nearbyManager.stopAdvertising()
        }

        _currentScreen.value = screen
    }

    fun setViewMode(mode: FileViewMode) {
        _fileViewMode.value = mode
        preferences.fileViewMode = mode
    }

    fun setSortOption(option: FileSortOption) {
        _fileSortOption.value = option
        preferences.fileSortOption = option
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openPreview(item: TransferItem) {
        _previewItem.value = item
    }

    fun closePreview() {
        _previewItem.value = null
    }

    fun setCategory(category: FileCategory) {
        _selectedCategory.value = category
        loadMediaForCategory(category)
    }

    fun loadMediaForCategory(category: FileCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMedia.value = true
            val items = when (category) {
                FileCategory.ALL -> fileManager.getAllAvailableFiles()
                FileCategory.IMAGE -> fileManager.getMediaImages()
                FileCategory.VIDEO -> fileManager.getMediaVideos()
                FileCategory.AUDIO -> fileManager.getMediaAudio()
                FileCategory.DOCUMENT -> fileManager.getMediaDocuments()
                FileCategory.APP -> fileManager.getInstalledApps()
                FileCategory.OTHER -> fileManager.getOtherFiles()
            }
            _availableMediaItems.value = items
            _isLoadingMedia.value = false
        }
    }

    fun toggleItemSelection(item: TransferItem) {
        val current = _selectedItems.value.toMutableMap()
        if (current.containsKey(item.id)) {
            current.remove(item.id)
        } else {
            current[item.id] = item
        }
        _selectedItems.value = current
    }

    fun selectAll(items: List<TransferItem>) {
        val current = _selectedItems.value.toMutableMap()
        for (item in items) {
            current[item.id] = item
        }
        _selectedItems.value = current
    }

    fun deselectAll() {
        _selectedItems.value = emptyMap()
    }

    fun clearSelection() {
        _selectedItems.value = emptyMap()
    }

    fun addCustomPickedUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentMap = _selectedItems.value.toMutableMap()
            val newItems = mutableListOf<TransferItem>()
            for (uri in uris) {
                val item = fileManager.getTransferItemFromUri(uri)
                if (item != null) {
                    currentMap[item.id] = item
                    newItems.add(item)
                }
            }
            _selectedItems.value = currentMap
            if (newItems.isNotEmpty()) {
                val existingAvailable = _availableMediaItems.value.toMutableList()
                for (item in newItems.reversed()) {
                    if (existingAvailable.none { it.id == item.id || (it.uri != null && it.uri == item.uri) }) {
                        existingAvailable.add(0, item)
                    }
                }
                _availableMediaItems.value = existingAvailable
            }
        }
    }

    fun handleInboundShare(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentMap = _selectedItems.value.toMutableMap()
            val newItems = mutableListOf<TransferItem>()
            for (uri in uris) {
                val item = fileManager.getTransferItemFromUri(uri)
                if (item != null) {
                    currentMap[item.id] = item
                    newItems.add(item)
                }
            }
            _selectedItems.value = currentMap
            if (newItems.isNotEmpty()) {
                val existingAvailable = _availableMediaItems.value.toMutableList()
                for (item in newItems.reversed()) {
                    if (existingAvailable.none { it.id == item.id || (it.uri != null && it.uri == item.uri) }) {
                        existingAvailable.add(0, item)
                    }
                }
                _availableMediaItems.value = existingAvailable
            }
            _currentScreen.value = Screen.FILE_PICKER
        }
    }

    fun startDiscovery() {
        nearbyManager.startDiscovery()
        _selectedDeviceEndpoints.value = emptySet()
    }

    fun toggleDeviceEndpointSelection(endpointId: String) {
        val current = _selectedDeviceEndpoints.value.toMutableSet()
        if (current.contains(endpointId)) {
            current.remove(endpointId)
        } else {
            current.add(endpointId)
        }
        _selectedDeviceEndpoints.value = current
    }

    fun sendToDevice(device: NearbyDevice) {
        val itemsToSend = _selectedItems.value.values.toList()
        if (itemsToSend.isEmpty()) return

        nearbyManager.connectToDevice(
            device = device,
            localDeviceName = _deviceName.value,
            itemsToSend = itemsToSend
        )
        _currentScreen.value = Screen.TRANSFER_PROGRESS
    }

    fun sendToSelectedDevices(devices: List<NearbyDevice>) {
        val itemsToSend = _selectedItems.value.values.toList()
        if (itemsToSend.isEmpty() || devices.isEmpty()) return

        if (devices.size == 1) {
            sendToDevice(devices.first())
        } else {
            nearbyManager.connectToMultipleDevices(
                devices = devices,
                localDeviceName = _deviceName.value,
                itemsToSend = itemsToSend
            )
            _currentScreen.value = Screen.TRANSFER_PROGRESS
        }
    }

    fun startReceiving() {
        nearbyManager.startAdvertising(_deviceName.value)
    }

    fun acceptIncomingConnection() {
        val req = incomingConnectionRequest.value ?: return
        nearbyManager.acceptIncomingConnection(req.endpointId)
        _currentScreen.value = Screen.TRANSFER_PROGRESS
    }

    fun rejectIncomingConnection() {
        val req = incomingConnectionRequest.value ?: return
        nearbyManager.rejectIncomingConnection(req.endpointId)
    }

    fun cancelTransfer() {
        nearbyManager.cancelTransfer()
    }

    fun dismissTransferComplete() {
        nearbyManager.resetSession()
        clearSelection()
        _selectedDeviceEndpoints.value = emptySet()
        _currentScreen.value = Screen.HOME
    }

    private fun handleSessionCompleted(session: TransferSession) {
        viewModelScope.launch(Dispatchers.IO) {
            val completedItems = session.items.filter { it.status == ItemTransferStatus.COMPLETED }
            val summary = completedItems.joinToString(", ") { it.name }

            val historyEntity = TransferHistoryEntity(
                id = UUID.randomUUID().toString(),
                sessionId = session.sessionId,
                peerName = session.peerDeviceName,
                isIncoming = session.isIncoming,
                filesCount = completedItems.size,
                totalBytes = completedItems.sumOf { it.size },
                status = if (session.state == SessionState.COMPLETED) "نجاح" else "مكتمل جزئياً",
                timestamp = System.currentTimeMillis(),
                filesSummary = summary
            )
            transferDao.insertHistory(historyEntity)

            if (session.isIncoming) {
                for (item in completedItems) {
                    val path = item.localPath ?: continue
                    val recEntity = ReceivedFileEntity(
                        id = UUID.randomUUID().toString(),
                        name = item.name,
                        path = path,
                        size = item.size,
                        mimeType = item.mimeType,
                        sha256 = item.sha256Checksum,
                        receivedFrom = session.peerDeviceName,
                        receivedAt = System.currentTimeMillis()
                    )
                    transferDao.insertReceivedFile(recEntity)
                }
                // Refresh categories so new files immediately show in All, Images, Videos, Documents, etc.
                loadMediaForCategory(_selectedCategory.value)
            }
            refreshStorageInfo()
            _currentScreen.value = Screen.TRANSFER_COMPLETE
        }
    }

    fun openReceivedFile(path: String) {
        val file = File(path)
        if (file.exists()) {
            fileManager.openFile(file)
        }
    }

    fun shareReceivedFile(path: String) {
        val file = File(path)
        if (file.exists()) {
            fileManager.shareFile(file)
        }
    }

    fun deleteReceivedFile(entity: ReceivedFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(entity.path)
            if (file.exists()) {
                file.delete()
            }
            transferDao.deleteReceivedFile(entity.id)
            refreshStorageInfo()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            transferDao.clearHistory()
        }
    }

    fun updateDeviceName(newName: String) {
        if (newName.isNotBlank()) {
            _deviceName.value = newName.trim()
            preferences.deviceName = newName.trim()
        }
    }

    fun refreshStorageInfo() {
        _availableStorageBytes.value = fileManager.getAvailableStorageBytes()
        _totalStorageBytes.value = fileManager.getTotalStorageBytes()
    }

    fun checkMissingPermissions(context: Context): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(android.Manifest.permission.BLUETOOTH)
            permissions.add(android.Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)

        return permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getStoragePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun previewOrOpenFile(item: TransferItem) {
        fileManager.previewOrOpenFile(item)
    }

    override fun onCleared() {
        super.onCleared()
        nearbyManager.cleanup()
    }
}
