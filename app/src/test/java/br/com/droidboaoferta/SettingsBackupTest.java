package br.com.droidboaoferta;

import android.content.SharedPreferences;
import org.json.JSONObject;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class SettingsBackupTest {
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
    @Test public void newerLocalValueWinsUnlessRestoreWasExplicit() throws Exception {
        String key = "property_market_reference/enabled";
        JSONObject local = new JSONObject().put(key, new JSONObject().put("value", true).put("updated_at", 200));
        JSONObject remote = new JSONObject().put(key, new JSONObject().put("value", false).put("updated_at", 100));
        assertTrue(SettingsBackup.merge(local, remote, false).getJSONObject(key).getBoolean("value"));
        assertFalse(SettingsBackup.merge(local, remote, true).getJSONObject(key).getBoolean("value"));
    }
    @Test public void rejectsUntrustedUrlsUnknownFieldsAndUnsupportedIntervals() throws Exception {
        JSONObject incoming = new JSONObject()
                .put("external_offer_sources/pelando_url", value("https://example.com"))
                .put("external_offer_sources/pelando_check_interval_seconds", value(13))
                .put("telegram_preferences/session", value("private"));
        assertEquals(0, SettingsBackup.merge(new JSONObject(), incoming, true).length());
    }
    private JSONObject value(Object value) throws Exception {
        return new JSONObject().put("value", value).put("updated_at", 100);
    }
    private SettingsBackup.Preferences files() {
        Map<String, SharedPreferences> files = new HashMap<>();
        return name -> files.computeIfAbsent(name, ignored -> TestPreferences.create());
    }
}
