package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the public KaBuM catalogue search rendered for a product term. */
final class KabumCatalogClient {
    private static final Pattern SCHEMA = Pattern.compile("(?is)<script id=[\\\"']productSchema[\\\"'][^>]*>(.*?)</script>");
    private KabumCatalogClient() { }

    static List<ExternalProductDeal> search(String baseUrl, String term) throws Exception {
        String query = URLEncoder.encode(term, StandardCharsets.UTF_8.name()).replace("+", "%20");
        String address = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + query;
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10_000); connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "Alertou/1.0 Android");
        try {
            if (connection.getResponseCode() / 100 != 2) throw new IllegalStateException("KaBuM HTTP error");
            String page = read(connection.getInputStream());
            return parseOffers(page);
        } finally { connection.disconnect(); }
    }

    static List<ExternalProductDeal> parseOffers(String page) throws Exception {
            Matcher matcher = SCHEMA.matcher(page == null ? "" : page);
            if (!matcher.find()) throw new IllegalStateException("KaBuM catalogue not found");
            JSONArray products = new JSONArray(matcher.group(1));
            List<ExternalProductDeal> deals = new ArrayList<>();
            for (int i = 0; i < products.length(); i++) {
                JSONObject product = products.optJSONObject(i);
                JSONObject offer = product == null ? null : product.optJSONObject("offers");
                String title = product == null ? "" : product.optString("name", "").trim();
                double price = offer == null ? Double.NaN : offer.optDouble("price", Double.NaN);
                String id = product == null ? "" : product.optString("sku", "").trim();
                String link = offer == null ? "" : offer.optString("url", "").trim();
                if (title.isEmpty() || id.isEmpty() || link.isEmpty() || price <= 0d) continue;
                deals.add(new ExternalProductDeal(id, title, link, Math.round(price * 100d) / 100d));
            }
            return deals;
    }

    private static String read(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > 4 * 1024 * 1024) throw new IllegalStateException("KaBuM response too large");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
