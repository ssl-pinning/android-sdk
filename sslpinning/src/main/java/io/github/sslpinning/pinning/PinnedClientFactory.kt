package io.github.sslpinning.pinning

import io.github.sslpinning.model.KeysResponse
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

internal class PinnedClientFactory {

    fun create(
        baseClient: OkHttpClient,
        keys: List<KeysResponse.KeyItem>,
    ): OkHttpClient {
        require(keys.isNotEmpty()) { "No keys provided for pinning" }

        val pinner = CertificatePinner.Builder().apply {
            keys.forEach { item ->
                val hostPattern = normalizeDomainPattern(item.domainName)
                val pin = normalizePin(item.keyBase64)
                add(hostPattern, pin)
            }
        }.build()

        return baseClient.newBuilder()
            .certificatePinner(pinner)
            .build()
    }

    private fun normalizePin(keyBase64: String): String {
        val trimmed = keyBase64.trim()
        return if (trimmed.startsWith("sha256/")) trimmed else "sha256/$trimmed"
    }

    private fun normalizeDomainPattern(domainName: String): String =
        domainName.trim().lowercase()
}
