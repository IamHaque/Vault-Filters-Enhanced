package net.joseph.vaultfilters.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.UIRenderHelper;
import net.joseph.vaultfilters.library.FilterLibraryStore;
import net.joseph.vaultfilters.library.SavedFilter;
import net.joseph.vaultfilters.library.SavedFilterType;
import net.joseph.vaultfilters.util.FilterUiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

public class FilterLibraryScreen extends Screen {
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 240;

    private int listLeft;
    private int listTop;
    private int listRight;
    private int listBottom;
    private static final int ROW_HEIGHT = 24;

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
        listTop = y + 36;
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

        sortButton = addRenderableWidget(new Button(x + 155, y + 19, 39, 14,
                new TextComponent(sortLabel()), b -> cycleSort()));

        if (contextType != null) {
            showAllButton = addRenderableWidget(new Button(x + 198, y + 19, 48, 14,
                    new TranslatableComponent("vaultfilters.gui.library.show_all"), b -> toggleShowAll()));
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
        renameButton = addRenderableWidget(createTooltipButton(mgmtStartX + idx * (btnW + gap) + groupGap, row2Y, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.rename"), b -> onRename(),
                "vaultfilters.gui.library.tooltip.rename"));
        idx++;
        duplicateButton = addRenderableWidget(createTooltipButton(mgmtStartX + idx * (btnW + gap) + groupGap, row2Y, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.duplicate"), b -> onDuplicate(),
                "vaultfilters.gui.library.tooltip.duplicate"));
        idx++;
        exportButton = addRenderableWidget(createTooltipButton(mgmtStartX + idx * (btnW + gap) + 2 * groupGap, row2Y, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.export"), b -> onExport(),
                "vaultfilters.gui.library.tooltip.export"));
        idx++;
        treeButton = addRenderableWidget(createTooltipButton(mgmtStartX + idx * (btnW + gap) + 2 * groupGap, row2Y, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.tree"), b -> onTree(),
                "vaultfilters.gui.library.tooltip.tree"));
        idx++;
        deleteButton = addRenderableWidget(createTooltipButton(mgmtStartX + idx * (btnW + gap) + 2 * groupGap, row2Y, btnW, btnH,
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
            applyMergeButton = addRenderableWidget(createTooltipButton(applyStartX + applyBtnW + 4, row1Y, applyBtnW, btnH,
                    new TranslatableComponent("vaultfilters.gui.library.apply_merge"), b -> onApplyFilter(true),
                    "vaultfilters.gui.library.tooltip.apply_merge"));
        }

        updateButtonStates();
    }

    private Button createTooltipButton(int x, int y, int w, int h, Component message, Button.OnPress onPress, String tooltipKey) {
        return new Button(x, y, w, h, message, onPress,
                (button, poseStack, mouseX, mouseY) ->
                        renderTooltip(poseStack, new TranslatableComponent(tooltipKey), mouseX, mouseY));
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
        if (sel == null || onApply == null || contextType == null) return;
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
        if (searchBox != null) searchBox.tick();
        if (deleteConfirming && System.currentTimeMillis() - deleteConfirmStart > 5000) {
            deleteConfirming = false;
            deleteTarget = null;
            updateDeleteButton();
        }
    }

    private SavedFilter selectedFilter() {
        if (selectedId == null) return null;
        for (SavedFilter f : filters) {
            if (f.id().equals(selectedId)) return f;
        }
        return null;
    }

    private void updateButtonStates() {
        SavedFilter sel = selectedFilter();
        boolean selected = sel != null;
        boolean renamingActive = renaming;
        if (applyReplaceButton != null) {
            applyReplaceButton.active = selected && !renamingActive && sel != null && contextType != null && sel.type() == contextType;
        }
        if (applyMergeButton != null) {
            applyMergeButton.active = selected && !renamingActive && sel != null && contextType != null && sel.type() == contextType;
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
        UIRenderHelper.drawStretched(ms, x + 4, y + GUI_HEIGHT - 3, GUI_WIDTH - 8, 3, 0, AllGuiTextures.BRASS_FRAME_BOTTOM);
        UIRenderHelper.drawStretched(ms, x, y + 4, 3, GUI_HEIGHT - 8, 0, AllGuiTextures.BRASS_FRAME_LEFT);
        UIRenderHelper.drawStretched(ms, x + GUI_WIDTH - 3, y + 4, 3, GUI_HEIGHT - 8, 0, AllGuiTextures.BRASS_FRAME_RIGHT);

        // Gold accent line at the top of the list panel
        fill(ms, x + 6, y + 17, x + GUI_WIDTH - 6, y + 18, 0xFFC99E3D);
    }

    @Override
    public void render(PoseStack ms, int mouseX, int mouseY, float partialTicks) {
        renderBackground(ms);
        renderBg(ms);
        super.render(ms, mouseX, mouseY, partialTicks);
        renderList(ms, mouseX, mouseY, partialTicks);

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
        font.draw(ms, showingLabel, panelX + 10, panelY + 33, 0xC0C0C0);
        font.draw(ms, countLabel, panelX + GUI_WIDTH - 10 - font.width(countLabel), panelY + 33, 0x808080);

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

    private static String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60000) return "just now";
        if (diff < 3600000) return (diff / 60000) + "m ago";
        if (diff < 86400000) return (diff / 3600000) + "h ago";
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
                    if (searchBox != null) searchBox.changeFocus(false);
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
            setStatus("Clipboard payload too large (" + clipboard.length() + " chars, max " + FilterUiUtils.MAX_IMPORT_CHARS + ")");
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
                    ? obj.get("name").getAsString() : "Imported Filter";
            if (name.length() > 35) name = name.substring(0, 35);

            if (!FilterLibraryStore.canAddMore()) {
                setStatus("Library is full (" + FilterLibraryStore.MAX_LIBRARY_ENTRIES + " max)");
                return;
            }

            SavedFilter filter = SavedFilter.createNew(savedType, name, obj);
            FilterLibraryStore.upsert(filter);
            refreshList();
            setStatus("Imported " + (savedType == SavedFilterType.ATTRIBUTE_FILTER ? "Attribute Filter" : "List Filter") + " \"" + name + "\"");
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
        renameBox = new EditBox(font, (width - 176) / 2, (height - GUI_HEIGHT) / 2 + GUI_HEIGHT / 2 - 16, 176, 16, new TextComponent(""));
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
        renameButton.active = false;
        duplicateButton.active = false;
        exportButton.active = false;
        treeButton.active = false;
        deleteButton.active = false;
        if (searchBox != null) searchBox.setEditable(false);
        sortButton.active = false;
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
        SavedFilter sel = selectedFilter();
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
        SavedFilter sel = selectedFilter();
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
        if (scrollOffset > maxOffset) scrollOffset = maxOffset;
        updateButtonStates();
    }
}
