package net.joseph.vaultfilters.mixin.compat.create;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    private static final Gson vault_Filters$GSON = new GsonBuilder().disableHtmlEscaping().create();
    @Unique
    private static final Gson vault_Filters$PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    @Unique
    private static final String vault_Filters$FORMAT_KEY = "vaultfilters.attribute_filter.v1";
    @Unique
    private static final String vault_Filters$ATTRIBUTES_KEY = "attributes";
    @Unique
    private static final String vault_Filters$FORMAT_FIELD = "format";
    @Unique
    private static final int vault_Filters$MAX_IMPORT_CHARS = 262_144;

    @Unique
    private Button vault_Filters$exportButton;
    @Unique
    private Button vault_Filters$importButton;
    @Unique
    private Button vault_Filters$exportTreeButton;
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
        boolean pretty = FilterUiUtils.isShiftDownSafe();
        List<Pair<ItemAttribute, Boolean>> currentAttributes = new ArrayList<>(((AttributeFilterMenuAccessor) this.menu).getSelectedAttributes());
        JsonObject root = new JsonObject();
        root.addProperty(vault_Filters$FORMAT_FIELD, vault_Filters$FORMAT_KEY);
        root.addProperty("isBlacklist", vault_Filters$isAttributeFilterBlacklist());
        String currentName = FilterUiUtils.getCurrentFilterName((AbstractFilterMenu) this.menu);
        if (currentName != null && !currentName.isEmpty()) {
            root.addProperty("name", currentName);
        }
        JsonArray attributes = new JsonArray();

        for (Pair<ItemAttribute, Boolean> pair : currentAttributes) {
            CompoundTag tag = new CompoundTag();
            pair.getFirst().serializeNBT(tag);
            JsonObject entry = new JsonObject();
            entry.addProperty("inverted", pair.getSecond());
            entry.addProperty("nbt", tag.toString());
            attributes.add(entry);
        }

        root.add(vault_Filters$ATTRIBUTES_KEY, attributes);
        try {
            String exportJson = pretty ? vault_Filters$PRETTY_GSON.toJson(root) : vault_Filters$GSON.toJson(root);
            Minecraft.getInstance().keyboardHandler.setClipboard(exportJson);
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.exported", currentAttributes.size()).withStyle(ChatFormatting.GREEN));
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
        if (clipboard.length() > vault_Filters$MAX_IMPORT_CHARS) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.too_large", vault_Filters$MAX_IMPORT_CHARS).withStyle(ChatFormatting.RED));
            return;
        }

        JsonArray array;
        String importedName = null;
        boolean hasImportedBlacklist = false;
        boolean importedBlacklist = false;
        try {
            JsonElement parsed = JsonParser.parseString(clipboard);
            if (parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                if (root.has(vault_Filters$FORMAT_FIELD)) {
                    String format = root.get(vault_Filters$FORMAT_FIELD).isJsonPrimitive() ? root.get(vault_Filters$FORMAT_FIELD).getAsString() : "";
                    if (!vault_Filters$FORMAT_KEY.equals(format)) {
                        FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.version", format).withStyle(ChatFormatting.RED));
                        return;
                    }
                }
                if (!root.has(vault_Filters$ATTRIBUTES_KEY) || !root.get(vault_Filters$ATTRIBUTES_KEY).isJsonArray()) {
                    FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.invalid").withStyle(ChatFormatting.RED));
                    return;
                }
                if (root.has("name") && root.get("name").isJsonPrimitive()) {
                    importedName = root.get("name").getAsString();
                }
                if (root.has("isBlacklist") && root.get("isBlacklist").isJsonPrimitive()) {
                    hasImportedBlacklist = true;
                    importedBlacklist = root.get("isBlacklist").getAsBoolean();
                }
                array = root.getAsJsonArray(vault_Filters$ATTRIBUTES_KEY);
            } else if (parsed.isJsonArray()) {
                array = parsed.getAsJsonArray();
            } else {
                FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.invalid").withStyle(ChatFormatting.RED));
                return;
            }
        } catch (Exception ignored) {
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.import.invalid").withStyle(ChatFormatting.RED));
            return;
        }

        List<Pair<ItemAttribute, Boolean>> imported = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        int invalid = 0;
        int duplicates = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                invalid++;
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("nbt") || !entry.get("nbt").isJsonPrimitive()) {
                invalid++;
                continue;
            }

            String nbtData = entry.get("nbt").getAsString();
            boolean inverted = entry.has("inverted") && entry.get("inverted").getAsBoolean();
            try {
                CompoundTag tag = TagParser.parseTag(nbtData);
                ItemAttribute attribute = ItemAttribute.fromNBT(tag);
                if (attribute == null) {
                    invalid++;
                    continue;
                }
                String dedupeKey = (inverted ? "1:" : "0:") + tag;
                if (!dedupe.add(dedupeKey)) {
                    duplicates++;
                    continue;
                }
                imported.add(Pair.of(attribute, inverted));
            } catch (Exception ignored) {
                invalid++;
            }
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

        if (hasImportedBlacklist) {
            vault_Filters$setAttributeFilterBlacklist(importedBlacklist);
            AllPackets.getChannel().sendToServer(new FilterScreenPacket(importedBlacklist ? FilterScreenPacket.Option.BLACKLIST : FilterScreenPacket.Option.WHITELIST, new CompoundTag()));
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
        boolean pretty = FilterUiUtils.isShiftDownSafe();
        JsonObject root = new JsonObject();
        root.addProperty(vault_Filters$FORMAT_FIELD, vault_Filters$FORMAT_KEY);
        JsonArray attributes = new JsonArray();

        for (ItemAttribute attribute : attributesOfItem) {
            CompoundTag tag = new CompoundTag();
            attribute.serializeNBT(tag);
            JsonObject entry = new JsonObject();
            entry.addProperty("inverted", false);
            entry.addProperty("nbt", tag.toString());
            attributes.add(entry);
        }

        root.add(vault_Filters$ATTRIBUTES_KEY, attributes);
        try {
            String exportJson = pretty ? vault_Filters$PRETTY_GSON.toJson(root) : vault_Filters$GSON.toJson(root);
            Minecraft.getInstance().keyboardHandler.setClipboard(exportJson);
            FilterUiUtils.notifyUser(new TranslatableComponent("vaultfilters.gui.attribute_filter.export_available.copied", attributesOfItem.size()).withStyle(ChatFormatting.GREEN));
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
            if (rootName == null || rootName.isBlank()) {
                rootName = "Attribute Filter";
            }

            tree.append(rootName)
                    .append(" (")
                    .append(vault_Filters$isAttributeFilterBlacklist() ? "Deny" : "Allow")
                    .append(")\n");

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
    private boolean vault_Filters$isAttributeFilterBlacklist() {
        ItemStack contentHolder = ((AbstractFilterMenu) this.menu).contentHolder;
        CompoundTag tag = contentHolder.getTag();
        if (tag == null || !tag.contains("WhitelistMode")) {
            return false;
        }
        return !tag.getBoolean("WhitelistMode");
    }

    @Unique
    private void vault_Filters$setAttributeFilterBlacklist(boolean blacklist) {
        ItemStack contentHolder = ((AbstractFilterMenu) this.menu).contentHolder;
        contentHolder.getOrCreateTag().putBoolean("WhitelistMode", !blacklist);
    }

}
