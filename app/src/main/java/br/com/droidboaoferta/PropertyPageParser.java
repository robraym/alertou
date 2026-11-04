package br.com.droidboaoferta;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PropertyPageParser {
    private static final Pattern NEXT_DATA = Pattern.compile(
            "(?is)<script[^>]*id=[\\\"']__NEXT_DATA__[\\\"'][^>]*>(.*?)</script>"
    );

    private PropertyPageParser() {
    }

    static PropertyPageResult parse(String html) {
        if (html == null || html.trim().isEmpty()) {
            return new PropertyPageResult("", new ArrayList<>());
        }
        Matcher matcher = NEXT_DATA.matcher(html);
        if (!matcher.find()) {
            return new PropertyPageResult("", new ArrayList<>());
        }
        try {
            JSONObject root = new JSONObject(matcher.group(1));
            PropertyPageResult loftResult = parseLoft(root, html);
            if (loftResult != null) {
                return loftResult;
            }
            JSONObject listings = findObjectContainingArray(root, "saleListings");
            String condominiumName = findString(root, "nameFormatted");
            if (listings == null) {
                List<PropertyPageListing> searchListings = parseSearchListings(root);
                if (!searchListings.isEmpty()) {
                    return new PropertyPageResult("", searchListings);
                }
            }
            List<PropertyPageListing> parsed = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            if (listings == null) {
                return new PropertyPageResult(condominiumName, parsed);
            }
            JSONArray saleListings = listings.optJSONArray("saleListings");
            if (saleListings == null) {
                return new PropertyPageResult(condominiumName, parsed);
            }
            for (int index = 0; index < saleListings.length(); index++) {
                JSONObject wrapper = saleListings.optJSONObject(index);
                if (wrapper == null) {
                    continue;
                }
                JSONObject source = wrapper.optJSONObject("_source");
                if (source == null) {
                    source = wrapper;
                }
                String id = wrapper.optString("_id", source.optString("id", ""));
                double area = source.optDouble("area", Double.NaN);
                double salePrice = source.optDouble("salePrice", Double.NaN);
                if (id.isEmpty() || !(area > 0d) || !(salePrice > 0d)
                        || !seenIds.add(id)) {
                    continue;
                }
                parsed.add(new PropertyPageListing(
                        id,
                        area,
                        salePrice,
                        source.optString("shortSaleDescription", ""),
                        PropertyPageClient.buildListingUrl(
                                id,
                                isQuintoAndarClassified(wrapper, source)
                        ),
                        containsTag(source.optJSONArray("listingTags"), "NEW_AD")
                ));
            }
            return new PropertyPageResult(
                    condominiumName,
                    parsed,
                    listings.optInt("saleListingsCount", parsed.size())
            );
        } catch (Exception ignored) {
            return new PropertyPageResult("", new ArrayList<>());
        }
    }

    static PropertyPageResult parseSearch(String html) {
        if (html == null || html.trim().isEmpty()) {
            return new PropertyPageResult("", new ArrayList<>());
        }
        Matcher matcher = NEXT_DATA.matcher(html);
        if (!matcher.find()) {
            return new PropertyPageResult("", new ArrayList<>());
        }
        try {
            JSONObject root = new JSONObject(matcher.group(1));
            List<PropertyPageListing> listings = parseSearchListings(root);
            return new PropertyPageResult("", listings, findSearchListingsCount(root, listings.size()));
        } catch (Exception ignored) {
            return new PropertyPageResult("", new ArrayList<>());
        }
    }

    /**
     * Builds the same paginated search request used by QuintoAndar's own web
     * application.  The rendered search page is only used to obtain the
     * location and filters; listing totals must come from this paginated API,
     * not from the first rendered page.
     */
    static String buildSearchApiRequest(String html, String normalizedUrl,
                                        int offset, int pageSize) {
        if (html == null || html.trim().isEmpty() || normalizedUrl == null) return null;
        Matcher matcher = NEXT_DATA.matcher(html);
        if (!matcher.find()) return null;
        try {
            JSONObject root = new JSONObject(matcher.group(1));
            JSONObject props = root.optJSONObject("props");
            JSONObject pageProps = props == null ? null : props.optJSONObject("pageProps");
            JSONObject state = pageProps == null ? null : pageProps.optJSONObject("initialState");
            JSONObject search = state == null ? null : state.optJSONObject("search");
            JSONObject location = search == null ? null : search.optJSONObject("location");
            JSONObject filtersState = search == null ? null : search.optJSONObject("filters");
            JSONObject choices = filtersState == null ? null : filtersState.optJSONObject("choices");
            if (location == null || choices == null) return null;

            JSONObject request = new JSONObject();
            request.put("slug", java.net.URI.create(normalizedUrl).getPath());
            request.put("fields", new JSONArray()
                    .put("id").put("salePrice").put("area").put("address")
                    .put("forSale").put("listingTags").put("shortSaleDescription")
                    .put("condominiumName"));
            request.put("sorting", new JSONObject()
                    .put("criteria", "RELEVANCE").put("order", "DESC"));
            request.put("pagination", new JSONObject()
                    .put("pageSize", Math.max(1, Math.min(500, pageSize)))
                    .put("offset", Math.max(0, offset)));
            request.put("context", new JSONObject()
                    .put("numPhotos", 12).put("isSSR", false)
                    .put("listShowing", true).put("mapShowing", false));

            JSONObject apiFilters = new JSONObject();
            apiFilters.put("businessContext", "SALE");
            apiFilters.put("location", buildSearchLocation(location));
            JSONArray priceRanges = new JSONArray();
            priceRanges.put(buildPriceRange("SALE_PRICE", choices.optJSONObject("salePrice")));
            priceRanges.put(buildPriceRange("IPTU_CONDO", choices.optJSONObject("condoIptu")));
            apiFilters.put("priceRange", priceRanges);
            apiFilters.put("availability", "ANY");
            apiFilters.put("occupancy", "ANY");
            apiFilters.put("partnerIds", new JSONArray());
            apiFilters.put("specialConditions", new JSONArray());
            apiFilters.put("excludedSpecialConditions", new JSONArray());
            apiFilters.put("blocklist", new JSONArray());
            apiFilters.put("categories", new JSONArray());
            apiFilters.put("houseSpecs", buildHouseSpecs(choices));
            apiFilters.put("origin", "HYBRID");
            request.put("filters", apiFilters);
            return request.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    static PropertyPageResult parseSearchApiResponse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new PropertyPageResult("", new ArrayList<>());
        }
        try {
            JSONObject root = new JSONObject(json);
            return new PropertyPageResult("", parseSearchListings(root));
        } catch (Exception ignored) {
            return new PropertyPageResult("", new ArrayList<>());
        }
    }

    private static JSONObject buildSearchLocation(JSONObject location) throws Exception {
        JSONObject result = new JSONObject();
        JSONObject coordinates = location.optJSONObject("latLng");
        if (coordinates != null) {
            result.put("coordinate", new JSONObject()
                    .put("lat", coordinates.optDouble("lat"))
                    .put("lng", coordinates.optDouble("lng")));
        }
        JSONObject viewport = location.optJSONObject("viewport");
        if (viewport != null) {
            result.put("viewport", new JSONObject()
                    .put("east", viewport.optDouble("east"))
                    .put("north", viewport.optDouble("north"))
                    .put("south", viewport.optDouble("south"))
                    .put("west", viewport.optDouble("west")));
        }
        result.put("neighborhoods", new JSONArray());
        result.put("city", location.optString("city", ""));
        result.put("address", location.optString("name", ""));
        return result;
    }

    private static JSONObject buildPriceRange(String type, JSONObject choice) throws Exception {
        JSONObject range = new JSONObject();
        if (choice != null) {
            range.put("min", selectedMinimum(choice));
            range.put("max", selectedMaximum(choice));
        }
        return new JSONObject().put("costType", type).put("range", range);
    }

    private static JSONObject buildHouseSpecs(JSONObject choices) throws Exception {
        JSONObject specs = new JSONObject();
        JSONObject areaChoice = choices.optJSONObject("area");
        JSONObject area = new JSONObject();
        if (areaChoice != null) {
            area.put("min", selectedMinimum(areaChoice));
            area.put("max", selectedMaximum(areaChoice));
        }
        specs.put("area", new JSONObject().put("range", area));
        specs.put("houseTypes", new JSONArray()
                .put("APARTMENT").put("HOUSE").put("STUDIO").put("HOUSE_CONDO"));
        specs.put("amenities", new JSONArray());
        specs.put("installations", new JSONArray());
        specs.put("bathrooms", new JSONObject().put("range", new JSONObject()));
        specs.put("bedrooms", new JSONObject().put("range", new JSONObject()));
        specs.put("parkingSpace", new JSONObject().put("range", new JSONObject()));
        specs.put("suites", new JSONObject().put("range", new JSONObject()));
        putBooleanChoice(specs, "isFurnished", choices.optString("furnished", "any"));
        putBooleanChoice(specs, "acceptPets", choices.optString("acceptsPets", "any"));
        putBooleanChoice(specs, "nearSubway", choices.optString("nearSubway", "any"));
        return specs;
    }

    private static double selectedMinimum(JSONObject choice) {
        return choice.has("selectedMin") ? choice.optDouble("selectedMin")
                : choice.optDouble("min");
    }

    private static double selectedMaximum(JSONObject choice) {
        return choice.has("selectedMax") ? choice.optDouble("selectedMax")
                : choice.optDouble("max");
    }

    private static void putBooleanChoice(JSONObject target, String name, String value)
            throws Exception {
        if ("yes".equals(value)) {
            target.put(name, true);
        } else if ("no".equals(value)) {
            target.put(name, false);
        }
    }

    /** Total oficial da busca por CEP/endereço, que pode ser maior que a primeira página. */
    private static int findSearchListingsCount(JSONObject root, int fallback) {
        JSONObject props = root.optJSONObject("props");
        JSONObject pageProps = props == null ? null : props.optJSONObject("pageProps");
        JSONObject initialState = pageProps == null ? null : pageProps.optJSONObject("initialState");
        JSONObject search = initialState == null ? null : initialState.optJSONObject("search");
        int total = search == null ? -1 : search.optInt("searchTotal", -1);
        return total >= 0 ? total : fallback;
    }

    static PropertyPageResult parseLoftApiResponse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new PropertyPageResult("", new ArrayList<>());
        }
        try {
            JSONObject condominium = new JSONObject(json);
            PropertyPageResult result = parseLoftCondominium(condominium, "");
            return result == null
                    ? new PropertyPageResult("", new ArrayList<>())
                    : result;
        } catch (Exception ignored) {
            return new PropertyPageResult("", new ArrayList<>());
        }
    }

    private static boolean isQuintoAndarClassified(JSONObject wrapper, JSONObject source) {
        return "CLASSIFIED".equals(wrapper.optString("origin", ""))
                || containsTag(source.optJSONArray("listingTags"), "CLASSIFIED");
    }

    private static List<PropertyPageListing> parseSearchListings(Object value) {
        List<PropertyPageListing> parsed = new ArrayList<>();
        collectSearchListings(value, parsed, new HashSet<>());
        return parsed;
    }

    private static void collectSearchListings(Object value,
                                              List<PropertyPageListing> parsed,
                                              Set<String> seenIds) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONObject source = object.optJSONObject("_source");
            JSONObject listing = source == null ? object : source;
            String id = object.optString("_id", listing.optString("id", ""));
            double area = listing.optDouble("area", Double.NaN);
            double salePrice = listing.optDouble("salePrice", Double.NaN);
            if (!id.isEmpty()
                    && Boolean.TRUE.equals(listing.opt("forSale"))
                    && area > 0d
                    && salePrice > 0d
                    && seenIds.add(id)) {
                String title = firstNonBlank(
                        findCondominiumName(listing),
                        findString(listing, "condominiumName"),
                        findString(listing, "condominium_name"),
                        findString(listing, "buildingName"),
                        listing.optString("shortSaleDescription", ""),
                        listing.optString("description", "")
                );
                parsed.add(new PropertyPageListing(
                        id,
                        area,
                        salePrice,
                        title,
                        PropertyPageClient.buildListingUrl(
                                id,
                                isQuintoAndarClassified(object, listing)
                        ),
                        containsTag(listingTags(object, listing), "NEW_AD"),
                        containsTag(listingTags(object, listing), "SALE_GOOD_PRICE"),
                        buildListingAddress(listing)
                ));
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                collectSearchListings(object.opt(keys.next()), parsed, seenIds);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                collectSearchListings(array.opt(index), parsed, seenIds);
            }
        }
    }

    private static String findCondominiumName(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.opt(key);
                String normalizedKey = key.toLowerCase(java.util.Locale.ROOT);
                if (child instanceof JSONObject
                        && (normalizedKey.contains("condominium")
                        || normalizedKey.contains("building"))) {
                    JSONObject childObject = (JSONObject) child;
                    String name = firstNonBlank(
                            childObject.optString("nameFormatted", ""),
                            childObject.optString("name", ""),
                            childObject.optString("title", "")
                    );
                    if (!name.isEmpty()) {
                        return name;
                    }
                }
                String found = findCondominiumName(child);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                String found = findCondominiumName(array.opt(index));
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    private static String buildListingAddress(JSONObject listing) {
        String street = firstNonBlank(
                findString(listing, "street"),
                findString(listing, "streetName"),
                findString(listing, "addressStreet"),
                findString(listing, "logradouro"),
                findNestedAddressValue(listing, "street"),
                findNestedAddressValue(listing, "streetName"),
                findNestedAddressValue(listing, "logradouro")
        );
        String number = firstNonBlank(
                findString(listing, "number"),
                findString(listing, "streetNumber"),
                findString(listing, "addressNumber"),
                findString(listing, "numero"),
                findNestedAddressValue(listing, "number"),
                findNestedAddressValue(listing, "streetNumber"),
                findNestedAddressValue(listing, "numero")
        );
        String fullAddress = firstNonBlank(
                findString(listing, "address"),
                findString(listing, "fullAddress"),
                findNestedAddressValue(listing, "fullAddress"),
                findNestedAddressValue(listing, "label")
        );
        if (street.isEmpty()) {
            return fullAddress;
        }
        return number.isEmpty() ? street : street + ", " + number;
    }

    private static JSONArray listingTags(JSONObject wrapper, JSONObject listing) {
        JSONArray tags = listing.optJSONArray("listingTags");
        if (tags != null) return tags;
        JSONObject fields = wrapper.optJSONObject("fields");
        return fields == null ? null : fields.optJSONArray("listingTags");
    }

    private static String findNestedAddressValue(JSONObject listing, String key) {
        JSONObject address = findAddressObject(listing);
        return address == null ? "" : address.optString(key, "");
    }

    private static JSONObject findAddressObject(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONObject direct = object.optJSONObject("address");
            if (direct != null) {
                return direct;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                JSONObject found = findAddressObject(object.opt(keys.next()));
                if (found != null) {
                    return found;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                JSONObject found = findAddressObject(array.opt(index));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static PropertyPageResult parseLoft(JSONObject root, String html) {
        JSONObject condominium = findLoftCondominium(root);
        if (condominium == null) {
            return null;
        }
        return parseLoftCondominium(condominium, html);
    }

    private static PropertyPageResult parseLoftCondominium(JSONObject condominium, String html) {
        JSONArray listings = condominium.optJSONArray("listings");
        if (listings == null) {
            return null;
        }
        String condominiumName = condominium.optString("name", "");
        List<PropertyPageListing> parsed = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int index = 0; index < listings.length(); index++) {
            JSONObject listing = listings.optJSONObject(index);
            if (listing == null || !"FOR_SALE".equals(listing.optString("status", ""))
                    || !seenIds.add(listing.optString("id", ""))) {
                continue;
            }
            String id = listing.optString("id", "");
            double area = listing.optDouble("area", Double.NaN);
            double price = listing.optDouble("price", Double.NaN);
            if (id.isEmpty() || !(area > 0d) || !(price > 0d)) {
                continue;
            }
            parsed.add(new PropertyPageListing(
                    id,
                    area,
                    price,
                    buildLoftDescription(listing),
                    findLoftListingUrl(html, id),
                    false
            ));
        }
        return new PropertyPageResult(condominiumName, parsed);
    }

    private static JSONObject findLoftCondominium(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.optJSONArray("listings") != null
                    && !object.optString("shortId", "").isEmpty()
                    && !object.optString("name", "").isEmpty()) {
                return object;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                JSONObject found = findLoftCondominium(object.opt(keys.next()));
                if (found != null) {
                    return found;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                JSONObject found = findLoftCondominium(array.opt(index));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String buildLoftDescription(JSONObject listing) {
        String type = listing.optString(
                "propertyType",
                listing.optString("type", "Apartamento")
        );
        if ("studio".equalsIgnoreCase(type)) {
            type = "Studio";
        } else if ("rooftop".equalsIgnoreCase(type)) {
            type = "Cobertura";
        } else {
            type = "Apartamento";
        }
        int bedrooms = listing.optInt("bedrooms", 0);
        return bedrooms > 0 ? type + " com " + bedrooms + " quartos" : type;
    }

    private static String findLoftListingUrl(String html, String id) {
        if (html == null || id == null || id.trim().isEmpty()) {
            return "";
        }
        Pattern pattern = Pattern.compile(
                "https://loft\\.com\\.br/imovel/[^\\\"#]*?/" + Pattern.quote(id.trim())
        );
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group() : PropertyPageClient.buildLoftListingUrl(id);
    }

    static PropertyListingMetadata parseListingMetadata(String html, String expectedId) {
        if (html == null || html.trim().isEmpty() || expectedId == null || expectedId.isEmpty()) {
            return PropertyListingMetadata.empty();
        }
        Matcher matcher = NEXT_DATA.matcher(html);
        if (!matcher.find()) {
            return PropertyListingMetadata.empty();
        }
        try {
            JSONObject root = new JSONObject(matcher.group(1));
            JSONObject query = root.optJSONObject("query");
            if (query == null || !expectedId.equals(query.optString("houseId"))) {
                return PropertyListingMetadata.empty();
            }
            if (root.optString("page").startsWith("/indisponivel/")) {
                return PropertyListingMetadata.unavailable(expectedId);
            }
            // Only the main listing is authoritative. Recommendations can have unrelated prices.
            JSONObject info = root.getJSONObject("props").getJSONObject("pageProps")
                    .getJSONObject("initialState").getJSONObject("house")
                    .getJSONObject("houseInfo");
            if (!expectedId.equals(info.optString("id"))) {
                return PropertyListingMetadata.empty();
            }
            if (Boolean.FALSE.equals(info.opt("forSale"))) {
                return PropertyListingMetadata.unavailable(expectedId);
            }
            double price = info.optDouble("salePrice", Double.NaN);
            double area = info.optDouble("area", Double.NaN);
            if (!Boolean.TRUE.equals(info.opt("forSale")) || !Double.isFinite(price)
                    || price <= 0d || !Double.isFinite(area) || area <= 0d) {
                return PropertyListingMetadata.empty();
            }
            long firstPublished = 0L;
            long lastPublished = 0L;
            JSONArray listings = info.optJSONArray("listings");
            for (int index = 0; listings != null && index < listings.length(); index++) {
                JSONObject listing = listings.optJSONObject(index);
                if (listing == null || !"SALE".equals(listing.optString("businessContext"))) {
                    continue;
                }
                String id = listing.optString("imovelId", "");
                if (!id.isEmpty() && !expectedId.equals(id)) {
                    continue;
                }
                String status = listing.optString("status", "");
                if ("unpublished".equals(status) || "despublicado".equals(status)) {
                    return PropertyListingMetadata.unavailable(expectedId);
                }
                firstPublished = parseDate(listing.optString("firstPublicationDate"));
                lastPublished = parseDate(listing.optString("lastPublicationDate"));
                break;
            }
            return PropertyListingMetadata.verified(expectedId, price, area,
                    firstPublished, lastPublished);
        } catch (Exception ignored) {
            return PropertyListingMetadata.empty();
        }
    }

    private static boolean containsTag(JSONArray tags, String expected) {
        if (tags == null) {
            return false;
        }
        for (int index = 0; index < tags.length(); index++) {
            if (expected.equals(tags.optString(index))) {
                return true;
            }
        }
        return false;
    }

    private static long parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX"
        };
        for (String pattern : patterns) {
            try {
                return new java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                        .parse(value).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private static JSONObject findObjectContainingArray(Object value, String key) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.optJSONArray(key) != null) {
                return object;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                JSONObject found = findObjectContainingArray(object.opt(keys.next()), key);
                if (found != null) {
                    return found;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                JSONObject found = findObjectContainingArray(array.opt(index), key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String findString(Object value, String key) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Object directValue = object.opt(key);
            String direct = directValue instanceof String
                    || directValue instanceof Number
                    || directValue instanceof Boolean
                    ? String.valueOf(directValue) : "";
            if (!direct.trim().isEmpty()) {
                return direct;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String found = findString(object.opt(keys.next()), key);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                String found = findString(array.opt(index), key);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

}
