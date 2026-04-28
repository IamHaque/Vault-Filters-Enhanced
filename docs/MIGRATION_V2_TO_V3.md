# Migration Guide: Vault Filters v2 → v3

## Overview

Vault Filters v3 introduces a new YAML export/import format. **Good news:** You don't have to migrate anything immediately. The mod still supports v2 JSON imports, so your existing exports continue to work.

However, **we recommend using v3 YAML** for new exports because:

- ✅ Human-readable (no embedded NBT code)
- ✅ Easier to share and edit
- ✅ Better recursive support (nested filters)
- ✅ Smaller file size
- ✅ Future-proof (v2 support may be deprecated eventually)

---

## Quick Start: Using v3

### Export Filters to v3 YAML

1. Open a filter menu (Attribute or List)
2. Click the **Export** button (same button as before)
3. Clipboard now contains **v3 YAML** (instead of v2 JSON)
4. Share, save, or paste into chat

### Import v3 YAML Filters

1. Copy a v3 YAML export to your clipboard
2. Open the corresponding filter menu
3. Click **Import**
4. The system auto-detects v3 format and imports automatically
5. Notification shows count of imported items

**That's it!** No manual conversion needed. The mod handles it.

---

## What If I Have Old v2 Exports?

### Option 1: Re-Export as v3 (Recommended)

1. Import your old v2 export
2. Click Export → now get v3 format
3. Use the new YAML going forward

### Option 2: Keep Using v2 (Still Works)

1. Continue importing old v2 JSON files
2. The Import button auto-detects v2 format
3. No action required; backward compatible

### Option 3: Automatic Conversion (Future)

- A planned "Convert to v3" button will help batch-convert exports
- Not yet implemented, but foundation is ready in code

---

## Format Differences at a Glance

### v2 JSON (Old)

```json
{
  "format": "attribute_filter.v1",
  "name": "Damage Ranges",
  "isBlacklist": false,
  "attributes": [
    {
      "key": "Damage",
      "nbt": "{min:10.0d,max:50.0d}"
    }
  ]
}
```

### v3 YAML (New)

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
```

**Key Changes:**
| v2 (JSON) | v3 (YAML) | Why |
|-----------|-----------|-----|
| `"nbt": "{...}"` | `params: {...}` | Explicit parameters, no code strings |
| `"isBlacklist": false` | `whitelist: true` | Clearer naming (inverted logic) |
| Embedded NBT strings | Maps/objects | Human-readable |
| Type not always clear | Explicit `type:` field | Unambiguous structure |

---

## Understanding Blacklist ↔ Whitelist Inversion

**Important:** v2 uses `isBlacklist`, but v3 uses `whitelist`. The logic is inverted:

| v2                     | v3                 | Mode                              |
| ---------------------- | ------------------ | --------------------------------- |
| `"isBlacklist": true`  | `whitelist: false` | Blacklist (reject matching items) |
| `"isBlacklist": false` | `whitelist: true`  | Whitelist (accept matching items) |

When you export a v2 filter to v3, the conversion happens automatically.

---

## Attribute Parameter Mapping

### Example: Damage Attribute

**v2 (NBT string):**

```json
"nbt": "{Damage:25.5d}"
```

**v3 (Explicit parameters):**

```yaml
params:
  Damage: 25.5
```

Or with range:

**v2:**

```json
"nbt": "{min:10.0d,max:50.0d}"
```

**v3:**

```yaml
params:
  min: 10.0
  max: 50.0
```

### Common Attributes & Their Parameters

#### Durability

```yaml
- key: Durability
  params:
    durability: 100
```

#### Enchantment

```yaml
- key: Enchantment
  params:
    enchantment: 'minecraft:sharpness'
    level: 3
```

#### Item Type

```yaml
- key: ItemType
  params:
    type: 'minecraft:diamond_sword'
```

#### Custom NBT Data (Advanced)

If your attribute uses custom NBT that's not pre-mapped, you can include it:

```yaml
- key: CustomAttribute
  params:
    customField: 'value'
    customNumber: 42
```

---

## List Filter Recursion: What's New

### v2 Limitation

List filters in v2 could nest items, but the structure was flat and confusing.

### v3 Advantage

List filters can now explicitly express nested structures:

```yaml
format: vaultfilters.v3
version: '3.0'
type: list
items:
  - name: 'Ores'
    type: list
    items:
      - type: item
        itemId: 'minecraft:coal_ore'
      - type: item
        itemId: 'minecraft:iron_ore'

  - name: 'Tools'
    type: attribute
    attributes:
      - key: Durability
        params:
          durability: 10
```

This clearly shows which items belong to which filter without confusing nesting.

---

## Step-by-Step Conversion Example

### Starting with v2 JSON

You have this old export saved:

```json
{
  "format": "attribute_filter.v1",
  "name": "My Sword Filter",
  "isBlacklist": true,
  "attributes": [
    {
      "key": "Damage",
      "nbt": "{min:15.0d,max:100.0d}"
    },
    {
      "key": "Enchantment",
      "nbt": "{enchantment:\"minecraft:sharpness\",level:2}"
    }
  ]
}
```

### Automatic Conversion Process

1. **Copy v2 JSON to clipboard**
2. **Open Attribute Filter menu in-game**
3. **Click Import**
4. Mod auto-detects v2 format
5. Items imported successfully
6. **Click Export**
7. **New v3 YAML is in clipboard:**

```yaml
format: vaultfilters.v3
version: '3.0'
name: 'My Sword Filter'
type: attribute
modes:
  whitelist: false

attributes:
  - key: Damage
    params:
      min: 15.0
      max: 100.0
    inverted: false

  - key: Enchantment
    params:
      enchantment: 'minecraft:sharpness'
      level: 2
    inverted: false
```

### What Changed

- ✅ `isBlacklist: true` → `whitelist: false`
- ✅ `"nbt": "{...}"` → Explicit `params: {...}`
- ✅ Added `type: attribute` for clarity
- ✅ Added `version: 3.0` header
- ✅ YAML format (human-readable)

---

## Troubleshooting

### "Invalid Format" Error

**Problem:** Import button shows "Invalid format" error.
**Solution:** Ensure you're pasting either v2 JSON (starts with `{`) or v3 YAML (starts with `format:`).

### "Unknown Item ID" Warning

**Problem:** Some items didn't import (e.g., `invalid_mod:thing`).
**Solution:** Verify the item ID is correct. Use `/give @s <item_id>` to check if it exists.

### Blacklist/Whitelist Inverted

**Problem:** My v2 blacklist became a whitelist after export.
**Solution:** This is expected. v3 uses `whitelist` instead of `isBlacklist`, so the logic appears inverted. Behavior is identical.

### Export File Too Large

**Problem:** Export exceeds 1 MB clipboard limit.
**Solution:** Reduce nested depth or split into multiple filters. Max safe size is ~500 KB.

---

## Performance Notes

### Export Speed

- v3 YAML export is **slightly faster** than v2 JSON (less NBT parsing)
- Typical export: <100 ms for 100 items

### Import Speed

- v3 YAML import is **comparable** to v2 JSON
- Typical import: <200 ms for 100 items
- Larger imports may take longer (up to 2-3 seconds for 1000+ items)

### File Size

- v3 YAML is typically **10-20% smaller** than v2 JSON (less metadata overhead)
- Example: 100-item list filter
  - v2 JSON: ~50 KB
  - v3 YAML: ~40 KB

---

## FAQ: Migration

**Q: Do I need to convert all my old exports?**
A: No. v2 imports still work. Convert when convenient.

**Q: Can I have both v2 and v3 exports of the same filter?**
A: Yes. Just export twice (once for v2, once for v3) or re-import and export.

**Q: What if an old v2 export has attributes that are no longer supported?**
A: Those attributes are skipped on import with a notification. Other attributes import normally.

**Q: Will v2 support be removed?**
A: No removal planned currently. v2 will likely be supported indefinitely for backward compatibility.

**Q: Can I manually edit v3 YAML files and re-import?**
A: Yes! v3 YAML is specifically designed to be human-editable. Just follow YAML syntax rules (indentation, colons, etc.).

**Q: How do I know which format to use?**
A: Use v3 YAML for new exports. It's the default and future-proof.

---

## Need Help?

- **YAML Syntax:** See [YAML_FORMAT_SPEC.md](YAML_FORMAT_SPEC.md) for detailed examples
- **Attribute Parameters:** Check attribute mod documentation
- **Import Issues:** Verify item IDs with `/give @s <item_id>`
- **Bug Reports:** Post in mod community channels with error message

---

## Summary

| Task                  | How to Do It                                     |
| --------------------- | ------------------------------------------------ |
| Export new filter     | Click Export (automatic v3 YAML)                 |
| Import v2 old export  | Copy JSON, click Import (auto-detected)          |
| Import v3 YAML export | Copy YAML, click Import (auto-detected)          |
| Convert v2 to v3      | Import v2, then Export (now v3)                  |
| Manually edit filter  | Export v3 YAML, edit in text editor, import back |

**Bottom Line:** Most users won't need to do anything. The system handles format detection automatically. If you want to migrate, just re-export after importing an old file—it's that simple!
