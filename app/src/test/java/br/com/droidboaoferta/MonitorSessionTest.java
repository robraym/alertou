package br.com.droidboaoferta;

import org.junit.Test;
import static org.junit.Assert.*;

public class MonitorSessionTest {
    @Test public void pauseThenResumeNeverRevivesAnOldCallback() {
        MonitorSession session = new MonitorSession();
        long pending = session.token();
        assertTrue(session.accepts(pending, true, true));
        session.invalidate();
        assertFalse(session.accepts(pending, false, true));
        assertFalse(session.accepts(pending, true, true));
        assertTrue(session.accepts(session.token(), true, true));
    }
    @Test public void deselectionAndPauseBlockPublication() {
        MonitorSession session = new MonitorSession();
        assertFalse(session.accepts(session.token(), true, false));
        assertFalse(session.accepts(session.token(), false, true));
    }
}
