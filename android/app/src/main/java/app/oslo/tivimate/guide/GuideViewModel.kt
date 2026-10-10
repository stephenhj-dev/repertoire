package app.oslo.tivimate.guide

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.oslo.tivimate.guide.data.CatalogDatabase
import app.oslo.tivimate.guide.data.CatalogSync
import app.oslo.tivimate.guide.data.PortalLogin
import app.oslo.tivimate.guide.data.PortalStore
import app.oslo.tivimate.guide.data.TvStore
import app.oslo.tivimate.guide.data.countryLabel
import app.oslo.tivimate.guide.data.likeNeedle
import app.oslo.tivimate.guide.remote.Keys
import app.oslo.tivimate.guide.remote.TvRemote
import app.oslo.tivimate.guide.remote.discoverTv
import app.oslo.tivimate.guide.tune.TvAdb
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Page { Home, Devices, Guide, Remote, Settings }
enum class Section { All, Tv, Movies, Series }
enum class BucketKind { Country, Package, Section, Category }

sealed interface GuideRow {
    data class Bucket(val id: String, val label: String, val count: Int, val kind: BucketKind) : GuideRow
    data class Title(val kind: String, val name: String, val caption: String) : GuideRow
    data class Banner(val name: String) : GuideRow
    data class More(val section: Section, val label: String) : GuideRow
    data class Note(val text: String) : GuideRow
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

    var page by mutableStateOf(Page.Home)
    var devices by mutableStateOf(listOf(tvStore.host))
    var adbOk by mutableStateOf<Boolean?>(null)
    var adbDetail by mutableStateOf("Not checked yet")
    var checkingAdb by mutableStateOf(false)
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
    var paired by mutableStateOf(tvStore.paired)
    var linked by mutableStateOf(tvStore.paired && tvStore.keepConnected)

    var drilled by mutableStateOf(false)
    private var drill: Drill? = null
    private var searchJob: Job? = null
    private var reconnectJob: Job? = null
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
        }
        host = tvStore.host
        remote.rememberHost(host)
        remote.onSessionLost = { scheduleReconnect() }
        page = Page.Home
        devices = listOf(tvStore.host)
        if (tvStore.paired && tvStore.keepConnected) connect()
        viewModelScope.launch {
            val meta = getApplication<Application>().getSharedPreferences("guide-meta", android.content.Context.MODE_PRIVATE)
            if (!meta.getBoolean("sectionsBackfilled", false)) {
                runCatching { sync.backfillSections() }
                meta.edit().putBoolean("sectionsBackfilled", true).apply()
            }
            if (saved != null && database.catalog().titleCount() == 0) refresh()
            else reload()
        }
    }

    fun show(page: Page) {
        this.page = page
        if (page == Page.Devices) checkAdb()
    }

    fun selectDevice(address: String) {
        val clean = address.trim()
        if (clean.isBlank()) return
        host = clean
        tvStore.host = clean
        remote.rememberHost(clean)
        if (clean !in devices) devices = devices + clean
        checkAdb()
    }

    fun checkAdb() {
        val address = host
        viewModelScope.launch {
            checkingAdb = true
            adbDetail = "Checking debugging on $address"
            val result = withContext(Dispatchers.IO) {
                app.oslo.tivimate.guide.tune.TvAdb.probe(getApplication<Application>().filesDir, address)
            }
            adbOk = result.ok
            adbDetail = result.detail
            checkingAdb = false
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

    fun loadMore() {
        if (endReached || loadingMore || query.trim().length >= 2) return
        val place = drill ?: return
        loadingMore = true
        viewModelScope.launch {
            val more = nextPage(place)
            rows = rows + more
            loadingMore = false
        }
    }

    fun savePortal() {
        val existing = portalStore.load()
        val pass = password.ifBlank { existing?.password.orEmpty() }
        if (portalUrl.isBlank() || username.isBlank() || pass.isBlank()) {
            status = "Portal, username, and password are required"
            return
        }
        portalStore.save(PortalLogin(portalUrl, username, pass))
        portalUrl = portalStore.load()?.url ?: portalUrl
        password = ""
        status = "Playlist saved"
        refresh()
    }

    fun refresh() {
        val login = portalStore.load() ?: run {
            status = "Save the playlist first"
            return
        }
        if (syncing) return
        syncing = true
        viewModelScope.launch {
            try {
                sync.refresh(login.url, login.username, login.password) { status = it }
                reload()
            } catch (error: Exception) {
                status = error.message ?: error.javaClass.simpleName
            } finally {
                syncing = false
            }
        }
    }

    fun findTv() {
        viewModelScope.launch {
            status = "Looking for the Shield"
            val found = discoverTv(getApplication())
            if (found != null) {
                selectDevice(found)
                status = "Found $found"
            } else {
                status = "No Shield found. The address can be entered by hand."
            }
        }
    }

    fun saveHost() {
        if (host.isBlank()) return
        tvStore.host = host
        host = tvStore.host
        remote.rememberHost(host)
    }

    fun pair() {
        saveHost()
        viewModelScope.launch {
            try {
                remote.startPairing(tvStore.host)
                status = "Enter the code shown on the TV"
            } catch (error: Exception) {
                status = error.message ?: "Could not start pairing"
            }
        }
    }

    fun submitPin() {
        viewModelScope.launch {
            try {
                remote.finishPairing(pin)
                pin = ""
                tvStore.paired = true
                tvStore.keepConnected = true
                paired = true
                linked = true
                status = "Paired"
                connect()
            } catch (error: Exception) {
                status = error.message ?: "Pairing failed"
            }
        }
    }

    fun connect() {
        tvStore.keepConnected = true
        linked = tvStore.paired
        reconnectJob?.cancel()
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
    }

    private fun scheduleReconnect() {
        if (!tvStore.keepConnected || !tvStore.paired) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            while (tvStore.keepConnected && tvStore.paired) {
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

    fun tap(code: Int) = send { remote.key(code) }

    fun hold(code: Int, down: Boolean) = send {
        remote.key(code, if (down) Keys.START_LONG else Keys.END_LONG)
    }

    fun digit(number: Int) = tap(Keys.digit(number))

    fun launchTiviMate() = send { remote.launchTiviMate() }

    fun menu() = send {
        remote.key(Keys.DPAD_CENTER, Keys.START_LONG)
        delay(550)
        remote.key(Keys.DPAD_CENTER, Keys.END_LONG)
    }

    private fun play(name: String) {
        viewModelScope.launch {
            remoteGate.withLock {
                status = "Opening $name"
                val host = tvStore.host
                val directory = getApplication<Application>().filesDir
                val reported = Handler(Looper.getMainLooper())
                status = withContext(Dispatchers.IO) {
                    TvAdb.play(directory, host, name) { message ->
                        reported.post { status = message }
                    }
                }
            }
        }
    }

    private fun send(block: suspend () -> Unit) {
        viewModelScope.launch {
            remoteGate.withLock {
                try {
                    block()
                } catch (error: Exception) {
                    status = error.message ?: error.javaClass.simpleName
                }
            }
        }
    }

    private fun reload() {
        viewModelScope.launch { rows = load() }
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
            else -> "Search"
        }
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
            Section.All -> emptyList()
        }
        pageOffset += found.size
        if (found.size < PAGE) endReached = true
        return found.map { row ->
            if (row.banner) GuideRow.Banner(row.name.trim('#').trim())
            else GuideRow.Title(row.kind, row.name, "")
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
        return found.map { GuideRow.Title(it.kind, it.name, caption) }
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
        val rows = found.map { GuideRow.Title(it.kind, it.name, caption) }
        return if (found.size >= limit) rows + GuideRow.More(more, moreLabel) else rows
    }

    private companion object {
        const val PAGE = 80
    }
}
