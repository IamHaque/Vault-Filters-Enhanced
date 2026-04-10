# Filter Tree Roundtrip Playbook

Purpose

- Preserve the workflow for rebuilding a valid importable filter JSON from:
  1. original JSON export,
  2. original tree export,
  3. edited tree export.

Scope

- Applies to list filter payloads exported by Vault Filters Enhanced.
- Works with V2 and legacy payloads. Preferred output is V2.
- Tree export is available in both List Filter and Attribute Filter UIs.
- This playbook is focused on list filter JSON roundtrip generation.

Required Inputs

1. Original JSON export (source of truth for exact machine values).
2. Original tree export of the same filter (human-readable baseline).
3. Edited tree export (requested changes).

Core Principle

- Never invent low-level NBT when original JSON already contains the value.
- Use the original JSON node as the base object, then apply edits inferred from tree diff.

Tree Grammar Contract (expected from Tree export button)

- Root line:
  - Title (ModeLabel[, Respect NBT])
- List node line:
  - - Title (ModeLabel[, Respect NBT])
- Attribute node line:
  - - Title
  - - key = value
  - - NOT key = value

Where ModeLabel is one of:

- Allow All
- Allow Any
- Deny All
- Deny Any

Mode Mapping

- Allow All -> isBlacklist=false, matchAll=true
- Allow Any -> isBlacklist=false, matchAll=false
- Deny All -> isBlacklist=true, matchAll=true
- Deny Any -> isBlacklist=true, matchAll=false

Roundtrip Algorithm

1. Parse original JSON into node tree.
2. Parse original tree into node tree.
3. Parse edited tree into node tree.
4. Match edited tree nodes to original JSON nodes using this priority:
   - Exact path + title + node type.
   - Exact sibling index + title + node type.
   - Fallback title-only match inside same parent.
5. For each matched node, apply updates:
   - Title changes -> update node name field and stack hover name in nbt if present.
   - Mode changes on list nodes -> update isBlacklist and matchAll.
   - Respect NBT toggle on list nodes -> update shouldRespectNBT.
   - Attribute condition edits:
     - remove missing conditions,
     - add new conditions,
     - update existing values,
     - preserve inversion via NOT prefix.
6. Keep untouched fields as-is from original JSON to avoid data loss.
7. Serialize to valid output format (recommended V2).
8. Validate with checklist below.

Condition Parsing Rules

- Condition key is the attribute id text after optional NOT prefix.
- Condition value is everything after " = ".
- Current Tree export normalizes values to avoid duplicated keys, e.g.:
  - `gear_rarity = Unique` (preferred current output)
  - not `gear_rarity = gear_rarity=Unique`.
- Import/roundtrip tooling should remain tolerant of both styles when reading edited trees:
  - normalized value (`Unique`), and
  - legacy duplicated prefix (`gear_rarity=Unique`).
- NOT prefix means Inverted=true, otherwise false.

Ambiguity Rules

- If multiple nodes share identical title/type under same parent, use sibling order.
- If still ambiguous, require path markers in edited tree before applying.
- Suggested marker format for future edits:
  - [index] Title (Mode)
  - Example: [2] Offensive Stats (Allow All)

Naming Rules (recommended for stable roundtrip)

- Always give every list filter and attribute filter a specific, meaningful name.
- Avoid default names such as List Filter and Attribute Filter in exported/edited trees.
- Prefer unique names among siblings under the same parent to reduce matcher ambiguity.
- If defaults already exist in a tree, rename them before editing conditions or moving branches.

Validation Checklist (must pass)

1. Output is valid JSON.
2. Root contains format, filter, and filter.items array.
3. Every item has type.
4. list_filter nodes keep nested items arrays when applicable.
5. Attribute/item filters contain valid nbt payload.
6. Edited mode/title/conditions are present in output.
7. Unedited branches remain unchanged from original JSON.
8. Import test in-game succeeds.

Recommended User Workflow

1. Export JSON.
2. Export Tree.
3. Edit Tree only.
4. Provide all three artifacts.
5. Generate new JSON via this playbook.
6. Import JSON back in-game.

Failure Handling

- If edited tree removes a parent but keeps children, remove entire subtree unless explicitly remapped.
- If edited tree introduces unknown attribute key not present in any original node, keep as requested but flag for user review.
- If a value cannot be converted back to expected NBT-like value, preserve as string and report the node path.

Notes

- Original JSON is always the canonical source for fields not represented in tree text.
- Tree export is intended for high-level edits, not full fidelity machine serialization.
