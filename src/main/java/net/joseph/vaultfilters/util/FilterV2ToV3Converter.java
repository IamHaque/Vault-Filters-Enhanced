package net.joseph.vaultfilters.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

/**
 * Converts v2 JSON filter exports to v3 YAML format.
 *
 * This utility helps users migrate old v2 exports to the new v3 format.
 * It parses v2 JSON and builds a FilterV3Data structure that can be
 * serialized to v3 YAML using FilterExportUtilsV3.
 *
 * Usage:
 * - Parse v2 JSON string
 * - Get FilterV3Data
 * - Serialize to YAML using FilterExportUtilsV3.toYamlString()
 */
@OnlyIn(Dist.CLIENT)
public class FilterV2ToV3Converter {

    /**
     * Convert a v2 JSON export to v3 FilterV3Data.
     *
     * @param jsonString Raw JSON v2 export string
     * @return FilterV3Data representing the export in v3 format, or null if conversion fails
     * @throws ImportException If JSON is malformed or missing required fields
     */
    public static FilterImportUtilsV3.FilterV3Data convertV2ToV3(String jsonString) throws FilterImportUtilsV3.ImportException {
        if (jsonString == null || jsonString.isBlank()) {
            throw new FilterImportUtilsV3.ImportException("Empty JSON string");
        }

        JsonElement element;
        try {
            element = JsonParser.parseString(jsonString);
        } catch (Exception e) {
            throw new FilterImportUtilsV3.ImportException("Invalid JSON: " + e.getMessage());
        }

        if (!element.isJsonObject()) {
            throw new FilterImportUtilsV3.ImportException("JSON root must be an object");
        }

        JsonObject root = element.getAsJsonObject();

        // Determine filter type from v2 structure
        String filterType = null;
        if (root.has("type")) {
            String typeStr = root.get("type").getAsString();
            if ("attribute".equalsIgnoreCase(typeStr)) {
                filterType = FilterExportUtilsV3.TYPE_ATTRIBUTE;
            } else if ("list".equalsIgnoreCase(typeStr)) {
                filterType = FilterExportUtilsV3.TYPE_LIST;
            }
        }

        if (filterType == null) {
            throw new FilterImportUtilsV3.ImportException("Unknown or missing type in v2 export");
        }

        // Create v3 data structure
        FilterImportUtilsV3.FilterV3Data data = new FilterImportUtilsV3.FilterV3Data();
        data.type = filterType;

        // Copy filter name if present
        if (root.has("name")) {
            data.filterName = root.get("name").getAsString();
        }

        // Extract modes from v2
        data.whitelist = !root.getAsJsonObject("options").get("isBlacklist").getAsBoolean();
        data.matchAll = root.getAsJsonObject("options").getAsJsonObject("listOptions").get("matchAll").getAsBoolean();
        data.respectNBT = root.getAsJsonObject("options").getAsJsonObject("listOptions").get("respectNBT").getAsBoolean();

        // Convert items
        if (root.has("items")) {
            JsonArray itemsArray = root.getAsJsonArray("items");
            List<FilterImportUtilsV3.FilterV3Item> convertedItems = new ArrayList<>();
            for (JsonElement itemElem : itemsArray) {
                FilterImportUtilsV3.FilterV3Item converted = convertV2Item(itemElem.getAsJsonObject());
                if (converted != null) {
                    convertedItems.add(converted);
                }
            }
            data.items = convertedItems;
        }

        // Convert attributes (for attribute filter)
        if (FilterExportUtilsV3.TYPE_ATTRIBUTE.equals(filterType) && root.has("attributes")) {
            JsonArray attrArray = root.getAsJsonArray("attributes");
            List<FilterImportUtilsV3.AttributeV3> convertedAttrs = new ArrayList<>();
            for (JsonElement attrElem : attrArray) {
                FilterImportUtilsV3.AttributeV3 converted = convertV2Attribute(attrElem.getAsJsonObject());
                if (converted != null) {
                    convertedAttrs.add(converted);
                }
            }
            data.attributes = convertedAttrs;
        }

        return data;
    }

    /**
     * Convert a v2 item JSON object to v3 FilterV3Item.
     *
     * @param itemObj v2 item JSON object
     * @return FilterV3Item, or null if conversion fails
     */
    private static FilterImportUtilsV3.FilterV3Item convertV2Item(JsonObject itemObj) {
        try {
            FilterImportUtilsV3.FilterV3Item item = new FilterImportUtilsV3.FilterV3Item();

            // Get type
            if (itemObj.has("type")) {
                item.type = itemObj.get("type").getAsString();
            }

            // Get custom name if present
            if (itemObj.has("name")) {
                item.name = itemObj.get("name").getAsString();
            }

            // Get modes if present (for nested list filters)
            if (itemObj.has("whitelist")) {
                item.whitelist = itemObj.get("whitelist").getAsBoolean();
            }
            if (itemObj.has("matchAll")) {
                item.matchAll = itemObj.get("matchAll").getAsBoolean();
            }
            if (itemObj.has("respectNBT")) {
                item.respectNBT = itemObj.get("respectNBT").getAsBoolean();
            }

            // Get attributes (for attribute filter items)
            if (itemObj.has("attributes")) {
                JsonArray attrArray = itemObj.getAsJsonArray("attributes");
                List<FilterImportUtilsV3.AttributeV3> attrs = new ArrayList<>();
                for (JsonElement elem : attrArray) {
                    FilterImportUtilsV3.AttributeV3 attr = convertV2Attribute(elem.getAsJsonObject());
                    if (attr != null) {
                        attrs.add(attr);
                    }
                }
                item.attributes = attrs;
            }

            // Get nested items (for nested list filters)
            if (itemObj.has("items")) {
                JsonArray nestedArray = itemObj.getAsJsonArray("items");
                List<FilterImportUtilsV3.FilterV3Item> nested = new ArrayList<>();
                for (JsonElement elem : nestedArray) {
                    FilterImportUtilsV3.FilterV3Item nestedItem = convertV2Item(elem.getAsJsonObject());
                    if (nestedItem != null) {
                        nested.add(nestedItem);
                    }
                }
                item.items = nested;
            }

            // Get item ID and count (for regular items)
            if (itemObj.has("itemId")) {
                item.itemId = itemObj.get("itemId").getAsString();
            }
            if (itemObj.has("count")) {
                item.count = itemObj.get("count").getAsInt();
            } else {
                item.count = 1;
            }

            return item;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert a v2 attribute JSON object to v3 AttributeV3.
     *
     * @param attrObj v2 attribute JSON object
     * @return AttributeV3, or null if conversion fails
     */
    private static FilterImportUtilsV3.AttributeV3 convertV2Attribute(JsonObject attrObj) {
        try {
            FilterImportUtilsV3.AttributeV3 attr = new FilterImportUtilsV3.AttributeV3();

            // Get attribute key
            if (attrObj.has("key")) {
                attr.key = attrObj.get("key").getAsString();
            }

            // Get inverted flag
            if (attrObj.has("inverted")) {
                attr.inverted = attrObj.get("inverted").getAsBoolean();
            }

            // Get parameters
            if (attrObj.has("params")) {
                JsonElement paramsElem = attrObj.get("params");
                if (paramsElem.isJsonObject()) {
                    attr.params = convertJsonToMap(paramsElem.getAsJsonObject());
                }
            }

            return attr;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Recursively convert a JsonObject to a Map for v3 storage.
     *
     * @param obj JsonObject to convert
     * @return Map representation
     */
    private static Map<String, Object> convertJsonToMap(JsonObject obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : obj.keySet()) {
            JsonElement elem = obj.get(key);
            map.put(key, convertJsonElement(elem));
        }
        return map;
    }

    /**
     * Convert a single JsonElement to an Object.
     *
     * @param elem JsonElement to convert
     * @return Object representation
     */
    private static Object convertJsonElement(JsonElement elem) {
        if (elem.isJsonPrimitive()) {
            if (elem.getAsJsonPrimitive().isBoolean()) {
                return elem.getAsBoolean();
            } else if (elem.getAsJsonPrimitive().isNumber()) {
                // Try to preserve int vs float
                try {
                    return elem.getAsInt();
                } catch (NumberFormatException e) {
                    return elem.getAsDouble();
                }
            } else {
                return elem.getAsString();
            }
        } else if (elem.isJsonObject()) {
            return convertJsonToMap(elem.getAsJsonObject());
        } else if (elem.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : elem.getAsJsonArray()) {
                list.add(convertJsonElement(item));
            }
            return list;
        }
        return null;
    }
}
