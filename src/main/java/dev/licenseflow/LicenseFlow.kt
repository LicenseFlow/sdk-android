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
    private let JSON = "application/json; charset=utf-8".toMediaType()

    fun getDeviceFingerprint(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return "android:$androidId"
    }

    fun activate(context: Context, licenseKey: String, customFingerprint: String? = null): LicenseLease {
        val fp = customFingerprint ?: getDeviceFingerprint(context)
        val jsonObj = JSONObject().apply {
            put("key", licenseKey)
            put("fingerprint", fp)
        }

        val request = Request.Builder()
            .url("$baseUrl/licenses/activate")
            .post(jsonObj.toString().toRequestBody(JSON))
            .apply {
                if (apiKey.isNotEmpty()) addHeader("Authorization", "Bearer $apiKey")
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

    fun resolveForIdentity(authToken: String, organizationId: String, productId: String): JSONObject {
        val jsonObj = JSONObject().apply {
            put("organization_id", organizationId)
            put("product_id", productId)
        }

        val request = Request.Builder()
            .url("$baseUrl/entitlements/resolve-identity")
            .post(jsonObj.toString().toRequestBody(JSON))
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Identity resolution failed: HTTP ${response.code}")
            return JSONObject(response.body?.string() ?: "{}")
        }
    }
}
