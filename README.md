# LicenseFlow Android Kotlin SDK

Official Android Kotlin SDK for LicenseFlow — Hardware-bound activations, keyless identity resolution, and EncryptedSharedPreferences lease caching.

## Installation

Add to your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.licenseflow:android-sdk:2.1.0")
}
```

## Usage Example

```kotlin
import dev.licenseflow.sdk.LicenseFlowClient

val client = LicenseFlowClient(apiKey = "lf_live_...")

// In background thread / Coroutine:
val lease = client.activate(context, licenseKey = "XXXX-XXXX-XXXX-XXXX")
if (lease.active) {
    // Unlock app features
}
```
