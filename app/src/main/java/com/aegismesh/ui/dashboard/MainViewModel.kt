package com.aegismesh.ui.dashboard

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegismesh.core.models.MeshPacket
import com.aegismesh.core.service.MeshService
import com.aegismesh.core.service.RadioState
import com.aegismesh.nlp.EmergencyCompressor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

// Bridges between the BLE engine and the UI
class MainViewModel(
    private val meshService: MeshService,
    private val compressor: EmergencyCompressor,
) : ViewModel() {

    val radioState: StateFlow<RadioState> = meshService.radioState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RadioState.IDLE,
        )

    private val _intercepts = MutableStateFlow<List<Pair<MeshPacket, String>>>(emptyList())
    val intercepts: StateFlow<List<Pair<MeshPacket, String>>> = _intercepts.asStateFlow()

    val availableIntents: List<String> = compressor.getAvailableIntents()

    private val _selectedPacket = MutableStateFlow<MeshPacket?>(null)
    val selectedPacket: StateFlow<MeshPacket?> = _selectedPacket.asStateFlow()

    // Mocked user location
    private val userLat = 37.7749
    private val userLon = -122.4194

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private var beepJob: Job? = null

    private val _beepEnabled = MutableStateFlow(value = false)
    val beepEnabled: StateFlow<Boolean> = _beepEnabled.asStateFlow()

    init {
        // Collect packets and map to human-readable strings
        viewModelScope.launch {
            meshService.incomingPackets.collect { packet ->
                val humanReadableIntent = compressor.decompressIntent(packet.intentCode)
                
                _intercepts.update { currentList ->
                    listOf(packet to humanReadableIntent) + currentList.take(99)
                }
            }
        }
    }

    fun startMesh() {
        meshService.startMeshEngine()
    }

    fun handleDeepLink(messageId: Int) {
        val found = _intercepts.value.find { it.first.messageId == messageId }
        found?.let { selectPacket(it.first) }
    }

    fun stopMesh() {
        meshService.stopMeshEngine()
        stopBeeping()
    }

    fun selectPacket(packet: MeshPacket?) {
        _selectedPacket.value = packet
        if ((packet != null) && _beepEnabled.value) {
            startBeeping(packet)
        } else {
            stopBeeping()
        }
    }

    fun toggleBeep(enabled: Boolean) {
        _beepEnabled.value = enabled
        val currentPacket = _selectedPacket.value
        if ((enabled) && (currentPacket != null)) {
            startBeeping(currentPacket)
        } else {
            stopBeeping()
        }
    }

    private fun startBeeping(target: MeshPacket) {
        stopBeeping()
        beepJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                if (!_beepEnabled.value) break

                val distance = calculateDistance(
                    userLat, userLon,
                    target.latitude, target.longitude
                )

                // Beep only when within 1-10 meters
                if (distance in 1.0..10.0) {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(300L)
                } else {
                    delay(1000L)
                }
            }
        }
    }

    private fun stopBeeping() {
        beepJob?.cancel()
        beepJob = null
    }

    // haversine formula for distance
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3
        val phi1 = lat1 * PI / 180
        val phi2 = lat2 * PI / 180
        val deltaPhi = (lat2 - lat1) * PI / 180
        val deltaLambda = (lon2 - lon1) * PI / 180

        val a = sin(deltaPhi / 2).pow(2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    fun triggerSos(intent: String = "I'm trapped and bleeding") {
        viewModelScope.launch {
            val intentCode = compressor.compressIntent(intent)
            
            // mock coordinates
            val currentLat = 37.7749
            val currentLon = -122.4194

            meshService.sendSOS(
                intentCode = intentCode,
                lat = currentLat,
                lon = currentLon
            )
        }
    }
}
