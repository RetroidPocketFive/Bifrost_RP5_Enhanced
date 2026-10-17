package com.moonbench.bifrost

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import com.moonbench.bifrost.animations.FadeTransitionAnimation
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.plugins.LivePolicy
import com.moonbench.bifrost.tools.PerformanceProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object PresetArchiveTransfer {

    private const val ARCHIVE_SCHEMA = "bifrost_preset_bundle"
    // v2 adds the optional generic `livePolicies` block (effect name → LivePolicy).
    // Older Bifrost reads a v2 bundle fine — it imports the presets and ignores
    // the block it doesn't know; newer Bifrost reads a v1 bundle fine — no block.
    private const val ARCHIVE_VERSION = 2
    private const val MANIFEST_ENTRY_NAME = "manifest.json"
    private const val ICONS_DIR_PREFIX = "icons/"
    // Defensive limits for user-selected archives and downloaded plugin bundles.
    private const val MAX_ARCHIVE_ENTRIES = 256
    private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 32 * 1024 * 1024
    private val SAFE_ICON_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,179}")

    data class ExportResult(
        val presetCount: Int,
        val iconCount: Int,
        val warnings: List<String>
    )

    data class ImportResult(
        val presets: List<LedPreset>,
        val mappings: Map<String, String>,
        val warnings: List<String>,
        val errors: List<String>,
        // Generic plugin live-feed policies (caller package → policy). Empty for
        // ordinary user preset bundles; populated by plugins that declare them.
        val livePolicies: Map<String, LivePolicy> = emptyMap()
    )

    fun exportToUri(
        context: Context,
        uri: Uri,
        presets: List<LedPreset>,
        mappings: Map<String, String>,
        cancelSignal: CancellationSignal? = null
    ): ExportResult {
        val warnings = mutableListOf<String>()
        var exportedIconCount = 0

        val manifestPresets = JSONArray()
        presets.forEach { preset ->
            val presetJson = JSONObject()
            presetJson.put("name", preset.name)
            presetJson.put("animationType", preset.animationType.name)
            presetJson.put("performanceProfile", preset.performanceProfile.name)
            presetJson.put("color", preset.color)
            presetJson.put("rightColor", preset.rightColor)
            presetJson.put("fadeEndColor", preset.fadeEndColor)
            presetJson.put("fadeEndRightColor", preset.fadeEndRightColor)
            presetJson.put("brightness", preset.brightness)
            presetJson.put("speed", preset.speed.toDouble())
            presetJson.put("smoothness", preset.smoothness.toDouble())
            presetJson.put("sensitivity", preset.sensitivity.toDouble())
            presetJson.put("saturationBoost", preset.saturationBoost.toDouble())
            presetJson.put("useCustomSampling", preset.useCustomSampling)
            presetJson.put("useSingleColor", preset.useSingleColor)
            presetJson.put("breatheWhenCharging", preset.breatheWhenCharging)
            presetJson.put("indicateChargingSpeed", preset.indicateChargingSpeed)
            presetJson.put("flashWhenReady", preset.flashWhenReady)
            preset.batteryLowColorOverride?.let { presetJson.put("batteryLowColorOverride", it) }
            preset.batteryMidColorOverride?.let { presetJson.put("batteryMidColorOverride", it) }
            preset.batteryHighColorOverride?.let { presetJson.put("batteryHighColorOverride", it) }
            preset.cpuCoolColorOverride?.let { presetJson.put("cpuCoolColorOverride", it) }
            preset.cpuWarmColorOverride?.let { presetJson.put("cpuWarmColorOverride", it) }
            preset.cpuHotColorOverride?.let { presetJson.put("cpuHotColorOverride", it) }
            presetJson.put("isAppProfileDefault", preset.isAppProfileDefault)
            presetJson.put("ragnarokAccepted", preset.ragnarokAccepted)
            presetJson.put("icon", preset.icon.name)
            preset.customEmoji?.let { presetJson.put("customEmoji", it) }
            preset.customImageFileName?.let { presetJson.put("customImageFileName", it) }
            preset.appIconPackageName?.let { presetJson.put("appIconPackageName", it) }
            manifestPresets.put(presetJson)
        }

        val mappingsJson = JSONObject()
        mappings.forEach { (packageName, presetName) ->
            mappingsJson.put(packageName, presetName)
        }

        val manifest = JSONObject().apply {
            put("schema", ARCHIVE_SCHEMA)
            put("version", ARCHIVE_VERSION)
            put("presets", manifestPresets)
            put("appProfileMappings", mappingsJson)
        }

        val seenIconNames = linkedSetOf<String>()
        // Check for cancellation before starting heavy IO
        cancelSignal?.throwIfCanceled()
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            ZipOutputStream(stream.buffered()).use { zip ->
                cancelSignal?.throwIfCanceled()
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                presets.forEach { preset ->
                    cancelSignal?.throwIfCanceled()
                    val iconName = preset.customImageFileName?.trim().orEmpty()
                    if (iconName.isEmpty() || !seenIconNames.add(iconName)) return@forEach

                    val input = PresetImageStorage.openIconInputStream(context, iconName)
                    if (input == null) {
                        warnings += "Image not found for preset icon '$iconName'; skipping."
                        return@forEach
                    }

                    input.use { iconStream ->
                        cancelSignal?.throwIfCanceled()
                        zip.putNextEntry(ZipEntry("$ICONS_DIR_PREFIX$iconName"))
                        iconStream.copyTo(zip)
                        zip.closeEntry()
                        exportedIconCount++
                    }
                }
            }
        }

        return ExportResult(
            presetCount = presets.size,
            iconCount = exportedIconCount,
            warnings = warnings
        )
    }

    fun importFromUri(context: Context, uri: Uri, cancelSignal: CancellationSignal? = null): ImportResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val zipEntries = mutableMapOf<String, ByteArray>()
        var totalUncompressedBytes = 0L
        var entryCount = 0
        // Check cancellation before reading. Do not trust ZIP metadata: enforce
        // limits while streaming so forged sizes and zip bombs are bounded.
        cancelSignal?.throwIfCanceled()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        cancelSignal?.throwIfCanceled()
                        val entry = zip.nextEntry ?: break
                        entryCount++
                        if (entryCount > MAX_ARCHIVE_ENTRIES) {
                            throw IllegalArgumentException("Archive contains too many entries.")
                        }
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }
                        val name = entry.name
                        val validName = name == MANIFEST_ENTRY_NAME ||
                            (name.startsWith(ICONS_DIR_PREFIX) &&
                                SAFE_ICON_NAME.matches(name.removePrefix(ICONS_DIR_PREFIX)) &&
                                !name.removePrefix(ICONS_DIR_PREFIX).contains('\\'))
                        if (!validName || name in zipEntries) {
                            throw IllegalArgumentException("Archive contains an unsafe or duplicate entry.")
                        }

                        val data = ByteArrayOutputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var entryBytes = 0
                            while (true) {
                                cancelSignal?.throwIfCanceled()
                                val read = zip.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                totalUncompressedBytes += read
                                if (entryBytes > MAX_ENTRY_BYTES) {
                                    throw IllegalArgumentException("Archive entry is too large.")
                                }
                                if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    throw IllegalArgumentException("Archive expands beyond the allowed size.")
                                }
                                output.write(buffer, 0, read)
                            }
                            output.toByteArray()
                        }
                        zipEntries[name] = data
                        zip.closeEntry()
                    }
                }
            } ?: return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = emptyList(),
                errors = listOf("Unable to read selected file.")
            )
        } catch (t: Throwable) {
            if (t is android.os.OperationCanceledException) throw t
            return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = emptyList(),
                errors = listOf(t.message ?: "Unable to read archive safely.")
            )
        }

        val manifestRaw = zipEntries[MANIFEST_ENTRY_NAME]
            ?: return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = emptyList(),
                errors = listOf("Archive is missing manifest.json")
            )

        val manifest = runCatching {
            JSONObject(manifestRaw.toString(Charsets.UTF_8))
        }.getOrElse {
            return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = emptyList(),
                errors = listOf("manifest.json is invalid JSON")
            )
        }

        if (manifest.optString("schema") != ARCHIVE_SCHEMA) {
            errors += "Unknown preset bundle schema."
        }

        val version = manifest.optInt("version", -1)
        if (version < 1) {
            errors += "Unsupported preset bundle version: $version"
        } else if (version > ARCHIVE_VERSION) {
            warnings += "Bundle version $version is newer than supported version $ARCHIVE_VERSION. Attempting compatible import."
        }

        // Never parse presets (which may import image files) from a manifest
        // whose schema or required version fields have already failed validation.
        if (errors.isNotEmpty()) {
            return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = warnings,
                errors = errors
            )
        }

        val presetArray = manifest.optJSONArray("presets")
        if (presetArray == null || presetArray.length() == 0) {
            return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = warnings,
                errors = listOf("Preset bundle contains no presets.")
            )
        }

        // Reject malformed entries instead of silently skipping them. This keeps
        // archive contents and app-profile mappings predictable before any images
        // are written into app storage.
        for (index in 0 until presetArray.length()) {
            if (presetArray.optJSONObject(index) == null) {
                errors += "Preset entry ${index + 1} is not a valid object."
            }
        }
        if (errors.isNotEmpty()) {
            return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = warnings,
                errors = errors
            )
        }

        // Validate preset identity before parsePreset() writes any bundled image
        // files into app storage. Duplicate names make app-profile mappings
        // ambiguous and can also create duplicate spinner entries.
        val seenPresetNames = linkedSetOf<String>()
        for (index in 0 until presetArray.length()) {
            val obj = presetArray.optJSONObject(index) ?: continue
            val name = obj.optString("name").takeIf { it.isNotBlank() }
                ?: "Imported Preset ${index + 1}"
            if (!seenPresetNames.add(name)) {
                errors += "Duplicate preset name '$name' in bundle. Rename duplicates and export again."
            }
        }
        if (errors.isNotEmpty()) {
            return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = warnings,
                errors = errors
            )
        }

        val importedPresets = mutableListOf<LedPreset>()

        for (index in 0 until presetArray.length()) {
            cancelSignal?.throwIfCanceled()
            val obj = presetArray.optJSONObject(index) ?: continue
            importedPresets += parsePreset(context, obj, index, zipEntries, warnings, cancelSignal)
        }

        val mappings = parseMappings(context, manifest.optJSONObject("appProfileMappings"), warnings, cancelSignal)
        val livePolicies = parseLivePolicies(manifest.optJSONObject("livePolicies"), warnings)

        return ImportResult(
            presets = importedPresets,
            mappings = mappings,
            warnings = warnings,
            errors = errors,
            livePolicies = livePolicies
        )
    }

    /**
     * Parse the generic `livePolicies` block: a JSON object of effect name →
     * policy object. Unknown/garbage entries are skipped with a warning. Generic
     * — no per-app knowledge; any plugin can declare policies for the effects it owns.
     */
    private fun parseLivePolicies(
        obj: JSONObject?,
        warnings: MutableList<String>
    ): Map<String, LivePolicy> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, LivePolicy>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val effect = keys.next()
            val policyObj = obj.optJSONObject(effect)
            if (policyObj == null) {
                warnings += "livePolicies: entry for '$effect' is not an object; skipping."
                continue
            }
            out[effect] = LivePolicy.fromJson(policyObj)
        }
        return out
    }

    private fun parsePreset(
        context: Context,
        obj: JSONObject,
        index: Int,
        zipEntries: Map<String, ByteArray>,
        warnings: MutableList<String>,
        cancelSignal: CancellationSignal? = null
    ): LedPreset {
        cancelSignal?.throwIfCanceled()
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Imported Preset ${index + 1}"

        val animationTypeName = obj.optString("animationType", LedAnimationType.STATIC.name)
        val animationType = runCatching { LedAnimationType.fromStoredName(animationTypeName)!! }
            .onFailure { warnings += "Preset '$name': unknown animation '$animationTypeName', using STATIC." }
            .getOrDefault(LedAnimationType.STATIC)

        val profileName = obj.optString("performanceProfile", PerformanceProfile.HIGH.name)
        val profile = runCatching { PerformanceProfile.valueOf(profileName) }
            .onFailure { warnings += "Preset '$name': unknown profile '$profileName', using HIGH." }
            .getOrDefault(PerformanceProfile.HIGH)

        val iconName = obj.optString("icon", PresetIcon.defaultFor(animationType).name)
        val icon = PresetIcon.fromStoredName(iconName)

        val customEmoji = obj.optString("customEmoji").takeIf { it.isNotBlank() }

        val importedIconName = obj.optString("customImageFileName").takeIf { it.isNotBlank() }
        val customImageFileName = if (importedIconName != null) {
            cancelSignal?.throwIfCanceled()
            val archiveEntryName = "$ICONS_DIR_PREFIX$importedIconName"
            val iconBytes = zipEntries[archiveEntryName]
            if (iconBytes == null) {
                warnings += "Preset '$name': missing icon file '$importedIconName'."
                null
            } else {
                PresetImageStorage.importIconFromBytes(context, importedIconName, iconBytes)
                    ?: run {
                        warnings += "Preset '$name': could not import icon '$importedIconName'."
                        null
                    }
            }
        } else {
            null
        }

        val requestedAppIconPackage = obj.optString("appIconPackageName").takeIf { it.isNotBlank() }
        val appIconPackageName = requestedAppIconPackage?.takeIf {
            cancelSignal?.throwIfCanceled(); isPackageInstalled(context.packageManager, it)
        } ?: run {
            if (!requestedAppIconPackage.isNullOrBlank()) {
                warnings += "Preset '$name': assigned app icon package '$requestedAppIconPackage' is not installed, skipping."
            }
            null
        }

        val color = obj.optInt("color", -1)

        return LedPreset(
            name = name,
            animationType = animationType,
            performanceProfile = profile,
            color = color,
            rightColor = obj.optInt("rightColor", color),
            fadeEndColor = obj.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR),
            fadeEndRightColor = obj.optInt("fadeEndRightColor", obj.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR)),
            brightness = obj.optInt("brightness", 255).coerceIn(0, 255),
            speed = obj.optDouble("speed", 0.5).toFloat().coerceIn(0f, 1f),
            smoothness = obj.optDouble("smoothness", 0.5).toFloat().coerceIn(0f, 1f),
            sensitivity = obj.optDouble("sensitivity", 0.5).toFloat().coerceIn(0f, 1f),
            saturationBoost = obj.optDouble("saturationBoost", 0.0).toFloat().coerceIn(0f, 1f),
            useCustomSampling = obj.optBoolean("useCustomSampling", false),
            useSingleColor = obj.optBoolean("useSingleColor", false),
            breatheWhenCharging = obj.optBoolean("breatheWhenCharging", false),
            indicateChargingSpeed = obj.optBoolean("indicateChargingSpeed", false),
            flashWhenReady = obj.optBoolean("flashWhenReady", false),
            batteryLowColorOverride = obj.optInt("batteryLowColorOverride").takeIf { obj.has("batteryLowColorOverride") },
            batteryMidColorOverride = obj.optInt("batteryMidColorOverride").takeIf { obj.has("batteryMidColorOverride") },
            batteryHighColorOverride = obj.optInt("batteryHighColorOverride").takeIf { obj.has("batteryHighColorOverride") },
            cpuCoolColorOverride = obj.optInt("cpuCoolColorOverride").takeIf { obj.has("cpuCoolColorOverride") },
            cpuWarmColorOverride = obj.optInt("cpuWarmColorOverride").takeIf { obj.has("cpuWarmColorOverride") },
            cpuHotColorOverride = obj.optInt("cpuHotColorOverride").takeIf { obj.has("cpuHotColorOverride") },
            isAppProfileDefault = obj.optBoolean("isAppProfileDefault", false),
            ragnarokAccepted = obj.optBoolean("ragnarokAccepted", false),
            icon = icon,
            customEmoji = customEmoji,
            customImageFileName = customImageFileName,
            appIconPackageName = appIconPackageName
        )
    }

    private fun parseMappings(
        context: Context,
        mappingsObj: JSONObject?,
        warnings: MutableList<String>,
        cancelSignal: CancellationSignal? = null
    ): Map<String, String> {
        if (mappingsObj == null) return emptyMap()

        val mappings = linkedMapOf<String, String>()
        val iterator = mappingsObj.keys()
        while (iterator.hasNext()) {
            cancelSignal?.throwIfCanceled()
            val packageName = iterator.next()
            val presetName = mappingsObj.optString(packageName).takeIf { it.isNotBlank() }
            if (presetName == null) continue

            if (!isPackageInstalled(context.packageManager, packageName)) {
                warnings += "Mapping ignored: package '$packageName' is not installed."
                continue
            }
            mappings[packageName] = presetName
        }
        return mappings
    }

    private fun isPackageInstalled(packageManager: PackageManager, packageName: String): Boolean {
        return runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }
}

