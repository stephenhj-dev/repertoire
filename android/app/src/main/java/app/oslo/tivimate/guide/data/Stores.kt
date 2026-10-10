package app.oslo.tivimate.guide.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class PortalLogin(val url: String, val username: String, val password: String)

class PortalStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "portal",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): PortalLogin? {
        val url = preferences.getString("url", null) ?: return null
        val username = preferences.getString("username", null) ?: return null
        val password = preferences.getString("password", null) ?: return null
        if (url.isBlank() || username.isBlank() || password.isBlank()) return null
        return PortalLogin(url, username, password)
    }

    fun save(login: PortalLogin) {
        preferences.edit()
            .putString("url", PortalUrl.normalize(login.url))
            .putString("username", login.username.trim())
            .putString("password", login.password)
            .apply()
    }
}

// The preference file keeps its original "shield" name so existing installs keep their host and pairing.
class TvStore(context: Context) {
    private val preferences = context.getSharedPreferences("shield", Context.MODE_PRIVATE)

    var host: String
        get() = preferences.getString("host", "192.168.1.50") ?: "192.168.1.50"
        set(value) { preferences.edit().putString("host", value.trim()).apply() }

    var paired: Boolean
        get() = preferences.getBoolean("paired", false)
        set(value) { preferences.edit().putBoolean("paired", value).apply() }

    var name: String
        get() = preferences.getString("name", "") ?: ""
        set(value) { preferences.edit().putString("name", value.trim()).apply() }

    var model: String
        get() = preferences.getString("model", "") ?: ""
        set(value) { preferences.edit().putString("model", value.trim()).apply() }

    var keepConnected: Boolean
        get() = preferences.getBoolean("keepConnected", true)
        set(value) { preferences.edit().putBoolean("keepConnected", value).apply() }
}
