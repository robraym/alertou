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

    @Test public void clearingPropertyAlertKeepsItsLowestMarketReference() {
        SharedPreferences preferences = TestPreferences.create();
        OfferRepository repository = new OfferRepository(preferences);
        long interestId = 23L;
        long observedAt = System.currentTimeMillis();
        repository.add(new ObservedOffer("property|23|listing-a", interestId,
                "Edifício Sol", "Alerta de imóvel", 500000, 600000,
                observedAt, "https://example.com/a", ""));
        repository.add(new ObservedOffer("property_market|23|listing-b", interestId,
                "Edifício Sol", "Menor valor", 480000, 600000,
                observedAt, "https://example.com/b", ""));

        repository.clearRecentForPropertyAlert(interestId);

        List<ObservedOffer> offers = repository.getRecentForValidation();
        assertEquals(1, offers.size());
        assertEquals("property_market|23|listing-b", offers.get(0).getId());
    }

    @Test public void replacingLowestMarketReferenceKeepsOnlyOnePerPropertyAlert() {
        SharedPreferences preferences = TestPreferences.create();
        OfferRepository repository = new OfferRepository(preferences);
        long observedAt = System.currentTimeMillis() - 1_000L;
        repository.replacePropertyMarketReference(new ObservedOffer(
                "property_market|23|listing-a", 23L, "Edifício Sol", "QuintoAndar",
                500000, 600000, observedAt, "https://example.com/a", ""));
        repository.replacePropertyMarketReference(new ObservedOffer(
                "property_market|23|listing-b", 23L, "Edifício Sol", "QuintoAndar",
                480000, 600000, observedAt + 1, "https://example.com/b", ""));
        repository.replacePropertyMarketReference(new ObservedOffer(
                "property_market|24|listing-c", 24L, "Edifício Lua", "QuintoAndar",
                450000, 600000, observedAt + 2, "https://example.com/c", ""));

        List<ObservedOffer> offers = repository.getRecentForValidation();
        assertEquals(2, offers.size());
        assertTrue(offers.stream().anyMatch(offer -> offer.getId().equals("property_market|23|listing-b")));
        assertTrue(offers.stream().anyMatch(offer -> offer.getId().equals("property_market|24|listing-c")));
    }

    @Test public void sameStoreProductKeepsNewestObservation() {
        SharedPreferences preferences = TestPreferences.create();
        OfferRepository repository = new OfferRepository(preferences);
        long now = System.currentTimeMillis();
        repository.add(storeOffer("kabum|7|123", "Galaxy S25 256 GB Preto", 3999, now));
        repository.add(storeOffer("kabum|7|123", "Galaxy S25 256 GB Preto", 4299, now - 1_000));

        List<ObservedOffer> offers = repository.getRecentForValidation();
        assertEquals(1, offers.size());
        assertEquals(3999, offers.get(0).getPrice(), 0.001);
        assertEquals(now, offers.get(0).getObservedAt());
    }

    @Test public void differentStoreVariantsAreNotMerged() {
        SharedPreferences preferences = TestPreferences.create();
        OfferRepository repository = new OfferRepository(preferences);
        long now = System.currentTimeMillis();
        repository.add(storeOffer("kabum|7|123", "Galaxy S25 256 GB Preto", 3999, now));
        repository.add(new ObservedOffer("kabum|7|456", 7L, "S25", "KaBuM", 3999, 9000,
                now, "https://example.com/produto/456", "", "Galaxy S25 512 GB Azul"));

        assertEquals(2, repository.getRecentForValidation().size());
    }

    @Test public void productTitleSurvivesStorageRoundTrip() {
        SharedPreferences preferences = TestPreferences.create();
        OfferRepository repository = new OfferRepository(preferences);
        repository.add(storeOffer("kabum|7|123", "Galaxy S25 256 GB Preto", 3999,
                System.currentTimeMillis()));

        ObservedOffer restored = repository.getRecentForValidation().get(0);
        assertEquals("Galaxy S25 256 GB Preto", restored.getProductTitle());
        assertEquals("Galaxy S25 256 GB Preto", restored.getDisplayTitle());
    }

    @Test public void unchangedStoreProductGainsTitleWithoutChangingObservationTime() {
        SharedPreferences preferences = TestPreferences.create();
        OfferRepository repository = new OfferRepository(preferences);
        long firstObservedAt = System.currentTimeMillis() - 60_000;
        repository.add(new ObservedOffer("kabum|7|123", 7L, "S25", "KaBuM", 3999, 9000,
                firstObservedAt, "https://example.com/produto/123", ""));

        repository.refreshStoreProduct(storeOffer("kabum|7|123",
                "Galaxy S25 256 GB Preto", 3999, System.currentTimeMillis()));

        ObservedOffer refreshed = repository.getRecentForValidation().get(0);
        assertEquals(firstObservedAt, refreshed.getObservedAt());
        assertEquals("Galaxy S25 256 GB Preto", refreshed.getDisplayTitle());
    }

    @Test public void metadataRefreshReplacesLegacyStoreIdentity() {
        SharedPreferences preferences = TestPreferences.create();
        OfferRepository repository = new OfferRepository(preferences);
        long now = System.currentTimeMillis();
        repository.add(new ObservedOffer("kabum_catalog|legacy-hash", 7L, "S25", "KaBuM", 3999,
                9000, now - 60_000, "https://example.com/produto/123", ""));

        repository.refreshStoreProduct(storeOffer("kabum_catalog|7|123",
                "Galaxy S25 256 GB Preto", 3999, now));

        List<ObservedOffer> offers = repository.getRecentForValidation();
        assertEquals(1, offers.size());
        assertEquals("kabum_catalog|7|123", offers.get(0).getId());
    }

    private static ObservedOffer storeOffer(String id, String productTitle, double price, long observedAt) {
        return new ObservedOffer(id, 7L, "S25", "KaBuM", price, 9000, observedAt,
                "https://example.com/produto/123", "", productTitle);
    }

    private static ObservedOffer offer(int id) {
        return new ObservedOffer("offer-" + id, id + 1, "Produto " + id, "Fonte " + id,
                100, 200, System.currentTimeMillis() - 1000, "https://example.com/" + id, "");
    }
}
