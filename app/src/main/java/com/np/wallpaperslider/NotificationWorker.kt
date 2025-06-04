package com.np.wallpaperslider

import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class NotificationWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        if(isWallpaperSet(applicationContext))
            showNotification(applicationContext)
        return Result.success()
    }

    private fun showNotification(context: Context) {
        val notificationManager = ContextCompat.getSystemService(
            context, NotificationManager::class.java
        ) as NotificationManager

        val builder = NotificationCompat.Builder(context, "WP")
            .setContentTitle("Wallpaper Slider Active")
            .setContentText("Your live wallpaper is running.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(101, builder.build())
    }

    private fun isWallpaperSet(context: Context): Boolean {
        return try {
            val wpm = WallpaperManager.getInstance(context)
            val info = wpm.wallpaperInfo
            if (info != null && info.packageName == context.packageName) {
                Log.d("BootReceiver", "Wallpaper is already running.")
                true
            } else {
                Log.d("BootReceiver", "Wallpaper is NOT running.")
                false
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "Error checking wallpaper state: ${e.message}", e)
            false
        }

    }
}