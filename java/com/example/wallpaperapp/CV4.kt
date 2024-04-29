package com.example.wallpaperapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Color.*
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

import kotlin.math.max
import kotlin.math.min

class CV4 : androidx.appcompat.widget.AppCompatImageView, View.OnTouchListener {

    // pass bitmap image
    //private val src: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.mountain)
    lateinit var src : Bitmap
    private var screenWidth = 0
    private var imageWidth = 0
    private var imageHeight = 0
    var aspectRatio = 0f
    var canvas: Canvas? = null
    var paint: Paint? = null
    //get the bitmap from test():Bitmap? function
    private lateinit var output: Bitmap

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

    private var motionX = 0f
    private var motionY = 0f
    private var rng = 40f // Touch range for the 4 side and 4 corners of the rect

    // p for point and l=left t=top r=right b=bottom
    private var pl = 100f
    private var plx = 100f //hold motionX value from moveDown() function

    private var pt = 100f
    private var pty = 100f //hold motionY value from moveDown() function

    private var pr = 300f
    private var pb = 400f

    //hold left,top,right,bottom value from moveDown() function
    private var l = 0f
    private var t = 0f
    private var r = 0f
    private var b = 0f

    // check user touch Down on rect edges, corners or inside

    //edges point
    private var c1 = false
    private var c2 = false
    private var c3 = false
    private var c4 = false

    private var c5 = false  // for inside selection to move the whole rect

    //corners point
    private var c6 = false
    private var c7 = false
    private var c8 = false
    private var c9 = false
/*
    fun test(origimage: Bitmap): Bitmap? {
        // fun test(origimage:Bitmap): Bitmap? {
         src = Bitmap.createBitmap(origimage)
        //src.setImageBitmap(origimage)
        aspectRatio = src.width.toFloat() / src.height.toFloat()
        val cropWidth: Float = (pr - pl)
        val cropHeight: Float = (pb - pt)

        //invalidate()
        //returning the bitmap
        return Bitmap.createBitmap(
            output,
            pl.toInt(),
            pt.toInt(),
            cropWidth.toInt(),
            cropHeight.toInt()
        )
    }
*/
    fun test(alteredBitmap: Bitmap?, bmp: Bitmap?): Bitmap?  {
    Log.i(
        "CV4flow",
        "test"
    )
        canvas = Canvas(alteredBitmap!!)
        paint = Paint()
        paint!!.setColor(Color.GREEN)
        paint!!.setStrokeWidth(5.0f)
        val matrix = Matrix()
        if (bmp != null) {
            canvas!!.drawBitmap(bmp, matrix!!, paint)
            src = bmp
            aspectRatio = bmp.width.toFloat() / bmp.height.toFloat()
        }
       // setImageBitmap(alteredBitmap)

        val cropWidth: Float = (pr - pl)
        val cropHeight: Float = (pb - pt)

        invalidate()
        //returning the bitmap
        return Bitmap.createBitmap(
            output,
            pl.toInt(),
            pt.toInt(),
            cropWidth.toInt(),
            cropHeight.toInt()
        )
    }
    init {

    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        Log.i(
            "CV4flow",
            "onFinishInflate: "
        )
    }
    //    onMeasure will be called before onSizeChanged and onDraw
//    please see the View lifecycle for more details
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        screenWidth = MeasureSpec.getSize(widthMeasureSpec)
        imageWidth = screenWidth
        imageHeight = ((screenWidth.toFloat() / src.width) * src.height).toInt()
        setMeasuredDimension(screenWidth, imageHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val desiredWidthInPx = imageWidth
        val derivedHeightInPx = (desiredWidthInPx / aspectRatio).toInt()

        output = Bitmap.createScaledBitmap(src, desiredWidthInPx, derivedHeightInPx, true)
        rectF2 = RectF(0f, 0f, imageWidth.toFloat(), derivedHeightInPx.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)


        parent.requestDisallowInterceptTouchEvent(true)
        canvas?.apply {

            // background image under overlay
            drawBitmap(output, 0f, 0f, null)

            // dark overlay
            drawRect(rectF2, rectPaint2)

            // clip rect same as movable rect. This will hide everything outside
            clipRect(rectF)

            // visible clear image covered by clip rect
            drawBitmap(output, 0f, 0f, null)

            // movable rect
            drawRoundRect(rectF, 10f, 10f, rectPaint)
        }
    }


  /*
    override fun onTouchEvent(v: View?, event: MotionEvent?): Boolean {

        if (event != null) {
            motionX = event.x
            motionY = event.y
            when (event.action) {
                MotionEvent.ACTION_MOVE -> moveMove()
                MotionEvent.ACTION_DOWN -> moveDown()
                MotionEvent.ACTION_UP -> moveUp()
            }
        }

        return true

    }*/

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        val action = event.action
        when (action) {
            MotionEvent.ACTION_DOWN -> moveDown()

            MotionEvent.ACTION_MOVE -> moveMove()

            MotionEvent.ACTION_UP -> moveUp()

            MotionEvent.ACTION_CANCEL -> {}
            else -> {}
        }
        return true
    }

    private fun moveMove() {

        //moving the whole rect
        if (c5) {

            if (pr < screenWidth && motionX > plx) {
                pr = min(r + (motionX - plx), screenWidth.toFloat())
                pl = min(l + (motionX - plx), screenWidth.toFloat() - (r - l))
            }
            if (pl > 0 && motionX < plx) {
                pr = max(r - (plx - motionX), 0f + (r - l))
                pl = max(l - (plx - motionX), 0f)
            }
            if (pb < imageHeight && motionY > pty) {
                pb = min(b + (motionY - pty), imageHeight.toFloat())
                pt = min(t + (motionY - pty), imageHeight.toFloat() - (b - t))
            }
            if (pt > 0 && motionY < pty) {
                pb = max(b - (pty - motionY), 0f + (b - t))
                pt = max(t - (pty - motionY), 0f)
            }
            rectF.set(pl + 5, pt + 5, pr - 5, pb - 5)
            invalidate()
        }

        // moving while holding corners
        if (c6) {
            if (motionX > 0 && motionX < (pr - 100)) pl = motionX
            if (motionY > 0 && motionY < (pb - 100)) pt = motionY
        }
        if (c7) {
            if (motionY > 0 && motionY < (pb - 100)) pt = motionY
            if (motionX > (pl + 100) && motionX < screenWidth) pr = motionX
        }
        if (c8) {
            if (motionX > (pl + 100) && motionX < screenWidth) pr = motionX
            if (motionY > (pt + 100) && motionY < imageHeight) pb = motionY
        }
        if (c9) {
            if (motionX > 0 && motionX < (pr - 100)) pl = motionX
            if (motionY > (pt + 100) && motionY < imageHeight) pb = motionY
        }

        // For moving the edge
        if (c1) if (motionX > 0 && motionX < (pr - 100)) pl = motionX
        if (c2) if (motionY > 0 && motionY < (pb - 100)) pt = motionY
        if (c3) if (motionX > (pl + 100) && motionX < screenWidth) pr = motionX
        if (c4) if (motionY > (pt + 100) && motionY < imageHeight) pb = motionY


        rectF.set(pl + 5, pt + 5, pr - 5, pb - 5)
        invalidate()

    }

    private fun moveDown() {

        if (motionX > (pl + rng) && motionX < (pr - rng) && motionY > (pt + rng) && motionY < (pb - rng)) {

            c5 = true
            l = pl
            t = pt
            r = pr
            b = pb

            if (motionY >= 0 && motionY <= imageHeight) pty = motionY
            if (motionX >= 0 && motionX <= screenWidth) plx = motionX

            invalidate()
            return
        }

        if (motionX in pl - rng..pl + rng && motionY in pt - rng..pt + rng) {
            c6 = true
            invalidate()
            return
        }
        if (motionY in pt - rng..pt + rng && motionX in pr - rng..pr + rng) {
            c7 = true
            invalidate()
            return
        }
        if (motionX in pr - rng..pr + rng && motionY in pb - rng..pb + rng) {
            c8 = true
            invalidate()
            return
        }
        if (motionY in pb - rng..pb + rng && motionX in pl - rng..pl + rng) {
            c9 = true
            invalidate()
            return
        }




        if (motionX > (pl - rng) && motionX < (pl + rng) && motionY > pt && motionY < pb) {
            c1 = true
            invalidate()
            return
        }
        if (motionY > (pt - rng) && motionY < (pt + rng) && motionX > pl && motionX < pr) {
            c2 = true
            invalidate()
            return
        }
        if (motionX > (pr - rng) && motionX < (pr + rng) && motionY > pt && motionY < pb) {
            c3 = true
            invalidate()
            return
        }
        if (motionY > (pb - rng) && motionY < (pb + rng) && motionX > pl && motionX < pr) {
            c4 = true
            invalidate()
            return
        }


        invalidate()

    }

    private fun moveUp() {
        c1 = false
        c2 = false
        c3 = false
        c4 = false
        c5 = false

        c6 = false
        c7 = false
        c8 = false
        c9 = false

        invalidate()
    }




    // resizable rect
    private var rectF = RectF(pl + 5, pt + 5, pr - 5, pb - 5)
    private val rectPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = Color.YELLOW

    }

    // dark overlay rect
    private val foregroundArcColor =
        context.resources?.getColor(R.color.blue, null) ?: GRAY
    private var rectF2 = RectF(0f, 0f, screenWidth.toFloat(), imageHeight.toFloat())
    private val rectPaint2 = Paint().apply {
        style = Paint.Style.FILL
        color = foregroundArcColor
    }



}