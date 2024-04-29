package com.example.wallpaperapp

import android.os.Bundle
import android.preference.Preference
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceActivity
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wallpaperapp.R
import com.example.wallpaperapp.SettingsFragment

//class MyPreferencesActivity : PreferenceActivity() {

    /**
     * Checks that a preference is a valid numerical value
     */
   /* internal var numberCheckListener: Preference.OnPreferenceChangeListener =
        Preference.OnPreferenceChangeListener { preference, newValue ->
            // check that the string is an integer
            if (newValue != null && newValue.toString().length > 0
                && newValue.toString().matches("\\d*".toRegex())
            ) {
                return@OnPreferenceChangeListener true
            }
            // If now create a message to the user
            Toast.makeText(
                this@MyPreferencesActivity, resources.getString(R.string.lable_invalid_input),
                Toast.LENGTH_SHORT
            ).show()
            false
        }

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.prefs)
        //setContentView(R.xml.prefs)
        // add a validator to the "numberofCircles" preference so that it only
        // accepts numbers
        val circlePreference = preferenceScreen.findPreference(resources.getString(R.string.lable_number_of_circles))

        // add the validator
        circlePreference.onPreferenceChangeListener = numberCheckListener
        val resetbutton = preferenceManager.findPreference("resetButton")

        val button = preferenceManager.findPreference("exitlink")
        if (button != null) {
            button.onPreferenceClickListener = OnPreferenceClickListener {
                finish()
                true
            }
        }
    }

}*/

class MyPreferencesActivity  : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_settings)

            // below line is to change
            // the title of our action bar.
            supportActionBar?.setTitle("Settings")

            // below line is used to check if
            // frame layout is empty or not.
            // below line is used to check if
            // frame layout is empty or not.
            if (findViewById<View?>(R.id.idFrameLayout) != null) {
                if (savedInstanceState != null) {
                    return
                }
                // below line is to inflate our fragment.
                fragmentManager.beginTransaction().add(R.id.idFrameLayout, SettingsFragment()).commit()
            }
        }
    }