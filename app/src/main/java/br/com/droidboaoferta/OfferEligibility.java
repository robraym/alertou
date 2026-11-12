package br.com.droidboaoferta;

final class OfferEligibility {
    static final long MAX_HISTORY_AGE_MS = 90L * 24L * 60L * 60L * 1000L;
    // A reconnect may replay recent Telegram updates. They can remain available in history,
    // but a phone alert delivered long after the publication is misleading.
    static final long MAX_NOTIFICATION_DELAY_MS = 15L * 60L * 1000L;

    private OfferEligibility() {
    }

    static boolean isRecent(long observedAt, long now) {
        return observedAt > 0L
                && observedAt <= now
                && now - observedAt <= MAX_HISTORY_AGE_MS;
    }

    static boolean hasUsableLink(String link) {
        if (link == null) {
            return false;
        }
        String cleanLink = link.trim().toLowerCase(java.util.Locale.ROOT);
        return cleanLink.startsWith("https://") || cleanLink.startsWith("http://");
    }

    static boolean isTimelyForNotification(long observedAt, long now) {
        return observedAt > 0L && observedAt <= now
                && now - observedAt <= MAX_NOTIFICATION_DELAY_MS;
    }

    static boolean canDisplay(ObservedOffer offer, long now) {
        return offer != null
                && isRecent(offer.getObservedAt(), now)
                && hasUsableLink(offer.getLink());
    }
}
