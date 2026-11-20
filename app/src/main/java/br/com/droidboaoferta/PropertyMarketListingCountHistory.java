package br.com.droidboaoferta;

import java.util.Collections;
import java.util.List;

final class PropertyMarketListingCountHistory {
    private final List<PropertyMarketListingCountPoint> points;

    PropertyMarketListingCountHistory(List<PropertyMarketListingCountPoint> points) {
        this.points = points == null ? Collections.emptyList() : Collections.unmodifiableList(points);
        markRapidLegacyAlternation();
        markRapidLegacyReturns();
    }

    private void markRapidLegacyReturns() {
        for (int i = 1; i + 1 < points.size(); i++) {
            PropertyMarketListingCountPoint before = points.get(i - 1);
            PropertyMarketListingCountPoint current = points.get(i);
            PropertyMarketListingCountPoint after = points.get(i + 1);
            long firstGap = current.getObservedAt() - before.getObservedAt();
            long secondGap = after.getObservedAt() - current.getObservedAt();
            if (!current.isConfirmed() && !before.isSuspicious() && !after.isSuspicious()
                    && before.getCount() == after.getCount() && current.getCount() != before.getCount()
                    && firstGap > 0 && secondGap > 0
                    && Math.min(firstGap, secondGap) <= 15L * 60 * 1000) {
                current.markSuspicious();
            }
        }
    }

    private void markRapidLegacyAlternation() {
        // Alternating legacy samples with a rapid transition within 15 minutes
        // form a suspect sequence. Independently confirmed readings are never reclassified.
        for (int start = 0; start + 5 < points.size(); start++) {
            PropertyMarketListingCountPoint first = points.get(start);
            PropertyMarketListingCountPoint second = points.get(start + 1);
            if (first.isConfirmed() || second.isConfirmed() || first.getCount() == second.getCount()) continue;
            int end = start + 1;
            int rapidGaps = 0;
            for (int i = start + 1; i < points.size(); i++) {
                PropertyMarketListingCountPoint current = points.get(i);
                long gap = current.getObservedAt() - points.get(i - 1).getObservedAt();
                if (current.isConfirmed() || gap <= 0 || gap > 36L * 60 * 60 * 1000
                        || (i > start + 1 && current.getCount() != points.get(i - 2).getCount())) break;
                if (gap <= 15L * 60 * 1000) rapidGaps++;
                end = i;
            }
            if (end - start + 1 >= 6 && rapidGaps > 0) {
                for (int i = start; i <= end; i++) points.get(i).markSuspicious();
                start = end;
            }
        }
    }

    List<PropertyMarketListingCountPoint> getChartPoints() {
        List<PropertyMarketListingCountPoint> result = new java.util.ArrayList<>();
        for (PropertyMarketListingCountPoint point : points) if (!point.isSuspicious()) result.add(point);
        return result;
    }

    int getSuspiciousCount() { return points.size() - getChartPoints().size(); }

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
            if (history == null || history.getChartPoints().isEmpty()) return new PropertyMarketListingCountHistory(totals);
            for (PropertyMarketListingCountPoint point : history.getChartPoints()) {
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
