package com.app.chat.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
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
import android.os.ParcelUuid
import android.util.Log
import com.app.chat.bridge.PythonCoreBridge
import com.app.chat.model.PeerDevice
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BleMeshManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BleMeshManager"
        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

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

    private val discoveredPeersMap = ConcurrentHashMap<String, PeerDevice>()
    private val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()

    var onPeerDiscovered: ((PeerDevice) -> Unit)? = null
    var onPacketReceived: ((ByteArray) -> Unit)? = null

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

        adv.startAdvertising(settings, data, scanResponse, advertiseCallback)
        Log.i(TAG, "BLE advertising started for $deviceName")
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
            // Scan without restrictive filter so all real nearby Bluetooth devices are detected!
            scn.startScan(null, settings, scanCallback)
            Log.i(TAG, "BLE wide-area scanning started successfully")
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
        val rawName = result.scanRecord?.deviceName ?: device.name
        val isNetChatPeer = result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true

        val name = when {
            !rawName.isNullOrBlank() -> rawName
            isNetChatPeer -> "NetChat Peer (${address.takeLast(5)})"
            else -> "Nearby Device (${address.takeLast(5)})"
        }
        val rssi = result.rssi
        val distance = estimateDistance(rssi)
        val distanceText = String.format("~ %.1f m", distance)

        val peer = PeerDevice(
            id = address,
            name = if (isNetChatPeer && !name.contains("NetChat")) "$name (Mesh Node)" else name,
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
            service.addCharacteristic(characteristic)
            gattServer?.addService(service)
            Log.i(TAG, "GATT Server started with NetChat Mesh service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GATT server", e)
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
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
            value?.let { packet ->
                Log.i(TAG, "Received packet of ${packet.size} bytes from ${device?.address}")
                onPacketReceived?.invoke(packet)
                try {
                    PythonCoreBridge.processIncomingBle(packet)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing packet in Python core", e)
                }
            }
        }
    }

    fun sendPayloadToPeer(peerAddress: String, payload: ByteArray) {
        val device = bluetoothAdapter?.getRemoteDevice(peerAddress) ?: return
        val existingGatt = activeConnections[peerAddress]

        if (existingGatt != null) {
            writeCharacteristic(existingGatt, payload)
        } else {
            device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "Connected to GATT peer $peerAddress, requesting MTU 512")
                        gatt?.requestMtu(512)
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        activeConnections.remove(peerAddress)
                        gatt?.close()
                    }
                }

                override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                    gatt?.discoverServices()
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                        activeConnections[peerAddress] = gatt
                        writeCharacteristic(gatt, payload)
                    }
                }
            })
        }
    }

    private fun writeCharacteristic(gatt: BluetoothGatt, data: ByteArray) {
        val service = gatt.getService(SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID) ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    fun broadcastPacket(payload: ByteArray) {
        discoveredPeersMap.keys.forEach { addr ->
            sendPayloadToPeer(addr, payload)
        }
    }
}
