package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

final class MeliuzCashbackSettings {
    static final String PREFS = "meliuz_cashback_settings";

    static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context context) { return preferences(context).getBoolean("enabled", false); }

    static boolean isPaused(Context context) { return preferences(context).getBoolean("paused", false); }

    static boolean canConsult(Context context) { return isEnabled(context) && !isPaused(context); }

    static void saveSchedule(Context context, int intervalSeconds, boolean paused) {
        if (intervalSeconds < 30 || intervalSeconds > 86400) throw new IllegalArgumentException();
        preferences(context).edit().putInt("interval_seconds", intervalSeconds)
                .putBoolean("paused", paused).apply();
        SettingsBackup.changed(context);
        MeliuzCashbackMonitor.getInstance().stop();
        MonitorServiceController.update(context);
        MeliuzCashbackMonitor.broadcast(context);
    }

    static double minimum(Context context) {
        try {
            double value = Double.parseDouble(preferences(context).getString("minimum", "10"));
            return Double.isFinite(value) && value >= 0 && value <= 100 ? value : 10;
        } catch (RuntimeException invalid) { return 10; }
    }

    static int intervalSeconds(Context context) {
        return Math.max(30, Math.min(86400, preferences(context).getInt("interval_seconds", 900)));
    }

    static String signature(Context context) {
        return isEnabled(context) + ":" + isPaused(context) + ":" + minimum(context) + ":" + intervalSeconds(context);
    }

    static void save(Context context, boolean enabled, double minimum, int intervalSeconds) {
        if (!Double.isFinite(minimum) || minimum < 0 || minimum > 100
                || intervalSeconds < 30 || intervalSeconds > 86400) throw new IllegalArgumentException();
        preferences(context).edit().putBoolean("enabled", enabled)
                .putString("minimum", Double.toString(minimum))
                .putInt("interval_seconds", intervalSeconds).apply();
        SettingsBackup.changed(context);
        MeliuzCashbackMonitor.getInstance().stop();
        MonitorServiceController.update(context);
        MeliuzCashbackMonitor.broadcast(context);
    }
}
