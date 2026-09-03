package dev.licenseflow.sdk

/**
 * Canonical version marker for the LicenseFlow Android SDK.
 *
 * Kept in sync with `VERSION_NAME` in `gradle.properties`; CI asserts both
 * match the pushed `sdk-android-v<semver>` tag.
 */
object LicenseFlowSdk {
    /** Semantic version of this artifact. */
    const val VERSION: String = "2.2.0"

    /** User-Agent sent with every LicenseFlow API request. */
    const val USER_AGENT: String = "licenseflow-android/$VERSION"
}
