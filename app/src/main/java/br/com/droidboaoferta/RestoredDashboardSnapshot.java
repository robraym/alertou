package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Carries the data already read while restoring into the first dashboard render. */
final class RestoredDashboardSnapshot {
    static final class Snapshot {
        final List<Interest> interests;
        final List<ObservedOffer> offers;
        final Map<String, PropertyHistoryEntry> propertyHistory;

        Snapshot(List<Interest> interests, List<ObservedOffer> offers,
                 Map<String, PropertyHistoryEntry> propertyHistory) {
            this.interests = interests;
            this.offers = offers;
            this.propertyHistory = propertyHistory;
        }
    }

    private static Snapshot pending;

    private RestoredDashboardSnapshot() {
    }

    /** Uses the backup object already in memory; never reads local storage after install. */
    static synchronized void capture(JSONObject backup) {
        JSONObject data = backup == null ? null : backup.optJSONObject("data");
        JSONArray source = null;
        if (data != null) {
            try {
                source = new JSONArray(data.optString("interests", "[]"));
            } catch (Exception ignored) {
                source = null;
            }
        }
        List<Interest> interests = new ArrayList<>();
        if (source != null) {
            for (int index = 0; index < source.length(); index++) {
                JSONObject item = source.optJSONObject(index);
                if (item == null) continue;
                long id = item.optLong("id", 0L);
                String term = item.optString("term", "").trim();
                if (id <= 0L || term.isEmpty() || !item.has("maximum_price")) continue;
                interests.add(new Interest(id, term, item.optDouble("maximum_price"),
                        item.optString("type", Interest.TYPE_PRICE),
                        item.optDouble("minimum_area", 0d), item.optDouble("maximum_area", 0d),
                        item.optString("property_name", ""), item.optString("coupon_name", ""),
                        item.optString("property_zip_code", ""),
                        item.optString("property_street", ""),
                        item.optString("property_neighborhood", ""),
                        item.optString("property_city", ""), item.optString("property_state", ""),
                        item.optInt("property_listing_limit",
                                Interest.DEFAULT_PROPERTY_LISTING_LIMIT)));
            }
        }
        interests.sort((first, second) -> Long.compare(second.getId(), first.getId()));
        pending = new Snapshot(interests, Collections.emptyList(), Collections.emptyMap());
    }

    static synchronized Snapshot take() {
        Snapshot snapshot = pending;
        pending = null;
        return snapshot;
    }
}
