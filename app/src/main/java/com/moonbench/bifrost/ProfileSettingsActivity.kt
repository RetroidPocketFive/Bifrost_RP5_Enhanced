package com.moonbench.bifrost

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ProfileSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_settings)
        findViewById<MaterialButton>(R.id.profileSettingsExitButton).setOnClickListener { finish() }

        val spinner = findViewById<Spinner>(R.id.profileSettingsPresetSpinner)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Select Profile"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val notReady = View.OnClickListener {
            Toast.makeText(this, "Profile actions will be connected after layout approval.", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.profileSettingsSaveButton).setOnClickListener(notReady)
        findViewById<MaterialButton>(R.id.profileSettingsEditButton).setOnClickListener(notReady)
        findViewById<MaterialButton>(R.id.profileSettingsRestoreButton).setOnClickListener(notReady)
        findViewById<MaterialButton>(R.id.profileSettingsDeleteButton).setOnClickListener(notReady)

        val tabs = listOf(
            findViewById<MaterialButton>(R.id.profileSettingsArtworkTab),
            findViewById<MaterialButton>(R.id.profileSettingsThemesTab),
            findViewById<MaterialButton>(R.id.profileSettingsWallpaperTab)
        )
        fun select(selected: MaterialButton) {
            tabs.forEach { tab ->
                val colour = if (tab == selected) R.color.bifrost_selected else R.color.bifrost_card
                tab.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(colour))
                tab.setTextColor(getColor(R.color.bifrost_text))
            }
        }
        tabs.forEach { tab -> tab.setOnClickListener { select(tab) } }
    }
}
