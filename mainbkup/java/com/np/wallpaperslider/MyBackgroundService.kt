package com.np.wallpaperslider

import android.app.Service
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.IOException

class MyBackgroundService : Service() {
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
       // onTaskRemoved(intent)
        if(!iswallpaperSet())
        {
            onTaskRemoved(intent)
        }
        if (Build.VERSION.SDK_INT > 16) {

            val wallpaperManager = WallpaperManager.getInstance(this)

            try {
                wallpaperManager.clear()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
               val wallpaperIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
               wallpaperIntent.putExtra(
                   WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                   ComponentName(this, MyWallpaperService::class.java)
               )
               wallpaperIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
               startActivity(wallpaperIntent)

               stopSelf() // Stop the service after launching wallpaper
               return START_NOT_STICKY

    }
    override fun onBind(intent: Intent): IBinder? {
       throw UnsupportedOperationException("Not yet implemented")
    }
    override fun onTaskRemoved(rootIntent: Intent) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
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

    private fun callwallpaperservice(packageContext: Context){

        if (Build.VERSION.SDK_INT > 16) {

            val wallpaperManager = WallpaperManager.getInstance(this)

            try {
                wallpaperManager.clear()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        val intent = Intent(
            WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
        )
        intent.putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(packageContext, MyWallpaperService::class.java)
        )

        startService(intent)

    }
}