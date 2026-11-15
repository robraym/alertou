package br.com.droidboaoferta;

import java.util.Collections;
import java.util.List;

final class PropertyMarketListingCountHistory {
    private final List<PropertyMarketListingCountPoint> points;

    PropertyMarketListingCountHistory(List<PropertyMarketListingCountPoint> points) {
        this.points = points == null ? Collections.emptyList() : Collections.unmodifiableList(points);
    }

    List<PropertyMarketListingCountPoint> getPoints() {
        return points;
    }

    int getLatestCount() {
        return points.isEmpty() ? 0 : points.get(points.size() - 1).getCount();
    }

    int getLatestChange() {
        if (points.size() < 2) return 0;
        return getLatestCount() - points.get(points.size() - 2).getCount();
    }

    /** Sum of the latest confirmed component readings known at each recorded timestamp. */
    static PropertyMarketListingCountHistory sumLatestKnown(
            List<PropertyMarketListingCountHistory> histories) {
        List<PropertyMarketListingCountPoint> totals = new java.util.ArrayList<>();
        if (histories == null || histories.isEmpty()) return new PropertyMarketListingCountHistory(totals);
        java.util.TreeMap<Long, List<int[]>> readings = new java.util.TreeMap<>();
        for (int component = 0; component < histories.size(); component++) {
            PropertyMarketListingCountHistory history = histories.get(component);
            if (history == null || history.getPoints().isEmpty()) return new PropertyMarketListingCountHistory(totals);
            for (PropertyMarketListingCountPoint point : history.getPoints()) {
                if (point.getObservedAt() <= 0L || point.getCount() < 0) continue;
                readings.computeIfAbsent(point.getObservedAt(), ignored -> new java.util.ArrayList<>())
                        .add(new int[]{component, point.getCount()});
            }
        }
        int[] latest = new int[histories.size()];
        java.util.Arrays.fill(latest, -1);
        int known = 0;
        long total = 0L;
        for (java.util.Map.Entry<Long, List<int[]>> event : readings.entrySet()) {
            // Apply simultaneous readings together so their order cannot create false changes.
            for (int[] reading : event.getValue()) {
                int previous = latest[reading[0]];
                if (previous < 0) known++;
                total += reading[1] - (long) Math.max(0, previous);
                latest[reading[0]] = reading[1];
            }
            if (known == histories.size()) {
                if (total > Integer.MAX_VALUE) return new PropertyMarketListingCountHistory(Collections.emptyList());
                if (totals.isEmpty() || totals.get(totals.size() - 1).getCount() != total) {
                    totals.add(new PropertyMarketListingCountPoint(event.getKey(), (int) total));
                }
            }
        }
        return new PropertyMarketListingCountHistory(totals);
    }
}
