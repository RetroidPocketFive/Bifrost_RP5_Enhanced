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
            text = "Export Full Backup to Bifrost/Saved/Backups"
            setOnClickListener { chooseBackupCategories(forExport = true) }
        })
        layout.addView(MaterialButton(this).apply {
            text = "Restore Full Backup (.zip)"
            setOnClickListener { chooseBackupCategories(forExport = false) }
        })
        layout.addView(TextView(this).apply {
            text = "Full backups can include themes, profile settings/mappings, and preset artwork. Restore replaces selected preference categories. LED calibration is excluded; artwork already on the device is preserved."
            textSize = 13f
            setPadding(0, padding / 2, 0, padding / 2)
        })
        layout.addView(MaterialButton(this).apply {
            text = "Export Sample Areas to Bifrost/Saved/Sample Area"
            setOnClickListener { exportSampleAreasToLibrary() }
        })
        layout.addView(MaterialButton(this).apply {
            text = "Import Sample Area Profiles"
            setOnClickListener { launchLibraryImport("Saved/Sample Area", arrayOf("application/json", "text/json", "text/plain"), importSampleAreasLauncher) }
        })
        layout.addView(TextView(this).apply {
            text = "Sample Area imports merge by profile name. They never activate a profile or change LED calibration. Import only JSON files exported by Bifrost."
            textSize = 13f
            setPadding(0, padding / 2, 0, padding / 2)
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
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(layout)
        })
    }
}
