package com.moonbench.bifrost.rp5

import android.content.Context
import android.graphics.*
import android.view.*

class LedCalibrationView(context: Context) : View(context) {
    var left = NormalizedRegion(.25f, .78f, .18f)
    var right = NormalizedRegion(.75f, .78f, .18f)
    private var frame: Bitmap? = null
    private var leftColor = Color.BLACK
    private var rightColor = Color.BLACK
    private var active = 0
    private var lastX = 0f
    private var lastY = 0f
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var onRegionChanged: ((NormalizedRegion, NormalizedRegion) -> Unit)? = null

    fun setFrame(bitmap: Bitmap?) {
        frame = bitmap
        invalidate()
    }

    fun setColors(left: Int, right: Int) {
        leftColor = left and 0xFFFFFF
        rightColor = right and 0xFFFFFF
        invalidate()
    }

    fun setRegions(left: NormalizedRegion, right: NormalizedRegion) {
        this.left = left
        this.right = right
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        frame?.let { bitmap ->
            if (!bitmap.isRecycled) {
                val scale = minOf(width.toFloat() / bitmap.width.coerceAtLeast(1), height.toFloat() / bitmap.height.coerceAtLeast(1))
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                val dst = RectF((width - drawWidth) / 2f, (height - drawHeight) / 2f, (width + drawWidth) / 2f, (height + drawHeight) / 2f)
                canvas.drawBitmap(bitmap, null, dst, imagePaint)
            }
        }
        drawRegion(canvas, left, Color.rgb(50, 130, 255), leftColor, "LEFT")
        drawRegion(canvas, right, Color.rgb(255, 145, 40), rightColor, "RIGHT")
    }

    private fun drawRegion(canvas: Canvas, region: NormalizedRegion, border: Int, color: Int, label: String) {
        val b = region.bounds()
        val rect = RectF(b.left * width, b.top * height, b.right * width, b.bottom * height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (color and 0xFFFFFF) or 0x66000000 }
        canvas.drawRect(rect, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = border
        canvas.drawRect(rect, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 24f
        canvas.drawText(label, rect.left + 8f, rect.top + 28f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x / width.coerceAtLeast(1)
        val y = event.y / height.coerceAtLeast(1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = when {
                    contains(left, x, y) -> 1
                    contains(right, x, y) -> 2
                    else -> 0
                }
                lastX = x
                lastY = y
                return active != 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (active == 0) return false
                val dx = x - lastX
                val dy = y - lastY
                if (active == 1) left = move(left, dx, dy) else right = move(right, dx, dy)
                lastX = x
                lastY = y
                onRegionChanged?.invoke(left, right)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = 0
                return true
            }
        }
        return true
    }

    private fun contains(region: NormalizedRegion, x: Float, y: Float): Boolean {
        val b = region.bounds()
        return x in b.left..b.right && y in b.top..b.bottom
    }

    private fun move(region: NormalizedRegion, dx: Float, dy: Float): NormalizedRegion {
        val half = region.size / 2f
        return region.copy(
            centerX = (region.centerX + dx).coerceIn(half, 1f - half),
            centerY = (region.centerY + dy).coerceIn(half, 1f - half),
        )
    }
}
