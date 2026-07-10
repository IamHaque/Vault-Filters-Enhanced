package net.joseph.vaultfilters.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.joseph.vaultfilters.VaultFilters;
import net.joseph.vaultfilters.util.FilterUiUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public final class FilterLibraryStore {
    private static final String FORMAT = "vaultfilters.library.v1";
    private static final String FILE_NAME = "saved_filters.json";
    public static final int MAX_LIBRARY_ENTRIES = 500;

    private static boolean loaded = false;
    private static final LinkedHashMap<UUID, SavedFilter> entries = new LinkedHashMap<>();
    private static int skippedCount = 0;

    private FilterLibraryStore() {
    }

    private static Path storagePath() {
        return FMLPaths.CONFIGDIR.get().resolve("vaultfilters").resolve(FILE_NAME);
    }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Path path = storagePath();
        if (!Files.exists(path)) return;

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();

            if (!root.has("format") || !FORMAT.equals(root.get("format").getAsString())) {
                VaultFilters.LOGGER.warn("FilterLibrary: unknown format in {}, treating as empty", FILE_NAME);
                return;
            }

            if (!root.has("entries") || !root.get("entries").isJsonArray()) {
                VaultFilters.LOGGER.warn("FilterLibrary: no entries array in {}, treating as empty", FILE_NAME);
                return;
            }

            JsonArray arr = root.getAsJsonArray("entries");
            int skipped = 0;
            entries.clear();

            for (JsonElement elem : arr) {
                try {
                    if (!elem.isJsonObject()) {
                        skipped++;
                        continue;
                    }
                    JsonObject obj = elem.getAsJsonObject();

                    String idStr = obj.get("id").getAsString();
                    String typeStr = obj.get("type").getAsString();
                    String name = obj.get("name").getAsString();
                    long createdAt = obj.get("createdAt").getAsLong();
                    long updatedAt = obj.get("updatedAt").getAsLong();
                    JsonObject payload = obj.getAsJsonObject("payload");

                    SavedFilterType type = SavedFilterType.fromJsonId(typeStr);
                    if (type == null || idStr == null) {
                        skipped++;
                        continue;
                    }

                    UUID id = UUID.fromString(idStr);
                    if (name == null) name = "";
                    if (name.length() > 35) name = name.substring(0, 35);
                    boolean favorite = obj.has("favorite") && obj.get("favorite").getAsBoolean();

                    entries.put(id, new SavedFilter(id, type, name, createdAt, updatedAt, payload, favorite));
                } catch (Exception e) {
                    skipped++;
                }
            }

            skippedCount = skipped;
            if (skipped > 0) {
                VaultFilters.LOGGER.warn("FilterLibrary: loaded {} entries, skipped {} invalid", entries.size(), skipped);
            }
        } catch (Exception e) {
            VaultFilters.LOGGER.warn("FilterLibrary: could not load {}: {}", FILE_NAME, e.getMessage());
        }
    }

    public static int skippedCount() {
        return skippedCount;
    }

    public static boolean hasWarnings() {
        return skippedCount > 0;
    }

    public static void resetWarnings() {
        skippedCount = 0;
    }

    public static List<SavedFilter> list() {
        ensureLoaded();
        return new ArrayList<>(entries.values());
    }

    public static List<SavedFilter> list(SortMode sort) {
        List<SavedFilter> result = list();
        sort.sort(result);
        return result;
    }

    public static List<SavedFilter> listByType(SavedFilterType type) {
        ensureLoaded();
        List<SavedFilter> result = new ArrayList<>();
        for (SavedFilter sf : entries.values()) {
            if (sf.type() == type) result.add(sf);
        }
        return result;
    }

    public static List<SavedFilter> listByType(SavedFilterType type, SortMode sort) {
        List<SavedFilter> result = listByType(type);
        sort.sort(result);
        return result;
    }

    public static List<SavedFilter> listFavorites() {
        ensureLoaded();
        List<SavedFilter> result = new ArrayList<>();
        for (SavedFilter sf : entries.values()) {
            if (sf.favorite()) result.add(sf);
        }
        return result;
    }

    public static List<SavedFilter> listFavorites(SavedFilterType type) {
        ensureLoaded();
        List<SavedFilter> result = new ArrayList<>();
        for (SavedFilter sf : entries.values()) {
            if (sf.favorite() && sf.type() == type) result.add(sf);
        }
        return result;
    }

    public static SavedFilter get(UUID id) {
        ensureLoaded();
        return entries.get(id);
    }

    public static SavedFilter get(String id) {
        try {
            return get(UUID.fromString(id));
        } catch (Exception e) {
            return null;
        }
    }

    public static void upsert(SavedFilter filter) {
        ensureLoaded();
        entries.put(filter.id(), filter);
        save();
    }

    public static void delete(UUID id) {
        ensureLoaded();
        entries.remove(id);
        save();
    }

    public static void rename(UUID id, String newName) {
        ensureLoaded();
        SavedFilter sf = entries.get(id);
        if (sf != null) {
            if (newName == null) newName = "";
            if (newName.length() > 35) newName = newName.substring(0, 35);
            sf.setName(newName).touch();
            save();
        }
    }

    public static SavedFilter duplicate(UUID id) {
        ensureLoaded();
        SavedFilter original = entries.get(id);
        if (original == null) return null;

        String dupName = original.name();
        if (dupName.length() > 28) {
            dupName = dupName.substring(0, 28);
        }
        dupName = dupName + " (Copy)";

        SavedFilter copy = SavedFilter.createNew(original.type(), dupName, original.payload().deepCopy());
        entries.put(copy.id(), copy);
        save();
        return copy;
    }

    public static boolean existsByName(SavedFilterType type, String name) {
        return existsByName(type, name, null);
    }

    public static boolean existsByName(SavedFilterType type, String name, UUID excludingId) {
        ensureLoaded();
        for (SavedFilter sf : entries.values()) {
            if (sf.type() == type && sf.name().equals(name) && !sf.id().equals(excludingId)) {
                return true;
            }
        }
        return false;
    }

    public static SavedFilter findByName(SavedFilterType type, String name) {
        ensureLoaded();
        for (SavedFilter sf : entries.values()) {
            if (sf.type() == type && sf.name().equals(name)) {
                return sf;
            }
        }
        return null;
    }

    public static int count() {
        ensureLoaded();
        return entries.size();
    }

    public static boolean canAddMore() {
        return count() < MAX_LIBRARY_ENTRIES;
    }

    private static void save() {
        Path path = storagePath();
        Path tmpPath = path.resolveSibling(FILE_NAME + ".tmp");

        try {
            Files.createDirectories(path.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("format", FORMAT);

            JsonArray arr = new JsonArray();
            for (SavedFilter sf : entries.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", sf.id().toString());
                obj.addProperty("type", sf.type().jsonId());
                obj.addProperty("name", sf.name());
                obj.addProperty("createdAt", sf.createdAt());
                obj.addProperty("updatedAt", sf.updatedAt());
                obj.addProperty("favorite", sf.favorite());
                obj.add("payload", sf.payload());
                arr.add(obj);
            }
            root.add("entries", arr);

            String json = FilterUiUtils.PRETTY_GSON.toJson(root);
            Files.writeString(tmpPath, json, StandardCharsets.UTF_8);

            try {
                Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            VaultFilters.LOGGER.error("FilterLibrary: failed to save {}: {}", FILE_NAME, e.getMessage());
        }
    }

    public enum SortMode {
        NAME_ASC,
        NEWEST_FIRST,
        OLDEST_FIRST;

        public void sort(List<SavedFilter> list) {
            switch (this) {
                case NAME_ASC -> list.sort(Comparator.comparing(SavedFilter::name, String.CASE_INSENSITIVE_ORDER));
                case NEWEST_FIRST -> list.sort(Comparator.comparingLong(SavedFilter::updatedAt).reversed());
                case OLDEST_FIRST -> list.sort(Comparator.comparingLong(SavedFilter::updatedAt));
            }
        }
    }
}
