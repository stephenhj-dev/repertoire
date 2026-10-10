package app.oslo.tivimate.guide.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.oslo.tivimate.guide.remote.Support

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

    /** Legacy flag: true once any TV was paired. Kept so older builds still see the pairing. */
    var paired: Boolean
        get() = preferences.getBoolean("paired", false)
        set(value) { preferences.edit().putBoolean("paired", value).apply() }

    init {
        // Builds before multi-device support paired exactly one TV, the saved host.
        if (!preferences.contains("pairedHosts") && paired && host.isNotBlank()) {
            preferences.edit().putStringSet("pairedHosts", setOf(host)).apply()
        }
    }

    val pairedHosts: Set<String>
        get() = preferences.getStringSet("pairedHosts", emptySet()).orEmpty().toSet()

    fun isPaired(address: String): Boolean = address.trim() in pairedHosts

    fun markPaired(address: String) {
        preferences.edit()
            .putStringSet("pairedHosts", pairedHosts + address.trim())
            .putBoolean("paired", true)
            .apply()
    }

    /** Capabilities last seen for the saved host; reset when the host changes. */
    var remoteV2: Support
        get() = Support.parse(preferences.getString("remoteV2", null))
        set(value) { preferences.edit().putString("remoteV2", value.name).apply() }

    var adb: Support
        get() = Support.parse(preferences.getString("adb", null))
        set(value) { preferences.edit().putString("adb", value.name).apply() }

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
