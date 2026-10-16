package com.example.nearby

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.FileManager
import com.example.model.ConnectionRequest
import com.example.model.ItemTransferStatus
import com.example.model.NearbyDevice
import com.example.model.SessionState
import com.example.model.TransferItem
import com.example.model.TransferProtocol
import com.example.model.TransferSession
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class NearbyTransferManager(
    private val context: Context,
    private val fileManager: FileManager,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        const val TAG = "NAQL_Nearby"
        const val SERVICE_ID = "com.naql.fileshare"
        // P2P_STAR allows 1-to-many topology for broadcasting and multi-device transmission
        val TRANSFER_STRATEGY: Strategy = Strategy.P2P_STAR
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    // State flows
    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()

    private val _incomingConnectionRequest = MutableStateFlow<ConnectionRequest?>(null)
    val incomingConnectionRequest: StateFlow<ConnectionRequest?> = _incomingConnectionRequest.asStateFlow()

    private val _activeSession = MutableStateFlow<TransferSession?>(null)
    val activeSession: StateFlow<TransferSession?> = _activeSession.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Multi-device endpoints connected in session
    private val connectedEndpoints = ConcurrentHashMap<String, String>() // endpointId -> deviceName

    // Internal maps
    private val incomingPayloads = ConcurrentHashMap<Long, Payload>()
    private val pendingFileMetas = ConcurrentHashMap<Long, TransferProtocol.FileMeta>()
    private val payloadProgressMap = ConcurrentHashMap<Long, Long>()

    // Speed calculation
    private var lastSpeedCalcTime = 0L
    private var lastSpeedCalcBytes = 0L

    var onTransferCompleted: ((TransferSession) -> Unit)? = null

    // Callbacks
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            val newDevice = NearbyDevice(
                endpointId = endpointId,
                name = info.endpointName,
                serviceId = info.serviceId
            )
            val current = _discoveredDevices.value.filter { it.endpointId != endpointId }.toMutableList()
            current.add(newDevice)
            _discoveredDevices.value = current
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            _discoveredDevices.value = _discoveredDevices.value.filter { it.endpointId != endpointId }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated: $endpointId (${info.endpointName}) Auth: ${info.authenticationDigits}")
            _incomingConnectionRequest.value = ConnectionRequest(
                endpointId = endpointId,
                endpointName = info.endpointName,
                authenticationDigits = info.authenticationDigits
            )

            // Update session if we are the sender
            val current = _activeSession.value
            if (current != null && (current.peerEndpointId == endpointId || current.peerEndpointId.isEmpty())) {
                _activeSession.value = current.copy(
                    peerEndpointId = endpointId,
                    peerDeviceName = info.endpointName,
                    authPin = info.authenticationDigits,
                    state = SessionState.CONNECTION_PENDING_APPROVAL
                )
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            Log.d(TAG, "Connection result for $endpointId: ${result.status.statusCode}")
            _incomingConnectionRequest.value = null

            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpoints[endpointId] = _activeSession.value?.peerDeviceName ?: "جهاز"
                val current = _activeSession.value
                if (current != null) {
                    _activeSession.value = current.copy(
                        state = SessionState.TRANSFERRING,
                        startTime = System.currentTimeMillis()
                    )
                    // If we are sender, start sending files now
                    if (!current.isIncoming) {
                        coroutineScope.launch(Dispatchers.IO) {
                            sendPayloadsForSession(endpointId, current)
                        }
                    }
                }
            } else {
                val current = _activeSession.value
                if (current != null) {
                    val statusText = when (result.status.statusCode) {
                        ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> "تم رفض الاتصال من الجهاز المقابل"
                        ConnectionsStatusCodes.STATUS_ERROR -> "خطأ في إنشاء الاتصال"
                        else -> "فشل الاتصال: ${result.status.statusMessage ?: ""}"
                    }
                    _activeSession.value = current.copy(
                        state = SessionState.FAILED,
                        errorMessage = statusText
                    )
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
            val current = _activeSession.value
            if (current != null && current.peerEndpointId == endpointId && current.state == SessionState.TRANSFERRING) {
                _activeSession.value = current.copy(
                    state = SessionState.FAILED,
                    errorMessage = "انقطع الاتصال بالجهاز الآخر"
                )
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "Payload received: ID=${payload.id}, type=${payload.type} from $endpointId")
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    if (TransferProtocol.isCancelMessage(bytes)) {
                        _activeSession.value?.let { session ->
                            _activeSession.value = session.copy(
                                state = SessionState.CANCELLED,
                                errorMessage = "تم إلغاء النقل من الجهاز المقابل"
                            )
                        }
                    } else if (TransferProtocol.isAckMessage(bytes)) {
                        // Sender receives ACK confirmation from receiver
                        Log.d(TAG, "Transfer ACK received from receiver $endpointId")
                        _activeSession.value?.let { session ->
                            if (!session.isIncoming && session.state != SessionState.COMPLETED) {
                                val allCompletedItems = session.items.map {
                                    it.copy(status = ItemTransferStatus.COMPLETED)
                                }
                                val completedSession = session.copy(
                                    items = allCompletedItems,
                                    state = SessionState.COMPLETED,
                                    endTime = System.currentTimeMillis()
                                )
                                _activeSession.value = completedSession
                                onTransferCompleted?.invoke(completedSession)
                            }
                        }
                    } else {
                        // Check if metadata
                        val meta = TransferProtocol.parseMetadataBytes(bytes)
                        if (meta != null) {
                            handleIncomingMetadata(endpointId, meta)
                        }
                    }
                }
                Payload.Type.FILE -> {
                    incomingPayloads[payload.id] = payload
                }
                Payload.Type.STREAM -> {
                    // Not used
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val bytesTransferred = update.bytesTransferred
            payloadProgressMap[payloadId] = bytesTransferred

            val session = _activeSession.value ?: return
            val isIncoming = session.isIncoming

            // Calculate overall transferred bytes and speed
            var aggregateTransferred = 0L
            val updatedItems = session.items.map { item ->
                if (item.payloadId == payloadId) {
                    val status = when (update.status) {
                        PayloadTransferUpdate.Status.SUCCESS -> {
                            if (isIncoming) {
                                // For incoming files, wire transfer finished, now processing on disk
                                if (item.status == ItemTransferStatus.COMPLETED) ItemTransferStatus.COMPLETED
                                else ItemTransferStatus.IN_PROGRESS
                            } else {
                                ItemTransferStatus.COMPLETED
                            }
                        }
                        PayloadTransferUpdate.Status.FAILURE -> ItemTransferStatus.FAILED
                        PayloadTransferUpdate.Status.CANCELED -> ItemTransferStatus.CANCELLED
                        else -> ItemTransferStatus.IN_PROGRESS
                    }
                    val transferred = if (status == ItemTransferStatus.COMPLETED) item.size else bytesTransferred
                    aggregateTransferred += transferred
                    item.copy(
                        status = status,
                        bytesTransferred = transferred
                    )
                } else {
                    val transferred = payloadProgressMap[item.payloadId] ?: (if (item.status == ItemTransferStatus.COMPLETED) item.size else item.bytesTransferred)
                    aggregateTransferred += transferred
                    item
                }
            }

            // Speed calculation
            val now = System.currentTimeMillis()
            var speed = session.transferSpeedBytesPerSec
            if (now - lastSpeedCalcTime >= 500) {
                val bytesDelta = aggregateTransferred - lastSpeedCalcBytes
                val timeDeltaSec = (now - lastSpeedCalcTime) / 1000.0
                if (timeDeltaSec > 0) {
                    speed = (bytesDelta / timeDeltaSec).toLong().coerceAtLeast(0L)
                }
                lastSpeedCalcTime = now
                lastSpeedCalcBytes = aggregateTransferred
            }

            // Check if incoming file payload completed wire transfer
            if (update.status == PayloadTransferUpdate.Status.SUCCESS && isIncoming) {
                val incomingPayload = incomingPayloads[payloadId]
                if (incomingPayload != null && incomingPayload.type == Payload.Type.FILE) {
                    coroutineScope.launch(Dispatchers.IO) {
                        processCompletedIncomingFile(payloadId, incomingPayload, endpointId)
                    }
                }
            }

            // Check if sender side completed all uploads
            val allCompletedSender = !isIncoming && updatedItems.isNotEmpty() && updatedItems.all {
                it.status == ItemTransferStatus.COMPLETED
            }

            val newState = if (allCompletedSender) SessionState.COMPLETED else session.state

            _activeSession.value = session.copy(
                items = updatedItems,
                transferredBytes = aggregateTransferred,
                transferSpeedBytesPerSec = speed,
                state = newState,
                endTime = if (allCompletedSender) System.currentTimeMillis() else session.endTime
            )

            if (allCompletedSender && session.state != SessionState.COMPLETED) {
                onTransferCompleted?.invoke(_activeSession.value!!)
            }
        }
    }

    fun startAdvertising(localDeviceName: String) {
        val options = AdvertisingOptions.Builder()
            .setStrategy(TRANSFER_STRATEGY)
            .build()
        connectionsClient.startAdvertising(
            localDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Started advertising as $localDeviceName")
            _isAdvertising.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start advertising", e)
            _isAdvertising.value = false
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
    }

    fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(TRANSFER_STRATEGY)
            .build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Started discovery")
            _isDiscovering.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start discovery", e)
            _isDiscovering.value = false
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
        _discoveredDevices.value = emptyList()
    }

    fun connectToDevice(device: NearbyDevice, localDeviceName: String, itemsToSend: List<TransferItem>) {
        stopDiscovery()
        val totalBytes = itemsToSend.sumOf { it.size }
        _activeSession.value = TransferSession(
            isIncoming = false,
            peerEndpointId = device.endpointId,
            peerDeviceName = device.name,
            items = itemsToSend,
            totalBytes = totalBytes,
            state = SessionState.CONNECTING
        )

        connectionsClient.requestConnection(
            localDeviceName,
            device.endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Connection requested to ${device.name}")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Connection request failed", e)
            _activeSession.value = _activeSession.value?.copy(
                state = SessionState.FAILED,
                errorMessage = "فشل طلب الاتصال: ${e.message}"
            )
        }
    }

    fun connectToMultipleDevices(devices: List<NearbyDevice>, localDeviceName: String, itemsToSend: List<TransferItem>) {
        stopDiscovery()
        if (devices.isEmpty()) return
        val totalBytes = itemsToSend.sumOf { it.size }
        val namesSummary = devices.joinToString(", ") { it.name }

        _activeSession.value = TransferSession(
            isIncoming = false,
            peerEndpointId = devices.first().endpointId,
            peerDeviceName = namesSummary,
            items = itemsToSend,
            totalBytes = totalBytes,
            state = SessionState.CONNECTING
        )

        for (device in devices) {
            connectionsClient.requestConnection(
                localDeviceName,
                device.endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.d(TAG, "Multi-connection requested to ${device.name}")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Connection request failed to ${device.name}", e)
            }
        }
    }

    fun acceptIncomingConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                Log.d(TAG, "Accepted connection from $endpointId")
                val req = _incomingConnectionRequest.value
                _activeSession.value = TransferSession(
                    isIncoming = true,
                    peerEndpointId = endpointId,
                    peerDeviceName = req?.endpointName ?: "جهاز قريب",
                    state = SessionState.TRANSFERRING,
                    authPin = req?.authenticationDigits ?: "",
                    startTime = System.currentTimeMillis()
                )
                _incomingConnectionRequest.value = null
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to accept connection", e)
                _incomingConnectionRequest.value = null
            }
    }

    fun rejectIncomingConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
        _incomingConnectionRequest.value = null
    }

    private suspend fun sendPayloadsForSession(endpointId: String, session: TransferSession) {
        try {
            val fileMetas = mutableListOf<TransferProtocol.FileMeta>()
            val payloadList = mutableListOf<Pair<TransferItem, Payload>>()

            for (item in session.items) {
                // Verify file access
                if (!fileManager.isFileAccessible(item)) {
                    Log.w(TAG, "File not accessible before send: ${item.name}")
                }

                val payload: Payload = when {
                    item.uri != null -> {
                        val pfd = context.contentResolver.openFileDescriptor(item.uri, "r")
                            ?: throw IllegalStateException("Cannot open uri: ${item.uri}")
                        Payload.fromFile(pfd)
                    }
                    item.localPath != null -> {
                        val file = File(item.localPath)
                        Payload.fromFile(file)
                    }
                    else -> throw IllegalStateException("No uri or path for item: ${item.name}")
                }

                val pId = payload.id
                val sha = if (item.sha256Checksum.isNotEmpty()) item.sha256Checksum else {
                    try {
                        if (item.uri != null) fileManager.calculateSha256(item.uri)
                        else if (item.localPath != null) fileManager.calculateSha256(File(item.localPath))
                        else ""
                    } catch (e: Exception) { "" }
                }

                fileMetas.add(
                    TransferProtocol.FileMeta(
                        payloadId = pId,
                        name = item.name,
                        size = item.size,
                        mimeType = item.mimeType,
                        sha256 = sha
                    )
                )
                payloadList.add(item.copy(payloadId = pId, sha256Checksum = sha) to payload)
            }

            // 1. Send metadata payload
            val metaBytes = TransferProtocol.createMetadataBytes(
                sessionId = session.sessionId,
                senderName = session.peerDeviceName,
                files = fileMetas
            )
            val metaPayload = Payload.fromBytes(metaBytes)
            connectionsClient.sendPayload(endpointId, metaPayload)

            // Update session items with assigned payload IDs
            val mappedItems = payloadList.map { it.first }
            _activeSession.value = _activeSession.value?.copy(items = mappedItems)

            // 2. Send each file payload
            for ((_, filePayload) in payloadList) {
                connectionsClient.sendPayload(endpointId, filePayload)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending payloads", e)
            _activeSession.value = _activeSession.value?.copy(
                state = SessionState.FAILED,
                errorMessage = "خطأ أثناء إرسال الملفات: ${e.message}"
            )
        }
    }

    private fun handleIncomingMetadata(endpointId: String, meta: TransferProtocol.TransferMetadata) {
        val totalBytes = meta.files.sumOf { it.size }
        val items = meta.files.map { f ->
            pendingFileMetas[f.payloadId] = f
            TransferItem(
                id = f.payloadId.toString(),
                name = f.name,
                size = f.size,
                mimeType = f.mimeType,
                category = fileManager.determineCategory(f.name, f.mimeType),
                sha256Checksum = f.sha256,
                payloadId = f.payloadId,
                status = ItemTransferStatus.IN_PROGRESS
            )
        }

        _activeSession.value = _activeSession.value?.copy(
            sessionId = meta.sessionId,
            peerDeviceName = meta.senderName,
            items = items,
            totalBytes = totalBytes,
            state = SessionState.TRANSFERRING
        )
    }

    private suspend fun processCompletedIncomingFile(payloadId: Long, payload: Payload, peerEndpointId: String) {
        val meta = pendingFileMetas[payloadId] ?: return
        val destDir = fileManager.getReceivedFilesDir()
        val tempFile = File(destDir, ".tmp_${System.currentTimeMillis()}_${payloadId}.part")
        val finalFile = fileManager.getUniqueFile(destDir, meta.name)

        try {
            var writeSuccess = false
            val payloadFile = payload.asFile()

            // 1. Try Java File if available and non-empty
            val javaFile = payloadFile?.asJavaFile()
            if (javaFile != null && javaFile.exists() && javaFile.length() > 0) {
                try {
                    FileInputStream(javaFile).use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output, bufferSize = 65536)
                        }
                    }
                    writeSuccess = tempFile.exists() && tempFile.length() > 0
                    try { javaFile.delete() } catch (_: Exception) {}
                } catch (e: Exception) {
                    Log.w(TAG, "Error copying javaFile payload", e)
                }
            }

            // 2. Try ParcelFileDescriptor
            if (!writeSuccess) {
                val pfd = payloadFile?.asParcelFileDescriptor()
                if (pfd != null) {
                    try {
                        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output, bufferSize = 65536)
                            }
                        }
                        writeSuccess = tempFile.exists() && tempFile.length() > 0
                    } catch (e: Exception) {
                        Log.w(TAG, "Error copying pfd payload", e)
                    }
                }
            }

            // 3. Try Uri
            if (!writeSuccess) {
                val uri = payloadFile?.asUri()
                if (uri != null) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output, bufferSize = 65536)
                            }
                        }
                        writeSuccess = tempFile.exists() && tempFile.length() > 0
                    } catch (e: Exception) {
                        Log.w(TAG, "Error copying uri payload", e)
                    }
                }
            }

            if (!writeSuccess || !tempFile.exists() || tempFile.length() == 0L) {
                Log.e(TAG, "Failed to read incoming file payload $payloadId from any source")
                try { tempFile.delete() } catch (_: Exception) {}
                markIncomingItemFinished(payloadId, null, false, "فشل حفظ الملف من المصدر", peerEndpointId)
                return
            }

            // 4. Verify integrity (SHA-256 and Size)
            val actualSha = fileManager.calculateSha256(tempFile)
            val verifiedSha = meta.sha256.isEmpty() || meta.sha256.equals(actualSha, ignoreCase = true)
            val verifiedSize = meta.size <= 0 || tempFile.length() == meta.size

            if (!verifiedSha || !verifiedSize) {
                Log.e(TAG, "Integrity check failed for ${meta.name}: expectedSha=${meta.sha256}, actualSha=$actualSha, expectedSize=${meta.size}, actualSize=${tempFile.length()}")
                try { tempFile.delete() } catch (_: Exception) {}
                markIncomingItemFinished(payloadId, null, false, "فشل التحقق من سلامة الملف المنقول", peerEndpointId)
                return
            }

            // 5. Finalize file name & placement
            if (!tempFile.renameTo(finalFile)) {
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(finalFile).use { output ->
                        input.copyTo(output, bufferSize = 65536)
                    }
                }
                try { tempFile.delete() } catch (_: Exception) {}
            }

            // 6. Notify MediaScanner so Android indexes it immediately in MediaStore / Downloads / Gallery
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalFile.absolutePath),
                    arrayOf(meta.mimeType.ifEmpty { null })
                ) { path, scannedUri ->
                    Log.d(TAG, "MediaScanner registered file: $path -> $scannedUri")
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaScanner notification error", e)
            }

            // 7. Mark item COMPLETED and check if entire session finished
            markIncomingItemFinished(payloadId, finalFile, true, null, peerEndpointId)

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error processing incoming file payload $payloadId", e)
            try { tempFile.delete() } catch (_: Exception) {}
            markIncomingItemFinished(payloadId, null, false, "خطأ أثناء حفظ الملف: ${e.message}", peerEndpointId)
        }
    }

    private fun markIncomingItemFinished(
        payloadId: Long,
        finalFile: File?,
        verified: Boolean,
        errorMessage: String?,
        peerEndpointId: String = ""
    ) {
        val current = _activeSession.value ?: return
        val updated = current.items.map { item ->
            if (item.payloadId == payloadId) {
                item.copy(
                    localPath = finalFile?.absolutePath,
                    uri = finalFile?.let { Uri.fromFile(it) },
                    verifiedChecksum = verified,
                    status = if (finalFile != null && verified) ItemTransferStatus.COMPLETED else ItemTransferStatus.FAILED
                )
            } else item
        }

        val allCompleted = updated.isNotEmpty() && updated.all {
            it.status == ItemTransferStatus.COMPLETED || it.status == ItemTransferStatus.FAILED || it.status == ItemTransferStatus.CANCELLED
        }

        val anySuccess = updated.any { it.status == ItemTransferStatus.COMPLETED }

        val newSessionState = if (allCompleted) {
            if (anySuccess) SessionState.COMPLETED else SessionState.FAILED
        } else {
            current.state
        }

        val completedSession = current.copy(
            items = updated,
            state = newSessionState,
            endTime = if (allCompleted) System.currentTimeMillis() else 0L
        )
        _activeSession.value = completedSession

        if (allCompleted) {
            // Send ACK to sender so sender also marks transfer completed
            val endpoint = if (peerEndpointId.isNotEmpty()) peerEndpointId else current.peerEndpointId
            if (endpoint.isNotEmpty()) {
                try {
                    val ackBytes = TransferProtocol.createAckMessageBytes(current.sessionId)
                    connectionsClient.sendPayload(endpoint, Payload.fromBytes(ackBytes))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send ACK to $endpoint", e)
                }
            }
            onTransferCompleted?.invoke(completedSession)
        }
    }

    fun cancelTransfer() {
        val session = _activeSession.value
        if (session != null && session.peerEndpointId.isNotEmpty()) {
            val cancelBytes = TransferProtocol.createCancelMessageBytes(session.sessionId)
            connectionsClient.sendPayload(session.peerEndpointId, Payload.fromBytes(cancelBytes))
            connectionsClient.disconnectFromEndpoint(session.peerEndpointId)
            _activeSession.value = session.copy(
                state = SessionState.CANCELLED,
                errorMessage = "تم إلغاء عملية النقل"
            )
        }
    }

    fun resetSession() {
        _activeSession.value?.let { session ->
            if (session.peerEndpointId.isNotEmpty()) {
                connectionsClient.disconnectFromEndpoint(session.peerEndpointId)
            }
        }
        _activeSession.value = null
        connectedEndpoints.clear()
        payloadProgressMap.clear()
        incomingPayloads.clear()
        pendingFileMetas.clear()
    }

    fun cleanup() {
        stopAdvertising()
        stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        _activeSession.value = null
    }
}
