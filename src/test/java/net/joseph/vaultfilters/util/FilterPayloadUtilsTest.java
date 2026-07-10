package net.joseph.vaultfilters.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterPayloadUtilsTest {

    @Test
    void listExportRootRetainsTopLevelSettings() {
        JsonArray items = new JsonArray();
        items.add(new JsonObject());

        JsonObject root = FilterPayloadUtils.buildListExportRoot(
                "Master Filter",
                true,
                true,
                false,
                items,
                FilterUiUtils.LIST_FORMAT_V2
        );

        assertEquals(FilterUiUtils.LIST_FORMAT_V2, root.get(FilterUiUtils.FORMAT_FIELD).getAsString());
        assertEquals("Master Filter", root.get("name").getAsString());

        JsonObject filter = root.getAsJsonObject("filter");
        assertTrue(filter.get("isBlacklist").getAsBoolean());
        assertTrue(filter.get("shouldRespectNBT").getAsBoolean());
        assertFalse(filter.get("matchAll").getAsBoolean());
        assertEquals(1, filter.getAsJsonArray("items").size());
    }

    @Test
    void listImportSettingsReadAllKnownFlags() {
        JsonObject filter = new JsonObject();
        filter.addProperty("isBlacklist", false);
        filter.addProperty("shouldRespectNBT", true);
        filter.addProperty("matchAll", true);

        FilterPayloadUtils.ListSettings settings = FilterPayloadUtils.readListSettings(filter);

        assertTrue(settings.hasBlacklist());
        assertFalse(settings.blacklist());
        assertTrue(settings.hasRespectNBT());
        assertTrue(settings.respectNBT());
        assertTrue(settings.hasMatchAll());
        assertTrue(settings.matchAll());
    }

    @Test
    void listTreeHeaderUsesAllowAnyAllAndRespectNBT() {
        assertEquals("Main Filter (Allow All, Respect NBT)",
                FilterPayloadUtils.listTreeHeader("Main Filter", false, true, true));
        assertEquals("List Filter (Deny Any)",
                FilterPayloadUtils.listTreeHeader("", true, false, false));
    }

    @Test
    void attributeExportRootRetainsBlacklistAndName() {
        JsonArray attributes = new JsonArray();
        attributes.add(new JsonObject());

        JsonObject root = FilterPayloadUtils.buildAttributeExportRoot(
                "Attribute Filter",
                true,
                true,
                attributes,
                FilterUiUtils.ATTRIBUTE_FORMAT_V2
        );

        assertEquals(FilterUiUtils.ATTRIBUTE_FORMAT_V2, root.get(FilterUiUtils.FORMAT_FIELD).getAsString());
        assertEquals("Attribute Filter", root.get("name").getAsString());
        assertTrue(root.get("isBlacklist").getAsBoolean());
        assertTrue(root.get("matchAll").getAsBoolean());
        assertEquals(1, root.getAsJsonArray(FilterUiUtils.ATTRIBUTES_FIELD).size());
    }

    @Test
    void attributeImportSettingsAcceptLegacyAndV2Formats() {
        JsonObject legacy = new JsonObject();
        legacy.addProperty(FilterUiUtils.FORMAT_FIELD, FilterUiUtils.ATTRIBUTE_FORMAT_V1);
        legacy.addProperty("isBlacklist", true);
        legacy.addProperty("matchAll", false);
        assertEquals(FilterUiUtils.ATTRIBUTE_FORMAT_V1, legacy.get(FilterUiUtils.FORMAT_FIELD).getAsString());
        assertTrue(FilterPayloadUtils.readAttributeSettings(legacy).blacklist());
        assertFalse(FilterPayloadUtils.readAttributeSettings(legacy).matchAll());

        JsonObject v2 = new JsonObject();
        v2.addProperty(FilterUiUtils.FORMAT_FIELD, FilterUiUtils.ATTRIBUTE_FORMAT_V2);
        v2.addProperty("isBlacklist", false);
        v2.addProperty("matchAll", true);
        assertEquals(FilterUiUtils.ATTRIBUTE_FORMAT_V2, v2.get(FilterUiUtils.FORMAT_FIELD).getAsString());
        assertFalse(FilterPayloadUtils.readAttributeSettings(v2).blacklist());
        assertTrue(FilterPayloadUtils.readAttributeSettings(v2).matchAll());
    }

    @Test
    void attributeImportSettingsReadBlacklistFlag() {
        JsonObject root = new JsonObject();
        root.addProperty("isBlacklist", true);

        FilterPayloadUtils.AttributeSettings settings = FilterPayloadUtils.readAttributeSettings(root);

        assertTrue(settings.hasBlacklist());
        assertTrue(settings.blacklist());
        assertFalse(settings.hasMatchAll());
    }

    @Test
    void attributeTreeHeaderUsesAllowAnyAllAndRespectBlacklist() {
        assertEquals("Attribute Filter (Allow Any)", FilterPayloadUtils.attributeTreeHeader(null, false, false));
        assertEquals("Custom Attribute Filter (Allow All)", FilterPayloadUtils.attributeTreeHeader("Custom Attribute Filter", false, true));
        assertEquals("Custom Attribute Filter (Deny Any)", FilterPayloadUtils.attributeTreeHeader("Custom Attribute Filter", true, false));
    }

    @Test
    void attributeModeLabelMatchesListModeLabels() {
        assertEquals("Allow Any", FilterPayloadUtils.attributeModeLabel(false, false));
        assertEquals("Allow All", FilterPayloadUtils.attributeModeLabel(false, true));
        assertEquals("Deny Any", FilterPayloadUtils.attributeModeLabel(true, false));
        assertEquals("Deny All", FilterPayloadUtils.attributeModeLabel(true, true));
    }

    @Test
    void jsonBridgeRoundTripsNestedCompoundAndListValues() {
        CompoundTag root = new CompoundTag();
        CompoundTag nested = new CompoundTag();
        nested.putString("card_color", "GREEN");
        root.put("attribute", nested);

        ListTag list = new ListTag();
        list.add(nested.copy());
        root.put("attributes", list);

        JsonObject json = FilterPayloadUtils.tagToJson(root).getAsJsonObject();
        CompoundTag roundTrip = FilterPayloadUtils.jsonToCompoundTag(json);

        assertEquals("GREEN", roundTrip.getCompound("attribute").getString("card_color"));
        assertTrue(roundTrip.contains("attributes", Tag.TAG_LIST));
    }

    @Test
    void nestedListImportValidationAcceptsValidNestedPayload() {
        JsonArray rootItems = new JsonArray();
        JsonObject nestedList = new JsonObject();
        nestedList.addProperty("type", "list_filter");
        nestedList.addProperty("name", "Nested");
        nestedList.add("items", new JsonArray());

        JsonObject leafAttribute = new JsonObject();
        leafAttribute.addProperty("type", "attribute_filter");
        leafAttribute.addProperty("nbt", "{Inverted:0b}");

        nestedList.getAsJsonArray("items").add(leafAttribute);
        rootItems.add(nestedList);

        assertTrue(FilterPayloadUtils.validateNestedListImport(rootItems));
    }

    @Test
    void nestedListImportValidationRejectsOverDeepPayload() {
        JsonArray items = new JsonArray();
        JsonArray currentItems = items;

        for (int depth = 0; depth <= FilterPayloadUtils.MAX_LIST_IMPORT_DEPTH; depth++) {
            JsonObject listNode = new JsonObject();
            listNode.addProperty("type", "list_filter");
            JsonArray childItems = new JsonArray();
            listNode.add("items", childItems);
            currentItems.add(listNode);
            currentItems = childItems;
        }

        assertFalse(FilterPayloadUtils.validateNestedListImport(items));
    }

    @Test
    void attributeFlatteningDoesNotBreakRoundTripImport() {
        // Build a CompoundTag that uses the nested duplicate-key shape produced by some attributes
        CompoundTag root = new CompoundTag();
        CompoundTag inner = new CompoundTag();
        inner.putString("card_color", "GREEN");
        // nested under same key
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("card_color", inner);
        root.put("MatchedAttributes", new ListTag());

        // Use the helper to produce JSON as exported by the UI
        JsonObject exported = FilterPayloadUtils.tagToJson(wrapper).getAsJsonObject();

        // Flatten via attributeTagToJson (what exporter now does)
        JsonObject flattened = FilterPayloadUtils.attributeTagToJson(wrapper).getAsJsonObject();

        // Ensure flattened shape no longer has nested duplicate
        assertTrue(flattened.has("card_color"));
        assertFalse(flattened.get("card_color").isJsonObject());

        // Ensure importer can turn flattened JSON back into a CompoundTag
        CompoundTag reconstructed = FilterPayloadUtils.jsonToCompoundTag(flattened);
        assertEquals("GREEN", reconstructed.getString("card_color"));
    }

    @Test
    void simplifiedAttributeExportImportRoundTrip() {
        // Simulate simplified exported attribute JSON { "card_color": "GREEN" }
        com.google.gson.JsonObject flat = new com.google.gson.JsonObject();
        flat.addProperty("card_color", "GREEN");

        CompoundTag parsed = FilterPayloadUtils.jsonToCompoundTag(flat);
        CompoundTag expanded = FilterPayloadUtils.expandFlattenedAttributeCompound(parsed);

        // Expanded should be { card_color: { card_color: "GREEN" } }
        CompoundTag inner = expanded.getCompound("card_color");
        assertEquals("GREEN", inner.getString("card_color"));

        // We don't call Create's parser here (may require game env); verifying
        // the expanded CompoundTag shape matches the legacy nested form is sufficient.
    }

    @Test
    void simplifiedNumericTypePreservationRoundTrip() {
        // Test that float/double types are preserved during simplified format export/import
        // Create a simple attribute tag with numeric value
        CompoundTag cardColorAttr = new CompoundTag();
        CompoundTag inner = new CompoundTag();
        inner.putFloat("card_color", 0.07f);
        cardColorAttr.put("card_color", inner);

        // Export to simplified JSON - should preserve float type with "f" suffix
        JsonElement json = FilterPayloadUtils.attributeTagToJson(cardColorAttr);
        JsonObject obj = json.getAsJsonObject();

        System.out.println("DEBUG: Generated JSON = " + FilterUiUtils.PRETTY_GSON.toJson(json));

        // After flattening, should collapse from {K: {K: V}} to {K: V}
        assertTrue(obj.has("card_color"));
        JsonElement cardColorValue = obj.get("card_color");
        assertTrue(cardColorValue.isJsonPrimitive(), "card_color should be primitive, got: " + cardColorValue.getClass().getSimpleName());
        String value = cardColorValue.getAsString();
        System.out.println("DEBUG: card_color value = '" + value + "'");
        assertTrue(value.endsWith("f"), "Float should have 'f' suffix, got: " + value);

        // Re-import and verify type is preserved
        CompoundTag reimported = FilterPayloadUtils.jsonToCompoundTag(json);
        // After flattening, the structure is {card_color: "0.07f"}, which re-imports to
        // CompoundTag with key "card_color" -> FloatTag(0.07f)
        Tag levelTag = reimported.get("card_color");
        assertTrue(levelTag instanceof FloatTag, "Should be FloatTag, got: " + levelTag.getClass().getSimpleName());
        assertEquals(0.07f, ((FloatTag) levelTag).getAsFloat(), 0.0001f);
    }

    @Test
    void listExportRootIncludesTypeField() {
        JsonObject root = FilterPayloadUtils.buildListExportRoot("Test", false, false, false, new JsonArray(), FilterUiUtils.LIST_FORMAT_V2);
        assertEquals("list_filter", root.get("type").getAsString());
    }

    @Test
    void listExportRootSimplifiedIncludesTypeField() {
        JsonObject root = FilterPayloadUtils.buildListExportRootSimplified("Test", false, false, false, new JsonArray());
        assertEquals("list_filter", root.get("type").getAsString());
    }

    @Test
    void attributeExportRootIncludesTypeField() {
        JsonObject root = FilterPayloadUtils.buildAttributeExportRoot("Test", false, false, new JsonArray(), FilterUiUtils.ATTRIBUTE_FORMAT_V2);
        assertEquals("attribute_filter", root.get("type").getAsString());
    }

    @Test
    void attributeExportRootSimplifiedIncludesTypeField() {
        JsonObject root = FilterPayloadUtils.buildAttributeExportRootSimplified("Test", false, false, new JsonArray());
        assertEquals("attribute_filter", root.get("type").getAsString());
    }

    @Test
    void numericJsonStringConversion() {
        // Test parsing of type-suffixed numeric strings

        // Test float parsing
        Tag floatTag = FilterPayloadUtils.parseNumericJsonString("0.07f");
        assertTrue(floatTag instanceof FloatTag);
        assertEquals(0.07f, ((FloatTag) floatTag).getAsFloat(), 0.0001f);

        // Test double parsing
        Tag doubleTag = FilterPayloadUtils.parseNumericJsonString("1.2d");
        assertTrue(doubleTag instanceof DoubleTag);
        assertEquals(1.2d, ((DoubleTag) doubleTag).getAsDouble(), 0.0001d);

        // Test long parsing
        Tag longTag = FilterPayloadUtils.parseNumericJsonString("100l");
        assertTrue(longTag instanceof LongTag);
        assertEquals(100L, ((LongTag) longTag).getAsLong());

        // Test invalid formats return null
        assertNull(FilterPayloadUtils.parseNumericJsonString("123x"));
        assertNull(FilterPayloadUtils.parseNumericJsonString("notanumber"));
        assertNull(FilterPayloadUtils.parseNumericJsonString(""));
    }
}
