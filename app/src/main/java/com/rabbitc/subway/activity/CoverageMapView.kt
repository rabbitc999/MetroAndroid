package com.rabbitc.subway.activity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.google.android.material.color.MaterialColors
import kotlin.math.cos
import kotlin.math.min

/** Geographic preview of measured track and station check-ins. */
class CoverageMapView(
    context: Context,
    private val coverage: MetroV2ViewModel.Coverage,
    private val checked: Set<String>,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val density = resources.displayMetrics.density
    private val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)

    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val updated = (zoom * detector.scaleFactor).coerceIn(1f, 12f)
            val factor = updated / zoom
            panX = detector.focusX - (detector.focusX - panX) * factor
            panY = detector.focusY - (detector.focusY - panY) * factor
            zoom = updated
            if (zoom == 1f) { panX = 0f; panY = 0f }
            invalidate()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1
            && !scaleDetector.isInProgress && zoom > 1f) {
            panX += event.x - lastX
            panY += event.y - lastY
            invalidate()
        }
        lastX = event.x
        lastY = event.y
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    init {
        contentDescription = "实测区间与打卡站点示意图"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(surface)
        val points = coverage.points
        if (points.isEmpty()) return
        val minLon = points.values.minOf { it.first }
        val minLat = points.values.minOf { it.second }
        val maxLat = points.values.maxOf { it.second }
        val correction = cos(Math.toRadians((minLat + maxLat) / 2))
        val dx = ((points.values.maxOf { it.first } - minLon) * correction).coerceAtLeast(1e-6)
        val dy = (maxLat - minLat).coerceAtLeast(1e-6)
        val padding = 12 * density
        val scale = min((width - padding * 2) / dx, (height - padding * 2) / dy)
        if (scale <= 0) return
        val left = (width - dx * scale) / 2
        val top = (height - dy * scale) / 2
        val pixels = points.mapValues { (_, point) ->
            ((left + (point.first - minLon) * correction * scale) * zoom + panX).toFloat() to
                ((top + (maxLat - point.second) * scale) * zoom + panY).toFloat()
        }
        for (edge in coverage.edges.sortedBy { it.samples }) {
            val a = pixels[edge.start] ?: continue
            val b = pixels[edge.end] ?: continue
            paint.color = when (edge.samples) {
                0 -> Color.GRAY
                1 -> Color.rgb(30, 125, 247)
                else -> Color.rgb(149, 73, 225)
            }
            paint.alpha = if (edge.samples == 0) 90 else 255
            paint.strokeWidth = (if (edge.samples == 0) 1.2f else 3.5f) * density
            canvas.drawLine(a.first, a.second, b.first, b.second, paint)
        }
        paint.color = Color.rgb(255, 139, 43)
        paint.alpha = 255
        for (name in checked) {
            val point = pixels[name] ?: continue
            canvas.drawCircle(point.first, point.second, 3.5f * density, paint)
        }
    }
}
