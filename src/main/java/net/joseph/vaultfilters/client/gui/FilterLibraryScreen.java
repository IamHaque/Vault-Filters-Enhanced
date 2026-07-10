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

public class FilterLibraryScreen extends Screen {
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 220;

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

    public FilterLibraryScreen() {
        super(new TranslatableComponent("vaultfilters.screen.filter_library"));
    }

    @Override
    protected void init() {
        super.init();
        filters = FilterLibraryStore.list();

        int x = (width - GUI_WIDTH) / 2;
        int y = (height - GUI_HEIGHT) / 2;

        listLeft = x + 10;
        listTop = y + 20;
        listRight = x + GUI_WIDTH - 10;
        listBottom = y + GUI_HEIGHT - 40;
        scrollOffset = 0;
        selectedId = null;

        int btnY = y + GUI_HEIGHT - 30;
        int btnW = 36;
        int btnH = 16;
        int gap = 2;
        int totalW = 6 * btnW + 5 * gap;
        int startX = x + (GUI_WIDTH - totalW) / 2;

        importButton = addRenderableWidget(new Button(startX, btnY, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.import"), b -> onImport()));
        renameButton = addRenderableWidget(new Button(startX + (btnW + gap), btnY, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.rename"), b -> onRename()));
        duplicateButton = addRenderableWidget(new Button(startX + 2 * (btnW + gap), btnY, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.duplicate"), b -> onDuplicate()));
        exportButton = addRenderableWidget(new Button(startX + 3 * (btnW + gap), btnY, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.export"), b -> onExport()));
        treeButton = addRenderableWidget(new Button(startX + 4 * (btnW + gap), btnY, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.tree"), b -> onTree()));
        deleteButton = addRenderableWidget(new Button(startX + 5 * (btnW + gap), btnY, btnW, btnH,
                new TranslatableComponent("vaultfilters.gui.library.delete"), b -> onDelete()));

        updateButtonStates();
    }

    @Override
    public void tick() {
        super.tick();
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

    @Override
    public void render(PoseStack ms, int mouseX, int mouseY, float partialTicks) {
        renderBackground(ms);
        renderBg(ms);
        renderList(ms, mouseX, mouseY, partialTicks);
        super.render(ms, mouseX, mouseY, partialTicks);

        drawCenteredString(ms, font, title, width / 2, (height - GUI_HEIGHT) / 2 + 5, 0xFFFFFF);

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

        if (filters.isEmpty() && !renaming) {
            drawCenteredString(ms, font,
                    new TranslatableComponent("vaultfilters.screen.filter_library.empty"),
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
                    selectedId = filters.get(i).id();
                    updateButtonStates();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
            setStatus("Imported as \"" + name + "\"");
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

        importButton.active = false;
        renameButton.active = false;
        duplicateButton.active = false;
        exportButton.active = false;
        treeButton.active = false;
        deleteButton.active = false;
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
        filters = FilterLibraryStore.list();
        int panelHeight = listBottom - listTop;
        int visibleCount = panelHeight / ROW_HEIGHT;
        int maxOffset = Math.max(0, filters.size() - visibleCount);
        if (scrollOffset > maxOffset) scrollOffset = maxOffset;
        updateButtonStates();
    }
}
