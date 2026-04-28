# Filter Export/Import Format v3 - Implementation Plan

## Executive Summary

Migrate from JSON-based export/import to an enhanced tree-based format (v3) that is:

- **Human-readable** for non-technical users
- **Data-complete** (zero data loss vs JSON export)
- **Bidirectionally compatible** (can import what was exported)
- **Backward compatible** (v1/v2 still work)
- **DRY** (shared code between attribute and list filters)

---

## Current State Issues

### JSON Format (v2)

✅ Pros: Structured, lossless, machine-readable
❌ Cons: Difficult for humans to read/edit, intimidating

### Tree Format (current)

✅ Pros: Easy to read
❌ Cons:

- Missing critical data (nested filter modes not shown)
- Not importable (just display, no parsing logic)
- Incomplete attribute serialization
- No NBT roundtrip capability

---

## Proposed V3 Format - YAML-Based

### Design Principles

1. **Pure YAML structure** - No NBT storage, everything reconstructible from YAML
2. **Hierarchical nesting** - YAML's natural indentation shows structure
3. **Complete data preservation** - All attributes, items, and modes included
4. **Human-editable** - Non-technical users can modify names, toggle flags
5. **Language-standard format** - YAML parsing available in all languages

### V3 Structure - List Filter Example

```yaml
format: vaultfilters.v3
version: '3.0'
name: 'Gear 2.0'
type: list

# List filter modes
modes:
  whitelist: true # false = Blacklist, true = Whitelist
  matchAll: false # false = ANY (OR), true = ALL (AND)
  respectNBT: false # false = Ignore NBT, true = Respect NBT

items:
  # Attribute filter with attributes
  - name: 'Gear Filter'
    type: attribute
    modes:
      whitelist: true
    attributes:
      - key: item_type
        params:
          item_type: 'Gear Piece'
        inverted: false
      - key: has_legendary
        params:
          has_legendary: 1
        inverted: false
      - key: item_name
        params:
          item_name: 'item.the_vault.vault_god_charm'
        inverted: true

  # Another attribute filter
  - name: 'Gear no magic'
    type: attribute
    modes:
      whitelist: true
    attributes:
      - key: max_legendary
        params:
          max_legendary: 1
        inverted: false
      - key: affix_number
        params:
          affix_number: '+70% Increased Attack Damage'
          affix_number_level: 0.7
          affix_number_simple: 'Increased Attack Damage'
        inverted: false

  # Nested list filter
  - name: 'Nested Router'
    type: list
    modes:
      whitelist: true
      matchAll: false
      respectNBT: true
    items:
      - name: 'Quality Check'
        type: attribute
        modes:
          whitelist: true
        attributes:
          - key: rarity
            params:
              rarity: 'Rare'
            inverted: false

  # Plain item filter
  - name: 'Diamond Stash'
    type: item
    itemId: 'minecraft:diamond'
    count: 1
```

### V3 Structure - Attribute Filter Example

```yaml
format: vaultfilters.v3
version: '3.0'
name: 'Powerful Gear'
type: attribute

# Attribute filter modes
modes:
  whitelist: true # false = Blacklist, true = Whitelist

attributes:
  - key: item_type
    params:
      item_type: 'Gear Piece'
    inverted: false

  - key: has_legendary
    params:
      has_legendary: 1
    inverted: false

  - key: affix_number
    params:
      affix_number: '+100% Increased Damage'
      affix_number_level: 1.0
      affix_number_simple: 'Increased Damage'
    inverted: false

  - key: level
    params:
      min_level: 50
    inverted: false
```

### Field Mapping Reference

**Root Level:**

- `format`: Always `"vaultfilters.v3"`
- `version`: Format version string `"3.0"`
- `name`: Filter display name (optional, defaults to "Filter")
- `type`: `"attribute"` or `"list"`
- `modes`: Mode flags (see below)
- `items`: Array of filter items (list filters only)
- `attributes`: Array of attributes (attribute filters only)

**Modes Object:**

For **List Filters**:

```yaml
modes:
  whitelist: true|false # Whitelist=true, Blacklist=false
  matchAll: true|false # ALL (AND)=true, ANY (OR)=false
  respectNBT: true|false # Respect NBT=true, Ignore NBT=false
```

For **Attribute Filters**:

```yaml
modes:
  whitelist: true|false # Whitelist=true, Blacklist=false
```

**Item Object** (in `items` array):

```yaml
- name: 'Filter Name' # Custom display name (optional)
  type: attribute|list|item # Filter type
  modes: { ... } # Modes (for attribute/list types)
  attributes: [...] # Attributes (for attribute type only)
  items: [...] # Nested items (for list type only)
  itemId: '...' # Item ID (for item type only)
  count: 1 # Item stack size (optional, default 1)
```

**Attribute Object** (in `attributes` array):

```yaml
- key: 'attribute_key' # Attribute identifier (e.g., "item_type", "rarity")
  params: # Attribute-specific parameters
    param1: value1
    param2: value2
  inverted: true|false # Inverted (blacklist) flag
```

---

## Implementation Roadmap

### Phase 1: Create Shared Utility Classes (DRY Foundation)

**File: `FilterExportUtilsV3.java` (new)**

```java
public class FilterExportUtilsV3 {
    // YAML building
    public static String buildYamlHeader(String filterName, String filterType,
                                        boolean isBlacklist, boolean matchAll, boolean respectNBT);

    public static String exportToYaml(List<FilterItemStack> items, String filterName,
                                      boolean isBlacklist, boolean matchAll, boolean respectNBT);

    public static String exportAttributesToYaml(List<Pair<ItemAttribute, Boolean>> attributes,
                                                 String filterName, boolean isBlacklist);

    // Attribute serialization
    public static Map<String, Object> attributeToMap(ItemAttribute attr, boolean inverted);
    public static String getAttributeKey(CompoundTag tag);

    // Format constants
    public static final String FORMAT_V3 = "vaultfilters.v3";
    public static final String VERSION = "3.0";
}
```

**File: `FilterImportUtilsV3.java` (new)**

```java
public class FilterImportUtilsV3 {
    // YAML parsing
    public static FilterV3Data parseYaml(String yamlText);
    public static List<FilterV3Item> parseItems(List<?> itemsList);
    public static List<AttributeV3> parseAttributes(List<?> attributesList);

    // Reconstruction
    public static CompoundTag reconstructAttributeNBT(AttributeV3 attr);
    public static ItemStack reconstructItemStack(FilterV3Item item);
    public static void applyModesToMenu(AbstractFilterMenu menu, FilterV3Data data);

    // Data classes
    public static class FilterV3Data {
        public String filterName;
        public String type; // "attribute" or "list"
        public boolean whitelist;
        public boolean matchAll; // List only
        public boolean respectNBT; // List only
        public List<FilterV3Item> items; // List only
        public List<AttributeV3> attributes; // Attribute only
    }

    public static class FilterV3Item {
        public String name;
        public String type; // "attribute", "list", "item"
        public boolean whitelist; // For attribute/list items
        public boolean matchAll; // For list items
        public boolean respectNBT; // For list items
        public String itemId; // For item type
        public int count; // For item type
        public List<AttributeV3> attributes; // For attribute type
        public List<FilterV3Item> items; // For list type
    }

    public static class AttributeV3 {
        public String key;
        public Map<String, Object> params;
        public boolean inverted;
    }
}
```

**File: `YamlParser.java` (new - lightweight YAML parser)**

Since we want to avoid external dependencies, create a minimal custom YAML parser that handles:

- Map/list parsing via indentation
- Quoted string values
- Boolean/number parsing
- Comments (lines starting with #)

```java
public class YamlParser {
    // Parse YAML text into Map structure
    public static Map<String, Object> parseYaml(String yaml);

    // Dump Map to YAML string
    public static String dumpYaml(Map<String, Object> data);
}
```

### Phase 2: Update MixinAttributeFilterScreen.java

**Changes:**

1. Add `vault_Filters$exportToClipboardV3()` method
2. Add `vault_Filters$importFromClipboardV3()` method
3. Update button click logic to detect v3 format on import
4. Reuse tree building code from Phase 1 utilities

**Key methods to add:**

```java
private void vault_Filters$exportToClipboardV3()  // Export as v3 tree
private void vault_Filters$importFromClipboardV3() // Import v3 tree
private JsonObject vault_Filters$exportToJsonV3()  // v3 as JSON backup
```

### Phase 3: Update MixinFilterScreen.java

**Changes:**

1. Add recursive tree building for nested list filters
2. Add `vault_Filters$exportToClipboardV3()` with nesting support
3. Add `vault_Filters$importFromClipboardV3()` with recursive parsing
4. Reuse shared utilities from Phase 1

**Key methods to add:**

```java
private void vault_Filters$buildTreeV3(StringBuilder sb, FilterItemStack filter, int depth)
private void vault_Filters$parseTreeV3(List<String> lines) // Recursive parsing
```

### Phase 4: Update FilterUiUtils.java

**Changes:**

1. Add v3 format detection (`isV3Format(String)`)
2. Add mode label generation for v3
3. Add tree character helpers
4. Add Base64 encoding/decoding for NBT

### Phase 5: Backward Compatibility & Format Detection

**Update export logic:**

```java
// Always export as v3 (with optional v2 JSON backup)
// Detection on import:
if (isV3Format(text)) {
    importFromClipboardV3();
} else if (isJsonFormat(text)) {
    importFromClipboardJSON(); // v1 or v2
} else {
    showError("Unknown format");
}
```

### Phase 6: Button Behavior

**Update button mappings:**

- **Normal Export** → v3 YAML format (default, pretty-printed)
- **Shift+Export** → v3 YAML minified (compact)
- **Shift+Ctrl+Export** → JSON v2 (legacy compatibility, optional)
- **Tree button** → Same as normal export (consolidate to v3 YAML)
- **Import button** → Auto-detect format (v1/v2/v3)

**Button state preservation:**

- ✅ Blacklist/Whitelist toggle → `modes.whitelist` field
- ✅ AND/OR toggle (list) → `modes.matchAll` field
- ✅ Respect NBT toggle (list) → `modes.respectNBT` field
- ✅ All attribute inverted flags → `inverted` field per attribute
- ✅ Filter names → `name` field
- ✅ Nested filter structure → Recursive `items` array

### Phase 7: Testing & Validation

**Test cases:**

1. ✅ Export attribute filter → Import as v3 → Verify all attributes + modes preserved
2. ✅ Export list filter → Import as v3 → Verify nested structure + all modes
3. ✅ Export v2 JSON → Import as v3 YAML (backward compat)
4. ✅ Export v3 YAML → Import as v2 JSON (if possible, data loss acceptable)
5. ✅ Round-trip: Export → Manual YAML edit (change name, add comment) → Import
6. ✅ Nested filters: Export deep tree (10+ levels) → Import → Verify nesting preserved
7. ✅ Mode persistence:
   - Attribute: Blacklist flag survives round-trip
   - List: Blacklist + AND/OR + Respect NBT all survive
8. ✅ Special characters: Quotes, colons, colons in names
9. ✅ Large filters: 1000+ attributes, 10-deep nesting
10. ✅ Empty/minimal filters: Single item, no attributes
11. ✅ YAML format validation: Proper indentation, no syntax errors
12. ✅ User-friendly editing: Change filter name in YAML, re-import works

---

## Data Persistence Guarantees

| Data Element                   | v2 JSON | v3 YAML  | Round-trip | Notes                              |
| ------------------------------ | ------- | -------- | ---------- | ---------------------------------- |
| Filter name                    | ✅      | ✅       | ✅         | Stored in `name` field             |
| Whitelist/Blacklist mode       | ✅      | ✅       | ✅         | In `modes.whitelist` (true/false)  |
| AND/OR mode (List only)        | ✅      | ✅       | ✅         | In `modes.matchAll` (true/false)   |
| Respect/Ignore NBT (List only) | ✅      | ✅       | ✅         | In `modes.respectNBT` (true/false) |
| All attributes/items           | ✅      | ✅       | ✅         | Reconstructed from YAML params     |
| Attribute inversion            | ✅      | ✅       | ✅         | Per-attribute `inverted` flag      |
| Nested filter structure        | ✅      | ✅       | ✅         | Recursive `items` array            |
| Nested filter modes            | ✅      | ✅ (NEW) | ✅         | Each item has own `modes`          |
| Custom filter names            | ✅      | ✅ (NEW) | ✅         | At root and per-item               |
| **NBT storage**                | ✅      | ❌ N/A   | ✅         | Fully reconstructible from YAML    |

---

## DRY Principle Application

### Code Reuse Strategy

1. **Shared YAML utilities** (Phase 1)
   - Both attribute and list filters use `FilterExportUtilsV3` for building YAML
   - Both use `FilterImportUtilsV3` for parsing YAML
   - `YamlParser` handles all parsing/dumping logic (one implementation)

2. **Common mode handling**
   - `applyModesToMenu()` handles both filter types' modes
   - `getModeLabel()` generates consistent mode strings
   - Mode persistence logic centralized

3. **Recursive structure** (one implementation for nesting)
   - List filter recursion for nested items → Attribute filter reuses base methods
   - `parseItems()` handles all item types: attribute, list, item
   - `exportToYaml()` recursive for nested filters

4. **Attribute serialization** (shared)
   - `attributeToMap()` converts ItemAttribute → YAML-compatible map
   - `getAttributeKey()` extracts attribute identifier consistently
   - Both filters use same conversion logic

### Code Reuse Metrics

- **Export logic**: ~80% shared (YAML building, mode handling)
- **Import logic**: ~75% shared (YAML parsing, mode application)
- **Mode handling**: 100% shared (centralized utility)
- **Overall duplication reduction**: ~45% less code vs current v2 implementation

### File Organization

```
vaultfilters/
├── util/
│   ├── FilterExportUtilsV3.java      (shared)
│   ├── FilterImportUtilsV3.java      (shared)
│   ├── YamlParser.java               (shared)
│   └── FilterUiUtils.java            (updated with v3 methods)
├── mixin/compat/create/
│   ├── MixinAttributeFilterScreen.java  (updated with v3 methods)
│   └── MixinFilterScreen.java           (updated with v3 methods)
```

---

## Compatibility Matrix

| Scenario                    | v1 Support     | v2 Support     | v3 Support         |
| --------------------------- | -------------- | -------------- | ------------------ |
| Export as v3                | —              | —              | ✅ Primary (YAML)  |
| Import v1                   | ✅ Legacy      | ✅ Works       | ✅ Works (convert) |
| Import v2                   | ✅ Legacy      | ✅ Works       | ✅ Works (convert) |
| Import v3                   | —              | —              | ✅ Primary (YAML)  |
| Edit in text then re-import | ❌ Hard (JSON) | ❌ Hard (JSON) | ✅ Easy (YAML)     |
| Manual sharing/copy-paste   | ❌ Risky       | ❌ Risky       | ✅ Safe & clear    |

---

## Benefits Over Current Implementation

| Aspect                      | Current (v2 JSON)          | v3 (YAML)                  | Improvement          |
| --------------------------- | -------------------------- | -------------------------- | -------------------- |
| **Readability**             | Dense JSON format          | Clean YAML structure       | 🟢 Much better       |
| **Editability**             | Risky (JSON syntax errors) | Safe (YAML plain text)     | 🟢 Much safer        |
| **Data completeness**       | Full but hidden            | Full and visible           | 🟢 Same, but clearer |
| **Nested mode display**     | ✅ Stored but not shown    | ✅ All modes at each level | 🟢 Better visibility |
| **For non-technical users** | ❌ Intimidating            | ✅ Clear structure         | 🟢 Accessible        |
| **Round-trip fidelity**     | ✅ 100%                    | ✅ 100%                    | 🟢 Same              |
| **File size**               | ~2-5KB                     | ~2-4KB                     | 🟢 Slightly smaller  |
| **Parsing complexity**      | JSON parser                | Simple line/key parsing    | 🟢 Simpler           |
| **Merge/version control**   | ❌ Difficult (JSON)        | ✅ Easy (plain text)       | 🟢 Much better       |
| **NBT dependency**          | ✅ Requires NBT storage    | ❌ Pure YAML               | 🟢 Simpler           |
| **Manual editing**          | ❌ Expert-only             | ✅ Non-technical friendly  | 🟢 User-friendly     |
| **Comments support**        | ❌ No                      | ✅ Yes (#comments)         | 🟢 Documented        |

---

## Error Handling Strategy

**Export errors:**

- Graceful fallback to v2 JSON if v3 YAML encoding fails (unlikely)
- Clear error message to user with what went wrong

**Import errors:**

- Detect malformed YAML (indentation errors, syntax issues)
- Validate required fields (format, version, type)
- Skip invalid attributes/items with user warning
- If parse fails completely, show error with sample format
- Never apply partial state (validate all before destructive action)

**Format detection:**

- Check `format: vaultfilters.v3` header first
- If not found, try JSON parser for v1/v2
- If both fail, show detailed error suggesting manual fix

---

## Rollout Plan

1. **Phase 1: Create shared utilities** (FilterExportUtilsV3, FilterImportUtilsV3, YamlParser)
2. **Phase 2-3: Implement export/import** (both attribute and list filters with v3 YAML)
3. **Phase 4-5: Format detection** (auto-detect v1/v2/v3 on import, convert as needed)
4. **Phase 6: Update button behavior** (Export → v3 YAML, Tree → v3 YAML, keep legacy options)
5. **Phase 7: Comprehensive testing** (round-trip, nesting, modes, edge cases)
6. **Phase 8: Documentation** (update README with format spec, examples)
7. **Phase 9: Release** (v3 as default export, v2 available as option)

**Timeline:** All phases should compile cleanly at each step, no breaking changes until final release

---

## Risk Mitigation

| Risk                           | Mitigation                                                |
| ------------------------------ | --------------------------------------------------------- |
| Break existing v2 imports      | Keep v2 parser, auto-detect on import, convert to v3      |
| Users expect JSON              | Provide v2 export option (Shift+Ctrl+Export)              |
| YAML indentation errors        | Use custom parser with clear error messages               |
| Attribute reconstruction fails | Fall back to showing error, keep existing filter intact   |
| Nested filter data loss        | Test with deep recursion (10+ levels), validate all modes |
| Character encoding issues      | Use UTF-8, handle special chars in names/params           |
| Large export file              | YAML is compact; test with 1000+ attributes               |
| Performance on large filters   | Use streaming parser for very large files                 |
