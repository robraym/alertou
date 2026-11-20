package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;

final class MeliuzCashbackChange {
    static double percentage(JSONObject current, JSONArray history) {
        if (current == null || history == null || history.length() < 2) return Double.NaN;
        JSONObject latest = history.optJSONObject(history.length() - 1);
        JSONObject previous = history.optJSONObject(history.length() - 2);
        if (latest == null || previous == null || !"percentage".equals(current.optString("type"))
                || !current.optString("type").equals(latest.optString("type"))
                || !current.optString("type").equals(previous.optString("type"))
                || current.optBoolean("category") != latest.optBoolean("category")
                || current.optBoolean("category") != previous.optBoolean("category")) return Double.NaN;
        double amount = current.optDouble("amount", Double.NaN);
        double last = latest.optDouble("amount", Double.NaN);
        double before = previous.optDouble("amount", Double.NaN);
        if (!Double.isFinite(amount) || !Double.isFinite(last) || !Double.isFinite(before)
                || before <= 0 || amount != last || amount == before) return Double.NaN;
        return (amount - before) / before * 100d;
    }
}
