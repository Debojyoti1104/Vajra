package com.aegismesh.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aegismesh.core.models.MeshPacket
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*

@Composable
fun AegisMapView(
    packet: MeshPacket,
    userLocation: Pair<Double, Double>,
    modifier: Modifier = Modifier,
) {
    // Senders location from mesh packet
    val senderPos = LatLng(packet.latitude, packet.longitude)
    
    // User location from ViewModel
    val userPos = LatLng(userLocation.first, userLocation.second)
    val hasValidUserLoc = (userLocation.first != 0.0) && (userLocation.second != 0.0)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(senderPos, 15f)
    }

    // Automatically zoom to show both points if user location is found
    LaunchedEffect(hasValidUserLoc) {
        if (hasValidUserLoc) {
            val bounds = LatLngBounds.builder()
                .include(senderPos)
                .include(userPos)
                .build()
            // Add padding around the markers
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(bounds, 200),
            )
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = true,
            mapType = MapType.HYBRID, // Satellite view as requested
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            myLocationButtonEnabled = true,
        )
    ) {
        // Mark the Victim
        Marker(
            state = MarkerState(position = senderPos),
            title = "VICTIM LOCATION",
            snippet = "Emergency Code: ${packet.intentCode}"
        )

        // Draw path if we have both locations
        if (hasValidUserLoc) {
            Polyline(
                points = listOf(userPos, senderPos),
                color = Color.Cyan,
                width = 10f
            )
        }
    }
}
