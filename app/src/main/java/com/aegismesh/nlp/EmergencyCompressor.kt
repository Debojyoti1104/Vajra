package com.aegismesh.nlp

import android.content.Context
import org.json.JSONObject
import java.io.InputStream

// Maps emergency strings to 1-byte codes using intents.json
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
                // keys are hex: "0x0A" -> byte
                val code = key.substring(2).toInt(16).toByte()
                map[code] = intents.getString(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    fun getAvailableIntents(): List<String> {
        return intentMap.values.asSequence().sorted().toList()
    }

    // string to byte
    fun compressIntent(intent: String): Byte {
        return intentMap.entries.find { it.value.equals(intent, ignoreCase = true) }?.key ?: 0x00
    }

    // byte back to string
    fun decompressIntent(intentCode: Byte): String {
        return intentMap[intentCode] ?: "Unknown Emergency"
    }
}
