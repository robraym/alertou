package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the public VTEX catalogue referenced by Samsung's official discount page. */
final class SamsungDiscountOfferClient {
    private static final Pattern SKU_ID = Pattern.compile("(?i)skuId=(\\d+)");
    private static final String VTEX_PRODUCT = "https://samsungbrshop.vtexcommercestable.com.br/api/catalog_system/pub/products/search?fq=skuId:";

    private SamsungDiscountOfferClient() { }

    static List<ExternalProductDeal> fetchOffers() throws Exception {
        String page = read(SamsungDiscountOfferSource.DEFAULT_URL);
        Set<String> skuIds = new LinkedHashSet<>();
        Matcher matcher = SKU_ID.matcher(page);
        while (matcher.find() && skuIds.size() < 20) skuIds.add(matcher.group(1));
        if (skuIds.isEmpty()) throw new IllegalStateException("No Samsung Discount SKU found");
        List<ExternalProductDeal> deals = new ArrayList<>();
        for (String skuId : skuIds) addProduct(read(VTEX_PRODUCT + skuId), deals, skuId);
        return deals;
    }

    private static void addProduct(String json, List<ExternalProductDeal> deals, String fallbackId) {
        try {
            JSONArray products = new JSONArray(json);
            JSONObject product = products.optJSONObject(0);
            if (product == null) return;
            String title = product.optString("productName", "").trim();
            String link = product.optString("link", "").trim();
            if (link.startsWith("/")) link = "https://shop.samsung.com" + link;
            JSONArray items = product.optJSONArray("items");
            double lowest = Double.NaN;
            if (items != null) for (int itemIndex = 0; itemIndex < items.length(); itemIndex++) {
                JSONArray sellers = items.optJSONObject(itemIndex).optJSONArray("sellers");
                if (sellers == null) continue;
                for (int sellerIndex = 0; sellerIndex < sellers.length(); sellerIndex++) {
                    JSONObject offer = sellers.optJSONObject(sellerIndex)
                            .optJSONObject("commertialOffer");
                    double price = offer == null ? Double.NaN : offer.optDouble("Price", Double.NaN);
                    if (price > 0d && (Double.isNaN(lowest) || price < lowest)) lowest = price;
                }
            }
            if (!title.isEmpty() && !link.isEmpty() && lowest > 0d) {
                deals.add(new ExternalProductDeal(product.optString("productId", fallbackId), title, link,
                        Math.round(lowest * 100d) / 100d));
            }
        } catch (Exception ignored) { }
    }

    private static String read(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "Alertou/1.0 Android");
        connection.setRequestProperty("Accept", "application/json,text/html");
        try {
            if (connection.getResponseCode() / 100 != 2) throw new IllegalStateException("Samsung HTTP error");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 4 * 1024 * 1024) throw new IllegalStateException("Samsung response too large");
                    output.write(buffer, 0, count);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }
}
