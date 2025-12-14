# SSL Pinning Android SDK

Android SDK and sample application for **secure SSL/TLS certificate pinning** using a **remote, signed key registry**.

The project consists of:
- **sslpinning** — reusable Android SDK module
- **app** — sample Compose application demonstrating SDK usage and integration with the ssl-pinning service

## Motivation

Traditional SSL pinning hardcodes certificates or public keys inside the app, making key rotation and incident response difficult.

This SDK solves the problem by:
- Fetching **pinning keys from a remote endpoint**
- Verifying the response using a **cryptographic signature**
- Applying SSL pinning dynamically via OkHttp CertificatePinner
- Allowing **key rotation without app updates**

### Project Structure

```
.
├── app/                    # Sample Android application
│   └── src/main/java/io/github/sslpinning/sample
│       ├── MainActivity.kt
│       └── ui/theme/...
│
└── sslpinning/             # Android SDK module
    └── src/main/java/io/github/sslpinning
        ├── api/            # Public SDK API
        │   ├── SslPinningClient.kt
        │   └── SslPinningConfig.kt
        │
        ├── init/           # Initialization orchestration
        │   └── SslPinningInitializer.kt
        │
        ├── network/        # Network layer
        │   └── KeysFetcher.kt
        │
        ├── model/          # Data models
        │   └── KeysResponse.kt
        │
        ├── crypto/         # Cryptography (signature verification)
        │   └── SignatureVerifier.kt
        │
        ├── pinning/        # OkHttp SSL pinning
        │   └── PinnedClientFactory.kt
        │
        └── util/           # Internal utilities
```

## How It Works

* **App initializes the SDK** with:
    - endpointUrl — URL of the signed pin registry
    - signingKeyBase64 — public key used to verify signatures
* SDK downloads the key registry from the endpoint
* The response is verified using:
    - **JSON Canonicalization Scheme (JCS)**
    - **RSA PKCS#1 v1.5**
    - **SHA-512 hash**
* If the signature is valid:
    - All keys are converted to sha256/ pins
    - Each pin is bound to its corresponding domainName
    - An OkHttpClient with CertificatePinner is created
* SDK exposes:
    - createPlainClient() — no pinning
    - createPinnedClient() — SSL pinning enabled

## Key Registry Response Format

Example response returned by endpointUrl:

```json
{
  "payload": {
    "keys": [
      {
        "date": "2025-12-14T21:02:11.05897+01:00",
        "domainName": "www.google.com",
        "expire": 5488607,
        "fqdn": "google.com",
        "key": "U+TeUuMnLRoEM6i/zYxqYrBzRCNFh/5+zVvxWy6riRo="
      },
      {
        "date": "2025-12-14T21:02:11.055098+01:00",
        "domainName": "mail.google.com",
        "expire": 5488740,
        "fqdn": "mail.google.com",
        "key": "1f1ohUq0c8pn9l3gSKQvBHL2VuDWA1W294iLs3KXy5o="
      }
    ]
  },
  "signature": "BASE64_RSA_SHA512_SIGNATURE"
}
```

### Important Notes

- domainName is used directly as the hostname pattern for pinning
- key is a base64-encoded SHA-256 SPKI hash (converted to sha256/<base64>)
- The signature is calculated over the **canonicalized JSON of the payload field**

### Cryptography Details
- **Canonicalization**: JSON Canonicalization Scheme (JCS)
- **Signature Algorithm**: RSA PKCS#1 v1.5
- **Hash Function**: SHA-512
- **Public Key Format**: PEM / DER (SPKI)

The Android SDK mirrors the server-side signing logic exactly.

### Usage Example (Sample App)

```kotlin
val config = SslPinningConfig(
    endpointUrl = BuildConfig.SSL_PINNING_ENDPOINT,
    signingKeyBase64 = BuildConfig.SSL_PINNING_SIGNING_KEY_B64,
)

val result = SslPinningClient.initialize(
    config = config,
    httpClient = OkHttpClient(),
)

result.onSuccess { client ->
    val pinnedClient = client.createPinnedClient()
    val plainClient = client.createPlainClient()
}
```

### Failure Modes

Initialization fails if:
- The endpoint is unreachable
- The response format is invalid
- The cryptographic signature is invalid
- No keys are returned

In all cases, the SDK fails closed and does not create a pinned client.

### Security Properties
- Protects against MITM attacks, including custom CA injection
- Prevents silent key substitution
- Supports safe remote key rotation
- No hardcoded pins in the application binary
