package net.joseph.vaultfilters.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.UIRenderHelper;
import net.joseph.vaultfilters.library.FilterLibraryStore;
import net.joseph.vaultfilters.library.SavedFilter;
import net.joseph.vaultfilters.library.SavedFilterType;
import net.joseph.vaultfilters.util.FilterPayloadUtils;
import net.joseph.vaultfilters.util.FilterUiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

public class FilterLibraryScreen extends Screen {
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 250;

    private int listLeft;
    private int listTop;
    private int listRight;
    private int listBottom;
    private static final int ROW_HEIGHT = 24;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_MIN_HEIGHT = 12;

    private int scrollOffset;
    private UUID selectedId;
    private Button importButton;
    private Button renameButton;
    private Button duplicateButton;
    private Button exportButton;
    private Button treeButton;
    private Button deleteButton;
    private Button applyReplaceButton;
    private Button applyMergeButton;

    private EditBox searchBox;
    private Button sortButton;
    private Button showAllButton;
    private FilterLibraryStore.SortMode currentSort = FilterLibraryStore.SortMode.NAME_ASC;
    private boolean showOtherType;

    private List<SavedFilter> allFilters = new ArrayList<>();
    private List<SavedFilter> filters = new ArrayList<>();
    private boolean deleteConfirming;
    private UUID deleteTarget;
    private long deleteConfirmStart;
    private boolean renaming;
    private EditBox renameBox;
    private Button renameConfirmButton;
    private Button renameCancelButton;
    private SavedFilter renameTarget;
    private String statusMessage;
    private long statusMessageUntil;

    private static final int TOOLTIP_DELAY_TICKS = 20;
    private final Map<Button, String> tooltipMap = new IdentityHashMap<>();
    private @Nullable Button renderHoveredButton;
    private @Nullable Button tickHoveredButton;
    private int tooltipHoverTicks;
    private @Nullable UUID renderHoveredRowId;
    private @Nullable UUID tickHoveredRowId;
    private int rowHoverTicks;
    private @Nullable List<Component> cachedPreviewLines;
    private @Nullable UUID cachedPreviewId;

    @Nullable
    private final Screen parentScreen;
    @Nullable
    private final SavedFilterType contextType;
    @Nullable
    private final BiConsumer<JsonObject, Boolean> onApply;

    public FilterLibraryScreen() {
        this(null, null, null);
    }

    public FilterLibraryScreen(@Nullable Screen parentScreen, @Nullable SavedFilterType contextType,
            @Nullable BiConsumer<JsonObject, Boolean> onApply) {
        super(new TranslatableComponent("vaultfilters.screen.filter_library"));
        this.parentScreen = parentScreen;
        this.contextType = contextType;
        this.onApply = onApply;
    }

    @Override
    public void onClose() {
        if (parentScreen != null) {
            Minecraft.getInstance().setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        super.init();
        showOtherType = false;
        currentSort = FilterLibraryStore.SortMode.NAME_ASC;

        if (contextType != null) {
            allFilters = FilterLibraryStore.listByType(contextType);
        } else {
            allFilters = FilterLibraryStore.list();
        }
        applySearchAndSort();

        int x = (width - GUI_WIDTH) / 2;
        int y = (height - GUI_HEIGHT) / 2;

        listLeft = x + 10;
        listTop = y + 52;
        listRight = x + GUI_WIDTH - 10;
        listBottom = y + GUI_HEIGHT - 58;
        scrollOffset = 0;
        selectedId = null;

        searchBox = new EditBox(font, x + 10, y + 20, 140, 12, new TextComponent(""));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setResponder(s -> {
            applySearchAndSort();
            scrollOffset = 0;
        });
        addRenderableWidget(searchBox);

        sortButton = addRenderableWidget(createTooltipButton(x + 155, y + 19, 39, 14,
                new TextComponent(sortLabel()), b -> cycleSort(),
                "vaultfilters.gui.library.tooltip.sort"));

        if (contextType != null) {
            showAllButton = addRenderableWidget(createTooltipButton(x + 198, y + 19, 48, 14,
                    new TranslatableComponent("vaultfilters.gui.library.show_all"), b -> toggleShowAll(),
                    "vaultfilters.gui.library.tooltip.show_all"));
        }

        int btnW = 36;
        int btnH = 16;
        int gap = 2;
        int groupGap = 4;
        boolean hasApply = onApply != null && contextType != null;

        int row2Y = y + GUI_HEIGHT - 26;
        int mgmtCount = 6;
        int mgmtTotalW = mgmtCount * btnW + (mgmtCount - 1) * gap + 2 * groupGap;
        int mgmtStartX = x + (GUI_WIDTH - mgmtTotalW) / 2;

        int idx = 0;
        importButton = addRenderableWidget(createTooltipButton(mgmtStartX + idx * (btnW + gap), row2Y, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.import"), b -> onImport(),
                "vaultfilters.gui.library.tooltip.import"));
        idx++;
        renameButton = addRenderableWidget(
                createTooltipButton(mgmtStartX + idx * (btnW + gap) + groupGap, row2Y, btnW, btnH,
                        new TranslatableComponent("vaultfilters.gui.library.rename"), b -> onRename(),
                        "vaultfilters.gui.library.tooltip.rename"));
        idx++;
        duplicateButton = addRenderableWidget(
                createTooltipButton(mgmtStartX + idx * (btnW + gap) + groupGap, row2Y, btnW, btnH,
                        new TranslatableComponent("vaultfilters.gui.library.duplicate"), b -> onDuplicate(),
                        "vaultfilters.gui.library.tooltip.duplicate"));
        idx++;
        exportButton = addRenderableWidget(
                createTooltipButton(mgmtStartX + idx * (btnW + gap) + 2 * groupGap, row2Y, btnW, btnH,
                        new TranslatableComponent("vaultfilters.gui.library.export"), b -> onExport(),
                        "vaultfilters.gui.library.tooltip.export"));
        idx++;
        treeButton = addRenderableWidget(
                createTooltipButton(mgmtStartX + idx * (btnW + gap) + 2 * groupGap, row2Y, btnW, btnH,
                        new TranslatableComponent("vaultfilters.gui.library.tree"), b -> onTree(),
                        "vaultfilters.gui.library.tooltip.tree"));
        idx++;
        deleteButton = addRenderableWidget(
                createTooltipButton(mgmtStartX + idx * (btnW + gap) + 2 * groupGap, row2Y, btnW, btnH,
                        new TranslatableComponent("vaultfilters.gui.library.delete"), b -> onDelete(),
                        "vaultfilters.gui.library.tooltip.delete"));

        if (hasApply) {
            int row1Y = y + GUI_HEIGHT - 46;
            int applyBtnW = 110;
            int applyTotalW = 2 * applyBtnW + 4;
            int applyStartX = x + (GUI_WIDTH - applyTotalW) / 2;
            applyReplaceButton = addRenderableWidget(createTooltipButton(applyStartX, row1Y, applyBtnW, btnH,
                    new TranslatableComponent("vaultfilters.gui.library.apply_replace"), b -> onApplyFilter(false),
                    "vaultfilters.gui.library.tooltip.apply_replace"));
            applyMergeButton = addRenderableWidget(
                    createTooltipButton(applyStartX + applyBtnW + 4, row1Y, applyBtnW, btnH,
                            new TranslatableComponent("vaultfilters.gui.library.apply_merge"), b -> onApplyFilter(true),
                            "vaultfilters.gui.library.tooltip.apply_merge"));
        }

        updateButtonStates();
    }

    private Button createTooltipButton(int x, int y, int w, int h, Component message, Button.OnPress onPress,
            String tooltipKey) {
        Button btn = new Button(x, y, w, h, message, onPress, Button.NO_TOOLTIP);
        tooltipMap.put(btn, tooltipKey);
        return btn;
    }

    private void applySearchAndSort() {
        List<SavedFilter> base = allFilters;
        String query = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        if (!query.isEmpty()) {
            base = new ArrayList<>();
            for (SavedFilter sf : allFilters) {
                if (sf.name().toLowerCase().contains(query)) {
                    base.add(sf);
                }
            }
        }
        currentSort.sort(base);
        filters = base;
    }

    private String sortLabel() {
        return switch (currentSort) {
            case NAME_ASC -> "A-Z";
            case NEWEST_FIRST -> "New";
            case OLDEST_FIRST -> "Old";
        };
    }

    private void cycleSort() {
        FilterLibraryStore.SortMode[] values = FilterLibraryStore.SortMode.values();
        currentSort = values[(currentSort.ordinal() + 1) % values.length];
        sortButton.setMessage(new TextComponent(sortLabel()));
        applySearchAndSort();
        scrollOffset = 0;
    }

    private void toggleShowAll() {
        showOtherType = !showOtherType;
        if (showOtherType) {
            allFilters = FilterLibraryStore.list();
        } else if (contextType != null) {
            allFilters = FilterLibraryStore.listByType(contextType);
        }
        if (showAllButton != null) {
            showAllButton.setMessage(new TranslatableComponent(
                    showOtherType ? "vaultfilters.gui.library.hide_other" : "vaultfilters.gui.library.show_all"));
        }
        applySearchAndSort();
        scrollOffset = 0;
    }

    private void onApplyFilter(boolean merge) {
        SavedFilter sel = selectedFilter();
        if (sel == null || onApply == null || contextType == null)
            return;
        if (sel.type() != contextType) {
            setStatus("Cannot apply: filter type mismatch");
            return;
        }
        onApply.accept(sel.payload(), merge);
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (searchBox != null)
            searchBox.tick();
        if (deleteConfirming && System.currentTimeMillis() - deleteConfirmStart > 5000) {
            deleteConfirming = false;
            deleteTarget = null;
            updateDeleteButton();
        }

        // Button tooltip delay tracking
        if (renderHoveredButton != null && renderHoveredButton == tickHoveredButton) {
            tooltipHoverTicks++;
        } else {
            tickHoveredButton = renderHoveredButton;
            tooltipHoverTicks = 0;
        }

        // Row hover delay tracking
        if (renderHoveredRowId != null && renderHoveredRowId.equals(tickHoveredRowId)) {
            rowHoverTicks++;
        } else {
            tickHoveredRowId = renderHoveredRowId;
            rowHoverTicks = 0;
            cachedPreviewLines = null;
            cachedPreviewId = null;
        }
    }

    private SavedFilter selectedFilter() {
        if (selectedId == null)
            return null;
        for (SavedFilter f : filters) {
            if (f.id().equals(selectedId))
                return f;
        }
        return null;
    }

    private void updateButtonStates() {
        SavedFilter sel = selectedFilter();
        boolean selected = sel != null;
        boolean renamingActive = renaming;
        if (applyReplaceButton != null) {
            applyReplaceButton.active = selected && !renamingActive && sel != null && contextType != null
                    && sel.type() == contextType;
        }
        if (applyMergeButton != null) {
            applyMergeButton.active = selected && !renamingActive && sel != null && contextType != null
                    && sel.type() == contextType;
        }
        importButton.active = !renamingActive;
        renameButton.active = selected && !renamingActive;
        duplicateButton.active = selected && !renamingActive;
        exportButton.active = selected && !renamingActive;
        treeButton.active = selected && !renamingActive;
        deleteButton.active = selected && !renamingActive;
        updateDeleteButton();
    }

    private void updateDeleteButton() {
        if (deleteConfirming) {
            deleteButton.setMessage(new TranslatableComponent("vaultfilters.gui.library.delete.confirm"));
        } else {
            deleteButton.setMessage(new TranslatableComponent("vaultfilters.gui.library.delete"));
        }
    }

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
        UIRenderHelper.drawStretched(ms, x + 4, y + GUI_HEIGHT - 3, GUI_WIDTH - 8, 3, 0,
                AllGuiTextures.BRASS_FRAME_BOTTOM);
        UIRenderHelper.drawStretched(ms, x, y + 4, 3, GUI_HEIGHT - 8, 0, AllGuiTextures.BRASS_FRAME_LEFT);
        UIRenderHelper.drawStretched(ms, x + GUI_WIDTH - 3, y + 4, 3, GUI_HEIGHT - 8, 0,
                AllGuiTextures.BRASS_FRAME_RIGHT);

        // Gold accent line at the top of the list panel
        fill(ms, x + 6, y + 17, x + GUI_WIDTH - 6, y + 18, 0xFFC99E3D);
    }

    @Override
    public void render(PoseStack ms, int mouseX, int mouseY, float partialTicks) {
        renderBackground(ms);
        renderBg(ms);
        super.render(ms, mouseX, mouseY, partialTicks);
        renderList(ms, mouseX, mouseY, partialTicks);
        renderScrollbar(ms);

        // Button tooltip delay: detect hovered button
        renderHoveredButton = null;
        for (var listener : this.children()) {
            if (listener instanceof Button btn && btn.isMouseOver(mouseX, mouseY) && tooltipMap.containsKey(btn)) {
                renderHoveredButton = btn;
                break;
            }
        }
        if (renderHoveredButton != null && tooltipHoverTicks >= TOOLTIP_DELAY_TICKS) {
            String key = tooltipMap.get(renderHoveredButton);
            if (key != null) {
                renderTooltip(ms, new TranslatableComponent(key), mouseX, mouseY);
            }
        }

        // Row hover delay: detect hovered row
        renderHoveredRowId = null;
        if (!renaming) {
            int panelHeight = listBottom - listTop;
            int visibleCount = panelHeight / ROW_HEIGHT;
            int end = Math.min(scrollOffset + visibleCount, filters.size());
            for (int i = scrollOffset; i < end; i++) {
                SavedFilter f = filters.get(i);
                int rowTop = listTop + (i - scrollOffset) * ROW_HEIGHT;
                int rowBottom = rowTop + ROW_HEIGHT;
                if (mouseX >= listLeft && mouseX <= listRight && mouseY >= rowTop && mouseY < rowBottom) {
                    renderHoveredRowId = f.id();
                    break;
                }
            }
        }
        if (renderHoveredRowId != null && rowHoverTicks >= TOOLTIP_DELAY_TICKS && !renaming) {
            if (!renderHoveredRowId.equals(cachedPreviewId)) {
                for (SavedFilter f : filters) {
                    if (f.id().equals(renderHoveredRowId)) {
                        cachedPreviewLines = buildFilterPreviewLines(f);
                        cachedPreviewId = renderHoveredRowId;
                        break;
                    }
                }
            }
            if (cachedPreviewLines != null) {
                renderTooltip(ms, cachedPreviewLines, Optional.empty(), mouseX, mouseY);
            }
        }

        drawCenteredString(ms, font, title, width / 2, (height - GUI_HEIGHT) / 2 + 5, 0xFFFFFF);

        String showingLabel;
        if (showOtherType) {
            showingLabel = "Showing: All";
        } else if (contextType == SavedFilterType.ATTRIBUTE_FILTER) {
            showingLabel = "Showing: Attribute Filters";
        } else if (contextType == SavedFilterType.LIST_FILTER) {
            showingLabel = "Showing: List Filters";
        } else {
            showingLabel = "Showing: All";
        }
        String countLabel = filters.size() + "/" + FilterLibraryStore.MAX_LIBRARY_ENTRIES;
        int panelX = (width - GUI_WIDTH) / 2;
        int panelY = (height - GUI_HEIGHT) / 2;
        font.draw(ms, showingLabel, panelX + 10, panelY + 38, 0xC0C0C0);
        font.draw(ms, countLabel, panelX + GUI_WIDTH - 10 - font.width(countLabel), panelY + 38, 0x808080);

        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            font.draw(ms, new TranslatableComponent("vaultfilters.gui.library.search"),
                    searchBox.x + 2, searchBox.y + 1, 0x606060);
        }

        if (statusMessage != null && System.currentTimeMillis() < statusMessageUntil) {
            drawCenteredString(ms, font, new TextComponent(statusMessage),
                    width / 2, (height - GUI_HEIGHT) / 2 + GUI_HEIGHT + 8, 0xFFFF55);
        }

        if (renaming && renameBox != null) {
            int rx = (width - 180) / 2;
            int ry = (height - GUI_HEIGHT) / 2 + GUI_HEIGHT / 2 - 20;
            fill(ms, rx - 4, ry - 4, rx + 184, ry + 44, 0xCC333333);
            renameBox.render(ms, mouseX, mouseY, partialTicks);
        }

        if (allFilters.isEmpty() && !renaming) {
            drawCenteredString(ms, font,
                    new TranslatableComponent("vaultfilters.screen.filter_library.empty"),
                    width / 2, listTop + 20, 0x808080);
        } else if (filters.isEmpty() && !renaming) {
            drawCenteredString(ms, font, new TextComponent("No matches"),
                    width / 2, listTop + 20, 0x808080);
        }
    }

    private void renderList(PoseStack ms, int mouseX, int mouseY, float partialTicks) {
        int panelHeight = listBottom - listTop;
        int visibleCount = panelHeight / ROW_HEIGHT;
        int count = filters.size();
        int end = Math.min(scrollOffset + visibleCount, count);

        for (int i = scrollOffset; i < end; i++) {
            SavedFilter filter = filters.get(i);
            int rowTop = listTop + (i - scrollOffset) * ROW_HEIGHT;
            int rowBottom = rowTop + ROW_HEIGHT;

            boolean hovering = mouseX >= listLeft && mouseX <= listRight && mouseY >= rowTop && mouseY < rowBottom;
            boolean isSelected = filter.id().equals(selectedId);

            if (isSelected) {
                fill(ms, listLeft, rowTop, listRight, rowBottom, 0x44AAAAAA);
            } else if (hovering) {
                fill(ms, listLeft, rowTop, listRight, rowBottom, 0x33FFFFFF);
            }

            font.draw(ms, filter.name(), listLeft + 4, rowTop + 2, 0xFFFFFF);

            String typeLabel = filter.type() == SavedFilterType.ATTRIBUTE_FILTER ? "Attr" : "List";
            font.draw(ms, typeLabel, listRight - 30, rowTop + 2, 0x808080);

            String timeStr = formatTime(filter.updatedAt());
            font.draw(ms, timeStr, listLeft + 4, rowTop + 12, 0x606060);
        }
    }

    private void renderScrollbar(PoseStack ms) {
        int panelHeight = listBottom - listTop;
        int visibleCount = panelHeight / ROW_HEIGHT;
        int totalCount = filters.size();
        if (totalCount <= visibleCount)
            return;

        int trackLeft = listRight + 1;
        int trackRight = trackLeft + SCROLLBAR_WIDTH;

        fill(ms, trackLeft, listTop, trackRight, listBottom, 0x33FFFFFF);

        float ratio = (float) visibleCount / totalCount;
        int thumbHeight = Math.max(SCROLLBAR_MIN_HEIGHT, (int) (panelHeight * ratio));
        int maxScroll = totalCount - visibleCount;
        float scrollFraction = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0f;
        int thumbTop = listTop + (int) ((panelHeight - thumbHeight) * scrollFraction);

        fill(ms, trackLeft, thumbTop, trackRight, thumbTop + thumbHeight, 0xAAFFFFFF);
    }

    private List<Component> buildFilterPreviewLines(SavedFilter sf) {
        List<Component> lines = new ArrayList<>();
        JsonObject payload = sf.payload();
        if (payload == null || !payload.isJsonObject()) {
            lines.add(new TextComponent("No preview available"));
            return lines;
        }
        if (sf.type() == SavedFilterType.ATTRIBUTE_FILTER) {
            buildAttributePreview(lines, payload);
        } else if (sf.type() == SavedFilterType.LIST_FILTER) {
            buildListPreview(lines, payload, 0);
        }
        return lines;
    }

    private void buildAttributePreview(List<Component> lines, JsonObject root) {
        FilterPayloadUtils.AttributeSettings settings = FilterPayloadUtils.readAttributeSettings(root);
        String mode = FilterPayloadUtils.getModeLabel(settings.blacklist(), settings.matchAll());
        lines.add(new TextComponent(mode).withStyle(ChatFormatting.GOLD));

        if (!root.has(FilterUiUtils.ATTRIBUTES_FIELD) ||
                !root.get(FilterUiUtils.ATTRIBUTES_FIELD).isJsonArray()) {
            return;
        }
        JsonArray array = root.getAsJsonArray(FilterUiUtils.ATTRIBUTES_FIELD);
        int shown = 0;
        for (JsonElement elem : array) {
            if (shown >= 8) {
                lines.add(new TextComponent("  ... (" + (array.size() - shown) + " more)")
                        .withStyle(ChatFormatting.GRAY));
                break;
            }
            if (!elem.isJsonObject())
                continue;
            JsonObject entry = elem.getAsJsonObject();
            boolean inverted = entry.has("inverted") && entry.get("inverted").getAsBoolean();

            JsonElement payload = null;
            if (entry.has("nbt") && entry.get("nbt").isJsonPrimitive()) {
                payload = entry.get("nbt");
            } else if (entry.has("attribute") && entry.get("attribute").isJsonObject()) {
                payload = entry.get("attribute");
            }
            if (payload == null)
                continue;

            try {
                CompoundTag tag;
                if (payload.isJsonPrimitive()) {
                    tag = TagParser.parseTag(payload.getAsString());
                } else {
                    tag = FilterPayloadUtils.jsonToCompoundTag(payload.getAsJsonObject());
                    tag = FilterPayloadUtils.expandFlattenedAttributeCompound(tag);
                }
                FilterUiUtils.TagEntry te = FilterUiUtils.firstSortedDataEntry(tag);
                String key = te == null ? "?" : te.key();
                String val = te == null ? "?"
                        : FilterUiUtils.normalizeAttributeSummary(key, FilterUiUtils.summarizeTag(te.value()));
                String prefix = inverted ? ChatFormatting.RED + "NOT " : "";
                lines.add(new TextComponent(prefix + ChatFormatting.WHITE + key
                        + ChatFormatting.GRAY + " = " + ChatFormatting.AQUA + val));
                shown++;
            } catch (Exception e) {
                lines.add(new TextComponent(ChatFormatting.RED + "invalid attribute"));
                shown++;
            }
        }
        if (array.size() == 0) {
            lines.add(new TextComponent("Empty filter").withStyle(ChatFormatting.GRAY));
        }
    }

    private void buildListPreview(List<Component> lines, JsonObject root, int depth) {
        if (depth > 2) {
            lines.add(new TextComponent("  ".repeat(depth) + "... (nested)").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (root.has("filter") && root.get("filter").isJsonObject()) {
            JsonObject filter = root.getAsJsonObject("filter");
            FilterPayloadUtils.ListSettings settings = FilterPayloadUtils.readListSettings(filter);
            String mode = FilterPayloadUtils.getModeLabel(settings.blacklist(), settings.matchAll());
            String nbt = settings.respectNBT() ? ", Respect NBT" : "";
            lines.add(new TextComponent("  ".repeat(depth) + mode + nbt)
                    .withStyle(ChatFormatting.GOLD));

            if (filter.has("items") && filter.get("items").isJsonArray()) {
                JsonArray items = filter.getAsJsonArray("items");
                int shown = 0;
                for (JsonElement elem : items) {
                    if (shown >= 6) {
                        lines.add(
                                new TextComponent("  ".repeat(depth + 1) + "... (" + (items.size() - shown) + " more)")
                                        .withStyle(ChatFormatting.GRAY));
                        break;
                    }
                    if (!elem.isJsonObject())
                        continue;
                    JsonObject item = elem.getAsJsonObject();

                    String indent = "  ".repeat(depth + 1);
                    if (item.has("type") && item.get("type").isJsonPrimitive()) {
                        String type = item.get("type").getAsString();
                        if ("list_filter".equals(type)) {
                            buildListPreview(lines, item, depth + 1);
                            shown++;
                            continue;
                        } else if ("attribute_filter".equals(type)) {
                            FilterPayloadUtils.AttributeSettings as = FilterPayloadUtils.readAttributeSettings(item);
                            String itemMode = FilterPayloadUtils.getModeLabel(as.blacklist(), as.matchAll());
                            int attrCount = 0;
                            if (item.has(FilterUiUtils.ATTRIBUTES_FIELD) &&
                                    item.get(FilterUiUtils.ATTRIBUTES_FIELD).isJsonArray()) {
                                attrCount = item.getAsJsonArray(FilterUiUtils.ATTRIBUTES_FIELD).size();
                            }
                            lines.add(new TextComponent(
                                    indent + "Attr Filter (" + itemMode + ", " + attrCount + " attrs)")
                                    .withStyle(ChatFormatting.AQUA));
                            shown++;
                            continue;
                        }
                    }

                    String name = item.has("name") && item.get("name").isJsonPrimitive()
                            ? item.get("name").getAsString()
                            : "Item";
                    lines.add(new TextComponent(indent + name).withStyle(ChatFormatting.WHITE));
                    shown++;
                }
                if (items.size() == 0) {
                    lines.add(new TextComponent("  ".repeat(depth + 1) + "Empty")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    private static String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60000)
            return "just now";
        if (diff < 3600000)
            return (diff / 60000) + "m ago";
        if (diff < 86400000)
            return (diff / 3600000) + "h ago";
        return (diff / 86400000) + "d ago";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int panelHeight = listBottom - listTop;
            int visibleCount = panelHeight / ROW_HEIGHT;
            int maxOffset = Math.max(0, filters.size() - visibleCount);
            scrollOffset = (int) Math.max(0, Math.min(maxOffset, scrollOffset - delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (renaming) {
            if (renameBox != null && renameBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int panelHeight = listBottom - listTop;
            int visibleCount = panelHeight / ROW_HEIGHT;
            int end = Math.min(scrollOffset + visibleCount, filters.size());
            for (int i = scrollOffset; i < end; i++) {
                int rowTop = listTop + (i - scrollOffset) * ROW_HEIGHT;
                int rowBottom = rowTop + ROW_HEIGHT;
                if (mouseY >= rowTop && mouseY < rowBottom) {
                    if (searchBox != null)
                        searchBox.changeFocus(false);
                    selectedId = filters.get(i).id();
                    updateButtonStates();
                    return true;
                }
            }
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        updateButtonStates();
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renaming) {
            if (keyCode == 256) {
                cancelRename();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                confirmRename();
                return true;
            }
            if (renameBox != null && renameBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return renameBox != null && renameBox.isFocused() || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == 256) {
                searchBox.changeFocus(false);
                return true;
            }
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renaming && renameBox != null && renameBox.isFocused()) {
            return renameBox.charTyped(codePoint, modifiers);
        }
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void setStatus(String msg) {
        statusMessage = msg;
        statusMessageUntil = System.currentTimeMillis() + 3000;
    }

    private void onImport() {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            setStatus("Clipboard is empty");
            return;
        }
        if (clipboard.length() > FilterUiUtils.MAX_IMPORT_CHARS) {
            setStatus("Clipboard payload too large (" + clipboard.length() + " chars, max "
                    + FilterUiUtils.MAX_IMPORT_CHARS + ")");
            return;
        }

        try {
            JsonObject obj = JsonParser.parseString(clipboard).getAsJsonObject();

            SavedFilterType savedType = null;
            if (obj.has("type") && obj.get("type").isJsonPrimitive()) {
                String type = obj.get("type").getAsString();
                if ("attribute_filter".equals(type)) {
                    savedType = SavedFilterType.ATTRIBUTE_FILTER;
                } else if ("list_filter".equals(type) || "item_filter".equals(type)) {
                    savedType = SavedFilterType.LIST_FILTER;
                }
            }
            if (savedType == null && obj.has("format") && obj.get("format").isJsonPrimitive()) {
                String format = obj.get("format").getAsString();
                if (format.startsWith("vaultfilters.attribute_filter.")) {
                    savedType = SavedFilterType.ATTRIBUTE_FILTER;
                } else if (format.startsWith("vaultfilters.list_filter.")) {
                    savedType = SavedFilterType.LIST_FILTER;
                }
            }
            if (savedType == null) {
                setStatus("Clipboard does not contain a valid filter payload");
                return;
            }

            String name = obj.has("name") && obj.get("name").isJsonPrimitive()
                    ? obj.get("name").getAsString()
                    : "Imported Filter";
            if (name.length() > 35)
                name = name.substring(0, 35);

            if (!FilterLibraryStore.canAddMore()) {
                setStatus("Library is full (" + FilterLibraryStore.MAX_LIBRARY_ENTRIES + " max)");
                return;
            }

            SavedFilter filter = SavedFilter.createNew(savedType, name, obj);
            FilterLibraryStore.upsert(filter);
            refreshList();
            setStatus("Imported " + (savedType == SavedFilterType.ATTRIBUTE_FILTER ? "Attribute Filter" : "List Filter")
                    + " \"" + name + "\"");
        } catch (Exception e) {
            setStatus("Invalid clipboard data: " + e.getMessage());
        }
    }

    private void onRename() {
        SavedFilter sel = selectedFilter();
        if (sel == null)
            return;
        beginRename(sel);
    }

    private void beginRename(SavedFilter filter) {
        renaming = true;
        renameTarget = filter;
        renameBox = new EditBox(font, (width - 176) / 2, (height - GUI_HEIGHT) / 2 + GUI_HEIGHT / 2 - 16, 176, 16,
                new TextComponent(""));
        renameBox.setMaxLength(35);
        renameBox.setValue(filter.name());
        renameBox.changeFocus(true);
        renameBox.setHighlightPos(filter.name().length());
        renameBox.setCursorPosition(filter.name().length());
        setFocused(renameBox);

        int y = renameBox.y + 20;
        int cx = (width - 80) / 2;
        renameConfirmButton = addRenderableWidget(new Button(cx, y, 38, 16,
                new TranslatableComponent("vaultfilters.gui.library.rename.confirm"), b -> confirmRename()));
        renameCancelButton = addRenderableWidget(new Button(cx + 42, y, 38, 16,
                new TranslatableComponent("vaultfilters.gui.library.rename.cancel"), b -> cancelRename()));

        if (applyReplaceButton != null)
            applyReplaceButton.active = false;
        if (applyMergeButton != null)
            applyMergeButton.active = false;
        importButton.active = false;
        renameButton.active = false;
        duplicateButton.active = false;
        exportButton.active = false;
        treeButton.active = false;
        deleteButton.active = false;
        if (searchBox != null)
            searchBox.setEditable(false);
        sortButton.active = false;
        if (showAllButton != null)
            showAllButton.active = false;
    }

    private void confirmRename() {
        if (renameTarget == null || renameBox == null)
            return;
        String newName = renameBox.getValue();
        if (newName == null || newName.isBlank()) {
            newName = renameTarget.name();
        }
        if (newName.length() > 35)
            newName = newName.substring(0, 35);
        FilterLibraryStore.rename(renameTarget.id(), newName);
        cleanupRename();
        refreshList();
        setStatus("Renamed to \"" + newName + "\"");
    }

    private void cancelRename() {
        cleanupRename();
        updateButtonStates();
    }

    private void cleanupRename() {
        renaming = false;
        renameTarget = null;
        if (renameBox != null) {
            removeWidget(renameBox);
            renameBox = null;
        }
        if (renameConfirmButton != null) {
            removeWidget(renameConfirmButton);
            renameConfirmButton = null;
        }
        if (renameCancelButton != null) {
            removeWidget(renameCancelButton);
            renameCancelButton = null;
        }
        if (searchBox != null)
            searchBox.setEditable(true);
        sortButton.active = true;
        if (showAllButton != null)
            showAllButton.active = true;
        setFocused(null);
    }

    private void onDuplicate() {
        SavedFilter sel = selectedFilter();
        if (sel != null) {
            SavedFilter copy = FilterLibraryStore.duplicate(sel.id());
            if (copy != null) {
                refreshList();
                setStatus("Duplicated as \"" + copy.name() + "\"");
            }
        }
    }

    private void onExport() {
        SavedFilter sel = selectedFilter();
        if (sel == null)
            return;

        try {
            String json = FilterUiUtils.PRETTY_GSON.toJson(sel.payload());
            Minecraft.getInstance().keyboardHandler.setClipboard(json);
            setStatus("Exported \"" + sel.name() + "\" to clipboard");
        } catch (Exception e) {
            setStatus("Export failed: " + e.getMessage());
        }
    }

    private void onTree() {
        SavedFilter sel = selectedFilter();
        if (sel == null)
            return;

        try {
            JsonObject obj = sel.payload().deepCopy();
            obj.addProperty("format", "tree");
            String json = FilterUiUtils.PRETTY_GSON.toJson(obj);
            Minecraft.getInstance().keyboardHandler.setClipboard(json);
            setStatus("Exported \"" + sel.name() + "\" tree to clipboard");
        } catch (Exception e) {
            setStatus("Tree export failed: " + e.getMessage());
        }
    }

    private void onDelete() {
        SavedFilter sel = selectedFilter();
        if (sel == null)
            return;

        if (!deleteConfirming || deleteTarget == null || !deleteTarget.equals(sel.id())) {
            deleteConfirming = true;
            deleteTarget = sel.id();
            deleteConfirmStart = System.currentTimeMillis();
            updateDeleteButton();
            return;
        }

        FilterLibraryStore.delete(deleteTarget);
        deleteConfirming = false;
        deleteTarget = null;
        refreshList();
        setStatus("Deleted filter");
    }

    void refreshList() {
        if (showOtherType || contextType == null) {
            allFilters = FilterLibraryStore.list();
        } else {
            allFilters = FilterLibraryStore.listByType(contextType);
        }
        applySearchAndSort();
        int panelHeight = listBottom - listTop;
        int visibleCount = panelHeight / ROW_HEIGHT;
        int maxOffset = Math.max(0, filters.size() - visibleCount);
        if (scrollOffset > maxOffset)
            scrollOffset = maxOffset;
        updateButtonStates();
    }
}
