import java.util.*;
import java.util.concurrent.*;

/**
 * Problem 3: DNS Cache with TTL (Time To Live)
 *
 * Scenario: DNS resolver cache that stores domain-to-IP mappings.
 * Implements TTL-based expiration, LRU eviction, and cache hit/miss stats.
 */
public class Problem3_DNSCacheWithTTL {

    /**
     * Represents a single DNS cache entry with TTL metadata.
     */
    static class DNSEntry {
        String domain;
        String ipAddress;
        long timestamp; // when the entry was cached (ms)
        long expiryTime; // when the entry expires (ms)

        DNSEntry(String domain, String ipAddress, int ttlSeconds) {
            this.domain = domain;
            this.ipAddress = ipAddress;
            this.timestamp = System.currentTimeMillis();
            this.expiryTime = this.timestamp + (ttlSeconds * 1000L);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }

        long getRemainingTTL() {
            long remaining = (expiryTime - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        }

        @Override
        public String toString() {
            return String.format("DNSEntry{domain='%s', ip='%s', ttl=%ds remaining}",
                    domain, ipAddress, getRemainingTTL());
        }
    }

    // LRU cache using LinkedHashMap with access-order
    private final LinkedHashMap<String, DNSEntry> cache;
    private final int maxSize;

    // Stats
    private int cacheHits = 0;
    private int cacheMisses = 0;
    private int expiredEvictions = 0;
    private long totalLookupTimeNs = 0;
    private int totalLookups = 0;

    // Background cleanup thread
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    public Problem3_DNSCacheWithTTL(int maxSize) {
        this.maxSize = maxSize;
        // LinkedHashMap in access-order mode for LRU eviction
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, DNSEntry> eldest) {
                return size() > maxSize;
            }
        };

        // Schedule background cleanup every 30 seconds
        cleaner.scheduleAtFixedRate(this::cleanExpiredEntries, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Simulated upstream DNS query (in real system, this would be an actual network
     * call).
     */
    private String queryUpstreamDNS(String domain) {
        // Simulate variable IPs for popular domains
        Map<String, String> upstreamDB = new HashMap<>();
        upstreamDB.put("google.com", "172.217.14.206");
        upstreamDB.put("facebook.com", "157.240.195.35");
        upstreamDB.put("github.com", "140.82.121.4");
        upstreamDB.put("stackoverflow.com", "151.101.193.69");
        upstreamDB.put("amazon.com", "205.251.242.103");
        return upstreamDB.getOrDefault(domain, "8.8.8." + (domain.hashCode() & 0xFF));
    }

    /**
     * Resolve a domain name, using cache for fast lookups.
     *
     * @param domain     the domain to resolve
     * @param ttlSeconds TTL for newly cached entries
     * @return IP address string
     */
    public synchronized String resolve(String domain, int ttlSeconds) {
        long startNs = System.nanoTime();
        totalLookups++;

        DNSEntry entry = cache.get(domain);

        String result;
        if (entry != null && !entry.isExpired()) {
            // Cache HIT
            cacheHits++;
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            totalLookupTimeNs += (System.nanoTime() - startNs);
            System.out.printf("resolve(\"%s\") → Cache HIT → %s (retrieved in 0.%dms)%n",
                    domain, entry.ipAddress, elapsedMs + 1);
            result = entry.ipAddress;
        } else if (entry != null) {
            // Cache EXPIRED
            cacheMisses++;
            expiredEvictions++;
            cache.remove(domain);
            String ip = queryUpstreamDNS(domain);
            cache.put(domain, new DNSEntry(domain, ip, ttlSeconds));
            totalLookupTimeNs += (System.nanoTime() - startNs);
            System.out.printf("resolve(\"%s\") → Cache EXPIRED → Query upstream → %s (TTL: %ds)%n",
                    domain, ip, ttlSeconds);
            result = ip;
        } else {
            // Cache MISS
            cacheMisses++;
            String ip = queryUpstreamDNS(domain);
            cache.put(domain, new DNSEntry(domain, ip, ttlSeconds));
            totalLookupTimeNs += (System.nanoTime() - startNs);
            System.out.printf("resolve(\"%s\") → Cache MISS → Query upstream → %s (TTL: %ds)%n",
                    domain, ip, ttlSeconds);
            result = ip;
        }
        return result;
    }

    /**
     * Remove all expired entries from the cache.
     */
    public synchronized void cleanExpiredEntries() {
        int beforeSize = cache.size();
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
        int removed = beforeSize - cache.size();
        if (removed > 0) {
            System.out.printf("[Cleaner] Removed %d expired DNS entries.%n", removed);
        }
    }

    /**
     * Manually invalidate a cache entry (e.g., when IP changes).
     */
    public synchronized void invalidate(String domain) {
        if (cache.remove(domain) != null) {
            System.out.printf("Cache entry for \"%s\" invalidated.%n", domain);
        } else {
            System.out.printf("No cache entry for \"%s\" to invalidate.%n", domain);
        }
    }

    /**
     * Print cache hit rate, miss rate, and average lookup time.
     */
    public void getCacheStats() {
        double hitRate = (totalLookups > 0) ? (cacheHits * 100.0 / totalLookups) : 0;
        double avgTimeMs = (totalLookups > 0) ? (totalLookupTimeNs / 1_000_000.0 / totalLookups) : 0;
        System.out.printf(
                "%ngetCacheStats() → Hit Rate: %.1f%%, Miss Rate: %.1f%%, Avg Lookup Time: %.2fms, Cache Size: %d/%d%n",
                hitRate, 100 - hitRate, avgTimeMs, cache.size(), maxSize);
        System.out.printf("  Total Lookups: %d | Hits: %d | Misses: %d | Expired Evictions: %d%n",
                totalLookups, cacheHits, cacheMisses, expiredEvictions);
    }

    /**
     * Print all current (non-expired) cache entries.
     */
    public void printCache() {
        System.out.println("\n--- Current Cache Contents ---");
        if (cache.isEmpty()) {
            System.out.println("  (empty)");
        } else {
            cache.forEach((domain, entry) -> {
                if (!entry.isExpired()) {
                    System.out.printf("  %-25s → %-20s (TTL remaining: %ds)%n",
                            domain, entry.ipAddress, entry.getRemainingTTL());
                }
            });
        }
    }

    public void shutdown() {
        cleaner.shutdown();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Problem 3: DNS Cache with TTL ===\n");

        Problem3_DNSCacheWithTTL dnsCache = new Problem3_DNSCacheWithTTL(1000);

        System.out.println("--- Initial DNS Resolutions (Cache Misses) ---");
        dnsCache.resolve("google.com", 300);
        dnsCache.resolve("facebook.com", 600);
        dnsCache.resolve("github.com", 300);
        dnsCache.resolve("stackoverflow.com", 120);

        System.out.println("\n--- Repeated Lookups (Cache Hits) ---");
        dnsCache.resolve("google.com", 300);
        dnsCache.resolve("google.com", 300);
        dnsCache.resolve("facebook.com", 600);
        dnsCache.resolve("github.com", 300);

        System.out.println("\n--- Cache Contents ---");
        dnsCache.printCache();

        System.out.println("\n--- Invalidation Test ---");
        dnsCache.invalidate("google.com");
        dnsCache.resolve("google.com", 300); // Should re-query upstream

        System.out.println("\n--- TTL Expiry Simulation ---");
        System.out.println("Resolving with 1-second TTL...");
        dnsCache.resolve("amazon.com", 1);
        dnsCache.resolve("amazon.com", 1); // Hit
        System.out.println("Waiting 2 seconds for TTL to expire...");
        Thread.sleep(2000);
        dnsCache.resolve("amazon.com", 1); // Expired → re-query

        System.out.println("\n--- Cache Statistics ---");
        dnsCache.getCacheStats();

        System.out.println("\n--- Hash Table Properties ---");
        System.out.println("  Lookup Time Complexity : O(1) average");
        System.out.println("  Eviction Policy        : LRU via LinkedHashMap (access-order)");
        System.out.println("  TTL Cleanup            : Background ScheduledExecutorService thread");
        System.out.println("  Collision Resolution   : Java LinkedHashMap uses chaining");

        dnsCache.shutdown();
    }
}
