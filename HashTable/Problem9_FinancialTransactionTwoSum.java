import java.util.*;

/**
 * Problem 9: Two-Sum Problem Variants for Financial Transactions
 *
 * Scenario: Payment processing company detecting fraud — pairs summing to
 * targets,
 * time-windowed pairs, K-Sum, and duplicate detection using HashMap for O(1)
 * lookups.
 */
public class Problem9_FinancialTransactionTwoSum {

    /**
     * Represents a financial transaction.
     */
    static class Transaction {
        int id;
        double amount;
        String merchant;
        String accountId;
        String time; // "HH:MM" format for simplicity

        Transaction(int id, double amount, String merchant, String accountId, String time) {
            this.id = id;
            this.amount = amount;
            this.merchant = merchant;
            this.accountId = accountId;
            this.time = time;
        }

        @Override
        public String toString() {
            return String.format("{id:%d, amount:%.0f, merchant:\"%s\", account:\"%s\", time:\"%s\"}",
                    id, amount, merchant, accountId, time);
        }
    }

    private final List<Transaction> transactions;

    public Problem9_FinancialTransactionTwoSum(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    // ─────────────────────────────────────────────────────────
    // 1. Classic Two-Sum: Find pairs summing to target amount
    // ─────────────────────────────────────────────────────────

    /**
     * Classic Two-Sum using HashMap for O(n) solution.
     * HashMap<complement, transaction> allows O(1) complement lookup.
     *
     * @param target the target sum
     * @return list of (Transaction, Transaction) pairs
     */
    public List<int[]> findTwoSum(double target) {
        // HashMap<amount, transactionId> — O(1) complement lookup
        Map<Double, Integer> complementMap = new HashMap<>();
        List<int[]> result = new ArrayList<>();

        for (Transaction tx : transactions) {
            double complement = target - tx.amount;

            if (complementMap.containsKey(tx.amount)) {
                int partnerId = complementMap.get(tx.amount);
                result.add(new int[] { partnerId, tx.id });
            }
            complementMap.put(complement, tx.id);
        }

        System.out.printf("findTwoSum(target=%.0f) → %s%n", target, formatPairs(result));
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 2. Two-Sum with Time Window: Pairs within 1 hour
    // ─────────────────────────────────────────────────────────

    /**
     * Convert "HH:MM" time string to minutes since midnight.
     */
    private int timeToMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    /**
     * Find pairs summing to target within a time window (in minutes).
     *
     * @param target        target sum
     * @param windowMinutes max time difference between transactions (e.g., 60 for 1
     *                      hour)
     * @return list of (transactionId1, transactionId2) pairs
     */
    public List<int[]> findTwoSumWithTimeWindow(double target, int windowMinutes) {
        List<int[]> result = new ArrayList<>();

        // Filter by time window using HashMap<amount, list of (id, time)>
        Map<Double, List<int[]>> seenAmounts = new HashMap<>(); // amount → [(id, timeMin)]

        for (Transaction tx : transactions) {
            double complement = target - tx.amount;
            int txTime = timeToMinutes(tx.time);

            List<int[]> partners = seenAmounts.get(complement);
            if (partners != null) {
                for (int[] partner : partners) {
                    int partnerTime = partner[1];
                    if (Math.abs(txTime - partnerTime) <= windowMinutes) {
                        result.add(new int[] { partner[0], tx.id });
                    }
                }
            }

            seenAmounts.computeIfAbsent(tx.amount, k -> new ArrayList<>())
                    .add(new int[] { tx.id, txTime });
        }

        System.out.printf("findTwoSumWithTimeWindow(target=%.0f, window=%dmin) → %s%n",
                target, windowMinutes, formatPairs(result));
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 3. K-Sum: Find K transactions summing to target
    // ─────────────────────────────────────────────────────────

    /**
     * Find K transactions that sum to target using recursive approach with
     * memoization.
     *
     * @param k      number of transactions to pick
     * @param target target sum
     * @return list of transaction ID groups
     */
    public List<List<Integer>> findKSum(int k, double target) {
        double[] amounts = transactions.stream().mapToDouble(t -> t.amount).toArray();
        int[] ids = transactions.stream().mapToInt(t -> t.id).toArray();
        List<List<Integer>> result = new ArrayList<>();
        kSumHelper(amounts, ids, 0, k, target, new ArrayList<>(), result);

        System.out.printf("findKSum(k=%d, target=%.0f) → ", k, target);
        if (result.isEmpty()) {
            System.out.println("No combination found");
        } else {
            for (List<Integer> group : result) {
                System.out.printf("(ids: %s) ", group);
            }
            System.out.println();
        }
        return result;
    }

    private void kSumHelper(double[] amounts, int[] ids, int start, int k, double target,
            List<Integer> current, List<List<Integer>> results) {
        if (k == 0 && Math.abs(target) < 0.001) {
            results.add(new ArrayList<>(current));
            return;
        }
        if (k == 0 || start >= amounts.length)
            return;

        // Two-Sum optimization at k==2: use HashMap for O(n) instead of O(n^2)
        if (k == 2) {
            Map<Double, Integer> map = new HashMap<>();
            for (int i = start; i < amounts.length; i++) {
                double complement = target - amounts[i];
                if (map.containsKey(amounts[i])) {
                    current.add(map.get(amounts[i]));
                    current.add(ids[i]);
                    results.add(new ArrayList<>(current));
                    current.remove(current.size() - 1);
                    current.remove(current.size() - 1);
                }
                map.put(complement, ids[i]);
            }
            return;
        }

        for (int i = start; i < amounts.length; i++) {
            current.add(ids[i]);
            kSumHelper(amounts, ids, i + 1, k - 1, target - amounts[i], current, results);
            current.remove(current.size() - 1);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 4. Duplicate Detection: Same amount + merchant, different accounts
    // ─────────────────────────────────────────────────────────

    /**
     * Detect potential duplicate/fraudulent transactions.
     * Groups by (amount, merchant) key and flags multiple different accounts.
     *
     * @return map of (amount+merchant) → list of account IDs involved
     */
    public Map<String, List<String>> detectDuplicates() {
        // HashMap<amount_merchant_key, List<accountId>> for grouping
        Map<String, List<String>> groups = new HashMap<>();

        for (Transaction tx : transactions) {
            String key = String.format("%.0f|%s", tx.amount, tx.merchant);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(tx.accountId);
        }

        // Filter for groups with 2+ unique accounts (potential fraud)
        Map<String, List<String>> fraudGroups = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            Set<String> uniqueAccounts = new HashSet<>(e.getValue());
            if (uniqueAccounts.size() >= 2) {
                fraudGroups.put(e.getKey(), new ArrayList<>(uniqueAccounts));
            }
        }

        System.out.println("detectDuplicates() →");
        if (fraudGroups.isEmpty()) {
            System.out.println("  No suspicious duplicates found.");
        } else {
            fraudGroups.forEach((key, accounts) -> {
                String[] parts = key.split("\\|");
                System.out.printf("  {amount:%.0f, merchant:\"%s\", accounts:%s}%n",
                        Double.parseDouble(parts[0]), parts[1], accounts);
            });
        }
        return fraudGroups;
    }

    private String formatPairs(List<int[]> pairs) {
        if (pairs.isEmpty())
            return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int[] pair : pairs) {
            sb.append(String.format("(id:%d, id:%d), ", pair[0], pair[1]));
        }
        sb.setLength(sb.length() - 2);
        sb.append("]");
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("=== Problem 9: Two-Sum Variants for Financial Transactions ===\n");

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction(1, 500, "Store A", "acc1", "10:00"));
        transactions.add(new Transaction(2, 300, "Store B", "acc2", "10:15"));
        transactions.add(new Transaction(3, 200, "Store C", "acc3", "10:30"));
        transactions.add(new Transaction(4, 700, "Store D", "acc4", "12:00"));
        transactions.add(new Transaction(5, 100, "Store E", "acc5", "10:45"));
        transactions.add(new Transaction(6, 500, "Store A", "acc9", "10:05")); // duplicate suspicion
        transactions.add(new Transaction(7, 400, "Store F", "acc6", "11:00"));
        transactions.add(new Transaction(8, 600, "Store G", "acc7", "14:30"));

        System.out.println("Transactions:");
        transactions.forEach(t -> System.out.println("  " + t));

        Problem9_FinancialTransactionTwoSum analyzer = new Problem9_FinancialTransactionTwoSum(transactions);

        System.out.println("\n--- 1. Classic Two-Sum ---");
        analyzer.findTwoSum(500); // 300+200
        analyzer.findTwoSum(1000); // 300+700, 400+600

        System.out.println("\n--- 2. Two-Sum with Time Window (60 minutes) ---");
        analyzer.findTwoSumWithTimeWindow(500, 60); // only within 1 hour
        analyzer.findTwoSumWithTimeWindow(1000, 180); // within 3 hours

        System.out.println("\n--- 3. K-Sum (K=3, target=1000) ---");
        analyzer.findKSum(3, 1000); // 500+300+200

        System.out.println("\n--- 4. Duplicate / Fraud Detection ---");
        analyzer.detectDuplicates();

        System.out.println("\n--- Hash Table Properties ---");
        System.out.println("  Two-Sum      : HashMap<complement, txId>      — O(n) time, O(n) space");
        System.out.println("  Time-Window  : HashMap<amount, List<id,time>> — O(n) scan");
        System.out.println("  K-Sum        : Recursive + 2-Sum HashMap opt  — O(n^(k-1)) time");
        System.out.println("  Duplicates   : HashMap<amount|merchant, List> — O(n) grouping");
    }
}
