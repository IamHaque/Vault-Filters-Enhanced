package net.joseph.vaultfilters.mixin.compat.create;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.logistics.filter.AbstractFilterMenu;
import com.simibubi.create.content.logistics.filter.AbstractFilterScreen;
import com.simibubi.create.content.logistics.filter.AttributeFilterMenu;
import com.simibubi.create.content.logistics.filter.AttributeFilterMenu.WhitelistMode;
import com.simibubi.create.content.logistics.filter.AttributeFilterScreen;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.filter.FilterScreenPacket;
import com.simibubi.create.content.logistics.filter.ItemAttribute;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.utility.Components;
import com.simibubi.create.foundation.utility.Pair;
import net.joseph.vaultfilters.attributes.abstracts.VaultAttribute;
import net.joseph.vaultfilters.network.MenuFeaturesPacket;
import net.joseph.vaultfilters.network.VFMessages;
import net.joseph.vaultfilters.library.FilterLibraryStore;
import net.joseph.vaultfilters.library.SavedFilter;
import net.joseph.vaultfilters.library.SavedFilterType;
import net.joseph.vaultfilters.util.FilterPayloadUtils;
import net.joseph.vaultfilters.util.FilterUiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(value = AttributeFilterScreen.class, remap = false)
public abstract class MixinAttributeFilterScreen extends AbstractFilterScreen<AttributeFilterMenu> {
    @Unique
    private Button vault_Filters$exportButton;
    @Unique
    private Button vault_Filters$importButton;
    @Unique
    private Button vault_Filters$exportTreeButton;
    @Unique
    private Button vault_Filters$saveLibraryButton;
    @Unique
    private Button vault_Filters$openLibraryButton;
    @Unique
    private java.util.UUID vault_Filters$loadedLibraryId;
    @Unique
    private Button vault_Filters$exportAvailableButton;

    protected MixinAttributeFilterScreen(AttributeFilterMenu menu, Inventory inv,
                                         Component title, AllGuiTextures background) {
        super(menu, inv, title, background);
    }

    // placing attr filter inside empty filter will take its attributes
//    @Inject(at = @At(value = "HEAD"), method = "referenceItemChanged")
//    public void copyCurrentAttrFilter(ItemStack stack, CallbackInfo ci) {
//        if (((AttributeFilterMenuAccessor)this.menu).getSelectedAttributes().isEmpty()
//            && FilterItemStack.of(stack) instanceof FilterItemStack.AttributeFilterItemStack attrFilter) {
//
//            var currentTests = attrFilter.attributeTests;
//            for (var test: currentTests){
//                this.vault_Filters$addAttr(test.getFirst(), test.getSecond());
//            }
//        }
//    }
    @Shadow
    private List<ItemAttribute> attributesOfItem = new ArrayList<>();
    @Inject(at = @At(value = "INVOKE_ASSIGN", target = "Ljava/util/List;stream()Ljava/util/stream/Stream;",shift = At.Shift.BEFORE), method = "referenceItemChanged")
    public void addAttributesFromFilter(ItemStack stack, CallbackInfo ci) {
        if (stack.is(AllItems.ATTRIBUTE_FILTER.get())) {
            boolean defaults = !stack.hasTag();
            ListTag attributes = defaults ? new ListTag() : stack.getTag().getList("MatchedAttributes", 10);
            if (attributes.isEmpty()) {
                return;
            }
            for (Tag inbt : attributes) {
                CompoundTag compound = (CompoundTag)inbt;
                ItemAttribute attribute = ItemAttribute.fromNBT(compound);
                if (attribute != null) {
                    attributesOfItem.add(attribute);
                }
            }
        }
    }


    // deletion logic below
    @Shadow private List<Component> selectedAttributes;
    @Shadow private Component selectedT;

    @Unique private int vault_Filters$selectedAttrIndex = 0;
    @Unique private int vault_Filters$deletionLastTick = 0;
    @Unique private int vault_Filters$deletionProgressTick = 0;

    // scrolling of selected attributes
    @Override public boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        var res = super.mouseScrolled(pMouseX, pMouseY, pDelta);
        if (this.hoveredSlot != null && this.hoveredSlot.index == 37) {
            var idx = vault_Filters$selectedAttrIndex;
            if (pDelta < 0 && idx + 1 < this.selectedAttributes.size() - 2) {
                vault_Filters$selectedAttrIndex = idx + 1;
                var oldAt = this.selectedAttributes.get(idx + 1);
                this.selectedAttributes.set(idx + 1, Components.literal(oldAt.getString()).withStyle(ChatFormatting.GRAY));
                var newAt = this.selectedAttributes.get(idx + 2);
                this.selectedAttributes.set(idx + 2, Components.literal(newAt.getString()).withStyle(ChatFormatting.WHITE));
                vault_Filters$deletionProgressTick = 0;
            } else if (pDelta > 0 && idx > 0) {
                vault_Filters$selectedAttrIndex = idx - 1;
                if (this.selectedAttributes.size() > idx + 1) {
                    var oldAt = this.selectedAttributes.get(idx + 1);
                    this.selectedAttributes.set(idx + 1, Components.literal(oldAt.getString()).withStyle(ChatFormatting.GRAY));
                }
                var newAt = this.selectedAttributes.get(idx);
                this.selectedAttributes.set(idx, Components.literal(newAt.getString()).withStyle(ChatFormatting.WHITE));
                vault_Filters$deletionProgressTick = 0;
            }
        }
        return res;
    }

    // deletion tooltip
    @Unique private static final Component vault_Filters$delTooltipLine =  Components.literal("Hold [").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC)
        .append(Components.literal("DEL").withStyle(ChatFormatting.WHITE))
        .append(Components.literal("] to remove attribute"));

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void addDelTooltipLine(CallbackInfo ci) {
        if (this.selectedAttributes.size() > 1) {
            this.selectedAttributes.add(vault_Filters$delTooltipLine);
        }

        int x = leftPos + this.background.width - 236;
        int y = topPos - 26;

        vault_Filters$exportTreeButton = new Button(x, y, 42, 18,
            new TranslatableComponent("vaultfilters.gui.attribute_filter.export_tree"),
            button -> vault_Filters$exportTreeToClipboard());

        vault_Filters$exportButton = new Button(x + 46, y, 42, 18,
                new TranslatableComponent("vaultfilters.gui.attribute_filter.export"),
                button -> vault_Filters$exportToClipboard());

        vault_Filters$importButton = new Button(x + 92, y, 42, 18,
                new TranslatableComponent("vaultfilters.gui.attribute_filter.import"),
                button -> vault_Filters$importFromClipboard());

        vault_Filters$exportAvailableButton = new Button(x + 138, y, 90, 18,
                new TranslatableComponent("vaultfilters.gui.attribute_filter.export_available"),
                button -> vault_Filters$exportAvailableAttributes());

        addRenderableWidget(vault_Filters$exportTreeButton);
        addRenderableWidget(vault_Filters$exportButton);
        addRenderableWidget(vault_Filters$importButton);
        addRenderableWidget(vault_Filters$exportAvailableButton);

        // Library buttons (second row)
        int libraryY = y - 22;
        vault_Filters$saveLibraryButton = new Button(x + 46, libraryY, 42, 18,
                new TranslatableComponent("vaultfilters.gui.library.save"),
                button -> vault_Filters$saveToLibrary());
        vault_Filters$openLibraryButton = new Button(x + 92, libraryY, 42, 18,
                new TranslatableComponent("vaultfilters.gui.library.open"),
                button -> vault_Filters$openLibrary());

        addRenderableWidget(vault_Filters$saveLibraryButton);
        addRenderableWidget(vault_Filters$openLibraryButton);
    }
    @Inject(method = "handleAddedAttibute", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private void rmDelTooltipLine(boolean inverted, CallbackInfoReturnable<Boolean> cir) {
        this.selectedAttributes.remove(vault_Filters$delTooltipLine);
    }

    @Inject(method = "handleAddedAttibute", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void readdDelTooltipLine(boolean inverted, CallbackInfoReturnable<Boolean> cir) {
        this.selectedAttributes.add(vault_Filters$delTooltipLine);
    }
    @WrapOperation(method = "renderForeground", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int renderForeground(List<Component> instance, Operation<Integer> original) {
        if (!instance.isEmpty() && instance.get(instance.size() - 1) == vault_Filters$delTooltipLine) {
            return instance.size() - 1; // don't count the deletion tooltip line
        }
        return original.call(instance);
    }

    // handle hold to delete and highlighting
    @Inject(method = "containerTick", at = @At("TAIL"), remap = true)
    private void attributeDeletionTick(CallbackInfo ci) {
        if (vault_Filters$pressDel()) {
            int idx = vault_Filters$selectedAttrIndex;
            var selectedAttrs = ((AttributeFilterMenuAccessor) this.menu).getSelectedAttributes();
            if (idx >= 0 && idx < selectedAttrs.size()) {
                Pair<ItemAttribute, Boolean> selectedAttr = selectedAttrs.get(idx);
                this.vault_Filters$removeAttr(selectedAttr.getFirst(), selectedAttr.getSecond());
                vault_Filters$selectedAttrIndex = Math.max(0, idx - 1);
                return;
            }
        }
        int idx = vault_Filters$selectedAttrIndex;
        if (idx >= 0 && idx + 1 < this.selectedAttributes.size()) {
            float progress = Math.min(Math.max(0, vault_Filters$deletionProgressTick), 20) / 20f;
            var selected = this.selectedAttributes.get(idx + 1);
            String text = selected.getString();
            int redChars = (int) (text.length() * progress);
            Component redPart = Components.literal(text.substring(0, redChars)).withStyle(ChatFormatting.RED);
            Component whitePart = Components.literal(text.substring(redChars)).withStyle(ChatFormatting.WHITE);
            this.selectedAttributes.set(idx + 1, Components.literal("").append(redPart).append(whitePart));
        }
    }

    // more hold to delete logic
    @Unique private boolean vault_Filters$pressDel() {
        var pl = Minecraft.getInstance().player;
        if (pl == null) {
            return false;
        }
        var tc = pl.tickCount;
        if (tc < vault_Filters$deletionLastTick || vault_Filters$deletionProgressTick < 0) {
            vault_Filters$deletionProgressTick = 0;
        }
        if (tc != vault_Filters$deletionLastTick) {
            if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_DELETE)) {
                vault_Filters$deletionProgressTick++;
            } else {
                vault_Filters$deletionProgressTick--;
            }
        }
        vault_Filters$deletionLastTick = tc;

        if (vault_Filters$deletionProgressTick > 20) {
            vault_Filters$deletionProgressTick = 0;
            return true;
        }
        return false;
    }

    @Unique private void vault_Filters$addAttr(ItemAttribute itemAttribute, boolean inverted) {
        CompoundTag tag = new CompoundTag();
        itemAttribute.serializeNBT(tag);
        AllPackets.getChannel()
            .sendToServer(new FilterScreenPacket(inverted ? FilterScreenPacket.Option.ADD_INVERTED_TAG : FilterScreenPacket.Option.ADD_TAG, tag));
        this.menu.appendSelectedAttribute(itemAttribute, inverted);
        if (((AttributeFilterMenuAccessor) this.menu).getSelectedAttributes().size() == 1) {
            this.selectedAttributes.set(0, this.selectedT.plainCopy().withStyle(ChatFormatting.YELLOW));
        }
        this.selectedAttributes.remove(vault_Filters$delTooltipLine);
        this.selectedAttributes.add(Components.literal("- ").append(itemAttribute.format(inverted)).withStyle(ChatFormatting.GRAY));
        this.selectedAttributes.add(vault_Filters$delTooltipLine);
    }

    // deletes all attrs and readds everything except the one that should be removed
    @Unique private void vault_Filters$removeAttr(ItemAttribute itemAttribute, boolean inverted) {
        var toRemove = Pair.of(itemAttribute, inverted);
        var currentAttributes = new ArrayList<>(((AttributeFilterMenuAccessor) this.menu).getSelectedAttributes());
        if (currentAttributes.remove(toRemove)) {
            this.menu.clearContents();
            this.contentsCleared();
            this.menu.sendClearPacket();
            for (Pair<ItemAttribute, Boolean> pair : currentAttributes) {
                this.vault_Filters$addAttr(pair.getFirst(), pair.getSecond());
            }
        }
    }

    @Unique
    private void vault_Filters$exportToClipboard() {
        boolean shift = FilterUiUtils.isShiftDownSafe();
        boolean ctrl = FilterUiUtils.isControlDownSafe();
        boolean alt = FilterUiUtils.isAltDownSafe();
        boolean simplifiedFormat = !shift && !ctrl && !alt;
        boolean prettyV2 = shift && !ctrl && !alt;
        boolean minifiedV2 = shift && ctrl && !alt;
        boolean legacyRaw = alt;
        boolean prettyJson = prettyV2 || (!minifiedV2 && !simplifiedFormat && !legacyRaw);

        List<Pair<ItemAttribute, Boolean>> currentAttributes = new ArrayList<>(((AttributeFilterMenuAccessor) this.menu).getSelectedAttributes());
        JsonArray attributes = FilterPayloadUtils.buildAttributeEntries(currentAttributes, simplifiedFormat);

        String format = simplifiedFormat ? FilterUiUtils.ATTRIBUTE_FORMAT_SIMPLIFIED :
                       (legacyRaw ? FilterUiUtils.ATTRIBUTE_FORMAT_V1 : FilterUiUtils.ATTRIBUTE_FORMAT_V2);

        JsonObject root = FilterPayloadUtils.buildAttributeExportRoot(
            FilterUiUtils.getCurrentFilterName((AbstractFilterMenu) this.menu),
            vault_Filters$isAttributeFilterBlacklist(),
            vault_Filters$isAttributeFilterMatchAll(),
            attributes,
            format
        );
        try {
            String exportJson = prettyJson ? FilterUiUtils.PRETTY_GSON.toJson(root) : FilterUiUtils.GSON.toJson(root);
            Minecraft.getInstance().keyboardHandler.setClipboard(exportJson);
            if (simplifiedFormat) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.exported.simplified", currentAttributes.size()).withStyle(ChatFormatting.GREEN));
            } else if (prettyV2) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.exported.pretty", currentAttributes.size()).withStyle(ChatFormatting.GREEN));
            } else if (minifiedV2) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.exported.minified", currentAttributes.size()).withStyle(ChatFormatting.GREEN));
            } else if (legacyRaw) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.exported.legacy", currentAttributes.size()).withStyle(ChatFormatting.GREEN));
            }
        } catch (Exception ignored) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.export.invalid").withStyle(ChatFormatting.RED));
        }
    }

    @Unique
    private void vault_Filters$importFromClipboard() {
        boolean merge = FilterUiUtils.isShiftDownSafe();
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.empty").withStyle(ChatFormatting.RED));
            return;
        }
        if (clipboard.length() > FilterUiUtils.MAX_IMPORT_CHARS) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.too_large", FilterUiUtils.MAX_IMPORT_CHARS).withStyle(ChatFormatting.RED));
            return;
        }

        String importedName;
        boolean hasImportedBlacklist;
        boolean importedBlacklist;
        boolean hasImportedMatchAll;
        boolean importedMatchAll;
        List<Pair<ItemAttribute, Boolean>> imported;
        int invalid;
        int duplicates;

        try {
            JsonElement parsed = JsonParser.parseString(clipboard);
            if (parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                FilterPayloadUtils.AttributeImportResult result = FilterPayloadUtils.parseAttributeImport(root);
                imported = result.attributes();
                importedName = result.name();
                hasImportedBlacklist = result.hasBlacklist();
                importedBlacklist = result.blacklist();
                hasImportedMatchAll = result.hasMatchAll();
                importedMatchAll = result.matchAll();
                invalid = result.invalid();
                duplicates = result.duplicates();
                if (imported.isEmpty() && result.invalid() == 0 && result.duplicates() == 0) {
                    FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.invalid").withStyle(ChatFormatting.RED));
                    return;
                }
            } else if (parsed.isJsonArray()) {
                JsonArray array = parsed.getAsJsonArray();
                importedName = null;
                hasImportedBlacklist = false;
                importedBlacklist = false;
                hasImportedMatchAll = false;
                importedMatchAll = false;
                imported = new ArrayList<>();
                Set<String> dedupe = new HashSet<>();
                invalid = 0;
                duplicates = 0;
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) { invalid++; continue; }
                    JsonObject entry = element.getAsJsonObject();
                    JsonElement payload = null;
                    if (entry.has("nbt") && entry.get("nbt").isJsonPrimitive()) payload = entry.get("nbt");
                    else if (entry.has("attribute") && entry.get("attribute").isJsonObject()) payload = entry.get("attribute");
                    if (payload == null) { invalid++; continue; }
                    boolean inverted = entry.has("inverted") && entry.get("inverted").getAsBoolean();
                    try {
                        CompoundTag tag = payload.isJsonPrimitive()
                                ? TagParser.parseTag(payload.getAsString())
                                : FilterPayloadUtils.expandFlattenedAttributeCompound(FilterPayloadUtils.jsonToCompoundTag(payload));
                        ItemAttribute attribute = ItemAttribute.fromNBT(tag);
                        if (attribute == null) { invalid++; continue; }
                        String dedupeKey = (inverted ? "1:" : "0:") + tag;
                        if (!dedupe.add(dedupeKey)) { duplicates++; continue; }
                        imported.add(Pair.of(attribute, inverted));
                    } catch (Exception ignored) { invalid++; }
                }
            } else {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.invalid").withStyle(ChatFormatting.RED));
                return;
            }
        } catch (Exception ignored) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.invalid").withStyle(ChatFormatting.RED));
            return;
        }

        if (imported.isEmpty()) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.none").withStyle(ChatFormatting.RED));
            return;
        }

        if (!merge) {
            this.menu.clearContents();
            this.contentsCleared();
            this.menu.sendClearPacket();

            this.selectedAttributes.clear();
            this.selectedAttributes.add(this.selectedT.plainCopy().withStyle(ChatFormatting.GRAY));
            this.selectedAttributes.remove(vault_Filters$delTooltipLine);
            this.vault_Filters$selectedAttrIndex = 0;
            this.vault_Filters$deletionProgressTick = 0;
        }

        if (importedName != null) {
            FilterUiUtils.applyImportedFilterName((AbstractFilterMenu) this.menu, importedName);
        }

        if (hasImportedBlacklist || hasImportedMatchAll) {
            vault_Filters$setAttributeFilterMode(importedBlacklist, importedMatchAll);
        }

        int applied = 0;
        for (Pair<ItemAttribute, Boolean> pair : imported) {
            this.vault_Filters$addAttr(pair.getFirst(), pair.getSecond());
            applied++;
        }

        if (merge) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.imported.merge", applied, invalid, duplicates).withStyle(ChatFormatting.GREEN));
        } else {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.imported.replace", applied, invalid, duplicates).withStyle(ChatFormatting.GREEN));
        }
    }

    @Unique
    private void vault_Filters$exportAvailableAttributes() {
        boolean shift = FilterUiUtils.isShiftDownSafe();
        boolean ctrl = FilterUiUtils.isControlDownSafe();
        boolean legacyRaw = shift && ctrl;
        boolean minifiedV2 = shift && !ctrl;
        boolean prettyV2 = !shift;
        boolean pretty = !minifiedV2;
        JsonObject root = new JsonObject();
        root.addProperty(FilterUiUtils.FORMAT_FIELD, legacyRaw ? FilterUiUtils.ATTRIBUTE_FORMAT_V1 : FilterUiUtils.ATTRIBUTE_FORMAT_V2);
        JsonArray attributes = new JsonArray();

        for (ItemAttribute attribute : attributesOfItem) {
            CompoundTag tag = new CompoundTag();
            attribute.serializeNBT(tag);
            JsonObject entry = new JsonObject();
            entry.addProperty("inverted", false);
            entry.addProperty("nbt", tag.toString());
            attributes.add(entry);
        }

        root.add(FilterUiUtils.ATTRIBUTES_FIELD, attributes);
        try {
            String exportJson = pretty ? FilterUiUtils.PRETTY_GSON.toJson(root) : FilterUiUtils.GSON.toJson(root);
            Minecraft.getInstance().keyboardHandler.setClipboard(exportJson);
            if (legacyRaw) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.export_available.copied.legacy", attributesOfItem.size()).withStyle(ChatFormatting.GREEN));
            } else if (prettyV2) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.export_available.copied.pretty", attributesOfItem.size()).withStyle(ChatFormatting.GREEN));
            } else {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.export_available.copied.minified", attributesOfItem.size()).withStyle(ChatFormatting.GREEN));
            }
        } catch (Exception ignored) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.export.invalid").withStyle(ChatFormatting.RED));
        }
    }

    @Unique
    private void vault_Filters$exportTreeToClipboard() {
        try {
            List<Pair<ItemAttribute, Boolean>> currentAttributes = new ArrayList<>(((AttributeFilterMenuAccessor) this.menu).getSelectedAttributes());
            StringBuilder tree = new StringBuilder();

            String rootName = FilterUiUtils.getCurrentFilterName((AbstractFilterMenu) this.menu);
            tree.append(FilterPayloadUtils.attributeTreeHeader(rootName, vault_Filters$isAttributeFilterBlacklist(), vault_Filters$isAttributeFilterMatchAll()))
                    .append("\n");

            for (Pair<ItemAttribute, Boolean> pair : currentAttributes) {
                CompoundTag tag = new CompoundTag();
                pair.getFirst().serializeNBT(tag);
                vault_Filters$appendAttributeTreeLine(tree, tag, pair.getSecond());
            }

            Minecraft.getInstance().keyboardHandler.setClipboard(tree.toString());
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.exported.tree", currentAttributes.size()).withStyle(ChatFormatting.GREEN));
        } catch (Exception ignored) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.export.invalid").withStyle(ChatFormatting.RED));
        }
    }

    @Unique
    private void vault_Filters$appendAttributeTreeLine(StringBuilder tree, CompoundTag tag, boolean inverted) {
        FilterUiUtils.TagEntry attributeEntry = FilterUiUtils.firstSortedDataEntry(tag);
        String key = attributeEntry == null ? null : attributeEntry.key();
        Tag valueTag = attributeEntry == null ? null : attributeEntry.value();

        tree.append("  - ");
        if (inverted) {
            tree.append("NOT ");
        }

        if (key == null || valueTag == null) {
            tree.append("unknown = <invalid>");
        } else {
            tree.append(key)
                    .append(" = ")
                    .append(FilterUiUtils.normalizeAttributeSummary(key, FilterUiUtils.summarizeTag(valueTag)));
        }

        tree.append("\n");
    }

    @Unique
    private WhitelistMode vault_Filters$getAttributeFilterMode() {
        ItemStack contentHolder = ((AbstractFilterMenu) this.menu).contentHolder;
        CompoundTag tag = contentHolder.getTag();
        if (tag == null || !tag.contains("WhitelistMode", Tag.TAG_INT)) {
            return WhitelistMode.WHITELIST_DISJ;
        }
        int ordinal = tag.getInt("WhitelistMode");
        WhitelistMode[] values = WhitelistMode.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return WhitelistMode.WHITELIST_DISJ;
        }
        return values[ordinal];
    }

    @Unique
    private boolean vault_Filters$isAttributeFilterBlacklist() {
        return vault_Filters$getAttributeFilterMode() == WhitelistMode.BLACKLIST;
    }

    @Unique
    private boolean vault_Filters$isAttributeFilterMatchAll() {
        return vault_Filters$getAttributeFilterMode() == WhitelistMode.WHITELIST_CONJ;
    }

    @Unique
    private void vault_Filters$setAttributeFilterMode(boolean blacklist, boolean matchAll) {
        ItemStack contentHolder = ((AbstractFilterMenu) this.menu).contentHolder;
        WhitelistMode mode = blacklist
                ? WhitelistMode.BLACKLIST
                : (matchAll ? WhitelistMode.WHITELIST_CONJ : WhitelistMode.WHITELIST_DISJ);
        contentHolder.getOrCreateTag().putInt("WhitelistMode", mode.ordinal());
        AllPackets.getChannel().sendToServer(new FilterScreenPacket(
                mode == WhitelistMode.BLACKLIST ? FilterScreenPacket.Option.BLACKLIST
                        : mode == WhitelistMode.WHITELIST_CONJ ? FilterScreenPacket.Option.WHITELIST2
                        : FilterScreenPacket.Option.WHITELIST,
                new CompoundTag()));
    }

    @Unique
    private void vault_Filters$saveToLibrary() {
        try {
            List<Pair<ItemAttribute, Boolean>> attrs = new ArrayList<>(((AttributeFilterMenuAccessor) this.menu).getSelectedAttributes());
            JsonArray attributes = FilterPayloadUtils.buildAttributeEntries(attrs, true);

            JsonObject payload = FilterPayloadUtils.buildAttributeExportRoot(
                    FilterUiUtils.getCurrentFilterName((AbstractFilterMenu) this.menu),
                    vault_Filters$isAttributeFilterBlacklist(),
                    vault_Filters$isAttributeFilterMatchAll(),
                    attributes,
                    FilterUiUtils.ATTRIBUTE_FORMAT_SIMPLIFIED
            );

            if (vault_Filters$loadedLibraryId != null) {
                SavedFilter existing = FilterLibraryStore.get(vault_Filters$loadedLibraryId);
                if (existing != null) {
                    existing.withPayload(payload).touch();
                    FilterLibraryStore.upsert(existing);
                    FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.library.saved", existing.name()).withStyle(ChatFormatting.GREEN));
                    return;
                }
            }

            String name = FilterUiUtils.getCurrentFilterName((AbstractFilterMenu) this.menu);
            if (name == null || name.isBlank()) name = "Attribute Filter";
            if (name.length() > 35) name = name.substring(0, 35);

            if (!FilterLibraryStore.canAddMore()) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.library.full").withStyle(ChatFormatting.RED));
                return;
            }

            SavedFilter saved = SavedFilter.createNew(SavedFilterType.ATTRIBUTE_FILTER, name, payload);
            FilterLibraryStore.upsert(saved);
            vault_Filters$loadedLibraryId = saved.id();
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.library.saved", name).withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.library.save_failed").withStyle(ChatFormatting.RED));
        }
    }

    @Unique
    private void vault_Filters$openLibrary() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new net.joseph.vaultfilters.client.gui.FilterLibraryScreen());
    }

}
