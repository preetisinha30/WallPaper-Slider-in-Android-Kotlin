package com.np.wallpaperslider

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.ProgressDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.util.Log
import android.view.ContextMenu
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.np.wallpaperslider.MainActivity.ProgressDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

class RGrid : AppCompatActivity() {

    private var homeImages = mutableListOf<String>()
    private var lockImages = mutableListOf<String>()
    private var bothImages = mutableListOf<String>()
    private var clearImages = mutableListOf<String>() // For unassigned images

    var currentSegment = "all"
    private var imagesList = mutableListOf<ImageItem>()
    var REQUEST_SET_LIVE_WALLPAPER = 200
    val permisson_code = 100
    private var PICK_IMAGE_MULTIPLE = 3
    var toolbar: Toolbar? = null
    var settime = 0L
    private var trashMenuItem: MenuItem? = null
    private var isSelectionMode = false

    lateinit var fromPage: String
    lateinit var durationtv:TextView
    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabCamera: FloatingActionButton
    private lateinit var fabGenerate: FloatingActionButton

    lateinit var spotlightView: SpotlightView
    lateinit var titleView: TextView

    // State flag to track if the menu is open
    private var isFabMenuOpen = false
    private var hasShownEmptyGroupTip = false
    private lateinit var nextButton: Button
    private val PREF_IMAGETAP_COMPLETED = "imagetap_completed"
    private val PREFS_NAME = "wallpaperimages"
    private val TAG = "GridActivity"
    // Permission Coordinator (now owns all permission launchers)
    private lateinit var permissionCoordinator: PermissionCoordinator
    // Launchers (Only keep those NOT managed by the coordinator)
    private lateinit var pickImagesLauncher: ActivityResultLauncher<String>
    private var changeNoticed:Boolean = false
    //companion object
    companion object {
        lateinit var recyclerview: RecyclerView
        lateinit var recyclerImageAdapter: RecyclerImageAdapter
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rgrid)

        toolbar = findViewById<MaterialToolbar>(R.id.xml_toolbar).also {
            setSupportActionBar(it)
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            supportActionBar?.setHomeButtonEnabled(false)
        }

        var bundle: Bundle? = intent.extras
        fromPage = bundle?.getString("frompage") ?: ""
        AppLogger.i(TAG, "frompage $fromPage")
        if (fromPage == "" && iswallpaperSet()) {
           /* moveTaskToBack(true)

            finishAndRemoveTask()
            return*/
            Toast.makeText(
                applicationContext,
                "Wallpaper set successfully.",
                Toast.LENGTH_LONG
            ).show()

        }
      /*  toolbar?.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar?.setNavigationOnClickListener {
            exitSelectionMode()
            navigateToMain()
        }*/
        permissionCoordinator = PermissionCoordinator(
            activity = this,
            registry = activityResultRegistry, // Pass the Activity's registry
            lifecycleOwner = this,          // Pass the Activity as the Lifecycle Owner
            onNotificationPermissionResult = { granted ->
                if (granted) {
                    Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
                    startSetupFlow()
                } else {
                    // Denied runtime permission, proceed to ask user to open settings
                    // Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
                    showEnableNotificationsDialog()
                }

            },
            onReturnedFromNotificationSettings = {
                // Re-check flow after returning from notification settings
                startSetupFlow()
            },
            onReturnedFromBatterySettings = {
                // Re-check flow after returning from battery settings
                startSetupFlow()
            }
        )
        registerLaunchers()
        val prefs = getSharedPreferences("slideduration", Context.MODE_PRIVATE)
        settime = (prefs.getInt("slideDuration", 30000)).toLong()


        fromPage = ""
        intent.removeExtra("frompage")
        imagesList = ArrayList()

        setupGrid()



        durationtv = findViewById<TextView>(R.id.duration_status_text)
        updateDurationText(settime)
        nextButton = findViewById<Button>(R.id.btn_activate_wallpaper)
        nextButton.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                processWPclearing()

            }.start()
        }
        spotlightView = findViewById<SpotlightView>(R.id.coach_mark_overlay)
        titleView = findViewById<TextView>(R.id.overlay_title)


        // Initialize BottomNavigationView
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnApplyWindowInsetsListener { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemInsets.bottom + 16, left = 16, right = 16)
            insets
        }

        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            currentSegment = when (item.itemId) {
                R.id.nav_all -> "all"
                R.id.nav_home -> "home"
                R.id.nav_lock -> "lock"
               // R.id.nav_both -> "both"
                else -> "all"
            }
            lifecycleScope.launch {
                updateRecyclerView()
            }
            true
        }

        // Set default selection to "All"
        currentSegment = "all"
        bottomNavigation.selectedItemId = R.id.nav_all

        /*lifecycleScope.launch {
            loadImageLists()
            updateRecyclerView()
        }*/
        fabMain = findViewById(R.id.fab_main)
        fabCamera = findViewById(R.id.fab_camera)
        fabGenerate = findViewById(R.id.fab_generate)

        setupFabMenu()

        fabMain.setOnClickListener {
           // toggleFabMenu()
            Toast.makeText(this, "Opening Camera/Gallery...", Toast.LENGTH_SHORT).show()
            chooseImages()
        }
        fabCamera.setOnClickListener {
            // Handle opening the camera or gallery
            Toast.makeText(this, "Opening Camera/Gallery...", Toast.LENGTH_SHORT).show()
            toggleFabMenu() // Close menu after selection
            chooseImages()
        }

        fabGenerate.setOnClickListener {
            // Handle image generation
            Toast.makeText(this, "Generating Image...", Toast.LENGTH_SHORT).show()
            toggleFabMenu() // Close menu after selection
        }

        val new_image_uri:String? = bundle?.getString("new_image_uri")
        if(new_image_uri!=null)
        {
            lifecycleScope.launch {
                saveNewImage(new_image_uri.toUri())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private fun setupGrid() {
        recyclerview = findViewById<RecyclerView>(R.id.rv_grid)
        
            recyclerview.visibility = View.GONE
            recyclerImageAdapter =
                RecyclerImageAdapter(this@RGrid, this@RGrid, mutableListOf()) { isSelecting ->
                    isSelectionMode = isSelecting
                    updateToolbarForSelectionMode()
                }
            val screenWidthDp = resources.configuration.screenWidthDp
            val columns = if (screenWidthDp >= 600) 4 else 3
            val layoutManager: RecyclerView.LayoutManager = GridLayoutManager(this, columns)
            recyclerview.layoutManager = layoutManager
            val spacingInPixels = (8 * resources.displayMetrics.density).toInt()

            // Remove existing decorators if you're calling this on screen rotation/unfolding
            while (recyclerview.itemDecorationCount > 0) {
                recyclerview.removeItemDecorationAt(0)
            }

            recyclerview.addItemDecoration(
                GridSpacingItemDecoration(columns, spacingInPixels, true)
            )
            recyclerview.adapter = recyclerImageAdapter

    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        // Refresh the grid setup when the screen size changes (Fold/Unfold)
        setupGrid()

        // This ensures your adaptive XML (guidelines, max widths)
        // also refreshes if needed.
        setContentView(R.layout.activity_rgrid)
    }

    private fun setupFabMenu() {
        // 1. Initially set the secondary FABs to be invisible and offset
        // This ensures they "slide" into place on the first expansion.
        fabCamera.apply {
            alpha = 0f
            translationY = height.toFloat() * 1.5f // Set translation to be far down
        }
        fabGenerate.apply {
            alpha = 0f
            translationY = height.toFloat() * 1.5f
        }
    }
    private fun toggleFabMenu() {
        if (isFabMenuOpen) {
            // Close the menu: Animate secondary FABs away and rotate main FAB back
            shrinkFab(fabGenerate)
            shrinkFab(fabCamera)
            fabMain.animate().rotation(0f).setDuration(200).start()
        } else {
            // Open the menu: Animate secondary FABs out and rotate main FAB to X
            expandFab(fabCamera)
            expandFab(fabGenerate)
            fabMain.animate().rotation(45f).setDuration(200).start()
        }
        isFabMenuOpen = !isFabMenuOpen
    }

    private fun expandFab(fab: FloatingActionButton) {
        fab.apply {
            visibility = View.VISIBLE
            // Simple animation: fade in and slide up slightly
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    // Helper function to animate and hide a secondary FAB
    private fun shrinkFab(fab: FloatingActionButton) {
        fab.animate()
            .alpha(0f)
            .translationY(fab.height.toFloat() * 1.5f) // Slide down and fade out
            .setDuration(300)
            .withEndAction {
                fab.visibility = View.GONE
            }
            .start()
    }
    override fun onResume() {
        super.onResume()
        startSetupFlow()
        lifecycleScope.launch {
            // Run data loading on IO thread
            withContext(Dispatchers.IO) {
                loadImageLists() // This function is already safe for background execution
            }

            // Ensure UI update runs on the Main thread
            withContext(Dispatchers.Main) {
                updateRecyclerView() // Calls adapter.updateData() safely
            }

        }

    }

     fun isImageTapCompleted(): Boolean {
       // Log.i(TAG, "in isImageTapCompleted")
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_IMAGETAP_COMPLETED, false)
    }

    fun setImageTapCompleted(isCompleted: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with (prefs.edit()) {
            putBoolean(PREF_IMAGETAP_COMPLETED, isCompleted)
            apply()
        }
    }

    private fun updateToolbarForSelectionMode() {
        // Show/hide right-side menu items
        trashMenuItem?.isVisible = isSelectionMode
        val multiSelectMenuItem = toolbar?.menu?.findItem(R.id.action_select_multiple)

        if (isSelectionMode) {
            // Multi-select ON: Show Delete, change Multi-select icon to 'X' or 'Back'
            multiSelectMenuItem?.setIcon(R.drawable.ic_close) // Use an X or a clear "Exit Selection" icon
            multiSelectMenuItem?.setTitle("Close") // Optional: change title for accessibility/tooltip
        } else {
            // Multi-select OFF: Hide Delete, change Multi-select icon back to normal
            multiSelectMenuItem?.setIcon(R.drawable.outline_select_check_box_24) // Your original icon
            multiSelectMenuItem?.setTitle("Select")
        }
       // toolbar?.menu?.findItem(R.id.setduration)?.isVisible = !isSelectionMode
        toolbar?.menu?.findItem(R.id.gotowallpaper)?.isVisible = !isSelectionMode

        // Set left-side navigation icon
        /*toolbar?.setNavigationIcon(
            if (isSelectionMode) R.drawable.ic_back_arrow else R.drawable.ic_back_arrow
        )*/
    }


    private fun exitSelectionMode() {
        if (isSelectionMode) {
            recyclerImageAdapter.exitSelectionMode()
            isSelectionMode = false
            updateToolbarForSelectionMode()
        }
    }

    private suspend fun loadImageLists() {
        withContext(Dispatchers.IO) {
            // Load categorized lists
            homeImages = loadImageList("homeImages").toMutableList()
            lockImages = loadImageList("lockImages").toMutableList()
            bothImages = loadImageList("bothImages").toMutableList()
            clearImages = loadImageList("clearImages").toMutableList()
            //Log.d(TAG, "Loaded lists: home=${homeImages.size}, lock=${lockImages.size}, both=${bothImages.size}, clear=${clearImages.size}")

            // Load all images from imagesPathList
            val allImages = prepareImageListData("imagesPathList", applicationContext)
            //Log.d(TAG, "Loaded ${allImages.size} images from imagesPathList: $allImages")

            // Add uncategorized images to clearImages
            val categorizedImages = (homeImages + lockImages + bothImages + clearImages).distinct()
            val uncategorizedImages = allImages.filter { it !in categorizedImages }
            if (uncategorizedImages.isNotEmpty()) {
                clearImages.addAll(uncategorizedImages)
                saveImageList("clearImages", clearImages)
                //Log.d(TAG, "Added ${uncategorizedImages.size} uncategorized images to clearImages: $uncategorizedImages")
            }

            // Log all images for debugging
            //Log.d(TAG, "Final clearImages: ${clearImages.size} images: $clearImages")

            // If no images are available in any list, notify user and navigate back
            if (homeImages.isEmpty() && lockImages.isEmpty() && bothImages.isEmpty() && clearImages.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RGrid, "No images found. Please add images.", Toast.LENGTH_LONG).show()
                    //navigateToMain()
                }
            }
        }
    }

    private suspend fun loadImageList(arrayName: String): List<String> {
        return withContext(Dispatchers.IO) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val size = prefs.getInt("${arrayName}_size", 0)
            //Log.d(TAG, "Loading $arrayName, size: $size")
            val images = mutableListOf<String>()
            for (index in 0 until size) {
                prefs.getString("${arrayName}_$index", null)?.let { path ->
                    if (path.isNotEmpty()) {
                        try {
                            // Validate URI accessibility
                            contentResolver.openInputStream(Uri.parse(path))?.close()
                            images.add(path)
                            //Log.d(TAG, "Valid URI for $arrayName[$index]: $path")
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Invalid URI for $arrayName[$index]: $path, error: ${e.message}")
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

    suspend fun prepareImageListData(arrayName: String, context: Context): List<String> {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val size = prefs.getInt("${arrayName}_size", 0)
            AppLogger.d(TAG, "Preparing $arrayName, size: $size")
            val images = mutableListOf<String>()
            for (index in 0 until size) {
                prefs.getString("${arrayName}_$index", null)?.let { path ->
                    if (path.isNotEmpty()) {
                        try {
                            context.contentResolver.openInputStream(Uri.parse(path))?.close()
                            images.add(path)
                            //Log.d(TAG, "Valid URI for $arrayName[$index]: $path")
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Invalid URI for $arrayName[$index]: $path, error: ${e.message}")
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

    private suspend fun updateRecyclerView() {
        imagesList.clear()
        when (currentSegment) {

            "all" -> {
                // Add images with their categories
                imagesList.addAll(homeImages.map { ImageItem.fromPath(it, "home") })
                imagesList.addAll(lockImages.map { ImageItem.fromPath(it, "lock") })
                imagesList.addAll(bothImages.map { ImageItem.fromPath(it, "both") })
                imagesList.addAll(clearImages.map { ImageItem.fromPath(it, "clear") })
            }
            "home" -> {
                imagesList.addAll(homeImages.map { ImageItem.fromPath(it, "home") })
                imagesList.addAll(bothImages.map { ImageItem.fromPath(it, "both") })
            }
            "lock" -> {
                imagesList.addAll(lockImages.map { ImageItem.fromPath(it, "lock") })
                imagesList.addAll(bothImages.map { ImageItem.fromPath(it, "both") })
            }
            "both" -> imagesList.addAll(bothImages.map { ImageItem.fromPath(it, "both") })
            "clear" -> imagesList.addAll(clearImages.map { ImageItem.fromPath(it, "clear") })
        }
        AppLogger.d(TAG, "Updating RecyclerView for $currentSegment: ${imagesList.size} images: $imagesList")
        withContext(Dispatchers.Main) {
            recyclerImageAdapter.updateData(imagesList.toList())
            recyclerview.alpha = 0f
            recyclerview.visibility = View.VISIBLE
            recyclerview.animate().alpha(1f).setDuration(300).start()


        }
        val totalGroupedImages = homeImages.size + lockImages.size + bothImages.size
        if (totalGroupedImages == 0 && !hasShownEmptyGroupTip) {
            // Ensure there is at least *one* image in the entire app before showing the tip.
            // Otherwise, the user should be prompted to add an image, not group one.
            if (imagesList.isNotEmpty()) {
                Toast.makeText(this@RGrid, "Long press on Image to group to Home and/or Lock", Toast.LENGTH_LONG).show()
                hasShownEmptyGroupTip = true // Set the flag so it won't show again this session
            }

        }
        if (imagesList.isEmpty() && currentSegment !="all") {
            Toast.makeText(this, "No images in $currentSegment category", Toast.LENGTH_SHORT).show()
        }
        nextButton.visibility = if (totalGroupedImages >= 1 && (!iswallpaperSet() || changeNoticed)) {
            View.VISIBLE
        } else {
            View.GONE
        }

        if (isImageTapCompleted()) {

            spotlightView.visibility = View.GONE
            titleView.visibility = View.GONE

        }
        else {

            recyclerview.post {
                val targetView =
                    recyclerview.findViewHolderForAdapterPosition(0)?.itemView // Get the image view item

                if (targetView != null) {
                    val coords = IntArray(2)
                    targetView.getLocationOnScreen(coords) // Get absolute screen position
                   // Log.i(TAG, "$targetView.getLocationOnScreen(coords)")
                    // 1. Configure and show the spotlight
                    spotlightView.setTargetRect(
                        coords[0],
                        coords[1],
                        coords[0] + targetView.width,
                        coords[1] + targetView.height
                    )
                    spotlightView.visibility = View.VISIBLE

                    // 2. Position the title/instruction text
                    titleView.translationY =
                        coords[1].toFloat() - titleView.height - 50 // Place above the target
                    titleView.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun saveImageList(arrayName: String, list: List<String>) {
        withContext(Dispatchers.IO) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putInt("${arrayName}_size", list.size)
            list.forEachIndexed { index, path ->
                editor.putString("${arrayName}_$index", path)
            }
            editor.apply()
        }
    }

    private suspend fun saveAllImageLists() {
        saveImageList("homeImages", homeImages)
        saveImageList("lockImages", lockImages)
        saveImageList("bothImages", bothImages)
        saveImageList("clearImages", clearImages)
        //Log.d(TAG, "Saved all image lists: home=${homeImages.size}, lock=${lockImages.size}, both=${bothImages.size}, clear=${clearImages.size}")
    }

    private suspend fun deleteSelectedImages() {
        val selectedIds = recyclerImageAdapter.getSelectedIds()
        val delct = selectedIds.size
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "No images selected for deletion", Toast.LENGTH_SHORT).show()
            return
        }

        val imagesToDelete = imagesList.filter { it.id in selectedIds }.map { it.imagePath }
        withContext(Dispatchers.IO) {
            // Load imagesPathList
            val imagesPathList = prepareImageListData("imagesPathList", applicationContext).toMutableList()
            imagesToDelete.forEach { path ->
                when {
                    homeImages.contains(path) -> homeImages.remove(path)
                    lockImages.contains(path) -> lockImages.remove(path)
                    bothImages.contains(path) -> bothImages.remove(path)
                    clearImages.contains(path) -> clearImages.remove(path)
                }
                // Remove from imagesPathList
                imagesPathList.remove(path)
            }
            saveImageList("homeImages", homeImages)
            saveImageList("lockImages", lockImages)
            saveImageList("bothImages", bothImages)
            saveImageList("clearImages", clearImages)
            saveImageList("imagesPathList", imagesPathList)
        }

        withContext(Dispatchers.Main) {
            updateRecyclerView()
            exitSelectionMode()
            Toast.makeText(this@RGrid, "$delct image(s) deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun refreshData() {
        loadImageLists()
        updateRecyclerView()
    }

    private suspend fun deleteSingleImage(id: Long) {
        val imageItem = imagesList.find { it.id == id } ?: return
        val imagePath = imageItem.imagePath
        withContext(Dispatchers.IO) {
            // Load imagesPathList
            val imagesPathList = prepareImageListData("imagesPathList", applicationContext).toMutableList()
            // Remove from the appropriate list
            when {
                homeImages.contains(imagePath) -> {
                    homeImages.remove(imagePath)
                    saveImageList("homeImages", homeImages)
                }
                lockImages.contains(imagePath) -> {
                    lockImages.remove(imagePath)
                    saveImageList("lockImages", lockImages)
                }
                bothImages.contains(imagePath) -> {
                    bothImages.remove(imagePath)
                    saveImageList("bothImages", bothImages)
                }
                clearImages.contains(imagePath) -> {
                    clearImages.remove(imagePath)
                    saveImageList("clearImages", clearImages)
                }
            }
            // Remove from imagesPathList
            imagesPathList.remove(imagePath)
            saveImageList("imagesPathList", imagesPathList)
        }
        withContext(Dispatchers.Main) {
            val position = imagesList.indexOfFirst { it.id == id }
            if (position != -1) {
                imagesList.removeAt(position)
                recyclerImageAdapter.itemRemovedAtPosition(position)
                Toast.makeText(this@RGrid, "Image deleted", Toast.LENGTH_SHORT).show()
            }

            updateRecyclerView()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.top_menu, menu)
        trashMenuItem = menu?.findItem(R.id.delete_images)
        trashMenuItem?.isVisible = false
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.delete_images) {
            lifecycleScope.launch {
                deleteSelectedImages()
            }
            return true
        }
        if (id == R.id.action_select_multiple) {
            if(isSelectionMode)
            {
                recyclerImageAdapter.exitSelectionMode()
            }
            else
            {
                recyclerImageAdapter.enterSelectionModeFromToolbar()
            }
            true
        }
        /*if (id == R.id.setduration) {
            fromPage = ""
            intent.removeExtra("frompage")

            showTimerDialog(this)
            return true
        }*/
        if (id == R.id.gotowallpaper) {
            fromPage = ""
            intent.removeExtra("frompage")
            processWPclearing()
           // showduration(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    // New override for context menu
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.wallpaper_context_menu, menu)
       // menu.setHeaderTitle("Choose")
        // Use your existing logic to find the image item
        val view = recyclerImageAdapter.getLastLongPressedView()

        val id = view?.tag as? Long
        val imageItem = imagesList.find { it.id == id }

        // Check if the item was found
        if (imageItem != null) {
            //Log.d(TAG, "imageItem : ${imageItem.category}")
            // Find the specific menu item by its ID
            val setHomeItem = menu.findItem(R.id.action_set_home)
            val setLockItem = menu.findItem(R.id.action_set_lock)

            // 2. Change the title dynamically based on the category
            if (imageItem.category == "home") {
                setHomeItem?.title = "Remove from Homescreen"
            } else {
                setHomeItem?.title = "Add to Homescreen"
            }
            if (imageItem.category == "lock") {
                setLockItem?.title = "Remove from Lockscreen"
            } else {
                setLockItem?.title = "Add to Lockscreen"
            }
            if (imageItem.category == "both") {
                setHomeItem?.title = "Remove from Homescreen"
                setLockItem?.title = "Remove from Lockscreen"
            }
        }
    }
    override fun onContextItemSelected(item: MenuItem): Boolean {
        val view = recyclerImageAdapter.getLastLongPressedView() ?: return super.onContextItemSelected(item)
        val id = view.tag as? Long ?: return super.onContextItemSelected(item)
        val imageItem = imagesList.find { it.id == id } ?: return super.onContextItemSelected(item)
        val imagePath = imageItem.imagePath
        val category = imageItem.category

        lifecycleScope.launch {
            when (item.itemId) {
                R.id.action_set_home -> {
                    when (imageItem.category) {
                        "both" -> { // Currently Home & Lock

                            moveImageToList(imagePath, "lock") // Target state is only Lock
                        }
                        "home" -> { // Currently only Home

                            moveImageToList(imagePath, "clear") // Target state is Clear
                        }
                        "lock" -> { // Currently only Lock

                            moveImageToList(imagePath, "both") // Target state is Both
                        }
                        else -> { // Currently clear

                            moveImageToList(imagePath, "home") // Target state is Home
                        }
                    }
                    changeNoticed = true
                }
                R.id.action_set_lock -> {
                    when (imageItem.category) {
                        "both" -> { // Currently Home & Lock

                            moveImageToList(imagePath, "home") // Target state is only Home
                        }
                        "lock" -> { // Currently only Lock

                            moveImageToList(imagePath, "clear") // Target state is Clear
                        }
                        "home" -> { // Currently only Home

                            moveImageToList(imagePath, "both") // Target state is Both
                        }
                        else -> { // Currently clear

                            moveImageToList(imagePath, "lock") // Target state is Lock
                        }
                    }
                    changeNoticed = true
                }
                /*R.id.action_set_both -> {
                    moveImageToList(imagePath, "both")
                  //  Toast.makeText(this@RGrid, "Set as Both: $imagePath", Toast.LENGTH_SHORT).show()
                }*/
                /*R.id.action_set_clear -> {
                    moveImageToList(imagePath, "clear")
                  //  Toast.makeText(this@RGrid, "Set as Both: $imagePath", Toast.LENGTH_SHORT).show()
                }*/
               /* R.id.action_view -> {
                    val intent = Intent(this@RGrid, RViewActivity::class.java).apply {
                        putExtra("data", imagePath)
                        putExtra("index", id)
                        putExtra("cat",category)
                    }
                    startActivity(intent)
                    return@launch
                }*/
                R.id.action_delete -> {
                    deleteSingleImage(id)
                }
                else -> return@launch
            }
           // Toast.makeText(this@RGrid, "Set as ${item.title}", Toast.LENGTH_SHORT).show()
            updateRecyclerView()
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

        withContext(Dispatchers.Main) {
            // Instead of reloading everything, just update affected item(s)
            clearGlideCache(applicationContext)

            // Refresh only if necessary
            updateRecyclerView() // or pass targetList to update selectively
        }
    }
    fun clearGlideCache(context: Context) {
        Glide.get(context).clearMemory()
        CoroutineScope(Dispatchers.IO).launch {
            Glide.get(context).clearDiskCache()
        }
    }


    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            navigateToMain()
        }
        super.onBackPressed()
    }

    private fun navigateToMain() {

        val intent = Intent(applicationContext, MainActivity::class.java).apply {

            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        }

        fromPage = ""

        startActivity(intent)

        finish() // Finish RGrid to prevent it from staying in the back stack

        //Log.d(TAG, "Navigating to MainActivity, finishing RGrid")

    }

    @SuppressLint("SuspiciousIndentation")
    fun showTimerDialog(activity: Activity) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_timer, null)
        val hourPicker = dialogView.findViewById<NumberPicker>(R.id.hour_picker)
        hourPicker.minValue = 0
        hourPicker.maxValue = 23
        val minutePicker = dialogView.findViewById<NumberPicker>(R.id.minute_picker)
        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        val secondPicker = dialogView.findViewById<NumberPicker>(R.id.second_picker)
        secondPicker.minValue = 0
        secondPicker.maxValue = 59
        val timerValue = dialogView.findViewById<TextView>(R.id.timer_value)

        val prevhours = TimeUnit.MILLISECONDS.toHours(settime)
        val prevminutes = TimeUnit.MILLISECONDS.toMinutes(settime) % 60
        val prevseconds = TimeUnit.MILLISECONDS.toSeconds(settime) % 60
        hourPicker.value = prevhours.toInt()
        minutePicker.value = prevminutes.toInt()
        secondPicker.value = prevseconds.toInt()
        var timetext = "Wallpapers change every: $prevseconds sec"
        if(prevminutes.toInt()>=1)
            timetext = "Wallpapers change every: $prevminutes mins $prevseconds sec"
        else if(prevhours.toInt()>=1)
            timetext = "Wallpapers change every: $prevhours hrs $prevminutes mins $prevseconds sec"
        timerValue.text = String.format(timetext)
        activity.findViewById<TextView>(R.id.duration_status_text).text = String.format(timetext)
        val updateTimerText = {
            val hours = hourPicker.value
            val minutes = minutePicker.value
            val seconds = secondPicker.value
            timetext = "Wallpapers change every: $prevseconds sec"
            if(minutes.toInt()>=1)
                timetext = "Wallpapers change every: $prevminutes mins $prevseconds sec"
            else if(hours.toInt()>=1)
                timetext = "Wallpapers change every: $prevhours hrs $prevminutes mins $prevseconds sec"
            timerValue.text = String.format(timetext)
            //activity.findViewById<TextView>(R.id.duration_status_text).text = String.format(timetext)

        }

        hourPicker.setOnValueChangedListener { _, _, _ -> updateTimerText() }
        minutePicker.setOnValueChangedListener { _, _, _ -> updateTimerText() }
        secondPicker.setOnValueChangedListener { _, _, _ -> updateTimerText() }

        val dialog = android.app.AlertDialog.Builder(ContextThemeWrapper(activity, R.style.AlertDialogCustom))
            .setTitle("Set Timer Duration")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedHours = hourPicker.value
                val selectedMinutes = minutePicker.value
                val selectedSeconds = secondPicker.value
                val totalMillis = (selectedHours * 3600000) + (selectedMinutes * 60000) + (selectedSeconds * 1000)
               // Toast.makeText(activity, "Timer Set: $selectedHours h $selectedMinutes m $selectedSeconds s", Toast.LENGTH_SHORT).show()
                saveduration(totalMillis.toInt())
            }
            .setNegativeButton("Cancel", null)
        dialog.show()
    }

    fun showTimerDialog(activity: Activity, onTimeSet: (Long) -> Unit) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_timer, null)
        val hourPicker = dialogView.findViewById<NumberPicker>(R.id.hour_picker)
        hourPicker.minValue = 0
        hourPicker.maxValue = 23
        val minutePicker = dialogView.findViewById<NumberPicker>(R.id.minute_picker)
        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        val secondPicker = dialogView.findViewById<NumberPicker>(R.id.second_picker)
        secondPicker.minValue = 0
        secondPicker.maxValue = 59
        val timerValue = dialogView.findViewById<TextView>(R.id.timer_value)

        // --- Initial Value Setup (Uses settime) ---
        val prevhours = TimeUnit.MILLISECONDS.toHours(settime)
        val prevminutes = TimeUnit.MILLISECONDS.toMinutes(settime) % 60
        val prevseconds = TimeUnit.MILLISECONDS.toSeconds(settime) % 60
        hourPicker.value = prevhours.toInt()
        minutePicker.value = prevminutes.toInt()
        secondPicker.value = prevseconds.toInt()

        // NOTE: This initial text update for the dialog and activity is using the old 'settime'.
        // We will fix the 'updateTimerText' logic below.
        val updateTimerText = {
            val hours = hourPicker.value
            val minutes = minutePicker.value
            val seconds = secondPicker.value


            var currentTimetext = "Wallpapers change every: $seconds sec"
            if(minutes >= 1) {
                currentTimetext = "Wallpapers change every: $minutes mins $seconds sec"
            }
            if(hours >= 1) { // Checks hours last for correct full display
                currentTimetext = "Wallpapers change every: $hours hrs $minutes mins $seconds sec"
            }

            // Update the TextView inside the dialog
            timerValue.text = currentTimetext
            // Removed the commented line that incorrectly tried to update the main activity here
        }

        // Initial call to set the text based on default picker values
        updateTimerText()

        hourPicker.setOnValueChangedListener { _, _, _ -> updateTimerText() }
        minutePicker.setOnValueChangedListener { _, _, _ -> updateTimerText() }
        secondPicker.setOnValueChangedListener { _, _, _ -> updateTimerText() }

        val dialog = android.app.AlertDialog.Builder(ContextThemeWrapper(activity, R.style.AlertDialogCustom))
            .setTitle("Set Timer Duration")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedHours = hourPicker.value
                val selectedMinutes = minutePicker.value
                val selectedSeconds = secondPicker.value
                val totalMillis = (selectedHours * 3600000L) + (selectedMinutes * 60000L) + (selectedSeconds * 1000L) // Use Long literal for safety

              //  Toast.makeText(activity, "Timer Set: $selectedHours h $selectedMinutes m $selectedSeconds s", Toast.LENGTH_SHORT).show()

                // 1. SAVE the new duration
                saveduration(totalMillis.toInt())

                // 2. TRIGGER the callback to update the main Activity's TextView
                onTimeSet.invoke(totalMillis)
            }
            .setNegativeButton("Cancel", null)
        dialog.show()
    }

    private fun updateDurationText(newSetTime: Long) {
        settime = newSetTime
        val prevhours = TimeUnit.MILLISECONDS.toHours(settime)
        val prevminutes = TimeUnit.MILLISECONDS.toMinutes(settime) % 60
        val prevseconds = TimeUnit.MILLISECONDS.toSeconds(settime) % 60

        var timetext: String
        if (prevhours >= 1) {
            timetext = "Wallpapers change every: %d hrs %d mins %d sec"
            timetext = String.format(timetext, prevhours, prevminutes, prevseconds)
        } else if (prevminutes >= 1) {
            timetext = "Wallpapers change every: %d mins %d sec"
            timetext = String.format(timetext, prevminutes, prevseconds)
        } else {
            timetext = "Wallpapers change every: %d sec"
            timetext = String.format(timetext, prevseconds)
        }

        val leadingSpace = " "
        val iconPlaceholder = " " // Single space to hold the icon
        val actionWord = "Change"
        val fullActionText = leadingSpace + iconPlaceholder + actionWord // E.g., " [icon] Change"


        val finalString = timetext + fullActionText
        val spannableStringBuilder = SpannableStringBuilder(finalString)

        val startOfAction = timetext.length + leadingSpace.length
        val endOfAction = finalString.length
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(view: View) {
                // Use the Activity's context or the view's context to show the dialog
                showTimerDialog(this@RGrid, ::updateDurationText)
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                ds.color = ContextCompat.getColor(this@RGrid, R.color.blue_200)
            }
        }
        spannableStringBuilder.setSpan(clickableSpan, startOfAction, endOfAction, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val drawable = ContextCompat.getDrawable(this, R.drawable.baseline_access_time_24)
        val iconSize = (durationtv.textSize * 1.2).toInt() // Scale icon slightly larger than text
        drawable?.setBounds(0, 0, iconSize, iconSize)// Set bounds for the image
        if (drawable != null) {
            val imageSpan = ImageSpan(drawable, ImageSpan.ALIGN_CENTER)

            // The ImageSpan applies only to the single space reserved for the icon
            val startOfIcon = timetext.length + leadingSpace.length
            val endOfIcon = startOfIcon + iconPlaceholder.length

            spannableStringBuilder.setSpan(imageSpan, startOfIcon, endOfIcon, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        durationtv.movementMethod = LinkMovementMethod.getInstance()
        durationtv.text = spannableStringBuilder
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SuspiciousIndentation")
    fun showduration(activity: Activity) {
        val builder = android.app.AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))
        val prevhours = TimeUnit.MILLISECONDS.toHours(settime)
        val prevminutes = TimeUnit.MILLISECONDS.toMinutes(settime) % 60
        val prevseconds = TimeUnit.MILLISECONDS.toSeconds(settime) % 60
        var timetext = "Wallpapers change every: $prevseconds sec"
        if(prevminutes.toInt()>=1)
            timetext = "Wallpapers change every: $prevminutes mins $prevseconds sec"
        else if(prevhours.toInt()>=1)
            timetext = "Wallpapers change every: $prevhours hrs $prevminutes mins $prevseconds sec"
        val timerValue = String.format(timetext)
        activity.findViewById<TextView>(R.id.duration_status_text).text = String.format(timetext)
       // val timerValue = String.format("Wallpapers change every : $prevhours hrs $prevminutes mins $prevseconds sec")
        with(builder) {
            setTitle("Confirm")
            setMessage("$timerValue. Would you like to update it?")
            setPositiveButton("Yes") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                showTimerDialog(activity)
            }
            setNegativeButton("No") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                processWPclearing()
            }
            show()
        }
    }
    private fun processWPclearing() {
        /*if (homeImages.size + lockImages.size + bothImages.size == 1) {
            Toast.makeText(this, "Add/group more images to preview slideshow", Toast.LENGTH_SHORT).show()
        } else*/ if (homeImages.size + lockImages.size + bothImages.size == 0) {
            Toast.makeText(this, "Long press on image to group to Home and/or Lock", Toast.LENGTH_SHORT).show()
            //navigateToMain()
        }
        else
        {
        var dialogtext = ""
        if (iswallpaperSet()) {
            val wpm = WallpaperManager.getInstance(this)

            dialogtext = "Clearing existing wallpaper..."
        }
        else
            dialogtext = "Preparing your wallpaper..."
        val loadingDialog = ProgressDialogFragment.newInstance(dialogtext)
        loadingDialog.show(supportFragmentManager, "progress")

        CoroutineScope(Dispatchers.IO).launch {

            withContext(Dispatchers.Main) {

              /*  val wallpaperManager = WallpaperManager.getInstance(applicationContext)
                try {
                    wallpaperManager.clear()
                } catch (e: IOException) {
                    e.printStackTrace()
                }*/
                kotlinx.coroutines.delay(3000L)

                val existingDialog =
                    supportFragmentManager.findFragmentByTag("progress") as? DialogFragment
                existingDialog?.dismiss()
                var prefs = getSharedPreferences("previewwall", Context.MODE_PRIVATE)
                var editor = prefs.edit()
                editor.clear().commit()
                editor.putString("previewwall", currentSegment)
                editor.commit()
                callforeGroundService()

            }
        }
    }
    }

    @SuppressLint("SuspiciousIndentation")
    @RequiresApi(Build.VERSION_CODES.O)
    fun callforeGroundService() {
        fromPage = ""
        intent.removeExtra("frompage")

        if (homeImages.size + lockImages.size + bothImages.size >= 1) {

            /*
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val isServiceRunning = activityManager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == WallpaperForegroundService::class.java.name }

            if (!isServiceRunning) {

             */
                val serviceIntent = Intent(this, WallpaperForegroundService::class.java)
                serviceIntent.putExtra("isPreview", true)
                startForegroundService(serviceIntent)
           /*     Log.d("RGrid", "Started WallpaperForegroundService")
            } else {
                Log.d("RGrid", "WallpaperForegroundService already running, skipping start")
            }*/

        }

    }

    fun showMain() {
        Toast.makeText(this, "Add images to create a slider", Toast.LENGTH_SHORT).show()
        //navigateToMain()
    }

    private fun saveduration(selduration: Int) {
        settime = selduration.toLong()
        var prefs = getSharedPreferences("slideduration", Context.MODE_PRIVATE)
        var editor = prefs.edit()
        editor.clear().commit()

        prefs = getSharedPreferences("slideduration", Context.MODE_PRIVATE)
        editor = prefs.edit()
        editor.putInt("slideDuration", selduration)
        editor.commit()
    }

    private fun iswallpaperSet(): Boolean {
        try {
            val wpm = WallpaperManager.getInstance(this)
            val info = wpm.wallpaperInfo
            if (info != null && info.packageName == this.packageName) {
                AppLogger.d(TAG, "We're already running")
                return true
            } else {
                AppLogger.d(TAG, "We're not running")
                return false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, e.message.toString(), e)
        }
        return false
    }

    /**
     * Checks the current wallpaper status (package name and flags).
     */
    private fun checkWallpaperStatus() {
        // WallpaperManager requires the context.
        val wallpaperManager = WallpaperManager.getInstance(this)

        try {
            // 1. Check which package is providing the current wallpaper
            val componentName = wallpaperManager.wallpaperInfo?.component

            val wallpaperPackageName = componentName?.packageName ?: "System/Static Wallpaper"

            AppLogger.d(TAG, "Current Wallpaper Package: $wallpaperPackageName")

            // 2. Check the set flags (only reliable on newer APIs or for non-live wallpapers)
            // Note: wallpaperManager.getWallpaperId() is needed for flags on API 24+ (N)
            var flagsInfo = ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val homeId = wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                val lockId = wallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK)

                // If both IDs are the same non-zero value, it's set to both.
                if (homeId > 0 || lockId > 0) {
                    if (homeId > 0) flagsInfo += "HOME SCREEN (FLAG_SYSTEM) "
                    if (lockId > 0) flagsInfo += "LOCK SCREEN (FLAG_LOCK) "
                } else {
                    // Fallback for system default or if IDs are not distinct
                    flagsInfo = "Standard Wallpaper (Flags not distinct)"
                }
            } else {
                // Pre-N, it's typically just the system wallpaper
                flagsInfo = "HOME SCREEN (Pre-API 24)"
            }

            // 3. Display the results in the log
            AppLogger.i(TAG, "--- WALLPAPER STATUS ---")
            AppLogger.i(TAG, "Provider Package: $wallpaperPackageName")
            AppLogger.i(TAG, "Set Flags: $flagsInfo")
            AppLogger.i(TAG, "------------------------")


            // You can also show a Toast for quick debugging on device
            // Toast.makeText(this, "WP Package: $wallpaperPackageName | Flags: $flagsInfo", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking wallpaper status: ${e.message}")
            // Toast.makeText(this, "Failed to check wallpaper status.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                AppLogger.d(TAG, "POST_NOTIFICATIONS permission granted")
            } else {
                AppLogger.w(TAG, "POST_NOTIFICATIONS permission denied")
                Toast.makeText(this, "Notification permission denied. Wallpaper service may not work.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun registerLaunchers() {
        // Use GetMultipleContents for picking multiple images easily
        pickImagesLauncher =
            registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
                if (!uris.isNullOrEmpty()) {
                    processImages(uris)
                }
            }
    }

    fun chooseImages() {
        pickImagesLauncher.launch("image/*")
    }


    private fun processImages(imageUris: List<Uri>) {

        val loadingDialog = ProgressDialogFragment.newInstance("Processing images...")
        loadingDialog.show(supportFragmentManager, "progress")

        // FIX: Request persistent read access for the URIs to prevent SecurityExceptions
        imageUris.forEach { uri ->
            try {
                // Request both read and write, though read is the crucial part for processing
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // This can happen if the content provider doesn't support persistable permissions
                AppLogger.w(TAG, "Provider does not support persistable URI permission for $uri: ${e.message}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to take persistable URI permission for $uri: ${e.message}")
            }
        }

        lifecycleScope.launch {
            val savedImages = imageUris.mapNotNull { uri ->
                saveImage(applicationContext, uri)
            }

           // withContext(Dispatchers.Main) {
                val imagesPathList = prepareImageListData("imagesPathList", applicationContext).toMutableList()
                imagesPathList.addAll(savedImages.map { it.toString() })
                clearImages.addAll(savedImages.map { it.toString() })
                saveImageList("imagesPathList", imagesPathList)
                saveImageList("clearImages", clearImages)
                updateRecyclerView()

                val existingDialog = supportFragmentManager.findFragmentByTag("progress") as? DialogFragment
                existingDialog?.dismiss()

            //}
        }
    }


    private fun saveImage(context: Context, uri: Uri): Uri? {
        val resolver = context.contentResolver
        val bitmap = BitmapFactory.decodeStream(resolver.openInputStream(uri)) ?: return null

        val displayName = uri.lastPathSegment ?: "wall_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WallPaperApp")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        }

        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.also { savedUri ->
            resolver.openOutputStream(savedUri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream) // Lower quality slightly for optimization
            }
        }
    }

    private suspend fun saveNewImage(imageURI:Uri)
    {
        withContext(Dispatchers.Main) {
            val imagesPathList =
                prepareImageListData("imagesPathList", applicationContext).toMutableList()
            imagesPathList.add(imageURI.toString())
            clearImages.add(imageURI.toString())
            saveImageList("imagesPathList", imagesPathList)
            saveImageList("clearImages", clearImages)
            updateRecyclerView()
            Toast.makeText(applicationContext, "Saved a copy of Image. Please group it to home and/or lock", Toast.LENGTH_LONG).show()
            changeNoticed = true
        }
    }

    private fun startSetupFlow() {
        when {
            // Check 1: Runtime Permission (Android 13+)
          /*  !permissionCoordinator.hasNotificationPermission() -> {
                requestNotificationPermission()
            }
            // Check 2: Notification Channel (Settings)
            !permissionCoordinator.isNotificationChannelEnabled() -> {
                showEnableNotificationsDialog()
            }*/
            // Check 3: Battery Optimization
            !permissionCoordinator.isIgnoringBatteryOptimizations() -> {
                requestIgnoreBatteryOptimizations()
            }
            else -> {
                // All good — proceed with normal app behavior
                AppLogger.d(TAG, "All permissions/settings satisfied")
            }
        }
    }
    private fun requestNotificationPermission() {
        // Delegate permission request to the coordinator
        permissionCoordinator.requestNotificationPermission()
    }

    private fun showEnableNotificationsDialog() {
        val builder = android.app.AlertDialog.Builder(
            androidx.appcompat.view.ContextThemeWrapper(
                this,
                R.style.AlertDialogCustom
            )
        )
            .setTitle("Enable Notifications")
            .setMessage("This app requires notifications to run the wallpaper service. Please enable the 'Wallpaper Slider' channel in settings.")
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                openNotificationSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Notifications are required for the wallpaper service to work.", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
        try {
            builder.show()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to show notifications dialog: ${e.message}")
        }
    }

    private fun openNotificationSettings() {
        // Delegate opening settings to the coordinator
        permissionCoordinator.openNotificationSettings()
    }

    // ---------- Battery optimization ----------

    // Delegated to coordinator: private fun isIgnoringBatteryOptimizations(): Boolean { ... }

    private fun requestIgnoreBatteryOptimizations() {
        // Delegate battery optimization request to the coordinator
        permissionCoordinator.requestIgnoreBatteryOptimizations()
    }


}
