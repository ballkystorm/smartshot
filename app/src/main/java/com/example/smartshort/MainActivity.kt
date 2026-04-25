package com.example.smartshort

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermission()

        Handler(Looper.getMainLooper()).postDelayed({
            startScreenshotService()
        }, 1000)
    }

//    override fun onResume() {
//        super.onResume()
//
//        Log.d("SMARTSHOT", "Observer started")
//
//        contentResolver.registerContentObserver(
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//            true,
//            object : ContentObserver(Handler(Looper.getMainLooper())) {
//
//                override fun onChange(selfChange: Boolean, uri: Uri?) {
//                    Log.d("SMARTSHOT", "Change detected")
//
//                    uri?.let {
//                        if (isScreenshot(it)) {
//                            Log.d("SMARTSHOT", "📸 Screenshot detected!")
//                        }
//                    }
//                }
//            }
//        )
//    }


    private fun startScreenshotService() {
        val intent = Intent(this, ScreenshotService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

//    private fun isScreenshot(uri: Uri): Boolean {
//        val cursor = contentResolver.query(uri, null, null, null, null)
//        cursor?.use {
//            val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
//
//            if (nameIndex != -1 && it.moveToFirst()) {
//                val fileName = it.getString(nameIndex)
//                Log.d("SMARTSHOT", "File: $fileName")
//
//                return fileName.lowercase().contains("screenshot")
//            }
//        }
//        return false
//    }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                100
            )
        }
    }
}