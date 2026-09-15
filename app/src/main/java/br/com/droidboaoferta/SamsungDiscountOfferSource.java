package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.util.Locale;

/** Official Samsung Brazil discount page, kept separate from Samsung Ofertas. */
final class SamsungDiscountOfferSource {
    static final String DEFAULT_URL = "https://shop.samsung.com/br/desconto-samsung";
    static final String DEFAULT_PRODUCT_API_URL = "https://samsungbrshop.vtexcommercestable.com.br/api/catalog_system/pub/products/search?fq=skuId:";
    private static final String PREFS = "external_offer_sources";
    private static final String KEY_URL = "samsung_discount_offer_url";
    private static final String KEY_PRODUCT_API_URL = "samsung_discount_offer_product_api_url";
    private static final String KEY_TITLE = "samsung_discount_offer_title";
    private static final String KEY_CHECK_INTERVAL_SECONDS = "samsung_discount_offer_check_interval_seconds";
    private static final String KEY_LAST_SUCCESS = "samsung_discount_offer_last_success";
    private static final String KEY_LAST_FAILURE = "samsung_discount_offer_last_failure";
    static final int DEFAULT_CHECK_INTERVAL_SECONDS = 15 * 60;

    private SamsungDiscountOfferSource() { }

    static String getUrl(Context context) {
        return preferences(context).getString(KEY_URL, DEFAULT_URL);
    }

    static String getTitle(Context context) {
        return preferences(context).getString(KEY_TITLE, "Samsung Desconto");
    }

    static String getProductApiUrl(Context context) {
        return preferences(context).getString(KEY_PRODUCT_API_URL, DEFAULT_PRODUCT_API_URL);
    }

    static void saveProductApiUrl(Context context, String rawUrl) {
        String normalized = normalizeProductApiUrl(rawUrl);
        if (normalized == null) throw new IllegalArgumentException("Unsupported Samsung product API URL");
        preferences(context).edit().putString(KEY_PRODUCT_API_URL, normalized).apply();
        SettingsBackup.changed(context);
    }

    static void saveTitle(Context context, String title) {
        String value = title == null ? "" : title.trim().replaceAll("\\s+", " ");
        preferences(context).edit().putString(KEY_TITLE,
                value.isEmpty() ? "Samsung Desconto" : value.substring(0, Math.min(40, value.length()))).apply();
        SettingsBackup.changed(context);
    }

    static void save(Context context, String rawUrl) {
        String normalized = normalizeUrl(rawUrl);
        if (normalized == null) throw new IllegalArgumentException("Unsupported Samsung discount URL");
        preferences(context).edit().putString(KEY_URL, normalized).apply();
        SettingsBackup.changed(context);
    }

    static boolean isConfigured(Context context) {
        return normalizeUrl(getUrl(context)) != null;
    }

    static void markSuccessfulCheck(Context context) {
        preferences(context).edit().putLong(KEY_LAST_SUCCESS, System.currentTimeMillis()).apply();
    }

    static void markFailedCheck(Context context) {
        preferences(context).edit().putLong(KEY_LAST_FAILURE, System.currentTimeMillis()).apply();
    }

    static boolean hasSuccessfulCheck(Context context) {
        return preferences(context).getLong(KEY_LAST_SUCCESS, 0L) > 0L;
    }

    static boolean hasLastCheckFailed(Context context) {
        SharedPreferences preferences = preferences(context);
        return preferences.getLong(KEY_LAST_FAILURE, 0L)
                > preferences.getLong(KEY_LAST_SUCCESS, 0L);
    }

    static long getLastSuccessfulCheckAt(Context context) {
        return preferences(context).getLong(KEY_LAST_SUCCESS, 0L);
    }

    static long getLastFailedCheckAt(Context context) {
        return preferences(context).getLong(KEY_LAST_FAILURE, 0L);
    }

    static String normalizeUrl(String rawUrl) {
        return StoreSourceUrl.normalize(rawUrl);
    }

    static String normalizeProductApiUrl(String rawUrl) {
        return StoreSourceUrl.normalize(rawUrl);
    }

    static int getCheckIntervalSeconds(Context context) {
        int seconds = preferences(context).getInt(KEY_CHECK_INTERVAL_SECONDS,
                DEFAULT_CHECK_INTERVAL_SECONDS);
        return SamsungOfferSource.isSupportedCheckIntervalSeconds(seconds)
                ? seconds : DEFAULT_CHECK_INTERVAL_SECONDS;
    }

    static void saveCheckIntervalSeconds(Context context, int seconds) {
        if (!SamsungOfferSource.isSupportedCheckIntervalSeconds(seconds)) {
            throw new IllegalArgumentException("Unsupported Samsung discount interval");
        }
        preferences(context).edit().putInt(KEY_CHECK_INTERVAL_SECONDS, seconds).apply();
        SettingsBackup.changed(context);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
