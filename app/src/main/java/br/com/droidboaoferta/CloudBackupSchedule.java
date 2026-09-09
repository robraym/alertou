package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

/** Local schedule for sending pending cloud backups. */
final class CloudBackupSchedule {
    static final int MANUAL = 0;
    static final int SIX_HOURS = 6 * 60;
    static final int TWELVE_HOURS = 12 * 60;
    static final int ONE_DAY = 24 * 60;
    static final int ONE_WEEK = 7 * ONE_DAY;

    private static final String PREFS = "cloud_backup_schedule";
    private static final String KEY_INTERVAL_MINUTES = "interval_minutes";
    private static final int DEFAULT_INTERVAL_MINUTES = SIX_HOURS;

    private CloudBackupSchedule() { }

    static int getIntervalMinutes(Context context) {
        int saved = preferences(context).getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES);
        return isSupported(saved) ? saved : DEFAULT_INTERVAL_MINUTES;
    }

    static void saveIntervalMinutes(Context context, int intervalMinutes) {
        if (!isSupported(intervalMinutes)) {
            throw new IllegalArgumentException("Intervalo de backup inválido.");
        }
        preferences(context).edit().putInt(KEY_INTERVAL_MINUTES, intervalMinutes).apply();
    }

    static boolean isAutomatic(Context context) {
        return getIntervalMinutes(context) > MANUAL;
    }

    static long getIntervalMillis(Context context) {
        return getIntervalMinutes(context) * 60_000L;
    }

    static boolean isSupported(int intervalMinutes) {
        return intervalMinutes == MANUAL || intervalMinutes == SIX_HOURS
                || intervalMinutes == TWELVE_HOURS || intervalMinutes == ONE_DAY
                || intervalMinutes == ONE_WEEK;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
