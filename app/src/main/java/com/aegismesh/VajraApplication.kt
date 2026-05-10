package com.aegismesh

import android.app.Application
import com.aegismesh.ble.BleMeshManager
import com.aegismesh.nlp.EmergencyCompressor

// Singleton container to share mesh data between Service and UI
class VajraApplication : Application() {

    lateinit var meshManager: BleMeshManager
    lateinit var compressor: EmergencyCompressor

    override fun onCreate() {
        super.onCreate()
        meshManager = BleMeshManager(this)
        compressor = EmergencyCompressor(this)
    }
}
