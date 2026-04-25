package com.example.smartshort

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import android.provider.Settings
import android.view.MotionEvent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class ScreenshotService : Service() {

    private var executionId = 0
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null

    private var lastScreenshotId: Long = -1
    private lateinit var windowManager: WindowManager
    private lateinit var observer: ContentObserver

    private var lastScreenshotTime = 0L
    private var currentOverlay: View? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundServiceProperly()

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {

                val now = System.currentTimeMillis()

                // Prevent fast duplicate triggers
                if (now - lastScreenshotTime < 1500) return

                val currentId = getLatestScreenshotId()

                if (currentId == lastScreenshotId) return

                lastScreenshotTime = now
                lastScreenshotId = currentId

                Log.d("SMARTSHOT", "📸 Screenshot detected")

                executionId++
                val currentExecution = executionId

                pendingRunnable?.let { handler.removeCallbacks(it) }

                pendingRunnable = Runnable {

                    if (currentExecution != executionId) return@Runnable

                    val bitmap = getLatestScreenshot()

                    if (bitmap == null || !isValidBitmap(bitmap)) return@Runnable

                    showOverlay(bitmap)
                }

                handler.postDelayed(pendingRunnable!!, 700)
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
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SmartShot Running")
            .setContentText("Monitoring screenshots...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(1, notification)
    }

    private fun getLatestScreenshot(): Bitmap? {

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        cursor?.use {
            if (it.moveToFirst()) {

                val idIndex = it.getColumnIndex(MediaStore.Images.Media._ID)
                val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)

                // 🚨 Safety check
                if (idIndex == -1 || nameIndex == -1) return null

                val name = it.getString(nameIndex)

                // 🚨 Only process screenshots
                if (!name.lowercase().contains("screenshot")) return null

                val id = it.getLong(idIndex)

                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                return try {
                    val stream = contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(stream)
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }

    private fun isValidBitmap(bitmap: Bitmap?): Boolean {
        return bitmap != null && bitmap.width > 50 && bitmap.height > 50
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay(bitmap: Bitmap) {

        // Remove old overlay safely
        currentOverlay?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            currentOverlay = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) return

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)

            background = GradientDrawable().apply {
                setColor(0xCCFFFFFF.toInt())
                cornerRadius = 40f
            }

            elevation = 20f
        }

        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            layoutParams = LinearLayout.LayoutParams(400, 700).apply {
                bottomMargin = 25
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        fun createButton(text: String): Button {
            return Button(this).apply {
                this.text = text
                textSize = 16f
                setTextColor(0xFF000000.toInt())

                background = GradientDrawable().apply {
                    setColor(0xFFFFFFFF.toInt())
                    cornerRadius = 50f
                }

                elevation = 10f
                setPadding(20, 20, 20, 20)

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 15 }
            }
        }

        val scrollBtn = createButton("Scroll")
        val shareBtn = createButton("Share")

        shareBtn.setOnClickListener {
            Log.d("SMARTSHOT", "Share button clicked")
            Toast.makeText(this, "Opening share...", Toast.LENGTH_SHORT).show()
            shareImage(bitmap)
        }

        layout.addView(imageView)
        layout.addView(scrollBtn)
        layout.addView(shareBtn)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 300

        // 🔥 SINGLE CLEAN TOUCH SYSTEM
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        layout.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    val displayMetrics = resources.displayMetrics

                    val maxX = displayMetrics.widthPixels - layout.width
                    val maxY = displayMetrics.heightPixels - layout.height

                    // 🔥 FIRST calculate new position
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()

                    // 🔥 THEN clamp it (THIS IS THE FIX)
                    params.x = newX.coerceIn(0, maxX)
                    params.y = newY.coerceIn(0, maxY)

                    windowManager.updateViewLayout(layout, params)
                    true
                }

                MotionEvent.ACTION_UP -> {

                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels

                    val centerX = params.x + (layout.width / 2)

                    // 🔥 SNAP LOGIC
                    params.x = if (centerX < screenWidth / 2) {
                        0 // snap to left
                    } else {
                        screenWidth - layout.width // snap to right
                    }

                    windowManager.updateViewLayout(layout, params)

                    // 🔥 OPTIONAL: swipe to dismiss
                    val deltaX = event.rawX - initialTouchX
                    if (kotlin.math.abs(deltaX) > 300) {
                        removeOverlay()
                    }

                    true
                }

//                MotionEvent.ACTION_UP -> {
//                    val deltaX = event.rawX - initialTouchX
//
//                    if (kotlin.math.abs(deltaX) > 200) {
//                        removeOverlay()
//                    }
//                    true
//                }

                else -> false
            }
        }

        windowManager.addView(layout, params)
        currentOverlay = layout

        layout.alpha = 0f
        layout.scaleX = 0.8f
        layout.scaleY = 0.8f

        layout.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start()

        Handler(Looper.getMainLooper()).postDelayed({
            removeOverlay()
        }, 3000)
    }


    private fun shareImage(bitmap: Bitmap) {
        try {
            Log.d("SMARTSHOT", "Preparing image for sharing")

            val file = File(cacheDir, "screenshot.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()

            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file
            )

            Log.d("SMARTSHOT", "URI created: $uri")

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // 🔥 FIX IS HERE
            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(chooser)

        } catch (e: Exception) {
            Log.e("SMARTSHOT", "Share failed: ${e.message}")
        }
    }


    private fun removeOverlay() {
        try {
            currentOverlay?.let {
                windowManager.removeView(it)
                currentOverlay = null
            }
        } catch (_: Exception) {}
    }

    private fun getLatestScreenshotId(): Long {
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return -1
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(observer)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}