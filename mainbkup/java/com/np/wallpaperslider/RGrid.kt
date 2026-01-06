package com.np.wallpaperslider

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.ProgressDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

class RGrid : AppCompatActivity() {

    private var imagesList = mutableListOf<Imagepath>()
    var REQUEST_SET_LIVE_WALLPAPER = 200
    val permisson_code = 100
    var toolbar: Toolbar? = null
    var settime = 0L
    private var trashMenuItem: MenuItem? = null
    private var isSelectionMode = false

    lateinit var fromPage: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rgrid)

        toolbar = findViewById<MaterialToolbar>(R.id.xml_toolbar).also {
            setSupportActionBar(it)
        }
        var bundle: Bundle? = intent.extras
        fromPage = bundle?.getString("frompage") ?: ""
        Log.i("bitmappos", "frompage $fromPage")
        if (fromPage != "main" && iswallpaperSet()) {
            //exitSelectionMode()
            moveTaskToBack(true)
            /*window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )*/
            finishAndRemoveTask()
            return
        }
        toolbar?.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar?.setNavigationOnClickListener {
            exitSelectionMode()
            navigateToMain()
        }
        val prefs = getSharedPreferences("slideduration", Context.MODE_PRIVATE)
        settime = (prefs.getInt("slideDuration", 30000)).toLong()

        fromPage = ""
        intent.removeExtra("frompage")
        imagesList = ArrayList()
        recyclerview = findViewById<RecyclerView>(R.id.rv_grid)
        recyclerview!!.visibility = View.GONE
        recyclerImageAdapter = RecyclerImageAdapter(this@RGrid, this@RGrid, imagesList) { isSelecting ->
            isSelectionMode = isSelecting
            updateToolbarForSelectionMode()
        }
        var layoutManager: RecyclerView.LayoutManager = GridLayoutManager(this, 3)
        recyclerview!!.layoutManager = layoutManager
        recyclerview!!.adapter = recyclerImageAdapter


        lifecycleScope.launch {
            var loadingDialog: ProgressDialog? = null
            try {

                val newList = prepareImageListData("imagesPathList", applicationContext)

                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        imagesList.clear()
                        imagesList.addAll(newList)
                        recyclerImageAdapter.updateData(newList)
                        recyclerview.alpha = 0f
                        recyclerview.visibility = View.VISIBLE
                        recyclerview.animate().alpha(1f).setDuration(300).start()
                    }
                }

            } finally {
                /*withContext(Dispatchers.Main) {
                    loadingDialog?.takeIf { it.isShowing }?.dismiss()
                    Log.d("bitmappos", "ggg ")
                }*/
            }
        }
    }

    private fun updateToolbarForSelectionMode() {
        // Show/hide right-side menu items
        trashMenuItem?.isVisible = isSelectionMode
        toolbar?.menu?.findItem(R.id.setduration)?.isVisible = !isSelectionMode
        toolbar?.menu?.findItem(R.id.gotowallpaper)?.isVisible = !isSelectionMode

        // Set left-side navigation icon
        toolbar?.setNavigationIcon(
            if (isSelectionMode) R.drawable.ic_back_arrow else R.drawable.ic_back_arrow
        )
    }


    private fun exitSelectionMode() {
        if (isSelectionMode) {
            recyclerImageAdapter.exitSelectionMode()
            isSelectionMode = false
            updateToolbarForSelectionMode()
        }
    }

    suspend fun prepareImageListData(arrayName: String, context: Context): List<Imagepath> {
        return withContext(Dispatchers.IO) {
            val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
            val size = prefs.getInt("${arrayName}_size", 0)
            List(size) { index ->
                prefs.getString("${arrayName}_$index", "") ?: ""
            }.map { Imagepath(it) }
        }
    }

    private suspend fun deleteSelectedImages() {
        val selectedPositions = recyclerImageAdapter.getSelectedPositions()
        val delct = selectedPositions.size
        if (selectedPositions.isEmpty()) {
            Toast.makeText(this, "No images selected for deletion", Toast.LENGTH_SHORT).show()
            return
        }

        withContext(Dispatchers.IO) {
            val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            val currentSize = prefs.getInt("imagesPathList_size", 0)
            val newList = imagesList.filterIndexed { index, _ -> index !in selectedPositions }.map { it.imagepath }
            editor.putInt("imagesPathList_size", newList.size)
            newList.forEachIndexed { index, path ->
                editor.putString("imagesPathList_$index", path)
            }
            editor.apply()
        }


        withContext(Dispatchers.Main) {
            val refreshedList = prepareImageListData("imagesPathList", applicationContext)

            imagesList.clear()
            imagesList.addAll(refreshedList)
            recyclerImageAdapter.updateData(refreshedList)

            exitSelectionMode()

            recyclerview.alpha = 0f
            recyclerview.visibility = View.VISIBLE
            recyclerview.animate().alpha(1f).setDuration(300).start()

            Toast.makeText(this@RGrid, "$delct image(s) deleted", Toast.LENGTH_SHORT).show()
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
        if (id == R.id.setduration) {
            fromPage = ""
            intent.removeExtra("frompage")

            showTimerDialog(this)
            return true
        }
        if (id == R.id.gotowallpaper) {
            fromPage = ""
            intent.removeExtra("frompage")

            showduration(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            navigateToMain()
        }
    }

    private fun navigateToMain() {

        val intent = Intent(applicationContext, MainActivity::class.java).apply {

            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        }

        fromPage = ""

        startActivity(intent)

        finish() // Finish RGrid to prevent it from staying in the back stack

        Log.d("RGrid", "Navigating to MainActivity, finishing RGrid")

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
        timerValue.text = String.format("Selected Duration: $prevhours hrs $prevminutes mins $prevseconds sec")

        val updateTimerText = {
            val hours = hourPicker.value
            val minutes = minutePicker.value
            val seconds = secondPicker.value
            timerValue.text = String.format("Selected Duration: $hours hrs $minutes mins $seconds sec")
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
                Toast.makeText(activity, "Timer Set: $selectedHours h $selectedMinutes m $selectedSeconds s", Toast.LENGTH_SHORT).show()
                saveduration(totalMillis)
            }
            .setNegativeButton("Cancel", null)
        dialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SuspiciousIndentation")
    fun showduration(activity: Activity) {
        val builder = android.app.AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))
        val prevhours = TimeUnit.MILLISECONDS.toHours(settime)
        val prevminutes = TimeUnit.MILLISECONDS.toMinutes(settime) % 60
        val prevseconds = TimeUnit.MILLISECONDS.toSeconds(settime) % 60
        val timerValue = String.format("Selected Frequency of Image slide is: $prevhours hrs $prevminutes mins $prevseconds sec")
        with(builder) {
            setTitle("Confirm")
            setMessage("$timerValue. Would you like to update it?")
            setPositiveButton("Yes") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                showTimerDialog(activity)
            }
            setNegativeButton("No") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                if (Build.VERSION.SDK_INT > 16) {
                    val wallpaperManager = WallpaperManager.getInstance(applicationContext)
                    try {
                        wallpaperManager.clear()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                callforeGroundService()
            }
            show()
        }
    }

    @SuppressLint("SuspiciousIndentation")
    @RequiresApi(Build.VERSION_CODES.O)
    fun callforeGroundService() {
        fromPage = ""
        intent.removeExtra("frompage")

        if (imagesList.size >= 2) {


            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val isServiceRunning = activityManager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == WallpaperForegroundService::class.java.name }

            if (!isServiceRunning) {
                val serviceIntent = Intent(this, WallpaperForegroundService::class.java)
                startForegroundService(serviceIntent)
                Log.d("RGrid", "Started WallpaperForegroundService")
            } else {
                Log.d("RGrid", "WallpaperForegroundService already running, skipping start")
            }

        } else {
            navigateToMain()
        }
    }

    fun showMain() {
        Toast.makeText(this, "Add images to create a slider", Toast.LENGTH_SHORT).show()
        navigateToMain()
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
                Log.d("bitmappos", "We're already running")
                return true
            } else {
                Log.d("bitmappos", "We're not running")
                return false
            }
        } catch (e: Exception) {
            Log.e("bitmappos....", e.message, e)
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("RGrid", "POST_NOTIFICATIONS permission granted")
            } else {
                Log.w("RGrid", "POST_NOTIFICATIONS permission denied")
                Toast.makeText(this, "Notification permission denied. Wallpaper service may not work.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        lateinit var recyclerview: RecyclerView
        lateinit var recyclerImageAdapter: RecyclerImageAdapter
    }
}