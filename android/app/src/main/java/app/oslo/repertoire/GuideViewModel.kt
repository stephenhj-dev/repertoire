package app.oslo.repertoire

import app.oslo.repertoire.data.SeriesProgressRow
import app.oslo.repertoire.tune.TiviMateScreens

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
import app.oslo.repertoire.tmdb.Tmdb
import app.oslo.repertoire.tmdb.TitleResolver
import app.oslo.repertoire.tmdb.TmdbImages
import app.oslo.repertoire.tmdb.TmdbPersonHit
import app.oslo.repertoire.data.PendingTitle
import app.oslo.repertoire.data.ResolvedTitle
import app.oslo.repertoire.library.CatalogItem
import app.oslo.repertoire.library.PlaybackPrefs
import app.oslo.repertoire.library.ResolvedInfo
import app.oslo.repertoire.library.TitleGrouping
import app.oslo.repertoire.library.Version
import app.oslo.repertoire.library.VersionPicker
import app.oslo.repertoire.library.VersionTagParser
import app.oslo.repertoire.watchlist.PlayOrder
import app.oslo.repertoire.watchlist.QueueItem
import app.oslo.repertoire.watchlist.WatchlistShare
import app.oslo.repertoire.watchlist.Watchlists
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
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
    /**
     * A channel, or a movie/series row. For movies and series [name] is the raw provider name of
     * the listed entry; [display]/[year]/[icon] are TMDB's when matched, and [versions] holds the
     * raw names of every catalog version of the title (played by preference).
     */
    data class Title(
        val kind: String,
        val name: String,
        val caption: String = "",
        val id: Long = 0,
        val icon: String? = null,
        val tmdbId: Int? = null,
        val year: Int? = null,
        val display: String = name,
        val versions: List<String> = emptyList(),
        val badges: String = "",
        val moreVersions: String? = null,
        val matched: Boolean = false,
    ) : GuideRow {
        val playable: List<String> get() = versions.ifEmpty { listOf(name) }
    }
    data class Banner(val name: String) : GuideRow
    data class More(val section: Section, val label: String) : GuideRow
    data class Note(val text: String) : GuideRow
}

/**
 * Titles played one after another ("Play all", a watchlist). [index] is the one playing; the
 * Up next bar advances it manually.
 */
@Immutable
data class PlayQueue(
    val title: String,
    val items: List<QueueItem>,
    val index: Int,
    /** Set when playing a watchlist, so advancing marks items watched. */
    val listId: Long? = null,
) {
    val current: QueueItem get() = items[index]
    val next: QueueItem?
        get() = PlayOrder.next(items, index)?.let { items[it] }
    val remaining: Int get() = PlayOrder.remaining(items, index)
}

/** A channel or title being tuned on the TV. */
@Immutable
data class TuneState(val name: String, val step: String)

/** The phone-side audio/subtitle picker mirroring TiviMate's track dialog on the TV. */
data class TrackSheet(
    val track: TiviMateScreens.Track,
    val busy: Boolean,
    val message: String = "",
    val options: List<TiviMateScreens.TrackOption> = emptyList(),
)

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

    /** Movies and series as posters rather than rows. */
    var posterGrid by mutableStateOf(uiPrefs.posterGrid)
        private set

    fun showPosterGrid(on: Boolean) {
        posterGrid = on
        uiPrefs.posterGrid = on
    }

    /** Preferred audio language (device language unless set, EN as the fallback) and 4K preference. */
    var playbackPrefs by mutableStateOf(
        PlaybackPrefs(
            uiPrefs.audioLanguage ?: VersionTagParser.languageForLocale(java.util.Locale.getDefault().language),
            uiPrefs.prefer4k,
        ),
    )
        private set
    val audioLanguageIsDevice: Boolean get() = uiPrefs.audioLanguage == null

    private val resolver = TitleResolver(app, viewModelScope)
    val watchlists = Watchlists(app)

    /** The Up next queue, if one is playing. */
    var queue by mutableStateOf<PlayQueue?>(null)
        private set

    /** A shared list waiting for the user to confirm the import. */
    var pendingImport by mutableStateOf<WatchlistShare.SharedList?>(null)
        private set
    /** The current Guide list before grouping (titles one per catalog entry), for re-grouping. */
    private var rawRows: List<GuideRow> = emptyList()
    private var rawSearch: List<GuideRow> = emptyList()

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
    var trackSheet by mutableStateOf<TrackSheet?>(null)
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
    /** TMDB people matching the search text (All scope only, when TMDB is configured). */
    var peopleResults by mutableStateOf<List<TmdbPersonHit>>(emptyList())
        private set
    private var globalSearchJob: Job? = null
    private var peopleJob: Job? = null
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
        resolver.start()
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            resolver.updates.debounce(1_200).collectLatest { regroup() }
        }
        viewModelScope.launch {
            if (!meta.getBoolean("sectionsBackfilled", false)) {
                runCatching { sync.backfillSections() }
                meta.edit().putBoolean("sectionsBackfilled", true).apply()
            }
            // Catalogs synced before v3 have no logos; fetch them once.
            // ...and catalogs synced before v4 have no TMDB ids or years.
            if (saved != null && (database.catalog().titleCount() == 0 || !meta.getBoolean("iconsFetched", false) ||
                    !meta.getBoolean("tmdbFieldsFetched", false))) refresh()
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

    /** [language] null follows the device language. */
    fun setAudioLanguage(language: String?) {
        uiPrefs.audioLanguage = language
        playbackPrefs = playbackPrefs.copy(language = language ?: VersionTagParser.languageForLocale(java.util.Locale.getDefault().language))
        regroup()
    }

    fun setPrefer4k(on: Boolean) {
        uiPrefs.prefer4k = on
        playbackPrefs = playbackPrefs.copy(prefer4k = on)
    }

    /** Catalog versions best first for the user's preferences. */
    fun orderVersions(names: List<String>): List<Version> = VersionPicker.order(names.distinct().map(::Version), playbackPrefs)

    /** Plays a list row: channels by name, movies/series in the preferred version. */
    fun playRow(row: GuideRow.Title) {
        if (row.kind == "live") return play(row.name)
        playBest(row.playable)
    }

    fun playBest(names: List<String>) {
        val best = orderVersions(names).firstOrNull() ?: return
        play(best.name)
    }

    /** Plays the first unwatched in-library item and keeps the rest as Up next. */
    fun startQueue(title: String, items: List<QueueItem>, listId: Long? = null) {
        val first = PlayOrder.first(items)
        if (first == null) {
            notify(if (items.isEmpty()) "This list is empty" else "Nothing left to play: everything is watched or not in your library")
            return
        }
        queue = PlayQueue(title, items, first, listId)
        playBest(items[first].versions)
    }

    /** Next: marks the current watchlist item watched and tunes the next playable one. */
    fun queueNext() {
        val now = queue ?: return
        if (tune != null) {
            notify("Still opening ${tune?.name}")
            return
        }
        val current = now.current
        val items = now.items.toMutableList()
        if (now.listId != null && current.itemId != null) {
            items[now.index] = current.copy(watched = true)
            viewModelScope.launch { watchlists.dao.setWatched(current.itemId, true) }
        }
        val next = PlayOrder.next(items, now.index)
        if (next == null) {
            queue = null
            notify("That was the last one in ${now.title}")
            return
        }
        queue = now.copy(items = items, index = next)
        playBest(items[next].versions)
    }

    fun stopQueue() {
        queue = null
    }

    /** Reads a shared list from text (share sheet, link or paste); asks before importing. */
    fun offerImport(text: String?): Boolean {
        val list = text?.let(WatchlistShare::decode)
        if (list == null) {
            if (!text.isNullOrBlank()) notify("That isn't a Repertoire list")
            return false
        }
        pendingImport = list
        return true
    }

    fun dismissImport() {
        pendingImport = null
    }

    /** Imports the pending list; [onDone] gets the new list id. */
    fun confirmImport(onDone: (Long) -> Unit) {
        val list = pendingImport ?: return
        pendingImport = null
        viewModelScope.launch {
            val id = watchlists.import(list)
            notify("Imported ${list.name}")
            onDone(id)
        }
    }

    /** True while any of these names is being tuned. */
    fun isTuning(names: List<String>): Boolean = tune?.name?.let { it in names } == true

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
            is GuideRow.Title -> playRow(row)
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
            rawRows = rawRows + more
            rows = present(rawRows)
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
                meta.edit().putBoolean("iconsFetched", true).putBoolean("tmdbFieldsFetched", true).apply()
                resolver.catalogChanged()
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

    /**
     * Plays one episode in TiviMate and remembers it as the series' last played episode.
     * [series] is the provider's series name (what TiviMate lists); [progressKey] the Room key.
     */
    fun playEpisode(series: String, season: Int, episode: Int, title: String?, progressKey: String?) {
        if (tvStore.host.isBlank()) {
            notify("Choose a TV to play on", openDevices = true)
            return
        }
        if (tune != null) {
            notify("Still opening ${tune?.name}")
            return
        }
        val label = "$series S$season E$episode"
        tune = TuneState(label, "Connecting to $deviceName")
        viewModelScope.launch {
            try {
                remoteGate.withLock {
                    val host = tvStore.host
                    val directory = getApplication<Application>().filesDir
                    val reported = Handler(Looper.getMainLooper())
                    val result = withContext(Dispatchers.IO) {
                        TvAdb.playEpisode(directory, host, series, season, episode, title, tivimatePackage) { message ->
                            reported.post {
                                status = message
                                if (tune?.name == label) tune = TuneState(label, message)
                            }
                        }
                    }
                    status = result
                    val reached = result.startsWith("Playing") || "press OK" in result
                    if (reached && progressKey != null) {
                        withContext(Dispatchers.IO) {
                            database.seriesProgress().save(
                                SeriesProgressRow(progressKey, season, episode, title, System.currentTimeMillis()),
                            )
                        }
                    }
                    notify(if (result.startsWith("Playing")) "Now playing S$season E$episode on $deviceName" else result)
                }
            } finally {
                tune = null
            }
        }
    }

    /** Remembers an episode as played without tuning (e.g. the user marks where they are). */
    fun markEpisode(progressKey: String, season: Int, episode: Int, title: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            database.seriesProgress().save(SeriesProgressRow(progressKey, season, episode, title, System.currentTimeMillis()))
        }
    }

    /**
     * Audio and subtitle buttons. TiviMate ignores KEYCODE_MEDIA_AUDIO_TRACK and
     * KEYCODE_CAPTIONS, so this opens its player menu over ADB, picks the Audio track or CC
     * entry, and mirrors the choices on the phone. Without ADB it opens the player menu with a
     * long OK press so the user can finish on the TV.
     */
    fun openTracks(track: TiviMateScreens.Track) {
        if (tvStore.host.isBlank()) {
            notify("Choose a TV first", openDevices = true)
            return
        }
        trackSheet = TrackSheet(track, busy = true, message = "Opening the menu on the TV")
        viewModelScope.launch {
            remoteGate.withLock {
                val sheet = try {
                    val picker = adbRemote.openTracks(tvStore.host, tivimatePackage, track)
                    TrackSheet(track, busy = false, message = picker.message, options = picker.options)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val fallback = runCatching {
                        if (transport == NavTransport.RemoteV2) {
                            remote.key(Keys.DPAD_CENTER, Keys.START_LONG)
                            delay(650)
                            remote.key(Keys.DPAD_CENTER, Keys.END_LONG)
                            true
                        } else {
                            false
                        }
                    }.getOrDefault(false)
                    TrackSheet(
                        track,
                        busy = false,
                        message = if (fallback) {
                            "Debugging isn't answering, so the player menu was opened on the TV. Choose ${if (track == TiviMateScreens.Track.Audio) "Audio track" else "CC"} there."
                        } else {
                            error.message ?: "Couldn't reach the TV"
                        },
                    )
                }
                if (trackSheet?.track == track) trackSheet = sheet
            }
        }
    }

    fun chooseTrack(label: String) {
        val sheet = trackSheet ?: return
        trackSheet = sheet.copy(busy = true, message = "Switching to $label")
        viewModelScope.launch {
            remoteGate.withLock {
                val result = try {
                    adbRemote.chooseTrack(tvStore.host, tivimatePackage, sheet.track, label)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    error.message ?: "Couldn't reach the TV"
                }
                trackSheet = null
                notify(result)
            }
        }
    }

    /** Closes the phone sheet; also backs out of TiviMate's dialog if it is still open. */
    fun dismissTracks(closeOnTv: Boolean) {
        val sheet = trackSheet
        trackSheet = null
        if (closeOnTv && sheet != null && !sheet.busy && sheet.options.isNotEmpty()) {
            send {
                adbRemote.key(tvStore.host, Keys.BACK)
                delay(300)
                adbRemote.key(tvStore.host, Keys.BACK)
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
        searchPeople()
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
        searchPeople()
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

    /** Debounced TMDB person search; quietly empty on errors so catalog results still show. */
    private fun searchPeople() {
        peopleJob?.cancel()
        val needle = searchText.trim()
        if (!Tmdb.enabled || needle.length < 3 || searchScope != SearchScope.All) {
            peopleResults = emptyList()
            return
        }
        peopleJob = viewModelScope.launch {
            delay(450)
            val found = try {
                Tmdb.searchPeople(getApplication(), needle).take(15)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            if (searchText.trim() == needle) peopleResults = found
        }
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
        val searchStart = System.nanoTime()
        val like = likeNeedle(needle)
        val scope = searchScope
        rawSearch = if (scope == SearchScope.All) {
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
        Perf.log("search '$needle' db: ${(System.nanoTime() - searchStart) / 1_000_000} ms")
        searchResults = present(rawSearch)
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
            rawRows = load()
            val loaded = present(rawRows)
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
        GuideRow.Title(kind = kind, name = name, caption = caption, id = id, icon = icon, tmdbId = tmdbId, year = year)

    /** Re-groups what is on screen after new TMDB lookups or a preference change. */
    private fun regroup() {
        viewModelScope.launch {
            if (rawRows.any { it is GuideRow.Title && it.kind != "live" }) {
                val again = present(rawRows)
                rows = again
                view = view.copy(rows = again)
            }
            if (rawSearch.any { it is GuideRow.Title && it.kind != "live" }) searchResults = present(rawSearch)
        }
    }

    /**
     * Turns raw catalog rows into list rows: movies/series that are the same TMDB title become one
     * row with TMDB's title, year and poster plus version badges; unmatched ones keep a cleaned
     * name. Titles never looked up are queued for the resolver. Channels pass through.
     */
    /** Grouping and tag parsing run off the main thread; database work is Room's. */
    private suspend fun present(raw: List<GuideRow>): List<GuideRow> =
        withContext(Dispatchers.Default) { Perf.time("present ${raw.size} rows") { presentNow(raw) } }

    private suspend fun presentNow(raw: List<GuideRow>): List<GuideRow> {
        val titles = raw.filterIsInstance<GuideRow.Title>().filter { it.kind != "live" }
        if (titles.isEmpty()) return raw
        val dao = database.catalog()
        val resolved = HashMap<Pair<String, String>, ResolvedTitle>()
        val versions = HashMap<Pair<String, Int>, MutableList<String>>()
        try {
            titles.groupBy { it.kind }.forEach { (kind, list) ->
                list.map { it.name }.distinct().chunked(500).forEach { names ->
                    dao.resolved(kind, names).forEach { resolved[kind to it.name] = it }
                }
                val ids = list.mapNotNull { it.tmdbId ?: resolved[kind to it.name]?.tmdbId }.distinct()
                ids.chunked(500).forEach { chunk ->
                    dao.versionNames(kind, chunk).forEach { versions.getOrPut(kind to it.tmdbId) { mutableListOf() } += it.name }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Lookups are an extra; show plain rows if they fail.
        }
        resolver.request(
            titles.filter { resolved[it.kind to it.name] == null }
                .map { PendingTitle(it.kind, it.name, it.tmdbId, it.year) },
        )
        val out = ArrayList<GuideRow>(raw.size)
        val run = ArrayList<GuideRow.Title>()
        fun flush() {
            if (run.isEmpty()) return
            val byId = run.associateBy { it.id }
            val items = run.map { row ->
                val r = resolved[row.kind to row.name]
                CatalogItem(
                    id = row.id,
                    kind = row.kind,
                    name = row.name,
                    icon = row.icon,
                    year = row.year,
                    portalTmdbId = row.tmdbId,
                    resolved = r?.let { ResolvedInfo(it.tmdbId, it.title, it.year, TmdbImages.poster(it.posterPath)) },
                )
            }
            TitleGrouping.group(items, versions).forEach { group ->
                val source = byId.getValue(group.representative.id)
                val summary = VersionPicker.summary(group.versions.map(::Version), playbackPrefs.language)
                out += source.copy(
                    tmdbId = group.tmdbId,
                    year = group.display.year,
                    display = group.display.title,
                    icon = group.display.poster,
                    versions = group.versions,
                    badges = summary.line,
                    moreVersions = summary.moreVersions,
                    matched = group.display.matched,
                )
            }
            run.clear()
        }
        for (row in raw) {
            if (row is GuideRow.Title && row.kind != "live") {
                if (run.isNotEmpty() && run.first().kind != row.kind) flush()
                run += row
            } else {
                flush()
                out += row
            }
        }
        flush()
        return out
    }

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
