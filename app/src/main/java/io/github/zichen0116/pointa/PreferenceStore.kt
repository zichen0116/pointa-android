package io.github.zichen0116.pointa

import android.content.Context
import io.github.zichen0116.pointa.ui.Margins
import io.github.zichen0116.pointa.ui.Preferences
import org.json.JSONObject

class PreferenceStore(context: Context) {
    private val storage = context.getSharedPreferences("pointa_preferences", Context.MODE_PRIVATE)
    fun load(): Preferences {
        val value = storage.getString("v1", null) ?: return Preferences()
        val j = JSONObject(value)
        fun margins(name: String): Margins {
            val m = j.getJSONObject(name)
            return Margins(m.getDouble("left").toFloat(), m.getDouble("right").toFloat(), m.getDouble("top").toFloat(), m.getDouble("bottom").toFloat())
        }
        return Preferences(theme = j.getInt("theme"), appearance = j.getString("appearance"), language = j.getString("language"),
            mode = j.getString("mode"), leftHand = j.getBoolean("leftHand"), leftMargins = margins("leftMargins"), rightMargins = margins("rightMargins"),
            fullRelative = j.getBoolean("fullRelative"), trailEnabled = j.getBoolean("trailEnabled"), trailMs = j.getInt("trailMs"), sensitivity = j.getInt("sensitivity"),
            laserColor = j.getString("laserColor"), laserSize = j.getString("laserSize"), smoothing = j.getString("smoothing"), volumeEnabled = j.getBoolean("volumeEnabled"),
            prevVolume = j.getString("prevVolume"), nextVolume = j.getString("nextVolume"), keepScreenOn = j.getBoolean("keepScreenOn"), haptics = j.getBoolean("haptics"),
            autoReconnect = j.getBoolean("autoReconnect"), reduceMotion = j.getBoolean("reduceMotion"), onboardingDone = j.getBoolean("onboardingDone")).also(::validate)
    }

    fun save(p: Preferences) {
        validate(p)
        fun margins(m: Margins) = JSONObject().put("left", m.left).put("right", m.right).put("top", m.top).put("bottom", m.bottom)
        val json = JSONObject().put("theme", p.theme).put("appearance", p.appearance).put("language", p.language).put("mode", p.mode)
            .put("leftHand", p.leftHand).put("leftMargins", margins(p.leftMargins)).put("rightMargins", margins(p.rightMargins))
            .put("fullRelative", p.fullRelative).put("trailEnabled", p.trailEnabled).put("trailMs", p.trailMs).put("sensitivity", p.sensitivity)
            .put("laserColor", p.laserColor).put("laserSize", p.laserSize).put("smoothing", p.smoothing).put("volumeEnabled", p.volumeEnabled)
            .put("prevVolume", p.prevVolume).put("nextVolume", p.nextVolume).put("keepScreenOn", p.keepScreenOn).put("haptics", p.haptics)
            .put("autoReconnect", p.autoReconnect).put("reduceMotion", p.reduceMotion).put("onboardingDone", p.onboardingDone)
        check(storage.edit().putString("v1", json.toString()).commit()) { "Could not save preferences" }
    }

    private fun validate(p: Preferences) {
        require(p.theme in 0..5 && p.appearance in setOf("dark", "light", "system") && p.language in setOf("zh", "en", "system"))
        require(p.mode in setOf("one", "full", "slides", "air") && p.trailMs in 100..1200 && p.sensitivity in 10..100)
        require(p.laserColor.matches(Regex("#[0-9a-fA-F]{6}")) && p.laserSize in setOf("small", "medium", "large") && p.smoothing in setOf("low", "standard", "high"))
        require(p.prevVolume in setOf("0", "+", "-") && p.nextVolume in setOf("0", "+", "-"))
        require(p.prevVolume == "0" || p.prevVolume != p.nextVolume)
        for (m in listOf(p.leftMargins, p.rightMargins)) {
            require(listOf(m.left, m.right, m.top, m.bottom).all { it.isFinite() && it in 0f..1f })
            require(m.left + m.right <= .5801f && m.top + m.bottom <= .6001f)
        }
    }
}
