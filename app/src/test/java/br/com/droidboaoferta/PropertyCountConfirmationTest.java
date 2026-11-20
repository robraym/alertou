package br.com.droidboaoferta;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class PropertyCountConfirmationTest {
    private PropertyPageResult count(int value) {
        return new PropertyPageResult("Santos Dumont", Collections.emptyList(), value, true);
    }

    @Test public void rejectsAlternatingSmallChangesInEitherDirection() {
        assertFalse(PropertyCountConfirmation.accept(10, count(9), () -> count(10)));
        assertFalse(PropertyCountConfirmation.accept(9, count(10), () -> count(9)));
        assertTrue(PropertyCountConfirmation.accept(10, count(9), () -> count(9)));
        assertTrue(PropertyCountConfirmation.accept(9, count(10), () -> count(10)));
    }

    @Test public void requiresConfirmationForInitialAndZeroCounts() {
        assertFalse(PropertyCountConfirmation.accept(-1, count(0), () -> count(10)));
        assertTrue(PropertyCountConfirmation.accept(-1, count(0), () -> count(0)));
        assertFalse(PropertyCountConfirmation.accept(10, count(0), () -> count(10)));
    }

    @Test public void preservesLastConfirmedCountOnFailuresAndWrongCondominium() {
        assertFalse(PropertyCountConfirmation.accept(10, count(9), () -> { throw new Exception("timeout"); }));
        assertFalse(PropertyCountConfirmation.accept(10, count(9), () ->
                new PropertyPageResult("Outro condomínio", Collections.emptyList(), 9, true)));
        assertFalse(PropertyCountConfirmation.accept(10, count(9), () ->
                new PropertyPageResult("Santos Dumont", Collections.emptyList(), 9, false)));
    }

    @Test public void unchangedCountsDoNotTriggerAnExtraRequest() {
        assertTrue(PropertyCountConfirmation.accept(10, count(10), () -> { fail("Unnecessary request"); return null; }));
    }

    @Test public void rejectsTotalsBelowTheReturnedListings() {
        PropertyPageListing listing = new PropertyPageListing("id", 30, 300000, "", "", false);
        assertFalse(PropertyCountConfirmation.accept(2,
                new PropertyPageResult("Santos Dumont", Collections.singletonList(listing), 0, true),
                () -> count(0)));
    }
}
