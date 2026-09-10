package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads the public VTEX catalog used by Motorola's official offers page. */
final class MotorolaOfferClient {
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final String API_URL = "https://www.motorola.com.br/api/catalog_system/pub/products/search"
            + "?fq=H:377&_from=0&_to=49";

    private MotorolaOfferClient() {
    }

    static List<ExternalProductDeal> fetchOffers() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Referer", MotorolaOfferSource.DEFAULT_URL);
        connection.setRequestProperty("User-Agent", "Alertou/1.0 Android");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
            return parseOffers(readResponse(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    private static List<ExternalProductDeal> parseOffers(String json) throws Exception {
        JSONArray products = new JSONArray(json);
        List<ExternalProductDeal> deals = new ArrayList<>();
        for (int productIndex = 0; productIndex < products.length(); productIndex++) {
            JSONObject product = products.optJSONObject(productIndex);
            if (product == null) continue;
            String id = product.optString("productId", "").trim();
            String title = product.optString("productName", "").trim();
            String link = product.optString("link", "").trim();
            double lowestPrice = findLowestAvailablePrice(product.optJSONArray("items"));
            if (id.isEmpty() || title.isEmpty() || link.isEmpty()
                    || Double.isNaN(lowestPrice) || lowestPrice <= 0d) continue;
            deals.add(new ExternalProductDeal(id, title, link,
                    Math.round(lowestPrice * 100d) / 100d));
        }
        return deals;
    }

    private static double findLowestAvailablePrice(JSONArray items) {
        double lowest = Double.NaN;
        if (items == null) return lowest;
        for (int itemIndex = 0; itemIndex < items.length(); itemIndex++) {
            JSONObject item = items.optJSONObject(itemIndex);
            JSONArray sellers = item == null ? null : item.optJSONArray("sellers");
            if (sellers == null) continue;
            for (int sellerIndex = 0; sellerIndex < sellers.length(); sellerIndex++) {
                JSONObject seller = sellers.optJSONObject(sellerIndex);
                JSONObject offer = seller == null ? null : seller.optJSONObject("commertialOffer");
                double quantity = offer == null ? 0d : offer.optDouble("AvailableQuantity", 0d);
                double price = offer == null ? Double.NaN : offer.optDouble("Price", Double.NaN);
                if (quantity > 0d && price > 0d
                        && (Double.isNaN(lowest) || price < lowest)) lowest = price;
            }
        }
        return lowest;
    }

    private static String readResponse(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("Motorola response is too large");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
