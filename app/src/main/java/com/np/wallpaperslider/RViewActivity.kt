package com.np.wallpaperslider

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.activity.ComponentActivity
import androidx.annotation.ContentView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.FileNotFoundException
import java.io.IOException


class RViewActivity:ComponentActivity(), View.OnClickListener {
    lateinit var view_image : ImageView
    lateinit var delicon : MaterialButton
    lateinit var cropicon : MaterialButton
    lateinit var rotateicon : MaterialButton
    lateinit var save_rimage : MaterialButton
    lateinit var discard_image : MaterialButton
    lateinit var image : Bitmap
    //lateinit var nexticon : Button
    var index :Int? =0
    var imagesList = ArrayList<Imagepath>()
    var ct:Int = 1
    lateinit var lvafterrotate : LinearLayout
    lateinit var filename : String
    lateinit var rotated : Bitmap
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view)

        val toolbar = findViewById<MaterialToolbar>(R.id.xml_toolbar)
        toolbar.title = "Image Editor" // Optional: Set a title
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow) // Optional: Add a navigation icon
        toolbar.setNavigationOnClickListener {
            var intent = Intent(applicationContext,RGrid::class.java)
            intent.putExtra("frompage","none")
            startActivity(intent)
            finish()
        }
        lvafterrotate = findViewById(R.id.after_rotate_container)
        lvafterrotate.visibility=View.GONE
        var modalItems: String = intent.getStringExtra("data") ?: ""

        val inputStream = applicationContext.contentResolver.openInputStream(Uri.parse(modalItems))
        inputStream?.use {
            image = BitmapFactory.decodeStream(it)
        }

        filename = Uri.parse(modalItems).lastPathSegment.toString()
        view_image = findViewById(R.id.view_image)
        view_image.setImageBitmap(image)
        index = intent.getSerializableExtra("index") as Int

        delicon = findViewById(R.id.delicon)
        cropicon = findViewById(R.id.cropicon)
        rotateicon = findViewById(R.id.rotateicon)
        save_rimage = findViewById(R.id.save_rimage)
        discard_image = findViewById(R.id.discard_image)
        //nexticon = findViewById(R.id.nexticon)
        delicon.setOnClickListener(this)
        cropicon.setOnClickListener(this)
        rotateicon.setOnClickListener(this)
        save_rimage.setOnClickListener(this)
        discard_image.setOnClickListener(this)


    }



    @SuppressLint("SuspiciousIndentation")
    override fun onClick(v: View?) {
        if(v==delicon)
        {
            val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
            val size = prefs.getInt("imagesPathList_size", 0)



            val array = Array(size-1) { "" }
            var j = 0
            for (i in 0 until size) {

                if(i!=index)
                {
                    array[j] = prefs.getString("imagesPathList_$i", null) ?: ""
                    imagesList.add(Imagepath(array[j]))
                    j++

                }
                else
                {
                    RGrid.recyclerImageAdapter.itemRemovedAtPosition(index!!)
                }

            }

            if(saveArray(array, "imagesPathList", applicationContext))
            {

                var intent = Intent(applicationContext,RGrid::class.java)
                intent.putExtra("frompage","none")
                startActivity(intent)


            }
        }
        else if(v==cropicon)
        {
            val i = Intent(applicationContext, CropActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            i.putExtra("imageindex",index.toString())
            i.putExtra("frompage","grid")
            startActivity(i)
        }
        else if(v==rotateicon)
        {
            if(ct == 1)
                rotateImage(RotateDegrees.ROTATE_90D)
            else if(ct == 2)
                rotateImage(RotateDegrees.ROTATE_180D)
            else if(ct == 3)
                rotateImage(RotateDegrees.ROTATE_270D)
            else if(ct == 4) {
                rotateImage(RotateDegrees.ROTATE_360D)
                ct = 0
            }
            ct++
            if(ct!=1)
                lvafterrotate.visibility=View.VISIBLE
        }
        else if(v==save_rimage)
        {
            var newimageUri: Uri? = saveRotatedImage(applicationContext,filename)

            var array = loadArray("imagesPathList", applicationContext)
            array.set(index!!,newimageUri.toString())

            lvafterrotate.visibility=View.GONE
            if(saveArray(array, "imagesPathList", applicationContext))
            {

                var intent = Intent(applicationContext,RGrid::class.java)
                intent.putExtra("frompage","none")
                startActivity(intent)
                finish()

            }
        }
        else if(v==discard_image)
        {
            view_image.setImageBitmap(image)
            lvafterrotate.visibility=View.GONE
        }

    }
    enum class RotateDegrees(val value: Int) {
        ROTATE_90D(90),
        ROTATE_180D(180),
        ROTATE_270D(270),
        ROTATE_360D(360)


    }

    fun rotateImage(degrees: RotateDegrees) {
        val source =  image?: return
        val angle = degrees.value
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        rotated = Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
        view_image.setImageBitmap(rotated)
    }

    fun saveRotatedImage(context: Context, fileName: String): Uri? {

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + "WallPaperApp"
                )
            }

            val resolver = context.contentResolver
            val imageUri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            imageUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { outputStream ->
                    rotated.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

                }
            }

            return imageUri // Return URI instead of file path

    }

    fun loadArray(arrayName: String, context: Context): Array<String> {
        val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        val size = prefs.getInt("${arrayName}_size", 0)

        val array = Array(size) { "" }
        for (i in 0 until size) {
            array[i] = prefs.getString("${arrayName}_$i", null) ?: ""

        }

        return array
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

            editor.putString("${arrayName}_$i", array[i])
        }

        return editor.commit()
    }



    fun imageExists(context: Context, imageUri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { true } ?: false
        } catch (e: FileNotFoundException) {
            false  // Image does not exist
        }
    }





}


