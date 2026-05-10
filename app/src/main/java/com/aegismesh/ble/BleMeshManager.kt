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

@SuppressLint("MissingPermission")
class BleMeshManager(context: Context) : MeshService {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? get() = bluetoothAdapter?.bluetoothLeAdvertiser
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val _incomingPackets = MutableSharedFlow<MeshPacket>(replay = 5)
    override val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

    private val _radioState = MutableStateFlow(RadioState.IDLE)
    override val radioState: SharedFlow<RadioState> = _radioState.asStateFlow()

    // track seen IDs to avoid loops
    private val seenMessageIds = ConcurrentHashMap<Int, Long>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // custom UUID for the mesh
    private val meshServiceUuid = ParcelUuid(UUID.fromString("0000AEB1-0000-1000-8000-00805f9b34fb"))

    override fun startMeshEngine() {
        Log.d("BleMeshManager", "Attempting to start Mesh Engine")
        val adapter = bluetoothAdapter
        
        if (adapter == null) {
            Log.e("BleMeshManager", "Bluetooth Adapter is NULL")
            _radioState.value = RadioState.ERROR
            return
        }
        if (!adapter.isEnabled) {
            Log.e("BleMeshManager", "Bluetooth is DISABLED")
            _radioState.value = RadioState.ERROR
            return
        }
        if (scanner == null) {
            Log.e("BleMeshManager", "Scanner is NULL")
            _radioState.value = RadioState.ERROR
            return
        }
        
        Log.d("BleMeshManager", "Checks passed, starting scan")
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
        val newId = (Math.random() * Int.MAX_VALUE).toInt()
        
        // Use 10M for 7-decimal precision (~1cm)
        val compLat = (lat * 10_000_000).toInt()
        val compLon = (lon * 10_000_000).toInt()

        val packet = MeshPacket(
            messageId = newId,
            hopCount = MeshPacket.DEFAULT_TTL,
            compressedLat = compLat,
            compressedLon = compLon,
            intentCode = intentCode,
        )
        
        Log.d("BleMeshManager", "Sending SOS: lat=$lat ($compLat), lon=$lon ($compLon)")
        seenMessageIds[newId] = System.currentTimeMillis()
        broadcastPacket(packet, durationMs = 10_000L)
    }

    private fun startScanning(mode: Int) {
        val settings = ScanSettings.Builder()
            .setScanMode(mode)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(meshServiceUuid)
            .build()

        _radioState.value = if (mode == ScanSettings.SCAN_MODE_LOW_LATENCY)
            RadioState.SCANNING_LOW_LATENCY else RadioState.SCANNING_BALANCED

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (_: Exception) {
            Log.e("BleMeshManager", "Failed to start scanning")
            _radioState.value = RadioState.ERROR
        }
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
            // Ignore
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.scanRecord?.serviceData?.get(meshServiceUuid)?.let { bytes ->
                MeshPacket.fromByteArray(bytes)?.let { packet ->
                    handleIncomingPacket(packet)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleMeshManager", "Scan failed: $errorCode")
            _radioState.value = RadioState.ERROR
        }
    }

    private fun handleIncomingPacket(packet: MeshPacket) {
        val currentTime = System.currentTimeMillis()

        if (seenMessageIds.containsKey(packet.messageId)) return
        seenMessageIds[packet.messageId] = currentTime

        scope.launch {
            _incomingPackets.emit(packet)
        }

        // if intent is critical, boost scan speed
        if (packet.intentCode in 1..5) {
            boostScannerLatency()
        }

        // relay logic
        if (packet.hopCount > 0) {
            packet.hopCount--
            broadcastPacket(packet, durationMs = 5000L)
        }
    }

    private fun broadcastPacket(packet: MeshPacket, durationMs: Long) {
        val adv = advertiser
        if (adv == null) {
            Log.e("BleMeshManager", "Cannot broadcast: Advertiser is NULL")
            return
        }

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(meshServiceUuid)
            .addServiceData(meshServiceUuid, packet.toByteArray())
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
            adv.startAdvertising(advertiseSettings, advertiseData, callback)

            scope.launch {
                delay(durationMs)
                adv.stopAdvertising(callback)
                if (_radioState.value == RadioState.RELAYING) {
                    _radioState.value = RadioState.SCANNING_BALANCED
                }
            }
        } catch (_: Exception) {
            Log.e("BleMeshManager", "Failed to start advertising")
        }
    }

    private fun boostScannerLatency() {
        if (_radioState.value == RadioState.SCANNING_LOW_LATENCY) return

        stopScanning()
        startScanning(ScanSettings.SCAN_MODE_LOW_LATENCY)

        scope.launch {
            delay(15_000L)
            if (_radioState.value == RadioState.SCANNING_LOW_LATENCY) {
                stopScanning()
                startScanning(ScanSettings.SCAN_MODE_BALANCED)
            }
        }
    }

    private fun stopAdvertising() {
        // TODO: track and stop active callbacks if needed
    }

    private fun cleanUpStaleIds() {
        scope.launch {
            while (isActive) {
                delay(60_000L)
                val now = System.currentTimeMillis()
                seenMessageIds.entries.removeIf { now - it.value > 300_000L }
            }
        }
    }
}
