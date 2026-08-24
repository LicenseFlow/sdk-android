package dev.licenseflow.sdk

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, TTL-based local entitlement cache for zero-network-request validation.
 *
 * Strategies:
 * - `CacheFirst` — Return cache if valid, else network.
 * - `StaleWhileRevalidate` — Return cache immediately, revalidate in background.
 * - `NetworkFirst` — Always hit network, cache as fallback.
 */
class EntitlementCache(
    private val ttlMs: Long = 300_000L,
    private val graceMs: Long = 72L * 3600 * 1000,
    private val strategy: Strategy = Strategy.StaleWhileRevalidate
) {
    enum class Strategy { CacheFirst, StaleWhileRevalidate, NetworkFirst }

    data class CachedEntry(
        val data: Map<String, Any?>,
        val cachedAt: Long,
        val expiresAt: Long,
        /** "network", "cache", or "offline" */
        val source: String
    )

    private val entries = ConcurrentHashMap<String, CachedEntry>()

    /** Get cached entitlements. Returns null on miss or full expiry. */
    fun get(key: String): CachedEntry? {
        val entry = entries[key] ?: return null
        val now = System.currentTimeMillis()

        // Within normal TTL
        if (now < entry.expiresAt) {
            return entry.copy(source = "cache")
        }

        // Within offline grace period
        if (now < entry.cachedAt + graceMs) {
            return entry.copy(source = "offline")
        }

        // Fully expired
        entries.remove(key)
        return null
    }

    /** Store entitlement decision in cache. */
    fun set(key: String, data: Map<String, Any?>) {
        val now = System.currentTimeMillis()
        entries[key] = CachedEntry(
            data = data,
            cachedAt = now,
            expiresAt = now + ttlMs,
            source = "network"
        )
    }

    /** Remove a specific cached entry. */
    fun invalidate(key: String) {
        entries.remove(key)
    }

    /** Clear all cached entries. */
    fun flush() {
        entries.clear()
    }

    /**
     * Determine cache action: "use_cache", "use_cache_revalidate", or "use_network".
     */
    fun getStrategy(key: String): String {
        val entry = get(key) ?: return "use_network"

        return when (strategy) {
            Strategy.CacheFirst ->
                if (entry.source == "offline") "use_cache_revalidate" else "use_cache"
            Strategy.StaleWhileRevalidate ->
                if (entry.source == "cache") "use_cache" else "use_cache_revalidate"
            Strategy.NetworkFirst -> "use_network"
        }
    }

    /** Number of cached entries. */
    val size: Int get() = entries.size
}
