package com.example.smartshort

import android.app.*
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenshotService : Service() {

    private lateinit var observer: ContentObserver
    private var lastScreenshotTime = 0L

    override fun onCreate() {
        super.onCreate()

        startForegroundServiceProperly()

        Log.d("SMARTSHOT", "Foreground Service started")

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Log.d("SMARTSHOT", "🔥 Background Change detected")

                uri?.let {
                    if (isScreenshot(it)) {

                        val now = System.currentTimeMillis()

                        // Prevent multiple triggers
                        if (now - lastScreenshotTime > 2000) {
                            lastScreenshotTime = now
                            Log.d("SMARTSHOT", "📸 Screenshot detected (REAL BACKGROUND)")
                        }
                    }
                }
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
    }

    private fun startForegroundServiceProperly() {
        val channelId = "smartshot_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SmartShot Service",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SmartShot Running")
            .setContentText("Monitoring screenshots...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun isScreenshot(uri: Uri): Boolean {
        val cursor = contentResolver.query(uri, null, null, null, null)

        cursor?.use {
            val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)

            if (nameIndex != -1 && it.moveToFirst()) {
                val fileName = it.getString(nameIndex)
                return fileName.lowercase().contains("screenshot")
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(observer)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}