package net.joseph.vaultfilters.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlParserRoundTripTest {
    @Test
    void exportsAttributePayloadWithCanonicalIndentation() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FilterExportUtilsV3.FORMAT_V3);
        root.put("version", FilterExportUtilsV3.VERSION);
        root.put("name", "Attribute Filter");
        root.put("type", FilterExportUtilsV3.TYPE_ATTRIBUTE);

        Map<String, Object> modes = new LinkedHashMap<>();
        modes.put(FilterExportUtilsV3.MODE_WHITELIST, true);
        root.put(FilterExportUtilsV3.MODES_FIELD, modes);

        List<Map<String, Object>> attributes = new ArrayList<>();
        attributes.add(attributeEntry("item_name", mapOf("item_name", "item.the_vault.greed_coin"), false));
        attributes.add(attributeEntry("card_pack_type", mapOf("card_pack_type", "Greed Booster Pack"), true));
        root.put(FilterExportUtilsV3.ATTRIBUTES_FIELD, attributes);

        String yaml = FilterExportUtilsV3.toYamlString(root);
        String expected = String.join("\n",
            "format: vaultfilters.v3",
            "version: \"3.0\"",
            "name: Attribute Filter",
            "type: attribute",
            "modes:",
            "  whitelist: true",
            "attributes:",
            "  - key: item_name",
            "    params:",
            "      item_name: item.the_vault.greed_coin",
            "  - key: card_pack_type",
            "    params:",
            "      card_pack_type: Greed Booster Pack",
            "    inverted: true"
        );

        assertEquals(expected, yaml);
    }

    @Test
    void parsesExportedAttributePayloadBackIntoStructure() throws Exception {
        String yaml = String.join("\n",
            "format: vaultfilters.v3",
            "version: \"3.0\"",
            "name: Attribute Filter",
            "type: attribute",
            "modes:",
            "  whitelist: true",
            "attributes:",
            "  - key: item_name",
            "    params:",
            "      item_name: item.the_vault.greed_coin",
            "  - key: card_pack_type",
            "    params:",
            "      card_pack_type: Greed Booster Pack",
            "    inverted: true"
        );

        FilterImportUtilsV3.FilterV3Data data = FilterImportUtilsV3.parseYaml(yaml);

        assertEquals("Attribute Filter", data.filterName);
        assertEquals("attribute", data.type);
        assertTrue(data.whitelist);
        assertNotNull(data.attributes);
        assertEquals(2, data.attributes.size());
        assertEquals("item_name", data.attributes.get(0).key);
        assertEquals("item.the_vault.greed_coin", data.attributes.get(0).params.get("item_name"));
        assertEquals("card_pack_type", data.attributes.get(1).key);
        assertTrue(data.attributes.get(1).inverted);
        assertEquals("Greed Booster Pack", data.attributes.get(1).params.get("card_pack_type"));
    }

    @Test
    void exportsListPayloadWithCanonicalIndentation() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FilterExportUtilsV3.FORMAT_V3);
        root.put("version", FilterExportUtilsV3.VERSION);
        root.put("name", "List Filter");
        root.put("type", FilterExportUtilsV3.TYPE_LIST);

        Map<String, Object> modes = new LinkedHashMap<>();
        modes.put(FilterExportUtilsV3.MODE_WHITELIST, false);
        modes.put(FilterExportUtilsV3.MODE_MATCH_ALL, true);
        modes.put(FilterExportUtilsV3.MODE_RESPECT_NBT, true);
        root.put(FilterExportUtilsV3.MODES_FIELD, modes);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(listItem("item", "Stone Stack", mapOf("itemId", "minecraft:stone", "count", 3)));
        items.add(listItem("attribute", null, mapOf(
            "key", "item_name",
            "params", mapOf("item_name", "item.the_vault.greed_coin")
        )));

        root.put(FilterExportUtilsV3.ITEMS_FIELD, items);

        String yaml = FilterExportUtilsV3.toYamlString(root);

        assertTrue(yaml.contains("format: vaultfilters.v3"));
        assertTrue(yaml.contains("type: list"));
        assertTrue(yaml.contains("modes:"));
        assertTrue(yaml.contains("  whitelist: false"));
        assertTrue(yaml.contains("  matchAll: true"));
        assertTrue(yaml.contains("  respectNBT: true"));
        assertTrue(yaml.contains("items:"));
        assertTrue(yaml.contains("  - name: Stone Stack"));
        assertTrue(yaml.contains("    type: item"));
        assertTrue(yaml.contains("    itemId: \"minecraft:stone\""));
        assertTrue(yaml.contains("    count: 3"));
        assertTrue(yaml.contains("  - type: attribute"));
        assertTrue(yaml.contains("    key: item_name"));
        assertTrue(yaml.contains("    params:"));
        assertTrue(yaml.contains("      item_name: item.the_vault.greed_coin"));
        assertFalse(yaml.contains("  -\n"));
    }

    @Test
    void parsesExportedListPayloadBackIntoStructure() throws Exception {
        String yaml = String.join("\n",
            "format: vaultfilters.v3",
            "version: \"3.0\"",
            "name: List Filter",
            "type: list",
            "modes:",
            "  whitelist: false",
            "  matchAll: true",
            "  respectNBT: true",
            "items:",
            "  - name: Stone Stack",
            "    type: item",
            "    itemId: \"minecraft:stone\"",
            "    count: 3"
        );

        FilterImportUtilsV3.FilterV3Data data = FilterImportUtilsV3.parseYaml(yaml);

        assertEquals("List Filter", data.filterName);
        assertEquals("list", data.type);
        assertFalse(data.whitelist);
        assertTrue(data.matchAll);
        assertTrue(data.respectNBT);
        assertNotNull(data.items);
        assertEquals(1, data.items.size());

        FilterImportUtilsV3.FilterV3Item firstItem = data.items.get(0);
        assertEquals("Stone Stack", firstItem.name);
        assertEquals("item", firstItem.type);
        assertEquals("minecraft:stone", firstItem.itemId);
        assertEquals(3, firstItem.count);

    }

    @Test
    void parsesYamlWithCommentsAndQuotedValues() throws Exception {
        String yaml = String.join("\n",
            "# leading comment should be ignored",
            "format: vaultfilters.v3",
            "version: \"3.0\"",
            "name: \"Quoted Name\"",
            "type: attribute",
            "modes:",
            "  whitelist: true",
            "attributes:",
            "  - key: item_name",
            "    params:",
            "      item_name: \"item.the_vault.greed_coin\""
        );

        FilterImportUtilsV3.FilterV3Data data = FilterImportUtilsV3.parseYaml(yaml);

        assertEquals("Quoted Name", data.filterName);
        assertEquals("attribute", data.type);
        assertEquals(1, data.attributes.size());
        assertEquals("item_name", data.attributes.get(0).key);
        assertEquals("item.the_vault.greed_coin", data.attributes.get(0).params.get("item_name"));
    }

    @Test
    void parsesYamlWithoutOptionalModesBlock() throws Exception {
        String yaml = String.join("\n",
            "format: vaultfilters.v3",
            "version: \"3.0\"",
            "name: Broken Filter",
            "type: attribute",
            "attributes:"
        );

        Map<String, Object> parsed = YamlParser.parseYaml(yaml);
        assertNull(parsed.get("modes"));
        assertEquals("Broken Filter", parsed.get("name"));
    }

    private static Map<String, Object> attributeEntry(String key, Map<String, Object> params, boolean inverted) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", key);
        entry.put("params", params);
        if (inverted) {
            entry.put("inverted", true);
        }
        return entry;
    }

    private static Map<String, Object> listItem(String type, String name, Map<String, Object> values) {
        Map<String, Object> entry = new LinkedHashMap<>();
        if (name != null) {
            entry.put("name", name);
        }
        entry.put("type", type);
        entry.putAll(values);
        return entry;
    }

    private static Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
