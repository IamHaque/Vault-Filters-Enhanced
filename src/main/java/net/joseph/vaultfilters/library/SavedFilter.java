package net.joseph.vaultfilters.library;

import com.google.gson.JsonObject;

import java.util.UUID;

public class SavedFilter {
    private final UUID id;
    private final SavedFilterType type;
    private String name;
    private final long createdAt;
    private long updatedAt;
    private JsonObject payload;
    private boolean favorite;

    public SavedFilter(UUID id, SavedFilterType type, String name, long createdAt, long updatedAt, JsonObject payload) {
        this(id, type, name, createdAt, updatedAt, payload, false);
    }

    public SavedFilter(UUID id, SavedFilterType type, String name, long createdAt, long updatedAt, JsonObject payload, boolean favorite) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.payload = payload;
        this.favorite = favorite;
    }

    public static SavedFilter createNew(SavedFilterType type, String name, JsonObject payload) {
        long now = System.currentTimeMillis();
        return new SavedFilter(UUID.randomUUID(), type, name, now, now, payload, false);
    }

    public UUID id() {
        return id;
    }

    public SavedFilterType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public long createdAt() {
        return createdAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public JsonObject payload() {
        return payload;
    }

    public boolean favorite() {
        return favorite;
    }

    public SavedFilter setName(String newName) {
        this.name = newName;
        return this;
    }

    public SavedFilter setPayload(JsonObject newPayload) {
        this.payload = newPayload;
        return this;
    }

    public SavedFilter setFavorite(boolean favorite) {
        this.favorite = favorite;
        return this;
    }

    public SavedFilter toggleFavorite() {
        this.favorite = !this.favorite;
        return this;
    }

    public SavedFilter touch() {
        this.updatedAt = System.currentTimeMillis();
        return this;
    }

    public JsonObject toIndexJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id.toString());
        obj.addProperty("type", type.jsonId());
        obj.addProperty("name", name);
        obj.addProperty("createdAt", createdAt);
        obj.addProperty("updatedAt", updatedAt);
        obj.addProperty("favorite", favorite);
        return obj;
    }

    public JsonObject toEntryJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type.jsonId());
        obj.addProperty("name", name);
        obj.addProperty("createdAt", createdAt);
        obj.addProperty("updatedAt", updatedAt);
        obj.addProperty("favorite", favorite);
        if (payload != null) {
            obj.add("payload", payload);
        }
        return obj;
    }

    public static SavedFilter fromEntryJson(JsonObject obj, UUID id) {
        String typeStr = obj.get("type").getAsString();
        SavedFilterType type = SavedFilterType.fromJsonId(typeStr);
        if (type == null) throw new IllegalArgumentException("Unknown type: " + typeStr);

        String name = obj.has("name") ? obj.get("name").getAsString() : "";
        if (name.length() > 35) name = name.substring(0, 35);

        long createdAt = obj.has("createdAt") ? obj.get("createdAt").getAsLong() : System.currentTimeMillis();
        long updatedAt = obj.has("updatedAt") ? obj.get("updatedAt").getAsLong() : createdAt;
        boolean favorite = obj.has("favorite") && obj.get("favorite").getAsBoolean();
        JsonObject payload = obj.has("payload") && obj.get("payload").isJsonObject()
                ? obj.getAsJsonObject("payload") : new JsonObject();

        return new SavedFilter(id, type, name, createdAt, updatedAt, payload, favorite);
    }
}
