package br.com.droidboaoferta;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

final class PropertyPageClient {
    private static final int MAX_RESPONSE_BYTES = 3 * 1024 * 1024;
    private static final int PAGE_CONNECT_TIMEOUT_MS = 12_000;
    private static final int PAGE_READ_TIMEOUT_MS = 18_000;
    // A página individual só complementa a lista oficial. Não deixe uma página lenta
    // segurar toda a consulta do condomínio.
    private static final int LISTING_CONNECT_TIMEOUT_MS = 4_000;
    private static final int LISTING_READ_TIMEOUT_MS = 6_000;
    private static final int QUINTO_ANDAR_LISTING_PAGE_SIZE = 20;
    private static final String QUINTO_ANDAR_LISTING_SEARCH_URL =
            "https://apigw.prod.quintoandar.com.br/house-listing-search/v3/search/list";

    private PropertyPageClient() {
    }

    static boolean isSupported(String rawUrl) {
        return normalizeSupportedUrl(rawUrl) != null;
    }

    static String normalizeSupportedUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if (!"https".equals(scheme)
                    || !(isCondominiumPath(path))) {
                return null;
            }
            if ("quintoandar.com.br".equals(host) || "www.quintoandar.com.br".equals(host)) {
                return "https://www.quintoandar.com.br" + path;
            }
            if (isCondominiumPath(path)
                    && ("loft.com.br".equals(host) || "www.loft.com.br".equals(host))) {
                return "https://loft.com.br" + path;
            }
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static PropertyPageResult fetch(String rawUrl) throws Exception {
        String normalizedUrl = normalizeSupportedUrl(rawUrl);
        if (normalizedUrl == null) {
            throw new IllegalArgumentException("Unsupported property page");
        }
        String loftDataUrl = buildLoftDataUrl(normalizedUrl);
        if (loftDataUrl != null) {
            return PropertyPageParser.parseLoftApiResponse(fetchHtml(loftDataUrl));
        }
        String page = fetchHtml(normalizedUrl);
        PropertyPageResult initialResult = PropertyPageParser.parse(page);
        if (!isQuintoAndarCondominiumUrl(normalizedUrl)
                || initialResult.getSaleListingsCount() <= initialResult.getSaleListings().size()) {
            return initialResult;
        }
        long condominiumId = PropertyPageParser.parseQuintoAndarCondominiumId(page);
        if (condominiumId <= 0L) {
            return initialResult;
        }
        List<PropertyPageListing> allListings = fetchAllQuintoAndarCondominiumListings(
                condominiumId, initialResult.getSaleListingsCount());
        return new PropertyPageResult(initialResult.getCondominiumName(), allListings,
                initialResult.getSaleListingsCount(),
                initialResult.hasReliableSaleListingsCount());
    }

    private static boolean isQuintoAndarCondominiumUrl(String normalizedUrl) {
        try {
            URI uri = URI.create(normalizedUrl);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return ("quintoandar.com.br".equals(host) || "www.quintoandar.com.br".equals(host))
                    && isCondominiumPath(uri.getPath());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static List<PropertyPageListing> fetchAllQuintoAndarCondominiumListings(
            long condominiumId, int expectedCount) throws Exception {
        LinkedHashMap<String, PropertyPageListing> listings = new LinkedHashMap<>();
        for (int offset = 0; offset < expectedCount; offset += QUINTO_ANDAR_LISTING_PAGE_SIZE) {
            List<PropertyPageListing> page = PropertyPageParser.parseQuintoAndarSearchResponse(
                    postJson(QUINTO_ANDAR_LISTING_SEARCH_URL,
                            buildQuintoAndarListingSearchRequest(condominiumId, offset)));
            if (page.isEmpty()) {
                throw new IllegalStateException("QuintoAndar returned an empty listings page");
            }
            for (PropertyPageListing listing : page) {
                listings.put(listing.getId(), listing);
            }
        }
        // saleListingsCount é o total oficial de anúncios do condomínio. Ele pode
        // incluir anúncios sem preço ou metragem no payload, que o parser não pode
        // transformar em oferta válida. Uma diferença entre esse total e a lista
        // utilizável não é falha de paginação; erros reais de rede ou página vazia
        // continuam lançando exceção acima e são informados ao usuário.
        return new ArrayList<>(listings.values());
    }

    private static String buildQuintoAndarListingSearchRequest(long condominiumId, int offset)
            throws Exception {
        JSONArray fields = new JSONArray();
        for (String field : new String[]{
                "id", "coverImage", "rent", "area", "bedrooms", "totalCost", "imageList",
                "imageCaptionList", "address", "regionName", "neighbourhood", "city",
                "visitStatus", "activeSpecialConditions", "attributeTags", "type", "forRent",
                "forSale", "salePrice", "iptu", "condominium", "iptuPlusCondominium",
                "parkingSpaces", "listingTags", "amenities", "bathrooms", "installations",
                "isFurnished", "shortRentDescription", "shortSaleDescription", "condoId"
        }) {
            fields.put(field);
        }
        JSONObject filters = new JSONObject()
                .put("businessContext", "SALE")
                .put("condoIds", new JSONArray().put(condominiumId))
                .put("availability", "ANY")
                .put("occupancy", "ANY")
                .put("origin", "HYBRID");
        return new JSONObject()
                .put("filters", filters)
                .put("sorting", new JSONObject().put("criteria", "RELEVANCE").put("order", "ASC"))
                .put("pagination", new JSONObject()
                        .put("pageSize", QUINTO_ANDAR_LISTING_PAGE_SIZE).put("offset", offset))
                .put("fields", fields)
                .toString();
    }

    static String buildLoftDataUrl(String normalizedUrl) {
        if (normalizedUrl == null || !"Loft".equals(getSourceName(normalizedUrl))) {
            return null;
        }
        try {
            String path = URI.create(normalizedUrl).getPath();
            int separator = path == null ? -1 : path.lastIndexOf('/');
            String shortId = separator >= 0 ? path.substring(separator + 1).trim() : "";
            if (shortId.isEmpty()) {
                return null;
            }
            return "https://informational-pages-api.loft.com.br/condominiums/" + shortId;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static String buildLoftListingUrl(String id) {
        if (id == null || id.trim().isEmpty()) {
            return "";
        }
        return "https://loft.com.br/imovel/" + id.trim();
    }

    static PropertyListingMetadata fetchListingMetadata(String rawUrl) throws Exception {
        String normalizedUrl = normalizeListingUrl(rawUrl);
        if (normalizedUrl == null) {
            throw new IllegalArgumentException("Unsupported listing page");
        }
        if (!isQuintoAndarListingUrl(normalizedUrl)) {
            return PropertyListingMetadata.empty();
        }
        return PropertyPageParser.parseListingMetadata(
                fetchHtml(normalizedUrl, LISTING_CONNECT_TIMEOUT_MS, LISTING_READ_TIMEOUT_MS),
                getListingId(normalizedUrl));
    }

    static boolean isQuintoAndarListingUrl(String url) {
        String normalized = normalizeListingUrl(url);
        return normalized != null && normalized.startsWith("https://www.quintoandar.com.br/");
    }

    static String getListingId(String url) {
        String normalized = normalizeListingUrl(url);
        if (normalized == null || !isQuintoAndarListingUrl(normalized)) {
            return "";
        }
        String[] parts = URI.create(normalized).getPath().split("/");
        return parts.length >= 3 ? parts[2] : "";
    }

    static String buildListingUrl(String id) {
        return buildListingUrl(id, false);
    }

    static String buildListingUrl(String id, boolean classified) {
        if (id == null || id.trim().isEmpty()) {
            return "";
        }
        return "https://www.quintoandar.com.br/"
                + (classified ? "classificado/" : "imovel/")
                + id.trim()
                + "/comprar";
    }

    static String buildListingUrlFromOfferId(String offerId) {
        if (offerId == null || !offerId.startsWith("property|")) {
            return "";
        }
        String[] parts = offerId.split("\\|", -1);
        if (parts.length < 3 || parts[2].trim().isEmpty()) {
            return "";
        }
        return buildListingUrl(parts[2]);
    }

    private static boolean isCondominiumPath(String path) {
        return path != null
                && path.startsWith("/condominio/")
                && path.length() > "/condominio/".length();
    }

    static String getSourceName(String rawUrl) {
        if (rawUrl == null) {
            return "QuintoAndar";
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if ("loft.com.br".equals(host) || "www.loft.com.br".equals(host)) {
                return "Loft";
            }
        } catch (IllegalArgumentException ignored) {
        }
        return "QuintoAndar";
    }

    static String normalizeListingUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if (!"https".equals(scheme)) {
                return null;
            }
            if ("quintoandar.com.br".equals(host) || "www.quintoandar.com.br".equals(host)) {
                if (path.startsWith("/classificado/") && path.endsWith("/comprar")) {
                    return "https://www.quintoandar.com.br" + path;
                }
                if (path.startsWith("/imovel/") && path.endsWith("/comprar")) {
                    return "https://www.quintoandar.com.br" + path;
                }
            } else if ("loft.com.br".equals(host) || "www.loft.com.br".equals(host)) {
                if (path.startsWith("/imovel/") && path.length() > "/imovel/".length()) {
                    return "https://loft.com.br" + path;
                }
            }
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String fetchHtml(String url) throws Exception {
        return fetchHtml(url, PAGE_CONNECT_TIMEOUT_MS, PAGE_READ_TIMEOUT_MS);
    }

    private static String fetchHtml(String url, int connectTimeoutMillis,
                                    int readTimeoutMillis) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                buildFreshRequestUrl(url, System.currentTimeMillis())).openConnection();
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        connection.setRequestProperty("Cache-Control", "no-cache, no-store");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("User-Agent", "Alertou/1.0 Android");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status);
            }
            return readResponse(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String postJson(String url, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(PAGE_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(PAGE_READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Origin", "https://www.quintoandar.com.br");
        connection.setRequestProperty("User-Agent", "Alertou/1.0 Android");
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status);
            }
            return readResponse(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    static String buildFreshRequestUrl(String url, long requestId) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "alertou_request=" + requestId;
    }

    private static String readResponse(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("Property page is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
