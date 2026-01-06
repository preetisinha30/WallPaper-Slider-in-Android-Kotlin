package com.np.wallpaperslider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class SpotlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cutOutRect = Rect()
    private val backgroundPaint = Paint()
    private val cutOutPaint = Paint()
    //private val borderPaint = Paint()
    init {
        // Set up the background paint (e.g., semi-transparent black)
        backgroundPaint.color = 0xAA000000.toInt() // Semi-transparent black

        // Set up the cutout paint using PorterDuff.Mode.CLEAR
        cutOutPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        cutOutPaint.isAntiAlias = true
        /* borderPaint.color = Color.GRAY
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 4f
        borderPaint.isAntiAlias = true*/
    }

    /** Sets the rectangular area to be cut out and invalidates the view. */
    fun setTargetRect(left: Int, top: Int, right: Int, bottom: Int) {
        cutOutRect.set(left, top, right, bottom)
       // cutOutRect.inset(1,1,1,1)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw the full semi-transparent background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // 2. Draw the transparent cutout area (the spotlight)
        //canvas.drawRect(cutOutRect, cutOutPaint)
       // canvas.drawRect(cutOutRect, borderPaint)
    }
}