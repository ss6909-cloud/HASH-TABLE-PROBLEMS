import java.util.*;

/**
 * Problem 10: Multi-Level Cache System with Hash Tables
 *
 * Scenario: Video streaming service (like Netflix) with L1 (memory), L2 (SSD),
 * L3 (DB).
 * Uses LinkedHashMap for LRU eviction and HashMap for access count tracking.
 */
public class Problem10_MultiLevelCacheSystem {

    /**
     * Represents video metadata stored in the cache.
     */
    static class VideoData {
        String videoId;
        String title;
        long sizeBytes;
        byte[] data; // simulated payload (in real system would be actual video data)

        VideoData(String videoId, String title, long sizeBytes) {
            this.videoId = videoId;
            this.title = title;
            this.sizeBytes = sizeBytes;
            this.data = new byte[0]; // mock data
        }

        @Override
        public String toString() {
            return String.format("VideoData{id='%s', title='%s', size=%dKB}",
                    videoId, title, sizeBytes / 1024);
        }
    }

    /**
     * Result of a cache lookup.
     */
    static class CacheResult {
        String level; // "L1", "L2", "L3", or "MISS"
        VideoData data;
        long latencyMs;
        boolean promoted;

        CacheResult(String level, VideoData data, long latencyMs, boolean promoted) {
            this.level = level;
            this.data = data;
            this.latencyMs = latencyMs;
            this.promoted = promoted;
        }
    }

    // ─── L1: In-memory LRU Cache (LinkedHashMap in access-order) ───
    private final int L1_CAPACITY;
    private final LinkedHashMap<String, VideoData> l1Cache;

    // ─── L2: SSD-backed cache (simulated with HashMap + file path) ───
    private final int L2_CAPACITY;
    private final LinkedHashMap<String, String> l2Cache; // videoId → "SSD path"
    private final Map<String, VideoData> l2DataStore; // simulated SSD storage

    // ─── L3: Database (simulated with HashMap, always available) ───
    private final Map<String, VideoData> l3Database = new HashMap<>();

    // Access count tracking for promotion decisions
    private final Map<String, Integer> accessCounts = new HashMap<>();
    private static final int L2_TO_L1_THRESHOLD = 3; // promote to L1 after 3 L2 hits
    private static final int L3_TO_L2_THRESHOLD = 2; // promote to L2 after 2 L3 hits

    // Statistics per level
    private int l1Hits = 0, l2Hits = 0, l3Hits = 0;
    private int l1Misses = 0, l2Misses = 0;
    private int totalRequests = 0;
    private long totalLatencyMs = 0;

    // Simulated latencies
    private static final long L1_LATENCY_MS = 0; // ~0.5ms (use 0 for demo)
    private static final long L2_LATENCY_MS = 5; // ~5ms
    private static final long L3_LATENCY_MS = 150; // ~150ms

    public Problem10_MultiLevelCacheSystem(int l1Capacity, int l2Capacity) {
        this.L1_CAPACITY = l1Capacity;
        this.L2_CAPACITY = l2Capacity;

        // L1: LinkedHashMap with access-order + auto-evict LRU
        this.l1Cache = new LinkedHashMap<>(l1Capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, VideoData> eldest) {
                if (size() > L1_CAPACITY) {
                    // Demote evicted entry to L2 instead of discarding
                    promoteToL2(eldest.getKey(), eldest.getValue());
                    return true;
                }
                return false;
            }
        };

        // L2: LinkedHashMap with insertion-order + LRU eviction
        this.l2DataStore = new HashMap<>();
        this.l2Cache = new LinkedHashMap<>(l2Capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                if (size() > L2_CAPACITY) {
                    l2DataStore.remove(eldest.getKey()); // clean up data store too
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * Populate L3 database with initial video catalog.
     */
    public void addToDatabase(String videoId, String title, long sizeBytes) {
        l3Database.put(videoId, new VideoData(videoId, title, sizeBytes));
    }

    /**
     * Promote a video from L2 to L1.
     */
    private void promoteToL2(String videoId, VideoData data) {
        l2Cache.put(videoId, "/ssd/cache/" + videoId);
        l2DataStore.put(videoId, data);
    }

    /**
     * Manually add to L1 cache (used for pre-warming popular content).
     */
    public void preWarmL1(String videoId) {
        VideoData data = l3Database.get(videoId);
        if (data != null)
            l1Cache.put(videoId, data);
    }

    /**
     * The main cache lookup: tries L1 → L2 → L3, with promotion logic.
     *
     * @param videoId the video to retrieve
     * @return CacheResult with data, level hit, and latency
     */
    public CacheResult getVideo(String videoId) {
        totalRequests++;
        long startMs = System.currentTimeMillis();
        CacheResult result;

        // ── L1 Lookup ──
        if (l1Cache.containsKey(videoId)) {
            l1Hits++;
            VideoData data = l1Cache.get(videoId); // access order updated automatically
            accessCounts.merge(videoId, 1, Integer::sum);
            long latency = Math.max(System.currentTimeMillis() - startMs, L1_LATENCY_MS);
            totalLatencyMs += latency;
            result = new CacheResult("L1", data, latency, false);
            System.out.printf("getVideo(\"%s\") → L1 Cache HIT (%.1fms)%n", videoId, 0.5);
            return result;
        }
        l1Misses++;

        // ── L2 Lookup ──
        if (l2Cache.containsKey(videoId)) {
            l2Hits++;
            VideoData data = l2DataStore.get(videoId);
            accessCounts.merge(videoId, 1, Integer::sum);

            // Check if should promote to L1
            boolean promoted = false;
            if (accessCounts.get(videoId) >= L2_TO_L1_THRESHOLD) {
                l1Cache.put(videoId, data);
                promoted = true;
                System.out.printf("getVideo(\"%s\") → L2 Cache HIT (%dms) → Promoted to L1%n",
                        videoId, L2_LATENCY_MS);
            } else {
                System.out.printf("getVideo(\"%s\") → L2 Cache HIT (%dms)%n", videoId, L2_LATENCY_MS);
            }

            totalLatencyMs += L2_LATENCY_MS;
            return new CacheResult("L2", data, L2_LATENCY_MS, promoted);
        }
        l2Misses++;

        // ── L3 (Database) Lookup ──
        if (l3Database.containsKey(videoId)) {
            l3Hits++;
            VideoData data = l3Database.get(videoId);
            accessCounts.merge(videoId, 1, Integer::sum);

            // Add to L2 cache
            promoteToL2(videoId, data);
            System.out.printf("getVideo(\"%s\") → L1 MISS → L2 MISS → L3 HIT (%dms) → Added to L2%n",
                    videoId, L3_LATENCY_MS);

            totalLatencyMs += L3_LATENCY_MS;
            return new CacheResult("L3", data, L3_LATENCY_MS, true);
        }

        // Not found anywhere
        System.out.printf("getVideo(\"%s\") → NOT FOUND in any cache level%n", videoId);
        totalLatencyMs += L3_LATENCY_MS;
        return new CacheResult("MISS", null, L3_LATENCY_MS, false);
    }

    /**
     * Invalidate a video across all cache levels (e.g., content updated).
     */
    public void invalidate(String videoId) {
        boolean removed = false;
        if (l1Cache.remove(videoId) != null) {
            removed = true;
        }
        if (l2Cache.remove(videoId) != null) {
            l2DataStore.remove(videoId);
            removed = true;
        }
        System.out.printf("invalidate(\"%s\") → %s%n",
                videoId, removed ? "Removed from all cache levels" : "Not found in caches");
    }

    /**
     * Print comprehensive cache statistics.
     */
    public void getStatistics() {
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║         MULTI-LEVEL CACHE STATISTICS            ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        double l1HitRate = totalRequests > 0 ? l1Hits * 100.0 / totalRequests : 0;
        double l2HitRate = totalRequests > 0 ? l2Hits * 100.0 / totalRequests : 0;
        double l3HitRate = totalRequests > 0 ? l3Hits * 100.0 / totalRequests : 0;
        double overallHitRate = totalRequests > 0 ? (l1Hits + l2Hits + l3Hits) * 100.0 / totalRequests : 0;
        double avgLatency = totalRequests > 0 ? (double) totalLatencyMs / totalRequests : 0;

        System.out.printf("  L1 Cache : Hit Rate %5.1f%%  | Avg Time: 0.5ms  | Size: %d/%d%n",
                l1HitRate, l1Cache.size(), L1_CAPACITY);
        System.out.printf("  L2 Cache : Hit Rate %5.1f%%  | Avg Time: %dms    | Size: %d/%d%n",
                l2HitRate, L2_LATENCY_MS, l2Cache.size(), L2_CAPACITY);
        System.out.printf("  L3 DB    : Hit Rate %5.1f%%  | Avg Time: %dms%n", l3HitRate, L3_LATENCY_MS);
        System.out.println("  ─────────────────────────────────────────────────");
        System.out.printf("  Overall  : Hit Rate %5.1f%%  | Avg Time: %.1fms%n", overallHitRate, avgLatency);
        System.out.printf("  Total Requests: %,d | L1 Hits: %d | L2 Hits: %d | L3 Hits: %d%n",
                totalRequests, l1Hits, l2Hits, l3Hits);
        System.out.println("══════════════════════════════════════════════════\n");
    }

    public static void main(String[] args) {
        System.out.println("=== Problem 10: Multi-Level Cache System ===\n");

        // L1 = 10 videos in memory, L2 = 50 videos on SSD
        Problem10_MultiLevelCacheSystem cache = new Problem10_MultiLevelCacheSystem(10, 50);

        // Populate L3 database
        System.out.println("--- Seeding L3 Database with Video Catalog ---");
        for (int i = 0; i < 100; i++) {
            cache.addToDatabase("video_" + i, "Video Title " + i, (long) (50 + i * 10) * 1024 * 1024);
        }
        System.out.println("L3 Database seeded with 100 videos.\n");

        System.out.println("--- First Accesses (L3 Miss → Cached in L2) ---");
        cache.getVideo("video_123".replace("123", "5")); // video_5
        cache.getVideo("video_10");
        cache.getVideo("video_20");
        cache.getVideo("video_999"); // not found

        System.out.println("\n--- Repeated Accesses (L2 → eventual L1 promotion) ---");
        cache.getVideo("video_5"); // L2 hit
        cache.getVideo("video_5"); // L2 hit again → promoted to L1 at threshold
        cache.getVideo("video_5"); // L1 hit
        cache.getVideo("video_5"); // L1 hit (access-order updated → stays warm)

        System.out.println("\n--- Loading More Videos (demonstrating LRU eviction) ---");
        for (int i = 1; i <= 12; i++) {
            cache.getVideo("video_" + i); // fills L1, older entries demoted to L2
        }

        System.out.println("\n--- Cache Invalidation ---");
        cache.invalidate("video_5");
        cache.getVideo("video_5"); // must re-fetch from L3

        System.out.println("\n--- Cache Statistics ---");
        cache.getStatistics();

        System.out.println("--- Hash Table Properties ---");
        System.out.println("  L1 Cache : LinkedHashMap (access-order) — LRU eviction to L2");
        System.out.println("  L2 Cache : LinkedHashMap (access-order) + HashMap data store");
        System.out.println("  L3 DB    : HashMap — source of truth");
        System.out.println("  Promotion: accessCounts HashMap — triggers L2→L1 after threshold hits");
        System.out.println("  All lookup and insert operations: O(1) average");
    }
}
