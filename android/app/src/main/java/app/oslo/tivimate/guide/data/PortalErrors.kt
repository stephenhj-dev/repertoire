package app.oslo.tivimate.guide.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** The portal answered with a non-2xx HTTP status. */
class PortalHttpException(val code: Int) : IOException("The portal returned HTTP $code")

/** The portal answered, but not with Xtream Codes JSON. */
class PortalFormatException(message: String) : IOException(message)

/** The portal answered with user_info.auth = 0 (wrong login or inactive account). */
class PortalAuthException : IOException("The portal did not accept the login")

/** Host part of a portal address, for messages. Never includes credentials. */
fun portalHost(portal: String): String =
    runCatching { URI(PortalUrl.normalize(portal)).host }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: portal.trim().ifBlank { "the server" }

/** A friendly, actionable explanation of why talking to the portal failed. */
fun portalErrorMessage(error: Throwable, portal: String): String {
    val host = portalHost(portal)
    return when (error) {
        is PortalHttpException -> when (error.code) {
            401 -> "$host turned down the username or password (HTTP 401). Check them and try again."
            403 -> "$host refused access (HTTP 403). The server URL is probably not the right one for this " +
                "account: check it, or try the other server address your provider gave you."
            404 -> "Nothing was found at $host (HTTP 404). Check the server URL. It should be the base " +
                "address, like http://example.com:8080."
            in 500..599 -> "$host had a problem (HTTP ${error.code}). Try again in a few minutes, or check the server URL."
            else -> "$host answered HTTP ${error.code}. Check the server URL."
        }
        is PortalAuthException -> "$host did not accept this login. Check the username and password, " +
            "and that the subscription is active."
        is PortalFormatException -> "$host answered, but not like an Xtream Codes portal. Check the server URL."
        is UnknownHostException -> "Couldn't find $host. Check the server URL for typos and that this phone is online."
        is ConnectException -> "Couldn't connect to $host. Check the server URL and port."
        is SocketTimeoutException -> "$host took too long to answer. Try again, or check the server URL."
        is SSLException -> "The secure connection to $host failed. If your provider uses plain HTTP, " +
            "start the server URL with http://."
        else -> error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    }
}
