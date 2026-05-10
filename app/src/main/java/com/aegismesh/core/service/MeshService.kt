package com.aegismesh.core.service

import com.aegismesh.core.models.MeshPacket
import kotlinx.coroutines.flow.SharedFlow

/**
 * Defines the core operations for the Aegis Mesh network.
 * Allows decoupling the UI/ViewModel from the raw BLE implementation.
 */
interface MeshService {

    /**
     * A hot stream of incoming, deduplicated packets detected by the radio.
     */
    val incomingPackets: SharedFlow<MeshPacket>

    /**
     * Current status of the BLE radio (Idle, Scanning, Relaying, Error).
     */
    val radioState: SharedFlow<RadioState>

    /**
     * Starts the BLE Scanner and Advertiser.
     */
    fun startMeshEngine()

    /**
     * Stops the radio engine to save battery.
     */
    fun stopMeshEngine()

    /**
     * Triggers a new SOS broadcast from the current device.
     * @param intentCode The predefined emergency condition code.
     * @param lat Current latitude.
     * @param lon Current longitude.
     */
    suspend fun sendSOS(intentCode: Byte, lat: Double, lon: Double)
}

enum class RadioState {
    IDLE,
    SCANNING_BALANCED,
    SCANNING_LOW_LATENCY, // Triggered when critical packets are nearby
    RELAYING,             // Currently re-broadcasting a packet
    ERROR
}
