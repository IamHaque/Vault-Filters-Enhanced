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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class FilterLibraryStore {
    private static final String FORMAT = "vaultfilters.library.v1";
    private static final String INDEX_FILE = "index.json";
    private static final String OLD_FILE = "saved_filters.json";
    private static final String MIGRATED_SUFFIX = ".migrated";
    public static final int MAX_LIBRARY_ENTRIES = 500;

    private static final Pattern UUID_FILE_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.json");

    private static boolean loaded = false;
    private static final LinkedHashMap<UUID, SavedFilter> entries = new LinkedHashMap<>();
    private static int skippedCount = 0;

    private FilterLibraryStore() {
    }

    private static Path libraryDir() {
        return FMLPaths.CONFIGDIR.get().resolve("vaultfilters").resolve("library");
    }

    private static Path indexPath() {
        return libraryDir().resolve(INDEX_FILE);
    }

    private static Path oldFilePath() {
        return FMLPaths.CONFIGDIR.get().resolve("vaultfilters").resolve(OLD_FILE);
    }

    private static Path entryPath(UUID id) {
        return libraryDir().resolve(id.toString() + ".json");
    }

    private static Path tmpPath(Path target) {
        return target.resolveSibling(target.getFileName() + ".tmp");
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = tmpPath(target);
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        entries.clear();
        skippedCount = 0;

        Path libDir = libraryDir();

        try {
            if (Files.exists(oldFilePath()) && !Files.exists(indexPath())) {
                migrateFromOldFormat();
            }
        } catch (Exception e) {
            VaultFilters.LOGGER.warn("FilterLibrary: migration failed: {}", e.getMessage());
        }

        if (!Files.exists(indexPath())) {
            return;
        }

        try {
            String indexContent = Files.readString(indexPath(), StandardCharsets.UTF_8);
            JsonObject indexRoot = JsonParser.parseString(indexContent).getAsJsonObject();

            if (!indexRoot.has("format") || !FORMAT.equals(indexRoot.get("format").getAsString())) {
                VaultFilters.LOGGER.warn("FilterLibrary: unknown index format, rebuilding from entry files");
                rebuildFromEntryFiles();
                return;
            }

            if (!indexRoot.has("entries") || !indexRoot.get("entries").isJsonArray()) {
                VaultFilters.LOGGER.warn("FilterLibrary: no entries in index, rebuilding from entry files");
                rebuildFromEntryFiles();
                return;
            }

            JsonArray indexArr = indexRoot.getAsJsonArray("entries");
            LinkedHashMap<UUID, SavedFilter> loadedEntries = new LinkedHashMap<>();
            int skipped = 0;

            for (JsonElement elem : indexArr) {
                try {
                    if (!elem.isJsonObject()) { skipped++; continue; }
                    JsonObject obj = elem.getAsJsonObject();

                    String idStr = obj.get("id").getAsString();
                    UUID id = UUID.fromString(idStr);

                    String typeStr = obj.get("type").getAsString();
                    SavedFilterType type = SavedFilterType.fromJsonId(typeStr);
                    if (type == null) { skipped++; continue; }

                    String name = obj.has("name") ? obj.get("name").getAsString() : "";
                    if (name.length() > 35) name = name.substring(0, 35);
                    long createdAt = obj.has("createdAt") ? obj.get("createdAt").getAsLong() : System.currentTimeMillis();
                    long updatedAt = obj.has("updatedAt") ? obj.get("updatedAt").getAsLong() : createdAt;
                    boolean favorite = obj.has("favorite") && obj.get("favorite").getAsBoolean();

                    Path entryFile = entryPath(id);
                    JsonObject payload = new JsonObject();
                    if (Files.exists(entryFile)) {
                        try {
                            String entryContent = Files.readString(entryFile, StandardCharsets.UTF_8);
                            JsonObject entryObj = JsonParser.parseString(entryContent).getAsJsonObject();
                            if (entryObj.has("payload") && entryObj.get("payload").isJsonObject()) {
                                payload = entryObj.getAsJsonObject("payload");
                            }
                        } catch (Exception e) {
                            VaultFilters.LOGGER.warn("FilterLibrary: could not read entry file {}: {}", id, e.getMessage());
                        }
                    } else {
                        VaultFilters.LOGGER.warn("FilterLibrary: entry {} referenced in index but file missing", id);
                        skipped++;
                        continue;
                    }

                    loadedEntries.put(id, new SavedFilter(id, type, name, createdAt, updatedAt, payload, favorite));
                } catch (Exception e) {
                    skipped++;
                }
            }

            skippedCount = skipped;
            if (skipped > 0) {
                VaultFilters.LOGGER.warn("FilterLibrary: loaded {} entries from index, skipped {}", loadedEntries.size(), skipped);
            }

            int recovered = recoverOrphanEntries(loadedEntries);
            if (recovered > 0) {
                VaultFilters.LOGGER.info("FilterLibrary: recovered {} orphan entries", recovered);
            }

            entries.putAll(loadedEntries);

            if (recovered > 0 || skipped > 0) {
                saveIndex();
            }

        } catch (Exception e) {
            VaultFilters.LOGGER.warn("FilterLibrary: could not load index: {}", e.getMessage());
            rebuildFromEntryFiles();
        }
    }

    private static int recoverOrphanEntries(LinkedHashMap<UUID, SavedFilter> loadedEntries) {
        int recovered = 0;
        Path libDir = libraryDir();
        if (!Files.isDirectory(libDir)) return 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(libDir)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (INDEX_FILE.equals(fileName)) continue;
                if (!UUID_FILE_PATTERN.matcher(fileName).matches()) continue;

                String idStr = fileName.substring(0, fileName.length() - ".json".length());
                try {
                    UUID id = UUID.fromString(idStr);
                    if (loadedEntries.containsKey(id)) continue;

                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                    SavedFilter sf = SavedFilter.fromEntryJson(obj, id);
                    loadedEntries.put(id, sf);
                    recovered++;
                } catch (Exception e) {
                    VaultFilters.LOGGER.warn("FilterLibrary: could not recover orphan entry {}: {}", idStr, e.getMessage());
                }
            }
        } catch (IOException e) {
            VaultFilters.LOGGER.warn("FilterLibrary: error scanning for orphan entries: {}", e.getMessage());
        }
        return recovered;
    }

    private static void rebuildFromEntryFiles() {
        entries.clear();
        int recovered = recoverOrphanEntries(entries);
        if (recovered > 0) {
            VaultFilters.LOGGER.info("FilterLibrary: rebuilt index from {} entry files", recovered);
            saveIndex();
        }
    }

    private static void migrateFromOldFormat() {
        Path oldFile = oldFilePath();
        if (!Files.exists(oldFile)) return;

        try {
            String content = Files.readString(oldFile, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();

            if (!root.has("format") || !FORMAT.equals(root.get("format").getAsString())) {
                VaultFilters.LOGGER.warn("FilterLibrary: unknown format in {}, skipping migration", OLD_FILE);
                return;
            }
            if (!root.has("entries") || !root.get("entries").isJsonArray()) {
                VaultFilters.LOGGER.warn("FilterLibrary: no entries in {}, skipping migration", OLD_FILE);
                return;
            }

            JsonArray arr = root.getAsJsonArray("entries");
            int migrated = 0;
            for (JsonElement elem : arr) {
                try {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();

                    String idStr = obj.get("id").getAsString();
                    String typeStr = obj.get("type").getAsString();
                    String name = obj.get("name").getAsString();
                    long createdAt = obj.get("createdAt").getAsLong();
                    long updatedAt = obj.get("updatedAt").getAsLong();
                    JsonObject payload = obj.getAsJsonObject("payload");
                    boolean favorite = obj.has("favorite") && obj.get("favorite").getAsBoolean();

                    SavedFilterType type = SavedFilterType.fromJsonId(typeStr);
                    if (type == null) continue;

                    UUID id = UUID.fromString(idStr);
                    if (name.length() > 35) name = name.substring(0, 35);

                    SavedFilter sf = new SavedFilter(id, type, name, createdAt, updatedAt, payload, favorite);
                    saveEntry(sf);
                    entries.put(id, sf);
                    migrated++;
                } catch (Exception e) {
                    VaultFilters.LOGGER.warn("FilterLibrary: skipped entry during migration: {}", e.getMessage());
                }
            }

            saveIndex();
            Files.move(oldFile, oldFile.resolveSibling(OLD_FILE + MIGRATED_SUFFIX), StandardCopyOption.REPLACE_EXISTING);
            VaultFilters.LOGGER.info("FilterLibrary: migrated {} entries from {}", migrated, OLD_FILE);
        } catch (Exception e) {
            VaultFilters.LOGGER.warn("FilterLibrary: migration failed: {}", e.getMessage());
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
        saveEntry(filter);
        saveIndex();
    }

    public static void delete(UUID id) {
        ensureLoaded();
        entries.remove(id);
        saveIndex();
        deleteEntry(id);
    }

    public static void rename(UUID id, String newName) {
        ensureLoaded();
        SavedFilter sf = entries.get(id);
        if (sf != null) {
            if (newName == null) newName = "";
            if (newName.length() > 35) newName = newName.substring(0, 35);
            sf.setName(newName).touch();
            saveIndex();
        }
    }

    public static void toggleFavorite(UUID id) {
        ensureLoaded();
        SavedFilter sf = entries.get(id);
        if (sf != null) {
            sf.toggleFavorite();
            saveIndex();
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
        saveEntry(copy);
        saveIndex();
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

    private static void saveEntry(SavedFilter sf) {
        try {
            String json = FilterUiUtils.PRETTY_GSON.toJson(sf.toEntryJson());
            atomicWrite(entryPath(sf.id()), json);
        } catch (IOException e) {
            VaultFilters.LOGGER.error("FilterLibrary: failed to save entry {}: {}", sf.id(), e.getMessage());
        }
    }

    private static void deleteEntry(UUID id) {
        try {
            Files.deleteIfExists(entryPath(id));
        } catch (IOException e) {
            VaultFilters.LOGGER.warn("FilterLibrary: failed to delete entry file {}: {}", id, e.getMessage());
        }
    }

    private static void saveIndex() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("format", FORMAT);

            JsonArray arr = new JsonArray();
            for (SavedFilter sf : entries.values()) {
                arr.add(sf.toIndexJson());
            }
            root.add("entries", arr);

            String json = FilterUiUtils.PRETTY_GSON.toJson(root);
            atomicWrite(indexPath(), json);
        } catch (IOException e) {
            VaultFilters.LOGGER.error("FilterLibrary: failed to save index: {}", e.getMessage());
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
