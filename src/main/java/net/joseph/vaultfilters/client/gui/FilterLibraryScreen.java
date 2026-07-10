package net.joseph.vaultfilters.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.UIRenderHelper;
import net.joseph.vaultfilters.library.FilterLibraryStore;
import net.joseph.vaultfilters.library.SavedFilter;
import net.joseph.vaultfilters.library.SavedFilterType;
import net.joseph.vaultfilters.network.ShareC2SPacket;
import net.joseph.vaultfilters.network.VFMessages;
import net.joseph.vaultfilters.util.FilterPayloadUtils;
import net.joseph.vaultfilters.util.FilterUiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
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
    private static final int GUI_WIDTH = 290;
    private static final int GUI_HEIGHT = 260;

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
    private Button applyReplaceButton;
    private Button applyMergeButton;

    private EditBox searchBox;
    private Button sortButton;
    private Button showAllButton;
    private Button favoritesButton;
    private FilterLibraryStore.SortMode currentSort = FilterLibraryStore.SortMode.NAME_ASC;
    private boolean showOtherType;
    private boolean showFavoritesOnly;

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

    private boolean sharing;
    private EditBox shareBox;
    private List<String> shareSuggestions = new ArrayList<>();
    private int shareSelectedSuggestion = -1;
    private Button shareConfirmButton;
    private Button shareCancelButton;

    private boolean actionMenuOpen;
    private UUID actionMenuFilterId;
    private boolean actionMenuDeleteConfirm;
    private int actionMenuHoveredIndex = -1;
    private Button actionCancelButton;

    private int closeX;
    private int closeY;
    private static final int CLOSE_W = 10;
    private static final int CLOSE_H = 10;

    private static final int ACTION_MENU_W = 190;
    private static final int ACTION_ITEM_H = 18;
    private static final int ACTION_COUNT = 7;
    private static final int ACTION_FAV = 0;
    private static final int ACTION_REN = 1;
    private static final int ACTION_DUP = 2;
    private static final int ACTION_SHR = 3;
    private static final int ACTION_EXP = 4;
    private static final int ACTION_TRE = 5;
    private static final int ACTION_DEL = 6;

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
        showFavoritesOnly = false;
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
        scrollOffset = 0;
        selectedId = null;

        searchBox = new EditBox(font, x + 10, y + 22, 150, 12, new TextComponent(""));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setResponder(s -> {
            applySearchAndSort();
            scrollOffset = 0;
        });
        addRenderableWidget(searchBox);

        sortButton = addRenderableWidget(createTooltipButton(x + 164, y + 21, 34, 14,
                new TextComponent(sortLabel()), b -> cycleSort(),
                "vaultfilters.gui.library.tooltip.sort"));

        favoritesButton = addRenderableWidget(createTooltipButton(x + 202, y + 21, 20, 14,
                new TextComponent(favoritesLabel()), b -> toggleFavorites(),
                "vaultfilters.gui.library.tooltip.favorites"));

        if (contextType != null) {
            showAllButton = addRenderableWidget(createTooltipButton(x + 226, y + 21, 54, 14,
                    new TranslatableComponent("vaultfilters.gui.library.show_all"), b -> toggleShowAll(),
                    "vaultfilters.gui.library.tooltip.show_all"));
        }

        int btnH = 18;
        boolean hasApply = onApply != null && contextType != null;

        int bottomButtonsHeight = hasApply ? 48 : 28;
        int availableListHeight = GUI_HEIGHT - 52 - bottomButtonsHeight;
        int visibleRows = availableListHeight / ROW_HEIGHT;
        int actualListHeight = visibleRows * ROW_HEIGHT;
        listBottom = y + 52 + actualListHeight;

        int row1Y = hasApply ? listBottom + 8 : 0;
        int row2Y = hasApply ? row1Y + 22 : listBottom + 8;

        int importBtnW = 60;
        int applyBtnW = 64;

        if (hasApply) {
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

        importButton = addRenderableWidget(createTooltipButton(x + (GUI_WIDTH - importBtnW) / 2, row2Y, importBtnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.import"), b -> onImport(),
                "vaultfilters.gui.library.tooltip.import"));

        closeX = x + GUI_WIDTH - 14;
        closeY = y + 4;

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
        refreshAllFilters();
        if (showAllButton != null) {
            showAllButton.setMessage(new TranslatableComponent(
                    showOtherType ? "vaultfilters.gui.library.hide_other" : "vaultfilters.gui.library.show_all"));
        }
        applySearchAndSort();
        scrollOffset = 0;
    }

    private String favoritesLabel() {
        return showFavoritesOnly ? "\u2605" : "\u2606";
    }

    private void toggleFavorites() {
        showFavoritesOnly = !showFavoritesOnly;
        favoritesButton.setMessage(new TextComponent(favoritesLabel()));
        refreshAllFilters();
        applySearchAndSort();
        scrollOffset = 0;
    }

    private void refreshAllFilters() {
        if (showFavoritesOnly) {
            if (showOtherType || contextType == null) {
                allFilters = FilterLibraryStore.listFavorites();
            } else {
                allFilters = FilterLibraryStore.listFavorites(contextType);
            }
        } else {
            if (showOtherType || contextType == null) {
                allFilters = FilterLibraryStore.list();
            } else {
                allFilters = FilterLibraryStore.listByType(contextType);
            }
        }
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
        }

        if (renderHoveredButton != null && renderHoveredButton == tickHoveredButton) {
            tooltipHoverTicks++;
        } else {
            tickHoveredButton = renderHoveredButton;
            tooltipHoverTicks = 0;
        }

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

    private SavedFilter selectedFilterForAction() {
        if (actionMenuFilterId == null)
            return null;
        for (SavedFilter f : filters) {
            if (f.id().equals(actionMenuFilterId))
                return f;
        }
        return null;
    }

    private void updateButtonStates() {
        SavedFilter sel = selectedFilter();
        boolean selected = sel != null;
        boolean modalActive = renaming || sharing || actionMenuOpen;
        favoritesButton.active = !modalActive;
        if (applyReplaceButton != null) {
            applyReplaceButton.active = selected && !modalActive && sel != null && contextType != null
                    && sel.type() == contextType;
        }
        if (applyMergeButton != null) {
            applyMergeButton.active = selected && !modalActive && sel != null && contextType != null
                    && sel.type() == contextType;
        }
        importButton.active = !modalActive;
    }

    private void renderBg(PoseStack ms) {
        int x = (width - GUI_WIDTH) / 2;
        int y = (height - GUI_HEIGHT) / 2;

        fill(ms, x + 3, y + 3, x + GUI_WIDTH - 3, y + GUI_HEIGHT - 3, 0xFF2D2D2D);

        AllGuiTextures.BRASS_FRAME_TL.render(ms, x, y, this);
        AllGuiTextures.BRASS_FRAME_TR.render(ms, x + GUI_WIDTH - 4, y, this);
        AllGuiTextures.BRASS_FRAME_BL.render(ms, x, y + GUI_HEIGHT - 4, this);
        AllGuiTextures.BRASS_FRAME_BR.render(ms, x + GUI_WIDTH - 4, y + GUI_HEIGHT - 4, this);

        UIRenderHelper.drawStretched(ms, x + 4, y, GUI_WIDTH - 8, 3, 0, AllGuiTextures.BRASS_FRAME_TOP);
        UIRenderHelper.drawStretched(ms, x + 4, y + GUI_HEIGHT - 3, GUI_WIDTH - 8, 3, 0,
                AllGuiTextures.BRASS_FRAME_BOTTOM);
        UIRenderHelper.drawStretched(ms, x, y + 4, 3, GUI_HEIGHT - 8, 0, AllGuiTextures.BRASS_FRAME_LEFT);
        UIRenderHelper.drawStretched(ms, x + GUI_WIDTH - 3, y + 4, 3, GUI_HEIGHT - 8, 0,
                AllGuiTextures.BRASS_FRAME_RIGHT);

        fill(ms, x + 6, y + 17, x + GUI_WIDTH - 6, y + 18, 0xFFC99E3D);
    }

    @Override
    public void render(PoseStack ms, int mouseX, int mouseY, float partialTicks) {
        renderBackground(ms);
        renderBg(ms);

        boolean anyModal = renaming || sharing || actionMenuOpen;

        if (renaming) {
            renderRenameModal(ms, mouseX, mouseY);
        }
        if (sharing) {
            renderShareModal(ms, mouseX, mouseY);
        }
        if (actionMenuOpen) {
            renderActionMenu(ms, mouseX, mouseY);
        }

        super.render(ms, mouseX, mouseY, partialTicks);

        if (renaming && renameBox != null) {
            renameBox.render(ms, mouseX, mouseY, partialTicks);
        }

        if (sharing && shareBox != null) {
            shareBox.render(ms, mouseX, mouseY, partialTicks);
            renderShareSuggestions(ms, mouseX, mouseY);
        }

        if (!anyModal) {
            renderList(ms, mouseX, mouseY, partialTicks);
            renderScrollbar(ms);
        }

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

        renderHoveredRowId = null;
        if (!anyModal) {
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
        if (renderHoveredRowId != null && rowHoverTicks >= TOOLTIP_DELAY_TICKS && !anyModal) {
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

        drawCenteredString(ms, font, title, width / 2, (height - GUI_HEIGHT) / 2 + 6, 0xFFFFFF);

        boolean hoveringClose = mouseX >= closeX && mouseX <= closeX + CLOSE_W && mouseY >= closeY && mouseY <= closeY + CLOSE_H;
        font.draw(ms, "\u00d7", closeX, closeY, hoveringClose ? 0xFF5555 : 0xAAAAAA);

        if (!anyModal) {
            String showingLabel;
            String favPrefix = showFavoritesOnly ? "\u2605 " : "";
            if (showOtherType) {
                showingLabel = "Showing: " + favPrefix + "All";
            } else if (contextType == SavedFilterType.ATTRIBUTE_FILTER) {
                showingLabel = "Showing: " + favPrefix + "Attribute Filters";
            } else if (contextType == SavedFilterType.LIST_FILTER) {
                showingLabel = "Showing: " + favPrefix + "List Filters";
            } else {
                showingLabel = "Showing: " + favPrefix + "All";
            }
            String countLabel = filters.size() + "/" + FilterLibraryStore.MAX_LIBRARY_ENTRIES;
            int panelX = (width - GUI_WIDTH) / 2;
            int panelY = (height - GUI_HEIGHT) / 2;
            font.draw(ms, showingLabel, panelX + 10, panelY + 40, 0xC0C0C0);
            font.draw(ms, countLabel, panelX + GUI_WIDTH - 10 - font.width(countLabel), panelY + 40, 0x808080);

            if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
                font.draw(ms, new TranslatableComponent("vaultfilters.gui.library.search"),
                        searchBox.x + 2, searchBox.y + 1, 0x606060);
            }
        }

        if (statusMessage != null && System.currentTimeMillis() < statusMessageUntil) {
            drawCenteredString(ms, font, new TextComponent(statusMessage),
                    width / 2, (height - GUI_HEIGHT) / 2 + GUI_HEIGHT + 8, 0xFFFF55);
        }

        if (!anyModal) {
            if (allFilters.isEmpty()) {
                drawCenteredString(ms, font,
                        new TranslatableComponent("vaultfilters.screen.filter_library.empty"),
                        width / 2, listTop + 20, 0x808080);
            } else if (filters.isEmpty()) {
                drawCenteredString(ms, font, new TextComponent("No matches"),
                        width / 2, listTop + 20, 0x808080);
            }
        }
    }

    private void renderRenameModal(PoseStack ms, int mouseX, int mouseY) {
        fill(ms, 0, 0, width, height, 0xFF000000);
        int rx = (width - 180) / 2;
        int ry = (height - GUI_HEIGHT) / 2 + GUI_HEIGHT / 2 - 20;
        fill(ms, rx - 4, ry - 4, rx + 184, ry + 44, 0xFF333333);

        drawCenteredString(ms, font, new TranslatableComponent("vaultfilters.gui.library.rename.title"),
                width / 2, ry + 2, 0xFFFFFF);

        boolean hoverX = mouseX >= rx + 178 && mouseX <= rx + 188 && mouseY >= ry - 2 && mouseY <= ry + 8;
        font.draw(ms, "\u00d7", rx + 178, ry - 2, hoverX ? 0xFF5555 : 0xAAAAAA);
    }

    private void renderShareModal(PoseStack ms, int mouseX, int mouseY) {
        fill(ms, 0, 0, width, height, 0xFF000000);
        int sx = (width - 200) / 2;
        int sy = (height - GUI_HEIGHT) / 2 + GUI_HEIGHT / 2 - 36;
        fill(ms, sx - 4, sy - 4, sx + 208, sy + 96, 0xFF333333);
        drawCenteredString(ms, font, new TranslatableComponent("vaultfilters.gui.library.share.title"),
                width / 2, sy + 4, 0xFFFFFF);

        boolean hoverX = mouseX >= sx + 198 && mouseX <= sx + 208 && mouseY >= sy - 2 && mouseY <= sy + 8;
        font.draw(ms, "\u00d7", sx + 198, sy - 2, hoverX ? 0xFF5555 : 0xAAAAAA);
    }

    private void renderActionMenu(PoseStack ms, int mouseX, int mouseY) {
        fill(ms, 0, 0, width, height, 0xFF000000);

        int cx = (width - ACTION_MENU_W) / 2;
        int cy = (height - 172) / 2;

        fill(ms, cx, cy, cx + ACTION_MENU_W, cy + 172, 0xFF333333);

        drawCenteredString(ms, font, "Filter Actions", width / 2, cy + 13, 0xFFFFFF);

        boolean hoverX = mouseX >= cx + ACTION_MENU_W - 14 && mouseX <= cx + ACTION_MENU_W - 4
                && mouseY >= cy + 2 && mouseY <= cy + 12;
        font.draw(ms, "\u00d7", cx + ACTION_MENU_W - 14, cy + 2, hoverX ? 0xFF5555 : 0xAAAAAA);

        fill(ms, cx + 4, cy + 18, cx + ACTION_MENU_W - 4, cy + 19, 0xFF555555);

        actionMenuHoveredIndex = -1;
        for (int i = 0; i < ACTION_COUNT; i++) {
            int itemY = cy + 22 + i * ACTION_ITEM_H;
            boolean hovered = mouseX >= cx + 2 && mouseX <= cx + ACTION_MENU_W - 2
                    && mouseY >= itemY && mouseY < itemY + ACTION_ITEM_H;

            if (hovered) {
                fill(ms, cx + 2, itemY, cx + ACTION_MENU_W - 2, itemY + ACTION_ITEM_H, 0xFF444444);
                actionMenuHoveredIndex = i;
            }

            SavedFilter sf = selectedFilterForAction();
            boolean isFav = sf != null && sf.favorite();

            switch (i) {
                case ACTION_FAV: {
                    AllIcons.I_ACTIVE.render(ms, cx + 6, itemY + 1);
                    int color = 0xFFFF55;
                    font.draw(ms, isFav ? "\u2605 Favorite" : "\u2606 Favorite", cx + 24, itemY + 3, color);
                    break;
                }
                case ACTION_REN:
                    AllIcons.I_CONFIG_OPEN.render(ms, cx + 6, itemY + 1);
                    font.draw(ms, "Rename", cx + 24, itemY + 3, 0xFFFFFF);
                    break;
                case ACTION_DUP:
                    AllIcons.I_REFRESH.render(ms, cx + 6, itemY + 1);
                    font.draw(ms, "Duplicate", cx + 24, itemY + 3, 0xFFFFFF);
                    break;
                case ACTION_SHR:
                    font.draw(ms, "\u2192", cx + 9, itemY + 3, 0xFFFFFF);
                    font.draw(ms, "Share", cx + 24, itemY + 3, 0xFFFFFF);
                    break;
                case ACTION_EXP:
                    AllIcons.I_OPEN_FOLDER.render(ms, cx + 6, itemY + 1);
                    font.draw(ms, "Export", cx + 24, itemY + 3, 0xFFFFFF);
                    break;
                case ACTION_TRE:
                    AllIcons.I_SCHEMATIC.render(ms, cx + 6, itemY + 1);
                    font.draw(ms, "Tree", cx + 24, itemY + 3, 0xFFFFFF);
                    break;
                case ACTION_DEL: {
                    AllIcons.I_TRASH.render(ms, cx + 6, itemY + 1);
                    int delColor = 0xFF5555;
                    String delText = actionMenuDeleteConfirm ? "Confirm Delete" : "Delete";
                    font.draw(ms, delText, cx + 24, itemY + 3, delColor);
                    break;
                }
            }
        }

        fill(ms, cx + 4, cy + 22 + ACTION_COUNT * ACTION_ITEM_H, cx + ACTION_MENU_W - 4,
                cy + 23 + ACTION_COUNT * ACTION_ITEM_H, 0xFF555555);
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

            String star = filter.favorite() ? "\u2605" : "\u2606";
            int starColor = filter.favorite() ? 0xFFFF55 : 0x606060;
            font.draw(ms, star, listLeft + 2, rowTop + 2, starColor);

            font.draw(ms, filter.name(), listLeft + 14, rowTop + 2, 0xFFFFFF);

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
            buildAttributePreview(lines, sf.name(), payload);
        } else if (sf.type() == SavedFilterType.LIST_FILTER) {
            buildListPreview(lines, sf.name(), payload);
        }
        return lines;
    }

    private void buildAttributePreview(List<Component> lines, String name, JsonObject root) {
        lines.add(new TextComponent(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        FilterPayloadUtils.AttributeSettings settings = FilterPayloadUtils.readAttributeSettings(root);
        String mode = FilterPayloadUtils.getModeLabel(settings.blacklist(), settings.matchAll());

        JsonArray array = root.has(FilterUiUtils.ATTRIBUTES_FIELD)
                && root.get(FilterUiUtils.ATTRIBUTES_FIELD).isJsonArray()
                ? root.getAsJsonArray(FilterUiUtils.ATTRIBUTES_FIELD) : new JsonArray();

        boolean empty = array.size() == 0;
        lines.add(new TextComponent("\u251c\u2500 " + mode + (empty ? "" : " (" + array.size()
                + " attribute" + (array.size() == 1 ? "" : "s") + ")"))
                .withStyle(ChatFormatting.GOLD));

        if (empty) return;

        List<JsonObject> entries = new ArrayList<>();
        for (JsonElement elem : array) {
            if (!elem.isJsonObject()) continue;
            JsonObject obj = elem.getAsJsonObject();
            if ((obj.has("nbt") && obj.get("nbt").isJsonPrimitive())
                    || (obj.has("attribute") && obj.get("attribute").isJsonObject())) {
                entries.add(obj);
            }
        }

        if (entries.isEmpty()) {
            lines.add(new TextComponent("\u2514\u2500 No readable attributes").withStyle(ChatFormatting.GRAY));
            return;
        }

        int maxShow = Math.min(entries.size(), 8);
        for (int i = 0; i < maxShow; i++) {
            boolean isLast = (i == maxShow - 1) && entries.size() <= 8;
            appendAttrLine(lines, entries.get(i), "", isLast);
        }

        if (entries.size() > 8) {
            lines.add(new TextComponent("\u2514\u2500 ... (" + (entries.size() - 8) + " more)")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void appendAttrLine(List<Component> lines, JsonObject entry, String prefix, boolean isLast) {
        JsonElement payload = entry.has("nbt") && entry.get("nbt").isJsonPrimitive()
                ? entry.get("nbt")
                : (entry.has("attribute") && entry.get("attribute").isJsonObject()
                    ? entry.get("attribute") : null);
        if (payload == null) return;

        boolean inverted = entry.has("inverted") && entry.get("inverted").getAsBoolean();
        String branch = isLast ? "\u2514\u2500" : "\u251c\u2500";

        try {
            CompoundTag tag = payload.isJsonPrimitive()
                    ? TagParser.parseTag(payload.getAsString())
                    : FilterPayloadUtils.expandFlattenedAttributeCompound(
                        FilterPayloadUtils.jsonToCompoundTag(payload));
            FilterUiUtils.TagEntry te = FilterUiUtils.firstSortedDataEntry(tag);
            String key = te == null ? "?" : te.key();
            String val = te == null ? "?"
                    : FilterUiUtils.normalizeAttributeSummary(key, FilterUiUtils.summarizeTag(te.value()));

            String line = prefix + branch + " " + (inverted ? ChatFormatting.RED + "NOT " : "")
                    + ChatFormatting.WHITE + key + ChatFormatting.GRAY + " = " + ChatFormatting.AQUA + val;
            lines.add(new TextComponent(line));
        } catch (Exception e) {
            lines.add(new TextComponent(prefix + branch + " " + ChatFormatting.RED + "invalid"));
        }
    }

    private void addAttrFilterLines(List<Component> lines, JsonObject root, String prefix) {
        FilterPayloadUtils.AttributeSettings settings = FilterPayloadUtils.readAttributeSettings(root);
        String mode = FilterPayloadUtils.getModeLabel(settings.blacklist(), settings.matchAll());

        JsonArray array = root.has(FilterUiUtils.ATTRIBUTES_FIELD)
                && root.get(FilterUiUtils.ATTRIBUTES_FIELD).isJsonArray()
                ? root.getAsJsonArray(FilterUiUtils.ATTRIBUTES_FIELD) : new JsonArray();

        if (array.size() == 0) {
            lines.add(new TextComponent(prefix + "\u2514\u2500 " + mode + " (0 attributes)").withStyle(ChatFormatting.GRAY));
            return;
        }

        List<JsonObject> entries = new ArrayList<>();
        for (JsonElement elem : array) {
            if (!elem.isJsonObject()) continue;
            JsonObject obj = elem.getAsJsonObject();
            if ((obj.has("nbt") && obj.get("nbt").isJsonPrimitive())
                    || (obj.has("attribute") && obj.get("attribute").isJsonObject())) {
                entries.add(obj);
            }
        }

        if (entries.isEmpty()) {
            lines.add(new TextComponent(prefix + "\u2514\u2500 " + mode + " (0 readable)").withStyle(ChatFormatting.GRAY));
            return;
        }

        boolean hasOverflow = entries.size() > 8;
        int maxShow = Math.min(entries.size(), 8);

        lines.add(new TextComponent(prefix + "\u251c\u2500 " + mode + " (" + entries.size()
                + " attribute" + (entries.size() == 1 ? "" : "s") + ")")
                .withStyle(ChatFormatting.GOLD));

        for (int i = 0; i < maxShow; i++) {
            boolean isLast = (i == maxShow - 1) && !hasOverflow;
            appendAttrLine(lines, entries.get(i), prefix, isLast);
        }

        if (hasOverflow) {
            lines.add(new TextComponent(prefix + "\u2514\u2500 ... (" + (entries.size() - 8) + " more)")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void buildListPreview(List<Component> lines, String name, JsonObject root) {
        JsonObject filterObj = root.has("filter") && root.get("filter").isJsonObject()
                ? root.getAsJsonObject("filter") : root;

        FilterPayloadUtils.ListSettings settings = FilterPayloadUtils.readListSettings(filterObj);
        String mode = FilterPayloadUtils.getModeLabel(settings.blacklist(), settings.matchAll());
        String nbtPart = settings.respectNBT() ? ", Respect NBT" : "";

        JsonArray itemsArr = filterObj.has("items") && filterObj.get("items").isJsonArray()
                ? filterObj.getAsJsonArray("items") : new JsonArray();

        List<JsonObject> items = new ArrayList<>();
        for (JsonElement elem : itemsArr) {
            if (elem.isJsonObject()) items.add(elem.getAsJsonObject());
        }

        String header = name + " (" + items.size() + " item" + (items.size() == 1 ? "" : "s") + ")"
                + (nbtPart.isEmpty() ? "" : " " + nbtPart);
        lines.add(new TextComponent(header).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (items.isEmpty()) {
            lines.add(new TextComponent("\u2514\u2500 " + mode).withStyle(ChatFormatting.GOLD));
            return;
        }

        lines.add(new TextComponent("\u251c\u2500 " + mode).withStyle(ChatFormatting.GOLD));
        renderListItemTree(lines, items, "");
    }

    private void renderListItemTree(List<Component> lines, List<JsonObject> items, String prefix) {
        int maxShow = Math.min(items.size(), 6);
        boolean hasOverflow = items.size() > 6;

        for (int i = 0; i < maxShow; i++) {
            JsonObject item = items.get(i);
            boolean isLastItem = (i == maxShow - 1) && !hasOverflow;
            String branch = isLastItem ? "\u2514\u2500" : "\u251c\u2500";
            String contPrefix = prefix + (isLastItem ? "   " : "\u2502  ");

            String type = item.has("type") && item.get("type").isJsonPrimitive()
                    ? item.get("type").getAsString() : "item_filter";

            if ("attribute_filter".equals(type)) {
                String itemName = item.has("name") && item.get("name").isJsonPrimitive()
                        ? item.get("name").getAsString() : "Attribute Filter";
                lines.add(new TextComponent(prefix + branch + " " + ChatFormatting.AQUA + "Attr: " + itemName)
                        .withStyle(ChatFormatting.AQUA));
                addAttrFilterLines(lines, item, contPrefix);
            } else if ("list_filter".equals(type)) {
                String itemName = item.has("name") && item.get("name").isJsonPrimitive()
                        ? item.get("name").getAsString() : "List Filter";
                lines.add(new TextComponent(prefix + branch + " " + ChatFormatting.LIGHT_PURPLE + "List: " + itemName)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                addNestedListLines(lines, item, contPrefix, 1);
            } else {
                String itemName = item.has("name") && item.get("name").isJsonPrimitive()
                        ? item.get("name").getAsString() : "Item";
                lines.add(new TextComponent(prefix + branch + " " + ChatFormatting.WHITE + itemName));
            }
        }

        if (hasOverflow) {
            lines.add(new TextComponent(prefix + "\u2514\u2500 ... (" + (items.size() - 6) + " more)")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void addNestedListLines(List<Component> lines, JsonObject root, String prefix, int depth) {
        if (depth > 3) {
            lines.add(new TextComponent(prefix + "\u2514\u2500 ... (nested)").withStyle(ChatFormatting.GRAY));
            return;
        }

        JsonObject filterObj = root.has("filter") && root.get("filter").isJsonObject()
                ? root.getAsJsonObject("filter") : root;

        FilterPayloadUtils.ListSettings settings = FilterPayloadUtils.readListSettings(filterObj);
        String mode = FilterPayloadUtils.getModeLabel(settings.blacklist(), settings.matchAll());
        String nbtPart = settings.respectNBT() ? ", Respect NBT" : "";

        JsonArray itemsArr = filterObj.has("items") && filterObj.get("items").isJsonArray()
                ? filterObj.getAsJsonArray("items") : new JsonArray();

        List<JsonObject> items = new ArrayList<>();
        for (JsonElement elem : itemsArr) {
            if (elem.isJsonObject()) items.add(elem.getAsJsonObject());
        }

        if (items.isEmpty()) {
            lines.add(new TextComponent(prefix + "\u2514\u2500 " + mode + nbtPart).withStyle(ChatFormatting.GOLD));
            return;
        }

        String modeLine = mode + nbtPart;
        boolean hasOverflow = items.size() > 6;
        int maxShow = Math.min(items.size(), 6);
        boolean modeIsLast = maxShow == 0 && !hasOverflow;
        lines.add(new TextComponent(prefix + (modeIsLast ? "\u2514\u2500" : "\u251c\u2500") + " " + modeLine)
                .withStyle(ChatFormatting.GOLD));

        for (int i = 0; i < maxShow; i++) {
            JsonObject item = items.get(i);
            boolean isLastItem = (i == maxShow - 1) && !hasOverflow;
            String branch = isLastItem ? "\u2514\u2500" : "\u251c\u2500";
            String contPrefix = prefix + (isLastItem ? "   " : "\u2502  ");

            String type = item.has("type") && item.get("type").isJsonPrimitive()
                    ? item.get("type").getAsString() : "item_filter";

            if ("attribute_filter".equals(type)) {
                String itemName = item.has("name") && item.get("name").isJsonPrimitive()
                        ? item.get("name").getAsString() : "Attribute Filter";
                lines.add(new TextComponent(prefix + branch + " " + ChatFormatting.AQUA + "Attr: " + itemName)
                        .withStyle(ChatFormatting.AQUA));
                addAttrFilterLines(lines, item, contPrefix);
            } else if ("list_filter".equals(type)) {
                String itemName = item.has("name") && item.get("name").isJsonPrimitive()
                        ? item.get("name").getAsString() : "List Filter";
                lines.add(new TextComponent(prefix + branch + " " + ChatFormatting.LIGHT_PURPLE + "List: " + itemName)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                addNestedListLines(lines, item, contPrefix, depth + 1);
            } else {
                String itemName = item.has("name") && item.get("name").isJsonPrimitive()
                        ? item.get("name").getAsString() : "Item";
                lines.add(new TextComponent(prefix + branch + " " + ChatFormatting.WHITE + itemName));
            }
        }

        if (hasOverflow) {
            lines.add(new TextComponent(prefix + "\u2514\u2500 ... (" + (items.size() - 6) + " more)")
                    .withStyle(ChatFormatting.GRAY));
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
        if (searchBox != null && !searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.changeFocus(false);
        }

        if (renaming) {
            if (renameBox != null && renameBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (button == 0) {
                int rx = (width - 180) / 2;
                int ry = (height - GUI_HEIGHT) / 2 + GUI_HEIGHT / 2 - 20;
                if (mouseX >= rx + 178 && mouseX <= rx + 188 && mouseY >= ry - 2 && mouseY <= ry + 8) {
                    cancelRename();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (sharing) {
            if (shareBox != null && shareBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (button == 0) {
                int sx = (width - 200) / 2;
                int sy = (height - GUI_HEIGHT) / 2 + GUI_HEIGHT / 2 - 36;
                if (mouseX >= sx + 198 && mouseX <= sx + 208 && mouseY >= sy - 2 && mouseY <= sy + 8) {
                    cancelShare();
                    return true;
                }
            }
            if (shareSuggestions != null && !shareSuggestions.isEmpty()) {
                int sx = shareBox.x;
                int sy = shareBox.y + shareBox.getHeight() + 1;
                int sw = shareBox.getWidth();
                int maxShow = Math.min(shareSuggestions.size(), 6);
                int sh = maxShow * 12;
                if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh) {
                    int idx = (int) ((mouseY - sy) / 12);
                    if (idx >= 0 && idx < maxShow) {
                        shareBox.setValue(shareSuggestions.get(idx));
                        shareSuggestions.clear();
                        shareSelectedSuggestion = -1;
                    }
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (actionMenuOpen) {
            if (button == 0) {
                int cx = (width - ACTION_MENU_W) / 2;
                int cy = (height - 172) / 2;

                boolean inPanel = mouseX >= cx && mouseX <= cx + ACTION_MENU_W && mouseY >= cy && mouseY <= cy + 172;

                if (mouseX >= cx + ACTION_MENU_W - 14 && mouseX <= cx + ACTION_MENU_W - 4
                        && mouseY >= cy + 2 && mouseY <= cy + 12) {
                    closeActionMenu();
                    return true;
                }

                if (inPanel) {
                    int itemY = cy + 22;
                    for (int i = 0; i < ACTION_COUNT; i++) {
                        if (mouseY >= itemY && mouseY < itemY + ACTION_ITEM_H) {
                            handleActionClick(i);
                            return true;
                        }
                        itemY += ACTION_ITEM_H;
                    }

                    int cancelY = cy + 150;
                    int cancelCX = cx + (ACTION_MENU_W - 80) / 2;
                    if (mouseX >= cancelCX && mouseX <= cancelCX + 80 && mouseY >= cancelY && mouseY <= cancelY + 16) {
                        closeActionMenu();
                        return true;
                    }
                    return true;
                }

                closeActionMenu();
                return true;
            }
            closeActionMenu();
            return true;
        }

        if (button == 0) {
            if (mouseX >= closeX && mouseX <= closeX + CLOSE_W && mouseY >= closeY && mouseY <= closeY + CLOSE_H) {
                onClose();
                return true;
            }
        }

        if (button == 1 && mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int panelHeight = listBottom - listTop;
            int visibleCount = panelHeight / ROW_HEIGHT;
            int end = Math.min(scrollOffset + visibleCount, filters.size());
            for (int i = scrollOffset; i < end; i++) {
                int rowTop = listTop + (i - scrollOffset) * ROW_HEIGHT;
                int rowBottom = rowTop + ROW_HEIGHT;
                if (mouseY >= rowTop && mouseY < rowBottom) {
                    selectedId = filters.get(i).id();
                    updateButtonStates();
                    openActionMenu(filters.get(i).id());
                    return true;
                }
            }
        }

        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int panelHeight = listBottom - listTop;
            int visibleCount = panelHeight / ROW_HEIGHT;
            int end = Math.min(scrollOffset + visibleCount, filters.size());
            for (int i = scrollOffset; i < end; i++) {
                int rowTop = listTop + (i - scrollOffset) * ROW_HEIGHT;
                int rowBottom = rowTop + ROW_HEIGHT;
                if (mouseY >= rowTop && mouseY < rowBottom) {
                    boolean onStar = mouseX >= listLeft + 2 && mouseX <= listLeft + 12;
                    if (onStar && button == 0) {
                        SavedFilter sf = filters.get(i);
                        sf.toggleFavorite();
                        FilterLibraryStore.toggleFavorite(sf.id());
                        setStatus(sf.favorite() ? "Favorited \"" + sf.name() + "\"" : "Unfavorited \"" + sf.name() + "\"");
                        return true;
                    }
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
        if (sharing) {
            if (keyCode == 256) {
                cancelShare();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                confirmShare();
                return true;
            }
            if (shareBox != null && shareBox.keyPressed(keyCode, scanCode, modifiers)) {
                updateShareSuggestions();
                return true;
            }
            return shareBox != null && shareBox.isFocused() || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (actionMenuOpen) {
            if (keyCode == 256) {
                closeActionMenu();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
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
        if (sharing && shareBox != null && shareBox.isFocused()) {
            boolean result = shareBox.charTyped(codePoint, modifiers);
            updateShareSuggestions();
            return result;
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

    private void openActionMenu(UUID filterId) {
        actionMenuOpen = true;
        actionMenuFilterId = filterId;
        actionMenuDeleteConfirm = false;
        actionMenuHoveredIndex = -1;

        int cx = (width - ACTION_MENU_W) / 2;
        int cy = (height - 172) / 2;
        int cancelCX = cx + (ACTION_MENU_W - 80) / 2;
        actionCancelButton = addRenderableWidget(new Button(cancelCX, cy + 150, 80, 16,
                new TranslatableComponent("vaultfilters.gui.library.rename.cancel"), b -> closeActionMenu()));

        updateButtonStates();
    }

    private void closeActionMenu() {
        actionMenuOpen = false;
        actionMenuFilterId = null;
        actionMenuDeleteConfirm = false;
        actionMenuHoveredIndex = -1;
        if (actionCancelButton != null) {
            removeWidget(actionCancelButton);
            actionCancelButton = null;
        }
        updateButtonStates();
    }

    private void handleActionClick(int index) {
        SavedFilter sf = selectedFilterForAction();
        if (sf == null) {
            closeActionMenu();
            return;
        }

        switch (index) {
            case ACTION_FAV:
                sf.toggleFavorite();
                FilterLibraryStore.toggleFavorite(sf.id());
                setStatus(sf.favorite() ? "Favorited \"" + sf.name() + "\"" : "Unfavorited \"" + sf.name() + "\"");
                closeActionMenu();
                break;
            case ACTION_REN:
                closeActionMenu();
                beginRename(sf);
                break;
            case ACTION_DUP:
                SavedFilter copy = FilterLibraryStore.duplicate(sf.id());
                if (copy != null) {
                    refreshList();
                    setStatus("Duplicated as \"" + copy.name() + "\"");
                }
                closeActionMenu();
                break;
            case ACTION_SHR:
                closeActionMenu();
                beginShare(sf);
                break;
            case ACTION_EXP:
                onExport();
                closeActionMenu();
                break;
            case ACTION_TRE:
                onTree();
                closeActionMenu();
                break;
            case ACTION_DEL:
                if (!actionMenuDeleteConfirm) {
                    actionMenuDeleteConfirm = true;
                } else {
                    FilterLibraryStore.delete(sf.id());
                    if (selectedId != null && selectedId.equals(sf.id())) {
                        selectedId = null;
                    }
                    deleteConfirming = false;
                    deleteTarget = null;
                    refreshList();
                    setStatus("Deleted filter");
                    closeActionMenu();
                }
                break;
        }
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
        if (sel == null) return;
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

        if (applyReplaceButton != null) applyReplaceButton.active = false;
        if (applyMergeButton != null) applyMergeButton.active = false;
        importButton.active = false;
        if (searchBox != null) searchBox.setEditable(false);
        sortButton.active = false;
        favoritesButton.active = false;
        if (showAllButton != null) showAllButton.active = false;
    }

    private void confirmRename() {
        if (renameTarget == null || renameBox == null) return;
        String newName = renameBox.getValue();
        if (newName == null || newName.isBlank()) {
            newName = renameTarget.name();
        }
        if (newName.length() > 35) newName = newName.substring(0, 35);
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
        if (searchBox != null) searchBox.setEditable(true);
        sortButton.active = true;
        favoritesButton.active = true;
        if (showAllButton != null) showAllButton.active = true;
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
        SavedFilter sel = selectedFilterForAction();
        if (sel == null) sel = selectedFilter();
        if (sel == null) return;

        try {
            String json = FilterUiUtils.PRETTY_GSON.toJson(sel.payload());
            Minecraft.getInstance().keyboardHandler.setClipboard(json);
            setStatus("Exported \"" + sel.name() + "\" to clipboard");
        } catch (Exception e) {
            setStatus("Export failed: " + e.getMessage());
        }
    }

    private void onTree() {
        SavedFilter sel = selectedFilterForAction();
        if (sel == null) sel = selectedFilter();
        if (sel == null) return;

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
        if (sel == null) return;

        if (!deleteConfirming || deleteTarget == null || !deleteTarget.equals(sel.id())) {
            deleteConfirming = true;
            deleteTarget = sel.id();
            deleteConfirmStart = System.currentTimeMillis();
            return;
        }

        FilterLibraryStore.delete(deleteTarget);
        deleteConfirming = false;
        deleteTarget = null;
        refreshList();
        setStatus("Deleted filter");
    }

    private void onShare() {
        SavedFilter sel = selectedFilter();
        if (sel == null) return;
        beginShare(sel);
    }

    private void beginShare(SavedFilter filter) {
        sharing = true;
        shareSuggestions = new ArrayList<>();
        shareSelectedSuggestion = -1;

        shareBox = new EditBox(font, (width - 192) / 2, (height - GUI_HEIGHT) / 2 + GUI_HEIGHT / 2 - 16, 176, 14,
                new TextComponent(""));
        shareBox.setMaxLength(16);
        shareBox.changeFocus(true);
        setFocused(shareBox);

        int y = shareBox.y + 56;
        int cx = (width - 80) / 2;
        shareConfirmButton = addRenderableWidget(new Button(cx, y, 38, 16,
                new TranslatableComponent("vaultfilters.gui.library.share.confirm"), b -> confirmShare()));
        shareCancelButton = addRenderableWidget(new Button(cx + 42, y, 38, 16,
                new TranslatableComponent("vaultfilters.gui.library.rename.cancel"), b -> cancelShare()));

        if (applyReplaceButton != null) applyReplaceButton.active = false;
        if (applyMergeButton != null) applyMergeButton.active = false;
        importButton.active = false;
        if (searchBox != null) searchBox.setEditable(false);
        sortButton.active = false;
        favoritesButton.active = false;
        if (showAllButton != null) showAllButton.active = false;
    }

    private void confirmShare() {
        if (shareBox == null) return;
        String targetName = shareBox.getValue();
        if (targetName == null || targetName.isBlank()) {
            setStatus("Enter a player name");
            return;
        }

        SavedFilter sel = selectedFilter();
        if (sel == null) {
            cleanupShare();
            setStatus("No filter selected");
            return;
        }

        String filterJson = FilterUiUtils.PRETTY_GSON.toJson(sel.toEntryJson());
        VFMessages.VFCHANNEL.sendToServer(new ShareC2SPacket(targetName, filterJson));

        cleanupShare();
        setStatus("Sent \"" + sel.name() + "\" to " + targetName);
    }

    private void cancelShare() {
        cleanupShare();
        updateButtonStates();
    }

    private void cleanupShare() {
        sharing = false;
        shareSuggestions = null;
        shareSelectedSuggestion = -1;
        if (shareBox != null) {
            removeWidget(shareBox);
            shareBox = null;
        }
        if (shareConfirmButton != null) {
            removeWidget(shareConfirmButton);
            shareConfirmButton = null;
        }
        if (shareCancelButton != null) {
            removeWidget(shareCancelButton);
            shareCancelButton = null;
        }
        if (searchBox != null) searchBox.setEditable(true);
        sortButton.active = true;
        favoritesButton.active = true;
        if (showAllButton != null) showAllButton.active = true;
        setFocused(null);
    }

    private void updateShareSuggestions() {
        shareSuggestions.clear();
        if (shareBox == null) return;
        String text = shareBox.getValue().toLowerCase();
        if (text.isEmpty()) return;

        for (PlayerInfo info : Minecraft.getInstance().player.connection.getOnlinePlayers()) {
            String name = info.getProfile().getName();
            if (!name.equalsIgnoreCase(Minecraft.getInstance().player.getGameProfile().getName())
                    && name.toLowerCase().contains(text)) {
                shareSuggestions.add(name);
            }
        }
        shareSuggestions.sort(String.CASE_INSENSITIVE_ORDER);
        shareSelectedSuggestion = shareSuggestions.size() == 1 ? 0 : -1;
    }

    private void renderShareSuggestions(PoseStack ms, int mouseX, int mouseY) {
        if (shareSuggestions == null || shareSuggestions.isEmpty() || shareBox == null) return;

        int sx = shareBox.x;
        int sy = shareBox.y + shareBox.getHeight() + 1;
        int sw = shareBox.getWidth();
        int maxShow = Math.min(shareSuggestions.size(), 6);
        int sh = maxShow * 12;

        fill(ms, sx, sy, sx + sw, sy + sh, 0xFF1A1A1A);
        fill(ms, sx, sy, sx + sw, sy + 1, 0xFF555555);
        fill(ms, sx, sy + sh - 1, sx + sw, sy + sh, 0xFF555555);
        fill(ms, sx, sy, sx + 1, sy + sh, 0xFF555555);
        fill(ms, sx + sw - 1, sy, sx + sw, sy + sh, 0xFF555555);

        for (int i = 0; i < maxShow; i++) {
            String name = shareSuggestions.get(i);
            int ry = sy + 2 + i * 12;
            if (mouseX >= sx && mouseX <= sx + sw && mouseY >= ry - 1 && mouseY < ry + 11) {
                fill(ms, sx + 1, ry - 1, sx + sw - 1, ry + 11, 0xFF444444);
            }
            font.draw(ms, name, sx + 3, ry, 0xFFFFFF);
        }
    }

    void refreshList() {
        refreshAllFilters();
        applySearchAndSort();
        int panelHeight = listBottom - listTop;
        int visibleCount = panelHeight / ROW_HEIGHT;
        int maxOffset = Math.max(0, filters.size() - visibleCount);
        if (scrollOffset > maxOffset)
            scrollOffset = maxOffset;
        updateButtonStates();
    }
}
