package dev.licenseflow.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParsingTest {

    @Test
    fun `verification result parses valid payload`() {
        val json = JSONObject(
            """
            {
              "valid": true,
              "status": "active",
              "licenseKey": "AAAA-BBBB-CCCC-DDDD",
              "productName": "Acme Pro",
              "maxActivations": 5,
              "currentActivations": 2,
              "entitlements": { "seats": 5 }
            }
            """.trimIndent()
        )

        val result = VerificationResult.from(json)
        assertTrue(result.valid)
        assertEquals("active", result.status)
        assertEquals("AAAA-BBBB-CCCC-DDDD", result.licenseKey)
        assertEquals(5, result.maxActivations)
        assertEquals(2, result.currentActivations)
        assertEquals(5, result.entitlements?.optInt("seats"))
    }

    @Test
    fun `verification result defaults to invalid`() {
        val result = VerificationResult.from(JSONObject("""{"error":"not_found"}"""))
        assertFalse(result.valid)
        assertEquals("not_found", result.error)
    }

    @Test
    fun `lease response parses snake case keys`() {
        val lease = LeaseResponse.from(
            JSONObject("""{"success":true,"lease_key":"lease_1","expires_at":"2026-09-04T00:00:00Z"}""")
        )
        assertTrue(lease.success)
        assertEquals("lease_1", lease.leaseKey)
        assertEquals("2026-09-04T00:00:00Z", lease.expiresAt)
    }

    @Test
    fun `update info is null when version matches current`() {
        assertNull(UpdateInfo.from(JSONObject("""{"version":"2.2.0"}"""), "2.2.0"))

        val update = UpdateInfo.from(
            JSONObject("""{"id":"r1","version":"2.3.0","changelog":"New"}"""),
            "2.2.0"
        )
        assertEquals("2.3.0", update?.version)
        assertEquals("New", update?.changelog)
    }

    @Test
    fun `artifact download falls back to download_url`() {
        val artifact = ArtifactDownload.from(
            JSONObject("""{"download_url":"https://cdn/x.zip","size":42}""")
        )
        assertEquals("https://cdn/x.zip", artifact.url)
        assertEquals(42, artifact.size)
    }
}
