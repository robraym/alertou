package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

/** Official Vivo overnight campaign. Its address is fixed so it cannot point to a third party. */
final class VivoMadrugadaSource {
    static final String URL = "https://store.vivo.com.br/oferta-da-madrugada/c";
    private static final String PREFS = "external_offer_sources";
    private static final String LAST_SUCCESS = "vivo_madrugada_last_success";
    private static final String LAST_FAILURE = "vivo_madrugada_last_failure";

    private VivoMadrugadaSource() { }

    static boolean isConfigured(Context context) {
        return VivoOutletSource.normalizeUrl(URL) != null;
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
