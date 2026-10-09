package com.moonbench.bifrost.rp5

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FineTuneManagerActivity : AppCompatActivity() {
    private lateinit var store: FineTuneStore
    private lateinit var list: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = FineTuneStore(this)
        setContentView(build())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) refresh()
    }

    private fun build() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 24, 24, 24)
        setBackgroundColor(0xFF101010.toInt())

        addView(TextView(this@FineTuneManagerActivity).apply {
            text = "SAMPLE AREA — AMBIENT VIEWING PROFILES"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
        })
        addView(TextView(this@FineTuneManagerActivity).apply {
            text = "Sample Area creates reusable screen viewing regions for Ambient. Fine Tune selects which saved Viewing Profile Ambient uses when Heimdall is on. Rainbow and other non-screen-matching animations do not need Fine Tune."
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, 8, 0, 16)
        })
        addView(Button(this@FineTuneManagerActivity).apply {
            text = "CREATE SAMPLE AREA / VIEWING PROFILE"
            setOnClickListener { openEditor(null) }
        })
        addView(Button(this@FineTuneManagerActivity).apply {
            text = "CALIBRATION / LED TEST"
            setOnClickListener { startActivity(Intent(this@FineTuneManagerActivity, LedCalibrationActivity::class.java)) }
        })

        list = LinearLayout(this@FineTuneManagerActivity).apply { orientation = LinearLayout.VERTICAL }
        addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(Button(this@FineTuneManagerActivity).apply {
            text = "EXIT"
            setOnClickListener { finish() }
        })
    }

    private fun refresh() {
        list.removeAllViews()
        val profiles = store.list()
        if (profiles.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No Ambient Viewing Profiles saved yet."
                gravity = Gravity.CENTER
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 16f
            })
            return
        }

        profiles.forEach { profile ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(0xFF202020.toInt())
                addView(TextView(this@FineTuneManagerActivity).apply {
                    text = profile.name + "  •  " + mode(profile.colourMode)
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 17f
                })
                addView(TextView(this@FineTuneManagerActivity).apply {
                    text = "Sample Area saved for Ambient screen matching"
                    setTextColor(0xFFAAAAAA.toInt())
                    textSize = 11f
                })
                val actions = LinearLayout(this@FineTuneManagerActivity).apply {
                    addView(button("EDIT SAMPLE AREA") { openEditor(profile.name) })
                    addView(button("PREVIEW") {
                        startActivity(Intent(this@FineTuneManagerActivity, FineTunePreviewActivity::class.java)
                            .putExtra(FineTunePreviewActivity.EXTRA_PROFILE_NAME, profile.name))
                    })
                    addView(button("FINE TUNE — ASSIGN") { bind(profile) })
                    addView(button("DELETE") { confirmDelete(profile.name) })
                }
                addView(actions)
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        }
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 10f
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }

    private fun mode(value: ThumbstickColourMode) = when (value) {
        ThumbstickColourMode.FIFTY_FIFTY -> "50/50"
        ThumbstickColourMode.AVERAGE -> "Average Colour"
        ThumbstickColourMode.CLOSE_MATCH -> "Close Match Colour"
        ThumbstickColourMode.MOST_DOMINANT -> "Most Dominant Colour"
        ThumbstickColourMode.DOMINANT_PRIME -> "Dominant Prime Colour"
    }

    private fun openEditor(name: String?) {
        startActivity(Intent(this, ThumbstickViewAreaEditorActivity::class.java)
            .putExtra(ThumbstickViewAreaEditorActivity.EXTRA_PROFILE_NAME, name))
    }

    private fun loadPresetNames(): List<String> {
        val raw = getSharedPreferences("bifrost_prefs", MODE_PRIVATE).getString("presets_json", null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun bind(profile: FineTuneProfile) {
        val names = loadPresetNames()
        if (names.isEmpty()) {
            Toast.makeText(this, "No Bifrost profiles found", Toast.LENGTH_SHORT).show()
            return
        }
        val map = store.getProfileBindings().toMutableMap()
        val checks = names.map { map[it]?.equals(profile.name, true) == true }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("FINE TUNE — AMBIENT VIEWING PROFILE")
            .setMessage("Fine Tune tells Ambient which saved Viewing Profile / Sample Area to use for screen-to-thumb-stick matching. This setting is only used when the selected Bifrost animation is Ambient with Heimdall screen monitoring enabled.")
            .setMultiChoiceItems(names.toTypedArray(), checks) { _, index, checked -> checks[index] = checked }
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                names.forEachIndexed { index, preset ->
                    if (checks[index]) map[preset] = profile.name
                    else if (map[preset]?.equals(profile.name, true) == true) map.remove(preset)
                }
                names.forEach { store.setProfileBinding(it, map[it]) }
                refresh()
            }
            .show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Viewing Profile?")
            .setMessage(name)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ -> store.delete(name); refresh() }
            .show()
    }
}
