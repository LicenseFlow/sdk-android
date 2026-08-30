package dev.licenseflow.sdk

import android.content.Context
import android.provider.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class LicenseLease(
    val licenseKey: String,
    val active: Boolean,
    val fingerprint: String,
    val expiresAt: String
)

class LicenseFlowClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.licenseflow.dev/v1"
) {
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun getDeviceFingerprint(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return "android:$androidId"
    }

    fun activate(context: Context, licenseKey: String, customFingerprint: String? = null): LicenseLease {
        val fp = customFingerprint ?: getDeviceFingerprint(context)
        val jsonObj = JSONObject().apply {
            put("licenseKey", licenseKey)
            put("deviceId", fp)
        }

        val request = Request.Builder()
            .url("$baseUrl/activate-license")
            .post(jsonObj.toString().toRequestBody(JSON))
            .apply {
                if (apiKey.isNotEmpty()) addHeader("x-api-key", apiKey)
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Activation failed: HTTP ${response.code}")
            val bodyStr = response.body?.string() ?: throw Exception("Empty response body")
            val resObj = JSONObject(bodyStr)

            return LicenseLease(
                licenseKey = licenseKey,
                active = resObj.optBoolean("active", true),
                fingerprint = fp,
                expiresAt = resObj.optString("expiresAt", "")
            )
        }
    }

    fun resolveForIdentity(email: String, productId: String? = null, environmentId: String? = null): JSONObject {
        val jsonObj = JSONObject().apply {
            put("email", email)
            if (productId != null) put("productId", productId)
            if (environmentId != null) put("environmentId", environmentId)
        }

        val request = Request.Builder()
            .url("$baseUrl/resolve-entitlements")
            .post(jsonObj.toString().toRequestBody(JSON))
            .apply {
                if (apiKey.isNotEmpty()) addHeader("x-api-key", apiKey)
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Identity resolution failed: HTTP ${response.code}")
            return JSONObject(response.body?.string() ?: "{}")
        }
    }
}
