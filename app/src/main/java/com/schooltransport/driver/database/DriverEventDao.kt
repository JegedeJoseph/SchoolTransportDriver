
package com.schooltransport.driver.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DriverEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: DriverEvent): Long

    @Query("SELECT * FROM driver_events ORDER BY eventTimestamp DESC")
    suspend fun getAllEvents(): List<DriverEvent>

    @Query("SELECT * FROM driver_events WHERE syncStatus = 'PENDING'")
    suspend fun getPendingEvents(): List<DriverEvent>

    @Query("UPDATE driver_events SET syncStatus = 'SYNCED' WHERE id = :eventId")
    suspend fun markAsSynced(eventId: Int)

    @Query("SELECT COUNT(*) FROM driver_events WHERE syncStatus = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("""
        SELECT * FROM driver_events
        WHERE serviceDate = :serviceDate
        ORDER BY eventTimestamp DESC
    """)
    suspend fun getEventsForDate(serviceDate: String): List<DriverEvent>
}
