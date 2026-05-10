package com.aegismesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aegismesh.ui.dashboard.MainDashboard
import com.aegismesh.ui.dashboard.MainViewModel
import com.google.android.gms.location.LocationServices

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startMeshService()
            viewModel.startMesh()
        }
    }

    private val viewModel: MainViewModel by viewModels {
        val app = application as VajraApplication
        val locationClient = LocationServices.getFusedLocationProviderClient(this)
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(app.meshManager, app.compressor, locationClient) as T
                }
                throw IllegalArgumentException("Unknown ViewModel")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Surface {
                val radioState by viewModel.radioState.collectAsState()
                val intercepts by viewModel.intercepts.collectAsState()
                val selectedPacket by viewModel.selectedPacket.collectAsState()
                val userLocation by viewModel.userLocation.collectAsState()
                val beepEnabled by viewModel.beepEnabled.collectAsState()
                val availableIntents = viewModel.availableIntents

                MainDashboard(
                    radioState = radioState,
                    incomingPackets = intercepts,
                    availableIntents = availableIntents,
                    selectedPacket = selectedPacket,
                    userLocation = userLocation,
                    beepEnabled = beepEnabled,
                    onBeepToggle = { viewModel.toggleBeep(it) },
                    onPacketSelected = { viewModel.selectPacket(it) },
                    onHoldSos = { ctx, intent ->
                        viewModel.triggerSos(ctx, intent)
                    }
                )
            }
        }

        requestPermissions()
        checkBluetoothState()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getIntExtra("message_id", -1)?.takeIf { it != -1 }?.let { id ->
            viewModel.handleDeepLink(id)
        }
    }

    private fun startMeshService() {
        val serviceIntent = Intent(this, com.aegismesh.core.service.MeshBackgroundService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun requestPermissions() {
        viewModel.stopMesh()

        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    private fun checkBluetoothState() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            if (hasPermission) {
                try {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivity(enableBtIntent)
                } catch (_: SecurityException) {
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopMesh()
    }
}
