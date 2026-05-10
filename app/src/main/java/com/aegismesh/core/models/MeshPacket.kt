package com.aegismesh.core.models

import java.nio.ByteBuffer

/**
 * Represents a single 31-byte BLE Connectionless Mesh Packet.
 *
 * BLE Advertisement Payload Limit: 31 bytes.
 * We structure it as follows:
 * - MessageID (4 bytes): Unique identifier to prevent broadcast storms.
 * - HopCount/TTL (1 byte): Time-To-Live, decremented at each hop.
 * - Latitude (4 bytes): Compressed integer representation of GPS lat.
 * - Longitude (4 bytes): Compressed integer representation of GPS lon.
 * - IntentCode (1 byte): Maps to a predefined emergency string (e.g., 0x0A = "Trapped").
 * - Signature (4-16 bytes): Optional lightweight HMAC or parity check to prevent spoofing.
 */
data class MeshPacket(
    val messageId: Int,
    var hopCount: Byte,
    val compressedLat: Int,
    val compressedLon: Int,
    val intentCode: Byte,
    val signature: ByteArray = ByteArray(4) { 0 } // Basic 4-byte signature placeholder
) {
    val latitude: Double get() = compressedLat / 10_000_000.0
    val longitude: Double get() = compressedLon / 10_000_000.0

    /**
     * Serializes the packet into a ByteArray suitable for BLE advertising.
     */
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
        const val PACKET_SIZE = 18 // 4 + 1 + 4 + 4 + 1 + 4
        const val DEFAULT_TTL: Byte = 5 // Max 5 hops to prevent endless storms

        /**
         * Deserializes a ByteArray back into a MeshPacket.
         */
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
