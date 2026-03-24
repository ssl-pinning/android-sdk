# SSL Pinning Android SDK

Android SDK for **SSL/TLS certificate pinning** using a **remote, cryptographically signed key registry**.

Solves the main limitation of traditional SSL pinning — hardcoded certificates require a full app update to rotate keys. This SDK fetches pins from a remote endpoint and verifies them with a signature, allowing safe key rotation without releasing a new app version.

## Requirements

- Android **minSdk 24** (Android 7.0+)
- OkHttp 4.x
- Kotlin Coroutines

## Installation

Make sure `mavenCentral()` is in your repositories:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

Add the dependency:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.sslpinninglib:sslpinning:0.1.0")
}
```

## Usage

```kotlin
val config = SslPinningConfig(
    endpointUrl = "https://your-backend.com/api/ssl-pinning.json",
    signingKeyBase64 = "BASE64_ENCODED_RSA_PUBLIC_KEY",
)

val result = SslPinningClient.initialize(
    config = config,
    httpClient = OkHttpClient(),
)

result
    .onSuccess { client ->
        val pinnedClient = client.createPinnedClient() // use for all HTTPS requests
        val plainClient  = client.createPlainClient()  // use only for non-pinned requests
    }
    .onFailure { error ->
        // initialization failed — do not proceed with network requests
    }
```

`initialize()` is a `suspend` function — call it from a coroutine scope (e.g. `viewModelScope` or `lifecycleScope`).

The `httpClient` you pass in is reused as the plain client and as the base for the pinned client.

## How It Works

1. SDK fetches the signed key registry from `endpointUrl` via HTTP GET
2. The response signature is verified using JCS + RSA PKCS#1 v1.5 + SHA-512
3. Each verified key is bound to its `domainName` as a `sha256/` pin
4. An OkHttp `CertificatePinner` is built and applied to the pinned client
5. Both clients (`plain` and `pinned`) are returned

**Fails closed** — initialization throws an exception if:
- The endpoint is unreachable
- The response format is invalid
- The cryptographic signature does not verify
- The key list is empty

## Key Registry Response Format

Your backend endpoint must return JSON in the following format:

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
  "signature": "BASE64_ENCODED_RSA_SHA512_SIGNATURE"
}
```

- `domainName` — hostname pattern passed directly to OkHttp `CertificatePinner`
- `key` — base64-encoded SHA-256 hash of the certificate's SPKI (same format as OkHttp pins)
- `signature` — RSA PKCS#1 v1.5 + SHA-512 signature over the **JCS-canonicalized JSON** of the `payload` field

## Cryptography

| Property | Value |
|----------|-------|
| Canonicalization | JSON Canonicalization Scheme (JCS, RFC 8785) |
| Signature algorithm | RSA PKCS#1 v1.5 |
| Hash function | SHA-512 |
| Public key format | PEM or DER (SPKI) |

The `signingKeyBase64` in `SslPinningConfig` is the base64-encoded public key used to verify the signature. The corresponding private key must be kept on the backend and used to sign each key registry response.

## Security Properties

- Protects against MITM attacks including custom CA injection
- Prevents silent key substitution via cryptographic signature
- Allows remote key rotation without app updates
- No hardcoded pins in the application binary

## Sample App

The repository includes a sample Compose application demonstrating SDK usage.

To run it, create `local.properties` in the project root:

```properties
SSL_PINNING_ENDPOINT=https://your-backend.com/api/ssl-pinning.json
SSL_PINNING_SIGNING_KEY_B64=BASE64_ENCODED_PUBLIC_KEY
```

Then open the project in Android Studio and run the `:app` module.

## License

BSD 3-Clause — see [LICENSE](LICENSE)
