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
}
