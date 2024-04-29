package com.example.wallpaperapp

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "image_table")
data class Wimages (
    @PrimaryKey @ColumnInfo(name = "imagepath") val imagepath: String)
