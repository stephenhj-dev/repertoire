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
import app.oslo.tivimate.guide.remote.NavTransport
import app.oslo.tivimate.guide.remote.Support
import app.oslo.tivimate.guide.remote.TvDevice
import app.oslo.tivimate.guide.remote.navigationTransport
import app.oslo.tivimate.guide.remote.probeRemoteV2
import app.oslo.tivimate.guide.remote.remoteSupport
import app.oslo.tivimate.guide.remote.scanTvs
import app.oslo.tivimate.guide.tune.AdbRemote
import app.oslo.tivimate.guide.tune.TiviMate
import app.oslo.tivimate.guide.tune.TvAdb
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
    private val adbRemote = AdbRemote(app.filesDir)

    var page by mutableStateOf(Page.Home)
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
        get() = devices.firstOrNull { it.host == selectedHost }?.name?.takeIf { it != selectedHost } ?: "TV"

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
        }
        host = tvStore.host
        remote.rememberHost(host)
        remote.onSessionLost = { scheduleReconnect() }
        page = Page.Home
        if (paired && tvStore.keepConnected) connect()
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
        if (page == Page.Devices) checkDevice()
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
                found.isEmpty() -> "No TVs found. The address can be entered by hand."
                found.size == 1 -> "Found ${found[0].name}"
                else -> "Found ${found.size} TVs. Tap one to use it."
            }
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
                tvStore.markPaired(tvStore.host)
                tvStore.keepConnected = true
                remoteV2 = Support.Yes
                tvStore.remoteV2 = Support.Yes
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

    fun launchTiviMate() = send {
        when (transport) {
            NavTransport.RemoteV2 -> remote.launchTiviMate()
            else -> if (!adbRemote.launchTiviMate(tvStore.host, tivimatePackage)) status = "Could not open TiviMate"
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
            }
        }
    }

    private fun stopAdbRepeat() {
        repeatJob?.cancel()
        repeatJob = null
    }

    private fun play(name: String) {
        viewModelScope.launch {
            remoteGate.withLock {
                status = "Opening $name"
                val host = tvStore.host
                val directory = getApplication<Application>().filesDir
                val reported = Handler(Looper.getMainLooper())
                status = withContext(Dispatchers.IO) {
                    TvAdb.play(directory, host, name, tivimatePackage) { message ->
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

    override fun onCleared() {
        adbRemote.close()
        super.onCleared()
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
