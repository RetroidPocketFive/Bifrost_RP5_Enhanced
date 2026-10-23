package com.moonbench.bifrost

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.LinearLayout
import android.widget.ScrollView
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

    private val exportSampleAreasLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching { SampleAreaProfileTransfer.exportToUri(this, uri) }
                .onSuccess { count ->
                    statusText.text = "Export complete: $count Sample Area profile(s).\nActive sampling and calibration were not changed."
                    Toast.makeText(this, "Sample Area profiles exported", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    statusText.text = "Export failed: ${error.localizedMessage ?: "unknown error"}"
                }
        }

    private val importSampleAreasLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultData ->
            if (resultData.resultCode != RESULT_OK) return@registerForActivityResult
            val uri = resultData.data?.data ?: return@registerForActivityResult
            runCatching { SampleAreaProfileTransfer.importFromUri(this, uri) }
                .onSuccess { result ->
                    statusText.text = "Import complete: ${result.importedCount} profile(s) imported or updated.\nOther saved profiles were preserved. Active sampling and calibration were not changed."
                    Toast.makeText(this, "Sample Area profiles imported", Toast.LENGTH_LONG).show()
                }
                .onFailure { error ->
                    statusText.text = "Import failed: ${error.localizedMessage ?: "unknown error"}"
                }
        }

    private val exportBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val options = pendingBackupOptions ?: BackupArchiveTransfer.CategoryOptions()
            pendingBackupOptions = null
            runCatching { BackupArchiveTransfer.exportToUri(this, uri, options) }
                .onSuccess { result ->
                    statusText.text = buildString {
                        append("Backup export complete. Preferences: ${result.preferenceCount}; images: ${result.iconCount}.")
                        if (result.warnings.isNotEmpty()) append("\n\nWarnings:\n" + result.warnings.joinToString("\n"))
                    }
                    Toast.makeText(this, "Bifrost backup exported", Toast.LENGTH_LONG).show()
                }
                .onFailure { error ->
                    statusText.text = "Backup export failed: ${error.localizedMessage ?: "unknown error"}"
                }
        }

    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultData ->
            if (resultData.resultCode != RESULT_OK) return@registerForActivityResult
            val uri = resultData.data?.data ?: return@registerForActivityResult
            val options = pendingBackupOptions ?: BackupArchiveTransfer.CategoryOptions()
            pendingBackupOptions = null
            runCatching { BackupArchiveTransfer.importFromUri(this, uri, options) }
                .onSuccess { result ->
                    statusText.text = buildString {
                        append("Backup restore finished. Preferences restored: ${result.preferenceCount}; images restored: ${result.iconCount}.")
                        if (result.warnings.isNotEmpty()) append("\n\nWarnings:\n" + result.warnings.joinToString("\n"))
                        if (result.errors.isNotEmpty()) append("\n\nErrors:\n" + result.errors.joinToString("\n"))
                        if (result.errors.isEmpty()) append("\n\nRestart Bifrost if restored settings are not reflected immediately.")
                    }
                    Toast.makeText(this, if (result.errors.isEmpty()) "Backup restore finished" else "Backup restore reported errors", Toast.LENGTH_LONG).show()
                }
                .onFailure { error ->
                    statusText.text = "Backup restore failed: ${error.localizedMessage ?: "unknown error"}"
                }
        }

    private fun launchLibraryImport(
        relativeFolder: String,
        mimeTypes: Array<String>,
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            BifrostLibraryStorage.getLibraryFolderUri(this@BifrostLibrarySettingsActivity, relativeFolder)
                ?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
        launcher.launch(intent)
    }

    private fun exportSampleAreasToLibrary() {
        val uri = try {
            BifrostLibraryStorage.createUniqueFileInLibrary(
                this, "Saved/Sample Area", "Bifrost-Sample-Area-Profiles.json", "application/json"
            )
        } catch (error: Exception) {
            statusText.text = "Export failed: ${error.localizedMessage ?: "could not create file"}"
            return
        }
        if (uri == null) {
            statusText.text = "Choose or repair the library folder first. Sample Area exports are saved in Bifrost/Saved/Sample Area."
            return
        }
        runCatching { SampleAreaProfileTransfer.exportToUri(this, uri) }
            .onSuccess { count ->
                statusText.text = "Export complete: $count Sample Area profile(s).\nSaved to Bifrost/Saved/Sample Area. Existing files were not overwritten."
                Toast.makeText(this, "Sample Area profiles exported", Toast.LENGTH_LONG).show()
            }
            .onFailure { error -> statusText.text = "Export failed: ${error.localizedMessage ?: "unknown error"}" }
    }

    private var pendingBackupOptions: BackupArchiveTransfer.CategoryOptions? = null

    private fun chooseBackupCategories(forExport: Boolean) {
        val labels = arrayOf("Themes and interface settings", "Profiles and profile mappings", "Preset artwork / images")
        val selected = booleanArrayOf(true, true, true)
        AlertDialog.Builder(this)
            .setTitle(if (forExport) "Export backup categories" else "Restore backup categories")
            .setMultiChoiceItems(labels, selected) { _, which, isChecked -> selected[which] = isChecked }
            .setMessage(
                if (forExport) "Only the selected categories will be included in the archive."
                else "Selected settings may replace matching settings on this device. Existing artwork not present in the archive is preserved. Global LED calibration is not included."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (forExport) "Choose file" else "Select backup") { _, _ ->
                val options = BackupArchiveTransfer.CategoryOptions(
                    themes = selected[0],
                    profiles = selected[1],
                    images = selected[2]
                )
                if (!options.hasAtLeastOneCategory()) {
                    statusText.text = "Choose at least one category."
                    return@setPositiveButton
                }
                pendingBackupOptions = options
                if (forExport) {
                    val uri = try {
                        BifrostLibraryStorage.createUniqueFileInLibrary(
                            this, "Saved/Backups", "Bifrost-Backup.zip", "application/zip"
                        )
                    } catch (error: Exception) {
                        statusText.text = "Backup export failed: ${error.localizedMessage ?: "could not create file"}"
                        null
                    }
                    if (uri == null) {
                        statusText.text = "Choose or repair the library folder first. Backups are saved in Bifrost/Saved/Backups."
                    } else {
                        runCatching { BackupArchiveTransfer.exportToUri(this, uri, options) }
                            .onSuccess { result ->
                                statusText.text = "Backup exported to Bifrost/Saved/Backups. Preferences: ${result.preferenceCount}; images: ${result.iconCount}."
                                if (result.warnings.isNotEmpty()) statusText.append("\n\nWarnings:\n" + result.warnings.joinToString("\n"))
                                Toast.makeText(this, "Bifrost backup exported", Toast.LENGTH_LONG).show()
                            }
                            .onFailure { error -> statusText.text = "Backup export failed: ${error.localizedMessage ?: "unknown error"}" }
                    }
                } else {
                    launchLibraryImport("Saved/Backups", arrayOf("application/zip", "application/octet-stream"), importBackupLauncher)
                }
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun styledButton(label: String, action: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(getColor(R.color.bifrost_text))
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.bifrost_surface))
            strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.bifrost_accent))
            strokeWidth = dp(1)
            cornerRadius = dp(12)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(48)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { action() }
        }
    }

    private fun sectionCard(title: String, description: String, content: LinearLayout): com.google.android.material.card.MaterialCardView {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
            setCardBackgroundColor(getColor(R.color.bifrost_card))
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.bifrost_accent)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        inner.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.bifrost_text))
        })
        inner.addView(TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(getColor(R.color.bifrost_text_secondary))
            setPadding(0, dp(5), 0, dp(12))
        })
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(0)
            content.removeViewAt(0)
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (index > 0) topMargin = dp(8) }
            inner.addView(child, params)
        }
        card.addView(inner)
        return card
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(getColor(R.color.bifrost_bg))
        }
        val header = com.google.android.material.card.MaterialCardView(this).apply {
            setCardBackgroundColor(getColor(R.color.bifrost_card))
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.bifrost_accent)
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        headerRow.addView(styledButton("‹") { finish() }.apply {
            textSize = 30f
            minWidth = dp(48)
            contentDescription = "Back"
            tooltipText = "Back"
            setPadding(0, 0, 0, dp(3))
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        headerRow.addView(android.widget.ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = "BIFROST logo"
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
            marginStart = dp(12)
            marginEnd = dp(10)
        })
        headerRow.addView(android.view.View(this).apply {
            setBackgroundColor(getColor(R.color.bifrost_accent))
        }, LinearLayout.LayoutParams(dp(1), dp(40)).apply { marginEnd = dp(12) })
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        heading.addView(TextView(this).apply {
            text = "BIFROST"
            textSize = 12f
            letterSpacing = 0.12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.bifrost_accent))
        })
        heading.addView(TextView(this).apply {
            text = "IMPORT / EXPORT"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.bifrost_text))
        })
        heading.addView(TextView(this).apply {
            text = "Manage backups, the library folder and Sample Area profiles."
            textSize = 12f
            setTextColor(getColor(R.color.bifrost_text_secondary))
        })
        headerRow.addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(headerRow)
        page.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val sections = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val libraryContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        libraryContent.addView(styledButton("Choose Library Folder") {
            folderPicker.launch(BifrostLibraryStorage.getRememberedRoot(this@BifrostLibrarySettingsActivity))
        })
        libraryContent.addView(styledButton("Check / Repair Folder Layout") {
            val uri = BifrostLibraryStorage.getRememberedRoot(this@BifrostLibrarySettingsActivity)
            if (uri == null) {
                statusText.text = "No library folder selected yet. Choose a folder first."
            } else {
                try {
                    val result = BifrostLibraryStorage.ensureDirectoryLayout(this@BifrostLibrarySettingsActivity, uri)
                    statusText.text = "Folder check complete. Created: ${result.createdFolders.size}; already present: ${result.existingFolders.size}" +
                        if (result.warnings.isEmpty()) "" else "\\n\\nWarnings:\\n" + result.warnings.joinToString("\\n")
                } catch (error: Exception) {
                    statusText.text = "Folder check failed: ${error.localizedMessage ?: "unknown error"}"
                }
            }
        })
        sections.addView(sectionCard(
            "LIBRARY FOLDER",
            "Choose a parent folder for Bifrost/Saved. Existing files are preserved.",
            libraryContent
        ))

        val backupContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        backupContent.addView(styledButton("Export Full Backup to Bifrost/Saved/Backups") {
            chooseBackupCategories(forExport = true)
        })
        backupContent.addView(styledButton("Restore Full Backup (.zip)") {
            chooseBackupCategories(forExport = false)
        })
        backupContent.addView(TextView(this).apply {
            text = "Full backups can include themes, profile settings/mappings, and preset artwork. LED calibration is excluded; existing artwork not present in the archive is preserved."
            textSize = 12f
            setTextColor(getColor(R.color.bifrost_text_secondary))
            setPadding(0, dp(8), 0, 0)
        })
        sections.addView(sectionCard("BACKUP & RESTORE", "Export or restore selected BIFROST data categories.", backupContent))

        val sampleContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sampleContent.addView(styledButton("Export Sample Areas to Bifrost/Saved/Sample Area") {
            exportSampleAreasToLibrary()
        })
        sampleContent.addView(styledButton("Import Sample Area Profiles") {
            launchLibraryImport("Saved/Sample Area", arrayOf("application/json", "text/json", "text/plain"), importSampleAreasLauncher)
        })
        sampleContent.addView(TextView(this).apply {
            text = "Sample Area imports merge by profile name. They do not activate a profile or change LED calibration. Import only JSON files exported by BIFROST."
            textSize = 12f
            setTextColor(getColor(R.color.bifrost_text_secondary))
            setPadding(0, dp(8), 0, 0)
        })
        sections.addView(sectionCard("SAMPLE AREA PROFILES", "Transfer saved Sample Area profiles between devices.", sampleContent))

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(getColor(R.color.bifrost_text_secondary))
            setPadding(dp(4), dp(8), dp(4), dp(12))
            text = if (BifrostLibraryStorage.getRememberedRoot(this@BifrostLibrarySettingsActivity) == null) {
                "No library folder selected."
            } else {
                "A library folder is saved. Use Check / Repair to verify its structure."
            }
        }
        sections.addView(statusText)
        scroll.addView(sections)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }}
