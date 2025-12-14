package io.github.sslpinning.init

import io.github.sslpinning.api.SslPinningClient
import io.github.sslpinning.api.SslPinningConfig
import io.github.sslpinning.crypto.SignatureVerifier
import io.github.sslpinning.network.KeysFetcher
import io.github.sslpinning.pinning.PinnedClientFactory
import okhttp3.OkHttpClient

internal class SslPinningInitializer(
    private val httpClient: OkHttpClient,
) {
    private val fetcher = KeysFetcher(httpClient)
    private val verifier = SignatureVerifier()
    private val pinnedFactory = PinnedClientFactory()

    suspend fun initialize(config: SslPinningConfig): SslPinningClient {
        val response = fetcher.fetch(config.endpointUrl)

        verifier.verifyPayloadSignature(
            payloadRawJson = response.payloadRawJson,
            signatureBase64 = response.signatureBase64,
            signingKeyBase64 = config.signingKeyBase64,
        )

        val pinnedClient = pinnedFactory.create(
            baseClient = httpClient,
            keys = response.payload.keys,
        )

        return SslPinningClient(
            plainClient = httpClient,
            pinnedClient = pinnedClient,
        )
    }
}
