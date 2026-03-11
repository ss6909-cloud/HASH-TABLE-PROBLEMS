import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Problem 6: Distributed Rate Limiter for API Gateway
 *
 * Scenario: API gateway for 100,000 clients, each limited to 1000
 * requests/hour.
 * Uses token bucket algorithm with HashMap for O(1) client tracking.
 */
public class Problem6_RateLimiter {

    /**
     * Token bucket for a single client.
     * Implements sliding refill: tokens are added based on time elapsed since last
     * refill.
     */
    static class TokenBucket {
        final String clientId;
        final int maxTokens; // max requests per window
        final double refillRate; // tokens per millisecond
        double tokens; // current available tokens
        long lastRefillTime; // when we last added tokens (ms)
        final AtomicLong totalRequests = new AtomicLong(0);
        final AtomicLong deniedRequests = new AtomicLong(0);

        TokenBucket(String clientId, int maxTokens, long windowMs) {
            this.clientId = clientId;
            this.maxTokens = maxTokens;
            this.refillRate = (double) maxTokens / windowMs; // tokens/ms
            this.tokens = maxTokens; // start full
            this.lastRefillTime = System.currentTimeMillis();
        }

        /**
         * Attempt to consume a token. Thread-safe via synchronized.
         * 
         * @return RateLimitResult with allowed status and remaining tokens
         */
        synchronized RateLimitResult consume(int tokensNeeded) {
            refill();
            totalRequests.incrementAndGet();

            if (tokens >= tokensNeeded) {
                tokens -= tokensNeeded;
                long retryAfterMs = 0;
                return new RateLimitResult(true, (int) Math.floor(tokens), maxTokens,
                        computeResetTime(), 0);
            } else {
                deniedRequests.incrementAndGet();
                // Calculate wait time until tokensNeeded tokens are available
                long retryAfterMs = (long) ((tokensNeeded - tokens) / refillRate);
                long retryAfterSec = retryAfterMs / 1000;
                return new RateLimitResult(false, 0, maxTokens,
                        computeResetTime(), retryAfterSec);
            }
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            double newTokens = elapsed * refillRate;
            tokens = Math.min(maxTokens, tokens + newTokens);
            lastRefillTime = now;
        }

        private long computeResetTime() {
            // Unix timestamp when bucket will be full again
            long msToFull = (long) ((maxTokens - tokens) / refillRate);
            return (System.currentTimeMillis() + msToFull) / 1000;
        }
    }

    /**
     * Result of a rate limit check.
     */
    static class RateLimitResult {
        boolean allowed;
        int remaining;
        int limit;
        long resetTime; // Unix timestamp when limit resets
        long retryAfterSec;

        RateLimitResult(boolean allowed, int remaining, int limit, long resetTime, long retryAfterSec) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.limit = limit;
            this.resetTime = resetTime;
            this.retryAfterSec = retryAfterSec;
        }
    }

    // HashMap<clientId, TokenBucket> — O(1) client lookup
    private final ConcurrentHashMap<String, TokenBucket> clientBuckets = new ConcurrentHashMap<>();

    private final int maxRequestsPerWindow;
    private final long windowMs; // milliseconds

    public Problem6_RateLimiter(int maxRequestsPerWindow, long windowMs) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowMs = windowMs;
    }

    /**
     * Get or create a token bucket for the client. O(1) average.
     */
    private TokenBucket getBucket(String clientId) {
        return clientBuckets.computeIfAbsent(clientId,
                id -> new TokenBucket(id, maxRequestsPerWindow, windowMs));
    }

    /**
     * Check rate limit for a client and consume one token if allowed.
     *
     * @param clientId the API key or IP address
     * @return RateLimitResult with allowed status and metadata
     */
    public RateLimitResult checkRateLimit(String clientId) {
        return checkRateLimit(clientId, 1);
    }

    /**
     * Check rate limit and consume specified number of tokens.
     */
    public RateLimitResult checkRateLimit(String clientId, int tokens) {
        TokenBucket bucket = getBucket(clientId);
        RateLimitResult result = bucket.consume(tokens);

        if (result.allowed) {
            System.out.printf("checkRateLimit(clientId=\"%s\") → Allowed (%d requests remaining)%n",
                    clientId, result.remaining);
        } else {
            System.out.printf("checkRateLimit(clientId=\"%s\") → Denied (0 requests remaining, retry after %ds)%n",
                    clientId, result.retryAfterSec);
        }
        return result;
    }

    /**
     * Get current rate limit status for a client.
     */
    public void getRateLimitStatus(String clientId) {
        TokenBucket bucket = getBucket(clientId);
        RateLimitResult result = bucket.consume(0); // peek without consuming
        System.out.printf("getRateLimitStatus(\"%s\") → {used: %d, limit: %d, reset: %d}%n",
                clientId,
                bucket.maxTokens - result.remaining,
                bucket.maxTokens,
                result.resetTime);
    }

    /**
     * Print gateway statistics across all registered clients.
     */
    public void printGatewayStats() {
        System.out.println("\n--- API Gateway Rate Limiter Statistics ---");
        System.out.printf("  Active clients       : %,d%n", clientBuckets.size());

        long totalRequests = 0, totalDenied = 0;
        for (TokenBucket bucket : clientBuckets.values()) {
            totalRequests += bucket.totalRequests.get();
            totalDenied += bucket.deniedRequests.get();
        }
        double denyRate = (totalRequests > 0) ? (totalDenied * 100.0 / totalRequests) : 0;
        System.out.printf("  Total requests       : %,d%n", totalRequests);
        System.out.printf("  Total denied         : %,d (%.1f%%)%n", totalDenied, denyRate);
    }

    /**
     * Simulate concurrent API requests from multiple clients.
     */
    public void simulateConcurrentLoad(int numClients, int requestsPerClient) throws InterruptedException {
        System.out.printf("%nSimulating %,d clients × %,d requests each...%n",
                numClients, requestsPerClient);

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(numClients, 100));
        CountDownLatch latch = new CountDownLatch(numClients * requestsPerClient);
        AtomicLong allowed = new AtomicLong();
        AtomicLong denied = new AtomicLong();

        long start = System.currentTimeMillis();
        for (int c = 0; c < numClients; c++) {
            String clientId = "client_" + c;
            for (int r = 0; r < requestsPerClient; r++) {
                executor.submit(() -> {
                    RateLimitResult result = getBucket(clientId).consume(1);
                    if (result.allowed)
                        allowed.incrementAndGet();
                    else
                        denied.incrementAndGet();
                    latch.countDown();
                });
            }
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("Load test complete in %dms: %,d allowed, %,d denied (%.1f%% deny rate)%n",
                elapsed, allowed.get(), denied.get(),
                denied.get() * 100.0 / (allowed.get() + denied.get()));
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Problem 6: Distributed Rate Limiter for API Gateway ===\n");

        // 1000 requests per hour (3,600,000ms window) — using 10 requests/1000ms for
        // demo speed
        Problem6_RateLimiter rateLimiter = new Problem6_RateLimiter(10, 1000);

        System.out.println("--- Sequential Rate Limit Checks ---");
        for (int i = 0; i < 12; i++) {
            rateLimiter.checkRateLimit("abc123");
        }

        System.out.println("\n--- Rate Limit Status ---");
        rateLimiter.getRateLimitStatus("abc123");

        System.out.println("\n--- Different Clients ---");
        rateLimiter.checkRateLimit("client_xyz");
        rateLimiter.checkRateLimit("client_xyz");
        rateLimiter.checkRateLimit("new_client");

        System.out.println("\n--- Token Refill Demo (wait 1 second) ---");
        System.out.println("Waiting 1 second for token refill...");
        Thread.sleep(1000); // tokens replenish after 1 second
        rateLimiter.checkRateLimit("abc123"); // should be allowed again

        System.out.println("\n--- Concurrent Load Test ---");
        Problem6_RateLimiter loadTestLimiter = new Problem6_RateLimiter(1000, 3600000);
        loadTestLimiter.simulateConcurrentLoad(5, 1200); // 5 clients, 1200 requests each (200 should be denied)

        loadTestLimiter.printGatewayStats();

        System.out.println("\n--- Hash Table Properties ---");
        System.out.println("  Client Map       : ConcurrentHashMap<clientId, TokenBucket> — O(1)");
        System.out.println("  Thread Safety    : ConcurrentHashMap + synchronized TokenBucket.consume()");
        System.out.println("  Algorithm        : Sliding Window Token Bucket");
        System.out.println("  Response Time    : O(1) per rate limit check");
    }
}
