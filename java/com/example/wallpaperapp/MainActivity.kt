package com.example.wallpaperapp

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
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
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel


class MainActivity : ComponentActivity() {
    private var context: Context? = null
    var PICK_IMAGE_MULTIPLE = 3
    lateinit var imagePath: String

    private val viewModel:MainViewModel by viewModels()

    public var imagesPathList = ArrayList<String>()
    lateinit var bgapp: ImageView
    lateinit var anim : Animation
    lateinit var logoimg : ImageView
    lateinit var lv1 : LinearLayout
    lateinit var lv2 : LinearLayout
    lateinit var lvmenus : LinearLayout
    lateinit var bottomanim : Animation
    lateinit var logoanim : Animation
    lateinit var lockscreenicon : ImageView
    lateinit var homescreenicon : ImageView
    //lateinit var cropscreenicon : ImageView
    lateinit var fitscreenicon : ImageView
    /*private val wimageViewModel: WimageViewModel by viewModels {
        WimageViewModelFactory((application as WimageApplication).repository)
    }*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            setKeepOnScreenCondition{viewModel.isLoading.value}
        }
        setContentView(R.layout.activity_main)
        bgapp = findViewById(R.id.bgapp)
        logoimg = findViewById(R.id.logoimg)
        lv1 = findViewById(R.id.lvsplash)
        lv2 = findViewById(R.id.lvhome)
        lvmenus = findViewById(R.id.lvmenus)

        //lockscreenicon = findViewById(R.id.lockscreenicon)
        //homescreenicon = findViewById(R.id.homescreenicon)
        //cropscreenicon = findViewById(R.id.cropscreenicon)
        fitscreenicon = findViewById(R.id.fitscreenicon)

        anim = AnimationUtils.loadAnimation(this, R.anim.bganim)
        bottomanim = AnimationUtils.loadAnimation(this,R.anim.frombottom)
        logoanim = AnimationUtils.loadAnimation(this, R.anim.logoanim)
        lv1.animate().translationY(-1500f).alpha(0f).setDuration(800).setStartDelay(600)
        bgapp.animate().translationY(-2000f).setDuration(800).setStartDelay(1000)
        lv2.startAnimation(bottomanim)
        lvmenus.startAnimation(bottomanim)
        /*lockscreenicon.setOnClickListener{
          //  val intent = Intent(this, SingleImageActivity::class.java)
            val intent = Intent(this, SingleImageCanvasActivity::class.java)
            startActivity(intent)


        }*/
       /* homescreenicon.setOnClickListener {
            //  val intent = Intent(this, SingleImageActivity::class.java)
            val intent = Intent(this, GenerateImageActivity::class.java)
            startActivity(intent)
        }*/
        fitscreenicon.setOnClickListener{
            chooseImages(it)
        }
        //logoimg.startAnimation(logoanim)
       // logoimg.animate().translationY(-1500f).setDuration(800).setStartDelay(600)

        //findViewById<TextView>(R.id.tv1).animate().translationY(-1500f).setDuration(800).setStartDelay(600)
        //findViewById<TextView>(R.id.tv2).animate().translationY(-1500f).setDuration(800).setStartDelay(600)
       // MainActivity.appContext = this.applicationContext
      // val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            // Do something with the image URI, such as displaying it in an ImageView
            //imageView.setImageURI(uri)
      //      val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
      //      val wallpaperManager = WallpaperManager.getInstance(baseContext)
      //      wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
       //     Toast.makeText(this, "Wallpaper set!", Toast.LENGTH_SHORT).show()
      //  }

        // Launch the contract with the desired type
      //  pickImage.launch("image/*")
    }

    fun setWallpaper(view: View) {
        val bitmap: Bitmap =
            BitmapFactory.decodeResource(resources, R.drawable.dancing)
        val wallpaperManager = WallpaperManager.getInstance(baseContext)
        wallpaperManager.setBitmap(bitmap)
        Toast.makeText(this, "Wallpaper set!", Toast.LENGTH_SHORT).show()
    }

    fun set_Wallpaper(view: View,uri: Uri) {
       // val bitmap: Bitmap =
        //    BitmapFactory.decodeResource(resources, R.drawable.wallpaper)
        val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
        val wallpaperManager = WallpaperManager.getInstance(baseContext)
        wallpaperManager.setBitmap(bitmap)
        Toast.makeText(this, "Wallpaper set!", Toast.LENGTH_SHORT).show()
    }

    fun chooseImage(view: View){
        Toast.makeText(this, "Do Nothing!", Toast.LENGTH_SHORT).show()
    }

    fun setWallpaperslide(view: View) {
       /* val intent = Intent(this,ImageSlider::class.java)
        startService(intent)*/
        val wallpaperManager = WallpaperManager.getInstance(this)
        try {
            wallpaperManager.clear()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (Build.VERSION.SDK_INT >= 16) {


            val intent = Intent(
                WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
            )
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, MyWallpaperService::class.java)
            )
            startActivity(intent)
        }
        else
        {
            val intent = Intent(
                WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER
            )
            startActivity(intent)
        }


    }


    fun chooseImages(view: View)
    {


        if (Build.VERSION.SDK_INT < 19) {
            var intent = Intent()
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(
                Intent.createChooser(intent, "Select Picture")
                , PICK_IMAGE_MULTIPLE
            )
        } else {
            var intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE_MULTIPLE);
           // startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_MULTIPLE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)
        // When an Image is picked
        if (resultCode == Activity.RESULT_OK && null != data) {
            if(requestCode == PICK_IMAGE_MULTIPLE){
                //Log.e("++data","" + data.getClipData()?.getItemCount());// Get count of image here.

           // Log.e("++count", "" + data.getClipData()?.getItemCount());
           //     Toast.makeText(this, "....mmmm.."+ data.getClipData()?.getItemCount()+"........ct.."+ data.getClipData()?.getItemCount(), Toast.LENGTH_SHORT).show()
            if (data.clipData != null) {
                //Log.d("++count", "" + data.getClipData()?.description);
                var count = data.clipData!!.itemCount
               // Toast.makeText(this, "....count.."+count.toString(), Toast.LENGTH_SHORT).show()
                if (count != null && count > 0) for (i in 0..<count) {
                    var imageUri: Uri = (data.clipData?.getItemAt(i) ?: return).uri
                   // Toast.makeText(this, "....imageUri.."+imageUri.toString(), Toast.LENGTH_SHORT).show()
                   // getPathFromURI(imageUri)
                    imagesPathList.add(imageUri.toString())
                    if(i==count-1) {
                        confirmImages(window.decorView.rootView)

                    }
                }
               /* if(imagesPathList.size==count && count!=0)
                {
                    confirmImages(window.decorView.rootView)
                }*/

            } else if (data.getData() != null) {
                var imagePath: String? = data?.data?.path
                //var imageUri = Uri.parse(imagePath)
                var imageUri = data.getData()
                Log.e("imagePath", imagePath.toString());
               // getPathFromURI(imageUri)
                imagesPathList.add(imageUri.toString())
                //saveArray(imagesPathList, "imagesPathList", applicationContext)
                if(imagesPathList.size==1)
                    Toast.makeText(this, "Select 2 or more images to create a slider", Toast.LENGTH_SHORT).show()
                    //callwallpaperservice()
            }

             // displayImageData()

        }
        }
    }

    private fun callwallpaperservice(){

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
            ComponentName(this, MyWallpaperService::class.java)
        )


        startActivity(intent)

    }

    private fun confirmImages(view: View) {

        val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))

        with(builder)
        {
            setTitle("Confirm")
            setMessage("Would you like to add more images?")
           // setPositiveButton("Yes", DialogInterface.OnClickListener(function = positiveButtonClick))
            setPositiveButton("Yes") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                chooseImages(view)
            }
            setNegativeButton("No"){ dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                saveArray(imagesPathList, "imagesPathList", applicationContext)
                callwallpaperservice()
            }

            show()
        }
    }


    private fun getPathFromURI(uri: Uri) {
        var path: String = uri.path.toString()// uri = any content Uri

        val databaseUri: Uri
        val selection: String?
        val selectionArgs: Array<String>?
        if (path.contains("/document/image:")) { // files selected from "Documents"
            databaseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            selection = "_id=?"
            selectionArgs = arrayOf(DocumentsContract.getDocumentId(uri).split(":")[1])
            //Toast.makeText(this, "....loop1 databaseUri.."+databaseUri.path.toString(), Toast.LENGTH_SHORT).show()
        } else { // files selected from all other sources, especially on Samsung devices
            databaseUri = uri
            selection = null
            selectionArgs = null
           // Toast.makeText(this, "....loop2 uri.."+databaseUri.path.toString(), Toast.LENGTH_SHORT).show()
        }
        imagesPathList.add(path)

        /*val wimages = Wimages(databaseUri.path.toString())
        wimageViewModel.insert(wimages)*/
/*
        Toast.makeText(this, "....imagesPathListsz.."+ imagesPathList.size.toString(), Toast.LENGTH_SHORT).show()
       // Toast.makeText(this, "....uri later.."+databaseUri.toString(), Toast.LENGTH_SHORT).show()
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.DATE_TAKEN
        ) // some example data you can query
        try {

            val cursor = contentResolver.query(
                databaseUri,
                projection, selection, selectionArgs, null
            )

            if(cursor!=null) {
                Log.v("Cursor Object", DatabaseUtils.dumpCursorToString(cursor))
                if (cursor.moveToFirst()) {

                    val columnIndex = cursor.getColumnIndex(projection[0])
                    val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    Log.v("column_index", cursor.getString(column_index))
                    var imagePath = cursor.getString(columnIndex)
                    // Log.e("path", imagePath);
                    Toast.makeText(this, "....imagesPathList.."+ imagePath, Toast.LENGTH_SHORT).show()
                    imagesPathList.add(imagePath)

                }
                cursor.close()
              //  Log.e("imagesPathListsz.....", imagesPathList.size.toString());
                Toast.makeText(this, "....imagesPathListsz.."+ imagesPathList.size.toString(), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "....error here.."+ e.message.toString(), Toast.LENGTH_SHORT).show()
            //Log.e(TAG, e.message, e)
        }*/
    }

    fun saveArray(array:ArrayList<String>,arrayName: String, context: Context): Boolean {
        val prefs = context.getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("${arrayName}_size", array.size)
        for (i in array.indices) {
          //  Toast.makeText(this,"data here.."+array[i],Toast.LENGTH_SHORT).show()
            editor.putString("${arrayName}_$i", array[i])
        }
        return editor.commit()
    }

    @Throws(IOException::class)
    private fun copyFile(sourceFile: File, destFile: File) {
        if (!sourceFile.exists()) {
            return
        }
        var source: FileChannel? = null
        var destination: FileChannel? = null
        source = FileInputStream(sourceFile).channel
        destination = FileOutputStream(destFile).channel
        if (destination != null && source != null) {
            destination.transferFrom(source, 0, source.size())
        }
        source?.close()
        destination?.close()
    }


}


