package com.moonbench.bifrost

import android.content.Context
import com.moonbench.bifrost.tools.SamplingRegion
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores named pairs of sampling regions separately from the active sampling settings.
 * This store never changes the currently active regions unless a caller explicitly loads one.
 */
object SampleAreaProfileStore {
    private const val PREFS_NAME = "bifrost_sample_area_profiles"
    private const val KEY_PROFILES = "profiles_json"
    private const val MAX_PROFILES = 100
    private const val MAX_NAME_LENGTH = 64

    data class Profile(val name: String, val left: SamplingRegion, val right: SamplingRegion)

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun list(context: Context): List<Profile> {
        val raw = preferences(context).getString(KEY_PROFILES, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        val profiles = mutableListOf<Profile>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val left = SamplingRegion.decode(item.optString("left"))
            val right = SamplingRegion.decode(item.optString("right"))
            if (name.isNotEmpty() && left != null && right != null) {
                profiles += Profile(name, left, right)
            }
        }
        return profiles.sortedBy { it.name.lowercase() }
    }

    /** Adds or replaces a profile with the same name, case-insensitively. */
    fun save(context: Context, name: String, left: SamplingRegion, right: SamplingRegion): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || cleanName.length > MAX_NAME_LENGTH) return false
        val current = list(context).toMutableList()
        val existingIndex = current.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        if (existingIndex >= 0) {
            current[existingIndex] = Profile(cleanName, left.clamped(), right.clamped())
        } else {
            if (current.size >= MAX_PROFILES) return false
            current += Profile(cleanName, left.clamped(), right.clamped())
        }
        return preferences(context).edit().putString(KEY_PROFILES, encode(current)).commit()
    }

    fun delete(context: Context, name: String): Boolean {
        val current = list(context).filterNot { it.name.equals(name, ignoreCase = true) }
        return preferences(context).edit().putString(KEY_PROFILES, encode(current)).commit()
    }

    private fun encode(profiles: List<Profile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(JSONObject().apply {
                put("name", profile.name)
                put("left", profile.left.clamped().encode())
                put("right", profile.right.clamped().encode())
            })
        }
        return array.toString()
    }

    /** Atomically replaces the saved library after validating all names and the profile limit. */
    fun replaceAll(context: Context, profiles: List<Profile>): Boolean {
        if (profiles.size > MAX_PROFILES) return false
        val seen = mutableSetOf<String>()
        val clean = mutableListOf<Profile>()
        for (profile in profiles) {
            val name = profile.name.trim()
            val key = name.lowercase()
            if (name.isEmpty() || name.length > MAX_NAME_LENGTH || !seen.add(key)) return false
            clean += Profile(name, profile.left.clamped(), profile.right.clamped())
        }
        return preferences(context).edit().putString(KEY_PROFILES, encode(clean)).commit()
    }

}
