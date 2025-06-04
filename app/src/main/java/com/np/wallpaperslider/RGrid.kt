package com.np.wallpaperslider

import android.annotation.SuppressLint
import android.app.Activity
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
import android.view.WindowManager
import android.widget.NumberPicker
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import java.io.IOException
import java.util.concurrent.TimeUnit

class RGrid:AppCompatActivity() {

    private var imagesList = mutableListOf<Imagepath>()
    var REQUEST_SET_LIVE_WALLPAPER = 200
    val permisson_code = 100
    var toolbar:Toolbar?=null
    var settime = 0L

    lateinit var fromPage : String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rgrid)

        toolbar = findViewById<MaterialToolbar>(R.id.xml_toolbar).also {
            setSupportActionBar(it)


        }
        var bundle :Bundle ?=intent.extras
        fromPage = bundle?.getString("frompage") ?: ""
        //Log.i("bitmappos","frompage $fromPage")
            if (fromPage!="main" && iswallpaperSet()) {

                finishAndRemoveTask()
                return
                /*moveTaskToBack(true)
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )*/
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            101
                        )
                    }
                }
            }

            toolbar?.setNavigationIcon(R.drawable.ic_back_arrow)
            toolbar?.setNavigationOnClickListener {
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                fromPage=""
                startActivity(intent)
                finishAndRemoveTask() // Closes the current activity
            }
            val prefs = getSharedPreferences("slideduration", Context.MODE_PRIVATE)
            settime = (prefs.getInt("slideDuration", 30000)).toLong()
        imagesList = ArrayList()
        recyclerview = findViewById<RecyclerView>(R.id.rv_grid)
        recyclerImageAdapter = RecyclerImageAdapter(this@RGrid,this@RGrid,imagesList)
        var layoutManager : RecyclerView.LayoutManager = GridLayoutManager(this,3)
        recyclerview!!.layoutManager = layoutManager
        recyclerview!!.adapter = recyclerImageAdapter
        recyclerImageAdapter!!.updateData()
        prepareImageListData("imagesPathList", applicationContext)



    }



    private fun prepareImageListData(arrayName: String, context: Context) {
        val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        val size = prefs.getInt("${arrayName}_size", 0)

        val array = Array(size) { "" }
        for (i in 0 until size) {
            array[i] = prefs.getString("${arrayName}_$i", null) ?: ""
            //Toast.makeText(context,"output here.."+array[i],Toast.LENGTH_SHORT).show()
            imagesList.add(Imagepath(array[i]))
        }
        recyclerImageAdapter!!.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.top_menu,menu)
        return true
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        if(id == R.id.setduration){
            fromPage=""
            //showSeekBarDialog(this)
            showTimerDialog(this)
        }
        return if (id == R.id.gotowallpaper) {
            fromPage=""
            showduration(this)

            true
        } else super.onOptionsItemSelected(item)
    }

    @SuppressLint("SuspiciousIndentation")
    fun showTimerDialog(activity: Activity) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_timer, null)
        val hourPicker = dialogView.findViewById<NumberPicker>(R.id.hour_picker)
        hourPicker.minValue=0
        hourPicker.maxValue=23
        val minutePicker = dialogView.findViewById<NumberPicker>(R.id.minute_picker)
        minutePicker.minValue=0
        minutePicker.maxValue=59
        val secondPicker = dialogView.findViewById<NumberPicker>(R.id.second_picker)
        secondPicker.minValue=0
        secondPicker.maxValue=59
        val timerValue = dialogView.findViewById<TextView>(R.id.timer_value)


        val prevhours = TimeUnit.MILLISECONDS.toHours(settime)
        val prevminutes = TimeUnit.MILLISECONDS.toMinutes(settime) % 60
        val prevseconds = TimeUnit.MILLISECONDS.toSeconds(settime) % 60
        hourPicker.value = prevhours.toInt()
        minutePicker.value = prevminutes.toInt()
        secondPicker.value = prevseconds.toInt()
        timerValue.text = String.format("Selected Duration: $prevhours hrs $prevminutes mins $prevseconds sec")
        // Update display when value changes
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
        //val dialog = AlertDialog.Builder(activity, R.style.AlertTheme)
            .setTitle("Set Timer Duration")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedHours = hourPicker.value
                val selectedMinutes = minutePicker.value
                val selectedSeconds = secondPicker.value

                val totalMillis = (selectedHours * 3600000) + (selectedMinutes * 60000) + (selectedSeconds * 1000)
                Toast.makeText(activity, "Timer Set: $selectedHours h $selectedMinutes m $selectedSeconds s", Toast.LENGTH_SHORT).show()
                saveduration(totalMillis)
                // Save duration in SharedPreferences or apply it to wallpaper transition logic
            }
            .setNegativeButton("Cancel", null)

           dialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SuspiciousIndentation")
    fun showduration(activity: Activity)
    {
        val builder = android.app.AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))
        val prevhours = TimeUnit.MILLISECONDS.toHours(settime)
        val prevminutes = TimeUnit.MILLISECONDS.toMinutes(settime) % 60
        val prevseconds = TimeUnit.MILLISECONDS.toSeconds(settime) % 60
        val timerValue = String.format("Selected Frequency of Image slide is: $prevhours hrs $prevminutes mins $prevseconds sec")
        with(builder)
        {
            setTitle("Confirm")
            setMessage("$timerValue. Would you like to update it?")
            // setPositiveButton("Yes", DialogInterface.OnClickListener(function = positiveButtonClick))
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
    fun callforeGroundService()
    {
        if(imagesList.size>=2) {

            //val intent = Intent(applicationContext,MyBackgroundService::class.java)
             val serviceIntent = Intent(this,WallpaperForegroundService::class.java)
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                 startForegroundService(serviceIntent) // Correct method for foreground service
             /*} else {
            startService(serviceIntent)
             }*/
            finishAndRemoveTask()
       }
        else
        {
            showMain()
        }

    }

    fun showMain(){
        Toast.makeText(this,"Add images to create a slider",Toast.LENGTH_SHORT).show()
        val intent = Intent(this,MainActivity::class.java)
        startActivity(intent)
    }

    fun showSeekBarDialog(activity: Activity) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_seekbar, null)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekbar)
        val seekBarValue = dialogView.findViewById<TextView>(R.id.seekbar_value)
        val prefs = activity.getSharedPreferences("slideduration", Context.MODE_PRIVATE)

        seekBar.progress = prefs.getInt("slideDuration", 5000)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                seekBarValue.text = "Duration: $progress ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        AlertDialog.Builder(activity)
            .setTitle("Set Wallpaper Change Duration")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedDuration = seekBar.progress
                Toast.makeText(activity, "Selected Duration: $selectedDuration ms", Toast.LENGTH_SHORT).show()
                saveduration(selectedDuration)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun saveduration(selduration:Int)
    {
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
        }catch (e: Exception)
        {
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
                // Proceed with service start if needed
            } else {
                Log.w("RGrid", "POST_NOTIFICATIONS permission denied")
                Toast.makeText(this, "Notification permission denied. Wallpaper service may not work.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object{
        lateinit var recyclerview:RecyclerView
        lateinit var recyclerImageAdapter:RecyclerImageAdapter
    }


}