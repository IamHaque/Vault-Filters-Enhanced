# Vault Filters v3 YAML Format Specification

## Overview

Vault Filters v3 introduces a human-readable YAML format for exporting and importing filters. This replaces the inline NBT format (v2 JSON) with a cleaner, more maintainable structure.

**Key Differences from v2:**

- ✅ No embedded NBT strings (fully human-readable)
- ✅ Recursive support for nested filters
- ✅ Explicit mode declarations (whitelist, matchAll, respectNBT)
- ✅ Backward compatible imports (v2 JSON still works)

---

## Root Structure

### Format Header

Every v3 export begins with metadata:

```yaml
format: vaultfilters.v3
version: "3.0"
name: "My Filter"
type: "attribute" | "list"
modes:
  whitelist: true | false
  matchAll: true | false        # List filters only
  respectNBT: true | false      # List filters only
```

**Field Descriptions:**

- `format` — Always `"vaultfilters.v3"` (identifies v3 format)
- `version` — Version string `"3.0"`
- `name` — (Optional) Custom filter name
- `type` — Filter type: `"attribute"` or `"list"`
- `modes` — Mode configuration:
  - `whitelist` — `true` = whitelist mode, `false` = blacklist mode
  - `matchAll` — (List only) `true` = AND mode, `false` = OR mode
  - `respectNBT` — (List only) `true` = respect NBT, `false` = ignore NBT

---

## Attribute Filter Format

Attribute filters contain a list of item attributes with match conditions.

### Full Example

```yaml
format: vaultfilters.v3
version: '3.0'
name: 'Damage Ranges'
type: attribute
modes:
  whitelist: true

attributes:
  - key: Damage
    params:
      min: 10.0
      max: 50.0
    inverted: false

  - key: Durability
    params:
      durability: 5
    inverted: true
```

### Attributes Array

Each attribute in the array has:

```yaml
- key: <AttributeKeyName>
  params:
    <param1>: <value1>
    <param2>: <value2>
    # ... additional params specific to the attribute
  inverted: true | false # Optional, default false
```

**Field Descriptions:**

- `key` — The attribute type name (e.g., `"Damage"`, `"Durability"`, `"Enchantment"`)
- `params` — Attribute-specific parameters (map of strings/numbers/booleans/arrays)
- `inverted` — Optional; inverts the match condition

### Attribute Parameter Examples

#### Damage Attribute

```yaml
- key: Damage
  params:
    min: 5.0
    max: 50.0
```

#### Durability Attribute

```yaml
- key: Durability
  params:
    durability: 100
```

#### Enchantment Attribute

```yaml
- key: Enchantment
  params:
    enchantment: 'minecraft:sharpness'
    level: 3
```

#### Modular Routers Filter Attributes

```yaml
- key: ModularRoutersFilter
  params:
    filter_id: 'modularrouters:type_item'
    # Additional filter-specific params
```

---

## List Filter Format (Recursive)

List filters contain nested items, which can be other list filters, attribute filters, or regular items.

### Full Example

```yaml
format: vaultfilters.v3
version: '3.0'
name: 'Master Filter'
type: list
modes:
  whitelist: true
  matchAll: false # OR mode
  respectNBT: true

items:
  - name: 'Ores'
    type: list
    modes:
      whitelist: true
      matchAll: false
      respectNBT: false
    items:
      - type: item
        itemId: 'minecraft:coal_ore'
      - type: item
        itemId: 'minecraft:iron_ore'
      - type: item
        itemId: 'minecraft:diamond_ore'

  - name: 'Enchanted Tools'
    type: attribute
    attributes:
      - key: Enchantment
        params:
          enchantment: 'minecraft:unbreaking'
          level: 1

  - type: item
    itemId: 'minecraft:apple'
    count: 64
```

### Items Array

Each item in the array has:

```yaml
- name: "Optional Custom Name"
  type: "attribute" | "list" | "item"
  # ... type-specific fields below
```

#### Attribute Filter Item

```yaml
- type: attribute
  attributes:
    - key: Damage
      params:
        min: 10.0
        max: 100.0
      inverted: false
```

#### List Filter Item (Recursive)

```yaml
- type: list
  modes:
    whitelist: true
    matchAll: false
    respectNBT: false
  items:
    # ... nested items array (same structure)
```

#### Regular Item

```yaml
- type: item
  itemId: 'minecraft:diamond_sword'
  count: 1 # Optional, default 1
```

---

## Data Types

| Type           | Example                                    | Notes                    |
| -------------- | ------------------------------------------ | ------------------------ |
| String         | `"minecraft:stone"`                        | Quoted strings           |
| Number (int)   | `42`, `100`                                | Whole numbers            |
| Number (float) | `1.5`, `3.14`                              | Decimal numbers          |
| Boolean        | `true`, `false`                            | Case-insensitive in YAML |
| Array          | `[1, 2, 3]` or `["a", "b"]`                | Lists of values          |
| Map            | `{key: value, key2: value2}` or multi-line | Nested key-value pairs   |

---

## Size & Nesting Limits

| Limit                  | Value           | Notes                                             |
| ---------------------- | --------------- | ------------------------------------------------- |
| Max Nesting Depth      | 32 levels       | Prevents stack overflow                           |
| Soft Export Size Limit | 1 MB            | Warnings above this; practical limit ~500 KB      |
| Max String Length      | 1,000,000 chars | Clipboard paste limit                             |
| Max Items per Filter   | Unlimited       | But nesting depth applies to recursive structures |

---

## Import/Export Behavior

### Export

- Lists all attributes with their exact match conditions
- Custom names preserved for filters and items
- All modes recorded (whitelist/matchAll/respectNBT)
- Recursive structures expanded fully

### Import

- **Replace Mode** (no Shift): Clears existing filter, applies imported version
- **Merge Mode** (Shift+Import): Appends imported items to existing filter
- **Deduplication**: Identical attributes/items not added twice
- **Validation**: Invalid item IDs or attributes gracefully skipped with notification
- **Auto-Detection**: Automatically detects v2 JSON vs v3 YAML and routes correctly

---

## Migration from v2 to v3

### v2 JSON Example

```json
{
  "format": "attribute_filter.v1",
  "name": "My Attributes",
  "isBlacklist": true,
  "attributes": [
    {
      "key": "Damage",
      "nbt": "{Damage:25.0d}"
    }
  ]
}
```

### v3 YAML Equivalent

```yaml
format: vaultfilters.v3
version: '3.0'
name: 'My Attributes'
type: attribute
modes:
  whitelist: false # v2 isBlacklist: true → whitelist: false
attributes:
  - key: Damage
    params:
      min: 25.0 # Reconstructed from NBT
    inverted: false
```

### Key Changes

- ✅ No `"nbt"` field; parameters explicit in `params` map
- ✅ `isBlacklist` → `whitelist` (inverted logic)
- ✅ New `type` field
- ✅ Recursive items fully supported (was only in list filters partially)

---

## Validation & Error Handling

### Export Validation

- ✅ Checks recursion depth (stops at 32 levels)
- ✅ Tracks export size (warning if > 1 MB)
- ✅ Silently skips invalid items
- ✅ Notifies user of export size and item count

### Import Validation

- ✅ Format detection (v2 vs v3)
- ✅ YAML parsing (malformed YAML → clear error)
- ✅ Type checking (attributes vs items)
- ✅ Item ID validation (invalid IDs skipped)
- ✅ Attribute reconstruction (missing attributes skipped)
- ✅ Deduplication (duplicates detected and skipped)

### Error Messages

- `"Empty clipboard"` — Nothing to import
- `"Clipboard too large"` — Exceeds 1 MB limit
- `"Invalid format"` — Neither v2 JSON nor v3 YAML detected
- `"Unsupported version"` — v3 format detected but version not `"3.0"`
- `"No items imported"` — All items were invalid or filtered out
- `"Imported X items (Y duplicates, Z invalid)"` — Detailed result

---

## Examples

### Example 1: Simple Damage Filter (Attribute)

```yaml
format: vaultfilters.v3
version: '3.0'
name: 'High Damage Items'
type: attribute
modes:
  whitelist: true

attributes:
  - key: Damage
    params:
      min: 20.0
```

### Example 2: Nested Crafting Filter (List)

```yaml
format: vaultfilters.v3
version: '3.0'
name: 'Crafting Whitelist'
type: list
modes:
  whitelist: true
  matchAll: false
  respectNBT: false

items:
  - type: item
    itemId: 'minecraft:oak_planks'
  - type: item
    itemId: 'minecraft:oak_logs'
  - type: item
    itemId: 'minecraft:crafting_table'
  - type: item
    itemId: 'minecraft:furnace'
```

### Example 3: Advanced Nested Structure

```yaml
format: vaultfilters.v3
version: '3.0'
name: 'Complex Warehouse Filter'
type: list
modes:
  whitelist: true
  matchAll: false
  respectNBT: false

items:
  - name: 'Ores & Minerals'
    type: list
    modes:
      whitelist: true
      matchAll: false
      respectNBT: false
    items:
      - type: item
        itemId: 'minecraft:coal_ore'
      - type: item
        itemId: 'minecraft:diamond_ore'
      - type: item
        itemId: 'minecraft:emerald_ore'

  - name: 'Enchanted Items'
    type: attribute
    attributes:
      - key: Enchantment
        params:
          enchantment: 'minecraft:unbreaking'
          level: 1

  - name: 'Special Blocks'
    type: list
    modes:
      whitelist: false # Blacklist mode inside whitelist
      matchAll: true
      respectNBT: true
    items:
      - type: item
        itemId: 'minecraft:bedrock'
      - type: item
        itemId: 'minecraft:end_portal_frame'
```

---

## YAML Syntax Notes

- **Colons & Spaces:** Keys must be followed by `:` (colon + space)
- **Indentation:** Use 2 spaces per level (not tabs)
- **Strings:** Quote strings containing special characters or spaces
- **Booleans:** Use `true` / `false` (no quotes)
- **Numbers:** Write as-is (integers or decimals, no quotes)
- **Comments:** Lines starting with `#` are ignored

### Valid YAML Examples

```yaml
# This is a comment
name: 'Filter Name' # String with colon in value must be quoted
count: 64 # Integer
damage: 5.5 # Float
enabled: true # Boolean
```

### Invalid YAML Examples

```yaml
name: Filter Name # ❌ Unquoted string with space should be quoted
count: '64' # ❌ Number as string (works but confusing)
mode: TRUE # ❌ Boolean must be lowercase
```

---

## Frequently Asked Questions

**Q: Can I manually edit exported YAML files?**
A: Yes! Vault Filters v3 is designed to be human-readable and editable. Just ensure YAML syntax is correct.

**Q: What happens if I import an invalid item ID?**
A: The item is silently skipped with a notification count. Other valid items are imported.

**Q: Can I mix v2 and v3 exports?**
A: Yes, the system auto-detects. Import either format and it will work.

**Q: What's the deepest filter nesting I can use?**
A: Up to 32 levels. Beyond that, the system stops recursion to prevent stack overflow.

**Q: Can I export to a file instead of clipboard?**
A: Currently clipboard only. You can copy from clipboard to a text file and edit locally.

**Q: Do I need to worry about NBT data loss?**
A: No! v3 reconstructs all item attributes from YAML parameters. No NBT is lost.

---

## Support & Issues

- **Format Issues:** Check YAML syntax (colons, indentation, quotes)
- **Import Failures:** Verify item IDs are valid (use `/give @s minecraft:item_name`)
- **Missing Attributes:** Some attributes may require specific mod versions
- **Nesting Too Deep:** Reduce depth below 32 levels or split into multiple filters

For bugs or feature requests, see the Vault Filters GitHub or modding community channels.
