package com.example.wallpaperapp
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import java.io.IOException


class CropActivity: ComponentActivity(), View.OnClickListener {
    //@Bind(R.id.cv4)
    lateinit var origPic : CropImageView
    lateinit var cropPic : Button
    lateinit var imagePic : ImageView
    lateinit var savePic : Button
    lateinit var lvbeforecrop : LinearLayout
    lateinit var lvaftercrop : LinearLayout
    private var imageindex:Int? =0
   // var storagePermission : Array<String>?=null
    private var imagesArray = arrayOf<String>()
    private val handler = Handler()
    private val drawRunner = Runnable { drawImage() }
    private var useDiceOne = false
    var bmp: Bitmap? = null
    var alteredBitmap: Bitmap? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        imagesArray = loadArray("imagesPathList", applicationContext)
        origPic = findViewById<View>(R.id.cv4) as CropImageView
        cropPic = findViewById<View>(R.id.crop_image) as Button
        imagePic = findViewById<View>(R.id.set_image) as ImageView
        savePic = findViewById<View>(R.id.save_image) as Button
        cropPic!!.setOnClickListener(this)
        savePic!!.setOnClickListener(this)
        lvbeforecrop = findViewById(R.id.lv_beforecrop)
        lvaftercrop = findViewById(R.id.lv_aftercrop)
        var bundle :Bundle ?=intent.extras
        imageindex = bundle!!.getString("imageindex")?.toInt()
        imageindex = ((imageindex?.toInt() ?: 0) - 1)
        Log.i(
            "bitmappos",
            "imageindex $imageindex"
        )
        lvaftercrop.visibility=View.GONE
        origPic.setImageResource(android.R.color.transparent)
        handler.postDelayed(drawRunner, 3000)


        /*
        var image: Bitmap = MediaStore.Images.Media.getBitmap(
            applicationContext.contentResolver,
            Uri.parse(imagesArray[imageindex!!])
        )
        userPic!!.setImageBitmap(image)*/
        //storagePermission = arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)


    }
    fun loadArray(arrayName: String, context: Context): Array<String> {
        val prefs = context.getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        val size = prefs.getInt("${arrayName}_size", 0)

        val array = Array(size) { "" }
        for (i in 0 until size) {
            array[i] = prefs.getString("${arrayName}_$i", null) ?: ""
            //Toast.makeText(context,"output here.."+array[i],Toast.LENGTH_SHORT).show()
        }
        useDiceOne = true
        //Toast.makeText(context,"output here.."+array.size.toString()+"!!!",Toast.LENGTH_SHORT).show()
        return array
    }

    private fun drawImage() {
        try {

            var image: Bitmap = MediaStore.Images.Media.getBitmap(
                applicationContext.contentResolver,
                Uri.parse(imagesArray[imageindex!!])
            )
            val imageHeight = (Math.ceil(image.height.toDouble() / 100) * 100).toInt()
            val imageWidth = (Math.ceil(image.width.toDouble() / 100) * 100).toInt()
            Log.i(
                "scaleDownLargeImageWIthAspectRatio",
                "CropImage Dimensions(W:H): $imageWidth:$imageHeight"
            )
            origPic?.setImageBitmap(image)
            /*
            if (getIntent().getBooleanExtra("isCircular", false)) {
            cropImageView.setCropMode(CropImageView.CropMode.CIRCLE);
        } else
             */
            origPic?.setCropMode(CropImageView.CropMode.RATIO_FREE)
           //CV4.src = Bitmap.createScaledBitmap(image, imageWidth, imageHeight, true)
          // CV4.aspectRatio = CV4.src.width.toFloat() / CV4.src.height.toFloat()
        }catch (e:Exception)
        {Log.e("TAGGGGG22....", e.message, e)}
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(drawRunner)
    }

    override fun onClick(v: View) {
        if (v === cropPic) {
           /* bmp = MediaStore.Images.Media.getBitmap(
                applicationContext.contentResolver,
                Uri.parse(imagesArray[imageindex!!])
            )
            var imageFileUri = Uri.parse(imagesArray[imageindex!!])

            val x = origPic!!.test(image)
            userPic?.setImageBitmap(x)
            */

            try {
               /* val bmpFactoryOptions = BitmapFactory.Options()
                bmpFactoryOptions.inJustDecodeBounds = true
                bmp = BitmapFactory
                    .decodeStream(
                        contentResolver.openInputStream(
                            imageFileUri!!
                        ), null, bmpFactoryOptions
                    )
                bmpFactoryOptions.inJustDecodeBounds = false
                bmp = BitmapFactory
                    .decodeStream(
                        contentResolver.openInputStream(
                            imageFileUri
                        ), null, bmpFactoryOptions
                    )
                     alteredBitmap = Bitmap.createBitmap(
                    bmp!!.width,
                    bmp!!.height, bmp!!.config
                )*/


                var x = origPic!!.croppedBitmap
                imagePic?.setImageBitmap(x)
                lvaftercrop.visibility = View.VISIBLE
                lvbeforecrop.visibility = View.GONE

            } catch (e: Exception) {
                Log.v("alteredBitmap ERROR", e.toString())
            }


        }
        if (v === savePic) {
            if (origPic.saveBitmapInToFolder()) {
                Log.i(
                    "Cropping",
                    "imageindex: $imageindex::"
                )

                 imagesArray.set(imageindex!!, origPic.getImageUri().toString())
                saveArray(imagesArray, "imagesPathList", applicationContext)
                setWallpaperslide(v)
            }
        }
        }

    fun saveArray(array:Array<String>,arrayName: String, context: Context): Boolean {
        //clearing
        var prefs = context.getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        var editor = prefs.edit()
        editor.clear().commit()
        //reinserting
        prefs = context.getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        editor = prefs.edit()
        editor.putInt("${arrayName}_size", array.size)
        for (i in array.indices) {
            //  Toast.makeText(this,"data here.."+array[i],Toast.LENGTH_SHORT).show()
            editor.putString("${arrayName}_$i", array[i])
        }
        return editor.commit()
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
        val intent = Intent(
            WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
        )
        intent.putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, MyWallpaperService::class.java)
        )
        startActivity(intent)


    }

}