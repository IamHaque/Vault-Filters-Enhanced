package net.joseph.vaultfilters.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.filter.ItemAttribute;
import com.simibubi.create.foundation.utility.Pair;
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
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        root.addProperty("type", "list_filter");
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
        root.addProperty("type", "list_filter");
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
        root.addProperty("type", "attribute_filter");
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
        root.addProperty("type", "attribute_filter");
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

    /**
     * Convert a numeric tag to a JSON string representation that preserves type information.
     * Uses NBT-style suffixes: f for float, d for double, l for long, etc.
     * Examples: "0.07f", "1.2d", "100l"
     */
    public static JsonPrimitive numericTagToJsonString(Tag tag) {
        if (tag instanceof FloatTag floatTag) {
            return new JsonPrimitive(floatTag.getAsFloat() + "f");
        }
        if (tag instanceof DoubleTag doubleTag) {
            return new JsonPrimitive(doubleTag.getAsDouble() + "d");
        }
        if (tag instanceof LongTag longTag) {
            return new JsonPrimitive(longTag.getAsLong() + "l");
        }
        if (tag instanceof IntTag intTag) {
            return new JsonPrimitive(intTag.getAsInt());
        }
        if (tag instanceof ShortTag shortTag) {
            return new JsonPrimitive(shortTag.getAsShort());
        }
        if (tag instanceof ByteTag byteTag) {
            return new JsonPrimitive(byteTag.getAsByte());
        }
        return null;
    }

    /**
     * Parse a numeric JSON string with type suffix back into the appropriate NBT tag.
     * Handles formats like "0.07f", "1.2d", "100l", etc.
     * Returns null if the string doesn't match a recognized numeric type pattern.
     */
    public static Tag parseNumericJsonString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        char lastChar = value.charAt(value.length() - 1);
        String numPart = value.substring(0, value.length() - 1);

        try {
            switch (lastChar) {
                case 'f', 'F' -> {
                    return FloatTag.valueOf(Float.parseFloat(numPart));
                }
                case 'd', 'D' -> {
                    return DoubleTag.valueOf(Double.parseDouble(numPart));
                }
                case 'l', 'L' -> {
                    return LongTag.valueOf(Long.parseLong(numPart));
                }
                default -> {
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Recursively convert an NBT tag to JSON with type-preserving numeric values.
     * All numeric values use NBT-style suffixes (f, d, l) to preserve type information
     * during simplified format export/import round-trips.
     */
    private static JsonElement tagToJsonWithTypes(Tag tag) {
        if (tag == null) {
            return JsonNull.INSTANCE;
        }

        if (tag instanceof CompoundTag compoundTag) {
            JsonObject object = new JsonObject();
            for (String key : compoundTag.getAllKeys()) {
                object.add(key, tagToJsonWithTypes(compoundTag.get(key)));
            }
            return object;
        }

        if (tag instanceof ListTag listTag) {
            JsonArray array = new JsonArray();
            for (int index = 0; index < listTag.size(); index++) {
                array.add(tagToJsonWithTypes(listTag.get(index)));
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

        // For numeric types, use type-preserving string representation
        JsonPrimitive numeric = numericTagToJsonString(tag);
        if (numeric != null) {
            return numeric;
        }

        if (tag instanceof StringTag stringTag) {
            return new JsonPrimitive(stringTag.getAsString());
        }

        if (tag instanceof ByteTag byteTag) {
            return new JsonPrimitive(byteTag.getAsByte() != 0);
        }

        return new JsonPrimitive(tag.getAsString());
    }

    /**
     * Normalize attribute JSON emitted from attribute tags to avoid duplicated
     * nested keys like { "card_color": { "card_color": "GREEN" } }.
     *
     * If the element is an object with a single key K whose value is an object
     * with a single key also named K, this collapses it to { K: innerValue }.
     *
     * For numeric values, preserves type information using NBT-style suffixes
     * (e.g., "0.07f" for FloatTag, "1.2d" for DoubleTag).
     */
    public static JsonElement attributeTagToJson(Tag tag) {
        // Use type-preserving conversion
        JsonElement elem = tagToJsonWithTypes(tag);
        if (!elem.isJsonObject()) return elem;

        JsonObject obj = elem.getAsJsonObject();
        if (obj.entrySet().size() != 1) return obj;

        String key = obj.keySet().iterator().next();
        JsonElement val = obj.get(key);
        if (!val.isJsonObject()) return obj;

        JsonObject inner = val.getAsJsonObject();
        if (inner.entrySet().size() != 1) return obj;
        if (!inner.has(key)) return obj;

        JsonElement innerVal = inner.get(key);
        JsonObject collapsed = new JsonObject();
        collapsed.add(key, innerVal);
        return collapsed;
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

        String rawString = primitive.getAsString();

        // Try to parse type-suffixed numeric strings (from simplified format export)
        // Examples: "0.07f", "1.2d", "100l"
        if (rawString.length() > 1) {
            char lastChar = rawString.charAt(rawString.length() - 1);
            if (lastChar == 'f' || lastChar == 'F' || lastChar == 'd' || lastChar == 'D' ||
                lastChar == 'l' || lastChar == 'L') {
                Tag parsedNumeric = parseNumericJsonString(rawString);
                if (parsedNumeric != null) {
                    return parsedNumeric;
                }
            }
        }

        if (primitive.isNumber()) {
            Number number = primitive.getAsNumber();
            if (rawString.contains(".") || rawString.contains("e") || rawString.contains("E")) {
                return DoubleTag.valueOf(number.doubleValue());
            }

            long longValue = number.longValue();
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return IntTag.valueOf((int) longValue);
            }
            return LongTag.valueOf(longValue);
        }

        return StringTag.valueOf(rawString);
    }

    public static CompoundTag jsonToCompoundTag(JsonElement element) {
        Tag tag = jsonToTag(element);
        return tag instanceof CompoundTag compoundTag ? compoundTag : new CompoundTag();
    }

    /**
     * If a CompoundTag represents a flattened attribute like { key: primitive }
     * expand it into the nested shape { key: { key: primitive } } which is
     * what Create's attribute parser expects inside MatchedAttributes.
     */
    public static CompoundTag expandFlattenedAttributeCompound(CompoundTag tag) {
        if (tag == null) return new CompoundTag();
        if (tag.getAllKeys().size() != 1) return tag;

        String k = tag.getAllKeys().iterator().next();
        Tag v = tag.get(k);
        if (v instanceof CompoundTag) return tag;

        CompoundTag inner = new CompoundTag();
        inner.put(k, v);
        CompoundTag outer = new CompoundTag();
        outer.put(k, inner);
        return outer;
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

    // ─── Attribute filter: build helpers ─────────────────────────────────

    public static JsonArray buildAttributeEntries(List<Pair<ItemAttribute, Boolean>> selectedAttributes, boolean simplifiedFormat) {
        JsonArray attributes = new JsonArray();
        for (Pair<ItemAttribute, Boolean> pair : selectedAttributes) {
            CompoundTag tag = new CompoundTag();
            pair.getFirst().serializeNBT(tag);
            JsonObject entry = new JsonObject();
            entry.addProperty("inverted", pair.getSecond());
            if (simplifiedFormat) {
                entry.add("attribute", attributeTagToJson(tag));
            } else {
                entry.addProperty("nbt", tag.toString());
            }
            attributes.add(entry);
        }
        return attributes;
    }

    // ─── Attribute filter: parse helpers ─────────────────────────────────

    public record AttributeImportResult(List<Pair<ItemAttribute, Boolean>> attributes, String name,
                                        boolean hasBlacklist, boolean blacklist,
                                        boolean hasMatchAll, boolean matchAll,
                                        int invalid, int duplicates) {}

    public static AttributeImportResult parseAttributeImport(JsonObject root) {
        String importedName = null;
        boolean hasImportedBlacklist = false;
        boolean importedBlacklist = false;
        boolean hasImportedMatchAll = false;
        boolean importedMatchAll = false;

        if (root.has(FilterUiUtils.FORMAT_FIELD)) {
            String format = root.get(FilterUiUtils.FORMAT_FIELD).isJsonPrimitive() ? root.get(FilterUiUtils.FORMAT_FIELD).getAsString() : "";
            if (!FilterUiUtils.ATTRIBUTE_FORMAT_V1.equals(format) && !FilterUiUtils.ATTRIBUTE_FORMAT_V2.equals(format) && !FilterUiUtils.ATTRIBUTE_FORMAT_SIMPLIFIED.equals(format)) {
                return new AttributeImportResult(new ArrayList<>(), null, false, false, false, false, 0, 0);
            }
        }
        if (!root.has(FilterUiUtils.ATTRIBUTES_FIELD) || !root.get(FilterUiUtils.ATTRIBUTES_FIELD).isJsonArray()) {
            return new AttributeImportResult(new ArrayList<>(), null, false, false, false, false, 0, 0);
        }
        if (root.has("name") && root.get("name").isJsonPrimitive()) {
            importedName = root.get("name").getAsString();
        }

        AttributeSettings attributeSettings = readAttributeSettings(root);
        hasImportedBlacklist = attributeSettings.hasBlacklist();
        importedBlacklist = attributeSettings.blacklist();
        hasImportedMatchAll = attributeSettings.hasMatchAll();
        importedMatchAll = attributeSettings.matchAll();

        JsonArray array = root.getAsJsonArray(FilterUiUtils.ATTRIBUTES_FIELD);
        List<Pair<ItemAttribute, Boolean>> imported = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        int invalid = 0;
        int duplicates = 0;

        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                invalid++;
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonElement payload = null;
            if (entry.has("nbt") && entry.get("nbt").isJsonPrimitive()) {
                payload = entry.get("nbt");
            } else if (entry.has("attribute") && entry.get("attribute").isJsonObject()) {
                payload = entry.get("attribute");
            }
            if (payload == null) {
                invalid++;
                continue;
            }
            boolean inverted = entry.has("inverted") && entry.get("inverted").getAsBoolean();
            try {
                CompoundTag tag;
                if (payload.isJsonPrimitive()) {
                    tag = TagParser.parseTag(payload.getAsString());
                } else {
                    tag = jsonToCompoundTag(payload);
                    tag = expandFlattenedAttributeCompound(tag);
                }
                ItemAttribute attribute = ItemAttribute.fromNBT(tag);
                if (attribute == null) {
                    invalid++;
                    continue;
                }
                String dedupeKey = (inverted ? "1:" : "0:") + tag;
                if (!dedupe.add(dedupeKey)) {
                    duplicates++;
                    continue;
                }
                imported.add(Pair.of(attribute, inverted));
            } catch (Exception ignored) {
                invalid++;
            }
        }
        return new AttributeImportResult(imported, importedName, hasImportedBlacklist, importedBlacklist, hasImportedMatchAll, importedMatchAll, invalid, duplicates);
    }

    // ─── List filter: build helpers ──────────────────────────────────────

    private static String getFilterItemCustomName(FilterItemStack filter) {
        ItemStack item = filter.item();
        if (item == null || item.isEmpty() || !item.hasCustomHoverName()) {
            return null;
        }
        String name = item.getHoverName().getString();
        return name == null || name.isBlank() ? null : name;
    }

    public static CompoundTag stripListItemsFromSerializedFilter(CompoundTag serializedTag) {
        CompoundTag compact = serializedTag.copy();
        if (compact.contains("tag", Tag.TAG_COMPOUND)) {
            CompoundTag itemTag = compact.getCompound("tag");
            itemTag.remove("Items");
            if (itemTag.isEmpty()) {
                compact.remove("tag");
            }
        }
        return compact;
    }

    public static JsonArray serializeMatchedAttributes(ItemStack itemStack) {
        JsonArray attributes = new JsonArray();
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTag()) {
            return attributes;
        }
        CompoundTag tag = itemStack.getTag();
        if (tag == null || !tag.contains("MatchedAttributes", Tag.TAG_LIST)) {
            return attributes;
        }
        ListTag matchedAttributes = tag.getList("MatchedAttributes", Tag.TAG_COMPOUND);
        for (int index = 0; index < matchedAttributes.size(); index++) {
            Tag entryTag = matchedAttributes.get(index);
            if (!(entryTag instanceof CompoundTag entry)) {
                continue;
            }
            JsonObject attributeEntry = new JsonObject();
            boolean inverted = entry.contains("Inverted", Tag.TAG_BYTE) && entry.getBoolean("Inverted");
            attributeEntry.addProperty("inverted", inverted);
            CompoundTag structuredEntry = entry.copy();
            structuredEntry.remove("Inverted");
            attributeEntry.add("attribute", attributeTagToJson(structuredEntry));
            attributes.add(attributeEntry);
        }
        return attributes;
    }

    public static JsonObject buildListFilterItemSimplified(FilterItemStack filter) {
        JsonObject obj = new JsonObject();
        String customName = getFilterItemCustomName(filter);
        if (customName != null) {
            obj.addProperty("name", customName);
        }
        if (filter instanceof FilterItemStack.AttributeFilterItemStack attrFilter) {
            obj.addProperty("type", "attribute_filter");
            obj.addProperty("isBlacklist", isAttributeFilterBlacklist(attrFilter.item()));
            obj.addProperty("matchAll", isAttributeFilterMatchAll(attrFilter.item()));
            obj.add("attributes", serializeMatchedAttributes(attrFilter.item()));
        } else if (filter instanceof FilterItemStack.ListFilterItemStack listFilter) {
            obj.addProperty("type", "list_filter");
            obj.addProperty("isBlacklist", listFilter.isBlacklist);
            obj.addProperty("shouldRespectNBT", listFilter.shouldRespectNBT);
            if (listFilter.item().hasTag()) {
                obj.addProperty("matchAll", listFilter.item().getTag().getBoolean("MatchAll"));
            }
            JsonArray nestedItems = new JsonArray();
            for (FilterItemStack item : listFilter.containedItems) {
                JsonObject nested = buildListFilterItemSimplified(item);
                if (nested != null) nestedItems.add(nested);
            }
            obj.add("items", nestedItems);
        } else if (!filter.isEmpty()) {
            obj.addProperty("type", "item_filter");
            obj.add("stack", tagToJson(filter.serializeNBT()));
        } else {
            return null;
        }
        return obj;
    }

    public static JsonObject buildListFilterItem(FilterItemStack filter, boolean legacyRaw, boolean simplifiedFormat) {
        if (simplifiedFormat) {
            return buildListFilterItemSimplified(filter);
        }
        JsonObject obj = new JsonObject();
        String customName = legacyRaw ? null : getFilterItemCustomName(filter);
        if (customName != null) {
            obj.addProperty("name", customName);
        }
        if (filter instanceof FilterItemStack.AttributeFilterItemStack attrFilter) {
            CompoundTag serializedTag = filter.serializeNBT();
            if (serializedTag != null) {
                obj.addProperty("nbt", serializedTag.toString());
            }
            obj.addProperty("type", "attribute_filter");
            obj.addProperty("inverted", false);
        } else if (filter instanceof FilterItemStack.ListFilterItemStack listFilter) {
            CompoundTag serializedTag = filter.serializeNBT();
            if (serializedTag != null) {
                if (legacyRaw) {
                    obj.addProperty("nbt", serializedTag.toString());
                } else {
                    CompoundTag compactListTag = stripListItemsFromSerializedFilter(serializedTag);
                    obj.addProperty("nbt", compactListTag.toString());
                }
            }
            obj.addProperty("type", "list_filter");
            JsonArray nestedItems = new JsonArray();
            for (FilterItemStack item : listFilter.containedItems) {
                JsonObject nested = buildListFilterItem(item, legacyRaw, simplifiedFormat);
                if (nested != null) nestedItems.add(nested);
            }
            obj.add("items", nestedItems);
            obj.addProperty("isBlacklist", listFilter.isBlacklist);
            obj.addProperty("shouldRespectNBT", listFilter.shouldRespectNBT);
            if (listFilter.item().hasTag()) {
                obj.addProperty("matchAll", listFilter.item().getTag().getBoolean("MatchAll"));
            }
        } else if (!filter.isEmpty()) {
            CompoundTag serializedTag = filter.serializeNBT();
            if (serializedTag != null) {
                obj.addProperty("nbt", serializedTag.toString());
            }
            obj.addProperty("type", "item_filter");
        } else {
            return null;
        }
        return obj;
    }

    public static boolean isAttributeFilterBlacklist(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasTag()) return false;
        CompoundTag tag = item.getTag();
        return tag != null
                && tag.contains("WhitelistMode", Tag.TAG_INT)
                && tag.getInt("WhitelistMode") == FilterItemStack.AttributeFilterItemStack.WhitelistMode.BLACKLIST.ordinal();
    }

    public static boolean isAttributeFilterMatchAll(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasTag()) return false;
        CompoundTag tag = item.getTag();
        return tag != null
                && tag.contains("WhitelistMode", Tag.TAG_INT)
                && tag.getInt("WhitelistMode") == FilterItemStack.AttributeFilterItemStack.WhitelistMode.WHITELIST_CONJ.ordinal();
    }

    // ─── List filter: parse/import helpers ──────────────────────────────

    public static CompoundTag applyNameToSerializedStack(CompoundTag serializedStack, String name) {
        if (serializedStack == null || name == null || name.isBlank()) return serializedStack;
        ItemStack stack = ItemStack.of(serializedStack);
        if (stack.isEmpty()) return serializedStack;
        stack.setHoverName(new TextComponent(name));
        return stack.serializeNBT();
    }

    public static CompoundTag buildAttributeFilterTagFromJson(JsonObject obj, String name) throws Exception {
        String nbtString = obj.has("nbt") && obj.get("nbt").isJsonPrimitive() ? obj.get("nbt").getAsString() : null;
        if (nbtString != null && !nbtString.isBlank()) {
            CompoundTag parsed = TagParser.parseTag(nbtString);
            return applyNameToSerializedStack(parsed, name);
        }
        JsonElement stackPayload = obj.has("stack") ? obj.get("stack") : null;
        if (stackPayload != null) {
            CompoundTag parsed = stackPayload.isJsonPrimitive()
                    ? TagParser.parseTag(stackPayload.getAsString())
                    : jsonToCompoundTag(stackPayload);
            return applyNameToSerializedStack(parsed, name);
        }
        ItemStack attributeStack = AllItems.ATTRIBUTE_FILTER.asStack();
        CompoundTag tag = attributeStack.getOrCreateTag();
        boolean hasBlacklist = obj.has("isBlacklist") && obj.get("isBlacklist").isJsonPrimitive();
        boolean hasMatchAll = obj.has("matchAll") && obj.get("matchAll").isJsonPrimitive();
        boolean blacklist = hasBlacklist && obj.get("isBlacklist").getAsBoolean();
        boolean matchAll = hasMatchAll && obj.get("matchAll").getAsBoolean();
        FilterItemStack.AttributeFilterItemStack.WhitelistMode mode = blacklist
                ? FilterItemStack.AttributeFilterItemStack.WhitelistMode.BLACKLIST
                : (matchAll ? FilterItemStack.AttributeFilterItemStack.WhitelistMode.WHITELIST_CONJ
                : FilterItemStack.AttributeFilterItemStack.WhitelistMode.WHITELIST_DISJ);
        tag.putInt("WhitelistMode", mode.ordinal());
        ListTag matchedAttributes = new ListTag();
        JsonArray attributes = obj.has("attributes") && obj.get("attributes").isJsonArray() ? obj.getAsJsonArray("attributes") : new JsonArray();
        for (JsonElement attributeElement : attributes) {
            if (!attributeElement.isJsonObject()) continue;
            JsonObject attributeObj = attributeElement.getAsJsonObject();
            boolean inverted = attributeObj.has("inverted") && attributeObj.get("inverted").getAsBoolean();
            JsonElement payload = attributeObj.has("attribute") ? attributeObj.get("attribute") : null;
            if (payload == null && attributeObj.has("nbt") && attributeObj.get("nbt").isJsonPrimitive()) {
                payload = attributeObj.get("nbt");
            }
            if (payload == null) continue;
            CompoundTag entryTag = payload.isJsonPrimitive()
                    ? TagParser.parseTag(payload.getAsString())
                    : jsonToCompoundTag(payload);
            if (entryTag.isEmpty()) continue;
            if (!payload.isJsonPrimitive()) {
                entryTag = expandFlattenedAttributeCompound(entryTag);
            }
            entryTag.putBoolean("Inverted", inverted);
            matchedAttributes.add(entryTag);
        }
        if (!matchedAttributes.isEmpty()) {
            tag.put("MatchedAttributes", matchedAttributes);
        }
        if (name != null && !name.isBlank()) {
            attributeStack.setHoverName(new TextComponent(name));
        }
        return attributeStack.serializeNBT();
    }

    public static CompoundTag buildListFilterTagFromJson(JsonObject obj, String name) throws Exception {
        JsonElement stackPayload = obj.has("stack") ? obj.get("stack") : null;
        String nbtString = obj.has("nbt") && obj.get("nbt").isJsonPrimitive() ? obj.get("nbt").getAsString() : null;
        ItemStack listStack;
        if (stackPayload != null) {
            listStack = stackPayload.isJsonPrimitive()
                    ? ItemStack.of(TagParser.parseTag(stackPayload.getAsString()))
                    : ItemStack.of(jsonToCompoundTag(stackPayload));
        } else if (nbtString != null && !nbtString.isBlank()) {
            listStack = ItemStack.of(TagParser.parseTag(nbtString));
        } else {
            listStack = AllItems.FILTER.asStack();
        }
        if (listStack.isEmpty()) {
            listStack = AllItems.FILTER.asStack();
        }
        CompoundTag listTag = listStack.getOrCreateTag();
        if (obj.has("isBlacklist") && obj.get("isBlacklist").isJsonPrimitive()) {
            listTag.putBoolean("Blacklist", obj.get("isBlacklist").getAsBoolean());
        }
        if (obj.has("shouldRespectNBT") && obj.get("shouldRespectNBT").isJsonPrimitive()) {
            listTag.putBoolean("RespectNBT", obj.get("shouldRespectNBT").getAsBoolean());
        }
        if (obj.has("matchAll") && obj.get("matchAll").isJsonPrimitive()) {
            listTag.putBoolean("MatchAll", obj.get("matchAll").getAsBoolean());
        }
        if (obj.has("items") && obj.get("items").isJsonArray()) {
            ItemStackHandler handler = FilterItem.getFilterItems(listStack);
            JsonArray nested = obj.getAsJsonArray("items");
            int slot = 0;
            for (JsonElement nestedElement : nested) {
                if (!nestedElement.isJsonObject()) continue;
                CompoundTag childTag = buildFilterTagFromJson(nestedElement.getAsJsonObject());
                if (childTag == null) continue;
                if (slot >= handler.getSlots()) break;
                handler.setStackInSlot(slot++, ItemStack.of(childTag));
            }
            listTag.put("Items", handler.serializeNBT());
        }
        if (name != null && !name.isBlank()) {
            listStack.setHoverName(new TextComponent(name));
        }
        return listStack.serializeNBT();
    }

    public static CompoundTag buildFilterTagFromJson(JsonObject obj) throws Exception {
        if (!obj.has("type") || !obj.get("type").isJsonPrimitive()) return null;
        String type = obj.get("type").getAsString();
        String name = null;
        if (obj.has("name") && obj.get("name").isJsonPrimitive()) {
            String raw = obj.get("name").getAsString();
            if (raw != null && !raw.isBlank()) name = raw;
        }
        if ("attribute_filter".equals(type)) {
            return buildAttributeFilterTagFromJson(obj, name);
        }
        if ("item_filter".equals(type)) {
            String nbtString = obj.has("nbt") && obj.get("nbt").isJsonPrimitive() ? obj.get("nbt").getAsString() : null;
            JsonElement stackPayloadObj = obj.has("stack") ? obj.get("stack") : null;
            CompoundTag parsed;
            if (stackPayloadObj != null) {
                parsed = stackPayloadObj.isJsonPrimitive()
                        ? TagParser.parseTag(stackPayloadObj.getAsString())
                        : jsonToCompoundTag(stackPayloadObj);
            } else if (nbtString != null && !nbtString.isBlank()) {
                parsed = TagParser.parseTag(nbtString);
            } else {
                return null;
            }
            return applyNameToSerializedStack(parsed, name);
        }
        if (!"list_filter".equals(type)) return null;
        return buildListFilterTagFromJson(obj, name);
    }

    // ─── WhitelistMode helpers ──────────────────────────────────────────

    public static FilterItemStack.AttributeFilterItemStack.WhitelistMode whitelistModeFromItem(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasTag()) {
            return FilterItemStack.AttributeFilterItemStack.WhitelistMode.WHITELIST_DISJ;
        }
        CompoundTag tag = item.getTag();
        if (tag == null || !tag.contains("WhitelistMode", Tag.TAG_INT)) {
            return FilterItemStack.AttributeFilterItemStack.WhitelistMode.WHITELIST_DISJ;
        }
        int ordinal = tag.getInt("WhitelistMode");
        FilterItemStack.AttributeFilterItemStack.WhitelistMode[] values = FilterItemStack.AttributeFilterItemStack.WhitelistMode.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return FilterItemStack.AttributeFilterItemStack.WhitelistMode.WHITELIST_DISJ;
        }
        return values[ordinal];
    }

    public static String getModeLabel(boolean isBlacklist, boolean matchAll) {
        if (isBlacklist) {
            return matchAll ? "Deny All" : "Deny Any";
        }
        return matchAll ? "Allow All" : "Allow Any";
    }
}
