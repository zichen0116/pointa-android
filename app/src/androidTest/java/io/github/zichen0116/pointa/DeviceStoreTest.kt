package io.github.zichen0116.pointa

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.zichen0116.pointa.protocol.KnownComputer
import io.github.zichen0116.pointa.ui.Margins
import io.github.zichen0116.pointa.ui.Preferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun keystoreRoundTripRevokeAndCorruptionPreserveTheOriginal() {
        val preferences = context.getSharedPreferences("pointa_trust", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val store = SecureDeviceStore(context)
        val device = KnownComputer("a".repeat(32), "Test computer", "10.0.2.2", 32145, "b".repeat(64), "c".repeat(32), "A".repeat(43), 1234)
        try {
            store.remember(device)
            assertEquals(device, SecureDeviceStore(context).load().single())
            val encrypted = preferences.getString("devices", "")!!
            assertFalse(encrypted.contains(device.credential))
            assertFalse(encrypted.contains(device.name))
            assertTrue(store.forget(device.serverId).isEmpty())
            assertTrue("A delayed connection update cannot restore a forgotten device", store.touch(device.copy(lastConnectedAt = 2345)).isEmpty())
            val replacement = device.copy(deviceId = "d".repeat(32), credential = "Q" + "A".repeat(42))
            store.remember(replacement)
            assertEquals("An old connection cannot overwrite a new pairing", replacement, store.touch(device.copy(lastConnectedAt = 3456)).single())
            preferences.edit().putString("devices", "damaged-record").commit()
            assertThrows(Exception::class.java) { store.load() }
            assertEquals("damaged-record", preferences.getString("devices", ""))
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun preferencesPersistBothHandsAndNeverOverwriteCorruptInput() {
        val raw = context.getSharedPreferences("pointa_preferences", Context.MODE_PRIVATE)
        val store = PreferenceStore(context)
        val p = Preferences(language = "en", leftMargins = Margins(left = .1f), rightMargins = Margins(bottom = .2f))
        try {
            store.save(p)
            assertEquals(p, store.load())
            val legacy = org.json.JSONObject(raw.getString("v1", null)!!).put("haptics", false).put("laserColor", "#FFFFFF").put("theme", 2)
                .put("autoReconnect", true).put("mode", "one").put("fullRelative", false).also { it.remove("touchDefaultsVersion") }
            raw.edit().putString("v1", legacy.toString()).commit()
            assertEquals("#32B894", store.load().laserColor)
            assertEquals(p.leftMargins, store.load().leftMargins)
            assertEquals("full", store.load().mode)
            assertTrue(store.load().fullRelative)
            assertFalse(org.json.JSONObject(raw.getString("v1", null)!!).has("autoReconnect"))
            store.save(store.load().copy(mode = "one", fullRelative = false))
            assertEquals("one", store.load().mode)
            assertFalse(store.load().fullRelative)
            raw.edit().putString("v1", "broken").commit()
            assertThrows(Exception::class.java) { store.load() }
            assertEquals("broken", raw.getString("v1", ""))
        } finally { raw.edit().clear().commit() }
    }

    @Test fun oldFullSizeDefaultsMigrateOnceAndCustomLayoutsSurvive() {
        val raw = context.getSharedPreferences("pointa_preferences", Context.MODE_PRIVATE)
        val store = PreferenceStore(context)
        val customRight = Margins(left = .12f, top = .1f, bottom = .2f)
        try {
            store.save(Preferences(leftMargins = Margins(), rightMargins = customRight))
            val legacy = org.json.JSONObject(raw.getString("v1", null)!!).also { it.remove("layoutDefaultsVersion") }
            raw.edit().putString("v1", legacy.toString()).commit()
            assertEquals(Preferences().leftMargins, store.load().leftMargins)
            assertEquals(customRight, store.load().rightMargins)
            val defaults = Preferences()
            assertEquals(defaults.rightMargins.left, defaults.leftMargins.right)
            assertEquals(defaults.rightMargins.right, defaults.leftMargins.left)
            assertEquals(defaults.rightMargins.top, defaults.leftMargins.top)
            assertEquals(defaults.rightMargins.bottom, defaults.leftMargins.bottom)
            store.save(store.load().copy(leftMargins = Margins()))
            assertEquals("Later explicit full-size adjustments must survive", Margins(), store.load().leftMargins)
        } finally { raw.edit().clear().commit() }
    }
}
