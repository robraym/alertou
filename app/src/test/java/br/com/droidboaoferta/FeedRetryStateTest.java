package br.com.droidboaoferta;

import android.content.SharedPreferences;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedRetryStateTest {
    @Test public void failedDetailsRemainRetryableAfterProcessRestartAndUnchangedFeed() {
        SharedPreferences preferences = TestPreferences.create();
        FeedRetryState state = new FeedRetryState(preferences);
        state.begin();
        state.complete("same-feed", false);
        FeedRetryState restarted = new FeedRetryState(preferences);
        assertTrue(restarted.needsRetry());
        assertTrue(restarted.shouldProcess("same-feed", false));
        restarted.complete("same-feed", true);
        assertFalse(restarted.needsRetry());
        assertFalse(restarted.shouldProcess("same-feed", false));
        assertTrue(restarted.shouldProcess("new-feed", false));
    }
}
