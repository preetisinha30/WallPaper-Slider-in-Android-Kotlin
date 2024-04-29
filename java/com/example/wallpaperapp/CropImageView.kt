package com.example.wallpaperapp


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import com.example.wallpaperapp.R

class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) :
    androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyle) {
    private val TRANSPARENT: Int
    private val TRANSLUCENT_WHITE = -0x44000001
    private val WHITE = -0x1
    private val TRANSLUCENT_BLACK = -0x45000000

    // Member variables ////////////////////////////////////////////////////////////////////////////
    private var mViewWidth = 0
    private var mViewHeight = 0
    private var mScale = 1.0f
    private val mAngle = 0.0f
    private var mImgWidth = 0.0f
    private var mImgHeight = 0.0f
    private var mIsInitialized = false
    private var mMatrix: Matrix? = null
    private val mPaintTransparent: Paint
    private val mPaintFrame: Paint
    private val mPaintBitmap: Paint
    private var mFrameRect: RectF? = null
    private var mImageRect: RectF? = null
    private var mCenter = PointF()
    private var mLastX = 0f
    private var mLastY = 0f

    // Instance variables for customizable attributes //////////////////////////////////////////////
    private var mTouchArea = TouchArea.OUT_OF_BOUNDS
    private var mCropMode: CropMode? = CropMode.RATIO_1_1
    private var mGuideShowMode: ShowMode? = ShowMode.SHOW_ALWAYS
    private var mHandleShowMode: ShowMode? = ShowMode.SHOW_ALWAYS
    private var mMinFrameSize: Float
    private var mHandleSize: Int
    private var mTouchPadding = 0
    private var mShowGuide = true
    private var mShowHandle = true
    private var mIsCropEnabled = true
    private var mIsEnabled = true
    private var mCustomRatio = PointF(1.0f, 1.0f)
    private var mFrameStrokeWeight = 3.0f
    private var mGuideStrokeWeight = 3.0f
    private var mBackgroundColor: Int
    private var mOverlayColor: Int
    private var mFrameColor: Int
    private var mHandleColor: Int
    private var mGuideColor: Int
    private var mInitialFrameScale = 0f // 0.01 ~ 1.0, 0.75 is default value
    private var imageName: String? = null

    // Constructor /////////////////////////////////////////////////////////////////////////////////
    init {
        TRANSPARENT = resources.getColor(android.R.color.transparent)
        val mDensity = density
        mHandleSize = (mDensity * HANDLE_SIZE_IN_DP).toInt()
        mMinFrameSize = mDensity * MIN_FRAME_SIZE_IN_DP
        mFrameStrokeWeight = mDensity * FRAME_STROKE_WEIGHT_IN_DP
        mGuideStrokeWeight = mDensity * GUIDE_STROKE_WEIGHT_IN_DP
        mPaintFrame = Paint()
        mPaintTransparent = Paint()
        mPaintBitmap = Paint()
        mPaintBitmap.isFilterBitmap = true
        mMatrix = Matrix()
        mScale = 1.0f
        mBackgroundColor = TRANSPARENT
        mFrameColor = WHITE
        mOverlayColor = TRANSLUCENT_BLACK
        mHandleColor = WHITE
        mGuideColor = TRANSLUCENT_WHITE

        // handle Styleable
        handleStyleable(context, attrs, defStyle, mDensity)
    }

    // Lifecycle methods ///////////////////////////////////////////////////////////////////////////
    public override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        val bm = bitmap
        ss.image = bm
        ss.mode = mCropMode
        ss.backgroundColor = mBackgroundColor
        ss.overlayColor = mOverlayColor
        ss.frameColor = mFrameColor
        ss.guideShowMode = mGuideShowMode
        ss.handleShowMode = mHandleShowMode
        ss.showGuide = mShowGuide
        ss.showHandle = mShowHandle
        ss.handleSize = mHandleSize
        ss.touchPadding = mTouchPadding
        ss.minFrameSize = mMinFrameSize
        ss.customRatioX = mCustomRatio.x
        ss.customRatioY = mCustomRatio.y
        ss.frameStrokeWeight = mFrameStrokeWeight
        ss.guideStrokeWeight = mGuideStrokeWeight
        ss.isCropEnabled = mIsCropEnabled
        ss.handleColor = mHandleColor
        ss.guideColor = mGuideColor
        ss.initialFrameScale = mInitialFrameScale
        return ss
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(ss.superState)
        mCropMode = ss.mode
        mBackgroundColor = ss.backgroundColor
        mOverlayColor = ss.overlayColor
        mFrameColor = ss.frameColor
        mGuideShowMode = ss.guideShowMode
        mHandleShowMode = ss.handleShowMode
        mShowGuide = ss.showGuide
        mShowHandle = ss.showHandle
        mHandleSize = ss.handleSize
        mTouchPadding = ss.touchPadding
        mMinFrameSize = ss.minFrameSize
        mCustomRatio = PointF(ss.customRatioX, ss.customRatioY)
        mFrameStrokeWeight = ss.frameStrokeWeight
        mGuideStrokeWeight = ss.guideStrokeWeight
        mIsCropEnabled = ss.isCropEnabled
        mHandleColor = ss.handleColor
        mGuideColor = ss.guideColor
        mInitialFrameScale = ss.initialFrameScale
        setImageBitmap(ss.image!!)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        val viewHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(viewWidth, viewHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        mViewWidth = r - l - paddingLeft - paddingRight
        mViewHeight = b - t - paddingTop - paddingBottom
        if (drawable != null) initLayout(mViewWidth, mViewHeight)
    }

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mIsInitialized) {
            setMatrix()
            val localMatrix1 = Matrix()
            localMatrix1.postConcat(mMatrix)
            val bm = bitmap
            if (bm != null) {
                canvas.drawBitmap(bm, localMatrix1, mPaintBitmap)
                // draw edit frame
                drawEditFrame(canvas)
            }
        }
    }

    // Handle styleable ////////////////////////////////////////////////////////////////////////////
    private fun handleStyleable(
        context: Context, attrs: AttributeSet?, defStyle: Int,
        mDensity: Float
    ) {
        /*val ta = context.obtainStyledAttributes(
            attrs, R.styleable.CropImageView, defStyle,
            0
        )*/
        var drawable: Drawable? = null
        mCropMode = CropMode.RATIO_1_1
        try {
            drawable.let { setImageDrawable(it) }
            //drawable = ta.getDrawable(R.styleable.CropImageView_imgSrc)
            //drawable?.let { setImageDrawable(it) }
           /* for (mode in CropMode.entries) {
                if (ta.getInt(R.styleable.CropImageView_cropMode, 3) == mode.id) {
                    mCropMode = mode
                    break
                }
            }*/
            mBackgroundColor = TRANSPARENT//ta.getColor(R.styleable.CropImageView_backgroundColor, TRANSPARENT)
            super.setBackgroundColor(mBackgroundColor)
            mOverlayColor = TRANSLUCENT_BLACK//ta.getColor(R.styleable.CropImageView_overlayColor, TRANSLUCENT_BLACK)
            mFrameColor = WHITE//ta.getColor(R.styleable.CropImageView_frameColor, WHITE)
            mHandleColor = WHITE//ta.getColor(R.styleable.CropImageView_handleColor, WHITE)
            mGuideColor = TRANSLUCENT_WHITE/*ta.getColor(R.styleable.CropImageView_guideColor, TRANSLUCENT_WHITE)
            for (mode in ShowMode.entries) {
                if (ta.getInt(R.styleable.CropImageView_guideShowMode, 1) == mode.id) {
                    mGuideShowMode = mode
                    break
                }
            }
            for (mode in ShowMode.entries) {
                if (ta.getInt(R.styleable.CropImageView_handleShowMode, 1) == mode.id) {
                    mHandleShowMode = mode
                    break
                }
            }*/
            setGuideShowMode(mGuideShowMode)
            setHandleShowMode(mHandleShowMode)
            mHandleSize = (HANDLE_SIZE_IN_DP * mDensity).toInt()/*ta.getDimensionPixelSize(
                R.styleable.CropImageView_handleSize,
                (HANDLE_SIZE_IN_DP * mDensity).toInt()
            )*/
            mTouchPadding = 0//ta.getDimensionPixelSize(R.styleable.CropImageView_touchPadding, 0)
            mMinFrameSize = ((MIN_FRAME_SIZE_IN_DP * mDensity).toInt()).toFloat()
            /*ta.getDimensionPixelSize(
                R.styleable.CropImageView_minFrameSize,
                (MIN_FRAME_SIZE_IN_DP * mDensity).toInt()
            ).toFloat()*/
            mFrameStrokeWeight = ((FRAME_STROKE_WEIGHT_IN_DP * mDensity).toInt()).toFloat()/*ta.getDimensionPixelSize(
                R.styleable.CropImageView_frameStrokeWeight,
                (FRAME_STROKE_WEIGHT_IN_DP * mDensity).toInt()
            ).toFloat()*/
            mGuideStrokeWeight = ((GUIDE_STROKE_WEIGHT_IN_DP * mDensity).toInt()).toFloat()/*ta.getDimensionPixelSize(
                R.styleable.CropImageView_guideStrokeWeight,
                (GUIDE_STROKE_WEIGHT_IN_DP * mDensity).toInt()
            ).toFloat()*/
            mIsCropEnabled = true//ta.getBoolean(R.styleable.CropImageView_cropEnabled, true)
            mInitialFrameScale = DEFAULT_INITIAL_FRAME_SCALE/*constrain(
                ta.getFloat(
                    R.styleable.CropImageView_initialFrameScale,
                    DEFAULT_INITIAL_FRAME_SCALE
                ), 0.01f, 1.0f,
                DEFAULT_INITIAL_FRAME_SCALE
            )*/
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            //ta.recycle()
        }
    }

    // Drawing method //////////////////////////////////////////////////////////////////////////////
    private fun drawEditFrame(canvas: Canvas) {
        if (!mIsCropEnabled) return
        if (mCropMode == CropMode.CIRCLE) {
            mPaintTransparent.isFilterBitmap = true
            mPaintTransparent.color = mOverlayColor
            mPaintTransparent.style = Paint.Style.FILL
            val path = Path()
            path.addRect(
                mImageRect!!.left, mImageRect!!.top, mImageRect!!.right, mImageRect!!.bottom,
                Path.Direction.CW
            )
            path.addCircle(
                (mFrameRect!!.left + mFrameRect!!.right) / 2,
                (mFrameRect!!.top + mFrameRect!!.bottom) / 2,
                (mFrameRect!!.right - mFrameRect!!.left) / 2, Path.Direction.CCW
            )
            canvas.drawPath(path, mPaintTransparent)
        } else {
            mPaintTransparent.isFilterBitmap = true
            mPaintTransparent.color = mOverlayColor
            mPaintTransparent.style = Paint.Style.FILL
            canvas.drawRect(
                mImageRect!!.left, mImageRect!!.top, mImageRect!!.right, mFrameRect!!.top,
                mPaintTransparent
            )
            canvas.drawRect(
                mImageRect!!.left, mFrameRect!!.bottom, mImageRect!!.right, mImageRect!!.bottom,
                mPaintTransparent
            )
            canvas.drawRect(
                mImageRect!!.left, mFrameRect!!.top, mFrameRect!!.left, mFrameRect!!.bottom,
                mPaintTransparent
            )
            canvas.drawRect(
                mFrameRect!!.right, mFrameRect!!.top, mImageRect!!.right, mFrameRect!!.bottom,
                mPaintTransparent
            )
        }
        mPaintFrame.isAntiAlias = true
        mPaintFrame.isFilterBitmap = true
        mPaintFrame.style = Paint.Style.STROKE
        mPaintFrame.color = mFrameColor
        mPaintFrame.strokeWidth = mFrameStrokeWeight
        canvas.drawRect(
            mFrameRect!!.left, mFrameRect!!.top, mFrameRect!!.right, mFrameRect!!.bottom,
            mPaintFrame
        )
        if (mShowGuide) {
            mPaintFrame.color = mGuideColor
            mPaintFrame.strokeWidth = mGuideStrokeWeight
            val h1 = mFrameRect!!.left + (mFrameRect!!.right - mFrameRect!!.left) / 3.0f
            val h2 = mFrameRect!!.right - (mFrameRect!!.right - mFrameRect!!.left) / 3.0f
            val v1 = mFrameRect!!.top + (mFrameRect!!.bottom - mFrameRect!!.top) / 3.0f
            val v2 = mFrameRect!!.bottom - (mFrameRect!!.bottom - mFrameRect!!.top) / 3.0f
            canvas.drawLine(h1, mFrameRect!!.top, h1, mFrameRect!!.bottom, mPaintFrame)
            canvas.drawLine(h2, mFrameRect!!.top, h2, mFrameRect!!.bottom, mPaintFrame)
            canvas.drawLine(mFrameRect!!.left, v1, mFrameRect!!.right, v1, mPaintFrame)
            canvas.drawLine(mFrameRect!!.left, v2, mFrameRect!!.right, v2, mPaintFrame)
        }
        if (mShowHandle) {
            mPaintFrame.style = Paint.Style.FILL
            mPaintFrame.color = mHandleColor
            canvas.drawCircle(
                mFrameRect!!.left,
                mFrameRect!!.top,
                mHandleSize.toFloat(),
                mPaintFrame
            )
            canvas.drawCircle(
                mFrameRect!!.right,
                mFrameRect!!.top,
                mHandleSize.toFloat(),
                mPaintFrame
            )
            canvas.drawCircle(
                mFrameRect!!.left,
                mFrameRect!!.bottom,
                mHandleSize.toFloat(),
                mPaintFrame
            )
            canvas.drawCircle(
                mFrameRect!!.right,
                mFrameRect!!.bottom,
                mHandleSize.toFloat(),
                mPaintFrame
            )
        }
    }

    private fun setMatrix() {
        mMatrix!!.reset()
        mMatrix!!.setTranslate(mCenter.x - mImgWidth * 0.5f, mCenter.y - mImgHeight * 0.5f)
        mMatrix!!.postScale(mScale, mScale, mCenter.x, mCenter.y)
        mMatrix!!.postRotate(mAngle, mCenter.x, mCenter.y)
    }

    // Initializer /////////////////////////////////////////////////////////////////////////////////
    private fun initLayout(viewW: Int, viewH: Int) {
        mImgWidth = drawable.intrinsicWidth.toFloat()
        mImgHeight = drawable.intrinsicHeight.toFloat()
        if (mImgWidth <= 0) mImgWidth = viewW.toFloat()
        if (mImgHeight <= 0) mImgHeight = viewH.toFloat()
        val w = viewW.toFloat()
        val h = viewH.toFloat()
        val viewRatio = w / h
        val imgRatio = mImgWidth / mImgHeight
        var scale = 1.0f
        if (imgRatio >= viewRatio) {
            scale = w / mImgWidth
        } else if (imgRatio < viewRatio) {
            scale = h / mImgHeight
        }
        setCenter(PointF(paddingLeft + w * 0.5f, paddingTop + h * 0.5f))
        setScale(scale)
        initCropFrame()
        adjustRatio()
        mIsInitialized = true
    }

    private fun initCropFrame() {
        setMatrix()
        val arrayOfFloat = FloatArray(8)
        arrayOfFloat[0] = 0.0f
        arrayOfFloat[1] = 0.0f
        arrayOfFloat[2] = 0.0f
        arrayOfFloat[3] = 10000f
        arrayOfFloat[4] = 100000f
        arrayOfFloat[5] = 0.0f
        arrayOfFloat[6] = mImgWidth
        arrayOfFloat[7] = mImgHeight
        mMatrix!!.mapPoints(arrayOfFloat)
        val l = arrayOfFloat[0]
        val t = arrayOfFloat[1]
        val r = arrayOfFloat[6]
        val b = arrayOfFloat[7]
        mFrameRect = RectF(l, t, r, b)
        mImageRect = RectF(l, t, r, b)
    }

    // Touch Event /////////////////////////////////////////////////////////////////////////////////
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mIsInitialized) return false
        if (!mIsCropEnabled) return false
        if (!mIsEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onDown(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                onMove(event)
                if (mTouchArea != TouchArea.OUT_OF_BOUNDS) {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                onCancel()
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                onUp(event)
                return true
            }
        }
        return false
    }

    private fun onDown(e: MotionEvent) {
        invalidate()
        mLastX = e.x
        mLastY = e.y
        checkTouchArea(e.x, e.y)
    }

    private fun onMove(e: MotionEvent) {
        val diffX = e.x - mLastX
        val diffY = e.y - mLastY
        when (mTouchArea) {
            TouchArea.CENTER -> moveFrame(diffX, diffY)
            TouchArea.LEFT_TOP -> moveHandleLT(diffX, diffY)
            TouchArea.RIGHT_TOP -> moveHandleRT(diffX, diffY)
            TouchArea.LEFT_BOTTOM -> moveHandleLB(diffX, diffY)
            TouchArea.RIGHT_BOTTOM -> moveHandleRB(diffX, diffY)
            TouchArea.OUT_OF_BOUNDS -> {}
        }
        invalidate()
        mLastX = e.x
        mLastY = e.y
    }

    private fun onUp(e: MotionEvent) {
        if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = false
        if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = false
        mTouchArea = TouchArea.OUT_OF_BOUNDS
        invalidate()
    }

    private fun onCancel() {
        mTouchArea = TouchArea.OUT_OF_BOUNDS
        invalidate()
    }

    // Hit test ////////////////////////////////////////////////////////////////////////////////////
    private fun checkTouchArea(x: Float, y: Float) {
        if (isInsideCornerLeftTop(x, y)) {
            mTouchArea = TouchArea.LEFT_TOP
            if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
            if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideCornerRightTop(x, y)) {
            mTouchArea = TouchArea.RIGHT_TOP
            if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
            if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideCornerLeftBottom(x, y)) {
            mTouchArea = TouchArea.LEFT_BOTTOM
            if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
            if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideCornerRightBottom(x, y)) {
            mTouchArea = TouchArea.RIGHT_BOTTOM
            if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
            if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideFrame(x, y)) {
            if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            mTouchArea = TouchArea.CENTER
            return
        }
        mTouchArea = TouchArea.OUT_OF_BOUNDS
    }

    private fun isInsideFrame(x: Float, y: Float): Boolean {
        if (mFrameRect!!.left <= x && mFrameRect!!.right >= x) {
            if (mFrameRect!!.top <= y && mFrameRect!!.bottom >= y) {
                mTouchArea = TouchArea.CENTER
                return true
            }
        }
        return false
    }

    private fun isInsideCornerLeftTop(x: Float, y: Float): Boolean {
        val dx = x - mFrameRect!!.left
        val dy = y - mFrameRect!!.top
        val d = dx * dx + dy * dy
        return sq((mHandleSize + mTouchPadding).toFloat()) >= d
    }

    private fun isInsideCornerRightTop(x: Float, y: Float): Boolean {
        val dx = x - mFrameRect!!.right
        val dy = y - mFrameRect!!.top
        val d = dx * dx + dy * dy
        return sq((mHandleSize + mTouchPadding).toFloat()) >= d
    }

    private fun isInsideCornerLeftBottom(x: Float, y: Float): Boolean {
        val dx = x - mFrameRect!!.left
        val dy = y - mFrameRect!!.bottom
        val d = dx * dx + dy * dy
        return sq((mHandleSize + mTouchPadding).toFloat()) >= d
    }

    private fun isInsideCornerRightBottom(x: Float, y: Float): Boolean {
        val dx = x - mFrameRect!!.right
        val dy = y - mFrameRect!!.bottom
        val d = dx * dx + dy * dy
        return sq((mHandleSize + mTouchPadding).toFloat()) >= d
    }

    // Adjust frame ////////////////////////////////////////////////////////////////////////////////
    private fun moveFrame(x: Float, y: Float) {
        mFrameRect!!.left += x
        mFrameRect!!.right += x
        mFrameRect!!.top += y
        mFrameRect!!.bottom += y
        checkMoveBounds()
    }

    private fun moveHandleLT(diffX: Float, diffY: Float) {
        if (mCropMode == CropMode.RATIO_FREE) {
            mFrameRect!!.left += diffX
            mFrameRect!!.top += diffY
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.left -= offsetX
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.top -= offsetY
            }
            checkScaleBounds()
        } else {
            val dx = diffX
            val dy = diffX * ratioY / ratioX
            mFrameRect!!.left += dx
            mFrameRect!!.top += dy
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.left -= offsetX
                val offsetY = offsetX * ratioY / ratioX
                mFrameRect!!.top -= offsetY
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.top -= offsetY
                val offsetX = offsetY * ratioX / ratioY
                mFrameRect!!.left -= offsetX
            }
            var ox: Float
            var oy: Float
            if (!isInsideHorizontal(mFrameRect!!.left)) {
                ox = mImageRect!!.left - mFrameRect!!.left
                mFrameRect!!.left += ox
                oy = ox * ratioY / ratioX
                mFrameRect!!.top += oy
            }
            if (!isInsideVertical(mFrameRect!!.top)) {
                oy = mImageRect!!.top - mFrameRect!!.top
                mFrameRect!!.top += oy
                ox = oy * ratioX / ratioY
                mFrameRect!!.left += ox
            }
        }
    }

    private fun moveHandleRT(diffX: Float, diffY: Float) {
        if (mCropMode == CropMode.RATIO_FREE) {
            mFrameRect!!.right += diffX
            mFrameRect!!.top += diffY
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.right += offsetX
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.top -= offsetY
            }
            checkScaleBounds()
        } else {
            val dx = diffX
            val dy = diffX * ratioY / ratioX
            mFrameRect!!.right += dx
            mFrameRect!!.top -= dy
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.right += offsetX
                val offsetY = offsetX * ratioY / ratioX
                mFrameRect!!.top -= offsetY
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.top -= offsetY
                val offsetX = offsetY * ratioX / ratioY
                mFrameRect!!.right += offsetX
            }
            var ox: Float
            var oy: Float
            if (!isInsideHorizontal(mFrameRect!!.right)) {
                ox = mFrameRect!!.right - mImageRect!!.right
                mFrameRect!!.right -= ox
                oy = ox * ratioY / ratioX
                mFrameRect!!.top += oy
            }
            if (!isInsideVertical(mFrameRect!!.top)) {
                oy = mImageRect!!.top - mFrameRect!!.top
                mFrameRect!!.top += oy
                ox = oy * ratioX / ratioY
                mFrameRect!!.right -= ox
            }
        }
    }

    private fun moveHandleLB(diffX: Float, diffY: Float) {
        if (mCropMode == CropMode.RATIO_FREE) {
            mFrameRect!!.left += diffX
            mFrameRect!!.bottom += diffY
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.left -= offsetX
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.bottom += offsetY
            }
            checkScaleBounds()
        } else {
            val dx = diffX
            val dy = diffX * ratioY / ratioX
            mFrameRect!!.left += dx
            mFrameRect!!.bottom -= dy
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.left -= offsetX
                val offsetY = offsetX * ratioY / ratioX
                mFrameRect!!.bottom += offsetY
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.bottom += offsetY
                val offsetX = offsetY * ratioX / ratioY
                mFrameRect!!.left -= offsetX
            }
            var ox: Float
            var oy: Float
            if (!isInsideHorizontal(mFrameRect!!.left)) {
                ox = mImageRect!!.left - mFrameRect!!.left
                mFrameRect!!.left += ox
                oy = ox * ratioY / ratioX
                mFrameRect!!.bottom -= oy
            }
            if (!isInsideVertical(mFrameRect!!.bottom)) {
                oy = mFrameRect!!.bottom - mImageRect!!.bottom
                mFrameRect!!.bottom -= oy
                ox = oy * ratioX / ratioY
                mFrameRect!!.left += ox
            }
        }
    }

    private fun moveHandleRB(diffX: Float, diffY: Float) {
        if (mCropMode == CropMode.RATIO_FREE) {
            mFrameRect!!.right += diffX
            mFrameRect!!.bottom += diffY
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.right += offsetX
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.bottom += offsetY
            }
            checkScaleBounds()
        } else {
            val dx = diffX
            val dy = diffX * ratioY / ratioX
            mFrameRect!!.right += dx
            mFrameRect!!.bottom += dy
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.right += offsetX
                val offsetY = offsetX * ratioY / ratioX
                mFrameRect!!.bottom += offsetY
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.bottom += offsetY
                val offsetX = offsetY * ratioX / ratioY
                mFrameRect!!.right += offsetX
            }
            var ox: Float
            var oy: Float
            if (!isInsideHorizontal(mFrameRect!!.right)) {
                ox = mFrameRect!!.right - mImageRect!!.right
                mFrameRect!!.right -= ox
                oy = ox * ratioY / ratioX
                mFrameRect!!.bottom -= oy
            }
            if (!isInsideVertical(mFrameRect!!.bottom)) {
                oy = mFrameRect!!.bottom - mImageRect!!.bottom
                mFrameRect!!.bottom -= oy
                ox = oy * ratioX / ratioY
                mFrameRect!!.right -= ox
            }
        }
    }

    // Frame position correction ///////////////////////////////////////////////////////////////////
    private fun checkScaleBounds() {
        val lDiff = mFrameRect!!.left - mImageRect!!.left
        val rDiff = mFrameRect!!.right - mImageRect!!.right
        val tDiff = mFrameRect!!.top - mImageRect!!.top
        val bDiff = mFrameRect!!.bottom - mImageRect!!.bottom
        if (lDiff < 0) {
            mFrameRect!!.left -= lDiff
        }
        if (rDiff > 0) {
            mFrameRect!!.right -= rDiff
        }
        if (tDiff < 0) {
            mFrameRect!!.top -= tDiff
        }
        if (bDiff > 0) {
            mFrameRect!!.bottom -= bDiff
        }
    }

    private fun checkMoveBounds() {
        var diff = mFrameRect!!.left - mImageRect!!.left
        if (diff < 0) {
            mFrameRect!!.left -= diff
            mFrameRect!!.right -= diff
        }
        diff = mFrameRect!!.right - mImageRect!!.right
        if (diff > 0) {
            mFrameRect!!.left -= diff
            mFrameRect!!.right -= diff
        }
        diff = mFrameRect!!.top - mImageRect!!.top
        if (diff < 0) {
            mFrameRect!!.top -= diff
            mFrameRect!!.bottom -= diff
        }
        diff = mFrameRect!!.bottom - mImageRect!!.bottom
        if (diff > 0) {
            mFrameRect!!.top -= diff
            mFrameRect!!.bottom -= diff
        }
    }

    private fun isInsideHorizontal(x: Float): Boolean {
        return mImageRect!!.left <= x && mImageRect!!.right >= x
    }

    private fun isInsideVertical(y: Float): Boolean {
        return mImageRect!!.top <= y && mImageRect!!.bottom >= y
    }

    private val isWidthTooSmall: Boolean
        private get() = frameW < mMinFrameSize
    private val isHeightTooSmall: Boolean
        private get() = frameH < mMinFrameSize

    // Frame aspect ratio correction ///////////////////////////////////////////////////////////////
    private fun adjustRatio() {
        if (mImageRect == null) return
        val imgW = mImageRect!!.right - mImageRect!!.left
        val imgH = mImageRect!!.bottom - mImageRect!!.top
        val frameW = getRatioX(imgW)
        val frameH = getRatioY(imgH)
        val imgRatio = imgW / imgH
        val frameRatio = frameW / frameH
        var l = mImageRect!!.left
        var t = mImageRect!!.top
        var r = mImageRect!!.right
        var b = mImageRect!!.bottom
        if (frameRatio >= imgRatio) {
            l = mImageRect!!.left
            r = mImageRect!!.right
            val hy = (mImageRect!!.top + mImageRect!!.bottom) * 0.5f
            val hh = imgW / frameRatio * 0.5f
            t = hy - hh
            b = hy + hh
        } else if (frameRatio < imgRatio) {
            t = mImageRect!!.top
            b = mImageRect!!.bottom
            val hx = (mImageRect!!.left + mImageRect!!.right) * 0.5f
            val hw = imgH * frameRatio * 0.5f
            l = hx - hw
            r = hx + hw
        }
        val w = r - l
        val h = b - t
        val cx = l + w / 2
        val cy = t + h / 2
        val sw = w * mInitialFrameScale
        val sh = h * mInitialFrameScale
        mFrameRect = RectF(cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2)
        invalidate()
    }

    private fun getRatioX(w: Float): Float {
        return when (mCropMode) {
            CropMode.RATIO_FIT_IMAGE -> mImgWidth
            CropMode.RATIO_FREE -> w
            CropMode.RATIO_4_3 -> 4.0f
            CropMode.RATIO_3_4 -> 3.0f
            CropMode.RATIO_16_9 -> 16.0f
            CropMode.RATIO_9_16 -> 9.0f
            CropMode.RATIO_1_1, CropMode.CIRCLE -> 1.0f
            CropMode.RATIO_CUSTOM -> mCustomRatio.x
            else -> w
        }
    }

    private fun getRatioY(h: Float): Float {
        return when (mCropMode) {
            CropMode.RATIO_FIT_IMAGE -> mImgHeight
            CropMode.RATIO_FREE -> h
            CropMode.RATIO_4_3 -> 3.0f
            CropMode.RATIO_3_4 -> 4.0f
            CropMode.RATIO_16_9 -> 9.0f
            CropMode.RATIO_9_16 -> 16.0f
            CropMode.RATIO_1_1, CropMode.CIRCLE -> 1.0f
            CropMode.RATIO_CUSTOM -> mCustomRatio.y
            else -> h
        }
    }

    private val ratioX: Float
        private get() = when (mCropMode) {
            CropMode.RATIO_FIT_IMAGE -> mImgWidth
            CropMode.RATIO_4_3 -> 4.0f
            CropMode.RATIO_3_4 -> 3.0f
            CropMode.RATIO_16_9 -> 16.0f
            CropMode.RATIO_9_16 -> 9.0f
            CropMode.RATIO_1_1, CropMode.CIRCLE -> 1.0f
            CropMode.RATIO_CUSTOM -> mCustomRatio.x
            else -> 1.0f
        }
    private val ratioY: Float
        private get() {
            return when (mCropMode) {
                CropMode.RATIO_FIT_IMAGE -> mImgHeight
                CropMode.RATIO_4_3 -> 3.0f
                CropMode.RATIO_3_4 -> 4.0f
                CropMode.RATIO_16_9 -> 9.0f
                CropMode.RATIO_9_16 -> 16.0f
                CropMode.RATIO_1_1, CropMode.CIRCLE -> 1.0f
                CropMode.RATIO_CUSTOM -> mCustomRatio.y
                else -> 1.0f
            }
        }
    private val density: Float
        // Utility methods /////////////////////////////////////////////////////////////////////////////
        private get() {
            val displayMetrics = DisplayMetrics()
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
                .getMetrics(displayMetrics)
            return displayMetrics.density
        }

    private fun sq(value: Float): Float {
        return value * value
    }

    private fun constrain(`val`: Float, min: Float, max: Float, defaultVal: Float): Float {
        return if (`val` < min || `val` > max) defaultVal else `val`
    }

    // Public methods //////////////////////////////////////////////////////////////////////////////
    val imageBitmap: Bitmap?
        /**
         * Get source image bitmap
         *
         * @return src bitmap
         */
        get() = bitmap

    /**
     * Set source image bitmap
     *
     * @param bitmap src image bitmap
     */
    override fun setImageBitmap(bitmap: Bitmap) {
        mIsInitialized = false
        super.setImageBitmap(bitmap)
        updateDrawableInfo()
    }

    /**
     * Set source image resource id
     *
     * @param resId source image resource id
     */
    override fun setImageResource(resId: Int) {
        mIsInitialized = false
        super.setImageResource(resId)
        updateDrawableInfo()
    }

    /**
     * Set image drawable.
     *
     * @param drawable
     */
    override fun setImageDrawable(drawable: Drawable?) {
        mIsInitialized = false
        super.setImageDrawable(drawable)
        updateDrawableInfo()
    }

    /**
     * Set image uri
     *
     * @param uri
     */
    override fun setImageURI(uri: Uri?) {
        mIsInitialized = false
        super.setImageURI(uri)
        updateDrawableInfo()
    }

    override fun setScaleType(scaleType: ScaleType) {
        super.setScaleType(ScaleType.FIT_XY)
    }

    private fun updateDrawableInfo() {
        val d = drawable
        if (d != null) {
            initLayout(mViewWidth, mViewHeight)
        }
    }

    /**
     * Rotate image.
     *
     * @param degrees angle of ration in degrees.
     */
    fun rotateImage(degrees: RotateDegrees) {
        val source = bitmap ?: return
        val angle = degrees.value
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        val rotated = Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
        setImageBitmap(rotated)
    }

    /**
     * Saves the Bitmap as a PNG file at path 'fullPath'
     *
     * @return true if it successfully saved, false otherwise
     * path in form of(Environment.getExternalStorageDirectory().toString() +"/" + "folderName";);
     */
    fun saveBitmapInToFolder(): Boolean {
        if (croppedBitmap == null) return false
        var fileCreated = false
        var bitmapCompressed = false
        var streamClosed = false
        //val filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/" + "WallPaperApp"
        Log.i(
            "Cropping",
            "in saveBitmapInToFolder"
        )
        try {
            val filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/" + "WallPaperApp"
            val fileMake = File(filePath)
            if (!fileMake.exists()) {
                fileMake.mkdir()
            }
            imageName = filePath + "/" + "image" + System.currentTimeMillis() + ".png"
            Log.i(
                "Cropping",
                "imageName: $imageName"
            )
        }catch (e: Exception) {
            Log.i(
                "Cropping",
                "directory created: ${e.message.toString()}"
            )
        }

        val imageFile = File(imageName)
        if (imageFile.exists()) if (!imageFile.delete()) return false
        try {
            fileCreated = imageFile.createNewFile()

        } catch (e: Exception) {
            Log.i(
                "Cropping",
                "file not created: ${e.message.toString()}"
            )
        }
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(imageFile)
            bitmapCompressed = croppedBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out)
        } catch (e: Exception) {
            e.printStackTrace()
            bitmapCompressed = false
        } finally {
            if (out != null) {
                try {
                    out.flush()
                    out.close()
                    streamClosed = true
                } catch (e: IOException) {
                    e.printStackTrace()
                    streamClosed = false
                }
            }
        }
        Log.i(
            "Cropping",
            "file created: $fileCreated:$bitmapCompressed::$streamClosed"
        )
        return fileCreated && bitmapCompressed && streamClosed

    }
    fun getImageUri():Uri
    {
        return Uri.fromFile(File(imageName))
    }
    /*val imageUri: Uri
        get() {
            return Uri.fromFile(File(imageName))
        }*/
    val croppedBitmap: Bitmap?
        /**
         * Get cropped image bitmap
         *
         * @return cropped image bitmap
         */
        get() {
            val source = bitmap ?: return null
            val x: Int
            val y: Int
            var w: Int
            var h: Int
            val l = mFrameRect!!.left / mScale
            val t = mFrameRect!!.top / mScale
            val r = mFrameRect!!.right / mScale
            val b = mFrameRect!!.bottom / mScale
            x = Math.round(l - mImageRect!!.left / mScale)
            y = Math.round(t - mImageRect!!.top / mScale)
            w = Math.round(r - l)
            h = Math.round(b - t)
            if (x + w > source.width) {
                w = source.width - x
            }
            if (y + h > source.height) {
                h = source.height - y
            }
            val cropped = Bitmap.createBitmap(source, x, y, w, h, null, false)
            return if (mCropMode != CropMode.CIRCLE) cropped else getCircularBitmap(cropped)
        }
    val rectBitmap: Bitmap?
        /**
         * Get cropped rect image bitmap
         *
         *
         * This method always returns rect image.
         * (If you need a square image with CropMode.CIRCLE, you can use this method.)
         *
         * @return cropped image bitmap
         */
        get() {
            val source = bitmap ?: return null
            val x: Int
            val y: Int
            var w: Int
            var h: Int
            val l = mFrameRect!!.left / mScale
            val t = mFrameRect!!.top / mScale
            val r = mFrameRect!!.right / mScale
            val b = mFrameRect!!.bottom / mScale
            x = Math.round(l - mImageRect!!.left / mScale)
            y = Math.round(t - mImageRect!!.top / mScale)
            w = Math.round(r - l)
            h = Math.round(b - t)
            if (x + w > source.width) {
                w = source.width - x
            }
            if (y + h > source.height) {
                h = source.height - y
            }
            return Bitmap.createBitmap(source, x, y, w, h, null, false)
        }

    /**
     * Crop the square image in a circular
     *
     * @param square image bitmap
     * @return circular image bitmap
     */
    fun getCircularBitmap(square: Bitmap?): Bitmap? {
        if (square == null) return null
        val output = Bitmap.createBitmap(
            square.width, square.height,
            Bitmap.Config.ARGB_8888
        )
        val rect = Rect(0, 0, square.width, square.height)
        val canvas = Canvas(output)
        val halfWidth = square.width / 2
        val halfHeight = square.height / 2
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        canvas.drawCircle(
            halfWidth.toFloat(),
            halfHeight.toFloat(),
            Math.min(halfWidth, halfHeight).toFloat(),
            paint
        )
        paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
        canvas.drawBitmap(square, rect, rect, paint)
        return output
    }

    private val bitmap: Bitmap?
        private get() {
            var bm: Bitmap? = null
            val d = drawable
            if (d != null && d is BitmapDrawable) bm = d.bitmap
            return bm
        }
    val actualCropRect: RectF
        /**
         * Get frame position relative to the source bitmap.
         *
         * @return crop area boundaries.
         */
        get() {
            val offsetX = mImageRect!!.left / mScale
            val offsetY = mImageRect!!.top / mScale
            val l = mFrameRect!!.left / mScale - offsetX
            val t = mFrameRect!!.top / mScale - offsetY
            val r = mFrameRect!!.right / mScale - offsetX
            val b = mFrameRect!!.bottom / mScale - offsetY
            return RectF(l, t, r, b)
        }

    /**
     * Set crop mode
     *
     * @param mode crop mode
     */
    fun setCropMode(mode: CropMode) {
        if (mode == CropMode.RATIO_CUSTOM) {
            setCustomRatio(1, 1)
        } else {
            mCropMode = mode
            adjustRatio()
        }
    }

    /**
     * Set custom aspect ratio to crop frame
     *
     * @param ratioX aspect ratio X
     * @param ratioY aspect ratio Y
     */
    fun setCustomRatio(ratioX: Int, ratioY: Int) {
        if (ratioX == 0 || ratioY == 0) return
        mCropMode = CropMode.RATIO_CUSTOM
        mCustomRatio = PointF(ratioX.toFloat(), ratioY.toFloat())
        adjustRatio()
    }

    /**
     * Set image overlay color
     *
     * @param overlayColor color resId or color int(ex. 0xFFFFFFFF)
     */
    fun setOverlayColor(overlayColor: Int) {
        mOverlayColor = overlayColor
        invalidate()
    }

    /**
     * Set crop frame color
     *
     * @param frameColor color resId or color int(ex. 0xFFFFFFFF)
     */
    fun setFrameColor(frameColor: Int) {
        mFrameColor = frameColor
        invalidate()
    }

    /**
     * Set handle color
     *
     * @param handleColor color resId or color int(ex. 0xFFFFFFFF)
     */
    fun setHandleColor(handleColor: Int) {
        mHandleColor = handleColor
        invalidate()
    }

    /**
     * Set guide color
     *
     * @param guideColor color resId or color int(ex. 0xFFFFFFFF)
     */
    fun setGuideColor(guideColor: Int) {
        mGuideColor = guideColor
        invalidate()
    }

    /**
     * Set view background color
     *
     * @param bgColor color resId or color int(ex. 0xFFFFFFFF)
     */
    override fun setBackgroundColor(bgColor: Int) {
        mBackgroundColor = bgColor
        super.setBackgroundColor(mBackgroundColor)
        invalidate()
    }

    /**
     * Set crop frame minimum size in density-independent pixels.
     *
     * @param minDp crop frame minimum size in density-independent pixels
     */
    fun setMinFrameSizeInDp(minDp: Int) {
        mMinFrameSize = minDp * density
    }

    /**
     * Set crop frame minimum size in pixels.
     *
     * @param minPx crop frame minimum size in pixels
     */
    fun setMinFrameSizeInPx(minPx: Int) {
        mMinFrameSize = minPx.toFloat()
    }

    /**
     * Set handle radius in density-independent pixels.
     *
     * @param handleDp handle radius in density-independent pixels
     */
    fun setHandleSizeInDp(handleDp: Int) {
        mHandleSize = (handleDp * density).toInt()
    }

    /**
     * Set crop frame handle touch padding(touch area) in density-independent pixels.
     *
     *
     * handle touch area : a circle of radius R.(R = handle size + touch padding)
     *
     * @param paddingDp crop frame handle touch padding(touch area) in density-independent pixels
     */
    fun setTouchPaddingInDp(paddingDp: Int) {
        mTouchPadding = (paddingDp * density).toInt()
    }

    /**
     * Set guideline show mode.
     * (SHOW_ALWAYS/NOT_SHOW/SHOW_ON_TOUCH)
     *
     * @param mode guideline show mode
     */
    fun setGuideShowMode(mode: ShowMode?) {
        mGuideShowMode = mode
        when (mode) {
            ShowMode.SHOW_ALWAYS -> mShowGuide = true
            ShowMode.NOT_SHOW, ShowMode.SHOW_ON_TOUCH -> mShowGuide = false
            else -> {}
        }
        invalidate()
    }

    /**
     * Set handle show mode.
     * (SHOW_ALWAYS/NOT_SHOW/SHOW_ON_TOUCH)
     *
     * @param mode handle show mode
     */
    fun setHandleShowMode(mode: ShowMode?) {
        mHandleShowMode = mode
        when (mode) {
            ShowMode.SHOW_ALWAYS -> mShowHandle = true
            ShowMode.NOT_SHOW, ShowMode.SHOW_ON_TOUCH -> mShowHandle = false
            else -> {}
        }
        invalidate()
    }

    /**
     * Set frame stroke weight in density-independent pixels.
     *
     * @param weightDp frame stroke weight in density-independent pixels.
     */
    fun setFrameStrokeWeightInDp(weightDp: Int) {
        mFrameStrokeWeight = weightDp * density
        invalidate()
    }

    /**
     * Set guideline stroke weight in density-independent pixels.
     *
     * @param weightDp guideline stroke weight in density-independent pixels.
     */
    fun setGuideStrokeWeightInDp(weightDp: Int) {
        mGuideStrokeWeight = weightDp * density
        invalidate()
    }

    /**
     * Set whether to show crop frame.
     *
     * @param enabled should show crop frame?
     */
    fun setCropEnabled(enabled: Boolean) {
        mIsCropEnabled = enabled
        invalidate()
    }

    /**
     * Set locking the crop frame.
     *
     * @param enabled should lock crop frame?
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        mIsEnabled = enabled
    }

    /**
     * Set initial scale of the frame.(0.01 ~ 1.0)
     *
     * @param initialScale initial scale
     */
    fun setInitialFrameScale(initialScale: Float) {
        mInitialFrameScale = constrain(initialScale, 0.01f, 1.0f, DEFAULT_INITIAL_FRAME_SCALE)
    }

    private fun setScale(mScale: Float) {
        this.mScale = mScale
    }

    private fun setCenter(mCenter: PointF) {
        this.mCenter = mCenter
    }

    private val frameW: Float
        private get() = (mFrameRect!!.right - mFrameRect!!.left)
    private val frameH: Float
        private get() = (mFrameRect!!.bottom - mFrameRect!!.top)

    // Enum ////////////////////////////////////////////////////////////////////////////////////////
    private enum class TouchArea {
        OUT_OF_BOUNDS,
        CENTER,
        LEFT_TOP,
        RIGHT_TOP,
        LEFT_BOTTOM,
        RIGHT_BOTTOM
    }

    enum class CropMode(val id: Int) {
        RATIO_FIT_IMAGE(0),
        RATIO_4_3(1),
        RATIO_3_4(2),
        RATIO_1_1(3),
        RATIO_16_9(4),
        RATIO_9_16(
            5
        ),
        RATIO_FREE(6),
        RATIO_CUSTOM(7),
        CIRCLE(8)

    }

    enum class ShowMode(val id: Int) {
        SHOW_ALWAYS(1),
        SHOW_ON_TOUCH(2),
        NOT_SHOW(3)

    }

    enum class RotateDegrees(val value: Int) {
        ROTATE_90D(90),
        ROTATE_180D(180),
        ROTATE_270D(270)

    }

    // Save/Restore support ////////////////////////////////////////////////////////////////////////
    class SavedState : BaseSavedState {
        var image: Bitmap? = null
        var mode: CropMode? = null
        var backgroundColor = 0
        var overlayColor = 0
        var frameColor = 0
        var guideShowMode: ShowMode? = null
        var handleShowMode: ShowMode? = null
        var showGuide = false
        var showHandle = false
        var handleSize = 0
        var touchPadding = 0
        var minFrameSize = 0f
        var customRatioX = 0f
        var customRatioY = 0f
        var frameStrokeWeight = 0f
        var guideStrokeWeight = 0f
        var isCropEnabled = false
        var handleColor = 0
        var guideColor = 0
        var initialFrameScale = 0f

        internal constructor(superState: Parcelable?) : super(superState)
        private constructor(`in`: Parcel) : super(`in`) {
            image = `in`.readParcelable(Bitmap::class.java.classLoader)
            mode = `in`.readSerializable() as CropMode?
            backgroundColor = `in`.readInt()
            overlayColor = `in`.readInt()
            frameColor = `in`.readInt()
            guideShowMode = `in`.readSerializable() as ShowMode?
            handleShowMode = `in`.readSerializable() as ShowMode?
            showGuide = `in`.readInt() != 0
            showHandle = `in`.readInt() != 0
            handleSize = `in`.readInt()
            touchPadding = `in`.readInt()
            minFrameSize = `in`.readFloat()
            customRatioX = `in`.readFloat()
            customRatioY = `in`.readFloat()
            frameStrokeWeight = `in`.readFloat()
            guideStrokeWeight = `in`.readFloat()
            isCropEnabled = `in`.readInt() != 0
            handleColor = `in`.readInt()
            guideColor = `in`.readInt()
            initialFrameScale = `in`.readFloat()
        }

        override fun writeToParcel(out: Parcel, flag: Int) {
            super.writeToParcel(out, flag)
            out.writeParcelable(image, flag)
            out.writeSerializable(mode)
            out.writeInt(backgroundColor)
            out.writeInt(overlayColor)
            out.writeInt(frameColor)
            out.writeSerializable(guideShowMode)
            out.writeSerializable(handleShowMode)
            out.writeInt(if (showGuide) 1 else 0)
            out.writeInt(if (showHandle) 1 else 0)
            out.writeInt(handleSize)
            out.writeInt(touchPadding)
            out.writeFloat(minFrameSize)
            out.writeFloat(customRatioX)
            out.writeFloat(customRatioY)
            out.writeFloat(frameStrokeWeight)
            out.writeFloat(guideStrokeWeight)
            out.writeInt(if (isCropEnabled) 1 else 0)
            out.writeInt(handleColor)
            out.writeInt(guideColor)
            out.writeFloat(initialFrameScale)
        }

        companion object {
            @SuppressLint("ParcelCreator")
            val CREATOR: Parcelable.Creator<*> = object : Parcelable.Creator<Any?> {
                override fun createFromParcel(inParcel: Parcel): SavedState? {
                    return SavedState(inParcel)
                }

                override fun newArray(inSize: Int): Array<SavedState?> {
                    return arrayOfNulls(inSize)
                }
            }
        }

        override fun describeContents(): Int {
            return 0
        }
        /*
        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState {
                return SavedState(parcel)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }*/


    }

    companion object {
        private val TAG = CropImageView::class.java.simpleName

        // Constants ///////////////////////////////////////////////////////////////////////////////////
        private const val HANDLE_SIZE_IN_DP = 16
        private const val MIN_FRAME_SIZE_IN_DP = 50
        private const val FRAME_STROKE_WEIGHT_IN_DP = 1
        private const val GUIDE_STROKE_WEIGHT_IN_DP = 1
        private const val DEFAULT_INITIAL_FRAME_SCALE = 0.75f
    }
}