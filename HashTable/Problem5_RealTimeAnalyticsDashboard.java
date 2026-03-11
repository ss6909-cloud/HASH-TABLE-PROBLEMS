import java.util.*;

/**
 * Problem 5: Real-Time Analytics Dashboard for Website Traffic
 *
 * Scenario: News website with 1 million page views/hour.
 * Tracks top pages, unique visitors, and traffic sources using multiple hash
 * tables.
 */
public class Problem5_RealTimeAnalyticsDashboard {

    /**
     * Represents a single page view event.
     */
    static class PageViewEvent {
        String url;
        String userId;
        String source; // google, facebook, direct, etc.
        long timestamp;

        PageViewEvent(String url, String userId, String source) {
            this.url = url;
            this.userId = userId;
            this.source = source;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // HashMap<pageUrl, visitCount> — total page view counts
    private final Map<String, Long> pageViewCounts = new HashMap<>();

    // HashMap<pageUrl, Set<userId>> — unique visitor tracking per page
    private final Map<String, Set<String>> uniqueVisitors = new HashMap<>();

    // HashMap<source, count> — traffic source distribution
    private final Map<String, Long> trafficSources = new HashMap<>();

    // Total events processed
    private long totalEvents = 0;
    private long lastDashboardUpdate = System.currentTimeMillis();

    /**
     * Process a single incoming page view event in real-time.
     * All HashMap operations are O(1) average.
     */
    public void processEvent(PageViewEvent event) {
        // Update page view count
        pageViewCounts.merge(event.url, 1L, Long::sum);

        // Track unique visitor for this page
        uniqueVisitors.computeIfAbsent(event.url, k -> new HashSet<>()).add(event.userId);

        // Track traffic source
        trafficSources.merge(event.source, 1L, Long::sum);

        totalEvents++;

        // Auto-update dashboard every batch of 1000 events
        if (totalEvents % 1000 == 0) {
            System.out.printf("[Auto-update] Processed %,d events.%n", totalEvents);
        }
    }

    /**
     * Process a batch of events (simulates stream ingestion).
     */
    public void processEvents(List<PageViewEvent> events) {
        for (PageViewEvent e : events) {
            processEvent(e);
        }
    }

    /**
     * Get top N most visited pages using a min-heap (PriorityQueue).
     * Time Complexity: O(n log k) where k = top N
     */
    public List<Map.Entry<String, Long>> getTopPages(int n) {
        // Min-heap of size n to find top n entries
        PriorityQueue<Map.Entry<String, Long>> minHeap = new PriorityQueue<>(n, Map.Entry.comparingByValue());

        for (Map.Entry<String, Long> entry : pageViewCounts.entrySet()) {
            minHeap.offer(entry);
            if (minHeap.size() > n) {
                minHeap.poll(); // remove smallest
            }
        }

        // Convert to sorted list (descending)
        List<Map.Entry<String, Long>> topPages = new ArrayList<>(minHeap);
        topPages.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return topPages;
    }

    /**
     * Get the number of unique visitors for a page.
     */
    public int getUniqueVisitors(String url) {
        Set<String> visitors = uniqueVisitors.get(url);
        return (visitors != null) ? visitors.size() : 0;
    }

    /**
     * Calculate traffic source percentages.
     */
    public Map<String, Double> getTrafficSourcePercentages() {
        Map<String, Double> percentages = new LinkedHashMap<>();
        if (totalEvents == 0)
            return percentages;

        trafficSources.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> percentages.put(e.getKey(), e.getValue() * 100.0 / totalEvents));

        return percentages;
    }

    /**
     * Print the full real-time analytics dashboard.
     */
    public void getDashboard() {
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║          REAL-TIME ANALYTICS DASHBOARD               ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.printf("  Total Events Processed: %,d%n%n", totalEvents);

        // Top Pages
        System.out.println("  📊 Top Pages:");
        List<Map.Entry<String, Long>> topPages = getTopPages(5);
        for (int i = 0; i < topPages.size(); i++) {
            String url = topPages.get(i).getKey();
            long views = topPages.get(i).getValue();
            int unique = getUniqueVisitors(url);
            System.out.printf("  %d. %-40s %,7d views (%,d unique)%n",
                    i + 1, url, views, unique);
        }

        // Traffic Sources
        System.out.println("\n  🌐 Traffic Sources:");
        Map<String, Double> sourcePcts = getTrafficSourcePercentages();
        sourcePcts.forEach((source, pct) -> System.out.printf("     %-12s : %.1f%%%n", source, pct));

        System.out.printf("%n  ⏱  Last updated: %dms ago%n",
                System.currentTimeMillis() - lastDashboardUpdate);
        lastDashboardUpdate = System.currentTimeMillis();
        System.out.println("══════════════════════════════════════════════════════\n");
    }

    /**
     * Generate a large batch of simulated page view events.
     */
    public static List<PageViewEvent> generateSampleEvents(int count) {
        String[] urls = {
                "/article/breaking-news", "/sports/championship", "/tech/ai-update",
                "/politics/election", "/entertainment/movies", "/health/tips",
                "/finance/stocks", "/travel/destinations", "/science/space",
                "/world/international"
        };
        String[] sources = { "google", "facebook", "direct", "twitter", "email" };
        double[] sourceWeights = { 0.45, 0.15, 0.30, 0.07, 0.03 };

        Random rng = new Random(42);
        List<PageViewEvent> events = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String url = urls[rng.nextInt(urls.length)];
            String userId = "user_" + rng.nextInt(50000);

            // Weighted source selection
            double r = rng.nextDouble();
            double cumulative = 0;
            String source = "direct";
            for (int s = 0; s < sources.length; s++) {
                cumulative += sourceWeights[s];
                if (r < cumulative) {
                    source = sources[s];
                    break;
                }
            }
            events.add(new PageViewEvent(url, userId, source));
        }
        return events;
    }

    public static void main(String[] args) {
        System.out.println("=== Problem 5: Real-Time Analytics Dashboard ===\n");

        Problem5_RealTimeAnalyticsDashboard dashboard = new Problem5_RealTimeAnalyticsDashboard();

        // Manual event processing (matching sample I/O)
        System.out.println("--- Processing Individual Events ---");
        dashboard.processEvent(new PageViewEvent("/article/breaking-news", "user_123", "google"));
        dashboard.processEvent(new PageViewEvent("/article/breaking-news", "user_456", "facebook"));
        dashboard.processEvent(new PageViewEvent("/sports/championship", "user_789", "direct"));

        // Bulk simulation
        System.out.println("\n--- Simulating 10,000 Page View Events ---");
        long start = System.currentTimeMillis();
        List<PageViewEvent> events = generateSampleEvents(10000);
        dashboard.processEvents(events);
        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("Processed 10,000 events in %dms (%.0f events/sec)%n",
                elapsed, 10000.0 / Math.max(elapsed, 1) * 1000);

        // Print dashboard
        dashboard.getDashboard();

        System.out.println("--- Hash Table Properties ---");
        System.out.println("  pageViewCounts   : HashMap<String, Long>  - O(1) increment");
        System.out.println("  uniqueVisitors   : HashMap<String, Set>   - O(1) Set.add()");
        System.out.println("  trafficSources   : HashMap<String, Long>  - O(1) increment");
        System.out.println("  Top-N Pages      : Min-Heap PriorityQueue - O(n log k)");
    }
}
