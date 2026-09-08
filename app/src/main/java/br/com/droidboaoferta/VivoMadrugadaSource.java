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

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
