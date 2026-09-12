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

final class CouponPageMonitor {
    private static final String PREFS = "coupon_page_monitor";
    private static final String LAST_AMOUNT_PREFIX = "last_amount_";
    private static final String LAST_CODE_PREFIX = "last_code_";
    private static final long CHECK_INTERVAL_MINUTES = 5L;
    private static final CouponPageMonitor INSTANCE = new CouponPageMonitor();

    private Context appContext;
    private final CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();

    private CouponPageMonitor() {
    }

    static CouponPageMonitor getInstance() {
        return INSTANCE;
    }

    synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        scheduler.start(this::checkAllSafely, TimeUnit.MINUTES.toMillis(CHECK_INTERVAL_MINUTES));
    }

    synchronized void stop() { scheduler.stop(); }

    synchronized void checkNow(Context context) {
        boolean started = scheduler.isStarted();
        start(context);
        if (started && MonitorRunPolicy.canRun(context)) scheduler.request(0);
    }

    synchronized void checkInterestNow(Context context, long interestId) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        if (!scheduler.isStarted()) {
            scheduler.start(this::checkAllSafely,
                    TimeUnit.MINUTES.toMillis(CHECK_INTERVAL_MINUTES),
                    () -> checkInterestSafely(interestId));
            return;
        }
        scheduler.request(0, () -> checkInterestSafely(interestId));
    }

    synchronized void checkIfStale(Context context) {
        boolean started = scheduler.isStarted();
        start(context);
        if (started && MonitorRunPolicy.canRun(context)) scheduler.request(TimeUnit.MINUTES.toMillis(2));
    }

    synchronized void rescheduleIfRunning(Context context) {
        if (!scheduler.isStarted()) return;
        stop();
        start(context);
    }

    void clearState(Context context, long interestId) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(LAST_AMOUNT_PREFIX + interestId)
                .remove(LAST_CODE_PREFIX + interestId)
                .apply();
    }

    private void checkAllSafely() {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context)) {
            return;
        }
        List<Interest> interests = new InterestRepository(context).getAll();
        for (Interest interest : interests) {
            if (!interest.isCoupon()) {
                continue;
            }
            if (!MonitorRunPolicy.isCurrent(context, interest)) continue;
            SourceCheckStatus.begin(context, interest.getId());
            try {
                checkInterest(context, interest);
            } catch (Exception error) {
                if (MonitorRunPolicy.canRun(context)) SourceCheckStatus.failed(context, interest.getId(), error);
            } finally {
                if (MonitorRunPolicy.isCurrent(context, interest)) SourceCheckStatus.finish(context, interest.getId(),
                        TimeUnit.MINUTES.toMillis(CHECK_INTERVAL_MINUTES));
            }
        }
    }

    private void checkInterestSafely(long interestId) {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context)) return;
        for (Interest interest : new InterestRepository(context).getAll()) {
            if (interest.getId() != interestId || !interest.isCoupon()) continue;
            if (!MonitorRunPolicy.isCurrent(context, interest)) return;
            SourceCheckStatus.begin(context, interest.getId());
            try {
                checkInterest(context, interest);
            } catch (Exception error) {
                if (MonitorRunPolicy.canRun(context)) {
                    SourceCheckStatus.failed(context, interest.getId(), error);
                }
            } finally {
                if (MonitorRunPolicy.isCurrent(context, interest)) {
                    SourceCheckStatus.finish(context, interest.getId(),
                            TimeUnit.MINUTES.toMillis(CHECK_INTERVAL_MINUTES));
                }
            }
            break;
        }
    }

    private void checkInterest(Context context, Interest interest) throws Exception {
        CouponPageCoupon highest = CouponPageClient.fetchHighest(interest.getTerm());
        if (!MonitorRunPolicy.isCurrent(context, interest) || highest == null
                || !highest.hasMonetaryValue()) {
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String amountKey = LAST_AMOUNT_PREFIX + interest.getId();
        String codeKey = LAST_CODE_PREFIX + interest.getId();
        boolean hadPrevious = preferences.contains(amountKey);
        double previousAmount = Double.longBitsToDouble(
                preferences.getLong(amountKey, Double.doubleToRawLongBits(Double.NaN)));
        String previousCode = preferences.getString(codeKey, "");

        preferences.edit()
                .putLong(amountKey, Double.doubleToRawLongBits(highest.getValue()))
                .putString(codeKey, highest.getCode())
                .apply();

        boolean reachedMinimum = highest.getValue() >= interest.getMaximumPrice();
        boolean becameBetter = !hadPrevious
                || highest.getValue() > previousAmount
                || (Double.compare(highest.getValue(), previousAmount) == 0
                && !highest.getCode().equals(previousCode));
        if (!reachedMinimum || !becameBetter) {
            return;
        }

        long observedAt = System.currentTimeMillis();
        String canonicalUrl = CouponPageClient.normalizeSupportedUrl(interest.getTerm());
        String brand = context.getString(CouponPageClient.isSamsung(canonicalUrl)
                ? R.string.coupon_brand_samsung : R.string.coupon_brand_motorola);
        String offerId = "coupon|" + interest.getId() + "|" + highest.getCode()
                + "|" + Double.doubleToLongBits(highest.getValue());
        ObservedOffer offer = new ObservedOffer(
                offerId,
                interest.getId(),
                context.getString(R.string.coupon_offer_title, brand),
                context.getString(R.string.coupon_source, brand, highest.getCode()),
                highest.getValue(),
                interest.getMaximumPrice(),
                observedAt,
                canonicalUrl == null ? interest.getTerm() : canonicalUrl,
                ""
        );
        new OfferRepository(context).add(offer);
        showNotification(context, interest, highest, offer.getLink(), brand);
        context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                .setPackage(context.getPackageName()));
    }

    private void showNotification(Context context, Interest interest, CouponPageCoupon coupon,
                                  String pageUrl, String brand) {
        if (!MonitorRunPolicy.isCurrent(context, interest)) return;
        Intent openPage = new Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl));
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                Long.hashCode(interest.getId()),
                openPage,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        String title = context.getString(
                R.string.coupon_notification_title,
                brand,
                currency.format(coupon.getValue())
        );
        String explanation = context.getString(
                R.string.coupon_notification_explanation,
                coupon.getCode(), brand
        );
        AlertSoundController.configureNotificationChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context,
                AlertSoundController.getChannelId(context)
        )
                .setSmallIcon(R.drawable.ic_notification_offer)
                .setContentTitle(title)
                .setContentText(explanation)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(explanation))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(AlertSoundController.getSoundUri(context))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        manager.notify(Long.hashCode(interest.getId()), builder.build());
        AlertSoundController.playSelectedSound(context);
    }
}
