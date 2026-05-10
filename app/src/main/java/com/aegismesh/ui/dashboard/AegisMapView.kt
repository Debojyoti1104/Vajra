package com.aegismesh.ui.dashboard

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aegismesh.core.models.MeshPacket
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@Composable
fun AegisMapView(
    packet: MeshPacket,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val mapView = MapView(context)
            
            // Try to load bundled offline map of Assam
            try {
                val mapFile = prepareOfflineMap(context)
                if (mapFile.exists()) {
                    val offlineProvider = OfflineTileProvider(
                        org.osmdroid.tileprovider.util.SimpleRegisterReceiver(context),
                        arrayOf(mapFile)
                    )
                    mapView.tileProvider = offlineProvider
                    
                    // Set tile source from archive if available
                    val archives = offlineProvider.archives
                    if (archives.isNotEmpty()) {
                        val tileSourceName = archives[0].tileSources.firstOrNull() ?: "Mapnik"
                        mapView.setTileSource(FileBasedTileSource.getSource(tileSourceName))
                    }
                    Log.d("AegisMapView", "Loaded bundled offline map")
                }
            } catch (e: Exception) {
                Log.e("AegisMapView", "Failed to load offline map assets", e)
                mapView.setTileSource(TileSourceFactory.MAPNIK)
            }

            mapView.apply {
                setMultiTouchControls(true)
                controller.setZoom(18.0)
                val targetPoint = GeoPoint(packet.latitude, packet.longitude)
                controller.setCenter(targetPoint)

                val marker = Marker(this)
                marker.position = targetPoint
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = "SENDER LOCATION"
                marker.subDescription = "ID: ${packet.messageId.toUInt().toString(16).uppercase()}"
                overlays.add(marker)
            }
            mapView
        },
        update = { view ->
            val targetPoint = GeoPoint(packet.latitude, packet.longitude)
            view.controller.animateTo(targetPoint)
        }
    )
}

/**
 * Copies the pre-bundled Assam map archive from assets to internal storage 
 * so OSMDroid can access it as a File.
 */
private fun prepareOfflineMap(context: android.content.Context): File {
    val destinationFile = File(context.getExternalFilesDir(null), "assam_offline.sqlite")
    if (!destinationFile.exists()) {
        try {
            val inputStream: InputStream = context.assets.open("maps/assam.sqlite")
            val outputStream = FileOutputStream(destinationFile)
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
        } catch (e: Exception) {
            Log.e("AegisMapView", "Error copying map asset", e)
        }
    }
    return destinationFile
}
