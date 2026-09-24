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

/** Independent general KaBuM catalogue monitor backed by the JSON search endpoint. */
final class KabumCatalogApiMonitor {
    static final String ACTION_STATUS_CHANGED = "br.com.droidboaoferta.KABUM_CATALOG_API_STATUS_CHANGED";
    private static final KabumCatalogApiMonitor INSTANCE = new KabumCatalogApiMonitor();
    private static final String PREFS = "kabum_catalog_api_monitor";
    private final CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();
    private Context appContext;

    static KabumCatalogApiMonitor getInstance() { return INSTANCE; }

    synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (MonitorRunPolicy.canRun(appContext)) {
            scheduler.start(() -> check(0L, false),
                    KabumCatalogApiSource.getCheckIntervalSeconds(appContext) * 1000L);
        }
    }

    synchronized void stop() { scheduler.stop(); }
    synchronized void checkNow(Context context) { checkInterestNow(context, 0L); }

    synchronized void checkInterestNow(Context context, long interestId) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        if (!scheduler.isStarted()) {
            scheduler.start(() -> check(0L, false),
                    KabumCatalogApiSource.getCheckIntervalSeconds(appContext) * 1000L,
                    () -> check(interestId, true));
        } else {
            scheduler.request(0L, () -> check(interestId, true));
        }
    }

    synchronized void rescheduleIfRunning(Context context) {
        if (scheduler.isStarted()) {
            stop();
            start(context);
        }
    }

    synchronized void clearState(Context context, long interestId) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        String prefix = interestId + "_";
        for (String key : preferences.getAll().keySet()) if (key.startsWith(prefix)) editor.remove(key);
        editor.apply();
    }

    private void check(long onlyInterestId, boolean force) {
        Context context = appContext;
        if (context == null
                || !StoreSourceControl.isEnabled(context, R.string.kabum_catalog_api_source_title)
                || !KabumCatalogApiSource.isConfigured(context)) return;
        if (!force && StoreSourceCheckStatus.isManualBatchActive()) return;
        StoreSourceCheckStatus.begin(context, R.string.kabum_catalog_api_source_title);
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        OfferRepository repository = new OfferRepository(context);
        OfferInvalidationRepository invalidations = new OfferInvalidationRepository(context);
        boolean succeeded = true;
        boolean found = false;
        try {
            for (Interest interest : new InterestRepository(context).getAll()) {
                if ((onlyInterestId != 0L && interest.getId() != onlyInterestId) || !interest.isPrice()) continue;
                List<ExternalProductDeal> deals = KabumCatalogApiClient.search(
                        KabumCatalogApiSource.getUrl(context), interest.getTerm());
                for (ExternalProductDeal deal : deals) {
                    if (!OfferTextParser.matchesInterest(deal.getTitle(), interest.getTerm())
                            || !OfferTextParser.isPlausiblePriceForInterest(deal.getPrice(), interest.getTerm())
                            || deal.getPrice() > interest.getMaximumPrice()) continue;
                    String key = interest.getId() + "_" + deal.getId();
                    long value = Double.doubleToRawLongBits(deal.getPrice());
                    boolean unchanged = preferences.contains(key) && preferences.getLong(key, 0L) == value;
                    preferences.edit().putLong(key, value).apply();
                    ObservedOffer offer = new ObservedOffer("kabum_catalog_api|" + key,
                            interest.getId(), interest.getTerm(),
                            StoreDisplayName.get(context, R.string.kabum_catalog_api_source_title),
                            deal.getPrice(), interest.getMaximumPrice(), System.currentTimeMillis(),
                            deal.getLink(), "", deal.getTitle());
                    if (invalidations.isInvalidated(offer)) continue;
                    if (unchanged) {
                        repository.refreshStoreProduct(offer);
                        continue;
                    }
                    repository.add(offer);
                    showNotification(context, offer);
                    found = true;
                }
            }
        } catch (Exception ignored) {
            succeeded = false;
        } finally {
            if (succeeded) KabumCatalogApiSource.markSuccessfulCheck(context);
            else KabumCatalogApiSource.markFailedCheck(context);
            StoreSourceCheckStatus.finish(context, R.string.kabum_catalog_api_source_title);
            if (found) context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                    .setPackage(context.getPackageName()));
            context.sendBroadcast(new Intent(ACTION_STATUS_CHANGED).setPackage(context.getPackageName()));
        }
    }

    private void showNotification(Context context, ObservedOffer offer) {
        if (!MonitorRunPolicy.canRun(context)) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(offer.getLink()));
        PendingIntent pending = PendingIntent.getActivity(context, offer.getId().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NumberFormat currency = CurrencyTextFormatter.displayFormatter();
        String text = context.getString(R.string.offer_notification_explanation,
                currency.format(offer.getPrice()), currency.format(offer.getMaximumPrice()), offer.getSource());
        AlertSoundController.configureNotificationChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                AlertSoundController.getChannelId(context))
                .setSmallIcon(R.drawable.ic_notification_offer)
                .setContentTitle(offer.getDisplayTitle())
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(AlertSoundController.getSoundUri(context))
                .setAutoCancel(true)
                .setContentIntent(pending);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(offer.getId().hashCode(), builder.build());
        AlertSoundController.playSelectedSound(context);
    }
}
