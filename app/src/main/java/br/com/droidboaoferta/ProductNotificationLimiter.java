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
    private static final String OFFER_ID = "alertou_offer_id";

    static boolean post(Context context, ObservedOffer offer, Notification notification) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        synchronized (OfferStorage.LOCK) {
            // A lista visível é a autoridade: ofertas descartadas pelo limite das
            // três melhores, invalidadas ou substituídas não podem notificar.
            boolean visible = false;
            java.util.Set<String> visibleIds = new java.util.HashSet<>();
            for (ObservedOffer item : new OfferRepository(context).getRecent()) {
                visibleIds.add(item.getId());
                if (item.getId().equals(offer.getId())) visible = true;
            }
            String product = OfferTextParser.normalize(offer.getInterest());
            // Migrate old per-message notifications; the three stable slots below are shared
            // by all sources and also prevent accumulation across price changes or restarts.
            for (StatusBarNotification active : manager.getActiveNotifications()) {
                Notification old = active.getNotification();
                CharSequence title = old.extras.getCharSequence(Notification.EXTRA_TITLE);
                if (!product.equals(old.extras.getString(PRODUCT, ""))
                        && (title == null || !product.equals(OfferTextParser.normalize(title.toString())))) continue;
                String oldOfferId = old.extras.getString(OFFER_ID, "");
                if (!oldOfferId.isEmpty() && !visibleIds.contains(oldOfferId)) {
                    manager.cancel(active.getTag(), active.getId());
                    continue;
                }
                boolean slot = false;
                for (int i = 0; i < MAX_PER_PRODUCT; i++) if (active.getId() == notificationId(product, i)) slot = true;
                if (!slot) manager.cancel(active.getTag(), active.getId());
            }
            if (!visible) return false;
            int slot = reserveSlot(context.getSharedPreferences("product_notification_limits", Context.MODE_PRIVATE),
                    offer, System.currentTimeMillis());
            if (slot < 0) return false;
            notification.extras.putString(PRODUCT, product);
            notification.extras.putString(OFFER_ID, offer.getId());
            manager.notify(notificationId(product, slot), notification);
            return true;
        }
    }

    private static int notificationId(String product, int slot) { return ("product|" + product + "|" + slot).hashCode(); }

    static void cancelForOffer(Context context, ObservedOffer offer) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || offer == null) return;
        for (StatusBarNotification active : manager.getActiveNotifications()) {
            Notification notification = active.getNotification();
            if (offer.getId().equals(notification.extras.getString(OFFER_ID, ""))) {
                manager.cancel(active.getTag(), active.getId());
            }
        }
    }

    static void cancelForProduct(Context context, String interest) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String product = OfferTextParser.normalize(interest);
        for (StatusBarNotification active : manager.getActiveNotifications()) {
            Notification notification = active.getNotification();
            if (product.equals(notification.extras.getString(PRODUCT, ""))) {
                manager.cancel(active.getTag(), active.getId());
            }
        }
    }

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
