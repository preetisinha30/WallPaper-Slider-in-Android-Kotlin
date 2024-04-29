package com.example.wallpaperapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.preference.PreferenceManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.widget.Toast

class ImageSlider : WallpaperService() {

    override fun onCreateEngine(): WallpaperService.Engine {
        return WallpaperEngine()
    }


     private inner class WallpaperEngine : WallpaperService.Engine(){
         private val slideDuration = 2000
         private val TAG = "MyActivity"
         private val imagesArray = intArrayOf(
             R.drawable.fashion, R.drawable.dancing, R.drawable.fashion, R.drawable.fitness
         )
         private var imagesArrayIndex = 0
         private var drawWallpaper: Thread

         //private val customWallpaperHelper = CustomWallpaperHelper(applicationContext, resources)
         private val maxNumber: Int
         private val touchEnabled: Boolean
         private var visible = true
        init {
            val prefs = PreferenceManager
                .getDefaultSharedPreferences(this@ImageSlider)
            maxNumber = Integer
                .valueOf(prefs.getString(resources.getString(R.string.lable_number_of_circles), "4")!!)
            Toast.makeText(applicationContext, "Hello Wallpaper!", Toast.LENGTH_SHORT).show()
            touchEnabled = prefs.getBoolean("touch", false)
            drawWallpaper = Thread {
                try {
                    while (true) {
                        //Log.v(TAG, "index=");
                        drawFrame()
                        incrementCounter()
                        Thread.sleep(slideDuration.toLong())
                    }
                } catch (e: Exception) {
                    // Handle exception
                    Log.v(TAG, "errrrrrrr"+e.message.toString());
                }
            }
            drawWallpaper.start();
        }

        private fun incrementCounter() {
            imagesArrayIndex++
            if (imagesArrayIndex >= imagesArray.size) {
                imagesArrayIndex = 0
            }
        }

        private fun drawFrame() {
            val holder: SurfaceHolder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.let {
                    drawImage(it)
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

         override fun onTouchEvent(event: MotionEvent) {
             if (touchEnabled) {

                 val holder: SurfaceHolder = surfaceHolder
                 var canvas: Canvas? = null
                 try {
                     canvas = holder.lockCanvas()
                     canvas?.let {
                         drawImage(it)
                     }
                 } finally {
                     if (canvas != null) {
                         holder.unlockCanvasAndPost(canvas)
                     }
                 }

                 super.onTouchEvent(event)
             }
         }



        private fun drawImage(canvas: Canvas) {
            val image = BitmapFactory.decodeResource(resources, imagesArray[imagesArrayIndex])
            Toast.makeText(applicationContext, "Wallpaper set!", Toast.LENGTH_SHORT).show()
            val scaledBitmap = Bitmap.createScaledBitmap(image, canvas.width, canvas.height, true)
            canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
        }
    }
}


