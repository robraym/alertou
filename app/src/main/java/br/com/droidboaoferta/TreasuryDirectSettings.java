package br.com.droidboaoferta;

import android.content.Context;

/** User-selected frequency for the official Tesouro Direto rate feed. */
final class TreasuryDirectSettings {
    private static final String PREFS = "treasury_direct_settings";
    private static final String KEY_INTERVAL_SECONDS = "interval_seconds";
    static final int DEFAULT_INTERVAL_SECONDS = 300;

    private TreasuryDirectSettings() { }

    static int getCheckIntervalSeconds(Context context) {
        int value = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS);
        return Math.max(30, Math.min(86400, value));
    }

    static void saveCheckIntervalSeconds(Context context, int seconds) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_INTERVAL_SECONDS, Math.max(30, Math.min(86400, seconds))).apply();
        SettingsBackup.changed(context);
    }
}
