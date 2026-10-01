package br.com.droidboaoferta;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TelegramRecoveryRequestPolicyTest {
    @Test
    public void requestsRecoveryOnlyOnceForTheSameSelection() {
        Set<String> groups = groups("10", "20");

        assertTrue(TelegramRecoveryRequestPolicy.shouldRequest(
                false, Collections.emptySet(), groups));
        assertFalse(TelegramRecoveryRequestPolicy.shouldRequest(
                true, groups, groups("10", "20")));
    }

    @Test
    public void requestsRecoveryWhenSelectedGroupsChange() {
        assertTrue(TelegramRecoveryRequestPolicy.shouldRequest(
                true, groups("10", "20"), groups("10", "30")));
    }

    @Test
    public void doesNotRequestRecoveryWithoutSelectedGroups() {
        assertFalse(TelegramRecoveryRequestPolicy.shouldRequest(
                false, Collections.emptySet(), Collections.emptySet()));
    }

    private static Set<String> groups(String... ids) {
        Set<String> groups = new HashSet<>();
        Collections.addAll(groups, ids);
        return groups;
    }
}
