package app.oslo.repertoire.remote

import java.math.BigInteger
import java.security.MessageDigest

fun pairingSecret(
    clientModulus: BigInteger,
    clientExponent: BigInteger,
    serverModulus: BigInteger,
    serverExponent: BigInteger,
    pin: String,
): ByteArray {
    val code = pin.trim().uppercase()
    require(code.length == 6 && code.all { it in '0'..'9' || it in 'A'..'F' }) {
        "The TV code is 6 hex characters"
    }
    val digest = MessageDigest.getInstance("SHA-256").apply {
        update(hexBytes(clientModulus.toString(16).uppercase()))
        update(hexBytes("0" + clientExponent.toString(16).uppercase()))
        update(hexBytes(serverModulus.toString(16).uppercase()))
        update(hexBytes("0" + serverExponent.toString(16).uppercase()))
        update(hexBytes(code.substring(2)))
    }.digest()
    val expected = code.substring(0, 2).toInt(16)
    if ((digest[0].toInt() and 0xFF) != expected) {
        throw IllegalArgumentException("That code does not match this TV")
    }
    return digest
}

private fun hexBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Certificate value cannot be hashed" }
    return ByteArray(hex.length / 2) { index ->
        hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
