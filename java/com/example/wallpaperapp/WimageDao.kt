package com.example.wallpaperapp


import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow


@Dao
interface WimageDao {
    @Query("SELECT * FROM image_table")
    fun getAll(): Flow<List<Wimages>>

    // @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Upsert
    suspend fun insert(imagepath:Wimages)

    @Delete
    suspend fun delete(imagepath:Wimages)
}