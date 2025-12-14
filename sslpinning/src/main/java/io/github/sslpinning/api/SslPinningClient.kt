package io.github.sslpinning.api

import io.github.sslpinning.init.SslPinningInitializer
import okhttp3.OkHttpClient

class SslPinningClient internal constructor(
    private val plainClient: OkHttpClient,
    private val pinnedClient: OkHttpClient,
) {
    fun createPlainClient(): OkHttpClient = plainClient
    fun createPinnedClient(): OkHttpClient = pinnedClient

    companion object {
        suspend fun initialize(
            config: SslPinningConfig,
            httpClient: OkHttpClient,
        ): Result<SslPinningClient> = runCatching {
            SslPinningInitializer(httpClient).initialize(config)
        }
    }
}