package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Iterator;

/** Only user preferences belong in this snapshot; session, caches and check times stay local. */
final class SettingsBackup {
    static final String KEY = "source_settings";
    private static final String META = "source_settings_sync";
    private static final String[] EXTERNAL_KEYS = {"vivo_outlet_url", "pelando_url", "promobit_url",
            "kabum_offer_url", "vivo_outlet_check_interval_minutes", "vivo_madrugada_check_interval_minutes", "pelando_check_interval_seconds",
            "promobit_check_interval_seconds", "kabum_offer_check_interval_seconds"};
    private static final String[] PROPERTY_KEYS = {"enabled", "check_interval_minutes"};

    private SettingsBackup() { }

    static void changed(Context context) {
        SharedPreferences metadata = context.getSharedPreferences(META, Context.MODE_PRIVATE);
        String before = metadata.getString("snapshot", "{}");
        JSONObject after = export(context);
        if (!before.equals(after.toString())) CloudSyncStore.markLocalChanged(context);
    }

    interface Preferences { SharedPreferences get(String name); }

    static JSONObject export(Context context) {
        return export(name -> context.getSharedPreferences(name, Context.MODE_PRIVATE));
    }

    static synchronized JSONObject export(Preferences files) {
        SharedPreferences metadata = files.get(META);
        JSONObject old = parse(metadata.getString("snapshot", "{}"));
        JSONObject result = new JSONObject();
        try {
            capture(files, result, old, "external_offer_sources", EXTERNAL_KEYS);
            capture(files, result, old, "property_market_reference", PROPERTY_KEYS);
            metadata.edit().putString("snapshot", result.toString()).apply();
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível preparar as configurações.", exception);
        }
    }

    private static void capture(Preferences files, JSONObject result, JSONObject old,
                                String file, String[] keys) throws Exception {
        SharedPreferences preferences = files.get(file);
        for (String key : keys) {
            String id = file + "/" + key;
            Object value = preferences.getAll().get(key);
            JSONObject previous = old.optJSONObject(id);
            if (value == null && previous == null) continue;
            if (value == null) value = JSONObject.NULL;
            boolean same = previous != null && String.valueOf(value).equals(
                    String.valueOf(previous.opt("value")));
            result.put(id, new JSONObject().put("value", value).put("updated_at", same
                    ? previous.optLong("updated_at")
                    : Math.max(System.currentTimeMillis(), previous == null ? 1 : previous.optLong("updated_at") + 1)));
        }
    }

    static boolean restore(Context context, JSONObject incoming, boolean force) {
        return restore(name -> context.getSharedPreferences(name, Context.MODE_PRIVATE), incoming, force);
    }

    static synchronized boolean restore(Preferences files, JSONObject incoming, boolean force) {
        if (incoming == null) return false; // Backward-compatible with older backups.
        JSONObject local = export(files);
        JSONObject merged = merge(local, incoming, force);
        if (merged.toString().equals(local.toString())) return false;
        apply(files, merged, "external_offer_sources", EXTERNAL_KEYS);
        apply(files, merged, "property_market_reference", PROPERTY_KEYS);
        files.get(META).edit()
                .putString("snapshot", merged.toString()).apply();
        return true;
    }

    static JSONObject merge(JSONObject local, JSONObject incoming, boolean force) {
        JSONObject merged = parse(local.toString());
        Iterator<String> ids = incoming.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            JSONObject value = incoming.optJSONObject(id);
            JSONObject previous = local.optJSONObject(id);
            if (value == null || !valid(id, value.opt("value")) || value.optLong("updated_at") <= 0) continue;
            if (force || previous == null || value.optLong("updated_at") > previous.optLong("updated_at")) {
                try { merged.put(id, value); } catch (Exception impossible) { throw new IllegalStateException(impossible); }
            }
        }
        return merged;
    }

    private static boolean valid(String id, Object value) {
        boolean external = id.startsWith("external_offer_sources/");
        boolean property = id.startsWith("property_market_reference/");
        String key = id.substring(id.indexOf('/') + 1);
        if (!(external && java.util.Arrays.asList(EXTERNAL_KEYS).contains(key))
                && !(property && java.util.Arrays.asList(PROPERTY_KEYS).contains(key))) return false;
        if (value == JSONObject.NULL) return true;
        if (key.equals("enabled")) return value instanceof Boolean;
        if (key.endsWith("_url")) {
            if (!(value instanceof String)) return false;
            String url = (String) value;
            if (url.isEmpty()) return true;
            switch (key) {
                case "vivo_outlet_url": return VivoOutletSource.normalizeUrl(url) != null;
                case "pelando_url": return PelandoSource.normalizeUrl(url) != null;
                case "promobit_url": return PromobitSource.normalizeUrl(url) != null;
                case "kabum_offer_url": return KabumOfferSource.normalizeUrl(url) != null;
                default: return false;
            }
        }
        if (!(value instanceof Number)) return false;
        int interval = ((Number) value).intValue();
        if (interval != ((Number) value).doubleValue()) return false;
        switch (key) {
            case "check_interval_minutes": return PropertyMarketReferenceSettings.isSupportedCheckInterval(interval);
            case "vivo_outlet_check_interval_minutes": return VivoOutletSource.isSupportedCheckInterval(interval);
            case "vivo_madrugada_check_interval_minutes": return VivoMadrugadaSource.isSupportedCheckInterval(interval);
            case "pelando_check_interval_seconds": return PelandoSource.isSupportedCheckInterval(interval);
            case "promobit_check_interval_seconds": return PromobitSource.isSupportedCheckInterval(interval);
            case "kabum_offer_check_interval_seconds": return KabumOfferSource.isSupportedCheckInterval(interval);
            default: return false;
        }
    }

    private static void apply(Preferences files, JSONObject snapshot, String file, String[] keys) {
        SharedPreferences.Editor editor = files.get(file).edit();
        for (String key : keys) {
            JSONObject item = snapshot.optJSONObject(file + "/" + key);
            if (item == null) continue;
            Object value = item.opt("value");
            if (value == JSONObject.NULL) editor.remove(key);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Number) editor.putInt(key, ((Number) value).intValue());
            else if (value instanceof String) editor.putString(key, (String) value);
        }
        editor.apply();
    }

    private static JSONObject parse(String text) {
        try { return new JSONObject(text); } catch (Exception invalid) { return new JSONObject(); }
    }
}
