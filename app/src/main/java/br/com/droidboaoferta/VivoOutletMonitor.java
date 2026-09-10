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

final class VivoOutletMonitor {
    static final String ACTION_STATUS_CHANGED =
            "br.com.droidboaoferta.VIVO_OUTLET_STATUS_CHANGED";
    private static final String PREFS = "vivo_outlet_monitor";
    private static final String LAST_PRICE_PREFIX = "last_price_";
    private static final VivoOutletMonitor INSTANCE = new VivoOutletMonitor();

    private Context appContext;
    private final CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();

    private VivoOutletMonitor() {
    }

    static VivoOutletMonitor getInstance() {
        return INSTANCE;
    }

    synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        scheduler.start(this::checkAllSafely, TimeUnit.MINUTES.toMillis(
                getShortestCheckIntervalMinutes(appContext)));
    }

    synchronized void stop() { scheduler.stop(); }

    synchronized void checkNow(Context context) {
        boolean started = scheduler.isStarted();
        start(context);
        if (started && MonitorRunPolicy.canRun(context)) {
            scheduler.request(0, () -> checkSafely(0L, true));
        }
    }

    synchronized void checkInterestNow(Context context, long interestId) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        if (!scheduler.isStarted()) {
            scheduler.start(this::checkAllSafely, TimeUnit.MINUTES.toMillis(
                    getShortestCheckIntervalMinutes(appContext)),
                    () -> checkInterestSafely(interestId));
            return;
        }
        scheduler.request(0, () -> checkInterestSafely(interestId));
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

    private void checkAllSafely() {
        checkSafely(0L, false);
    }

    private void checkInterestSafely(long interestId) {
        checkSafely(interestId, true);
    }

    private void checkSafely(long interestId, boolean force) {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context)) {
            return;
        }
        boolean found = false;
        if (VivoOutletSource.isConfigured(context)
                && (force || isDue(VivoOutletSource.getLastCheckAt(context),
                VivoOutletSource.getCheckIntervalMinutes(context)))) {
            found |= checkSource(context, VivoOutletSource.getUrl(context), "vivo_outlet_",
                    "vivo|", R.string.vivo_outlet_offer_source, true, interestId);
        }
        if (force || isDue(VivoMadrugadaSource.getLastCheckAt(context),
                VivoMadrugadaSource.getCheckIntervalMinutes(context))) {
            found |= checkSource(context, VivoMadrugadaSource.getUrl(context), "vivo_madrugada_",
                    "vivo_madrugada|", R.string.vivo_madrugada_offer_source, false, interestId);
        }
        if (found) {
            context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                    .setPackage(context.getPackageName()));
        }
        context.sendBroadcast(new Intent(ACTION_STATUS_CHANGED)
                .setPackage(context.getPackageName()));
    }

    private int getShortestCheckIntervalMinutes(Context context) {
        return Math.min(VivoOutletSource.getCheckIntervalMinutes(context),
                VivoMadrugadaSource.getCheckIntervalMinutes(context));
    }

    private boolean isDue(long lastCheck, int intervalMinutes) {
        return lastCheck <= 0L || System.currentTimeMillis() - lastCheck
                >= TimeUnit.MINUTES.toMillis(intervalMinutes);
    }

    private boolean checkSource(Context context, String sourceUrl, String preferencePrefix,
                                String offerPrefix, int sourceResource, boolean outlet,
                                long interestId) {
        try {
            List<VivoOutletProduct> products = VivoOutletClient.fetchProducts(sourceUrl);
            if (products.isEmpty()) {
                throw new IllegalStateException("No Vivo products");
            }
            if (outlet) VivoOutletSource.markSuccessfulCheck(context);
            else VivoMadrugadaSource.markSuccessfulCheck(context);
            List<Interest> interests = new InterestRepository(context).getAll();
            OfferRepository repository = new OfferRepository(context);
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long observedAt = System.currentTimeMillis();
            boolean found = false;
            for (Interest interest : interests) {
                if (interestId != 0L && interest.getId() != interestId) continue;
                if (!MonitorRunPolicy.isCurrent(context, interest)) continue;
                if (!interest.isPrice()) {
                    continue;
                }
                for (VivoOutletProduct product : products) {
                    if (!OfferTextParser.matchesInterest(product.getName(), interest.getTerm())
                            || !OfferTextParser.isPlausiblePriceForInterest(
                                    product.getPixPrice(), interest.getTerm())
                            || product.getPixPrice() > interest.getMaximumPrice()) {
                        continue;
                    }
                    if (!MonitorRunPolicy.isCurrent(context, interest)) return found;
                    String key = preferencePrefix + LAST_PRICE_PREFIX + interest.getId()
                            + "_" + product.getCode();
                    boolean known = preferences.contains(key);
                    double lastPrice = Double.longBitsToDouble(preferences.getLong(
                            key, Double.doubleToRawLongBits(Double.NaN)));
                    if (known && Double.compare(lastPrice, product.getPixPrice()) == 0) {
                        continue;
                    }
                    preferences.edit().putLong(key, Double.doubleToRawLongBits(product.getPixPrice()))
                            .apply();
                    ObservedOffer offer = new ObservedOffer(
                            offerPrefix + interest.getId() + "|" + product.getCode(),
                            interest.getId(),
                            interest.getTerm(),
                            context.getString(sourceResource),
                            product.getPixPrice(),
                            interest.getMaximumPrice(),
                            observedAt,
                            product.getLink(),
                            ""
                    );
                    repository.add(offer);
                    showNotification(context, offer);
                    found = true;
                }
            }
            return found;
        } catch (Exception ignored) {
            if (outlet) VivoOutletSource.markFailedCheck(context);
            else VivoMadrugadaSource.markFailedCheck(context);
            // Mantém a última leitura válida se a loja estiver indisponível temporariamente.
            return false;
        }
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
