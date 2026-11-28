
package com.schooltransport.driver

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
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
import java.time.LocalDate

class MainActivity : Activity() {

    private enum class TripMode {
        MORNING_PICKUP,
        AFTERNOON_DROPOFF
    }

    private data class DemoStudent(
    val id: String,
    val name: String,
    val homeLatitude: Double,
    val homeLongitude: Double,
    var pickedUp: Boolean = false,
    var droppedOff: Boolean = false
    )

    // Default school closing time: 2:00 PM.
    // Owner-configurable settings will be added separately.
    private val schoolClosingTime = LocalTime.of(14, 0)
    private val pickupCutoffTime = LocalTime.of(12, 0)

    private var tripMode = TripMode.MORNING_PICKUP

    private val students = mutableListOf(
    DemoStudent(
        "DEMO_001",
        "Demo Student 01",
        6.5856348,
        3.5981975
    ),
    DemoStudent(
        "DEMO_002",
        "Demo Student 02",
        6.587041,
        3.597996
    ),
    DemoStudent(
        "DEMO_003",
        "Demo Student 03",
        6.586142,
        3.598720
    )
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

    private val maxGpsAccuracyMeters = 30f
    private val geofenceRadiusMeters = 100f
    private val maxLocationAgeMillis = 30_000L

    private fun isPickupAvailable(): Boolean {
    return LocalTime.now().isBefore(pickupCutoffTime)
}
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

                val events = dao.getEventsForDate(LocalDate.now().toString())
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

    private fun validateLocation(
        student: DemoStudent,
        location: Location
    ): String? {

        // Reject a location without accuracy information.
        if (!location.hasAccuracy()) {
            return "GPS accuracy information is unavailable."
        }

        // Reject poor GPS accuracy.
        if (location.accuracy > maxGpsAccuracyMeters) {
            return "GPS accuracy is too poor (${location.accuracy.toInt()}m). Move to an open area and retry."
        }

        // Reject stale or invalid timestamps.
        val locationAge = System.currentTimeMillis() - location.time

        if (locationAge < 0 || locationAge > maxLocationAgeMillis) {
            return "GPS location is outdated. Please retry."
        }

        // Calculate distance from the student's approved home location.
        val distanceResults = FloatArray(1)

        Location.distanceBetween(
            location.latitude,
            location.longitude,
            student.homeLatitude,
            student.homeLongitude,
            distanceResults
        )

        val distanceFromHome = distanceResults[0]

        if (distanceFromHome > geofenceRadiusMeters) {
            return "You are ${distanceFromHome.toInt()}m from the pickup location. Move within ${geofenceRadiusMeters.toInt()}m and retry."
        }

        return null
    }

    // Save a pickup or drop-off event permanently on the phone.
    private fun recordEvent(student: DemoStudent, eventType: String) {
        if (!eventsLoaded || student.id in savingStudentIds) return
                
        if (eventType == "PICKUP" && !isPickupAvailable()) {
            Toast.makeText(
                this,
                "Student pickup is closed after 12:00 PM.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (eventType == "PICKUP" && student.pickedUp) return

        if (eventType == "DROPOFF" && student.droppedOff) return

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

                if (location == null) {
                    rejectLocationEvent(
                        student,
                        "GPS location unavailable. Move to an open area and retry."
                    )
                    return@addOnSuccessListener
                }

                val validationError = validateLocation(student, location)

                if (validationError != null) {
                    rejectLocationEvent(student, validationError)
                    return@addOnSuccessListener
                }

                // Only a location that passes validation is saved.
                saveEventToDatabase(student, eventType, location)

            }.addOnFailureListener { exception ->

                rejectLocationEvent(
                    student,
                    "Could not obtain GPS location: ${exception.message}"
                )
            }

        } catch (exception: SecurityException) {
            rejectLocationEvent(
                student,
                "Location permission was denied."
            )
        }
    }

    private fun rejectLocationEvent(
        student: DemoStudent,
        message: String
    ) {
        runOnUiThread {
            savingStudentIds.remove(student.id)

            Toast.makeText(
                this@MainActivity,
                message,
                Toast.LENGTH_LONG
            ).show()

            renderScreen()
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
                    serviceDate = LocalDate.now().toString(),
                    syncStatus = "PENDING"
                )

                val dao = driverDatabase.driverEventDao()

                val insertedId = dao.insertEvent(event)

                if (insertedId == -1L) {
                    runOnUiThread {
                        savingStudentIds.remove(student.id)

                        Toast.makeText(
                            this@MainActivity,
                            "Duplicate blocked: this event has already been recorded today",
                            Toast.LENGTH_LONG
                        ).show()

                        renderScreen()
                    }
                    return@launch
                }

                val pendingCount = dao.getPendingCount()

                runOnUiThread {
                    savingStudentIds.remove(student.id)

                    if (eventType == "PICKUP") {
                        student.pickedUp = true
                    } else {
                        student.droppedOff = true
                    }

                    pendingEventCount = pendingCount

                    Toast.makeText(
                        this@MainActivity,
                        "$eventType saved with GPS location",
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
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(DesignSystem.SKY_TINT_BG)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scrollView.addView(content)
        setContentView(scrollView)

        // ==========================================
        // 1. BRAND HERO HEADER (Sky Blue Gradient)
        // ==========================================
        val heroHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = DesignSystem.createGradientHeader(
                this@MainActivity,
                DesignSystem.SKY_PRIMARY_DARK,
                DesignSystem.SKY_PRIMARY,
                16
            )
            elevation = dp(3).toFloat()
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(14))
            }
        }

        val headerCategory = TextView(this).apply {
            text = "SCHOOL TRANSPORT DRIVER"
            textSize = 11f
            setTextColor(Color.parseColor("#B3E5FC"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }
        heroHeader.addView(headerCategory)

        val appTitle = TextView(this).apply {
            text = "Driver Portal"
            textSize = 24f
            setTextColor(DesignSystem.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(8))
        }
        heroHeader.addView(appTitle)

        val demoBadgeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val demoBadge = TextView(this).apply {
            text = "DEMO MODE — fictional data only"
            textSize = 11f
            setTextColor(DesignSystem.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = DesignSystem.createPillBadge(
                this@MainActivity,
                Color.parseColor("#33FFFFFF"),
                Color.TRANSPARENT,
                0
            )
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        demoBadgeContainer.addView(demoBadge)
        heroHeader.addView(demoBadgeContainer)
        content.addView(heroHeader)

        // ==========================================
        // 2. ASSIGNED VEHICLE & ROUTE CARD
        // ==========================================
        val vehicleCard = createCardLayout()

        val vehicleCardHeader = TextView(this).apply {
            text = "ASSIGNED TRIP DETAILS"
            textSize = 11f
            setTextColor(DesignSystem.SKY_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding(0, 0, 0, dp(10))
        }
        vehicleCard.addView(vehicleCardHeader)

        val busRow = createInfoRow("Assigned bus", "Bus 01", isHighlighted = true)
        val routeRow = createInfoRow("Route", "Demo School Route", isHighlighted = false)
        val driverRow = createInfoRow("Driver", "Demo Driver", isHighlighted = false)

        vehicleCard.addView(busRow)
        vehicleCard.addView(routeRow)
        vehicleCard.addView(driverRow)
        content.addView(vehicleCard)

        // ==========================================
        // 3. TODAY'S TRIP (MODE SELECTION) CARD
        // ==========================================
        val tripCard = createCardLayout()

        val tripHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        val tripTitle = TextView(this).apply {
            text = "Today's Trip"
            textSize = 18f
            setTextColor(DesignSystem.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tripHeaderRow.addView(tripTitle)

        val closingTimeText = TextView(this).apply {
            text = "School closes: ${schoolClosingTime.format(timeFormatter)}"
            textSize = 12f
            setTextColor(DesignSystem.TEXT_SECONDARY)
        }
        tripHeaderRow.addView(closingTimeText)
        tripCard.addView(tripHeaderRow)

        val tripSubtitle = TextView(this).apply {
            text = "Select an active trip mode to manage student arrivals and departures."
            textSize = 13f
            setTextColor(DesignSystem.TEXT_SECONDARY)
            setPadding(0, 0, 0, dp(12))
        }
        tripCard.addView(tripSubtitle)

        // Morning Pickup button & action
        val pickupAvailable = isPickupAvailable()
        val isMorningActive = tripMode == TripMode.MORNING_PICKUP
        val pickupButtonText = if (pickupAvailable) {
            "Morning: Student Pickup" + if (isMorningActive) "  ● ACTIVE" else ""
        } else {
            "Morning: Student Pickup (Closed at 12:00 PM)"
        }

        val pickupButton = createSegmentButton(
            text = pickupButtonText,
            isActive = isMorningActive,
            isEnabled = pickupAvailable
        ) {
            if (isPickupAvailable()) {
                tripMode = TripMode.MORNING_PICKUP
                renderScreen()
            } else {
                Toast.makeText(
                    this,
                    "Student pickup is closed after 12:00 PM.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        tripCard.addView(pickupButton)

        // Afternoon Drop-off button & action
        val dropoffAvailable = isDropoffAvailable()
        val isAfternoonActive = tripMode == TripMode.AFTERNOON_DROPOFF
        val dropoffButtonText = if (dropoffAvailable) {
            "Afternoon: Student Drop-off" + if (isAfternoonActive) "  ● ACTIVE" else ""
        } else {
            "Afternoon: Student Drop-off (Available at 2:00 PM)"
        }

        val dropoffButton = createSegmentButton(
            text = dropoffButtonText,
            isActive = isAfternoonActive,
            isEnabled = dropoffAvailable
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
        tripCard.addView(dropoffButton)
        content.addView(tripCard)

        // ==========================================
        // 4. PROGRESS & SYNC METRICS CARD
        // ==========================================
        val completed = if (tripMode == TripMode.MORNING_PICKUP) {
            students.count { it.pickedUp }
        } else {
            students.count { it.droppedOff }
        }

        val progressCard = createCardLayout()

        val progressHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val progressTitle = TextView(this).apply {
            text = "Trip Progress"
            textSize = 15f
            setTextColor(DesignSystem.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        progressHeaderRow.addView(progressTitle)

        val progressCount = TextView(this).apply {
            text = "$completed / ${students.size} students"
            textSize = 15f
            setTextColor(DesignSystem.SKY_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        }
        progressHeaderRow.addView(progressCount)
        progressCard.addView(progressHeaderRow)

        // Visual Progress Bar
        val progressBarContainer = FrameLayout(this).apply {
            background = DesignSystem.createPillBadge(
                this@MainActivity,
                DesignSystem.SKY_SURFACE_LIGHT,
                Color.TRANSPARENT,
                0
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
            ).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }

        val totalStudents = if (students.isNotEmpty()) students.size else 1
        val progressRatio = (completed.toFloat() / totalStudents).coerceIn(0f, 1f)

        val progressFill = View(this).apply {
            background = DesignSystem.createPillBadge(
                this@MainActivity,
                DesignSystem.SKY_PRIMARY,
                Color.TRANSPARENT,
                0
            )
            val fillParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            post {
                val measuredWidth = progressBarContainer.width
                fillParams.width = (measuredWidth * progressRatio).toInt()
                layoutParams = fillParams
            }
        }
        progressBarContainer.addView(progressFill)
        progressCard.addView(progressBarContainer)

        // Sync & Loading Status Chips
        val badgesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val syncBadge = TextView(this).apply {
            if (pendingEventCount > 0) {
                text = "Events waiting to sync: $pendingEventCount"
                setTextColor(DesignSystem.SYNC_PENDING_TEXT)
                background = DesignSystem.createPillBadge(
                    this@MainActivity,
                    DesignSystem.SYNC_PENDING_BG,
                    DesignSystem.SYNC_PENDING_BORDER,
                    1
                )
            } else {
                text = "Events synced"
                setTextColor(DesignSystem.SYNC_OK_TEXT)
                background = DesignSystem.createPillBadge(
                    this@MainActivity,
                    DesignSystem.SYNC_OK_BG,
                    DesignSystem.SYNC_OK_BORDER,
                    1
                )
            }
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        badgesRow.addView(syncBadge)

        if (!eventsLoaded) {
            val loadingBadge = TextView(this).apply {
                text = "Loading saved records..."
                textSize = 12f
                setTextColor(DesignSystem.SKY_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                background = DesignSystem.createPillBadge(
                    this@MainActivity,
                    DesignSystem.SKY_SURFACE_LIGHT,
                    DesignSystem.BORDER_SKY,
                    1
                )
                setPadding(dp(10), dp(4), dp(10), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(8), 0, 0, 0)
                }
            }
            badgesRow.addView(loadingBadge)
        }
        progressCard.addView(badgesRow)
        content.addView(progressCard)

        // ==========================================
        // 5. STUDENT ROSTER LIST
        // ==========================================
        val listHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(10))
        }

        val listTitle = TextView(this).apply {
            text = "Student List"
            textSize = 18f
            setTextColor(DesignSystem.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        listHeaderRow.addView(listTitle)

        val listCountBadge = TextView(this).apply {
            text = "${students.size} Total"
            textSize = 12f
            setTextColor(DesignSystem.TEXT_SECONDARY)
            background = DesignSystem.createPillBadge(
                this@MainActivity,
                DesignSystem.WHITE,
                DesignSystem.BORDER_LIGHT,
                1
            )
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        listHeaderRow.addView(listCountBadge)
        content.addView(listHeaderRow)

        students.forEachIndexed { index, student ->
            val studentCard = createCardLayout()

            // Top Row: Avatar + Name/ID + Status Badge
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(12))
            }

            val avatar = TextView(this).apply {
                text = String.format("%02d", index + 1)
                textSize = 13f
                setTextColor(DesignSystem.SKY_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = DesignSystem.createCircleDrawable(
                    this@MainActivity,
                    DesignSystem.SKY_SURFACE_LIGHT,
                    DesignSystem.BORDER_SKY,
                    1
                )
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            }
            topRow.addView(avatar)

            val studentInfo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val studentName = TextView(this).apply {
                text = student.name
                textSize = 16f
                setTextColor(DesignSystem.TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
            }
            studentInfo.addView(studentName)

            val studentId = TextView(this).apply {
                text = "ID: ${student.id}"
                textSize = 12f
                setTextColor(DesignSystem.TEXT_TERTIARY)
            }
            studentInfo.addView(studentId)
            topRow.addView(studentInfo)

            // Status Badge
            val statusBadge = TextView(this).apply {
                when {
                    student.droppedOff -> {
                        text = "Dropped off"
                        setTextColor(DesignSystem.STATUS_DROPOFF_TEXT)
                        background = DesignSystem.createPillBadge(
                            this@MainActivity,
                            DesignSystem.STATUS_DROPOFF_BG,
                            DesignSystem.STATUS_DROPOFF_BORDER,
                            1
                        )
                    }
                    student.pickedUp -> {
                        text = "Picked up — on bus"
                        setTextColor(DesignSystem.STATUS_PICKUP_TEXT)
                        background = DesignSystem.createPillBadge(
                            this@MainActivity,
                            DesignSystem.STATUS_PICKUP_BG,
                            DesignSystem.STATUS_PICKUP_BORDER,
                            1
                        )
                    }
                    else -> {
                        text = "Waiting for pickup"
                        setTextColor(DesignSystem.STATUS_WAITING_TEXT)
                        background = DesignSystem.createPillBadge(
                            this@MainActivity,
                            DesignSystem.STATUS_WAITING_BG,
                            DesignSystem.STATUS_WAITING_BORDER,
                            1
                        )
                    }
                }
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }
            topRow.addView(statusBadge)
            studentCard.addView(topRow)

            // Action Button
            val isSaving = student.id in savingStudentIds

            if (tripMode == TripMode.MORNING_PICKUP) {
                val isButtonEnabled = eventsLoaded && !student.pickedUp && !isSaving
                val buttonLabel = when {
                    isSaving -> "Saving pickup..."
                    student.pickedUp -> "✓ Pickup Confirmed"
                    else -> "Confirm Pickup"
                }

                val actionButton = createActionButton(
                    label = buttonLabel,
                    isEnabled = isButtonEnabled,
                    isCompleted = student.pickedUp && !isSaving
                ) {
                    recordEvent(student, "PICKUP")
                }
                studentCard.addView(actionButton)
            } else {
                val isButtonEnabled = eventsLoaded && !student.droppedOff && !isSaving
                val buttonLabel = when {
                    isSaving -> "Saving drop-off..."
                    student.droppedOff -> "✓ Drop-off Confirmed"
                    else -> "Confirm Drop-off"
                }

                val actionButton = createActionButton(
                    label = buttonLabel,
                    isEnabled = isButtonEnabled,
                    isCompleted = student.droppedOff && !isSaving
                ) {
                    recordEvent(student, "DROPOFF")
                }
                studentCard.addView(actionButton)
            }

            content.addView(studentCard)
        }

        // ==========================================
        // 6. SYSTEM INFO FOOTER CARD
        // ==========================================
        val footerCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = DesignSystem.createCardDrawable(
                this@MainActivity,
                DesignSystem.WHITE,
                DesignSystem.BORDER_SKY,
                1,
                12
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(4), 0, dp(16))
            }
        }

        val footerText = TextView(this).apply {
            text = "Events are saved locally. GPS verification and server synchronisation have not yet been implemented."
            textSize = 12f
            setTextColor(DesignSystem.TEXT_SECONDARY)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        footerCard.addView(footerText)
        content.addView(footerCard)
    }

    // --- Modern UI Helper Functions ---

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = DesignSystem.createCardDrawable(
                this@MainActivity,
                DesignSystem.WHITE,
                DesignSystem.BORDER_LIGHT,
                1,
                14
            )
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }
    }

    private fun createInfoRow(label: String, value: String, isHighlighted: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))

            val labelView = TextView(this@MainActivity).apply {
                text = "$label: "
                textSize = 14f
                setTextColor(DesignSystem.TEXT_SECONDARY)
            }
            addView(labelView)

            val valueView = TextView(this@MainActivity).apply {
                text = value
                textSize = 14f
                setTextColor(if (isHighlighted) DesignSystem.TEXT_PRIMARY else DesignSystem.TEXT_SECONDARY)
                if (isHighlighted) typeface = Typeface.DEFAULT_BOLD
            }
            addView(valueView)
        }
    }

    private fun createSegmentButton(
        text: String,
        isActive: Boolean,
        isEnabled: Boolean,
        action: () -> Unit
    ): View {
        return Button(this).apply {
            this.text = text
            this.isEnabled = isEnabled
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false

            if (!isEnabled) {
                background = DesignSystem.createCardDrawable(
                    this@MainActivity,
                    DesignSystem.BTN_DISABLED_BG,
                    Color.TRANSPARENT,
                    0,
                    10
                )
                setTextColor(DesignSystem.BTN_DISABLED_TEXT)
            } else if (isActive) {
                background = DesignSystem.createModernButton(
                    this@MainActivity,
                    DesignSystem.SKY_PRIMARY,
                    DesignSystem.SKY_PRIMARY_DARK,
                    10
                ) as Drawable
                setTextColor(DesignSystem.WHITE)
                elevation = dp(2).toFloat()
            } else {
                background = DesignSystem.createModernButton(
                    this@MainActivity,
                    DesignSystem.WHITE,
                    DesignSystem.SKY_SURFACE_LIGHT,
                    10,
                    DesignSystem.BORDER_SKY,
                    1
                ) as Drawable
                setTextColor(DesignSystem.SKY_PRIMARY)
            }

            setOnClickListener { action() }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                setMargins(0, dp(4), 0, dp(8))
            }
            layoutParams = params
        }
    }

    private fun createActionButton(
        label: String,
        isEnabled: Boolean,
        isCompleted: Boolean,
        action: () -> Unit
    ): View {
        return Button(this).apply {
            text = label
            this.isEnabled = isEnabled
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false

            if (isCompleted) {
                background = DesignSystem.createCardDrawable(
                    this@MainActivity,
                    DesignSystem.BTN_COMPLETED_BG,
                    DesignSystem.BORDER_LIGHT,
                    1,
                    10
                )
                setTextColor(DesignSystem.BTN_COMPLETED_TEXT)
            } else if (!isEnabled) {
                background = DesignSystem.createCardDrawable(
                    this@MainActivity,
                    DesignSystem.BTN_DISABLED_BG,
                    Color.TRANSPARENT,
                    0,
                    10
                )
                setTextColor(DesignSystem.BTN_DISABLED_TEXT)
            } else {
                background = DesignSystem.createModernButton(
                    this@MainActivity,
                    DesignSystem.SKY_PRIMARY,
                    DesignSystem.SKY_PRIMARY_DARK,
                    10
                ) as Drawable
                setTextColor(DesignSystem.WHITE)
                elevation = dp(2).toFloat()
            }

            setOnClickListener { action() }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                setMargins(0, dp(4), 0, 0)
            }
            layoutParams = params
        }
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
            setTextColor(DesignSystem.TEXT_PRIMARY)

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }

            setPadding(0, dp(6), 0, dp(6))
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
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false

            if (!enabled) {
                background = DesignSystem.createCardDrawable(
                    this@MainActivity,
                    DesignSystem.BTN_DISABLED_BG,
                    Color.TRANSPARENT,
                    0,
                    10
                )
                setTextColor(DesignSystem.BTN_DISABLED_TEXT)
            } else {
                background = DesignSystem.createModernButton(
                    this@MainActivity,
                    DesignSystem.SKY_PRIMARY,
                    DesignSystem.SKY_PRIMARY_DARK,
                    10
                ) as Drawable
                setTextColor(DesignSystem.WHITE)
                elevation = dp(2).toFloat()
            }

            setOnClickListener { action() }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(44)
        ).apply {
            setMargins(0, dp(4), 0, dp(8))
        }

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