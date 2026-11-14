package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TreasuryDirectMonitorTest {
    @Test
    public void findsLatestOfficialMarketPricingTimestamp() throws Exception {
        JSONArray bonds = new JSONArray()
                .put(new JSONObject().put("lastMarketPricingDate", "2026-11-11T18:00:07.650"))
                .put(new JSONObject().put("lastMarketPricingDate", "2026-11-12T09:30:00.000"));

        assertEquals(1_794_486_600_000L, TreasuryDirectMonitor.findLatestMarketPricingAt(bonds));
    }

    @Test
    public void ignoresMissingOfficialMarketPricingTimestamp() throws Exception {
        assertEquals(0L, TreasuryDirectMonitor.findLatestMarketPricingAt(
                new JSONArray().put(new JSONObject())));
    }

}
