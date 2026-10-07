package com.moonbench.bifrost.rp5

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent store for named Fine Tune profiles and preset bindings. */
class FineTuneStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<FineTuneProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) runCatching {
                fromJson(array.getJSONObject(i))
            }.getOrNull()?.let(::add)
        }
    }

    fun find(name: String): FineTuneProfile? =
        list().firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun save(profile: FineTuneProfile) {
        writeProfiles(list().filterNot { it.name.equals(profile.name, true) } + profile)
        if (getCurrentName() == null) setCurrent(profile.name)
        if (getDefaultName() == null) setDefault(profile.name)
    }

    fun delete(name: String) {
        val profiles = list().filterNot { it.name.equals(name, true) }
        writeProfiles(profiles)
        if (getCurrentName()?.equals(name, true) == true) setCurrent(profiles.firstOrNull()?.name)
        if (getDefaultName()?.equals(name, true) == true) setDefault(profiles.firstOrNull()?.name)
    }

    fun setCurrent(name: String?) = prefs.edit().putString(KEY_CURRENT, name).apply()
    fun current(): FineTuneProfile? = getCurrentName()?.let(::find)
    fun getCurrentName(): String? = prefs.getString(KEY_CURRENT, null)

    fun setDefault(name: String?) = prefs.edit().putString(KEY_DEFAULT, name).apply()
    fun default(): FineTuneProfile? = getDefaultName()?.let(::find)
    fun getDefaultName(): String? = prefs.getString(KEY_DEFAULT, null)

    fun setProfileBinding(presetName: String, fineTuneName: String?) {
        val map = getProfileBindings().toMutableMap()
        if (fineTuneName.isNullOrBlank()) map.remove(presetName) else map[presetName] = fineTuneName
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        prefs.edit().putString(KEY_PROFILE_BINDINGS, obj.toString()).apply()
    }

    fun getProfileBindings(): Map<String, String> {
        val raw = prefs.getString(KEY_PROFILE_BINDINGS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap { obj.keys().forEach { key -> put(key, obj.optString(key)) } }
        }.getOrDefault(emptyMap())
    }

    fun fineTuneForProfile(presetName: String): FineTuneProfile? =
        getProfileBindings()[presetName]?.let(::find)

    private fun writeProfiles(profiles: List<FineTuneProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun toJson(profile: FineTuneProfile) = JSONObject().apply {
        put("name", profile.name)
        put("version", profile.version)
        put("colourMode", profile.colourMode.name)
        put("left", regionJson(profile.leftViewArea))
        put("right", regionJson(profile.rightViewArea))
    }

    private fun regionJson(r: NormalizedRegion) = JSONObject().apply {
        put("centerX", r.centerX); put("centerY", r.centerY); put("size", r.size)
    }

    private fun fromJson(obj: JSONObject) = FineTuneProfile(
        name = obj.getString("name"),
        leftViewArea = regionFromJson(obj.getJSONObject("left")),
        rightViewArea = regionFromJson(obj.getJSONObject("right")),
        colourMode = runCatching {
            ThumbstickColourMode.valueOf(obj.optString("colourMode", ThumbstickColourMode.AVERAGE.name))
        }.getOrDefault(ThumbstickColourMode.AVERAGE),
        version = obj.optInt("version", FineTuneProfile.CURRENT_VERSION),
    )

    private fun regionFromJson(obj: JSONObject) = NormalizedRegion(
        obj.getDouble("centerX").toFloat(),
        obj.getDouble("centerY").toFloat(),
        obj.getDouble("size").toFloat(),
    )

    companion object {
        private const val PREFS = "thumbstick_fine_tune"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_CURRENT = "current_profile"
        private const val KEY_DEFAULT = "default_profile"
        private const val KEY_PROFILE_BINDINGS = "profile_bindings"
    }
}