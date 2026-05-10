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

/**
 * The ViewModel tying together the BleMeshManager (Radio Engine)
 * and the EmergencyCompressor (TinyNLP), feeding state to the Compose UI.
 */
class MainViewModel(
    private val meshService: MeshService,
    private val compressor: EmergencyCompressor
) : ViewModel() {

    // Expose raw radio state
    val radioState: StateFlow<RadioState> = meshService.radioState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RadioState.IDLE
        )

    // A cumulative list of recent intercepts, with their decompressed intent
    private val _intercepts = MutableStateFlow<List<Pair<MeshPacket, String>>>(emptyList())
    val intercepts: StateFlow<List<Pair<MeshPacket, String>>> = _intercepts.asStateFlow()

    // List of available emergency intents for the user to choose from
    val availableIntents: List<String> = compressor.getAvailableIntents()

    // Currently selected packet for map tracking
    private val _selectedPacket = MutableStateFlow<MeshPacket?>(null)
    val selectedPacket: StateFlow<MeshPacket?> = _selectedPacket.asStateFlow()

    // User's own location (Mocked for now, in production use LocationServices)
    private val userLat = 37.7749
    private val userLon = -122.4194

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private var beepJob: Job? = null

    // Toggle for proximity beeping
    private val _beepEnabled = MutableStateFlow(false)
    val beepEnabled: StateFlow<Boolean> = _beepEnabled.asStateFlow()

    init {
        // Collect incoming packets and map them to human-readable text
        viewModelScope.launch {
            meshService.incomingPackets.collect { packet ->
                val humanReadableIntent = compressor.decompressIntent(packet.intentCode)
                
                _intercepts.update { currentList ->
                    // Add new packet to the top of the list, keep only the latest 100
                    listOf(packet to humanReadableIntent) + currentList.take(99)
                }
            }
        }
    }

    fun startMesh() {
        meshService.startMeshEngine()
    }

    fun stopMesh() {
        meshService.stopMeshEngine()
        stopBeeping()
    }

    fun selectPacket(packet: MeshPacket?) {
        _selectedPacket.value = packet
        if (packet != null && _beepEnabled.value) {
            startBeeping(packet)
        } else {
            stopBeeping()
        }
    }

    fun toggleBeep(enabled: Boolean) {
        _beepEnabled.value = enabled
        val currentPacket = _selectedPacket.value
        if (enabled && currentPacket != null) {
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

                // Beep only when within 1-10 meters for high accuracy demo
                if (distance in 1.0..10.0) {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(300L) // Rapid beeping
                } else {
                    delay(1000L) // Slower check when out of range
                }
            }
        }
    }

    private fun stopBeeping() {
        beepJob?.cancel()
        beepJob = null
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
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

    /**
     * Triggers a critical SOS using a specific intent and mocked coordinates.
     */
    fun triggerSos(intent: String = "I'm trapped and bleeding") {
        viewModelScope.launch {
            // Compress intent string into 1-byte code
            val intentCode = compressor.compressIntent(intent)
            
            // In reality, get GPS coordinates here. Using a mock location for demo.
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
