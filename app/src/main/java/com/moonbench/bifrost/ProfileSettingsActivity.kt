package com.moonbench.bifrost

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ProfileSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_settings)

        findViewById<MaterialButton>(R.id.profileSettingsExitButton).setOnClickListener { finish() }

        configureSpinner(R.id.profileSettingsPresetSpinner, listOf("Select Profile"))
        configureSpinner(R.id.profileSettingsPerformanceSpinner, listOf("High", "Balanced", "Power Saving"))
        configureSpinner(R.id.profileSettingsAnimationModeSpinner, listOf("Ambient"))
        // Deliberately only a prompt: no viewing-profile choices or behaviour are enabled yet.
        configureSpinner(R.id.profileSettingsViewingProfileSpinner, listOf("Select viewing profile"))
        configureSpinner(R.id.profileSettingsThemeSpinner, listOf("Select theme"))

        val notReady = View.OnClickListener {
            android.widget.Toast.makeText(
                this,
                "Profile actions will be connected after the layout is approved.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        findViewById<MaterialButton>(R.id.profileSettingsSaveButton).setOnClickListener(notReady)
        findViewById<MaterialButton>(R.id.profileSettingsEditButton).setOnClickListener(notReady)
        findViewById<MaterialButton>(R.id.profileSettingsRestoreButton).setOnClickListener(notReady)
        findViewById<MaterialButton>(R.id.profileSettingsDeleteButton).setOnClickListener(notReady)

        val characteristicsTab = findViewById<MaterialButton>(R.id.profileSettingsArtworkTab)
        val themeTab = findViewById<MaterialButton>(R.id.profileSettingsThemesTab)
        val blankTab = findViewById<MaterialButton>(R.id.profileSettingsWallpaperTab)
        val characteristics = findViewById<View>(R.id.profileSettingsCharacteristicsContent)
        val theme = findViewById<View>(R.id.profileSettingsThemeContent)
        val blank = findViewById<View>(R.id.profileSettingsBlankContent)

        fun selectTab(selected: MaterialButton) {
            listOf(characteristicsTab, themeTab, blankTab).forEach { tab ->
                val selectedNow = tab == selected
                tab.backgroundTintList = ColorStateList.valueOf(
                    getColor(if (selectedNow) R.color.bifrost_selected else R.color.bifrost_card)
                )
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
    }

    private fun configureSpinner(id: Int, items: List<String>) {
        val spinner = findViewById<Spinner>(id)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(0)
    }
}
