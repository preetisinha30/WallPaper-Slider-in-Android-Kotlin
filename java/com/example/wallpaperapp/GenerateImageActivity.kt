package com.example.wallpaperapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.squareup.picasso.Picasso
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
/*If you want to Generate an Image by using Dall-E-3 and set that as wallpaper*/
class GenerateImageActivity: ComponentActivity() {
    private lateinit var inputTx : EditText
    private lateinit var generateBt : Button
    private lateinit var imgView : ImageView
    private lateinit var progressBar : ProgressBar
    var client = OkHttpClient()/*OkHttpClient is a modern applications network used to exchange data & media*/
    val chatGPT_API_KEY ="YOUR KEY"
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_genimage)
        inputTx = findViewById(R.id.input_text)
        generateBt = findViewById(R.id.genbutton)
        imgView = findViewById(R.id.image_view)
        progressBar = findViewById(R.id.progressbar)

        generateBt.setOnClickListener{
            val text = inputTx.text.toString()
            if(text.isEmpty())
            {
                inputTx.setError("Text can't be empty")
            }
            else
            {
                callAPI(text)
            }
        }


    }

    private fun callAPI(text: String) {
        setInProgress(true)
        val jsonBody = JSONObject()
        try {
            jsonBody.put("model","dall-e-3")
            jsonBody.put("prompt",text)
            jsonBody.put("n",1)
            jsonBody.put("size","1024x1024")
        }catch (e:Exception){
            Log.d("callingapi", e.message, e)
        }
        val requestBody:RequestBody = RequestBody.create(JSON,jsonBody.toString())
        val request : Request = Request.Builder()
            .url("https://api.openai.com/v1/images/generations")
            .header("Authorization","Bearer $chatGPT_API_KEY")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object:Callback{
            override fun onFailure(call: Call, e: IOException) {
                Toast.makeText(applicationContext,"Failed to generate image", Toast.LENGTH_SHORT).show()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())
                    val imgUrl = jsonBody.getJSONArray("data")
                        .getJSONObject(0)
                        .getString("url")
                    loadImage(imgUrl)
                }catch (e:Exception)
                {
                    Log.d("callingimageresponse", e.message, e)
                }
            }
        })
    }

    private fun setInProgress(inProgress:Boolean)
    {
        runOnUiThread{
            if(inProgress){
               progressBar.visibility = View.VISIBLE
               generateBt.visibility = View.GONE
            }
            else{
                progressBar.visibility = View.GONE
                generateBt.visibility = View.VISIBLE
            }
        }
    }

    private fun loadImage(imgUrl: String) {
        runOnUiThread {
            Picasso.get().load(imgUrl).into(imgView)
            imgView.visibility = View.VISIBLE
        }

    }
    companion object{
        val JSON : MediaType = "application/json; charset=utf-8".toMediaType()
    }
}