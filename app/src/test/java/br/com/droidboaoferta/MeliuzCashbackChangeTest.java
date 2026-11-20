package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MeliuzCashbackChangeTest {
    private JSONObject rate(double amount, String type, boolean upTo) throws Exception {
        return new JSONObject().put("amount", amount).put("type", type).put("category", upTo);
    }

    @Test public void computesIncreaseAndDecreaseUsingSameRelativePercentageAsTreasury() throws Exception {
        JSONObject ten = rate(10, "percentage", false);
        JSONObject fifteen = rate(15, "percentage", false);
        assertEquals(50, MeliuzCashbackChange.percentage(fifteen, new JSONArray().put(ten).put(fifteen)), 0.0001);
        assertEquals(-100d / 3, MeliuzCashbackChange.percentage(ten, new JSONArray().put(fifteen).put(ten)), 0.0001);
    }

    @Test public void doesNotInventChangeWithoutComparablePreviousReading() throws Exception {
        JSONObject ten = rate(10, "percentage", false);
        assertTrue(Double.isNaN(MeliuzCashbackChange.percentage(ten, new JSONArray().put(ten))));
        assertTrue(Double.isNaN(MeliuzCashbackChange.percentage(ten, new JSONArray().put(ten).put(ten))));
        assertTrue(Double.isNaN(MeliuzCashbackChange.percentage(ten,
                new JSONArray().put(rate(0, "percentage", false)).put(ten))));
        assertTrue(Double.isNaN(MeliuzCashbackChange.percentage(ten,
                new JSONArray().put(rate(5, "currency", false)).put(ten))));
        assertTrue(Double.isNaN(MeliuzCashbackChange.percentage(ten,
                new JSONArray().put(rate(5, "percentage", true)).put(ten))));
        assertTrue(Double.isNaN(MeliuzCashbackChange.percentage(ten,
                new JSONArray().put(rate(5, "percentage", false)).put(rate(15, "percentage", false)))));
    }
}
