package net.joseph.vaultfilters.mixin.compat.create;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.AllItems;
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
import net.joseph.vaultfilters.access.AbstractFilterMenuAdvancedAccessor;
import net.joseph.vaultfilters.access.FilterMenuAdvancedAccessor;
import net.joseph.vaultfilters.network.MenuFeaturesPacket;
import net.joseph.vaultfilters.network.VFMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private static final Gson vault_Filters$GSON = new GsonBuilder().disableHtmlEscaping().create();
    @Unique
    private static final String vault_Filters$LIST_FORMAT_KEY = "vaultfilters.list_filter.v1";
    @Unique
    private Button vault_Filters$exportButton;
    @Unique
    private Button vault_Filters$importButton;

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

        addRenderableWidget(vault_Filters$exportButton);
        addRenderableWidget(vault_Filters$importButton);
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
        boolean pretty = Screen.hasShiftDown();
        List<FilterItemStack> filters = vault_Filters$getCurrentFilters();

        JsonObject root = new JsonObject();
        root.addProperty("format", vault_Filters$LIST_FORMAT_KEY);
        String currentName = vault_Filters$getCurrentFilterName();
        if (currentName != null && !currentName.isEmpty()) {
            root.addProperty("name", currentName);
        }
        JsonObject filterObj = new JsonObject();
        filterObj.addProperty("isBlacklist", menuAccessor.vault_filters$isBlacklist());
        filterObj.addProperty("shouldRespectNBT", menuAccessor.vault_filters$shouldRespectNBT());
        filterObj.addProperty("matchAll", menuAccessor.vault_filters$getMatchAll());

        JsonArray items = new JsonArray();
        for (FilterItemStack filter : filters) {
            JsonObject item = vault_Filters$serializeFilterItem(filter);
            if (item != null) {
                items.add(item);
            }
        }

        filterObj.add("items", items);
        root.add("filter", filterObj);

        String exportJson = pretty ? new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(root) : vault_Filters$GSON.toJson(root);
        Minecraft.getInstance().keyboardHandler.setClipboard(exportJson);
        vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.exported", filters.size()).withStyle(ChatFormatting.GREEN));
    }

    @Unique
    private JsonObject vault_Filters$serializeFilterItem(FilterItemStack filter) {
        JsonObject obj = new JsonObject();
        CompoundTag serializedTag = filter.serializeNBT();
        if (serializedTag != null) {
            obj.addProperty("nbt", serializedTag.toString());
        }

        if (filter instanceof FilterItemStack.AttributeFilterItemStack attrFilter) {
            obj.addProperty("type", "attribute_filter");
            obj.addProperty("inverted", false);
        } else if (filter instanceof FilterItemStack.ListFilterItemStack listFilter) {
            obj.addProperty("type", "list_filter");
            JsonArray nestedItems = new JsonArray();
            for (FilterItemStack item : listFilter.containedItems) {
                JsonObject nested = vault_Filters$serializeFilterItem(item);
                if (nested != null) nestedItems.add(nested);
            }
            obj.add("items", nestedItems);
            obj.addProperty("isBlacklist", listFilter.isBlacklist);
            obj.addProperty("shouldRespectNBT", listFilter.shouldRespectNBT);
            if (listFilter.item().hasTag()) {
                obj.addProperty("matchAll", listFilter.item().getTag().getBoolean("MatchAll"));
            }
        } else if (!filter.isEmpty()) {
            obj.addProperty("type", "item_filter");
        } else {
            return null;
        }

        return obj;
    }

    @Unique
    private void vault_Filters$importFromClipboard() {
        boolean merge = Screen.hasShiftDown();
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.empty").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            JsonElement parsed = JsonParser.parseString(clipboard);
            if (!parsed.isJsonObject()) {
                vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.invalid").withStyle(ChatFormatting.RED));
                return;
            }

            JsonObject root = parsed.getAsJsonObject();
            if (root.has("format")) {
                if (!root.get("format").isJsonPrimitive() || !vault_Filters$LIST_FORMAT_KEY.equals(root.get("format").getAsString())) {
                    vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.version").withStyle(ChatFormatting.RED));
                    return;
                }
            }

            String importedName = null;
            if (root.has("name") && root.get("name").isJsonPrimitive()) {
                importedName = root.get("name").getAsString();
            }

            if (!root.has("filter") || !root.get("filter").isJsonObject()) {
                vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.invalid").withStyle(ChatFormatting.RED));
                return;
            }

            JsonObject filterObj = root.getAsJsonObject("filter");
            if (!filterObj.has("items") || !filterObj.get("items").isJsonArray()) {
                vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.invalid").withStyle(ChatFormatting.RED));
                return;
            }

            boolean hasIsBlacklist = filterObj.has("isBlacklist") && filterObj.get("isBlacklist").isJsonPrimitive();
            boolean hasShouldRespectNBT = filterObj.has("shouldRespectNBT") && filterObj.get("shouldRespectNBT").isJsonPrimitive();
            boolean hasMatchAll = filterObj.has("matchAll") && filterObj.get("matchAll").isJsonPrimitive();
            boolean isBlacklist = hasIsBlacklist && filterObj.get("isBlacklist").getAsBoolean();
            boolean shouldRespectNBT = hasShouldRespectNBT && filterObj.get("shouldRespectNBT").getAsBoolean();
            boolean matchAll = hasMatchAll && filterObj.get("matchAll").getAsBoolean();

            JsonArray items = filterObj.getAsJsonArray("items");
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
                CompoundTag tag = vault_Filters$buildFilterTagFromJson(elem.getAsJsonObject());
                if (tag != null) {
                    importTags.add(tag);
                } else {
                    invalid++;
                }
            }

            if (importTags.isEmpty()) {
                vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.none").withStyle(ChatFormatting.RED));
                return;
            }

            if (hasIsBlacklist) {
                menuAccessor.vault_filters$setBlacklist(isBlacklist);
                AllPackets.getChannel().sendToServer(new FilterScreenPacket(isBlacklist ? FilterScreenPacket.Option.BLACKLIST : FilterScreenPacket.Option.WHITELIST, new CompoundTag()));
            }
            if (hasShouldRespectNBT) {
                menuAccessor.vault_filters$setRespectNBT(shouldRespectNBT);
                AllPackets.getChannel().sendToServer(new FilterScreenPacket(shouldRespectNBT ? FilterScreenPacket.Option.RESPECT_DATA : FilterScreenPacket.Option.IGNORE_DATA, new CompoundTag()));
            }
            if (hasMatchAll) {
                menuAccessor.vault_filters$setMatchAll(matchAll);
                VFMessages.VFCHANNEL.sendToServer(new MenuFeaturesPacket(matchAll ? MenuFeaturesPacket.MenuAction.MATCH_ALL : MenuFeaturesPacket.MenuAction.MATCH_ANY));
            }

            if (!merge) {
                this.menu.clearContents();
            }

            if (importedName != null) {
                vault_Filters$applyImportedFilterName(importedName);
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
                vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.none").withStyle(ChatFormatting.RED));
                return;
            }

            if (merge) {
                vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.imported.merge", imported, invalid).withStyle(ChatFormatting.GREEN));
            } else {
                vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.imported.replace", imported, invalid).withStyle(ChatFormatting.GREEN));
            }
        } catch (Exception ignored) {
            vault_Filters$notifyUser(new TranslatableComponent("vaultfilters.gui.list_filter.import.invalid").withStyle(ChatFormatting.RED));
        }
    }

    @Unique
    private CompoundTag vault_Filters$buildFilterTagFromJson(JsonObject obj) throws Exception {
        if (!obj.has("type") || !obj.get("type").isJsonPrimitive()) return null;
        String type = obj.get("type").getAsString();

        if (("attribute_filter".equals(type) || "item_filter".equals(type)) && obj.has("nbt")) {
            return TagParser.parseTag(obj.get("nbt").getAsString());
        }

        if (!"list_filter".equals(type)) {
            return null;
        }

        if (obj.has("nbt")) {
            return TagParser.parseTag(obj.get("nbt").getAsString());
        }

        if (!obj.has("items") || !obj.get("items").isJsonArray()) {
            return null;
        }

        ItemStack listStack = AllItems.FILTER.asStack();
        CompoundTag listTag = listStack.getOrCreateTag();
        if (obj.has("isBlacklist") && obj.get("isBlacklist").isJsonPrimitive()) {
            listTag.putBoolean("Blacklist", obj.get("isBlacklist").getAsBoolean());
        }
        if (obj.has("shouldRespectNBT") && obj.get("shouldRespectNBT").isJsonPrimitive()) {
            listTag.putBoolean("RespectNBT", obj.get("shouldRespectNBT").getAsBoolean());
        }
        if (obj.has("matchAll") && obj.get("matchAll").isJsonPrimitive()) {
            listTag.putBoolean("MatchAll", obj.get("matchAll").getAsBoolean());
        }

        ItemStackHandler handler = FilterItem.getFilterItems(listStack);
        JsonArray nested = obj.getAsJsonArray("items");
        int slot = 0;
        for (JsonElement nestedElement : nested) {
            if (!(nestedElement instanceof JsonObject nestedObject)) {
                continue;
            }
            CompoundTag childTag = vault_Filters$buildFilterTagFromJson(nestedObject);
            if (childTag == null) {
                continue;
            }
            if (slot >= handler.getSlots()) {
                break;
            }
            handler.setStackInSlot(slot++, ItemStack.of(childTag));
        }

        return listStack.serializeNBT();
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

    @Unique
    private void vault_Filters$notifyUser(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, true);
        }
    }

    @Unique
    private String vault_Filters$getCurrentFilterName() {
        return ((AbstractFilterMenuAdvancedAccessor) (AbstractFilterMenu) this.menu).vault_filters$getName();
    }

    @Unique
    private void vault_Filters$applyImportedFilterName(String importedName) {
        if (!vault_Filters$shouldApplyImportedName(importedName)) {
            return;
        }

        String normalizedName = importedName == null ? "" : importedName.trim();
        if (normalizedName.length() > 35) {
            normalizedName = normalizedName.substring(0, 35);
        }

        ((AbstractFilterMenuAdvancedAccessor) (AbstractFilterMenu) this.menu).vault_filters$setName(normalizedName);
        VFMessages.VFCHANNEL.sendToServer(new MenuFeaturesPacket(MenuFeaturesPacket.MenuAction.CHANGE_NAME, normalizedName));
    }

    @Unique
    private boolean vault_Filters$shouldApplyImportedName(String importedName) {
        if (importedName == null) {
            return false;
        }

        String normalizedName = importedName.trim();
        if (normalizedName.isEmpty()) {
            return false;
        }

        ItemStack contentHolder = ((AbstractFilterMenu) this.menu).contentHolder;
        String defaultName = new ItemStack(contentHolder.getItem()).getHoverName().getString();
        return !normalizedName.equals(defaultName);
    }
}
