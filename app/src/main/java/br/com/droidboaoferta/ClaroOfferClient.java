package br.com.droidboaoferta;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads Claro's official server-rendered smartphone cards. */
final class ClaroOfferClient {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final String ORIGIN = "https://planoscelular.claro.com.br";
    private static final Pattern PRODUCT_CARD = Pattern.compile(
            "(?is)<div\\s+id=[\\\"']product-([0-9]+)[\\\"'][^>]*"
                    + "data-link-redirect=[\\\"']([^\\\"']+)[\\\"'][^>]*>"
                    + ".*?name=[\\\"']productName[\\\"']\\s+value=[\\\"']([^\\\"']+)[\\\"']"
                    + ".*?name=[\\\"']productPrice[\\\"']\\s+value=[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);?");

    private ClaroOfferClient() {
    }

    static List<ExternalProductDeal> fetchOffers(Context context) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(ClaroOfferSource.getUrl(context)).openConnection();
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
        Matcher matcher = PRODUCT_CARD.matcher(html == null ? "" : html);
        while (matcher.find()) {
            String id = matcher.group(1).trim();
            String link = decodeHtml(matcher.group(2)).trim();
            String title = decodeHtml(matcher.group(3)).trim().replaceAll("\\s+", " ");
            double price = parsePrice(matcher.group(4));
            if (id.isEmpty() || title.isEmpty() || price <= 0d || !seenIds.add(id)) continue;
            if (link.startsWith("/")) link = ORIGIN + link;
            if (!link.startsWith("https://planoscelular.claro.com.br/")) continue;
            deals.add(new ExternalProductDeal(id, title, link, price));
        }
        return deals;
    }

    private static String decodeHtml(String value) {
        String text = value == null ? "" : value;
        Matcher matcher = NUMERIC_ENTITY.matcher(text);
        StringBuffer decoded = new StringBuffer();
        while (matcher.find()) {
            try {
                String entity = matcher.group(1);
                int codePoint = entity.startsWith("x") || entity.startsWith("X")
                        ? Integer.parseInt(entity.substring(1), 16) : Integer.parseInt(entity);
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(
                        new String(Character.toChars(codePoint))));
            } catch (Exception invalidEntity) {
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(decoded);
        return decoded.toString().replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&nbsp;", " ");
    }

    private static double parsePrice(String value) {
        try {
            return Math.round(Double.parseDouble(value.replace(".", "").replace(',', '.')) * 100d) / 100d;
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private static String readResponse(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("Claro response is too large");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
