package br.com.droidboaoferta;

import android.content.SharedPreferences;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductNotificationLimiterTest {
    private ObservedOffer offer(String term, String source, double price) {
        return new ObservedOffer(term, source, price, 331, 1000, "https://example.com/offer");
    }

    @Test public void echoSpotSharesThreeSlotsAcrossStoresAndFourteenTelegramGroups() {
        SharedPreferences preferences = TestPreferences.create();
        assertEquals(0, ProductNotificationLimiter.reserveSlot(preferences, offer("Echo Spot", "Pelando", 327), 1000));
        assertEquals(1, ProductNotificationLimiter.reserveSlot(preferences, offer("Echo Spot", "Promobit", 327), 1001));
        assertEquals(2, ProductNotificationLimiter.reserveSlot(preferences, offer("Echo Spot", "Telegram 0", 327), 1002));
        for (int i = 1; i <= 14; i++) assertEquals(-1, ProductNotificationLimiter.reserveSlot(
                preferences, offer("Echo Spot", "Telegram " + i, 327), 1002 + i));
        // A separate limiter instance/source reads the same persistent budget.
        assertEquals(-1, ProductNotificationLimiter.reserveSlot(preferences,
                offer("ECHO SPOT", "Outra loja", 327), 2000));
    }

    @Test public void duplicateSourceAndHiddenCentChangesDoNotCreateAnotherAlert() {
        SharedPreferences preferences = TestPreferences.create();
        assertEquals(0, ProductNotificationLimiter.reserveSlot(preferences, offer("Echo Spot", "Canal", 327.20), 1000));
        assertEquals(-1, ProductNotificationLimiter.reserveSlot(preferences, offer("Echo Spot", "Canal", 327.10), 1001));
    }

    @Test public void simultaneousSourcesCannotReserveMoreThanThreeSlots() throws Exception {
        SharedPreferences preferences = TestPreferences.create();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                String source = "Canal " + i;
                results.add(pool.submit(() -> ProductNotificationLimiter.reserveSlot(preferences,
                        offer("Echo Spot", source, 327), 1000)));
            }
            int accepted = 0;
            for (java.util.concurrent.Future<Integer> result : results) if (result.get() >= 0) accepted++;
            assertEquals(3, accepted);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test public void realLowerPriceAndNewPromotionCanNotifyAgainWithoutAffectingOtherProducts() {
        SharedPreferences preferences = TestPreferences.create();
        for (int i = 0; i < 3; i++) assertEquals(i, ProductNotificationLimiter.reserveSlot(
                preferences, offer("Echo Spot", "Canal " + i, 327), 1000 + i));
        assertEquals(0, ProductNotificationLimiter.reserveSlot(preferences, offer("Echo Spot", "Canal 0", 320), 2000));
        assertEquals(0, ProductNotificationLimiter.reserveSlot(preferences, offer("Outro produto", "Canal 0", 327), 2000));
        assertEquals(0, ProductNotificationLimiter.reserveSlot(preferences,
                offer("Echo Spot", "Canal 0", 327), 2000 + 12L * 60 * 60 * 1000));
    }
}
