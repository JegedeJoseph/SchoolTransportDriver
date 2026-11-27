package com.schooltransport.driver.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DriverEvent::class],
    version = 3,
    exportSchema = false
)
abstract class DriverDatabase : RoomDatabase() {

    abstract fun driverEventDao(): DriverEventDao

    companion object {
        @Volatile
        private var INSTANCE: DriverDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE driver_events
                    ADD COLUMN serviceDate TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {

                // Give older records distinct legacy dates.
                // This preserves them without assigning invented dates.
                database.execSQL(
                    """
                    UPDATE driver_events
                    SET serviceDate = 'LEGACY_' || id
                    WHERE serviceDate = ''
                    """.trimIndent()
                )

                // Enforce one event of each type per student per service date.
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_driver_events_studentId_eventType_serviceDate
                    ON driver_events (studentId, eventType, serviceDate)
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): DriverDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DriverDatabase::class.java,
                    "school_transport_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
