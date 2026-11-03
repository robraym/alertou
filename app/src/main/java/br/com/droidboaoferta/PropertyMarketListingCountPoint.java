package br.com.droidboaoferta;

final class PropertyMarketListingCountPoint {
    private final long observedAt;
    private final int count;

    PropertyMarketListingCountPoint(long observedAt, int count) {
        this.observedAt = observedAt;
        this.count = count;
    }

    long getObservedAt() {
        return observedAt;
    }

    int getCount() {
        return count;
    }
}
