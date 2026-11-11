package app.englishreviewer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.settingsStore by preferencesDataStore("settings")
data class AppSettings(val hour: Int = 8, val minute: Int = 30, val reminders: Boolean = false, val endpoint: String = "", val encryptedToken: String = "")

class Settings(private val context: Context) {
    private val hour = intPreferencesKey("hour")
    private val minute = intPreferencesKey("minute")
    private val reminders = booleanPreferencesKey("reminders")
    private val endpoint = stringPreferencesKey("endpoint")
    private val token = stringPreferencesKey("encrypted_token")
    val flow = context.settingsStore.data.map { AppSettings(it[hour] ?: 8, it[minute] ?: 30, it[reminders] ?: false, it[endpoint].orEmpty(), it[token].orEmpty()) }
    suspend fun saveTime(h: Int, m: Int) { require(h in 0..23 && m in 0..59); context.settingsStore.edit { it[hour] = h; it[minute] = m } }
    suspend fun saveReminders(enabled: Boolean) { context.settingsStore.edit { it[reminders] = enabled } }
    suspend fun saveServer(url: String, secret: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isNotBlank()) {
            val uri = URI(clean)
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "서버 주소는 https://로 시작하는 주소를 입력해 주세요." }
        }
        val encrypted = if (secret.isBlank()) "" else TokenVault.encrypt(secret.trim())
        context.settingsStore.edit { it[endpoint] = clean; it[token] = encrypted }
    }
    fun decrypt(settings: AppSettings): String = if (settings.encryptedToken.isEmpty()) "" else TokenVault.decrypt(settings.encryptedToken)
}

private object TokenVault {
    private const val alias = "english-reviewer-token"
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
    }
}
