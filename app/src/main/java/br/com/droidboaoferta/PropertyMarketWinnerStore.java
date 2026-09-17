package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

/** Keeps the most recent listing that became the lowest market reference until a new one wins. */
final class PropertyMarketWinnerStore {
    private static final String PREFS = "property_market_winners";
    private static final String KEY_ID_PREFIX = "winner_id_";
    private static final String KEY_AT_PREFIX = "winner_at_";
    private static final String KEY_PREVIOUS_PRICE_PREFIX = "winner_previous_price_";
    private static final String KEY_PREVIOUS_ID_PREFIX = "winner_previous_id_";
    private static final String KEY_PREVIOUS_INTEREST_PREFIX = "winner_previous_interest_";
    private static final String KEY_PREVIOUS_SOURCE_PREFIX = "winner_previous_source_";
    private static final String KEY_PREVIOUS_MAXIMUM_PREFIX = "winner_previous_maximum_";
    private static final String KEY_PREVIOUS_LINK_PREFIX = "winner_previous_link_";
    private static final String KEY_PREVIOUS_TITLE_PREFIX = "winner_previous_title_";

    private PropertyMarketWinnerStore() {
    }

    static void record(Context context, ObservedOffer offer, ObservedOffer previousOffer) {
        if (offer == null || previousOffer == null) return;
        preferences(context).edit()
                .putString(KEY_ID_PREFIX + offer.getInterestId(), offer.getId())
                .putLong(KEY_AT_PREFIX + offer.getInterestId(), offer.getObservedAt())
                .putLong(KEY_PREVIOUS_PRICE_PREFIX + offer.getInterestId(),
                        Double.doubleToRawLongBits(previousOffer.getPrice()))
                .putString(KEY_PREVIOUS_ID_PREFIX + offer.getInterestId(), previousOffer.getId())
                .putString(KEY_PREVIOUS_INTEREST_PREFIX + offer.getInterestId(), previousOffer.getInterest())
                .putString(KEY_PREVIOUS_SOURCE_PREFIX + offer.getInterestId(), previousOffer.getSource())
                .putLong(KEY_PREVIOUS_MAXIMUM_PREFIX + offer.getInterestId(),
                        Double.doubleToRawLongBits(previousOffer.getMaximumPrice()))
                .putString(KEY_PREVIOUS_LINK_PREFIX + offer.getInterestId(), previousOffer.getLink())
                .putString(KEY_PREVIOUS_TITLE_PREFIX + offer.getInterestId(), previousOffer.getProductTitle())
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

    static ObservedOffer getPreviousReference(Context context, ObservedOffer offer) {
        if (offer == null || !isActive(context, offer)) return null;
        SharedPreferences preferences = preferences(context);
        long interestId = offer.getInterestId();
        String id = preferences.getString(KEY_PREVIOUS_ID_PREFIX + interestId, "").trim();
        String link = preferences.getString(KEY_PREVIOUS_LINK_PREFIX + interestId, "").trim();
        if (id.isEmpty() || link.isEmpty()) return null;
        return new ObservedOffer(
                id,
                interestId,
                preferences.getString(KEY_PREVIOUS_INTEREST_PREFIX + interestId, offer.getInterest()),
                preferences.getString(KEY_PREVIOUS_SOURCE_PREFIX + interestId, ""),
                Double.longBitsToDouble(preferences.getLong(KEY_PREVIOUS_PRICE_PREFIX + interestId,
                        Double.doubleToRawLongBits(Double.NaN))),
                Double.longBitsToDouble(preferences.getLong(KEY_PREVIOUS_MAXIMUM_PREFIX + interestId,
                        Double.doubleToRawLongBits(offer.getMaximumPrice()))),
                preferences.getLong(KEY_AT_PREFIX + interestId, 0L),
                link,
                "",
                preferences.getString(KEY_PREVIOUS_TITLE_PREFIX + interestId, "")
        );
    }

    static void rememberPreviousReference(Context context, ObservedOffer offer,
                                          ObservedOffer previousOffer) {
        if (offer == null || previousOffer == null || !isActive(context, offer)) return;
        long interestId = offer.getInterestId();
        preferences(context).edit()
                .putString(KEY_PREVIOUS_ID_PREFIX + interestId, previousOffer.getId())
                .putString(KEY_PREVIOUS_INTEREST_PREFIX + interestId, previousOffer.getInterest())
                .putString(KEY_PREVIOUS_SOURCE_PREFIX + interestId, previousOffer.getSource())
                .putLong(KEY_PREVIOUS_PRICE_PREFIX + interestId,
                        Double.doubleToRawLongBits(previousOffer.getPrice()))
                .putLong(KEY_PREVIOUS_MAXIMUM_PREFIX + interestId,
                        Double.doubleToRawLongBits(previousOffer.getMaximumPrice()))
                .putString(KEY_PREVIOUS_LINK_PREFIX + interestId, previousOffer.getLink())
                .putString(KEY_PREVIOUS_TITLE_PREFIX + interestId, previousOffer.getProductTitle())
                .apply();
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
