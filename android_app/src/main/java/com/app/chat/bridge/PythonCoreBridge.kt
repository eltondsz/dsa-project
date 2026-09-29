package com.app.chat.bridge

import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

object PythonCoreBridge {
    private const val TAG = "PythonCoreBridge"
    private var isInitialized = false
    private var pythonModule: PyObject? = null

    fun initialize(context: Context): Boolean {
        if (isInitialized && pythonModule != null) return true
        return try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context.applicationContext))
            }
            val py = Python.getInstance()
            pythonModule = py.getModule("core_logic")
            val initRes = pythonModule?.callAttr("init_database")?.asMap()
            val code = initRes?.entries?.find { it.key.toString() == "code" }?.value?.toInt() ?: 0
            isInitialized = true
            Log.d(TAG, "Chaquopy Python core initialized successfully. Result code: $code")
            code == 0
        } catch (e: Throwable) {
            Log.w(TAG, "Chaquopy initialization bypassed or running in fallback: ${e.message}")
            isInitialized = true
            false
        }
    }

    fun getDeviceId(): String {
        return try {
            val result = pythonModule?.callAttr("get_device_id")?.asMap()
            result?.entries?.find { it.key.toString() == "device_id" }?.value?.toString() ?: "device-local-uuid"
        } catch (e: Throwable) {
            Log.e(TAG, "Error in getDeviceId: ${e.message}")
            "device-local-uuid"
        }
    }

    fun getPublicKey(): String {
        return try {
            val result = pythonModule?.callAttr("get_public_key")?.asMap()
            result?.entries?.find { it.key.toString() == "public_key" }?.value?.toString() ?: "Unavailable"
        } catch (e: Throwable) {
            Log.e(TAG, "Error in getPublicKey: ${e.message}")
            "Unavailable"
        }
    }

    fun prepareHandshakePacket(): String? {
        return try {
            val result = pythonModule?.callAttr("prepare_handshake_packet")?.asMap()
            result?.entries?.find { it.key.toString() == "packet" }?.value?.toString()
        } catch (e: Throwable) {
            Log.e(TAG, "Error in prepareHandshakePacket: ${e.message}")
            null
        }
    }

    fun prepareOutgoingMessage(recipientUuid: String, message: String): String? {
        return try {
            val result = pythonModule?.callAttr("prepare_outgoing_message", recipientUuid, message)?.asMap()
            result?.entries?.find { it.key.toString() == "packet" }?.value?.toString()
        } catch (e: Throwable) {
            Log.e(TAG, "Error in prepareOutgoingMessage: ${e.message}")
            null
        }
    }

    fun processIncomingBle(rawPayload: ByteArray): Map<String, Any?>? {
        return try {
            val result = pythonModule?.callAttr("process_incoming_ble", rawPayload)
            result?.asMap()?.mapKeys { it.key.toString() }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in processIncomingBle: ${e.message}")
            null
        }
    }

    fun getPeers(): List<Map<String, Any?>> {
        return try {
            val result = pythonModule?.callAttr("get_peers")?.asList()
            result?.mapNotNull { item ->
                item?.asMap()?.mapKeys { it.key.toString() }
            } ?: emptyList()
        } catch (e: Throwable) {
            Log.e(TAG, "Error in getPeers: ${e.message}")
            emptyList()
        }
    }

    fun getMessagesForPeer(peerId: String): List<Map<String, Any?>> {
        return try {
            val result = pythonModule?.callAttr("get_messages_for_peer", peerId)
            result?.asList()?.mapNotNull { item ->
                item.asMap()?.mapKeys { it.key.toString() }
            } ?: emptyList()
        } catch (e: Throwable) {
            Log.e(TAG, "Error in getMessagesForPeer: ${e.message}")
            emptyList()
        }
    }

    fun getStorageBreakdown(): Map<String, Long> {
        return try {
            val result = pythonModule?.callAttr("get_storage_breakdown")?.asMap()
            result?.mapKeys { it.key.toString() }?.mapValues { it.value.toLong() } ?: emptyMap()
        } catch (e: Throwable) {
            Log.e(TAG, "Error in getStorageBreakdown: ${e.message}")
            emptyMap()
        }
    }

    fun triggerPanicWipe(): Boolean {
        return try {
            val result = pythonModule?.callAttr("trigger_panic_wipe")?.asMap()
            val code = result?.entries?.find { it.key.toString() == "code" }?.value?.toInt() ?: 0
            code == 0
        } catch (e: Throwable) {
            Log.e(TAG, "Error in triggerPanicWipe: ${e.message}")
            true
        }
    }
}
