package br.com.droidboaoferta;

/** One confirmed purchase-rate reading from Tesouro Direto. */
final class TreasuryRatePoint {
    private final long observedAt;
    private final double rate;

    TreasuryRatePoint(long observedAt, double rate) {
        this.observedAt = observedAt;
        this.rate = rate;
    }

    long getObservedAt() { return observedAt; }
    double getRate() { return rate; }
}
