import java.util.*;

/**
 * Problem 4: Plagiarism Detection System
 *
 * Scenario: University plagiarism checker comparing student submissions against
 * a database of 100,000 previous essays using n-gram hashing.
 */
public class Problem4_PlagiarismDetector {

    /**
     * Represents a document in the database.
     */
    static class Document {
        String docId;
        String content;
        Set<String> ngrams;

        Document(String docId, String content) {
            this.docId = docId;
            this.content = content;
            this.ngrams = new HashSet<>();
        }
    }

    // n-gram → Set of document IDs that contain it
    private final Map<String, Set<String>> ngramIndex = new HashMap<>();

    // docId → Document
    private final Map<String, Document> documentDatabase = new HashMap<>();

    private final int N; // n-gram size (e.g., 5 words)

    public Problem4_PlagiarismDetector(int n) {
        this.N = n;
    }

    /**
     * Extract all n-grams from a given text.
     * An n-gram is a sequence of N consecutive words.
     */
    private Set<String> extractNgrams(String text) {
        Set<String> ngrams = new HashSet<>();
        String[] words = text.toLowerCase().replaceAll("[^a-z0-9 ]", "").split("\\s+");

        for (int i = 0; i <= words.length - N; i++) {
            StringBuilder ngram = new StringBuilder();
            for (int j = i; j < i + N; j++) {
                if (j > i)
                    ngram.append(" ");
                ngram.append(words[j]);
            }
            ngrams.add(ngram.toString());
        }
        return ngrams;
    }

    /**
     * Add a document to the database and index all its n-grams.
     */
    public void addDocument(String docId, String content) {
        Document doc = new Document(docId, content);
        doc.ngrams = extractNgrams(content);
        documentDatabase.put(docId, doc);

        // Index each n-gram → set of docIds
        for (String ngram : doc.ngrams) {
            ngramIndex.computeIfAbsent(ngram, k -> new HashSet<>()).add(docId);
        }
        System.out.printf("Indexed document \"%s\" with %d unique %d-grams.%n",
                docId, doc.ngrams.size(), N);
    }

    /**
     * Analyze a new document for plagiarism against all stored documents.
     * Returns a similarity report sorted by similarity percentage descending.
     */
    public void analyzeDocument(String queryDocId, String content) {
        System.out.printf("%nanalyzeDocument(\"%s\")%n", queryDocId);

        Set<String> queryNgrams = extractNgrams(content);
        System.out.printf("→ Extracted %d n-grams%n", queryNgrams.size());

        if (queryNgrams.isEmpty()) {
            System.out.println("→ Document too short for analysis.");
            return;
        }

        // Count matching n-grams per document using hash table lookup
        Map<String, Integer> matchCounts = new HashMap<>();
        for (String ngram : queryNgrams) {
            Set<String> matchingDocs = ngramIndex.get(ngram);
            if (matchingDocs != null) {
                for (String docId : matchingDocs) {
                    if (!docId.equals(queryDocId)) {
                        matchCounts.merge(docId, 1, Integer::sum);
                    }
                }
            }
        }

        if (matchCounts.isEmpty()) {
            System.out.println("→ No matches found. Document appears original.");
            return;
        }

        // Sort by match count descending
        List<Map.Entry<String, Integer>> results = new ArrayList<>(matchCounts.entrySet());
        results.sort((a, b) -> b.getValue() - a.getValue());

        System.out.println("--- Similarity Report ---");
        for (Map.Entry<String, Integer> entry : results) {
            String docId = entry.getKey();
            int matchCount = entry.getValue();
            double similarity = (matchCount * 100.0) / queryNgrams.size();

            String verdict;
            if (similarity >= 50.0) {
                verdict = "PLAGIARISM DETECTED";
            } else if (similarity >= 20.0) {
                verdict = "suspicious";
            } else {
                verdict = "acceptable";
            }

            System.out.printf("→ Found %d matching n-grams with \"%s\"%n", matchCount, docId);
            System.out.printf("→ Similarity: %.1f%% (%s)%n", similarity, verdict);
        }
    }

    /**
     * Find documents most similar to the query using cosine-like Jaccard
     * similarity.
     */
    public double jaccardSimilarity(String docId1, String docId2) {
        Document d1 = documentDatabase.get(docId1);
        Document d2 = documentDatabase.get(docId2);
        if (d1 == null || d2 == null)
            return 0.0;

        Set<String> intersection = new HashSet<>(d1.ngrams);
        intersection.retainAll(d2.ngrams);

        Set<String> union = new HashSet<>(d1.ngrams);
        union.addAll(d2.ngrams);

        return union.isEmpty() ? 0.0 : (intersection.size() * 100.0 / union.size());
    }

    /**
     * Get the total number of unique n-grams across all documents.
     */
    public void printIndexStats() {
        System.out.printf("%n--- Index Statistics ---%n");
        System.out.printf("  Unique n-grams indexed : %,d%n", ngramIndex.size());
        System.out.printf("  Documents in database  : %,d%n", documentDatabase.size());
        System.out.printf("  N-gram size            : %d words%n", N);
    }

    public static void main(String[] args) {
        System.out.println("=== Problem 4: Plagiarism Detection System ===\n");

        Problem4_PlagiarismDetector detector = new Problem4_PlagiarismDetector(5);

        // Add original documents to database
        String essay001 = "The quick brown fox jumps over the lazy dog. " +
                "Natural language processing is the study of linguistics and computer science. " +
                "Machine learning algorithms can detect patterns in text data efficiently. " +
                "Hash tables provide constant time lookup for key value pairs in memory. " +
                "Data structures are fundamental to efficient software engineering designs.";

        String essay089 = "Java is the most popular programming language for enterprise applications. " +
                "Object oriented programming uses classes and objects as core abstractions. " +
                "The quick brown fox jumps over the lazy dog in many typing tests. " +
                "Sorting algorithms like quicksort and mergesort organize data efficiently. " +
                "Database management systems store and retrieve structured data effectively.";

        String essay092 = "Hash tables provide constant time lookup for key value pairs in memory. " +
                "Data structures are fundamental to efficient software engineering designs. " +
                "Natural language processing is the study of linguistics and computer science. " +
                "Machine learning algorithms can detect patterns in text data efficiently. " +
                "The quick brown fox jumps over the lazy dog and nothing happens after.";

        System.out.println("--- Indexing Documents ---");
        detector.addDocument("essay_001", essay001);
        detector.addDocument("essay_089", essay089);
        detector.addDocument("essay_092", essay092);

        detector.printIndexStats();

        // Test document — similar to essay_092 (plagiarism) and slightly similar to
        // essay_089
        String newEssay = "Hash tables provide constant time lookup for key value pairs in memory. " +
                "Data structures are fundamental to efficient software engineering designs. " +
                "Natural language processing is the study of linguistics and computer science. " +
                "Machine learning algorithms can detect patterns in text data efficiently. " +
                "The quick brown fox jumps over the lazy dog and plays in the garden near.";

        System.out.println("\n--- Analyzing New Submission ---");
        detector.analyzeDocument("essay_123", newEssay);

        System.out.println("\n--- Jaccard Similarity Between Stored Documents ---");
        double sim = detector.jaccardSimilarity("essay_001", "essay_092");
        System.out.printf("  essay_001 vs essay_092 Jaccard Similarity: %.1f%%%n", sim);
        sim = detector.jaccardSimilarity("essay_001", "essay_089");
        System.out.printf("  essay_001 vs essay_089 Jaccard Similarity: %.1f%%%n", sim);

        System.out.println("\n--- Hash Table Properties ---");
        System.out.println("  Primary Structure : HashMap<n-gram, Set<docId>> for index");
        System.out.println("  Lookup Complexity : O(1) per n-gram lookup during analysis");
        System.out.println("  String Hashing    : Java's built-in String.hashCode() used");
        System.out.println("  Time Complexity   : O(n) to analyze document of n n-grams");
    }
}
