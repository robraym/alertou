package br.com.droidboaoferta;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.List;
import java.util.concurrent.TimeUnit;

final class SamsungDiscountOfferMonitor {
    static final String ACTION_STATUS_CHANGED = "br.com.droidboaoferta.SAMSUNG_DISCOUNT_OFFER_STATUS_CHANGED";
    private static final SamsungDiscountOfferMonitor INSTANCE = new SamsungDiscountOfferMonitor();
    private final CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();
    private Context appContext;

    private SamsungDiscountOfferMonitor() { }
    static SamsungDiscountOfferMonitor getInstance() { return INSTANCE; }

    synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (MonitorRunPolicy.canRun(appContext)) scheduler.start(() -> checkSafely(false),
                TimeUnit.SECONDS.toMillis(SamsungDiscountOfferSource.getCheckIntervalSeconds(appContext)));
    }
    synchronized void stop() { scheduler.stop(); }
    synchronized void checkNow(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        if (!scheduler.isStarted()) {
            scheduler.start(() -> checkSafely(false), TimeUnit.SECONDS.toMillis(
                    SamsungDiscountOfferSource.getCheckIntervalSeconds(appContext)), () -> checkSafely(true));
        } else scheduler.request(0, () -> checkSafely(true));
    }
    synchronized void clearState(Context context, long interestId) {
        SharedPreferences preferences = context.getSharedPreferences(
                "samsung_discount_offer_monitor", Context.MODE_PRIVATE);
        String prefix = "price_" + interestId + '_';
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.apply();
    }
    synchronized void rescheduleIfRunning(Context context) { if (scheduler.isStarted()) { stop(); start(context); } }

    private void checkSafely(boolean force) {
        Context context = appContext;
        if (context == null || !MonitorRunPolicy.canRun(context)
                || !StoreSourceControl.isEnabled(context, R.string.samsung_discount_offer_source_title)
                || !SamsungDiscountOfferSource.isConfigured(context)) return;
        StoreSourceCheckStatus.begin(context, R.string.samsung_discount_offer_source_title);
        try {
            List<ExternalProductDeal> deals = SamsungDiscountOfferClient.fetchOffers(context);
            if (deals.isEmpty()) throw new IllegalStateException("No Samsung Discount offers");
            SamsungDiscountOfferSource.markSuccessfulCheck(context);
            SharedPreferences prefs = context.getSharedPreferences("samsung_discount_offer_monitor", Context.MODE_PRIVATE);
            OfferRepository offers = new OfferRepository(context);
            OfferInvalidationRepository invalidations = new OfferInvalidationRepository(context);
            boolean found = false;
            for (Interest interest : new InterestRepository(context).getAll()) {
                if (!interest.isPrice()) continue;
                for (ExternalProductDeal deal : deals) {
                    if (!OfferTextParser.matchesInterest(deal.getTitle(), interest.getTerm())
                            || !OfferTextParser.isPlausiblePriceForInterest(deal.getPrice(), interest.getTerm())
                            || deal.getPrice() > interest.getMaximumPrice()) continue;
                    String key = "price_" + interest.getId() + '_' + deal.getId();
                    long value = Double.doubleToRawLongBits(deal.getPrice());
                    boolean unchanged = prefs.contains(key) && prefs.getLong(key, 0L) == value;
                    prefs.edit().putLong(key, value).apply();
                    ObservedOffer offer = new ObservedOffer("samsung_discount|" + interest.getId() + '|' + deal.getId(),
                            interest.getId(), interest.getTerm(), "Samsung Desconto", deal.getPrice(),
                            interest.getMaximumPrice(), System.currentTimeMillis(), deal.getLink(), "",
                            deal.getTitle());
                    if (invalidations.isInvalidated(offer)) continue;
                    if (unchanged) {
                        offers.refreshStoreProduct(offer);
                        continue;
                    }
                    offers.add(offer);
                    found = true;
                }
            }
            if (found) context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                    .setPackage(context.getPackageName()));
        } catch (Exception ignored) {
            SamsungDiscountOfferSource.markFailedCheck(context);
        } finally {
            StoreSourceCheckStatus.finish(context, R.string.samsung_discount_offer_source_title);
            context.sendBroadcast(new Intent(ACTION_STATUS_CHANGED).setPackage(context.getPackageName()));
        }
    }
}
