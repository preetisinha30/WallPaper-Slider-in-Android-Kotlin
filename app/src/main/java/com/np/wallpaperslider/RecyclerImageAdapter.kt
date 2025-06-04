package com.np.wallpaperslider

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView


class RecyclerImageAdapter constructor(private val context: Context,private val getActivity: RGrid,private val imagesList: MutableList<Imagepath>):
    RecyclerView.Adapter<RecyclerImageAdapter.MyViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerImageAdapter.MyViewHolder {

        val view = LayoutInflater.from(context).inflate(R.layout.layout_images_list,parent,false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerImageAdapter.MyViewHolder, position: Int) {


        var image: Bitmap = MediaStore.Images.Media.getBitmap(
            context.contentResolver,
            Uri.parse(imagesList[position].imagepath)
        )
        holder.im_imagepath.setImageBitmap(image)
        holder.cardview.setOnClickListener{
            //Toast.makeText(getActivity, "AAAA", Toast.LENGTH_SHORT).show()
            val intent = Intent(context,RViewActivity::class.java)
            intent.putExtra("data",imagesList[position].imagepath)
            intent.putExtra("index",position)
            context.startActivity(intent)

        }
    }
    fun updateData(){
        notifyDataSetChanged()
    }
    fun itemRemovedAtPosition(position:Int){
        notifyItemRemoved(position)
    }
    override fun getItemCount(): Int {
        return imagesList.size
    }

    class MyViewHolder(itemView:View):RecyclerView.ViewHolder(itemView){
        val im_imagepath: ImageView = itemView.findViewById(R.id.thumbimg)
        val cardview: CardView = itemView.findViewById(R.id.cardView)

    }
}