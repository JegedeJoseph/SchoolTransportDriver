package com.schooltransport.driver.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DriverEventDao {

    @Insert
    suspend fun insertEvent(event: DriverEvent)

    @Query("SELECT * FROM driver_events ORDER BY eventTimestamp DESC")
    suspend fun getAllEvents(): List<DriverEvent>

    @Query("SELECT * FROM driver_events WHERE syncStatus = 'PENDING'")
    suspend fun getPendingEvents(): List<DriverEvent>

    @Query("UPDATE driver_events SET syncStatus = 'SYNCED' WHERE id = :eventId")
    suspend fun markAsSynced(eventId: Int)

    @Query("SELECT COUNT(*) FROM driver_events WHERE syncStatus = 'PENDING'")
    suspend fun getPendingCount(): Int
}
