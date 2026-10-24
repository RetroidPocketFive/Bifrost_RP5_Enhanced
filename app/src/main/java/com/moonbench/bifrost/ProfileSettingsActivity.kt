package com.moonbench.bifrost

import android.content.res.ColorStateList
import android.content.ComponentName
import android.content.Intent
import android.app.WallpaperManager
import java.io.File
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
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
import com.moonbench.bifrost.tools.SamplingRegionStore
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.services.AppProfileManager
import com.moonbench.bifrost.services.LiveWallpaperSettingsManager
import com.moonbench.bifrost.services.VideoLiveWallpaperService
import org.json.JSONArray
import org.json.JSONObject

class ProfileSettingsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PRESET_NAME = "com.moonbench.bifrost.extra.PROFILE_SETTINGS_PRESET_NAME"
        private const val MAX_VIDEO_WALLPAPER_BYTES = 100L * 1024L * 1024L
    }

    private val prefs by lazy { getSharedPreferences("bifrost_prefs", MODE_PRIVATE) }
    private var presets = JSONArray()
    private var selectedPreset = 0
    private var bindingPreset = false
    private var restoreDefaultsPending = false

    private lateinit var presetSpinner: Spinner
    private lateinit var performanceSpinner: Spinner
    private lateinit var animationSpinner: Spinner
    private lateinit var viewingSpinner: Spinner
    private lateinit var viewingProfileDescription: TextView
    private lateinit var appProfileSwitch: SwitchMaterial
    private lateinit var assignAppButton: MaterialButton
    private lateinit var manageAppAssignmentsButton: MaterialButton
    private var viewingProfileNames: List<String> = emptyList()
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
    private val colorPickerDialog = ColorPickerDialog()
    private var selectedLedColor: Int = android.graphics.Color.WHITE
    private var selectedRightLedColor: Int = android.graphics.Color.WHITE
    private var selectedBatteryLowColor: Int? = null
    private var selectedBatteryMidColor: Int? = null
    private var selectedBatteryHighColor: Int? = null
    private lateinit var leftStickColorButton: MaterialButton
    private lateinit var rightStickColorButton: MaterialButton
    private lateinit var batteryLowColorButton: MaterialButton
    private lateinit var batteryMidColorButton: MaterialButton
    private lateinit var batteryHighColorButton: MaterialButton
    private lateinit var batteryPaletteRow: View
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
            require(target.length() <= MAX_VIDEO_WALLPAPER_BYTES) { "Video exceeds the 100 MB limit" }
            fileName
        }.getOrNull()
        if (imported == null) {
            File(filesDir, fileName).delete()
            toast("Could not import video. Use MP4/H.264 video up to 100 MB.")
        } else {
            // A profile uses one BIFROST background source at a time. Clear the
            // previous image so it cannot be shadowed by the video's higher priority.
            profile.remove("wallpaperImageFileName")
            profile.put("wallpaperVideoFileName", imported)
            renderWallpaperSelection(profile)
            toast("Video selected as the BIFROST home background. Press Save, then Apply to BIFROST Background.")
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
            // Clear any previously selected video; otherwise MainActivity's
            // video-first renderer would keep showing the old video instead.
            profile.remove("wallpaperVideoFileName")
            profile.put("wallpaperImageFileName", imported)
            renderWallpaperSelection(profile)
            toast("Image selected as the BIFROST home background. Press Save, then Apply to BIFROST Background.")
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
        renderProfileArtworkEditor()
        toast("Profile icon image selected. Press Save to keep this change.")
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
        setupAnimationSpinner()
        animationDescription = findViewById(R.id.profileSettingsAnimationModeDescription)
        updateAnimationDescription()
        viewingProfileDescription = findViewById(R.id.profileSettingsViewingProfileDescription)
        appProfileSwitch = findViewById(R.id.profileSettingsAutoSwitchByAppSwitch)
        assignAppButton = findViewById(R.id.profileSettingsAssignAppButton)
        manageAppAssignmentsButton = findViewById(R.id.profileSettingsManageAppAssignmentsButton)
        setupAppProfileToggle()
        assignAppButton.setOnClickListener { showAppAssignmentPicker() }
        manageAppAssignmentsButton.setOnClickListener { showAppAssignmentManager() }
        refreshViewingProfileSpinner(null)
        findViewById<MaterialButton>(R.id.profileSettingsApplyViewingProfileButton).setOnClickListener {
            applyViewingProfileSelection()
        }
        findViewById<MaterialButton>(R.id.profileSettingsEditViewingProfileButton).setOnClickListener {
            editViewingProfileSelection()
        }
        setupEnumSpinner(themeSpinner, listOf("Classic", "OLED Purple"))
        setupEnumSpinner(fpsSpinner, listOf("24 FPS", "30 FPS", "60 FPS"))
        reactivity.max = 100
        saturation.max = 100
        intensity.max = 255
        setupLedColorControls()

        findViewById<MaterialButton>(R.id.profileSettingsSaveButton).setOnClickListener { saveSelectedPreset() }
        findViewById<MaterialButton>(R.id.profileSettingsEditButton).setOnClickListener { renameSelectedPreset() }
        findViewById<MaterialButton>(R.id.profileSettingsRestoreButton).setOnClickListener { restoreSelectedPresetDefaults() }
        findViewById<MaterialButton>(R.id.profileSettingsDeleteButton).setOnClickListener { deleteSelectedPreset() }

        findViewById<MaterialButton>(R.id.profileSettingsIconColorButton).setOnClickListener { chooseProfileColor() }
        setupProfileArtworkEditor()

        // Reuse the existing Sample Area screen for all three background sources.
        // This menu only selects the source; it does not launch the separate calibration flow.
        findViewById<MaterialButton>(R.id.profileSettingsLiveImageButton).setOnClickListener {
            startActivity(Intent(this, EditSampleAreaActivity::class.java)
                .putExtra(EditSampleAreaActivity.EXTRA_SOURCE, EditSampleAreaActivity.SOURCE_LIVE))
        }
        findViewById<MaterialButton>(R.id.profileSettingsStillImageButton).setOnClickListener {
            startActivity(Intent(this, EditSampleAreaActivity::class.java)
                .putExtra(EditSampleAreaActivity.EXTRA_SOURCE, EditSampleAreaActivity.SOURCE_STILL))
        }
        findViewById<MaterialButton>(R.id.profileSettingsColourWheelButton).setOnClickListener {
            startActivity(Intent(this, EditSampleAreaActivity::class.java)
                .putExtra(EditSampleAreaActivity.EXTRA_SOURCE, EditSampleAreaActivity.SOURCE_COLOR_WHEEL))
        }

        findViewById<MaterialButton>(R.id.profileSettingsPickVideoButton).setOnClickListener {
            videoWallpaperPicker.launch(arrayOf("video/*"))
        }
        findViewById<MaterialButton>(R.id.profileSettingsPickImageWallpaperButton).setOnClickListener {
            wallpaperImagePicker.launch("image/*")
        }
        findViewById<MaterialButton>(R.id.profileSettingsResetWallpaperButton).setOnClickListener {
            resetSelectedProfileWallpaper()
        }
        findViewById<MaterialButton>(R.id.profileSettingsApplyWallpaperButton).setOnClickListener {
            val profile = presets.optJSONObject(selectedPreset)
            if (profile == null) {
                toast("Select a saved profile first.")
                return@setOnClickListener
            }
            val videoName = profile.optString("wallpaperVideoFileName").orEmpty()
            val videoFile = videoName.takeIf { it.isNotBlank() }?.let { File(filesDir, it) }
            val imageName = profile.optString("wallpaperImageFileName").orEmpty()
            val imageFile = imageName.takeIf { it.isNotBlank() }?.let { File(filesDir, it) }
            val iconName = profile.optString("customImageFileName").orEmpty()
            val iconFile = iconName.takeIf { it.isNotBlank() }?.let { File(File(filesDir, "preset_icons"), it) }
            val hasVideo = videoFile?.isFile == true && videoFile.length() > 0L
            val hasImage = imageFile?.isFile == true && imageFile.length() > 0L
            val hasIconImage = iconFile?.isFile == true && iconFile.length() > 0L
            // Applying with no image/video restores the default BIFROST background.
            // Persist this profile before returning so MainActivity can render the
            // chosen in-app background rather than launching Android's wallpaper UI.
            profile.put("profileIconColor", selectedProfileColor)
            prefs.edit()
                .putString("presets_json", presets.toString())
                .putString("profile_settings_apply_preset_name", profile.optString("name"))
                .apply()
            val applied = when {
                hasVideo -> "video wallpaper"
                hasImage -> "image wallpaper"
                hasIconImage -> "icon image"
                else -> "icon colour"
            }
            // Update the home background immediately without closing Profile Settings.
            sendBroadcast(
                Intent(MainActivity.ACTION_APPLY_PROFILE_WALLPAPER)
                    .setPackage(packageName)
                    .putExtra(MainActivity.EXTRA_WALLPAPER_IMAGE, if (hasImage) imageName else "")
                    .putExtra(MainActivity.EXTRA_WALLPAPER_VIDEO, if (hasVideo) videoName else "")
            )
            toast("Applied $applied. Profile Settings will stay open.")
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
                tab.isSelected = active
                tab.backgroundTintList = ColorStateList.valueOf(
                    getColor(if (active) R.color.bifrost_selected else R.color.bifrost_card)
                )
                tab.setTextColor(getColor(if (active) R.color.bifrost_text else R.color.bifrost_text_secondary))
                tab.strokeColor = ColorStateList.valueOf(
                    getColor(if (active) R.color.bifrost_accent_light else R.color.bifrost_border)
                )
                tab.strokeWidth = dp(if (active) 2 else 1)
                tab.alpha = if (active) 1f else 0.86f
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
                if (!bindingPreset) { restoreDefaultsPending = false; selectedPreset = position; loadSelectedPreset() }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        viewingSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateViewingProfileAvailability()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        listOf(performanceSpinner, animationSpinner, themeSpinner).forEach { spinner ->
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!bindingPreset) {
                        if (spinner == themeSpinner) applyThemeSelection(position)
                        if (spinner == animationSpinner) {
                            updateAnimationDescription()
                            updateViewingProfileAvailability()
                            updateAnimationColorControls()
                        }
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
        singleColorSwitch.setOnCheckedChangeListener { _, _ -> updateViewingProfileAvailability() }
        screenCaptureSwitch.setOnCheckedChangeListener { _, _ -> }
        coloredLogoSwitch.setOnCheckedChangeListener { _, _ -> }
        videoWallpaperSwitch.setOnCheckedChangeListener { _, checked ->
            if (!bindingPreset && checked && LiveWallpaperSettingsManager.getVideoUri(prefs) == null) {
                toast("Choose a video below, then press Save to keep the setting.")
            }
        }
        fpsSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!bindingPreset) {
                    // FPS remains a draft until Save is pressed.
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


    private fun reloadPresets() {
        presets = runCatching { JSONArray(prefs.getString("presets_json", "[]")) }.getOrDefault(JSONArray())
        val names = (0 until presets.length()).map { presets.optJSONObject(it)?.optString("name", "Preset ${it + 1}") ?: "Preset ${it + 1}" }
        bindingPreset = true
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, if (names.isEmpty()) listOf("No saved profiles — press Save to create") else names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        presetSpinner.adapter = adapter
        val requestedPresetName = intent.getStringExtra(EXTRA_PRESET_NAME)
        val requestedIndex = requestedPresetName?.let { requested ->
            names.indexOfFirst { it.equals(requested, ignoreCase = true) }
        } ?: -1
        selectedPreset = (if (requestedIndex >= 0) requestedIndex else selectedPreset)
            .coerceIn(0, (names.size - 1).coerceAtLeast(0))
        if (names.isNotEmpty()) presetSpinner.setSelection(selectedPreset, false)
        bindingPreset = false
        loadSelectedPreset()
    }


    private fun setupProfileArtworkEditor() {
        findViewById<MaterialButton>(R.id.profileSettingsIconImageButton).visibility = View.GONE
        findViewById<MaterialButton>(R.id.presetArtworkUploadButton).setOnClickListener {
            if (presets.optJSONObject(selectedPreset) == null) {
                toast("Create or select a saved profile first.")
            } else {
                profileImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        findViewById<MaterialButton>(R.id.presetArtworkApplyEmojiButton).setOnClickListener {
            val input = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.presetArtworkEmojiInput)
            val layout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.presetArtworkEmojiInputLayout)
            val value = input.text?.toString()?.trim().orEmpty()
            if (value.isBlank()) {
                layout.error = "Enter an emoji or short symbol"
                return@setOnClickListener
            }
            layout.error = null
            val target = presets.optJSONObject(selectedPreset) ?: return@setOnClickListener
            target.put("customEmoji", value)
            target.remove("customImageFileName")
            target.remove("appIconPackageName")
            renderProfileArtworkEditor()
            toast("Profile artwork changed. Press Save to keep it.")
        }
        findViewById<MaterialButton>(R.id.presetArtworkResetButton).setOnClickListener {
            val target = presets.optJSONObject(selectedPreset) ?: return@setOnClickListener
            target.remove("customEmoji")
            target.remove("customImageFileName")
            target.remove("appIconPackageName")
            renderProfileArtworkEditor()
            toast("Saved icon restored. Press Save to keep it.")
        }
        findViewById<MaterialButton>(R.id.presetArtworkCloseButton).visibility = View.GONE
    }

    private fun renderProfileArtworkEditor() {
        val target = presets.optJSONObject(selectedPreset)
        val title = findViewById<TextView>(R.id.presetArtworkTitle)
        val nameView = findViewById<TextView>(R.id.presetArtworkPreviewName)
        val imageView = findViewById<android.widget.ImageView>(R.id.presetArtworkPreviewImage)
        val emojiView = findViewById<TextView>(R.id.presetArtworkPreviewEmoji)
        val options = findViewById<android.widget.LinearLayout>(R.id.presetArtworkIconOptions)
        val reset = findViewById<MaterialButton>(R.id.presetArtworkResetButton)
        val emojiInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.presetArtworkEmojiInput)
        if (target == null) {
            title.text = "CUSTOMIZE PROFILE"
            nameView.text = "No profile selected"
            imageView.setImageDrawable(null)
            emojiView.text = null
            options.removeAllViews()
            reset.visibility = View.GONE
            return
        }
        val name = target.optString("name", "Profile")
        title.text = "CUSTOMIZE ${name.uppercase()}"
        nameView.text = name
        val customEmoji = target.optString("customEmoji").takeIf { it.isNotBlank() }
        if (emojiInput.text?.toString() != customEmoji.orEmpty()) emojiInput.setText(customEmoji.orEmpty())
        val icon = PresetIcon.fromStoredName(target.optString("icon"))
        val spec = PresetVisualSpec(
            builtInIcon = icon,
            customEmoji = customEmoji,
            customImageFileName = target.optString("customImageFileName").takeIf { it.isNotBlank() },
            appIconPackageName = target.optString("appIconPackageName").takeIf { it.isNotBlank() },
            iconColor = target.optInt("profileIconColor", selectedProfileColor)
        )
        PresetVisuals.bind(this, spec, imageView, emojiView, (88 * resources.displayMetrics.density).toInt())
        reset.visibility = if (!spec.customEmoji.isNullOrBlank() || !spec.customImageFileName.isNullOrBlank() || !spec.appIconPackageName.isNullOrBlank()) View.VISIBLE else View.GONE
        options.removeAllViews()
        PresetIcon.values().forEach { option ->
            val selected = spec.customEmoji.isNullOrBlank() && spec.customImageFileName.isNullOrBlank() &&
                spec.appIconPackageName.isNullOrBlank() && icon == option
            val density = resources.displayMetrics.density
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams((64 * density).toInt(), (64 * density).toInt()).apply { marginEnd = (10 * density).toInt() }
                radius = 14 * density
                cardElevation = 0f
                setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this@ProfileSettingsActivity, R.color.bifrost_surface))
                setStrokeColor(androidx.core.content.ContextCompat.getColor(this@ProfileSettingsActivity, if (selected) R.color.bifrost_accent else R.color.bifrost_text_secondary))
                strokeWidth = if (selected) (2 * density).toInt() else (1 * density).toInt()
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    target.put("icon", option.name)
                    target.remove("customEmoji")
                    target.remove("customImageFileName")
                    target.remove("appIconPackageName")
                    findViewById<com.google.android.material.textfield.TextInputLayout?>(R.id.presetArtworkEmojiInputLayout)?.error = null
                    renderProfileArtworkEditor()
                    toast("Profile artwork changed. Press Save to keep it.")
                }
            }
            val frame = android.widget.FrameLayout(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams((42 * density).toInt(), (42 * density).toInt())
            }
            val optionImage = android.widget.ImageView(this)
            val optionEmoji = TextView(this).apply { gravity = android.view.Gravity.CENTER; textSize = 22f; visibility = View.GONE }
            frame.addView(optionImage, android.widget.FrameLayout.LayoutParams(-1, -1))
            frame.addView(optionEmoji, android.widget.FrameLayout.LayoutParams(-1, -1))
            PresetVisuals.bind(this, PresetVisualSpec(option), optionImage, optionEmoji, (42 * density).toInt())
            val content = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                addView(frame)
            }
            card.addView(content)
            options.addView(card)
        }
    }

    private fun restoreSelectedPresetDefaults() {
        val profile = presets.optJSONObject(selectedPreset) ?: run {
            toast("Select a saved profile first.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Restore profile defaults")
            .setMessage("Reset the settings shown on this screen to defaults? Nothing will be saved until you press Save.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ ->
                restoreDefaultsPending = true
                bindingPreset = true
                performanceSpinner.setSelection(PerformanceProfile.values().indexOf(PerformanceProfile.HIGH).coerceAtLeast(0))
                animationSpinner.setSelection(LedAnimationType.values().indexOf(LedAnimationType.AMBIENT).coerceAtLeast(0))
                themeSpinner.setSelection(0, false)
                coloredLogoSwitch.isChecked = false
                screenCaptureSwitch.isChecked = LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION
                videoWallpaperSwitch.isChecked = false
                fpsSpinner.setSelection(1, false)
                reactivity.progress = 50
                saturation.progress = 0
                intensity.progress = 255
                singleColorSwitch.isChecked = false
                selectedLedColor = android.graphics.Color.WHITE
                selectedRightLedColor = android.graphics.Color.WHITE
                selectedBatteryLowColor = null
                selectedBatteryMidColor = null
                selectedBatteryHighColor = null
                selectedProfileColor = android.graphics.Color.WHITE
                presets.optJSONObject(selectedPreset)?.put("profileIconColor", selectedProfileColor)
                renderProfileArtworkEditor()
                applySettingsTheme("classic")
                bindingPreset = false
                toast("Defaults shown. Press Save to keep these changes; exit without saving to discard them.")
            }
            .show()
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
            selectedLedColor = p.optInt("color", android.graphics.Color.WHITE)
            selectedRightLedColor = p.optInt("rightColor", selectedLedColor)
            selectedBatteryLowColor = p.optInt("batteryLowColorOverride").takeIf { p.has("batteryLowColorOverride") }
            selectedBatteryMidColor = p.optInt("batteryMidColorOverride").takeIf { p.has("batteryMidColorOverride") }
            selectedBatteryHighColor = p.optInt("batteryHighColorOverride").takeIf { p.has("batteryHighColorOverride") }
            selectedProfileColor = p.optInt("profileIconColor", android.graphics.Color.WHITE)
        } else {
            performanceSpinner.setSelection(PerformanceProfile.values().indexOf(PerformanceProfile.HIGH))
            animationSpinner.setSelection(LedAnimationType.values().indexOf(LedAnimationType.AMBIENT))
            reactivity.progress = 50; saturation.progress = 0; intensity.progress = 255
            singleColorSwitch.isChecked = false
            selectedLedColor = android.graphics.Color.WHITE
            selectedRightLedColor = android.graphics.Color.WHITE
            selectedBatteryLowColor = null
            selectedBatteryMidColor = null
            selectedBatteryHighColor = null
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
        refreshViewingProfileSpinner(p?.optString("sampleAreaProfileName"))
        bindingPreset = false
        renderProfileArtworkEditor()
        renderWallpaperSelection(p)
        updateViewingProfileAvailability()
        updateColorButtonSwatches()
        updateAnimationColorControls()
    }

    override fun onResume() {
        super.onResume()
        if (::viewingSpinner.isInitialized) {
            val savedName = presets.optJSONObject(selectedPreset)?.optString("sampleAreaProfileName")
            refreshViewingProfileSpinner(selectedSampleAreaProfileName() ?: savedName)
            updateViewingProfileAvailability()
        }
    }

    private fun refreshViewingProfileSpinner(selectedName: String?) {
        val profiles = SampleAreaProfileStore.list(this)
        viewingProfileNames = profiles.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Default") + viewingProfileNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        viewingSpinner.adapter = adapter
        val selectedIndex = viewingProfileNames.indexOfFirst { it.equals(selectedName, ignoreCase = true) }
        viewingSpinner.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0, false)
    }

    private fun selectedSampleAreaProfileName(): String? {
        val selectedPosition = viewingSpinner.selectedItemPosition
        if (selectedPosition <= 0) return null
        return viewingProfileNames.getOrNull(selectedPosition - 1)
    }

    private fun updateViewingProfileAvailability() {
        val ambientSelected = animationSpinner.selectedItemPosition in LedAnimationType.values().indices &&
            LedAnimationType.values()[animationSpinner.selectedItemPosition] == LedAnimationType.AMBIENT
        val modeAllowsViewingProfile = ambientSelected && !singleColorSwitch.isChecked
        viewingSpinner.isEnabled = modeAllowsViewingProfile
        findViewById<MaterialButton>(R.id.profileSettingsApplyViewingProfileButton).isEnabled = modeAllowsViewingProfile
        findViewById<MaterialButton>(R.id.profileSettingsEditViewingProfileButton).isEnabled =
            modeAllowsViewingProfile && selectedSampleAreaProfileName() != null
        viewingProfileDescription.text = when {
            !modeAllowsViewingProfile ->
                "Using default animation-mode settings while this control is disabled."
            selectedSampleAreaProfileName() == null ->
                "Default uses the standard animation-mode sampling regions. Choose a saved profile, then tap Apply. Tap Save above to store this choice in the selected Profile Settings preset."
            else ->
                "Tap Apply to use this saved Sample Area profile. Tap Edit to change its viewing regions. Tap Save above to store the selection in Profile Settings."
        }
        viewingProfileDescription.alpha = if (modeAllowsViewingProfile) 1f else 0.55f
    }

    private fun applyViewingProfileSelection() {
        if (animationSpinner.selectedItemPosition !in LedAnimationType.values().indices ||
            LedAnimationType.values()[animationSpinner.selectedItemPosition] != LedAnimationType.AMBIENT ||
            singleColorSwitch.isChecked) {
            toast("Animation Viewing Profile is available only for Ambient mode with Single Color Mode off.")
            return
        }
        val selectedName = selectedSampleAreaProfileName()
        val current = presets.optJSONObject(selectedPreset)
        if (current == null) {
            toast("Save or create a Profile Settings preset first, then apply the viewing profile.")
            return
        }
        if (selectedName == null) {
            SamplingRegionStore.setEnabled(this, false)
            current.remove("sampleAreaProfileName")
            toast("Default animation-mode sampling applied. Tap Save to store this choice.")
        } else {
            val profile = SampleAreaProfileStore.list(this)
                .firstOrNull { it.name.equals(selectedName, ignoreCase = true) }
            if (profile == null) {
                toast("That Sample Area profile is no longer available. Refresh the selection.")
                refreshViewingProfileSpinner(null)
                updateViewingProfileAvailability()
                return
            }
            SamplingRegionStore.setRegions(this, profile.left, profile.right)
            SamplingRegionStore.setEnabled(this, true)
            current.put("sampleAreaProfileName", profile.name)
            toast("Applied '$selectedName'. Tap Save to store this choice in Profile Settings.")
        }
        updateViewingProfileAvailability()
    }

    private fun editViewingProfileSelection() {
        val selectedName = selectedSampleAreaProfileName()
        if (selectedName == null) {
            toast("Choose a saved viewing profile to edit.")
            return
        }
        startActivity(Intent(this, EditSampleAreaActivity::class.java)
            .putExtra(EditSampleAreaActivity.EXTRA_PROFILE_NAME, selectedName))
    }

    private fun applySelectedSampleAreaProfile(profileName: String?) {
        if (profileName.isNullOrBlank()) {
            SamplingRegionStore.setEnabled(this, false)
            return
        }
        val profile = SampleAreaProfileStore.list(this)
            .firstOrNull { it.name.equals(profileName, ignoreCase = true) } ?: run {
                SamplingRegionStore.setEnabled(this, false)
                return
            }
        SamplingRegionStore.setRegions(this, profile.left, profile.right)
        SamplingRegionStore.setEnabled(this, true)
    }

    private fun setupAppProfileToggle() {
        val manager = AppProfileManager(prefs)
        appProfileSwitch.isChecked = manager.isEnabled
        appProfileSwitch.setOnCheckedChangeListener { _, enabled ->
            if (enabled && !manager.hasUsageStatsPermission(this)) {
                appProfileSwitch.isChecked = false
                Toast.makeText(this, "Grant usage access to Bifrost to enable automatic app switching.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                return@setOnCheckedChangeListener
            }
            manager.isEnabled = enabled
            manager.resetLastForegroundPackage()
            if (LEDService.isRunning) {
                startService(Intent(this, LEDService::class.java).setAction(LEDService.ACTION_FORCE_APP_PROFILE_RESOLUTION))
            }
            Toast.makeText(this, if (enabled) "Automatic foreground-app switching enabled." else "Automatic foreground-app switching disabled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentProfileName(): String? =
        presets.optJSONObject(selectedPreset)?.optString("name")?.trim()?.takeIf { it.isNotEmpty() }

    private fun notifyAppAssignmentsChanged() {
        val manager = AppProfileManager(prefs)
        manager.resetLastForegroundPackage()
        if (LEDService.isRunning) {
            startService(Intent(this, LEDService::class.java).setAction(LEDService.ACTION_FORCE_APP_PROFILE_RESOLUTION))
        }
    }

    private fun showAppAssignmentPicker() {
        val profileName = currentProfileName()
        if (profileName == null) { toast("Create or select a saved profile first."); return }
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launchIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (apps.isEmpty()) { toast("No launchable apps were found."); return }
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Assign app to " + profileName)
            .setItems(labels) { _, which ->
                val packageNameToAssign = apps[which].activityInfo.packageName
                AppProfileManager(prefs).setMapping(packageNameToAssign, profileName)
                notifyAppAssignmentsChanged()
                toast(labels[which] + " assigned to " + profileName + ".")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppAssignmentManager() {
        val manager = AppProfileManager(prefs)
        val mappings = manager.getMappings().toList().sortedBy { (pkg, _) ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                .getOrDefault(pkg).lowercase()
        }
        if (mappings.isEmpty()) { toast("No app assignments yet. Choose Assign app to this profile first."); return }
        val labels = mappings.map { (pkg, profileName) ->
            val appLabel = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                .getOrDefault(pkg)
            appLabel + " → " + profileName
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Manage app assignments")
            .setItems(labels) { _, which ->
                manager.removeMapping(mappings[which].first)
                notifyAppAssignmentsChanged()
                toast("Removed assignment for " + labels[which].substringBefore(" → ") + ".")
            }
            .setNegativeButton("Close", null)
            .show()
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
                // Carry the wallpaper draft from the profile being edited whenever the
                // save target is a different profile. This also handles saving over an
                // existing profile name, not only creating a brand-new name.
                if (current != null && target !== current) {
                    listOf("wallpaperImageFileName", "wallpaperVideoFileName").forEach { key ->
                        if (current.has(key)) target.put(key, current.opt(key)) else target.remove(key)
                    }
                }
                target.put("name", name)
                if (restoreDefaultsPending) {
                    target.put("color", android.graphics.Color.WHITE)
                    target.put("rightColor", android.graphics.Color.WHITE)
                    target.put("fadeEndColor", android.graphics.Color.WHITE)
                    target.put("fadeEndRightColor", android.graphics.Color.WHITE)
                    target.put("sensitivity", 0.5)
                    target.put("breatheWhenCharging", false)
                    target.put("indicateChargingSpeed", false)
                    target.put("flashWhenReady", false)
                    target.put("icon", "LIGHT")
                    target.remove("customEmoji")
                    target.remove("customImageFileName")
                    target.remove("appIconPackageName")
                    target.remove("wallpaperImageFileName")
                    target.remove("wallpaperVideoFileName")
                    target.put("uiThemeId", "classic")
                    target.put("coloredLogoEnabled", false)
                }
                target.put("animationType", LedAnimationType.values()[animationSpinner.selectedItemPosition].name)
                target.put("performanceProfile", PerformanceProfile.values()[performanceSpinner.selectedItemPosition].name)
                target.put("color", selectedLedColor)
                target.put("rightColor", selectedRightLedColor)
                if (selectedBatteryLowColor == null) target.remove("batteryLowColorOverride")
                else target.put("batteryLowColorOverride", selectedBatteryLowColor!!)
                if (selectedBatteryMidColor == null) target.remove("batteryMidColorOverride")
                else target.put("batteryMidColorOverride", selectedBatteryMidColor!!)
                if (selectedBatteryHighColor == null) target.remove("batteryHighColorOverride")
                else target.put("batteryHighColorOverride", selectedBatteryHighColor!!)
                target.put("profileIconColor", selectedProfileColor)
                target.put("fadeEndColor", target.optInt("fadeEndColor", android.graphics.Color.WHITE))
                target.put("fadeEndRightColor", target.optInt("fadeEndRightColor", android.graphics.Color.WHITE))
                target.put("brightness", intensity.progress)
                target.put("speed", reactivity.progress / 100.0)
                target.put("smoothness", reactivity.progress / 100.0)
                target.put("sensitivity", target.optDouble("sensitivity", 0.5))
                target.put("saturationBoost", saturation.progress / 100.0)
                target.put("useCustomSampling", screenCaptureSwitch.isChecked)
                target.put("useSingleColor", singleColorSwitch.isChecked)
                val sampleAreaProfileName = selectedSampleAreaProfileName()
                if (sampleAreaProfileName != null) target.put("sampleAreaProfileName", sampleAreaProfileName)
                else target.remove("sampleAreaProfileName")
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
                    .putBoolean("profile_settings_video_wallpaper_requested", videoWallpaperSwitch.isChecked)
                    .putString("profile_settings_apply_preset_name", name)
                    .apply()
                applySettingsTheme(themeId)
                LiveWallpaperSettingsManager.setTargetFps(prefs, listOf(24, 30, 60).getOrElse(fpsSpinner.selectedItemPosition) { 30 })
                persistPresets()
                if (animationSpinner.selectedItemPosition in LedAnimationType.values().indices &&
                    LedAnimationType.values()[animationSpinner.selectedItemPosition] == LedAnimationType.AMBIENT &&
                    !singleColorSwitch.isChecked) {
                    applySelectedSampleAreaProfile(sampleAreaProfileName)
                }
                restoreDefaultsPending = false
                reloadPresets()

                // Saving a profile also applies its BIFROST in-app background.
                // Keep Profile Settings open; MainActivity updates its background
                // through the package-scoped receiver and reads the saved filenames.
                sendBroadcast(
                    Intent(MainActivity.ACTION_APPLY_PROFILE_WALLPAPER)
                        .setPackage(packageName)
                        .putExtra(MainActivity.EXTRA_WALLPAPER_IMAGE, target.optString("wallpaperImageFileName", ""))
                        .putExtra(MainActivity.EXTRA_WALLPAPER_VIDEO, target.optString("wallpaperVideoFileName", ""))
                )
                toast("Profile '$name' saved and its BIFROST background applied.")
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
            presets.optJSONObject(selectedPreset)?.put("profileIconColor", selectedProfileColor)
            renderProfileArtworkEditor()
            toast("Profile icon colour selected. Press Save to keep this change.")
        }.show()
    }

    private fun applyThemeSelection(position: Int) {
        val id = if (position == 1) "oled_purple" else "classic"
        // Preview the theme in this editor; the profile is only changed by Save.
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

    private lateinit var animationDescription: TextView

    private fun setupAnimationSpinner() {
        val types = LedAnimationType.values().toList()
        val adapter = object : ArrayAdapter<LedAnimationType>(this, 0, types) {
            private fun title(type: LedAnimationType) = label(type.name)

            private fun styledText(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
                TextView(this@ProfileSettingsActivity).apply {
                    this.text = text
                    textSize = size
                    setTextColor(color)
                    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                }

            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return styledText(
                    title(getItem(position) ?: LedAnimationType.AMBIENT),
                    15f,
                    getColor(R.color.bifrost_text),
                    true
                ).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(12), 0, dp(36), 0)
                    minHeight = dp(48)
                    maxLines = 1
                }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val type = getItem(position) ?: LedAnimationType.AMBIENT
                val selected = position == animationSpinner.selectedItemPosition
                val row = android.widget.LinearLayout(this@ProfileSettingsActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(getColor(if (selected) R.color.bifrost_selected else R.color.bifrost_dropdown_bg))
                        cornerRadius = dp(10).toFloat()
                        setStroke(dp(1), getColor(if (selected) R.color.bifrost_accent else R.color.bifrost_border))
                    }
                }
                row.addView(styledText(title(type), 14f, getColor(R.color.bifrost_text), true))
                row.addView(styledText(animationModeDescription(type), 12f, getColor(R.color.bifrost_text_secondary)).apply {
                    setPadding(0, dp(3), 0, 0)
                })
                return row
            }
        }
        animationSpinner.adapter = adapter
        animationSpinner.dropDownVerticalOffset = dp(4)
        animationSpinner.setPopupBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
            setColor(getColor(R.color.bifrost_dropdown_bg))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), getColor(R.color.bifrost_border))
        })
    }

    private fun setupLedColorControls() {
        leftStickColorButton = findViewById(R.id.profileSettingsLeftStickColorButton)
        rightStickColorButton = findViewById(R.id.profileSettingsRightStickColorButton)
        batteryLowColorButton = findViewById(R.id.profileSettingsBatteryLowColorButton)
        batteryMidColorButton = findViewById(R.id.profileSettingsBatteryMidColorButton)
        batteryHighColorButton = findViewById(R.id.profileSettingsBatteryHighColorButton)
        batteryPaletteRow = findViewById(R.id.profileSettingsBatteryPaletteRow)
        leftStickColorButton.setOnClickListener {
            colorPickerDialog.show(activity = this, initialColor = selectedLedColor) { color ->
                selectedLedColor = color
                updateColorButtonSwatches()
            }
        }
        rightStickColorButton.setOnClickListener {
            colorPickerDialog.show(activity = this, initialColor = selectedRightLedColor) { color ->
                selectedRightLedColor = color
                updateColorButtonSwatches()
            }
        }
        batteryLowColorButton.setOnClickListener { chooseBatteryColor(0) }
        batteryMidColorButton.setOnClickListener { chooseBatteryColor(1) }
        batteryHighColorButton.setOnClickListener { chooseBatteryColor(2) }
        batteryLowColorButton.setOnLongClickListener { selectedBatteryLowColor = null; updateColorButtonSwatches(); true }
        batteryMidColorButton.setOnLongClickListener { selectedBatteryMidColor = null; updateColorButtonSwatches(); true }
        batteryHighColorButton.setOnLongClickListener { selectedBatteryHighColor = null; updateColorButtonSwatches(); true }
        updateColorButtonSwatches()
        updateAnimationColorControls()
    }

    private fun chooseBatteryColor(level: Int) {
        val current = when (level) {
            0 -> selectedBatteryLowColor ?: android.graphics.Color.rgb(255, 0, 0)
            1 -> selectedBatteryMidColor ?: android.graphics.Color.rgb(255, 255, 0)
            else -> selectedBatteryHighColor ?: android.graphics.Color.rgb(0, 255, 0)
        }
        colorPickerDialog.show(activity = this, initialColor = current) { color ->
            when (level) {
                0 -> selectedBatteryLowColor = color
                1 -> selectedBatteryMidColor = color
                else -> selectedBatteryHighColor = color
            }
            updateColorButtonSwatches()
        }
    }

    private fun updateColorButtonSwatches() {
        if (!::leftStickColorButton.isInitialized) return
        leftStickColorButton.setBackgroundColor(selectedLedColor)
        rightStickColorButton.setBackgroundColor(selectedRightLedColor)
        batteryLowColorButton.setBackgroundColor(selectedBatteryLowColor ?: android.graphics.Color.rgb(255, 0, 0))
        batteryMidColorButton.setBackgroundColor(selectedBatteryMidColor ?: android.graphics.Color.rgb(255, 255, 0))
        batteryHighColorButton.setBackgroundColor(selectedBatteryHighColor ?: android.graphics.Color.rgb(0, 255, 0))
    }

    private fun updateAnimationColorControls() {
        if (!::batteryPaletteRow.isInitialized) return
        val type = LedAnimationType.values().getOrNull(animationSpinner.selectedItemPosition) ?: LedAnimationType.AMBIENT
        batteryPaletteRow.visibility = if (type == LedAnimationType.BATTERY_INDICATOR) View.VISIBLE else View.GONE
        leftStickColorButton.contentDescription = "Choose left stick LED colour"
        rightStickColorButton.contentDescription = "Choose right stick LED colour"
    }
    private fun updateAnimationDescription() {
        val position = animationSpinner.selectedItemPosition
        val type = LedAnimationType.values().getOrNull(position) ?: LedAnimationType.AMBIENT
        animationDescription.text = animationModeDescription(type)
    }

    private fun animationModeDescription(type: LedAnimationType): String = when (type) {
        LedAnimationType.AMBIENT -> "Matches LED colours to sampled on-screen colours for ambient lighting."
        LedAnimationType.AUDIO_REACTIVE -> "Drives colour and movement from audio; requires screen/audio capture permission where prompted."
        LedAnimationType.AMBIAURORA -> "Creates flowing aurora-like colour effects, reacting to the captured source."
        LedAnimationType.BATTERY_INDICATOR -> "Shows battery status through the LED colour output."
        LedAnimationType.CPU_TEMPERATURE -> "Uses device CPU temperature to drive the LED output."
        LedAnimationType.STATIC -> "Keeps the LEDs on a fixed colour you choose."
        LedAnimationType.BREATH -> "Fades a chosen colour in and out smoothly."
        LedAnimationType.RAINBOW -> "Cycles continuously through rainbow colours."
        LedAnimationType.PULSE -> "Pulses a chosen colour in repeating bursts."
        LedAnimationType.STROBE -> "Flashes a chosen colour rapidly."
        LedAnimationType.SPARKLE -> "Adds sparkling flashes to a chosen colour."
        LedAnimationType.FADE_TRANSITION -> "Fades smoothly between colours."
        LedAnimationType.RAVE -> "Cycles vivid colours with a fast, energetic effect."
        LedAnimationType.CHASE -> "Moves a chosen colour along the LED sequence."
        LedAnimationType.PIPBOY -> "Uses a Pip-Boy-inspired green display style."
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupEnumSpinner(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }
    private fun label(value: String) = value.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    private fun renderWallpaperSelection(profile: JSONObject?) {
        val previewRow = findViewById<View>(R.id.profileSettingsWallpaperSelectionPreview)
        val previewImage = findViewById<android.widget.ImageView>(R.id.profileSettingsWallpaperPreviewImage)
        val previewName = findViewById<TextView>(R.id.profileSettingsWallpaperPreviewName)
        val imageName = profile?.optString("wallpaperImageFileName").orEmpty()
        val videoName = profile?.optString("wallpaperVideoFileName").orEmpty()
        val isVideo = videoName.isNotBlank()
        val fileName = if (isVideo) videoName else imageName
        val file = fileName.takeIf { it.isNotBlank() }?.let { File(filesDir, it) }

        if (file == null || !file.isFile || file.length() <= 0L) {
            previewRow.visibility = View.GONE
            previewImage.setImageDrawable(null)
            previewName.text = ""
            return
        }

        previewRow.visibility = View.VISIBLE
        previewName.text = "${if (isVideo) "VIDEO" else "IMAGE"} · ${file.name}"
        val bitmap = if (isVideo) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        } else {
            runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        }
        if (bitmap != null) {
            previewImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            previewImage.setImageBitmap(bitmap)
        } else {
            previewImage.setImageResource(if (isVideo) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_gallery)
        }
    }

    private fun resetSelectedProfileWallpaper() {
        val profile = presets.optJSONObject(selectedPreset)
        if (profile == null) {
            toast("Select a saved profile first.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Reset wallpaper")
            .setMessage("Remove this profile's selected image and video wallpaper and use the default BIFROST background?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                profile.remove("wallpaperImageFileName")
                profile.remove("wallpaperVideoFileName")
                renderWallpaperSelection(profile)
                // Keep the original files intact until Save is pressed; Cancel/exit must discard this draft.
                toast("Wallpaper reset in the editor. Press Save to keep this change.")
            }
            .show()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

}
