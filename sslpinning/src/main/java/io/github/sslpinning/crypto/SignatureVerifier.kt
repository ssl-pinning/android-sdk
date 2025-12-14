package io.github.sslpinning.crypto

import android.util.Base64
import org.erdtman.jcs.JsonCanonicalizer
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

internal class SignatureVerifier {

    fun verifyPayloadSignature(
        payloadRawJson: String,
        signatureBase64: String,
        signingKeyBase64: String,
    ) {
        val publicKey = parsePublicKey(signingKeyBase64)
        val signatureBytes = Base64.decode(signatureBase64, Base64.DEFAULT)

        val canonical = canonicalizeJson(payloadRawJson)

        val ok = verifyRsaSha512(publicKey, canonical, signatureBytes)

        require(ok) { "Invalid signature for SSL pinning payload" }
    }

    private fun canonicalizeJson(json: String): ByteArray {
        // JCS canonical JSON as UTF-8 bytes
        return JsonCanonicalizer(json).encodedUTF8
    }

    private fun verifyRsaSha512(publicKey: PublicKey, data: ByteArray, sig: ByteArray): Boolean {
        val verifier = Signature.getInstance("SHA512withRSA")
        verifier.initVerify(publicKey)
        verifier.update(data)
        return verifier.verify(sig)
    }

    private fun parsePublicKey(input: String): PublicKey {
        val raw = input.trim()

        if (raw.contains("BEGIN PUBLIC KEY")) {
            return derToPublicKey(pemToDer(raw))
        }

        val decoded = Base64.decode(raw, Base64.DEFAULT)

        val decodedStr = runCatching { String(decoded, Charsets.UTF_8) }.getOrNull()
        if (decodedStr != null && decodedStr.contains("BEGIN PUBLIC KEY")) {
            return derToPublicKey(pemToDer(decodedStr))
        }

        return derToPublicKey(decoded)
    }

    private fun pemToDer(pem: String): ByteArray {
        val b64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
            .trim()
        return Base64.decode(b64, Base64.DEFAULT)
    }

    private fun derToPublicKey(der: ByteArray): PublicKey {
        val spec = X509EncodedKeySpec(der)
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }
}