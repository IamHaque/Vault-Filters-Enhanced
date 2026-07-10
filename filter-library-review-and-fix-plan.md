# Filter Library — Fix Plan

This revises the original review after **live in-game testing** surfaced two more
bugs beyond the static code review, plus a new hard requirement (type-scoping). Hand
this single document to the agent — it fully supersedes the v1 fix plan; don't use
both.

Reviewed: `IamHaque/Vault-Filters-Enhanced`, branch **`V2`** (not `master`). Commits:
`2a2f7df..bd59b10`, built on an earlier payload-format refactor already on `V2`
(`c540b1f..d75cd14`).

**Verdict:** the storage layer and several UI pieces are solid and should be kept
as-is. But the feature's central action — taking a saved filter and applying it to
an open filter item — was never built, and live testing surfaced two more concrete
bugs on top of that. None of this requires a rewrite of what's there; it's a scoped
fix-it pass.

---

## 1. What's correct — keep this as-is, don't touch except where a fix below requires it

- `FilterLibraryStore` — storage path (`config/vaultfilters/saved_filters.json`),
  envelope format (`vaultfilters.library.v1`), atomic tmp-file + rename with
  non-atomic fallback, defensive per-entry parsing (skip-and-count rather than
  discard-the-file on a bad entry), unknown top-level format → treated as empty +
  logged, not deleted.
- Shared `FilterPayloadUtils` used by both the pre-existing clipboard flow and the
  new Save/Library flow — no duplicated serialization logic.
- Entry-count cap (`canAddMore()`, 500 max) correctly checked in both mixins before
  creating a new entry.
- Two-step Delete (arm → confirm, 5s auto-reset) exactly as specified.
- Rename dialog: real `EditBox`, ESC/Enter handling, 35-char cap.
- Visual styling: Create's brass-frame corner/edge textures for the outer frame,
  flat-fill interior with gold accent line.
- README/changelog updated in the existing project style/location.

## 2. Critical gap — the feature's actual point is missing

Trace the current code:

- `FilterLibraryScreen.onImport()` reads the OS clipboard and adds a **new library
  entry**. This is "import into library," not "import into the currently open
  filter."
- A library row's **Export** button copies that entry's JSON to the clipboard —
  nothing more.
- Both mixins open the screen via
  `Minecraft.getInstance().setScreen(new FilterLibraryScreen())` — no arguments.
  `FilterLibraryScreen` has only a no-arg constructor: no `parentScreen`, no
  `onApply`/`onImport` callback, no idea which filter screen (if any) it was opened
  from, or what type of filter that screen wants.

Net effect: to actually apply a saved filter today, a player must: open Library →
select entry → Export → close Library → reopen the target filter → click _that
screen's own pre-existing_ clipboard Import button → paste. That's the old
clipboard workflow with an extra hop through the library grafted on — not the
one-click "select a saved filter, it's applied, replace or merge" behavior that was
the actual ask.

**This is the #1 thing to build in the fix-it pass** (see §5, step 4).

## 3. Bugs found in static code review

1. **Round-trip is broken by a schema mismatch** (confirmed live — see §4A below).
   `FilterPayloadUtils.buildAttributeExportRoot` / `buildListExportRoot` (used by
   Save) write a root object with a `"format"` field and no `"type"` field.
   `FilterLibraryScreen.onImport()` requires a top-level `"type"` field
   (`attribute_filter` / `list_filter` / `item_filter`) to accept clipboard JSON.
   Consequence: exporting a filter you just saved, then pasting that exact JSON back
   in via the Library's own Import button, fails with "Clipboard does not contain a
   valid filter payload."

2. **No return-to-parent on close.** `FilterLibraryScreen` never overrides
   `onClose()` and never stores a parent screen, so closing it falls back to vanilla
   `Screen.onClose()` → `setScreen(null)`. Opening the Library from an open
   Attribute/List Filter container and then closing the Library will likely boot the
   player out of that container entirely rather than returning them to it.

3. **No Save-As dialog, no overwrite prompt.** Clicking **Save** silently reuses
   whatever name is already on the Create filter item's `contentHolder` — there is
   no name-entry prompt at all. `FilterLibraryStore.existsByName(...)` is fully
   implemented but is **never called from anywhere in the codebase** (dead code).
   Practical effect: saving the same untitled filter twice silently creates two
   duplicate entries instead of updating the first or prompting to overwrite.

4. **`duplicate()` name-cap math overflows the 35-char limit.**
   `FilterLibraryStore.duplicate()` truncates the original name to 30 chars, then
   appends `" (Copy)"` (7 chars) → up to 37 chars, exceeding the 35-char cap enforced
   everywhere else (Save, Rename, load-time normalization). Should truncate to
   `35 - " (Copy)".length()` = 28 chars before appending.

5. **No search / sort / type-filter UI**, even though the store-level building
   blocks exist and are unused: `FilterLibraryStore.SortMode` (`NAME_ASC` /
   `NEWEST_FIRST` / `OLDEST_FIRST`) and `listByType(SavedFilterType)` are never
   called by `FilterLibraryScreen`, which just calls plain `list()` (insertion
   order) with no search box, no sort toggle, no type filter anywhere in the file.
   (This item is now folded into the type-scoping requirement in §4C below — treat
   §4C as the authoritative version of this fix.)

6. **Two different, inconsistent import-size limits.**
   `FilterLibraryScreen` hardcodes `MAX_IMPORT_SIZE = 25000` for its own
   clipboard-into-library import, instead of reusing
   `FilterUiUtils.MAX_IMPORT_CHARS` (262,144) that the rest of the mod already uses
   for the identical concept.

7. **No per-entry payload size cap at Save time.** Only the 500-entry _count_ cap is
   enforced (`canAddMore()`); a single filter with a very large serialized payload
   isn't capped before being written to disk.

8. **No automated tests for the new `library` package**, despite
   `src/test/java/.../util/FilterPayloadUtilsTest.java` already existing as an
   established precedent in this exact codebase from an earlier commit on the same
   branch. A basic test on `FilterLibraryStore.duplicate()`/`rename()` would have
   caught bug #4 immediately.

9. Minor API smell (not a bug today, but worth tightening): `SavedFilter.withName()`
   / `withPayload()` mutate `this` in place and return `this`, despite the
   `with*`-naming convention normally implying a copy. Works today only because
   `FilterLibraryStore` always operates on the same map-held reference — a footgun
   for future code that reasonably expects copy-on-write semantics from a `with*`
   method. Fix by either making it genuinely immutable or renaming to
   `setName`/`setPayload`.

## 4. Bugs found in live testing (in-game, with a screenshot of the Library screen)

### 4A — Confirmed live: the `"format"`/`"type"` schema bug

Same root cause as §3 bug #1, now confirmed by actually clicking Import in-game and
getting the "invalid/wrong format" error, not just by reading the code.

**Fix:** add `"type"` to the export root inside
`buildAttributeExportRoot`/`buildListExportRoot` in `FilterPayloadUtils.java`
(preferred — smaller, localized change, doesn't touch the already-tested clipboard
Export/Import path used by the filter screens themselves). Alternative: change
`FilterLibraryScreen.onImport()` to also accept the existing `"format"` field as a
type discriminator — only do this instead if adding `"type"` to the export root
turns out to break something else.

### 4B — The list panel is fighting vanilla `ObjectSelectionList`

Screenshot evidence: a full-height tiled dirt-pattern background bleeds from the top
of the screen down into the panel, and clicking on listed entries does nothing.

**Root cause (one underlying issue, two symptoms):**

`FilterLibraryScreen.FilterList` extends `ObjectSelectionList<FilterEntry>` and
tries to reposition it after construction via `list.setLeftPos(x + 10)` (relying on
an inherited method) and a hand-rolled `setRightPos(int right) { this.x1 = right; }`
(added specifically because no such setter exists by default — a sign the class
wasn't designed to be repositioned this way after construction). Two things go wrong
as a result:

1. **Background bleed-through.** `AbstractSelectionList` draws its own full-screen-height
   tiled backdrop as part of its normal render path, independent of the `x0`/`x1`
   bounds being poked after the fact. The code's attempt to suppress it —
   `@Override protected void renderBackground(PoseStack ms) {}` — compiles (so it's
   overriding _some_ real method) but evidently isn't the actual hook used to draw
   that backdrop in this Forge/MC mapping, since it's still visible.
2. **Unclickable rows.** Directly mutating `x0`/`x1` after construction, without
   going through whatever internal resize/update path the class expects, is a
   plausible and consistent explanation for rows rendering in the right place (via
   the `left`/`width` params `render()` receives) while hit-testing (which
   `AbstractSelectionList` computes from its own internal cached row-bounds) lands
   somewhere else, so `list.mouseClicked(...)` never matches a `FilterEntry`.

**Fix: replace `ObjectSelectionList` with a hand-rolled list.** This is exactly what
`massuus/vault-party-ui` (a client-side Forge mod for the same MC 1.18.2 / VH
context) does for its own scrollable panels (`PartyPanelRenderer`), specifically to
avoid fighting `AbstractSelectionList`'s baked-in decorations inside a small
custom-framed window. Concretely:

- Drop `FilterList`/`FilterEntry` (the `ObjectSelectionList` subclasses) entirely.
- Store `List<SavedFilter> filters` (already exists) plus a `scrollOffset` (int) and
  a `selectedId` (`UUID`) field directly on `FilterLibraryScreen`.
- In `render()`, loop over the currently-visible slice of `filters` (based on
  `scrollOffset` and the panel's interior height / row height), and for each row:
  draw the hover-highlight fill, name, type badge, relative time — same visuals as
  today's `FilterEntry.render()`, just called directly from a loop instead of
  through the widget framework.
- In `mouseClicked()`, manually test `mouseY`/`mouseX` against each visible row's
  computed rectangle (`top = listTop + (i - scrollOffset) * rowHeight`, etc.); on
  hit, set `selectedId` and call `updateButtonStates()` — same behavior as today's
  `FilterEntry.mouseClicked()`, just invoked from a plain loop instead of relying on
  `ObjectSelectionList`'s hit-testing.
- Handle `mouseScrolled()` yourself: clamp `scrollOffset` to
  `[0, max(0, filters.size() - visibleRowCount)]`, adjust by a fixed step per wheel
  tick. Optionally draw a simple manual scrollbar (a thin vertical bar on the right
  edge of the panel, height proportional to `visibleRowCount / filters.size()`) —
  not required for correctness, just a nice-to-have; skip it for the first pass if
  time-constrained.
- No change needed to `renderBg()` (the brass frame) — that's independent of this
  and was already correct. The tiled-dirt bleed-through goes away simply because
  nothing derived from `AbstractSelectionList` is in the render path anymore.

This is a net simplification, not just a bug fix — it also removes any need to
figure out the exact right vanilla method to override for background suppression or
whether `setLeftPos` does the right thing, since there's no longer any vanilla
list-positioning API being relied on at all.

### 4C — New requirement: type-scope the library to the calling filter, and enforce it

Two changes, one for the UI and one as a hard safety check. This supersedes and
subsumes §3 bug #5 (search/sort/type-filter UI) — implement type-scoping as
described here rather than as a separate optional toggle.

1. **Default view is filtered to the calling context's type.** When
   `FilterLibraryScreen` is opened from an Attribute Filter screen, the visible list
   should show only `SavedFilterType.ATTRIBUTE_FILTER` entries by default (and only
   `LIST_FILTER` entries when opened from a List Filter screen) — filter first,
   don't just gray out mismatched rows. A toggle to reveal the other type (for pure
   management — Rename/Duplicate/Export/Delete only, never Apply) is a reasonable
   secondary affordance but the default view should already be scoped.
2. **Hard-enforce the type match at the apply boundary regardless of what the UI
   shows.** The method that actually pushes a payload into the open filter screen
   should check `entry.type() == expectedType` and refuse (with a clear
   `vaultfilters.gui.library.wrong_type` message) even if it's somehow invoked on a
   mismatched entry. Attribute Filter payloads and List Filter payloads have
   different internal structure (flat `attributes` array vs. nested `filter.items`)
   and are not interchangeable — this must never be allowed to silently
   misinterpret one as the other. Treat this as a correctness invariant, not just a
   UX nicety.

Also add basic search (`EditBox`, case-insensitive substring on name) and a sort
toggle (Name / Newest / Oldest, using the already-implemented but currently-unused
`FilterLibraryStore.SortMode`) to the screen while you're building this — the
store-side support already exists, it just needs a UI hook.

---

## 5. Ordered fix-it plan

Do **not** re-architect what's listed in §1. This is a targeted pass, in this
order:

1. **Verify the build.** `./gradlew compileJava`, then `./gradlew runClient` and
   open both filter screens, open the Library.

2. **Fix the `"format"`/`"type"` schema mismatch** (§4A) — add `"type"` to
   `buildAttributeExportRoot`/`buildListExportRoot`'s output in
   `FilterPayloadUtils.java`.

3. **Replace `ObjectSelectionList` with a hand-rolled row list** (§4B) — fixes both
   the dirt-background bleed-through and the unclickable rows in one change. Do
   this _before_ step 4, since Apply needs working row selection to know which
   entry to act on.

4. **Build the "apply to open filter" action** (§2), combined with type-scoping
   (§4C) — this is the main piece of work:
   - Give `FilterLibraryScreen` a real constructor:
     `FilterLibraryScreen(@Nullable Screen parentScreen, @Nullable SavedFilterType contextType, @Nullable BiConsumer<JsonObject, Boolean> onApply)`
     where `onApply` is `(payload, merge) -> void`, wired by the calling mixin to
     invoke the _already-existing_ clipboard-apply logic (the same code path
     `vault_Filters$importFromClipboard()` already uses) — do not write a second
     parser.
   - Filter the displayed list to `contextType` by default (§4C.1).
   - Override `onClose()`: `Minecraft.getInstance().setScreen(this.parentScreen)`
     (falls back to `null` only when `parentScreen` is itself `null`, i.e. a
     standalone/browse-only opening).
   - Add two new per-row actions distinct from the existing clipboard-oriented
     **Export**: **"Apply (Replace)"** and **"Apply (Merge)"**, active only when
     `onApply != null` and the row's type matches `contextType`. These call
     `onApply.accept(entry.payload(), merge)` then close back to the parent. Before
     calling `onApply`, hard-check `entry.type() == contextType` and refuse with a
     clear message if not (§4C.2 — defense in depth even though the UI is already
     filtered).
   - Update both mixins' `vault_Filters$openLibrary()` to pass
     `new FilterLibraryScreen(this, SavedFilterType.ATTRIBUTE_FILTER /* or LIST_FILTER */, this::vault_Filters$applyLibraryPayload)`
     where `vault_Filters$applyLibraryPayload` is a small new method that calls the
     same underlying apply logic the clipboard Import button already calls (extract
     it to a shared method if it isn't already cleanly callable outside the
     clipboard-read wrapper).
   - **Explicitly test** the container-desync concern from §3 bug #2 while doing
     this: open Library from a filter, apply an entry, confirm the filter
     menu/container is still in a correct, usable state afterward; also test
     opening Library, doing nothing, and closing it (should cleanly return to the
     filter, contents untouched).

5. **Add the Save-As / overwrite-prompt flow** (§3 bug #3). Reuse the same
   `EditBox`-based dialog pattern already built for Rename in `FilterLibraryScreen`
   (or a lighter inline version directly in the mixin screens) — prefill with the
   filter's current display name, and call the already-implemented but currently
   unused `FilterLibraryStore.existsByName(type, name, excludingId)` before creating
   a new entry to decide whether to prompt "overwrite existing filter named X?"
   instead of silently duplicating.

6. **Fix `duplicate()`'s truncation math** (§3 bug #4) — trivial one-line fix:
   `substring(0, 35 - " (Copy)".length())`.

7. **Add search + sort to the UI** (§4C, last paragraph) — an `EditBox` for name
   search and a sort-cycle button (Name / Newest / Oldest) using the store's
   existing `SortMode`. The type filter itself is already handled by step 4's
   default type-scoping; add a toggle to reveal the other type for management
   purposes if time allows.

8. **Unify the size limits** (§3 bugs #6, #7) — remove
   `FilterLibraryScreen.MAX_IMPORT_SIZE` and use `FilterUiUtils.MAX_IMPORT_CHARS`
   everywhere; add the same check against a built payload's serialized length
   inside both mixins' `vault_Filters$saveToLibrary()` before calling
   `FilterLibraryStore.upsert(...)`, with a clear
   `vaultfilters.gui.library.save_too_large` message.

9. **Add unit tests for `FilterLibraryStore`** (§3 bug #8) covering: rename length
   cap, duplicate name-length cap (would have caught bug #4), `existsByName`
   matching once it's actually wired up, the type-filter/hard-check from §4C, and a
   round-trip save→load→list test against a temp directory. Follow the existing
   `FilterPayloadUtilsTest.java` as the pattern to match.

10. Optional polish: tighten `SavedFilter.withName`/`withPayload` per §3 bug #9.

## 6. Testing checklist

- [ ] `./gradlew compileJava` succeeds.
- [ ] Open Library from an Attribute Filter → only Attribute Filter entries are
      listed by default.
- [ ] Open Library from a List Filter → only List Filter entries are listed by
      default.
- [ ] Clicking a row selects it (visually highlighted) and enables the row action
      buttons — this must actually work now, it didn't before.
- [ ] No dirt-texture background bleed above/around the panel.
- [ ] Scrolling the list (if more entries than fit) works via mouse wheel.
- [ ] Apply (Replace) on a selected entry replaces the open filter's attributes and
      returns to the filter screen correctly.
- [ ] Apply (Merge) adds onto existing attributes instead of replacing.
- [ ] Export a saved entry → clipboard-Import that same JSON back into the Library
      → succeeds (regression test for §4A).
- [ ] Attempting to force-apply a mismatched-type payload (e.g. by temporarily
      disabling the UI filter to test the hard check) is rejected with a clear
      message, not silently misapplied.
- [ ] Save the same untitled filter twice → overwrite prompt, not a silent
      duplicate.
- [ ] Duplicate an entry with a 35-char name → resulting copy name is ≤ 35 chars.
- [ ] Search box filters by substring; sort toggle actually reorders the list.

---

## Ready-to-paste agent prompt

> Continuing work on the Filter Library feature (`IamHaque/Vault-Filters-Enhanced`,
> branch `V2`, commits `2a2f7df..bd59b10`). A code review plus live in-game testing
> found the following, all detailed with root causes in
> `filter-library-review-and-fix-plan.md` (this is the only planning doc — no other file
> needed):
>
> The storage layer (`FilterLibraryStore`), Delete/Rename dialogs, and brass-frame
> visual styling are solid — keep those as-is. But: (1) there's currently no way to
> take a saved library entry and apply it to the open Attribute/List Filter —
> "Import" today only means "paste clipboard JSON into the library," and even that
> is broken by a schema mismatch (Save/Export write a `"format"` field, but the
> Library's Import requires a `"type"` field that's never written); (2) the Library
> screen's list panel is broken in-game — a tiled dirt-texture background bleeds
> above the panel and clicking on listed entries does nothing, both traced to
> `FilterList extends ObjectSelectionList<FilterEntry>` being repositioned after
> construction via ad hoc `x0`/`x1` field mutation instead of whatever resize path
> the class actually expects; (3) the Library must filter its visible list to the
> calling filter's type by default and hard-reject any attempt to apply a
> mismatched type, since Attribute Filter and List Filter payloads have different,
> non-interchangeable structure.
>
> Follow the ordered plan in §5 of `filter-library-review-and-fix-plan.md`: fix the
> schema mismatch first (§5.2, quick), then replace `ObjectSelectionList` with a
> hand-rolled row list for the panel (§5.3 — this fixes both the background bug and
> the click bug in one change; model it on `massuus/vault-party-ui`'s
> `PartyPanelRenderer`, which hand-rolls its own list for the same reason), then
> build the actual "apply this saved filter to my open filter" action with
> type-scoping built in from the start (§5.4). Steps 5–10 (Save-As/overwrite
> prompt, the `duplicate()` name-cap bug, search/sort UI, unified size limits, and
> unit tests) are smaller and follow in the same pass. Use the testing checklist in
> §6 before considering this done — in particular, actually click on library rows
> and confirm selection/highlighting works, and confirm the dirt background is
> gone.
