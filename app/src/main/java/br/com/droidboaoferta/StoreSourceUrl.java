package br.com.droidboaoferta;

import java.net.URI;
import java.util.Locale;

/** A manually configured source must not be restricted to a previously known domain or path. */
final class StoreSourceUrl {
    private StoreSourceUrl() {
    }

    static String normalize(String rawUrl) {
        if (rawUrl == null) return null;
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("https".equals(scheme) || "http".equals(scheme))
                    && uri.getHost() != null && !uri.getHost().trim().isEmpty()
                    ? uri.normalize().toString() : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
