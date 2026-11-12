package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Iterator;

/** Only user preferences belong in this snapshot; session, caches and check times stay local. */
final class SettingsBackup {
    static final String KEY = "source_settings";
    private static final String META = "source_settings_sync";
    private static final String[] EXTERNAL_KEYS = {"vivo_outlet_url", "vivo_madrugada_url", "pelando_url",
            "promobit_url", "kabum_offer_url", "motorola_offer_url", "motorola_offer_title", "claro_offer_url", "claro_offer_title",
            "samsung_offer_url", "samsung_offer_title", "samsung_discount_offer_url", "samsung_discount_offer_product_api_url",
            "samsung_discount_offer_title", "vivo_outlet_check_interval_minutes", "vivo_madrugada_check_interval_minutes",
            "vivo_outlet_check_interval_seconds", "vivo_madrugada_check_interval_seconds",
            "motorola_offer_check_interval_seconds", "claro_offer_check_interval_seconds", "samsung_offer_check_interval_seconds", "pelando_check_interval_seconds",
            "promobit_check_interval_seconds", "kabum_offer_check_interval_seconds",
            "samsung_discount_offer_check_interval_seconds", StoreSourceControl.KEY_DISABLED_SOURCES_MASK};
    private static final String[] PROPERTY_KEYS = {"enabled", "show_condominium", "show_zip",
            "check_interval_minutes", "check_interval_seconds", "zip_check_interval_seconds"};
    private static final String[] TREASURY_KEYS = {"interval_seconds"};
    private static final String[] TELEGRAM_KEYS = {"groups_sort_order"};
    private static final String[] ALERT_KEYS = {"alerts_sort_order", "home_sort_order",
            "alerts_section_coupons_expanded", "alerts_section_properties_expanded",
            "alerts_section_property_zip_expanded", "alerts_section_treasury_expanded",
            "alerts_section_products_expanded", "home_section_coupons_expanded",
            "home_section_property_market_expanded", "home_section_properties_expanded",
            "home_section_property_zip_expanded", "home_section_treasury_expanded",
            "home_section_products_expanded"};
    private static final String[] APPEARANCE_KEYS = {"theme_mode", "accent_color",
            "navigation_animation"};
    private static final String[] PROFILE_KEYS = {"appearance_expanded", "properties_expanded",
            "treasury_expanded", "external_sources_expanded", "sync_expanded", "info_expanded"};
    private static final String[] CLOUD_SYNC_KEYS = {"automatic_sync_enabled"};
    private static final String[] STORE_DISPLAY_KEYS = {
            "title_" + R.string.vivo_outlet_source_title,
            "title_" + R.string.vivo_madrugada_source_title,
            "title_" + R.string.pelando_source_title,
            "title_" + R.string.promobit_source_title,
            "title_" + R.string.kabum_offer_source_title
    };

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
            capture(files, result, old, "treasury_direct_settings", TREASURY_KEYS);
            capture(files, result, old, "telegram_preferences", TELEGRAM_KEYS);
            capture(files, result, old, "offer_preferences", ALERT_KEYS);
            capture(files, result, old, "app_preferences", APPEARANCE_KEYS);
            capture(files, result, old, "profile_preferences", PROFILE_KEYS);
            capture(files, result, old, "cloud_sync_preferences", CLOUD_SYNC_KEYS);
            capture(files, result, old, "store_display_names", STORE_DISPLAY_KEYS);
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
        apply(files, merged, "treasury_direct_settings", TREASURY_KEYS);
        apply(files, merged, "telegram_preferences", TELEGRAM_KEYS);
        apply(files, merged, "offer_preferences", ALERT_KEYS);
        apply(files, merged, "app_preferences", APPEARANCE_KEYS);
        apply(files, merged, "profile_preferences", PROFILE_KEYS);
        apply(files, merged, "cloud_sync_preferences", CLOUD_SYNC_KEYS);
        apply(files, merged, "store_display_names", STORE_DISPLAY_KEYS);
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
        boolean treasury = id.startsWith("treasury_direct_settings/");
        boolean telegram = id.startsWith("telegram_preferences/");
        boolean alerts = id.startsWith("offer_preferences/");
        boolean appearance = id.startsWith("app_preferences/");
        boolean profile = id.startsWith("profile_preferences/");
        boolean cloudSync = id.startsWith("cloud_sync_preferences/");
        boolean display = id.startsWith("store_display_names/");
        String key = id.substring(id.indexOf('/') + 1);
        if (!(external && java.util.Arrays.asList(EXTERNAL_KEYS).contains(key))
                && !(property && java.util.Arrays.asList(PROPERTY_KEYS).contains(key))
                && !(treasury && java.util.Arrays.asList(TREASURY_KEYS).contains(key))
                && !(telegram && java.util.Arrays.asList(TELEGRAM_KEYS).contains(key))
                && !(alerts && java.util.Arrays.asList(ALERT_KEYS).contains(key))
                && !(appearance && java.util.Arrays.asList(APPEARANCE_KEYS).contains(key))
                && !(profile && java.util.Arrays.asList(PROFILE_KEYS).contains(key))
                && !(cloudSync && java.util.Arrays.asList(CLOUD_SYNC_KEYS).contains(key))
                && !(display && java.util.Arrays.asList(STORE_DISPLAY_KEYS).contains(key))) return false;
        if (value == JSONObject.NULL) return true;
        if (display) {
            return value instanceof String && !((String) value).trim().isEmpty()
                    && ((String) value).trim().length() <= 40;
        }
        if (telegram) {
            return value instanceof Number && ((Number) value).intValue() >= 0
                    && ((Number) value).intValue() <= 2
                    && ((Number) value).intValue() == ((Number) value).doubleValue();
        }
        if (alerts) {
            if (key.endsWith("_expanded")) return value instanceof Boolean;
            int maximum = key.equals("alerts_sort_order") ? 3 : 5;
            return value instanceof Number && ((Number) value).intValue() >= 0
                    && ((Number) value).intValue() <= maximum
                    && ((Number) value).intValue() == ((Number) value).doubleValue();
        }
        if (profile) return value instanceof Boolean;
        if (cloudSync) return value instanceof Boolean;
        if (appearance) {
            if (!(value instanceof String)) return false;
            String mode = (String) value;
            if (key.equals("theme_mode")) {
                return ThemeController.MODE_LIGHT.equals(mode) || ThemeController.MODE_DARK.equals(mode);
            }
            return mode.length() <= 32 && !mode.trim().isEmpty();
        }
        if (key.equals("enabled") || key.equals("show_condominium") || key.equals("show_zip")) {
            return value instanceof Boolean;
        }
        if (key.equals(StoreSourceControl.KEY_DISABLED_SOURCES_MASK)) {
            return value instanceof Number && ((Number) value).intValue() >= 0
                    && ((Number) value).intValue() <= 1023
                    && ((Number) value).intValue() == ((Number) value).doubleValue();
        }
        if (key.endsWith("_title")) {
            return value instanceof String && !((String) value).trim().isEmpty()
                    && ((String) value).trim().length() <= 40;
        }
        if (key.endsWith("_url")) {
            if (!(value instanceof String)) return false;
            String url = (String) value;
            if (url.isEmpty()) return true;
            switch (key) {
                case "vivo_outlet_url": return VivoOutletSource.normalizeUrl(url) != null;
                case "vivo_madrugada_url": return VivoMadrugadaSource.normalizeUrl(url) != null;
                case "pelando_url": return PelandoSource.normalizeUrl(url) != null;
                case "promobit_url": return PromobitSource.normalizeUrl(url) != null;
                case "kabum_offer_url": return KabumOfferSource.normalizeUrl(url) != null;
                case "motorola_offer_url": return MotorolaOfferSource.normalizeUrl(url) != null;
                case "claro_offer_url": return ClaroOfferSource.normalizeUrl(url) != null;
                case "samsung_offer_url": return SamsungOfferSource.normalizeUrl(url) != null;
                case "samsung_discount_offer_url": return SamsungDiscountOfferSource.normalizeUrl(url) != null;
                case "samsung_discount_offer_product_api_url": return SamsungDiscountOfferSource.normalizeProductApiUrl(url) != null;
                default: return false;
            }
        }
        if (!(value instanceof Number)) return false;
        int interval = ((Number) value).intValue();
        if (interval != ((Number) value).doubleValue()) return false;
        switch (key) {
            case "check_interval_minutes": return PropertyMarketReferenceSettings.isSupportedCheckInterval(interval);
            case "check_interval_seconds": return PropertyMarketReferenceSettings.isSupportedCheckIntervalSeconds(interval);
            case "zip_check_interval_seconds": return PropertyMarketReferenceSettings.isSupportedCheckIntervalSeconds(interval);
            case "interval_seconds": return interval >= 30 && interval <= 86400;
            case "vivo_outlet_check_interval_minutes": return VivoOutletSource.isSupportedCheckInterval(interval);
            case "vivo_madrugada_check_interval_minutes": return VivoMadrugadaSource.isSupportedCheckInterval(interval);
            case "vivo_outlet_check_interval_seconds": return VivoOutletSource.isSupportedCheckIntervalSeconds(interval);
            case "vivo_madrugada_check_interval_seconds": return VivoMadrugadaSource.isSupportedCheckIntervalSeconds(interval);
            case "motorola_offer_check_interval_seconds": return MotorolaOfferSource.isSupportedCheckIntervalSeconds(interval);
            case "claro_offer_check_interval_seconds": return ClaroOfferSource.isSupportedCheckIntervalSeconds(interval);
            case "samsung_offer_check_interval_seconds": return SamsungOfferSource.isSupportedCheckIntervalSeconds(interval);
            case "samsung_discount_offer_check_interval_seconds": return SamsungOfferSource.isSupportedCheckIntervalSeconds(interval);
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
