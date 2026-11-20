package br.com.droidboaoferta;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;

/** One shared product budget for Telegram and every store monitor. */
final class ProductNotificationLimiter {
    static final int MAX_PER_PRODUCT = 3;
    private static final long PROMOTION_WINDOW_MS = 12L * 60 * 60 * 1000;
    private static final String PRODUCT = "alertou_product";

    static boolean post(Context context, ObservedOffer offer, Notification notification) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        synchronized (OfferStorage.LOCK) {
            String product = OfferTextParser.normalize(offer.getInterest());
            // Migrate old per-message notifications; the three stable slots below are shared
            // by all sources and also prevent accumulation across price changes or restarts.
            for (StatusBarNotification active : manager.getActiveNotifications()) {
                Notification old = active.getNotification();
                CharSequence title = old.extras.getCharSequence(Notification.EXTRA_TITLE);
                if (!product.equals(old.extras.getString(PRODUCT, ""))
                        && (title == null || !product.equals(OfferTextParser.normalize(title.toString())))) continue;
                boolean slot = false;
                for (int i = 0; i < MAX_PER_PRODUCT; i++) if (active.getId() == notificationId(product, i)) slot = true;
                if (!slot) manager.cancel(active.getTag(), active.getId());
            }
            int slot = reserveSlot(context.getSharedPreferences("product_notification_limits", Context.MODE_PRIVATE),
                    offer, System.currentTimeMillis());
            if (slot < 0) return false;
            notification.extras.putString(PRODUCT, product);
            manager.notify(notificationId(product, slot), notification);
            return true;
        }
    }

    private static int notificationId(String product, int slot) { return ("product|" + product + "|" + slot).hashCode(); }

    static int reserveSlot(SharedPreferences preferences, ObservedOffer offer, long now) {
        synchronized (OfferStorage.LOCK) {
            String product = OfferTextParser.normalize(offer.getInterest());
            try {
                JSONObject state = new JSONObject(preferences.getString(product, "{}"));
                long price = Math.round(offer.getPrice());
                long started = state.optLong("started", 0);
                if (started <= 0 || now < started || now - started >= PROMOTION_WINDOW_MS
                        || price < state.optLong("price", Long.MAX_VALUE)) {
                    state = new JSONObject().put("started", now).put("price", price).put("sources", new JSONArray());
                }
                JSONArray sources = state.optJSONArray("sources");
                if (sources == null) sources = new JSONArray();
                String source = OfferTextParser.normalize(offer.getSource());
                for (int i = 0; i < sources.length(); i++) if (source.equals(sources.optString(i))) return -1;
                if (sources.length() >= MAX_PER_PRODUCT) return -1;
                int slot = sources.length();
                sources.put(source);
                state.put("sources", sources);
                preferences.edit().putString(product, state.toString()).apply();
                return slot;
            } catch (Exception invalid) {
                return -1;
            }
        }
    }
}
