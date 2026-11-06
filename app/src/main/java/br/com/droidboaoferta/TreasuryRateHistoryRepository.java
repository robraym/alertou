package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local history of rates actually returned by the official Tesouro Direto feed. */
final class TreasuryRateHistoryRepository {
    private static final String PREFS = "treasury_rate_history";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_POINTS_PER_TITLE = 120;
    private final SharedPreferences preferences;

    TreasuryRateHistoryRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void record(long interestId, double rate, long observedAt) {
        List<TreasuryRatePoint> points = new ArrayList<>(get(interestId));
        if (!points.isEmpty() && Double.compare(points.get(points.size() - 1).getRate(), rate) == 0) {
            // Same rate is a new successful consultation, not a false rate change.
            points.set(points.size() - 1, new TreasuryRatePoint(observedAt, rate));
        } else {
            points.add(new TreasuryRatePoint(observedAt, rate));
        }
        while (points.size() > MAX_POINTS_PER_TITLE) points.remove(0);
        save(interestId, points);
    }

    synchronized List<TreasuryRatePoint> get(long interestId) {
        try {
            JSONObject root = new JSONObject(preferences.getString(KEY_HISTORY, "{}"));
            JSONArray values = root.optJSONArray(Long.toString(interestId));
            if (values == null) return Collections.emptyList();
            List<TreasuryRatePoint> points = new ArrayList<>();
            for (int index = 0; index < values.length(); index++) {
                JSONObject item = values.optJSONObject(index);
                if (item == null) continue;
                long observedAt = item.optLong("observed_at", 0L);
                double rate = item.optDouble("rate", Double.NaN);
                if (observedAt > 0L && !Double.isNaN(rate)) {
                    points.add(new TreasuryRatePoint(observedAt, rate));
                }
            }
            return points;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private void save(long interestId, List<TreasuryRatePoint> points) {
        try {
            JSONObject root = new JSONObject(preferences.getString(KEY_HISTORY, "{}"));
            JSONArray values = new JSONArray();
            for (TreasuryRatePoint point : points) {
                values.put(new JSONObject().put("observed_at", point.getObservedAt())
                        .put("rate", point.getRate()));
            }
            root.put(Long.toString(interestId), values);
            preferences.edit().putString(KEY_HISTORY, root.toString()).apply();
        } catch (Exception ignored) {
            // A failed local history write must never interrupt the alert monitor.
        }
    }
}
