package app.oslo.repertoire.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.oslo.repertoire.remote.Support

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
    private companion object {
        const val LEGACY_DEFAULT_HOST = "192.168.1.50"
    }

    private val preferences = context.getSharedPreferences("shield", Context.MODE_PRIVATE)

    var host: String
        get() = preferences.getString("host", null) ?: ""
        set(value) { preferences.edit().putString("host", value.trim()).apply() }

    /** Legacy flag: true once any TV was paired. Kept so older builds still see the pairing. */
    var paired: Boolean
        get() = preferences.getBoolean("paired", false)
        set(value) { preferences.edit().putBoolean("paired", value).apply() }

    init {
        // Builds before multi-device support defaulted the host to the original Shield's address
        // without saving it. Keep that address for installs that paired against it.
        if (!preferences.contains("host") && paired) {
            preferences.edit().putString("host", LEGACY_DEFAULT_HOST).apply()
        }
        // They also paired exactly one TV, the saved host.
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

    /** ro.product.manufacturer of the TV in use, from the last debugging check. */
    var manufacturer: String
        get() = preferences.getString("manufacturer", "") ?: ""
        set(value) { preferences.edit().putString("manufacturer", value.trim()).apply() }

    var keepConnected: Boolean
        get() = preferences.getBoolean("keepConnected", true)
        set(value) { preferences.edit().putBoolean("keepConnected", value).apply() }
}

/** Starred titles, keyed by [favoriteKey] so they survive catalog refreshes. */
class FavoritesStore(context: Context) {
    private val preferences = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    val keys: Set<String>
        get() = preferences.getStringSet("keys", emptySet()).orEmpty().toSet()

    fun toggle(key: String): Boolean {
        val current = keys
        val on = key !in current
        preferences.edit().putStringSet("keys", if (on) current + key else current - key).apply()
        return on
    }
}

/** Appearance, onboarding and search history. */
class UiPrefs(context: Context) {
    private val preferences = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var themeMode: String
        get() = preferences.getString("themeMode", "System") ?: "System"
        set(value) { preferences.edit().putString("themeMode", value).apply() }

    var dynamicColor: Boolean
        // Brand amber by default; wallpaper colours are opt-in.
        get() = preferences.getBoolean("dynamicColor", false)
        set(value) { preferences.edit().putBoolean("dynamicColor", value).apply() }

    /** True once setup was finished or skipped. */
    var setupDone: Boolean
        get() = preferences.getBoolean("setupDone", false)
        set(value) { preferences.edit().putBoolean("setupDone", value).apply() }

    /** Preferred audio language code ("EN", "FR"); null = follow the device language. */
    var audioLanguage: String?
        get() = preferences.getString("audioLanguage", null)
        set(value) { preferences.edit().putString("audioLanguage", value).apply() }

    /** Movies and series as a poster grid (true) or as rows. */
    var posterGrid: Boolean
        get() = preferences.getBoolean("posterGrid", true)
        set(value) { preferences.edit().putBoolean("posterGrid", value).apply() }

    var prefer4k: Boolean
        get() = preferences.getBoolean("prefer4k", false)
        set(value) { preferences.edit().putBoolean("prefer4k", value).apply() }

    var recentSearches: List<String>
        get() = preferences.getString("recentSearches", "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(value) { preferences.edit().putString("recentSearches", value.take(8).joinToString("\n")).apply() }
}
