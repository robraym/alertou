package br.com.droidboaoferta;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class SamsungOfferMonitor {
    static final String ACTION_STATUS_CHANGED = "br.com.droidboaoferta.samsung_offer_STATUS_CHANGED";
    private static final String PREFS = "samsung_offer_monitor";
    private static final String LAST_PRICE_PREFIX = "last_price_";
    private static final String KEY_LAST_FEED_SIGNATURE = "last_feed_signature";
    private static final SamsungOfferMonitor INSTANCE = new SamsungOfferMonitor();

    private Context appContext;
    private final CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();

    private SamsungOfferMonitor() {
    }

    static SamsungOfferMonitor getInstance() {
        return INSTANCE;
    }

    synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        scheduler.start(() -> checkAllSafely(false), TimeUnit.SECONDS.toMillis(
                SamsungOfferSource.getCheckIntervalSeconds(appContext)));
    }

    synchronized void stop() {
        scheduler.stop();
    }

    synchronized void checkNow(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        if (!scheduler.isStarted()) {
            scheduler.start(() -> checkAllSafely(false), TimeUnit.SECONDS.toMillis(
                    SamsungOfferSource.getCheckIntervalSeconds(appContext)), () -> checkAllSafely(true));
            return;
        }
        scheduler.request(0, () -> checkAllSafely(true));
    }

    synchronized void checkInterestNow(Context context, long interestId) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        if (!scheduler.isStarted()) {
            scheduler.start(() -> checkAllSafely(false), TimeUnit.SECONDS.toMillis(
                    SamsungOfferSource.getCheckIntervalSeconds(appContext)),
                    () -> checkAllSafely(true, interestId));
            return;
        }
        scheduler.request(0, () -> checkAllSafely(true, interestId));
    }

    void clearState(Context context, long interestId) {
        String prefix = LAST_PRICE_PREFIX + interestId + "_";
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.apply();
    }

    synchronized void rescheduleIfRunning(Context context) {
        if (!scheduler.isStarted()) return;
        stop();
        start(context);
    }

    private void checkAllSafely(boolean force) {
        checkAllSafely(force, 0L);
    }

    private void checkAllSafely(boolean force, long interestId) {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context) || !SamsungOfferSource.isConfigured(context)) return;
        StoreSourceCheckStatus.begin(context, R.string.samsung_offer_source_title);
        try {
            List<ExternalProductDeal> deals = SamsungOfferClient.fetchOffers();
            if (deals.isEmpty()) throw new IllegalStateException("No Samsung offers");
            SamsungOfferSource.markSuccessfulCheck(context);
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String feedSignature = createFeedSignature(deals);
            if (!force && feedSignature.equals(preferences.getString(KEY_LAST_FEED_SIGNATURE, ""))) return;
            preferences.edit().putString(KEY_LAST_FEED_SIGNATURE, feedSignature).apply();
            List<Interest> interests = new InterestRepository(context).getAll();
            OfferRepository repository = new OfferRepository(context);
            long observedAt = System.currentTimeMillis();
            boolean found = false;
            for (ExternalProductDeal deal : deals) {
                for (Interest interest : interests) {
                    if (interestId != 0L && interest.getId() != interestId) continue;
                    if (!MonitorRunPolicy.isCurrent(context, interest)) return;
                    if (!interest.isPrice() || !OfferTextParser.matchesInterest(deal.getTitle(), interest.getTerm())
                            || !OfferTextParser.isPlausiblePriceForInterest(
                                    deal.getPrice(), interest.getTerm())
                            || deal.getPrice() > interest.getMaximumPrice()) continue;
                    String key = LAST_PRICE_PREFIX + interest.getId() + "_" + deal.getId();
                    boolean known = preferences.contains(key);
                    double lastPrice = Double.longBitsToDouble(preferences.getLong(key,
                            Double.doubleToRawLongBits(Double.NaN)));
                    preferences.edit().putLong(key, Double.doubleToRawLongBits(deal.getPrice())).apply();
                    if (known && Double.compare(lastPrice, deal.getPrice()) == 0) continue;
                    ObservedOffer offer = new ObservedOffer(
                            "samsung|" + interest.getId() + "|" + deal.getId(), interest.getId(),
                            interest.getTerm(), context.getString(R.string.samsung_offer_source),
                            deal.getPrice(), interest.getMaximumPrice(), observedAt, deal.getLink(), "");
                    repository.add(offer);
                    showNotification(context, offer);
                    found = true;
                }
            }
            if (found) context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                    .setPackage(context.getPackageName()));
        } catch (Exception ignored) {
            SamsungOfferSource.markFailedCheck(context);
        } finally {
            StoreSourceCheckStatus.finish(context, R.string.samsung_offer_source_title);
            context.sendBroadcast(new Intent(ACTION_STATUS_CHANGED).setPackage(context.getPackageName()));
        }
    }

    private String createFeedSignature(List<ExternalProductDeal> deals) {
        StringBuilder signature = new StringBuilder();
        int limit = Math.min(40, deals.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) signature.append('|');
            ExternalProductDeal deal = deals.get(index);
            signature.append(deal.getId()).append('@').append(deal.getPrice());
        }
        return signature.toString();
    }

    private void showNotification(Context context, ObservedOffer offer) {
        if (!MonitorRunPolicy.canRun(context)) return;
        Intent openPage = new Intent(Intent.ACTION_VIEW, Uri.parse(offer.getLink()));
        PendingIntent pendingIntent = PendingIntent.getActivity(context, offer.getId().hashCode(), openPage,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        String explanation = context.getString(R.string.offer_notification_explanation,
                currency.format(offer.getPrice()), currency.format(offer.getMaximumPrice()), offer.getSource());
        AlertSoundController.configureNotificationChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                AlertSoundController.getChannelId(context))
                .setSmallIcon(R.drawable.ic_notification_offer)
                .setContentTitle(offer.getInterest()).setContentText(explanation)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(explanation))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setSound(AlertSoundController.getSoundUri(context))
                .setAutoCancel(true).setContentIntent(pendingIntent);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(offer.getId().hashCode(), builder.build());
        AlertSoundController.playSelectedSound(context);
    }
}
