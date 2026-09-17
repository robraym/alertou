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
import java.util.Locale;

/** Reads the JSON catalogue endpoint used by KaBuM's own general search. */
final class KabumCatalogApiClient {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private KabumCatalogApiClient() { }

    static List<ExternalProductDeal> search(String baseUrl, String term) throws Exception {
        try {
            return fetch(baseUrl, term);
        } catch (Exception primaryError) {
            String fallback = alternateCatalogUrl(baseUrl);
            if (fallback.equals(baseUrl)) throw primaryError;
            return fetch(fallback, term);
        }
    }

    private static List<ExternalProductDeal> fetch(String baseUrl, String term) throws Exception {
        String separator = baseUrl.contains("?") ? "&" : "?";
        String address = baseUrl + separator + "query="
                + URLEncoder.encode(term, StandardCharsets.UTF_8.name()).replace("+", "%20")
                + "&page_number=1&page_size=60&include=gift";
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Origin", "https://www.kabum.com.br");
        connection.setRequestProperty("Referer", "https://www.kabum.com.br/busca/");
        connection.setRequestProperty("User-Agent", "Alertou/1.0 Android");
        // The catalogue occasionally returns a compressed body to Android's default
        // HttpURLConnection request. Keep it as plain JSON, which is the format the
        // parser consumes and the official endpoint consistently supports.
        connection.setRequestProperty("Accept-Encoding", "identity");
        try {
            int status = connection.getResponseCode();
            // This endpoint uses 404 when a search term has no catalogue result.
            // It is a valid empty search, not a failure of the KaBuM source.
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return new ArrayList<>();
            if (status < 200 || status >= 300) throw new IllegalStateException("KaBuM HTTP " + status);
            return parseOffers(read(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    private static String alternateCatalogUrl(String baseUrl) {
        if (baseUrl.contains("/catalog/v2/products")) {
            return baseUrl.replace("/catalog/v2/products", "/catalog/v2/search");
        }
        if (baseUrl.contains("/catalog/v2/search")) {
            return baseUrl.replace("/catalog/v2/search", "/catalog/v2/products");
        }
        return baseUrl;
    }

    private static List<ExternalProductDeal> parseOffers(String json) throws Exception {
        JSONArray products = new JSONObject(json).optJSONArray("data");
        if (products == null) throw new IllegalStateException("KaBuM catalogue API not found");
        List<ExternalProductDeal> deals = new ArrayList<>();
        for (int index = 0; index < products.length(); index++) {
            JSONObject product = products.optJSONObject(index);
            JSONObject attributes = product == null ? null : product.optJSONObject("attributes");
            long id = product == null ? 0L : product.optLong("id", 0L);
            if (id <= 0L || attributes == null || !attributes.optBoolean("available", true)) continue;
            String title = attributes.optString("title", "").trim();
            double price = resolvePrice(attributes);
            if (title.isEmpty() || Double.isNaN(price) || price <= 0d) continue;
            deals.add(new ExternalProductDeal(String.valueOf(id), title,
                    createProductUrl(id, attributes.optString("product_link", "")),
                    Math.round(price * 100d) / 100d));
        }
        return deals;
    }

    private static double resolvePrice(JSONObject attributes) {
        JSONObject offer = attributes.optJSONObject("offer");
        if (offer != null) {
            double value = offer.optDouble("price_with_discount", Double.NaN);
            if (!Double.isNaN(value) && value > 0d) return value;
            value = offer.optDouble("price", Double.NaN);
            if (!Double.isNaN(value) && value > 0d) return value;
        }
        double value = attributes.optDouble("price_with_discount", Double.NaN);
        return !Double.isNaN(value) && value > 0d ? value : attributes.optDouble("price", Double.NaN);
    }

    private static String createProductUrl(long id, String productLink) throws Exception {
        String slug = productLink == null ? "" : productLink.trim();
        if (slug.startsWith("http://") || slug.startsWith("https://")) return slug;
        if (slug.startsWith("/")) return "https://www.kabum.com.br" + slug;
        if (slug.isEmpty()) slug = "produto";
        return "https://www.kabum.com.br/produto/" + id + "/"
                + URLEncoder.encode(slug.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8.name()).replace("+", "%20");
    }

    private static String read(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("KaBuM response too large");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
