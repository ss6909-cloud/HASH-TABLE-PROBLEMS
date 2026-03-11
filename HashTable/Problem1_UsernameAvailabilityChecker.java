import java.util.*;

/**
 * Problem 1: Social Media Username Availability Checker
 *
 * Scenario: Registration system for a social media platform with 10 million users.
 * Uses HashMap for O(1) lookup, tracks attempt frequencies, and suggests alternatives.
 */
public class Problem1_UsernameAvailabilityChecker {

    // username -> userId mapping (registered users)
    private final Map<String, Integer> registeredUsers = new HashMap<>();

    // username attempt frequency counter
    private final Map<String, Integer> attemptFrequency = new HashMap<>();

    public Problem1_UsernameAvailabilityChecker() {
        // Pre-populate with some existing users
        registeredUsers.put("john_doe", 1001);
        registeredUsers.put("admin", 1002);
        registeredUsers.put("user123", 1003);
        registeredUsers.put("jane", 1004);
        registeredUsers.put("superuser", 1005);

        // Simulate previous attempt counts
        attemptFrequency.put("admin", 10543);
        attemptFrequency.put("john_doe", 4320);
        attemptFrequency.put("user123", 3210);
    }

    /**
     * Check if a username is available in O(1) time.
     * Also increments attempt frequency tracker.
     *
     * @param username the username to check
     * @return true if available, false if taken
     */
    public boolean checkAvailability(String username) {
        // Track every check attempt
        attemptFrequency.merge(username, 1, Integer::sum);

        boolean available = !registeredUsers.containsKey(username);
        System.out.printf("checkAvailability(\"%s\") → %s%n",
                username, available ? "true (available)" : "false (already taken)");
        return available;
    }

    /**
     * Register a username for the given user ID.
     *
     * @param username the desired username
     * @param userId   the user's ID
     * @return true if registered successfully, false if already taken
     */
    public boolean registerUsername(String username, int userId) {
        if (registeredUsers.containsKey(username)) {
            System.out.printf("registerUsername(\"%s\", %d) → FAILED (username taken)%n", username, userId);
            return false;
        }
        registeredUsers.put(username, userId);
        System.out.printf("registerUsername(\"%s\", %d) → SUCCESS%n", username, userId);
        return true;
    }

    /**
     * Suggest alternative usernames when requested one is taken.
     * Tries numeric suffixes and dot-separator variants.
     *
     * @param username the taken username
     * @return list of available alternative usernames
     */
    public List<String> suggestAlternatives(String username) {
        List<String> suggestions = new ArrayList<>();

        // Try numeric suffixes: username1, username2, username3
        for (int i = 1; i <= 5; i++) {
            String candidate = username + i;
            if (!registeredUsers.containsKey(candidate)) {
                suggestions.add(candidate);
            }
        }

        // Try dot separator: john.doe
        String dotVariant = username.replace("_", ".");
        if (!dotVariant.equals(username) && !registeredUsers.containsKey(dotVariant)) {
            suggestions.add(dotVariant);
        }

        // Try underscore removal: johndoe
        String compactVariant = username.replace("_", "");
        if (!compactVariant.equals(username) && !registeredUsers.containsKey(compactVariant)) {
            suggestions.add(compactVariant);
        }

        // Try underscore + year suffix
        String yearVariant = username + "_2024";
        if (!registeredUsers.containsKey(yearVariant)) {
            suggestions.add(yearVariant);
        }

        System.out.printf("suggestAlternatives(\"%s\") → %s%n", username, suggestions);
        return suggestions;
    }

    /**
     * Returns the most attempted username across all checks.
     *
     * @return the username with the highest attempt frequency
     */
    public String getMostAttempted() {
        String mostAttempted = Collections.max(
                attemptFrequency.entrySet(),
                Map.Entry.comparingByValue()
        ).getKey();

        int count = attemptFrequency.get(mostAttempted);
        System.out.printf("getMostAttempted() → \"%s\" (%,d attempts)%n", mostAttempted, count);
        return mostAttempted;
    }

    /**
     * Returns top N most attempted usernames.
     *
     * @param n number of top usernames to return
     * @return sorted list of (username, count) pairs
     */
    public List<Map.Entry<String, Integer>> getTopAttempted(int n) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(attemptFrequency.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        List<Map.Entry<String, Integer>> top = entries.subList(0, Math.min(n, entries.size()));
        System.out.println("\nTop " + n + " most attempted usernames:");
        for (int i = 0; i < top.size(); i++) {
            System.out.printf("  %d. \"%s\" - %,d attempts%n",
                    i + 1, top.get(i).getKey(), top.get(i).getValue());
        }
        return top;
    }

    /**
     * Returns total number of registered users (HashMap size).
     */
    public int getTotalUsers() {
        return registeredUsers.size();
    }

    public static void main(String[] args) {
        System.out.println("=== Problem 1: Social Media Username Availability Checker ===\n");

        Problem1_UsernameAvailabilityChecker checker = new Problem1_UsernameAvailabilityChecker();

        System.out.println("--- Availability Checks ---");
        checker.checkAvailability("john_doe");       // false - already taken
        checker.checkAvailability("jane_smith");     // true - available
        checker.checkAvailability("admin");          // false - already taken
        checker.checkAvailability("new_user");       // true - available

        System.out.println("\n--- Suggesting Alternatives ---");
        checker.suggestAlternatives("john_doe");
        checker.suggestAlternatives("admin");

        System.out.println("\n--- Registering New User ---");
        checker.registerUsername("jane_smith", 5001);
        checker.checkAvailability("jane_smith");     // now taken

        System.out.println("\n--- Frequency Analysis ---");
        // Simulate additional checks to build frequency data
        for (int i = 0; i < 100; i++) checker.checkAvailability("admin");
        checker.getMostAttempted();
        checker.getTopAttempted(3);

        System.out.println("\n--- System Stats ---");
        System.out.println("Total registered users: " + checker.getTotalUsers());
        System.out.println("\nHash Table Properties:");
        System.out.println("  Lookup Time Complexity : O(1) average");
        System.out.println("  Space Complexity       : O(n) where n = number of users");
        System.out.println("  Collision Resolution   : Java HashMap uses chaining (linked list/tree)");
    }
}
