package br.com.droidboaoferta;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.LinkedHashSet;
import java.util.Set;

/** Current store checks plus persisted durations for individual and complete cycles. */
final class StoreSourceCheckStatus {
    static final String ACTION_CHANGED = "br.com.droidboaoferta.STORE_SOURCE_CHECK_STATUS_CHANGED";
    static final String EXTRA_SOURCE_TITLE = "source_title";
    static final String EXTRA_CHECKING = "checking";
    private static final Set<Integer> RUNNING_SOURCE_TITLES = new LinkedHashSet<>();
    private static final String PREFS = "store_source_check_status";
    private static final String KEY_LAST_BATCH_COMPLETED_AT = "last_batch_completed_at";
    private static final String KEY_LAST_BATCH_DURATION = "last_batch_duration";
    private static final long AUTOMATIC_BATCH_START_WINDOW_MS = 5_000L;
    private static final Object BATCH_LOCK = new Object();
    private static final Set<Integer> AUTOMATIC_BATCH_EXPECTED = new LinkedHashSet<>();
    private static final Set<Integer> AUTOMATIC_BATCH_STARTED = new LinkedHashSet<>();
    private static final Set<Integer> AUTOMATIC_BATCH_FINISHED = new LinkedHashSet<>();
    private static long automaticBatchStartedAt;
    private static boolean automaticBatchQualified;
    private static boolean manualBatchActive;
    private static long manualBatchStartedAt;

    private StoreSourceCheckStatus() { }

    static void begin(Context context, int sourceTitleResource) {
        trackAutomaticBatchStart(context, sourceTitleResource);
        synchronized (RUNNING_SOURCE_TITLES) {
            RUNNING_SOURCE_TITLES.remove(sourceTitleResource);
            RUNNING_SOURCE_TITLES.add(sourceTitleResource);
        }
        prefs(context).edit().putLong(startKey(sourceTitleResource), SystemClock.elapsedRealtime()).apply();
        notifyChanged(context, sourceTitleResource, true);
    }

    static void finish(Context context, int sourceTitleResource) {
        synchronized (RUNNING_SOURCE_TITLES) {
            RUNNING_SOURCE_TITLES.remove(sourceTitleResource);
        }
        long startedAt = prefs(context).getLong(startKey(sourceTitleResource), 0L);
        if (startedAt > 0L) {
            prefs(context).edit()
                    .putLong(durationKey(sourceTitleResource),
                            Math.max(0L, SystemClock.elapsedRealtime() - startedAt))
                    .remove(startKey(sourceTitleResource))
                    .apply();
        }
        trackAutomaticBatchFinish(context, sourceTitleResource);
        notifyChanged(context, sourceTitleResource, false);
    }

    static void beginManualBatch() {
        synchronized (BATCH_LOCK) {
            manualBatchActive = true;
            manualBatchStartedAt = SystemClock.elapsedRealtime();
            clearAutomaticBatchLocked();
        }
    }

    static void finishManualBatch(Context context) {
        synchronized (BATCH_LOCK) {
            if (!manualBatchActive) return;
            saveBatchResult(context, manualBatchStartedAt);
            manualBatchActive = false;
            manualBatchStartedAt = 0L;
        }
    }

    static long getLastBatchCompletedAt(Context context) {
        return prefs(context).getLong(KEY_LAST_BATCH_COMPLETED_AT, 0L);
    }

    static long getLastBatchDurationMillis(Context context) {
        return prefs(context).getLong(KEY_LAST_BATCH_DURATION, 0L);
    }

    static long getLastDurationMillis(Context context, int sourceTitleResource) {
        return prefs(context).getLong(durationKey(sourceTitleResource), 0L);
    }

    static int getCurrentSourceTitleResource() {
        synchronized (RUNNING_SOURCE_TITLES) {
            for (Integer title : RUNNING_SOURCE_TITLES) return title;
            return 0;
        }
    }

    static boolean isCurrent(int sourceTitleResource) {
        return getCurrentSourceTitleResource() == sourceTitleResource;
    }

    static boolean isChecking(int sourceTitleResource) {
        synchronized (RUNNING_SOURCE_TITLES) {
            return RUNNING_SOURCE_TITLES.contains(sourceTitleResource);
        }
    }

    private static void trackAutomaticBatchStart(Context context, int sourceTitleResource) {
        synchronized (BATCH_LOCK) {
            if (manualBatchActive) return;
            long now = SystemClock.elapsedRealtime();
            if (AUTOMATIC_BATCH_EXPECTED.isEmpty()
                    || (!automaticBatchQualified
                    && now - automaticBatchStartedAt > AUTOMATIC_BATCH_START_WINDOW_MS)) {
                clearAutomaticBatchLocked();
                AUTOMATIC_BATCH_EXPECTED.addAll(getConfiguredSourceTitles(context));
                automaticBatchStartedAt = now;
            }
            if (!AUTOMATIC_BATCH_EXPECTED.contains(sourceTitleResource)) return;
            AUTOMATIC_BATCH_STARTED.add(sourceTitleResource);
            automaticBatchQualified = !AUTOMATIC_BATCH_EXPECTED.isEmpty()
                    && AUTOMATIC_BATCH_STARTED.containsAll(AUTOMATIC_BATCH_EXPECTED)
                    && now - automaticBatchStartedAt <= AUTOMATIC_BATCH_START_WINDOW_MS;
        }
    }

    private static void trackAutomaticBatchFinish(Context context, int sourceTitleResource) {
        synchronized (BATCH_LOCK) {
            if (manualBatchActive || !AUTOMATIC_BATCH_EXPECTED.contains(sourceTitleResource)) return;
            AUTOMATIC_BATCH_FINISHED.add(sourceTitleResource);
            if (automaticBatchQualified
                    && AUTOMATIC_BATCH_FINISHED.containsAll(AUTOMATIC_BATCH_EXPECTED)) {
                saveBatchResult(context, automaticBatchStartedAt);
                clearAutomaticBatchLocked();
            }
        }
    }

    private static Set<Integer> getConfiguredSourceTitles(Context context) {
        Set<Integer> titles = new LinkedHashSet<>();
        if (VivoOutletSource.isConfigured(context)) titles.add(R.string.vivo_outlet_source_title);
        if (VivoMadrugadaSource.isConfigured(context)) titles.add(R.string.vivo_madrugada_source_title);
        if (PelandoSource.isConfigured(context)) titles.add(R.string.pelando_source_title);
        if (PromobitSource.isConfigured(context)) titles.add(R.string.promobit_source_title);
        if (KabumOfferSource.isConfigured(context)) titles.add(R.string.kabum_offer_source_title);
        if (MotorolaOfferSource.isConfigured(context)) titles.add(R.string.motorola_offer_source_title);
        if (SamsungOfferSource.isConfigured(context)) titles.add(R.string.samsung_offer_source_title);
        if (SamsungDiscountOfferSource.isConfigured(context)) {
            titles.add(R.string.samsung_discount_offer_source_title);
        }
        return titles;
    }

    private static void saveBatchResult(Context context, long startedAt) {
        if (startedAt <= 0L) return;
        prefs(context).edit()
                .putLong(KEY_LAST_BATCH_COMPLETED_AT, System.currentTimeMillis())
                .putLong(KEY_LAST_BATCH_DURATION,
                        Math.max(0L, SystemClock.elapsedRealtime() - startedAt))
                .apply();
    }

    private static void clearAutomaticBatchLocked() {
        AUTOMATIC_BATCH_EXPECTED.clear();
        AUTOMATIC_BATCH_STARTED.clear();
        AUTOMATIC_BATCH_FINISHED.clear();
        automaticBatchStartedAt = 0L;
        automaticBatchQualified = false;
    }

    private static void notifyChanged(Context context, int sourceTitleResource, boolean checking) {
        context.sendBroadcast(new Intent(ACTION_CHANGED)
                .putExtra(EXTRA_SOURCE_TITLE, sourceTitleResource)
                .putExtra(EXTRA_CHECKING, checking)
                .setPackage(context.getPackageName()));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String startKey(int sourceTitleResource) {
        return sourceTitleResource + ":started_at";
    }

    private static String durationKey(int sourceTitleResource) {
        return sourceTitleResource + ":last_duration";
    }
}
