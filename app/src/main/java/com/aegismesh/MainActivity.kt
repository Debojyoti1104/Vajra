package com.aegismesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aegismesh.ble.BleMeshManager
import com.aegismesh.core.service.MeshBackgroundService
import com.aegismesh.nlp.EmergencyCompressor
import com.aegismesh.ui.dashboard.MainDashboard
import com.aegismesh.ui.dashboard.MainViewModel
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    // Request permissions dynamically based on Android version
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startMeshService()
            viewModel.startMesh()
        } else {
            // Handle permission denial: in production, show a rationale dialog
        }
    }

    private lateinit var meshManager: BleMeshManager
    private lateinit var compressor: EmergencyCompressor

    // Provide dependencies to ViewModel (Manual Dependency Injection for simplicity)
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(meshManager, compressor) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSMDroid Configuration for Offline Maps
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        // Initialize Core Engines
        meshManager = BleMeshManager(this.applicationContext)
        compressor = EmergencyCompressor(this.applicationContext)

        // Set Compose UI
        setContent {
            Surface {
                val radioState by viewModel.radioState.collectAsState()
                val intercepts by viewModel.intercepts.collectAsState()
                val selectedPacket by viewModel.selectedPacket.collectAsState()
                val beepEnabled by viewModel.beepEnabled.collectAsState()
                val availableIntents = viewModel.availableIntents

                MainDashboard(
                    radioState = radioState,
                    incomingPackets = intercepts,
                    availableIntents = availableIntents,
                    selectedPacket = selectedPacket,
                    beepEnabled = beepEnabled,
                    onBeepToggle = { viewModel.toggleBeep(it) },
                    onPacketSelected = { viewModel.selectPacket(it) },
                    onHoldSos = { intent ->
                        viewModel.triggerSos(intent)
                    }
                )
            }
        }

        requestPermissions()
        checkBluetoothState()
    }

    private fun startMeshService() {
        val serviceIntent = Intent(this, MeshBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun requestPermissions() {
        viewModel.stopMesh() // Reset state before requesting

        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Android 12+ (API 31+) Bluetooth Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Android 13+ (API 33+) Notification Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    private fun checkBluetoothState() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            try {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(enableBtIntent)
            } catch (e: SecurityException) {
                // This can happen on Android 12+ if BLUETOOTH_CONNECT is not yet granted.
                // It will be handled once the user grants permissions and startMesh() is called.
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopMesh()
    }
}