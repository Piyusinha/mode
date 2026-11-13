package com.piyush.mode.adb

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/**
 * Mode's own debugging key: made once, kept in Mode's private files. Wireless debugging remembers it after
 * pairing, so later connections need no code. Deleting it (Reset Mode, turning background mode off) forgets the
 * pairing on Mode's side.
 */
internal class AdbKeys(private val dir: File) {
    private val keyFile get() = File(dir, KEY)
    private val certFile get() = File(dir, CERT)

    val exists: Boolean get() = keyFile.exists() && certFile.exists()

    fun load(): Pair<PrivateKey, Certificate> {
        if (!exists) create()
        val key = KeyFactory.getInstance(RSA).generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        val cert = certFile.inputStream().use { CertificateFactory.getInstance(X509).generateCertificate(it) }
        return key to cert
    }

    fun forget() {
        keyFile.delete()
        certFile.delete()
    }

    private fun create() {
        dir.mkdirs()
        val pair = KeyPairGenerator.getInstance(RSA).apply { initialize(BITS) }.generateKeyPair()
        val name = X500Name("CN=Mode")
        val now = System.currentTimeMillis()
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - DAY_MS), Date(now + YEARS_MS), name, pair.public)
            .build(JcaContentSignerBuilder(SIGNATURE).build(pair.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)
        keyFile.writeBytes(pair.private.encoded)
        certFile.writeBytes(cert.encoded)
    }

    private companion object {
        const val KEY = "adb.key"
        const val CERT = "adb.crt"
        const val RSA = "RSA"
        const val X509 = "X.509"
        const val SIGNATURE = "SHA256withRSA"
        const val BITS = 2048
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val YEARS_MS = 30L * 365 * DAY_MS
    }
}
