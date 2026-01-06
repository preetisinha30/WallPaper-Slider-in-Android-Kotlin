package com.np.wallpaperslider

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ProgressDialog

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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View


import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ServiceCompat.stopForeground
import androidx.core.content.ContextCompat

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.np.wallpaperapp.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.EasyPermissions.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL


class MainActivity : AppCompatActivity(), PermissionCallbacks {
    private var context: Context? = null
    private var PICK_IMAGE_MULTIPLE = 3




    private val viewModel: MainViewModel by viewModels()
    private var imagesPathList = ArrayList<String>()
    var oldimagesPathList = ArrayList<String>()

    lateinit var logoimg : ImageView

    lateinit var cropscreenicon : MaterialButton
    lateinit var fitscreenicon : MaterialButton
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
            //sendTestNotification()
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            showSettingsDialog()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition{viewModel.isLoading.value}
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()
        // Check notification permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else if (!areNotificationsEnabled()) {
            Toast.makeText(this, "Notifications are disabled. Please enable them in Settings.", Toast.LENGTH_LONG).show()
            openNotificationSettings()
        }
        //checkBatteryOptimization()

        logoimg = findViewById(R.id.logoimg)
        logoimg.scaleX = 0f
        logoimg.scaleY = 0f
        logoimg.animate().scaleX(1f).scaleY(1f).setDuration(500).start()

        cropscreenicon = findViewById(R.id.cropscreenicon)
        fitscreenicon = findViewById(R.id.fitscreenicon)


        fitscreenicon.setOnClickListener{
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                chooseImages()

            }.start()


        }
        cropscreenicon.setOnClickListener{
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()


            if(imagesPathList.size>1 || oldimagesPathList.size>1)
            {
              //  checkGrid(it)
               checkRGrid()
            }
            else
            {
                Toast.makeText(
                    this,
                    "You haven't selected any images to create a slider",
                    Toast.LENGTH_SHORT
                ).show()

            }
            }.start()

        }

   }

    private fun createNotificationChannel() {

            val manager = getSystemService(NotificationManager::class.java)
            val channelId = "WP"
            val existingChannel = manager.getNotificationChannel(channelId)

            if (existingChannel == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Wallpaper Slider",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notification for Wallpaper Slider service"
                    setSound(null, null) // Silent notification
                    enableLights(false)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
                Log.d("Notifications", "Notification channel created: $channelId")
            } else {
                Log.d("Notifications", "Notification channel already exists: $channelId")
            }

    }

    private fun areNotificationsEnabled(): Boolean {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.areNotificationsEnabled()
    }

    private fun openNotificationSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }
    /*fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, "WP")
            putExtra("fromSettings", true) // Flag to indicate returning from settings
        }
        if (intent.resolveActivity(packageManager) != null) {
            notificationSettingsLauncher.launch(intent)
        }
        else {
            Log.e("Notifications", "No activity found for notification settings")
            Toast.makeText(applicationContext, "Unable to open notification settings", Toast.LENGTH_SHORT).show()
        }
    }*/
    @SuppressLint("SuspiciousIndentation")
    private fun enableNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = manager.getNotificationChannel("WP")
            val notificationsEnabled = manager.areNotificationsEnabled() &&
                    (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)

            Log.d("Notifications", "enableNotificationChannel: App notifications enabled=${manager.areNotificationsEnabled()}, Channel=$channel, Importance=${channel?.importance}, NotificationsEnabled=$notificationsEnabled")
            if (notificationsEnabled) {
                Log.d("Notifications", "Notifications enabled for app and channel WP")
                return
            }

            Log.d("Notifications", "Creating notification dialog")
            val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom)) // Fallback to default context to rule out theme issues
            with(builder) {
                setTitle("Enable Notifications")
                setMessage("This app requires notifications to run the wallpaper service. Please enable the 'Wallpaper Slider' channel in settings and press the back button to return.")
                setPositiveButton("Go to Settings") { dialog: DialogInterface, _: Int ->
                    Log.d("Notifications", "User clicked Go to Settings")
                    dialog.dismiss()
                    openNotificationSettings()
                }
                setNegativeButton("Cancel") { dialog: DialogInterface, _: Int ->
                    Log.d("Notifications", "User clicked Cancel")
                    dialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "Notifications are required for the wallpaper service to work.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                setCancelable(false)
                try {
                    Log.d("Notifications", "Attempting to show notification dialog")
                    show()
                } catch (e: Exception) {
                    Log.e("Notifications", "Failed to show dialog: ${e.message}")
                }
            }
        } else {
            val manager = getSystemService(NotificationManager::class.java)
            val notificationsEnabled = manager.areNotificationsEnabled()
            Log.d("Notifications", "enableNotificationChannel (pre-Oreo): App notifications enabled=$notificationsEnabled")
            if (notificationsEnabled) {
                Log.d("Notifications", "Notifications enabled for app (pre-Oreo)")
                return
            }

            Log.d("Notifications", "Creating notification dialog (pre-Oreo)")
            val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))// Fallback to default context
            with(builder) {
                setTitle("Enable Notifications")
                setMessage("This app requires notifications to run the wallpaper service. Please enable notifications in settings and press the back button to return.")
                setPositiveButton("Go to Settings") { dialog: DialogInterface, _: Int ->
                    Log.d("Notifications", "User clicked Go to Settings (pre-Oreo)")
                    dialog.dismiss()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    notificationSettingsLauncher.launch(intent)
                }
                setNegativeButton("Cancel") { dialog: DialogInterface, _: Int ->
                    Log.d("Notifications", "User clicked Cancel (pre-Oreo)")
                    dialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "Notifications are required for the wallpaper service to work.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                setCancelable(false)
                try {
                    Log.d("Notifications", "Attempting to show notification dialog (pre-Oreo)")
                    show()
                } catch (e: Exception) {
                    Log.e("Notifications", "Failed to show dialog (pre-Oreo): ${e.message}")
                }
            }
        }
    }

    private fun checkNotificationStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = manager.getNotificationChannel("WP")
            val notificationsEnabled = manager.areNotificationsEnabled() &&
                    (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)

            Log.d("Notifications", "checkNotificationStatus: App notifications enabled=${manager.areNotificationsEnabled()}, Channel=$channel, Importance=${channel?.importance}, NotificationsEnabled=$notificationsEnabled")
            if (notificationsEnabled) {
                Log.d("Notifications", "Notifications are enabled for app and channel WP")
            } else {
                Log.w("Notifications", "Notifications disabled or channel blocked, prompting user")
                Toast.makeText(this, "Please enable notifications to continue.", Toast.LENGTH_SHORT).show()
                enableNotificationChannel()
            }
        } else {
            val manager = getSystemService(NotificationManager::class.java)
            val notificationsEnabled = manager.areNotificationsEnabled()
            Log.d("Notifications", "checkNotificationStatus (pre-Oreo): App notifications enabled=$notificationsEnabled")
            if (notificationsEnabled) {
                Log.d("Notifications", "Notifications enabled for app (pre-Oreo)")
            } else {
                Log.w("Notifications", "Notifications disabled for app (pre-Oreo), prompting user")
                enableNotificationChannel()
            }
        }
    }

    private val notificationSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Check notification status after returning from settings
            checkNotificationStatus()
        }
    /*private val notificationSettingsLauncher =
        this.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Return to the app after settings are closed
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }*/




    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)

        if (!isIgnoringOptimizations) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

    }

    override fun onResume() {
        super.onResume()
        oldimagesPathList = loadArray("imagesPathList", applicationContext)
        if (intent.getBooleanExtra("fromSettings", false)) {
            checkNotificationStatus()
        }
    }

    fun chooseImages() {
        val intent = if (Build.VERSION.SDK_INT < 19) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_MULTIPLE)
    }


    /* @RequiresApi(Build.VERSION_CODES.Q)
    @Deprecated("Deprecated in Java")
   override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)
        // When an Image is picked
        if (resultCode == Activity.RESULT_OK && null != data) {
            if(requestCode == PICK_IMAGE_MULTIPLE){
                //Log.e("++data","" + data.getClipData()?.getItemCount());// Get count of image here.


                if (data.clipData != null) {
                    //Log.d("++count", "" + data.getClipData()?.description);
                    var count = data.clipData!!.itemCount
                    // Toast.makeText(this, "....count.."+count.toString(), Toast.LENGTH_SHORT).show()
                    if (count != null && count > 0) for (i in 0..<count) {
                        var imageUri: Uri = (data.clipData?.getItemAt(i) ?: return).uri


                        var image: Bitmap = MediaStore.Images.Media.getBitmap(
                            applicationContext.contentResolver,
                            imageUri
                        )
                        var filename = imageUri.lastPathSegment.toString()
                       // saveBitmapInToFolder(image,filename)
                        //imagesPathList.add(imageUri.toString())
                        var newimageUri: Uri? = saveImage(applicationContext, image, filename)
                        imagesPathList.add(newimageUri.toString())
                        if(i==count-1) {
                            confirmImages(window.decorView.rootView)

                        }
                    }


                } else if (data.getData() != null) {

                    var imageUri = data.getData()

                    if(imageUri!=null) {
                        var image: Bitmap = MediaStore.Images.Media.getBitmap(
                            applicationContext.contentResolver,
                            imageUri
                        )
                        var filename = imageUri.lastPathSegment.toString()
                        var newimageUri: Uri? = saveImage(applicationContext, image, filename)
                        imagesPathList.add(newimageUri.toString())

                        if (imagesPathList.size == 1 && oldimagesPathList.size == 0) {
                            Toast.makeText(
                                this,
                                "Long press and select 2 or more images to create a slider",
                                Toast.LENGTH_SHORT
                            ).show()
                            imagesPathList.clear()
                        } else
                            confirmImages(window.decorView.rootView)
                    }

                }


            }

        }
    }*/
    @RequiresApi(Build.VERSION_CODES.Q)
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && requestCode == PICK_IMAGE_MULTIPLE && data != null) {
            val imageUris = mutableListOf<Uri>()

            // Gather all image URIs
            data.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    imageUris.add(clipData.getItemAt(i).uri)
                }
            } ?: data.data?.let { imageUris.add(it) }

            if (imageUris.isNotEmpty()) {
                processImages(imageUris)
            }
        }
    }

    private fun processImages(imageUris: List<Uri>) {
       /* val loadingDialog = ProgressDialog(this).apply {
            setMessage("Processing images...")
            setCancelable(false)
            show()
        }*/
        val loadingDialog = ProgressDialogFragment.newInstance("Processing images...")
        loadingDialog.show(supportFragmentManager, "progress")

        CoroutineScope(Dispatchers.IO).launch {
            val savedImages = imageUris.mapNotNull { uri ->
                saveImage(applicationContext, uri)
            }

            withContext(Dispatchers.Main) {
                imagesPathList.clear()
                imagesPathList.addAll(savedImages.map { it.toString() })
                confirmImages(window.decorView.rootView)
               // loadingDialog.dismiss()
                val existingDialog = supportFragmentManager.findFragmentByTag("progress") as? DialogFragment
                existingDialog?.dismiss()

            }
        }
    }

    /*fun getPathFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor = getContentResolver().query(uri, projection, null, null, null) ?: return null
        val column_index: Int = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()
        val s: String = cursor.getString(column_index)
        cursor.close()
        return s
    }*/



    private fun confirmImages(view: View) {
    if(imagesPathList.size>1 || oldimagesPathList.size>1){
      /*  val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))

        with(builder)
        {
            setTitle("Confirm")
            setMessage("Would you like to add more images?")
            // setPositiveButton("Yes", DialogInterface.OnClickListener(function = positiveButtonClick))
            setPositiveButton("Yes") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                chooseImages()
            }
            setNegativeButton("No") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()*/
                 for (i in 0 until oldimagesPathList.size) {
                     if(!imagesPathList.contains(oldimagesPathList.get(i)))
                        imagesPathList.add(oldimagesPathList.get(i))
                    // Toast.makeText(context,"output here in wallpaperslider.."+array[i],Toast.LENGTH_SHORT).show()
                }
                saveArray(imagesPathList, "imagesPathList", applicationContext)
                checkRGrid()
           /* }

            show()
        }*/
    }
        else{
            Toast.makeText(this,"Please select 2 or more images to create a slider",Toast.LENGTH_SHORT).show()
    }
    }
    fun saveArray(array:ArrayList<String>,arrayName: String, context: Context): Boolean {
        var prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        var editor = prefs.edit()
        editor.clear().commit()

        prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        editor = prefs.edit()
        editor.putInt("${arrayName}_size", array.size)
        for (i in array.indices) {
            //  Toast.makeText(this,"data here.."+array[i],Toast.LENGTH_SHORT).show()
            editor.putString("${arrayName}_$i", array[i])
        }

        return editor.commit()
    }

    fun loadArray(arrayName: String, context: Context): ArrayList<String> {
        val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        val size = prefs.getInt("${arrayName}_size", 0)
       // Toast.makeText(context,"loadarraylist.."+size.toString()+"!!!",Toast.LENGTH_SHORT).show()
        val olimagesPathList = arrayListOf<String>()
        for (i in 0 until size) {
            olimagesPathList.add(prefs.getString("${arrayName}_$i", null) ?: "")
            // Toast.makeText(context,"output here in wallpaperslider.."+array[i],Toast.LENGTH_SHORT).show()
        }
        //Toast.makeText(context,"output here.."+array.size.toString()+"!!!",Toast.LENGTH_SHORT).show()
        return olimagesPathList


    }

    fun checkRGrid(){

            val intent = Intent(applicationContext,RGrid::class.java)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra("frompage","main")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
           // finishAndRemoveTask()

    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {

    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>)
    {
        if(EasyPermissions.somePermissionPermanentlyDenied(this@MainActivity,perms))
        {
            showSettingsDialog()
        }
        else
        {
            requestPermissions()
        }
    }
    private fun showSettingsDialog()
    {
        val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))
        builder.setTitle("Need Permissions")
        builder.setMessage("This app needs permission to use this feature. You can grant them in app settings.")
        builder.setPositiveButton("GOTO SETTINGS") { dialog: DialogInterface, which: Int ->
            // this method is called on click on positive button and on clicking shit button
            dialog.dismiss()
            // below is the intent from which we are redirecting our user.
           /* val intent: Intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.setData(uri)
            startActivityForResult(intent, 101)*/
            Handler(Looper.getMainLooper()).postDelayed({
                openNotificationSettings()
            }, 200) // slight delay gives time for dismissal animation

        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog: DialogInterface, which: Int ->
           dialog.cancel()
        }

        builder.show()

    }

    private fun hasPermissions()=
        EasyPermissions.hasPermissions(this,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE)


    private fun requestPermissions(){
        EasyPermissions.requestPermissions(this,"These permissions are required for this application to work",
            PERMISSION_REQUEST_CODE,android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,this)
    }

    companion object{
        const val PERMISSION_REQUEST_CODE = 1
    }

    fun checkImage(fileName: String): Boolean {
        var folderPath = Environment.DIRECTORY_PICTURES+ "/" + "WallPaperApp"
        val file = File(folderPath, fileName)

        // Check if file already exists
        if (file.exists()) {
            Log.d("SaveImage", "File already exists: ${file.absolutePath}")
            return true // Prevent duplicate save
        }
        return false

    }

    /*fun saveImage(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES+ "/" + "WallPaperApp")
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        imageUri?.let { uri ->
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            } ?: Log.e("SaveImage", "Failed to open output stream")

        }

        return imageUri // Return URI instead of file path
    }*/
    /*fun saveImage(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val resolver = context.contentResolver

        // Check if the image already exists
        val existingUri = resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
            null
        }

        if (existingUri != null) {
            Log.d("SaveImage", "Skipping save, image already exists: $existingUri")
            return existingUri // Return existing URI instead of saving again
        }

        // Proceed with saving if no existing image found
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
           // put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WallPaperApp")
        }

        val mimeType = when {
            fileName.endsWith(".png") -> "image/png"
            fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> "image/jpeg"
            fileName.endsWith(".webp") -> "image/webp"
            else -> "image/png" // Default fallback
        }
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, mimeType)

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        imageUri?.let { uri ->
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                        Log.e("SaveImage", "Bitmap compression failed")
                    }
                }
            } catch (e: Exception) {
                Log.e("SaveImage", "Error saving image: ${e.message}")
            }
        } ?: Log.e("SaveImage", "Failed to create image URI")

        return imageUri
    }*/
    private fun saveImage(context: Context, uri: Uri): Uri? {
        val resolver = context.contentResolver
        val bitmap = BitmapFactory.decodeStream(resolver.openInputStream(uri)) ?: return null

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, uri.lastPathSegment)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WallPaperApp")
        }

        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.also { savedUri ->
            resolver.openOutputStream(savedUri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream) // Lower quality slightly for optimization
            }
        }
    }



    fun saveBitmapInToFolder(imageBitmap:Bitmap,imgName:String): Boolean {
        if (imageBitmap == null) return false
        var fileCreated = false
        var bitmapCompressed = false
        var streamClosed = false
        var imageName =""
        //val filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/" + "WallPaperApp"
        /*Log.i(
            "Cropping",
            "in saveBitmapInToFolder"
        )*/
        try {
            var filePath = context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/" + "WallPaperApp"
           // val filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/" + "WallPaperApp"
            val fileMake = File(filePath)
            if (!fileMake.exists()) {
                fileMake.mkdir()
            }
            imageName = filePath + "/" + imgName + ".png"
            /*Log.i(
                "Cropping",
                "imageName: $imageName"
            )*/
        }catch (e: Exception) {
            /*Log.i(
                "Cropping",
                "directory created: ${e.message.toString()}"
            )*/
        }

        val imageFile = File(imageName)
        if (imageFile.exists()) if (!imageFile.delete()) return false
        try {
            fileCreated = imageFile.createNewFile()

        } catch (e: Exception) {
            Log.i(
                "Cropping",
                "file not created: ${e.message.toString()}"
            )
        }
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(imageFile)
            bitmapCompressed = imageBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out)
        } catch (e: Exception) {
            e.printStackTrace()
            bitmapCompressed = false
        } finally {
            if (out != null) {
                try {
                    out.flush()
                    out.close()
                    streamClosed = true
                } catch (e: IOException) {
                    e.printStackTrace()
                    streamClosed = false
                }
            }
        }
        Log.i(
            "bitmapppos",
            "file created: $fileCreated:$bitmapCompressed::$streamClosed"
        )
        if(fileCreated && bitmapCompressed && streamClosed)
            imagesPathList.add(imageName)

        return fileCreated && bitmapCompressed && streamClosed

    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveFileUsingMediaStore(context: Context, url: String, fileName: String) {
        var fileCreated = false
        var bitmapCompressed = false
        var streamClosed = false
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + "WallPaperApp"
            )
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {

            URL(url).openStream().use { input ->
                resolver.openOutputStream(uri).use { output ->
                    input.copyTo(output!!, DEFAULT_BUFFER_SIZE)
                }
            }
        }

    }
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
                val fragment = ProgressDialogFragment()
                val args = Bundle()
                args.putString(ARG_MESSAGE, message)
                fragment.arguments = args
                return fragment
            }
        }
    }

}


