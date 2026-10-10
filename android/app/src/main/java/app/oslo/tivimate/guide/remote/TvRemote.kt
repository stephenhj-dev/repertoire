package app.oslo.tivimate.guide.remote

import android.content.Context
import android.util.Log
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class TvStatus(
    val connected: Boolean = false,
    val waitingForCode: Boolean = false,
    val host: String = "",
    val volumeLevel: Int? = null,
    val volumeMax: Int? = null,
    val muted: Boolean = false,
    val currentApp: String = "",
    val detail: String = "Shield offline",
)

class TvRemote(context: Context) {
    private val directory = context.filesDir
    private val identity by lazy { ClientIdentityStore.loadOrCreate(directory) }
    private val _status = MutableStateFlow(TvStatus())
    val status: StateFlow<TvStatus> = _status

    private var command: CommandSession? = null
    private var pairing: PairingSession? = null
    private var savedHost: String = ""
    private var generation = 0
    @Volatile private var connecting = false
    private val connectGate = Mutex()
    var onSessionLost: ((Int) -> Unit)? = null
    private val sender = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tv-send").apply { isDaemon = true }
    }

    val currentApp: String get() = _status.value.currentApp

    fun inTiviMate(): Boolean = Keys.TIVIMATE in currentApp

    fun rememberHost(host: String) {
        savedHost = host.trim()
    }

    suspend fun connect(host: String) = withContext(Dispatchers.IO) {
        connectGate.withLock {
        val target = host.trim()
        savedHost = target
        connecting = true
        val gen = ++generation
        closeCommand()
        _status.value = _status.value.copy(host = target, connected = false, detail = "Connecting")
        val session = CommandSession(identity, target, gen, { update ->
            if (gen != generation) return@CommandSession
            _status.value = _status.value.copy(
                connected = update.connected,
                host = target,
                volumeLevel = update.volumeLevel ?: _status.value.volumeLevel,
                volumeMax = update.volumeMax ?: _status.value.volumeMax,
                muted = if (update.volumeLevel != null) update.muted else _status.value.muted,
                currentApp = update.currentApp.ifEmpty { _status.value.currentApp },
                detail = update.detail,
            )
        }) { lost ->
            if (lost == generation && !connecting) onSessionLost?.invoke(lost)
        }
        try {
            withTimeout(12_000) { session.open() }
            command = session
        } catch (error: Exception) {
            session.close()
            if (gen == generation) {
                _status.value = _status.value.copy(connected = false, detail = error.message ?: "Could not connect")
            }
            throw error
        } finally {
            connecting = false
        }
        }
    }

    fun disconnect() {
        generation++
        closeCommand()
        _status.value = _status.value.copy(connected = false, detail = "Disconnected")
    }

    suspend fun startPairing(host: String) = withContext(Dispatchers.IO) {
        pairing?.close()
        _status.value = _status.value.copy(host = host, waitingForCode = false, detail = "Pairing")
        val session = PairingSession(identity, host)
        pairing = session
        try {
            withTimeout(15_000) { session.awaitCode() }
        } catch (error: Exception) {
            session.close()
            pairing = null
            throw error
        }
        _status.value = _status.value.copy(waitingForCode = true, detail = "Enter the code shown on the TV")
    }

    suspend fun finishPairing(pin: String) = withContext(Dispatchers.IO) {
        val session = pairing ?: error("Pairing has not started")
        session.submit(pin)
        session.close()
        pairing = null
        _status.value = _status.value.copy(waitingForCode = false, detail = "Paired")
    }

    fun cancelPairing() {
        pairing?.close()
        pairing = null
        _status.value = _status.value.copy(waitingForCode = false, detail = "Shield offline")
    }

    suspend fun key(code: Int, direction: Int = Keys.SHORT) {
        deliver { it.sendKey(code, direction) }
    }

    suspend fun launchTiviMate() {
        val links = listOf(
            "intent:#Intent;component=${Keys.TIVIMATE}/.ui.MainActivity;end",
            "market://launch?id=${Keys.TIVIMATE}",
            Keys.TIVIMATE,
        )
        for (link in links) {
            Log.i("TiviGuide", "launch $link")
            deliver { it.launch(link) }
            delay(900)
            if (inTiviMate()) return
        }
    }

    private suspend fun deliver(block: (CommandSession) -> Unit) {
        val target = savedHost.ifBlank { error("Shield is not connected") }
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                val current = command
                if (current == null || !current.alive) connect(target)
                val session = command ?: error("Shield is not connected")
                onSender { block(session) }
                return
            } catch (error: Exception) {
                last = error
                command?.close()
                command = null
                if (attempt == 1 || !closedSocket(error)) throw error
            }
        }
        throw last ?: IllegalStateException("Shield is not connected")
    }

    private fun closedSocket(error: Throwable): Boolean {
        val text = (error.message ?: "").lowercase()
        return "socket" in text || "broken pipe" in text || "connection reset" in text || "closed" in text
    }

    private fun onSender(block: () -> Unit) {
        try {
            sender.submit(Callable {
                block()
                null
            }).get()
        } catch (error: ExecutionException) {
            val cause = error.cause
            if (cause != null) throw cause
            throw error
        }
    }

    private fun closeCommand() {
        command?.close()
        command = null
    }
}

private class CommandSession(
    private val identity: ClientIdentity,
    private val host: String,
    private val generation: Int,
    private val onStatus: (TvStatus) -> Unit,
    private val onLost: (Int) -> Unit,
) {
    @Volatile var alive = true
    @Volatile private var closedByUs = false
    private var socket: SSLSocket? = null
    private var output: OutputStream? = null
    @Volatile private var features = Features.WANTED
    private val ready = CompletableDeferred<Unit>()

    suspend fun open() {
        val opened = tlsSocket(identity, host, 6466)
        socket = opened
        output = opened.outputStream
        val thread = Thread({ readLoop(opened.inputStream) }, "tv-remote")
        thread.isDaemon = true
        thread.start()
        ready.await()
    }

    fun close() {
        closedByUs = true
        alive = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    fun sendKey(code: Int, direction: Int) {
        send(ProtoWriter().apply {
            msg(10) {
                int(1, code)
                int(2, direction)
            }
        }.toByteArray())
    }

    fun launch(packageName: String) {
        val link = if ("://" in packageName) packageName else "market://launch?id=$packageName"
        send(ProtoWriter().apply {
            msg(90) { str(1, link) }
        }.toByteArray())
    }

    private fun send(message: ByteArray) {
        val stream = output ?: error("Shield is not connected")
        val framed = frame(message)
        synchronized(stream) {
            stream.write(framed)
            stream.flush()
        }
    }

    private fun readLoop(input: InputStream) {
        val frames = FrameBuffer()
        val chunk = ByteArray(8192)
        try {
            while (alive) {
                val count = input.read(chunk)
                if (count < 0) break
                for (raw in frames.append(chunk, count)) handle(parseFields(raw))
            }
        } catch (error: Exception) {
            if (!ready.isCompleted) ready.completeExceptionally(error)
            if (!closedByUs) {
                onStatus(TvStatus(connected = false, detail = error.message ?: "Shield disconnected"))
            }
        } finally {
            alive = false
            if (!closedByUs) onLost(generation)
        }
    }

    private fun handle(message: ProtoFields) {
        when {
            message.has(1) -> {
                val configure = message.msg(1)
                val supported = configure?.int(1) ?: Features.WANTED
                features = Features.WANTED and supported
                Log.i("TiviGuide", "features device=$supported active=$features")
                send(ProtoWriter().apply {
                    msg(1) {
                        int(1, features)
                        msg(2) {
                            int(3, 1)
                            str(4, "1")
                            str(5, "atvremote")
                            str(6, "1.0.0")
                        }
                    }
                }.toByteArray())
            }
            message.has(2) -> {
                send(ProtoWriter().apply { msg(2) { int(1, features) } }.toByteArray())
            }
            message.has(8) -> {
                val value = message.msg(8)?.int(1) ?: 0
                send(ProtoWriter().apply { msg(9) { int(1, value) } }.toByteArray())
            }
            message.has(20) -> {
                val app = message.msg(20)?.msg(1)?.text(12).orEmpty()
                if (app.isNotEmpty()) {
                    Log.i("TiviGuide", "foreground $app")
                    onStatus(TvStatus(connected = true, currentApp = app, detail = "Connected"))
                }
            }
            message.has(40) -> {
                val started = message.msg(40)?.bool(1) ?: true
                onStatus(TvStatus(connected = true, detail = if (started) "Connected" else "Shield is asleep"))
                if (!ready.isCompleted) ready.complete(Unit)
            }
            message.has(50) -> {
                val volume = message.msg(50)
                onStatus(
                    TvStatus(
                        connected = true,
                        volumeMax = volume?.int(6),
                        volumeLevel = volume?.int(7),
                        muted = volume?.bool(8) ?: false,
                        detail = "Connected",
                    ),
                )
            }
        }
    }
}

private class PairingSession(
    private val identity: ClientIdentity,
    private val host: String,
) {
    private var socket: SSLSocket? = null
    private var output: OutputStream? = null
    private var serverCert: X509Certificate? = null
    private val codeReady = CompletableDeferred<Unit>()
    private val paired = CompletableDeferred<Unit>()

    suspend fun awaitCode() {
        val opened = tlsSocket(identity, host, 6467) { serverCert = it }
        socket = opened
        output = opened.outputStream
        val thread = Thread({ readLoop(opened.inputStream) }, "tv-pair")
        thread.isDaemon = true
        thread.start()
        send(pairingRequest())
        codeReady.await()
    }

    suspend fun submit(pin: String) {
        val server = serverCert ?: error("The Shield did not present a certificate")
        val clientKey = identity.certificate.publicKey as RSAPublicKey
        val serverKey = server.publicKey as RSAPublicKey
        val secret = pairingSecret(
            clientKey.modulus,
            clientKey.publicExponent,
            serverKey.modulus,
            serverKey.publicExponent,
            pin,
        )
        send(ProtoWriter().apply {
            int(1, 2)
            int(2, 200)
            msg(40) { bytes(1, secret) }
        }.toByteArray())
        paired.await()
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    private fun readLoop(input: InputStream) {
        val frames = FrameBuffer()
        val chunk = ByteArray(4096)
        try {
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                for (raw in frames.append(chunk, count)) handle(parseFields(raw))
            }
            fail(IllegalStateException("Pairing connection closed"))
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun handle(message: ProtoFields) {
        val status = message.int(2) ?: 200
        if (status != 200) {
            fail(IllegalStateException(if (status == 402) "The Shield rejected that code" else "Pairing failed ($status)"))
            return
        }
        val reply = when {
            message.has(11) -> options()
            message.has(20) -> configuration()
            message.has(31) -> {
                if (!codeReady.isCompleted) codeReady.complete(Unit)
                null
            }
            message.has(41) -> {
                if (!paired.isCompleted) paired.complete(Unit)
                null
            }
            else -> null
        }
        if (reply != null) send(reply)
    }

    private fun fail(error: Exception) {
        if (!codeReady.isCompleted) codeReady.completeExceptionally(error)
        if (!paired.isCompleted) paired.completeExceptionally(error)
    }

    private fun send(message: ByteArray) {
        val stream = output ?: return
        val framed = frame(message)
        synchronized(stream) {
            stream.write(framed)
            stream.flush()
        }
    }
}

private fun pairingRequest(): ByteArray = ProtoWriter().apply {
    int(1, 2)
    int(2, 200)
    msg(10) {
        str(1, "atvremote")
        str(2, "TiviMate Guide")
    }
}.toByteArray()

private fun options(): ByteArray = ProtoWriter().apply {
    int(1, 2)
    int(2, 200)
    msg(20) {
        msg(1) {
            int(1, 3)
            int(2, 6)
        }
        int(3, 1)
    }
}.toByteArray()

private fun configuration(): ByteArray = ProtoWriter().apply {
    int(1, 2)
    int(2, 200)
    msg(30) {
        msg(1) {
            int(1, 3)
            int(2, 6)
        }
        int(2, 1)
    }
}.toByteArray()

private fun tlsSocket(
    identity: ClientIdentity,
    host: String,
    port: Int,
    onPeer: (X509Certificate) -> Unit = {},
): SSLSocket {
    val store = KeyStore.getInstance(KeyStore.getDefaultType())
    store.load(null, null)
    val password = "guide".toCharArray()
    store.setKeyEntry("client", identity.privateKey, password, arrayOf(identity.certificate))
    val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    keys.init(store, password)
    var peer: X509Certificate? = null
    val trust = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            peer = chain?.firstOrNull()
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val context = SSLContext.getInstance("TLS")
    context.init(keys.keyManagers, arrayOf<TrustManager>(trust), SecureRandom())
    val socket = context.socketFactory.createSocket(host, port) as SSLSocket
    socket.keepAlive = true
    socket.tcpNoDelay = true
    socket.useClientMode = true
    socket.startHandshake()
    peer?.let(onPeer)
    return socket
}

suspend fun discoverTv(context: Context, timeoutMs: Long = 4000): String? {
    val manager = context.getSystemService(NsdManager::class.java) ?: return null
    return withContext(Dispatchers.Main) {
        try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    lateinit var listener: NsdManager.DiscoveryListener
                    listener = object : NsdManager.DiscoveryListener {
                        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
                        override fun onDiscoveryStarted(serviceType: String?) = Unit
                        override fun onDiscoveryStopped(serviceType: String?) = Unit
                        override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit
                        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) = Unit
                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    val address = info.host?.hostAddress
                                    if (cont.isActive) cont.resume(address)
                                    runCatching { manager.stopServiceDiscovery(listener) }
                                }
                            })
                        }
                    }
                    cont.invokeOnCancellation { runCatching { manager.stopServiceDiscovery(listener) } }
                    manager.discoverServices("_androidtvremote2._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

