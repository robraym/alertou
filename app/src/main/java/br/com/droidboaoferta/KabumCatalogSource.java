package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

final class KabumCatalogSource {
    static final String DEFAULT_URL = "https://www.kabum.com.br/busca/";
    private static final String PREFS = "external_offer_sources";
    private static final String KEY_URL = "kabum_catalog_url";
    private static final String KEY_LAST_SUCCESS = "kabum_catalog_last_success";
    private static final String KEY_LAST_FAILURE = "kabum_catalog_last_failure";
    private static final String KEY_INTERVAL = "kabum_catalog_check_interval_seconds";
    static final int DEFAULT_CHECK_INTERVAL_SECONDS = 900;
    private KabumCatalogSource() { }
    static String getUrl(Context context) { String saved=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_URL,DEFAULT_URL); return StoreSourceUrl.normalize(saved); }
    static boolean isConfigured(Context context) { return getUrl(context)!=null; }
    static void save(Context context,String url) { String normalized=StoreSourceUrl.normalize(url); if(normalized==null) throw new IllegalArgumentException(); context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_URL,normalized).apply(); SettingsBackup.changed(context); }
    static void markSuccessfulCheck(Context c) { preferences(c).edit().putLong(KEY_LAST_SUCCESS,System.currentTimeMillis()).apply(); }
    static void markFailedCheck(Context c) { preferences(c).edit().putLong(KEY_LAST_FAILURE,System.currentTimeMillis()).apply(); }
    static boolean hasSuccessfulCheck(Context c) { return getLastSuccessfulCheckAt(c)>0; }
    static boolean hasLastCheckFailed(Context c) { return getLastFailedCheckAt(c)>getLastSuccessfulCheckAt(c); }
    static long getLastSuccessfulCheckAt(Context c) { return preferences(c).getLong(KEY_LAST_SUCCESS,0); }
    static long getLastFailedCheckAt(Context c) { return preferences(c).getLong(KEY_LAST_FAILURE,0); }
    static int getCheckIntervalSeconds(Context c) { int value=preferences(c).getInt(KEY_INTERVAL,DEFAULT_CHECK_INTERVAL_SECONDS); return isSupportedCheckInterval(value)?value:DEFAULT_CHECK_INTERVAL_SECONDS; }
    static void saveCheckIntervalSeconds(Context c,int value) { if(!isSupportedCheckInterval(value)) throw new IllegalArgumentException(); preferences(c).edit().putInt(KEY_INTERVAL,value).apply(); SettingsBackup.changed(c); }
    static boolean isSupportedCheckInterval(int value) { return KabumOfferSource.isSupportedCheckInterval(value); }
    private static SharedPreferences preferences(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE); }
}
