package app.oslo.repertoire

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.oslo.repertoire.data.CatalogDatabase
import app.oslo.repertoire.data.CatalogSync
import app.oslo.repertoire.data.FavoritesStore
import app.oslo.repertoire.data.PortalAccount
import app.oslo.repertoire.data.PortalCheck
import app.oslo.repertoire.data.PortalLogin
import app.oslo.repertoire.data.PortalStore
import app.oslo.repertoire.data.TitleRow
import app.oslo.repertoire.data.TvStore
import app.oslo.repertoire.data.UiPrefs
import app.oslo.repertoire.data.countryLabel
import app.oslo.repertoire.data.favoriteKey
import app.oslo.repertoire.data.likeNeedle
import app.oslo.repertoire.data.portalErrorMessage
import app.oslo.repertoire.ui.theme.ThemeMode
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import app.oslo.repertoire.remote.Keys
import app.oslo.repertoire.remote.TvRemote
import app.oslo.repertoire.remote.NavTransport
import app.oslo.repertoire.remote.Support
import app.oslo.repertoire.remote.TvDevice
import app.oslo.repertoire.remote.navigationTransport
import app.oslo.repertoire.remote.probeRemoteV2
import app.oslo.repertoire.remote.remoteSupport
import app.oslo.repertoire.remote.scanTvs
import app.oslo.repertoire.tune.AdbRemote
import app.oslo.repertoire.tune.TiviMate
import app.oslo.repertoire.tune.TvAdb
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Section { All, Tv, Movies, Series, Favorites }
enum class BucketKind { Country, Package, Section, Category }

/** Scope of the global search screen. */
enum class SearchScope(val kind: String?, val label: String) {
    All(null, "All"), Live("live", "Live TV"), Movies("movie", "Movies"), Shows("show", "Shows");
}

@Immutable
sealed interface GuideRow {
    data class Bucket(val id: String, val label: String, val count: Int, val kind: BucketKind) : GuideRow
    data class Title(
        val kind: String,
        val name: String,
        val caption: String = "",
        val id: Long = 0,
        val icon: String? = null,
    ) : GuideRow
    data class Banner(val name: String) : GuideRow
    data class More(val section: Section, val label: String) : GuideRow
    data class Note(val text: String) : GuideRow
}

/** A channel or title being tuned on the TV. */
@Immutable
data class TuneState(val name: String, val step: String)

/** One-shot message for a snackbar. [retry] names a title that can be tuned again. */
data class UiMessage(val text: String, val retry: String? = null, val openDevices: Boolean = false)

/**
 * What the Guide shows, published in one piece so a transition never pairs a new title with
 * old rows. [key] changes when the user moves to another list (not while filtering it).
 */
@Immutable
data class GuideView(
    val key: String = "",
    val section: Section = Section.Tv,
    val depth: Int = 0,
    val title: String = "",
    val rows: List<GuideRow> = emptyList(),
    val filtering: Boolean = false,
)

@Immutable
data class CatalogCounts(val live: Int = 0, val movies: Int = 0, val shows: Int = 0) {
    val total: Int get() = live + movies + shows
}

private data class Drill(
    val country: Boolean,
    val id: String,
    val label: String,
    val parentLabel: String,
    val section: String? = null,
    val inSection: Boolean = false,
)

class GuideViewModel(app: Application) : AndroidViewModel(app) {
    private val portalStore = PortalStore(app)
    private val tvStore = TvStore(app)
    private val database = CatalogDatabase.get(app)
    private val sync = CatalogSync(database)
    val remote = TvRemote(app)
    private val adbRemote = AdbRemote(app.filesDir)
    private val uiPrefs = UiPrefs(app)
    private val favoritesStore = FavoritesStore(app)
    private val meta = app.getSharedPreferences("guide-meta", android.content.Context.MODE_PRIVATE)
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    private var lastMessage = "" to 0L

    /** Snackbar messages; collect once from the UI. */
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    var themeMode by mutableStateOf(ThemeMode.parse(uiPrefs.themeMode))
        private set
    var dynamicColor by mutableStateOf(uiPrefs.dynamicColor)
        private set

    /** Shown on first launch until a portal and a TV are set, or the user skips setup. */
    val setupNeeded: Boolean = !uiPrefs.setupDone && (portalStore.load() == null || tvStore.host.isBlank())

    var hasPortal by mutableStateOf(false)
        private set
    var account by mutableStateOf<PortalAccount?>(null)
        private set
    var signingIn by mutableStateOf(false)
        private set
    var portalError by mutableStateOf<String?>(null)
    var syncStep by mutableStateOf("")
        private set
    var syncError by mutableStateOf<String?>(null)
        private set
    var counts by mutableStateOf(CatalogCounts())
        private set
    var loading by mutableStateOf(true)
        private set
    /** 0 = section root, 1 = a country/group/category, 2 = a section inside a country. */
    var depth by mutableStateOf(0)
        private set
    var view by mutableStateOf(GuideView())
        private set
    var tune by mutableStateOf<TuneState?>(null)
        private set
    var favorites by mutableStateOf(favoritesStore.keys)
        private set
    var devicesNote by mutableStateOf("")
        private set
    var pinError by mutableStateOf<String?>(null)
    var pairing by mutableStateOf(false)
        private set

    var searchText by mutableStateOf("")
        private set
    var searchScope by mutableStateOf(SearchScope.All)
        private set
    var searchResults by mutableStateOf<List<GuideRow>>(emptyList())
        private set
    var searchBusy by mutableStateOf(false)
        private set
    var recentSearches by mutableStateOf(uiPrefs.recentSearches)
        private set
    private var globalSearchJob: Job? = null
    private var reloadJob: Job? = null

    var devices by mutableStateOf(savedDevices())
    var scanning by mutableStateOf(false)
    var adbOk by mutableStateOf<Boolean?>(null)
    var adbDetail by mutableStateOf("Not checked yet")
    var checkingAdb by mutableStateOf(false)
    /** The TV in use. [host] is the editable address field and can differ while typing. */
    var selectedHost by mutableStateOf(tvStore.host)
    var remoteV2 by mutableStateOf(tvStore.remoteV2)
    var adbSupport by mutableStateOf(tvStore.adb)
    var fireTv by mutableStateOf(false)
    private var tivimatePackage = TiviMate.PLAYER
    var section by mutableStateOf(Section.Tv)
    var query by mutableStateOf("")
    var rows by mutableStateOf<List<GuideRow>>(emptyList())
    var heading by mutableStateOf("Countries")
    var status by mutableStateOf("")
    var syncing by mutableStateOf(false)
    var portalUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var host by mutableStateOf(tvStore.host)
    var pin by mutableStateOf("")
    var paired by mutableStateOf(tvStore.isPaired(tvStore.host))
    var linked by mutableStateOf(tvStore.isPaired(tvStore.host) && tvStore.keepConnected)

    /** Remote v2 when paired and available, ADB key events otherwise (e.g. Fire TV). */
    val transport: NavTransport get() = navigationTransport(remoteV2, paired, adbSupport)

    val deviceName: String
        get() = devices.firstOrNull { it.host == selectedHost }?.name?.takeIf { it != selectedHost }
            ?: tvStore.name.takeIf { it.isNotBlank() && selectedHost.isNotBlank() } ?: "TV"

    val deviceModel: String
        get() = devices.firstOrNull { it.host == selectedHost }?.model?.takeIf { it.isNotBlank() } ?: tvStore.model

    val hasTv: Boolean get() = selectedHost.isNotBlank()

    var drilled by mutableStateOf(false)
    private var drill: Drill? = null
    private var searchJob: Job? = null
    private var reconnectJob: Job? = null
    private var repeatJob: Job? = null
    private var checkJob: Job? = null
    private var retryMs = 1_000L
    private val remoteGate = Mutex()
    private var pageOffset = 0
    private var endReached = true
    private var loadingMore = false

    init {
        val saved = portalStore.load()
        if (saved != null) {
            portalUrl = saved.url
            username = saved.username
            hasPortal = true
        }
        host = tvStore.host
        remote.rememberHost(host)
        remote.onSessionLost = { scheduleReconnect() }
        if (paired && tvStore.keepConnected) connect()
        viewModelScope.launch {
            if (!meta.getBoolean("sectionsBackfilled", false)) {
                runCatching { sync.backfillSections() }
                meta.edit().putBoolean("sectionsBackfilled", true).apply()
            }
            // Catalogs synced before v3 have no logos; fetch them once.
            if (saved != null && (database.catalog().titleCount() == 0 || !meta.getBoolean("iconsFetched", false))) refresh()
            reload()
            loadCounts()
        }
    }

    fun onDevicesShown() = checkDevice()

    fun notify(text: String, retry: String? = null, openDevices: Boolean = false) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        // Holding a key on an offline TV would otherwise queue the same error many times.
        if (lastMessage.first == text && now - lastMessage.second < 3_000) return
        lastMessage = text to now
        _messages.trySend(UiMessage(text, retry, openDevices))
    }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        uiPrefs.themeMode = mode.name
    }

    fun setDynamic(on: Boolean) {
        dynamicColor = on
        uiPrefs.dynamicColor = on
    }

    fun finishSetup() {
        uiPrefs.setupDone = true
    }

    fun isFavorite(kind: String, name: String): Boolean = favoriteKey(kind, name) in favorites

    fun toggleFavorite(kind: String, name: String) {
        val on = favoritesStore.toggle(favoriteKey(kind, name))
        favorites = favoritesStore.keys
        notify(if (on) "Added to favorites" else "Removed from favorites")
        if (section == Section.Favorites) reload()
    }

    fun selectDevice(device: TvDevice) {
        val clean = device.host.trim()
        if (clean.isBlank()) return
        val switched = clean != tvStore.host
        host = clean
        tvStore.host = clean
        tvStore.name = device.name.takeIf { it != clean }.orEmpty()
        tvStore.model = device.model
        if (switched) {
            reconnectJob?.cancel()
            remote.disconnect()
            adbRemote.close()
            remoteV2 = if (device.advertisesRemote) Support.Yes else Support.Unknown
            adbSupport = Support.Unknown
            tvStore.remoteV2 = remoteV2
            tvStore.adb = adbSupport
            fireTv = device.fireTv
            adbOk = null
            selectedHost = clean
            paired = tvStore.isPaired(clean)
            linked = paired && tvStore.keepConnected
        }
        remote.rememberHost(clean)
        devices = if (devices.any { it.host == clean }) {
            devices.map { if (it.host == clean) device.copy(host = clean) else it }
        } else {
            devices + device.copy(host = clean)
        }
        checkDevice()
        if (switched && linked) connect()
    }

    fun useAddress(address: String) {
        val clean = address.trim()
        if (clean.isBlank()) return
        selectDevice(devices.firstOrNull { it.host == clean } ?: TvDevice(host = clean, name = clean))
    }

    private fun savedDevices(): List<TvDevice> {
        val saved = tvStore.host
        if (saved.isBlank()) return emptyList()
        return listOf(TvDevice(host = saved, name = tvStore.name.ifBlank { saved }, model = tvStore.model))
    }

    /** Probes Remote v2 and ADB on the selected TV and records what it supports. */
    fun checkDevice() {
        val address = tvStore.host
        if (address.isBlank()) {
            adbDetail = "Find a TV or enter its address."
            adbOk = null
            return
        }
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            checkingAdb = true
            try {
                adbDetail = "Checking $address"
                val directory = getApplication<Application>().filesDir
                val remoteProbe = async { probeRemoteV2(address) }
                val adbProbe = async(Dispatchers.IO) { TvAdb.probe(directory, address) }
                val advertised = devices.firstOrNull { it.host == address }?.advertisesRemote == true
                val remoteResult = remoteSupport(advertised, remoteProbe.await())
                val result = adbProbe.await()
                if (address != tvStore.host) return@launch
                // A timeout says nothing new; keep an earlier positive answer.
                if (remoteResult != Support.Unknown || remoteV2 == Support.Unknown) remoteV2 = remoteResult
                if (result.support != Support.Unknown || adbSupport == Support.Unknown) adbSupport = result.support
                tvStore.remoteV2 = remoteV2
                tvStore.adb = adbSupport
                fireTv = result.fireTv || devices.firstOrNull { it.host == address }?.fireTv == true
                if (result.ok && result.model.isNotBlank()) {
                    tvStore.model = result.model
                    devices = devices.map { if (it.host == address) it.copy(model = result.model, fireTv = fireTv) else it }
                }
                result.tivimate?.let { tivimatePackage = it }
                adbOk = result.ok
                adbDetail = result.detail
            } finally {
                checkingAdb = false
            }
        }
    }

    fun select(section: Section) {
        this.section = section
        drill = null
        drilled = false
        reload()
    }

    fun onQuery(value: String) {
        query = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            reload()
        }
    }

    fun open(row: GuideRow) {
        when (row) {
            is GuideRow.Bucket -> {
                query = ""
                val place = drill
                drill = if (row.kind == BucketKind.Section && place != null) {
                    place.copy(inSection = true, section = row.id.ifEmpty { null }, label = row.label)
                } else {
                    Drill(
                        country = row.kind == BucketKind.Country,
                        id = row.id,
                        label = row.label,
                        parentLabel = row.label,
                    )
                }
                drilled = true
                reload()
            }
            is GuideRow.Title -> play(row.name)
            is GuideRow.More -> select(row.section)
            is GuideRow.Banner, is GuideRow.Note -> Unit
        }
    }

    fun back(): Boolean {
        val place = drill ?: return false
        drill = if (place.inSection) {
            place.copy(inSection = false, section = null, label = place.parentLabel)
        } else {
            null
        }
        drilled = drill != null
        reload()
        return true
    }

    /** Back to the root of the current section (e.g. tapping the Guide tab again). */
    fun backToRoot() {
        if (drill == null && query.isEmpty()) return
        query = ""
        drill = null
        drilled = false
        reload()
    }

    fun loadMore() {
        if (endReached || loadingMore || query.trim().length >= 2) return
        val place = drill ?: return
        loadingMore = true
        viewModelScope.launch {
            val more = nextPage(place)
            rows = rows + more
            view = view.copy(rows = rows)
            loadingMore = false
        }
    }

    fun savePortal() = signIn {}

    /**
     * Checks the login against the portal before saving it, then downloads the catalog.
     * A blank password keeps the saved one, so the server URL can be edited on its own.
     */
    fun signIn(onSuccess: () -> Unit) {
        val existing = portalStore.load()
        val pass = password.ifBlank { existing?.password.orEmpty() }
        if (portalUrl.isBlank() || username.isBlank() || pass.isBlank()) {
            portalError = "Enter the server URL, username and password."
            return
        }
        if (signingIn) return
        val url = portalUrl
        val user = username.trim()
        signingIn = true
        portalError = null
        viewModelScope.launch {
            try {
                val checked = withContext(Dispatchers.IO) { PortalCheck.verify(url, user, pass) }
                portalStore.save(PortalLogin(url, user, pass))
                portalUrl = portalStore.load()?.url ?: url
                password = ""
                account = checked
                hasPortal = true
                status = "Playlist saved"
                syncError = null
                onSuccess()
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                portalError = portalErrorMessage(error, url)
            } finally {
                signingIn = false
            }
        }
    }

    /** Reads the account status for Settings; quiet on failure. */
    fun loadAccount() {
        val login = portalStore.load() ?: return
        if (account != null) return
        viewModelScope.launch {
            account = runCatching {
                withContext(Dispatchers.IO) { PortalCheck.verify(login.url, login.username, login.password) }
            }.getOrNull()
        }
    }

    fun refresh() {
        val login = portalStore.load() ?: run {
            status = "Save the playlist first"
            notify("Add your IPTV portal in Settings first")
            return
        }
        if (syncing) return
        syncing = true
        syncError = null
        syncStep = "Connecting to ${app.oslo.repertoire.data.portalHost(login.url)}"
        viewModelScope.launch {
            try {
                sync.refresh(login.url, login.username, login.password) { step ->
                    status = step
                    syncStep = friendlyStep(step)
                }
                meta.edit().putBoolean("iconsFetched", true).apply()
                reload()
                loadCounts()
                if (searchText.isNotBlank()) runSearch()
                notify("Guide updated")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = portalErrorMessage(error, login.url)
                status = message
                syncError = message
            } finally {
                syncing = false
                syncStep = ""
            }
        }
    }

    private fun friendlyStep(step: String): String {
        val parts = step.split(' ')
        val count = parts.getOrNull(1)?.toIntOrNull()
        if (parts.size == 2 && count != null) {
            val what = when (parts[0]) { "live" -> "channels"; "movie" -> "movies"; else -> "series" }
            return "Downloading $what · ${"%,d".format(count)}"
        }
        return step
    }

    private suspend fun loadCounts() {
        val byKind = database.catalog().kindCounts().associate { it.code to it.count }
        counts = CatalogCounts(byKind["live"] ?: 0, byKind["movie"] ?: 0, byKind["show"] ?: 0)
    }

    fun findTv() {
        if (scanning) return
        viewModelScope.launch {
            scanning = true
            status = "Looking for TVs on this network"
            val found = try {
                scanTvs(getApplication())
            } finally {
                scanning = false
            }
            val manual = devices.filter { known -> found.none { it.host == known.host } }
            devices = found.map { fresh ->
                val known = devices.firstOrNull { it.host == fresh.host }
                if (known != null && fresh.model.isBlank()) fresh.copy(model = known.model) else fresh
            } + manual
            found.firstOrNull { it.host == host }?.let { mine ->
                if (mine.name != mine.host) tvStore.name = mine.name
                if (mine.model.isNotBlank()) tvStore.model = mine.model
            }
            status = when {
                found.isEmpty() -> "No TVs found. Check that the TV is on and on the same Wi-Fi, or enter its address."
                found.size == 1 -> "Found ${found[0].name}"
                else -> "Found ${found.size} TVs. Tap one to use it."
            }
            devicesNote = status
            if (host.isBlank() && found.size == 1) selectDevice(found[0])
        }
    }

    fun saveHost() {
        if (host.isBlank()) return
        if (host.trim() != tvStore.host) {
            useAddress(host)
            return
        }
        remote.rememberHost(tvStore.host)
    }

    fun pair() {
        saveHost()
        if (tvStore.host.isBlank()) {
            notify("Choose a TV first")
            return
        }
        if (pairing) return
        pairing = true
        pinError = null
        viewModelScope.launch {
            try {
                remote.startPairing(tvStore.host)
                status = "Enter the code shown on the TV"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = error.message ?: "Could not start pairing"
                notify("Couldn't start pairing: ${error.message ?: "the TV did not answer"}")
            } finally {
                pairing = false
            }
        }
    }

    fun cancelPairing() {
        remote.cancelPairing()
        pin = ""
        pinError = null
    }

    fun submitPin() {
        if (pin.length < 6) {
            pinError = "The code has 6 characters"
            return
        }
        viewModelScope.launch {
            try {
                remote.finishPairing(pin)
                pin = ""
                pinError = null
                tvStore.markPaired(tvStore.host)
                tvStore.keepConnected = true
                remoteV2 = Support.Yes
                tvStore.remoteV2 = Support.Yes
                paired = true
                linked = true
                status = "Paired"
                notify("Paired with $deviceName")
                connect()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = error.message ?: "Pairing failed"
                pinError = "That code didn't work. Check it on the TV and try again."
            }
        }
    }

    fun connect() {
        tvStore.keepConnected = true
        linked = paired
        reconnectJob?.cancel()
        if (!paired) return
        viewModelScope.launch {
            try {
                remote.connect(tvStore.host)
                retryMs = 1_000L
            } catch (error: Exception) {
                status = error.message ?: "Could not connect"
                scheduleReconnect()
            }
        }
    }

    fun disconnectTv() {
        tvStore.keepConnected = false
        linked = false
        reconnectJob?.cancel()
        remote.disconnect()
        status = "Disconnected. Pairing is saved."
        notify(status)
    }

    private fun scheduleReconnect() {
        if (!tvStore.keepConnected || !tvStore.isPaired(tvStore.host)) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            while (tvStore.keepConnected && tvStore.isPaired(tvStore.host)) {
                delay(retryMs)
                if (!tvStore.keepConnected) return@launch
                try {
                    remote.connect(tvStore.host)
                    retryMs = 1_000L
                    linked = true
                    return@launch
                } catch (_: Exception) {
                    retryMs = (retryMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    fun tap(code: Int) = send { navKey(code) }

    fun hold(code: Int, down: Boolean) {
        if (transport == NavTransport.Adb) {
            if (down) startAdbRepeat(code) else stopAdbRepeat()
            return
        }
        send { remote.key(code, if (down) Keys.START_LONG else Keys.END_LONG) }
    }

    fun digit(number: Int) = tap(Keys.digit(number))

    fun playPause() = tap(Keys.MEDIA_PLAY_PAUSE)

    fun launchTiviMate() = send {
        when (transport) {
            NavTransport.RemoteV2 -> remote.launchTiviMate()
            else -> if (!adbRemote.launchTiviMate(tvStore.host, tivimatePackage)) {
                status = "Could not open TiviMate"
                notify(status)
            }
        }
    }

    fun menu() = send { navKey(Keys.DPAD_CENTER, longPress = true) }

    private suspend fun navKey(code: Int, longPress: Boolean = false) {
        when (transport) {
            NavTransport.RemoteV2 -> if (longPress) {
                remote.key(code, Keys.START_LONG)
                delay(550)
                remote.key(code, Keys.END_LONG)
            } else {
                remote.key(code)
            }
            NavTransport.Adb -> adbRemote.key(tvStore.host, code, longPress)
            NavTransport.None -> error("This TV has no remote service or network debugging. Check it on the Devices page.")
        }
    }

    /** ADB has no key-down/key-up, so a held button repeats the key until it is released. */
    private fun startAdbRepeat(code: Int) {
        repeatJob?.cancel()
        repeatJob = viewModelScope.launch {
            try {
                adbRemote.key(tvStore.host, code)
                delay(450)
                while (isActive) {
                    adbRemote.key(tvStore.host, code)
                    delay(180)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = error.message ?: error.javaClass.simpleName
                notify(status, openDevices = true)
            }
        }
    }

    private fun stopAdbRepeat() {
        repeatJob?.cancel()
        repeatJob = null
    }

    /** Tunes TiviMate on the TV to [name] over ADB; progress is in [tune], the outcome a message. */
    fun play(name: String) {
        if (tvStore.host.isBlank()) {
            notify("Choose a TV to play on", openDevices = true)
            return
        }
        if (tune != null) {
            notify("Still opening ${tune?.name}")
            return
        }
        tune = TuneState(name, "Connecting to $deviceName")
        viewModelScope.launch {
            try {
                remoteGate.withLock {
                    status = "Opening $name"
                    val host = tvStore.host
                    val directory = getApplication<Application>().filesDir
                    val reported = Handler(Looper.getMainLooper())
                    val result = withContext(Dispatchers.IO) {
                        TvAdb.play(directory, host, name, tivimatePackage) { message ->
                            reported.post {
                                status = message
                                if (tune?.name == name) tune = TuneState(name, message)
                            }
                        }
                    }
                    status = result
                    if (result.startsWith("Playing")) {
                        notify("Now playing on $deviceName")
                    } else {
                        notify(result, retry = name)
                    }
                }
            } finally {
                tune = null
            }
        }
    }

    private fun send(block: suspend () -> Unit) {
        viewModelScope.launch {
            remoteGate.withLock {
                try {
                    block()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    status = error.message ?: error.javaClass.simpleName
                    notify(status, openDevices = true)
                }
            }
        }
    }

    fun onSearchText(value: String) {
        searchText = value
        globalSearchJob?.cancel()
        if (value.trim().length < 2) {
            searchResults = emptyList()
            searchBusy = false
            return
        }
        searchBusy = true
        globalSearchJob = viewModelScope.launch {
            delay(300)
            runSearchNow()
        }
    }

    fun selectScope(scope: SearchScope) {
        if (scope == searchScope) return
        searchScope = scope
        runSearch()
    }

    fun rememberSearch(text: String = searchText) {
        val clean = text.trim()
        if (clean.length < 2) return
        recentSearches = (listOf(clean) + recentSearches.filterNot { it.equals(clean, ignoreCase = true) }).take(8)
        uiPrefs.recentSearches = recentSearches
    }

    fun clearRecentSearches() {
        recentSearches = emptyList()
        uiPrefs.recentSearches = emptyList()
    }

    private fun runSearch() {
        globalSearchJob?.cancel()
        globalSearchJob = viewModelScope.launch { runSearchNow() }
    }

    private suspend fun runSearchNow() {
        val needle = searchText.trim()
        if (needle.length < 2) {
            searchResults = emptyList()
            searchBusy = false
            return
        }
        searchBusy = true
        val like = likeNeedle(needle)
        val scope = searchScope
        searchResults = if (scope == SearchScope.All) {
            val groups = listOf(
                Triple("live", "Live TV", Section.Tv),
                Triple("movie", "Movies", Section.Movies),
                Triple("show", "Shows", Section.Series),
            ).map { (kind, label, more) ->
                val found = searchKind(kind, null, null, like, needle, 8, captionFor(kind), more, "See all $label")
                if (found.isEmpty()) emptyList() else listOf(GuideRow.Banner(label)) + found
            }
            groups.flatten()
        } else {
            database.catalog().search(scope.kind, null, null, like, needle, 120).map { it.toTitle(captionFor(it.kind)) }
        }
        searchBusy = false
    }

    override fun onCleared() {
        adbRemote.close()
        super.onCleared()
    }

    private fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            loading = true
            val loaded = load()
            val place = drill
            rows = loaded
            depth = when {
                place == null -> 0
                place.inSection -> 2
                else -> 1
            }
            view = GuideView(
                key = "$section/$depth/${place?.id}/${place?.section}",
                section = section,
                depth = depth,
                title = place?.label ?: heading,
                rows = loaded,
                filtering = query.trim().length >= 2,
            )
            loading = false
        }
    }

    private fun captionFor(kind: String) = when (kind) {
        "movie" -> "Movie"
        "show" -> "Show"
        else -> "Live TV"
    }

    private fun TitleRow.toTitle(caption: String = "") =
        GuideRow.Title(kind = kind, name = name, caption = caption, id = id, icon = icon)

    private suspend fun load(): List<GuideRow> {
        pageOffset = 0
        endReached = true
        val needle = query.trim()
        if (needle.length >= 2) return search(needle)
        val place = drill
        heading = when {
            place != null -> place.label
            section == Section.Tv -> "Countries"
            section == Section.Movies -> "Movies"
            section == Section.Series -> "Series"
            section == Section.Favorites -> "Favorites"
            else -> "Search"
        }
        if (place == null && section == Section.Favorites) return favoriteList()
        if (place == null && section == Section.All) {
            return listOf(GuideRow.Note("Search, or choose TV, Movies, or Series."))
        }
        if (place == null && section == Section.Tv) return countryList()
        if (place == null && section == Section.Movies) return groupList("movie")
        if (place == null && section == Section.Series) return groupList("show")
        if (place != null && section == Section.Tv && !place.inSection) {
            sectionMenu(place)?.let { return it }
        }
        if (place == null) return emptyList()
        endReached = false
        return nextPage(place)
    }

    private suspend fun countryList(): List<GuideRow> {
        val dao = database.catalog()
        if (dao.titleCount() == 0) return listOf(GuideRow.Note("The guide is empty. Refresh it from Settings."))
        val countries = dao.countries()
            .map { GuideRow.Bucket(it.code, countryLabel(it.code), it.count, BucketKind.Country) }
            .sortedBy { it.label.lowercase() }
        val packages = dao.otherGroups().map { GuideRow.Bucket(it.code, it.code, it.count, BucketKind.Package) }
        return if (packages.isEmpty()) countries else countries + GuideRow.Banner("More") + packages
    }

    private suspend fun favoriteList(): List<GuideRow> {
        val keys = favorites.toList().take(900)
        if (keys.isEmpty()) return emptyList()
        val found = database.catalog().favorites(keys)
        return found.groupBy { it.kind }.flatMap { (kind, titles) ->
            listOf(GuideRow.Banner(when (kind) { "live" -> "Channels"; "movie" -> "Movies"; else -> "Shows" })) +
                titles.map { it.toTitle() }
        }
    }

    private suspend fun groupList(kind: String): List<GuideRow> {
        val dao = database.catalog()
        if (dao.titleCount() == 0) return listOf(GuideRow.Note("The guide is empty. Refresh it from Settings."))
        return dao.groups(kind).map { GuideRow.Bucket(it.categoryId, it.name, it.count, BucketKind.Category) }
    }

    private suspend fun sectionMenu(place: Drill): List<GuideRow>? {
        val sections = if (place.country) {
            database.catalog().sectionsForCountry(place.id)
        } else {
            database.catalog().sectionsForGroup(place.id)
        }
        if (sections.none { !it.section.isNullOrBlank() }) return null
        endReached = true
        return sections.map { row ->
            GuideRow.Bucket(
                id = row.section.orEmpty(),
                label = row.section ?: "Channels",
                count = row.count,
                kind = BucketKind.Section,
            )
        }
    }

    private suspend fun nextPage(place: Drill): List<GuideRow> {
        val dao = database.catalog()
        val found = when (section) {
            Section.Tv -> when {
                place.inSection && place.country -> dao.channelsInSection(
                    place.id,
                    place.section.orEmpty(),
                    if (place.section == null) 1 else 0,
                    PAGE,
                    pageOffset,
                )
                place.inSection -> dao.groupChannelsInSection(
                    place.id,
                    place.section.orEmpty(),
                    if (place.section == null) 1 else 0,
                    PAGE,
                    pageOffset,
                )
                place.country -> dao.channels(place.id, PAGE, pageOffset)
                else -> dao.groupedChannels(place.id, PAGE, pageOffset)
            }
            Section.Movies -> dao.titlesInGroup("movie", place.id, PAGE, pageOffset)
            Section.Series -> dao.titlesInGroup("show", place.id, PAGE, pageOffset)
            Section.All, Section.Favorites -> emptyList()
        }
        pageOffset += found.size
        if (found.size < PAGE) endReached = true
        return found.map { row ->
            if (row.banner) GuideRow.Banner(row.name.trim('#').trim())
            else row.toTitle()
        }
    }

    private suspend fun search(needle: String): List<GuideRow> {
        heading = "Results"
        val dao = database.catalog()
        val plain = needle
        val like = likeNeedle(needle)
        val place = drill
        if (section == Section.All && place == null) {
            return searchKind("live", null, null, like, plain, 12, "Live TV", Section.Tv, "More live TV") +
                searchKind("movie", null, null, like, plain, 12, "Movie", Section.Movies, "More movies") +
                searchKind("show", null, null, like, plain, 12, "Show", Section.Series, "More shows")
        }
        val kind = when (section) {
            Section.Movies -> "movie"
            Section.Series -> "show"
            else -> "live"
        }
        val country = if (section == Section.Tv && place?.country == true) place.id else null
        val category = if (section != Section.Tv && place != null) place.id else null
        val caption = when (kind) {
            "movie" -> "Movie"
            "show" -> "Show"
            else -> "Live TV"
        }
        val found = dao.search(kind, country, category, like, plain, 60)
        if (found.isEmpty()) return listOf(GuideRow.Note("No matches"))
        return found.map { it.toTitle(caption) }
    }

    private suspend fun searchKind(
        kind: String,
        country: String?,
        categoryId: String?,
        like: String,
        plain: String,
        limit: Int,
        caption: String,
        more: Section,
        moreLabel: String,
    ): List<GuideRow> {
        val found = database.catalog().search(kind, country, categoryId, like, plain, limit)
        if (found.isEmpty()) return emptyList()
        val rows = found.map { it.toTitle(caption) }
        return if (found.size >= limit) rows + GuideRow.More(more, moreLabel) else rows
    }

    private companion object {
        const val PAGE = 80
    }
}
