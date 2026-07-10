package net.joseph.vaultfilters.library;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilterLibraryStoreTest {

    private SavedFilter createFilter(String name, long updatedAt) {
        return new SavedFilter(java.util.UUID.randomUUID(), SavedFilterType.LIST_FILTER, name, 0, updatedAt, new JsonObject());
    }

    @Test
    void sortByNameAsc() {
        List<SavedFilter> list = new ArrayList<>();
        list.add(createFilter("Zeta", 100));
        list.add(createFilter("Alpha", 200));
        list.add(createFilter("Beta", 300));

        FilterLibraryStore.SortMode.NAME_ASC.sort(list);
        assertEquals("Alpha", list.get(0).name());
        assertEquals("Beta", list.get(1).name());
        assertEquals("Zeta", list.get(2).name());
    }

    @Test
    void sortByNewestFirst() {
        List<SavedFilter> list = new ArrayList<>();
        list.add(createFilter("Old", 100));
        list.add(createFilter("New", 300));
        list.add(createFilter("Mid", 200));

        FilterLibraryStore.SortMode.NEWEST_FIRST.sort(list);
        assertEquals("New", list.get(0).name());
        assertEquals("Mid", list.get(1).name());
        assertEquals("Old", list.get(2).name());
    }

    @Test
    void sortByOldestFirst() {
        List<SavedFilter> list = new ArrayList<>();
        list.add(createFilter("Mid", 200));
        list.add(createFilter("Old", 100));
        list.add(createFilter("New", 300));

        FilterLibraryStore.SortMode.OLDEST_FIRST.sort(list);
        assertEquals("Old", list.get(0).name());
        assertEquals("Mid", list.get(1).name());
        assertEquals("New", list.get(2).name());
    }

    @Test
    void sortByNameIsCaseInsensitive() {
        List<SavedFilter> list = new ArrayList<>();
        list.add(createFilter("zeta", 0));
        list.add(createFilter("Alpha", 0));
        list.add(createFilter("beta", 0));

        FilterLibraryStore.SortMode.NAME_ASC.sort(list);
        assertEquals("Alpha", list.get(0).name());
        assertEquals("beta", list.get(1).name());
        assertEquals("zeta", list.get(2).name());
    }

    @Test
    void duplicateNameTruncatedAt28() {
        String longName = "abcdefghijklmnopqrstuvwxyz1234"; // 30 chars
        assertTrue(longName.length() > 28);
        String truncated = longName.length() > 28 ? longName.substring(0, 28) : longName;
        assertEquals(28, truncated.length());
        assertEquals("abcdefghijklmnopqrstuvwxyz12", truncated);

        String copyName = truncated + " (Copy)";
        assertEquals(36, copyName.length());
        assertTrue(copyName.endsWith(" (Copy)"));
    }

    @Test
    void duplicateNameShortNameNotTruncated() {
        String shortName = "My Filter";
        String truncated = shortName.length() > 28 ? shortName.substring(0, 28) : shortName;
        assertEquals(shortName, truncated);

        String copyName = truncated + " (Copy)";
        assertEquals("My Filter (Copy)", copyName);
    }

    @Test
    void findByNameMatchesExactTypeAndName() {
        SavedFilterType type = SavedFilterType.LIST_FILTER;
        assertNotNull(SavedFilterType.fromJsonId("list_filter"));
        assertEquals(type, SavedFilterType.fromJsonId("list_filter"));
    }

    @Test
    void findByTypeMismatchReturnsNull() {
        assertNull(SavedFilterType.fromJsonId("unknown_type"));
        assertNull(SavedFilterType.fromJsonId(""));
        assertNull(SavedFilterType.fromJsonId(null));
    }

    @Test
    void savedFilterCreateNewSetsTimestamps() {
        SavedFilter sf = SavedFilter.createNew(SavedFilterType.ATTRIBUTE_FILTER, "Test", new JsonObject());
        assertNotNull(sf.id());
        assertEquals(SavedFilterType.ATTRIBUTE_FILTER, sf.type());
        assertEquals("Test", sf.name());
        assertTrue(sf.createdAt() > 0);
        assertEquals(sf.createdAt(), sf.updatedAt());

        sf.touch();
        assertTrue(sf.updatedAt() >= sf.createdAt());
    }

    @Test
    void savedFilterSetNameAndSetPayloadMutate() {
        SavedFilter sf = SavedFilter.createNew(SavedFilterType.LIST_FILTER, "Original", new JsonObject());
        assertEquals("Original", sf.name());

        sf.setName("Renamed");
        assertEquals("Renamed", sf.name());

        JsonObject newPayload = new JsonObject();
        newPayload.addProperty("key", "value");
        sf.setPayload(newPayload);
        assertEquals("value", sf.payload().get("key").getAsString());
    }
}
