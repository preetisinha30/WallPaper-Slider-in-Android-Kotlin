package com.example.wallpaperapp

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar



class SingleImageCanvasActivity : ComponentActivity(), View.OnClickListener {
    var choosenImageView: DrawableImageView? = null
    var choosePicture: Button? = null
    var savePicture: Button? = null
    var bmp: Bitmap? = null
    var alteredBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simage)
        choosenImageView = findViewById<View>(R.id.ChoosenImageView) as DrawableImageView
        choosePicture = findViewById<View>(R.id.ChoosePictureButton) as Button
        savePicture = findViewById<View>(R.id.SavePictureButton) as Button
        savePicture!!.setOnClickListener(this)
        choosePicture!!.setOnClickListener(this)
/*
        try {
            val bottomAppBar = findViewById<BottomAppBar>(R.id.bottomAppBar)
            //setSupportActionBar(bottomAppBar)

            val fab = findViewById<FloatingActionButton>(R.id.fab)
            fab.setOnClickListener {
                // Handle FloatingActionButton click here
                Snackbar.make(it, "FloatingActionButton clicked", Snackbar.LENGTH_SHORT).show()
            }

            bottomAppBar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_home -> {
                        // Handle Home click here
                        true
                    }

                    R.id.action_search -> {
                        // Handle Search click here
                        true
                    }

                    R.id.action_notifications -> {
                        // Handle Notifications click here
                        true
                    }

                    R.id.action_settings -> {
                        // Handle Settings click here
                        true
                    }

                    else -> false
                }
            }
        }catch (e: Exception) {
           Log.e("bottombar", e.message, e)
        }*/
    }

    override fun onClick(v: View) {
        if (v === choosePicture) {
            val choosePictureIntent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            startActivityForResult(choosePictureIntent, 0)
        } else if (v === savePicture) {

            if (alteredBitmap != null) {
                Log.v("alteredBitmap EXCEPTION", "not null")
                val wallpaperManager = WallpaperManager.getInstance(baseContext)
                wallpaperManager.setBitmap(alteredBitmap, null, true, WallpaperManager.FLAG_LOCK)
                Toast.makeText(this, "Wallpaper set on Lockscreen!", Toast.LENGTH_SHORT).show()
                finish()
               /* val contentValues = ContentValues(3)
                contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, "Draw On Me")
                val imageFileUri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                )
                try {
                    val imageFileOS = contentResolver
                        .openOutputStream(imageFileUri!!)
                    alteredBitmap!!
                        .compress(CompressFormat.JPEG, 90, imageFileOS!!)
                    val t = Toast
                        .makeText(this, "Saved!", Toast.LENGTH_SHORT)
                    t.show()
                } catch (e: Exception) {
                    Log.v("EXCEPTION", e.message!!)
                }*/
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent? ) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode == RESULT_OK) {
            val imageFileUri = intent?.data
            try {
                val bmpFactoryOptions = BitmapFactory.Options()
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
                )
                choosenImageView!!.setNewImage(alteredBitmap, bmp)
            } catch (e: Exception) {
                Log.v("alteredBitmap ERROR", e.toString())
            }
        }
    }
}


