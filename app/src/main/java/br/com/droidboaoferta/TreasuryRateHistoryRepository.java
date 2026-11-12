package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** Local history of rates actually returned by the official Tesouro Direto feed. */
final class TreasuryRateHistoryRepository {
    private static final String PREFS = "treasury_rate_history";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_POINTS_PER_TITLE = 120;
    private static final Object HISTORY_LOCK = new Object();
    private final SharedPreferences preferences;

    TreasuryRateHistoryRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Returns true only when the observed rate actually changed. */
    boolean record(long interestId, double rate, long observedAt) {
        synchronized (HISTORY_LOCK) {
            List<TreasuryRatePoint> points = new ArrayList<>(get(interestId));
            boolean changed = points.isEmpty()
                    || Double.compare(points.get(points.size() - 1).getRate(), rate) != 0;
            if (!points.isEmpty()
                    && Double.compare(points.get(points.size() - 1).getRate(), rate) == 0) {
                // Same rate is a new successful consultation, not a false rate change.
                points.set(points.size() - 1, new TreasuryRatePoint(observedAt, rate));
            } else {
                points.add(new TreasuryRatePoint(observedAt, rate));
            }
            while (points.size() > MAX_POINTS_PER_TITLE) points.remove(0);
            save(interestId, points);
            return changed;
        }
    }

    /** Compact history safe to carry in the encrypted Telegram backup. */
    JSONObject exportForSync() {
        synchronized (HISTORY_LOCK) {
            JSONObject exported = new JSONObject();
            try {
                JSONObject root = new JSONObject(preferences.getString(KEY_HISTORY, "{}"));
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    long interestId = Long.parseLong(key);
                    JSONArray values = pointsToJson(get(interestId));
                    if (values.length() > 0) exported.put(key, values);
                }
            } catch (Exception ignored) {
                // Keep a backup usable even if one local history entry is malformed.
            }
            return exported;
        }
    }

    /** Merges readings from another device instead of replacing the local history. */
    boolean mergeFromSync(JSONObject incoming) {
        if (incoming == null) return false;
        synchronized (HISTORY_LOCK) {
            boolean changed = false;
            Iterator<String> keys = incoming.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                long interestId;
                try {
                    interestId = Long.parseLong(key);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                List<TreasuryRatePoint> combined = new ArrayList<>(get(interestId));
                JSONArray remote = incoming.optJSONArray(key);
                for (int index = 0; remote != null && index < remote.length(); index++) {
                    JSONObject point = remote.optJSONObject(index);
                    long observedAt = point == null ? 0L : point.optLong("observed_at", 0L);
                    double rate = point == null ? Double.NaN : point.optDouble("rate", Double.NaN);
                    if (observedAt > 0L && !Double.isNaN(rate)) {
                        combined.add(new TreasuryRatePoint(observedAt, rate));
                    }
                }
                combined.sort(Comparator.comparingLong(TreasuryRatePoint::getObservedAt));
                List<TreasuryRatePoint> merged = new ArrayList<>();
                for (TreasuryRatePoint point : combined) {
                    if (!merged.isEmpty() && merged.get(merged.size() - 1).getObservedAt()
                            == point.getObservedAt()) {
                        merged.set(merged.size() - 1, point);
                    } else {
                        merged.add(point);
                    }
                }
                merged = removeRepeatedRates(merged);
                while (merged.size() > MAX_POINTS_PER_TITLE) merged.remove(0);
                if (!pointsToJson(get(interestId)).toString().equals(pointsToJson(merged).toString())) {
                    save(interestId, merged);
                    changed = true;
                }
            }
            return changed;
        }
    }

    List<TreasuryRatePoint> get(long interestId) {
        synchronized (HISTORY_LOCK) {
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
                return removeRepeatedRates(points);
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }
    }

    private List<TreasuryRatePoint> removeRepeatedRates(List<TreasuryRatePoint> points) {
        List<TreasuryRatePoint> uniquePoints = new ArrayList<>();
        for (TreasuryRatePoint point : points) {
            if (!uniquePoints.isEmpty() && Double.compare(
                    uniquePoints.get(uniquePoints.size() - 1).getRate(), point.getRate()) == 0) {
                uniquePoints.set(uniquePoints.size() - 1, point);
            } else {
                uniquePoints.add(point);
            }
        }
        return uniquePoints;
    }

    private void save(long interestId, List<TreasuryRatePoint> points) {
        try {
            JSONObject root = new JSONObject(preferences.getString(KEY_HISTORY, "{}"));
            root.put(Long.toString(interestId), pointsToJson(points));
            preferences.edit().putString(KEY_HISTORY, root.toString()).apply();
        } catch (Exception ignored) {
            // A failed local history write must never interrupt the alert monitor.
        }
    }

    private JSONArray pointsToJson(List<TreasuryRatePoint> points) {
        JSONArray values = new JSONArray();
        for (TreasuryRatePoint point : points) {
            try {
                values.put(new JSONObject().put("observed_at", point.getObservedAt())
                        .put("rate", point.getRate()));
            } catch (Exception ignored) {
                // Skip only the malformed point; the remaining history is still useful.
            }
        }
        return values;
    }
}
