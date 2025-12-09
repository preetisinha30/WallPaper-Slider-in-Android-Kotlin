package com.np.wallpaperslider

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.IOException
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CropActivity : ComponentActivity(), View.OnClickListener {
    private lateinit var origPic: CropImageView
    private lateinit var cropPic: MaterialButton
    private lateinit var zoomin: FloatingActionButton
    private lateinit var zoomout: FloatingActionButton
    //private lateinit var rotatePic: FloatingActionButton
    private lateinit var undoIcon: FloatingActionButton
    private lateinit var imagePic: ImageView
    private lateinit var savePic: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var cl_imageContainer: ConstraintLayout
    private lateinit var cl_buttonContainer: ConstraintLayout
    private lateinit var cl_actionButtons: LinearLayout
    private var imageindex: Long? = 0L
    //private var imagesArray = arrayOf<String>()
    var imagesList = ArrayList<ImageItem>()
    private val handler = Handler(Looper.getMainLooper())
    private val drawRunner = Runnable { drawImage() }
    private var useDiceOne = false
    private var bmp: Bitmap? = null
    private var alteredBitmap: Bitmap? = null
    private lateinit var loading: LoadingDialog
    private lateinit var frompage: String
    private lateinit var filename: String
    private val TAG = "CropActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        // Initialize toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.xml_toolbar)
        toolbar.title = "Crop Image"
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.setNavigationOnClickListener {
            val intent = Intent(applicationContext, RViewActivity::class.java)
            startActivity(intent)
            finishAndRemoveTask()
        }

        // Initialize views
        loading = LoadingDialog(this)
        loading.startLoading()
        origPic = findViewById(R.id.cv4)
        cropPic = findViewById(R.id.crop_image)
        zoomin = findViewById(R.id.zoom_in)
        zoomout = findViewById(R.id.zoom_out)
        imagePic = findViewById(R.id.set_image)
        savePic = findViewById(R.id.save_image)
        //rotatePic = findViewById(R.id.rot_icon)
        undoIcon = findViewById(R.id.undo_icon)
        cancelButton = findViewById(R.id.cancel_button)
        cl_imageContainer = findViewById(R.id.cl_imageContainer)
        cl_buttonContainer = findViewById(R.id.cl_buttonContainer)
        cl_actionButtons = findViewById(R.id.cl_actionButtons)

        // Set up button listeners
        cropPic.setOnClickListener(this)
        savePic.setOnClickListener(this)
        zoomin.setOnClickListener(this)
        zoomout.setOnClickListener(this)
        //rotatePic.setOnClickListener(this)
        undoIcon.setOnClickListener(this)
        cancelButton.setOnClickListener(this)
        zoomin.tooltipText = "Zoom In"
        zoomout.tooltipText = "Zoom Out"
       // rotatePic.tooltipText = "Rotate"
        undoIcon.tooltipText = "Undo"

        // Set maxHeight for cl_imageContainer dynamically
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenHeight = displayMetrics.heightPixels / displayMetrics.density
        val toolbarHeight = 56f // Approximate toolbar height
        val actionButtonsHeight = 48f + 8f + 8f // Button height + margins
        val bottomButtonsHeight = 48f + 16f + 16f // Button height + padding
        val maxImageHeight = screenHeight * 0.8f // Use 80% of screen height for image
        val maxHeight = (maxImageHeight * displayMetrics.density).toInt()
        cl_imageContainer.maxHeight = maxHeight
        Log.d(TAG, "Screen height: $screenHeight dp, Max image height: $maxImageHeight dp, Container maxHeight: $maxHeight px")

        // Load image array and index
        //imagesArray = loadArray("imagesPathList", applicationContext)
        loadImageListFromPrefs()
        showBeforeCrop()
        val bundle = intent.extras
        //imageindex = bundle?.getString("imageindex")?.toIntOrNull() ?: 0
        imageindex = intent.getLongExtra("imageindex", 0L)
        frompage = bundle?.getString("frompage") ?: "none"
        origPic.setImageResource(android.R.color.transparent)
        handler.postDelayed(drawRunner, 3000)
    }

    private fun loadArray(arrayName: String, context: Context): Array<String> {
        val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        val size = prefs.getInt("${arrayName}_size", 0)
        val array = Array(size) { "" }
        for (i in 0 until size) {
            array[i] = prefs.getString("${arrayName}_$i", "") ?: ""
        }
        useDiceOne = true
        return array
    }

    private fun drawImage() {
        try {
           /* val imageUri = (imagesArray.getOrNull(imageindex ?: return) ?: run {
                Log.e(TAG, "Invalid image index: $imageindex")
                showError("Failed to load image")
                return
            }).toUri()*/
            val imageItem = imagesList.find { it.id == imageindex } ?: return
            val imageUri = imageItem.imagePath.toUri()

            filename = imageUri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "image_${System.currentTimeMillis()}"
            val displayMetrics = resources.displayMetrics
            val targetWidth = displayMetrics.widthPixels
            val targetHeight = (cl_imageContainer.maxHeight / displayMetrics.density).toInt()

            origPic.post {
                val bitmap = loadScaledBitmap(imageUri, targetWidth, targetHeight)
                if (bitmap != null) {
                    with(origPic) {
                        resetMatrix()
                        // Remove setScale(1.0f) to rely on initLayout's scaling
                        setRotation(0f)
                        setImageBitmap(bitmap)
                        setCropMode(CropImageView.CropMode.RATIO_FREE)
                        setCropEnabled(true)
                        setEnabled(true)
                    }
                    Log.d(TAG, "Image loaded: ${bitmap.width}x${bitmap.height}")
                    bmp = bitmap
                } else {
                    Log.e(TAG, "Failed to load bitmap")
                    showError("Failed to display image")
                }
                loading.isDismiss()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image: ${e.message}", e)
            showError("Failed to load image")
            loading.isDismiss()
        }
    }

    private fun loadScaledBitmap(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, this)
                }
            }

            val imageWidth = options.outWidth
            val imageHeight = options.outHeight
            val imageAspect = imageWidth.toFloat() / imageHeight
            val screenAspect = targetWidth.toFloat() / targetHeight

            val scaleFactor = if (imageAspect > screenAspect) {
                imageWidth.toFloat() / targetWidth // Wide image: scale by width to fill height
            } else {
                imageHeight.toFloat() / targetHeight // Tall image: scale by height to fill width
            }.toInt().coerceAtLeast(1)

            options.inSampleSize = scaleFactor
            options.inJustDecodeBounds = false
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)?.let { bitmap ->
                    val scaledWidth = if (imageAspect > screenAspect) {
                        targetWidth
                    } else {
                        (targetHeight * imageAspect).toInt().coerceAtMost(targetWidth)
                    }
                    val scaledHeight = if (imageAspect > screenAspect) {
                        (targetWidth / imageAspect).toInt().coerceAtMost(targetHeight)
                    } else {
                        targetHeight
                    }
                    //Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                    bitmap.scale(scaledWidth, scaledHeight)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scale bitmap: ${e.message}", e)
            null
        }
    }

    private fun showError(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        origPic.setEnabled(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(drawRunner)
        loading.isDismiss()
        bmp?.recycle()
        bmp = null
    }

    private fun showBeforeCrop() {
        cl_imageContainer.findViewById<View>(R.id.cv4).visibility = View.VISIBLE
        cl_imageContainer.findViewById<View>(R.id.set_image).visibility = View.GONE
        cl_buttonContainer.findViewById<View>(R.id.crop_image).visibility = View.VISIBLE
        cl_actionButtons.visibility = View.VISIBLE
        cl_buttonContainer.findViewById<View>(R.id.cancel_button).visibility = View.GONE
        cl_buttonContainer.findViewById<View>(R.id.save_image).visibility = View.GONE
    }

    private fun showAfterCrop() {
        cl_imageContainer.findViewById<View>(R.id.cv4).visibility = View.GONE
        cl_imageContainer.findViewById<View>(R.id.set_image).visibility = View.VISIBLE
        cl_buttonContainer.findViewById<View>(R.id.crop_image).visibility = View.GONE
        cl_actionButtons.visibility = View.GONE
        cl_buttonContainer.findViewById<View>(R.id.cancel_button).visibility = View.VISIBLE
        cl_buttonContainer.findViewById<View>(R.id.save_image).visibility = View.VISIBLE
    }

    override fun onClick(v: View) {
        when (v) {
            zoomin -> {
                Log.d(TAG, "Zoom In button clicked")
                lifecycleScope.launch {
                    try {
                        origPic.zoomIn()
                    } catch (e: Exception) {
                        Log.e(TAG, "Zoom failed: ${e.message}", e)
                        Toast.makeText(this@CropActivity, "Zoom error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            zoomout -> {
                Log.d(TAG, "Zoom Out button clicked")
                lifecycleScope.launch {
                    try {
                        origPic.zoomOut()
                    } catch (e: Exception) {
                        Log.e(TAG, "Zoom failed: ${e.message}", e)
                        Toast.makeText(this@CropActivity, "Zoom error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            /*rotatePic -> {
                Log.d(TAG, "Rotate button clicked")
                lifecycleScope.launch {
                    try {
                        origPic.rotateImageByDegrees(90.0f)
                    } catch (e: Exception) {
                        Log.e(TAG, "Rotation failed: ${e.message}", e)
                        Toast.makeText(this@CropActivity, "Rotation error", Toast.LENGTH_SHORT).show()
                    }
                }
            }*/
            cropPic -> {
                Log.d(TAG, "Crop button clicked")
                try {
                    val cropped = origPic.croppedBitmap
                    if (cropped != null) {
                        imagePic.setImageBitmap(cropped)
                        showAfterCrop()
                    } else {
                        Log.e(TAG, "Cropped bitmap is null")
                        Toast.makeText(this, "Failed to crop image", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Crop failed: ${e.message}", e)
                    Toast.makeText(this, "Failed to crop image", Toast.LENGTH_SHORT).show()
                }
            }
            savePic -> {
                Log.d(TAG, "Save button clicked")
               /* val oldUri = (imagesArray.getOrNull(imageindex ?: return) ?: run {
                    Log.e(TAG, "Invalid image index: $imageindex")
                    showError("Failed to load image")
                    return
                }).toUri()*/
                val imageItem = imagesList.find { it.id == imageindex } ?: return
                val oldUri = imageItem.imagePath.toUri()
               /* val newImageUri: Uri? = origPic.saveCroppedImage(applicationContext, filename)
                if (newImageUri != null) {
                    imagesList.add(imageindex, newImageUri.toString())
                    if (saveArray(imagesArray, "imagesPathList", applicationContext)) {
                        val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
                        val editor = prefs.edit()

                        fun removeFromCategory(listName: String) {
                            val size = prefs.getInt("${listName}_size", 0)
                            val newList = mutableListOf<String>()
                            for (i in 0 until size) {
                                val path = prefs.getString("${listName}_$i", null)
                                if (path != null && path != oldUri.toString()) newList.add(path)
                            }
                            editor.putInt("${listName}_size", newList.size)
                            newList.forEachIndexed { i, v -> editor.putString("${listName}_$i", v) }
                        }

                        removeFromCategory("homeImages")
                        removeFromCategory("lockImages")
                        removeFromCategory("bothImages")
                        removeFromCategory("clearImages")

                        editor.apply()

                        // Delete old file from MediaStore
                        try {
                            applicationContext.contentResolver.delete(oldUri, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        val intent = Intent(this, RGrid::class.java)
                        intent.putExtra("frompage", "none")
                        startActivity(intent)
                        finishAndRemoveTask()
                    }
                } else {
                    Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
                }*/
                val success = origPic.saveCroppedImageNew(applicationContext, oldUri)

                if (success) {
                    bustGlideCache(oldUri.toString())

                    Glide.get(applicationContext).clearMemory()

                    // 2. Clear GLIDE DISK CACHE (MUST be on Background Thread)
                    lifecycleScope.launch(Dispatchers.IO) {
                        // Note: This clears the ENTIRE disk cache, which is the most aggressive fix.
                        // If you can get a File object from the URI, clearing only that file is better.
                        Glide.get(applicationContext).clearDiskCache()

                        // 3. Navigate back to RGrid after cache is cleared (optional but safer)
                        withContext(Dispatchers.Main) {
                            val intent = Intent(applicationContext, RGrid::class.java)
                            intent.putExtra("frompage", "none")
                            startActivity(intent)
                            finish()
                        }
                    }
                } else {
                    Toast.makeText(this, "Failed to save rotation", Toast.LENGTH_SHORT).show()
                }
            }
            undoIcon -> {
                Log.d(TAG, "Undo button clicked")
                try {
                    bmp?.let { bitmap ->
                        with(origPic) {
                            resetMatrix()
                            // Remove setScale(1.0f) to rely on initLayout
                            setRotation(0f)
                            setImageBitmap(bitmap)
                            setCropMode(CropImageView.CropMode.RATIO_FREE)
                            setCropEnabled(true)
                        }
                        showBeforeCrop()
                        Log.d(TAG, "Transformations reset")
                    } ?: run {
                        Log.e(TAG, "Original bitmap is null")
                        Toast.makeText(this@CropActivity, "No image to reset", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Undo failed: ${e.message}", e)
                    Toast.makeText(this@CropActivity, "Undo error", Toast.LENGTH_SHORT).show()
                }
            }
            cancelButton -> {
                Log.d(TAG, "Cancel button clicked")
                bmp?.let { bitmap ->
                    with(origPic) {
                        resetMatrix()
                        // Remove setScale(1.0f) to rely on initLayout
                        setRotation(0f)
                        setImageBitmap(bitmap)
                        setCropMode(CropImageView.CropMode.RATIO_FIT_IMAGE)
                        setCropEnabled(true)
                    }
                    showBeforeCrop()
                    Log.d(TAG, "Transformations reset")
                } ?: run {
                    Log.e(TAG, "Original bitmap is null")
                    Toast.makeText(this@CropActivity, "No image to reset", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bustGlideCache(uriString: String) {
        val prefs = getSharedPreferences("glide_cache_busters", Context.MODE_PRIVATE)
        val currentBuster = prefs.getInt(uriString, 0)
        prefs.edit().putInt(uriString, currentBuster + 1).apply()
        Log.d(TAG, "Busted cache for $uriString. New buster: ${currentBuster + 1}")
    }

    private fun saveArray(array: Array<String>, arrayName: String, context: Context): Boolean {
        val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        //editor.clear().apply()
        editor.putInt("${arrayName}_size", array.size)
        for (i in array.indices) {
            editor.putString("${arrayName}_$i", array[i])
        }
        return editor.commit()
    }
    private fun loadImageListFromPrefs() {
        val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        val size = prefs.getInt("imagesPathList_size", 0)

        imagesList.clear()
        for (i in 0 until size) {
            val path = prefs.getString("imagesPathList_$i", null)
            if (path != null) {
                imagesList.add(ImageItem.fromPath(path, ""))
            }
        }
    }
}