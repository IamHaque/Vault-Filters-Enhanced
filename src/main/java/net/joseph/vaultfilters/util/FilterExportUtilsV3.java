package net.joseph.vaultfilters.util;

import com.simibubi.create.content.logistics.filter.ItemAttribute;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Utilities for exporting filters to YAML format (v3).
 *
 * Handles:
 * - Building YAML structures from filter data
 * - Converting ItemAttributes to YAML-compatible maps
 * - Extracting attribute keys and parameters
 * - Mode label generation
 *
 * Used by both MixinAttributeFilterScreen and MixinFilterScreen.
 */
@OnlyIn(Dist.CLIENT)
public class FilterExportUtilsV3 {
    public static final String FORMAT_V3 = "vaultfilters.v3";
    public static final String VERSION = "3.0";
    public static final String FORMAT_FIELD = "format";
    public static final String VERSION_FIELD = "version";
    public static final String NAME_FIELD = "name";
    public static final String TYPE_FIELD = "type";
    public static final String MODES_FIELD = "modes";
    public static final String ATTRIBUTES_FIELD = "attributes";
    public static final String ITEMS_FIELD = "items";
    public static final String KEY_FIELD = "key";
    public static final String PARAMS_FIELD = "params";
    public static final String INVERTED_FIELD = "inverted";
    public static final String TYPE_ATTRIBUTE = "attribute";
    public static final String TYPE_LIST = "list";
    public static final String TYPE_ITEM = "item";

    // Mode field names
    public static final String MODE_WHITELIST = "whitelist";
    public static final String MODE_MATCH_ALL = "matchAll";
    public static final String MODE_RESPECT_NBT = "respectNBT";

    /**
     * Build a YAML root header for a filter export.
     *
     * @param filterName Name of the filter
     * @param filterType "attribute" or "list"
     * @param isBlacklist Whitelist/Blacklist mode
     * @param matchAll AND/OR mode (only for list filters)
     * @param respectNBT Respect NBT mode (only for list filters)
     * @return Map representing the root YAML header
     */
    public static Map<String, Object> buildYamlHeader(String filterName, String filterType,
                                                       boolean isBlacklist, boolean matchAll,
                                                       boolean respectNBT) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(FORMAT_FIELD, FORMAT_V3);
        root.put(VERSION_FIELD, VERSION);

        if (filterName != null && !filterName.isBlank()) {
            root.put(NAME_FIELD, filterName);
        }

        root.put(TYPE_FIELD, filterType);

        // Add modes
        Map<String, Object> modes = new LinkedHashMap<>();
        modes.put(MODE_WHITELIST, !isBlacklist);

        if (TYPE_LIST.equals(filterType)) {
            modes.put(MODE_MATCH_ALL, matchAll);
            modes.put(MODE_RESPECT_NBT, respectNBT);
        }

        root.put(MODES_FIELD, modes);

        return root;
    }

    /**
     * Convert an ItemAttribute to a YAML-compatible map for storage.
     *
     * @param attribute The ItemAttribute to convert
     * @param inverted Whether the attribute is inverted
     * @return Map with key, params, and inverted fields
     */
    public static Map<String, Object> attributeToMap(ItemAttribute attribute, boolean inverted) {
        Map<String, Object> attrMap = new LinkedHashMap<>();

        CompoundTag tag = new CompoundTag();
        attribute.serializeNBT(tag);

        String key = getAttributeKey(tag);
        if (key == null) {
            return null;
        }

        attrMap.put(KEY_FIELD, key);

        // Extract attribute parameters
        Map<String, Object> params = extractAttributeParams(tag, key);
        attrMap.put(PARAMS_FIELD, params);

        if (inverted) {
            attrMap.put(INVERTED_FIELD, true);
        }

        return attrMap;
    }

    /**
     * Extract the attribute key from an NBT tag.
     * The key is the first non-standard field (excluding "id" type).
     *
     * @param tag CompoundTag containing the attribute data
     * @return Attribute key, or null if not found
     */
    public static String getAttributeKey(CompoundTag tag) {
        // Standard fields to skip
        Set<String> standardFields = new HashSet<>(Arrays.asList(
            "id", "type", "TypeId", "Inverted"
        ));

        for (String key : tag.getAllKeys()) {
            if (!standardFields.contains(key)) {
                return key;
            }
        }

        return null;
    }

    /**
     * Extract attribute parameters from an NBT tag into a map.
     * Recursively flattens compound tags for readability.
     *
     * @param tag CompoundTag containing the attribute data
     * @param attributeKey The main attribute key
     * @return Map of parameter names to values
     */
    private static Map<String, Object> extractAttributeParams(CompoundTag tag, String attributeKey) {
        Map<String, Object> params = new LinkedHashMap<>();

        if (!tag.contains(attributeKey)) {
            return params;
        }

        Tag valueTag = tag.get(attributeKey);
        if (valueTag instanceof CompoundTag) {
            CompoundTag valueCompound = (CompoundTag) valueTag;
            for (String key : valueCompound.getAllKeys()) {
                params.put(key, tagToObject(valueCompound.get(key)));
            }
        } else {
            params.put(attributeKey, tagToObject(valueTag));
        }

        return params;
    }

    /**
     * Convert an NBT Tag to a Java object for YAML serialization.
     *
     * @param tag NBT Tag to convert
     * @return Object (String, Number, Boolean, etc.)
     */
    private static Object tagToObject(Tag tag) {
        if (tag == null) {
            return null;
        }

        switch (tag.getId()) {
            case Tag.TAG_BYTE: {
                byte value = ((net.minecraft.nbt.ByteTag) tag).getAsByte();
                return value != 0;
            }
            case Tag.TAG_SHORT: {
                short value = ((net.minecraft.nbt.ShortTag) tag).getAsShort();
                return (int) value;
            }
            case Tag.TAG_INT: {
                return ((net.minecraft.nbt.IntTag) tag).getAsInt();
            }
            case Tag.TAG_LONG: {
                long value = ((net.minecraft.nbt.LongTag) tag).getAsLong();
                return value;
            }
            case Tag.TAG_FLOAT: {
                float value = ((net.minecraft.nbt.FloatTag) tag).getAsFloat();
                return (double) value;
            }
            case Tag.TAG_DOUBLE: {
                return ((net.minecraft.nbt.DoubleTag) tag).getAsDouble();
            }
            case Tag.TAG_STRING: {
                return ((net.minecraft.nbt.StringTag) tag).getAsString();
            }
            case Tag.TAG_COMPOUND: {
                CompoundTag compound = (CompoundTag) tag;
                Map<String, Object> map = new LinkedHashMap<>();
                for (String key : compound.getAllKeys()) {
                    map.put(key, tagToObject(compound.get(key)));
                }
                return map;
            }
            case Tag.TAG_LIST: {
                net.minecraft.nbt.ListTag list = (net.minecraft.nbt.ListTag) tag;
                List<Object> result = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    result.add(tagToObject(list.get(i)));
                }
                return result;
            }
            default:
                return tag.getAsString();
        }
    }

    /**
     * Create a YAML map for a single filter item (attribute, list, or item).
     *
     * @param itemName Display name of the filter item
     * @param itemType "attribute", "list", or "item"
     * @param modes Mode map (may be null for items)
     * @param attributes Attributes list (for attribute type only)
     * @param items Nested items list (for list type only)
     * @param itemId Item ID (for item type only)
     * @return Map representing the filter item in YAML format
     */
    public static Map<String, Object> buildFilterItemMap(String itemName, String itemType,
                                                          Map<String, Object> modes,
                                                          List<Map<String, Object>> attributes,
                                                          List<Map<String, Object>> items,
                                                          String itemId) {
        Map<String, Object> itemMap = new LinkedHashMap<>();

        if (itemName != null && !itemName.isBlank()) {
            itemMap.put(NAME_FIELD, itemName);
        }

        itemMap.put(TYPE_FIELD, itemType);

        if (modes != null) {
            itemMap.put(MODES_FIELD, modes);
        }

        if (attributes != null && !attributes.isEmpty()) {
            itemMap.put(ATTRIBUTES_FIELD, attributes);
        }

        if (items != null && !items.isEmpty()) {
            itemMap.put(ITEMS_FIELD, items);
        }

        if (itemId != null && !itemId.isBlank() && TYPE_ITEM.equals(itemType)) {
            itemMap.put("itemId", itemId);
        }

        return itemMap;
    }

    /**
     * Convert a YAML export to pretty-printed string format.
     *
     * @param root Root YAML map
     * @return Formatted YAML string
     */
    public static String toYamlString(Map<String, Object> root) {
        try {
            return YamlParser.dumpYaml(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize YAML", e);
        }
    }
}
