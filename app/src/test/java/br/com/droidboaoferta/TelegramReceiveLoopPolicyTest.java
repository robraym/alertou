package br.com.droidboaoferta;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TelegramReceiveLoopPolicyTest {
    @Test
    public void waitsOnlyWhenTdlibHasNoUpdate() {
        assertTrue(TelegramReceiveLoopPolicy.isEmptyResult(null));
        assertTrue(TelegramReceiveLoopPolicy.isEmptyResult(""));
        assertFalse(TelegramReceiveLoopPolicy.isEmptyResult(
                "{\"@type\":\"updateNewMessage\"}"));
    }

    @Test
    public void idlePollingKeepsNotificationLatencyBelowOneTenthOfASecond() {
        assertTrue(TelegramReceiveLoopPolicy.EMPTY_QUEUE_DELAY_MILLIS > 0L);
        assertTrue(TelegramReceiveLoopPolicy.EMPTY_QUEUE_DELAY_MILLIS < 100L);
        assertTrue(TelegramReceiveLoopPolicy.RECEIVE_TIMEOUT_SECONDS == 0.0d);
    }
}
