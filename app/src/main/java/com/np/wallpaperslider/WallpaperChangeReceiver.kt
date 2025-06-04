package com.np.wallpaperslider

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class WallpaperChangeReceiver : BroadcastReceiver() {

     override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER) {
            val serviceIntent = Intent(context, WallpaperForegroundService::class.java)

            context?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.startForegroundService(serviceIntent) // Correct method for foreground service
                } else {
                    it.startService(serviceIntent)
                }
            }
        }
    }

}