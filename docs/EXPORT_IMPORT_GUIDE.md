# Export & Import Guide

This guide explains how to export and import filters in Vault Filters Enhanced, including the different export formats and keyboard modifiers.

## Export Format Overview

Vault Filters Enhanced supports multiple export formats, each optimized for different use cases. You can switch between formats using keyboard modifiers when clicking the **Export** button.

### Default Export (No Modifier) - **Simplified Format** ⭐ RECOMMENDED

**Keyboard:** Just click Export (no modifier keys)

**Format ID:** `vaultfilters.list_filter.simplified` or `vaultfilters.attribute_filter.simplified`

**Best For:** Human-readable exports, sharing with others, documentation, external tools

**Characteristics:**

- Clear, explicit type fields (`"type": "attribute_filter"`, `"type": "list_filter"`)
- Includes NBT data for complete round-trip compatibility
- Designed for external tools to parse and display beautifully
- Pretty-printed JSON format for easy reading
- Preserves all semantic information needed for re-import

**Example:**

```json
{
  "format": "vaultfilters.list_filter.simplified",
  "name": "My Awesome Filter",
  "filter": {
    "isBlacklist": false,
    "shouldRespectNBT": false,
    "matchAll": true,
    "items": [
      {
        "type": "attribute_filter",
        "nbt": "{...}",
        "inverted": false
      }
    ]
  }
}
```

### V2 Format (Shift) - **Pretty V2**

**Keyboard:** Hold **Shift** and click Export

**Format ID:** `vaultfilters.list_filter.v2` or `vaultfilters.attribute_filter.v2`

**Best For:** General use before simplified format was introduced, maximum compatibility

**Characteristics:**

- Standard V2 format (previously the default)
- Pretty-printed JSON with readable formatting
- Includes all structural information and NBT data
- Fully compatible with imports

### V2 Format (Shift+Ctrl) - **Minified V2**

**Keyboard:** Hold **Shift + Ctrl** and click Export

**Format ID:** `vaultfilters.list_filter.v2` or `vaultfilters.attribute_filter.v2`

**Best For:** Compact size, minimal output, clipboard sharing

**Characteristics:**

- V2 format without pretty-printing
- Compact single-line JSON (minified)
- Smaller file size for clipboard/sharing
- Still fully importable

**Example:**

```json
{
  "format": "vaultfilters.list_filter.v2",
  "name": "Filter",
  "filter": {
    "isBlacklist": false,
    "shouldRespectNBT": false,
    "matchAll": true,
    "items": [{ "type": "attribute_filter", "nbt": "{...}" }]
  }
}
```

### V1 Format (Alt) - **Legacy**

**Keyboard:** Hold **Alt** and click Export

**Format ID:** `vaultfilters.list_filter.v1` or `vaultfilters.attribute_filter.v1`

**Best For:** Legacy compatibility with very old exports

**Characteristics:**

- Original format from earlier Vault Filters versions
- Contains all necessary data but less structured than V2/Simplified
- Still fully compatible with imports
- Not recommended for new exports

---

## Import - All Formats Supported ✅

Vault Filters Enhanced automatically detects the format of imported JSON and handles it correctly, whether it's:

- ✅ Simplified Format
- ✅ V2 Format (Pretty or Minified)
- ✅ V1 Format (Legacy)
- ✅ Mix of formats

**How to Import:**

1. Copy your filter JSON to clipboard
2. Click the **Import** button in the filter screen
3. The system automatically detects and imports the correct format

### Import Merge Option

Hold **Shift** when clicking Import to **merge** filters instead of replacing them:

- Without Shift: Replaces current filter contents
- With Shift: Adds imported filters to existing ones

---

## Export Modifier Cheat Sheet

| What You Want           | Keyboard   | Format      | File Size | Readability |
| ----------------------- | ---------- | ----------- | --------- | ----------- |
| **Recommended default** | None       | Simplified  | Medium    | ⭐⭐⭐⭐⭐  |
| Standard format         | Shift      | V2 Pretty   | Medium    | ⭐⭐⭐⭐    |
| Compact format          | Shift+Ctrl | V2 Minified | Small     | ⭐⭐⭐      |
| Legacy/Old              | Alt        | V1 Legacy   | Medium    | ⭐⭐        |

---

## Export Buttons Summary

### Attribute Filters & List Filters both support:

1. **Export** Button
   - Default (no keys): Simplified JSON → Clipboard
   - Shift: V2 Pretty JSON → Clipboard
   - Shift+Ctrl: V2 Minified JSON → Clipboard
   - Alt: V1 Legacy JSON → Clipboard

2. **Import** Button
   - Default (no keys): Import from clipboard, replace current filter
   - Shift: Import from clipboard, merge with current filter

3. **Export Tree** Button
   - Exports human-readable tree format
   - Useful for documentation, editing, roundtrip workflows

---

## Common Workflows

### Workflow 1: Share a Filter with Friends

1. Click **Export** (no modifier) to get Simplified format
2. Copy the JSON output
3. Send it to your friend
4. They click **Import** and paste it in
   ✅ Works perfectly, format auto-detected

### Workflow 2: Compact Storage

1. Click **Export** and hold **Shift+Ctrl** (minified)
2. Smaller JSON output
3. Store or share the compact format
   ✅ Still fully compatible on import

### Workflow 3: Document a Filter

1. Click **Export** (no modifier) for Simplified format
2. Pretty-printed JSON is easy to read and document
3. Share as part of documentation
   ✅ Clear structure for external tools

### Workflow 4: Legacy System Integration

1. Click **Export** and hold **Alt** for V1 Legacy format
2. Use with older system compatibility
   ✅ Still works, all data preserved

---

## Format Comparison Table

| Feature         | Simplified        | V2           | V1          |
| --------------- | ----------------- | ------------ | ----------- |
| Format ID       | ✅ Explicit       | ✅           | ✅          |
| Type Fields     | ✅ Clear `"type"` | ✅           | Implicit    |
| NBT Data        | ✅ Included       | ✅ Included  | ✅ Included |
| Pretty Print    | Default           | Optional     | N/A         |
| Import Support  | ✅                | ✅           | ✅          |
| Readability     | ⭐⭐⭐⭐⭐        | ⭐⭐⭐⭐     | ⭐⭐        |
| Backward Compat | ✅ (from V2)      | ✅ (from V1) | Legacy      |

---

## Technical Details

### Format Detection

During import, the system checks:

1. The `"format"` field in the JSON root
2. Field names and structure patterns
3. Applies appropriate parser for that format version

### Backward Compatibility

✅ All new simplified exports can be imported by older code if it supports V2
✅ All existing V1/V2 exports continue to work unchanged
✅ No breaking changes to existing workflows
✅ Future formats can be added without breaking current imports

### What Gets Preserved

- Filter name
- Mode settings (Allow/Deny, Any/All)
- Respect NBT flag (list filters)
- All filter criteria and conditions
- Inversion flags on attributes
- Nested filter structure

---

## Troubleshooting

**Q: I exported in Simplified format but got an error on import**

- A: Make sure you're pasting into the correct filter type (list or attribute). Simplified exports are also fully compatible with any system that reads V2 or V1.

**Q: Which format should I use?**

- A: Use the **default (no modifier)** Simplified format. It's human-readable and designed for modern workflows.

**Q: Are older exports still supported?**

- A: Yes! All V1 and V2 exports continue to work. The import system auto-detects the format.

**Q: What if I need to share compact JSON?**

- A: Use **Shift+Ctrl** (minified V2) to get a single-line compact format.
