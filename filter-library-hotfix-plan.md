# Filter Library — Hotfix Plan (round 4, self-contained)

This is the only file needed — it restates everything still outstanding from the
previous round plus the new issues found in this round of in-game testing, so
nothing needs to be cross-referenced from earlier files.

Branch `V2`, tested state: commit `d3aad6a` (no new commits since the last review).

## 1. No background in the Library screen (carried over, not yet fixed)

**Root cause:** commit `2f08f36` (`§5.3 Replace ObjectSelectionList with
hand-rolled row list`) accidentally deleted the `private void renderBg(PoseStack ms)`
method — which draws the Create brass-frame corners/edges, the dark interior fill,
and the gold accent line — while removing the old `FilterList`/`FilterEntry`
`ObjectSelectionList` subclasses it happened to sit next to. The _call_ to
`renderBg(ms)` was left in place, so the project stopped compiling at that commit
and stayed broken through the next four commits. The final commit
(`d3aad6a`, "§6 Compilation fixes") found the resulting compile error and fixed it
by deleting the call to `renderBg(ms)` — instead of restoring the method it called.
Net result: `FilterLibraryScreen` currently renders with no background/frame at
all.

**Add this method back to `FilterLibraryScreen.java`:**

```java
private void renderBg(PoseStack ms) {
    int x = (width - GUI_WIDTH) / 2;
    int y = (height - GUI_HEIGHT) / 2;

    // Interior dark fill
    fill(ms, x + 3, y + 3, x + GUI_WIDTH - 3, y + GUI_HEIGHT - 3, 0xFF2D2D2D);

    // Brass frame corners
    AllGuiTextures.BRASS_FRAME_TL.render(ms, x, y, this);
    AllGuiTextures.BRASS_FRAME_TR.render(ms, x + GUI_WIDTH - 4, y, this);
    AllGuiTextures.BRASS_FRAME_BL.render(ms, x, y + GUI_HEIGHT - 4, this);
    AllGuiTextures.BRASS_FRAME_BR.render(ms, x + GUI_WIDTH - 4, y + GUI_HEIGHT - 4, this);

    // Brass frame edges (stretched to fill gaps)
    UIRenderHelper.drawStretched(ms, x + 4, y, GUI_WIDTH - 8, 3, 0, AllGuiTextures.BRASS_FRAME_TOP);
    UIRenderHelper.drawStretched(ms, x + 4, y + GUI_HEIGHT - 3, GUI_WIDTH - 8, 3, 0, AllGuiTextures.BRASS_FRAME_BOTTOM);
    UIRenderHelper.drawStretched(ms, x, y + 4, 3, GUI_HEIGHT - 8, 0, AllGuiTextures.BRASS_FRAME_LEFT);
    UIRenderHelper.drawStretched(ms, x + GUI_WIDTH - 3, y + 4, 3, GUI_HEIGHT - 8, 0, AllGuiTextures.BRASS_FRAME_RIGHT);

    // Gold accent line at the top of the list panel
    fill(ms, x + 6, y + 17, x + GUI_WIDTH - 6, y + 18, 0xFFC99E3D);
}
```

**Restore the call at the top of `render()`** (keep everything else in the method
as-is, just add these two lines back before `super.render(...)`):

```java
@Override
public void render(PoseStack ms, int mouseX, int mouseY, float partialTicks) {
    renderBackground(ms);
    renderBg(ms);
    super.render(ms, mouseX, mouseY, partialTicks);
    renderList(ms, mouseX, mouseY, partialTicks);
    // ...rest unchanged
```

The `AllGuiTextures`/`UIRenderHelper` imports are already present in the file (they
were never removed, only the method using them was) — no import changes needed.

**After fixing:** re-run the exact scenario from the original bug report — open the
Library from a filter screen and confirm the brass frame, dark panel, and gold
accent line are all visible again, with no dirt-texture bleed (that part of the fix
from §5.3 is unaffected and still correct).

**Process note for the agent:** this happened because a large multi-hunk deletion
swept up an unrelated method that happened to be adjacent in the file, and the
compile error that resulted was fixed at the call site instead of by checking what
the deleted method actually was. When resolving "cannot find symbol" errors that
show up after a big refactor commit, check `git diff` for that method name across
the refactor commit before assuming the call site is what's wrong — the method may
have been deleted by accident rather than intentionally renamed/removed.

## 2. List Filter library shows nothing / Attribute Filter library shows everything

I reviewed every layer of the type-scoping code at the current commit and all of it
is correct:

- Save-side type tagging (`SavedFilter.createNew(SavedFilterType.LIST_FILTER, ...)`
  in `MixinFilterScreen`) has been correct in **every commit since it was first
  introduced** — verified across the full commit history, not just the latest state.
- Load-side parsing (`FilterLibraryStore`'s file-load loop, `SavedFilterType.fromJsonId`)
  correctly reads the per-entry `"type"` field and skips entries where it doesn't
  parse, rather than defaulting to a wrong type.
- `FilterLibraryStore.listByType()` correctly compares `sf.type() == type`.
- Both mixins' `vault_Filters$openLibrary()` correctly pass
  `SavedFilterType.ATTRIBUTE_FILTER` / `SavedFilterType.LIST_FILTER` respectively.
- `FilterLibraryScreen.init()` correctly branches on `contextType` to call
  `listByType(contextType)` instead of the unfiltered `list()`.

Since I can't find a bug by reading the code, and the code has been correct on this
front since the very first commit that introduced Save/Library buttons, **the most
likely explanation is stale data or a stale build, not a live code bug.** Diagnose
in this order — each step takes under a minute and will tell us which:

1. **Check the actual file on disk first.** Open
   `<instance>/config/vaultfilters/saved_filters.json` in a text editor and look at
   the `"type"` field on each entry. If entries you believe you saved as list
   filters actually show `"type": "attribute_filter"` in the raw file, the data
   itself is wrong (most likely leftover from testing during an earlier build of
   this feature, before type-scoping existed at all — recall the very first Library
   screenshot showed entries with no type-awareness in the UI whatsoever). **Fix:**
   delete the file (or just the mistagged entries) and re-save fresh with the
   current build — no code change needed. If new saves make through this test
   still land with the wrong `"type"` in the file, that's a real, reproducible bug —
   stop here and report back with the exact JSON, since that would mean something I
   haven't found yet (worth adding a one-line debug log right before
   `FilterLibraryStore.upsert(...)` in both mixins' save methods, printing the
   `SavedFilterType` being passed, to catch it in the act).
2. **If the file's data is already correctly typed** but the in-game list still
   shows the wrong entries, confirm the client is actually running the latest
   build: do a clean rebuild (`./gradlew clean build`) and check the built jar's
   timestamp, or the log line Forge prints on startup showing the mod version/file
   being loaded, to rule out a stale/cached jar being loaded instead of the latest
   compile.
3. Only if both of the above check out (fresh data is correctly typed, and the
   client is confirmed running the latest jar) and the bug still reproduces: add
   temporary debug logging in `FilterLibraryScreen.init()` right after the
   `listByType`/`list()` call, printing `contextType` and, for each entry in the
   result, `sf.name() + " -> " + sf.type()`, and share that log output — at that
   point this needs a live trace rather than more static review.

## 3. Bottom button row — reorganize into two rows + add tooltips

Doing both, as suggested, rather than picking one — they solve different problems
(overflow vs. clarity) and are cheap to do together.

**Current state:** up to 8 buttons (`Replace, Merge, Import, Rename, Duplicate,
Export, Tree, Delete`) crammed into one row at `36px width + 2px gap` = 302px total,
inside a 256px-wide panel — genuinely overflows the frame by 46px, which is likely
part of why the labels look clipped/unclear in-game.

**Proposed layout — two rows, grouped by function:**

- **Row 1 (only when opened from a filter screen, i.e. `onApply != null &&
contextType != null`):** the two Apply actions, given more room since there are
  only two of them — `"Apply Replace"` / `"Apply Merge"` at full width instead of
  truncated, e.g. `110px` each + `4px` gap = `224px`, comfortably inside 256px.
- **Row 2 (always present):** the six management actions in a slightly
  reorganized, more deliberate order — **Import** (adds new, doesn't need a
  selection) grouped slightly apart from the selection-dependent actions:
  `Import | Rename, Duplicate | Export, Tree | Delete` — visually this can just be
  a small extra gap between groups rather than separate widgets, e.g. `4px` between
  groups vs `2px` within a group. At `36px` width these 6 buttons need
  `6*36 + 5*2 = 226px` (plus a few px for group gaps, say `236px` total), which
  fits inside 256px without needing to shrink text.
- This needs a bit more vertical room than the current single-row layout — increase
  `GUI_HEIGHT` from `220` to roughly `240` and adjust `listBottom` accordingly so
  the list area doesn't get squeezed; recalculate `btnY` for two rows (`row1Y = y +
GUI_HEIGHT - 46`, `row2Y = y + GUI_HEIGHT - 26`, adjust to taste once tested
  in-game).

**Add tooltips regardless of the layout change** — even with room to show full
button text, abbreviated labels benefit from a tooltip on hover. Vanilla
`net.minecraft.client.gui.components.Button` in this MC version has a constructor
overload accepting an `OnTooltip` callback:

```java
new Button(x, y, w, h, message, onPress, (button, poseStack, mouseX, mouseY) ->
        renderTooltip(poseStack, new TranslatableComponent("vaultfilters.gui.library.tooltip.export"), mouseX, mouseY))
```

Add one `vaultfilters.gui.library.tooltip.<action>` lang key per button
(`import`, `rename`, `duplicate`, `export`, `tree`, `delete`, `apply_replace`,
`apply_merge`) with a short one-line description of what it does, and wire each
button's construction to use this constructor overload instead of the current
3-arg one.

## 4. Other recommended tweaks (found while reviewing for the above)

None of these are bugs exactly, but worth doing while already in this code:

1. **Add a visible "Showing: Attribute Filters" / "Showing: List Filters" / "Showing:
   All Filters" label** near the top of the panel, reflecting `contextType` and
   `showOtherType`. This is a genuinely useful permanent UX improvement (players
   should always know what scope they're looking at), and it also would have made
   issue #2 above immediately self-diagnosing from a screenshot rather than needing
   a code review to narrow down.
2. **Search box doesn't lose focus when clicking a list row.** In `mouseClicked()`,
   the row-hit-test branch sets `selectedId` and returns `true` without calling
   `searchBox.changeFocus(false)` (or equivalent) first. Practical effect: after
   typing in search and then clicking a row, the search box may still silently hold
   keyboard focus. Add `if (searchBox != null) searchBox.changeFocus(false);` right
   before `selectedId = filters.get(i).id();` in that branch.
3. **The Rename dialog isn't a true modal.** `beginRename()` disables the six
   action buttons but doesn't disable `searchBox`, `sortButton`, or
   `showAllButton` — a player can still type in search or toggle sort/show-all
   while the rename box is open on top. Since `renameTarget` is held by direct
   object reference (not by list index), this won't corrupt the rename itself, but
   it's inconsistent with the rest of the modal's behavior. Add
   `searchBox.setEditable(false)` (or similar) and
   `sortButton.active = showAllButton.active = false` alongside the existing
   button-disabling lines in `beginRename()`, and re-enable them in
   `cleanupRename()`.
4. **No scroll position indicator.** If a library grows past the visible row
   count, there's currently no visual cue (scrollbar, "3/12" counter, etc.) that
   more entries exist below/above the current view — a player has to discover
   scrolling by accident. A simple `"{count} filters"` label plus, optionally, a
   thin manual scrollbar track on the right edge of the list (proportional height =
   `visibleCount / filters.size()`) would help; not urgent, but worth doing while
   the panel is already being resized for the button-row change above.
5. **No visible entry-count vs. cap.** `FilterLibraryStore.MAX_LIBRARY_ENTRIES` is
   500 but nothing in the UI shows how close a player is to it. A small
   `"{count}/500"` label near the search box (or folded into the "Showing: ..."
   label from item 1) would be a cheap, useful addition.

## Testing checklist

- [ ] `./gradlew compileJava` succeeds.
- [ ] Brass frame, dark panel fill, and gold accent line all visible when the
      Library is opened.
- [ ] Check `saved_filters.json` directly — confirm `"type"` fields match what was
      actually saved, before/after a fresh save from each filter screen.
- [ ] Library opened from Attribute Filter shows only attribute filter entries by
      default (toggle "Show All" to confirm list filters appear there instead, not
      mixed in by default).
- [ ] Library opened from List Filter shows only list filter entries by default.
- [ ] Button rows fit fully inside the frame at the default GUI scale, no clipped
      or overflowing text.
- [ ] Hovering each button shows a tooltip with a clear description.
- [ ] Typing in search, then clicking a row, doesn't leave the search box focused
      (test by pressing a letter key afterward and confirming it doesn't land in
      the search box).
- [ ] Opening Rename, then trying to type in search or click Sort/Show All, is
      blocked until Rename is confirmed/cancelled.

## Ready-to-paste agent prompt

> Continuing the Filter Library feature (`IamHaque/Vault-Filters-Enhanced`, branch
> `V2`, currently at `d3aad6a`). In-game testing found: (1) the background/brass
> frame is still missing — this is the same regression identified previously
> (commit `2f08f36` deleted `renderBg()`, never restored); (2) the Library shows
> the wrong filters by type — opened from List Filter it shows no list filters,
> opened from Attribute Filter it shows everything; (3) the bottom button row
> overflows the panel and labels are hard to read.
>
> Full details and fixes are in `filter-library-hotfix-plan.md` (self-contained,
> no other file needed). For #1, restore the exact method and call given in §1 —
> this was accidentally deleted in an earlier refactor. For #2, I reviewed every
> layer of the type-scoping code (save-tagging, load-parsing, `listByType`,
> `contextType` wiring into both mixins) across the full commit history and all of
> it is correct — follow the diagnostic steps in §2 in order, starting with
> literally opening `config/vaultfilters/saved_filters.json` and checking the
> `"type"` field on existing entries, since the most likely explanation is stale
> data or a stale build rather than a live bug; only add debug logging if steps 1–2
> don't explain it. For #3, reorganize into the two-row layout in §3 and add
> tooltips using the vanilla `Button` `OnTooltip` constructor overload. §4 has four
> small additional UX improvements worth doing in the same pass since they touch
> the same code (a "Showing: X" label would also make #2 easier to diagnose from a
> screenshot in the future). Use the testing checklist in the final section before
> considering this done.
