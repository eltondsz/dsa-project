package com.app.chat.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import com.app.chat.engine.MeshEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages dual-role Bluetooth Low Energy operations (Central + Peripheral)
 * to form an ad-hoc BitChat mesh network.
 */
@SuppressLint("MissingPermission")
class BleMeshManager(
    private val context: Context,
    val meshEngine: MeshEngine,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "BleMeshManager"

        // BitChat Protocol UUIDs
        val SERVICE_UUID: UUID = UUID.fromString("F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C")
        val CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        @Volatile
        private var instance: BleMeshManager? = null

        fun getInstance(context: Context, meshEngine: MeshEngine, scope: CoroutineScope): BleMeshManager {
            return instance ?: synchronized(this) {
                instance ?: BleMeshManager(context.applicationContext, meshEngine, scope).also { instance = it }
            }
        }

        fun getExistingInstance(): BleMeshManager? = instance
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    // Peripheral role state
    private var gattServer: BluetoothGattServer? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private val subscribedCentrals = ConcurrentHashMap<String, BluetoothDevice>()

    // Central role state
    private var scanner: BluetoothLeScanner? = null
    private val connectedPeripherals = ConcurrentHashMap<String, BluetoothGatt>()
    private val peripheralCharacteristics = ConcurrentHashMap<String, BluetoothGattCharacteristic>()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isMeshRunning = MutableStateFlow(false)
    val isMeshRunning: StateFlow<Boolean> = _isMeshRunning.asStateFlow()

    init {
        // Wire mesh engine outbound packet delivery
        meshEngine.onSendPacket = { bytes, excludeLinkId ->
            sendPacketToAllLinks(bytes, excludeLinkId)
        }
    }

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    fun enableBluetoothDirectly(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return try {
            @Suppress("DEPRECATION")
            adapter.enable()
        } catch (e: Exception) {
            Log.w(TAG, "Direct bluetooth enable failed: ${e.message}")
            false
        }
    }

    fun startMesh() {
        if (!isBluetoothEnabled) {
            Log.w(TAG, "Bluetooth is disabled, cannot start mesh")
            return
        }

        startGattServer()
        startAdvertising()
        startScanning()
        _isMeshRunning.value = true
        Log.i(TAG, "BitChat BLE mesh started (Dual-role active)")
    }

    fun stopMesh() {
        stopAdvertising()
        stopScanning()
        closeAllConnections()
        stopGattServer()
        updateLinkCount()
        _isMeshRunning.value = false
        Log.i(TAG, "BitChat BLE mesh stopped")
    }

    // =========================================================================
    // PERIPHERAL ROLE (GATT Server + Advertising)
    // =========================================================================

    private fun startGattServer() {
        if (gattServer != null) return
        val manager = bluetoothManager ?: return

        try {
            gattServer = manager.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            val properties = BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY

            val permissions = BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE

            gattCharacteristic = BluetoothGattCharacteristic(CHAR_UUID, properties, permissions)

            val cccd = BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            gattCharacteristic?.addDescriptor(cccd)
            service.addCharacteristic(gattCharacteristic)

            gattServer?.addService(service)
            Log.i(TAG, "GATT Server started and service added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GATT server: ${e.message}")
        }
    }

    private fun stopGattServer() {
        try {
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
            subscribedCentrals.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT server: ${e.message}")
        }
    }

    private fun startAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BLE Advertiser not supported on this device")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false) // Keeps advertisement under 31-byte legacy limit
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            _isAdvertising.value = false
        } catch (_: Exception) {}
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE advertising started successfully")
            _isAdvertising.value = true
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed with error code: $errorCode")
            _isAdvertising.value = false
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            val linkId = "central_$address"
            Log.d(TAG, "Central $linkId state: $newState (status: $status)")

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribedCentrals.remove(address)
                updateLinkCount()
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }

            if (value != null && characteristic.uuid == CHAR_UUID) {
                val linkId = "central_${device.address}"
                scope.launch(Dispatchers.Default) {
                    meshEngine.processInboundPacket(value, ingressLinkId = linkId)
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                subscribedCentrals[device.address] = device
                updateLinkCount()
                // Send Announce back to newly subscribed central
                meshEngine.broadcastAnnounce()
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    // =========================================================================
    // CENTRAL ROLE (GATT Client + Scanning)
    // =========================================================================

    private fun startScanning() {
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BLE Scanner not available")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            Log.i(TAG, "BLE scanning started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scanning: ${e.message}")
        }
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
            _isScanning.value = false
        } catch (_: Exception) {}
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val address = device.address

            // Don't connect if already connected or connecting
            if (connectedPeripherals.containsKey(address) || subscribedCentrals.containsKey(address)) {
                return
            }

            Log.i(TAG, "Discovered BitChat peer: $address (RSSI: ${result.rssi})")
            connectToPeripheral(device)
        }
    }

    private fun connectToPeripheral(device: BluetoothDevice) {
        val address = device.address
        if (connectedPeripherals.containsKey(address)) return

        try {
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattClientCallback)
            }
            if (gatt != null) {
                connectedPeripherals[address] = gatt
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $address: ${e.message}")
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            Log.d(TAG, "Peripheral $address connection state: $newState (status: $status)")

            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                // Request higher MTU for BitChat packets
                gatt.requestMtu(512)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedPeripherals.remove(address)?.close()
                peripheralCharacteristics.remove(address)
                updateLinkCount()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU for ${gatt.device.address} changed to $mtu (status: $status)")
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val service = gatt.getService(SERVICE_UUID) ?: return
            val char = service.getCharacteristic(CHAR_UUID) ?: return
            val address = gatt.device.address

            peripheralCharacteristics[address] = char

            // Enable local notifications for characteristic
            gatt.setCharacteristicNotification(char, true)

            // Enable notifications on remote peripheral by writing CCCD
            val cccd = char.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }

            updateLinkCount()
            // Announce presence over the new link
            meshEngine.broadcastAnnounce()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHAR_UUID) {
                @Suppress("DEPRECATION")
                val bytes = characteristic.value ?: return
                val linkId = "peripheral_${gatt.device.address}"
                scope.launch(Dispatchers.Default) {
                    meshEngine.processInboundPacket(bytes, ingressLinkId = linkId)
                }
            }
        }

        // For Android 13+ (API 33)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHAR_UUID) {
                val linkId = "peripheral_${gatt.device.address}"
                scope.launch(Dispatchers.Default) {
                    meshEngine.processInboundPacket(value, ingressLinkId = linkId)
                }
            }
        }
    }

    // =========================================================================
    // PACKET TRANSMISSION & SPLIT-HORIZON ROUTING
    // =========================================================================

    /**
     * Sends packet bytes to all active peer connections, excluding the ingress link (Split-Horizon).
     */
    private fun sendPacketToAllLinks(bytes: ByteArray, excludeLinkId: String?) {
        // Send to connected peripherals (we are Central)
        for ((address, gatt) in connectedPeripherals) {
            val linkId = "peripheral_$address"
            if (linkId == excludeLinkId) continue

            val char = peripheralCharacteristics[address] ?: continue
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        char,
                        bytes,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    char.value = bytes
                    @Suppress("DEPRECATION")
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to $address: ${e.message}")
            }
        }

        // Send to subscribed centrals (we are Peripheral)
        val server = gattServer
        val serverChar = gattCharacteristic
        if (server != null && serverChar != null) {
            for ((address, device) in subscribedCentrals) {
                val linkId = "central_$address"
                if (linkId == excludeLinkId) continue

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        server.notifyCharacteristicChanged(device, serverChar, false, bytes)
                    } else {
                        @Suppress("DEPRECATION")
                        serverChar.value = bytes
                        @Suppress("DEPRECATION")
                        server.notifyCharacteristicChanged(device, serverChar, false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to notify central $address: ${e.message}")
                }
            }
        }
    }

    private fun closeAllConnections() {
        for ((_, gatt) in connectedPeripherals) {
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {}
        }
        connectedPeripherals.clear()
        peripheralCharacteristics.clear()
        subscribedCentrals.clear()
    }

    private fun updateLinkCount() {
        val total = connectedPeripherals.size + subscribedCentrals.size
        meshEngine.updateDirectLinksCount(total)
    }
}
