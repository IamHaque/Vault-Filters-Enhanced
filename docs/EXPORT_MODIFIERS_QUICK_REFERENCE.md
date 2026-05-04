# Quick Reference: Export Modifiers

## Export Keyboard Modifiers

**Click the Export button with these keys:**

| Keyboard           | Format      | Size   | Use Case                          |
| ------------------ | ----------- | ------ | --------------------------------- |
| **None** (default) | Simplified  | Medium | ⭐ **Use this** - Human readable  |
| **Shift**          | V2 Pretty   | Medium | Standard format with pretty print |
| **Shift+Ctrl**     | V2 Minified | Small  | Compact, single-line JSON         |
| **Alt**            | V1 Legacy   | Medium | Legacy compatibility              |

## Import

| Keyboard           | Action                            |
| ------------------ | --------------------------------- |
| **None** (default) | Import, replace current filter    |
| **Shift**          | Import, merge with current filter |

## Export Tree

| Button          | Result                                  |
| --------------- | --------------------------------------- |
| **Export Tree** | Human-readable tree format to clipboard |

## Format Comparison

```
Simplified (Default)  ← Best for humans, external tools, documentation
├─ Type: Explicit
├─ Format ID: Clear identifier
└─ Readability: ⭐⭐⭐⭐⭐

V2 Pretty (Shift)
├─ Type: Implicit
├─ Format ID: "v2"
└─ Readability: ⭐⭐⭐⭐

V2 Minified (Shift+Ctrl)
├─ Type: Single-line
├─ Compact size
└─ Readability: ⭐⭐⭐

V1 Legacy (Alt)
├─ Type: Implicit
├─ Oldest format
└─ Readability: ⭐⭐
```

## All Formats Are Importable ✅

No matter which format you export in, you can always import it back. The system auto-detects the format.
