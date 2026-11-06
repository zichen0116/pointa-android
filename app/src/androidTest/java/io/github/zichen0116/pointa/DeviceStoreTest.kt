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
        val p = Preferences(language = "en", leftMargins = Margins(left = .1f), rightMargins = Margins(bottom = .2f), autoReconnect = false)
        try {
            store.save(p)
            assertEquals(p, store.load())
            raw.edit().putString("v1", "broken").commit()
            assertThrows(Exception::class.java) { store.load() }
            assertEquals("broken", raw.getString("v1", ""))
        } finally { raw.edit().clear().commit() }
    }
}
