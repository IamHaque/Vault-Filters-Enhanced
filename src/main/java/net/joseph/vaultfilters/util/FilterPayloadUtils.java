package net.joseph.vaultfilters.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class FilterPayloadUtils {
    private FilterPayloadUtils() {
    }

    public static final int MAX_LIST_IMPORT_DEPTH = 16;
    public static final int MAX_LIST_IMPORT_NODES = 512;

    public record ListSettings(boolean hasBlacklist, boolean blacklist, boolean hasRespectNBT, boolean respectNBT,
                               boolean hasMatchAll, boolean matchAll) {
    }

    public record AttributeSettings(boolean hasBlacklist, boolean blacklist, boolean hasMatchAll, boolean matchAll) {
    }

    public static JsonObject buildListExportRoot(String name, boolean blacklist, boolean respectNBT, boolean matchAll,
                                                 JsonArray items, String format) {
        JsonObject root = new JsonObject();
        root.addProperty(FilterUiUtils.FORMAT_FIELD, format);
        if (name != null && !name.isBlank()) {
            root.addProperty("name", name);
        }

        JsonObject filter = new JsonObject();
        filter.addProperty("isBlacklist", blacklist);
        filter.addProperty("shouldRespectNBT", respectNBT);
        filter.addProperty("matchAll", matchAll);
        filter.add("items", items == null ? new JsonArray() : items);
        root.add("filter", filter);
        return root;
    }

    public static JsonObject buildAttributeExportRoot(String name, boolean blacklist, boolean matchAll,
                                                      JsonArray attributes, String format) {
        JsonObject root = new JsonObject();
        root.addProperty(FilterUiUtils.FORMAT_FIELD, format);
        root.addProperty("isBlacklist", blacklist);
        root.addProperty("matchAll", matchAll);
        if (name != null && !name.isBlank()) {
            root.addProperty("name", name);
        }
        root.add(FilterUiUtils.ATTRIBUTES_FIELD, attributes == null ? new JsonArray() : attributes);
        return root;
    }

    public static ListSettings readListSettings(JsonObject filterObj) {
        boolean hasBlacklist = filterObj.has("isBlacklist") && filterObj.get("isBlacklist").isJsonPrimitive();
        boolean hasRespectNBT = filterObj.has("shouldRespectNBT") && filterObj.get("shouldRespectNBT").isJsonPrimitive();
        boolean hasMatchAll = filterObj.has("matchAll") && filterObj.get("matchAll").isJsonPrimitive();
        return new ListSettings(
                hasBlacklist,
                hasBlacklist && filterObj.get("isBlacklist").getAsBoolean(),
                hasRespectNBT,
                hasRespectNBT && filterObj.get("shouldRespectNBT").getAsBoolean(),
                hasMatchAll,
                hasMatchAll && filterObj.get("matchAll").getAsBoolean()
        );
    }

    public static AttributeSettings readAttributeSettings(JsonObject root) {
        boolean hasBlacklist = root.has("isBlacklist") && root.get("isBlacklist").isJsonPrimitive();
        boolean hasMatchAll = root.has("matchAll") && root.get("matchAll").isJsonPrimitive();
        return new AttributeSettings(
                hasBlacklist,
                hasBlacklist && root.get("isBlacklist").getAsBoolean(),
                hasMatchAll,
                hasMatchAll && root.get("matchAll").getAsBoolean()
        );
    }

    public static String listModeLabel(boolean isBlacklist, boolean matchAll) {
        if (isBlacklist) {
            return matchAll ? "Deny All" : "Deny Any";
        }
        return matchAll ? "Allow All" : "Allow Any";
    }

    public static String attributeModeLabel(boolean isBlacklist, boolean matchAll) {
        return listModeLabel(isBlacklist, matchAll);
    }

    public static String listTreeHeader(String rootName, boolean isBlacklist, boolean matchAll, boolean respectNBT) {
        StringBuilder tree = new StringBuilder();
        tree.append(rootName == null || rootName.isBlank() ? "List Filter" : rootName)
                .append(" (")
                .append(listModeLabel(isBlacklist, matchAll));
        if (respectNBT) {
            tree.append(", Respect NBT");
        }
        return tree.append(")").toString();
    }

    public static String attributeTreeHeader(String rootName, boolean isBlacklist, boolean matchAll) {
        return (rootName == null || rootName.isBlank() ? "Attribute Filter" : rootName) + " (" + attributeModeLabel(isBlacklist, matchAll) + ")";
    }

    public static boolean validateNestedListImport(JsonArray items) {
        if (items == null) {
            return false;
        }

        int[] nodeCounter = new int[] {0};
        return validateNestedListImport(items, 0, nodeCounter);
    }

    private static boolean validateNestedListImport(JsonArray items, int depth, int[] nodeCounter) {
        if (depth > MAX_LIST_IMPORT_DEPTH) {
            return false;
        }

        for (int index = 0; index < items.size(); index++) {
            if (!items.get(index).isJsonObject()) {
                return false;
            }
            if (!validateNestedListImportNode(items.get(index).getAsJsonObject(), depth, nodeCounter)) {
                return false;
            }
        }

        return true;
    }

    private static boolean validateNestedListImportNode(JsonObject obj, int depth, int[] nodeCounter) {
        if (++nodeCounter[0] > MAX_LIST_IMPORT_NODES) {
            return false;
        }

        if (!obj.has("type") || !obj.get("type").isJsonPrimitive()) {
            return false;
        }

        String type = obj.get("type").getAsString();
        if ("list_filter".equals(type)) {
            if (!obj.has("items") || !obj.get("items").isJsonArray()) {
                return false;
            }
            return validateNestedListImport(obj.getAsJsonArray("items"), depth + 1, nodeCounter);
        }

        return "attribute_filter".equals(type) || "item_filter".equals(type);
    }
}
