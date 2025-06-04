package com.np.wallpaperslider

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigInteger

class WallpaperPreviewActivity : ComponentActivity() {
    private lateinit var wallpaperRenderer: WallpaperRenderer
    private val handler = Handler(Looper.getMainLooper())
    private val drawRunner = Runnable { wallpaperRenderer.drawFrame() }
    private var isPreview = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_wallpaper_preview)

        // Set up the MaterialToolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.xml_toolbar)
        toolbar.title = "Wallpaper Preview"
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.setNavigationOnClickListener { finish() }

        // Initialize and start the wallpaper preview
        val surfaceView = findViewById<SurfaceView>(R.id.wallpaper_preview)
        val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        val size = prefs.getInt("imagesPathList_size", 0)
        val imagesArray = Array(size) { "" }
        for (i in 0 until size) {
            imagesArray[i] = prefs.getString("imagesPathList_$i", "") ?: ""
        }
        val slideDuration = getSharedPreferences("slideduration", Context.MODE_PRIVATE)
            .getInt("slideDuration", 5000)

        wallpaperRenderer = WallpaperRenderer(surfaceView.holder, imagesArray, slideDuration, isPreview)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                wallpaperRenderer.startRendering()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                wallpaperRenderer.updateSize(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                wallpaperRenderer.stopRendering()
            }
        })

        // Button click listeners
        findViewById<MaterialButton>(R.id.cancel_button).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.settings_button).setOnClickListener {
            showSettingsDialog()
        }

        findViewById<MaterialButton>(R.id.set_wallpaper_button).setOnClickListener {
            setWallpaper()
        }
    }

    private fun showSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Wallpaper Settings")
            .setMessage("Adjust slide duration:")
            .setPositiveButton("Set Duration") { _, _ ->
                val intent = Intent(this, DurationACtivity::class.java) // Replace with your settings activity
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra("slideDuration", wallpaperRenderer.slideDuration.toString())
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun setWallpaper() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, MyWallpaperService::class.java)
            )
            startActivityForResult(intent, 0)
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            Toast.makeText(this, "Wallpaper set successfully!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wallpaperRenderer.stopRendering()
        handler.removeCallbacks(drawRunner)
    }

    // Inner class to render the live wallpaper preview
    inner class WallpaperRenderer(
        private val holder: SurfaceHolder,
        private val imagesArray: Array<String>,
        var slideDuration: Int,
        private val isPreview: Boolean
    ) {
        private var running = false
        private var width = 0
        private var height = 0
        private var imagesArrayIndex = 0
        private val paint = Paint()

        fun startRendering() {
            running = true
            handler.post(drawRunner)
        }

        fun updateSize(width: Int, height: Int) {
            this.width = width
            this.height = height
        }

        fun stopRendering() {
            running = false
        }

        fun drawFrame() {
            if (!running) return
            val canvas = holder.lockCanvas() ?: return
            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                drawImage(canvas)
                imagesArrayIndex++
                if (imagesArrayIndex >= imagesArray.size) imagesArrayIndex = 0
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            handler.removeCallbacks(drawRunner)
            if (running) {
                handler.postDelayed(drawRunner, slideDuration.toLong())
            }
        }

        private fun drawImage(canvas: Canvas) {
            if (imagesArrayIndex < imagesArray.size) {
                try {
                    val inputStream = contentResolver.openInputStream(Uri.parse(imagesArray[imagesArrayIndex]))
                    var image = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    val imageHeight = (Math.ceil(image.height.toDouble() / 100) * 100).toInt()
                    val imageWidth = (Math.ceil(image.width.toDouble() / 100) * 100).toInt()
                    val GCD = BigInteger.valueOf(imageHeight.toLong())
                        .gcd(BigInteger.valueOf(imageWidth.toLong())).toInt()
                    val imageVerticalAspectRatio = imageHeight / GCD
                    val imageHorizontalAspectRatio = imageWidth / GCD

                    val containerWidth = width
                    val containerHeight = height
                    var bestFitScalingFactor = 0f
                    val precisionValue = 0.2f

                    while (imageHorizontalAspectRatio * bestFitScalingFactor <= containerWidth &&
                        imageVerticalAspectRatio * bestFitScalingFactor <= containerHeight) {
                        bestFitScalingFactor += precisionValue
                    }

                    val bestFitHeight = (imageVerticalAspectRatio * bestFitScalingFactor).toInt()
                    val bestFitWidth = (imageHorizontalAspectRatio * bestFitScalingFactor).toInt()
                    image = Bitmap.createScaledBitmap(image, bestFitWidth, bestFitHeight, true)

                    val leftPadding = ((containerWidth - image.width) / 2).toFloat()
                    val topPadding = ((containerHeight - image.height) / 2).toFloat()
                    canvas.drawBitmap(image, leftPadding, topPadding, null)
                } catch (e: Exception) {
                    Log.e("WallpaperPreview", "Error drawing image: ${e.message}", e)
                }
            }
        }

        fun updateSlideDuration(newDuration: Int) {
            slideDuration = newDuration
        }
    }
}