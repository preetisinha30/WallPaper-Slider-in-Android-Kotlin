package com.np.wallpaperslider

import androidx.recyclerview.widget.DiffUtil

class ImageDiffCallback(
    private val oldList: List<Imagepath>,
    private val newList: List<Imagepath>
) : DiffUtil.Callback() {
    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // If each image has a unique path, you can compare them directly
        return oldList[oldItemPosition].imagepath == newList[newItemPosition].imagepath
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Return true if the content is visually the same
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}