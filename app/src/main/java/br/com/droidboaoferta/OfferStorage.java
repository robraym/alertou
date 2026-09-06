package br.com.droidboaoferta;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Shared transaction boundary for offer collections, including cloud imports. */
final class OfferStorage {
    static final Object LOCK = new Object();

    private OfferStorage() { }

    static List<ObservedOffer> read(SharedPreferences preferences, String key) {
        synchronized (LOCK) {
            String raw = preferences.getString(key, "[]");
            List<ObservedOffer> result = new ArrayList<>();
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    try {
                        JSONObject item = array.getJSONObject(i);
                        double price = item.getDouble("price");
                        double maximum = item.getDouble("maximum_price");
                        if (!Double.isFinite(price) || !Double.isFinite(maximum)) {
                            throw new IllegalArgumentException("Invalid stored price");
                        }
                        result.add(new ObservedOffer(item.optString("id", ""),
                                item.optLong("interest_id", 0), item.getString("interest"),
                                item.getString("source"), price, maximum, item.getLong("observed_at"),
                                item.optString("link"), item.optString("telegram_post_link", "")));
                    } catch (Exception malformedItem) {
                        preserve(preferences, key, raw);
                    }
                }
            } catch (Exception malformedCollection) {
                preserve(preferences, key, raw);
            }
            return result;
        }
    }

    private static void preserve(SharedPreferences preferences, String key, String raw) {
        // Keep the original bytes available for recovery before a later write repairs the list.
        String recoveryKey = "recovery_" + key;
        if (!preferences.contains(recoveryKey)) {
            preferences.edit().putString(recoveryKey, raw).commit();
        }
    }

    static String encode(List<ObservedOffer> offers) {
        JSONArray array = new JSONArray();
        try {
            for (ObservedOffer item : offers) {
                array.put(new JSONObject().put("id", item.getId())
                        .put("interest_id", item.getInterestId()).put("interest", item.getInterest())
                        .put("source", item.getSource()).put("price", item.getPrice())
                        .put("maximum_price", item.getMaximumPrice()).put("observed_at", item.getObservedAt())
                        .put("link", item.getLink()).put("telegram_post_link", item.getTelegramPostLink()));
            }
            return array.toString();
        } catch (Exception invalidOffer) {
            throw new IllegalArgumentException("Não foi possível salvar a oferta.", invalidOffer);
        }
    }
}
