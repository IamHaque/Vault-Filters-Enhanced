package net.joseph.vaultfilters.mixin.compat.create;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.logistics.filter.AbstractFilterMenu;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.AbstractFilterScreen;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.filter.FilterMenu;
import com.simibubi.create.content.logistics.filter.FilterScreen;
import com.simibubi.create.content.logistics.filter.FilterScreenPacket;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Indicator;
import com.simibubi.create.foundation.utility.Components;
import com.simibubi.create.foundation.utility.Lang;
import net.joseph.vaultfilters.access.FilterMenuAdvancedAccessor;
import net.joseph.vaultfilters.network.MenuFeaturesPacket;
import net.joseph.vaultfilters.network.VFMessages;
import net.joseph.vaultfilters.util.FilterPayloadUtils;
import net.joseph.vaultfilters.util.FilterUiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;

@Mixin(value = FilterScreen.class, remap = false)
public abstract class MixinFilterScreen extends AbstractFilterScreen<FilterMenu> {
    @Shadow private IconButton blacklist;
    @Shadow private IconButton whitelist;
    @Shadow private IconButton respectNBT;
    @Shadow private IconButton ignoreNBT;
    @Shadow private Component denyDESC;
    @Shadow private Component allowDESC;
    @Shadow private Component respectDataDESC;
    @Shadow private Component ignoreDataDESC;
    @Shadow private Indicator blacklistIndicator;
    @Shadow private Indicator whitelistIndicator;
    @Shadow private Indicator respectNBTIndicator;
    @Shadow private Indicator ignoreNBTIndicator;

    @Unique
    private Component allowAnyN = Lang.translateDirect("gui.attribute_filter.allow_list_disjunctive");
    @Unique
    private Component allowAnyDESC = Lang.translateDirect("gui.attribute_filter.allow_list_disjunctive.description");
    @Unique
    private Component allowAllN = Lang.translateDirect("gui.attribute_filter.allow_list_conjunctive");
    @Unique
    private Component allowAllDESC = Lang.translateDirect("gui.attribute_filter.allow_list_conjunctive.description");
    @Unique
    private IconButton matchAny, matchAll;
    @Unique
    private Indicator matchAnyIndicator, matchAllIndicator;
    @Unique
    private FilterMenuAdvancedAccessor menuAccessor = (FilterMenuAdvancedAccessor) menu;

    // List filter import/export
    @Unique
    private Button vault_Filters$exportButton;
    @Unique
    private Button vault_Filters$importButton;
    @Unique
    private Button vault_Filters$exportTreeButton;

    // This constructor is required by Mixin
    protected MixinFilterScreen(FilterMenu menu, Inventory inv, Component title, AllGuiTextures background) {
        super(menu, inv, title, background);
    }

    @Inject(method = "init",at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/filter/FilterScreen;handleIndicators()V",shift = At.Shift.BEFORE, ordinal = 0, remap = false), remap = true)
    private void injectInitializer(CallbackInfo ci, @Local(ordinal = 0) int x, @Local(ordinal = 1) int y) {
        matchAll = new IconButton(x + 102, y + 75, AllIcons.I_WHITELIST_AND);
        matchAll.withCallback(() -> {
            menuAccessor.vault_filters$setMatchAll(true);
            VFMessages.VFCHANNEL.sendToServer(new MenuFeaturesPacket(MenuFeaturesPacket.MenuAction.MATCH_ALL));
        });
        matchAll.setToolTip(allowAllN);
        matchAllIndicator= new Indicator(x + 102, y + 69, Components.immutableEmpty());

        matchAny= new IconButton(x + 120, y + 75, AllIcons.I_WHITELIST_OR);
        matchAny.withCallback(() -> {
            menuAccessor.vault_filters$setMatchAll(false);
            VFMessages.VFCHANNEL.sendToServer(new MenuFeaturesPacket(MenuFeaturesPacket.MenuAction.MATCH_ANY));
        });
        matchAny.setToolTip(allowAnyN);
        matchAnyIndicator = new Indicator(x + 120, y + 69, Components.immutableEmpty());

        addRenderableWidgets(matchAll, matchAny, matchAllIndicator, matchAnyIndicator);

        // List filter export/import buttons
        int bx = leftPos + this.background.width - 152;
        int by = topPos - 26;

        vault_Filters$exportButton = new Button(bx, by, 42, 18,
                new TranslatableComponent("vaultfilters.gui.list_filter.export"),
                button -> vault_Filters$exportToClipboard());

        vault_Filters$importButton = new Button(bx + 46, by, 42, 18,
                new TranslatableComponent("vaultfilters.gui.list_filter.import"),
                button -> vault_Filters$importFromClipboard());

        vault_Filters$exportTreeButton = new Button(bx - 46, by, 42, 18,
            new TranslatableComponent("vaultfilters.gui.list_filter.export_tree"),
            button -> vault_Filters$exportTreeToClipboard());

        addRenderableWidget(vault_Filters$exportButton);
        addRenderableWidget(vault_Filters$importButton);
        addRenderableWidget(vault_Filters$exportTreeButton);
    }

    @Inject(method = "getTooltipButtons", at = @At("HEAD"), cancellable = true)
    private void addToolTipButtons(CallbackInfoReturnable<List<IconButton>> cir) {
            cir.setReturnValue(Arrays.asList(blacklist, whitelist, respectNBT, ignoreNBT, matchAll, matchAny));
            return;
    }

    @Inject(method = "getTooltipDescriptions", at = @At("HEAD"), cancellable = true)
    private void addToolTipDescriptions(CallbackInfoReturnable<List<MutableComponent>> cir) {
            cir.setReturnValue(Arrays.asList(denyDESC.plainCopy(), allowDESC.plainCopy(), respectDataDESC.plainCopy(),
                    ignoreDataDESC.plainCopy(), allowAllDESC.plainCopy(), allowAnyDESC.plainCopy()));
            return;
    }
    @Inject(method = "getIndicators", at = @At("HEAD"), cancellable = true)
    private void addIndicators(CallbackInfoReturnable<List<Indicator>> cir) {
            cir.setReturnValue(Arrays.asList(blacklistIndicator, whitelistIndicator, respectNBTIndicator,
                    ignoreNBTIndicator, matchAllIndicator, matchAnyIndicator));
    }

    @Inject(method = "isButtonEnabled", at = @At("TAIL"), cancellable = true)
    private void addButtonsEnabled(IconButton button, CallbackInfoReturnable<Boolean> cir) {
        if (button == matchAll) {
            cir.setReturnValue(!menuAccessor.vault_filters$getMatchAll());
        } else if (button == matchAny) {
            cir.setReturnValue(menuAccessor.vault_filters$getMatchAll());
        }
    }

    @Inject(method = "isIndicatorOn", at = @At("TAIL"), cancellable = true)
    private void addIndicatorsEnabled(Indicator indicator, CallbackInfoReturnable<Boolean> cir) {
        if (indicator == matchAllIndicator) {
            cir.setReturnValue(menuAccessor.vault_filters$getMatchAll());
        } else if (indicator == matchAnyIndicator) {
            cir.setReturnValue(!menuAccessor.vault_filters$getMatchAll());
        }
    }

    @Unique
    private void vault_Filters$exportToClipboard() {
        try {
            boolean shift = FilterUiUtils.isShiftDownSafe();
            boolean ctrl = FilterUiUtils.isControlDownSafe();
            boolean alt = FilterUiUtils.isAltDownSafe();
            boolean simplifiedFormat = !shift && !ctrl && !alt;
            boolean prettyV2 = shift && !ctrl && !alt;
            boolean minifiedV2 = shift && ctrl && !alt;
            boolean legacyRaw = alt;
            boolean prettyJson = prettyV2 || (!minifiedV2 && !legacyRaw);

            List<FilterItemStack> filters = vault_Filters$getCurrentFilters();

            JsonArray items = new JsonArray();
            for (FilterItemStack filter : filters) {
                JsonObject item = FilterPayloadUtils.buildListFilterItem(filter, legacyRaw, simplifiedFormat);
                if (item != null) items.add(item);
            }

            String format = simplifiedFormat ? FilterUiUtils.LIST_FORMAT_SIMPLIFIED :
                           (legacyRaw ? FilterUiUtils.LIST_FORMAT_V1 : FilterUiUtils.LIST_FORMAT_V2);

            JsonObject root = FilterPayloadUtils.buildListExportRoot(
                    FilterUiUtils.getCurrentFilterName((AbstractFilterMenu) this.menu),
                    menuAccessor.vault_filters$isBlacklist(),
                    menuAccessor.vault_filters$shouldRespectNBT(),
                    menuAccessor.vault_filters$getMatchAll(),
                    items,
                    format
            );

            String exportJson = prettyJson ? FilterUiUtils.PRETTY_GSON.toJson(root) : FilterUiUtils.GSON.toJson(root);
            Minecraft.getInstance().keyboardHandler.setClipboard(exportJson);
            if (simplifiedFormat) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.exported.simplified", filters.size()).withStyle(ChatFormatting.GREEN));
            } else if (prettyV2) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.exported.pretty", filters.size()).withStyle(ChatFormatting.GREEN));
            } else if (minifiedV2) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.exported.minified", filters.size()).withStyle(ChatFormatting.GREEN));
            } else if (legacyRaw) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.exported.legacy", filters.size()).withStyle(ChatFormatting.GREEN));
            }
        } catch (Exception e) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.export.invalid").withStyle(ChatFormatting.RED));
        }
    }

    @Unique
    private void vault_Filters$exportTreeToClipboard() {
        try {
            List<FilterItemStack> filters = vault_Filters$getCurrentFilters();
            StringBuilder tree = new StringBuilder();

            String rootName = FilterUiUtils.getCurrentFilterName((AbstractFilterMenu) this.menu);
            if (rootName == null || rootName.isBlank()) {
                rootName = "List Filter";
            }

                tree.append(FilterPayloadUtils.listTreeHeader(
                    rootName,
                    menuAccessor.vault_filters$isBlacklist(),
                    menuAccessor.vault_filters$getMatchAll(),
                    menuAccessor.vault_filters$shouldRespectNBT()))
                    .append("\n");

            for (FilterItemStack filter : filters) {
                vault_Filters$appendTreeNode(tree, filter, 1);
            }

            Minecraft.getInstance().keyboardHandler.setClipboard(tree.toString());
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.exported.tree", filters.size()).withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.export.invalid").withStyle(ChatFormatting.RED));
        }
    }

    @Unique
    private void vault_Filters$appendTreeNode(StringBuilder out, FilterItemStack filter, int depth) {
        String indent = "  ".repeat(Math.max(0, depth));

        if (filter instanceof FilterItemStack.ListFilterItemStack listFilter) {
            String title = vault_Filters$getNodeTitle(filter, "List Filter");
            out.append(indent)
                    .append("- ")
                    .append(title)
                    .append(" (")
                    .append(FilterPayloadUtils.getModeLabel(listFilter.isBlacklist, listFilter.item().hasTag() && listFilter.item().getTag().getBoolean("MatchAll")))
                    .append(listFilter.shouldRespectNBT ? ", Respect NBT" : "")
                    .append(")\n");

            for (FilterItemStack child : listFilter.containedItems) {
                vault_Filters$appendTreeNode(out, child, depth + 1);
            }
            return;
        }

        if (filter instanceof FilterItemStack.AttributeFilterItemStack attrFilter) {
            String title = vault_Filters$getNodeTitle(filter, "Attribute Filter");
            out.append(indent)
                    .append("- ")
                    .append(title)
                    .append(" (")
                    .append(getAttributeModeLabel(attrFilter.item()))
                    .append(")\n");
            vault_Filters$appendAttributeDetails(out, filter.item(), depth + 1);
            return;
        }

        ItemStack stack = filter.item();
        ResourceLocation key = stack == null || stack.isEmpty() ? null : Registry.ITEM.getKey(stack.getItem());
        out.append(indent)
                .append("- ")
                .append(vault_Filters$getNodeTitle(filter, "Item Filter"));
        if (key != null) {
            out.append(": ").append(key);
        }
        out.append("\n");
    }

    @Unique
    private void vault_Filters$appendAttributeDetails(StringBuilder out, ItemStack item, int depth) {
        if (item == null || item.isEmpty() || !item.hasTag()) {
            return;
        }

        CompoundTag tag = item.getTag();
        if (!tag.contains("MatchedAttributes", Tag.TAG_LIST)) {
            return;
        }

        ListTag attributes = tag.getList("MatchedAttributes", Tag.TAG_COMPOUND);
        String indent = "  ".repeat(Math.max(0, depth));
        for (int i = 0; i < attributes.size(); i++) {
            Tag entryTag = attributes.get(i);
            if (!(entryTag instanceof CompoundTag entry)) {
                continue;
            }

            boolean inverted = entry.contains("Inverted", Tag.TAG_BYTE) && entry.getBoolean("Inverted");
            FilterUiUtils.TagEntry attributeEntry = FilterUiUtils.firstSortedDataEntry(entry, "Inverted");
            String key = attributeEntry == null ? null : attributeEntry.key();
            Tag valueTag = attributeEntry == null ? null : attributeEntry.value();

            if (key == null || valueTag == null) {
                out.append(indent).append("- unknown filter\n");
                continue;
            }

            out.append(indent)
                    .append("- ")
                    .append(inverted ? "NOT " : "")
                    .append(key)
                    .append(" = ")
                    .append(FilterUiUtils.normalizeAttributeSummary(key, FilterUiUtils.summarizeTag(valueTag)))
                    .append("\n");
        }
    }

    @Unique
    private String getAttributeModeLabel(ItemStack item) {
        var mode = FilterPayloadUtils.whitelistModeFromItem(item);
        return FilterPayloadUtils.getModeLabel(
            mode == FilterItemStack.AttributeFilterItemStack.WhitelistMode.BLACKLIST,
            mode == FilterItemStack.AttributeFilterItemStack.WhitelistMode.WHITELIST_CONJ
        );
    }

    @Unique
    private String vault_Filters$getNodeTitle(FilterItemStack filter, String fallback) {
        ItemStack item = filter.item();
        if (item != null && !item.isEmpty() && item.hasCustomHoverName()) {
            String name = item.getHoverName().getString();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return fallback;
    }

    @Unique
    private void vault_Filters$importFromClipboard() {
        boolean merge = FilterUiUtils.isShiftDownSafe();
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.empty").withStyle(ChatFormatting.RED));
            return;
        }
        if (clipboard.length() > FilterUiUtils.MAX_IMPORT_CHARS) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.too_large", FilterUiUtils.MAX_IMPORT_CHARS).withStyle(ChatFormatting.RED));
            return;
        }

        try {
            JsonElement parsed = JsonParser.parseString(clipboard);
            if (!parsed.isJsonObject()) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.invalid").withStyle(ChatFormatting.RED));
                return;
            }

            JsonObject root = parsed.getAsJsonObject();
            if (root.has("format")) {
                if (!root.get("format").isJsonPrimitive()) {
                    FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.version").withStyle(ChatFormatting.RED));
                    return;
                }

                String format = root.get("format").getAsString();
                if (!FilterUiUtils.LIST_FORMAT_V2.equals(format) && !FilterUiUtils.LIST_FORMAT_V1.equals(format) && !FilterUiUtils.LIST_FORMAT_SIMPLIFIED.equals(format)) {
                    FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.version").withStyle(ChatFormatting.RED));
                    return;
                }
            }

            String importedName = null;
            if (root.has("name") && root.get("name").isJsonPrimitive()) {
                importedName = root.get("name").getAsString();
            }

            if (!root.has("filter") || !root.get("filter").isJsonObject()) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.invalid").withStyle(ChatFormatting.RED));
                return;
            }

            JsonObject filterObj = root.getAsJsonObject("filter");
            if (!filterObj.has("items") || !filterObj.get("items").isJsonArray()) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.invalid").withStyle(ChatFormatting.RED));
                return;
            }

            FilterPayloadUtils.ListSettings listSettings = FilterPayloadUtils.readListSettings(filterObj);

            JsonArray items = filterObj.getAsJsonArray("items");
            if (!FilterPayloadUtils.validateNestedListImport(items)) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.invalid").withStyle(ChatFormatting.RED));
                return;
            }
            ItemStack contentHolder = ((AbstractFilterMenu) this.menu).contentHolder;
            ItemStackHandler handler = FilterItem.getFilterItems(contentHolder);
            int slotCount = handler.getSlots();

            List<CompoundTag> importTags = new ArrayList<>();
            int invalid = 0;
            for (JsonElement elem : items) {
                if (!elem.isJsonObject()) {
                    invalid++;
                    continue;
                }
                try {
                    CompoundTag tag = FilterPayloadUtils.buildFilterTagFromJson(elem.getAsJsonObject());
                    if (tag != null) {
                        importTags.add(tag);
                    } else {
                        invalid++;
                    }
                } catch (Exception e) {
                    invalid++;
                }
            }

            if (importTags.isEmpty()) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.none").withStyle(ChatFormatting.RED));
                return;
            }

            if (listSettings.hasBlacklist()) {
                menuAccessor.vault_filters$setBlacklist(listSettings.blacklist());
                AllPackets.getChannel().sendToServer(new FilterScreenPacket(listSettings.blacklist() ? FilterScreenPacket.Option.BLACKLIST : FilterScreenPacket.Option.WHITELIST, new CompoundTag()));
            }
            if (listSettings.hasRespectNBT()) {
                menuAccessor.vault_filters$setRespectNBT(listSettings.respectNBT());
                AllPackets.getChannel().sendToServer(new FilterScreenPacket(listSettings.respectNBT() ? FilterScreenPacket.Option.RESPECT_DATA : FilterScreenPacket.Option.IGNORE_DATA, new CompoundTag()));
            }
            if (listSettings.hasMatchAll()) {
                menuAccessor.vault_filters$setMatchAll(listSettings.matchAll());
                VFMessages.VFCHANNEL.sendToServer(new MenuFeaturesPacket(listSettings.matchAll() ? MenuFeaturesPacket.MenuAction.MATCH_ALL : MenuFeaturesPacket.MenuAction.MATCH_ANY));
            }

            if (!merge) {
                this.menu.clearContents();
            }

            if (importedName != null) {
                FilterUiUtils.applyImportedFilterName((AbstractFilterMenu) this.menu, importedName);
            }

            if (!merge) {
                CompoundTag emptyTag = ItemStack.EMPTY.serializeNBT();
                for (int slot = 0; slot < slotCount; slot++) {
                    vault_Filters$sendFilterSlotUpdate(slot, emptyTag);
                    handler.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }

            int imported = 0;
            int slotIndex = merge ? vault_Filters$findFirstEmptySlot(handler) : 0;
            for (CompoundTag itemTag : importTags) {
                while (slotIndex < slotCount && !handler.getStackInSlot(slotIndex).isEmpty()) {
                    slotIndex++;
                }
                if (slotIndex >= slotCount) {
                    break;
                }

                vault_Filters$sendFilterSlotUpdate(slotIndex, itemTag);
                handler.setStackInSlot(slotIndex, ItemStack.of(itemTag));
                imported++;
                slotIndex++;
            }

            if (imported == 0) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.none").withStyle(ChatFormatting.RED));
                return;
            }

            if (merge) {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.imported.merge", imported, invalid).withStyle(ChatFormatting.GREEN));
            } else {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.imported.replace", imported, invalid).withStyle(ChatFormatting.GREEN));
            }
        } catch (Exception ignored) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.invalid").withStyle(ChatFormatting.RED));
        }
    }

    @Unique
    private int vault_Filters$findFirstEmptySlot(ItemStackHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return handler.getSlots();
    }

    @Unique
    private void vault_Filters$sendFilterSlotUpdate(int slot, CompoundTag itemTag) {
        CompoundTag packetData = new CompoundTag();
        packetData.putInt("Slot", slot);
        packetData.put("Item", itemTag.copy());
        AllPackets.getChannel().sendToServer(new FilterScreenPacket(FilterScreenPacket.Option.UPDATE_FILTER_ITEM, packetData));
    }

    @Unique
    private List<FilterItemStack> vault_Filters$getCurrentFilters() {
        List<FilterItemStack> filters = new ArrayList<>();
        ItemStack contentHolder = ((AbstractFilterMenu) this.menu).contentHolder;
        ItemStackHandler handler = FilterItem.getFilterItems(contentHolder);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                filters.add(FilterItemStack.of(stack));
            }
        }
        return filters;
    }

}
