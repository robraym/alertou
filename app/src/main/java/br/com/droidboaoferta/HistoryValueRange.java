package br.com.droidboaoferta;

final class HistoryValueRange {
    private final double minimum;
    private final double maximum;

    HistoryValueRange(double[] values) {
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (double value : values) if (Double.isFinite(value)) {
            low = Math.min(low, value);
            high = Math.max(high, value);
        }
        minimum = low;
        maximum = high;
    }

    // Equal readings have no distinct low/high; keep their normal color.
    int position(double value) {
        if (!Double.isFinite(value) || !(minimum < maximum)) return 0;
        if (value == minimum) return -1;
        if (value == maximum) return 1;
        return 0;
    }
}
