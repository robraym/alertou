package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Small, atomic snapshot plus transitions, separate from product prices and Telegram history. */
final class MeliuzCashbackStore {
    private static final Object LOCK = new Object();
    private static final String PREFS = "meliuz_cashback_state";
    private final SharedPreferences preferences;

    MeliuzCashbackStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    MeliuzCashbackStore(SharedPreferences preferences) { this.preferences = preferences; }

    JSONObject snapshot() { synchronized (LOCK) { return read("snapshot"); } }
    JSONObject history() { synchronized (LOCK) { return read("history"); } }
    long lastSuccess() { return snapshot().optLong("observed_at"); }
    boolean failed() { return preferences.getBoolean("failed", false); }
    void failedAttempt() { preferences.edit().putBoolean("failed", true).apply(); }

    List<JSONObject> record(JSONArray stores, double minimum, long at) throws Exception {
        synchronized (LOCK) {
            JSONObject old = read("snapshot");
            JSONArray previous = old.optJSONArray("stores");
            JSONObject history = read("history");
            List<JSONObject> notifications = new ArrayList<>();
            for (int i = 0; i < stores.length(); i++) {
                JSONObject store = stores.getJSONObject(i);
                long id = store.getLong("id");
                JSONObject before = find(previous, id);
                if (MeliuzCashbackClient.shouldNotify(before, old.optDouble("minimum", minimum),
                        store, minimum)) notifications.add(store);
                JSONArray points = history.optJSONArray(Long.toString(id));
                if (points == null) points = new JSONArray();
                JSONObject last = points.length() == 0 ? null : points.optJSONObject(points.length() - 1);
                if (MeliuzCashbackClient.changed(last, store)) {
                    points.put(new JSONObject(store.toString()).put("observed_at", at));
                    history.put(Long.toString(id), points);
                }
            }
            preferences.edit().putString("snapshot", new JSONObject()
                    .put("observed_at", at).put("minimum", minimum).put("stores", stores).toString())
                    .putString("history", history.toString()).putBoolean("failed", false).apply();
            return notifications;
        }
    }

    static JSONObject find(JSONArray stores, long id) {
        if (stores != null) for (int i = 0; i < stores.length(); i++) {
            JSONObject store = stores.optJSONObject(i);
            if (store != null && store.optLong("id") == id) return store;
        }
        return null;
    }

    JSONObject exportBackup() {
        synchronized (LOCK) {
            try { return new JSONObject().put("snapshot", read("snapshot")).put("history", read("history")); }
            catch (Exception impossible) { throw new IllegalStateException(impossible); }
        }
    }

    boolean restore(JSONObject backup, boolean force) {
        if (backup == null) return false;
        synchronized (LOCK) {
            try {
                JSONObject old = read("snapshot");
                JSONObject incoming = backup.optJSONObject("snapshot");
                JSONObject snapshot = old;
                if (validSnapshot(incoming) && (force
                        || incoming.optLong("observed_at") > old.optLong("observed_at"))) snapshot = incoming;
                JSONObject history = mergeHistory(read("history"), backup.optJSONObject("history"));
                boolean changed = !snapshot.toString().equals(old.toString())
                        || !history.toString().equals(read("history").toString());
                if (changed) preferences.edit().putString("snapshot", snapshot.toString())
                        .putString("history", history.toString()).putBoolean("failed", false).apply();
                return changed;
            } catch (Exception invalid) { return false; }
        }
    }

    static JSONObject mergeHistory(JSONObject local, JSONObject remote) throws Exception {
        JSONObject merged = new JSONObject(local.toString());
        if (remote == null) return merged;
        java.util.Iterator<String> keys = remote.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.matches("[1-9][0-9]*")) continue;
            Map<Long, JSONObject> points = new TreeMap<>();
            for (JSONArray source : new JSONArray[]{local.optJSONArray(key), remote.optJSONArray(key)}) {
                if (source == null) continue;
                for (int i = 0; i < source.length(); i++) {
                    JSONObject point = source.optJSONObject(i);
                    if (validStore(point) && key.equals(Long.toString(point.optLong("id")))
                            && point.optLong("observed_at") > 0) {
                        points.putIfAbsent(point.getLong("observed_at"), point);
                    }
                }
            }
            JSONArray result = new JSONArray();
            for (JSONObject point : points.values()) result.put(point);
            if (result.length() > 0) merged.put(key, result);
        }
        return merged;
    }

    static boolean validSnapshot(JSONObject snapshot) {
        if (snapshot == null || snapshot.optLong("observed_at") <= 0) return false;
        JSONArray stores = snapshot.optJSONArray("stores");
        if (stores == null || stores.length() == 0 || stores.length() > 500) return false;
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (int i = 0; i < stores.length(); i++) {
            JSONObject store = stores.optJSONObject(i);
            if (!validStore(store) || !ids.add(store.optLong("id"))) return false;
        }
        return true;
    }

    private static boolean validStore(JSONObject store) {
        if (store == null) return false;
        double amount = store.optDouble("amount", -1);
        return store.optLong("id") > 0 && !store.optString("name").trim().isEmpty()
                && store.optString("uri").matches("[a-zA-Z0-9_-]+")
                && Double.isFinite(amount) && amount >= 0 && !store.optString("type").isEmpty()
                && (!"percentage".equals(store.optString("type")) || amount <= 100);
    }

    private JSONObject read(String key) {
        try { return new JSONObject(preferences.getString(key, "{}")); }
        catch (Exception invalid) { return new JSONObject(); }
    }
}
