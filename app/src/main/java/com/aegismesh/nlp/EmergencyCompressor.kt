package com.aegismesh.nlp

import android.content.Context
import org.json.JSONObject
import java.io.InputStream

/**
 * A lightweight utility to map human-readable emergency strings to compact 1-byte codes
 * using a predefined dictionary (intents.json). This minimizes packet size for BLE Mesh.
 */
class EmergencyCompressor(private val context: Context) {

    private val intentMap: Map<Byte, String> by lazy {
        loadIntents()
    }

    private fun loadIntents(): Map<Byte, String> {
        val map = mutableMapOf<Byte, String>()
        try {
            val inputStream: InputStream = context.assets.open("intents.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            
            val json = String(buffer, Charsets.UTF_8)
            val jsonObject = JSONObject(json)
            val intents = jsonObject.getJSONObject("intents")
            
            intents.keys().forEach { key ->
                // Keys are in "0x00" format
                val code = key.substring(2).toInt(16).toByte()
                map[code] = intents.getString(key)
            }
        } catch (e: Exception) {

            e.printStackTrace()
        }
        return map
    }

    /**
     * Returns a list of all available human-readable emergency descriptions.
     */
    fun getAvailableIntents(): List<String> {
        return intentMap.values.toList().sorted()
    }

    /**
     * Compresses a descriptive string into a single byte.
     * Returns 0x00 (Unknown) if no match is found.
     */
    fun compressIntent(intent: String): Byte {
        return intentMap.entries.find { it.value.equals(intent, ignoreCase = true) }?.key ?: 0x00
    }

    /**
     * Decompresses a 1-byte code back into its descriptive string.
     */
    fun decompressIntent(intentCode: Byte): String {
        return intentMap[intentCode] ?: "Unknown Emergency"
    }
}
