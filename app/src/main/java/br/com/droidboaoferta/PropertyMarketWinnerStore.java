package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

/** Keeps the most recent listing that became the lowest market reference until a new one wins. */
final class PropertyMarketWinnerStore {
    private static final String PREFS = "property_market_winners";
    private static final String KEY_ID_PREFIX = "winner_id_";
    private static final String KEY_AT_PREFIX = "winner_at_";
    private static final String KEY_PREVIOUS_PRICE_PREFIX = "winner_previous_price_";

    private PropertyMarketWinnerStore() {
    }

    static void record(Context context, ObservedOffer offer, double previousPrice) {
        preferences(context).edit()
                .putString(KEY_ID_PREFIX + offer.getInterestId(), offer.getId())
                .putLong(KEY_AT_PREFIX + offer.getInterestId(), offer.getObservedAt())
                .putLong(KEY_PREVIOUS_PRICE_PREFIX + offer.getInterestId(),
                        Double.doubleToRawLongBits(previousPrice))
                .apply();
    }

    static boolean isActive(Context context, ObservedOffer offer) {
        SharedPreferences preferences = preferences(context);
        return offer.getId().equals(preferences.getString(KEY_ID_PREFIX + offer.getInterestId(), ""))
                && preferences.getLong(KEY_AT_PREFIX + offer.getInterestId(), 0L) > 0L;
    }

    static WinnerInfo get(Context context, ObservedOffer offer) {
        SharedPreferences preferences = preferences(context);
        if (!isActive(context, offer)) return null;
        return new WinnerInfo(
                preferences.getLong(KEY_AT_PREFIX + offer.getInterestId(), 0L),
                Double.longBitsToDouble(preferences.getLong(
                        KEY_PREVIOUS_PRICE_PREFIX + offer.getInterestId(),
                        Double.doubleToRawLongBits(Double.NaN)
                ))
        );
    }

    static final class WinnerInfo {
        final long wonAt;
        final double previousPrice;

        WinnerInfo(long wonAt, double previousPrice) {
            this.wonAt = wonAt;
            this.previousPrice = previousPrice;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
