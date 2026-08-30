package dev.licenseflow.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.LruCache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// ── Error Types ──

sealed class LicenseFlowException(message: String, val code: String, val statusCode: Int? = null) : Exception(message) {
    class NetworkError(message: String) : LicenseFlowException(message, "NETWORK_ERROR")
    class RateLimitExceeded(message: String) : LicenseFlowException(message, "RATE_LIMIT_EXCEEDED", 429)
    class InvalidLicense(message: String) : LicenseFlowException(message, "INVALID_LICENSE", 400)
    class ApiError(message: String, status: Int) : LicenseFlowException(message, "API_ERROR", status)
}

// ── Data Classes ──

data class LicenseLease(
    val licenseKey: String,
    val active: Boolean,
    val fingerprint: String,
    val expiresAt: String,
    val entitlements: JSONObject? = null
)

data class VerificationResult(
    val valid: Boolean,
    val status: String?,
    val licenseKey: String?,
    val productName: String?,
    val maxActivations: Int?,
    val currentActivations: Int?,
    val expiresAt: String?,
    val entitlements: JSONObject?,
    val proof: String?,
    val error: String?
) {
    companion object {
        fun from(json: JSONObject): VerificationResult = VerificationResult(
            valid = json.optBoolean("valid", false),
            status = json.optString("status", null),
            licenseKey = json.optString("licenseKey", null),
            productName = json.optString("productName", null),
            maxActivations = if (json.has("maxActivations")) json.optInt("maxActivations") else null,
            currentActivations = if (json.has("currentActivations")) json.optInt("currentActivations") else null,
            expiresAt = json.optString("expiresAt", null),
            entitlements = if (json.has("entitlements")) json.optJSONObject("entitlements") else null,
            proof = json.optString("proof", null),
            error = json.optString("error", null)
        )
    }
}

data class LeaseResponse(
    val success: Boolean,
    val leaseKey: String?,
    val expiresAt: String?,
    val error: String?
) {
    companion object {
        fun from(json: JSONObject): LeaseResponse = LeaseResponse(
            success = json.optBoolean("success", false),
            leaseKey = json.optString("lease_key", null) ?: json.optString("leaseKey", null),
            expiresAt = json.optString("expiresAt", null) ?: json.optString("expires_at", null),
            error = json.optString("error", null)
        )
    }
}

data class UpdateInfo(
    val id: String,
    val version: String,
    val changelog: String?,
    val publishedAt: String?
) {
    companion object {
        fun from(json: JSONObject, currentVersion: String): UpdateInfo? {
            val version = json.optString("version", "")
            if (version.isEmpty() || version == currentVersion) return null
            return UpdateInfo(
                id = json.optString("id", ""),
                version = version,
                changelog = json.optString("changelog", null),
                publishedAt = json.optString("published_at", null)
            )
        }
    }
}

data class ArtifactDownload(
    val url: String,
    val filename: String?,
    val size: Int?
) {
    companion object {
        fun from(json: JSONObject): ArtifactDownload = ArtifactDownload(
            url = json.optString("url", "") .ifEmpty { json.optString("download_url", "") },
            filename = json.optString("filename", null),
            size = if (json.has("size")) json.optInt("size") else null
        )
    }
}

// ── Client ──

class LicenseFlowClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.licenseflow.dev/v1"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val cache = LruCache<String, JSONObject>(100)
    private var heartbeatHandler: Handler? = null
    private var heartbeatRunnable: Runnable? = null

    // ── Device Fingerprint ──

    fun getDeviceFingerprint(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return "android:$androidId"
    }

    // ── Activate ──

    /** Activate a license for a specific device */
    fun activate(context: Context, licenseKey: String, customFingerprint: String? = null): LicenseLease {
        val fp = customFingerprint ?: getDeviceFingerprint(context)
        val jsonObj = JSONObject().apply {
            put("licenseKey", licenseKey)
            put("deviceId", fp)
        }

        val resObj = post("/activate-license", jsonObj)
        return LicenseLease(
            licenseKey = licenseKey,
            active = resObj.optBoolean("active", true),
            fingerprint = fp,
            expiresAt = resObj.optString("expiresAt", ""),
            entitlements = resObj.optJSONObject("entitlements")
        )
    }

    // ── Verify ──

    /** Verify the current status of a license with caching */
    fun verify(context: Context, licenseKey: String, deviceId: String? = null, environmentId: String? = null): VerificationResult {
        val did = deviceId ?: getDeviceFingerprint(context)
        val cacheKey = "verify:$licenseKey:$did:${environmentId ?: "default"}"

        cache.get(cacheKey)?.let {
            return VerificationResult.from(it)
        }

        val jsonObj = JSONObject().apply {
            put("licenseKey", licenseKey)
            put("deviceId", did)
            if (environmentId != null) put("environmentId", environmentId)
        }

        val resObj = post("/verify-license", jsonObj)
        val result = VerificationResult.from(resObj)

        if (result.valid) {
            cache.put(cacheKey, resObj)
        }

        return result
    }

    // ── Deactivate ──

    /** Deactivate a license from a device */
    fun deactivate(context: Context, licenseKey: String, deviceId: String? = null, environmentId: String? = null): JSONObject {
        val did = deviceId ?: getDeviceFingerprint(context)
        val jsonObj = JSONObject().apply {
            put("licenseKey", licenseKey)
            put("deviceId", did)
            if (environmentId != null) put("environmentId", environmentId)
        }

        val result = post("/deactivate-license", jsonObj)
        cache.evictAll()
        return result
    }

    // ── Entitlement Helpers ──

    /** Check if a verified license has a specific feature enabled */
    fun hasFeature(verification: VerificationResult, featureCode: String): Boolean {
        if (!verification.valid || verification.entitlements == null) return false
        if (!verification.entitlements.has(featureCode)) return false

        val ent = verification.entitlements.opt(featureCode) ?: return false
        return when (ent) {
            is Boolean -> ent
            is JSONObject -> ent.optBoolean("enabled", false) || ent.optBoolean("value", false)
            else -> false
        }
    }

    /** Get entitlement value for a feature code */
    fun getEntitlement(verification: VerificationResult, featureCode: String): Any? {
        if (!verification.valid || verification.entitlements == null) return null
        return verification.entitlements.opt(featureCode)
    }

    // ── Identity Resolution ──

    /** Identity-based (keyless) entitlement resolution */
    fun resolveForIdentity(email: String, productId: String? = null, environmentId: String? = null): JSONObject {
        val cacheKey = "identity:$email:${productId ?: "all"}:${environmentId ?: "default"}"

        cache.get(cacheKey)?.let { return it }

        val jsonObj = JSONObject().apply {
            put("email", email)
            if (productId != null) put("productId", productId)
            if (environmentId != null) put("environmentId", environmentId)
        }

        val resObj = post("/resolve-entitlements", jsonObj)

        if (resObj.optBoolean("resolved", false)) {
            cache.put(cacheKey, resObj)
        }

        return resObj
    }

    // ── Floating License Leases ──

    /** Acquire a temporary floating license lease */
    fun checkoutLicense(
        context: Context,
        licenseKey: String,
        durationSeconds: Int = 3600,
        requesterId: String? = null,
        requesterType: String = "sdk",
        metadata: JSONObject? = null
    ): LeaseResponse {
        val jsonObj = JSONObject().apply {
            put("license_key", licenseKey)
            put("duration_seconds", durationSeconds)
            put("requester_id", requesterId ?: getDeviceFingerprint(context))
            put("requester_type", requesterType)
            if (metadata != null) put("metadata", metadata)
        }

        return LeaseResponse.from(post("/checkout-license", jsonObj))
    }

    /** Release (check-in) a floating license lease */
    fun checkinLicense(leaseKey: String): JSONObject {
        val jsonObj = JSONObject().apply { put("lease_key", leaseKey) }
        return post("/checkin-license", jsonObj)
    }

    /** Get the status of a floating license lease */
    fun getLeaseStatus(leaseKey: String): LeaseResponse {
        val jsonObj = JSONObject().apply { put("lease_key", leaseKey) }
        return LeaseResponse.from(post("/lease-status", jsonObj))
    }

    // ── Release Management ──

    /** Check for available software updates */
    fun checkForUpdates(currentVersion: String, productId: String, channel: String = "stable"): UpdateInfo? {
        val resObj = get("/release-management/latest?product_id=$productId&channel=$channel")
        return UpdateInfo.from(resObj, currentVersion)
    }

    /** Download artifact with license verification */
    fun downloadArtifact(
        licenseKey: String,
        releaseId: String? = null,
        artifactId: String? = null,
        platform: String? = null,
        architecture: String? = null
    ): ArtifactDownload {
        val jsonObj = JSONObject().apply {
            put("licenseKey", licenseKey)
            if (releaseId != null) put("release_id", releaseId)
            if (artifactId != null) put("artifact_id", artifactId)
            if (platform != null) put("platform", platform)
            if (architecture != null) put("architecture", architecture)
        }

        return ArtifactDownload.from(post("/artifact-download", jsonObj))
    }

    // ── Usage Metering ──

    /** Record usage metrics for a license */
    fun recordUsage(payload: JSONObject): JSONObject {
        return post("/record-usage", payload)
    }

    // ── Heartbeat ──

    /** Start periodic heartbeat to keep a license session alive */
    fun startHeartbeat(context: Context, licenseKey: String, intervalMs: Long = 60_000) {
        stopHeartbeat()
        heartbeatHandler = Handler(Looper.getMainLooper())
        heartbeatRunnable = object : Runnable {
            override fun run() {
                Thread {
                    try {
                        verify(context, licenseKey)
                    } catch (e: Exception) {
                        android.util.Log.w("LicenseFlow", "Heartbeat failed: ${e.message}")
                    }
                }.start()
                heartbeatHandler?.postDelayed(this, intervalMs)
            }
        }
        heartbeatHandler?.postDelayed(heartbeatRunnable!!, intervalMs)
    }

    /** Stop the periodic heartbeat */
    fun stopHeartbeat() {
        heartbeatRunnable?.let { heartbeatHandler?.removeCallbacks(it) }
        heartbeatHandler = null
        heartbeatRunnable = null
    }

    /** Clear the internal verification cache */
    fun clearCache() {
        cache.evictAll()
    }

    // ── HTTP Helpers ──

    private fun post(endpoint: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .apply {
                if (apiKey.isNotEmpty()) addHeader("x-api-key", apiKey)
            }
            .build()

        return executeRequest(request)
    }

    private fun get(endpoint: String): JSONObject {
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .get()
            .apply {
                if (apiKey.isNotEmpty()) addHeader("x-api-key", apiKey)
            }
            .build()

        return executeRequest(request)
    }

    private fun executeRequest(request: Request): JSONObject {
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"

                if (!response.isSuccessful) {
                    val errorObj = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }
                    val message = errorObj.optString("message", "").ifEmpty {
                        errorObj.optString("error", "HTTP ${response.code}")
                    }

                    throw when (response.code) {
                        429 -> LicenseFlowException.RateLimitExceeded(message)
                        400, 404 -> LicenseFlowException.InvalidLicense(message)
                        else -> LicenseFlowException.ApiError(message, response.code)
                    }
                }

                return JSONObject(bodyStr)
            }
        } catch (e: LicenseFlowException) {
            throw e
        } catch (e: IOException) {
            throw LicenseFlowException.NetworkError(e.message ?: "Network error")
        }
    }
}
