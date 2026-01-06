package com.np.wallpaperslider


import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.Context

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler

import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.np.wallpaperapp.MyPoint
import java.math.BigInteger

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection

import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import java.io.File
import kotlin.math.max
import androidx.core.net.toUri
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import androidx.core.graphics.createBitmap
import kotlin.math.min


class MyWallpaperService : WallpaperService() {
    private var engine: MyWallpaperEngine? = null
    private var messenger: Messenger? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            messenger = Messenger(service)
            isBound = true
            AppLogger.d("MyWallpaperService", "Bound to WallpaperForegroundService${engine?.isPreview()}")
            if (engine?.isPreview() == true) {
                sendMessage(WallpaperForegroundService.MSG_REMOVE_NOTIFICATION)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            messenger = null
            isBound = false
            AppLogger.d("MyWallpaperService", "Unbound from WallpaperForegroundService${engine?.isPreview()}")
        }
    }

    override fun onCreate() {
        super.onCreate()

      //  if (!isPreviewSet()) {

            bindService(
                Intent(this, WallpaperForegroundService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
       // }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d("MyWallpaperService", "onStartCommand: isWallpaperSet=${iswallpaperSet()}, isPreview=${engine?.isPreview()}")
        return START_NOT_STICKY
    }

    private fun iswallpaperSet(): Boolean {
        try {
            val wpm = WallpaperManager.getInstance(applicationContext)
            val info = wpm.wallpaperInfo

            if (info != null && info.packageName == applicationContext.packageName) {
                AppLogger.d("MyWallpaperService", "We're already running")
                return true
            } else {
                AppLogger.d("MyWallpaperService", "We're not running")
                return false
            }
        }catch (e: Exception)
        {
            AppLogger.e("bitmappos....", e.message.toString(), e)
        }
        return false
    }

    private fun isPreviewSet(): Boolean {
        // Simple check to see if the engine is running in preview mode
        return engine?.isPreview() == true
    }

    override fun onDestroy() {
        // Only send the remove message and unbind if we are the active wallpaper service
        if (iswallpaperSet() && !isPreviewSet()) {

            sendMessage(WallpaperForegroundService.MSG_REMOVE_NOTIFICATION)
            if (isBound) {
                unbindService(connection)
                isBound = false
            }
        }
        super.onDestroy()

    }

    private fun sendMessage(messageType: Int) {
        if (!isBound || messenger == null) {
            AppLogger.w("MyWallpaperService", "Not bound to service, cannot send message: $messageType")
            return
        }
        try {
            val msg = Message.obtain(null, messageType)
            messenger?.send(msg)
            AppLogger.d("MyWallpaperService", "Sent message: $messageType")
        } catch (e: Exception) {
            AppLogger.e("MyWallpaperService", "Error sending message: ${e.message}", e)
        }
    }

    override fun onCreateEngine(): WallpaperService.Engine {
        engine = MyWallpaperEngine()
        return engine!!
    }

    @Suppress("DEPRECATION")
    private inner class MyWallpaperEngine : WallpaperService.Engine() {
        private val handler = Handler()
        private val keyguardManager: KeyguardManager by lazy {
            applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        }
        // --- NEW STATE VARIABLES FOR SCREEN DETECTION ---
        private var lastOffsetUpdateTime: Long = 0
        // Flag set to true whenever onOffsetsChanged is called
        private var isHomeVisibleByOffset: Boolean = false
        private var offsetResetTask: Runnable? = null
        private val OFFSET_RESET_DELAY = 100L
        // Time window to consider the Home screen active after the last scroll event
        private val HOME_OFFSET_TIMEOUT = 500L
        private val drawRunner = Runnable { drawFrame() }
        private var imagesArray = arrayOf<String>()

        private var imagesArrayIndex = 0
        private var homeSlideIndex = 0 // Independent index for home screen slideshow
        private var lockSlideIndex = 0 // Independent index for lock screen slideshow

        // This variable is temporary, used to pass the current image index to drawImage
        private var currentDrawingIndex = 0
        private var slideDuration:Int = 1000
        private val circles: MutableList<MyPoint>
        private val paint = Paint()
        private var width: Int = 0
        internal var height: Int = 0
        private var visible = true
        private val maxNumber: Int
        private val touchEnabled: Boolean

        // Use a property to store the flags once they are safely retrieved (from SafeWallpaperEngine.kt logic)
        private var currentWallpaperFlags: Int = 0

        val wm = applicationContext?.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
        val display = wm?.defaultDisplay
        var flagtype = ""
        private var wallpaperManager: WallpaperManager? = null
        val buttonRect = RectF(100f, 100f, 300f, 200f)
        val myButtonPaint = Paint()
        var rawHomeImages = mutableListOf<String>()
        var rawLockImages = mutableListOf<String>()
        var rawBothImages = mutableListOf<String>()

        // NEW: These are the final filtered lists used for drawing
        private var finalHomeImages = emptyList<String>()
        private var finalLockImages = emptyList<String>()
        private val PROMPT_WALLPAPER_SHOWN_KEY = "prompt_wallpaper_shown"
        val shouldPrompt :Boolean
        val prefs1 = getSharedPreferences(PROMPT_WALLPAPER_SHOWN_KEY, Context.MODE_PRIVATE)
        //var settingsicon : Bitmap?
        //var scaledsettingsicon : Bitmap
        init {
            // val prefs = PreferenceManager.getDefaultSharedPreferences(this@MyWallpaperService)


            //imagesArray = loadArray("imagesPathList", applicationContext)

            maxNumber = Integer
                .valueOf((imagesArray.size).toString())
            touchEnabled = false//prefs.getBoolean("touch", false)
            circles = ArrayList()
            width = display?.width ?: 0
            height = display?.height ?: 0
            wallpaperManager = WallpaperManager.getInstance(applicationContext)


            myButtonPaint.isAntiAlias = true
            myButtonPaint.color = Color.WHITE
            myButtonPaint.style = Paint.Style.STROKE
            myButtonPaint.strokeJoin = Paint.Join.ROUND
            myButtonPaint.strokeWidth = 10f



            //settingsicon = ContextCompat.getDrawable(applicationContext, R.drawable.ic_crop)?.toBitmap()
            //settingsicon = BitmapFactory.decodeResource(resources, R.drawable.interval)
            //scaledsettingsicon = Bitmap.createScaledBitmap(settingsicon!!, 200, 200, true)


            val prefs = getSharedPreferences("slideduration", Context.MODE_PRIVATE)
            slideDuration = prefs.getInt("slideDuration", 5000)

            shouldPrompt = prefs1.getBoolean(PROMPT_WALLPAPER_SHOWN_KEY, true)
            loadImagesByCategory()


        }

        private fun loadImagesByCategory() {
            // No need to update flags here, it's done in onSurfaceCreated

            runBlocking {
                withContext(Dispatchers.IO) {
                    // Load all base lists from shared preferences
                    rawHomeImages = loadImageList("homeImages").toMutableList()
                    rawLockImages = loadImageList("lockImages").toMutableList()
                    rawBothImages = loadImageList("bothImages").toMutableList()

                    // 1. Create the final HOME list (Home images + Both images)
                    val homeList = mutableListOf<String>()
                    homeList.addAll(rawHomeImages)
                    homeList.addAll(rawBothImages)
                    finalHomeImages = homeList.distinct()

                    // 2. Create the final LOCK list (Lock images + Both images)
                    val lockList = mutableListOf<String>()
                    lockList.addAll(rawLockImages)
                    lockList.addAll(rawBothImages)
                    finalLockImages = lockList.distinct()

                    // Log the final counts
                    AppLogger.d("MyWallpaperService", "loadImagesByCategory Final HOME images loaded: ${finalHomeImages.size}")
                    AppLogger.d("MyWallpaperService", "loadImagesByCategory Final LOCK images loaded: ${finalLockImages.size}")

                }
            }
        }

        private fun loadImageList(arrayName: String): List<String> {
            val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
            val size = prefs.getInt("${arrayName}_size", 0)
            AppLogger.d("MyWallpaperService", "Loading $arrayName, size: $size")
            val images = mutableListOf<String>()
            for (index in 0 until size) {
                prefs.getString("${arrayName}_$index", null)?.let { path ->
                    if (path.isNotEmpty()) {
                        try {
                            contentResolver.openInputStream(Uri.parse(path))?.close()
                            images.add(path)
                            AppLogger.d("MyWallpaperService", "loadImageList Valid URI for $arrayName[$index]: $path")
                        } catch (e: Exception) {
                            AppLogger.w("MyWallpaperService", "loadImageList Invalid URI for $arrayName[$index]: $path, error: ${e.message}")
                        }
                    }
                }
            }
            return images
        }

        fun loadArray(arrayName: String, context: Context): Array<String> {
            val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
            val size = prefs.getInt("${arrayName}_size", 0)

            val array = Array(size) { "" }
            for (i in 0 until size) {
                array[i] = prefs.getString("${arrayName}_$i", null) ?: ""

            }

            return array
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            AppLogger.d("MyWallpaperService", "in oncreate surfaceHolder")
            // Initial setup here.
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            if (!isPreview()) {
                // This is the REAL wallpaper starting.
                // Tell the foreground service to show the notification now.
                sendMessage(WallpaperForegroundService.MSG_SHOW_NOTIFICATION)
            }
            // 1. Check and set the flags safely here
            safeUpdateWallpaperFlags()
            if(isPreview())
            {
                if (shouldPrompt) {
                    // Show guidance Toast (once)
                    if(finalHomeImages.isNotEmpty() && finalLockImages.isEmpty()) {
                        Toast.makeText(
                            applicationContext,
                            "Please set Wallpaper to Home screen.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else if(finalHomeImages.isEmpty() && finalLockImages.isNotEmpty()) {
                        Toast.makeText(
                            applicationContext,
                            "Please set Wallpaper to Lock screen.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else if(finalHomeImages.isNotEmpty() && finalLockImages.isNotEmpty()) {
                        Toast.makeText(
                            applicationContext,
                            "Please set Wallpaper to Home screen and Lock screen.",
                            Toast.LENGTH_LONG
                        ).show()
                    }


                    // Mark as shown so it doesn't repeat on subsequent calls/resumes
                    prefs1.edit().putBoolean(PROMPT_WALLPAPER_SHOWN_KEY, false).apply()
                }
                else {
                    // Our wallpaper is set. Reset the prompt flag so it will show next time if the user unsets it.
                    prefs1.edit().putBoolean(PROMPT_WALLPAPER_SHOWN_KEY, true).apply()
                }
            }
            AppLogger.d("MyWallpaperService", "onSurfaceCreated Flags after surface created: $currentWallpaperFlags")
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible

            // Check if either list has images before starting the draw runner
            val hasImages = finalHomeImages.isNotEmpty() || finalLockImages.isNotEmpty()
            AppLogger.d("MyWallpaperService", "in onVisibilityChanged $visible hasImages $hasImages")
            if (visible && hasImages) {
                loadImagesByCategory() // Reload data just in case settings changed
                drawFrame()
            } else {
                handler.removeCallbacks(drawRunner)
                // Also remove the offset reset task if we lose visibility
                offsetResetTask?.let { handler.removeCallbacks(it) }
                isHomeVisibleByOffset = false
                /*if (isPreview) {

                    try {
                        wallpaperManager?.forgetLoadedWallpaper()
                    } catch (e: Exception) {
                        Log.e("WallpaperService", "Error clearing wallpaper: ${e.message}", e)
                    }
                }*/
            }


        }

        /**
         * Safely attempts to call the new Android 14 API method (API 34+).
         */
        private fun safeUpdateWallpaperFlags() {

            // Set both home and lock screen wallpapers
            currentWallpaperFlags = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            // Note: getWallpaperFlags() is only reliable in a full, running engine,
            // not necessarily in the preview.
            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                // Call the API inside a try/catch block as a further safety net
                currentWallpaperFlags = getWallpaperFlags()
            } catch (e: NullPointerException) {
                // Log the error but continue. This handles the internal framework bug.
                AppLogger.e("MyLiveEngine", "NPE on getWallpaperFlags() in API 34+!", e)
                // Fallback: Assume both screens or use the previous flag state
                currentWallpaperFlags = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            //}
            AppLogger.d("MyLiveEngine", "Updated wallpaper flags to: $currentWallpaperFlags")
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            AppLogger.d("MyWallpaperService", "in onSurfaceDestroyed")
            this.visible = false
            handler.removeCallbacks(drawRunner)
            offsetResetTask?.let { handler.removeCallbacks(it) }
            isHomeVisibleByOffset = false
            if(isPreview)
            {
                try {
                    wallpaperManager?.forgetLoadedWallpaper()
                    AppLogger.d("WallpaperService", "Cleared wallpaper cache in onSurfaceDestroyed")
                } catch (e: Exception) {
                    AppLogger.e("WallpaperService", "Error clearing wallpaper: ${e.message}", e)
                }
            }
            super.onSurfaceDestroyed(holder)
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder, format: Int,
            width: Int, height: Int
        ) {
            this.width = width
            this.height = height
            // IMPORTANT: Reload data here to catch settings changes when surface recreates (e.g., orientation change)
            loadImagesByCategory()
            AppLogger.d("MyWallpaperService", "in onSurfaceChanged")
            super.onSurfaceChanged(holder, format, width, height)
        }

        // --- KEY IMPLEMENTATION FOR HOME SCREEN DETECTION ---
        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            super.onOffsetsChanged(xOffset, yOffset, xStep, yStep, xPixels, yPixels)

            // 1. Set the flag: We are definitely interacting with or viewing the Home Screen.
            if (!isHomeVisibleByOffset) {
                isHomeVisibleByOffset = true
                AppLogger.d("WP_OFFSET", "Offset change started. isHomeVisibleByOffset=true")
            }

            // 2. Clear any pending reset task.
            offsetResetTask?.let { handler.removeCallbacks(it) }

            // 3. Schedule the reset task. This task will run 100ms after the LAST onOffsetsChanged call,
            // ensuring the flag is reset quickly after scrolling stops.
            offsetResetTask = Runnable {
                isHomeVisibleByOffset = false
                AppLogger.d("WP_OFFSET", "Offset reset timeout (100ms). isHomeVisibleByOffset=false")
            }
            handler.postDelayed(offsetResetTask!!, OFFSET_RESET_DELAY)
        }


        override fun onTouchEvent(event: MotionEvent) {
            if (touchEnabled) {

                val x = event.x
                val y = event.y
                val holder = surfaceHolder
                var canvas: Canvas? = null

                canvas = holder.lockCanvas()
                if (canvas != null) {
                    if (imagesArrayIndex >= imagesArray.size) {
                        imagesArrayIndex = 0
                    }
                    //  drawImage(canvas)
                    imagesArrayIndex++


                }

                if (canvas != null)
                    holder.unlockCanvasAndPost(canvas)

                super.onTouchEvent(event)
            }
            if (event.action == MotionEvent.ACTION_UP) {
                // Check if the touch coordinates are within the button area
                if (buttonRect.contains(event.x, event.y)) {
                    // Launch your activity or perform an action
                    // (e.g., open settings)


                }
            }

            val x = event.x
            val y = event.y

            val bitmapXPosition = (3 * width) / 4.toFloat()
            val bitmapYPosition = (3 * height) / 4.toFloat()
            //val bitmapWidth = scaledsettingsicon.width
            //val bitmapHeight = scaledsettingsicon.height
            if(isPreview){
                when (event.action) {
                    MotionEvent.ACTION_UP -> {

                        //Check if the x and y position of the touch is inside the bitmap
                        /*if (x > bitmapXPosition && x < bitmapXPosition + bitmapWidth && y > bitmapYPosition && y < bitmapYPosition + bitmapHeight) {
                            //Bitmap touched
                            // Toast.makeText(applicationContext, "....kkkkkkk....", Toast.LENGTH_LONG).show()
                            try {
                                notifyForegroundServiceToLaunchActivity(applicationContext)
                                /*val i = Intent(applicationContext, DurationACtivity::class.java)
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                i.putExtra("slideDuration", slideDuration.toString())
                                startActivity(i)*/
                                /*val i = Intent(applicationContext, CropActivity::class.java)
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                i.putExtra("imageindex", (imagesArrayIndex - 1).toString())
                                i.putExtra("frompage", "service")
                                startActivity(i)*/
                                //setDuration(applicationContext)
                            } catch (e: Exception) {

                                Log.e("TAGGGGG33....", e.message, e)
                            }
                        }*/

                        //return true
                    }

                    MotionEvent.ACTION_DOWN -> {

                    }
                }
                // return false
            }
        }

        /* fun notifyForegroundServiceToLaunchActivity(context: Context) {
             val intent = Intent("com.np.wallpaperslider.LAUNCH_SETTINGS")
             context.sendBroadcast(intent) // Broadcast to the foreground service
         }*/

        fun getBitmapFromPath(filePath: String): Bitmap? {
            val imageFile = File(filePath)
            if (!imageFile.exists()) {
                return null
            }
            return BitmapFactory.decodeFile(imageFile.absolutePath)
        }


        /**
         * Draws the current image from the imagesArray onto the provided Canvas,
         * scaling it to fit within the canvas dimensions while maintaining its aspect ratio,
         * and centering it.
         *
         * @param canvas The Canvas object to draw upon.
         */
        private fun drawImage(canvas: Canvas, imageList: List<String>, index: Int) {
            // Step 1: Clear the entire canvas to transparent. This ensures that
            // previous drawings are removed before drawing the new frame.
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            AppLogger.d("Wallpaperservice","drawImage index:$index,imageList.size:${imageList.size}")
            // Step 2: Check if there are images available and if the current index is valid.
            if (index < imageList.size) {
                try {
                    // Step 3: Open an input stream from the image URI.
                    val imageUri = imageList[index].toUri()
                    val inputStream = applicationContext.contentResolver.openInputStream(imageUri)

                    // Step 4: Decode the image from the input stream into a Bitmap.
                    // It's important to use 'var' for 'image' as we will reassign it after scaling.
                    var image: Bitmap? = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close() // Ensure the input stream is closed immediately to release resources.

                    // Step 5: Handle cases where the image decoding fails (e.g., corrupted file, invalid URI).
                    if (image == null) {
                        AppLogger.e("MyWallpaperService", "Failed to decode image at index $imagesArrayIndex. URI: $imageUri")
                        // Fallback: draw a black background to indicate an error.
                        canvas.drawColor(ContextCompat.getColor(applicationContext, R.color.purple_200))
                        return // Exit the function as there's no image to draw.
                    }

                    // Step 6: Get the dimensions of the canvas (the drawing surface).
                    // These are the target dimensions for fitting the image.

                    var canvasWidth = canvas.width
                    var canvasHeight = canvas.height

                    val imageRatio = image.width.toFloat() / image.height
                    val canvasRatio = canvas.width.toFloat() / canvas.height
                    val ratioDiff = kotlin.math.abs(imageRatio - canvasRatio)
                    val useCover = ratioDiff < 0.35f

                    // Step 7: Calculate the scaling factors.
                    // We determine how much to scale the image to fill its width or height
                    // into the canvas's width or height respectively.
                    val scaleX = canvasWidth.toFloat() / image.width.toFloat()
                    val scaleY = canvasHeight.toFloat() / image.height.toFloat()

                    val maxCrop = 1.25f
                    val coverScale = max(scaleX, scaleY)
                    val containScale = min(scaleX, scaleY)

                    val scale = min(coverScale, containScale * maxCrop)

                    // Step 8: Determine the overall scaling factor for 'cover' behavior.
                    // To make the image cover the entire canvas, we use the LARGER of the
                    // two scaling factors (scaleX or scaleY). This will ensure that at least
                    // one dimension (width or height) of the scaled image matches the canvas,
                    // and the other will be larger, allowing for cropping.
                    /*val scaleFactor = if (useCover) {
                        max(scaleX, scaleY)
                    }
                    else{
                        min(scaleX, scaleY)
                    }*/
                    val scaleFactor = max(scaleX, scaleY)
                    // Step 9: Calculate the new dimensions of the image after scaling.
                    val scaledWidth = (image.width * scaleFactor).toInt()
                    val scaledHeight = (image.height * scaleFactor).toInt()

                    //val scaledWidth = (image.width * scale).toInt()
                    //val scaledHeight = (image.height * scale).toInt()

                    // Step 10: Create a new scaled Bitmap.
                    // `createScaledBitmap` will return a new Bitmap, and the original `image`
                    // can then be recycled if it's no longer needed.
                    image = image.scale(scaledWidth, scaledHeight)

                    // Step 11: Calculate padding (or offset) to center the scaled image on the canvas.
                    // Since the image might be larger than the canvas in one dimension, these
                    // paddings can be negative, effectively offsetting the image to center the visible part.
                    val leftPadding = (canvasWidth - scaledWidth) / 2f
                    val topPadding = (canvasHeight - scaledHeight) / 2f

                    // Step 12: Draw the background color.
                    // This will fill the entire canvas behind the image.
                    canvas.drawColor(ContextCompat.getColor(applicationContext, R.color.purple_200))

                    // Step 13: Draw the scaled and centered image onto the canvas.
                    canvas.drawBitmap(image, leftPadding, topPadding, null)



                } catch (e: Exception) {
                    // Step 14: Catch and log any exceptions that occur during the image processing.
                    AppLogger.e("MyWallpaperService", "Error drawing image at index $index: ${e.message}", e)
                    // Fallback: draw a default background color on error to prevent a blank screen.
                    canvas.drawColor(ContextCompat.getColor(applicationContext, R.color.purple_200))
                }
            } else {
                // Step 15: If there are no images in the array or the index is out of bounds,
                // log a warning and draw a default background.
                AppLogger.w("MyWallpaperService", "No images to draw or imagesArrayIndex out of bounds. Using default background.")
                canvas.drawColor(ContextCompat.getColor(applicationContext, R.color.purple_200))
            }
        }




        @SuppressLint("SuspiciousIndentation")
        private fun drawFrame() {
            AppLogger.d("Wallpaperservice","drawFrame")
            safeUpdateWallpaperFlags() // Get the most current flags before drawing
            // Check which display context the engine is active for
            val prefs = getSharedPreferences("previewwall", Context.MODE_PRIVATE)
            var previewwall = prefs.getString("previewwall", "all")


            var targetList: List<String> = emptyList()
            var currentIndex: Int = -1
            var targetIndexRef: String? = null // Used to track which index variable to advance

            if (isPreview()) {
                AppLogger.i("WP_SCREEN", "Active Screen: PREVIEW MODE (Preference-based)")


                when (previewwall) {
                    "lock" -> {
                        targetList = finalLockImages
                        currentIndex = lockSlideIndex
                        targetIndexRef = "LOCK"
                        AppLogger.d("WP_SCREEN", "Previewing LOCK screen.")
                    }

                    "home" -> {
                        targetList = finalHomeImages
                        currentIndex = homeSlideIndex
                        targetIndexRef = "HOME"
                        AppLogger.d("WP_SCREEN", "Previewing HOME screen.")
                    }

                    "all" -> {
                        // In "all" preview mode, default to HOME or handle as you wish
                        if(finalHomeImages.isNotEmpty())
                        {
                            targetList = finalHomeImages
                            currentIndex = homeSlideIndex
                            targetIndexRef = "HOME"
                            AppLogger.d("WP_SCREEN", "Previewing ALL/Default to HOME screen.")
                        }
                        else
                        {
                            targetList = finalLockImages
                            currentIndex = lockSlideIndex
                            targetIndexRef = "LOCK"
                            AppLogger.d("WP_SCREEN", "Previewing ALL/Default to LOCK screen.")
                        }
                    }

                    else -> {
                        targetList = finalHomeImages
                        currentIndex = homeSlideIndex
                        targetIndexRef = "HOME"
                    }
                }
            }
            else
            {
                val isSetToSystem = (currentWallpaperFlags and WallpaperManager.FLAG_SYSTEM) != 0
                val isSetToLock = (currentWallpaperFlags and WallpaperManager.FLAG_LOCK) != 0

                AppLogger.d("WallpaperService", "isSetToLock $isSetToLock   isSetToSystem $isSetToSystem")
                val isDeviceLocked = try {
                    keyguardManager.isDeviceLocked || keyguardManager.isKeyguardLocked
                } catch (e: Exception) {
                    // Handle cases where KeyguardManager is not accessible (e.g., specific Android variants)
                    AppLogger.e("WP_SCREEN", "Error checking KeyguardManager", e)
                    false
                }
                /*
                if (isSetToLock && isDeviceLocked && finalLockImages.isNotEmpty()) {
                     targetList = finalLockImages
                    currentIndex = lockSlideIndex
                    targetIndexRef = "LOCK"
                    Log.i("WP_SCREEN", "Active Screen: LOCK (Keyguard Manager Check)")
                }
                else if (isSetToSystem && !isSetToLock && finalHomeImages.isNotEmpty() && finalLockImages.isEmpty()) {
                    targetList = finalHomeImages
                    currentIndex = homeSlideIndex
                    targetIndexRef = "HOME"
                    Log.i("WP_SCREEN", "Active Screen: HOME ONLY (Using Home List)")
                }
                else if (isSetToSystem && isHomeVisibleByOffset && finalHomeImages.isNotEmpty()) {
                    targetList = finalHomeImages
                    currentIndex = homeSlideIndex
                    targetIndexRef = "HOME"
                    Log.i("WP_SCREEN", "Active Screen: HOME (Offset Active)")
                } else if (isSetToSystem && finalHomeImages.isNotEmpty()) {
                    targetList = finalHomeImages
                    currentIndex = homeSlideIndex
                    targetIndexRef = "HOME"
                    Log.i("WP_SCREEN", "Active Screen: HOME (Final Default)")
                } else if (isSetToSystem && finalHomeImages.isEmpty() && finalLockImages.isNotEmpty()) {
                    targetList = finalLockImages
                    currentIndex = lockSlideIndex
                    targetIndexRef = "LOCK"
                    Log.i("WP_SCREEN", "Active Screen: HOME (Final Default)")
                } else {
                    Log.w("WallpaperService", "No active screen or image lists found. Stopping runner.")
                    handler.removeCallbacks(drawRunner)
                    return
                }*/
                when {
                    // 1. LOCK SCREEN PRIORITY: Highest priority, ONLY if the device is locked
                    //    AND we actually have images for the Lock Screen.
                    isDeviceLocked && isSetToLock && finalLockImages.isNotEmpty() -> {
                        targetList = finalLockImages
                        currentIndex = lockSlideIndex
                        targetIndexRef = "LOCK"
                        AppLogger.i("WP_SCREEN", "Active Screen: LOCK (Device Locked)")
                    }

                    // 2. HOME SCREEN PRIORITY: Use Home list if we are not locked OR
                    //    if the service is running for the Home Screen and we have Home images.
                    isSetToSystem && finalHomeImages.isNotEmpty() -> {
                        if (isHomeVisibleByOffset) {
                            // Priority 2a: Home Screen with active interaction (Scrolling)
                            targetList = finalHomeImages
                            currentIndex = homeSlideIndex
                            targetIndexRef = "HOME"
                            AppLogger.i("WP_SCREEN", "Active Screen: HOME (Offset Active)")
                        } else {
                            // Priority 2b: Home Screen Default (unlocked, static view)
                            targetList = finalHomeImages
                            currentIndex = homeSlideIndex
                            targetIndexRef = "HOME"
                            AppLogger.i("WP_SCREEN", "Active Screen: HOME (Default View)")
                        }
                    }

                    // 3. FALLBACK: If we passed 1 & 2, but the Lock flag is set and we have Lock images,
                    //    use the Lock list (covers Lock-Only case when unlocked, but only if Home list is empty).
                    isSetToLock && finalLockImages.isNotEmpty() -> {
                        targetList = finalLockImages
                        currentIndex = lockSlideIndex
                        targetIndexRef = "LOCK"
                        AppLogger.i("WP_SCREEN", "Active Screen: LOCK (Fallback/Home List Empty)")
                    }

                    // 4. FINAL FAILURE: No lists or contexts match
                    else -> {
                        AppLogger.w("WallpaperService", "No active screen or image lists found. Stopping runner.")
                        handler.removeCallbacks(drawRunner)
                        return
                    }
                }
            }

            if (!visible || targetList.isEmpty()) {
                AppLogger.w("WallpaperService", "Skipping draw: visible=$visible, target list empty.")
                handler.removeCallbacks(drawRunner)
                return
            }

            val holder: SurfaceHolder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {

                    // Draw the image using the dynamically selected list and index
                    drawImage(canvas, targetList, currentIndex)
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                        when (targetIndexRef) {
                            "LOCK" -> {
                                lockSlideIndex = (lockSlideIndex + 1) % finalLockImages.size
                                AppLogger.d("WallpaperService", "Lock index advanced to $lockSlideIndex")
                            }
                            "HOME" -> {
                                homeSlideIndex = (homeSlideIndex + 1) % finalHomeImages.size
                                AppLogger.d("WallpaperService", "Home index advanced to $homeSlideIndex")
                            }
                            else -> {}
                        }
                    } catch (e: Exception) {
                        AppLogger.e("WallpaperService", "Error unlocking canvas or advancing index: ${e.message}", e)
                    }
                }
            }

            // 3. Reschedule the runnable
            handler.removeCallbacks(drawRunner)
            if (visible && targetList.isNotEmpty()) {
                handler.postDelayed(drawRunner, slideDuration.toLong())
            }
        }



        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean
        ): Bundle? {
            AppLogger.d("WallpaperService", "onCommand called with action: $action, isPreview: $isPreview")
            if (action == "wallpaper_set") {
                AppLogger.d("WallpaperService", "Wallpaper set command received")
                if (isPreview()) {
                    // Stop drawing and clean up preview engine
                    handler.removeCallbacks(drawRunner)
                    offsetResetTask?.let { handler.removeCallbacks(it) }
                    isHomeVisibleByOffset = false
                    try {
                        wallpaperManager?.forgetLoadedWallpaper()
                        AppLogger.d("WallpaperService", "Cleared wallpaper cache")
                    } catch (e: Exception) {
                        AppLogger.e("WallpaperService", "Error clearing wallpaper: ${e.message}", e)
                    }
                    // Force preview engine to stop
                    visible = false
                    notifyWallpaperSet()

                }
                else
                {
                    sendMessage(WallpaperForegroundService.MSG_SHOW_NOTIFICATION)
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        // Helper to notify system of wallpaper change
        private fun notifyWallpaperSet() {

            try {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                applicationContext.sendBroadcast(intent)
                AppLogger.d("WallpaperService", "Broadcast ACTION_WALLPAPER_CHANGED")
            } catch (e: Exception) {
                AppLogger.e("WallpaperService", "Error broadcasting wallpaper change: ${e.message}", e)
            }
        }



        private fun draw() {
            val holder = surfaceHolder
            var canvas: Canvas? = null

            canvas = holder.lockCanvas()
            if (canvas != null) {
                if (circles.size >= maxNumber) {
                    circles.clear()
                }
                val x = (width * Math.random()).toInt()
                val y = (height * Math.random()).toInt()
                circles.add(
                    MyPoint(
                        (circles.size + 1).toString(),
                        x.toFloat(), y.toFloat()
                    )
                )
                drawCircles(canvas, circles)
            }

            if (canvas != null)
                holder.unlockCanvasAndPost(canvas)

            handler.removeCallbacks(drawRunner)
            if (visible) {
                handler.postDelayed(drawRunner, slideDuration.toLong())
            }
        }

        // Surface view requires that all elements are drawn completely
        private fun drawCircles(canvas: Canvas, circles: List<MyPoint>) {
            canvas.drawColor(Color.BLACK)
            for (point in circles) {
                canvas.drawCircle(point.x, point.y, 20.0f, paint)
            }
        }

        private fun updateslideDuration(dur:Int)
        {
            slideDuration = dur
            handler.removeCallbacks(drawRunner)
            if (visible) {
                handler.postDelayed(drawRunner, slideDuration.toLong())
            }
        }


    }



}
