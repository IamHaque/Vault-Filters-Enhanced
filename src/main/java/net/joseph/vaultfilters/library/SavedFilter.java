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

    public SavedFilter(UUID id, SavedFilterType type, String name, long createdAt, long updatedAt, JsonObject payload) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.payload = payload;
    }

    public static SavedFilter createNew(SavedFilterType type, String name, JsonObject payload) {
        long now = System.currentTimeMillis();
        return new SavedFilter(UUID.randomUUID(), type, name, now, now, payload);
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

    public SavedFilter withName(String newName) {
        this.name = newName;
        return this;
    }

    public SavedFilter withPayload(JsonObject newPayload) {
        this.payload = newPayload;
        return this;
    }

    public SavedFilter touch() {
        this.updatedAt = System.currentTimeMillis();
        return this;
    }
}
