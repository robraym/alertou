package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.util.Locale;

/** Official Samsung Brazil offers catalog. */
final class SamsungOfferSource {
    static final String DEFAULT_URL = "https://www.samsung.com/br/offer/";
    private static final String PREFS = "external_offer_sources";
    private static final String KEY_URL = "samsung_offer_url";
    private static final String KEY_TITLE = "samsung_offer_title";
    private static final String KEY_LAST_SUCCESS = "samsung_offer_last_success";
    private static final String KEY_LAST_FAILURE = "samsung_offer_last_failure";
    private static final String KEY_CHECK_INTERVAL_MINUTES = "samsung_offer_check_interval_minutes";
    private static final String KEY_CHECK_INTERVAL_SECONDS = "samsung_offer_check_interval_seconds";
    static final int DEFAULT_CHECK_INTERVAL_MINUTES = 15;
    static final int DEFAULT_CHECK_INTERVAL_SECONDS = DEFAULT_CHECK_INTERVAL_MINUTES * 60;

    private SamsungOfferSource() {
    }

    static String getUrl(Context context) {
        return preferences(context).getString(KEY_URL, DEFAULT_URL);
    }

    static String getTitle(Context context) {
        return preferences(context).getString(KEY_TITLE, "Samsung Ofertas");
    }

    static void saveTitle(Context context, String title) {
        String value = title == null ? "" : title.trim().replaceAll("\\s+", " ");
        preferences(context).edit().putString(KEY_TITLE,
                value.isEmpty() ? "Samsung Ofertas" : value.substring(0, Math.min(40, value.length()))).apply();
        SettingsBackup.changed(context);
    }

    static boolean isConfigured(Context context) {
        return normalizeUrl(getUrl(context)) != null;
    }

    static void save(Context context, String rawUrl) {
        String normalized = normalizeUrl(rawUrl);
        if (normalized == null) throw new IllegalArgumentException("Unsupported Samsung URL");
        preferences(context).edit().putString(KEY_URL, normalized).apply();
        SettingsBackup.changed(context);
    }

    static String normalizeUrl(String rawUrl) {
        if (rawUrl == null) return null;
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            return "https".equals(scheme) && "www.samsung.com".equals(host)
                    && "/br/offer".equals(path) ? DEFAULT_URL : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static void markSuccessfulCheck(Context context) {
        preferences(context).edit().putLong(KEY_LAST_SUCCESS, System.currentTimeMillis()).apply();
    }

    static void markFailedCheck(Context context) {
        preferences(context).edit().putLong(KEY_LAST_FAILURE, System.currentTimeMillis()).apply();
    }

    static boolean hasLastCheckFailed(Context context) {
        SharedPreferences preferences = preferences(context);
        return preferences.getLong(KEY_LAST_FAILURE, 0L)
                > preferences.getLong(KEY_LAST_SUCCESS, 0L);
    }

    static boolean hasSuccessfulCheck(Context context) {
        return preferences(context).getLong(KEY_LAST_SUCCESS, 0L) > 0L;
    }

    static long getLastSuccessfulCheckAt(Context context) {
        return preferences(context).getLong(KEY_LAST_SUCCESS, 0L);
    }

    static long getLastFailedCheckAt(Context context) {
        return preferences(context).getLong(KEY_LAST_FAILURE, 0L);
    }

    static int getCheckIntervalMinutes(Context context) {
        return getCheckIntervalSeconds(context) / 60;
    }

    static void saveCheckIntervalMinutes(Context context, int minutes) {
        if (!isSupportedCheckInterval(minutes)) {
            throw new IllegalArgumentException("Unsupported Samsung check interval");
        }
        preferences(context).edit().putInt(KEY_CHECK_INTERVAL_MINUTES, minutes).apply();
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
            throw new IllegalArgumentException("Unsupported Samsung check interval");
        }
        preferences(context).edit().putInt(KEY_CHECK_INTERVAL_SECONDS, seconds).apply();
        SettingsBackup.changed(context);
    }

    static boolean isSupportedCheckInterval(int minutes) {
        return minutes == 5 || minutes == 15 || minutes == 30 || minutes == 60
                || minutes == 360 || minutes == 720 || minutes == 1440;
    }

    static boolean isSupportedCheckIntervalSeconds(int seconds) {
        return seconds == 30 || seconds == 60 || seconds == 120 || seconds == 300
                || seconds == 900 || seconds == 1800 || seconds == 3600
                || seconds == 21600 || seconds == 43200 || seconds == 86400;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
