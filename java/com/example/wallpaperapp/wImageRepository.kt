package com.example.wallpaperapp

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow

class wImageRepository(private val wimageDao: WimageDao) {
    // Room executes all queries on a separate thread.
    // Observed Flow will notify the observer when the data has changed.
    val allWimages: Flow<List<Wimages>> = wimageDao.getAll()

    // By default Room runs suspend queries off the main thread, therefore, we don't need to
    // implement anything else to ensure we're not doing long running database work
    // off the main thread.
    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(wImage: Wimages) {
        wimageDao.insert(wImage)
    }
    suspend fun delete(wImage: Wimages) {
        wimageDao.delete(wImage)
    }
}