# LicenseFlow Android Kotlin SDK

Official Android Kotlin SDK for LicenseFlow — hardware-bound activations,
identity-based entitlement resolution, floating seat leases and
EncryptedSharedPreferences lease caching.

Current version: **2.2.0** · `minSdk 24` · JVM target 17

## Installation

Maven Central coordinates: `dev.licenseflow:licenseflow-android`

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("dev.licenseflow:licenseflow-android:2.2.0")
}
```

Groovy DSL:

```groovy
implementation 'dev.licenseflow:licenseflow-android:2.2.0'
```

## Usage

```kotlin
import dev.licenseflow.sdk.LicenseFlowClient

val client = LicenseFlowClient(apiKey = "lf_live_...")

// Call from a coroutine / background thread
val lease = client.activate(context, licenseKey = "XXXX-XXXX-XXXX-XXXX")
if (lease.active) {
    // Unlock app features
}
```

### Offline entitlement cache

```kotlin
import dev.licenseflow.sdk.EntitlementCache

val cache = EntitlementCache(
    ttlMs = 300_000L,
    graceMs = 72L * 3600 * 1000,
    strategy = EntitlementCache.Strategy.StaleWhileRevalidate
)

when (cache.getStrategy("org:acme")) {
    "use_cache" -> Unit                 // serve locally
    "use_cache_revalidate" -> refresh() // serve + refresh in background
    else -> fetchFromNetwork()
}
```

### Diagnostics

```kotlin
LicenseFlowSdk.VERSION      // "2.2.0"
LicenseFlowSdk.USER_AGENT   // "licenseflow-android/2.2.0"
```

## Development

```bash
./gradlew test           # JVM unit tests
./gradlew assembleRelease
./gradlew publishReleasePublicationToLocalRepoRepository   # dry-run publish
```

## Releasing

Tag `sdk-android-v<semver>` from `main`. CI verifies the tag against
`gradle.properties`, GPG-signs the artifacts and publishes to Maven Central via
the Sonatype Central Portal. See [`sdk/PUBLISHING_GUIDE.md`](../PUBLISHING_GUIDE.md).

## Changelog

See [CHANGELOG.md](./CHANGELOG.md).
