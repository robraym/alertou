package br.com.droidboaoferta;

final class PropertyMarketListingCountPoint {
    private final long observedAt;
    private final int count;
    private final boolean confirmed;
    private boolean suspicious;

    PropertyMarketListingCountPoint(long observedAt, int count) {
        this(observedAt, count, true);
    }

    PropertyMarketListingCountPoint(long observedAt, int count, boolean confirmed) {
        this.observedAt = observedAt;
        this.count = count;
        this.confirmed = confirmed;
    }

    boolean isConfirmed() { return confirmed; }
    boolean isSuspicious() { return suspicious; }
    void markSuspicious() { suspicious = true; }

    long getObservedAt() {
        return observedAt;
    }

    int getCount() {
        return count;
    }
}
