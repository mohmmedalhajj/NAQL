package com.example.model

import org.json.JSONArray
import org.json.JSONObject

object TransferProtocol {
    const val TYPE_METADATA = "META"
    const val TYPE_CONTROL_CANCEL = "CANCEL"
    const val TYPE_CONTROL_ACK = "ACK"

    data class FileMeta(
        val payloadId: Long,
        val name: String,
        val size: Long,
        val mimeType: String,
        val sha256: String
    )

    data class TransferMetadata(
        val sessionId: String,
        val senderName: String,
        val files: List<FileMeta>
    )

    fun createMetadataBytes(sessionId: String, senderName: String, files: List<FileMeta>): ByteArray {
        val json = JSONObject()
        json.put("type", TYPE_METADATA)
        json.put("session_id", sessionId)
        json.put("sender_name", senderName)

        val array = JSONArray()
        for (f in files) {
            val obj = JSONObject()
            obj.put("payload_id", f.payloadId)
            obj.put("name", f.name)
            obj.put("size", f.size)
            obj.put("mime_type", f.mimeType)
            obj.put("sha256", f.sha256)
            array.put(obj)
        }
        json.put("files", array)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun parseMetadataBytes(bytes: ByteArray): TransferMetadata? {
        return try {
            val str = String(bytes, Charsets.UTF_8)
            val json = JSONObject(str)
            if (json.optString("type") != TYPE_METADATA) return null

            val sessionId = json.getString("session_id")
            val senderName = json.optString("sender_name", "Unknown")
            val array = json.getJSONArray("files")
            val files = mutableListOf<FileMeta>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                files.add(
                    FileMeta(
                        payloadId = obj.getLong("payload_id"),
                        name = obj.getString("name"),
                        size = obj.getLong("size"),
                        mimeType = obj.optString("mime_type", "*/*"),
                        sha256 = obj.optString("sha256", "")
                    )
                )
            }
            TransferMetadata(sessionId, senderName, files)
        } catch (e: Exception) {
            null
        }
    }

    fun createCancelMessageBytes(sessionId: String, reason: String = "User cancelled"): ByteArray {
        val json = JSONObject()
        json.put("type", TYPE_CONTROL_CANCEL)
        json.put("session_id", sessionId)
        json.put("reason", reason)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun isCancelMessage(bytes: ByteArray): Boolean {
        return try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            json.optString("type") == TYPE_CONTROL_CANCEL
        } catch (e: Exception) {
            false
        }
    }

    fun createAckMessageBytes(sessionId: String): ByteArray {
        val json = JSONObject()
        json.put("type", TYPE_CONTROL_ACK)
        json.put("session_id", sessionId)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun isAckMessage(bytes: ByteArray): Boolean {
        return try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            json.optString("type") == TYPE_CONTROL_ACK
        } catch (e: Exception) {
            false
        }
    }
}
