package br.com.droidboaoferta;

import android.content.SharedPreferences;
import org.json.JSONObject;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class SettingsBackupTest {
    @Test public void fontSizesAndFamiliesRoundTripAndRejectInvalidValues() throws Exception {
        SettingsBackup.Preferences source = files();
        SettingsBackup.Preferences destination = files();
        for (String family : FontController.MODES) {
            for (int percent = FontSizeController.MIN; percent <= FontSizeController.MAX; percent++) {
                source.get("app_preferences").edit().putString(FontController.KEY, family)
                        .putInt(FontSizeController.KEY, percent).apply();
                assertTrue(SettingsBackup.restore(destination, SettingsBackup.export(source), true));
                assertEquals(family, destination.get("app_preferences").getString(FontController.KEY, ""));
                assertEquals(percent, destination.get("app_preferences").getInt(FontSizeController.KEY, 0));
            }
        }
        assertFalse(FontSizeController.isSupported(0));
        assertFalse(FontSizeController.isSupported(500));
        assertFalse(SettingsBackup.restore(destination, new JSONObject()
                .put("app_preferences/font_size_percent", value(500)), true));
        assertEquals(FontSizeController.MAX, destination.get("app_preferences").getInt(FontSizeController.KEY, 0));
    }
    @Test public void fontChoiceRoundTripsAndRejectsUnsupportedFamilies() throws Exception {
        SettingsBackup.Preferences source = files();
        source.get("app_preferences").edit().putString("font_family", "condensed").apply();
        SettingsBackup.Preferences destination = files();
        assertTrue(SettingsBackup.restore(destination, SettingsBackup.export(source), true));
        assertEquals("condensed", destination.get("app_preferences").getString("font_family", ""));
        assertFalse(SettingsBackup.restore(destination, new JSONObject()
                .put("app_preferences/font_family", value("unknown-font")), true));
        assertEquals("condensed", destination.get("app_preferences").getString("font_family", ""));
        assertFalse(SettingsBackup.restore(destination, null, true));
    }
    @Test public void cashbackSettingsRoundTripAndInvalidBackupValues() throws Exception {
        SettingsBackup.Preferences source = files();
        source.get("meliuz_cashback_settings").edit().putBoolean("enabled", true)
                .putString("minimum", "12.5").putInt("interval_seconds", 30).putBoolean("paused", true).putBoolean("alerts_expanded", false).apply();
        SettingsBackup.Preferences destination = files();
        assertTrue(SettingsBackup.restore(destination, SettingsBackup.export(source), true));
        assertTrue(destination.get("meliuz_cashback_settings").getBoolean("enabled", false));
        assertEquals("12.5", destination.get("meliuz_cashback_settings").getString("minimum", ""));
        assertEquals(30, destination.get("meliuz_cashback_settings").getInt("interval_seconds", 0));
        assertTrue(destination.get("meliuz_cashback_settings").getBoolean("paused", false));
        assertFalse(destination.get("meliuz_cashback_settings").getBoolean("alerts_expanded", true));
        JSONObject invalid = new JSONObject().put("meliuz_cashback_settings/minimum", value("NaN"))
                .put("meliuz_cashback_settings/interval_seconds", value(1));
        assertFalse(SettingsBackup.restore(destination, invalid, true));
        assertEquals("12.5", destination.get("meliuz_cashback_settings").getString("minimum", ""));
    }
    @Test public void roundTripRestoresAllSourceSettingsWithoutRuntimeDataOrEcho() {
        SettingsBackup.Preferences source = files();
        source.get("external_offer_sources").edit().putString("pelando_url", PelandoSource.DEFAULT_URL)
                .putString("promobit_url", PromobitSource.DEFAULT_URL)
                .putString("vivo_outlet_url", VivoOutletSource.DEFAULT_URL)
                .putString("kabum_offer_url", KabumOfferSource.DEFAULT_URL)
                .putInt("pelando_check_interval_seconds", 30)
                .putInt("promobit_check_interval_seconds", 30)
                .putInt("vivo_outlet_check_interval_minutes", 15)
                .putInt("kabum_offer_check_interval_seconds", 60)
                .putLong("pelando_last_success", 123).apply();
        source.get("property_market_reference").edit().putBoolean("enabled", true)
                .putInt("check_interval_minutes", 30).apply();
        JSONObject backup = SettingsBackup.export(source);
        assertEquals(10, backup.length());
        assertFalse(backup.toString().contains("last_success"));
        SettingsBackup.Preferences destination = files();
        assertTrue(SettingsBackup.restore(destination, backup, true));
        assertEquals(backup.toString(), SettingsBackup.export(destination).toString());
        assertFalse(SettingsBackup.restore(destination, backup, false));
        assertTrue(destination.get("property_market_reference").getBoolean("enabled", false));
        assertEquals(30, destination.get("property_market_reference").getInt("check_interval_minutes", 0));
        assertFalse(destination.get("external_offer_sources").contains("pelando_last_success"));
    }
    @Test public void oldBackupDoesNotEraseSettings() {
        SettingsBackup.Preferences files = files();
        files.get("property_market_reference").edit().putBoolean("enabled", true).apply();
        assertFalse(SettingsBackup.restore(files, null, true));
        assertTrue(files.get("property_market_reference").getBoolean("enabled", false));
    }

    @Test public void restoresTreasuryIntervalSeparatelyFromRuntimeData() throws Exception {
        JSONObject incoming = new JSONObject().put("treasury_direct_settings/interval_seconds",
                value(900));
        SettingsBackup.Preferences destination = files();
        assertTrue(SettingsBackup.restore(destination, incoming, true));
        assertEquals(900, destination.get("treasury_direct_settings")
                .getInt("interval_seconds", 0));
    }
    @Test public void roundTripRestoresStoreOrderingAndSamsungRegistration() {
        SettingsBackup.Preferences source = files();
        source.get("external_offer_sources").edit()
                .putString("samsung_offer_url", SamsungOfferSource.DEFAULT_URL)
                .putString("samsung_offer_title", "Samsung Promo")
                .putInt("samsung_offer_check_interval_seconds", 900)
                .putString("samsung_discount_offer_url", SamsungDiscountOfferSource.DEFAULT_URL)
                .putString("samsung_discount_offer_title", "Samsung Desconto")
                .putInt("samsung_discount_offer_check_interval_seconds", 1800)
                .apply();
        source.get("store_display_names").edit()
                .putString("title_" + R.string.pelando_source_title, "Pelando Oficial")
                .apply();
        source.get("telegram_preferences").edit().putInt("groups_sort_order", 2).apply();

        SettingsBackup.Preferences destination = files();
        assertTrue(SettingsBackup.restore(destination, SettingsBackup.export(source), true));
        SharedPreferences external = destination.get("external_offer_sources");
        assertEquals(SamsungOfferSource.DEFAULT_URL, external.getString("samsung_offer_url", ""));
        assertEquals("Samsung Promo", external.getString("samsung_offer_title", ""));
        assertEquals(900, external.getInt("samsung_offer_check_interval_seconds", 0));
        assertEquals(SamsungDiscountOfferSource.DEFAULT_URL,
                external.getString("samsung_discount_offer_url", ""));
        assertEquals("Samsung Desconto", external.getString("samsung_discount_offer_title", ""));
        assertEquals(1800, external.getInt("samsung_discount_offer_check_interval_seconds", 0));
        assertEquals("Pelando Oficial", destination.get("store_display_names")
                .getString("title_" + R.string.pelando_source_title, ""));
        assertEquals(2, destination.get("telegram_preferences").getInt("groups_sort_order", -1));
    }
    @Test public void newerLocalValueWinsUnlessRestoreWasExplicit() throws Exception {
        String key = "property_market_reference/enabled";
        JSONObject local = new JSONObject().put(key, new JSONObject().put("value", true).put("updated_at", 200));
        JSONObject remote = new JSONObject().put(key, new JSONObject().put("value", false).put("updated_at", 100));
        assertTrue(SettingsBackup.merge(local, remote, false).getJSONObject(key).getBoolean("value"));
        assertFalse(SettingsBackup.merge(local, remote, true).getJSONObject(key).getBoolean("value"));
    }

    @Test public void ignoresRetiredSettingsWhileRestoringCondominiumVisibility() throws Exception {
        JSONObject incoming = new JSONObject()
                .put("property_market_reference/show_condominium", value(false))
                .put("property_market_reference/show_zip", value(true))
                .put("property_market_reference/zip_check_interval_seconds", value(1800));
        JSONObject merged = SettingsBackup.merge(new JSONObject(), incoming, true);
        assertEquals(1, merged.length());
        SettingsBackup.Preferences destination = files();
        assertTrue(SettingsBackup.restore(destination, merged, true));
        assertFalse(destination.get("property_market_reference")
                .getBoolean("show_condominium", true));
        assertFalse(destination.get("property_market_reference").contains("show_zip"));
        assertFalse(destination.get("property_market_reference").contains("zip_check_interval_seconds"));
    }
    @Test public void acceptsManuallyConfiguredUrlsButRejectsUnknownFieldsAndUnsupportedIntervals() throws Exception {
        JSONObject incoming = new JSONObject()
                .put("external_offer_sources/pelando_url", value("https://example.com"))
                .put("external_offer_sources/pelando_check_interval_seconds", value(13))
                .put("telegram_preferences/session", value("private"));
        assertEquals(1, SettingsBackup.merge(new JSONObject(), incoming, true).length());
    }
    private JSONObject value(Object value) throws Exception {
        return new JSONObject().put("value", value).put("updated_at", 100);
    }
    private SettingsBackup.Preferences files() {
        Map<String, SharedPreferences> files = new HashMap<>();
        return name -> files.computeIfAbsent(name, ignored -> TestPreferences.create());
    }
}
