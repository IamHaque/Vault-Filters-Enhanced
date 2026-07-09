package net.joseph.vaultfilters.library;

public enum SavedFilterType {
    ATTRIBUTE_FILTER("attribute_filter"),
    LIST_FILTER("list_filter");

    private final String jsonId;

    SavedFilterType(String jsonId) {
        this.jsonId = jsonId;
    }

    public String jsonId() {
        return jsonId;
    }

    public static SavedFilterType fromJsonId(String id) {
        for (SavedFilterType type : values()) {
            if (type.jsonId.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
