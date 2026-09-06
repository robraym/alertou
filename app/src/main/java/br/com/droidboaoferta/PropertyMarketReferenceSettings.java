package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

final class PropertyMarketReferenceSettings {
    private static final String PREFS = "property_market_reference";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CHECK_INTERVAL_MINUTES = "check_interval_minutes";
    static final int DEFAULT_CHECK_INTERVAL_MINUTES = 15;

    private PropertyMarketReferenceSettings() {
    }

    static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        SettingsBackup.changed(context);
    }

    static int getSummaryResource(Context context) {
        return isEnabled(context)
                ? R.string.property_market_reference_enabled
                : R.string.property_market_reference_disabled;
    }

    static int getCheckIntervalMinutes(Context context) {
        int saved = preferences(context).getInt(
                KEY_CHECK_INTERVAL_MINUTES,
                DEFAULT_CHECK_INTERVAL_MINUTES
        );
        return isSupportedCheckInterval(saved) ? saved : DEFAULT_CHECK_INTERVAL_MINUTES;
    }

    static void saveCheckIntervalMinutes(Context context, int minutes) {
        if (!isSupportedCheckInterval(minutes)) {
            throw new IllegalArgumentException("Unsupported property check interval");
        }
        preferences(context).edit().putInt(KEY_CHECK_INTERVAL_MINUTES, minutes).apply();
        SettingsBackup.changed(context);
    }

    static boolean isReference(ObservedOffer offer) {
        return offer != null && offer.getId().startsWith("property_market|");
    }

    static String createOfferId(long interestId, String listingId) {
        return "property_market|" + interestId + "|" + listingId;
    }

    static boolean isSupportedCheckInterval(int minutes) {
        return minutes == 5 || minutes == 15 || minutes == 30 || minutes == 60;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
