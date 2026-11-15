package br.com.droidboaoferta;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class PropertyMarketListingCountHistoryTest {
    @Test public void sumsOnlyReadingsAlreadyKnownAtEachTimestamp() {
        PropertyMarketListingCountHistory total = PropertyMarketListingCountHistory.sumLatestKnown(Arrays.asList(
                history(point(10, 9), point(30, 8)),
                history(point(20, 59), point(40, 60))));
        assertEquals(3, total.getPoints().size());
        assertPoint(total, 0, 20, 68);
        assertPoint(total, 1, 30, 67);
        assertPoint(total, 2, 40, 68);
    }

    @Test public void missingFirstReadingNeverBecomesAnInventedZero() {
        assertTrue(PropertyMarketListingCountHistory.sumLatestKnown(Arrays.asList(
                history(point(10, 9)), history())).getPoints().isEmpty());
        assertTrue(PropertyMarketListingCountHistory.sumLatestKnown(Collections.emptyList()).getPoints().isEmpty());
        PropertyMarketListingCountHistory total = PropertyMarketListingCountHistory.sumLatestKnown(Arrays.asList(
                history(point(10, 9), point(30, 8)), history(point(40, 0))));
        assertEquals(1, total.getPoints().size());
        assertPoint(total, 0, 40, 8);
    }

    @Test public void simultaneousReadingsDoNotProduceArtificialIntermediateChanges() {
        PropertyMarketListingCountHistory total = PropertyMarketListingCountHistory.sumLatestKnown(Arrays.asList(
                history(point(10, 9), point(20, 10)),
                history(point(10, 59), point(20, 58))));
        assertEquals(1, total.getPoints().size());
        assertPoint(total, 0, 10, 68);
    }

    @Test public void latestTotalMatchesAllFiveDisplayedCondominiumBadges() {
        PropertyMarketListingCountHistory first = history(point(10, 9), point(30, 10));
        PropertyMarketListingCountHistory total = PropertyMarketListingCountHistory.sumLatestKnown(Arrays.asList(
                first, history(point(10, 59)), history(point(10, 3)),
                history(point(10, 50)), history(point(10, 53))));
        assertPoint(total, 0, 10, 174);
        assertEquals(175, total.getLatestCount());
        assertEquals(1, total.getLatestChange());
        assertEquals(2, first.getPoints().size());
        assertEquals(10, first.getLatestCount());
    }

    private static PropertyMarketListingCountPoint point(long at, int count) {
        return new PropertyMarketListingCountPoint(at, count);
    }

    private static PropertyMarketListingCountHistory history(PropertyMarketListingCountPoint... points) {
        return new PropertyMarketListingCountHistory(Arrays.asList(points));
    }

    private static void assertPoint(PropertyMarketListingCountHistory history, int index, long at, int count) {
        assertEquals(at, history.getPoints().get(index).getObservedAt());
        assertEquals(count, history.getPoints().get(index).getCount());
    }
}
