package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

/** User choice to pause an individual store without losing its configured URL or interval. */
final class StoreSourceControl {
    static final String KEY_DISABLED_SOURCES_MASK = "disabled_store_sources_mask";
    private static final String PREFS = "external_offer_sources";

    private StoreSourceControl() { }

    static boolean isEnabled(Context context, int sourceTitleResource) {
        return (preferences(context).getInt(KEY_DISABLED_SOURCES_MASK, 0)
                & bitFor(sourceTitleResource)) == 0;
    }

    static void setEnabled(Context context, int sourceTitleResource, boolean enabled) {
        SharedPreferences preferences = preferences(context);
        int bit = bitFor(sourceTitleResource);
        int mask = preferences.getInt(KEY_DISABLED_SOURCES_MASK, 0);
        int updated = enabled ? mask & ~bit : mask | bit;
        if (updated == mask) return;
        preferences.edit().putInt(KEY_DISABLED_SOURCES_MASK, updated).apply();
        SettingsBackup.changed(context);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int bitFor(int sourceTitleResource) {
        if (sourceTitleResource == R.string.vivo_outlet_source_title) return 1;
        if (sourceTitleResource == R.string.vivo_madrugada_source_title) return 1 << 1;
        if (sourceTitleResource == R.string.pelando_source_title) return 1 << 2;
        if (sourceTitleResource == R.string.promobit_source_title) return 1 << 3;
        if (sourceTitleResource == R.string.kabum_offer_source_title) return 1 << 4;
        if (sourceTitleResource == R.string.kabum_catalog_source_title) return 1 << 5;
        if (sourceTitleResource == R.string.motorola_offer_source_title) return 1 << 6;
        if (sourceTitleResource == R.string.claro_offer_source_title) return 1 << 7;
        if (sourceTitleResource == R.string.samsung_offer_source_title) return 1 << 8;
        if (sourceTitleResource == R.string.samsung_discount_offer_source_title) return 1 << 9;
        if (sourceTitleResource == R.string.kabum_catalog_api_source_title) return 1 << 10;
        throw new IllegalArgumentException("Fonte de loja desconhecida");
    }
}
