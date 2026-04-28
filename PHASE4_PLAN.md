# Phase 4: Backward Compatibility & Format Migration Plan

## Current State

- ✅ Export buttons → v3 YAML (default)
- ✅ Import buttons → auto-detect v2 JSON OR v3 YAML
- ✅ Both attribute & list filters support v3
- ⚠️ v1/v2 compatibility layer exists but not formalized

## Phase 4 Objectives

### 4.1 Format Auto-Detection Framework

**Goal:** Explicit versioning and format detection for safer migrations

**Files to create/modify:**

- `FilterVersionDetector.java` — Detect format version (v1, v2, v3) from raw export string
- Update `FilterImportUtilsV3.java` — Add version detection in parseYaml()
- Update import buttons → Use version detector before routing to correct parser

**Detection Logic:**

```
if starts with "format: vaultfilters.v3" → v3 YAML (FilterImportUtilsV3)
else if starts with "{" → v2 JSON (existing importFromClipboard)
else if contains specific v1 markers → v1 (not yet supported, but detect)
else → invalid
```

### 4.2 v2→v3 Migration Utility

**Goal:** Help users convert old v2 exports to v3 format

**Files to create:**

- `FilterV2ToV3Converter.java` — Parse v2 JSON → FilterV3Data
- Optional: UI button "Convert to v3" (future enhancement)

### 4.3 Edge Case Handling

**Goal:** Graceful degradation for malformed or incomplete exports

**Add validation for:**

- Missing item IDs in v3 items
- Invalid attribute keys (items/attributes that no longer exist)
- Corrupted YAML structure
- Oversized exports (already have MAX_IMPORT_CHARS check)

### 4.4 Migration Documentation

**Goal:** User-facing docs on what changed and how to migrate

**Create:**

- `MIGRATION_V2_TO_V3.md` — Migration guide (optional, can be in changelog)
- Examples of v2 vs v3 format
- FAQ for common migration issues

## Implementation Order

1. **4.1a** Create `FilterVersionDetector.java` with basic detection
2. **4.1b** Update import flows to use detector
3. **4.2** Create `FilterV2ToV3Converter.java` for migration
4. **4.3** Add validation checks in import methods
5. **4.4** Document in README/changelog

## Success Criteria

- [ ] v2 JSON imports still work (backward compatible)
- [ ] v3 YAML imports work
- [ ] Clear error messages for unsupported/malformed formats
- [ ] (Optional) v2→v3 conversion available to users
- [ ] No data loss in format detection/routing

## Timeline Estimate

- 4.1: 1-2 hours (detection framework)
- 4.2: 1 hour (converter)
- 4.3: 1 hour (validation)
- 4.4: 30 min (docs)
- **Total: 3.5-4.5 hours**
