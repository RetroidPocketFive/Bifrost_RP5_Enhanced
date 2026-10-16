package com.moonbench.bifrost

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class BifrostLibrarySettingsActivity : AppCompatActivity() {
    private lateinit var statusText: TextView

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val result = BifrostLibraryStorage.ensureDirectoryLayout(this, uri)
            BifrostLibraryStorage.rememberRoot(this, uri)
            statusText.text = buildString {
                append("Library folder prepared.\n")
                append("Created: ${result.createdFolders.size}\n")
                append("Already present: ${result.existingFolders.size}")
                if (result.warnings.isNotEmpty()) {
                    append("\n\nWarnings:\n")
                    append(result.warnings.joinToString("\n"))
                }
            }
            Toast.makeText(this, "Bifrost library folder checked", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            statusText.text = "Could not prepare the selected folder.\n${error.localizedMessage ?: "Check folder access and try again."}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        layout.addView(TextView(this).apply {
            text = "Library & Import/Export"
            textSize = 24f
        })
        layout.addView(TextView(this).apply {
            text = "Choose a parent folder for Bifrost/Saved. Bifrost will create or reuse its organised folders without deleting existing files."
            textSize = 16f
            setPadding(0, padding / 2, 0, padding)
        })
        layout.addView(MaterialButton(this).apply {
            text = "Choose Library Folder"
            setOnClickListener { folderPicker.launch(BifrostLibraryStorage.getRememberedRoot(this@BifrostLibrarySettingsActivity)) }
        })
        layout.addView(MaterialButton(this).apply {
            text = "Check / Repair Folder Layout"
            setOnClickListener {
                val uri = BifrostLibraryStorage.getRememberedRoot(this@BifrostLibrarySettingsActivity)
                if (uri == null) {
                    statusText.text = "No library folder selected yet. Choose a folder first."
                } else {
                    try {
                        val result = BifrostLibraryStorage.ensureDirectoryLayout(this@BifrostLibrarySettingsActivity, uri)
                        statusText.text = "Folder check complete. Created: ${result.createdFolders.size}; already present: ${result.existingFolders.size}" +
                            if (result.warnings.isEmpty()) "" else "\n\nWarnings:\n" + result.warnings.joinToString("\n")
                    } catch (error: Exception) {
                        statusText.text = "Folder check failed: ${error.localizedMessage ?: "unknown error"}"
                    }
                }
            }
        })
        layout.addView(MaterialButton(this).apply {
            text = "Back"
            setOnClickListener { finish() }
        })
        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, padding, 0, 0)
            text = if (BifrostLibraryStorage.getRememberedRoot(this@BifrostLibrarySettingsActivity) == null) {
                "No library folder selected."
            } else {
                "A library folder is saved. Use Check / Repair to verify its structure."
            }
        }
        layout.addView(statusText)
        setContentView(layout)
    }
}
