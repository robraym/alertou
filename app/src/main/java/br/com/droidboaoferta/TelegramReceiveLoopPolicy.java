package br.com.droidboaoferta;

/**
 * Keeps the TDLib receiver responsive without depending on a native timed wait.
 *
 * <p>The bundled native receiver was observed consuming a CPU core while waiting with a
 * positive timeout. A non-blocking read followed by a short Java sleep when the queue is empty
 * preserves immediate draining of available updates and gives the CPU a real idle period.</p>
 */
final class TelegramReceiveLoopPolicy {
    static final double RECEIVE_TIMEOUT_SECONDS = 0.0d;
    static final long EMPTY_QUEUE_DELAY_MILLIS = 50L;

    private TelegramReceiveLoopPolicy() {
    }

    static boolean isEmptyResult(String result) {
        return result == null || result.isEmpty();
    }
}
