package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Stores the user's explicit decision that one observed price is wrong. */
final class OfferInvalidationRepository {
    private static final String PREFS = "offer_invalidation_preferences";
    private static final String KEY_INVALIDATIONS = "invalidations";
    private static final int MAX_INVALIDATIONS = 300;

    private final SharedPreferences preferences;

    OfferInvalidationRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void markInvalid(ObservedOffer offer) {
        if (offer == null) return;
        JSONArray invalidations = read();
        JSONArray updated = new JSONArray();
        String interest = normalize(offer.getInterest());
        String source = normalize(offer.getSource());
        for (int index = 0; index < invalidations.length(); index++) {
            JSONObject item = invalidations.optJSONObject(index);
            if (item != null && same(item, interest, source, offer.getPrice(), offer.getObservedAt())) {
                continue;
            }
            updated.put(invalidations.opt(index));
        }
        try {
            updated.put(new JSONObject()
                    .put("interest", interest)
                    .put("source", source)
                    .put("price", offer.getPrice())
                    .put("observed_at", offer.getObservedAt()));
        } catch (Exception ignored) {
            return;
        }
        while (updated.length() > MAX_INVALIDATIONS) {
            JSONArray trimmed = new JSONArray();
            for (int index = 1; index < updated.length(); index++) {
                trimmed.put(updated.opt(index));
            }
            updated = trimmed;
        }
        preferences.edit().putString(KEY_INVALIDATIONS, updated.toString()).apply();
    }

    synchronized boolean isInvalidated(String interest, String source, double price, long observedAt) {
        String normalizedInterest = normalize(interest);
        String normalizedSource = normalize(source);
        JSONArray invalidations = read();
        for (int index = 0; index < invalidations.length(); index++) {
            JSONObject item = invalidations.optJSONObject(index);
            if (item != null && same(item, normalizedInterest, normalizedSource, price, observedAt)) {
                return true;
            }
        }
        return false;
    }

    private boolean same(JSONObject item, String interest, String source, double price, long observedAt) {
        return interest.equals(item.optString("interest"))
                && source.equals(item.optString("source"))
                && Double.compare(price, item.optDouble("price", Double.NaN)) == 0
                && observedAt == item.optLong("observed_at", 0L);
    }

    private JSONArray read() {
        try {
            return new JSONArray(preferences.getString(KEY_INVALIDATIONS, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private String normalize(String value) {
        return OfferTextParser.normalize(value == null ? "" : value);
    }
}
