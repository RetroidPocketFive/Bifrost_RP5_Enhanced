package com.moonbench.bifrost

import android.content.res.ColorStateList
import android.content.ComponentName
import android.content.Intent
import android.app.WallpaperManager
import java.io.File
import android.os.Bundle
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.services.LiveWallpaperSettingsManager
import com.moonbench.bifrost.services.VideoLiveWallpaperService
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
    private val videoWallpaperPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val profile = presets.optJSONObject(selectedPreset)
        if (profile == null) { toast("Create or select a saved profile first."); return@registerForActivityResult }
        val fileName = "profile_video_" + System.currentTimeMillis() + ".mp4"
        val imported = runCatching {
            val target = File(filesDir, fileName)
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to read video" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            fileName
        }.getOrNull()
        if (imported == null) {
            toast("Could not import the selected video.")
        } else {
            profile.put("wallpaperVideoFileName", imported)
            persistPresets()
            toast("Video saved to this profile. Press Apply Wallpaper and confirm in Android's wallpaper preview.")
        }
    }
    private val wallpaperImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val profile = presets.optJSONObject(selectedPreset)
        if (profile == null) { toast("Create or select a saved profile first."); return@registerForActivityResult }
        val fileName = "profile_wallpaper_" + System.currentTimeMillis() + ".img"
        val imported = runCatching {
            val target = File(filesDir, fileName)
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to read image" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            fileName
        }.getOrNull()
        if (imported == null) {
            toast("Could not import the selected image.")
        } else {
            profile.put("wallpaperImageFileName", imported)
            persistPresets()
            toast("Image saved to this profile as its BIFROST background.")
        }
    }
    private val profileImagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val target = presets.optJSONObject(selectedPreset) ?: run { toast("Select a saved profile first."); return@registerForActivityResult }
        val fileName = runCatching { PresetImageStorage.copyPickedImage(this, uri) }.getOrNull()
        if (fileName == null) { toast("Could not import that image."); return@registerForActivityResult }
        target.remove("customEmoji")
        target.put("customImageFileName", fileName)
        target.remove("appIconPackageName")
        persistPresets()
        toast("Profile icon image saved.")
    }

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
        findViewById<MaterialButton>(R.id.profileSettingsRestoreButton).setOnClickListener { restoreSelectedPresetDefaults() }
        findViewById<MaterialButton>(R.id.profileSettingsDeleteButton).setOnClickListener { deleteSelectedPreset() }

        findViewById<MaterialButton>(R.id.profileSettingsIconColorButton).setOnClickListener { chooseProfileColor() }
        findViewById<MaterialButton>(R.id.profileSettingsPickVideoButton).setOnClickListener {
            videoWallpaperPicker.launch(arrayOf("video/*"))
        }
        findViewById<MaterialButton>(R.id.profileSettingsPickImageWallpaperButton).setOnClickListener {
            wallpaperImagePicker.launch("image/*")
        }
        findViewById<MaterialButton>(R.id.profileSettingsApplyWallpaperButton).setOnClickListener {
            val profile = presets.optJSONObject(selectedPreset)
            val fileName = profile?.optString("wallpaperVideoFileName").orEmpty()
            val videoFile = if (fileName.isNotBlank()) File(filesDir, fileName) else null
            if (videoFile == null || !videoFile.isFile || videoFile.length() == 0L) {
                toast("This profile has no video wallpaper. Choose a video first.")
                return@setOnClickListener
            }
            LiveWallpaperSettingsManager.setVideoUri(prefs, Uri.fromFile(videoFile))
            prefs.edit().putBoolean("live_wallpaper_apply_in_progress", true).apply()
            val component = ComponentName(this, VideoLiveWallpaperService::class.java)
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            }
            runCatching { startActivity(intent) }.onFailure {
                prefs.edit().putBoolean("live_wallpaper_apply_in_progress", false).apply()
                toast("Android could not open the live-wallpaper preview.")
            }
        }
        findViewById<MaterialButton>(R.id.profileSettingsIconImageButton).setOnClickListener {
            if (presets.optJSONObject(selectedPreset) == null) { toast("Create or select a saved profile first."); return@setOnClickListener }
            profileImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
            if (!bindingPreset) {
                presets.optJSONObject(selectedPreset)?.put("coloredLogoEnabled", checked)
                persistPresets()
            }
        }
        videoWallpaperSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("profile_settings_video_wallpaper_requested", checked).apply()
            if (checked && LiveWallpaperSettingsManager.getVideoUri(prefs) == null) {
                toast("Choose a video below, then tap Apply Wallpaper.")
            }
        }
        fpsSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!bindingPreset) {
                    val fps = listOf(24, 30, 60).getOrElse(position) { 30 }
                    LiveWallpaperSettingsManager.setTargetFps(prefs, fps)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        reloadPresets()
        bindingPreset = true
        videoWallpaperSwitch.isChecked = prefs.getBoolean("profile_settings_video_wallpaper_requested", false)
        fpsSpinner.setSelection(listOf(24, 30, 60).indexOf(LiveWallpaperSettingsManager.getTargetFps(prefs).let { current ->
            listOf(24, 30, 60).minByOrNull { kotlin.math.abs(it - current) } ?: 30
        }).coerceAtLeast(0), false)
        bindingPreset = false
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

    private fun restoreSelectedPresetDefaults() {
        val profile = presets.optJSONObject(selectedPreset) ?: run {
            toast("Select a saved profile first.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Restore profile defaults")
            .setMessage("Reset this profile's settings, theme, logo, icon and wallpaper selections to their defaults? Other profiles will not be changed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ ->
                val name = profile.optString("name", "Profile")
                val defaults = JSONObject()
                defaults.put("name", name)
                defaults.put("animationType", LedAnimationType.AMBIENT.name)
                defaults.put("performanceProfile", PerformanceProfile.HIGH.name)
                defaults.put("color", android.graphics.Color.WHITE)
                defaults.put("rightColor", android.graphics.Color.WHITE)
                defaults.put("fadeEndColor", android.graphics.Color.WHITE)
                defaults.put("fadeEndRightColor", android.graphics.Color.WHITE)
                defaults.put("brightness", 255)
                defaults.put("speed", 0.5)
                defaults.put("smoothness", 0.5)
                defaults.put("sensitivity", 0.5)
                defaults.put("saturationBoost", 0.0)
                defaults.put("useCustomSampling", LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION)
                defaults.put("useSingleColor", false)
                defaults.put("breatheWhenCharging", false)
                defaults.put("indicateChargingSpeed", false)
                defaults.put("flashWhenReady", false)
                defaults.put("isAppProfileDefault", profile.optBoolean("isAppProfileDefault", false))
                defaults.put("ragnarokAccepted", profile.optBoolean("ragnarokAccepted", false))
                defaults.put("icon", "LIGHT")
                defaults.put("uiThemeId", "classic")
                defaults.put("coloredLogoEnabled", false)
                presets.put(selectedPreset, defaults)
                prefs.edit()
                    .putString("selected_ui_theme", "classic")
                    .putBoolean("colored_logo_enabled", false)
                    .putBoolean(LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION, LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION)
                    .putString("profile_settings_apply_preset_name", name)
                    .apply()
                persistPresets()
                loadSelectedPreset()
                toast("Defaults restored for '$name'.")
            }.show()
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
            val themeId = prefs.getString("selected_ui_theme", "classic") ?: "classic"
            themeSpinner.setSelection(if (themeId == "oled_purple") 1 else 0, false)
            coloredLogoSwitch.isChecked = prefs.getBoolean("colored_logo_enabled", false)
        }
        screenCaptureSwitch.isChecked = p?.optBoolean("useCustomSampling", prefs.getBoolean(LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION, LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION))
            ?: prefs.getBoolean(LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION, LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION)
        val themeId = p?.optString("uiThemeId", prefs.getString("selected_ui_theme", "classic")) ?: prefs.getString("selected_ui_theme", "classic")
        themeSpinner.setSelection(if (themeId == "oled_purple") 1 else 0, false)
        coloredLogoSwitch.isChecked = p?.optBoolean("coloredLogoEnabled", prefs.getBoolean("colored_logo_enabled", false))
            ?: prefs.getBoolean("colored_logo_enabled", false)
        applySettingsTheme(themeId ?: "classic")
        bindingPreset = false
    }

    private fun saveSelectedPreset() {
        val current = presets.optJSONObject(selectedPreset)
        val input = android.widget.EditText(this).apply {
            hint = "Profile name"
            setText(current?.optString("name", "") ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            if (text.isNotEmpty()) setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(if (current == null) "Create profile" else "Save profile")
            .setMessage("Enter a profile name. An existing name will update that profile; a new name creates a separate profile.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { toast("Enter a profile name."); return@setPositiveButton }
                val existingIndex = (0 until presets.length()).firstOrNull {
                    presets.optJSONObject(it)?.optString("name")?.equals(name, true) == true
                }
                val target = if (existingIndex != null) presets.optJSONObject(existingIndex) else JSONObject()
                if (target == null) { toast("Could not create the profile."); return@setPositiveButton }
                target.put("name", name)
                target.put("animationType", LedAnimationType.values()[animationSpinner.selectedItemPosition].name)
                target.put("performanceProfile", PerformanceProfile.values()[performanceSpinner.selectedItemPosition].name)
                target.put("color", selectedProfileColor)
                target.put("rightColor", selectedProfileColor)
                target.put("fadeEndColor", target.optInt("fadeEndColor", android.graphics.Color.WHITE))
                target.put("fadeEndRightColor", target.optInt("fadeEndRightColor", android.graphics.Color.WHITE))
                target.put("brightness", intensity.progress)
                target.put("speed", reactivity.progress / 100.0)
                target.put("smoothness", reactivity.progress / 100.0)
                target.put("sensitivity", target.optDouble("sensitivity", 0.5))
                target.put("saturationBoost", saturation.progress / 100.0)
                target.put("useCustomSampling", screenCaptureSwitch.isChecked)
                target.put("useSingleColor", singleColorSwitch.isChecked)
                target.put("uiThemeId", if (themeSpinner.selectedItemPosition == 1) "oled_purple" else "classic")
                target.put("coloredLogoEnabled", coloredLogoSwitch.isChecked)
                target.put("breatheWhenCharging", target.optBoolean("breatheWhenCharging", false))
                target.put("indicateChargingSpeed", target.optBoolean("indicateChargingSpeed", false))
                target.put("flashWhenReady", target.optBoolean("flashWhenReady", false))
                target.put("isAppProfileDefault", target.optBoolean("isAppProfileDefault", false))
                target.put("ragnarokAccepted", target.optBoolean("ragnarokAccepted", false))
                target.put("icon", target.optString("icon", "LIGHT"))
                if (existingIndex == null) {
                    presets.put(target)
                    selectedPreset = presets.length() - 1
                } else {
                    presets.put(existingIndex, target)
                    selectedPreset = existingIndex
                }
                val themeId = target.optString("uiThemeId", "classic")
                prefs.edit()
                    .putString("selected_ui_theme", themeId)
                    .putBoolean("colored_logo_enabled", target.optBoolean("coloredLogoEnabled", false))
                    .putBoolean(LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION, screenCaptureSwitch.isChecked)
                    .putString("profile_settings_apply_preset_name", name)
                    .apply()
                applySettingsTheme(themeId)
                persistPresets()
                reloadPresets()
                toast("Profile '$name' saved. Returning to BIFROST to apply it.")
                finish()
            }.show()
    }

    private fun persistPresets() {
        // Editing a profile must not silently change which profile is active.
        prefs.edit().putString("presets_json", presets.toString()).apply()
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
        if (!bindingPreset) {
            presets.optJSONObject(selectedPreset)?.put("uiThemeId", id)
            persistPresets()
        }
        applySettingsTheme(id)
    }

    private fun applySettingsTheme(themeId: String) {
        val root = findViewById<View>(android.R.id.content) ?: return
        val purple = themeId == "oled_purple"
        val background = android.graphics.Color.parseColor(if (purple) "#10091A" else "#0A0E1A")
        val card = android.graphics.Color.parseColor(if (purple) "#1B1029" else "#12182B")
        val surface = android.graphics.Color.parseColor(if (purple) "#2A1740" else "#1A2236")
        val accent = android.graphics.Color.parseColor(if (purple) "#C084FC" else "#6366F1")
        root.setBackgroundColor(background)
        fun tint(view: View) {
            when (view) {
                is com.google.android.material.card.MaterialCardView -> view.setCardBackgroundColor(card)
                is MaterialButton -> {
                    view.backgroundTintList = ColorStateList.valueOf(surface)
                    view.strokeColor = ColorStateList.valueOf(accent)
                }
            }
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) tint(view.getChildAt(i))
            }
        }
        tint(root)
    }

    private fun setupEnumSpinner(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }
    private fun label(value: String) = value.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
