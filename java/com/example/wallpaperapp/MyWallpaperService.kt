package com.example.wallpaperapp

import android.app.WallpaperManager
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
import android.os.Build
import android.os.Handler
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.wallpaperapp.MyPreferencesActivity
import java.math.BigInteger


class MyWallpaperService : WallpaperService() {
    override fun onCreateEngine(): WallpaperService.Engine {
        return MyWallpaperEngine()
    }

    private inner class MyWallpaperEngine : WallpaperService.Engine() {
        private val handler = Handler()
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private val drawRunner = Runnable { drawFrame() }
        private var imagesArray = arrayOf<String>()
       //     "R.drawable.fashion", "R.drawable.dancing", "R.drawable.concerts", "R.drawable.fitness"
       // )
       /* private val wimageViewModel: WimageViewModel by viewModels {
            WimageViewModelFactory((application as WimageApplication).repository)
        }*/
        //var imagesArray: ArrayList<ImagesInfo?>? = ArrayList<ImagesInfo?>()
        private var imagesArrayIndex = 0
        private val slideDuration = 2000
        private val circles: MutableList<MyPoint>
        private val paint = Paint()
        private var width: Int = 0
        internal var height: Int = 0
        private var visible = true
        private val maxNumber: Int
        private val touchEnabled: Boolean

        //val adapter = WordListAdapter()
        //val uriPathHelper = URIPathHelper()
        //var displayMetrics: DisplayMetrics? = applicationContext.getResources().getDisplayMetrics()
        val wm = applicationContext?.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
        val display = wm?.defaultDisplay
        var flagtype = ""
        private var wallpaperManager: WallpaperManager? = null
        val buttonRect = RectF(100f, 100f, 300f, 200f)
        val myButtonPaint = Paint()
        var settingsicon : Bitmap?
        var scaledsettingsicon : Bitmap
        init {
            val prefs = PreferenceManager
                .getDefaultSharedPreferences(this@MyWallpaperService)


            imagesArray = loadArray("imagesPathList", applicationContext)
            maxNumber = Integer
                .valueOf((imagesArray.size).toString()!!)
            touchEnabled = false//prefs.getBoolean("touch", false)
            circles = ArrayList()
            width = display?.width ?: 0
            height = display?.height ?: 0
            wallpaperManager = WallpaperManager.getInstance(applicationContext)
            //Toast.makeText(applicationContext, "plsssss"+width.toString()+"...."+height.toString(), Toast.LENGTH_LONG).show()
           /* WimageViewModel.allWimages.observe(owner = this) { words ->
                // Update the cached copy of the words in the adapter.
                words.let { adapter.submitList(it) }
            }*/


            /* imagesArray = this.displayContext().getIntent()?.getParcelableArrayListExtra<ImagesInfo>("images") ?:
             throw IllegalStateException("Images array list is null")*/
           /* paint.isAntiAlias = true
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = 10f*/
          //  handler.post(drawRunner)

            myButtonPaint.isAntiAlias = true
            myButtonPaint.color = Color.WHITE
            myButtonPaint.style = Paint.Style.STROKE
            myButtonPaint.strokeJoin = Paint.Join.ROUND
            myButtonPaint.strokeWidth = 10f

            //settingsicon = ContextCompat.getDrawable(applicationContext, R.drawable.ic_crop)?.toBitmap()
            settingsicon = BitmapFactory.decodeResource(resources, R.drawable.crop)
            scaledsettingsicon = Bitmap.createScaledBitmap(settingsicon!!, 200, 200, true)

        }

        fun loadArray(arrayName: String, context: Context): Array<String> {
            val prefs = context.getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
            val size = prefs.getInt("${arrayName}_size", 0)

            val array = Array(size) { "" }
            for (i in 0 until size) {
                array[i] = prefs.getString("${arrayName}_$i", null) ?: ""
                //Toast.makeText(context,"output here.."+array[i],Toast.LENGTH_SHORT).show()
            }
            //Toast.makeText(context,"output here.."+array.size.toString()+"!!!",Toast.LENGTH_SHORT).show()
            return array
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                handler.post(drawRunner)
            } else {
                handler.removeCallbacks(drawRunner)
            }
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            this.visible = false
            handler.removeCallbacks(drawRunner)
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
                    if(imagesArrayIndex >= imagesArray.size)
                    {
                        imagesArrayIndex = 0
                    }
                    drawImage(canvas)
                    imagesArrayIndex++
                    /*canvas.drawColor(Color.BLACK)
                    circles.clear()
                    circles.add(MyPoint((circles.size + 1).toString(), x, y))
                    drawCircles(canvas, circles)*/

                }

                if (canvas != null)
                    holder.unlockCanvasAndPost(canvas)

                super.onTouchEvent(event)
            }
           /* if (event.action == MotionEvent.ACTION_UP) {
                // Check if the touch coordinates are within the button area
                if (buttonRect.contains(event.x, event.y)) {
                    // Launch your activity or perform an action
                    // (e.g., open settings)
                    Toast.makeText(applicationContext, imagesArrayIndex.toString()+"....kkkkkkk...."+imagesArray.size.toString(), Toast.LENGTH_LONG).show()

                }
            }*/
            val x = event.x
            val y = event.y

            val bitmapXPosition = (3*width)/4.toFloat()
            val bitmapYPosition = (3*height)/4.toFloat()
            val bitmapWidth = scaledsettingsicon.width
            val bitmapHeight = scaledsettingsicon.height
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    Log.i(
                        "bitmappos",
                        "x::bitmapXPosition $imagesArrayIndex:::$x:$bitmapXPosition:($x > $bitmapXPosition):($x < $bitmapXPosition + $bitmapWidth)"
                    )
                    Log.i(
                        "bitmappos",
                        "y::bitmapYPosition $y:$bitmapYPosition:($y > $bitmapYPosition):($y < $bitmapYPosition + $bitmapHeight)"
                    )
                    //Check if the x and y position of the touch is inside the bitmap
                    if (x > bitmapXPosition && x < bitmapXPosition + bitmapWidth && y > bitmapYPosition && y < bitmapYPosition + bitmapHeight) {
                        //Bitmap touched
                       // Toast.makeText(applicationContext, "....kkkkkkk....", Toast.LENGTH_LONG).show()
                        try {
                            val i = Intent(applicationContext, CropActivity::class.java)
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            i.putExtra("imageindex",imagesArrayIndex.toString())
                            startActivity(i)
                        }catch (e: Exception) {

                            Log.e("TAGGGGG33....", e.message, e)
                        }
                    }

                    //return true
                }
                MotionEvent.ACTION_DOWN -> {
                    Log.i(
                        "bitmappos",
                        "x::bitmapXPosition $imagesArrayIndex:::$x:$bitmapXPosition:($x > $bitmapXPosition):($x < $bitmapXPosition + $bitmapWidth)"
                    )
                    Log.i(
                        "bitmappos",
                        "y::bitmapYPosition $y:$bitmapYPosition:($y > $bitmapYPosition):($y < $bitmapYPosition + $bitmapHeight)"
                    )
                    //Check if the x and y position of the touch is inside the bitmap
                    if (x > bitmapXPosition && x < bitmapXPosition + bitmapWidth && y > bitmapYPosition && y < bitmapYPosition + bitmapHeight) {
                        //Bitmap touched
                        //Toast.makeText(applicationContext, "....kkkkkkk....", Toast.LENGTH_LONG).show()
                        try {
                            val i = Intent(applicationContext, CropActivity::class.java)
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            i.putExtra("imageindex",imagesArrayIndex.toString())
                            startActivity(i)
                        }catch (e: Exception) {

                            Log.e("TAGGGGG33....", e.message, e)
                        }
                    }

                    //return true
                }
            }
           // return false
        }

        private fun drawImage(canvas: Canvas) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            // Toast.makeText(applicationContext, imagesArrayIndex.toString()+"....kkkkkkk...."+imagesArray.size.toString(), Toast.LENGTH_LONG).show()
            if (imagesArrayIndex < imagesArray.size) {
                //  val image = BitmapFactory.decodeResource(resources, imagesArray[imagesArrayIndex])
                try {
                    var image: Bitmap = MediaStore.Images.Media.getBitmap(
                        applicationContext.contentResolver,
                        Uri.parse(imagesArray[imagesArrayIndex])
                    )

                    width = display?.width ?: 0
                    height = display?.height ?: 0
                    val imageVerticalAspectRatio: Int
                    val imageHorizontalAspectRatio: Int
                    var bestFitScalingFactor = 0f
                    val percesionValue = 0.2.toFloat()

                    //getAspect Ratio of Image

                    //getAspect Ratio of Image
                    val imageHeight = (Math.ceil(image.height.toDouble() / 100) * 100).toInt()
                    val imageWidth = (Math.ceil(image.width.toDouble() / 100) * 100).toInt()
                    val GCD = BigInteger.valueOf(imageHeight.toLong())
                        .gcd(BigInteger.valueOf(imageWidth.toLong())).toInt()
                    imageVerticalAspectRatio = imageHeight / GCD
                    imageHorizontalAspectRatio = imageWidth / GCD
                    Log.i(
                        "scaleDownLargeImageWIthAspectRatio",
                        "Image Dimensions(W:H): $imageWidth:$imageHeight"
                    )
                    Log.i(
                        "scaleDownLargeImageWIthAspectRatio",
                        "Image AspectRatio(W:H): $imageHorizontalAspectRatio:$imageVerticalAspectRatio"
                    )
                    //getContainer Dimensions

                    //getContainer Dimensions
                    val displayWidth: Int = width
                    val displayHeight: Int = height

                    val leftMargin = 0
                    val rightMargin = 0
                    val topMargin = 0
                    val bottomMargin = 0
                    val containerWidth = displayWidth - (leftMargin + rightMargin)
                    val containerHeight = displayHeight - (topMargin + bottomMargin)
                    Log.i(
                        "scaleDownLargeImageWIthAspectRatio",
                        "Container dimensions(W:H): $containerWidth:$containerHeight"
                    )

                    //iterate to get bestFitScaleFactor per constraints

                    //iterate to get bestFitScaleFactor per constraints
                    while (imageHorizontalAspectRatio * bestFitScalingFactor <= containerWidth && imageVerticalAspectRatio * bestFitScalingFactor <= containerHeight) {
                        bestFitScalingFactor += percesionValue
                    }

                    //return bestFit bitmap

                    //return bestFit bitmap
                    val bestFitHeight = (imageVerticalAspectRatio * bestFitScalingFactor).toInt()
                    val bestFitWidth = (imageHorizontalAspectRatio * bestFitScalingFactor).toInt()
                    Log.i(
                        "scaleDownLargeImageWIthAspectRatio",
                        "bestFitScalingFactor: $bestFitScalingFactor"
                    )
                    Log.i(
                        "scaleDownLargeImageWIthAspectRatio",
                        "bestFitOutPutDimesions(W:H): $bestFitWidth:$bestFitHeight"
                    )
                    image = Bitmap.createScaledBitmap(image, bestFitWidth, bestFitHeight, true)

                    //Position the bitmap centre of the container

                    //Position the bitmap centre of the container
                    val leftPadding = ((containerWidth - image.width) / 2).toFloat()
                    val topPadding = ((containerHeight - image.height) / 2).toFloat()
                    val backDrop =
                        Bitmap.createBitmap(containerWidth, containerHeight, Bitmap.Config.RGB_565)


                    var can = canvas
                    can = Canvas(backDrop)
                    canvas.drawBitmap(image, leftPadding, topPadding, null)
                   //Toast.makeText(applicationContext, "Wallpaper set!", Toast.LENGTH_SHORT).show()

                   // val scaledBitmap = Bitmap.createScaledBitmap(image, canvas.width, canvas.height, true)
                   // canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                    if(imagesArrayIndex == 0) {

                        val wallpaperMgr = WallpaperManager.getInstance(baseContext)
                        //val lockScreenWallpaperFile = wallpaperMgr.getWallpaperId(WallpaperManager.FLAG_LOCK)
                        //Log.i("TAGGGGG....", "flagfile: $lockScreenWallpaperFile")
                        wallpaperMgr.setBitmap(backDrop, null, true, WallpaperManager.FLAG_LOCK)
                    }

                    } catch (e: Exception) {
                        Toast.makeText(
                            applicationContext,
                            "....error here.." + e.message.toString(),
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("TAGGGGG....", e.message, e)
                    }
                }
            }



        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun drawFrame() {
            val flagval = getWallpaperFlags()
            Log.i("TAGGGGG....", "flagval: $flagval")
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
                    if(isPreview) {
                        //canvas.drawRect(buttonRect, myButtonPaint)
                        val xPos = (3*width)/4.toFloat()
                        val yPos = (3*height)/4.toFloat()
                        try {
                             canvas.drawBitmap(scaledsettingsicon, xPos, yPos, null)

                        } catch(e: Exception) {
                           Log.e("TAGGGGG22....", e.message, e)
                        }
                    }
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
           /* if(flagtype=="Home Screen")
                setWallpaper(canvas, WallpaperManager.FLAG_SYSTEM)
            else if(flagtype=="Lock Screen")
                setWallpaper(handler, WallpaperManager.FLAG_LOCK)
            else
                setWallpaper(handler, WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM)*/
            handler.removeCallbacks(drawRunner)
            if (visible) {
                handler.postDelayed(drawRunner, 2000)
            }
        }
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
                handler.postDelayed(drawRunner, 5000)
            }
        }

        // Surface view requires that all elements are drawn completely
        private fun drawCircles(canvas: Canvas, circles: List<MyPoint>) {
            canvas.drawColor(Color.BLACK)
            for (point in circles) {
                canvas.drawCircle(point.x, point.y, 20.0f, paint)
            }
        }
    }

}
