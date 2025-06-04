package com.np.wallpaperslider

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager

import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent

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
import android.view.ContextThemeWrapper
import android.view.View


import android.widget.ImageView

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ServiceCompat.stopForeground

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
   // var GALLERY_INTENT_CALLED = 1
   //var GALLERY_KITKAT_INTENT_CALLED = 2

    //lateinit var imagePath: String

    private val viewModel: MainViewModel by viewModels()
    private var imagesPathList = ArrayList<String>()
    var oldimagesPathList = ArrayList<String>()
    //lateinit var bgapp: ImageView
    //lateinit var anim : Animation
    lateinit var logoimg : ImageView
    //lateinit var lv1 : LinearLayout
    //lateinit var lv2 : LinearLayout
    //lateinit var lvmenus : LinearLayout
    //lateinit var bottomanim : Animation
    //lateinit var logoanim : Animation
    lateinit var cropscreenicon : MaterialButton
    lateinit var fitscreenicon : MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition{viewModel.isLoading.value}
        }
        super.onCreate(savedInstanceState)
       // imagesPathList = loadArray("imagesPathList", applicationContext)

        enableNotificationChannel()
        checkBatteryOptimization()
        setContentView(R.layout.activity_main)
        //bgapp = findViewById(R.id.bgapp)
        logoimg = findViewById(R.id.logoimg)
        logoimg.scaleX = 0f
        logoimg.scaleY = 0f
        logoimg.animate().scaleX(1f).scaleY(1f).setDuration(500).start()
       // lv1 = findViewById(R.id.lvsplash)
        //lv2 = findViewById(R.id.lvhome)
       // lvmenus = findViewById(R.id.lvmenus)
        cropscreenicon = findViewById(R.id.cropscreenicon)
        fitscreenicon = findViewById(R.id.fitscreenicon)

        /*
        anim = AnimationUtils.loadAnimation(this, R.anim.bganim)
        bottomanim = AnimationUtils.loadAnimation(this,R.anim.frombottom)
        logoanim = AnimationUtils.loadAnimation(this, R.anim.logoanim)
        */

       // lv1.animate().translationY(-1500f).alpha(0f).setDuration(800).setStartDelay(600)
       // bgapp.animate().translationY(-2000f).setDuration(800).setStartDelay(1000)

        //lv2.startAnimation(bottomanim)
       // lvmenus.startAnimation(bottomanim)
        /*if (Build.VERSION.SDK_INT > 16) {

            val wallpaperManager = WallpaperManager.getInstance(this)

            try {
                wallpaperManager.clear()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }*/
        fitscreenicon.setOnClickListener{
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                // if (hasPermissions())
                //  {
                chooseImages()
                /*  }
                  else
                  {
                      requestPermissions()
                  }*/
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

    @SuppressLint("SuspiciousIndentation")
    private fun enableNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel("WP") // Replace with actual channel ID

        // Check if notifications are enabled
        if (channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE) {
            Log.d("Notifications", "Notifications are already enabled.")
            return // Exit function if notifications are enabled
        }

        val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))

        with(builder)
        {
            setTitle("Enable Notifications")
            setMessage("This app requires you to enable notifications")
            // setPositiveButton("Yes", DialogInterface.OnClickListener(function = positiveButtonClick))
                .setPositiveButton("Go to Settings") { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                    openNotificationSettings()

                }
            setNegativeButton("Cancel") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()

            }

            show()
        }
    }

    private val notificationSettingsLauncher =
        this.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Return to the app after settings are closed
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, "WP")
        }
        if (intent.resolveActivity(packageManager) != null) {
            notificationSettingsLauncher.launch(intent)
        }
        else {
            Log.e("Notifications", "No activity found for notification settings")
            Toast.makeText(applicationContext, "Unable to open notification settings", Toast.LENGTH_SHORT).show()
        }
    }


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
        CoroutineScope(Dispatchers.IO).launch {
            val savedImages = imageUris.mapNotNull { uri ->
                saveImage(applicationContext, uri)
            }

            withContext(Dispatchers.Main) {
                imagesPathList.clear()
                imagesPathList.addAll(savedImages.map { it.toString() })
                confirmImages(window.decorView.rootView)
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
    if(imagesPathList.size>1){
        val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))

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
                dialog.dismiss()
                 for (i in 0 until oldimagesPathList.size) {
                     if(!imagesPathList.contains(oldimagesPathList.get(i)))
                        imagesPathList.add(oldimagesPathList.get(i))
                    // Toast.makeText(context,"output here in wallpaperslider.."+array[i],Toast.LENGTH_SHORT).show()
                }
                saveArray(imagesPathList, "imagesPathList", applicationContext)
                checkRGrid()
            }

            show()
        }
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
            finishAndRemoveTask()

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
        val builder = AlertDialog.Builder(this@MainActivity)

        // below line is the title for our alert dialog.

        // below line is the title for our alert dialog.
        builder.setTitle("Need Permissions")

        // below line is our message for our dialog

        // below line is our message for our dialog
        builder.setMessage("This app needs permission to use this feature. You can grant them in app settings.")
        builder.setPositiveButton("GOTO SETTINGS") { dialog: DialogInterface, which: Int ->
            // this method is called on click on positive button and on clicking shit button
            // we are redirecting our user from our app to the settings page of our app.
            dialog.cancel()
            // below is the intent from which we are redirecting our user.
            val intent: Intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.setData(uri)
            startActivityForResult(intent, 101)
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog: DialogInterface, which: Int ->
            // this method is called when user click on negative button.
            dialog.cancel()
        }
        // below line is used to display our dialog
        // below line is used to display our dialog
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

}


