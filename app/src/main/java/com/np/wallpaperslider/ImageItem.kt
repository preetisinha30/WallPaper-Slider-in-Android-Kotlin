package com.np.wallpaperslider

data class ImageItem(
    val id: Long,              // stable unique ID
    val imagePath: String,
    val category: String
) {
    companion object {
        // helper: generate id from imagePath hash
        fun fromPath(imagePath: String, category: String): ImageItem {
            return ImageItem(
                id = imagePath.hashCode().toLong(),
                imagePath = imagePath,
                category = category
            )
        }
    }
}