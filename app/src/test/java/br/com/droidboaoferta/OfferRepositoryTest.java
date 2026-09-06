package br.com.droidboaoferta;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.junit.Assert.*;

public class OfferRepositoryTest {
    @Test public void simultaneousSourcesKeepEveryOffer() throws Exception {
        SharedPreferences preferences = TestPreferences.create();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < 24; i++) {
                final int id = i;
                tasks.add(workers.submit(() -> {
                    start.await();
                    new OfferRepository(preferences).add(offer(id));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) task.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(24, new OfferRepository(preferences).getRecentForValidation().size());
        } finally { workers.shutdownNow(); }
    }

    @Test public void archiveAndTrashRetainMoreThanThirtySavedItems() {
        SharedPreferences preferences = TestPreferences.create();
        OfferRepository repository = new OfferRepository(preferences);
        for (int i = 0; i < 65; i++) {
            repository.add(offer(i));
            repository.archive("offer-" + i);
        }
        assertEquals(65, repository.getArchived().size());
        for (int i = 0; i < 65; i++) repository.trashArchived("offer-" + i);
        assertTrue(repository.getArchived().isEmpty());
        assertEquals(65, repository.getTrashed().size());
        repository.restoreAllTrashed();
        assertEquals(65, repository.getRecentForValidation().size());
        assertTrue(repository.getTrashed().isEmpty());
    }

    @Test public void badItemDoesNotHideOrOverwriteItsValidNeighbors() throws Exception {
        SharedPreferences preferences = TestPreferences.create();
        JSONArray array = new JSONArray(OfferStorage.encode(java.util.Arrays.asList(offer(1), offer(2))));
        array.put(1, new JSONObject().put("broken", true));
        array.put(new JSONArray(OfferStorage.encode(java.util.Collections.singletonList(offer(3)))).get(0));
        String raw = array.toString();
        preferences.edit().putString("archived_offers", raw).apply();
        OfferRepository repository = new OfferRepository(preferences);
        assertEquals(2, repository.getArchived().size());
        repository.add(offer(4));
        repository.archive("offer-4");
        assertEquals(3, repository.getArchived().size());
        assertEquals(raw, preferences.getString("recovery_archived_offers", ""));
    }

    @Test public void malformedCollectionIsPreservedBeforeRepair() {
        SharedPreferences preferences = TestPreferences.create();
        preferences.edit().putString("recent_offers", "[{truncated").apply();
        OfferRepository repository = new OfferRepository(preferences);
        repository.add(offer(1));
        assertEquals(1, repository.getRecentForValidation().size());
        assertEquals("[{truncated", preferences.getString("recovery_recent_offers", ""));
    }

    private static ObservedOffer offer(int id) {
        return new ObservedOffer("offer-" + id, id + 1, "Produto " + id, "Fonte " + id,
                100, 200, System.currentTimeMillis() - 1000, "https://example.com/" + id, "");
    }
}
