package net.joseph.vaultfilters.util;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Optimizations for recursive filter serialization and import.
 *
 * Provides:
 * - Recursion depth tracking and limits
 * - Export size tracking and warnings
 * - Lazy evaluation hints for deeply nested structures
 *
 * Phase 5 optimization: Handles edge cases with deeply nested filters.
 */
@OnlyIn(Dist.CLIENT)
public class SerializationOptimizer {

    // Recursion depth limits
    public static final int MAX_NESTING_DEPTH = 32;  // Prevents stack overflow
    public static final int WARN_NESTING_DEPTH = 16; // Warn user at this depth
    public static final int MAX_EXPORT_BYTES = 1_000_000; // 1 MB soft limit

    // Tracking state for current serialization
    private static int currentDepth = 0;
    private static int currentExportSize = 0;

    /**
     * Push a recursion level.
     *
     * @return True if depth is within limits, false if max depth exceeded
     */
    public static boolean pushDepth() {
        currentDepth++;
        return currentDepth <= MAX_NESTING_DEPTH;
    }

    /**
     * Pop a recursion level.
     */
    public static void popDepth() {
        if (currentDepth > 0) {
            currentDepth--;
        }
    }

    /**
     * Get current recursion depth.
     *
     * @return Current depth (0 = top level)
     */
    public static int getCurrentDepth() {
        return currentDepth;
    }

    /**
     * Check if current depth is at warning threshold.
     *
     * @return True if at or near max depth
     */
    public static boolean isNearMaxDepth() {
        return currentDepth >= WARN_NESTING_DEPTH;
    }

    /**
     * Add to export size tracker.
     *
     * @param bytes Number of bytes to add
     */
    public static void trackExportSize(int bytes) {
        currentExportSize += bytes;
    }

    /**
     * Check if export size is exceeding soft limit.
     *
     * @return True if approaching limit
     */
    public static boolean isExportTooLarge() {
        return currentExportSize > MAX_EXPORT_BYTES;
    }

    /**
     * Get current export size in bytes.
     *
     * @return Size estimate
     */
    public static int getCurrentExportSize() {
        return currentExportSize;
    }

    /**
     * Reset serialization state (call at start of new export/import).
     */
    public static void reset() {
        currentDepth = 0;
        currentExportSize = 0;
    }

    /**
     * Get a user-friendly size string.
     *
     * @param bytes Size in bytes
     * @return Formatted string (e.g., "1.2 MB", "512 KB")
     */
    public static String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
