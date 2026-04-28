package net.joseph.vaultfilters.util;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Detects the format version of filter exports.
 *
 * Supports:
 * - v3: YAML with "format: vaultfilters.v3" header
 * - v2: JSON starting with "{"
 * - v1: Legacy format (detected but not yet imported; for future use)
 *
 * Used by import flows to route to the correct parser.
 */
@OnlyIn(Dist.CLIENT)
public class FilterVersionDetector {
    public enum FilterVersion {
        V3_YAML("v3 (YAML)"),
        V2_JSON("v2 (JSON)"),
        V1_LEGACY("v1 (Legacy)"),
        UNKNOWN("Unknown/Invalid");

        public final String label;

        FilterVersion(String label) {
            this.label = label;
        }
    }

    /**
     * Detect the version of a filter export string.
     *
     * @param exportString Raw export string (from clipboard, file, etc.)
     * @return FilterVersion enum indicating the detected format
     */
    public static FilterVersion detectVersion(String exportString) {
        if (exportString == null || exportString.isBlank()) {
            return FilterVersion.UNKNOWN;
        }

        String trimmed = exportString.trim();

        // v3: YAML with format header
        if (trimmed.startsWith("format:") && trimmed.contains("vaultfilters.v3")) {
            return FilterVersion.V3_YAML;
        }
        if (trimmed.startsWith("name:") && trimmed.contains("type:") && trimmed.contains("vaultfilters.v3")) {
            return FilterVersion.V3_YAML;
        }

        // v2: JSON format
        if (trimmed.startsWith("{") && (trimmed.contains("\"format\":") || trimmed.contains("\"type\":"))) {
            return FilterVersion.V2_JSON;
        }

        // v1 markers (legacy, for future use)
        // v1 typically had specific NBT encoding or different markers
        // Reserved for future implementation

        return FilterVersion.UNKNOWN;
    }

    /**
     * Check if a version is supported for import.
     *
     * @param version The detected version
     * @return True if the version can be imported
     */
    public static boolean isSupported(FilterVersion version) {
        return version == FilterVersion.V3_YAML || version == FilterVersion.V2_JSON;
    }

    /**
     * Get a user-friendly error message for unsupported versions.
     *
     * @param version The detected (unsupported) version
     * @return Error message
     */
    public static String getUnsupportedMessage(FilterVersion version) {
        switch (version) {
            case V1_LEGACY:
                return "v1 format is no longer supported. Please re-export using the mod's export feature.";
            case UNKNOWN:
                return "Unknown or invalid format. Ensure you're importing a valid filter export.";
            default:
                return "This format cannot be imported.";
        }
    }
}
