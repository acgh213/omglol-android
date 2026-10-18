package lol.omg.pocket.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import lol.omg.pocket.data.Credentials
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

interface CredentialStore {
    fun load(): Credentials?
    fun save(credentials: Credentials)
    fun clear()
}

/** All callers use Dispatchers.IO. No plaintext preferences or backup fallback. */
class KeystoreCredentialStore(context: Context) : CredentialStore {
    private val prefs = context.getSharedPreferences("credential_vault", Context.MODE_PRIVATE)
    private val alias = "omglol-pocket.credentials.v1"
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(create: Boolean): SecretKey? {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized override fun load(): Credentials? {
        val record = prefs.getString("record", null) ?: return null
        val obj = JSONObject(record)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false) ?: error("Missing Keystore key"), GCMParameterSpec(128, Base64.decode(obj.getString("iv"), Base64.NO_WRAP)))
        val plaintext = cipher.doFinal(Base64.decode(obj.getString("ciphertext"), Base64.NO_WRAP))
        return try {
            val payload = JSONObject(String(plaintext, Charsets.UTF_8))
            Credentials(payload.getString("email"), payload.getString("key"))
        } finally { plaintext.fill(0) }
    }
    @Synchronized override fun save(credentials: Credentials) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val plaintext = JSONObject().put("email", credentials.email).put("key", credentials.apiKey).toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
        val record = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).toString()
        check(prefs.edit().putString("record", record).commit()) { "Cannot persist credentials" }
    }
    @Synchronized override fun clear() {
        // Remove ciphertext first. Deleting the key makes any stale ciphertext unusable.
        val removed = prefs.edit().clear().commit()
        keyStore().deleteEntry(alias)
        check(removed) { "Cannot clear credential storage" }
    }
}
