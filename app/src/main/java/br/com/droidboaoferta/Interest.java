package br.com.droidboaoferta;

final class Interest {
    static final String TYPE_PRICE = "price";
    static final String TYPE_COUPON = "coupon";
    static final String TYPE_PROPERTY = "property";
    static final String TYPE_PROPERTY_ZIP = "property_zip";

    private final long id;
    private final String term;
    private final double maximumPrice;
    private final String type;
    private final double minimumArea;
    private final double maximumArea;
    private final String propertyName;
    private final String couponName;
    private final String propertyZipCode;
    private final String propertyStreet;
    private final String propertyNeighborhood;
    private final String propertyCity;
    private final String propertyState;

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
                "", "", "", "", "");
    }

    Interest(long id, String term, double maximumPrice, String type,
             double minimumArea, double maximumArea, String propertyName, String couponName,
             String propertyZipCode, String propertyStreet, String propertyNeighborhood,
             String propertyCity, String propertyState) {
        this.id = id;
        this.term = term;
        this.maximumPrice = maximumPrice;
        this.type = TYPE_COUPON.equals(type)
                ? TYPE_COUPON
                : (TYPE_PROPERTY.equals(type)
                ? TYPE_PROPERTY
                : (TYPE_PROPERTY_ZIP.equals(type) ? TYPE_PROPERTY_ZIP : TYPE_PRICE));
        this.minimumArea = minimumArea;
        this.maximumArea = maximumArea;
        this.propertyName = propertyName == null ? "" : propertyName.trim();
        this.couponName = couponName == null ? "" : couponName.trim();
        this.propertyZipCode = propertyZipCode == null ? "" : propertyZipCode.trim();
        this.propertyStreet = propertyStreet == null ? "" : propertyStreet.trim();
        this.propertyNeighborhood = propertyNeighborhood == null
                ? "" : propertyNeighborhood.trim();
        this.propertyCity = propertyCity == null ? "" : propertyCity.trim();
        this.propertyState = propertyState == null ? "" : propertyState.trim().toUpperCase();
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
        return isPropertyCondominium() || isPropertyZip();
    }

    boolean isPropertyCondominium() {
        return TYPE_PROPERTY.equals(type);
    }

    boolean isPropertyZip() {
        return TYPE_PROPERTY_ZIP.equals(type);
    }

    boolean isPrice() {
        return TYPE_PRICE.equals(type);
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

    String getPropertyZipCode() {
        return propertyZipCode;
    }

    String getPropertyStreet() {
        return propertyStreet;
    }

    String getPropertyNeighborhood() {
        return propertyNeighborhood;
    }

    String getPropertyCity() {
        return propertyCity;
    }

    String getPropertyState() {
        return propertyState;
    }
}
