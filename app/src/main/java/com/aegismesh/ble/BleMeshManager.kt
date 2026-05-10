package com.aegismesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.aegismesh.core.models.MeshPacket
import com.aegismesh.core.service.MeshService
import com.aegismesh.core.service.RadioState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission") // Permissions handled at UI level
class BleMeshManager(private val context: Context) : MeshService {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
    private val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    // Expose flows to the UI layer
    private val _incomingPackets = MutableSharedFlow<MeshPacket>(replay = 5)
    override val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

    private val _radioState = MutableStateFlow(RadioState.IDLE)
    override val radioState: SharedFlow<RadioState> = _radioState.asStateFlow()

    // Deduplication Set: Store seen MessageIDs with a timestamp
    // Using ConcurrentHashMap to allow safe removal of stale IDs
    private val seenMessageIds = ConcurrentHashMap<Int, Long>()

    // Coroutine scope for managing relay timings
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Standard UUID for Aegis Mesh Broadcasts
    private val MESH_SERVICE_UUID = ParcelUuid(UUID.fromString("0000AEB1-0000-1000-8000-00805f9b34fb"))

    override fun startMeshEngine() {
        Log.d("BleMeshManager", "Starting Mesh Engine")
        if (bluetoothAdapter == null) {
            Log.e("BleMeshManager", "Bluetooth Adapter is NULL")
            _radioState.value = RadioState.ERROR
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.e("BleMeshManager", "Bluetooth is DISABLED")
            _radioState.value = RadioState.ERROR
            return
        }
        if (scanner == null) {
            Log.e("BleMeshManager", "Scanner is NULL")
            _radioState.value = RadioState.ERROR
            return
        }
        if (advertiser == null) {
            Log.e("BleMeshManager", "Advertiser is NULL")
            _radioState.value = RadioState.ERROR
            return
        }
        
        Log.d("BleMeshManager", "All checks passed, starting scan")
        startScanning(ScanSettings.SCAN_MODE_BALANCED)
        cleanUpStaleIds()
    }

    override fun stopMeshEngine() {
        stopScanning()
        stopAdvertising()
        _radioState.value = RadioState.IDLE
        scope.coroutineContext.cancelChildren()
    }

    override suspend fun sendSOS(intentCode: Byte, lat: Double, lon: Double) {
        // Generate a random 32-bit integer for the MessageID
        val newId = (Math.random() * Int.MAX_VALUE).toInt()
        val packet = MeshPacket(
            messageId = newId,
            hopCount = MeshPacket.DEFAULT_TTL,
            compressedLat = (lat * 10_000_000).toInt(),
            compressedLon = (lon * 10_000_000).toInt(),
            intentCode = intentCode
        )

        // Prevent self-relaying
        seenMessageIds[newId] = System.currentTimeMillis()

        broadcastPacket(packet, durationMs = 10_000L) // Broadcast own SOS longer
    }

    private fun startScanning(mode: Int) {
        val settings = ScanSettings.Builder()
            .setScanMode(mode)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(MESH_SERVICE_UUID)
            .build()

        _radioState.value = if (mode == ScanSettings.SCAN_MODE_LOW_LATENCY)
            RadioState.SCANNING_LOW_LATENCY else RadioState.SCANNING_BALANCED

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (e: Exception) {
            Log.e("BleMeshManager", "Failed to start scanning", e)
            _radioState.value = RadioState.ERROR
        }
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.scanRecord?.serviceData?.get(MESH_SERVICE_UUID)?.let { bytes ->
                MeshPacket.fromByteArray(bytes)?.let { packet ->
                    handleIncomingPacket(packet)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleMeshManager", "Scan failed with error code: $errorCode")
            _radioState.value = RadioState.ERROR
        }
    }

    private fun handleIncomingPacket(packet: MeshPacket) {
        val currentTime = System.currentTimeMillis()

        // Deduplication Logic
        if (seenMessageIds.containsKey(packet.messageId)) return
        seenMessageIds[packet.messageId] = currentTime

        scope.launch {
            _incomingPackets.emit(packet)
        }

        // Tier 0 (Critical) Alert heuristic: e.g., intent codes 0x01 to 0x05 are critical
        if (packet.intentCode in 1..5) {
            boostScannerLatency()
        }

        // Routing / Re-broadcasting Logic
        if (packet.hopCount > 0) {
            packet.hopCount--
            broadcastPacket(packet, durationMs = 5000L) // Propagate for 5 seconds
        }
    }

    private fun broadcastPacket(packet: MeshPacket, durationMs: Long) {
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(MESH_SERVICE_UUID)
            .addServiceData(MESH_SERVICE_UUID, packet.toByteArray())
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _radioState.value = RadioState.RELAYING
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("BleMeshManager", "Advertising failed: $errorCode")
            }
        }

        try {
            advertiser?.startAdvertising(advertiseSettings, advertiseData, callback)

            // Stop advertising after the specified duration
            scope.launch {
                delay(durationMs)
                advertiser?.stopAdvertising(callback)
                if (_radioState.value == RadioState.RELAYING) {
                    _radioState.value = RadioState.SCANNING_BALANCED
                }
            }
        } catch (e: Exception) {
            Log.e("BleMeshManager", "Failed to start advertising", e)
        }
    }

    private fun boostScannerLatency() {
        if (_radioState.value == RadioState.SCANNING_LOW_LATENCY) return

        // Temporarily boost scanner to catch rapid mesh bursts
        stopScanning()
        startScanning(ScanSettings.SCAN_MODE_LOW_LATENCY)

        scope.launch {
            delay(15_000L) // Keep boosted for 15s
            if (_radioState.value == RadioState.SCANNING_LOW_LATENCY) {
                stopScanning()
                startScanning(ScanSettings.SCAN_MODE_BALANCED)
            }
        }
    }

    private fun stopAdvertising() {
        // Implementation would track active callbacks and stop them
        // For simplicity in this blueprint, it is handled within broadcastPacket's delay scope
    }

    private fun cleanUpStaleIds() {
        scope.launch {
            while (isActive) {
                delay(60_000L) // Run every minute
                val now = System.currentTimeMillis()
                // Remove IDs older than 5 minutes
                seenMessageIds.entries.removeIf { now - it.value > 300_000L }
            }
        }
    }
}
