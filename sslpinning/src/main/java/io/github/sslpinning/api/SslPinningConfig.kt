package io.github.sslpinning.api

data class SslPinningConfig(
    val endpointUrl: String,
    val signingKeyBase64: String,
)