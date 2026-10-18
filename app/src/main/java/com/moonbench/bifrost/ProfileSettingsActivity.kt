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
import com.google.android.material.switchmaterial.SwitchMaterial
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
    private lateinit var singleColorSwitch: SwitchMaterial
    private lateinit var screenCaptureSwitch: SwitchMaterial
    private lateinit var coloredLogoSwitch: SwitchMaterial
    private lateinit var videoWallpaperSwitch: SwitchMaterial
    private lateinit var fpsSpinner: Spinner
    private var selectedProfileColor: Int = android.graphics.Color.WHITE

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
        singleColorSwitch = findViewById(R.id.profileSettingsSingleColorSwitch)
        screenCaptureSwitch = findViewById(R.id.profileSettingsScreenCaptureSwitch)
        coloredLogoSwitch = findViewById(R.id.profileSettingsColoredLogoSwitch)
        videoWallpaperSwitch = findViewById(R.id.profileSettingsVideoWallpaperSwitch)
        fpsSpinner = findViewById(R.id.profileSettingsTargetFpsSpinner)

        setupEnumSpinner(performanceSpinner, PerformanceProfile.values().map { label(it.name) })
        setupEnumSpinner(animationSpinner, LedAnimationType.values().map { label(it.name) })
        setupEnumSpinner(viewingSpinner, listOf("Select viewing profile"))
        setupEnumSpinner(themeSpinner, listOf("Classic", "OLED Purple"))
        setupEnumSpinner(fpsSpinner, listOf("24 FPS", "30 FPS", "60 FPS"))
        reactivity.max = 100
        saturation.max = 100
        intensity.max = 255

        findViewById<MaterialButton>(R.id.profileSettingsSaveButton).setOnClickListener { saveSelectedPreset() }
        findViewById<MaterialButton>(R.id.profileSettingsEditButton).setOnClickListener { renameSelectedPreset() }
        findViewById<MaterialButton>(R.id.profileSettingsRestoreButton).setOnClickListener { loadSelectedPreset() }
        findViewById<MaterialButton>(R.id.profileSettingsDeleteButton).setOnClickListener { deleteSelectedPreset() }

        findViewById<MaterialButton>(R.id.profileSettingsIconColorButton).setOnClickListener { chooseProfileColor() }
        findViewById<MaterialButton>(R.id.profileSettingsIconImageButton).setOnClickListener {
            toast("Profile image selection will be added in the next step; the saved-profile icon colour is available now.")
        }

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
                if (!bindingPreset) { selectedPreset = position; loadSelectedPreset() }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        listOf(performanceSpinner, animationSpinner, themeSpinner).forEach { spinner ->
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!bindingPreset) {
                        if (spinner == themeSpinner) applyThemeSelection(position)
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        val change = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        reactivity.setOnSeekBarChangeListener(change)
        saturation.setOnSeekBarChangeListener(change)
        intensity.setOnSeekBarChangeListener(change)
        singleColorSwitch.setOnCheckedChangeListener { _, _ -> }
        screenCaptureSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION, checked).apply()
        }
        coloredLogoSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("colored_logo_enabled", checked).apply()
        }
        videoWallpaperSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) toast("Video wallpaper is managed by Android's live wallpaper service. Use the wallpaper chooser to activate it.")
            prefs.edit().putBoolean("profile_settings_video_wallpaper_requested", checked).apply()
        }
        fpsSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val fps = listOf(24, 30, 60).getOrElse(position) { 30 }
                prefs.edit().putInt("live_wallpaper_target_fps", fps).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        reloadPresets()
        themeSpinner.setSelection(if (prefs.getString("selected_ui_theme", "classic") == "oled_purple") 1 else 0)
        coloredLogoSwitch.isChecked = prefs.getBoolean("colored_logo_enabled", false)
        screenCaptureSwitch.isChecked = prefs.getBoolean(LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION, LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION)
        videoWallpaperSwitch.isChecked = prefs.getBoolean("profile_settings_video_wallpaper_requested", false)
        fpsSpinner.setSelection(listOf(24, 30, 60).indexOf(prefs.getInt("live_wallpaper_target_fps", 30)).coerceAtLeast(0))
    }

    override fun onResume() {
        super.onResume()
        reloadPresets()
    }

    private fun reloadPresets() {
        presets = runCatching { JSONArray(prefs.getString("presets_json", "[]")) }.getOrDefault(JSONArray())
        val names = (0 until presets.length()).map { presets.optJSONObject(it)?.optString("name", "Preset ${it + 1}") ?: "Preset ${it + 1}" }
        bindingPreset = true
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, if (names.isEmpty()) listOf("No saved profiles — press Save to create") else names)
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
            singleColorSwitch.isChecked = p.optBoolean("useSingleColor", false)
            selectedProfileColor = p.optInt("color", android.graphics.Color.WHITE)
        } else {
            performanceSpinner.setSelection(PerformanceProfile.values().indexOf(PerformanceProfile.HIGH))
            animationSpinner.setSelection(LedAnimationType.values().indexOf(LedAnimationType.AMBIENT))
            reactivity.progress = 50; saturation.progress = 0; intensity.progress = 255
            singleColorSwitch.isChecked = false
            selectedProfileColor = android.graphics.Color.WHITE
        }
        screenCaptureSwitch.isChecked = prefs.getBoolean(LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION, LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION)
        bindingPreset = false
    }

    private fun saveSelectedPreset() {
        val current = presets.optJSONObject(selectedPreset)
        if (current == null) {
            val input = android.widget.EditText(this).apply {
                hint = "Profile name"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            AlertDialog.Builder(this).setTitle("Create profile").setMessage("Save these settings as a new profile.")
                .setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Create and save") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isBlank()) { toast("Enter a profile name."); return@setPositiveButton }
                    if ((0 until presets.length()).any { presets.optJSONObject(it)?.optString("name")?.equals(name, true) == true }) {
                        toast("A profile with that name already exists."); return@setPositiveButton
                    }
                    val p = JSONObject().apply {
                        put("name", name)
                        put("animationType", LedAnimationType.values()[animationSpinner.selectedItemPosition].name)
                        put("performanceProfile", PerformanceProfile.values()[performanceSpinner.selectedItemPosition].name)
                        put("color", selectedProfileColor); put("rightColor", selectedProfileColor)
                        put("fadeEndColor", android.graphics.Color.WHITE); put("fadeEndRightColor", android.graphics.Color.WHITE)
                        put("brightness", intensity.progress); put("speed", reactivity.progress / 100.0)
                        put("smoothness", reactivity.progress / 100.0); put("sensitivity", 0.5)
                        put("saturationBoost", saturation.progress / 100.0)
                        put("useCustomSampling", screenCaptureSwitch.isChecked)
                        put("useSingleColor", singleColorSwitch.isChecked)
                        put("breatheWhenCharging", false); put("indicateChargingSpeed", false); put("flashWhenReady", false)
                        put("isAppProfileDefault", false); put("ragnarokAccepted", false); put("icon", "LIGHT")
                    }
                    presets.put(p)
                    selectedPreset = presets.length() - 1
                    persistPresets()
                    reloadPresets()
                    toast("Profile created and saved.")
                }.show()
            return
        }
        current.put("performanceProfile", PerformanceProfile.values()[performanceSpinner.selectedItemPosition].name)
        current.put("animationType", LedAnimationType.values()[animationSpinner.selectedItemPosition].name)
        current.put("speed", reactivity.progress / 100.0)
        current.put("smoothness", reactivity.progress / 100.0)
        current.put("saturationBoost", saturation.progress / 100.0)
        current.put("brightness", intensity.progress)
        current.put("useSingleColor", singleColorSwitch.isChecked)
        current.put("useCustomSampling", screenCaptureSwitch.isChecked)
        current.put("color", selectedProfileColor)
        current.put("rightColor", selectedProfileColor)
        persistPresets()
        toast("Profile saved. Reopen or select it in BIFROST to apply.")
    }

    private fun persistPresets() {
        prefs.edit().putString("presets_json", presets.toString())
            .putString("last_preset_name", presets.optJSONObject(selectedPreset)?.optString("name", "") ?: "")
            .apply()
    }

    private fun renameSelectedPreset() {
        val p = presets.optJSONObject(selectedPreset) ?: run { toast("No saved profile selected."); return }
        val input = android.widget.EditText(this).apply { setText(p.optString("name")); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; setSelection(text.length) }
        AlertDialog.Builder(this).setTitle("Rename profile").setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { toast("Profile name cannot be empty."); return@setPositiveButton }
                if ((0 until presets.length()).any { it != selectedPreset && presets.optJSONObject(it)?.optString("name")?.equals(name, true) == true }) { toast("A profile with that name already exists."); return@setPositiveButton }
                p.put("name", name); persistPresets(); reloadPresets(); toast("Profile renamed.")
            }.show()
    }

    private fun deleteSelectedPreset() {
        if (presets.optJSONObject(selectedPreset) == null) { toast("No saved profile selected."); return }
        AlertDialog.Builder(this).setTitle("Delete profile").setMessage("Delete this saved profile? This cannot be undone.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                val updated = JSONArray()
                for (i in 0 until presets.length()) if (i != selectedPreset) updated.put(presets.getJSONObject(i))
                presets = updated; selectedPreset = (selectedPreset - 1).coerceAtLeast(0)
                persistPresets(); reloadPresets(); toast("Profile deleted.")
            }.show()
    }

    private fun chooseProfileColor() {
        val colors = intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.RED, android.graphics.Color.GREEN, android.graphics.Color.BLUE, android.graphics.Color.MAGENTA, android.graphics.Color.CYAN, android.graphics.Color.YELLOW)
        val names = arrayOf("White", "Red", "Green", "Blue", "Purple", "Cyan", "Yellow")
        AlertDialog.Builder(this).setTitle("Profile icon colour").setItems(names) { _, which ->
            selectedProfileColor = colors[which]
            val p = presets.optJSONObject(selectedPreset)
            if (p != null) { p.put("color", selectedProfileColor); p.put("rightColor", selectedProfileColor); persistPresets() }
            toast("Profile icon colour saved.")
        }.show()
    }

    private fun applyThemeSelection(position: Int) {
        val id = if (position == 1) "oled_purple" else "classic"
        prefs.edit().putString("selected_ui_theme", id).apply()
        toast("Theme set to ${if (position == 1) "OLED Purple" else "Classic"}. Return to BIFROST to refresh the theme.")
    }

    private fun setupEnumSpinner(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }
    private fun label(value: String) = value.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
