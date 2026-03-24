# SSL Pinning Android SDK

Android SDK for **secure SSL/TLS certificate pinning** using a **remote, signed key registry**.

## Installation

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.sslpinninglib:sslpinning:0.1.0")
}
```

## Usage

```kotlin
val config = SslPinningConfig(
    endpointUrl = "https://your-pinning-endpoint/keys",
    signingKeyBase64 = "BASE64_ENCODED_PUBLIC_KEY",
)

val result = SslPinningClient.initialize(
    config = config,
    httpClient = OkHttpClient(),
)

result.onSuccess { client ->
    val pinnedClient = client.createPinnedClient()  // SSL pinning enabled
    val plainClient  = client.createPlainClient()   // no pinning
}
```

## How It Works

1. SDK fetches a signed key registry from `endpointUrl`
2. Response is verified using JCS + RSA PKCS#1 v1.5 + SHA-512
3. Valid keys are converted to OkHttp `CertificatePinner` entries
4. Two clients are returned: plain and pinned

SDK **fails closed** — if the endpoint is unreachable, the signature is invalid, or no keys are returned, initialization fails.

## Key Registry Response Format

```json
{
  "payload": {
    "keys": [
      {
        "domainName": "www.example.com",
        "key": "base64-encoded-sha256-spki-hash",
        "expire": 5488607,
        "date": "2025-12-14T21:02:11Z"
      }
    ]
  },
  "signature": "BASE64_RSA_SHA512_SIGNATURE"
}
```

Signature is computed over the **JCS-canonicalized JSON** of the `payload` field.

## Security Properties

- Protects against MITM attacks and custom CA injection
- Prevents silent key substitution
- Supports remote key rotation without app updates
- No hardcoded pins in the binary

## License

BSD 3-Clause — see [LICENSE](LICENSE)

## Repository

[github.com/ssl-pinning/android-sdk](https://github.com/ssl-pinning/android-sdk)
