package dev.licenseflow.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EntitlementCacheTest {

    @Test
    fun `set then get returns cached entry`() {
        val cache = EntitlementCache(ttlMs = 300_000L)
        cache.set("org:1", mapOf("plan" to "pro"))

        val entry = cache.get("org:1")
        assertNotNull(entry)
        assertEquals("cache", entry!!.source)
        assertEquals("pro", entry.data["plan"])
        assertEquals(1, cache.size)
    }

    @Test
    fun `miss returns null`() {
        assertNull(EntitlementCache().get("nope"))
    }

    @Test
    fun `expired ttl falls back to offline grace`() {
        val cache = EntitlementCache(ttlMs = 0L, graceMs = 72L * 3600 * 1000)
        cache.set("org:1", mapOf("plan" to "pro"))

        assertEquals("offline", cache.get("org:1")?.source)
    }

    @Test
    fun `fully expired entry is evicted`() {
        val cache = EntitlementCache(ttlMs = 0L, graceMs = 0L)
        cache.set("org:1", emptyMap())

        assertNull(cache.get("org:1"))
        assertEquals(0, cache.size)
    }

    @Test
    fun `invalidate and flush`() {
        val cache = EntitlementCache()
        cache.set("a", emptyMap())
        cache.set("b", emptyMap())

        cache.invalidate("a")
        assertNull(cache.get("a"))
        assertNotNull(cache.get("b"))

        cache.flush()
        assertEquals(0, cache.size)
    }

    @Test
    fun `strategy selection`() {
        val networkFirst = EntitlementCache(strategy = EntitlementCache.Strategy.NetworkFirst)
        networkFirst.set("k", emptyMap())
        assertEquals("use_network", networkFirst.getStrategy("k"))

        val swr = EntitlementCache(strategy = EntitlementCache.Strategy.StaleWhileRevalidate)
        swr.set("k", emptyMap())
        assertEquals("use_cache", swr.getStrategy("k"))

        val stale = EntitlementCache(
            ttlMs = 0L,
            graceMs = 72L * 3600 * 1000,
            strategy = EntitlementCache.Strategy.CacheFirst
        )
        stale.set("k", emptyMap())
        assertEquals("use_cache_revalidate", stale.getStrategy("k"))

        assertEquals("use_network", EntitlementCache().getStrategy("missing"))
    }

    @Test
    fun `sdk version marker is exposed`() {
        assertEquals("2.2.0", LicenseFlowSdk.VERSION)
        assertEquals("licenseflow-android/2.2.0", LicenseFlowSdk.USER_AGENT)
    }
}
