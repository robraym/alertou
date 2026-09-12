package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the public product schema embedded in Samsung Brazil's offers page. */
final class SamsungOfferClient {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Pattern JSON_LD = Pattern.compile(
            "(?is)<script[^>]+type=[\\\"']application/ld\\+json[\\\"'][^>]*>(.*?)</script>");

    private SamsungOfferClient() {
    }

    static List<ExternalProductDeal> fetchOffers() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(SamsungOfferSource.DEFAULT_URL).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        connection.setRequestProperty("User-Agent", "Alertou/1.0 Android");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
            return parseOffers(readResponse(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    static List<ExternalProductDeal> parseOffers(String html) {
        List<ExternalProductDeal> deals = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Matcher matcher = JSON_LD.matcher(html == null ? "" : html);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            try {
                if (value.startsWith("[")) {
                    JSONArray array = new JSONArray(value);
                    for (int index = 0; index < array.length(); index++) {
                        collectProducts(array.opt(index), deals, seenIds);
                    }
                } else {
                    collectProducts(new JSONObject(value), deals, seenIds);
                }
            } catch (Exception ignored) {
                // Ignore malformed schema blocks and keep the valid official product blocks.
            }
        }
        return deals;
    }

    private static void collectProducts(Object value, List<ExternalProductDeal> deals,
                                        Set<String> seenIds) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) collectProducts(array.opt(index), deals, seenIds);
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        Object type = object.opt("@type");
        if ("Product".equals(type)) addProduct(object, deals, seenIds);
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!"offers".equals(key)) collectProducts(object.opt(key), deals, seenIds);
        }
    }

    private static void addProduct(JSONObject product, List<ExternalProductDeal> deals,
                                   Set<String> seenIds) {
        JSONObject offer = product.optJSONObject("offers");
        String title = product.optString("name", "").trim();
        String link = product.optString("url", "").trim();
        double price = offer == null ? Double.NaN : offer.optDouble("price", Double.NaN);
        String id = product.optString("sku", product.optString("productID", link)).trim();
        if (title.isEmpty() || link.isEmpty() || id.isEmpty() || price <= 0d || !seenIds.add(id)) return;
        deals.add(new ExternalProductDeal(id, title, link, Math.round(price * 100d) / 100d));
    }

    private static String readResponse(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("Samsung response is too large");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
