package net.joseph.vaultfilters.util;

import com.simibubi.create.content.logistics.filter.ItemAttribute;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Utilities for importing filters from YAML format (v3).
 *
 * Handles:
 * - Parsing YAML into filter data structures
 * - Reconstructing ItemAttributes from YAML params
 * - Validating filter data
 * - Applying modes to menu/filters
 *
 * Used by both MixinAttributeFilterScreen and MixinFilterScreen.
 */
@OnlyIn(Dist.CLIENT)
public class FilterImportUtilsV3 {

    /**
     * Parse YAML text into a FilterV3Data structure.
     * Validates format and required fields.
     *
     * @param yamlText YAML text to parse
     * @return Parsed filter data
     * @throws ImportException if parsing or validation fails
     */
    public static FilterV3Data parseYaml(String yamlText) throws ImportException {
        if (yamlText == null || yamlText.isBlank()) {
            throw new ImportException("Empty YAML input");
        }

        Map<String, Object> root;
        try {
            root = YamlParser.parseYaml(yamlText);
        } catch (YamlParser.YamlParseException e) {
            throw new ImportException("YAML syntax error: " + e.getMessage(), e);
        }

        // Validate format and version
        String format = getStringField(root, "format", null);
        if (!FilterExportUtilsV3.FORMAT_V3.equals(format)) {
            throw new ImportException("Unsupported format. Expected '" +
                                    FilterExportUtilsV3.FORMAT_V3 + "', got '" + format + "'");
        }

        FilterV3Data data = new FilterV3Data();
        data.filterName = getStringField(root, "name", null);
        data.type = getStringField(root, "type", "list");

        if (!data.type.equals("attribute") && !data.type.equals("list")) {
            throw new ImportException("Invalid filter type: " + data.type);
        }

        // Parse modes
        Map<String, Object> modesMap = getMapField(root, "modes", new HashMap<>());
        data.whitelist = getBooleanField(modesMap, "whitelist", true);
        data.matchAll = getBooleanField(modesMap, "matchAll", false);
        data.respectNBT = getBooleanField(modesMap, "respectNBT", false);

        // Parse attributes or items based on type
        if ("attribute".equals(data.type)) {
            List<?> attrList = getListField(root, "attributes", new ArrayList<>());
            data.attributes = parseAttributes(attrList);
        } else {
            List<?> itemList = getListField(root, "items", new ArrayList<>());
            data.items = parseItems(itemList);
        }

        return data;
    }

    /**
     * Parse a list of attribute maps into AttributeV3 objects.
     * Skips invalid entries and tracks them for reporting.
     *
     * @param attributeList List of attribute maps from YAML
     * @return List of parsed attributes
     */
    public static List<AttributeV3> parseAttributes(List<?> attributeList) {
        List<AttributeV3> result = new ArrayList<>();

        for (Object item : attributeList) {
            if (!(item instanceof Map)) {
                continue;
            }

            Map<String, Object> attrMap = (Map<String, Object>) item;
            try {
                AttributeV3 attr = new AttributeV3();
                attr.key = getStringField(attrMap, "key", null);

                if (attr.key == null) {
                    continue;
                }

                attr.params = getMapField(attrMap, "params", new HashMap<>());
                attr.inverted = getBooleanField(attrMap, "inverted", false);

                result.add(attr);
            } catch (Exception e) {
                // Skip invalid attributes
                continue;
            }
        }

        return result;
    }

    /**
     * Parse a list of filter item maps into FilterV3Item objects recursively.
     * Handles nested lists and mixed item types.
     *
     * @param itemList List of filter items from YAML
     * @return List of parsed filter items
     */
    public static List<FilterV3Item> parseItems(List<?> itemList) {
        List<FilterV3Item> result = new ArrayList<>();

        for (Object item : itemList) {
            if (!(item instanceof Map)) {
                continue;
            }

            Map<String, Object> itemMap = (Map<String, Object>) item;
            try {
                FilterV3Item filterItem = parseFilterItem(itemMap);
                if (filterItem != null) {
                    result.add(filterItem);
                }
            } catch (Exception e) {
                // Skip invalid items
                continue;
            }
        }

        return result;
    }

    /**
     * Parse a single filter item map into a FilterV3Item object.
     * Handles recursion for nested list filters.
     *
     * @param itemMap Map representing a filter item from YAML
     * @return Parsed filter item, or null if invalid
     */
    private static FilterV3Item parseFilterItem(Map<String, Object> itemMap) {
        FilterV3Item item = new FilterV3Item();

        item.name = getStringField(itemMap, "name", null);
        item.type = getStringField(itemMap, "type", "item");

        if (!item.type.equals("attribute") && !item.type.equals("list") && !item.type.equals("item")) {
            return null;
        }

        Map<String, Object> modesMap = getMapField(itemMap, "modes", new HashMap<>());
        item.whitelist = getBooleanField(modesMap, "whitelist", true);
        item.matchAll = getBooleanField(modesMap, "matchAll", false);
        item.respectNBT = getBooleanField(modesMap, "respectNBT", false);

        if ("attribute".equals(item.type)) {
            List<?> attrList = getListField(itemMap, "attributes", new ArrayList<>());
            item.attributes = parseAttributes(attrList);
        } else if ("list".equals(item.type)) {
            List<?> nestedItems = getListField(itemMap, "items", new ArrayList<>());
            item.items = parseItems(nestedItems);
        } else if ("item".equals(item.type)) {
            item.itemId = getStringField(itemMap, "itemId", null);
            item.count = getIntField(itemMap, "count", 1);
        }

        return item;
    }

    /**
     * Reconstruct an ItemAttribute from a parsed AttributeV3 object.
     *
     * @param attr The attribute data to reconstruct
     * @return ItemAttribute, or null if reconstruction fails
     */
    public static ItemAttribute reconstructAttribute(AttributeV3 attr) {
        try {
            CompoundTag tag = new CompoundTag();

            if (attr.params == null || attr.params.isEmpty()) {
                return null;
            }

            CompoundTag valueTag = new CompoundTag();
            for (Map.Entry<String, Object> entry : attr.params.entrySet()) {
                objectToTag(valueTag, entry.getKey(), entry.getValue());
            }

            tag.put(attr.key, valueTag);
            return ItemAttribute.fromNBT(tag);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert a Java object to an NBT Tag for reconstruction.
     * Handles primitives, strings, and nested maps.
     *
     * @param parent Parent CompoundTag to add to
     * @param key Key in parent
     * @param value Value to convert
     */
    private static void objectToTag(CompoundTag parent, String key, Object value) {
        if (value == null) {
            return;
        }

        if (value instanceof Boolean) {
            parent.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            parent.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            parent.putLong(key, (Long) value);
        } else if (value instanceof Double) {
            parent.putDouble(key, (Double) value);
        } else if (value instanceof Float) {
            parent.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            parent.putString(key, (String) value);
        } else if (value instanceof Map) {
            CompoundTag nested = new CompoundTag();
            Map<String, Object> map = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                objectToTag(nested, entry.getKey(), entry.getValue());
            }
            parent.put(key, nested);
        } else if (value instanceof List) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            List<?> values = (List<?>) value;
            for (Object item : values) {
                if (item instanceof Map) {
                    CompoundTag nested = new CompoundTag();
                    Map<String, Object> map = (Map<String, Object>) item;
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        objectToTag(nested, entry.getKey(), entry.getValue());
                    }
                    list.add(nested);
                }
            }
            parent.put(key, list);
        }
    }

    // Utility methods for type-safe field extraction
    private static String getStringField(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }

    private static boolean getBooleanField(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    private static int getIntField(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMapField(Map<String, Object> map, String key, Map<String, Object> defaultValue) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static List<?> getListField(Map<String, Object> map, String key, List<?> defaultValue) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<?>) value;
        }
        return defaultValue;
    }

    /**
     * Parsed v3 filter data structure
     */
    public static class FilterV3Data {
        public String filterName;
        public String type; // "attribute" or "list"
        public boolean whitelist;
        public boolean matchAll;
        public boolean respectNBT;
        public List<AttributeV3> attributes; // For attribute filters
        public List<FilterV3Item> items; // For list filters
    }

    /**
     * Parsed attribute within a filter
     */
    public static class AttributeV3 {
        public String key;
        public Map<String, Object> params;
        public boolean inverted;
    }

    /**
     * Parsed filter item (attribute filter, list filter, or plain item)
     */
    public static class FilterV3Item {
        public String name;
        public String type; // "attribute", "list", or "item"
        public boolean whitelist;
        public boolean matchAll;
        public boolean respectNBT;
        public List<AttributeV3> attributes; // For attribute type
        public List<FilterV3Item> items; // For list type (nested)
        public String itemId; // For item type
        public int count; // For item type
    }

    /**
     * Exception thrown during import parsing
     */
    public static class ImportException extends Exception {
        public ImportException(String message) {
            super(message);
        }

        public ImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
