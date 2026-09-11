package br.com.droidboaoferta;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.LinkedHashSet;
import java.util.Set;

/** Runtime-only status for the store currently being consulted. */
final class StoreSourceCheckStatus {
    static final String ACTION_CHANGED = "br.com.droidboaoferta.STORE_SOURCE_CHECK_STATUS_CHANGED";
    private static final Set<Integer> RUNNING_SOURCE_TITLES = new LinkedHashSet<>();
    private static final String PREFS = "store_source_check_status";

    private StoreSourceCheckStatus() { }

    static void begin(Context context, int sourceTitleResource) {
        synchronized (RUNNING_SOURCE_TITLES) {
            RUNNING_SOURCE_TITLES.remove(sourceTitleResource);
            RUNNING_SOURCE_TITLES.add(sourceTitleResource);
        }
        prefs(context).edit().putLong(startKey(sourceTitleResource), SystemClock.elapsedRealtime()).apply();
        notifyChanged(context);
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
        notifyChanged(context);
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

    private static void notifyChanged(Context context) {
        context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
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
