package br.com.droidboaoferta;

import android.content.Context;

final class MonitorRunPolicy {
    private MonitorRunPolicy() { }

    static boolean canRun(Context context) {
        return context != null && !Thread.currentThread().isInterrupted()
                && CoalescingCheckScheduler.isCurrentRun()
                && MonitorServiceController.isEnabled(context);
    }

    static boolean isCurrent(Context context, Interest expected) {
        if (!canRun(context)) return false;
        for (Interest current : new InterestRepository(context).getAll()) {
            if (current.getId() == expected.getId()) {
                return current.getTerm().equals(expected.getTerm())
                        && current.getType().equals(expected.getType())
                        && Double.compare(current.getMaximumPrice(), expected.getMaximumPrice()) == 0
                        && Double.compare(current.getMinimumArea(), expected.getMinimumArea()) == 0
                        && Double.compare(current.getMaximumArea(), expected.getMaximumArea()) == 0;
            }
        }
        return false;
    }
}
