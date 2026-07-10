package net.joseph.vaultfilters.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.joseph.vaultfilters.library.SavedFilter;
import net.joseph.vaultfilters.library.SavedFilterType;
import net.joseph.vaultfilters.util.FilterPayloadUtils;
import net.joseph.vaultfilters.util.FilterUiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

import java.util.ArrayList;
import java.util.List;

public final class FilterLibraryPreviewBuilder {

    public static List<Component> buildPreview(SavedFilter sf) {
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

    private static void buildAttributePreview(List<Component> lines, String name, JsonObject root) {
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

    private static void appendAttrLine(List<Component> lines, JsonObject entry, String prefix, boolean isLast) {
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

    private static void addAttrFilterLines(List<Component> lines, JsonObject root, String prefix) {
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

    private static void buildListPreview(List<Component> lines, String name, JsonObject root) {
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

    private static void renderListItemTree(List<Component> lines, List<JsonObject> items, String prefix) {
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

    private static void addNestedListLines(List<Component> lines, JsonObject root, String prefix, int depth) {
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

    public static String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60000) return "just now";
        if (diff < 3600000) return (diff / 60000) + "m ago";
        if (diff < 86400000) return (diff / 3600000) + "h ago";
        return (diff / 86400000) + "d ago";
    }

    private FilterLibraryPreviewBuilder() {}
}
