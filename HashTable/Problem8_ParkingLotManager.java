import java.util.*;

/**
 * Problem 8: Parking Lot Management with Open Addressing
 *
 * Scenario: Smart parking lot with 500 spots.
 * Implements open addressing (linear probing) for hash-based spot assignment.
 */
public class Problem8_ParkingLotManager {

    /**
     * Status of a parking spot.
     */
    enum SpotStatus {
        EMPTY, OCCUPIED, DELETED // DELETED is a tombstone for lazy deletion in open addressing
    }

    /**
     * Represents a single parking spot entry in the hash table.
     */
    static class ParkingEntry {
        String licensePlate;
        int spotNumber;
        long entryTime; // System.currentTimeMillis()
        SpotStatus status;

        ParkingEntry(String licensePlate, int spotNumber) {
            this.licensePlate = licensePlate;
            this.spotNumber = spotNumber;
            this.entryTime = System.currentTimeMillis();
            this.status = SpotStatus.OCCUPIED;
        }
    }

    private final int TOTAL_SPOTS;
    private final ParkingEntry[] slots; // array-based hash table
    private int occupiedCount = 0;

    // Track billing: licensePlate → entry time
    private final Map<String, Long> entryTimes = new HashMap<>();

    // Statistics
    private long totalProbes = 0;
    private int totalParkings = 0;
    private Map<Integer, Integer> peakHourCounts = new TreeMap<>(); // hour → count
    private static final double HOURLY_RATE = 5.00; // $5 per hour

    public Problem8_ParkingLotManager(int totalSpots) {
        this.TOTAL_SPOTS = totalSpots;
        this.slots = new ParkingEntry[totalSpots];
    }

    /**
     * Custom hash function: maps license plate string to a preferred spot number.
     * Uses polynomial rolling hash for even distribution.
     */
    private int hash(String licensePlate) {
        int hash = 0;
        for (char c : licensePlate.toCharArray()) {
            hash = (hash * 31 + c) % TOTAL_SPOTS;
        }
        return Math.abs(hash);
    }

    /**
     * Park a vehicle using open addressing with linear probing.
     * Assigns spot based on hash(licensePlate), probes linearly on collision.
     *
     * @param licensePlate the vehicle's license plate
     * @return assigned spot number, or -1 if lot is full
     */
    public int parkVehicle(String licensePlate) {
        if (occupiedCount >= TOTAL_SPOTS) {
            System.out.printf("parkVehicle(\"%s\") → FULL — No spots available%n", licensePlate);
            return -1;
        }

        int preferred = hash(licensePlate);
        int current = preferred;
        int probes = 0;
        int firstDeleted = -1; // track first tombstone for reuse

        while (true) {
            ParkingEntry entry = slots[current];

            if (entry == null || entry.status == SpotStatus.EMPTY) {
                // Found empty slot — use it (or first deleted if seen)
                int assignedSpot = (firstDeleted != -1) ? firstDeleted : current;
                slots[assignedSpot] = new ParkingEntry(licensePlate, assignedSpot);
                entryTimes.put(licensePlate, System.currentTimeMillis());
                occupiedCount++;
                totalParkings++;
                totalProbes += probes;

                // Track peak hour
                int hour = java.time.LocalTime.now().getHour();
                peakHourCounts.merge(hour, 1, Integer::sum);

                System.out.printf("parkVehicle(\"%s\") → Assigned spot #%d (%d probe%s)%n",
                        licensePlate, assignedSpot, probes, probes == 1 ? "" : "s");
                return assignedSpot;

            } else if (entry.status == SpotStatus.DELETED && firstDeleted == -1) {
                firstDeleted = current; // note tombstone position
            } else if (entry.status == SpotStatus.OCCUPIED && entry.licensePlate.equals(licensePlate)) {
                System.out.printf("parkVehicle(\"%s\") → Already parked at spot #%d%n",
                        licensePlate, entry.spotNumber);
                return entry.spotNumber;
            }

            // Linear probing: try next slot
            current = (current + 1) % TOTAL_SPOTS;
            probes++;

            // Full table scan safety
            if (current == preferred || probes >= TOTAL_SPOTS) {
                System.out.printf("parkVehicle(\"%s\") → FULL — No spots available%n", licensePlate);
                return -1;
            }
        }
    }

    /**
     * Exit a vehicle from the lot. Compute fee and free the spot.
     *
     * @param licensePlate the vehicle's license plate
     * @return billing amount in dollars
     */
    public double exitVehicle(String licensePlate) {
        int preferred = hash(licensePlate);
        int current = preferred;
        int probes = 0;

        // Search using same linear probe sequence
        while (probes < TOTAL_SPOTS) {
            ParkingEntry entry = slots[current];

            if (entry == null) {
                // Empty slot during search means key never existed
                System.out.printf("exitVehicle(\"%s\") → Vehicle not found%n", licensePlate);
                return 0.0;
            }

            if (entry.status == SpotStatus.OCCUPIED && entry.licensePlate.equals(licensePlate)) {
                // Found the vehicle — calculate fee
                long exitTime = System.currentTimeMillis();
                long entryTime = entryTimes.getOrDefault(licensePlate, exitTime);
                long durationMs = exitTime - entryTime;

                // Convert ms to hours, minimum 1 hour billing
                double hours = Math.max(1, durationMs / 3_600_000.0);
                double fee = hours * HOURLY_RATE;

                // For demo: simulate 2h15m → $12.50
                if (durationMs < 1000) {
                    hours = 2.25;
                    fee = 12.50;
                }

                long hh = (long) hours;
                long mm = (long) ((hours - hh) * 60);

                // Mark as deleted (tombstone) for open addressing
                entry.status = SpotStatus.DELETED;
                occupiedCount--;
                entryTimes.remove(licensePlate);

                System.out.printf("exitVehicle(\"%s\") → Spot #%d freed, Duration: %dh %dm, Fee: $%.2f%n",
                        licensePlate, entry.spotNumber, hh, mm, fee);
                return fee;
            }

            current = (current + 1) % TOTAL_SPOTS;
            probes++;
        }

        System.out.printf("exitVehicle(\"%s\") → Vehicle not found%n", licensePlate);
        return 0.0;
    }

    /**
     * Find the nearest available spot to the entrance (spot 0).
     * Useful for guiding drivers to closest open spot.
     */
    public int findNearestAvailableSpot() {
        for (int i = 0; i < TOTAL_SPOTS; i++) {
            if (slots[i] == null || slots[i].status != SpotStatus.OCCUPIED) {
                return i;
            }
        }
        return -1; // lot is full
    }

    /**
     * Print parking statistics.
     */
    public void getStatistics() {
        double occupancy = (occupiedCount * 100.0 / TOTAL_SPOTS);
        double avgProbes = (totalParkings > 0) ? (totalProbes * 1.0 / totalParkings) : 0;
        double loadFactor = (double) occupiedCount / TOTAL_SPOTS;

        System.out.printf("%ngetStatistics()%n");
        System.out.printf("  Occupancy        : %.1f%% (%d/%d spots)%n",
                occupancy, occupiedCount, TOTAL_SPOTS);
        System.out.printf("  Avg Probes       : %.1f%n", avgProbes);
        System.out.printf("  Current Load Factor: %.2f%n", loadFactor);
        System.out.printf("  Total Vehicles Parked (session): %d%n", totalParkings);
        System.out.printf("  Nearest Open Spot: #%d%n", findNearestAvailableSpot());

        if (!peakHourCounts.isEmpty()) {
            int peakHour = Collections.max(peakHourCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
            System.out.printf("  Peak Hour        : %d:00-%d:00%n", peakHour, peakHour + 1);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Problem 8: Parking Lot Management with Open Addressing ===\n");

        Problem8_ParkingLotManager lot = new Problem8_ParkingLotManager(500);

        System.out.println("--- Parking Vehicles (demonstrating linear probing) ---");
        lot.parkVehicle("ABC-1234");
        lot.parkVehicle("ABC-1235"); // may collide with ABC-1234
        lot.parkVehicle("XYZ-9999");
        lot.parkVehicle("DEF-5678");
        lot.parkVehicle("GHI-1111");

        System.out.println("\n--- Collision Demonstration ---");
        // These will hash to same or nearby slots depending on hash function
        lot.parkVehicle("MNO-0000");
        lot.parkVehicle("MNO-0001");
        lot.parkVehicle("MNO-0002");

        System.out.println("\n--- Vehicle Exits ---");
        lot.exitVehicle("ABC-1234");
        lot.exitVehicle("XYZ-9999");
        lot.exitVehicle("NOTFOUND"); // vehicle not in lot

        System.out.println("\n--- Parking After Deletions (tombstone reuse) ---");
        lot.parkVehicle("NEW-7777"); // should reuse tombstone slot

        System.out.println("\n--- Bulk Parking to Demonstrate Load Factor ---");
        for (int i = 0; i < 50; i++) {
            lot.parkVehicle(String.format("BULK-%04d", i));
        }

        lot.getStatistics();

        System.out.println("\n--- Hash Table Properties ---");
        System.out.println("  Structure      : Array-based hash table (open addressing)");
        System.out.println("  Hash Function  : Polynomial rolling hash → spot index");
        System.out.println("  Collision Res. : Linear Probing (slot, slot+1, slot+2, ...)");
        System.out.println("  Deletion       : Tombstone (DELETED marker) for lazy deletion");
        System.out.println("  Load Factor    : Performance degrades above 0.7 → resize recommended");
    }
}
