package com.np.wallpaperslider


import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context

import android.content.Intent
import android.content.pm.PackageManager

import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast

import androidx.core.app.ActivityCompat
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class PermissionCoordinator(
    private val activity: Activity, // Use Activity for Context and package name access
    registry: ActivityResultRegistry,
    lifecycleOwner: LifecycleOwner,
    // Callbacks for success/failure/return
    private val onNotificationPermissionResult: (Boolean) -> Unit,
    private val onReturnedFromNotificationSettings: () -> Unit,
    private val onReturnedFromBatterySettings: () -> Unit
) : DefaultLifecycleObserver {

    private val NOTIF_CHANNEL_ID = "WP"
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var notificationSettingsLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestIgnoreBatteryLauncher: ActivityResultLauncher<Intent>

    init {
        // Register the coordinator as a Lifecycle Observer to ensure launchers are properly managed
        lifecycleOwner.lifecycle.addObserver(this)
        registerLaunchers(registry, lifecycleOwner)
    }

    /**
     * Registers all ActivityResultLaunchers using the host's ActivityResultRegistry.
     * This is the core of making the logic reusable.
     */
    private fun registerLaunchers(registry: ActivityResultRegistry, lifecycleOwner: LifecycleOwner) {
        // 1. Notification Runtime Permission (Android 13+)
        requestNotificationPermissionLauncher = registry.register(
            "NOTIF_PERM_KEY",
            lifecycleOwner,
            ActivityResultContracts.RequestPermission()
        ) { granted: Boolean ->
            onNotificationPermissionResult(granted)
        }

        // 2. Notification Settings Launcher (Handles user manually enabling notifications)
        notificationSettingsLauncher = registry.register(
            "NOTIF_SETTINGS_KEY",
            lifecycleOwner,
            ActivityResultContracts.StartActivityForResult()
        ) {
            onReturnedFromNotificationSettings()
        }

        // 3. Ignore Battery Optimization Launcher
        requestIgnoreBatteryLauncher = registry.register(
            "BATTERY_OPT_KEY",
            lifecycleOwner,
            ActivityResultContracts.StartActivityForResult()
        ) {
            onReturnedFromBatterySettings()
        }
    }

    // ---------- Notification permission & channel helpers ----------

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun isNotificationChannelEnabled(): Boolean {
        val manager = activity.getSystemService(NotificationManager::class.java)
        val notificationsEnabled = manager.areNotificationsEnabled()
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.getNotificationChannel(NOTIF_CHANNEL_ID) else null
        return notificationsEnabled && (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Permission not required pre-13, treat as granted
            onNotificationPermissionResult(true)
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        }
        try {
            notificationSettingsLauncher.launch(intent)
        } catch (e: Exception) {
            AppLogger.e("Coordinator", "Failed to open notification settings: ${e.message}")
            // Fallback to app details
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            notificationSettingsLauncher.launch(fallback)
        }
    }

    // ---------- Battery optimization ----------

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return try {
            pm.isIgnoringBatteryOptimizations(activity.packageName)
        } catch (e: Exception) {
            AppLogger.e("Coordinator", "Error checking battery optimization: ${e.message}")
            false
        }
    }

    fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            requestIgnoreBatteryLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("Coordinator", "Battery optimization intent not found: ${e.message}")
            // Fallback: proceed with flow since we can't request it.
            onReturnedFromBatterySettings()
        }
    }
}