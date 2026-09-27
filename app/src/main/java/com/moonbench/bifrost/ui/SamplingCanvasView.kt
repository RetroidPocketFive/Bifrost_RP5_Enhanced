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

class SamplingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnRegionsChangedListener {
        fun onRegionsChanged(left: SamplingRegion, right: SamplingRegion)
    }

    enum class Focus { BOTH, LEFT, RIGHT }

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

    var screenAspectRatio: Float = 16f / 9f
        set(value) {
            field = if (value > 0f) value else 16f / 9f
            computeContentRect()
            invalidate()
        }

    var focus: Focus = Focus.BOTH
        set(value) {
            field = value
            computeContentRect()
            invalidate()
        }

    private val leftColor = Color.rgb(40, 130, 255)
    private val rightColor = Color.rgb(255, 130, 30)
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isFilterBitmap = true }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        isFakeBoldText = true
    }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        isFakeBoldText = true
    }

    private val handleRadius = 24f
    private val touchSlop = 24f
    private val zoomFactor = 2.8f
    private var contentRect = RectF()
    private var visibleRegion = RectF(0f, 0f, 1f, 1f)

    private enum class DragTarget { NONE, LEFT, RIGHT }
    private enum class DragMode { MOVE, RESIZE }
    private var activeTarget = DragTarget.NONE
    private var activeMode = DragMode.MOVE
    private var activeHandle = 0
    private var lastX = 0f
    private var lastY = 0f

    fun setRegions(left: SamplingRegion, right: SamplingRegion, notify: Boolean = false) {
        leftRegion = left.clamped()
        rightRegion = right.clamped()
        computeContentRect()
        invalidate()
        if (notify) listener?.onRegionsChanged(leftRegion, rightRegion)
    }

    private fun focusedRegion(): SamplingRegion? = when (focus) {
        Focus.LEFT -> leftRegion
        Focus.RIGHT -> rightRegion
        Focus.BOTH -> null
    }

    private fun computeContentRect() {
        if (width <= 0 || height <= 0) return
        val pad = 6f
        val availW = width - 2f * pad
        val availH = height - 2f * pad
        if (availW <= 0f || availH <= 0f) return

        val scale = min(availW / screenAspectRatio, availH)
        val drawW = screenAspectRatio * scale
        val drawH = scale
        val dx = (width - drawW) / 2f
        val dy = (height - drawH) / 2f
        contentRect = RectF(dx, dy, dx + drawW, dy + drawH)

        visibleRegion = when (val r = focusedRegion()) {
            null -> RectF(0f, 0f, 1f, 1f)
            else -> {
                val desiredW = max(
                    r.width * zoomFactor,
                    r.height * zoomFactor * screenAspectRatio
                ).coerceIn(0.30f, 1f)
                val desiredH = (desiredW / screenAspectRatio).coerceIn(0.30f, 1f)
                val cx = (r.left + r.right) / 2f
                val cy = (r.top + r.bottom) / 2f
                val left = (cx - desiredW / 2f).coerceIn(0f, 1f - desiredW)
                val top = (cy - desiredH / 2f).coerceIn(0f, 1f - desiredH)
                RectF(left, top, left + desiredW, top + desiredH)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (contentRect.width() <= 0f) computeContentRect()

        canvas.drawColor(Color.rgb(10, 10, 10))

        backgroundBitmap?.let { bmp ->
            if (bmp.width > 0 && bmp.height > 0) {
                val src = Rect(
                    (visibleRegion.left * bmp.width).toInt().coerceIn(0, bmp.width - 1),
                    (visibleRegion.top * bmp.height).toInt().coerceIn(0, bmp.height - 1),
                    (visibleRegion.right * bmp.width).toInt().coerceIn(1, bmp.width),
                    (visibleRegion.bottom * bmp.height).toInt().coerceIn(1, bmp.height)
                )
                if (src.right > src.left && src.bottom > src.top) {
                    bgPaint.alpha = 255
                    canvas.drawBitmap(bmp, src, contentRect, bgPaint)
                }
            }
        }

        if (focus == Focus.BOTH) {
            drawRegion(canvas, leftRegion, leftColor, "L", 255)
            drawRegion(canvas, rightRegion, rightColor, "R", 255)
        } else {
            val active = if (focus == Focus.LEFT) leftRegion else rightRegion
            val inactive = if (focus == Focus.LEFT) rightRegion else leftRegion
            val activeColor = if (focus == Focus.LEFT) leftColor else rightColor
            val inactiveColor = if (focus == Focus.LEFT) rightColor else leftColor
            drawRegion(canvas, inactive, inactiveColor, "", 80)
            drawRegion(canvas, active, activeColor, if (focus == Focus.LEFT) "LEFT" else "RIGHT", 255)
            val label = if (focus == Focus.LEFT) "LEFT STICK — zoomed editor" else "RIGHT STICK — zoomed editor"
            canvas.drawText(label, 18f, 32f, infoPaint)
        }
    }

    private fun drawRegion(
        canvas: Canvas,
        region: SamplingRegion,
        color: Int,
        label: String,
        alpha: Int
    ) {
        val r = regionToView(region)
        fillPaint.color = Color.argb(alpha * 40 / 255, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRect(r, fillPaint)

        rectPaint.strokeWidth = if (label.isBlank()) 3f else 6f
        rectPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRect(r, rectPaint)

        if (label.isNotBlank()) {
            val corners = listOf(
                r.left to r.top, r.right to r.top,
                r.left to r.bottom, r.right to r.bottom
            )
            corners.forEach { (cx, cy) ->
                handlePaint.color = Color.WHITE
                canvas.drawCircle(cx, cy, handleRadius, handlePaint)
                handlePaint.color = color
                canvas.drawCircle(cx, cy, handleRadius - 7f, handlePaint)
            }
            labelPaint.color = color
            canvas.drawText(label, r.left + 12f, r.top + 34f, labelPaint)
        }
    }

    private fun regionToView(region: SamplingRegion): RectF {
        val l = (region.left - visibleRegion.left) / visibleRegion.width()
        val r = (region.right - visibleRegion.left) / visibleRegion.width()
        val t = (region.top - visibleRegion.top) / visibleRegion.height()
        val b = (region.bottom - visibleRegion.top) / visibleRegion.height()
        return RectF(
            contentRect.left + l * contentRect.width(),
            contentRect.top + t * contentRect.height(),
            contentRect.left + r * contentRect.width(),
            contentRect.top + b * contentRect.height()
        )
    }

    private fun viewToNormalized(x: Float, y: Float): Pair<Float, Float> {
        val nx = (visibleRegion.left + ((x - contentRect.left) / contentRect.width()) * visibleRegion.width()).coerceIn(0f, 1f)
        val ny = (visibleRegion.top + ((y - contentRect.top) / contentRect.height()) * visibleRegion.height()).coerceIn(0f, 1f)
        return nx to ny
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (contentRect.width() <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitTest(event.x, event.y)
                if (hit.first == DragTarget.NONE) return false
                activeTarget = hit.first
                activeMode = hit.second
                activeHandle = hit.third
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val (lastNx, lastNy) = viewToNormalized(lastX, lastY)
                val (nowNx, nowNy) = viewToNormalized(event.x, event.y)
                lastX = event.x
                lastY = event.y
                applyDrag(nowNx - lastNx, nowNy - lastNy)
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                activeTarget = DragTarget.NONE
                return true
            }
        }
        return false
    }

    private fun hitTest(x: Float, y: Float): Triple<DragTarget, DragMode, Int> {
        if (x < contentRect.left - touchSlop || x > contentRect.right + touchSlop ||
            y < contentRect.top - touchSlop || y > contentRect.bottom + touchSlop
        ) return Triple(DragTarget.NONE, DragMode.MOVE, 0)

        val targets = when (focus) {
            Focus.LEFT -> listOf(DragTarget.LEFT)
            Focus.RIGHT -> listOf(DragTarget.RIGHT)
            Focus.BOTH -> listOf(DragTarget.RIGHT, DragTarget.LEFT)
        }

        for (target in targets) {
            val region = if (target == DragTarget.LEFT) leftRegion else rightRegion
            val r = regionToView(region)
            val corners = listOf(
                r.left to r.top, r.right to r.top,
                r.left to r.bottom, r.right to r.bottom
            )
            corners.forEachIndexed { idx, (cx, cy) ->
                if (abs(x - cx) <= handleRadius + touchSlop &&
                    abs(y - cy) <= handleRadius + touchSlop
                ) return Triple(target, DragMode.RESIZE, idx)
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

        computeContentRect()
        invalidate()
        listener?.onRegionsChanged(leftRegion, rightRegion)
    }

    private fun resizeRegion(r: SamplingRegion, handle: Int, dx: Float, dy: Float): SamplingRegion {
        var l = r.left
        var t = r.top
        var rr = r.right
        var b = r.bottom
        when (handle) {
            0 -> {
                l = (l + dx).coerceIn(0f, rr - SamplingRegion.MIN_SIDE)
                t = (t + dy).coerceIn(0f, b - SamplingRegion.MIN_SIDE)
            }
            1 -> {
                rr = (rr + dx).coerceIn(l + SamplingRegion.MIN_SIDE, 1f)
                t = (t + dy).coerceIn(0f, b - SamplingRegion.MIN_SIDE)
            }
            2 -> {
                l = (l + dx).coerceIn(0f, rr - SamplingRegion.MIN_SIDE)
                b = (b + dy).coerceIn(t + SamplingRegion.MIN_SIDE, 1f)
            }
            3 -> {
                rr = (rr + dx).coerceIn(l + SamplingRegion.MIN_SIDE, 1f)
                b = (b + dy).coerceIn(t + SamplingRegion.MIN_SIDE, 1f)
            }
        }
        return SamplingRegion(l, t, rr, b)
    }
}
