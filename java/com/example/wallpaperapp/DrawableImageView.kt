package com.example.wallpaperapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.ImageView


class DrawableImageView : androidx.appcompat.widget.AppCompatImageView, OnTouchListener {
    var downx = 0f
    var downy = 0f
    var upx = 0f
    var upy = 0f
    var canvas: Canvas? = null
    var paint: Paint? = null
    //lateinit var matrix: Matrix

    constructor(context: Context?) : super(context!!) {
        setOnTouchListener(this)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        setOnTouchListener(this)
    }

    constructor(
        context: Context?, attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context!!, attrs, defStyleAttr) {
        setOnTouchListener(this)
    }

    fun setNewImage(alteredBitmap: Bitmap?, bmp: Bitmap?) {
        canvas = Canvas(alteredBitmap!!)
        paint = Paint()
        paint!!.setColor(Color.GREEN)
        paint!!.setStrokeWidth(5.0f)
        val matrix = Matrix()
        if (bmp != null) {
            canvas!!.drawBitmap(bmp, matrix!!, paint)
        }
        setImageBitmap(alteredBitmap)
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        val action = event.action
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downx = getPointerCoords(event)[0] //event.getX();
                downy = getPointerCoords(event)[1] //event.getY();
            }

            MotionEvent.ACTION_MOVE -> {
                upx = getPointerCoords(event)[0] //event.getX();
                upy = getPointerCoords(event)[1] //event.getY();
                paint?.let { canvas?.drawLine(downx, downy, upx, upy, it) }
                invalidate()
                downx = upx
                downy = upy
            }

            MotionEvent.ACTION_UP -> {
                upx = getPointerCoords(event)[0] //event.getX();
                upy = getPointerCoords(event)[1] //event.getY();
                paint?.let { canvas?.drawLine(downx, downy, upx, upy, it) }
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {}
            else -> {}
        }
        return true
    }

    fun getPointerCoords(e: MotionEvent): FloatArray {
        val index = e.actionIndex
        val coords = floatArrayOf(e.getX(index), e.getY(index))
        val matrix = Matrix()
        imageMatrix.invert(matrix)
        matrix.postTranslate(scrollX.toFloat(), scrollY.toFloat())
        matrix.mapPoints(coords)
        return coords
    }
}