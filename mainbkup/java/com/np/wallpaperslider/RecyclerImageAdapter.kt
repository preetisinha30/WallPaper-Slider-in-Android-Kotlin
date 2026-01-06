package com.np.wallpaperslider

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView


class RecyclerImageAdapter constructor(private val context: Context,private val getActivity: RGrid,private val imagesList: MutableList<Imagepath>,private val onSelectionModeChanged: (Boolean) -> Unit = {}):
    RecyclerView.Adapter<RecyclerImageAdapter.MyViewHolder>() {
    private var isSelectionMode = false
    private val selectedPositions = mutableSetOf<Int>()
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerImageAdapter.MyViewHolder {

        val view = LayoutInflater.from(context).inflate(R.layout.layout_images_list,parent,false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerImageAdapter.MyViewHolder, position: Int) {
        val imagePath = imagesList[position].imagepath
        try {
            val image: Bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, Uri.parse(imagePath))
            holder.im_imagepath.setImageBitmap(image)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Handle checkbox visibility and state
        holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedPositions.contains(position)

        // Handle click
        holder.cardview.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
                notifyItemChanged(position)
            } else {
                val intent = Intent(context, RViewActivity::class.java).apply {
                    putExtra("data", imagePath)
                    putExtra("index", position)
                }
                context.startActivity(intent)
            }
        }
        // Handle long press to enter selection mode
        holder.cardview.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                toggleSelection(position)
                onSelectionModeChanged(true)
                notifyDataSetChanged()
            }
            true
        }

        // Toggle checkbox on click
        holder.checkBox.setOnClickListener {
            toggleSelection(position)
            notifyItemChanged(position)
        }
    }
    override fun getItemCount(): Int  = imagesList.size


    fun updateData(newList: List<Imagepath>) {
        val diffCallback = ImageDiffCallback(imagesList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        imagesList.clear()
        imagesList.addAll(newList)
        exitSelectionMode()
        diffResult.dispatchUpdatesTo(this)
    }
    fun updateData(){
        exitSelectionMode()
        notifyDataSetChanged()
    }
    fun itemRemovedAtPosition(position:Int){
        notifyItemRemoved(position)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedPositions.clear()
        onSelectionModeChanged(false)
        notifyDataSetChanged()
    }

    fun getSelectedPositions(): Set<Int> {
        Log.d(TAG, "Selected positions: $selectedPositions")
        return selectedPositions
    }

    private fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        if (selectedPositions.isEmpty()) {
            isSelectionMode = false
            onSelectionModeChanged(false)
        }
    }

    class MyViewHolder(itemView:View):RecyclerView.ViewHolder(itemView){
        val im_imagepath: ImageView = itemView.findViewById(R.id.thumbimg)
        val cardview: CardView = itemView.findViewById(R.id.cardView)
        val checkBox: CheckBox = itemView.findViewById(R.id.delete_checkbox)
    }
    companion object {
        private const val TAG = "RecyclerImageAdapter"
    }
}