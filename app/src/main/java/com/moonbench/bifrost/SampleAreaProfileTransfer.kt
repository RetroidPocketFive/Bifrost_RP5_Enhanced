package com.moonbench.bifrost

import android.content.Context
import android.net.Uri
import com.moonbench.bifrost.tools.SamplingRegion
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Portable JSON transfer for named Sample Area profiles only.
 * Import never activates a profile or touches global LED calibration/settings.
 */
object SampleAreaProfileTransfer {
    private const val SCHEMA = "bifrost_sample_area_profiles"
    private const val VERSION = 1
    private const val MAX_BYTES = 1024 * 1024
    private const val MAX_PROFILES = 100
    private const val MAX_NAME_LENGTH = 64

    data class ImportResult(val importedCount: Int, val warnings: List<String>)

    fun exportToUri(context: Context, uri: Uri): Int {
        val profiles = SampleAreaProfileStore.list(context)
        val json = JSONObject().apply {
            put("schema", SCHEMA)
            put("version", VERSION)
            put("profiles", JSONArray().apply {
                profiles.forEach { profile ->
                    put(JSONObject().apply {
                        put("name", profile.name)
                        put("left", profile.left.clamped().encode())
                        put("right", profile.right.clamped().encode())
                    })
                }
            })
        }
        val bytes = json.toString(2).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw IOException("Sample Area export exceeds the size limit.")
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open the selected destination.")
        stream.use { it.write(bytes) }
        return profiles.size
    }

    fun importFromUri(context: Context, uri: Uri): ImportResult {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected file.")
        val bytes = stream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_BYTES) throw IOException("Import file exceeds the 1 MB size limit.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val root = try {
            JSONObject(bytes.toString(Charsets.UTF_8))
        } catch (error: Exception) {
            throw IOException("This is not a valid Sample Area profile JSON file.", error)
        }
        if (root.optString("schema") != SCHEMA) throw IOException("Unsupported file type: expected Sample Area profiles.")
        if (root.optInt("version", -1) != VERSION) throw IOException("Unsupported Sample Area profile file version.")
        val array = root.optJSONArray("profiles") ?: throw IOException("The file has no profiles list.")
        if (array.length() > MAX_PROFILES) throw IOException("The file exceeds the $MAX_PROFILES profile limit.")

        val imported = mutableListOf<SampleAreaProfileStore.Profile>()
        val seen = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: throw IOException("Profile entry ${index + 1} is not an object.")
            val name = item.optString("name").trim()
            if (name.isEmpty() || name.length > MAX_NAME_LENGTH) {
                throw IOException("Profile ${index + 1} has an invalid name.")
            }
            if (!seen.add(name.lowercase())) throw IOException("Duplicate profile name: $name")
            val left = SamplingRegion.decode(item.optString("left"))
                ?: throw IOException("Profile '$name' has an invalid left sampling area.")
            val right = SamplingRegion.decode(item.optString("right"))
                ?: throw IOException("Profile '$name' has an invalid right sampling area.")
            imported += SampleAreaProfileStore.Profile(name, left, right)
        }

        // Merge by name; validate the complete result before writing, so a failed
        // import cannot partially update the saved profile library.
        val merged = SampleAreaProfileStore.list(context).toMutableList()
        imported.forEach { incoming ->
            val existingIndex = merged.indexOfFirst { it.name.equals(incoming.name, ignoreCase = true) }
            if (existingIndex >= 0) merged[existingIndex] = incoming else merged += incoming
        }
        if (merged.size > MAX_PROFILES) {
            throw IOException("Import would exceed the $MAX_PROFILES saved-profile limit. Delete profiles and retry.")
        }
        if (!SampleAreaProfileStore.replaceAll(context, merged)) {
            throw IOException("Could not save imported profiles.")
        }
        return ImportResult(imported.size, emptyList())
    }
}
