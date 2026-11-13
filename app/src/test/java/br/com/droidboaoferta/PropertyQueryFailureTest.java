package br.com.droidboaoferta;

import org.junit.Test;
import static org.junit.Assert.*;

public class PropertyQueryFailureTest {
    @Test public void futureWrapperDoesNotHideTimeout() {
        java.net.SocketTimeoutException timeout = new java.net.SocketTimeoutException("Read timed out");
        Exception wrapped = new java.util.concurrent.ExecutionException(
                new PropertyQueryFailure.Verification(timeout, 2));
        assertSame(timeout, PropertyQueryFailure.cause(wrapped));
        assertTrue(PropertyQueryFailure.retryable(wrapped));
        assertEquals("SocketTimeoutException: Read timed out", PropertyQueryFailure.detail(wrapped));
    }

    @Test public void retriesTemporaryHttpFailuresButNotPermanentOrIdentityErrors() {
        assertTrue(PropertyQueryFailure.retryable(new IllegalStateException("HTTP 503")));
        assertTrue(PropertyQueryFailure.retryable(new IllegalStateException("HTTP 429")));
        assertFalse(PropertyQueryFailure.retryable(new IllegalStateException("HTTP 404")));
        assertFalse(PropertyQueryFailure.retryable(new IllegalStateException("Listing identity could not be confirmed")));
    }

    @Test public void diagnosticPreservesCauseAndBoundsStoredMessageSize() {
        Exception error = new IllegalStateException("x".repeat(1000));
        assertEquals(400, PropertyQueryFailure.detail(error).length());
    }
}
