package com.moonbench.bifrost

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.tools.PerformanceProfile
import org.json.JSONArray
import org.json.JSONObject

class ProfileSettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("bifrost_prefs", MODE_PRIVATE) }
    private var presets = JSONArray()
    private var selectedPreset = 0
    private var bindingPreset = false

    private lateinit var presetSpinner: Spinner
    private lateinit var performanceSpinner: Spinner
    private lateinit var animationSpinner: Spinner
    private lateinit var viewingSpinner: Spinner
    private lateinit var themeSpinner: Spinner
    private lateinit var reactivity: SeekBar
    private lateinit var saturation: SeekBar
    private lateinit var intensity: SeekBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_settings)
        findViewById<MaterialButton>(R.id.profileSettingsExitButton).setOnClickListener { finish() }

        presetSpinner = findViewById(R.id.profileSettingsPresetSpinner)
        performanceSpinner = findViewById(R.id.profileSettingsPerformanceSpinner)
        animationSpinner = findViewById(R.id.profileSettingsAnimationModeSpinner)
        viewingSpinner = findViewById(R.id.profileSettingsViewingProfileSpinner)
        themeSpinner = findViewById(R.id.profileSettingsThemeSpinner)
        reactivity = findViewById(R.id.profileSettingsReactivitySeekBar)
        saturation = findViewById(R.id.profileSettingsSaturationSeekBar)
        intensity = findViewById(R.id.profileSettingsIntensitySeekBar)

        setupEnumSpinner(performanceSpinner, PerformanceProfile.values().map { label(it.name) })
        setupEnumSpinner(animationSpinner, LedAnimationType.values().map { label(it.name) })
        setupEnumSpinner(viewingSpinner, listOf("Select viewing profile"))
        setupEnumSpinner(themeSpinner, listOf("Classic", "OLED Purple"))
        reactivity.max = 100
        saturation.max = 100
        intensity.max = 255

        val notReady = View.OnClickListener { toast("This action is not available yet.") }
        findViewById<MaterialButton>(R.id.profileSettingsSaveButton).setOnClickListener { saveSelectedPreset() }
        findViewById<MaterialButton>(R.id.profileSettingsEditButton).setOnClickListener { renameSelectedPreset() }
        findViewById<MaterialButton>(R.id.profileSettingsRestoreButton).setOnClickListener { loadSelectedPreset() }
        findViewById<MaterialButton>(R.id.profileSettingsDeleteButton).setOnClickListener { deleteSelectedPreset() }

        val characteristicsTab = findViewById<MaterialButton>(R.id.profileSettingsArtworkTab)
        val themeTab = findViewById<MaterialButton>(R.id.profileSettingsThemesTab)
        val blankTab = findViewById<MaterialButton>(R.id.profileSettingsWallpaperTab)
        val characteristics = findViewById<View>(R.id.profileSettingsCharacteristicsContent)
        val theme = findViewById<View>(R.id.profileSettingsThemeContent)
        val blank = findViewById<View>(R.id.profileSettingsBlankContent)
        fun selectTab(selected: MaterialButton) {
            listOf(characteristicsTab, themeTab, blankTab).forEach { tab ->
                val active = tab == selected
                tab.backgroundTintList = ColorStateList.valueOf(getColor(if (active) R.color.bifrost_selected else R.color.bifrost_card))
                tab.setTextColor(getColor(R.color.bifrost_text))
                tab.strokeColor = ColorStateList.valueOf(getColor(R.color.bifrost_accent))
            }
            characteristics.visibility = if (selected == characteristicsTab) View.VISIBLE else View.GONE
            theme.visibility = if (selected == themeTab) View.VISIBLE else View.GONE
            blank.visibility = if (selected == blankTab) View.VISIBLE else View.GONE
        }
        characteristicsTab.setOnClickListener { selectTab(characteristicsTab) }
        themeTab.setOnClickListener { selectTab(themeTab) }
        blankTab.setOnClickListener { selectTab(blankTab) }
        selectTab(characteristicsTab)

        presetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!bindingPreset) {
                    selectedPreset = position
                    loadSelectedPreset()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        listOf(performanceSpinner, animationSpinner, themeSpinner).forEach { spinner ->
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!bindingPreset) updateDraft()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        val change = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser && !bindingPreset) updateDraft() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        reactivity.setOnSeekBarChangeListener(change)
        saturation.setOnSeekBarChangeListener(change)
        intensity.setOnSeekBarChangeListener(change)
        reloadPresets()
    }

    private fun reloadPresets() {
        presets = runCatching { JSONArray(prefs.getString("presets_json", "[]")) }.getOrDefault(JSONArray())
        val names = (0 until presets.length()).map { presets.optJSONObject(it)?.optString("name", "Preset ${it + 1}") ?: "Preset ${it + 1}" }
        bindingPreset = true
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, if (names.isEmpty()) listOf("No saved profiles") else names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        presetSpinner.adapter = adapter
        selectedPreset = selectedPreset.coerceIn(0, (names.size - 1).coerceAtLeast(0))
        if (names.isNotEmpty()) presetSpinner.setSelection(selectedPreset, false)
        bindingPreset = false
        loadSelectedPreset()
    }

    private fun loadSelectedPreset() {
        val p = presets.optJSONObject(selectedPreset)
        bindingPreset = true
        if (p != null) {
            performanceSpinner.setSelection(PerformanceProfile.values().indexOfFirst { it.name == p.optString("performanceProfile", "HIGH") }.coerceAtLeast(0))
            animationSpinner.setSelection(LedAnimationType.values().indexOfFirst { it.name == p.optString("animationType", "AMBIENT") }.coerceAtLeast(0))
            reactivity.progress = (p.optDouble("speed", 0.5) * 100).toInt().coerceIn(0, 100)
            saturation.progress = (p.optDouble("saturationBoost", 0.0) * 100).toInt().coerceIn(0, 100)
            intensity.progress = p.optInt("brightness", 255).coerceIn(0, 255)
        } else {
            performanceSpinner.setSelection(PerformanceProfile.values().indexOf(PerformanceProfile.HIGH))
            animationSpinner.setSelection(LedAnimationType.values().indexOf(LedAnimationType.AMBIENT))
            reactivity.progress = 50
            saturation.progress = 0
            intensity.progress = 255
        }
        bindingPreset = false
    }

    private fun updateDraft() {
        // Keep edits in the visible controls until the user explicitly saves.
    }

    private fun saveSelectedPreset() {
        val p = presets.optJSONObject(selectedPreset)
        if (p == null) {
            toast("There are no saved profiles to update. Create a profile in BIFROST Settings first.")
            return
        }
        p.put("performanceProfile", PerformanceProfile.values()[performanceSpinner.selectedItemPosition].name)
        p.put("animationType", LedAnimationType.values()[animationSpinner.selectedItemPosition].name)
        p.put("speed", reactivity.progress / 100.0)
        p.put("smoothness", reactivity.progress / 100.0)
        p.put("saturationBoost", saturation.progress / 100.0)
        p.put("brightness", intensity.progress)
        prefs.edit().putString("presets_json", presets.toString()).apply()
        prefs.edit().putString("last_preset_name", p.optString("name")).apply()
        toast("Saved profile settings. Reopen or select this profile in BIFROST to apply it.")
    }

    private fun renameSelectedPreset() {
        val p = presets.optJSONObject(selectedPreset) ?: run { toast("No saved profile selected."); return }
        val input = android.widget.EditText(this).apply {
            setText(p.optString("name"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSelection(text.length)
        }
        AlertDialog.Builder(this).setTitle("Rename profile").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { toast("Profile name cannot be empty."); return@setPositiveButton }
                if ((0 until presets.length()).any { it != selectedPreset && presets.optJSONObject(it)?.optString("name")?.equals(name, true) == true }) {
                    toast("A profile with that name already exists."); return@setPositiveButton
                }
                p.put("name", name)
                prefs.edit().putString("presets_json", presets.toString()).apply()
                reloadPresets()
                toast("Profile renamed.")
            }.show()
    }

    private fun deleteSelectedPreset() {
        if (presets.optJSONObject(selectedPreset) == null) { toast("No saved profile selected."); return }
        AlertDialog.Builder(this).setTitle("Delete profile")
            .setMessage("Delete this saved profile? This cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val updated = JSONArray()
                for (i in 0 until presets.length()) if (i != selectedPreset) updated.put(presets.getJSONObject(i))
                presets = updated
                selectedPreset = (selectedPreset - 1).coerceAtLeast(0)
                prefs.edit().putString("presets_json", presets.toString()).apply()
                reloadPresets()
                toast("Profile deleted.")
            }.show()
    }

    private fun setupEnumSpinner(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun label(value: String) = value.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
