package com.aegismesh.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aegismesh.core.models.MeshPacket
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun AegisMapView(
    packet: MeshPacket,
    modifier: Modifier = Modifier
) {
    // Senders location from mesh packet
    val senderPos = LatLng(packet.latitude, packet.longitude)
    
    // User location (Mocked for demo)
    val userPos = LatLng(37.7749, -122.4194)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(senderPos, 15f)
    }

    // Google Maps is fully integrated within the app UI
    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            myLocationButtonEnabled = true
        )
    ) {
        // mark the victim
        Marker(
            state = MarkerState(position = senderPos),
            title = "VICTIM",
            snippet = "ID: ${packet.messageId.toUInt().toString(16).uppercase()}"
        )

        // mark the responder
        Marker(
            state = MarkerState(position = userPos),
            title = "YOU"
        )

        // Visual path between responder and victim
        Polyline(
            points = listOf(userPos, senderPos),
            color = Color.Cyan,
            width = 8f
        )
    }
}
