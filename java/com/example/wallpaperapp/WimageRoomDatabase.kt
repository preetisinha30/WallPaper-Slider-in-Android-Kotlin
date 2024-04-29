package com.example.wallpaperapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@Database(entities = arrayOf(Wimages::class), version = 1, exportSchema = false)
public abstract class WimageRoomDatabase : RoomDatabase() {

    abstract fun wimageDao(): WimageDao

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: WimageRoomDatabase? = null

        fun getDatabase(context: Context,
                        scope: CoroutineScope
        ): WimageRoomDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WimageRoomDatabase::class.java,
                    "wimages_database"
                ).fallbackToDestructiveMigration()
                    .addCallback(WimageDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }

    private class WimageDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        /**
         * Override the onCreate method to populate the database.
         */
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // If you want to keep the data through app restarts,
            // comment out the following line.
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.wimageDao())
                }
            }
        }

        /**
         * Populate the database in a new coroutine.
         * If you want to start with more words, just add them.
         */
        suspend fun populateDatabase(wimageDao: WimageDao) {
            // Start the app with a clean database every time.
            // Not needed if you only populate on creation.
            //wimageDao.deleteAll()

            var wimage = Wimages("R.drawable.fitness")
            wimageDao.insert(wimage)
            wimage = Wimages("R.drawable.fashion")
            wimageDao.insert(wimage)
            wimage = Wimages("R.drawable.dancing")
            wimageDao.insert(wimage)
            wimage = Wimages("R.drawable.concerts")
            wimageDao.insert(wimage)

        }

    }




}