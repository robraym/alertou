package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RetiredAlertCleanupTest {
    private JSONObject alert(long id, String type) throws Exception {
        return new JSONObject().put("id", id).put("type", type)
                .put("term", "https://www.quintoandar.com.br/condominio/teste-id")
                .put("maximum_price", 400000);
    }

    @Test public void oldBackupCannotRestoreRetiredAlertsOrTheirOffersAndHistory() throws Exception {
        JSONArray alerts = new JSONArray().put(alert(1, "property_zip"))
                .put(alert(2, Interest.TYPE_PROPERTY));
        JSONArray offers = new JSONArray().put(new JSONObject().put("interest_id", 1))
                .put(new JSONObject().put("interest_id", 2));
        JSONObject data = new JSONObject().put("interests", alerts.toString())
                .put(SettingsBackup.KEY, new JSONObject()
                        .put("property_market_reference/show_zip", true)
                        .put("property_market_reference/show_condominium", true));
        for (String key : new String[]{"recent_offers", "archived_offers", "trashed_offers"}) {
            data.put(key, offers.toString());
        }
        data.put("property_history", PropertyHistorySync.pack(new JSONArray()
                .put(PropertyHistorySyncTest.entry(1, "123", 100,
                        PropertyHistorySyncTest.point(100, 350000)))
                .put(PropertyHistorySyncTest.entry(2, "456", 100,
                        PropertyHistorySyncTest.point(100, 400000)))));
        JSONObject original = new JSONObject().put("data", data);
        JSONObject restored = RetiredAlertCleanup.sanitizeBackup(original).getJSONObject("data");
        JSONArray kept = new JSONArray(restored.getString("interests"));
        assertEquals(1, kept.length());
        assertEquals(2, kept.getJSONObject(0).getLong("id"));
        for (String key : new String[]{"recent_offers", "archived_offers", "trashed_offers"}) {
            assertEquals(2, new JSONArray(restored.getString(key)).getJSONObject(0).getLong("interest_id"));
        }
        assertFalse(restored.getJSONObject(SettingsBackup.KEY).has("property_market_reference/show_zip"));
        assertTrue(restored.getJSONObject(SettingsBackup.KEY).has("property_market_reference/show_condominium"));
        JSONArray history = PropertyHistorySync.unpack(restored.opt("property_history"));
        assertEquals(1, history.length());
        assertEquals(2, history.getJSONObject(0).getLong("interest_id"));
        assertEquals(2, new JSONArray(original.getJSONObject("data").getString("interests")).length());
    }

    @Test public void oldDeltaCannotRecreateRemovedCategory() throws Exception {
        JSONObject payload = new JSONObject().put("interest_id", 1)
                .put("fields", alert(1, "property_zip"));
        assertEquals(0, CloudSyncStore.applyInterestConfigurationDelta("[]", payload).length());
    }

    @Test public void retiredTypeIsNeverConvertedIntoAProductAlert() {
        assertFalse(Interest.isSupportedType("property_zip"));
        try {
            new Interest(1, "old", 10, "property_zip");
            fail("Retired type accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
