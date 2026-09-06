package br.com.droidboaoferta;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reuses unchanged cards so updates preserve scroll, focus and swipe state. */
final class OfferSectionCache {
    private final Map<String, View> views = new HashMap<>();
    private final Map<String, String> fingerprints = new HashMap<>();
    private final Set<String> visible = new HashSet<>();
    private int position;

    void begin() { visible.clear(); position = 0; }
    View find(String key, String fingerprint) {
        return fingerprint.equals(fingerprints.get(key)) ? views.get(key) : null;
    }
    void attach(LinearLayout container, String key, String fingerprint, View view) {
        View previous = views.put(key, view);
        if (previous != null && previous != view) container.removeView(previous);
        fingerprints.put(key, fingerprint);
        visible.add(key);
        if (container.indexOfChild(view) != position) {
            container.removeView(view);
            container.addView(view, Math.min(position, container.getChildCount()));
        }
        position++;
    }
    void end(LinearLayout container) {
        for (String key : new HashSet<>(views.keySet())) {
            if (!visible.contains(key)) {
                container.removeView(views.remove(key));
                fingerprints.remove(key);
            }
        }
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            if (!views.containsValue(container.getChildAt(i))) container.removeViewAt(i);
        }
    }
    void clear() { views.clear(); fingerprints.clear(); }

    static String fingerprint(Context context, List<ObservedOffer> offers,
                              PropertyHistoryRepository history, boolean expanded, String summary) {
        StringBuilder key = new StringBuilder(OfferStorage.encode(offers));
        key.append(expanded).append(summary).append(OfferDateFormatter.getGroupKey(System.currentTimeMillis()));
        GroupSpeedRepository speed = new GroupSpeedRepository(context);
        for (ObservedOffer offer : offers) {
            key.append('|').append(speed.isOfferExpired(offer));
            PropertyHistoryEntry entry = history.getForOffer(offer);
            if (entry != null) {
                key.append('|').append(entry.isUnavailable()).append('|').append(entry.isPendingValidation())
                        .append('|').append(entry.getLatestPrice(offer.getPrice()))
                        .append('|').append(entry.getLatestPriceChangeAmount())
                        .append('|').append(entry.getLatestPriceChangePercentage())
                        .append('|').append(entry.getFirstPublicationAt())
                        .append('|').append(entry.isRecent(System.currentTimeMillis()));
            }
        }
        return key.toString();
    }
}
