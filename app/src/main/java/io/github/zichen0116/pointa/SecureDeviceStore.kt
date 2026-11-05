package io.github.zichen0116.pointa

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.zichen0116.pointa.protocol.KnownComputer
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only authenticated computer records enter this store. A corrupt blob is preserved, never reset implicitly. */
class SecureDeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences("pointa_trust", Context.MODE_PRIVATE)
    private val alias = "pointa.trust.v1"
    private val aad = "Pointa:trusted-computers:v1".toByteArray()

    @Synchronized fun load(): List<KnownComputer> {
        val saved = preferences.getString("devices", null) ?: return emptyList()
        val bytes = Base64.decode(saved, Base64.NO_WRAP)
        require(bytes.size in 29..1_048_576) { "Invalid encrypted device record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        val plaintext = cipher.doFinal(bytes, 12, bytes.size - 12)
        try {
            val array = JSONArray(String(plaintext, Charsets.UTF_8))
            require(array.length() <= 100)
            return (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                KnownComputer(json.getString("serverId"), json.getString("name"), json.getString("host"),
                    json.getInt("port"), json.getString("certificateSha256"), json.getString("deviceId"),
                    json.getString("credential"), json.getLong("lastConnectedAt")).also { validate(it) }
            }.also { require(it.map(KnownComputer::serverId).distinct().size == it.size) }
        } finally { plaintext.fill(0) }
    }

    @Synchronized fun remember(computer: KnownComputer): List<KnownComputer> {
        validate(computer)
        val updated = (load().filterNot { it.serverId == computer.serverId } + computer).sortedByDescending { it.lastConnectedAt }
        save(updated)
        return updated
    }

    @Synchronized fun forget(serverId: String): List<KnownComputer> = load().filterNot { it.serverId == serverId }.also(::save)

    private fun save(computers: List<KnownComputer>) {
        require(computers.size <= 100)
        val array = JSONArray()
        computers.forEach { computer ->
            array.put(JSONObject().put("serverId", computer.serverId).put("name", computer.name).put("host", computer.host)
                .put("port", computer.port).put("certificateSha256", computer.certificateSha256)
                .put("deviceId", computer.deviceId).put("credential", computer.credential).put("lastConnectedAt", computer.lastConnectedAt))
        }
        val plaintext = array.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        cipher.updateAAD(aad)
        val ciphertext = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
        check(preferences.edit().putString("devices", Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)).commit()) {
            "Could not save trusted computers"
        }
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create && !preferences.contains("devices")) { "Device encryption key is unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    private fun validate(computer: KnownComputer) {
        require(computer.serverId.matches(Regex("[a-f0-9]{32}")) && computer.deviceId.matches(Regex("[a-f0-9]{32}")))
        require(computer.certificateSha256.matches(Regex("[a-f0-9]{64}")) && computer.credential.matches(Regex("[A-Za-z0-9_-]{43}")))
        require(computer.name.isNotBlank() && computer.name.length <= 64 && computer.name.none(Char::isISOControl))
        require(computer.port in 1..65535 && computer.lastConnectedAt >= 0)
        val octets = computer.host.split('.').map { it.toIntOrNull() ?: -1 }
        require(octets.size == 4 && octets.all { it in 0..255 })
        require(octets[0] == 10 || octets[0] == 127 || (octets[0] == 172 && octets[1] in 16..31) || (octets[0] == 192 && octets[1] == 168))
    }
}
