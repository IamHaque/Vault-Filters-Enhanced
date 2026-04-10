package net.joseph.vaultfilters.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.joseph.vaultfilters.access.AbstractFilterMenuAdvancedAccessor;
import net.joseph.vaultfilters.network.MenuFeaturesPacket;
import net.joseph.vaultfilters.network.VFMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.simibubi.create.content.logistics.filter.AbstractFilterMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FilterUiUtils {
    private FilterUiUtils() {
    }

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    public static final int MAX_IMPORT_CHARS = 262_144;

    public static final String LIST_FORMAT_V2 = "vaultfilters.list_filter.v2";
    public static final String LIST_FORMAT_V1 = "vaultfilters.list_filter.v1";
    public static final String ATTRIBUTE_FORMAT_V1 = "vaultfilters.attribute_filter.v1";
    public static final String FORMAT_FIELD = "format";
    public static final String ATTRIBUTES_FIELD = "attributes";

    public static final class TagEntry {
        private final String key;
        private final Tag value;

        public TagEntry(String key, Tag value) {
            this.key = key;
            this.value = value;
        }

        public String key() {
            return key;
        }

        public Tag value() {
            return value;
        }
    }

    public static boolean isShiftDownSafe() {
        try {
            return Screen.hasShiftDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isControlDownSafe() {
        try {
            return Screen.hasControlDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void notifyUser(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, true);
        }
    }

    public static String getCurrentFilterName(AbstractFilterMenu menu) {
        return ((AbstractFilterMenuAdvancedAccessor) menu).vault_filters$getName();
    }

    public static void applyImportedFilterName(AbstractFilterMenu menu, String importedName) {
        if (!shouldApplyImportedName(menu, importedName)) {
            return;
        }

        String normalizedName = importedName == null ? "" : importedName.trim();
        if (normalizedName.length() > 35) {
            normalizedName = normalizedName.substring(0, 35);
        }

        ((AbstractFilterMenuAdvancedAccessor) menu).vault_filters$setName(normalizedName);
        VFMessages.VFCHANNEL.sendToServer(new MenuFeaturesPacket(MenuFeaturesPacket.MenuAction.CHANGE_NAME, normalizedName));
    }

    private static boolean shouldApplyImportedName(AbstractFilterMenu menu, String importedName) {
        if (importedName == null) {
            return false;
        }

        String normalizedName = importedName.trim();
        if (normalizedName.isEmpty()) {
            return false;
        }

        ItemStack contentHolder = menu.contentHolder;
        String defaultName = new ItemStack(contentHolder.getItem()).getHoverName().getString();
        return !normalizedName.equals(defaultName);
    }

    public static TagEntry firstSortedDataEntry(CompoundTag tag, String... excludedKeys) {
        Set<String> excluded = new HashSet<>();
        Collections.addAll(excluded, excludedKeys);

        List<String> candidates = new ArrayList<>();
        for (String candidate : tag.getAllKeys()) {
            if (excluded.contains(candidate)) {
                continue;
            }
            candidates.add(candidate);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Collections.sort(candidates);
        String key = candidates.get(0);
        return new TagEntry(key, tag.get(key));
    }

    public static String normalizeAttributeSummary(String key, String summary) {
        if (summary == null || summary.isBlank() || key == null || key.isBlank()) {
            return summary;
        }

        String spacedPrefix = key + " = ";
        if (summary.startsWith(spacedPrefix)) {
            return summary.substring(spacedPrefix.length());
        }

        String compactPrefix = key + "=";
        if (summary.startsWith(compactPrefix)) {
            return summary.substring(compactPrefix.length());
        }

        return summary;
    }

    public static String summarizeTag(Tag tag) {
        if (tag == null) {
            return "<null>";
        }

        if (tag instanceof CompoundTag compound) {
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (String key : compound.getAllKeys()) {
                if (shown == 3) {
                    sb.append(", ...");
                    break;
                }
                if (shown > 0) {
                    sb.append(", ");
                }
                sb.append(key).append(" = ").append(summarizeTag(compound.get(key)));
                shown++;
            }
            return sb.length() == 0 ? "{}" : sb.toString();
        }

        if (tag instanceof ListTag list) {
            StringBuilder sb = new StringBuilder("[");
            int shown = Math.min(3, list.size());
            for (int i = 0; i < shown; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(summarizeTag(list.get(i)));
            }
            if (list.size() > shown) {
                sb.append(", ...");
            }
            sb.append("]");
            return sb.toString();
        }

        String text = tag.getAsString();
        if (text == null || text.isBlank()) {
            text = tag.toString();
        }
        if (text.length() > 120) {
            return text.substring(0, 117) + "...";
        }
        return text;
    }
}
