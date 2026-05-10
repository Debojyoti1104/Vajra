package com.aegismesh.core.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aegismesh.MainActivity
import com.aegismesh.VajraApplication
import com.aegismesh.ble.BleMeshManager
import com.aegismesh.nlp.EmergencyCompressor
import kotlinx.coroutines.*

class MeshBackgroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private lateinit var meshManager: BleMeshManager
    private lateinit var compressor: EmergencyCompressor

    companion object {
        const val CHANNEL_ID = "MeshServiceChannel"
        const val NOTIFICATION_ID = 1
        const val EMERGENCY_CHANNEL_ID = "EmergencyAlerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        
        val app = application as VajraApplication
        meshManager = app.meshManager
        compressor = app.compressor
        
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        
        serviceScope.launch {
            meshManager.incomingPackets.collect { packet ->
                showEmergencyNotification(packet)
            }
        }
    }

    private fun createForegroundNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vajra Active")
            .setContentText("Listening for signals...")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun showEmergencyNotification(packet: com.aegismesh.core.models.MeshPacket) {
        val intentText = compressor.decompressIntent(packet.intentCode)
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("message_id", packet.messageId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, packet.messageId, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, EMERGENCY_CHANNEL_ID)
            .setContentTitle("EMERGENCY DETECTED")
            .setContentText(intentText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(packet.messageId, notification)
    }

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID, "Mesh Network Service",
            NotificationManager.IMPORTANCE_LOW
        )
        
        val emergencyChannel = NotificationChannel(
            EMERGENCY_CHANNEL_ID, "Emergency Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical alerts"
            enableLights(true)
            lightColor = android.graphics.Color.RED
            enableVibration(true)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(emergencyChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
