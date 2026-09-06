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

final class PelandoMonitor {
    static final String ACTION_STATUS_CHANGED =
            "br.com.droidboaoferta.PELANDO_STATUS_CHANGED";
    private static final String PREFS = "pelando_monitor";
    private static final String LAST_PRICE_PREFIX = "last_price_";
    private static final PelandoMonitor INSTANCE = new PelandoMonitor();

    private Context appContext;
    private final CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();

    private PelandoMonitor() {
    }

    static PelandoMonitor getInstance() {
        return INSTANCE;
    }

    synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        scheduler.start(() -> checkAllSafely(false), TimeUnit.SECONDS.toMillis(
                PelandoSource.getCheckIntervalSeconds(appContext)));
    }

    synchronized void stop() { scheduler.stop(); }

    synchronized void checkNow(Context context) {
        boolean started = scheduler.isStarted();
        start(context);
        if (started && MonitorRunPolicy.canRun(context)) scheduler.request(0, () -> checkAllSafely(true));
    }

    void clearState(Context context, long interestId) {
        String prefix = LAST_PRICE_PREFIX + interestId + "_";
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    synchronized void rescheduleIfRunning(Context context) {
        if (!scheduler.isStarted()) {
            return;
        }
        stop();
        start(context);
    }

    private void checkAllSafely(boolean force) {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context) || !PelandoSource.isConfigured(context)) {
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        FeedRetryState retry = new FeedRetryState(preferences);
        boolean retryDetails = retry.needsRetry();
        try {
            PelandoRecentClient.RecentResult result = PelandoRecentClient.fetchRecent(
                    PelandoSource.getUrl(context),
                    PelandoSource.getLastModified(context),
                    force || retryDetails
            );
            if (!MonitorRunPolicy.canRun(context)) return;
            if (!result.isChanged()) {
                PelandoSource.markSuccessfulCheck(context, result.getLastModified());
                return;
            }
            String feedSignature = createFeedSignature(result.getDeals());
            if (!retry.shouldProcess(feedSignature, force)) {
                PelandoSource.markSuccessfulCheck(context, result.getLastModified());
                return;
            }
            retry.begin();
            boolean allDetailsSucceeded = true;
            List<Interest> interests = new InterestRepository(context).getAll();
            OfferRepository repository = new OfferRepository(context);
            long observedAt = System.currentTimeMillis();
            boolean found = false;
            for (PelandoDeal feedDeal : result.getDeals()) {
                for (Interest interest : interests) {
                    if (!MonitorRunPolicy.isCurrent(context, interest)) return;
                    if (!interest.isPrice()
                            || !OfferTextParser.matchesInterest(feedDeal.getTitle(), interest.getTerm())) {
                        continue;
                    }
                    PelandoDeal deal;
                    try {
                        deal = PelandoRecentClient.fetchDealDetails(
                                feedDeal.getTitle(),
                                feedDeal.getLink()
                        );
                    } catch (Exception ignored) {
                        allDetailsSucceeded = false;
                        continue;
                    }
                    if (!MonitorRunPolicy.isCurrent(context, interest)) return;
                    String key = LAST_PRICE_PREFIX + interest.getId() + "_" + deal.getId();
                    boolean known = preferences.contains(key);
                    double lastPrice = Double.longBitsToDouble(preferences.getLong(
                            key,
                            Double.doubleToRawLongBits(Double.NaN)
                    ));
                    preferences.edit().putLong(key, Double.doubleToRawLongBits(deal.getPrice()))
                            .apply();
                    if (deal.getPrice() > interest.getMaximumPrice()
                            || (known && Double.compare(lastPrice, deal.getPrice()) == 0)) {
                        continue;
                    }
                    ObservedOffer offer = new ObservedOffer(
                            "pelando|" + interest.getId() + "|" + deal.getId(),
                            interest.getId(),
                            interest.getTerm(),
                            context.getString(R.string.pelando_offer_source),
                            deal.getPrice(),
                            interest.getMaximumPrice(),
                            observedAt,
                            deal.getLink(),
                            ""
                    );
                    repository.add(offer);
                    showNotification(context, offer);
                    found = true;
                }
            }
            if (allDetailsSucceeded) {
                retry.complete(feedSignature, true);
                PelandoSource.markSuccessfulCheck(context, result.getLastModified());
            } else {
                PelandoSource.markFailedCheck(context);
            }
            if (found) {
                context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                        .setPackage(context.getPackageName()));
            }
        } catch (Exception ignored) {
            PelandoSource.markFailedCheck(context);
        } finally {
            context.sendBroadcast(new Intent(ACTION_STATUS_CHANGED)
                    .setPackage(context.getPackageName()));
        }
    }

    private String createFeedSignature(List<PelandoDeal> deals) {
        StringBuilder signature = new StringBuilder();
        int limit = Math.min(40, deals.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                signature.append('|');
            }
            signature.append(deals.get(index).getId());
        }
        return signature.toString();
    }

    private void showNotification(Context context, ObservedOffer offer) {
        if (!MonitorRunPolicy.canRun(context)) return;
        Intent openPage = new Intent(Intent.ACTION_VIEW, Uri.parse(offer.getLink()));
        int notificationId = offer.getId().hashCode();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openPage,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        String explanation = context.getString(
                R.string.offer_notification_explanation,
                currency.format(offer.getPrice()),
                currency.format(offer.getMaximumPrice()),
                offer.getSource()
        );
        AlertSoundController.configureNotificationChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context,
                AlertSoundController.getChannelId(context)
        )
                .setSmallIcon(R.drawable.ic_notification_offer)
                .setContentTitle(offer.getInterest())
                .setContentText(explanation)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(explanation))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(AlertSoundController.getSoundUri(context))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE
        );
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
        AlertSoundController.playSelectedSound(context);
    }
}
