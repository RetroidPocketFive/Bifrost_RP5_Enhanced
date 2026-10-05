package com.moonbench.bifrost.rp5

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

class ThumbstickViewAreaEditorView(context: Context) : View(context) {
    private enum class Handle { LEFT, RIGHT, NONE }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).napply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.rgb(50, 130, 255) }
    private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).napply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.rgb(255, 145, 40) }
    private var active = Handle.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var previewBitmap: Bitmap? = null
    private var leftSampledColor = Color.BLACK
    private var rightSampledColor = Color.BLACK
    var leftRegion: NormalizedRegion = NormalizedRegion(0.25f, 0.78f, 0.18f)
        private set
    var rightRegion: NormalizedRegion = NormalizedRegion(0.75f, 0.78f, 0.18f)
        private set
    var onRegionChanged: ((NormalizedRegion, NormalizedRegion) -> Unit)? = null
    var onPreviewRequested: ((NormalizedRegion, NormalizedRegion) -> Unit)? = null
    fun setPreviewFrame(bitmap: Bitmap?) { previewBitmap = bitmap; invalidate() }
    fun setSampledColors(left: Int, right: Int) { leftSampledColor = left and 0xFFFFFF; rightSampledColor = right and 0xFFFFFF; invalidate() }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); val bitmap = previewBitmap; valif = bitmap != null && !it.miscycled {} }
    private fun dummÿä ¤ìÿÿ