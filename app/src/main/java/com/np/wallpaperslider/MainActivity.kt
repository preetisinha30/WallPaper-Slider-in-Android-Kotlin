package com.np.wallpaperslider

import android.Manifest

import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ProgressDialog
import android.content.ActivityNotFoundException

import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment

import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log

import android.view.Gravity



import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper

import androidx.core.content.ContextCompat


import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    // UI
    private lateinit var logoimg: ImageView
    private lateinit var cropscreenicon: MaterialButton
    private lateinit var fitscreenicon: MaterialButton

    // Lists
    private var imagesPathList = ArrayList<String>()
    private var oldimagesPathList = ArrayList<String>()

    // Launchers (Only keep those NOT managed by the coordinator)
    private lateinit var pickImagesLauncher: ActivityResultLauncher<String>

    // Permission Coordinator (now owns all permission launchers)
    private lateinit var permissionCoordinator: PermissionCoordinator

    // Constants
    companion object {
        private const val PREFS_NAME = "wallpaperimages"
        private const val NOTIF_CHANNEL_ID = "WP"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI wiring
        logoimg = findViewById(R.id.logoimg)
        cropscreenicon = findViewById(R.id.cropscreenicon)
        fitscreenicon = findViewById(R.id.fitscreenicon)

        // Basic animation
        logoimg.scaleX = 0f
        logoimg.scaleY = 0f
        logoimg.animate().scaleX(1f).scaleY(1f).setDuration(500).start()

        // Create notification channel (idempotent)
        createNotificationChannel()

        // Register the one remaining launcher
        registerLaunchers()

        // Initialize the centralized PermissionCoordinator
        permissionCoordinator = PermissionCoordinator(
            activity = this,
            registry = activityResultRegistry, // Pass the Activity's registry
            lifecycleOwner = this,          // Pass the Activity as the Lifecycle Owner
            onNotificationPermissionResult = { granted ->
                if (granted) {
                    Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
                    startSetupFlow()
                } else {
                    // Denied runtime permission, proceed to ask user to open settings
                   // Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
                    //showEnableNotificationsDialog()
                }

            },
            onReturnedFromNotificationSettings = {
                // Re-check flow after returning from notification settings
                startSetupFlow()
            },
            onReturnedFromBatterySettings = {
                // Re-check flow after returning from battery settings
                startSetupFlow()
            }
        )

        // Setup click listeners
        fitscreenicon.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                chooseImages()
            }.start()
        }

        cropscreenicon.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                if (imagesPathList.size > 1 || loadArray("imagesPathList").size > 1) {
                    checkRGrid()
                } else {
                    Toast.makeText(this, "You haven't selected any images to create a slider", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }

        // Start the setup flow (permissions / settings)
        startSetupFlow()
    }


    private fun registerLaunchers() {
        // Use GetMultipleContents for picking multiple images easily
        pickImagesLauncher =
            registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
                if (!uris.isNullOrEmpty()) {
                    processImages(uris)
                }
            }
    }

    private fun startSetupFlow() {
        when {
            // Check 1: Runtime Permission (Android 13+)
            !permissionCoordinator.hasNotificationPermission() -> {
                requestNotificationPermission()
            }
            // Check 2: Notification Channel (Settings)
           /* !permissionCoordinator.isNotificationChannelEnabled() -> {
                showEnableNotificationsDialog()
            }*/
            // Check 3: Battery Optimization
            !permissionCoordinator.isIgnoringBatteryOptimizations() -> {
                requestIgnoreBatteryOptimizations()
            }
            else -> {
                // All good — proceed with normal app behavior
                AppLogger.d(TAG, "All permissions/settings satisfied")
            }
        }
    }

    // ---------- Notification permission & channel helpers ----------

    // Delegated to coordinator: private fun hasNotificationPermission(): Boolean { ... }

    private fun requestNotificationPermission() {
        // Delegate permission request to the coordinator
        permissionCoordinator.requestNotificationPermission()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val existingChannel = manager.getNotificationChannel(NOTIF_CHANNEL_ID)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Wallpaper Slider",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notification for Wallpaper Slider service"
                    setSound(null, null)
                    enableLights(false)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
                AppLogger.d(TAG, "Notification channel created")
            } else {
                AppLogger.d(TAG, "Notification channel exists")
            }
        }
    }

    // Delegated to coordinator: private fun isNotificationChannelEnabled(): Boolean { ... }

    private fun showEnableNotificationsDialog() {
        val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))
            .setTitle("Enable Notifications")
            .setMessage("This app requires notifications to run the wallpaper service. Please enable the 'Wallpaper Slider' channel in settings.")
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                openNotificationSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Notifications are required for the wallpaper service to work.", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
        try {
            builder.show()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to show notifications dialog: ${e.message}")
        }
    }

    private fun openNotificationSettings() {
        // Delegate opening settings to the coordinator
        permissionCoordinator.openNotificationSettings()
    }

    // ---------- Battery optimization ----------

    // Delegated to coordinator: private fun isIgnoringBatteryOptimizations(): Boolean { ... }

    private fun requestIgnoreBatteryOptimizations() {
        // Delegate battery optimization request to the coordinator
        permissionCoordinator.requestIgnoreBatteryOptimizations()
    }

    // ---------- Image picking & processing (rest of the code remains the same) ----------

    private fun chooseImages() {
        // GetMultipleContents returns a List<Uri> for the given mime type
        pickImagesLauncher.launch("image/*")
    }

    private fun processImages(imageUris: List<Uri>) {
        // Show progress dialog fragment
        val loadingDialog = ProgressDialogFragment.newInstance("Processing images...")
        loadingDialog.show(supportFragmentManager, "progress")

        // FIX: Request persistent read access for the URIs to prevent SecurityExceptions
        imageUris.forEach { uri ->
            try {
                // Request both read and write, though read is the crucial part for processing
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // This can happen if the content provider doesn't support persistable permissions
                AppLogger.w(TAG, "Provider does not support persistable URI permission for $uri: ${e.message}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to take persistable URI permission for $uri: ${e.message}")
            }
        }

        lifecycleScope.launch {
            val savedImages = withContext(Dispatchers.IO) {
                imageUris.mapNotNull { uri -> saveImage(applicationContext, uri) }
            }

            // Dismiss progress
            val existingDialog = supportFragmentManager.findFragmentByTag("progress") as? DialogFragment
            existingDialog?.dismiss()

            if (savedImages.isNotEmpty()) {
                imagesPathList.clear()
                imagesPathList.addAll(savedImages.map { it.toString() })

                // Merge with previously saved list if needed (preserving older ones)
                val old = loadArray("imagesPathList")
                for (p in old) if (!imagesPathList.contains(p)) imagesPathList.add(p)

                // Persist final list now (this is the deterministic copy point)
                saveArray(imagesPathList, "imagesPathList")

                // Open RGrid after saving
                checkRGrid()
            } else {
                Toast.makeText(this@MainActivity, "Failed to process selected images.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Save image into MediaStore (returns saved Uri)
    private fun saveImage(context: Context, uri: Uri): Uri? {
        val resolver = context.contentResolver
        val input = try {
            resolver.openInputStream(uri)
        } catch (e: Exception) {
            AppLogger.e(TAG, "openInputStream failed: ${e.message}")
            null
        } ?: return null

        val bitmap = BitmapFactory.decodeStream(input) ?: return null

        val displayName = uri.lastPathSegment ?: "wall_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WallPaperApp")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        }

        return try {
            val savedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            savedUri?.let { outUri ->
                resolver.openOutputStream(outUri)?.use { outStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outStream)
                }
            }
            savedUri
        } catch (e: Exception) {
            AppLogger.e(TAG, "saveImage failed: ${e.message}")
            null
        } finally {
            input.closeQuietly()
        }
    }

    // ---------- Persistence helpers ----------

    private fun saveArray(array: ArrayList<String>, arrayName: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("${arrayName}_size", array.size)
        array.forEachIndexed { idx, path ->
            editor.putString("${arrayName}_$idx", path)
        }
        editor.apply()
        AppLogger.d(TAG, "Saved array $arrayName size=${array.size}")
    }

    private fun loadArray(arrayName: String): ArrayList<String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val size = prefs.getInt("${arrayName}_size", 0)
        val list = arrayListOf<String>()
        for (i in 0 until size) {
            prefs.getString("${arrayName}_$i", null)?.let { list.add(it) }
        }
        return list
    }

    override fun onResume() {
        super.onResume()
        // reload persisted list if needed
        oldimagesPathList = loadArray("imagesPathList")
        if (intent.getBooleanExtra("fromSettings", false)) {
            startSetupFlow()
        }
    }

    // ---------- Navigation ----------

    fun checkRGrid() {
        val intent = Intent(applicationContext, RGrid::class.java).apply {
            putExtra("frompage", "main")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    // ---------- Utility ----------

    private fun closeQuietly(closeable: AutoCloseable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
        }
    }

    // extension for InputStream closure
    private fun java.io.InputStream?.closeQuietly() {
        try {
            this?.close()
        } catch (_: Exception) {
        }
    }

    // ---------- Progress dialog fragment ----------
    class ProgressDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(50, 50, 50, 50)
                gravity = Gravity.CENTER_VERTICAL
            }

            val progressBar = ProgressBar(requireContext()).apply {
                isIndeterminate = true
            }

            val messageView = TextView(requireContext()).apply {
                text = arguments?.getString(ARG_MESSAGE) ?: "Loading..."
                setPadding(40, 0, 0, 0)
                textSize = 16f
            }

            container.addView(progressBar)
            container.addView(messageView)

            return AlertDialog.Builder(requireContext())
                .setView(container)
                .setCancelable(false)
                .create()
        }

        companion object {
            private const val ARG_MESSAGE = "message"
            fun newInstance(message: String): ProgressDialogFragment {
                val f = ProgressDialogFragment()
                val args = Bundle()
                args.putString(ARG_MESSAGE, message)
                f.arguments = args
                return f
            }
        }
    }
}


