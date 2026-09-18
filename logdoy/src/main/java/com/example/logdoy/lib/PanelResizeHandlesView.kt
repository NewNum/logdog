package com.example.logdoy.lib

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws four corner resize grips on the expanded panel:
 * bottom-left: up + right 40dp; bottom-right: up + left 40dp.
 * Stroke matches the panel border width but uses a brighter color.
 */
internal class PanelResizeHandlesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val strokePx = 10f * density
    private val handleLenPx = 40f * density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.SQUARE
        // Brighter than panel border (#D0D0D0).
        color = 0xFFFFFFFF.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = strokePx / 2f
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val left = inset
        val right = w - inset
        val bottom = h - inset
        val len = handleLenPx.coerceAtMost(minOf(w, h) / 2f)

        // Bottom-left: up and right.
        canvas.drawLine(left, bottom, left, bottom - len, paint)
        canvas.drawLine(left, bottom, left + len, bottom, paint)

        // Bottom-right: up and left.
        canvas.drawLine(right, bottom, right, bottom - len, paint)
        canvas.drawLine(right, bottom, right - len, bottom, paint)
    }
}
