package com.vlad8110.teendrive.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class,
        RoutePointEntity::class,
        SpeedAlertEntity::class,
        SafetyAlertEntity::class,
        DeletedTripTombstoneEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TeenDriveDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var instance: TeenDriveDatabase? = null

        fun getInstance(context: Context): TeenDriveDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TeenDriveDatabase::class.java,
                    "teendrive.db",
                ).build().also { instance = it }
            }
    }
}
