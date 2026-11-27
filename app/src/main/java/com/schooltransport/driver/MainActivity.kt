package com.schooltransport.driver

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private enum class TripMode {
        MORNING_PICKUP,
        AFTERNOON_DROPOFF
    }

    private data class DemoStudent(
        val name: String,
        var pickedUp: Boolean = false,
        var droppedOff: Boolean = false
    )

    private var tripMode = TripMode.MORNING_PICKUP

    // Fictional students for interface testing only.
    private val students = mutableListOf(
        DemoStudent("Demo Student 01"),
        DemoStudent("Demo Student 02"),
        DemoStudent("Demo Student 03")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderScreen()
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
            tripMode == TripMode.MORNING_PICKUP
        ) {
            tripMode = TripMode.MORNING_PICKUP
            renderScreen()
        }

        addButton(
            content,
            "Afternoon: Student Drop-off",
            tripMode == TripMode.AFTERNOON_DROPOFF
        ) {
            tripMode = TripMode.AFTERNOON_DROPOFF
            renderScreen()
        }

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

        addText(content, "Student List", 22f, true)

        students.forEach { student ->
            addText(content, student.name, 18f, true)

            val status = when {
                student.droppedOff -> "Dropped off"
                student.pickedUp -> "Picked up — on bus"
                else -> "Waiting for pickup"
            }

            addText(content, "Status: $status", 15f, false)

            if (tripMode == TripMode.MORNING_PICKUP) {
                addButton(
                    content,
                    "Confirm Pickup",
                    !student.pickedUp
                ) {
                    student.pickedUp = true
                    Toast.makeText(
                        this,
                        "Demo pickup recorded locally",
                        Toast.LENGTH_SHORT
                    ).show()
                    renderScreen()
                }
            } else {
                addButton(
                    content,
                    "Confirm Drop-off",
                    student.pickedUp && !student.droppedOff
                ) {
                    student.droppedOff = true
                    Toast.makeText(
                        this,
                        "Demo drop-off recorded locally",
                        Toast.LENGTH_SHORT
                    ).show()
                    renderScreen()
                }
            }
        }

        addText(
            content,
            "These demo actions are not yet saved permanently, GPS-verified, or sent to parents.",
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
}
