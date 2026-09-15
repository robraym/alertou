package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.util.Locale;

final class KabumOfferSource {
    static final String DEFAULT_URL = "https://servicespub.prod.api.aws.grupokabum.com.br/catalog/v2/brandshowcase?query=oferta-relampago-lista&is_prime=false&payload_data=products_category_filters";
    private static final String PREFS = "external_offer_sources";
    private static final String KEY_URL = "kabum_offer_url";
    private static final String KEY_LAST_SUCCESS = "kabum_offer_last_success";
    private static final String KEY_LAST_FAILURE = "kabum_offer_last_failure";
    private static final String KEY_CHECK_INTERVAL_SECONDS = "kabum_offer_check_interval_seconds";
    static final int DEFAULT_CHECK_INTERVAL_SECONDS = 60;

    private KabumOfferSource() {
    }

    static String getUrl(Context context) {
        String saved = preferences(context).getString(KEY_URL, "");
        if ("https://www.kabum.com.br/lojas/oferta-relampago".equals(saved)) return DEFAULT_URL;
        String normalized = normalizeUrl(saved);
        return normalized == null ? saved : normalized;
    }

    static boolean isConfigured(Context context) {
        return normalizeUrl(getUrl(context)) != null;
    }

    static void save(Context context, String rawUrl) {
        String normalized = normalizeUrl(rawUrl);
        if (normalized == null) {
            throw new IllegalArgumentException("Unsupported KaBuM URL");
        }
        preferences(context).edit().putString(KEY_URL, normalized).apply();
        SettingsBackup.changed(context);
    }

    static String normalizeUrl(String rawUrl) {
        return StoreSourceUrl.normalize(rawUrl);
    }

    static void markSuccessfulCheck(Context context) {
        preferences(context).edit()
                .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
                .apply();
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
        int saved = preferences(context).getInt(
                KEY_CHECK_INTERVAL_SECONDS,
                DEFAULT_CHECK_INTERVAL_SECONDS
        );
        return isSupportedCheckInterval(saved) ? saved : DEFAULT_CHECK_INTERVAL_SECONDS;
    }

    static void saveCheckIntervalSeconds(Context context, int seconds) {
        if (!isSupportedCheckInterval(seconds)) {
            throw new IllegalArgumentException("Unsupported KaBuM check interval");
        }
        preferences(context).edit().putInt(KEY_CHECK_INTERVAL_SECONDS, seconds).apply();
        SettingsBackup.changed(context);
    }

    static boolean isSupportedCheckInterval(int seconds) {
        return seconds == 30 || seconds == 60 || seconds == 120 || seconds == 300 || seconds == 900
                || seconds == 1800 || seconds == 3600 || seconds == 21600
                || seconds == 43200 || seconds == 86400;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
