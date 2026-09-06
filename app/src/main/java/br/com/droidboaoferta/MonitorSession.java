package br.com.droidboaoferta;

import java.util.concurrent.atomic.AtomicLong;

/** A callback from before a pause must remain obsolete even after monitoring resumes. */
final class MonitorSession {
    private final AtomicLong generation = new AtomicLong();
    long token() { return generation.get(); }
    void invalidate() { generation.incrementAndGet(); }
    boolean accepts(long token, boolean enabled, boolean selected) {
        return token == generation.get() && enabled && selected;
    }
}
