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
    @Test public void trashingDisplayedProductsPreservesOtherSectionsAndSupportsRestoring() {
        OfferRepository repository = new OfferRepository(TestPreferences.create());
        ObservedOffer product = offer(1);
        ObservedOffer property = new ObservedOffer("property|23|listing", 23L,
                "Condomínio", "QuintoAndar", 300000, 400000,
                System.currentTimeMillis(), "https://example.com/property", "");
        repository.add(product);
        repository.add(property);

        assertTrue(repository.trashDisplayedRecent(java.util.Collections.singletonList(product)));
        assertEquals(1, repository.getRecentForValidation().size());
        assertEquals(property.getId(), repository.getRecentForValidation().get(0).getId());
        assertEquals(product.getId(), repository.getTrashed().get(0).getId());

        repository.restoreTrashed(product.getId());
        assertTrue(repository.getTrashed().isEmpty());
        assertEquals(2, repository.getRecentForValidation().size());
    }

    @Test public void confirmationPreservesDisplayedOfferEvenIfConcurrentUpdateRemovedIt() {
        OfferRepository repository = new OfferRepository(TestPreferences.create());
        ObservedOffer displayed = offer(1);
        repository.add(displayed);
        List<ObservedOffer> dialogSnapshot = repository.getRecentForValidation();
        repository.clearRecentOffer(displayed.getId());
        repository.add(offer(2));

        assertTrue(repository.trashDisplayedRecent(dialogSnapshot));
        assertEquals("offer-2", repository.getRecentForValidation().get(0).getId());
        assertEquals(displayed.getId(), repository.getTrashed().get(0).getId());
        assertEquals(displayed.getPrice(), repository.getTrashed().get(0).getPrice(), 0d);
        assertEquals(displayed.getLink(), repository.getTrashed().get(0).getLink());
        assertFalse(repository.trashDisplayedRecent(dialogSnapshot));
        assertEquals(1, repository.getTrashed().size());
    }

    @Test public void confirmationMovesLatestStoredVersionInsteadOfOverwritingItWithOldDisplay() {
        OfferRepository repository = new OfferRepository(TestPreferences.create());
        ObservedOffer displayed = offer(1);
        ObservedOffer updated = new ObservedOffer(displayed.getId(), displayed.getInterestId(),
                displayed.getInterest(), displayed.getSource(), 90, 200,
                System.currentTimeMillis(), displayed.getLink(), "");
        repository.add(displayed);
        repository.add(updated);

        assertTrue(repository.trashDisplayedRecent(java.util.Collections.singletonList(displayed)));
        assertTrue(repository.getRecentForValidation().isEmpty());
        assertEquals(90d, repository.getTrashed().get(0).getPrice(), 0d);
    }

    @Test public void loweringCondominiumLimitsRemovesAboveLimitMarketReferences() {
        SharedPreferences preferences = TestPreferences.create();
        OfferRepository repository = new OfferRepository(preferences);
        double[] limits = {340000, 600000, 300000, 420000};
        double[] prices = {345000, 630000, 320000, 425000};
        List<Interest> interests = new ArrayList<>();
        for (int i = 0; i < limits.length; i++) {
            long id = i + 1;
            interests.add(new Interest(id, "Condomínio " + id, limits[i], Interest.TYPE_PROPERTY));
            repository.replacePropertyMarketReference(new ObservedOffer(
                    "property_market|" + id + "|listing", id, "Condomínio " + id,
                    "QuintoAndar", prices[i], 900000, System.currentTimeMillis(),
                    "https://example.com/" + id, ""));
        }

        repository.reconcileRecentWithInterests(interests);

        assertTrue(repository.getRecentForValidation().isEmpty());
    }

    @Test public void condominiumPriceLimitIncludesEqualityForOffersAndReferences() {
        Interest interest = new Interest(23L, "Princes", 600000, Interest.TYPE_PROPERTY);
        for (String prefix : new String[]{"property", "property_market"}) {
            ObservedOffer equal = new ObservedOffer(prefix + "|23|listing", 23L,
                    "Princes", "QuintoAndar", 600000, 900000,
                    System.currentTimeMillis(), "https://example.com/listing", "");
            ObservedOffer above = new ObservedOffer(prefix + "|23|above", 23L,
                    "Princes", "QuintoAndar", 600000.01, 900000,
                    System.currentTimeMillis(), "https://example.com/above", "");
            assertTrue(OfferRepository.matchesInterestPriceLimit(equal, interest));
            assertFalse(OfferRepository.matchesInterestPriceLimit(above, interest));
        }
    }

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
        repository.add(new ObservedOffer("kabum|legacy-hash", 7L, "S25", "KaBuM", 3999,
                9000, now - 60_000, "https://example.com/produto/123", ""));

        repository.refreshStoreProduct(storeOffer("kabum|7|123",
                "Galaxy S25 256 GB Preto", 3999, now));

        List<ObservedOffer> offers = repository.getRecentForValidation();
        assertEquals(1, offers.size());
        assertEquals("kabum|7|123", offers.get(0).getId());
    }

    @Test public void removesOffersFromRetiredStorePrefixes() {
        SharedPreferences preferences = TestPreferences.create();
        OfferRepository repository = new OfferRepository(preferences);
        long now = System.currentTimeMillis();
        repository.add(storeOffer("kabum_legacy|7|123", "Produto aposentado", 3999, now - 1));
        repository.add(storeOffer("kabum|7|456", "Oferta relâmpago", 3999, now));

        repository.removeRetiredStoreOffers();

        assertEquals(1, repository.getRecentForValidation().size());
        assertEquals("kabum|7|456", repository.getRecentForValidation().get(0).getId());
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
