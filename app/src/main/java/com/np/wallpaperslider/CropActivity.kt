package com.np.wallpaperslider
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.IOException


class CropActivity: ComponentActivity(), View.OnClickListener {
    //@Bind(R.id.cv4)
    lateinit var origPic : CropImageView
    lateinit var cropPic : MaterialButton
    lateinit var imagePic : ImageView
    lateinit var savePic : MaterialButton
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
    private lateinit var loading:LoadingDialog
    lateinit var frompage : String
    lateinit var filename : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        val toolbar = findViewById<MaterialToolbar>(R.id.xml_toolbar)
        toolbar.title = "Crop Image" // Optional: Set a title
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow) // Optional: Add a navigation icon
        toolbar.setNavigationOnClickListener {
            var intent = Intent(applicationContext,RViewActivity::class.java)
            startActivity(intent)
            finish()
        }
        loading = LoadingDialog(this)
        loading.startLoading()
        imagesArray = loadArray("imagesPathList", applicationContext)
        origPic = findViewById<View>(R.id.cv4) as CropImageView
        cropPic = findViewById<View>(R.id.crop_image) as MaterialButton
        imagePic = findViewById<View>(R.id.set_image) as ImageView
        savePic = findViewById<View>(R.id.save_image) as MaterialButton
        cropPic.setOnClickListener(this)
        savePic.setOnClickListener(this)
        lvbeforecrop = findViewById(R.id.lv_beforecrop)
        lvaftercrop = findViewById(R.id.lv_aftercrop)
        var bundle :Bundle ?=intent.extras
        imageindex = bundle!!.getString("imageindex")?.toInt()
        //imageindex = ((imageindex?.toInt() ?: 0) - 1)
        imageindex = (imageindex?.toInt() ?: 0)
       /* Log.i(
            "bitmappos",
            "imageindex $imageindex"
        )*/
        frompage = bundle.getString("frompage").toString()
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
        val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
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
        loading.isDismiss()
        try {

            var image: Bitmap = MediaStore.Images.Media.getBitmap(
                applicationContext.contentResolver,
                Uri.parse(imagesArray[imageindex!!])
            )
            filename = Uri.parse(imagesArray[imageindex!!]).lastPathSegment.toString()
            val imageHeight = (Math.ceil(image.height.toDouble() / 100) * 100).toInt()
            val imageWidth = (Math.ceil(image.width.toDouble() / 100) * 100).toInt()
           /* Log.i(
                "scaleDownLargeImageWIthAspectRatio",
                "CropImage Dimensions(W:H): $imageWidth:$imageHeight"
            )*/
            origPic.setImageBitmap(image)
            /*
            if (getIntent().getBooleanExtra("isCircular", false)) {
            cropImageView.setCropMode(CropImageView.CropMode.CIRCLE);
        } else
             */
            origPic.setCropMode(CropImageView.CropMode.RATIO_FREE)
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
           /*  if (origPic.saveBitmapInToFolder()) {


                 imagesArray.set(imageindex!!, origPic.getImageUri().toString())
                if(saveArray(imagesArray, "imagesPathList", applicationContext)) {
                 //   Toast.makeText(this, "....frompage.."+frompage, Toast.LENGTH_SHORT).show()
                    if(frompage=="service") {
                        setWallpaperslide(v)
                    }
                    else
                    {
                        var intent = Intent(this,RGrid::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }*/
            var newimageUri: Uri? = origPic.saveCroppedImage(applicationContext,filename)
            imagesArray.set(imageindex!!,newimageUri.toString())
            if(saveArray(imagesArray, "imagesPathList", applicationContext)) {
                //   Toast.makeText(this, "....frompage.."+frompage, Toast.LENGTH_SHORT).show()
                if(frompage=="service") {
                    setWallpaperslide(v)
                }
                else
                {
                    var intent = Intent(this,RGrid::class.java)
                    intent.putExtra("frompage","none")
                    startActivity(intent)
                    finish()
                }
            }
        }
        }

    fun saveArray(array:Array<String>,arrayName: String, context: Context): Boolean {
        //clearing
        var prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        var editor = prefs.edit()
        editor.clear().commit()
        //reinserting
        prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        editor = prefs.edit()
        editor.putInt("${arrayName}_size", array.size)
        for (i in array.indices) {
            //  Toast.makeText(this,"data here.."+array[i],Toast.LENGTH_SHORT).show()
          /*  Log.i(
                "bitmappos",
                "data here..$i...."+array[i]
            )*/
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

        Handler().postDelayed({
            val intent = Intent(
                WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
            )
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, MyWallpaperService::class.java)
            )
            //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }, 4000)


    }

}