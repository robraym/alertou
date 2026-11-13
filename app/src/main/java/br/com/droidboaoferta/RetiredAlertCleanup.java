package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Removes data belonging to retired alert categories; never starts a monitor. */
final class RetiredAlertCleanup {
    private RetiredAlertCleanup() { }

    private static void remember(Context context, Set<Long> ids) {
        if (ids.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences("retired_alert_cleanup", Context.MODE_PRIVATE);
        Set<String> all = new HashSet<>(prefs.getStringSet("removed_ids", java.util.Collections.emptySet()));
        for (Long id : ids) all.add(Long.toString(id));
        prefs.edit().putStringSet("removed_ids", all).commit();
    }

    static JSONArray filterOffers(Context context, JSONArray offers) {
        Set<Long> ids = new HashSet<>();
        for (String id : context.getSharedPreferences("retired_alert_cleanup", Context.MODE_PRIVATE)
                .getStringSet("removed_ids", java.util.Collections.emptySet())) {
            try { ids.add(Long.parseLong(id)); } catch (NumberFormatException ignored) { }
        }
        return clean(offers, ids, false);
    }

    static JSONObject sanitizeBackup(Context context, JSONObject original) {
        if (original != null && original.optJSONObject("data") != null) {
            try {
                remember(context, retiredIds(new JSONArray(original.getJSONObject("data")
                        .optString("interests", "[]"))));
            } catch (Exception invalid) {
                throw new IllegalArgumentException("Backup inválido", invalid);
            }
        }
        return sanitizeBackup(original);
    }

    static Set<Long> retiredIds(JSONArray interests) {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < interests.length(); i++) {
            JSONObject item = interests.optJSONObject(i);
            if (item != null && !Interest.isSupportedType(item.optString("type", Interest.TYPE_PRICE))) {
                ids.add(item.optLong("id"));
            }
        }
        return ids;
    }

    static JSONArray clean(JSONArray items, Set<Long> ids, boolean interests) {
        JSONArray result = new JSONArray();
        for (int i = 0; items != null && i < items.length(); i++) {
            Object value = items.opt(i);
            if (value instanceof JSONObject) {
                JSONObject item = (JSONObject) value;
                if (ids.contains(item.optLong(interests ? "id" : "interest_id", 0L))) continue;
                if (interests && !Interest.isSupportedType(item.optString("type", Interest.TYPE_PRICE))) continue;
                if (interests) {
                    for (String key : new String[]{"property_zip_code", "property_street",
                            "property_neighborhood", "property_city", "property_state", "property_zip_listing_limit"}) {
                        item.remove(key);
                    }
                }
            }
            result.put(value);
        }
        return result;
    }

    static void run(Context context) {
        SharedPreferences metadata = context.getSharedPreferences("retired_alert_cleanup", Context.MODE_PRIVATE);
        if (metadata.getBoolean("completed", false)) return;
        SharedPreferences offers = context.getSharedPreferences("offer_preferences", Context.MODE_PRIVATE);
        try {
            JSONArray interests = new JSONArray(offers.getString("interests", "[]"));
            Set<Long> ids = retiredIds(interests);
            remember(context, ids);
            SharedPreferences.Editor editor = offers.edit()
                    .putString("interests", clean(interests, ids, true).toString());
            for (String key : new String[]{"recent_offers", "archived_offers", "trashed_offers"}) {
                editor.putString(key, clean(new JSONArray(offers.getString(key, "[]")), ids, false).toString());
            }
            JSONArray processed = new JSONArray(offers.getString("processed_messages", "[]"));
            JSONArray kept = new JSONArray();
            for (int i = 0; i < processed.length(); i++) {
                String item = processed.optString(i);
                boolean retired = false;
                for (Long id : ids) retired |= item.endsWith(":" + id);
                if (!retired) kept.put(item);
            }
            editor.putString("processed_messages", kept.toString()).commit();
            SharedPreferences history = context.getSharedPreferences("property_history", Context.MODE_PRIVATE);
            SharedPreferences.Editor h = history.edit();
            for (String key : new String[]{"entries", "market_listing_counts"}) {
                h.putString(key, clean(new JSONArray(history.getString(key, "[]")), ids, false).toString());
            }
            h.commit();
            for (String file : new String[]{"offer_preferences", "property_market_reference",
                    "property_page_monitor", "property_market_check_status", "source_check_status",
                    "property_market_winners", "cloud_sync_preferences", "source_settings_sync"}) {
                SharedPreferences prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE);
                SharedPreferences.Editor changes = prefs.edit();
                for (String key : prefs.getAll().keySet()) {
                    boolean remove = key.toLowerCase(java.util.Locale.ROOT).contains("zip");
                    for (Long id : ids) remove |= key.contains(Long.toString(id));
                    if (remove) changes.remove(key);
                }
                if (file.equals("source_settings_sync")) changes.remove("snapshot");
                changes.commit();
            }
            metadata.edit().putBoolean("completed", true).apply();
        } catch (Exception error) {
            AppErrorStore.recordSerious(context, "Limpeza de alertas antigos", error.getMessage());
        }
    }

    static JSONObject sanitizeBackup(JSONObject original) {
        if (original == null) return null;
        try {
            JSONObject backup = new JSONObject(original.toString());
            JSONObject data = backup.optJSONObject("data");
            if (data == null) return backup;
            JSONArray interests = new JSONArray(data.optString("interests", "[]"));
            Set<Long> ids = retiredIds(interests);
            data.put("interests", clean(interests, ids, true).toString());
            JSONObject settings = data.optJSONObject(SettingsBackup.KEY);
            if (settings != null) {
                java.util.List<String> removed = new java.util.ArrayList<>();
                java.util.Iterator<String> keys = settings.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.toLowerCase(java.util.Locale.ROOT).contains("zip")) removed.add(key);
                }
                for (String key : removed) settings.remove(key);
            }
            for (String key : new String[]{"recent_offers", "archived_offers", "trashed_offers"}) {
                if (data.has(key)) data.put(key, clean(new JSONArray(data.optString(key, "[]")), ids, false).toString());
            }
            if (data.has("property_history")) data.put("property_history", PropertyHistorySync.pack(
                    clean(PropertyHistorySync.unpack(data.opt("property_history")), ids, false)));
            return backup;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Backup inválido", invalid);
        }
    }
}
