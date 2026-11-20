package br.com.droidboaoferta;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HistoryValueRangeTest {
    @Test public void colorsEveryTiedExtremeAndKeepsIntermediateNeutral() {
        HistoryValueRange range = new HistoryValueRange(new double[]{7.5, 7.2, 7.9, 7.2, 7.9});
        assertEquals(-1, range.position(7.2));
        assertEquals(1, range.position(7.9));
        assertEquals(0, range.position(7.5));
    }

    @Test public void emptyAndUnchangedHistoriesHaveNoDistinctExtremes() {
        assertEquals(0, new HistoryValueRange(new double[]{}).position(5));
        assertEquals(0, new HistoryValueRange(new double[]{5}).position(5));
        assertEquals(0, new HistoryValueRange(new double[]{5, 5}).position(5));
    }

    @Test public void ignoresMissingReadingsAndAcceptsZeroCounts() {
        HistoryValueRange range = new HistoryValueRange(new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, 3});
        assertEquals(0, range.position(Double.NaN));
        assertEquals(-1, range.position(0));
        assertEquals(1, range.position(3));
    }
}
