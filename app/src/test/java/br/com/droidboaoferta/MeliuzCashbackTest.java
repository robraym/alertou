package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class MeliuzCashbackTest {
    private JSONObject partner(long id) throws Exception {
        return new JSONObject().put("id", id).put("name", "Loja " + id).put("uri", "cupom-loja-" + id);
    }

    private JSONObject offer(long id, double amount, boolean category) throws Exception {
        return new JSONObject().put("partner_id", id).put("cashback_category", category)
                .put("cashback", new JSONObject().put("amount", amount).put("type", "percentage"));
    }

    private JSONObject store(long id, double amount) throws Exception {
        return partner(id).put("amount", amount).put("type", "percentage").put("category", false);
    }

    @Test public void readsActualFeedShapePreservingDecimalsAndCategoryCeiling() throws Exception {
        JSONArray partners = new JSONArray().put(partner(1142)).put(partner(737));
        JSONObject feed = new JSONObject().put("offers", new JSONArray()
                .put(offer(737, 8.5, false)).put(offer(1142, 18, true)
                        .put("previous", new JSONObject().put("amount", 2).put("type", "percentage"))));
        List<String> urls = new ArrayList<>();
        JSONArray stores = MeliuzCashbackClient.fetch(url -> {
            urls.add(url);
            return url.equals(MeliuzCashbackClient.FEATURED_URL) ? partners.toString() : feed.toString();
        });
        assertEquals(2, urls.size());
        assertEquals("https://www.meliuz.com.br/v3/api/home/cashback?ids=1142,737", urls.get(1));
        assertEquals(18, stores.getJSONObject(0).getDouble("amount"), 0);
        assertTrue(stores.getJSONObject(0).getBoolean("category"));
        assertEquals(8.5, stores.getJSONObject(1).getDouble("amount"), 0);
        assertFalse(stores.getJSONObject(1).getBoolean("category"));
    }

    @Test public void batchesAtFiftyStoresRatherThanOneRequestPerStore() throws Exception {
        JSONArray partners = new JSONArray();
        for (int i = 1; i <= 51; i++) partners.put(partner(i));
        List<String> urls = new ArrayList<>();
        JSONArray stores = MeliuzCashbackClient.fetch(url -> {
            urls.add(url);
            if (url.equals(MeliuzCashbackClient.FEATURED_URL)) return partners.toString();
            JSONArray offers = new JSONArray();
            for (String id : url.substring(url.indexOf('=') + 1).split(",")) {
                offers.put(offer(Long.parseLong(id), 10, false));
            }
            return new JSONObject().put("offers", offers).toString();
        });
        assertEquals(51, stores.length());
        assertEquals(3, urls.size());
        assertTrue(urls.get(2).endsWith("ids=51"));
    }

    @Test public void missingStoreIsAnErrorRatherThanZeroCashback() throws Exception {
        assertThrows(IOException.class, () -> MeliuzCashbackClient.fetch(url ->
                url.equals(MeliuzCashbackClient.FEATURED_URL)
                        ? new JSONArray().put(partner(1)).put(partner(2)).toString()
                        : new JSONObject().put("offers", new JSONArray().put(offer(1, 10, false))).toString()));
    }

    @Test public void rejectsDuplicateStoresMalformedAmountsAndExternalLinks() throws Exception {
        assertThrows(IOException.class, () -> MeliuzCashbackClient.fetch(url ->
                new JSONArray().put(partner(1)).put(partner(1)).toString()));
        assertThrows(IOException.class, () -> MeliuzCashbackClient.fetch(url ->
                new JSONArray().put(partner(1).put("uri", "https://example.com")).toString()));
        assertThrows(IOException.class, () -> MeliuzCashbackClient.fetch(url ->
                url.equals(MeliuzCashbackClient.FEATURED_URL) ? new JSONArray().put(partner(1)).toString()
                        : new JSONObject().put("offers", new JSONArray().put(offer(1, 101, false))).toString()));
    }

    @Test public void explicitNullIsNotConfusedWithMissingCashback() throws Exception {
        JSONArray result = MeliuzCashbackClient.fetch(url ->
                url.equals(MeliuzCashbackClient.FEATURED_URL) ? new JSONArray().put(partner(1)).toString()
                        : new JSONObject().put("offers", new JSONArray().put(new JSONObject()
                        .put("partner_id", 1).put("cashback", JSONObject.NULL))).toString());
        assertFalse(MeliuzCashbackClient.qualifies(result.getJSONObject(0), 0));
        assertEquals("none", result.getJSONObject(0).getString("type"));
    }

    @Test public void percentageFilterIncludesExactMinimumButNeverFixedMoneyOrZero() throws Exception {
        assertTrue(MeliuzCashbackClient.qualifies(store(1, 10), 10));
        assertFalse(MeliuzCashbackClient.qualifies(store(1, 9.99), 10));
        assertFalse(MeliuzCashbackClient.qualifies(store(1, 0), 0));
        assertFalse(MeliuzCashbackClient.qualifies(store(1, 100).put("type", "currency"), 10));
    }

    @Test public void notificationRequiresCrossingThresholdOrIncrease() throws Exception {
        assertTrue(MeliuzCashbackClient.shouldNotify(null, 10, store(1, 10), 10));
        assertFalse(MeliuzCashbackClient.shouldNotify(store(1, 15), 10, store(1, 15), 10));
        assertFalse(MeliuzCashbackClient.shouldNotify(store(1, 15), 10, store(1, 12), 10));
        assertTrue(MeliuzCashbackClient.shouldNotify(store(1, 12), 10, store(1, 13), 10));
        assertTrue(MeliuzCashbackClient.shouldNotify(store(1, 9), 10, store(1, 10), 10));
        assertTrue(MeliuzCashbackClient.shouldNotify(store(1, 9), 10, store(1, 9), 8));
    }

    @Test public void persistsBeforeNotificationKeepsHistoryAndPreservesDataOnFailure() throws Exception {
        MeliuzCashbackStore repository = new MeliuzCashbackStore(TestPreferences.create());
        assertEquals(1, repository.record(new JSONArray().put(store(1, 15)), 10, 100).size());
        assertTrue(repository.record(new JSONArray().put(store(1, 15)), 10, 200).isEmpty());
        assertEquals(200, repository.lastSuccess());
        assertEquals(1, repository.history().getJSONArray("1").length());
        repository.failedAttempt();
        assertTrue(repository.failed());
        assertEquals(200, repository.lastSuccess());
        assertEquals(15, repository.snapshot().getJSONArray("stores").getJSONObject(0).getDouble("amount"), 0);
        assertTrue(repository.record(new JSONArray().put(store(1, 8)), 10, 300).isEmpty());
        assertFalse(repository.failed());
        assertEquals(1, repository.record(new JSONArray().put(store(1, 12)), 10, 400).size());
        assertEquals(3, repository.history().getJSONArray("1").length());
    }

    @Test public void restoreRoundTripRetainsSnapshotHistoryAndNotificationDeduplication() throws Exception {
        MeliuzCashbackStore source = new MeliuzCashbackStore(TestPreferences.create());
        source.record(new JSONArray().put(store(1, 12)), 10, 100);
        source.record(new JSONArray().put(store(1, 18).put("category", true)), 10, 200);
        MeliuzCashbackStore destination = new MeliuzCashbackStore(TestPreferences.create());
        assertTrue(destination.restore(source.exportBackup(), true));
        assertEquals(source.exportBackup().toString(), destination.exportBackup().toString());
        assertFalse(destination.restore(source.exportBackup(), false));
        assertTrue(destination.record(new JSONArray().put(store(1, 18).put("category", true)), 10, 300).isEmpty());
        assertEquals(2, destination.history().getJSONArray("1").length());
        assertFalse(destination.restore(null, true));
        assertEquals(300, destination.lastSuccess());
    }

    @Test public void oldRemoteSnapshotCannotReplaceNewReadButItsHistoryIsMerged() throws Exception {
        MeliuzCashbackStore older = new MeliuzCashbackStore(TestPreferences.create());
        older.record(new JSONArray().put(store(1, 10)), 10, 100);
        MeliuzCashbackStore newer = new MeliuzCashbackStore(TestPreferences.create());
        newer.record(new JSONArray().put(store(1, 18)), 10, 200);
        assertTrue(newer.restore(older.exportBackup(), false));
        assertEquals(200, newer.lastSuccess());
        assertEquals(2, newer.history().getJSONArray("1").length());
        assertFalse(newer.restore(older.exportBackup(), false));
    }
}
