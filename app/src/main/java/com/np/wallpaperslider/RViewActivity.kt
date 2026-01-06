package com.np.wallpaperslider

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.activity.ComponentActivity
import androidx.annotation.ContentView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.FileNotFoundException
import java.io.IOException
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.constraintlayout.helper.widget.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream


class RViewActivity:ComponentActivity(), View.OnClickListener {
    lateinit var view_image : ImageView
    lateinit var delicon : MaterialButton
    lateinit var cropicon : MaterialButton
    lateinit var rotateicon : MaterialButton
    lateinit var save_rimage : MaterialButton
    lateinit var discard_image : MaterialButton
    lateinit var image : Bitmap
    lateinit var cat_image : ImageView
    var modalItems: String = ""
    var category : String = ""
    //lateinit var nexticon : Button
    var index :Long = 0L
    var imagesList = ArrayList<ImageItem>()
    var ct:Int = 1
    lateinit var lvafterrotate : Flow
    lateinit var filename : String
    lateinit var rotated : Bitmap
    private var homeImages = mutableListOf<String>()
    private var lockImages = mutableListOf<String>()
    private var bothImages = mutableListOf<String>()
    private var clearImages = mutableListOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view)

        val toolbar = findViewById<MaterialToolbar>(R.id.xml_toolbar)
        toolbar.title = "Image Editor" // Optional: Set a title
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow) // Optional: Add a navigation icon
        toolbar.setNavigationOnClickListener {
            var intent = Intent(applicationContext,RGrid::class.java)
            intent.putExtra("frompage","none")
            startActivity(intent)
            finishAndRemoveTask()
        }
        lvafterrotate = findViewById(R.id.after_rotate_container)
        lvafterrotate.visibility=View.GONE
        modalItems = intent.getStringExtra("data") ?: ""

        if (modalItems.isEmpty()) {
            // If "data" is missing, we cannot load an image.
            Toast.makeText(this, "Error: Image URI is missing.", Toast.LENGTH_LONG).show()
            AppLogger.e("RViewActivity", "FATAL: modalItems (image URI) is empty.")

            // Return or finish the activity immediately to prevent a crash
            finish()
            return
        }
        val inputStream = applicationContext.contentResolver.openInputStream(Uri.parse(modalItems))
        inputStream?.use {
            image = BitmapFactory.decodeStream(it)
        }

        filename = modalItems.toUri().lastPathSegment.toString()
        view_image = findViewById(R.id.view_image)
        view_image.setImageBitmap(image)
       // index = intent.getSerializableExtra("index")
        index = intent.getLongExtra("index", 0L)
        delicon = findViewById(R.id.delicon)
        cropicon = findViewById(R.id.cropicon)
        rotateicon = findViewById(R.id.rotateicon)
        save_rimage = findViewById(R.id.save_rimage)
        discard_image = findViewById(R.id.discard_image)
        //nexticon = findViewById(R.id.nexticon)
        delicon.setOnClickListener(this)
        cropicon.setOnClickListener(this)
        rotateicon.setOnClickListener(this)
        save_rimage.setOnClickListener(this)
        discard_image.setOnClickListener(this)
        cat_image = findViewById(R.id.view_category_indicator)
        category = intent.getStringExtra("cat") ?: "clear"
        AppLogger.d("RViewActivity", "category = $category")
        updateCategoryIndicator(category)
        lifecycleScope.launch {
            loadImageListFromPrefs()
        }
        registerForContextMenu(cat_image)
        cat_image.setOnClickListener {
            openContextMenu(it)
        }
    }

    private fun updateCategoryIndicator(currentCategory: String) {
        val indicatorRes = when (currentCategory) {
            "home" -> R.drawable.homeimage
            "lock" -> R.drawable.lockimage
            "both" -> R.drawable.bothimage
            else -> R.drawable.nocategory // 0 means no drawable is set, effectively clearing it if there's no clear icon
        }
        cat_image.setImageResource(indicatorRes)
    }
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.view_context_menu, menu)
        //menu.setHeaderTitle("Choose")

        val currentCategory = category
        val setHomeItem = menu.findItem(R.id.action_viewset_home)
        val setLockItem = menu.findItem(R.id.action_viewset_lock)
        if (setHomeItem == null || setLockItem == null) {
            AppLogger.e("RViewActivity", "Menu item IDs not found in view_context_menu.xml")
            return
        }
        if (currentCategory == "home" || currentCategory == "both") {
            setHomeItem.title = "Remove from Homescreen"
        } else {
            setHomeItem.title = "Add to Homescreen"
        }
        if (currentCategory == "lock" || currentCategory == "both") {
            setLockItem.title = "Remove from Lockscreen"
        } else {
            setLockItem.title = "Add to Lockscreen"
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val imagePath = modalItems
        val currentCategory = category
        lifecycleScope.launch {
            when (item.itemId) {
                R.id.action_viewset_home -> {
                    // If currently 'home' or 'both', the action is to CLEAR the home setting.
                    // Otherwise, the action is to SET it to 'home'.
                    val targetCategory = when (currentCategory) {
                        "home" -> "clear" // Clear Home
                        "both" -> "lock"  // Clear Home, keep Lock
                        else -> "home"    // Set Home
                    }
                    moveImageToList(imagePath, targetCategory)
                }
                R.id.action_viewset_lock -> {
                    // If currently 'lock' or 'both', the action is to CLEAR the lock setting.
                    // Otherwise, the action is to SET it to 'lock'.
                    val targetCategory = when (currentCategory) {
                        "lock" -> "clear" // Clear Lock
                        "both" -> "home"  // Clear Lock, keep Home
                        else -> "lock"    // Set Lock
                    }
                    moveImageToList(imagePath, targetCategory)
                }
                // R.id.action_viewset_both -> // You might need to add logic for setting 'both' explicitly
                // R.id.action_viewset_clear -> // You might need to add logic for setting 'clear' explicitly
                else -> return@launch
            }
        }
        return true
    }

    private suspend fun moveImageToList(imagePath: String, targetList: String) {
        val lists = mapOf(
            "home" to homeImages,
            "lock" to lockImages,
            "both" to bothImages,
            "clear" to clearImages
        )

        withContext(Dispatchers.IO) {
            // Remove from all lists safely
            lists.values.forEach { it.remove(imagePath) }

            // Add to target if valid
            lists[targetList]?.add(imagePath)

            // Save only changed lists
            saveImageList(targetList, lists[targetList] ?: mutableListOf())

            // Optionally, only resave lists that had the item removed
            saveImageList("homeImages", homeImages)
            saveImageList("lockImages", lockImages)
            saveImageList("bothImages", bothImages)
            saveImageList("clearImages", clearImages)
        }
        // FIX: Switch back to the Main thread to update UI state and indicator
        withContext(Dispatchers.Main) {
            category = targetList // Update the local state variable
            updateCategoryIndicator(targetList) // Update the icon

            val message = if (targetList == "clear") {
                "Category cleared."
            } else {
                "Category set to $targetList."
            }
            Toast.makeText(this@RViewActivity, message, Toast.LENGTH_SHORT).show()
        }

    }
    private suspend fun saveImageList(arrayName: String, list: List<String>) {
        withContext(Dispatchers.IO) {
            val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putInt("${arrayName}_size", list.size)
            list.forEachIndexed { index, path ->
                editor.putString("${arrayName}_$index", path)
            }
            editor.apply()
        }
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onClick(v: View?) {
        if(v==delicon) {
            val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
            val size = prefs.getInt("imagesPathList_size", 0)
            val itemToDelete = imagesList.find { it.id == index }
            val deletedImagePath = itemToDelete?.imagePath ?: return
            val array = Array(size-1) { "" }
            var j = 0
            for (i in 0 until size) {
                val path = prefs.getString("imagesPathList_$i", null) ?: ""
                if (path != deletedImagePath) {
                    array[j] = path
                    j++
                }
               // else
              //  { RGrid.recyclerImageAdapter.itemRemovedAtPosition(j) }
            }
            if (saveArray(array, "imagesPathList", applicationContext)) {
                val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
                val editor = prefs.edit()

                fun removeFromCategory(listName: String) {
                    val size = prefs.getInt("${listName}_size", 0)
                    val newList = mutableListOf<String>()
                    for (i in 0 until size) {
                        val path = prefs.getString("${listName}_$i", null)
                        if (path != null && path != deletedImagePath) newList.add(path)
                    }
                    editor.putInt("${listName}_size", newList.size)
                    newList.forEachIndexed { i, v -> editor.putString("${listName}_$i", v) }
                }

                removeFromCategory("homeImages")
                removeFromCategory("lockImages")
                removeFromCategory("bothImages")
                removeFromCategory("clearImages")

                editor.apply()


                val intent = Intent(this, RGrid::class.java)
                intent.putExtra("frompage", "none")
                startActivity(intent)
                finishAndRemoveTask()
            }


            }
        else if(v==cropicon)
        {
            val i = Intent(applicationContext, CropActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            i.putExtra("imageindex",index)
            i.putExtra("frompage","grid")
            startActivity(i)
            finish()
        }
        else if(v==rotateicon)
        {
            if(ct == 1)
                rotateImage(RotateDegrees.ROTATE_90D)
            else if(ct == 2)
                rotateImage(RotateDegrees.ROTATE_180D)
            else if(ct == 3)
                rotateImage(RotateDegrees.ROTATE_270D)
            else if(ct == 4) {
                rotateImage(RotateDegrees.ROTATE_360D)
                ct = 0
            }
            ct++
            if(ct!=1)
                lvafterrotate.visibility=View.VISIBLE
        }
        else if(v==save_rimage)
        {
            var array = loadArray("imagesPathList", applicationContext)
            //val oldUri = array[index!!].toUri()
            val imageItem = imagesList.find { it.id == index } ?: return
            val oldUri = imageItem.imagePath.toUri()
            AppLogger.d("RViewActivity", "index $index")

            val success = saveRotatedImage(applicationContext, oldUri)

            if (success) {
                view_image.setImageBitmap(rotated)
                lvafterrotate.visibility = View.GONE

                Toast.makeText(this, "Image rotated and saved", Toast.LENGTH_SHORT).show()

                // Reload grid cleanly
                /*val intent = Intent(applicationContext, RGrid::class.java)
                intent.putExtra("frompage", "none")
                startActivity(intent)
                finish()*/
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
        else if(v==discard_image)
        {
            view_image.setImageBitmap(image)
            lvafterrotate.visibility=View.GONE
        }

    }

    private fun bustGlideCache(uriString: String) {
        val prefs = getSharedPreferences("glide_cache_busters", Context.MODE_PRIVATE)
        val currentBuster = prefs.getInt(uriString, 0)
        prefs.edit().putInt(uriString, currentBuster + 1).apply()
        AppLogger.d("RViewActivity", "Busted cache for $uriString. New buster: ${currentBuster + 1}")
    }
    enum class RotateDegrees(val value: Int) {
        ROTATE_90D(90),
        ROTATE_180D(180),
        ROTATE_270D(270),
        ROTATE_360D(360)


    }

    fun rotateImage(degrees: RotateDegrees) {
        val source =  image?: return
        val angle = degrees.value
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        rotated = Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
        view_image.setImageBitmap(rotated)
    }

    fun saveRotatedImage(context: Context, oldUri: Uri): Boolean {
    return try {
        val pfd = context.contentResolver.openFileDescriptor(oldUri, "w") ?: return false
        FileOutputStream(pfd.fileDescriptor).use { outputStream ->
            rotated.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
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
    fun overwriteRotatedImage(context: Context, existingUri: Uri) {
        context.contentResolver.openOutputStream(existingUri, "wt")?.use { outputStream ->
            rotated.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
    }

    private suspend fun loadImageListFromPrefs() {
        withContext(Dispatchers.IO) {
            val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
            val size = prefs.getInt("imagesPathList_size", 0)

            imagesList.clear()
            for (i in 0 until size) {
                val path = prefs.getString("imagesPathList_$i", null)
                if (path != null) {
                    imagesList.add(ImageItem.fromPath(path, ""))
                }
            }
            homeImages = loadImageList("homeImages").toMutableList()
            lockImages = loadImageList("lockImages").toMutableList()
            bothImages = loadImageList("bothImages").toMutableList()
            clearImages = loadImageList("clearImages").toMutableList()
            AppLogger.d(
                "RGrid",
                "Loaded lists in RViewActivity: home=${homeImages.size}, lock=${lockImages.size}, both=${bothImages.size}, clear=${clearImages.size}"
            )
        }

    }

    private suspend fun loadImageList(arrayName: String): List<String> {
        return withContext(Dispatchers.IO) {
            val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
            val size = prefs.getInt("${arrayName}_size", 0)
            AppLogger.d("RGrid", "Loading $arrayName, size: $size")
            val images = mutableListOf<String>()
            for (index in 0 until size) {
                prefs.getString("${arrayName}_$index", null)?.let { path ->
                    if (path.isNotEmpty()) {
                        try {
                            // Validate URI accessibility
                            contentResolver.openInputStream(Uri.parse(path))?.close()
                            images.add(path)
                            AppLogger.d("RGrid", "Valid URI for $arrayName[$index]: $path")
                        } catch (e: Exception) {
                            AppLogger.w("RGrid", "Invalid URI for $arrayName[$index]: $path, error: ${e.message}")
                            // Remove invalid entry
                            val editor = prefs.edit()
                            editor.remove("${arrayName}_$index")
                            editor.apply()
                        }
                    }
                }
            }
            images
        }
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

    fun saveArray(array:Array<String>,arrayName: String, context: Context): Boolean {
        //clearing
        /*var prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        var editor = prefs.edit()
        editor.clear().commit()*/

        //reinserting
        var prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        var editor = prefs.edit()
        editor.putInt("${arrayName}_size", array.size)


        for (i in array.indices) {

            editor.putString("${arrayName}_$i", array[i])
        }

        return editor.commit()
    }



    fun imageExists(context: Context, imageUri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { true } ?: false
        } catch (e: FileNotFoundException) {
            false  // Image does not exist
        }
    }


}


