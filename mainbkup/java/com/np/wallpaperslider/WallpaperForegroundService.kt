package com.np.wallpaperslider

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.IOException

class WallpaperForegroundService : Service() {
        val notificationChannel = "WP"
        val serviceId=101
        lateinit var notification: Notification
        lateinit var notificationManager: NotificationManager
        //val handler: Handler = Handler(Looper.getMainLooper())
        private var isInPreview = false
        private var messenger: Messenger? = null
        // Message types
        companion object {
            const val MSG_SHOW_NOTIFICATION = 1
            const val MSG_REMOVE_NOTIFICATION = 2
        }

    // Handler for incoming messages
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_SHOW_NOTIFICATION -> {
                    if (iswallpaperSet() && !isInPreview) {
                        Log.d("WallpaperForegroundService", "Showing notification")
                        startForeground(serviceId, createNotification())
                    }
                }
                MSG_REMOVE_NOTIFICATION -> {
                    isInPreview = true
                    Log.d("WallpaperForegroundService", "Removing notification")
                    stopForeground(true)
                    notificationManager.cancel(serviceId)
                    if (!iswallpaperSet()) {
                        stopSelf()
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreate() {
            super.onCreate()
            notificationManager = getSystemService(NotificationManager::class.java)
            Log.d("WallpaperService", "Service onCreate")
            createChannel()
            messenger = Messenger(handler)


        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            // Check and handle battery optimization
           /* if (!isBatteryOptimizationDisabled()) {
                promptDisableBatteryOptimization()
            }*/


            Log.d("WallpaperService", "Foreground service started")

            startForeground(serviceId, createTemporaryNotification())

            isInPreview = intent?.getBooleanExtra("isPreview", isInPreview) ?: isInPreview
            if (!iswallpaperSet() || isInPreview) {
                Log.d("WallpaperForegroundService", "Wallpaper not set or in preview, stopping foreground")
                stopForeground(true)
                notificationManager.cancel(serviceId)
                callWallpaperService(applicationContext)
                if (!iswallpaperSet() && !isInPreview) {
                    Log.d("WallpaperForegroundService", "Wallpaper not set and not in preview, stopping service")
                    stopSelf()
                }
            } else {
                startForeground(serviceId, createNotification())
                callWallpaperService(applicationContext)
            }
            return START_NOT_STICKY
        }


    override fun onBind(intent: Intent?): IBinder? {
        return messenger?.binder
    }

        private fun isBatteryOptimizationDisabled(): Boolean {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }

        private fun promptDisableBatteryOptimization() {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Ensure the intent opens a new task
            startActivity(intent)
        }

        override fun onTaskRemoved(rootIntent: Intent?) {


            // Restart the service
             if (iswallpaperSet() && !isInPreview) {
                Log.d("WallpaperService", "Wallpaper active, restarting service")
                 handler.postDelayed({
                     val restartServiceIntent = Intent(applicationContext, WallpaperForegroundService::class.java)
                     restartServiceIntent.setPackage(packageName)
                     startService(restartServiceIntent)
                 }, 1000) // Delay to allow task cleanup
            } else {
                Log.d("WallpaperService", "Wallpaper not active, not restarting")
                 stopForeground(true)
                 notificationManager.cancel(serviceId)
                 stopSelf()
            }
            super.onTaskRemoved(rootIntent)
        }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext!!.getSystemService(NotificationManager::class.java)
            val existingChannel = manager.getNotificationChannel("WP")

            if (existingChannel == null) {
                val channel = NotificationChannel(
                    notificationChannel,
                    "Wallpaper Slider",
                    NotificationManager.IMPORTANCE_HIGH // Ensures visibility
                ).apply {
                    description = "Notification for Wallpaper Slider service"
                    setSound(null, null) // Silent notification
                    enableLights(false)
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(channel)
                Log.d("WallpaperService", "Notification channel created: $notificationChannel")
            }
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
         flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
         }

        val pendingIntent = PendingIntent.getActivity(this, 0, intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, notificationChannel)
            .setContentTitle("Wallpaper Slider Active")
            .setContentText("Your live wallpaper is running.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true) // No sound/vibration

        notification = builder.build()
        Log.d("WallpaperService", "Notification created")
        return notification
    }

    private fun createTemporaryNotification(): Notification {
        val builder = NotificationCompat.Builder(this, notificationChannel)
            .setContentTitle("Wallpaper Slider")
            .setContentText("Initializing service...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        val notification = builder.build()
        Log.d("WallpaperForegroundService", "Temporary notification created")
        return notification
    }

    private fun iswallpaperSet(): Boolean {
        try {
            val wpm = WallpaperManager.getInstance(this)
            val info = wpm.wallpaperInfo
            return info != null && info.packageName == packageName
        } catch (e: Exception) {
            Log.e("WallpaperService", "Error checking wallpaper: ${e.message}", e)
            return false
        }
    }

    private fun callWallpaperService(packageContext: Context){
        if (iswallpaperSet()) {
            Log.d("WallpaperForegroundService", "Wallpaper already set, starting MyWallpaperService")
            val wallpaperIntent = Intent(applicationContext, MyWallpaperService::class.java)
            startService(wallpaperIntent)
            return
        }
        if (Build.VERSION.SDK_INT > 16) {

            val wallpaperManager = WallpaperManager.getInstance(this)

            try {
                wallpaperManager.clear()
                Log.d("WallpaperForegroundService", "Cleared existing wallpaper")
            } catch (e: IOException) {
                Log.e("WallpaperForegroundService", "Error clearing wallpaper: ${e.message}", e)
                e.printStackTrace()

            }
        }

        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(packageContext, MyWallpaperService::class.java))
        }
        /*try {
            packageContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("WallpaperService", "Failed to launch wallpaper activity", e)
        }*/
        try {
            packageContext.startActivity(intent)
            Log.d("WallpaperForegroundService", "Launched live wallpaper picker")
            // Stop the foreground service if in preview or wallpaper not set to avoid lingering
            if (!iswallpaperSet() || isInPreview) {
                stopForeground(true)
                notificationManager.cancel(serviceId)
                stopSelf()
            }

        } catch (e: ActivityNotFoundException) {
            Log.e("WallpaperForegroundService", "Failed to launch wallpaper activity", e)
            stopForeground(true)
            notificationManager.cancel(serviceId)
            stopSelf()
        }


    }

    override fun onDestroy() {
        stopForeground(true)
        notificationManager.cancel(serviceId)
        Log.d("WallpaperForegroundService", "Service destroyed")
        super.onDestroy()
    }

    private fun navigateToMain() {

        val intent = Intent(this, MainActivity::class.java).apply {

            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        }

        val pendingIntent = PendingIntent.getActivity(

            this, 0, intent,

            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        )

        try {

            pendingIntent.send()

            Log.d("WallpaperForegroundService", "Navigated to MainActivity")

        } catch (e: Exception) {

            Log.e("WallpaperForegroundService", "Failed to navigate to MainActivity: ${e.message}")

        }
    }

    fun launchSettingsActivity(context: Context) {
        val intent = Intent(context, DurationACtivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            pendingIntent.send() // Launch the activity safely
        } catch (e: PendingIntent.CanceledException) {
            Log.e("WallpaperForegroundService", "PendingIntent failed", e)
        }
    }


    fun getNotifications()
    {val notifications = notificationManager.activeNotifications
        for (notif in notifications) {
            Log.d("WallpaperService", "Active Notification ID: ${notif.id}")
        }
    }






}