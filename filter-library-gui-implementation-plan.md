# Implementation Plan: "Filter Library" GUI for Vault-Filters-Enhanced

This document is written to be handed directly to a coding agent (Claude Code, Cursor,
etc.) working inside a clone of `https://github.com/IamHaque/Vault-Filters-Enhanced`.
It captures the codebase analysis already done, the target design, and an ordered task
list. A ready-to-paste condensed prompt is at the very end if you just want to kick the
agent off quickly — but the agent should still read the full plan for context.

---

## 1. What we're building

Today, Export/Import only round-trip through the OS clipboard (`Ctrl+C`/`Ctrl+V`-style,
one filter at a time, nothing persisted). We want a **persistent, in-game library** of
saved Attribute Filters and List Filters:

- A **"Save"** action in the existing filter GUIs that writes the current filter to a
  JSON file on disk (in addition to, not instead of, the existing clipboard Export).
- A new **"Filter Library"** screen, opened from a button on the filter GUIs (and
  optionally a keybind), that lists every saved filter and lets you **Import
  (replace/merge), Rename, Duplicate, Export-to-clipboard, view as Tree, and Delete**
  each one.
- Visual style consistent with the Create-based filter GUIs Vault Hunters players
  already know ("VH-like"), following the precedent set by
  `https://github.com/massuus/vault-party-ui` for building a bespoke, VH-flavored
  screen on this exact mod stack.

## 2. Verified codebase facts (do not re-derive, but do re-check if the repo has moved on)

**Stack:** Minecraft `1.18.2`, Forge `40.x` (`loaderVersion="[40,)"`), Create
`0.5.1.i-435`, Java 17 (`compatibilityLevel: JAVA_17` in mixin configs). This means:

- Rendering uses `PoseStack` + `GuiComponent.blit`/`GuiComponent.fill`, **not** the
  1.20+ `GuiGraphics` API.
- Text components are `net.minecraft.network.chat.TranslatableComponent` /
  `Component.literal`-style `Components.literal(...)` from Create's utility class, not
  `Component.translatable(...)` (that's 1.19.4+).
- Mixin classes use `com.llamalad7.mixinextras` (already a dependency) for
  `@WrapOperation` etc.

**Relevant existing files (fork: `IamHaque/Vault-Filters-Enhanced`, package root
`net.joseph.vaultfilters`):**

| File                                                         | Role                                                                                                                                                                                                                                                                                                                                    |
| ------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `mixin/compat/create/MixinAttributeFilterScreen.java`        | Mixes into Create's `AttributeFilterScreen`. Adds the Tree/Export/Import/Export-Available button row (`init` injection at `TAIL`), clipboard export (`vault_Filters$exportToClipboard`), clipboard import (`vault_Filters$importFromClipboard`), tree export, and the hold-`DEL`-to-remove UX for selected attributes.                  |
| `mixin/compat/create/MixinFilterScreen.java`                 | Same idea, for Create's `FilterScreen` (the **List Filter** GUI). Has its own Tree/Export/Import buttons and JSON building for the nested `list_filter.v2` format.                                                                                                                                                                      |
| `util/FilterUiUtils.java`                                    | Shared helpers: `GSON`/`PRETTY_GSON` instances, `MAX_IMPORT_CHARS = 262_144`, format constants (`ATTRIBUTE_FORMAT_V1`, `LIST_FORMAT_V2`, `LIST_FORMAT_V1`), `notifyUser(Component)`, filter name get/set helpers, NBT summarization helpers used by the Tree view. **This is the natural home for new shared library-payload helpers.** |
| `textures/VFGuiTextures.java`                                | Example of the mod's existing custom-texture pattern (`ScreenElement`, `RenderSystem.setShaderTexture`, `GuiComponent.blit`) — follow this pattern for any new bespoke textures.                                                                                                                                                        |
| `network/MenuFeaturesPacket.java`, `network/VFMessages.java` | Existing client→server packet infra (used e.g. to rename a filter's display name via `MenuFeaturesPacket.MenuAction.CHANGE_NAME`). Importing a saved filter re-uses the **same** attribute-add packets the clipboard import already sends — no new networking should be required for this feature (see §6).                             |
| `src/main/resources/mixins.vaultfilters.create.json`         | Mixin registration file. Both `MixinAttributeFilterScreen` and `MixinFilterScreen` are already registered under `"client"` — editing them further requires **no new mixin registration**.                                                                                                                                               |
| `src/main/resources/assets/vaultfilters/lang/en_us.json`     | Existing translation keys follow `vaultfilters.gui.attribute_filter.*` / `create.item_attributes.*` conventions.                                                                                                                                                                                                                        |
| `docs/FILTER_TREE_ROUNDTRIP_PLAYBOOK.md`                     | Existing doc on the Tree format — read it before touching tree rendering.                                                                                                                                                                                                                                                               |

**Canonical payload formats (already shipped, do not redesign — wrap them instead):**

```json
// Attribute Filter export (vaultfilters.attribute_filter.v1)
{
  "format": "vaultfilters.attribute_filter.v1",
  "isBlacklist": false,
  "name": "My Gear Attr Filter",
  "attributes": [{ "inverted": false, "nbt": "{level:0}" }]
}
```

```json
// List Filter export (vaultfilters.list_filter.v2)
{
  "format": "vaultfilters.list_filter.v2",
  "name": "My Master Filter",
  "filter": {
    "isBlacklist": false,
    "shouldRespectNBT": false,
    "matchAll": true,
    "items": [
      { "type": "attribute_filter", "nbt": "..." },
      { "type": "list_filter", "items": [] }
    ]
  }
}
```

**Precedent for a bespoke, persisted, VH-styled standalone screen on this exact
stack:** `massuus/vault-party-ui` (MC 1.18.2, Forge 40.x, client-side Forge mod). Two
patterns from it are worth reusing directly:

1. **Client-side JSON/flat-file persistence in the Forge config dir**
   (`ClientFavoritePlayers.java`): resolves its file via
   `net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("<filename>")`,
   loads lazily on first access, writes on every mutation, wrapped in try/catch so a
   corrupt/missing file never crashes the client.
2. **A plain `Screen` (not menu-backed) pushed on top of another screen and returned
   to on close** (`VoiceGroupCreateScreen.java`): constructor takes a
   `@Nullable Screen parentScreen`, and `onClose()` calls
   `Minecraft.getInstance().setScreen(this.parentScreen)`. This is exactly the shape
   we want for "Library" opening from a filter screen and returning to it.

## 3. Scope

**In scope:**

- Persistent local storage of saved Attribute Filters and List Filters (client-side).
- Save (new) / Update (overwrite an existing saved entry) from both filter GUIs.
- A Filter Library screen: list, search, sort, Import (replace or merge), Rename,
  Duplicate, per-entry Export-to-clipboard, per-entry Tree view, Delete with
  confirmation.
- VH/Create-consistent visual styling.

**Out of scope (flag to the user, don't silently build):**

- Syncing the library between machines/players — this is local client storage only,
  same as clipboard export today. Cross-player sharing still happens via the existing
  clipboard Export/Import (a library entry can always be exported to clipboard to
  share).
- Server-side storage or per-server libraries. Everything lives in the Forge config
  dir and is the same regardless of which world/server you're on.
- Touching the underlying attribute-matching logic, mixin compat layers (RS/AE2/
  Modular Routers/Sophisticated Backpacks), or the recipe/crafting changes. None of
  that needs to change for this feature.

## 4. Data model & storage design

**Location:** `FMLPaths.CONFIGDIR.get().resolve("vaultfilters").resolve("saved_filters.json")`
(mirrors `ClientFavoritePlayers`'s pattern but in its own subfolder so future
per-feature files — e.g. client settings — have a home without cluttering
`config/`).

**Format — single file, one JSON array, wrapped envelope so it can be format-versioned
independently of the two existing payload formats it contains:**

```json
{
  "format": "vaultfilters.library.v1",
  "entries": [
    {
      "id": "6a2b7e2e-6c1a-4d1b-9a2e-6a2b7e2e6c1a",
      "type": "attribute_filter",
      "name": "Omega Gear",
      "createdAt": 1751980800000,
      "updatedAt": 1751980800000,
      "payload": { "format": "vaultfilters.attribute_filter.v1", "...": "..." }
    },
    {
      "id": "b1c9...",
      "type": "list_filter",
      "name": "Master Sort",
      "createdAt": 1751981000000,
      "updatedAt": 1751981500000,
      "payload": { "format": "vaultfilters.list_filter.v2", "...": "..." }
    }
  ]
}
```

`payload` is **byte-for-byte the same JsonObject the existing Export button already
builds** — see §5's refactor step. This is the key design decision: the library is a
thin persistence/metadata layer on top of code that already exists and is tested by
the current clipboard flow; it should not reimplement serialization.

**Single file vs. one-file-per-entry:** recommend a **single file**. Rationale: entry
counts will realistically be in the dozens, not thousands; a single file means one
atomic write, trivial listing (no directory scan), and a simple manual-backup story
for players ("just copy this one file"). Revisit only if players report needing
hundreds+ of entries.

**Write safety:** write to `saved_filters.json.tmp` in the same directory, then
`Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)`
(fall back to non-atomic move if `ATOMIC_MOVE` isn't supported on the filesystem).
Never partially write the live file.

**Caps & validation (mirror the existing clipboard-import posture in
`MixinAttributeFilterScreen`/`MixinFilterScreen`, i.e. "invalid entries are ignored
and reported, not fatal"):**

- Reuse `FilterUiUtils.MAX_IMPORT_CHARS` (262,144) as the max serialized size of a
  single entry's `payload`.
- Add a max entry count constant (suggest `MAX_LIBRARY_ENTRIES = 500`) — enforced on
  Save with a clear `notifyUser` message, not a hard crash.
- On load, parse defensively: unknown `format` at the envelope level → treat as
  empty library and log a warning rather than deleting the file (don't destroy user
  data on a format the client doesn't understand yet); a malformed individual entry
  is skipped and counted, with a summary notification ("Loaded 42 filters, skipped 2
  invalid entries") the first time the Library screen is opened after such a load.
- Name field: trim, cap length at 35 chars (matches the existing cap in
  `FilterUiUtils.applyImportedFilterName`), reject empty names in the Save dialog.

## 5. Code architecture — new & modified files

### New package `net.joseph.vaultfilters.library`

- **`SavedFilterType.java`** — enum `ATTRIBUTE_FILTER`, `LIST_FILTER` with the exact
  string ids used in the JSON (`attribute_filter`, `list_filter` — reuse the same
  strings the list-filter nested-items format already uses, don't invent new ones).
- **`SavedFilter.java`** — immutable-ish POJO: `id`, `type`, `name`, `createdAt`,
  `updatedAt`, `payload` (`JsonObject`). Simple `withName(...)`, `withPayload(...)`,
  `touch()` (bumps `updatedAt`) helpers.
- **`FilterLibraryStore.java`** — static-ish manager class, same shape as
  `ClientFavoritePlayers`: lazy `ensureLoaded()`, in-memory `LinkedHashMap<String,
SavedFilter>`, `list()`/`listByType(SavedFilterType)` (sorted, see below),
  `get(id)`, `upsert(SavedFilter)`, `delete(id)`, `rename(id, newName)`,
  `duplicate(id)`, `save()` (atomic write as above), `existsByName(type, name,
excludingId)` for a friendly "a filter with this name already exists — overwrite?"
  check in the Save dialog.

### Refactor (do this **before** building new UI, as a low-risk isolated step)

Both mixins currently build the export `JsonObject` and apply an imported
`JsonObject` inline, mixed together with the clipboard read/write calls. Extract the
pure data logic so it can be reused by the new "Save to Library" and "Import from
Library" actions without duplicating it:

- In `FilterUiUtils` (or a new `util/FilterPayloads.java` if `FilterUiUtils` gets too
  large), add:
  - `JsonObject buildAttributeFilterPayload(AttributeFilterMenu/menu-derived args)` —
    the exact body currently inside `vault_Filters$exportToClipboard()` in
    `MixinAttributeFilterScreen`, minus the `Minecraft.getInstance().keyboardHandler.setClipboard(...)`
    call.
  - `ImportResult applyAttributeFilterPayload(JsonObject root, boolean merge, ...)` —
    the exact body currently inside `vault_Filters$importFromClipboard()`, minus the
    clipboard-read and the `MAX_IMPORT_CHARS` length check (that check is
    clipboard-specific; loading from the library file should validate size at
    load-time instead, per §4).
  - Equivalent `buildListFilterPayload(...)` / `applyListFilterPayload(...)` pulled
    out of `MixinFilterScreen` the same way.
- Re-point the existing clipboard export/import methods in both mixins to call these
  extracted helpers, so behavior is provably unchanged (this refactor should be
  covered by manually re-testing the existing Export→Import round trip before moving
  on — regression safety for a mod players already rely on).
- **Why this matters:** once this refactor is done, "Save" is just
  `FilterLibraryStore.upsert(new SavedFilter(..., FilterUiUtils.buildAttributeFilterPayload(...)))`,
  and "Import from Library" is just
  `FilterUiUtils.applyAttributeFilterPayload(entry.payload(), merge)` — no new
  parsing/serialization code paths to get subtly wrong.

### New Screen: `client/screen/FilterLibraryScreen.java`

Plain `net.minecraft.client.gui.screens.Screen` (not container-backed), following the
`VoiceGroupCreateScreen` precedent from vault-party-ui:

```java
public class FilterLibraryScreen extends Screen {
    private final Screen parentScreen;                 // returned to on close
    private final SavedFilterType filterTypeForImport;  // which type this instance can import
    private final BiConsumer<JsonObject, Boolean> onImport; // (payload, merge) -> apply to parent
    ...
    @Override public void onClose() {
        Minecraft.getInstance().setScreen(this.parentScreen);
    }
}
```

- Opened by a new **"Library"** button injected into both `MixinAttributeFilterScreen`
  and `MixinFilterScreen`'s `init` (same injection point as the existing button row).
  Constructed with `parentScreen = this`, `filterTypeForImport =
SavedFilterType.ATTRIBUTE_FILTER` (or `LIST_FILTER`), and `onImport` wired to call
  the newly-extracted `applyAttributeFilterPayload`/`applyListFilterPayload` helper on
  the originating mixin screen instance.
- Optional stretch: also register a keybinding (`ClientKeyMappings`-style, see
  `vault-party-ui/client/ClientKeyMappings.java` for the exact registration pattern)
  to open the library with `parentScreen = null`, `onImport = null` — a
  browse/manage-only mode (Import button disabled with a tooltip like "Open a filter
  first to import") for players who just want to review/prune/rename/delete without
  having a filter item open.
- **Container-desync note to verify during implementation:** pushing a plain `Screen`
  over an `AbstractContainerScreen` via `setScreen` does not itself send a
  container-close packet (that only happens via `Screen.onClose()` on the _original_
  container screen, which we're not calling). Returning to the parent screen should
  be safe, but **test it explicitly**: open Library, sit on it a few seconds, close
  it, confirm the underlying filter menu's slot/contents are still correct and the
  server hasn't force-closed the container.

### List rendering

Use vanilla `net.minecraft.client.gui.components.ObjectSelectionList` (1.18.2's
scrollable-list base widget) subclassed for the rows, rather than hand-rolling
scroll math — it gives scrollbar, mouse-wheel, and click handling for free and is the
idiomatic MC 1.18.2 way to do this. (`vault-party-ui`'s `PartyPanelRenderer` hand-rolls
its own scrolling instead; that's a valid alternative if more visual control over row
layout is needed later, but `ObjectSelectionList` is the more direct first
implementation.)

Each row shows: type indicator (small "A" or "L" badge, or an icon), name, relative
timestamp ("2 days ago"), and a compact set of icon buttons — Import, Rename,
Duplicate, Export, Tree, Delete. Follow `VFGuiTextures`'s `ScreenElement` pattern if
new icon textures are added, or reuse Create's `AllIcons` (already used for
`AllIcons.I_WHITELIST_AND`/`I_WHITELIST_OR` in `MixinFilterScreen`) where a
close-enough icon already exists.

### Visual style ("VH-like")

Two complementary techniques, both already used in this codebase / its neighbor:

1. **Reuse Create's existing `AllGuiTextures`** (the same texture set the filter
   screens themselves render with, via the `background` field passed into
   `AbstractFilterScreen`) for the screen's frame/border where it's a fixed size —
   this gets automatic visual parity with the filter GUIs at zero art cost. Note its
   backgrounds are fixed-size crate/slot-grid art, so it's best used for a
   fixed-size **outer frame**, not for an arbitrarily-tall scrolling list.
2. **Flat-fill panel + accent border for the scrollable interior**, following
   `PartyScreenGraphics.drawPanel` in vault-party-ui (`GuiComponent.fill` for a
   translucent dark backing, a 1px accent-color top border) — cheap, no new
   textures, and already proven to read as "in-theme" for a VH-adjacent mod on this
   exact stack. Sample the mod's existing accent usage / Create's brass/gold tones
   for the border color rather than inventing a new palette.

### Lang keys

Add under a new `vaultfilters.gui.library.*` namespace (title, search placeholder,
sort labels, row action tooltips, delete-confirm text, save-dialog title/placeholder,
"overwrite existing filter named X?" prompt, load-skipped-entries notice) plus a
couple of new keys on the existing `vaultfilters.gui.attribute_filter.*` /
equivalent list-filter namespace for the new **Save** / **Library** buttons
themselves, matching the existing key-naming convention in `en_us.json`.

## 6. Interaction details

- **Save button** (next to the existing Tree/Export/Import/Export-Available row —
  will likely need a second row or narrower buttons to fit; check current screen
  width budget in `MixinAttributeFilterScreen`'s `init` injection, which already
  computes `x = leftPos + this.background.width - 236` for 4 buttons at ~42–90px
  each):
  - If the currently-open filter was itself loaded from a library entry this
    session (track via a `@Unique Optional<String> vault_Filters$loadedLibraryId` on
    the mixin screen), clicking Save updates that entry in place (bumps
    `updatedAt`) with a lightweight "Saved" toast via `FilterUiUtils.notifyUser`.
  - Otherwise it opens a small name-entry prompt (an `EditBox`-based mini dialog, cf.
    `VoiceGroupCreateScreen`'s `passwordBox`) prefilled with the filter's current
    display name, then creates a new entry. If the name collides with an existing
    entry of the same type, ask to overwrite (via `FilterLibraryStore.existsByName`)
    rather than silently duplicating.
- **Import from Library:** reuses the exact replace/merge semantics already
  documented for clipboard import (`Shift` = merge mode) — surface this as two
  explicit buttons on the row or on a small per-entry action menu ("Import
  (Replace)" / "Import (Merge)") rather than relying on a modifier key, since this is
  a list of many entries rather than a single Import button — but keep the
  underlying `merge: boolean` parameter identical to the existing clipboard path so
  behavior matches player expectations from the current feature.
- **No new networking is required.** Applying a saved filter's payload goes through
  the same extracted `applyAttributeFilterPayload`/`applyListFilterPayload` helpers
  the clipboard import already uses, which already send the correct
  `FilterScreenPacket`s to the server. The library itself never talks to the server.
- **Delete:** two-step confirm (click → button becomes a red "Confirm Delete?" state
  for a few seconds → second click deletes; reverts automatically if not confirmed)
  rather than a modal, to stay consistent with the mod's existing hold-`DEL`-style
  "make destructive actions require a deliberate second step" philosophy already
  present in `MixinAttributeFilterScreen`'s attribute-deletion UX — a modal is also
  fine if simpler to implement well; either is acceptable, just don't make Delete a
  single accidental click.
- **Search/sort:** an `EditBox` filtering by name substring (case-insensitive), plus
  a type filter (All / Attribute Filters / List Filters — though when opened _from_ a
  filter screen for Import purposes, non-matching-type entries should probably be
  visibly present but Import-disabled rather than hidden, so players can still
  Rename/Delete/Export them from the same screen) and a sort toggle (Name / Newest /
  Oldest).

## 7. Edge cases to handle explicitly

- Empty library → friendly empty-state message, not a blank scrollable void.
- Config file present but unreadable/corrupt JSON → treat as empty library, warn
  once, **do not delete or overwrite the file** (so a player can recover it by hand
  or file a bug report with it attached).
- Saved payload references an attribute type from a mod that's no longer installed
  (e.g. an AE2-specific attribute saved while AE2 was present, loaded later without
  it) → this is exactly the "invalid entries are ignored and reported" case the
  clipboard import already handles at the per-attribute level inside
  `applyAttributeFilterPayload`; make sure that behavior is preserved when the
  source is the library rather than the clipboard.
- Two entries with the same name — allowed (id is the real key), but the Save-dialog
  overwrite prompt should only fire on an _exact_ name+type match, not silently
  create ambiguous duplicates by default.
- Very long names — enforce the existing 35-char cap in the Save dialog's `EditBox`
  (`setMaxLength`), not just on write.

## 8. Testing checklist

- [ ] Existing clipboard Export/Import/Tree/Export-Available still work unchanged
      after the §5 refactor (regression pass — do this before writing any new UI).
- [ ] Save → close client entirely → relaunch → Library still shows the entry.
- [ ] Import from Library produces attribute-for-attribute identical results to
      pasting the same entry's clipboard export would (parity check).
- [ ] Merge-mode import from Library behaves like Shift+clipboard-import today.
- [ ] Renaming/duplicating/deleting in the Library persists correctly to disk and
      survives a restart.
- [ ] Opening Library from an Attribute Filter screen and returning via `onClose`
      leaves the original filter menu/container in a correct, still-usable state
      (see the container-desync note in §5).
- [ ] Library with 100+ entries scrolls smoothly and search/sort remain responsive.
- [ ] Deliberately corrupt `saved_filters.json` by hand → client doesn't crash,
      shows an empty/warned library, original file is left on disk untouched.
- [ ] `./gradlew compileJava` / `./gradlew build` succeed; no mixin apply errors in
      the dev-client log (`./gradlew runClient`) when opening either filter screen.

## 9. Suggested milestone order

1. **Data layer** — `SavedFilterType`, `SavedFilter`, `FilterLibraryStore` (with
   atomic save/load, caps, defensive parsing). No UI yet; can be sanity-checked by
   temporarily calling it from an existing button's click handler and checking the
   file on disk.
2. **Refactor pass** — extract `build*Payload`/`apply*Payload` helpers out of both
   mixins per §5, re-point existing clipboard methods at them, re-verify existing
   Export/Import manually. Commit this as its own isolated step so it's easy to
   bisect if something regresses later.
3. **FilterLibraryScreen shell** — screen class, `onClose`/return-to-parent wiring,
   empty-state, static (non-scrolling) placeholder list, opened via a temporary debug
   keybind if useful for fast iteration before button wiring exists.
4. **List + row actions** — `ObjectSelectionList` subclass, Import/Rename/
   Duplicate/Export/Tree/Delete per row, search box, sort/type filter.
5. **Save flow** — Save/Save-As dialog wired into both filter screens, "Library"
   button wired into both filter screens, `loadedLibraryId` tracking for in-place
   updates.
6. **Styling pass** — swap placeholder flat rendering for the `AllGuiTextures`-frame
   - accent-bordered flat-fill interior described in §5.
7. **Lang keys, README/changelog updates** (repo already maintains a `changelog.txt`
   at the root — add an entry there following its existing style, and extend the
   README's feature list/usage guide to mention the Library alongside the existing
   Import/Export sections).
8. **Full manual QA pass** against §8.
9. **Stretch:** standalone keybind entry point (browse/manage without an open
   filter).

## 10. Assumptions made — confirm or correct before/while the agent works

- Single JSON file for the whole library (§4) rather than one file per entry.
- Client-only storage, no cross-device/cross-player sync (§3).
- Standalone overlay `Screen` for the Library rather than an inline expanding panel
  within the existing filter screens (§5) — flagged as the main structural choice
  worth double-checking against your own mental picture of "similar to VH" before
  the agent invests in it, since it's the biggest single design decision here.
- Two-step click-to-confirm Delete rather than a modal dialog (§6) — either is fine,
  just don't leave Delete as a single accidental click.
- `MAX_LIBRARY_ENTRIES = 500` as a soft cap — arbitrary, pick whatever feels right.

---

## Ready-to-paste agent prompt

> You are working in a clone of `IamHaque/Vault-Filters-Enhanced`, a fork of the
> Vault Hunters 3rd Edition Minecraft mod "Vault Filters" (MC 1.18.2, Forge 40.x,
> Create 0.5.1.i-435, Java 17) that already adds clipboard-based Export/Import/Tree
> for Create's Attribute Filter and List Filter GUIs (see
> `mixin/compat/create/MixinAttributeFilterScreen.java`,
> `mixin/compat/create/MixinFilterScreen.java`, and `util/FilterUiUtils.java`).
>
> Implement a persistent, in-game **Filter Library**: a "Save" action in both filter
> GUIs that writes the current filter to a local JSON file (`config/vaultfilters/saved_filters.json`, atomic writes, envelope format `vaultfilters.library.v1`
> wrapping the existing `vaultfilters.attribute_filter.v1` / `vaultfilters.list_filter.v2`
> payloads unchanged), and a new standalone `FilterLibraryScreen` (opened from a
> "Library" button on both filter GUIs, VH/Create-styled, using
> `ObjectSelectionList` for a searchable/sortable scrollable list) offering Import
> (replace/merge), Rename, Duplicate, per-entry clipboard Export, per-entry Tree
> view, and two-step-confirm Delete for every saved entry.
>
> Full design, file-by-file plan, refactor steps, edge cases, and a testing checklist
> are in `filter-library-gui-implementation-plan.md` — follow it, but re-verify its
> factual claims about the current code against the actual repo state first, since it
> may have moved on since this plan was written. Start with the data layer and the
> refactor step (extracting shared payload build/apply helpers out of the two mixins)
> before touching any UI, so the existing clipboard Export/Import behavior is
> provably unchanged and reused rather than duplicated.
