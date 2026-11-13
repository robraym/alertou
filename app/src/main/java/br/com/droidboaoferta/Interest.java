package br.com.droidboaoferta;

import java.util.Locale;

final class Interest {
    static final String TYPE_PRICE = "price";
    static final String TYPE_COUPON = "coupon";
    static final String TYPE_PROPERTY = "property";
    static final String TYPE_TREASURY = "treasury";
    static final int DEFAULT_PROPERTY_LISTING_LIMIT = 20;

    private final long id;
    private final String term;
    private final double maximumPrice;
    private final String type;
    private final double minimumArea;
    private final double maximumArea;
    private final String propertyName;
    private final String couponName;
    private final int propertyListingLimit;

    Interest(long id, String term, double maximumPrice) {
        this(id, term, maximumPrice, TYPE_PRICE);
    }

    Interest(long id, String term, double maximumPrice, String type) {
        this(id, term, maximumPrice, type, 0d, 0d);
    }

    Interest(long id, String term, double maximumPrice, String type,
             double minimumArea, double maximumArea) {
        this(id, term, maximumPrice, type, minimumArea, maximumArea, "");
    }

    Interest(long id, String term, double maximumPrice, String type,
             double minimumArea, double maximumArea, String propertyName) {
        this(id, term, maximumPrice, type, minimumArea, maximumArea, propertyName, "");
    }

    Interest(long id, String term, double maximumPrice, String type,
             double minimumArea, double maximumArea, String propertyName, String couponName) {
        this(id, term, maximumPrice, type, minimumArea, maximumArea, propertyName, couponName,
                DEFAULT_PROPERTY_LISTING_LIMIT);
    }

    Interest(long id, String term, double maximumPrice, String type,
             double minimumArea, double maximumArea, String propertyName, String couponName,
             int propertyListingLimit) {
        this.id = id;
        this.term = term;
        this.maximumPrice = maximumPrice;
        if (!isSupportedType(type)) throw new IllegalArgumentException("Unsupported alert type");
        this.type = type;
        this.minimumArea = minimumArea;
        this.maximumArea = maximumArea;
        this.propertyName = propertyName == null ? "" : propertyName.trim();
        this.couponName = couponName == null ? "" : couponName.trim();
        this.propertyListingLimit = Math.max(1, Math.min(100, propertyListingLimit));
    }

    static boolean isSupportedType(String type) {
        return TYPE_PRICE.equals(type) || TYPE_COUPON.equals(type)
                || TYPE_PROPERTY.equals(type) || TYPE_TREASURY.equals(type);
    }

    long getId() {
        return id;
    }

    String getTerm() {
        return term;
    }

    double getMaximumPrice() {
        return maximumPrice;
    }

    String getType() {
        return type;
    }

    boolean isCoupon() {
        return TYPE_COUPON.equals(type);
    }

    boolean isProperty() {
        return isPropertyCondominium();
    }

    boolean isPropertyCondominium() {
        return TYPE_PROPERTY.equals(type);
    }

    boolean isPrice() {
        return TYPE_PRICE.equals(type);
    }

    boolean isTreasury() {
        return TYPE_TREASURY.equals(type);
    }

    /** For Tesouro Direto alerts, this persisted numeric limit is the minimum purchase rate. */
    double getMinimumTreasuryRate() {
        return maximumPrice;
    }

    double getMinimumArea() {
        return minimumArea;
    }

    double getMaximumArea() {
        return maximumArea;
    }

    String getPropertyName() {
        return propertyName;
    }

    String getCouponName() {
        return couponName;
    }

    /** Maximum listings processed and shown for this property alert. */
    int getPropertyListingLimit() {
        return propertyListingLimit;
    }
}
