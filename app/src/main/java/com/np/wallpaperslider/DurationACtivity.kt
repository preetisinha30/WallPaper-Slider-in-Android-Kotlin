package com.np.wallpaperslider


import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException



class DurationACtivity:AppCompatActivity() {

        lateinit var radio_group: RadioGroup
        lateinit var button:Button
        lateinit var slideDuration:String
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setFinishOnTouchOutside(false)
            setContentView(R.layout.activity_duration)
            var bundle :Bundle ?=intent.extras
            slideDuration = bundle?.getString("slideDuration").toString()
            radio_group=findViewById<RadioGroup>(R.id.radio_group)
            button=findViewById<Button>(R.id.button)

            // Get radio group selected item
            // using on checked change listener
           /* radio_group.setOnCheckedChangeListener(
                RadioGroup.OnCheckedChangeListener { group, checkedId ->
                    val radio: RadioButton = findViewById(checkedId)
                    Toast.makeText(applicationContext," On checked change :"+
                            " ${radio.text}",
                        Toast.LENGTH_SHORT).show()
                })
            */
            // Get radio group selected status
            // and text using button click event
            button.setOnClickListener{

                // Get the checked radio button id from radio group
                var id: Int = radio_group.checkedRadioButtonId

                if (id!=-1){

                    // If any radio button checked from radio group
                    // Get the instance of radio button using id
                    val radio:RadioButton = findViewById(id)
                    if(radio.text == "2 sec")
                        slideDuration = "2000"
                    else if(radio.text == "5 sec")
                        slideDuration = "5000"
                    else if(radio.text == "10 sec")
                        slideDuration = "10000"
                    else if(radio.text == "30 sec")
                        slideDuration = "30000"
                    /*Toast.makeText(applicationContext,"On button click :" +
                            " ${slideDuration}",
                        Toast.LENGTH_SHORT).show()*/
                    var prefs = getSharedPreferences("slideduration", Context.MODE_PRIVATE)
                    var editor = prefs.edit()
                    editor.clear().commit()

                    prefs = getSharedPreferences("slideduration", Context.MODE_PRIVATE)
                    editor = prefs.edit()
                    editor.putInt("slideDuration", slideDuration.toInt())
                    editor.commit()
                   // val intent = Intent(applicationContext,MyBackgroundService::class.java)
                    val intent = Intent(applicationContext,WallpaperForegroundService::class.java)
                    startService(intent)
                    //setWallpaperslide()
                }/*else{

                    // If no radio button checked in this radio group
                    Toast.makeText(applicationContext,
                            " nothing selected",
                        Toast.LENGTH_SHORT).show()
                }*/
            }
            if(iswallpaperSet())
            {
            moveTaskToBack(true)
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

            }
        }

    // Get the selected radio button text
    // using radio button on click listener
    fun radio_button_click(view: View)
    {
        // Get the clicked radio button instance
        val radio: RadioButton = findViewById(radio_group.checkedRadioButtonId)
        Toast.makeText(applicationContext,"On click : ${radio.text}",
            Toast.LENGTH_SHORT).show()
    }

    fun setWallpaperslide() {
        /* val intent = Intent(this,ImageSlider::class.java)
         startService(intent)*/

        val wallpaperManager = WallpaperManager.getInstance(this)
        try {
            wallpaperManager.clear()
        } catch (e: IOException) {
            e.printStackTrace()
        }

       // Handler().postDelayed({
            val intent = Intent(
                WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
            )
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, MyWallpaperService::class.java)
            )

           // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            overridePendingTransition(0,0)
            //ActivityCompat.finishAffinity(this)
            this.finish()
       // }, 4000)


    }

    private fun iswallpaperSet(): Boolean {
        try {
            val wpm = WallpaperManager.getInstance(this)
            val info = wpm.wallpaperInfo

            if (info != null && info.packageName == this.packageName) {
                Log.d("bitmappos inDuration", "We're already running")
                return true
            } else {
                Log.d("bitmappos inDuration", "We're not running")
                return false
            }
        }catch (e: Exception)
        {
            Log.e("bitmappos....", e.message, e)
        }
        return false
    }



}












