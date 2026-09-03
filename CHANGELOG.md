# Changelog — LicenseFlow Android (Kotlin) SDK

All notable changes to the Android SDK.
This project follows [Semantic Versioning](https://semver.org/).

Maven coordinates: `dev.licenseflow:licenseflow-android`

## [2.2.0] - 2026-09-03

### Features
- **Identity-based entitlement resolution** — resolve the effective entitlement
  set for a signed-in user without prompting for a license key.
- **Seat leases** — floating-seat checkout/checkin with automatic renewal and
  hardware-bound fingerprints (`ANDROID_ID` + app signature).
- **Offline entitlement cache** — `EntitlementCache` with TTL,
  `CacheFirst` / `StaleWhileRevalidate` / `NetworkFirst` strategies and a
  configurable offline grace period (default 72h).
- **`LicenseFlowSdk.VERSION` / `USER_AGENT`** exposed for diagnostics.

### Build
- Publishable to Maven Central via the Sonatype Central Portal with GPG-signed
  artifacts, sources and Javadoc jars, and full POM metadata.
- Gradle settings, wrapper configuration and a JVM unit-test source set added.

### Documentation
- README coordinates corrected to `dev.licenseflow:licenseflow-android:2.2.0`.

## [2.1.0] - 2026-08-06

### Features
- Initial Kotlin SDK: hardware-bound activation, license verification, lease
  management, EncryptedSharedPreferences lease caching, update checks.
