package com.aegismesh.ui.dashboard

import android.annotation.SuppressLint
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegismesh.core.models.MeshPacket
import com.aegismesh.core.service.MeshService
import com.aegismesh.core.service.RadioState
import com.aegismesh.nlp.EmergencyCompressor
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// Bridges between the BLE engine and the UI
class MainViewModel(
    private val meshService: MeshService,
    private val compressor: EmergencyCompressor,
    private val locationClient: FusedLocationProviderClient? = null,
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

    // real device location tracking
    private val _userLocation = MutableStateFlow(0.0 to 0.0)
    val userLocation: StateFlow<Pair<Double, Double>> = _userLocation.asStateFlow()

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private var beepJob: Job? = null

    private val _beepEnabled = MutableStateFlow(value = false)
    val beepEnabled: StateFlow<Boolean> = _beepEnabled.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                _userLocation.value = it.latitude to it.longitude
                Log.d("MainViewModel", "Updated location: ${it.latitude}, ${it.longitude}")
            }
        }
    }

    init {
        updateLocation()
        startLocationTracking()

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

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).build()
        locationClient?.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    fun updateLocation() {
        locationClient?.lastLocation?.addOnSuccessListener { location ->
            location?.let {
                _userLocation.value = it.latitude to it.longitude
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

                val currentLoc = _userLocation.value
                val results = FloatArray(1)
                Location.distanceBetween(
                    currentLoc.first, currentLoc.second,
                    target.latitude, target.longitude,
                    results,
                )
                val distance = results[0].toDouble()
                
                Log.d("MainViewModel", "Proximity distance: ${distance}m")

                // High accuracy requirement: Beep between 1m and 10m
                if ((distance in 1.0..10.0)) {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(300L) 
                } else if (distance < 100.0) {
                    // Approach warning
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                    delay(1000L)
                } else {
                    delay(2000L)
                }
            }
        }
    }

    private fun stopBeeping() {
        beepJob?.cancel()
        beepJob = null
    }

    @SuppressLint("MissingPermission")
    fun triggerSos(context: android.content.Context, intent: String = "I'm trapped and bleeding") {
        val intentCode = compressor.compressIntent(intent)
        val currentLoc = _userLocation.value
        
        if (currentLoc.first == 0.0 || currentLoc.second == 0.0) {
            Toast.makeText(context, "Waiting for GPS fix...", Toast.LENGTH_SHORT).show()
            updateLocation()
            return
        }

        viewModelScope.launch {
            meshService.sendSOS(
                intentCode = intentCode,
                lat = currentLoc.first,
                lon = currentLoc.second
            )
            Toast.makeText(context, "SOS Sent!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationClient?.removeLocationUpdates(locationCallback)
    }
}
