package com.schooltransport.driver.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DriverEvent::class],
    version = 1,
    exportSchema = false
)
abstract class DriverDatabase : RoomDatabase() {

    abstract fun driverEventDao(): DriverEventDao

    companion object {
        @Volatile
        private var INSTANCE: DriverDatabase? = null

        fun getDatabase(context: Context): DriverDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DriverDatabase::class.java,
                    "school_transport_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}
