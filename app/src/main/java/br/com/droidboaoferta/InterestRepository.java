package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class InterestRepository {
    private static final String PREFS = "offer_preferences";
    private static final String KEY_INTERESTS = "interests";

    private final Context context;
    private final SharedPreferences preferences;

    InterestRepository(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<Interest> getAll() {
        String stored = preferences.getString(KEY_INTERESTS, "[]");
        List<Interest> interests = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(stored);
            for (int index = 0; index < array.length(); index++) {
                try {
                    JSONObject item = array.getJSONObject(index);
                    interests.add(new Interest(
                            item.getLong("id"),
                            item.getString("term"),
                            item.getDouble("maximum_price"),
                            item.optString("type", Interest.TYPE_PRICE),
                            item.optDouble("minimum_area", 0d),
                            item.optDouble("maximum_area", 0d),
                            item.optString("property_name", ""),
                            item.optString("coupon_name", ""),
                            item.optString("property_zip_code", ""),
                            item.optString("property_street", ""),
                            item.optString("property_neighborhood", ""),
                            item.optString("property_city", ""),
                            item.optString("property_state", "")
                    ));
                } catch (Exception ignored) {
                    // A corrupt synchronized item must not hide every valid alert.
                }
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        interests.sort((first, second) -> Long.compare(second.getId(), first.getId()));
        return interests;
    }

    long add(String term, double maximumPrice) {
        return add(term, maximumPrice, Interest.TYPE_PRICE);
    }

    long addCoupon(String pageUrl, double minimumCouponValue) {
        return addCoupon(pageUrl, minimumCouponValue, "");
    }

    long addCoupon(String pageUrl, double minimumCouponValue, String couponName) {
        return add(pageUrl, minimumCouponValue, Interest.TYPE_COUPON, 0d, 0d, "", couponName);
    }

    long addProperty(String pageUrl, double minimumArea, double maximumArea,
                     double maximumPrice, String propertyName) {
        return add(pageUrl, maximumPrice, Interest.TYPE_PROPERTY, minimumArea, maximumArea,
                propertyName);
    }

    long addPropertyZip(String searchUrl, String zipCode, String street, String neighborhood,
                        String city, String state, double minimumArea, double maximumArea,
                        double maximumPrice) {
        return add(searchUrl, maximumPrice, Interest.TYPE_PROPERTY_ZIP, minimumArea, maximumArea,
                buildPropertyZipName(street, neighborhood), "", zipCode, street, neighborhood,
                city, state);
    }

    private long add(String term, double maximumPrice, String type) {
        return add(term, maximumPrice, type, 0d, 0d, "", "");
    }

    private long add(String term, double maximumPrice, String type,
                     double minimumArea, double maximumArea, String propertyName) {
        return add(term, maximumPrice, type, minimumArea, maximumArea, propertyName, "");
    }

    private long add(String term, double maximumPrice, String type,
                     double minimumArea, double maximumArea, String propertyName,
                     String couponName) {
        return add(term, maximumPrice, type, minimumArea, maximumArea, propertyName, couponName,
                "", "", "", "", "");
    }

    private long add(String term, double maximumPrice, String type,
                     double minimumArea, double maximumArea, String propertyName,
                     String couponName, String propertyZipCode, String propertyStreet,
                     String propertyNeighborhood, String propertyCity, String propertyState) {
        List<Interest> interests = new ArrayList<>(getAll());
        long now = System.currentTimeMillis();
        long id = now;
        Interest added = new Interest(
                id, term.trim(), maximumPrice, type, minimumArea, maximumArea, propertyName,
                couponName, propertyZipCode, propertyStreet, propertyNeighborhood, propertyCity,
                propertyState);
        interests.add(0, added);
        CloudSyncStore.rememberInterestChanged(context, id, now);
        save(interests);
        CloudSyncStore.syncInterestChanged(context, null, added, now);
        return id;
    }

    void update(long id, String term, double maximumPrice) {
        List<Interest> interests = new ArrayList<>(getAll());
        long now = System.currentTimeMillis();
        Interest previous = null;
        Interest updated = null;
        for (int index = 0; index < interests.size(); index++) {
            Interest interest = interests.get(index);
            if (interest.getId() == id) {
                previous = interest;
                updated = new Interest(
                        id, term.trim(), maximumPrice, interest.getType(),
                        interest.getMinimumArea(), interest.getMaximumArea(),
                        interest.getPropertyName(), interest.getCouponName(),
                        interest.getPropertyZipCode(), interest.getPropertyStreet(),
                        interest.getPropertyNeighborhood(), interest.getPropertyCity(),
                        interest.getPropertyState());
                interests.set(index, updated);
                CloudSyncStore.rememberInterestChanged(context, id, now);
                break;
            }
        }
        save(interests);
        if (updated != null) {
            CloudSyncStore.syncInterestChanged(context, previous, updated, now);
        }
    }

    void updateProperty(long id, String pageUrl, double minimumArea,
                        double maximumArea, double maximumPrice, String propertyName) {
        List<Interest> interests = new ArrayList<>(getAll());
        long now = System.currentTimeMillis();
        Interest previous = null;
        Interest updated = null;
        for (int index = 0; index < interests.size(); index++) {
            Interest interest = interests.get(index);
            if (interest.getId() == id) {
                previous = interest;
                updated = new Interest(
                        id,
                        pageUrl.trim(),
                        maximumPrice,
                        Interest.TYPE_PROPERTY,
                        minimumArea,
                        maximumArea,
                        propertyName,
                        interest.getCouponName(),
                        interest.getPropertyZipCode(),
                        interest.getPropertyStreet(),
                        interest.getPropertyNeighborhood(),
                        interest.getPropertyCity(),
                        interest.getPropertyState()
                );
                interests.set(index, updated);
                CloudSyncStore.rememberInterestChanged(context, id, now);
                break;
            }
        }
        save(interests);
        if (updated != null) {
            CloudSyncStore.syncInterestChanged(context, previous, updated, now);
        }
    }

    void updatePropertyName(long id, String propertyName) {
        if (propertyName == null || propertyName.trim().isEmpty()) {
            return;
        }
        List<Interest> interests = new ArrayList<>(getAll());
        Interest previous = null;
        Interest updated = null;
        long now = System.currentTimeMillis();
        for (int index = 0; index < interests.size(); index++) {
            Interest interest = interests.get(index);
            if (interest.getId() == id && interest.isProperty()
                    && !propertyName.trim().equals(interest.getPropertyName())) {
                previous = interest;
                updated = new Interest(
                        interest.getId(), interest.getTerm(), interest.getMaximumPrice(),
                        interest.getType(), interest.getMinimumArea(), interest.getMaximumArea(),
                        propertyName, interest.getCouponName(), interest.getPropertyZipCode(),
                        interest.getPropertyStreet(), interest.getPropertyNeighborhood(),
                        interest.getPropertyCity(), interest.getPropertyState());
                interests.set(index, updated);
                CloudSyncStore.rememberInterestChanged(context, id, now);
                break;
            }
        }
        if (updated != null) {
            save(interests);
            CloudSyncStore.syncInterestChanged(context, previous, updated, now);
        }
    }

    void updateCoupon(long id, String pageUrl, double minimumCouponValue, String couponName) {
        List<Interest> interests = new ArrayList<>(getAll());
        long now = System.currentTimeMillis();
        Interest previous = null;
        Interest updated = null;
        for (int index = 0; index < interests.size(); index++) {
            Interest interest = interests.get(index);
            if (interest.getId() == id) {
                previous = interest;
                updated = new Interest(id, pageUrl.trim(), minimumCouponValue,
                        Interest.TYPE_COUPON, 0d, 0d, "", couponName);
                interests.set(index, updated);
                CloudSyncStore.rememberInterestChanged(context, id, now);
                break;
            }
        }
        save(interests);
        if (updated != null) {
            CloudSyncStore.syncInterestChanged(context, previous, updated, now);
        }
    }

    void updatePropertyZip(long id, String searchUrl, String zipCode, String street,
                           String neighborhood, String city, String state,
                           double minimumArea, double maximumArea, double maximumPrice) {
        List<Interest> interests = new ArrayList<>(getAll());
        long now = System.currentTimeMillis();
        Interest previous = null;
        Interest updated = null;
        for (int index = 0; index < interests.size(); index++) {
            Interest interest = interests.get(index);
            if (interest.getId() == id) {
                previous = interest;
                updated = new Interest(
                        id,
                        searchUrl.trim(),
                        maximumPrice,
                        Interest.TYPE_PROPERTY_ZIP,
                        minimumArea,
                        maximumArea,
                        buildPropertyZipName(street, neighborhood),
                        interest.getCouponName(),
                        zipCode,
                        street,
                        neighborhood,
                        city,
                        state
                );
                interests.set(index, updated);
                CloudSyncStore.rememberInterestChanged(context, id, now);
                break;
            }
        }
        save(interests);
        if (updated != null) {
            CloudSyncStore.syncInterestChanged(context, previous, updated, now);
        }
    }

    void remove(long id) {
        List<Interest> interests = new ArrayList<>(getAll());
        boolean removed = interests.removeIf(interest -> interest.getId() == id);
        long deletedAt = System.currentTimeMillis();
        CloudSyncStore.rememberInterestDeleted(context, id, deletedAt);
        save(interests);
        if (removed) {
            CloudSyncStore.syncInterestDeleted(context, id, deletedAt);
        }
    }

    private void save(List<Interest> interests) {
        JSONArray array = new JSONArray();
        try {
            for (Interest interest : interests) {
                array.put(new JSONObject()
                        .put("id", interest.getId())
                        .put("term", interest.getTerm())
                        .put("maximum_price", interest.getMaximumPrice())
                        .put("type", interest.getType())
                        .put("minimum_area", interest.getMinimumArea())
                        .put("maximum_area", interest.getMaximumArea())
                        .put("property_name", interest.getPropertyName())
                        .put("coupon_name", interest.getCouponName())
                        .put("property_zip_code", interest.getPropertyZipCode())
                        .put("property_street", interest.getPropertyStreet())
                        .put("property_neighborhood", interest.getPropertyNeighborhood())
                        .put("property_city", interest.getPropertyCity())
                        .put("property_state", interest.getPropertyState()));
            }
            preferences.edit().putString(KEY_INTERESTS, array.toString()).apply();
        } catch (Exception ignored) {
            // Os valores são primitivos e não devem falhar ao serem serializados.
        }
    }

    private static String buildPropertyZipName(String street, String neighborhood) {
        String cleanStreet = street == null ? "" : street.trim();
        String cleanNeighborhood = neighborhood == null ? "" : neighborhood.trim();
        if (cleanStreet.isEmpty()) {
            return cleanNeighborhood;
        }
        if (cleanNeighborhood.isEmpty()) {
            return cleanStreet;
        }
        return cleanStreet + ", " + cleanNeighborhood;
    }
}
