import java.util.*;

/**
 * Problem 7: Autocomplete System for Search Engine
 *
 * Scenario: Google-like autocomplete based on 10 million previous search
 * queries.
 * Uses a Trie for prefix matching with a HashMap for frequency storage.
 */
public class Problem7_AutocompleteSystem {

    /**
     * Trie node class. Each node stores a HashMap of children and frequency.
     */
    static class TrieNode {
        // character → child TrieNode
        Map<Character, TrieNode> children = new HashMap<>();
        String completeWord; // non-null if this is end of a word
        int frequency; // search frequency of the complete word
    }

    // Root of the Trie
    private final TrieNode root = new TrieNode();

    // HashMap<query, frequency> — global frequency store for O(1) update
    private final Map<String, Integer> queryFrequency = new HashMap<>();

    // Cache: HashMap<prefix, List<suggestion>> for frequently used prefixes
    private final Map<String, List<String>> prefixCache = new HashMap<>();
    private static final int CACHE_SIZE = 10000;

    public Problem7_AutocompleteSystem() {
    }

    /**
     * Insert a query with given frequency into the Trie and frequency map.
     * Time Complexity: O(L) where L = query length
     */
    public void insert(String query, int frequency) {
        query = query.toLowerCase().trim();
        queryFrequency.put(query, queryFrequency.getOrDefault(query, 0) + frequency);

        TrieNode current = root;
        for (char c : query.toCharArray()) {
            current.children.putIfAbsent(c, new TrieNode());
            current = current.children.get(c);
        }
        current.completeWord = query;
        current.frequency = queryFrequency.get(query);

        // Invalidate cache for all prefixes of this query
        final String finalQuery = query;
        prefixCache.keySet().removeIf(cachedPrefix -> finalQuery.startsWith(cachedPrefix));
    }

    /**
     * Update frequency of an existing query (e.g., new search recorded).
     */
    public void updateFrequency(String query) {
        query = query.toLowerCase().trim();
        int oldFreq = queryFrequency.getOrDefault(query, 0);
        int newFreq = oldFreq + 1;
        System.out.printf("updateFrequency(\"%s\") → Frequency: %d → %d%s%n",
                query, oldFreq, newFreq,
                newFreq > oldFreq * 1.5 && newFreq > 100 ? " (trending!)" : "");
        insert(query, 1);
    }

    /**
     * DFS to collect all completions from a given TrieNode.
     */
    private void collectCompletions(TrieNode node, List<String[]> results) {
        if (node.completeWord != null) {
            results.add(new String[] { node.completeWord, String.valueOf(node.frequency) });
        }
        for (TrieNode child : node.children.values()) {
            collectCompletions(child, results);
        }
    }

    /**
     * Get top K autocomplete suggestions for a given prefix.
     * Uses Trie prefix traversal + min-heap for top K by frequency.
     *
     * @param prefix the typed prefix
     * @param k      number of suggestions to return
     * @return list of (query, frequency) pairs sorted by frequency descending
     */
    public List<Map.Entry<String, Integer>> search(String prefix, int k) {
        prefix = prefix.toLowerCase().trim();

        // Check cache first
        if (prefixCache.containsKey(prefix)) {
            List<String> cached = prefixCache.get(prefix);
            System.out.printf("search(\"%s\") [CACHE HIT] →%n", prefix);
            List<Map.Entry<String, Integer>> cachedResults = new ArrayList<>();
            for (String q : cached) {
                cachedResults.add(new AbstractMap.SimpleEntry<>(q, queryFrequency.getOrDefault(q, 0)));
            }
            return cachedResults;
        }

        // Navigate to prefix node in Trie
        TrieNode current = root;
        for (char c : prefix.toCharArray()) {
            if (!current.children.containsKey(c)) {
                System.out.printf("search(\"%s\") → No suggestions found.%n", prefix);
                return Collections.emptyList();
            }
            current = current.children.get(c);
        }

        // Collect all completions from this node
        List<String[]> allCompletions = new ArrayList<>();
        collectCompletions(current, allCompletions);

        // Use min-heap of size k to find top-k by frequency
        PriorityQueue<String[]> minHeap = new PriorityQueue<>(k + 1,
                Comparator.comparingInt(a -> Integer.parseInt(a[1])));

        for (String[] completion : allCompletions) {
            minHeap.offer(completion);
            if (minHeap.size() > k)
                minHeap.poll();
        }

        // Sort descending by frequency
        List<Map.Entry<String, Integer>> results = new ArrayList<>();
        while (!minHeap.isEmpty()) {
            String[] item = minHeap.poll();
            results.add(0, new AbstractMap.SimpleEntry<>(item[0], Integer.parseInt(item[1])));
        }

        // Cache this result
        if (prefixCache.size() < CACHE_SIZE) {
            List<String> queryList = new ArrayList<>();
            results.forEach(e -> queryList.add(e.getKey()));
            prefixCache.put(prefix, queryList);
        }

        return results;
    }

    /**
     * Print autocomplete results for a prefix in formatted style.
     */
    public void printSuggestions(String prefix, int k) {
        System.out.printf("search(\"%s\") →%n", prefix);
        List<Map.Entry<String, Integer>> suggestions = search(prefix, k);
        if (suggestions.isEmpty()) {
            System.out.println("  (no suggestions)");
            return;
        }
        for (int i = 0; i < suggestions.size(); i++) {
            System.out.printf("  %d. \"%s\" (%,d searches)%n",
                    i + 1, suggestions.get(i).getKey(), suggestions.get(i).getValue());
        }
    }

    /**
     * Simple typo correction: find closest query by edit distance.
     */
    public String correctTypo(String query) {
        query = query.toLowerCase();
        String best = query;
        int bestDist = Integer.MAX_VALUE;

        for (String stored : queryFrequency.keySet()) {
            // Only check words close in length
            if (Math.abs(stored.length() - query.length()) > 3)
                continue;
            int dist = editDistance(query, stored);
            if (dist < bestDist || (dist == bestDist && queryFrequency.get(stored) > queryFrequency.get(best))) {
                bestDist = dist;
                best = stored;
            }
        }

        if (!best.equals(query) && bestDist <= 2) {
            System.out.printf("Did you mean: \"%s\"? (edit distance: %d)%n", best, bestDist);
        }
        return best;
    }

    private int editDistance(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++)
            dp[i][0] = i;
        for (int j = 0; j <= n; j++)
            dp[0][j] = j;
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++)
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1) ? dp[i - 1][j - 1]
                        : 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
        return dp[m][n];
    }

    public static void main(String[] args) {
        System.out.println("=== Problem 7: Autocomplete System for Search Engine ===\n");

        Problem7_AutocompleteSystem autocomplete = new Problem7_AutocompleteSystem();

        System.out.println("--- Loading Search Query Database ---");
        // Java-related queries
        autocomplete.insert("java tutorial", 1_234_567);
        autocomplete.insert("javascript", 987_654);
        autocomplete.insert("java download", 456_789);
        autocomplete.insert("java 21 features", 1);
        autocomplete.insert("java interview questions", 345_678);
        autocomplete.insert("java spring boot", 289_000);
        autocomplete.insert("java vs python", 201_340);
        autocomplete.insert("java stream api", 178_900);

        // Other queries
        autocomplete.insert("python tutorial", 1_100_000);
        autocomplete.insert("python machine learning", 890_000);
        autocomplete.insert("javascript frameworks", 670_000);
        autocomplete.insert("javascript react", 820_000);
        autocomplete.insert("data structures and algorithms", 560_000);
        autocomplete.insert("hash table implementation", 45_000);
        autocomplete.insert("hash map vs hash set", 32_000);

        System.out.println("Database loaded with " + autocomplete.queryFrequency.size() + " queries.\n");

        System.out.println("--- Autocomplete Suggestions ---");
        autocomplete.printSuggestions("jav", 5);
        System.out.println();
        autocomplete.printSuggestions("java ", 5);
        System.out.println();
        autocomplete.printSuggestions("hash", 5);

        System.out.println("\n--- Frequency Updates (Trending) ---");
        for (int i = 0; i < 5; i++)
            autocomplete.updateFrequency("java 21 features");

        System.out.println("\n--- After Update ---");
        autocomplete.printSuggestions("java 2", 3);

        System.out.println("\n--- Typo Correction ---");
        autocomplete.correctTypo("javascrpt"); // typo of javascript
        autocomplete.correctTypo("hsh tble"); // typo of hash table

        System.out.println("\n--- Hash Table Properties ---");
        System.out.println("  queryFrequency : HashMap<String, Integer>  — O(1) lookup & update");
        System.out.println("  TrieNode.children: HashMap<Character, TrieNode> — O(1) char navigation");
        System.out.println("  prefixCache    : HashMap<prefix, List>     — O(1) cache lookup");
        System.out.println("  Top-K Search   : Min-Heap of size K        — O(n log k)");
    }
}
