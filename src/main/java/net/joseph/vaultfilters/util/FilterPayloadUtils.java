package net.joseph.vaultfilters.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public final class FilterPayloadUtils {
    private FilterPayloadUtils() {
    }

    public static final int MAX_LIST_IMPORT_DEPTH = 16;
    public static final int MAX_LIST_IMPORT_NODES = 512;

    public record ListSettings(boolean hasBlacklist, boolean blacklist, boolean hasRespectNBT, boolean respectNBT,
                               boolean hasMatchAll, boolean matchAll) {
    }

    public record AttributeSettings(boolean hasBlacklist, boolean blacklist, boolean hasMatchAll, boolean matchAll) {
    }

    public static JsonObject buildListExportRoot(String name, boolean blacklist, boolean respectNBT, boolean matchAll,
                                                 JsonArray items, String format) {
        JsonObject root = new JsonObject();
        root.addProperty(FilterUiUtils.FORMAT_FIELD, format);
        if (name != null && !name.isBlank()) {
            root.addProperty("name", name);
        }

        JsonObject filter = new JsonObject();
        filter.addProperty("isBlacklist", blacklist);
        filter.addProperty("shouldRespectNBT", respectNBT);
        filter.addProperty("matchAll", matchAll);
        filter.add("items", items == null ? new JsonArray() : items);
        root.add("filter", filter);
        return root;
    }

    public static JsonObject buildListExportRootSimplified(String name, boolean blacklist, boolean respectNBT,
                                                           boolean matchAll, JsonArray items) {
        JsonObject root = new JsonObject();
        root.addProperty(FilterUiUtils.FORMAT_FIELD, FilterUiUtils.LIST_FORMAT_SIMPLIFIED);
        if (name != null && !name.isBlank()) {
            root.addProperty("name", name);
        }

        JsonObject filter = new JsonObject();
        filter.addProperty("isBlacklist", blacklist);
        filter.addProperty("shouldRespectNBT", respectNBT);
        filter.addProperty("matchAll", matchAll);
        filter.add("items", items == null ? new JsonArray() : items);
        root.add("filter", filter);
        return root;
    }

    public static JsonObject buildAttributeExportRoot(String name, boolean blacklist, boolean matchAll,
                                                      JsonArray attributes, String format) {
        JsonObject root = new JsonObject();
        root.addProperty(FilterUiUtils.FORMAT_FIELD, format);
        root.addProperty("isBlacklist", blacklist);
        root.addProperty("matchAll", matchAll);
        if (name != null && !name.isBlank()) {
            root.addProperty("name", name);
        }
        root.add(FilterUiUtils.ATTRIBUTES_FIELD, attributes == null ? new JsonArray() : attributes);
        return root;
    }

    public static JsonObject buildAttributeExportRootSimplified(String name, boolean blacklist, boolean matchAll,
                                                                JsonArray attributes) {
        JsonObject root = new JsonObject();
        root.addProperty(FilterUiUtils.FORMAT_FIELD, FilterUiUtils.ATTRIBUTE_FORMAT_SIMPLIFIED);
        root.addProperty("isBlacklist", blacklist);
        root.addProperty("matchAll", matchAll);
        if (name != null && !name.isBlank()) {
            root.addProperty("name", name);
        }
        root.add(FilterUiUtils.ATTRIBUTES_FIELD, attributes == null ? new JsonArray() : attributes);
        return root;
    }

    public static ListSettings readListSettings(JsonObject filterObj) {
        boolean hasBlacklist = filterObj.has("isBlacklist") && filterObj.get("isBlacklist").isJsonPrimitive();
        boolean hasRespectNBT = filterObj.has("shouldRespectNBT") && filterObj.get("shouldRespectNBT").isJsonPrimitive();
        boolean hasMatchAll = filterObj.has("matchAll") && filterObj.get("matchAll").isJsonPrimitive();
        return new ListSettings(
                hasBlacklist,
                hasBlacklist && filterObj.get("isBlacklist").getAsBoolean(),
                hasRespectNBT,
                hasRespectNBT && filterObj.get("shouldRespectNBT").getAsBoolean(),
                hasMatchAll,
                hasMatchAll && filterObj.get("matchAll").getAsBoolean()
        );
    }

    public static AttributeSettings readAttributeSettings(JsonObject root) {
        boolean hasBlacklist = root.has("isBlacklist") && root.get("isBlacklist").isJsonPrimitive();
        boolean hasMatchAll = root.has("matchAll") && root.get("matchAll").isJsonPrimitive();
        return new AttributeSettings(
                hasBlacklist,
                hasBlacklist && root.get("isBlacklist").getAsBoolean(),
                hasMatchAll,
                hasMatchAll && root.get("matchAll").getAsBoolean()
        );
    }

    /**
     * Checks if an attribute entry in the JSON is in simplified format (no NBT field).
     * Simplified format has structured fields like "key", "value", "inverted".
     */
    public static boolean isAttributeSimplifiedFormat(JsonObject entry) {
        return !entry.has("nbt") && entry.has("key");
    }

    /**
     * Checks if a list item in the JSON is in simplified format (no NBT field).
     * Simplified format has "type" field to identify the filter type.
     */
    public static boolean isListItemSimplifiedFormat(JsonObject item) {
        return !item.has("nbt") || isListItemTypedFormat(item);
    }

    /**
     * Checks if a JSON item has explicit type field (used in both simplified and structured formats).
     */
    public static boolean isListItemTypedFormat(JsonObject item) {
        return item.has("type") && item.get("type").isJsonPrimitive();
    }

    /**
     * Checks if a JSON root or filter uses simplified format by looking for format field.
     */
    public static boolean isSimplifiedFormat(JsonObject root) {
        if (!root.has(FilterUiUtils.FORMAT_FIELD)) {
            return false;
        }
        String format = root.get(FilterUiUtils.FORMAT_FIELD).getAsString();
        return FilterUiUtils.ATTRIBUTE_FORMAT_SIMPLIFIED.equals(format) ||
               FilterUiUtils.LIST_FORMAT_SIMPLIFIED.equals(format);
    }

    public static JsonElement tagToJson(Tag tag) {
        if (tag == null) {
            return JsonNull.INSTANCE;
        }

        if (tag instanceof CompoundTag compoundTag) {
            JsonObject object = new JsonObject();
            for (String key : compoundTag.getAllKeys()) {
                object.add(key, tagToJson(compoundTag.get(key)));
            }
            return object;
        }

        if (tag instanceof ListTag listTag) {
            JsonArray array = new JsonArray();
            for (int index = 0; index < listTag.size(); index++) {
                array.add(tagToJson(listTag.get(index)));
            }
            return array;
        }

        if (tag instanceof ByteArrayTag byteArrayTag) {
            JsonArray array = new JsonArray();
            for (byte value : byteArrayTag.getAsByteArray()) {
                array.add(value);
            }
            return array;
        }

        if (tag instanceof IntArrayTag intArrayTag) {
            JsonArray array = new JsonArray();
            for (int value : intArrayTag.getAsIntArray()) {
                array.add(value);
            }
            return array;
        }

        if (tag instanceof LongArrayTag longArrayTag) {
            JsonArray array = new JsonArray();
            for (long value : longArrayTag.getAsLongArray()) {
                array.add(value);
            }
            return array;
        }

        if (tag instanceof StringTag stringTag) {
            return new JsonPrimitive(stringTag.getAsString());
        }

        if (tag instanceof ByteTag byteTag) {
            return new JsonPrimitive(byteTag.getAsByte() != 0);
        }

        if (tag instanceof ShortTag shortTag) {
            return new JsonPrimitive(shortTag.getAsShort());
        }

        if (tag instanceof IntTag intTag) {
            return new JsonPrimitive(intTag.getAsInt());
        }

        if (tag instanceof LongTag longTag) {
            return new JsonPrimitive(longTag.getAsLong());
        }

        if (tag instanceof FloatTag floatTag) {
            return new JsonPrimitive(floatTag.getAsFloat());
        }

        if (tag instanceof DoubleTag doubleTag) {
            return new JsonPrimitive(doubleTag.getAsDouble());
        }

        return new JsonPrimitive(tag.getAsString());
    }

    public static Tag jsonToTag(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return StringTag.valueOf("");
        }

        if (element.isJsonObject()) {
            CompoundTag compoundTag = new CompoundTag();
            JsonObject object = element.getAsJsonObject();
            for (String key : object.keySet()) {
                compoundTag.put(key, jsonToTag(object.get(key)));
            }
            return compoundTag;
        }

        if (element.isJsonArray()) {
            ListTag listTag = new ListTag();
            for (JsonElement child : element.getAsJsonArray()) {
                listTag.add(jsonToTag(child));
            }
            return listTag;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return ByteTag.valueOf((byte) (primitive.getAsBoolean() ? 1 : 0));
        }

        if (primitive.isNumber()) {
            Number number = primitive.getAsNumber();
            String raw = primitive.getAsString();
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return DoubleTag.valueOf(number.doubleValue());
            }

            long longValue = number.longValue();
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return IntTag.valueOf((int) longValue);
            }
            return LongTag.valueOf(longValue);
        }

        return StringTag.valueOf(primitive.getAsString());
    }

    public static CompoundTag jsonToCompoundTag(JsonElement element) {
        Tag tag = jsonToTag(element);
        return tag instanceof CompoundTag compoundTag ? compoundTag : new CompoundTag();
    }

    public static String listModeLabel(boolean isBlacklist, boolean matchAll) {
        if (isBlacklist) {
            return matchAll ? "Deny All" : "Deny Any";
        }
        return matchAll ? "Allow All" : "Allow Any";
    }

    public static String attributeModeLabel(boolean isBlacklist, boolean matchAll) {
        return listModeLabel(isBlacklist, matchAll);
    }

    public static String listTreeHeader(String rootName, boolean isBlacklist, boolean matchAll, boolean respectNBT) {
        StringBuilder tree = new StringBuilder();
        tree.append(rootName == null || rootName.isBlank() ? "List Filter" : rootName)
                .append(" (")
                .append(listModeLabel(isBlacklist, matchAll));
        if (respectNBT) {
            tree.append(", Respect NBT");
        }
        return tree.append(")").toString();
    }

    public static String attributeTreeHeader(String rootName, boolean isBlacklist, boolean matchAll) {
        return (rootName == null || rootName.isBlank() ? "Attribute Filter" : rootName) + " (" + attributeModeLabel(isBlacklist, matchAll) + ")";
    }

    public static boolean validateNestedListImport(JsonArray items) {
        if (items == null) {
            return false;
        }

        int[] nodeCounter = new int[] {0};
        return validateNestedListImport(items, 0, nodeCounter);
    }

    private static boolean validateNestedListImport(JsonArray items, int depth, int[] nodeCounter) {
        if (depth > MAX_LIST_IMPORT_DEPTH) {
            return false;
        }

        for (int index = 0; index < items.size(); index++) {
            if (!items.get(index).isJsonObject()) {
                return false;
            }
            if (!validateNestedListImportNode(items.get(index).getAsJsonObject(), depth, nodeCounter)) {
                return false;
            }
        }

        return true;
    }

    private static boolean validateNestedListImportNode(JsonObject obj, int depth, int[] nodeCounter) {
        if (++nodeCounter[0] > MAX_LIST_IMPORT_NODES) {
            return false;
        }

        if (!obj.has("type") || !obj.get("type").isJsonPrimitive()) {
            return false;
        }

        String type = obj.get("type").getAsString();
        if ("list_filter".equals(type)) {
            if (!obj.has("items") || !obj.get("items").isJsonArray()) {
                return false;
            }
            return validateNestedListImport(obj.getAsJsonArray("items"), depth + 1, nodeCounter);
        }

        return "attribute_filter".equals(type) || "item_filter".equals(type);
    }
}
