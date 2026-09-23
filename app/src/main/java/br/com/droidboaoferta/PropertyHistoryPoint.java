package br.com.droidboaoferta;

final class PropertyHistoryPoint {
    private final long observedAt;
    private final double price;
    private final double area;
    private final boolean previousReference;

    PropertyHistoryPoint(long observedAt, double price, double area) {
        this(observedAt, price, area, false);
    }

    PropertyHistoryPoint(long observedAt, double price, double area,
                         boolean previousReference) {
        this.observedAt = observedAt;
        this.price = price;
        this.area = area;
        this.previousReference = previousReference;
    }

    long getObservedAt() {
        return observedAt;
    }

    double getPrice() {
        return price;
    }

    double getArea() {
        return area;
    }

    boolean isPreviousReference() {
        return previousReference;
    }
}
