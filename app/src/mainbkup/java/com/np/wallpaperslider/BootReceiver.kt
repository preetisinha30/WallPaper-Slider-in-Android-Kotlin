package com.np.wallpaperslider

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            // Call a function to re-trigger the notification
            /*if(isWallpaperSet(context))
                showNotification(context)*/
            try {
                if (isWallpaperSet(context)) {
                    Log.d("BootReceiver", "Wallpaper is set, starting WallpaperForegroundService")
                    val serviceIntent = Intent(context, WallpaperForegroundService::class.java)
                    context.startService(serviceIntent)
                } else {
                    Log.d("BootReceiver", "Wallpaper not set, skipping service start")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error checking wallpaper status: ${e.message}", e)
            }

        }
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

        notificationManager.notify(1001, builder.build())
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