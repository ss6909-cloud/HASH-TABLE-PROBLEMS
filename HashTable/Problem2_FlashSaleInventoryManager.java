import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Problem 2: E-commerce Flash Sale Inventory Manager
 *
 * Scenario: 50,000 customers simultaneously purchase limited stock items.
 * Uses ConcurrentHashMap for thread safety, LinkedHashMap for FIFO waiting
 * list.
 */
public class Problem2_FlashSaleInventoryManager {

    // productId -> stock count (thread-safe)
    private final ConcurrentHashMap<String, AtomicInteger> inventory = new ConcurrentHashMap<>();

    // productId -> FIFO waiting list of userIds
    private final ConcurrentHashMap<String, Queue<Integer>> waitingLists = new ConcurrentHashMap<>();

    // productId -> total purchase count
    private final ConcurrentHashMap<String, AtomicInteger> purchaseCount = new ConcurrentHashMap<>();

    /**
     * Add a product with specified stock count.
     */
    public void addProduct(String productId, int stock) {
        inventory.put(productId, new AtomicInteger(stock));
        waitingLists.put(productId, new ConcurrentLinkedQueue<>());
        purchaseCount.put(productId, new AtomicInteger(0));
        System.out.printf("Product \"%s\" added with %d units of stock.%n", productId, stock);
    }

    /**
     * Check real-time stock for a product in O(1) time.
     */
    public int checkStock(String productId) {
        AtomicInteger stock = inventory.get(productId);
        if (stock == null) {
            System.out.printf("checkStock(\"%s\") → Product not found%n", productId);
            return -1;
        }
        int units = stock.get();
        System.out.printf("checkStock(\"%s\") → %d units available%n", productId, units);
        return units;
    }

    /**
     * Process a purchase request atomically.
     * If stock is available, decrement and return success.
     * Otherwise, add userId to waiting list.
     *
     * @param productId the product to purchase
     * @param userId    the requesting user's ID
     * @return true if purchase successful, false if added to waiting list
     */
    public boolean purchaseItem(String productId, int userId) {
        AtomicInteger stock = inventory.get(productId);
        if (stock == null) {
            System.out.printf("purchaseItem(\"%s\", userId=%d) → Product not found%n", productId, userId);
            return false;
        }

        // Atomic compare-and-decrement to prevent race conditions
        int currentStock;
        do {
            currentStock = stock.get();
            if (currentStock <= 0) {
                // Out of stock — add to waiting list
                Queue<Integer> waitList = waitingLists.get(productId);
                waitList.offer(userId);
                int position = getWaitingListSize(productId);
                System.out.printf("purchaseItem(\"%s\", userId=%d) → Added to waiting list, position #%d%n",
                        productId, userId, position);
                return false;
            }
        } while (!stock.compareAndSet(currentStock, currentStock - 1));

        // Successfully decremented stock
        int sold = purchaseCount.get(productId).incrementAndGet();
        System.out.printf("purchaseItem(\"%s\", userId=%d) → Success, %d units remaining%n",
                productId, userId, currentStock - 1);
        return true;
    }

    /**
     * Get the size of the waiting list for a product.
     */
    public int getWaitingListSize(String productId) {
        Queue<Integer> waitList = waitingLists.get(productId);
        return (waitList != null) ? waitList.size() : 0;
    }

    /**
     * Notify the next user in the waiting list when stock is restocked.
     */
    public void restockProduct(String productId, int additionalStock) {
        AtomicInteger stock = inventory.get(productId);
        if (stock == null)
            return;

        stock.addAndGet(additionalStock);
        System.out.printf("\nRestocked \"%s\" with %d units. New stock: %d%n",
                productId, additionalStock, stock.get());

        // Auto-process waiting list
        Queue<Integer> waitList = waitingLists.get(productId);
        int processed = 0;
        while (processed < additionalStock && !waitList.isEmpty()) {
            int nextUser = waitList.poll();
            purchaseItem(productId, nextUser);
            processed++;
        }
    }

    /**
     * Print full inventory status.
     */
    public void printInventoryStatus() {
        System.out.println("\n--- Inventory Status ---");
        for (String productId : inventory.keySet()) {
            System.out.printf("  %s: %d in stock | %d sold | %d on waiting list%n",
                    productId,
                    inventory.get(productId).get(),
                    purchaseCount.get(productId).get(),
                    getWaitingListSize(productId));
        }
    }

    /**
     * Simulate concurrent flash sale with multiple threads.
     */
    public void simulateConcurrentFlashSale(String productId, int numBuyers) throws InterruptedException {
        System.out.printf("%nSimulating %,d concurrent buyers for \"%s\"%n", numBuyers, productId);
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(numBuyers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger waitlistCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < numBuyers; i++) {
            final int userId = 10000 + i;
            executor.submit(() -> {
                boolean purchased = purchaseItem(productId, userId);
                if (purchased)
                    successCount.incrementAndGet();
                else
                    waitlistCount.incrementAndGet();
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.printf("Simulation complete in %dms: %d purchased, %d on waitlist%n",
                elapsed, successCount.get(), waitlistCount.get());
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Problem 2: E-commerce Flash Sale Inventory Manager ===\n");

        Problem2_FlashSaleInventoryManager manager = new Problem2_FlashSaleInventoryManager();

        // Setup products
        manager.addProduct("IPHONE15_256GB", 100);
        manager.addProduct("PS5_CONSOLE", 50);

        System.out.println("\n--- Stock Availability Checks ---");
        manager.checkStock("IPHONE15_256GB");
        manager.checkStock("PS5_CONSOLE");

        System.out.println("\n--- Sequential Purchase Simulation ---");
        manager.purchaseItem("IPHONE15_256GB", 12345);
        manager.purchaseItem("IPHONE15_256GB", 67890);

        // Drain stock manually for demo
        System.out.println("\n(Draining remaining 98 units of IPHONE15_256GB...)");
        for (int i = 2; i < 100; i++) {
            manager.purchaseItem("IPHONE15_256GB", 20000 + i);
        }

        System.out.println("\n--- Attempting to buy when out of stock ---");
        manager.purchaseItem("IPHONE15_256GB", 99999);
        manager.purchaseItem("IPHONE15_256GB", 88888);
        manager.purchaseItem("IPHONE15_256GB", 77777);

        manager.printInventoryStatus();

        System.out.println("\n--- Restocking ---");
        manager.restockProduct("IPHONE15_256GB", 3);
        manager.printInventoryStatus();

        System.out.println("\n--- Hash Table Properties ---");
        System.out.println("  Lookup Time Complexity : O(1) average via ConcurrentHashMap");
        System.out.println("  Thread Safety          : AtomicInteger + compareAndSet for lock-free ops");
        System.out.println("  Waiting List           : ConcurrentLinkedQueue (FIFO)");
    }
}
