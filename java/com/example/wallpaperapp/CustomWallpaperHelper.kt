package com.example.wallpaperapp

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.PointF
import android.view.Display
import android.view.WindowManager

class CustomWallpaperHelper(mContext: Context?, mResources: Resources?) {
    companion object {
        const val IMAGE_SCALE_STRETCH_TO_SCREEN = "Stretch to   screen";

        const val IMAGE_SCALE_FIT_TO_SCREEN = "Fit to screen";
    }

    private val screenSize = Point();

    private var bgImageScaled: Bitmap? = null;
    private var bgImagePos = Point(0, 0);

    // public CustomWallpaperHelper(Context mContext, Resources mResources) {
    // this.mContext = mContext;
    //  this.mResources = mResources;
    init {
        val wm = mContext?.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
        val display = wm?.defaultDisplay

        screenSize.x = display?.width ?: 0
        screenSize.y = display?.height ?: 0
    }


    // }

    private fun scaleBackground() {
        val imageScale = "Stretch to screen";
        var bgImage: Bitmap? = null;

        if (imageScale == IMAGE_SCALE_STRETCH_TO_SCREEN) {
            bgImagePos = Point(0, 0);
            bgImageScaled = Bitmap.createScaledBitmap(bgImage!!, screenSize.x, screenSize.y, true);
        }
    }

    public fun setBackground(canvas: Canvas) {
        if (bgImageScaled != null) {
            canvas.drawBitmap(
                bgImageScaled!!,
                bgImagePos.x.toFloat(),
                bgImagePos.y.toFloat(),
                null
            );
        } else {
            canvas.drawColor(0xff000000.toInt());
        }
    }

    fun getScreenWidth(): Int {
        return screenSize.x;
    }

    fun getScreenHeight(): Int {
        return screenSize.y;
    }

    fun getImagePos(canvasScale: PointF, imageWidth: Int, imageHeight: Int):Point {
        var imagePos = Point(0, 0)
        imagePos.x = ((screenSize.x - (imageWidth * canvasScale.x)) / 2).toInt()
        imagePos.y = ((screenSize.y - (imageHeight * canvasScale.y)) / 2).toInt()
        return imagePos
    }

    fun getCanvasScale(imageScale: String, imageWidth: Int, imageHeight: Int):PointF {
        var canvasScale = PointF(1f, 1f);

        if (imageScale == IMAGE_SCALE_STRETCH_TO_SCREEN) {
            canvasScale.x = getScreenWidth() / (1f * imageWidth)
            canvasScale.y = getScreenHeight() / (1f * imageHeight)
        } else {
            var tooWide = false;
            var tooTall = false;

            if (getScreenWidth() < imageWidth) {
                tooWide = true;
            }

            if (getScreenHeight() < imageHeight) {
                tooTall = true;
            }

            if (tooWide && tooTall) {
                val x = imageWidth / getScreenWidth()
                val y = imageHeight / getScreenHeight()

                if (x > y) {
                    canvasScale.x = getScreenWidth() / (1f * imageWidth)
                    canvasScale.y = 1f
                } else {
                    canvasScale.x = 1f
                    canvasScale.y = getScreenHeight() / (1f * imageHeight)
                }
            } else if (tooWide) {
                canvasScale.x = getScreenWidth() / (1f * imageWidth)
                canvasScale.y = 1f
            } else if (tooTall) {
                canvasScale.x = 1f
                canvasScale.y = getScreenHeight() / (1f * imageHeight)
            }
        }

        return canvasScale
    }
}