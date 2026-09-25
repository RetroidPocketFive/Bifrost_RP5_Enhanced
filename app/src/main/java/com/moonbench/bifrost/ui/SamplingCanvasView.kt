package com.moonbench.bifrost.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.moonbench.bifrost.tools.SamplingRegion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * An interactive canvas representing the device screen, letting the user
 * position and resize two sampling rectangles (left stick, right stick).
 *
 * The canvas draws a content rect whose aspect ratio matches the real screen
 * (passed in as screenWidth/screenHeight), centered in the view. Both the
 * optional screenshot background and the rectangles are drawn relative to that
 * content rect, so a rectangle at fractions (0.2, 0.1, 0.4, 0.6) covers the
 * same screen area whether or not a screenshot is present. This keeps the
 * rectangle positions consistent with what ScreenAnalyzer samples.
 *
 * Each rectangle has four corner handles for resizing; dragging inside moves it.
 * Regions are clamped to a minimum side and kept inside the screen bounds.
 */
class SamplingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnRegionsChangedListener {
        fun onRegionsChanged(left: SamplingRegion, right: SamplingRegion)
    }

    var leftRegion: SamplingRegion = SamplingRegion.LEFT_HALF
        private set
    var rightRegion: SamplingRegion = SamplingRegion.RIGHT_HALF
        private set

    var backgroundBitmap: Bitmap? = null
        set(value) {
            field = value
            computeContentRect()
            invalidate()
        }

    var listener: OnRegionsChangedListener? = null

    // Screen aspect (w/h) the content rect should match. Defaults to 16:9-ish.
    var screenAspectRatio: Float = 16f / 9f
        set(value) {
            field = if (value > 0f) value else 16f / 9f
            computeContentRect()
            invalidate()
        }

    // Colors: left = blue, right = orange.
    private val leftColor = Color.rgb(40, 130, 255)
    private val rightColor = Color.rgb(255, 130, 30)

    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isFilterBitmap = true }
    private val scrimPaint = Paint().apply { color = Color.argb(120, 0, 0, 0) }
    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 32f; isFakeBoldText = true
    }

    private val handleRadius = 26f
    private val touchSlop = 24f

    private var contentRect = RectF(0f, 0f, 0f, 0f)

    fun setRegions(left: SamplingRegion, right: SamplingRegion, notify: Boolean = false) {
        leftRegion = left.clamped()
        rightRegion = right.clamped()
        invalidate()
        if (notify) listener?.onRegionsChanged(leftRegion, rightRegion)
    }

    private enum class DragTarget { NONE, LEFT, RIGHT }
    private enum class DragMode { MOVE, RESIZE }
    private var activeTarget = DragTarget.NONE
    private var activeMode = DragMode.MOVE
    private var activeHandle = 0
    private var lastX = 0f
    private var lastY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeContentRect()
    }

    private fun computeContentRect() {
        if (width == 0 || height == 0) return
        val pad = 8f
        val availW = width - 2 * pad
        val availH = height - 2 * pad
        if (availW <= 0 || availH <= 0) return
        val scale = min(availW / screenAspectRatio, availH) // fit the aspect box
        val drawW = screenAspectRatio * scale
        val drawH = scale
        val dx = (width - drawW) / 2f
        val dy = (height - drawH) / 2f
        contentRect = RectF(dx, dy, dx + drawW, dy + drawH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        if (contentRect.width() <= 0f) computeContentRect()
        if (contentRect.width() <= 0f) return

        // Dark border around the screen area.
        scrimPaint.color = Color.argb(255, 12, 12, 12)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // Background screenshot, fitted to content rect.
        backgroundBitmap?.let { bmp ->
            if (bmp.width > 0 && bmp.height > 0) {
                bgPaint.alpha = 255
                canvas.drawBitmap(bmp, null, contentRect, bgPaint)
            }
        }

        drawRegion(canvas, leftRegion, leftColor, "L")
        drawRegion(canvas, rightRegion, rightColor, "R")
    }

    private fun drawRegion(canvas: Canvas, region: SamplingRegion, color: Int, label: String) {
        val r = regionToView(region)
        fillPaint.color = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRect(r, fillPaint)
        rectPaint.color = color
        canvas.drawRect(r, rectPaint)

        val corners = listOf(
            r.left to r.top, r.right to r.top,
            r.left to r.bottom, r.right to r.bottom
        )
        corners.forEach { (cx, cy) ->
            handlePaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, handleRadius, handlePaint)
            handlePaint.color = color
            canvas.drawCircle(cx, cy, handleRadius - 8f, handlePaint)
        }

        labelPaint.color = color
        canvas.drawText(label, r.left + 14f, r.top + 38f, labelPaint)
    }

    /** Map a 0..1 fraction region to view coordinates inside the content rect. */
    private fun regionToView(region: SamplingRegion): RectF {
        val l = contentRect.left + region.left * contentRect.width()
        val r = contentRect.left + region.right * contentRect.width()
        val t = contentRect.top + region.top * contentRect.height()
        val b = contentRect.top + region.bottom * contentRect.height()
        return RectF(l, t, max(r, l + 1), max(b, t + 1))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (contentRect.width() <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val (target, mode, handle) = hitTest(event.x, event.y)
                if (target == DragTarget.NONE) return false
                activeTarget = target
                activeMode = mode
                activeHandle = handle
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - lastX) / contentRect.width()
                val dy = (event.y - lastY) / contentRect.height()
                lastX = event.x
                lastY = event.y
                if (dx == 0f && dy == 0f) return true
                applyDrag(dx, dy)
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                activeTarget = DragTarget.NONE
            }
        }
        return false
    }

    private fun hitTest(x: Float, y: Float): Triple<DragTarget, DragMode, Int> {
        // Ignore touches outside the screen content rect.
        if (x < contentRect.left - touchSlop || x > contentRect.right + touchSlop ||
            y < contentRect.top - touchSlop || y > contentRect.bottom + touchSlop
        ) {
            return Triple(DragTarget.NONE, DragMode.MOVE, 0)
        }
        // Right first so an overlapping region on top is preferred.
        for (target in listOf(DragTarget.RIGHT, DragTarget.LEFT)) {
            val region = if (target == DragTarget.LEFT) leftRegion else rightRegion
            val r = regionToView(region)
            val corners = listOf(
                r.left to r.top, r.right to r.top,
                r.left to r.bottom, r.right to r.bottom
            )
            corners.forEachIndexed { idx, (cx, cy) ->
                if (abs(x - cx) <= handleRadius + touchSlop && abs(y - cy) <= handleRadius + touchSlop) {
                    return Triple(target, DragMode.RESIZE, idx)
                }
            }
            if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) {
                return Triple(target, DragMode.MOVE, 0)
            }
        }
        return Triple(DragTarget.NONE, DragMode.MOVE, 0)
    }

    private fun applyDrag(dx: Float, dy: Float) {
        if (activeTarget == DragTarget.NONE) return
        val source = if (activeTarget == DragTarget.LEFT) leftRegion else rightRegion
        val updated = if (activeMode == DragMode.MOVE) {
            val spanX = source.right - source.left
            val spanY = source.bottom - source.top
            val nl = (source.left + dx).coerceIn(0f, 1f - spanX)
            val nt = (source.top + dy).coerceIn(0f, 1f - spanY)
            SamplingRegion(nl, nt, nl + spanX, nt + spanY)
        } else {
            resizeRegion(source, activeHandle, dx, dy)
        }
        if (activeTarget == DragTarget.LEFT) leftRegion = updated.clamped()
        else rightRegion = updated.clamped()
        invalidate()
        listener?.onRegionsChanged(leftRegion, rightRegion)
    }

    private fun resizeRegion(r: SamplingRegion, handle: Int, dx: Float, dy: Float): SamplingRegion {
        var l = r.left; var t = r.top; var rr = r.right; var b = r.bottom
        when (handle) {
            0 -> { l = (l + dx).coerceIn(0f, rr - SamplingRegion.MIN_SIDE); t = (t + dy).coerceIn(0f, b - SamplingRegion.MIN_SIDE) }
            1 -> { rr = (rr + dx).coerceIn(l + SamplingRegion.MIN_SIDE, 1f); t = (t + dy).coerceIn(0f, b - SamplingRegion.MIN_SIDE) }
            2 -> { l = (l + dx).coerceIn(0f, rr - SamplingRegion.MIN_SIDE); b = (b + dy).coerceIn(t + SamplingRegion.MIN_SIDE, 1f) }
            3 -> { rr = (rr + dx).coerceIn(l + SamplingRegion.MIN_SIDE, 1f); b = (b + dy).coerceIn(t + SamplingRegion.MIN_SIDE, 1f) }
        }
        return SamplingRegion(l, t, rr, b)
    }
}