package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

/** Configurable KaBuM general-catalog JSON endpoint, separate from the HTML fallback. */
final class KabumCatalogApiSource {
    static final String DEFAULT_URL = "https://servicespub.prod.api.aws.grupokabum.com.br/catalog/v2/products";
    private static final String PREFS = "external_offer_sources";
    private static final String KEY_URL = "kabum_catalog_api_url";
    private static final String KEY_LAST_SUCCESS = "kabum_catalog_api_last_success";
    private static final String KEY_LAST_FAILURE = "kabum_catalog_api_last_failure";
    private static final String KEY_INTERVAL = "kabum_catalog_api_check_interval_seconds";
    static final int DEFAULT_CHECK_INTERVAL_SECONDS = 900;

    private KabumCatalogApiSource() { }

    static String getUrl(Context context) {
        String saved = preferences(context).getString(KEY_URL, DEFAULT_URL);
        return StoreSourceUrl.normalize(saved);
    }

    static boolean isConfigured(Context context) { return getUrl(context) != null; }

    static void save(Context context, String url) {
        String normalized = StoreSourceUrl.normalize(url);
        if (normalized == null) throw new IllegalArgumentException();
        preferences(context).edit().putString(KEY_URL, normalized).apply();
        SettingsBackup.changed(context);
    }

    static void markSuccessfulCheck(Context context) {
        preferences(context).edit().putLong(KEY_LAST_SUCCESS, System.currentTimeMillis()).apply();
    }

    static void markFailedCheck(Context context) {
        preferences(context).edit().putLong(KEY_LAST_FAILURE, System.currentTimeMillis()).apply();
    }

    static boolean hasSuccessfulCheck(Context context) { return getLastSuccessfulCheckAt(context) > 0L; }
    static boolean hasLastCheckFailed(Context context) { return getLastFailedCheckAt(context) > getLastSuccessfulCheckAt(context); }
    static long getLastSuccessfulCheckAt(Context context) { return preferences(context).getLong(KEY_LAST_SUCCESS, 0L); }
    static long getLastFailedCheckAt(Context context) { return preferences(context).getLong(KEY_LAST_FAILURE, 0L); }

    static int getCheckIntervalSeconds(Context context) {
        int value = preferences(context).getInt(KEY_INTERVAL, DEFAULT_CHECK_INTERVAL_SECONDS);
        return isSupportedCheckInterval(value) ? value : DEFAULT_CHECK_INTERVAL_SECONDS;
    }

    static void saveCheckIntervalSeconds(Context context, int value) {
        if (!isSupportedCheckInterval(value)) throw new IllegalArgumentException();
        preferences(context).edit().putInt(KEY_INTERVAL, value).apply();
        SettingsBackup.changed(context);
    }

    static boolean isSupportedCheckInterval(int value) { return KabumOfferSource.isSupportedCheckInterval(value); }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
