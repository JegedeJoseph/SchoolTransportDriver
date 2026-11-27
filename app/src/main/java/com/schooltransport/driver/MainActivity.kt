
package com.schooltransport.driver

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.schooltransport.driver.database.DriverDatabase
import com.schooltransport.driver.database.DriverEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class MainActivity : Activity() {

    private enum class TripMode {
        MORNING_PICKUP,
        AFTERNOON_DROPOFF
    }

    private data class DemoStudent(
        val id: String,
        val name: String,
        var pickedUp: Boolean = false,
        var droppedOff: Boolean = false
    )

    // Default school closing time: 2:00 PM.
    // Owner-configurable settings will be added separately.
    private val schoolClosingTime = LocalTime.of(14, 0)

    private var tripMode = TripMode.MORNING_PICKUP

    private val students = mutableListOf(
        DemoStudent("DEMO_001", "Demo Student 01"),
        DemoStudent("DEMO_002", "Demo Student 02"),
        DemoStudent("DEMO_003", "Demo Student 03")
    )

    private lateinit var driverDatabase: DriverDatabase

    private val databaseScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val savingStudentIds = mutableSetOf<String>()

    private var eventsLoaded = false
    private var pendingEventCount = 0

    private val handler = Handler(Looper.getMainLooper())

    private val refreshTask = object : Runnable {
        override fun run() {
            renderScreen()
            handler.postDelayed(this, 30_000)
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var pendingStudent: DemoStudent? = null
    private var pendingEventType: String? = null

    private val timeFormatter =
        DateTimeFormatter.ofPattern("h:mm a")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        driverDatabase = DriverDatabase.getDatabase(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        renderScreen()
        loadSavedEvents()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshTask)
    }

    private fun isDropoffAvailable(): Boolean {
        return !LocalTime.now().isBefore(schoolClosingTime)
    }

    // Restore student statuses from saved database events.
    private fun loadSavedEvents() {

    databaseScope.launch {
        try {
            val dao = driverDatabase.driverEventDao()

            val events = dao.getAllEvents()
            val pendingCount = dao.getPendingCount()

            runOnUiThread {

                students.forEach { student ->
                    student.pickedUp = false
                    student.droppedOff = false

                    val studentEvents = events.filter {
                        it.studentId == student.id
                    }

                    student.pickedUp = studentEvents.any {
                        it.eventType == "PICKUP"
                    }

                    student.droppedOff = studentEvents.any {
                        it.eventType == "DROPOFF"
                    }
                }

                pendingEventCount = pendingCount
                eventsLoaded = true

                renderScreen()
            }
        }   
            catch (exception: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Could not load saved events: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
    return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val granted = hasLocationPermission()
            val student = pendingStudent
            val eventType = pendingEventType

            pendingStudent = null
            pendingEventType = null

            if (granted && student != null && eventType != null) {
                captureAndSaveEvent(student, eventType)
            } else {
                Toast.makeText(
                    this,
                    "Location permission is required for GPS capture.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Save a pickup or drop-off event permanently on the phone.
    private fun recordEvent(student: DemoStudent, eventType: String) {
        if (!eventsLoaded || student.id in savingStudentIds) return

        if (eventType == "PICKUP" && student.pickedUp) return

        if (eventType == "DROPOFF" &&
            (!student.pickedUp || student.droppedOff)
        ) return

        if (!hasLocationPermission()) {
            pendingStudent = student
            pendingEventType = eventType

            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        captureAndSaveEvent(student, eventType)
    }

    private fun captureAndSaveEvent(
        student: DemoStudent,
        eventType: String
    ) {
        if (student.id in savingStudentIds) return

        savingStudentIds.add(student.id)
        renderScreen()

        try {
            val cancellationTokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->

                saveEventToDatabase(student, eventType, location)

            }.addOnFailureListener {

                saveEventToDatabase(student, eventType, null)

            }

        } catch (exception: SecurityException) {
            saveEventToDatabase(student, eventType, null)
        }
    }

            private fun saveEventToDatabase(
        student: DemoStudent,
        eventType: String,
        location: Location?
    ) {
        databaseScope.launch {
            try {
                val event = DriverEvent(
                    studentId = student.id,
                    studentName = student.name,
                    eventType = eventType,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    gpsAccuracy = location?.accuracy,
                    eventTimestamp = System.currentTimeMillis(),
                    syncStatus = "PENDING"
                )

                val dao = driverDatabase.driverEventDao()

                dao.insertEvent(event)

                val pendingCount = dao.getPendingCount()

                runOnUiThread {
                    savingStudentIds.remove(student.id)

                    if (eventType == "PICKUP") {
                        student.pickedUp = true
                    } else {
                        student.droppedOff = true
                    }

                    pendingEventCount = pendingCount

                    val message = if (location != null) {
                        "$eventType saved with GPS location"
                    } else {
                        "$eventType saved, but GPS location unavailable"
                    }

                    Toast.makeText(
                        this@MainActivity,
                        message,
                        Toast.LENGTH_LONG
                    ).show()

                    renderScreen()
                }

            } catch (exception: Exception) {
                runOnUiThread {
                    savingStudentIds.remove(student.id)

                    Toast.makeText(
                        this@MainActivity,
                        "Could not save event: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    renderScreen()
                }
            }
        }
    }

    private fun renderScreen() {

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        val scrollView = ScrollView(this).apply {
            addView(content)
        }

        setContentView(scrollView)

        addText(content, "School Transport Driver", 26f, true)
        addText(content, "DEMO MODE — fictional data only", 14f, true)

        addText(content, "Assigned bus: Bus 01", 20f, true)
        addText(content, "Route: Demo School Route", 16f, false)
        addText(content, "Driver: Demo Driver", 16f, false)

        addText(content, "Today's Trip", 22f, true)

        addButton(
            content,
            "Morning: Student Pickup",
            true
        ) {
            tripMode = TripMode.MORNING_PICKUP
            renderScreen()
        }

        val dropoffAvailable = isDropoffAvailable()

        val dropoffButtonText = if (dropoffAvailable) {
            "Afternoon: Student Drop-off"
        } else {
            "Afternoon: Student Drop-off (Available at 2:00 PM)"
        }

        addButton(
            content,
            dropoffButtonText,
            dropoffAvailable
        ) {
            if (isDropoffAvailable()) {
                tripMode = TripMode.AFTERNOON_DROPOFF
                renderScreen()
            } else {
                Toast.makeText(
                    this,
                    "Afternoon drop-off is not yet available.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        addText(
            content,
            "School closing time: ${schoolClosingTime.format(timeFormatter)}",
            15f,
            false
        )

        val completed = if (tripMode == TripMode.MORNING_PICKUP) {
            students.count { it.pickedUp }
        } else {
            students.count { it.droppedOff }
        }

        addText(
            content,
            "Progress: $completed / ${students.size} students",
            18f,
            true
        )

        addText(
            content,
            "Events waiting to sync: $pendingEventCount",
            16f,
            true
        )

        if (!eventsLoaded) {
            addText(content, "Loading saved records...", 15f, false)
        }

        addText(content, "Student List", 22f, true)

        students.forEach { student ->

            addText(content, student.name, 18f, true)

            val status = when {
                student.droppedOff -> "Dropped off"
                student.pickedUp -> "Picked up — on bus"
                else -> "Waiting for pickup"
            }

            addText(content, "Status: $status", 15f, false)

            val isSaving = student.id in savingStudentIds

            if (tripMode == TripMode.MORNING_PICKUP) {

                addButton(
                    content,
                    if (isSaving) "Saving pickup..." else "Confirm Pickup",
                    eventsLoaded && !student.pickedUp && !isSaving
                ) {
                    recordEvent(student, "PICKUP")
                }

            } else {

                addButton(
                    content,
                    if (isSaving) "Saving drop-off..." else "Confirm Drop-off",
                    eventsLoaded &&
                        student.pickedUp &&
                        !student.droppedOff &&
                        !isSaving
                ) {
                    recordEvent(student, "DROPOFF")
                }
            }
        }

        addText(
            content,
            "Events are saved locally. GPS verification and server synchronisation have not yet been implemented.",
            14f,
            false
        )
    }

    private fun addText(
        parent: LinearLayout,
        text: String,
        size: Float,
        bold: Boolean
    ) {

        val view = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.BLACK)

            if (bold) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            setPadding(0, dp(8), 0, dp(8))
        }

        parent.addView(
            view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addButton(
        parent: LinearLayout,
        label: String,
        enabled: Boolean = true,
        action: () -> Unit
    ) {

        val button = Button(this).apply {
            text = label
            isEnabled = enabled
            setOnClickListener { action() }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(0, dp(4), 0, dp(8))

        parent.addView(button, params)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
    super.onDestroy()
    databaseScope.cancel()
}
}