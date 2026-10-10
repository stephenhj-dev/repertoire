package app.oslo.tivimate.guide.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A resolved mDNS service: where it lives and what its TXT record says. */
data class ResolvedService(
    val type: String,
    val name: String,
    val host: String,
    val port: Int,
    val attributes: Map<String, String> = emptyMap(),
)

suspend fun discoverTv(context: Context, timeoutMs: Long = 4000): String? = coroutineScope {
    val manager = context.getSystemService(NsdManager::class.java) ?: return@coroutineScope null
    val result = CompletableDeferred<String?>()
    val scope = this
    val listener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            result.complete(null)
        }
        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
        override fun onDiscoveryStarted(serviceType: String?) = Unit
        override fun onDiscoveryStopped(serviceType: String?) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            scope.launch { manager.resolve(serviceInfo, REMOTE_SERVICE)?.let { result.complete(it.host) } }
        }
    }
    try {
        withContext(Dispatchers.Main) {
            manager.discoverServices(REMOTE_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
        withTimeoutOrNull(timeoutMs) { result.await() }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { manager.stopServiceDiscovery(listener) }
        scope.coroutineContext.cancelChildren()
    }
}

private const val REMOTE_SERVICE = "_androidtvremote2._tcp."

/**
 * Resolves one service. API 34+ uses registerServiceInfoCallback (resolveService and
 * NsdServiceInfo.host are deprecated there); older releases keep the ResolveListener path,
 * serialised because the legacy resolver rejects concurrent requests (FAILURE_ALREADY_ACTIVE).
 */
suspend fun NsdManager.resolve(info: NsdServiceInfo, type: String, timeoutMs: Long = 3_000): ResolvedService? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        withTimeoutOrNull(timeoutMs) { resolveModern(info, type) }
    } else {
        legacyResolveGate.withLock { withTimeoutOrNull(timeoutMs) { resolveLegacy(info, type) } }
    }
}

private val legacyResolveGate = Mutex()

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private suspend fun NsdManager.resolveModern(info: NsdServiceInfo, type: String): ResolvedService? =
    suspendCancellableCoroutine { cont ->
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                val address = pickAddress(serviceInfo.hostAddresses) ?: return
                if (cont.isActive) cont.resume(serviceInfo.toResolved(type, address))
                runCatching { unregisterServiceInfoCallback(this) }
            }
            override fun onServiceLost() = Unit
            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        try {
            registerServiceInfoCallback(info, Executor { it.run() }, callback)
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { runCatching { unregisterServiceInfoCallback(callback) } }
    }

@Suppress("DEPRECATION")
private suspend fun NsdManager.resolveLegacy(info: NsdServiceInfo, type: String): ResolvedService? =
    suspendCancellableCoroutine { cont ->
        resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val address = serviceInfo.host?.let { pickAddress(listOf(it)) }
                if (cont.isActive) cont.resume(address?.let { serviceInfo.toResolved(type, it) })
            }
        })
    }

private fun NsdServiceInfo.toResolved(type: String, address: String) = ResolvedService(
    type = type,
    name = serviceName.orEmpty(),
    host = address,
    port = port,
    attributes = attributes.orEmpty().mapValues { (_, value) -> value?.toString(Charsets.UTF_8).orEmpty() },
)

private fun pickAddress(addresses: List<InetAddress>): String? {
    val usable = addresses.filterNot { it.isLoopbackAddress }
    val chosen = usable.firstOrNull { it is Inet4Address }
        ?: usable.firstOrNull { !it.isLinkLocalAddress }
        ?: return null
    return chosen.hostAddress
}
