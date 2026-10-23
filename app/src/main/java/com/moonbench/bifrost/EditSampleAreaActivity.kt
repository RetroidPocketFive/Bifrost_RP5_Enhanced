package com.moonbench.bifrost

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.services.BifrostAccessibilityService
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.tools.SamplingRegion
import com.moonbench.bifrost.ui.SamplingCanvasView
import java.util.concurrent.Executor

/** Standalone profile editor. Intentionally does not read or write calibration SamplingRegionStore. */
class EditSampleAreaActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SOURCE = "edit_sample_area.source"
        const val SOURCE_LIVE = "live"
        const val SOURCE_STILL = "still"
        const val SOURCE_COLOR_WHEEL = "color_wheel"
        private const val PREFS = "bifrost_edit_sample_area_profiles"
        private const val KEY_NAMES = "profile_names"
        private const val KEY_ACTIVE = "active_profile"
        private val MODES = listOf("50/50 (Left/Right)", "Average colour", "Close match colour", "Dominant colour", "Dominant prime color")
    }

    private val bg = Color.rgb(8, 12, 24)
    private val panel = Color.rgb(16, 22, 41)
    private val surface = Color.rgb(21, 29, 51)
    private val accent = Color.rgb(104, 101, 242)
    private val primaryText = Color.rgb(242, 244, 255)
    private val secondaryText = Color.rgb(137, 146, 173)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var canvas: SamplingCanvasView
    private lateinit var modeSpinner: Spinner
    private var bitmap: Bitmap? = null
    private var capturePending = false
    private var captureStatus: TextView? = null
    private var activeProfile = ""
    private val restoreBoxColors = Runnable {
        if (::canvas.isInitialized && !isFinishing && !isDestroyed) canvas.restoreDefaultRegionColors()
    }
    private val boxColorResetDelayMs = 5_000L

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadStill(uri) else if (bitmap == null) {
            if (intent.hasExtra(EXTRA_SOURCE)) finish() else showSourceChooser()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        migrateLegacyProfiles()
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        when (intent.getStringExtra(EXTRA_SOURCE)) {
            SOURCE_LIVE -> { showCaptureWaiting(); startLiveCapture() }
            SOURCE_STILL -> { showCaptureWaiting(); imagePicker.launch("image/*") }
            SOURCE_COLOR_WHEEL -> {
                bitmap = createColorWheelBitmap(720)
                showEditor()
            }
            else -> showSourceChooser()
        }
    }

    private fun showCaptureWaiting() {
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
            addView(TextView(this@EditSampleAreaActivity).apply {
                text = "EDIT SAMPLE AREA"; textSize = 26f; setTextColor(primaryText); gravity = Gravity.CENTER
            })
            addView(TextView(this@EditSampleAreaActivity).apply {
                text = "Preparing image…"; textSize = 14f; setTextColor(secondaryText); gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
                captureStatus = this
            })
        })
    }

    private fun showSourceChooser() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "EDIT SAMPLE AREA"
            textSize = 28f; setTextColor(primaryText); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        root.addView(TextView(this).apply {
            text = "Choose a live game screen or a still image to configure a saved ambient viewing area."
            textSize = 14f; setTextColor(secondaryText); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(28) })
        addButton(root, "LIVE IMAGE", "Capture the current screen after a short countdown") {
            setContentView(root); startLiveCapture()
        }
        addButton(root, "STILL IMAGE", "Choose a saved screenshot or image") { imagePicker.launch("image/*") }
        addButton(root, "EXIT", "Return to the previous menu") { finish() }
        setContentView(root)
    }

    private fun showEditor() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(bg)
        }
        canvas = SamplingCanvasView(this).apply {
            focus = SamplingCanvasView.Focus.BOTH
            screenAspectRatio = bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 16f / 9f
            backgroundBitmap = bitmap
            setRegions(SamplingRegion.RP5_LEFT_DEFAULT, SamplingRegion.RP5_RIGHT_DEFAULT)
            listener = object : SamplingCanvasView.OnRegionsChangedListener {
                private var previousLeft = SamplingRegion.RP5_LEFT_DEFAULT
                private var previousRight = SamplingRegion.RP5_RIGHT_DEFAULT

                override fun onRegionsChanged(left: SamplingRegion, right: SamplingRegion) {
                    // Only the box being adjusted gets temporary sampled-colour feedback.
                    val leftChanged = left != previousLeft
                    val rightChanged = right != previousRight
                    previousLeft = left
                    previousRight = right
                    refreshLedColors(feedbackLeft = leftChanged, feedbackRight = rightChanged)
                }
            }
        }
        root.addView(canvas, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "Box Viewing Area"
            textSize = 13f
            setTextColor(secondaryText)
            setPadding(dp(4), dp(4), 0, dp(2))
        }, LinearLayout.LayoutParams(-1, -2))
        modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@EditSampleAreaActivity, android.R.layout.simple_spinner_dropdown_item, MODES)
            setSelection(0)
            setBackgroundColor(surface)
        }
        root.addView(modeSpinner, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var initialSelection = true
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (initialSelection) {
                    initialSelection = false
                    showDefaultLedColors()
                } else {
                    refreshLedColors()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; weightSum = 4f }
        listOf("SAVE", "EDIT", "RESET", "EXIT").forEach { label ->
            val button = MaterialButton(this).apply {
                text = label; textSize = 12f; setTextColor(primaryText); insetTop = 0; insetBottom = 0
                setBackgroundColor(accent); minWidth = 0; setPadding(dp(2), 0, dp(2), 0)
                setOnClickListener {
                    when (label) {
                        "SAVE" -> promptSave()
                        "EDIT" -> chooseProfileToEdit()
                        "RESET" -> resetEditor()
                        "EXIT" -> { clearLeds(); finish() }
                    }
                }
            }
            actions.addView(button, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        root.addView(actions)
        setContentView(root)
        // Start with the stick identity colors; sample colors take over after user adjustment.
        canvas.post { showDefaultLedColors() }
    }

    private fun promptSave() {
        val input = EditText(this).apply { hint = "Profile name"; setText(activeProfile) }
        AlertDialog.Builder(this).setTitle("Save Edit Sample Area")
            .setMessage("Save the viewing area as a named profile.")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { Toast.makeText(this, "Enter a profile name.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (!saveProfile(name)) {
                    Toast.makeText(this, "Could not save sample-area profile. Check the name and profile limit.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                activeProfile = name
                val colors = sampleColors()
                runLedTest(colors.first, colors.second, 1200L)
                Toast.makeText(this, "Saved sample-area profile: $name", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun saveProfile(name: String): Boolean {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val names = (prefs.getStringSet(KEY_NAMES, emptySet()) ?: emptySet()).toMutableSet()
        names.add(name)
        val saved = SampleAreaProfileStore.save(this, name, canvas.leftRegion, canvas.rightRegion)
        if (!saved) return false
        prefs.edit().putStringSet(KEY_NAMES, names).putString(KEY_ACTIVE, name)
            .putString("$name.left", canvas.leftRegion.encode())
            .putString("$name.right", canvas.rightRegion.encode())
            .putInt("$name.mode", modeSpinner.selectedItemPosition).apply()
        return true
    }

    /**
     * Older versions stored Edit Sample Area profiles in a private list that the
     * Profile Settings viewing-profile selector could not see. Import them into
     * the shared named-profile store without deleting the original saved data.
     */
    private fun migrateLegacyProfiles() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val legacyNames = prefs.getStringSet(KEY_NAMES, emptySet()) ?: emptySet()
        val sharedNames = SampleAreaProfileStore.list(this).map { it.name.lowercase() }.toSet()
        legacyNames.forEach { name ->
            if (name.lowercase() !in sharedNames) {
                val left = SamplingRegion.decode(prefs.getString("$name.left", null))
                val right = SamplingRegion.decode(prefs.getString("$name.right", null))
                if (left != null && right != null) SampleAreaProfileStore.save(this, name, left, right)
            }
        }
    }

    private fun chooseProfileToEdit() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val profiles = SampleAreaProfileStore.list(this)
        if (profiles.isEmpty()) {
            Toast.makeText(this, "No saved sample-area profiles yet. Save one first.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Edit saved profile")
            .setItems(profiles.map { it.name }.toTypedArray()) { _, index ->
                val profile = profiles[index]
                activeProfile = profile.name
                canvas.setRegions(profile.left, profile.right)
                modeSpinner.setSelection(prefs.getInt("${profile.name}.mode", 0).coerceIn(0, MODES.lastIndex))
                Toast.makeText(this, "Editing ${profile.name} — save to update it.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun resetEditor() {
        canvas.setRegions(SamplingRegion.RP5_LEFT_DEFAULT, SamplingRegion.RP5_RIGHT_DEFAULT)
        modeSpinner.setSelection(0)
        activeProfile = ""
        // Reset should return to the same held identity colours used on editor startup,
        // not a short test pulse that clears the LEDs after 450ms.
        showDefaultLedColors()
        Toast.makeText(this, "Viewing area, method, and LED preview reset to defaults.", Toast.LENGTH_SHORT).show()
    }

    private fun sampleColors(): Pair<Int, Int> {
        val bmp = bitmap ?: return Color.BLACK to Color.BLACK
        // Always sample the exact regions shown by the draggable boxes.
        val left = canvas.leftRegion
        val right = canvas.rightRegion
        fun sample(region: SamplingRegion, dominant: Boolean, weighted: Boolean): Int {
            val x0 = (region.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
            val x1 = (region.right * bmp.width).toInt().coerceIn(x0 + 1, bmp.width)
            val y0 = (region.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
            val y1 = (region.bottom * bmp.height).toInt().coerceIn(y0 + 1, bmp.height)
            if (dominant) {
                val bins = HashMap<Int, Int>()
                for (y in y0 until y1 step maxOf(1, (y1 - y0) / 120)) for (x in x0 until x1 step maxOf(1, (x1 - x0) / 120)) {
                    val c = bmp.getPixel(x, y)
                    val q = ((Color.red(c) shr 4) shl 8) or ((Color.green(c) shr 4) shl 4) or (Color.blue(c) shr 4)
                    bins[q] = (bins[q] ?: 0) + 1
                }
                val q = bins.maxByOrNull { it.value }?.key ?: return Color.BLACK
                return Color.rgb(((q shr 8) and 15) * 17, ((q shr 4) and 15) * 17, (q and 15) * 17)
            }
            var rr = 0.0; var gg = 0.0; var bb = 0.0; var total = 0.0
            val cx = (x0 + x1 - 1) / 2.0; val cy = (y0 + y1 - 1) / 2.0
            val sx = maxOf(1.0, (x1 - x0) / 2.0); val sy = maxOf(1.0, (y1 - y0) / 2.0)
            for (y in y0 until y1) for (x in x0 until x1) {
                val c = bmp.getPixel(x, y)
                val weight = if (weighted) 1.0 / (1.0 + ((x - cx) / sx) * ((x - cx) / sx) + ((y - cy) / sy) * ((y - cy) / sy)) else 1.0
                rr += Color.red(c) * weight; gg += Color.green(c) * weight; bb += Color.blue(c) * weight; total += weight
            }
            return Color.rgb((rr / total).toInt().coerceIn(0, 255), (gg / total).toInt().coerceIn(0, 255), (bb / total).toInt().coerceIn(0, 255))
        }
        return when (modeSpinner.selectedItemPosition) {
            0, 1 -> sample(left, false, false) to sample(right, false, false)
            2 -> sample(left, false, true) to sample(right, false, true)
            3 -> sample(left, true, false) to sample(right, true, false)
            else -> primeDominant(bmp, left) to primeDominant(bmp, right)
        }
    }

    private fun primeDominant(bmp: Bitmap, region: SamplingRegion): Int {
        val x0 = (region.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val x1 = (region.right * bmp.width).toInt().coerceIn(x0 + 1, bmp.width)
        val y0 = (region.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val y1 = (region.bottom * bmp.height).toInt().coerceIn(y0 + 1, bmp.height)
        val bins = HashMap<Int, Int>()
        val stepX = maxOf(1, (x1 - x0) / 90); val stepY = maxOf(1, (y1 - y0) / 90)
        for (y in y0 until y1 step stepY) for (x in x0 until x1 step stepX) {
            val c = bmp.getPixel(x, y); val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            val max = maxOf(r, g, b); val min = minOf(r, g, b)
            if (max - min < 36 || max < 48) continue
            val q = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            bins[q] = (bins[q] ?: 0) + 1
        }
        val q = bins.maxByOrNull { it.value }?.key ?: return Color.BLACK
        return Color.rgb(((q shr 8) and 15) * 17, ((q shr 4) and 15) * 17, (q and 15) * 17)
    }

    private fun loadStill(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val decoded = android.graphics.BitmapFactory.decodeStream(input) ?: throw IllegalArgumentException("Image could not be decoded")
                bitmap?.recycle()
                bitmap = Bitmap.createScaledBitmap(decoded, minOf(decoded.width, 900), (decoded.height * minOf(decoded.width, 900).toFloat() / decoded.width).toInt().coerceAtLeast(1), true)
                if (bitmap !== decoded) decoded.recycle()
                showEditor()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load image: ${e.message}", Toast.LENGTH_LONG).show()
            showSourceChooser()
        }
    }

    private fun startLiveCapture() {
        val service = BifrostAccessibilityService.instance
        if (service == null || !BifrostAccessibilityService.isEnabled(service)) {
            Toast.makeText(this, "Enable Bifrost accessibility service before using LIVE IMAGE.", Toast.LENGTH_LONG).show()
            showSourceChooser()
            return
        }
        if (capturePending) return
        capturePending = true
        var secondsLeft = 5
        captureStatus?.text = "Switch to your game now. Capturing in 5…"
        val countdown = object : Runnable {
            override fun run() {
                if (!capturePending || isFinishing || isDestroyed) return
                secondsLeft--
                if (secondsLeft > 0) {
                    captureStatus?.text = "Switch to your game now. Capturing in $secondsLeft…"
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        handler.postDelayed(countdown, 1000L)
        handler.postDelayed({ if (capturePending) captureScreen(service) }, 5000L)
        handler.postDelayed({
            if (capturePending && !isFinishing && !isDestroyed) {
                capturePending = false
                captureStatus = null
                Toast.makeText(this, "Live capture timed out. Try again or use STILL IMAGE.", Toast.LENGTH_LONG).show()
                showSourceChooser()
            }
        }, 15000L)
    }

    private fun captureScreen(service: AccessibilityService) {
        if (isFinishing || isDestroyed) return
        try {
            service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, Executor { runOnUiThread(it) }, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    capturePending = false
                    captureStatus = null
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        screenshot.hardwareBuffer.close()
                        val soft = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        hardware?.recycle()
                        if (soft == null) { Toast.makeText(this@EditSampleAreaActivity, "Could not convert screen capture.", Toast.LENGTH_LONG).show(); showSourceChooser(); return }
                        bitmap?.recycle()
                        bitmap = Bitmap.createScaledBitmap(soft, minOf(soft.width, 900), (soft.height * minOf(soft.width, 900).toFloat() / soft.width).toInt().coerceAtLeast(1), true)
                        soft.recycle(); showEditor()
                    } catch (e: Exception) { Toast.makeText(this@EditSampleAreaActivity, "Capture failed: ${e.message}", Toast.LENGTH_LONG).show(); showSourceChooser() }
                }
                override fun onFailure(errorCode: Int) {
                    capturePending = false
                    Toast.makeText(this@EditSampleAreaActivity, "Screen capture failed ($errorCode).", Toast.LENGTH_LONG).show()
                    showSourceChooser()
                }
            })
        } catch (e: Exception) { capturePending = false; captureStatus = null; Toast.makeText(this, "Capture unavailable: ${e.message}", Toast.LENGTH_LONG).show(); showSourceChooser() }
    }

    private fun createColorWheelBitmap(size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val center = (size - 1) / 2f
        val radius = center * 0.96f
        val hsv = floatArrayOf(0f, 1f, 1f)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - center
                val dy = y - center
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                val index = y * size + x
                if (distance > radius) {
                    pixels[index] = Color.TRANSPARENT
                } else {
                    hsv[0] = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
                    hsv[1] = (distance / radius).coerceIn(0f, 1f)
                    pixels[index] = Color.HSVToColor(hsv)
                }
            }
        }
        output.setPixels(pixels, 0, size, 0, 0, size, size)
        return output
    }

    private fun showDefaultLedColors() {
        handler.removeCallbacks(restoreBoxColors)
        runLedTest(Color.rgb(40, 130, 255), Color.rgb(255, 106, 0), 5000L, hold = true)
    }

    private fun refreshLedColors(
        feedbackLeft: Boolean = true,
        feedbackRight: Boolean = true,
        scheduleReset: Boolean = true
    ) {
        if (bitmap == null || !::canvas.isInitialized || !::modeSpinner.isInitialized) return
        val colors = runCatching { sampleColors() }.getOrElse { Color.rgb(40, 130, 255) to Color.rgb(255, 106, 0) }
        if (feedbackLeft || feedbackRight) {
            canvas.setSampleColors(
                if (feedbackLeft) colors.first else null,
                if (feedbackRight) colors.second else null
            )
        }
        handler.removeCallbacks(restoreBoxColors)
        if (scheduleReset) handler.postDelayed(restoreBoxColors, boxColorResetDelayMs)
        runLedTest(colors.first, colors.second, 5000L, hold = true)
    }

    private fun runLedTest(left: Int, right: Int, duration: Long, hold: Boolean = false) {
        val intent = Intent(this, LEDService::class.java).apply {
            action = LEDService.ACTION_RP5_LED_TEST
            putExtra(LEDService.EXTRA_RP5_LEFT_COLOR, left and 0xFFFFFF)
            putExtra(LEDService.EXTRA_RP5_RIGHT_COLOR, right and 0xFFFFFF)
            putExtra(LEDService.EXTRA_RP5_TEST_DURATION_MS, duration)
            putExtra(LEDService.EXTRA_RP5_TEST_HOLD, hold)
            putExtra(LEDService.EXTRA_RP5_TEST_BYPASS_CALIBRATION, true)
        }
        runCatching { startService(intent) }.onFailure { Toast.makeText(this, "Could not update thumb-stick LEDs. Check LED control is enabled.", Toast.LENGTH_LONG).show() }
    }

    private fun clearLeds() = runLedTest(Color.BLACK, Color.BLACK, 250L, hold = false)
    private fun addButton(parent: LinearLayout, title: String, description: String, action: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundColor(panel) }
        box.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(primaryText) })
        box.addView(TextView(this).apply { text = description; textSize = 13f; setTextColor(secondaryText); setPadding(0, dp(4), 0, 0) })
        box.setOnClickListener { action() }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        clearLeds()
        bitmap?.recycle()
        bitmap = null
        super.onDestroy()
    }
}