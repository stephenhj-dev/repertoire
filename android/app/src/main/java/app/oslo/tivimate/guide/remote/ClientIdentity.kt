package app.oslo.tivimate.guide.remote

import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

internal data class ClientIdentity(
    val certificate: X509Certificate,
    val privateKey: PrivateKey,
)

internal object ClientIdentityStore {
    private const val NAME = "tivimate-guide"

    fun loadOrCreate(directory: File): ClientIdentity {
        // File names predate multi-device support; renaming them would drop the existing pairing.
        val certFile = File(directory, "shield-cert.pem")
        val keyFile = File(directory, "shield-key.pem")
        if (certFile.exists() && keyFile.exists()) {
            return ClientIdentity(
                certificate = readCert(certFile.readText()),
                privateKey = readKey(keyFile.readText()),
            )
        }
        val created = generate()
        certFile.writeText(created.first)
        keyFile.writeText(created.second)
        return ClientIdentity(readCert(created.first), readKey(created.second))
    }

    private fun generate(): Pair<String, String> {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = Date()
        val later = Date(now.time + 10L * 365 * 24 * 60 * 60 * 1000)
        val subject = X500Name("CN=$NAME")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(1000),
            now,
            later,
            subject,
            keys.public,
        )
        builder.addExtension(Extension.basicConstraints, false, BasicConstraints(0))
        builder.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(GeneralName(GeneralName.dNSName, NAME)),
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keys.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return pem("CERTIFICATE", certificate.encoded) to pem("PRIVATE KEY", keys.private.encoded)
    }

    private fun pem(kind: String, der: ByteArray): String {
        val body = Base64.encodeToString(der, Base64.NO_WRAP).chunked(64).joinToString("\n")
        return "-----BEGIN $kind-----\n$body\n-----END $kind-----\n"
    }

    private fun readCert(pem: String): X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(pem.byteInputStream()) as X509Certificate

    private fun readKey(pem: String): PrivateKey {
        val body = pem.lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
        val spec = PKCS8EncodedKeySpec(Base64.decode(body, Base64.DEFAULT))
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }
}
