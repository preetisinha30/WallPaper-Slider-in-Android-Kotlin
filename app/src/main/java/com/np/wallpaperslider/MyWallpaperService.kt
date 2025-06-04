package com.np.wallpaperslider


import android.annotation.SuppressLint
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

import java.io.File



@RequiresApi(Build.VERSION_CODES.ECLAIR_MR1)
class MyWallpaperService : WallpaperService() {
    private var engine: MyWallpaperEngine? = null
    private var messenger: Messenger? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            messenger = Messenger(service)
            isBound = true
            Log.d("MyWallpaperService", "Bound to WallpaperForegroundService")
            if (engine?.isPreview() == true) {
                sendMessage(WallpaperForegroundService.MSG_REMOVE_NOTIFICATION)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            messenger = null
            isBound = false
            Log.d("MyWallpaperService", "Unbound from WallpaperForegroundService")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MyWallpaperService", "Service onCreate")
        bindService(
            Intent(this, WallpaperForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MyWallpaperService", "onStartCommand: isWallpaperSet=${iswallpaperSet()}, isPreview=${engine?.isPreview()}")
        return START_NOT_STICKY
    }

    private fun iswallpaperSet(): Boolean {
        try {
            val wpm = WallpaperManager.getInstance(applicationContext)
            val info = wpm.wallpaperInfo

            if (info != null && info.packageName == applicationContext.packageName) {
                Log.d("bitmappos", "We're already running")
               return true
            } else {
                Log.d("bitmappos", "We're not running")
                return false
            }
        }catch (e: Exception)
        {
            Log.e("bitmappos....", e.message, e)
        }
        return false
    }

    override fun onDestroy() {
        sendMessage(WallpaperForegroundService.MSG_REMOVE_NOTIFICATION)
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }

    private fun sendMessage(messageType: Int) {
        if (!isBound || messenger == null) {
            Log.w("MyWallpaperService", "Not bound to service, cannot send message: $messageType")
            return
        }
        try {
            val msg = Message.obtain(null, messageType)
            messenger?.send(msg)
            Log.d("MyWallpaperService", "Sent message: $messageType")
        } catch (e: Exception) {
            Log.e("MyWallpaperService", "Error sending message: ${e.message}", e)
        }
    }

    override fun onCreateEngine(): WallpaperService.Engine {
         return MyWallpaperEngine()
    }

    @Suppress("DEPRECATION")
    private inner class MyWallpaperEngine : WallpaperService.Engine() {
        private val handler = Handler()


        private val drawRunner = Runnable { drawFrame() }
        private var imagesArray = arrayOf<String>()

        private var imagesArrayIndex = 0
        private var slideDuration:Int = 1000
        private val circles: MutableList<MyPoint>
        private val paint = Paint()
        private var width: Int = 0
        internal var height: Int = 0
        private var visible = true
        private val maxNumber: Int
        private val touchEnabled: Boolean


        val wm = applicationContext?.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
        val display = wm?.defaultDisplay
        var flagtype = ""
        private var wallpaperManager: WallpaperManager? = null
        val buttonRect = RectF(100f, 100f, 300f, 200f)
        val myButtonPaint = Paint()
        //var settingsicon : Bitmap?
        //var scaledsettingsicon : Bitmap
        init {
           // val prefs = PreferenceManager.getDefaultSharedPreferences(this@MyWallpaperService)


            imagesArray = loadArray("imagesPathList", applicationContext)
            maxNumber = Integer
                .valueOf((imagesArray.size).toString()!!)
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



        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (isPreview && !visible) {
                handler.removeCallbacks(drawRunner)

                try {
                    wallpaperManager?.forgetLoadedWallpaper()
                } catch (e: Exception) {
                    Log.e("WallpaperService", "Error clearing wallpaper: ${e.message}", e)
                }
            }
                if (visible && imagesArray.isNotEmpty()) {
                    drawFrame()
                } else {
                    handler.removeCallbacks(drawRunner)
                }


        }



        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            this.visible = false
            handler.removeCallbacks(drawRunner)

            try {
                wallpaperManager?.forgetLoadedWallpaper()
                Log.d("WallpaperService", "Cleared wallpaper cache in onSurfaceDestroyed")
            } catch (e: Exception) {
                Log.e("WallpaperService", "Error clearing wallpaper: ${e.message}", e)
            }
            super.onSurfaceDestroyed(holder)
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder, format: Int,
            width: Int, height: Int
        ) {
            this.width = width
            this.height = height
           super.onSurfaceChanged(holder, format, width, height)
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
                    drawImage(canvas)
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

        private fun drawImage(canvas: Canvas) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            // Toast.makeText(applicationContext, imagesArrayIndex.toString()+"....kkkkkkk...."+imagesArray.size.toString(), Toast.LENGTH_LONG).show()
            if (imagesArrayIndex < imagesArray.size) {

                try {

                    val inputStream = applicationContext.contentResolver.openInputStream(Uri.parse(imagesArray[imagesArrayIndex]))
                    var image: Bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (image == null) {
                        Log.e("MyWallpaperService", "Failed to decode image at index $imagesArrayIndex")
                        canvas.drawColor(Color.BLACK) // Default background
                        return
                    }

                    width = display?.width ?: 0
                    height = display?.height ?: 0
                    val imageVerticalAspectRatio: Int
                    val imageHorizontalAspectRatio: Int
                    var bestFitScalingFactor = 0f
                    val percesionValue = 0.2.toFloat()

                    //getAspect Ratio of Image
                    val imageHeight = (Math.ceil(image.height.toDouble() / 100) * 100).toInt()
                    val imageWidth = (Math.ceil(image.width.toDouble() / 100) * 100).toInt()
                    val GCD = BigInteger.valueOf(imageHeight.toLong())
                        .gcd(BigInteger.valueOf(imageWidth.toLong())).toInt()
                    imageVerticalAspectRatio = imageHeight / GCD
                    imageHorizontalAspectRatio = imageWidth / GCD

                    //getContainer Dimensions
                    val displayWidth: Int = width
                    val displayHeight: Int = height

                    val leftMargin = 0
                    val rightMargin = 0
                    val topMargin = 0
                    val bottomMargin = 0
                    val containerWidth = displayWidth - (leftMargin + rightMargin)
                    val containerHeight = displayHeight - (topMargin + bottomMargin)

                    //iterate to get bestFitScaleFactor per constraints
                    while (imageHorizontalAspectRatio * bestFitScalingFactor <= containerWidth &&
                        imageVerticalAspectRatio * bestFitScalingFactor <= containerHeight) {
                        bestFitScalingFactor += percesionValue
                    }

                    //return bestFit bitmap
                    val bestFitHeight = (imageVerticalAspectRatio * bestFitScalingFactor).toInt()
                    val bestFitWidth = (imageHorizontalAspectRatio * bestFitScalingFactor).toInt()

                    image = Bitmap.createScaledBitmap(image, bestFitWidth, bestFitHeight, true)

                    //Position the bitmap centre of the container
                    val leftPadding = ((containerWidth - image.width) / 2).toFloat()
                    val topPadding = ((containerHeight - image.height) / 2).toFloat()
                    val backDrop =
                        Bitmap.createBitmap(containerWidth, containerHeight, Bitmap.Config.RGB_565)


                    var can = canvas
                    can = Canvas(backDrop)
                    //can.drawColor(Color.BLACK)
                    can.drawColor(ContextCompat.getColor(applicationContext, R.color.purple_200))
                    can.drawBitmap(image, leftPadding, topPadding, null)
                    canvas.drawBitmap(backDrop, 0f, 0f, null)


                    if(imagesArrayIndex == 0) {

                       // val wallpaperMgr = WallpaperManager.getInstance(baseContext)
                        //val lockScreenWallpaperFile = wallpaperMgr.getWallpaperId(WallpaperManager.FLAG_LOCK)
                        //Log.i("TAGGGGG....", "flagfile: $lockScreenWallpaperFile")
                       // wallpaperMgr.setBitmap(backDrop, null, true, WallpaperManager.FLAG_LOCK)
                    }

                    } catch (e: Exception) {

                    Log.e("MyWallpaperService", "Error drawing image at index $imagesArrayIndex: ${e.message}", e)
                    canvas.drawColor(ContextCompat.getColor(applicationContext, R.color.purple_200))
                    }


            }
            else {
                Log.w("MyWallpaperService", "No images to draw, using default background")
                canvas.drawColor(ContextCompat.getColor(applicationContext, R.color.purple_200)) // Default background
            }
            }




        @SuppressLint("SuspiciousIndentation")
        private fun drawFrame() {
           if (!visible || imagesArray.isEmpty()) {
                Log.w("WallpaperService", "Skipping draw: visible=$visible, images=${imagesArray.size}")
                return
            }
            if(!iswallpaperSet() && !isPreview)
            {

                sendMessage(WallpaperForegroundService.MSG_REMOVE_NOTIFICATION)
                stopForeground(true)
                stopSelf()
            }
            if(iswallpaperSet() && !isPreview)
            {
                Log.i("WallpaperService", "stopping both")
                sendMessage(WallpaperForegroundService.MSG_SHOW_NOTIFICATION)

                //stopForeground(true)
                stopSelf()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            val flagval = getWallpaperFlags()
                if(flagval==1)
                {
                    val backDrop =
                        Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                    val wallpaperMgr = WallpaperManager.getInstance(baseContext)
                    wallpaperMgr.setBitmap(backDrop, null, true, WallpaperManager.FLAG_LOCK)

                }

               // Log.i("TAGGGGG....", "flagval: $flagval")
            } else {
                Toast.makeText(
                    applicationContext,
                    "Wallpaper slider not supported!",
                    Toast.LENGTH_SHORT
                ).show()
                TODO("VERSION.SDK_INT < UPSIDE_DOWN_CAKE")

            }

            val holder: SurfaceHolder = surfaceHolder

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                   // Toast.makeText(applicationContext, "111 set!", Toast.LENGTH_SHORT).show()
                    if(imagesArrayIndex >= imagesArray.size)
                    {
                        imagesArrayIndex = 0
                    }

                        drawImage(canvas)
                        imagesArrayIndex++
                    /*if(isPreview) {
                        //canvas.drawRect(buttonRect, myButtonPaint)
                       val xPos = (3*width)/4.toFloat()
                        val yPos = (3*height)/4.toFloat()
                        try {
                             canvas.drawBitmap(scaledsettingsicon, xPos, yPos, null)

                        } catch(e: Exception) {
                           Log.e("TAGGGGG22....", e.message, e)
                        }
                    }*/
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e("WallpaperService", "Error unlocking canvas: ${e.message}", e)
                    }
                }
            }
           /* if(flagtype=="Home Screen")
                setWallpaper(canvas, WallpaperManager.FLAG_SYSTEM)
            else if(flagtype=="Lock Screen")
                setWallpaper(handler, WallpaperManager.FLAG_LOCK)
            else
                setWallpaper(handler, WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM)*/

            handler.removeCallbacks(drawRunner)
            if (visible && imagesArray.isNotEmpty()) {
                handler.postDelayed(drawRunner, slideDuration.toLong())
            }
            /*if(iswallpaperSet() && !isPreview)
            {
                //visible=false
                handler.removeCallbacks(drawRunner)
            }*/
        }





        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean
        ): Bundle? {
            Log.d("WallpaperService", "onCommand called with action: $action, isPreview: $isPreview")
            if (action == "wallpaper_set") {
                Log.d("WallpaperService", "Wallpaper set command received")
                if (isPreview()) {
                    // Stop drawing and clean up preview engine
                    handler.removeCallbacks(drawRunner)

                    try {
                        wallpaperManager?.forgetLoadedWallpaper()
                        Log.d("WallpaperService", "Cleared wallpaper cache")
                    } catch (e: Exception) {
                        Log.e("WallpaperService", "Error clearing wallpaper: ${e.message}", e)
                    }
                    // Force preview engine to stop
                    visible = false
                    notifyWallpaperSet()
                }
                else{
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
                Log.d("WallpaperService", "Broadcast ACTION_WALLPAPER_CHANGED")
            } catch (e: Exception) {
                Log.e("WallpaperService", "Error broadcasting wallpaper change: ${e.message}", e)
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
