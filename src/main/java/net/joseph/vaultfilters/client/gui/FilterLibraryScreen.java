package net.joseph.vaultfilters.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.joseph.vaultfilters.library.FilterLibraryStore;
import net.joseph.vaultfilters.library.SavedFilter;
import net.joseph.vaultfilters.library.SavedFilterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.ArrayList;
import java.util.List;

public class FilterLibraryScreen extends Screen {
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 220;

    private FilterList list;
    private Button importButton;
    private Button renameButton;
    private Button duplicateButton;
    private Button exportButton;
    private Button treeButton;
    private Button deleteButton;

    private List<SavedFilter> filters = new ArrayList<>();

    public FilterLibraryScreen() {
        super(new TranslatableComponent("vaultfilters.screen.filter_library"));
    }

    @Override
    protected void init() {
        super.init();
        filters = FilterLibraryStore.list();

        int x = (width - GUI_WIDTH) / 2;
        int y = (height - GUI_HEIGHT) / 2;

        list = new FilterList(this, Minecraft.getInstance(), GUI_WIDTH - 20, GUI_HEIGHT - 60, y + 20, y + GUI_HEIGHT - 40, 24);
        list.setLeftPos(x + 10);
        list.setRightPos(x + GUI_WIDTH - 10);
        addWidget(list);

        for (SavedFilter filter : filters) {
            list.addFilterEntry(new FilterEntry(this, filter));
        }

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

    private void updateButtonStates() {
        boolean selected = list.getSelected() != null;
        importButton.active = true;
        renameButton.active = selected;
        duplicateButton.active = selected;
        exportButton.active = selected;
        treeButton.active = selected;
        deleteButton.active = selected;
    }

    @Override
    public void render(PoseStack ms, int mouseX, int mouseY, float partialTicks) {
        renderBackground(ms);
        renderBg(ms);
        list.render(ms, mouseX, mouseY, partialTicks);
        super.render(ms, mouseX, mouseY, partialTicks);

        drawCenteredString(ms, font, title, width / 2, (height - GUI_HEIGHT) / 2 + 5, 0xFFFFFF);

        if (filters.isEmpty()) {
            drawCenteredString(ms, font,
                    new TranslatableComponent("vaultfilters.screen.filter_library.empty"),
                    width / 2, list.getY0() + 20, 0x808080);
        }
    }

    private void renderBg(PoseStack ms) {
        int x = (width - GUI_WIDTH) / 2;
        int y = (height - GUI_HEIGHT) / 2;
        int border = 4;

        fill(ms, x, y, x + GUI_WIDTH, y + border, 0xFF999999);
        fill(ms, x, y + GUI_HEIGHT - border, x + GUI_WIDTH, y + GUI_HEIGHT, 0xFF999999);
        fill(ms, x, y + border, x + border, y + GUI_HEIGHT - border, 0xFF999999);
        fill(ms, x + GUI_WIDTH - border, y + border, x + GUI_WIDTH, y + GUI_HEIGHT - border, 0xFF999999);
        fill(ms, x + border, y + border, x + GUI_WIDTH - border, y + GUI_HEIGHT - border, 0xFF2D2D2D);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (list.mouseClicked(mouseX, mouseY, button)) {
            updateButtonStates();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onImport() {
        // Will wire up in next milestone
    }

    private void onRename() {
        FilterEntry entry = list.getSelected();
        if (entry != null) {
            // Will wire up in next milestone
        }
    }

    private void onDuplicate() {
        FilterEntry entry = list.getSelected();
        if (entry != null) {
            FilterLibraryStore.duplicate(entry.filter.id());
            refreshList();
        }
    }

    private void onExport() {
        // Will wire up in next milestone
    }

    private void onTree() {
        // Will wire up in next milestone
    }

    private void onDelete() {
        // Will wire up in next milestone
    }

    void refreshList() {
        filters = FilterLibraryStore.list();
        list.clearFilterEntries();
        for (SavedFilter filter : filters) {
            list.addFilterEntry(new FilterEntry(this, filter));
        }
        updateButtonStates();
    }

    static class FilterList extends ObjectSelectionList<FilterEntry> {
        private final FilterLibraryScreen screen;

        public FilterList(FilterLibraryScreen screen, Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            this.screen = screen;
        }

        @Override
        protected void renderBackground(PoseStack ms) {
        }

        public void setRightPos(int right) {
            this.x1 = right;
        }

        public void addFilterEntry(FilterEntry entry) {
            super.addEntry(entry);
        }

        public void clearFilterEntries() {
            super.clearEntries();
        }

        public int getY0() {
            return this.y0;
        }
    }

    static class FilterEntry extends ObjectSelectionList.Entry<FilterEntry> {
        private final FilterLibraryScreen screen;
        private final SavedFilter filter;

        public FilterEntry(FilterLibraryScreen screen, SavedFilter filter) {
            this.screen = screen;
            this.filter = filter;
        }

        @Override
        public void render(PoseStack ms, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTicks) {
            if (hovering) {
                fill(ms, left, top, left + width, top + height, 0x33FFFFFF);
            }
            screen.font.draw(ms, filter.name(), left + 4, top + 2, 0xFFFFFF);

            String typeLabel = filter.type() == SavedFilterType.ATTRIBUTE_FILTER ? "Attr" : "List";
            screen.font.draw(ms, typeLabel, left + width - 30, top + 2, 0x808080);

            String timeStr = formatTime(filter.updatedAt());
            screen.font.draw(ms, timeStr, left + 4, top + 12, 0x606060);
        }

        private static String formatTime(long timestamp) {
            long diff = System.currentTimeMillis() - timestamp;
            if (diff < 60000) return "just now";
            if (diff < 3600000) return (diff / 60000) + "m ago";
            if (diff < 86400000) return (diff / 3600000) + "h ago";
            return (diff / 86400000) + "d ago";
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            screen.list.setSelected(this);
            screen.updateButtonStates();
            return true;
        }

        @Override
        public Component getNarration() {
            return new TextComponent(filter.name());
        }
    }
}
