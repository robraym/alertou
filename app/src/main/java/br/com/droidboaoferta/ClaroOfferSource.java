package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.util.Locale;

/** Official Claro Brazil smartphone catalog. */
final class ClaroOfferSource {
    static final String DEFAULT_URL = "https://planoscelular.claro.com.br/claro/pt/c/celulares";
    private static final String PREFS = "external_offer_sources";
    private static final String KEY_URL = "claro_offer_url";
    private static final String KEY_TITLE = "claro_offer_title";
    private static final String KEY_LAST_SUCCESS = "claro_offer_last_success";
    private static final String KEY_LAST_FAILURE = "claro_offer_last_failure";
    private static final String KEY_CHECK_INTERVAL_SECONDS = "claro_offer_check_interval_seconds";
    static final int DEFAULT_CHECK_INTERVAL_SECONDS = 15 * 60;

    private ClaroOfferSource() {
    }

    static String getUrl(Context context) {
        return preferences(context).getString(KEY_URL, DEFAULT_URL);
    }

    static String getTitle(Context context) {
        return preferences(context).getString(KEY_TITLE, "Claro");
    }

    static void saveTitle(Context context, String title) {
        String value = title == null ? "" : title.trim().replaceAll("\\s+", " ");
        preferences(context).edit().putString(KEY_TITLE,
                value.isEmpty() ? "Claro" : value.substring(0, Math.min(40, value.length()))).apply();
        SettingsBackup.changed(context);
    }

    static boolean isConfigured(Context context) {
        return normalizeUrl(getUrl(context)) != null;
    }

    static void save(Context context, String rawUrl) {
        String normalized = normalizeUrl(rawUrl);
        if (normalized == null) throw new IllegalArgumentException("Unsupported Claro URL");
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
            return "https".equals(scheme) && "planoscelular.claro.com.br".equals(host)
                    && "/claro/pt/c/celulares".equals(path) ? DEFAULT_URL : null;
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

    static int getCheckIntervalSeconds(Context context) {
        int seconds = preferences(context).getInt(KEY_CHECK_INTERVAL_SECONDS,
                DEFAULT_CHECK_INTERVAL_SECONDS);
        return isSupportedCheckIntervalSeconds(seconds) ? seconds : DEFAULT_CHECK_INTERVAL_SECONDS;
    }

    static void saveCheckIntervalSeconds(Context context, int seconds) {
        if (!isSupportedCheckIntervalSeconds(seconds)) {
            throw new IllegalArgumentException("Unsupported Claro check interval");
        }
        preferences(context).edit().putInt(KEY_CHECK_INTERVAL_SECONDS, seconds).apply();
        SettingsBackup.changed(context);
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
