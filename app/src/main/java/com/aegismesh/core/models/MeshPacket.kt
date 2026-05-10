package com.aegismesh.core.models

import java.nio.ByteBuffer

// Mesh packet structure (fits in 31-byte BLE limit)
// MessageID (4), TTL (1), Lat (4), Lon (4), Intent (1), Sig (4) = 18 bytes
data class MeshPacket(
    val messageId: Int,
    var hopCount: Byte,
    val compressedLat: Int,
    val compressedLon: Int,
    val intentCode: Byte,
    val signature: ByteArray = ByteArray(4) { 0 }
) {
    val latitude: Double get() = compressedLat / 10_000_000.0
    val longitude: Double get() = compressedLon / 10_000_000.0

    // serialize to byte array for advertising
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(PACKET_SIZE)
        buffer.putInt(messageId)
        buffer.put(hopCount)
        buffer.putInt(compressedLat)
        buffer.putInt(compressedLon)
        buffer.put(intentCode)
        buffer.put(signature)
        return buffer.array()
    }

    companion object {
        const val PACKET_SIZE = 18
        const val DEFAULT_TTL: Byte = 5

        // parse byte array back into packet object
        fun fromByteArray(bytes: ByteArray): MeshPacket? {
            if (bytes.size < PACKET_SIZE) return null

            val buffer = ByteBuffer.wrap(bytes)
            val id = buffer.int
            val ttl = buffer.get()
            val lat = buffer.int
            val lon = buffer.int
            val intent = buffer.get()
            val sig = ByteArray(4)
            buffer.get(sig)

            return MeshPacket(id, ttl, lat, lon, intent, sig)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MeshPacket
        return messageId == other.messageId
    }

    override fun hashCode(): Int {
        return messageId
    }
}
