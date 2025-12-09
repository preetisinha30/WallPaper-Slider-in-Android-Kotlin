package com.np.wallpaperslider

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Parcelable
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import androidx.appcompat.widget.AppCompatImageView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Parcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {
    private val TRANSPARENT = resources.getColor(android.R.color.transparent)
    private val TRANSLUCENT_WHITE = -0x44000001
    private val WHITE = -0x1 // Fully opaque white
    private val TRANSLUCENT_BLACK = -0x45000000

    // Member variables
    private var mViewWidth = 0
    private var mViewHeight = 0
    private var mScale = 1.0f
    private var mAngle = 0.0f
    private var mImgWidth = 0.0f
    private var mImgHeight = 0.0f
    private var mIsInitialized = false
    private var mMatrix: Matrix = Matrix()
    private val mPaintTransparent = Paint()
    private val mPaintFrame = Paint()
    private val mPaintBitmap = Paint().apply { isFilterBitmap = true }
    private var mFrameRect: RectF? = null
    private var mImageRect: RectF? = null
    private var mCenter = PointF()
    private var mLastX = 0f
    private var mLastY = 0f

    // Zoom variables
    private val scaleGestureDetector: ScaleGestureDetector

    private val INITIAL_SCALE_FACTOR = 1.0f // Slight zoom for better initial appearance

    // Customizable attributes
    private var mTouchArea = TouchArea.OUT_OF_BOUNDS
    private var mCropMode = CropMode.RATIO_1_1
    private var mGuideShowMode = ShowMode.SHOW_ALWAYS
    private var mHandleShowMode = ShowMode.SHOW_ALWAYS
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
    private var mBackgroundColor = TRANSPARENT
    private var mOverlayColor = TRANSLUCENT_BLACK
    private var mFrameColor = WHITE
    private var mHandleColor = WHITE
    private var mGuideColor = TRANSLUCENT_WHITE
    private var mInitialFrameScale = DEFAULT_INITIAL_FRAME_SCALE
    private var imageName: String? = null

    // Screen aspect ratio
    private val screenAspectRatio: Float
    private val screenWidth: Float
    private val screenHeight: Float

    val mDensity = density


    init {
        val mDensity = density
        mHandleSize = (mDensity * HANDLE_SIZE_IN_DP).toInt()
        mMinFrameSize = mDensity * MIN_FRAME_SIZE_IN_DP
        mFrameStrokeWeight = mDensity * FRAME_STROKE_WEIGHT_IN_DP
        mGuideStrokeWeight = mDensity * GUIDE_STROKE_WEIGHT_IN_DP
        mTouchPadding = (mDensity * 8).toInt()
        mMinFrameSize = MIN_FRAME_SIZE_IN_DP * mDensity

        // Initialize mPaintFrame
        mPaintFrame.apply {
            isAntiAlias = true
            isFilterBitmap = true
            style = Paint.Style.STROKE
            color = mFrameColor
            strokeWidth = mFrameStrokeWeight
        }

        // Calculate screen aspect ratio
        val displayMetrics = DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels.toFloat()
        screenHeight = displayMetrics.heightPixels.toFloat()
        screenAspectRatio = screenWidth / screenHeight

        // Initialize scale gesture detector
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                Log.d(TAG, "ScaleGestureDetector: scaleFactor=${detector.scaleFactor}, focusX=${detector.focusX}, focusY=${detector.focusY}")
                val scaleFactor = detector.scaleFactor
                var newScale = mScale * scaleFactor
               /* if (newScale in MIN_ZOOM..MAX_ZOOM) {
                    mScale = newScale
                    mCenter.set(detector.focusX, detector.focusY)
                    checkScaleBounds()
                    invalidate()
                }*/
                // Set MIN_ZOOM to the initial scale to prevent zooming out too far
                if (!mIsInitialized) return false
                val initialScale = calculateInitialScale(mViewWidth, mViewHeight, mImgWidth, mImgHeight)
                val effectiveMinZoom = max(MIN_ZOOM, initialScale)

                if (newScale < effectiveMinZoom) {
                    newScale = effectiveMinZoom
                } else if (newScale > MAX_ZOOM) {
                    newScale = MAX_ZOOM
                }

                if (newScale != mScale) {
                    mScale = newScale
                    // Panning focus is the point of pinch
                    mCenter.x = detector.focusX
                    mCenter.y = detector.focusY
                    setMatrix() // Apply new scale/center immediately
                    invalidate()
                }
                return true
            }
        })

        handleStyleable(context, attrs, defStyle, mDensity)
        // Ensure scale type is MATRIX to avoid parent scaling
        super.setScaleType(ScaleType.MATRIX)
    }

    // Reset state for new image
    private fun resetState() {
        mScale = 1.0f
        mAngle = 0.0f
        mCenter.set(0f, 0f)
        mFrameRect = null
        mImageRect = null
        mIsInitialized = false
        mMatrix.reset()
        mLastX = 0f
        mLastY = 0f
        Log.d(TAG, "State reset for new image")
    }

    // Lifecycle methods
    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.image = bitmap
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
        ss.scale = mScale
        ss.centerX = mCenter.x
        ss.centerY = mCenter.y
        mFrameRect?.let {
            ss.frameLeft = it.left
            ss.frameTop = it.top
            ss.frameRight = it.right
            ss.frameBottom = it.bottom
        }
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(ss.superState)
        mCropMode = ss.mode ?: CropMode.RATIO_1_1
        mBackgroundColor = ss.backgroundColor
        mOverlayColor = ss.overlayColor
        mFrameColor = ss.frameColor
        mGuideShowMode = ss.guideShowMode ?: ShowMode.SHOW_ALWAYS
        mHandleShowMode = ss.handleShowMode ?: ShowMode.SHOW_ALWAYS
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
        mScale = constrain(ss.scale, MIN_ZOOM, MAX_ZOOM, 1.0f)
        mCenter.set(ss.centerX, ss.centerY)
        if (ss.frameLeft != 0f && ss.frameTop != 0f && ss.frameRight != 0f && ss.frameBottom != 0f) {
            mFrameRect = RectF(ss.frameLeft, ss.frameTop, ss.frameRight, ss.frameBottom)
        }
        setImageBitmap(ss.image)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        mViewWidth = r - l - paddingLeft - paddingRight
        mViewHeight = b - t - paddingTop - paddingBottom
        if (drawable != null && !mIsInitialized) initLayout(mViewWidth, mViewHeight)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(mBackgroundColor) // Clear canvas with background color
        if (mIsInitialized) {
            setMatrix()
            bitmap?.let {
                canvas.drawBitmap(it, mMatrix, mPaintBitmap)
                drawEditFrame(canvas)
            }
           /* drawable?.let {
                it.draw(canvas) // Use drawable.draw(canvas) instead of bitmap
                drawEditFrame(canvas)
            }*/
        }
    }

    private fun calculateInitialScale(viewW: Int, viewH: Int, imgW: Float, imgH: Float): Float {
        if (imgW <= 0f || imgH <= 0f) return 1.0f
        val viewRatio = viewW.toFloat() / viewH.toFloat()
        val imgRatio = imgW / imgH
        return if (imgRatio > viewRatio) {
            viewW.toFloat() / imgW
        } else {
            viewH.toFloat() / imgH
        }
    }

    private fun handleStyleable(context: Context, attrs: AttributeSet?, defStyle: Int, mDensity: Float) {
        mCropMode = CropMode.RATIO_1_1
        setGuideShowMode(mGuideShowMode)
        setHandleShowMode(mHandleShowMode)
    }

    private fun drawEditFrame(canvas: Canvas) {
        if (!mIsCropEnabled || mFrameRect == null || mImageRect == null) {
            Log.w(TAG, "Skipping drawEditFrame: cropEnabled=$mIsCropEnabled, frameRect=$mFrameRect, imageRect=$mImageRect")
            return
        }

        // Log frame rect for debugging
        Log.d(TAG, "drawEditFrame: frameRect=[left=${mFrameRect!!.left}, top=${mFrameRect!!.top}, right=${mFrameRect!!.right}, bottom=${mFrameRect!!.bottom}]")

        // Draw overlay (dimmed area outside crop frame)
        mPaintTransparent.apply {
            isFilterBitmap = true
            color = mOverlayColor
            style = Paint.Style.FILL
        }
        if (mCropMode == CropMode.CIRCLE) {
            val path = Path().apply {
                addRect(mImageRect!!, Path.Direction.CW)
                addCircle(
                    (mFrameRect!!.left + mFrameRect!!.right) / 2,
                    (mFrameRect!!.top + mFrameRect!!.bottom) / 2,
                    (mFrameRect!!.right - mFrameRect!!.left) / 2,
                    Path.Direction.CCW
                )
            }
            canvas.drawPath(path, mPaintTransparent)
        } else {
            canvas.drawRect(mImageRect!!.left, mImageRect!!.top, mImageRect!!.right, mFrameRect!!.top, mPaintTransparent)
            canvas.drawRect(mImageRect!!.left, mFrameRect!!.bottom, mImageRect!!.right, mImageRect!!.bottom, mPaintTransparent)
            canvas.drawRect(mImageRect!!.left, mFrameRect!!.top, mFrameRect!!.left, mFrameRect!!.bottom, mPaintTransparent)
            canvas.drawRect(mFrameRect!!.right, mFrameRect!!.top, mImageRect!!.right, mFrameRect!!.bottom, mPaintTransparent)
            Log.d(TAG, "drawEditFrame-mImageRect: left=${mImageRect!!.left}, top=${mImageRect!!.top}, bottom=${mImageRect!!.bottom}, right=${mImageRect!!.right}")
        }

        // Draw crop frame (ensure it's on top)
        mPaintFrame.apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = mFrameColor // Opaque white
            strokeWidth = mFrameStrokeWeight
            alpha = 255 // Ensure full opacity
        }
        canvas.drawRect(mFrameRect!!, mPaintFrame)

        // Draw guides
        if (mShowGuide) {
            mPaintFrame.apply {
                color = mGuideColor
                strokeWidth = mGuideStrokeWeight
            }
            /*val h1 = mFrameRect!!.left + (mFrameRect!!.right - mFrameRect!!.left) / 4.0f
            val h2 = mFrameRect!!.right - (mFrameRect!!.right - mFrameRect!!.left) / 4.0f
            val v1 = mFrameRect!!.top + (mFrameRect!!.bottom - mFrameRect!!.top) / 4.0f
            val v2 = mFrameRect!!.bottom - (mFrameRect!!.bottom - mFrameRect!!.top) / 4.0f*/
            val h1 = mFrameRect!!.left + mFrameRect!!.width() / 3.0f
            val h2 = mFrameRect!!.right - mFrameRect!!.width() / 3.0f
            val v1 = mFrameRect!!.top + mFrameRect!!.height() / 3.0f
            val v2 = mFrameRect!!.bottom - mFrameRect!!.height() / 3.0f
            canvas.drawLine(h1, mFrameRect!!.top, h1, mFrameRect!!.bottom, mPaintFrame)
            canvas.drawLine(h2, mFrameRect!!.top, h2, mFrameRect!!.bottom, mPaintFrame)
            canvas.drawLine(mFrameRect!!.left, v1, mFrameRect!!.right, v1, mPaintFrame)
            canvas.drawLine(mFrameRect!!.left, v2, mFrameRect!!.right, v2, mPaintFrame)
        }

        // Draw corner handles only
        if (mShowHandle) {
            mPaintFrame.apply {
                style = Paint.Style.FILL
                color = mHandleColor
                alpha = 255
            }
            // Corner handles
            val handleSizeFloat = mHandleSize.toFloat()
            canvas.drawCircle(mFrameRect!!.left, mFrameRect!!.top, handleSizeFloat, mPaintFrame)
            canvas.drawCircle(mFrameRect!!.right, mFrameRect!!.top, handleSizeFloat, mPaintFrame)
            canvas.drawCircle(mFrameRect!!.left, mFrameRect!!.bottom, handleSizeFloat, mPaintFrame)
            canvas.drawCircle(mFrameRect!!.right, mFrameRect!!.bottom, handleSizeFloat, mPaintFrame)
        }
    }
/*
    private fun setMatrix() {
        if (mImgWidth <= 0 || mImgHeight <= 0 || mScale <= 0) {
            Log.w(TAG, "Invalid matrix parameters: mImgWidth=$mImgWidth, mImgHeight=$mImgHeight, mScale=$mScale")
            return
        }
        mMatrix.reset()
        mMatrix.setTranslate(mCenter.x - mImgWidth * 0.5f, mCenter.y - mImgHeight * 0.5f)
        mMatrix.postScale(mScale, mScale, mCenter.x, mCenter.y)
        mMatrix.postRotate(mAngle, mCenter.x, mCenter.y)

        updateImageRect()
        checkScaleBounds()
    }
*/
private fun setMatrix() {
    if (mImgWidth <= 0 || mImgHeight <= 0) return

    // --- Core Matrix Logic (Applies Scale/Rotation/Translation) ---
    /*fun applyTransformations(center: PointF) {
        mMatrix.reset()

        // 1. Scale and Rotate: Pivot around the image's original center (mImgWidth/2, mImgHeight/2)
        // This is generally more stable as the pivot point remains constant relative to the drawable.
        mMatrix.postScale(mScale, mScale, mImgWidth * 0.5f, mImgHeight * 0.5f)
        mMatrix.postRotate(mAngle, mImgWidth * 0.5f, mImgHeight * 0.5f)

        // 2. Translate: Move the center of the transformed image
        // (which is now at mImgWidth/2, mImgHeight/2 in matrix space) to the desired view center (mCenter).

        // Calculate the required translation to move the scaled center to 'center'
        val scaledCenterX = mImgWidth * mScale * 0.5f
        val scaledCenterY = mImgHeight * mScale * 0.5f

        // The required translation distance is the difference between the desired view center (mCenter)
        // and the current scaled image center (relative to the drawable origin, which is 0,0)
        val dx = center.x - scaledCenterX
        val dy = center.y - scaledCenterY

        mMatrix.postTranslate(dx, dy)
    }*/
    fun applyTransformations(center: PointF) {
        mMatrix.reset()

        // 1. Scale and Rotate: Pivot around the image's original center (mImgWidth/2, mImgHeight/2)
        val pivotX = mImgWidth * 0.5f
        val pivotY = mImgHeight * 0.5f
        mMatrix.postScale(mScale, mScale, pivotX, pivotY)
        mMatrix.postRotate(mAngle, pivotX, pivotY)

        // 2. Translate: Move the center of the transformed image
        val currentCenter = floatArrayOf(pivotX, pivotY)
        mMatrix.mapPoints(currentCenter)

        val dx = center.x - currentCenter[0]
        val dy = center.y - currentCenter[1]

        mMatrix.postTranslate(dx, dy)
    }

    // --- Pass 1: Apply current transformation for bounds check ---
    applyTransformations(mCenter)
    imageMatrix = mMatrix

    updateImageRect()
    checkPanBounds() // This checks the bounds based on mImageRect and clamps mCenter

    // --- Pass 2: Re-apply transformation with potentially clamped mCenter ---
    // This ensures the next draw cycle uses the correct, constrained position.
    applyTransformations(mCenter)
    imageMatrix = mMatrix

    // Update the frame based on the final, corrected image position
    updateCropFrame()
}
    private fun updateCropFrame() {
        // After pan/zoom, ensure the frame is still constrained to the new image boundaries
        checkScaleBounds()
        checkMoveBounds()
    }
    private fun updateImageRect() {
        if (mImgWidth <= 0 || mImgHeight <= 0) return
        val points = floatArrayOf(
            0f, 0f, // Top-left
            mImgWidth, 0f, // Top-right
            mImgWidth, mImgHeight, // Bottom-right
            0f, mImgHeight // Bottom-left
        )
        mMatrix.mapPoints(points)
        val left = points.asSequence().filterIndexed { index, _ -> index % 2 == 0 }.minOrNull() ?: 0f
        val right = points.asSequence().filterIndexed { index, _ -> index % 2 == 0 }.maxOrNull() ?: mImgWidth
        val top = points.asSequence().filterIndexed { index, _ -> index % 2 == 1 }.minOrNull() ?: 0f
        val bottom = points.asSequence().filterIndexed { index, _ -> index % 2 == 1 }.maxOrNull() ?: mImgHeight
        mImageRect = RectF(left, top, right, bottom)
        Log.d(TAG, "updateImageRect: left=$left, top=$top, right=$right, bottom=$bottom")
    }

    private fun initLayout(viewW: Int, viewH: Int) {
        mImgWidth = drawable?.intrinsicWidth?.toFloat() ?: viewW.toFloat()
        mImgHeight = drawable?.intrinsicHeight?.toFloat() ?: viewH.toFloat()
        if (mImgWidth <= 0 || mImgHeight <= 0) {
            Log.e(TAG, "Invalid image dimensions: width=$mImgWidth, height=$mImgHeight")
            return
        }

        // Use device screen aspect ratio for wallpaper preview
        /*val displayMetrics = context.resources.displayMetrics
        val screenAspect = displayMetrics.widthPixels.toFloat() / displayMetrics.heightPixels
        val imageAspect = mImgWidth / mImgHeight

        // Scale image to fit screen aspect ratio
        mScale = if (imageAspect > screenAspect) {
            displayMetrics.heightPixels.toFloat() / mImgHeight
        } else {
            displayMetrics.widthPixels.toFloat() / mImgWidth
        }
        mScale = mScale.coerceIn(MIN_ZOOM, MAX_ZOOM)*/
        mScale = 1.0f

        mCenter.set(paddingLeft + viewW * 0.5f, paddingTop + viewH * 0.5f)
//new addition
        // Ensure image is fully visible initially (Fit Center logic)
        val viewRatio = viewW.toFloat() / viewH.toFloat()
        val imgRatio = mImgWidth / mImgHeight

        if (imgRatio > viewRatio) {
            // Image is wider, scale based on width
            mScale = viewW.toFloat() / mImgWidth
        } else {
            // Image is taller, scale based on height
            mScale = viewH.toFloat() / mImgHeight
        }
        //till here
        initCropFrame()
        mIsInitialized = true
        Log.d(TAG, "initLayout: scale=$mScale, centerX=${mCenter.x}, centerY=${mCenter.y}")
    }

    private fun initCropFrame() {
        setMatrix()
        if (mImgWidth <= 0 || mImgHeight <= 0 || mImageRect == null) {
            Log.e(TAG, "Cannot init crop frame: mImgWidth=$mImgWidth, mImgHeight=$mImgHeight, mImageRect=$mImageRect")
            return
        }
/*
        // Initialize crop frame to match mImageRect
        mFrameRect = RectF(mImageRect!!.left, mImageRect!!.top, mImageRect!!.right, mImageRect!!.bottom)
        checkScaleBounds() // Adjust to maintain screen aspect ratio
        //mHandleSize = (min(mViewWidth, mViewHeight) * 0.05f).toInt()
        //mTouchPadding = (min(mViewWidth, mViewHeight) * 0.02f).toInt()
        //mMinFrameSize = mDensity * MIN_FRAME_SIZE_IN_DP
        //mMinFrameSize = min(mViewWidth, mViewHeight) * 0.1f
        val minViewDimension = min(mViewWidth, mViewWidth).toFloat()
        mHandleSize = (minViewDimension * 0.05f).toInt().coerceAtLeast(30) // Ensure handles are touchable

        mTouchPadding = (minViewDimension * 0.02f).toInt().coerceAtLeast(15)
        mMinFrameSize = max(
            minViewDimension * MIN_FRAME_SIZE_PERCENTAGE,
            MIN_FRAME_SIZE_IN_DP * mDensity // 60dp minimum for easy touching/visual clarity
        )
        Log.d(TAG, "initCropFrame: frameRect=[left=${mFrameRect?.left}, top=${mFrameRect?.top}, right=${mFrameRect?.right}, bottom=${mFrameRect?.bottom}]")
        */
        // Use the 80% scale to set the initial frame size
        val imgW = mImageRect!!.width()
        val imgH = mImageRect!!.height()

        var frameW = imgW * INITIAL_FRAME_SCALE
        var frameH = imgH * INITIAL_FRAME_SCALE

        // Adjust for aspect ratio if not RATIO_FREE
        if (mCropMode != CropMode.RATIO_FREE) {
            if (frameW / frameH > aspectRatio) {
                // Frame is too wide, constrain by height
                frameW = frameH * aspectRatio
            } else {
                // Frame is too tall, constrain by width
                frameH = frameW / aspectRatio
            }
        }
        val cx = mImageRect!!.centerX()
        val cy = mImageRect!!.centerY()

        // Set the initial frame to 80% of the image, centered
        mFrameRect = RectF(cx - frameW / 2, cy - frameH / 2, cx + frameW / 2, cy + frameH / 2)

        // Ensure all subsequent handle movements respect the small mMinFrameSize
        // checkScaleBounds() will now only enforce the *small* minimum size.
        checkScaleBounds()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mIsInitialized || !mIsEnabled) {
            Log.e(TAG, "Touch ignored: mIsInitialized=$mIsInitialized, mIsEnabled=$mIsEnabled")
            return false
        }
        parent.requestDisallowInterceptTouchEvent(true)
        Log.d(TAG, "Touch event: action=${event.actionMasked}, pointerCount=${event.pointerCount}")
        scaleGestureDetector.onTouchEvent(event)
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                onDown(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleGestureDetector.isInProgress) {
                    onMove(event)
                    if (mTouchArea != TouchArea.OUT_OF_BOUNDS) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                onCancel()
                return true
            }
        }
        return false
    }

    private fun onDown(e: MotionEvent) {
        mLastX = e.x
        mLastY = e.y
        checkTouchArea(e.x, e.y)
        Log.d(TAG, "Touch down: x=${e.x}, y=${e.y}, touchArea=$mTouchArea")
        //new addition
        /*if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH && mTouchArea != TouchArea.OUT_OF_BOUNDS) {
            mShowHandle = true
        }
        if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH && (mTouchArea == TouchArea.CENTER || mTouchArea.name.startsWith("TouchArea.R"))) {
            mShowGuide = true
        }*/
        //till here
        invalidate()
    }

    private fun onMove(e: MotionEvent) {
        val diffX = e.x - mLastX
        val diffY = e.y - mLastY
        Log.d(TAG, "Move: diffX=$diffX, diffY=$diffY, touchArea=$mTouchArea")
        when (mTouchArea) {
            TouchArea.CENTER -> moveFrame(diffX, diffY)
            TouchArea.LEFT_TOP -> moveHandleLT(diffX, diffY)
            TouchArea.RIGHT_TOP -> moveHandleRT(diffX, diffY)
            TouchArea.LEFT_BOTTOM -> moveHandleLB(diffX, diffY)
            TouchArea.RIGHT_BOTTOM -> moveHandleRB(diffX, diffY)
            TouchArea.PAN -> panImage(diffX, diffY)
            else -> {}
        }
        mLastX = e.x
        mLastY = e.y
        invalidate()
    }

    private fun onCancel() {
        if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = false
        if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = false
        mTouchArea = TouchArea.OUT_OF_BOUNDS
        invalidate()
    }

    private fun checkTouchArea(x: Float, y: Float) {
        if (mFrameRect == null) return

        // Check corners (higher priority)
        if (isInsideCornerLeftTop(x, y)) {
            mTouchArea = TouchArea.LEFT_TOP
           // if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
           // if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideCornerRightTop(x, y)) {
            mTouchArea = TouchArea.RIGHT_TOP
           // if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
           // if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideCornerLeftBottom(x, y)) {
            mTouchArea = TouchArea.LEFT_BOTTOM
           // if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
           // if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideCornerRightBottom(x, y)) {
            mTouchArea = TouchArea.RIGHT_BOTTOM
          //  if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
          //  if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }

        // Check if inside frame for moving
        if (isInsideFrame(x, y)) {
            mTouchArea = TouchArea.CENTER
          //  if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }

        // Default to panning
        mTouchArea = TouchArea.PAN
    }

    private fun isInsideFrame(x: Float, y: Float): Boolean {
        return mFrameRect?.let { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom } ?: false
    }

    private fun isInsideCornerLeftTop(x: Float, y: Float): Boolean {
        val dx = x - (mFrameRect?.left ?: return false)
        val dy = y - (mFrameRect?.top ?: return false)
        return sq((mHandleSize + mTouchPadding).toFloat()) >= dx * dx + dy * dy
    }

    private fun isInsideCornerRightTop(x: Float, y: Float): Boolean {
        val dx = x - (mFrameRect?.right ?: return false)
        val dy = y - (mFrameRect?.top ?: return false)
        return sq((mHandleSize + mTouchPadding).toFloat()) >= dx * dx + dy * dy
    }

    private fun isInsideCornerLeftBottom(x: Float, y: Float): Boolean {
        val dx = x - (mFrameRect?.left ?: return false)
        val dy = y - (mFrameRect?.bottom ?: return false)
        return sq((mHandleSize + mTouchPadding).toFloat()) >= dx * dx + dy * dy
    }

    private fun isInsideCornerRightBottom(x: Float, y: Float): Boolean {
        val dx = x - (mFrameRect?.right ?: return false)
        val dy = y - (mFrameRect?.bottom ?: return false)
        return sq((mHandleSize + mTouchPadding).toFloat()) >= dx * dx + dy * dy
    }

    private fun moveFrame(x: Float, y: Float) {
        mFrameRect?.offset(x, y)
        checkMoveBounds()
    }

    private fun panImage(diffX: Float, diffY: Float) {
        mCenter.offset(diffX, diffY)
        checkPanBounds()
        invalidate()
    }

    private fun checkPanBounds() {
        if (mImageRect == null || mFrameRect == null) return
        val viewLeft = paddingLeft.toFloat()
        val viewRight = mViewWidth.toFloat() - paddingRight
        val viewTop = paddingTop.toFloat()
        val viewBottom = mViewHeight.toFloat() - paddingBottom
        val viewWidth = viewRight - viewLeft
        val viewHeight = viewBottom - viewTop

        // Ensure mImageRect stays within view bounds
        /*val imageWidth = mImageRect!!.width()
        val imageHeight = mImageRect!!.height()
        val minX = if (imageWidth > viewWidth) viewRight - imageWidth else viewLeft
        val maxX = if (imageWidth > viewWidth) viewLeft + imageWidth else viewRight
        val minY = if (imageHeight > viewHeight) viewBottom - imageHeight else viewTop
        val maxY = if (imageHeight > viewHeight) viewTop + imageHeight else viewBottom

        Log.d(TAG, "checkPanBounds: imageWidth=$imageWidth, imageHeight=$imageHeight, viewWidth=$viewWidth, viewHeight=$viewHeight")
        Log.d(TAG, "checkPanBounds: minX=$minX, maxX=$maxX, minY=$minY, maxY=$maxY")

        mCenter.x = mCenter.x.coerceIn(minX, maxX)
        mCenter.y = mCenter.y.coerceIn(minY, maxY)
        */
        // Horizontal Bounds
        val minTranslationX = viewRight - mImageRect!!.right
        val maxTranslationX = viewLeft - mImageRect!!.left

        // Vertical Bounds
        val minTranslationY = viewBottom - mImageRect!!.bottom
        val maxTranslationY = viewTop - mImageRect!!.top

        // We only need to check bounds if the image is larger than the viewable area (which it should be after initLayout)
        if (mImageRect!!.width() > viewRight - viewLeft) {
            // Clamp the center.x based on the image's current bounds and desired view bounds
           // val currentTranslationX = mCenter.x - (mImgWidth * 0.5f * mScale)

            // This translation should be between the min and max required to cover the screen.
           // mCenter.x = mCenter.x.coerceIn(mCenter.x + minTranslationX, mCenter.x + maxTranslationX)
            // Min X: When image right edge is exactly at view right edge.
            val minX = viewRight - (mImageRect!!.right - mCenter.x)
            // Max X: When image left edge is exactly at view left edge.
            val maxX = viewLeft + (mCenter.x - mImageRect!!.left)

            mCenter.x = mCenter.x.coerceIn(minX, maxX)
        }else {
            // If image is smaller than view, center it and prevent panning.
            mCenter.x = viewLeft + (viewRight - viewLeft) / 2
        }

        if (mImageRect!!.height() > viewBottom - viewTop) {
           // mCenter.y = mCenter.y.coerceIn(mCenter.y + minTranslationY, mCenter.y + maxTranslationY)
            // Min Y: When image bottom edge is exactly at view bottom edge.
            val minY = viewBottom - (mImageRect!!.bottom - mCenter.y)
            // Max Y: When image top edge is exactly at view top edge.
            val maxY = viewTop + (mCenter.y - mImageRect!!.top)

            mCenter.y = mCenter.y.coerceIn(minY, maxY)
        }
        else {
            // If image is smaller than view, center it and prevent panning.
            mCenter.y = viewTop + (viewBottom - viewTop) / 2
        }
        //setMatrix()
    }

    private fun moveHandleLT(diffX: Float, diffY: Float) {
        mFrameRect?.let { frame ->
            mImageRect?.let { image ->
                if (mCropMode == CropMode.RATIO_FREE) {
                    frame.left = max(image.left, min(frame.left + diffX, frame.right - mMinFrameSize))
                    frame.top = max(image.top, min(frame.top + diffY, frame.bottom - mMinFrameSize))
                } else {
                /*   val dx = diffX
                    val dy = dx * ratioY / ratioX
                    frame.left = max(image.left, min(frame.left + dx, frame.right - mMinFrameSize))
                    frame.top = max(image.top, min(frame.top + dy, frame.bottom - mMinFrameSize))
                    if (frame.width() < mMinFrameSize) {
                        frame.left = frame.right - mMinFrameSize
                        frame.top = frame.bottom - (mMinFrameSize * ratioY / ratioX)
                    }
                    if (frame.height() < mMinFrameSize) {
                        frame.top = frame.bottom - mMinFrameSize
                        frame.left = frame.right - (mMinFrameSize * ratioX / ratioY)
                    }
                */
                    // Ratio constrained mode
                    val currentWidth = frame.width()
                    val currentHeight = frame.height()
                    val targetDelta = if (abs(diffX) > abs(diffY)) diffX else diffY

                    // New Left boundary: must decrease when moving left (neg targetDelta)
                    val newLeft = frame.left + targetDelta
                    // New Top boundary: must decrease when moving up (neg targetDelta)
                    val newTop = frame.top + targetDelta / aspectRatio

                    // Calculate the resulting width/height if we constrain by the bounds hit first
                    var constrainedLeft = max(image.left, min(newLeft, frame.right - mMinFrameSize))
                    var constrainedTop = max(image.top, min(newTop, frame.bottom - mMinFrameSize))

                    // Calculate which boundary was hit (X or Y)
                    val newWidthX = frame.right - constrainedLeft
                    val newHeightY = frame.bottom - constrainedTop

                    if (newWidthX / aspectRatio > newHeightY) {
                        // Y is the limiting factor (new height is smaller than required)
                        frame.top = constrainedTop
                        frame.left = frame.right - newHeightY * aspectRatio
                    } else {
                        // X is the limiting factor
                        frame.left = constrainedLeft
                        frame.top = frame.bottom - newWidthX / aspectRatio
                    }
                }
                checkScaleBounds()
            }
        }
    }

    private fun moveHandleRT(diffX: Float, diffY: Float) {
        mFrameRect?.let { frame ->
            mImageRect?.let { image ->
                if (mCropMode == CropMode.RATIO_FREE) {
                    frame.right = max(frame.left + mMinFrameSize, min(frame.right + diffX, image.right))
                    frame.top = max(image.top, min(frame.top + diffY, frame.bottom - mMinFrameSize))
                } else {
                  /*  val dx = diffX
                    val dy = dx * ratioY / ratioX
                    frame.right = max(frame.left + mMinFrameSize, min(frame.right + dx, image.right))
                    frame.top = max(image.top, min(frame.top + dy, frame.bottom - mMinFrameSize))
                    if (frame.width() < mMinFrameSize) {
                        frame.right = frame.left + mMinFrameSize
                        frame.top = frame.bottom - (mMinFrameSize * ratioY / ratioX)
                    }
                    if (frame.height() < mMinFrameSize) {
                        frame.top = frame.bottom - mMinFrameSize
                        frame.right = frame.left + (mMinFrameSize * ratioX / ratioY)
                    }*/
                    val targetDelta = if (abs(diffX) > abs(diffY)) diffX else diffY

                    // X: Right moves with diffX
                    val newRight = frame.right + targetDelta
                    // Y: Top moves OPPOSITE to diffX for ratio. A positive dx (drag right) means UP (negative y change).
                    val ySign = if (targetDelta > 0) -1.0f else 1.0f
                    val newTop = frame.top + (targetDelta / aspectRatio) * ySign

                    var constrainedRight = max(frame.left + mMinFrameSize, min(newRight, image.right))
                    var constrainedTop = max(image.top, min(newTop, frame.bottom - mMinFrameSize))

                    val newWidthX = constrainedRight - frame.left
                    val newHeightY = frame.bottom - constrainedTop

                    if (newWidthX / aspectRatio > newHeightY) {
                        // Y is the limiting factor (new height is smaller than required)
                        frame.top = constrainedTop
                        frame.right = frame.left + newHeightY * aspectRatio
                    } else {
                        // X is the limiting factor
                        frame.right = constrainedRight
                        frame.top = frame.bottom - newWidthX / aspectRatio
                    }
                }
                checkScaleBounds()
            }
        }
    }

    private fun moveHandleLB(diffX: Float, diffY: Float) {
        mFrameRect?.let { frame ->
            mImageRect?.let { image ->
                if (mCropMode == CropMode.RATIO_FREE) {
                    frame.left = max(image.left, min(frame.left + diffX, frame.right - mMinFrameSize))
                    frame.bottom = max(frame.top + mMinFrameSize, min(frame.bottom + diffY, image.bottom))
                } else {
                 /*   val dx = diffX
                    val dy = dx * ratioY / ratioX
                    frame.left = max(image.left, min(frame.left + dx, frame.right - mMinFrameSize))
                    frame.bottom = max(frame.top + mMinFrameSize, min(frame.bottom + dy, image.bottom))
                    if (frame.width() < mMinFrameSize) {
                        frame.left = frame.right - mMinFrameSize
                        frame.bottom = frame.top + (mMinFrameSize * ratioY / ratioX)
                    }
                    if (frame.height() < mMinFrameSize) {
                        frame.bottom = frame.top + mMinFrameSize
                        frame.left = frame.right - (mMinFrameSize * ratioX / ratioY)
                    }*/
                    // --- FIX: Ratio constrained mode for Bottom-Left (Left decreases, Bottom increases) ---
                    val targetDelta = if (abs(diffX) > abs(diffY)) diffX else diffY

                    // X: Left moves with diffX
                    val newLeft = frame.left + targetDelta
                    // Y: Bottom moves OPPOSITE to diffX for ratio. A positive dx (drag right) means DOWN (positive y change).
                    val ySign = if (targetDelta > 0) -1.0f else 1.0f
                    val newBottom = frame.bottom - (targetDelta / aspectRatio) * ySign // Need opposite sign

                    var constrainedLeft = max(image.left, min(newLeft, frame.right - mMinFrameSize))
                    var constrainedBottom = max(frame.top + mMinFrameSize, min(newBottom, image.bottom))

                    val newWidthX = frame.right - constrainedLeft
                    val newHeightY = constrainedBottom - frame.top

                    if (newWidthX / aspectRatio > newHeightY) {
                        // Y is the limiting factor (new height is smaller than required)
                        frame.bottom = constrainedBottom
                        frame.left = frame.right - newHeightY * aspectRatio
                    } else {
                        // X is the limiting factor
                        frame.left = constrainedLeft
                        frame.bottom = frame.top + newWidthX / aspectRatio
                    }
                }
                checkScaleBounds()
            }
        }
    }

    private fun moveHandleRB(diffX: Float, diffY: Float) {
        mFrameRect?.let { frame ->
            mImageRect?.let { image ->
                if (mCropMode == CropMode.RATIO_FREE) {
                    frame.right = max(frame.left + mMinFrameSize, min(frame.right + diffX, image.right))
                    frame.bottom = max(frame.top + mMinFrameSize, min(frame.bottom + diffY, image.bottom))
                } else {
                  /*  val dx = diffX
                    val dy = dx * ratioY / ratioX
                    frame.right = max(frame.left + mMinFrameSize, min(frame.right + dx, image.right))
                    frame.bottom = max(frame.top + mMinFrameSize, min(frame.bottom + dy, image.bottom))
                    if (frame.width() < mMinFrameSize) {
                        frame.right = frame.left + mMinFrameSize
                        frame.bottom = frame.top + (mMinFrameSize * ratioY / ratioX)
                    }
                    if (frame.height() < mMinFrameSize) {
                        frame.bottom = frame.top + mMinFrameSize
                        frame.right = frame.left + (mMinFrameSize * ratioX / ratioY)
                    }*/
                    // Ratio constrained mode
                    val targetDelta = if (abs(diffX) > abs(diffY)) diffX else diffY

                    // New Right boundary: must increase when moving right (pos targetDelta)
                    val newRight = frame.right + targetDelta
                    // New Bottom boundary: must increase when moving down (pos targetDelta)
                    val newBottom = frame.bottom + targetDelta / aspectRatio

                    var constrainedRight = max(frame.left + mMinFrameSize, min(newRight, image.right))
                    var constrainedBottom = max(frame.top + mMinFrameSize, min(newBottom, image.bottom))

                    val newWidthX = constrainedRight - frame.left
                    val newHeightY = constrainedBottom - frame.top

                    if (newWidthX / aspectRatio > newHeightY) {
                        // Y is the limiting factor (new height is smaller than required)
                        frame.bottom = constrainedBottom
                        frame.right = frame.left + newHeightY * aspectRatio
                    } else {
                        // X is the limiting factor
                        frame.right = constrainedRight
                        frame.bottom = frame.top + newWidthX / aspectRatio
                    }
                }
                checkScaleBounds()
            }
        }
    }

   /* private fun checkScaleBounds() {
        mFrameRect?.let { frame ->
            mImageRect?.let { image ->
                if (frame.width() < mMinFrameSize) {
                    // Adjust width to min size, centered
                    val centerX = frame.centerX()
                    frame.left = centerX - mMinFrameSize / 2
                    frame.right = centerX + mMinFrameSize / 2
                }
                if (frame.height() < mMinFrameSize) {
                    // Adjust height to min size, centered
                    val centerY = frame.centerY()
                    frame.top = centerY - mMinFrameSize / 2
                    frame.bottom = centerY + mMinFrameSize / 2
                }
                // Ensure frame stays within image bounds
                frame.left = max(image.left, min(frame.left, image.right - mMinFrameSize))
                frame.right = min(image.right, max(frame.right, image.left + mMinFrameSize))
                frame.top = max(image.top, min(frame.top, image.bottom - mMinFrameSize))
                frame.bottom = min(image.bottom, max(frame.bottom, image.top + mMinFrameSize))
/*
                // Maintain screen aspect ratio
                val screenAspect = context.resources.displayMetrics.widthPixels.toFloat() / context.resources.displayMetrics.heightPixels
                val currentRatio = frame.width() / frame.height()
                if (currentRatio > screenAspect) {
                    // Too wide, adjust width
                    val newWidth = frame.height() * screenAspect
                    val centerX = frame.centerX()
                    frame.left = centerX - newWidth / 2
                    frame.right = centerX + newWidth / 2
                } else if (currentRatio < screenAspect) {
                    // Too tall, adjust height
                    val newHeight = frame.width() / screenAspect
                    val centerY = frame.centerY()
                    frame.top = centerY - newHeight / 2
                    frame.bottom = centerY + newHeight / 2
                }*/
            }
        }
    }*/

    private fun checkScaleBounds() {
        mFrameRect?.let { frame ->
            mImageRect?.let { image ->

                // 1. Enforce Minimum Size
                if (frame.width() < mMinFrameSize || frame.height() < mMinFrameSize) {
                    val centerX = frame.centerX()
                    val centerY = frame.centerY()

                    frame.left = centerX - mMinFrameSize / 2
                    frame.right = centerX + mMinFrameSize / 2
                    frame.top = centerY - mMinFrameSize / 2
                    frame.bottom = centerY + mMinFrameSize / 2
                }

                // 2. Enforce Image Bounds
                val width = frame.width()
                val height = frame.height()

                // Clamp frame to image bounds, moving it if necessary
                frame.left = max(image.left, min(frame.left, image.right - width))
                frame.right = frame.left + width
                frame.top = max(image.top, min(frame.top, image.bottom - height))
                frame.bottom = frame.top + height
            }
        }
    }

    private fun checkMoveBounds() {
        mFrameRect?.let { frame ->
            mImageRect?.let { image ->
                val width = frame.width()
                val height = frame.height()
                frame.left = max(image.left, min(frame.left, image.right - width))
                frame.right = frame.left + width
                frame.top = max(image.top, min(frame.top, image.bottom - height))
                frame.bottom = frame.top + height
            }
        }
    }

    private fun isInsideHorizontal(x: Float): Boolean {
        return mImageRect?.let { x >= it.left && x <= it.right } ?: false
    }

    private fun isInsideVertical(y: Float): Boolean {
        return mImageRect?.let { y >= it.top && y <= it.bottom } ?: false
    }

    private val isWidthTooSmall: Boolean
        get() = frameW < mMinFrameSize

    private val isHeightTooSmall: Boolean
        get() = frameH < mMinFrameSize

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
        } else {
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
        checkScaleBounds()
        invalidate()
    }

    private fun getRatioX(w: Float): Float {
        return when (mCropMode) {
            CropMode.RATIO_FIT_IMAGE -> mImgWidth
            CropMode.RATIO_FREE -> screenWidth // Use screen aspect ratio for wallpaper
            CropMode.RATIO_4_3 -> 4.0f
            CropMode.RATIO_3_4 -> 3.0f
            CropMode.RATIO_16_9 -> 16.0f
            CropMode.RATIO_9_16 -> 9.0f
            CropMode.RATIO_1_1, CropMode.CIRCLE -> 1.0f
            CropMode.RATIO_CUSTOM -> mCustomRatio.x
        }
    }

    private fun getRatioY(h: Float): Float {
        return when (mCropMode) {
            CropMode.RATIO_FIT_IMAGE -> mImgHeight
            CropMode.RATIO_FREE -> screenHeight // Use screen aspect ratio for wallpaper
            CropMode.RATIO_4_3 -> 3.0f
            CropMode.RATIO_3_4 -> 4.0f
            CropMode.RATIO_16_9 -> 9.0f
            CropMode.RATIO_9_16 -> 16.0f
            CropMode.RATIO_1_1, CropMode.CIRCLE -> 1.0f
            CropMode.RATIO_CUSTOM -> mCustomRatio.y
        }
    }

    private val ratioX: Float
        get() = when (mCropMode) {
            CropMode.RATIO_FIT_IMAGE -> mImgWidth
            CropMode.RATIO_4_3 -> 4.0f
            CropMode.RATIO_3_4 -> 3.0f
            CropMode.RATIO_16_9 -> 16.0f
            CropMode.RATIO_9_16 -> 9.0f
            CropMode.RATIO_1_1, CropMode.CIRCLE -> 1.0f
            CropMode.RATIO_CUSTOM -> mCustomRatio.x
            else -> screenWidth
        }

    private val ratioY: Float
        get() = when (mCropMode) {
            CropMode.RATIO_FIT_IMAGE -> mImgHeight
            CropMode.RATIO_4_3 -> 3.0f
            CropMode.RATIO_3_4 -> 4.0f
            CropMode.RATIO_16_9 -> 9.0f
            CropMode.RATIO_9_16 -> 16.0f
            CropMode.RATIO_1_1, CropMode.CIRCLE -> 1.0f
            CropMode.RATIO_CUSTOM -> mCustomRatio.y
            else -> screenHeight
        }
    private val aspectRatio: Float
        get() = if (ratioY > 0) ratioX / ratioY else 1.0f

    private val density: Float
        get() = context.resources.displayMetrics.density

    private fun sq(value: Float): Float = value * value

    private fun constrain(value: Float, min: Float, max: Float, defaultVal: Float): Float {
        return if (value < min || value > max || value.isNaN()) defaultVal else value
    }

    // Public methods
    val imageBitmap: Bitmap?
        get() = bitmap

    override fun setImageBitmap(bitmap: Bitmap?) {
        resetState()
        super.setImageBitmap(bitmap)
        updateDrawableInfo()
    }

    override fun setImageResource(resId: Int) {
        resetState()
        super.setImageResource(resId)
        updateDrawableInfo()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        resetState()
        super.setImageDrawable(drawable)
        updateDrawableInfo()
    }

    override fun setImageURI(uri: Uri?) {
        resetState()
        super.setImageURI(uri)
        updateDrawableInfo()
    }

    override fun setScaleType(scaleType: ScaleType) {
        // Enforce MATRIX to prevent parent scaling
        super.setScaleType(ScaleType.MATRIX)
    }

    private fun updateDrawableInfo() {
        val d = drawable
        if (d != null && mViewWidth > 0 && mViewHeight > 0) {
            initLayout(mViewWidth, mViewHeight)
        }
    }

    fun rotateImage(degrees: RotateDegrees) {
        val source = bitmap ?: return
        val angle = degrees.value
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        setImageBitmap(rotated)
    }

    fun saveBitmapInToFolder(): Boolean {
        val croppedBitmap = croppedBitmap ?: return false
        var fileCreated = false
        var bitmapCompressed = false
        var streamClosed = false
        try {
            val filePath = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/WallPaperApp"
            val fileDir = File(filePath)
            if (!fileDir.exists()) fileDir.mkdirs()
            imageName = "$filePath/image${System.currentTimeMillis()}.png"
        } catch (e: Exception) {
            Log.e(TAG, "Directory creation failed: ${e.message}", e)
            return false
        }

        val imageFile = File(imageName)
        if (imageFile.exists()) imageFile.delete()
        try {
            fileCreated = imageFile.createNewFile()
        } catch (e: Exception) {
            Log.e(TAG, "File creation failed: ${e.message}", e)
            return false
        }

        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(imageFile)
            bitmapCompressed = croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } catch (e: IOException) {
            Log.e(TAG, "Bitmap compression failed: ${e.message}", e)
            return false
        } finally {
            try {
                out?.flush()
                out?.close()
                streamClosed = true
            } catch (e: IOException) {
                Log.e(TAG, "Stream closing failed: ${e.message}", e)
                streamClosed = false
            }
        }
        Log.i(TAG, "Image saved: $fileCreated, $bitmapCompressed, $streamClosed")
        return fileCreated && bitmapCompressed && streamClosed
    }

    fun saveCroppedImage(context: Context, fileName: String): Uri? {
        val croppedBitmap = croppedBitmap ?: return null
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WallPaperApp")
        }
        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        imageUri?.let { uri ->
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save cropped image: ${e.message}", e)
                return null
            }
        }
        return imageUri
    }
    fun saveCroppedImageNew(context: Context, oldUri: Uri): Boolean {
        val croppedBitmap = croppedBitmap ?: return false
        return try {
            val pfd = context.contentResolver.openFileDescriptor(oldUri, "w") ?: return false
            FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
            pfd.close()

            // Optional: trigger MediaStore to update thumbnails
            context.contentResolver.update(oldUri, ContentValues().apply {
                put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            }, null, null)
            //context.contentResolver.update(oldUri, ContentValues(), null, null)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getImageUri(): Uri? {
        return imageName?.let { Uri.parse(it) }
    }

    val croppedBitmap: Bitmap?
        get() {
            val source = bitmap ?: return null
            if (mFrameRect == null || mImageRect == null) {
                Log.w(TAG, "Frame or image rect is null")
                return null
            }

            // Map mFrameRect corners to image coordinates
            val inverseMatrix = Matrix()
            val points = floatArrayOf(
                mFrameRect!!.left, mFrameRect!!.top, // Top-left
                mFrameRect!!.right, mFrameRect!!.bottom // Bottom-right
            )
            if (mMatrix.invert(inverseMatrix)) {
                inverseMatrix.mapPoints(points)
            } else {
                Log.w(TAG, "Matrix inversion failed, using fallback mapping")
                val dx = (mCenter.x - mViewWidth * 0.5f) / mScale
                val dy = (mCenter.y - mViewHeight * 0.5f) / mScale
                points[0] = (mFrameRect!!.left / mScale) - dx
                points[1] = (mFrameRect!!.top / mScale) - dy
                points[2] = (mFrameRect!!.right / mScale) - dx
                points[3] = (mFrameRect!!.bottom / mScale) - dy
            }

            // Calculate crop coordinates in original image
            val x = points[0]
            val y = points[1]
            val w = points[2] - points[0]
            val h = points[3] - points[1]

            // Clamp to bitmap bounds
            val xClamped = max(0f, min(x, source.width.toFloat()))
            val yClamped = max(0f, min(y, source.height.toFloat()))
            val wClamped = min(w, source.width - xClamped)
            val hClamped = min(h, source.height - yClamped)

            if (wClamped <= 0 || hClamped <= 0) {
                Log.w(TAG, "Invalid crop dimensions: w=$wClamped, h=$hClamped")
                return null
            }

            Log.d(TAG, "Crop: x=$xClamped, y=$yClamped, w=$wClamped, h=$hClamped")
            try {
                val cropped = Bitmap.createBitmap(source, xClamped.toInt(), yClamped.toInt(), wClamped.toInt(), hClamped.toInt())
                return if (mCropMode != CropMode.CIRCLE) cropped else getCircularBitmap(cropped)
            } catch (e: Exception) {
                Log.e(TAG, "Crop failed: ${e.message}", e)
                return null
            }
        }

    val rectBitmap: Bitmap?
        get() {
            val source = bitmap ?: return null
            if (mFrameRect == null || mImageRect == null) {
                Log.w(TAG, "Frame or image rect is null")
                return null
            }

            // Map mFrameRect corners to image coordinates
            val inverseMatrix = Matrix()
            val points = floatArrayOf(
                mFrameRect!!.left, mFrameRect!!.top,
                mFrameRect!!.right, mFrameRect!!.bottom
            )
            if (mMatrix.invert(inverseMatrix)) {
                inverseMatrix.mapPoints(points)
            } else {
                Log.w(TAG, "Matrix inversion failed for rectBitmap, using fallback")
                val dx = (mCenter.x - mViewWidth * 0.5f) / mScale
                val dy = (mCenter.y - mViewHeight * 0.5f) / mScale
                points[0] = (mFrameRect!!.left / mScale) - dx
                points[1] = (mFrameRect!!.top / mScale) - dy
                points[2] = (mFrameRect!!.right / mScale) - dx
                points[3] = (mFrameRect!!.bottom / mScale) - dy
            }

            // Calculate crop coordinates
            val x = points[0]
            val y = points[1]
            val w = points[2] - points[0]
            val h = points[3] - points[1]

            // Clamp to bitmap bounds
            val xClamped = max(0f, min(x, source.width.toFloat()))
            val yClamped = max(0f, min(y, source.height.toFloat()))
            val wClamped = min(w, source.width - xClamped)
            val hClamped = min(h, source.height - yClamped)

            if (wClamped <= 0 || hClamped <= 0) {
                Log.w(TAG, "Invalid rect crop dimensions: w=$wClamped, h=$hClamped")
                return null
            }

            Log.d(TAG, "Rect crop: x=$xClamped, y=$yClamped, w=$wClamped, h=$hClamped")
            try {
                return Bitmap.createBitmap(source, xClamped.toInt(), yClamped.toInt(), wClamped.toInt(), hClamped.toInt())
            } catch (e: Exception) {
                Log.e(TAG, "Rect crop failed: ${e.message}", e)
                return null
            }
        }

    private fun getCircularBitmap(source: Bitmap?): Bitmap? {
        if (source == null) return null
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val rect = Rect(0, 0, source.width, source.height)
        val halfWidth = source.width / 2f
        val halfHeight = source.height / 2f
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        canvas.drawCircle(halfWidth, halfHeight, min(halfWidth, halfHeight), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, rect, rect, paint)
        return output
    }

    private val bitmap: Bitmap?
        get() = (drawable as? BitmapDrawable)?.bitmap

    val actualCropRect: RectF?
        get() {
            if (mImageRect == null || mFrameRect == null) return null
            val inverse = Matrix()
            val points = floatArrayOf(
                mFrameRect!!.left, mFrameRect!!.top,
                mFrameRect!!.right, mFrameRect!!.bottom
            )
            if (mMatrix.invert(inverse)) {
                inverse.mapPoints(points)
            } else {
                Log.w(TAG, "Matrix inversion failed for actualCropRect, using fallback")
                val dx = (mCenter.x - mViewWidth * 0.5f) / mScale
                val dy = (mCenter.y - mViewHeight * 0.5f) / mScale
                points[0] = (mFrameRect!!.left / mScale) - dx
                points[1] = (mFrameRect!!.top / mScale) - dy
                points[2] = (mFrameRect!!.right / mScale) - dx
                points[3] = (mFrameRect!!.bottom / mScale) - dy
            }
            return RectF(points[0], points[1], points[2], points[3])
        }

    fun setCropMode(mode: CropMode) {
        if (mode == CropMode.RATIO_CUSTOM) {
            setCustomRatio(1, 1)
        } else {
            mCropMode = mode
            adjustRatio()
        }
    }

    fun setCustomRatio(ratioX: Int, ratioY: Int) {
        if (ratioX <= 0 || ratioY <= 0) return
        mCropMode = CropMode.RATIO_CUSTOM
        mCustomRatio = PointF(ratioX.toFloat(), ratioY.toFloat())
        adjustRatio()
    }

    fun setOverlayColor(color: Int) {
        mOverlayColor = color
        invalidate()
    }

    fun setFrameColor(color: Int) {
        mFrameColor = color
        mPaintFrame.color = color
        invalidate()
    }

    fun setHandleColor(color: Int) {
        mHandleColor = color
        invalidate()
    }

    fun setGuideColor(color: Int) {
        mGuideColor = color
        invalidate()
    }

    override fun setBackgroundColor(color: Int) {
        mBackgroundColor = color
        super.setBackgroundColor(color)
        invalidate()
    }

    fun setMinFrameSizeInDp(minDp: Int) {
        mMinFrameSize = minDp * density
    }

    fun setHandleSizeInDp(handleSize: Int) {
        mHandleSize = (handleSize * density).toInt()
    }

    fun setTouchPaddingInDp(padding: Int) {
        mTouchPadding = (padding * density).toInt()
    }

    fun setGuideShowMode(mode: ShowMode?) {
        mGuideShowMode = mode ?: ShowMode.SHOW_ALWAYS
        when (mGuideShowMode) {
            ShowMode.SHOW_ALWAYS -> mShowGuide = true
            ShowMode.NOT_SHOW, ShowMode.SHOW_ON_TOUCH -> mShowGuide = false
            else -> {}
        }
        invalidate()
    }

    fun setHandleShowMode(mode: ShowMode?) {
        mHandleShowMode = mode ?: ShowMode.SHOW_ALWAYS
        when (mHandleShowMode) {
            ShowMode.SHOW_ALWAYS -> mShowHandle = true
            ShowMode.NOT_SHOW, ShowMode.SHOW_ON_TOUCH -> mShowHandle = false
            else -> {}
        }
        invalidate()
    }

    fun setFrameStrokeWeightInDp(weight: Int) {
        mFrameStrokeWeight = weight * density
        mPaintFrame.strokeWidth = mFrameStrokeWeight
        invalidate()
    }

    fun setGuideStrokeWeightInDp(weight: Int) {
        mGuideStrokeWeight = weight * density
        invalidate()
    }

    fun setCropEnabled(enabled: Boolean) {
        mIsCropEnabled = enabled
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        mIsEnabled = enabled
        super.setEnabled(enabled)
    }

    fun setInitialFrameScale(scale: Float) {
        mInitialFrameScale = constrain(scale, 0.01f, 1.0f, DEFAULT_INITIAL_FRAME_SCALE)
    }

    suspend fun zoomIn(): Bitmap? = withContext(Dispatchers.Main) {
        Log.d(TAG, "Zoom In: currentScale=$mScale")
        if (mScale * 1.1f <= MAX_ZOOM) {
            mScale *= 1.1f
            checkScaleBounds()
            setMatrix()
            invalidate()
            Log.d(TAG, "Zoomed to scale=$mScale")
        }
        null
    }

   /* suspend fun zoomOut(): Bitmap? = withContext(Dispatchers.Main) {
        Log.d(TAG, "Zoom Out: currentScale=$mScale")
        if (mScale / 1.25f >= MIN_ZOOM) {
            mScale /= 1.25f
            checkScaleBounds()
            setMatrix()
            invalidate()
            Log.d(TAG, "Zoomed to scale=$mScale")
        }
        null
    }*/
   suspend fun zoomOut(): Bitmap? = withContext(Dispatchers.Main) {
       Log.d(TAG, "Zoom Out: currentScale=$mScale")

       // Calculate the effective minimum zoom (initial fit scale)
       val initialScale = calculateInitialScale(mViewWidth, mViewHeight, mImgWidth, mImgHeight)
       val effectiveMinZoom = max(MIN_ZOOM, initialScale)

       val targetScale = mScale / 1.25f

       if (targetScale >= effectiveMinZoom) {
           mScale = targetScale
           // checkScaleBounds() // Not strictly needed here
           setMatrix()
           invalidate()
           Log.d(TAG, "Zoomed to scale=$mScale")
       } else if (mScale > effectiveMinZoom) {
           // If the target is too low, clamp to the minimum effective scale
           mScale = effectiveMinZoom
           // checkScaleBounds() // Not strictly needed here
           setMatrix()
           invalidate()
           Log.d(TAG, "Clamped to minimum scale=$mScale")
       }
       null
   }


    suspend fun rotateImageByDegrees(degrees: Float): Bitmap? = withContext(Dispatchers.Main) {
        Log.d(TAG, "Rotating image by $degrees degrees, current angle=$mAngle")
        val source = bitmap ?: run {
            Log.e(TAG, "Source bitmap is null")
            return@withContext null
        }
        if (mImageRect == null) {
            Log.e(TAG, "Image rect is null")
            return@withContext null
        }
        if (mViewWidth <= 0 || mViewHeight <= 0) {
            Log.e(TAG, "Invalid view dimensions: width=$mViewWidth, height=$mViewHeight")
            return@withContext null
        }
        // Normalize and update angle
        mAngle = (mAngle + degrees) % 360f
        setMatrix()
        invalidate()
        Log.d(TAG, "Rotated to angle=$mAngle")
        null
    }

    fun resetMatrix() {
        mMatrix.reset()
        mScale = 1.0f
        invalidate()
    }

    fun setAspectRatio(ratio: Float) {
        // Update internal aspect ratio, if supported
        invalidate()
    }

    override fun setRotation(rotation: Float) {
        mAngle = rotation % 360
        setMatrix()
        invalidate()
    }

    fun setScale(scale: Float) {
        mScale = constrain(scale, MIN_ZOOM, MAX_ZOOM, 1.0f)
        checkScaleBounds()
        invalidate()
    }

    private fun setCenter(center: PointF) {
        mCenter.set(center)
    }

    private val frameW: Float
        get() = mFrameRect?.let { it.right - it.left } ?: 0f

    private val frameH: Float
        get() = mFrameRect?.let { it.bottom - it.top } ?: 0f

    // Enums
    private enum class TouchArea {
        OUT_OF_BOUNDS,
        CENTER,
        LEFT_TOP,
        RIGHT_TOP,
        LEFT_BOTTOM,
        RIGHT_BOTTOM,
        PAN
    }

    enum class CropMode(val id: Int) {
        RATIO_FIT_IMAGE(0),
        RATIO_4_3(1),
        RATIO_3_4(2),
        RATIO_1_1(3),
        RATIO_16_9(4),
        RATIO_9_16(5),
        RATIO_FREE(6),
        RATIO_CUSTOM(7),
        CIRCLE(8),
    }

    enum class ShowMode(val id: Int) {
        SHOW_ALWAYS(1),
        SHOW_ON_TOUCH(2),
        NOT_SHOW(3),
    }

    enum class RotateDegrees(val value: Int) {
        ROTATE_90D(90),
        ROTATE_180D(180),
        ROTATE_270D(270),
    }

    // SavedState
    inner class SavedState : BaseSavedState {
        var image: Bitmap? = null
        var mode: CropMode? = null
        var backgroundColor: Int = 0
        var overlayColor: Int = 0
        var frameColor: Int = 0
        var guideShowMode: ShowMode? = null
        var handleShowMode: ShowMode? = null
        var showGuide: Boolean = false
        var showHandle: Boolean = false
        var handleSize: Int = 0
        var touchPadding: Int = 0
        var minFrameSize: Float = 0f
        var customRatioX: Float = 0f
        var customRatioY: Float = 0f
        var frameStrokeWeight: Float = 0f
        var guideStrokeWeight: Float = 0f
        var isCropEnabled: Boolean = false
        var handleColor: Int = 0
        var guideColor: Int = 0
        var initialFrameScale: Float = 0f
        var scale: Float = 1.0f
        var centerX: Float = 0f
        var centerY: Float = 0f
        var frameLeft: Float = 0f
        var frameTop: Float = 0f
        var frameRight: Float = 0f
        var frameBottom: Float = 0f

        constructor(superState: Parcelable?) : super(superState)

        constructor(parcel: Parcel) : super(parcel) {
            image = parcel.readParcelable(Bitmap::class.java.classLoader)
            mode = parcel.readSerializable() as? CropMode
            backgroundColor = parcel.readInt()
            overlayColor = parcel.readInt()
            frameColor = parcel.readInt()
            guideShowMode = parcel.readSerializable() as? ShowMode
            handleShowMode = parcel.readSerializable() as? ShowMode
            showGuide = parcel.readInt() != 0
            showHandle = parcel.readInt() != 0
            handleSize = parcel.readInt()
            touchPadding = parcel.readInt()
            minFrameSize = parcel.readFloat()
            customRatioX = parcel.readFloat()
            customRatioY = parcel.readFloat()
            frameStrokeWeight = parcel.readFloat()
            guideStrokeWeight = parcel.readFloat()
            isCropEnabled = parcel.readInt() != 0
            handleColor = parcel.readInt()
            guideColor = parcel.readInt()
            initialFrameScale = parcel.readFloat()
            scale = parcel.readFloat()
            centerX = parcel.readFloat()
            centerY = parcel.readFloat()
            frameLeft = parcel.readFloat()
            frameTop = parcel.readFloat()
            frameRight = parcel.readFloat()
            frameBottom = parcel.readFloat()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeParcelable(image, flags)
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
            out.writeFloat(scale)
            out.writeFloat(centerX)
            out.writeFloat(centerY)
            out.writeFloat(frameLeft)
            out.writeFloat(frameTop)
            out.writeFloat(frameRight)
            out.writeFloat(frameBottom)
        }
    }

    companion object {
        private const val TAG = "CropImageView"
        private const val HANDLE_SIZE_IN_DP = 16
        private const val MIN_FRAME_SIZE_IN_DP = 150f
        private const val INITIAL_FRAME_SCALE = 1.0f
        private const val FRAME_STROKE_WEIGHT_IN_DP = 3f
        private const val GUIDE_STROKE_WEIGHT_IN_DP = 2f
        private const val DEFAULT_INITIAL_FRAME_SCALE = 0.8f
        private const val MIN_FRAME_SIZE_PERCENTAGE = 0.10f
        private const val MIN_ZOOM = 1.0f
        private const val MAX_ZOOM = 5.0f
    }
}