package com.schooltransport.driver.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "driver_events")
data class DriverEvent(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val studentId: String,

    val studentName: String,

    val eventType: String,

    val latitude: Double?,

    val longitude: Double?,

    val gpsAccuracy: Float?,

    val eventTimestamp: Long,

    val syncStatus: String = "PENDING"
)
