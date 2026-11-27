package com.schooltransport.driver

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = TextView(this).apply {
            text = """
                School Transport Driver

                Phase 3 - Android App

                Project setup successful.
            """.trimIndent()

            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        setContentView(message)
    }
}
