package com.np.wallpaperslider

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import androidx.core.net.toUri
import com.bumptech.glide.signature.ObjectKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


class RecyclerImageAdapter constructor(
    private val context: Context,
    private val getActivity: RGrid,
    private val imagesList: MutableList<ImageItem>,
    private val onSelectionModeChanged: (Boolean) -> Unit = {}):
    RecyclerView.Adapter<RecyclerImageAdapter.MyViewHolder>() {
    private var isSelectionMode = false
    //private val selectedPositions = mutableSetOf<Int>()
    private val selectedIds = mutableSetOf<Long>()
    private var lastLongPressedView: View? = null // Track the last long-pressed view


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerImageAdapter.MyViewHolder {

        val view = LayoutInflater.from(context).inflate(R.layout.layout_images_list,parent,false)
        return MyViewHolder(view)
    }

    private fun getCacheBuster(uriString: String): ObjectKey {
        val prefs = context.getSharedPreferences("glide_cache_busters", Context.MODE_PRIVATE)
        val buster = prefs.getInt(uriString, 0)
        return ObjectKey(buster) // Use the counter as the cache signature
    }
    override fun onBindViewHolder(holder: RecyclerImageAdapter.MyViewHolder, position: Int) {
        val imageItem = imagesList[position]
        val imagePath = imageItem.imagePath
        val category = imageItem.category
        val cacheBusterKey = getCacheBuster(imagePath)
        try {
            Glide.with(context)
                .load(imagePath.toUri())
                .apply(
                    RequestOptions()
                   // .diskCacheStrategy(DiskCacheStrategy.NONE)
                   // .skipMemoryCache(true)
                    //.signature(ObjectKey(File(imagePath).lastModified()))
                        .signature(cacheBusterKey)
                    .error(R.drawable.error_placeholder) // Create this drawable
                    .placeholder(R.drawable.placeholder)) // Optional: loading placeholder
                .into(holder.im_imagepath)
            Log.d(TAG, "Loading image: $imagePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image: $imagePath, error: ${e.message}", e)
        }
        holder.categoryIndicator.setImageDrawable(null)
        holder.categoryIndicator.visibility = View.GONE
        // Set category indicator
        if (getActivity.currentSegment == "all") {
            val indicatorRes = when (category) {
                "home" -> R.drawable.homeimage
                "lock" -> R.drawable.lockimage
                "both" -> R.drawable.bothimage
                else -> 0
            }
            if (indicatorRes != 0) {
                holder.categoryIndicator.visibility = View.VISIBLE
                holder.categoryIndicator.setImageResource(indicatorRes)

            } else {
                holder.categoryIndicator.visibility = View.GONE
            }
        } else {
            // If currentSegment is NOT "all" (i.e., filtered view), explicitly hide the indicator
            holder.categoryIndicator.visibility = View.GONE
        }
        // If currentSegment is NOT "all", the indicator remains View.GONE.

        // Handle checkbox visibility and state
        holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedIds.contains(imageItem.id)

        // Handle click
        holder.cardview.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(imageItem.id)
                notifyItemChanged(position)
                // Store position in the tag for context menu
                holder.cardview.tag = position
                holder.checkBox.isChecked = selectedIds.contains(imageItem.id)
                onSelectionModeChanged(selectedIds.isNotEmpty())
            } else {
                val intent = Intent(context, RViewActivity::class.java).apply {
                    putExtra("data", imagePath)
                    putExtra("index", imageItem.id)
                    putExtra("cat",category)
                }
                context.startActivity(intent)
                notifyItemChanged(position)
            }
        }
        // Handle long press to either enter selection mode or show context menu
        holder.cardview.setOnLongClickListener {
            lastLongPressedView = holder.cardview // Store the long-pressed view
            if (!isSelectionMode) {
                // Option 1: Show context menu only (recommended to avoid confusion)
                //it.showContextMenu()
                getActivity.openContextMenu(holder.cardview)
                getActivity.setImageTapCompleted(true)
                if(getActivity.isImageTapCompleted())
                {
                    getActivity.spotlightView.visibility = View.GONE
                    getActivity.titleView.visibility = View.GONE
                }
                true
            } else {
                // Option 2: Toggle selection mode (if you want to keep this behavior)
                toggleSelection(imageItem.id)
                holder.checkBox.isChecked = selectedIds.contains(imageItem.id)
                onSelectionModeChanged(selectedIds.isNotEmpty())
                notifyItemChanged(position)
                true
            }
        }

        // Toggle checkbox on click
        holder.checkBox.setOnClickListener {
            toggleSelection(imageItem.id)
            //notifyItemChanged(position)
        }

        // Store position in the tag for context menu
        holder.cardview.tag = imageItem.id

    }
    // Method to get the last long-pressed view
    fun getLastLongPressedView(): View? = lastLongPressedView

    override fun getItemCount(): Int  = imagesList.size

    override fun onViewRecycled(holder: MyViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(context).clear(holder.im_imagepath)
    }

    fun updateData(newList: List<ImageItem>) {
        //Log.d(TAG, "Before exit: ${newList.size} images: $newList")
        //exitSelectionMode()
        //Log.d(TAG, "after exit: ${newList.size} images: $newList")
        /*val diffCallback = ImageDiffCallback(imagesList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        //Log.d(TAG, "imagesList before clear: ${imagesList.size}")
        imagesList.clear()
        //Log.d(TAG, "imagesList after clear: ${imagesList.size}")
        imagesList.addAll(newList)
        //Log.d(TAG, "imagesList after addAll: ${imagesList.size}")
        diffResult.dispatchUpdatesTo(this)*/
        //Log.d(TAG, "Updated data: ${imagesList.size} images: $imagesList")
        imagesList.clear()
        imagesList.addAll(newList)

        // Clear selection mode if the view list has changed (e.g., segment switch)
        if (selectedIds.isNotEmpty() && newList.none { it.id in selectedIds }) {
            exitSelectionMode()
        }

        // Force a full redraw for immediate UI reflection without move animations.
        notifyDataSetChanged()

        Log.d(TAG, "Data updated and notifyDataSetChanged called for immediate UI reflection.")
    }


    fun refresh(){
        exitSelectionMode()
        notifyDataSetChanged()
    }
    fun itemRemovedAtPosition(position:Int){
        val id = imagesList[position].id
        selectedIds.remove(id)
        imagesList.removeAt(position)
        notifyItemRemoved(position)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        onSelectionModeChanged(false)
        notifyDataSetChanged()
    }
    fun enterSelectionModeFromToolbar() {
        if (!isSelectionMode) {
            isSelectionMode = true
            onSelectionModeChanged(true)
            notifyDataSetChanged()
        }
    }
    /*fun getSelectedPositions(): Set<Int> {
        Log.d(TAG, "Selected positions: $selectedPositions")
        return selectedPositions
    }*/
    fun getSelectedIds(): Set<Long> = selectedIds

    private fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
            isSelectionMode = true
            onSelectionModeChanged(true)
        }
        if (selectedIds.isEmpty()) {
            isSelectionMode = false
            onSelectionModeChanged(false)
        }
    }

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val im_imagepath: ImageView = itemView.findViewById(R.id.thumbimg)
        val cardview: CardView = itemView.findViewById(R.id.cardView)
        val checkBox: CheckBox = itemView.findViewById(R.id.delete_checkbox)
        val categoryIndicator: ImageView = itemView.findViewById(R.id.category_indicator)
        init {
            // Register the card view for context menu
            getActivity.registerForContextMenu(cardview)

            // Set up context menu creation
            /*cardview.setOnCreateContextMenuListener { menu, _, menuInfo ->
                menu.setHeaderTitle("Choose")
                getActivity.menuInflater.inflate(R.menu.wallpaper_context_menu, menu)

            }*/
        }
    }

    companion object {
        private const val TAG = "RecyclerImageAdapter"
    }
}
