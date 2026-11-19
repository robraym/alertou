package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The same public JSON feeds used by Méliuz's featured-store grid. No account or cookies. */
final class MeliuzCashbackClient {
    static final String BASE = "https://www.meliuz.com.br";
    static final String FEATURED_URL = BASE + "/v3/api/home/hot-partners";
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    interface Transport { String get(String url) throws Exception; }

    static JSONArray fetch() throws Exception { return fetch(MeliuzCashbackClient::get); }

    static JSONArray fetch(Transport transport) throws Exception {
        JSONArray partners = new JSONArray(transport.get(FEATURED_URL));
        if (partners.length() == 0 || partners.length() > 500) throw invalid();
        Set<Long> ids = new HashSet<>();
        Map<Long, JSONObject> rates = new HashMap<>();
        // Bound URL length; never fan out one request per store.
        for (int start = 0; start < partners.length(); start += 50) {
            StringBuilder query = new StringBuilder();
            for (int i = start; i < Math.min(start + 50, partners.length()); i++) {
                JSONObject partner = partners.getJSONObject(i);
                long id = partner.getLong("id");
                if (id <= 0 || !ids.add(id) || partner.getString("name").trim().isEmpty()
                        || !partner.getString("uri").matches("[a-zA-Z0-9_-]+")) throw invalid();
                if (query.length() > 0) query.append(',');
                query.append(id);
            }
            JSONObject response = new JSONObject(transport.get(
                    BASE + "/v3/api/home/cashback?ids=" + query));
            JSONArray offers = response.getJSONArray("offers");
            for (int i = 0; i < offers.length(); i++) {
                JSONObject offer = offers.getJSONObject(i);
                long id = offer.has("partner_id") ? offer.getLong("partner_id")
                        : offer.getLong("partnerId");
                if (!ids.contains(id) || rates.put(id, offer) != null) throw invalid();
            }
        }
        JSONArray stores = new JSONArray();
        for (int i = 0; i < partners.length(); i++) {
            JSONObject partner = partners.getJSONObject(i);
            JSONObject offer = rates.get(partner.getLong("id"));
            // Missing data is not zero cashback. Keep the previous complete snapshot on failure.
            if (offer == null) throw invalid();
            JSONObject cashback = offer.optJSONObject("cashback");
            if (cashback == null && offer.has("value")) cashback = offer;
            JSONObject store = new JSONObject().put("id", partner.getLong("id"))
                    .put("name", partner.getString("name"))
                    .put("uri", partner.getString("uri"))
                    .put("category", offer.optBoolean("cashback_category", false));
            if (cashback == null && offer.has("cashback") && offer.isNull("cashback")) {
                store.put("amount", 0).put("type", "none");
            } else {
                if (cashback == null) throw invalid();
                Object raw = cashback.opt("amount");
                if (raw == null) raw = cashback.opt("value");
                if (!(raw instanceof Number)) throw invalid();
                double amount = ((Number) raw).doubleValue();
                String type = cashback.getString("type");
                if (!Double.isFinite(amount) || amount < 0 || type.isEmpty()) throw invalid();
                if ("percent".equals(type)) type = "percentage";
                if ("percentage".equals(type) && amount > 100) throw invalid();
                store.put("amount", amount).put("type", type);
            }
            stores.put(store);
        }
        return stores;
    }

    static boolean qualifies(JSONObject store, double minimum) {
        return "percentage".equals(store.optString("type"))
                && store.optDouble("amount", -1) > 0
                && store.optDouble("amount", -1) >= minimum;
    }

    static boolean changed(JSONObject before, JSONObject after) {
        return before == null || Double.compare(before.optDouble("amount", -1),
                after.optDouble("amount", -1)) != 0
                || !before.optString("type").equals(after.optString("type"))
                || before.optBoolean("category") != after.optBoolean("category");
    }

    static boolean shouldNotify(JSONObject before, double previousMinimum,
                                JSONObject after, double minimum) {
        return qualifies(after, minimum) && (before == null || !qualifies(before, previousMinimum)
                || after.optDouble("amount") > before.optDouble("amount"));
    }

    private static IOException invalid() { return new IOException("Invalid Méliuz cashback feed"); }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Alertou/1.0 Android");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (output.size() + count > MAX_BYTES) throw invalid();
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
