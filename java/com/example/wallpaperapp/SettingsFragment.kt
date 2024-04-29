package com.example.wallpaperapp

import android.os.Bundle
import android.preference.PreferenceFragment

class SettingsFragment : PreferenceFragment() {
    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // below line is used to add preference
        // fragment from our xml folder.
        addPreferencesFromResource(R.xml.prefs)
    }

}