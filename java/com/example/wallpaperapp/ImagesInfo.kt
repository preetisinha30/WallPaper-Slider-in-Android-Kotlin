package com.example.wallpaperapp.com.example.wallpaperapp

import android.R.attr.name
import android.os.Parcel
import android.os.Parcelable


data class ImagesInfo(
    var title: String? = null,
    ) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
    }

    private fun readFromParcel(i: Parcel) {
        title = i.readString( )
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ImagesInfo> {
        override fun createFromParcel(parcel: Parcel): ImagesInfo {
            return ImagesInfo(parcel)
        }

        override fun newArray(size: Int): Array<ImagesInfo?> {
            return arrayOfNulls(size)
        }
    }
}