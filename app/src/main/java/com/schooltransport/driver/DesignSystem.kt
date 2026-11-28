package com.schooltransport.driver

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build

/**
 * Design System for School Transport Driver
 * Theme: Sky Blue & White (Modern, Clean, Professional)
 */
object DesignSystem {

    // --- Core Brand Colors ---
    val SKY_PRIMARY = Color.parseColor("#0288D1")        // Rich Sky Blue (Primary)
    val SKY_PRIMARY_DARK = Color.parseColor("#01579B")   // Deep Navy Sky Blue (Hero/Header)
    val SKY_PRIMARY_LIGHT = Color.parseColor("#03A9F4")  // Bright Sky Blue (Active States)
    val SKY_SURFACE_LIGHT = Color.parseColor("#E1F5FE")  // Light Sky Tint (Borders/Accents)
    val SKY_TINT_BG = Color.parseColor("#F0F7FC")        // Soft Sky-white Canvas Background

    // --- Neutral Surface & Text Colors ---
    val WHITE = Color.parseColor("#FFFFFF")              // Pure White (Cards/Surfaces)
    val TEXT_PRIMARY = Color.parseColor("#0F2438")       // Deep Slate Navy (High Contrast)
    val TEXT_SECONDARY = Color.parseColor("#5A6E82")     // Muted Slate (Descriptions)
    val TEXT_TERTIARY = Color.parseColor("#8CA0B3")      // Subtle Muted (Captions/IDs)
    val BORDER_LIGHT = Color.parseColor("#E2E8F0")       // Crisp Card Border
    val BORDER_SKY = Color.parseColor("#BEE3F8")         // Active Sky Border

    // --- Status Badge Colors ---
    // Waiting Status
    val STATUS_WAITING_BG = Color.parseColor("#FFFBEB")
    val STATUS_WAITING_TEXT = Color.parseColor("#B45309")
    val STATUS_WAITING_BORDER = Color.parseColor("#FDE68A")

    // On-Bus / Picked Up Status
    val STATUS_PICKUP_BG = Color.parseColor("#ECFDF5")
    val STATUS_PICKUP_TEXT = Color.parseColor("#047857")
    val STATUS_PICKUP_BORDER = Color.parseColor("#A7F3D0")

    // Dropped Off Status
    val STATUS_DROPOFF_BG = Color.parseColor("#E0F2FE")
    val STATUS_DROPOFF_TEXT = Color.parseColor("#0284C7")
    val STATUS_DROPOFF_BORDER = Color.parseColor("#BAE6FD")

    // Sync Badge
    val SYNC_PENDING_BG = Color.parseColor("#FEF3C7")
    val SYNC_PENDING_TEXT = Color.parseColor("#92400E")
    val SYNC_PENDING_BORDER = Color.parseColor("#FCD34D")

    val SYNC_OK_BG = Color.parseColor("#F0FDF4")
    val SYNC_OK_TEXT = Color.parseColor("#15803D")
    val SYNC_OK_BORDER = Color.parseColor("#BBF7D0")

    // --- Button States ---
    val BTN_DISABLED_BG = Color.parseColor("#E2E8F0")
    val BTN_DISABLED_TEXT = Color.parseColor("#94A3B8")
    val BTN_COMPLETED_BG = Color.parseColor("#F1F5F9")
    val BTN_COMPLETED_TEXT = Color.parseColor("#64748B")

    /**
     * Creates a rounded card background drawable.
     */
    fun createCardDrawable(
        context: Context,
        bgColor: Int = WHITE,
        borderColor: Int = BORDER_LIGHT,
        borderWidthDp: Int = 1,
        cornerRadiusDp: Int = 16
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = cornerRadiusDp * density
            if (borderWidthDp > 0) {
                setStroke((borderWidthDp * density).toInt(), borderColor)
            }
        }
    }

    /**
     * Creates a gradient header background drawable.
     */
    fun createGradientHeader(
        context: Context,
        startColor: Int = SKY_PRIMARY_DARK,
        endColor: Int = SKY_PRIMARY,
        cornerRadiusDp: Int = 16
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(startColor, endColor)
        ).apply {
            cornerRadius = cornerRadiusDp * density
        }
    }

    /**
     * Creates a rounded pill badge drawable.
     */
    fun createPillBadge(
        context: Context,
        bgColor: Int,
        borderColor: Int = Color.TRANSPARENT,
        borderWidthDp: Int = 1
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = 999f * density
            if (borderColor != Color.TRANSPARENT && borderWidthDp > 0) {
                setStroke((borderWidthDp * density).toInt(), borderColor)
            }
        }
    }

    /**
     * Creates an avatar circle drawable.
     */
    fun createCircleDrawable(
        context: Context,
        bgColor: Int,
        borderColor: Int = Color.TRANSPARENT,
        borderWidthDp: Int = 0
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
            if (borderWidthDp > 0 && borderColor != Color.TRANSPARENT) {
                setStroke((borderWidthDp * density).toInt(), borderColor)
            }
        }
    }

    /**
     * Creates a modern responsive button drawable with state feedback.
     */
    fun createModernButton(
        context: Context,
        normalColor: Int,
        pressedColor: Int,
        cornerRadiusDp: Int = 12,
        borderColor: Int = Color.TRANSPARENT,
        borderWidthDp: Int = 0
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val radius = cornerRadiusDp * density

        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(normalColor)
            cornerRadius = radius
            if (borderWidthDp > 0 && borderColor != Color.TRANSPARENT) {
                setStroke((borderWidthDp * density).toInt(), borderColor)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = radius
            }
            return RippleDrawable(
                ColorStateList.valueOf(pressedColor),
                normalDrawable,
                mask
            )
        } else {
            return StateListDrawable().apply {
                val pressedDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(pressedColor)
                    cornerRadius = radius
                }
                addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
                addState(intArrayOf(), normalDrawable)
            }
        }
    }
}
