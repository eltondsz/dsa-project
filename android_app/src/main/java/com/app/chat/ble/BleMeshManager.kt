package com.app.chat.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.app.chat.bridge.PythonCoreBridge
import com.app.chat.model.PeerDevice
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("MissingPermission")
class BleMeshManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BleMeshManager"
        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MAGIC_CHUNK_BYTE: Byte = 0xFD.toByte()
        private const val MAX_CHUNK_PAYLOAD = 16

        @Volatile
        private var instance: BleMeshManager? = null

        fun getInstance(context: Context): BleMeshManager {
            return instance ?: synchronized(this) {
                instance ?: BleMeshManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null

    val discoveredPeersMap = ConcurrentHashMap<String, PeerDevice>()
    val activeGattClients = ConcurrentHashMap<String, BluetoothGatt>()
    val serverConnectedClients = ConcurrentHashMap<String, BluetoothDevice>()

    private val outboxQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<ByteArray>>()
    private val isWriting = ConcurrentHashMap<String, Boolean>()
    private val connectingPeers = ConcurrentHashMap<String, Boolean>()
    private val nextPacketSeq = AtomicInteger(1)
    private val chunkAssemblies = ConcurrentHashMap<String, ConcurrentHashMap<Int, ByteArray>>()
    private val lastWriteTimestamp = ConcurrentHashMap<String, Long>()
    @Volatile
    private var latestConnectedClient: BluetoothDevice? = null

    var onPeerDiscovered: ((PeerDevice) -> Unit)? = null
    var onPacketReceived: ((packet: ByteArray, senderAddress: String?) -> Unit)? = null

    fun isBluetoothEnabled(): Boolean {
        val adapter = bluetoothManager?.adapter ?: bluetoothAdapter
        return adapter?.isEnabled == true
    }

    fun getBluetoothAdapter(): BluetoothAdapter? {
        return bluetoothManager?.adapter ?: bluetoothAdapter
    }

    fun enableBluetoothDirectly(): Boolean {
        val adapter = getBluetoothAdapter() ?: return false
        return try {
            @Suppress("DEPRECATION")
            adapter.enable()
        } catch (e: Exception) {
            Log.w(TAG, "Direct bluetooth enable failed: ${e.message}")
            false
        }
    }

    fun initialize(): Boolean {
        val adapter = getBluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is not available or disabled")
            return false
        }
        advertiser = adapter.bluetoothLeAdvertiser
        scanner = adapter.bluetoothLeScanner
        startGattServer()
        return true
    }

    fun startAdvertising(deviceName: String) {
        if (advertiser == null) {
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        }
        val adv = advertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val pUuid = ParcelUuid(SERVICE_UUID)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(pUuid)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        try {
            adv.startAdvertising(settings, data, scanResponse, advertiseCallback)
            Log.i(TAG, "BLE advertising started for NetChat: $deviceName")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising: ${e.message}")
        }
    }

    fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertising failed with error: $errorCode")
        }
    }

    fun startScanning() {
        val adapter = getBluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Cannot start scan: Bluetooth is disabled")
            return
        }
        if (scanner == null) {
            scanner = adapter.bluetoothLeScanner
        }
        val scn = scanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // Scan for nearby devices and filter in callback
            scn.startScan(null, settings, scanCallback)
            Log.i(TAG, "BLE scanning started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan: ${e.message}")
        }
    }

    fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed: $errorCode")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val address = device.address ?: return
        val scanRecord = result.scanRecord

        // STRICT FILTER: Only show mobile devices running NetChat application!
        // Reject TVs, smart speakers, earbuds, smartwatches, car audio, etc.
        val pUuid = ParcelUuid(SERVICE_UUID)
        val hasServiceUuid = scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
        val hasServiceData = scanRecord?.serviceData?.containsKey(pUuid) == true
        val rawName = scanRecord?.deviceName ?: device.name ?: ""
        val hasNetChatName = rawName.contains("NetChat", ignoreCase = true)

        val isNetChatPeer = hasServiceUuid || hasServiceData || hasNetChatName
        if (!isNetChatPeer) {
            // Drop immediately - strictly filter out all non-NetChat devices
            return
        }

        // Also reject non-phone device classes if BluetoothClass is reported
        val btClass = device.bluetoothClass
        if (btClass != null) {
            val majorClass = btClass.majorDeviceClass
            if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                majorClass == BluetoothClass.Device.Major.WEARABLE ||
                majorClass == BluetoothClass.Device.Major.PERIPHERAL
            ) {
                return
            }
        }

        val cleanName = when {
            rawName.startsWith("NetChat-", ignoreCase = true) -> rawName.removePrefix("NetChat-").trim()
            rawName.startsWith("NetChat:", ignoreCase = true) -> rawName.removePrefix("NetChat:").trim()
            rawName.isNotBlank() -> rawName
            else -> "NetChat Node (${address.takeLast(5)})"
        }

        val rssi = result.rssi
        val distance = estimateDistance(rssi)
        val distanceText = String.format("~ %.1f m", distance)

        val peer = PeerDevice(
            id = address,
            name = cleanName,
            distanceText = distanceText,
            distanceMeters = distance,
            rssi = rssi,
            isOnline = true
        )

        discoveredPeersMap[address] = peer
        onPeerDiscovered?.invoke(peer)
    }

    private fun estimateDistance(rssi: Int): Float {
        val txPower = -59
        if (rssi == 0) return -1.0f
        val ratio = (txPower - rssi) / 20.0
        return Math.pow(10.0, ratio).toFloat().coerceIn(0.5f, 50.0f)
    }

    private fun startGattServer() {
        if (bluetoothManager == null) return
        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            // Add CCCD descriptor for client notification subscriptions
            val cccdDescriptor = BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            characteristic.addDescriptor(cccdDescriptor)

            service.addCharacteristic(characteristic)
            gattServer?.addService(service)
            Log.i(TAG, "GATT Server started with NetChat Mesh service & CCCD")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GATT server", e)
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (device == null) return
            val address = device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT Server: Client connected from $address")
                serverConnectedClients[address] = device
                latestConnectedClient = device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT Server: Client disconnected from $address")
                serverConnectedClients.remove(address)
                if (latestConnectedClient?.address == address) {
                    latestConnectedClient = null
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
            if (device != null) {
                serverConnectedClients[device.address] = device
                latestConnectedClient = device
            }
            value?.let { packet ->
                handleIncomingPacketBytes(packet, device?.address)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            descriptor?.value = value
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
            if (device != null) {
                serverConnectedClients[device.address] = device
                latestConnectedClient = device
            }
            Log.i(TAG, "GATT Server: Descriptor write request from ${device?.address}")
        }
    }

    fun handleIncomingPacketBytes(packet: ByteArray, senderAddress: String?) {
        if (packet.size >= 4 && packet[0] == MAGIC_CHUNK_BYTE) {
            val seq = packet[1].toInt() and 0xFF
            val chunkIdx = packet[2].toInt() and 0xFF
            val totalChunks = packet[3].toInt() and 0xFF
            val senderKey = senderAddress ?: "unknown"
            val assemblyKey = "$senderKey-$seq-$totalChunks"

            val map = chunkAssemblies.getOrPut(assemblyKey) { ConcurrentHashMap() }
            val chunkData = packet.copyOfRange(4, packet.size)
            map[chunkIdx] = chunkData

            if (map.size == totalChunks) {
                chunkAssemblies.remove(assemblyKey)
                val bos = ByteArrayOutputStream()
                for (i in 0 until totalChunks) {
                    val part = map[i]
                    if (part != null) {
                        bos.write(part)
                    }
                }
                val assembledBytes = bos.toByteArray()
                Log.i(TAG, "Assembled complete packet (${assembledBytes.size} bytes) from $senderAddress")
                onPacketReceived?.invoke(assembledBytes, senderAddress)
            }
            return
        }

        Log.i(TAG, "Received raw BLE packet (${packet.size} bytes) from $senderAddress")
        onPacketReceived?.invoke(packet, senderAddress)
    }

    fun sendPayloadToPeer(peerAddress: String, payload: ByteArray) {
        if (!BluetoothAdapter.checkBluetoothAddress(peerAddress)) {
            Log.w(TAG, "sendPayloadToPeer: invalid Bluetooth address '$peerAddress'")
            return
        }
        if (payload.size <= 20 && payload.firstOrNull() != MAGIC_CHUNK_BYTE) {
            enqueueRawPacket(peerAddress, payload)
        } else {
            val seq = (nextPacketSeq.getAndIncrement() and 0xFF).toByte()
            val totalChunks = ((payload.size + MAX_CHUNK_PAYLOAD - 1) / MAX_CHUNK_PAYLOAD)
            if (totalChunks > 255) {
                Log.e(TAG, "Payload too large to chunk: ${payload.size} bytes")
                return
            }
            for (i in 0 until totalChunks) {
                val offset = i * MAX_CHUNK_PAYLOAD
                val len = minOf(MAX_CHUNK_PAYLOAD, payload.size - offset)
                val chunk = ByteArray(4 + len)
                chunk[0] = MAGIC_CHUNK_BYTE
                chunk[1] = seq
                chunk[2] = i.toByte()
                chunk[3] = totalChunks.toByte()
                System.arraycopy(payload, offset, chunk, 4, len)
                enqueueRawPacket(peerAddress, chunk)
            }
        }
    }

    private fun enqueueRawPacket(peerAddress: String, rawPacket: ByteArray) {
        val queue = outboxQueues.getOrPut(peerAddress) { ConcurrentLinkedQueue() }
        queue.add(rawPacket)
        processOutbox(peerAddress)
    }

    private fun processOutbox(peerAddress: String) {
        val queue = outboxQueues[peerAddress] ?: return
        if (queue.isEmpty()) return

        // 1. If we have an active GATT client connection to this peer, write to it
        val existingGatt = activeGattClients[peerAddress]
        if (existingGatt != null) {
            val lastWrite = lastWriteTimestamp[peerAddress] ?: 0L
            if (isWriting[peerAddress] == true) {
                if (System.currentTimeMillis() - lastWrite > 800L) {
                    isWriting[peerAddress] = false
                } else {
                    return
                }
            }
            val nextPacket = queue.peek() ?: return
            lastWriteTimestamp[peerAddress] = System.currentTimeMillis()
            isWriting[peerAddress] = true
            if (writeCharacteristic(existingGatt, nextPacket)) {
                queue.poll()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isWriting[peerAddress] = false
                    processOutbox(peerAddress)
                }, 25)
            } else {
                isWriting[peerAddress] = false
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    processOutbox(peerAddress)
                }, 50)
            }
            return
        }

        // 2. If peer is connected to our GATT Server as a client, notify the client
        val serverClient = serverConnectedClients[peerAddress] ?: latestConnectedClient
        if (serverClient != null && gattServer != null) {
            val service = gattServer?.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
            if (characteristic != null) {
                val nextPacket = queue.poll() ?: return
                notifyClient(serverClient, characteristic, nextPacket)
                if (!queue.isEmpty()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        processOutbox(peerAddress)
                    }, 25)
                }
                return
            }
        }

        // 3. Neither client nor server connection is currently ready - connect via LE
        connectToPeer(peerAddress)
    }

    private fun connectToPeer(peerAddress: String) {
        if (!BluetoothAdapter.checkBluetoothAddress(peerAddress)) {
            Log.w(TAG, "connectToPeer: invalid Bluetooth address '$peerAddress'")
            return
        }
        if (connectingPeers[peerAddress] == true) {
            Log.d(TAG, "Already connecting to $peerAddress, skipping duplicate connectGatt")
            return
        }
        val device = bluetoothAdapter?.getRemoteDevice(peerAddress) ?: return
        connectingPeers[peerAddress] = true

        // Stop scanning to preserve radio bandwidth during connection establishment
        try {
            stopScanning()
        } catch (_: Exception) {}

        Log.i(TAG, "Connecting GATT client to peer $peerAddress with TRANSPORT_LE")
        device.connectGatt(context, false, createGattCallback(peerAddress), BluetoothDevice.TRANSPORT_LE)
    }

    private fun createGattCallback(peerAddress: String) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            connectingPeers.remove(peerAddress)
            if (newState == BluetoothProfile.STATE_CONNECTED && gatt != null && status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Connected to GATT peer $peerAddress, discovering services")
                isWriting[peerAddress] = false
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Disconnected or connection error with GATT peer $peerAddress (status: $status)")
                activeGattClients.remove(peerAddress)
                isWriting.remove(peerAddress)
                try {
                    gatt?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing GATT", e)
                }

                // If outbox still has unsent messages, schedule reconnect retry after 1s
                val queue = outboxQueues[peerAddress]
                if (queue != null && !queue.isEmpty()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        connectToPeer(peerAddress)
                    }, 1000)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                Log.i(TAG, "Services discovered for $peerAddress. Total: ${gatt.services.size}")
                val service = gatt.getService(SERVICE_UUID)
                    ?: gatt.services.find { it.uuid.toString().equals(SERVICE_UUID.toString(), ignoreCase = true) }
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                    ?: service?.characteristics?.find { it.uuid.toString().equals(CHARACTERISTIC_UUID.toString(), ignoreCase = true) }

                if (characteristic != null) {
                    activeGattClients[peerAddress] = gatt
                    isWriting[peerAddress] = false

                    // Subscribe to notifications for incoming packets
                    val subSuccess = try {
                        subscribeToNotifications(gatt)
                    } catch (e: Exception) {
                        Log.w(TAG, "Subscribe notification failed: ${e.message}")
                        false
                    }

                    if (!subSuccess) {
                        processOutbox(peerAddress)
                    }
                } else {
                    Log.w(TAG, "Required service/characteristic not found on $peerAddress. Found: ${gatt.services.map { it.uuid }}")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            Log.i(TAG, "onDescriptorWrite for $peerAddress, status: $status")
            isWriting[peerAddress] = false
            processOutbox(peerAddress)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            isWriting[peerAddress] = false
            Log.d(TAG, "Characteristic write completed for $peerAddress with status: $status")
            processOutbox(peerAddress)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingPacketBytes(value, gatt.device.address)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.value?.let { value ->
                handleIncomingPacketBytes(value, gatt?.device?.address)
            }
        }
    }

    private fun subscribeToNotifications(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID) ?: return false
        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun writeCharacteristic(gatt: BluetoothGatt, data: ByteArray): Boolean {
        val service = gatt.getService(SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID) ?: return false

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val res = gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            res == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun notifyClient(
        clientDevice: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ) {
        val server = gattServer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(clientDevice, characteristic, false, data)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(clientDevice, characteristic, false)
        }
    }

    fun broadcastPacket(payload: ByteArray) {
        val allTargetAddresses = mutableSetOf<String>()
        allTargetAddresses.addAll(discoveredPeersMap.keys)
        allTargetAddresses.addAll(activeGattClients.keys)
        allTargetAddresses.addAll(serverConnectedClients.keys)

        allTargetAddresses.forEach { addr ->
            sendPayloadToPeer(addr, payload)
        }
    }
}
