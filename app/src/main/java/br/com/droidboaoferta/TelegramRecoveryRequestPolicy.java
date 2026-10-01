package br.com.droidboaoferta;

import java.util.Collections;
import java.util.Set;

/** Prevents service refreshes from repeatedly scanning the same Telegram groups. */
final class TelegramRecoveryRequestPolicy {
    private TelegramRecoveryRequestPolicy() {
    }

    static boolean shouldRequest(boolean initialized,
                                 Set<String> previousGroups,
                                 Set<String> selectedGroups) {
        Set<String> previous = previousGroups == null
                ? Collections.emptySet() : previousGroups;
        Set<String> selected = selectedGroups == null
                ? Collections.emptySet() : selectedGroups;
        return !selected.isEmpty() && (!initialized || !selected.equals(previous));
    }
}
