# Vault Filters <a href="https://www.curseforge.com/minecraft/mc-mods/vault-filters"><img src="http://cf.way2muchnoise.eu/952507.svg" alt="CF"></a>

Getting tired of sorting gear manually? This simple but powerful mod allows you to filter and sort data heavy vault gear,
trinkets, charms, inscriptions and jewels according to your needs and preferences by adding vault related attributes to
the already existing attribute filter from the Create mod. The mod adds compatibility for the list and attribute filters
with several mods including modular routers, refined storage and sophisticated backpacks. It also removes Create as a
research requirement for the filters allowing the mod to be used independently of Create using the compatibility features.
Wanna filter omega gear pieces? Put an omega item in the attribute filter and select the new "is of rarity: OMEGA" filter,
simple as that! The mod of course provides more than just rarity filtering, it provides many filter attributes!

## Credits

Huge thanks to the people here for making this mod possible

- Author: Joseph Franci
- Contributor: [JustAHuman-xD](https://github.com/JustAHuman-xD)
  Rewrite with both code and performance optimizations and a ton of polishing and mod presence detection.
- Contributor: [radimous](https://github.com/radimous)
  Added a ton of mod compatibility and huge technical help
- Contributor: [iwolfking](https://github.com/iwolfking)
  Added additional support to Refined Storage and Applied Energistics, added Tom's storage support, added backpack attributes and provided technical help.
- Contributor: [ShiftTheDev](https://github.com/shiftthedev)
  Added backpack attributes
-

<details open>
<summary style="font-size: 1.75em; font-weight: bold">
New Item Attributes (Click to Collapse/Expand)
</summary>

(Everything inside "" is the parameter of the attribute, with the contents being an example parameter.
But you can of course choose different parameters during selection in the filter menu.)

### General

- Is a "Trinket/Charm/Gear Piece/Inscription/Jewel/Treasure Key/Catalyst" Item
- Is unidentified
- Is at least level "32"
- Is of item ID "Vault Helmet"

### Soul value

- Has soul value
- Has exactly "256" soul value
- Has at least "128" soul value

### Affix Attributes

(Compatible with Gear Pieces, Vault Tools and Jewels)

- Has a legendary modifier
- Has a legendary "Resistance" prefix
- Has a legendary "Trap Disarm" suffix

#### Example Filter 2

- Has a "Block Chance" implicit
- Has a "Health" prefix
- Has a "Mana Regen" suffix

#### Example Filter 3

- Has an implicit that adds at least "+5% Movement Speed"
- Has a prefix that adds at least "+10 Armor"
- Has a suffix that adds at least "+5% Item Quantity"

#### Example Filter 4

- Has an affix in group "OnHitEffect"

### Gear Pieces

- Is a "Rare" gear piece
- Has at least "2" Unused repair slots
- Uses the "Rusty Warrior" transmog

### Jewels

- Is a "Flawed" jewel
- Is a jewel with up to "22" size
- Has been free cut "2" times

### Vault Tools

- Is a "Chromatic Iron" vault tool

### Inscriptions

- Adds a "Wild West" room
- Adds a "Challenge" type room

#### Inscription Size Filter

- Is an inscription with up to "10" size

### Catalysts

- Is a catalyst with up to "10" size

#### Catalyst with Modifier Filter

- Is a catalyst with a "Plentiful" modifier
- Is a catalyst with a "Bonus Chests/Cascading" category modifier

### Charms

- Is a "Regal" charm
- Adds affinity for "Velara"

#### Charm with Affinity Filter

- Adds at least "20%" affinity
- Is a charm with at least "7" uses left

### Trinkets

- Is a "The Frog" trinket
- Is a "Blue/Red" colored trinket
- Is a trinket with at least "25" uses left

</details>

## Extra features

- The attribute and list filters from the Create mod no longer require the Create mod to be researched for crafting and use.
- The attribute filter now requires gold nuggets instead of brass nuggets to craft.
- Attribute filters now support **Tree / Import / Export / Export Available** actions directly in the filter UI.
- List filters now support **Tree / Import / Export** actions in their UI.

## Attribute Filter Import / Export

You can now share and reuse full attribute filter setups without needing to physically own example items for every attribute value.

### Buttons in the Attribute Filter UI

- **Tree**: Copies the current attribute filter to clipboard as a readable tree view.
- **Export**: Copies the current filter attributes to your clipboard as JSON.
- **Import**: Reads JSON from your clipboard and applies it to the filter.
- **Export Available**: Copies all available attributes for the currently selected item to your clipboard as a JSON payload (useful for discovering all possible attributes without manual construction).

### Power-user options (Attribute Filters)

- **Shift + Export**: Exports pretty-printed JSON (human-readable).
- **Shift + Import**: Imports in **merge mode** (adds onto current attributes instead of replacing them).
- **Shift + Export Available**: Exports available attributes as pretty-printed JSON.

### Import behavior (Attribute Filters)

- Normal Import replaces existing attributes in the filter.
- Shift Import merges imported attributes into current ones.
- Top-level `isBlacklist` is exported/imported for attribute filters when present.
- Duplicate entries in the payload are skipped.
- Invalid entries are ignored and reported.
- Payload format/version is validated.
- Oversized clipboard payloads are rejected before parse (current limit: 262144 characters).
- Filter name is included in export when custom and imported when provided.
- If imported name is missing/blank/default item name, current name is kept.

### Quick usage guide

1. Open an **Attribute Filter** with an item selected.
2. Click **Tree** if you want a human-readable snapshot.
3. Click **Export Available** to export all attributes for that item.
4. Modify the exported JSON to keep only the attributes you need.
5. Click **Import** on another filter to apply it.
6. Alternatively, build a filter manually and click **Export** to share it.

### Payload format (canonical, Attribute Filters)

```json
{
  "format": "vaultfilters.attribute_filter.v1",
  "isBlacklist": false,
  "name": "My Gear Attr Filter",
  "attributes": [
    {
      "inverted": false,
      "nbt": "{level:0}"
    }
  ]
}
```

Notes:

- `name` is optional. If omitted, blank, or default item name, import keeps the current filter name.
- `isBlacklist` is optional on import. If provided, it applies allow/deny mode (`false` = whitelist, `true` = blacklist).
- `inverted` corresponds to negated attributes (blacklist-style condition for that entry).
- `nbt` is the serialized Create/Vault Filters attribute payload.
- For best compatibility, always use payloads generated via **Export**.

## List Filter Import / Export

List filters now support full **Tree / Import / Export** actions in their UI, including nested filters and top-level list options.

### Buttons in the List Filter UI

- **Tree**: Copies current list filter setup to clipboard as a readable hierarchy.
- **Export**: Copies current list filter setup to clipboard as JSON.
- **Import**: Reads clipboard JSON and applies it.

### Power-user options

- **Export**: Exports pretty-printed compact JSON (`vaultfilters.list_filter.v2`).
- **Shift + Export**: Exports minified compact JSON (`vaultfilters.list_filter.v2`).
- **Ctrl + Shift + Export**: Exports legacy raw JSON (`vaultfilters.list_filter.v1`) including list-level `nbt` blobs.
- **Shift + Import**: Imports in **merge mode** (appends to open slots instead of replacing all slots).

### Import behavior

- Normal Import clears existing list slots, then applies imported entries.
- If no valid imported entries are found, existing list contents are left unchanged.
- Shift Import merges into first available empty slots.
- Oversized clipboard payloads are rejected before parse (current limit: 262144 characters).
- Supports nested `list_filter` and `attribute_filter` entries (recursive payloads).
- Top-level list settings are exported/imported and applied when present (both replace and merge imports):
  - `isBlacklist` (allow/deny mode)
  - `shouldRespectNBT` (ignore/respect data)
  - `matchAll` (AND/OR mode)
- Filter name is imported when provided and non-default.
- If imported name is missing/blank/default item name, current name is kept.

### Payload format (canonical)

```json
{
  "format": "vaultfilters.list_filter.v2",
  "name": "My Master Filter",
  "filter": {
    "isBlacklist": false,
    "shouldRespectNBT": false,
    "matchAll": true,
    "items": [
      {
        "type": "attribute_filter",
        "nbt": "{Count:1b,id:\"create:attribute_filter\",tag:{MatchedAttributes:[{Inverted:0b,item_type:{item_type:\"Gear Piece\"}}],WhitelistMode:1}}"
      },
      {
        "type": "list_filter",
        "items": []
      }
    ]
  }
}
```

Notes:

- `name` is optional and follows the same apply rules as attribute filters.
- `format` is optional on import. If present, it may be `vaultfilters.list_filter.v2` (compact) or `vaultfilters.list_filter.v1` (legacy).
- `items` are imported into list slots in order, up to available capacity.
- For best compatibility, use payloads produced by in-game **Export**.

Compact export notes:

- List filter entries are exported without redundant list-level `nbt` blobs.
- Nested structure (`type`, list options, and `items`) remains the source of truth for list filters.
- Tree output uses spaced `=` formatting for readability and strips duplicated key prefixes (for example `gear_rarity = Unique` instead of `gear_rarity = gear_rarity=Unique`).
- Tree roundtrip guidance is documented in `docs/FILTER_TREE_ROUNDTRIP_PLAYBOOK.md`.

Due to these changes and the compatibility additions, create is no longer a requirement to use this mod.

<details open>
<summary style="font-size: 1.75em; font-weight: bold">
Mod Compatibilities (Click to Collapse/Expand)
</summary>

## Compatibility

(Filters = List & Attribute Filters)

### Modular Routers

- Filters now work properly as filter items inside modules. Note: Only one of each filter type can be placed, but multiple attribute filters can be placed inside a single list filter.

### Sophisticated Backpacks

- Filters now function correctly inside the filter slots of upgrades like the pickup upgrade and void upgrade.

### Refined Storage

- RS filter item compatibility: Attribute filters can now be placed inside the "Filter" item.
- Filters work properly inside Exporters, Importers, Destructors, and External Storage.
- For performance reasons, when pulling out of a disk, only gear-like items matching the filters will be pulled out, When pulling out of items stored in external storages, everything functions normally.

### Applied Energistics

- Filters work properly inside of View Cells, Import Bus, Export Bus, Storage Bus, and Formation Planes. Supports normal and Fuzzy card operation.

</details>

## Server / Client Requirements

- The mod functions when only on the server, allowing players without it to join.
- Players must have the mod installed on the client to select the new attributes.
- While not mandatory, it's recommended to install the mod on both server and clients to avoid compatibility issues.

## Disclaimer

- This mod is not a Create addon but a gear sorting addon that utilizes the existing attribute filter.
- Vault Filters is a Vault Hunters 3rd edition addon, intended for use alongside the modpack.

## Licenses

Vault Filters falls under the MIT license. See [Vault Filter's license](https://github.com/Odgug/Vault-Filters/blob/master/LICENSE) for more information.
Certain sections of the code are from the Create mod, which is licensed under the MIT license. See [Create's license](https://github.com/Creators-of-Create/Create/blob/mc1.18/dev/LICENSE) for more information.

![JewelExample](assets/JewelExample.png?raw=true 'Jewel Attributes')
![GearExample](assets/GearExample.png?raw=true 'Gear Attributes')

<https://github.com/Odgug/Vault-Filters/assets/108800252/8794aba7-9325-4e3b-96fc-5b9d2d5c4d17>
