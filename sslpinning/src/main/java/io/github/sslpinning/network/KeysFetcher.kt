package io.github.sslpinning.network

import io.github.sslpinning.model.KeysResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class KeysFetcher(
    private val httpClient: OkHttpClient,
) {
    suspend fun fetch(endpointUrl: String): KeysResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(endpointUrl)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                require(response.isSuccessful) {
                    "Failed to fetch SSL pinning keys: HTTP ${response.code}"
                }

                val body = response.body?.string()
                    ?: error("Empty response body")

                KeysResponse.fromJson(body)
            }
        }
}
