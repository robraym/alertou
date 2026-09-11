package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

/** Official Vivo overnight campaign. */
final class VivoMadrugadaSource {
    static final String DEFAULT_URL = "https://store.vivo.com.br/oferta-da-madrugada/c";
    static final String URL = DEFAULT_URL;
    private static final String PREFS = "external_offer_sources";
    private static final String KEY_URL = "vivo_madrugada_url";
    private static final String LAST_SUCCESS = "vivo_madrugada_last_success";
    private static final String LAST_FAILURE = "vivo_madrugada_last_failure";
    private static final String KEY_CHECK_INTERVAL_MINUTES = "vivo_madrugada_check_interval_minutes";
    private static final String KEY_CHECK_INTERVAL_SECONDS = "vivo_madrugada_check_interval_seconds";
    private static final int DEFAULT_CHECK_INTERVAL_MINUTES = 15;
    private static final int DEFAULT_CHECK_INTERVAL_SECONDS = DEFAULT_CHECK_INTERVAL_MINUTES * 60;

    private VivoMadrugadaSource() { }

    static String getUrl(Context context) {
        return preferences(context).getString(KEY_URL, DEFAULT_URL);
    }

    static String normalizeUrl(String rawUrl) {
        return VivoOutletSource.normalizeUrl(rawUrl);
    }

    static void save(Context context, String rawUrl) {
        String normalized = normalizeUrl(rawUrl);
        if (normalized == null) {
            throw new IllegalArgumentException("Link da Madrugada Vivo inválido.");
        }
        preferences(context).edit()
                .putString(KEY_URL, normalized)
                .remove(LAST_SUCCESS)
                .remove(LAST_FAILURE)
                .apply();
        SettingsBackup.changed(context);
    }

    static boolean isConfigured(Context context) {
        return normalizeUrl(getUrl(context)) != null;
    }

    static void markSuccessfulCheck(Context context) {
        preferences(context).edit().putLong(LAST_SUCCESS, System.currentTimeMillis()).apply();
    }

    static void markFailedCheck(Context context) {
        preferences(context).edit().putLong(LAST_FAILURE, System.currentTimeMillis()).apply();
    }

    static int getCheckIntervalMinutes(Context context) {
        return getCheckIntervalSeconds(context) / 60;
    }

    static void saveCheckIntervalMinutes(Context context, int interval) {
        if (!isSupportedCheckInterval(interval)) {
            throw new IllegalArgumentException("Intervalo de consulta da Madrugada Vivo inválido.");
        }
        preferences(context).edit().putInt(KEY_CHECK_INTERVAL_MINUTES, interval).apply();
        SettingsBackup.changed(context);
    }

    static int getCheckIntervalSeconds(Context context) {
        SharedPreferences preferences = preferences(context);
        int seconds = preferences.getInt(KEY_CHECK_INTERVAL_SECONDS, 0);
        if (isSupportedCheckIntervalSeconds(seconds)) return seconds;
        int legacyMinutes = preferences.getInt(KEY_CHECK_INTERVAL_MINUTES,
                DEFAULT_CHECK_INTERVAL_MINUTES);
        int migratedSeconds = legacyMinutes * 60;
        return isSupportedCheckIntervalSeconds(migratedSeconds)
                ? migratedSeconds : DEFAULT_CHECK_INTERVAL_SECONDS;
    }

    static void saveCheckIntervalSeconds(Context context, int seconds) {
        if (!isSupportedCheckIntervalSeconds(seconds)) {
            throw new IllegalArgumentException("Intervalo de consulta da Madrugada Vivo inválido.");
        }
        preferences(context).edit().putInt(KEY_CHECK_INTERVAL_SECONDS, seconds).apply();
        SettingsBackup.changed(context);
    }

    static boolean isSupportedCheckInterval(int interval) {
        return interval == 5 || interval == 15 || interval == 30 || interval == 60
                || interval == 360 || interval == 720 || interval == 1440;
    }

    static boolean isSupportedCheckIntervalSeconds(int seconds) {
        return seconds == 30 || seconds == 60 || seconds == 120 || seconds == 300
                || seconds == 900 || seconds == 1800 || seconds == 3600
                || seconds == 21600 || seconds == 43200 || seconds == 86400;
    }

    static boolean hasLastCheckFailed(Context context) {
        SharedPreferences preferences = preferences(context);
        return preferences.getLong(LAST_FAILURE, 0L) > preferences.getLong(LAST_SUCCESS, 0L);
    }

    static boolean hasSuccessfulCheck(Context context) {
        return preferences(context).getLong(LAST_SUCCESS, 0L) > 0L;
    }

    static long getLastSuccessfulCheckAt(Context context) {
        return preferences(context).getLong(LAST_SUCCESS, 0L);
    }

    static long getLastFailedCheckAt(Context context) {
        return preferences(context).getLong(LAST_FAILURE, 0L);
    }

    static long getLastCheckAt(Context context) {
        SharedPreferences preferences = preferences(context);
        return Math.max(preferences.getLong(LAST_SUCCESS, 0L),
                preferences.getLong(LAST_FAILURE, 0L));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
