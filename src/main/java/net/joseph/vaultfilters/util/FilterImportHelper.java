package net.joseph.vaultfilters.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Common import validation and helper utilities.
 *
 * Provides:
 * - Clipboard size/format validation
 * - Centralized error notifications
 * - Caching for frequently reconstructed attributes (Phase 5 optimization)
 *
 * Used by both v2 and v3 import flows to reduce code duplication.
 */
@OnlyIn(Dist.CLIENT)
public class FilterImportHelper {

    // Cache for recently reconstructed attributes (key -> ItemAttribute NBT string)
    private static final Map<String, Object> ATTRIBUTE_CACHE = new HashMap<>();
    private static final int CACHE_MAX_SIZE = 100;

    /**
     * Validate clipboard contents before import.
     *
     * @param clipboard Raw clipboard string
     * @return True if valid, false otherwise. Notifies user if invalid.
     */
    public static boolean validateClipboard(String clipboard, String filterType) {
        if (clipboard == null || clipboard.isBlank()) {
            String typeLabel = "attribute".equals(filterType) ? "vaultfilters.gui.attribute_filter.import.empty" : "vaultfilters.gui.list_filter.import.empty";
            FilterUiUtils.notifyUser(new TranslatableComponent(typeLabel).withStyle(ChatFormatting.RED));
            return false;
        }

        if (clipboard.length() > FilterUiUtils.MAX_IMPORT_CHARS) {
            String typeLabel = "attribute".equals(filterType) ? "vaultfilters.gui.attribute_filter.import.too_large" : "vaultfilters.gui.list_filter.import.too_large";
            FilterUiUtils.notifyUser(new TranslatableComponent(typeLabel, FilterUiUtils.MAX_IMPORT_CHARS).withStyle(ChatFormatting.RED));
            return false;
        }

        return true;
    }

    /**
     * Notify user of invalid format.
     *
     * @param filterType "attribute" or "list"
     */
    public static void notifyInvalidFormat(String filterType) {
        String typeLabel = "attribute".equals(filterType) ? "vaultfilters.gui.attribute_filter.import.invalid" : "vaultfilters.gui.list_filter.import.invalid";
        FilterUiUtils.notifyUser(new TranslatableComponent(typeLabel).withStyle(ChatFormatting.RED));
    }

    /**
     * Notify user of format detection failure.
     *
     * @param version The detected (unsupported) format version
     */
    public static void notifyUnsupportedFormat(FilterVersionDetector.FilterVersion version) {
        String errorMsg = FilterVersionDetector.getUnsupportedMessage(version);
        FilterUiUtils.notifyUser(new TranslatableComponent(errorMsg).withStyle(ChatFormatting.RED));
    }

    /**
     * Notify user of import success.
     *
     * @param filterType "attribute" or "list"
     * @param count Number of items imported
     * @param merge Whether merge mode was used
     */
    public static void notifyImportSuccess(String filterType, int count, boolean merge) {
        String mode = merge ? "imported.merge" : "imported.replace";
        String typePrefix = "attribute".equals(filterType) ? "vaultfilters.gui.attribute_filter." : "vaultfilters.gui.list_filter.";
        FilterUiUtils.notifyUser(new TranslatableComponent(typePrefix + mode, count).withStyle(ChatFormatting.GREEN));
    }

    /**
     * Notify user of import failure (no items imported).
     *
     * @param filterType "attribute" or "list"
     */
    public static void notifyImportNone(String filterType) {
        String typeLabel = "attribute".equals(filterType) ? "vaultfilters.gui.attribute_filter.import.none" : "vaultfilters.gui.list_filter.import.none";
        FilterUiUtils.notifyUser(new TranslatableComponent(typeLabel).withStyle(ChatFormatting.RED));
    }

    /**
     * Cache an attribute for future lookups.
     * Uses simple LRU-like eviction when cache exceeds max size.
     *
     * @param key Unique cache key (e.g., NBT string hash)
     * @param value The cached value
     */
    public static void cacheAttribute(String key, Object value) {
        if (ATTRIBUTE_CACHE.size() >= CACHE_MAX_SIZE) {
            // Simple eviction: remove first (oldest) entry
            if (!ATTRIBUTE_CACHE.isEmpty()) {
                ATTRIBUTE_CACHE.remove(ATTRIBUTE_CACHE.keySet().iterator().next());
            }
        }
        ATTRIBUTE_CACHE.put(key, value);
    }

    /**
     * Retrieve a cached attribute.
     *
     * @param key Cache key
     * @return Cached value, or null if not found
     */
    public static Object getCachedAttribute(String key) {
        return ATTRIBUTE_CACHE.get(key);
    }

    /**
     * Clear the attribute cache.
     * Call this when filter menu closes or on major state changes.
     */
    public static void clearAttributeCache() {
        ATTRIBUTE_CACHE.clear();
    }

    /**
     * Check if shift key is held (merge mode indicator).
     *
     * @return True if shift is down
     */
    public static boolean isMergeMode() {
        return FilterUiUtils.isShiftDownSafe();
    }
}
