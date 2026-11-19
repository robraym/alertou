package br.com.droidboaoferta;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class MeliuzCashbackMonitor {
    static final String ACTION_CHANGED = "br.com.droidboaoferta.MELIUZ_CASHBACK_CHANGED";
    private static final MeliuzCashbackMonitor INSTANCE = new MeliuzCashbackMonitor();
    private final CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();
    private volatile boolean checking;
    private Context appContext;

    static MeliuzCashbackMonitor getInstance() { return INSTANCE; }
    boolean isChecking() { return checking; }

    synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext) || !MeliuzCashbackSettings.isEnabled(appContext)) return;
        long interval = TimeUnit.SECONDS.toMillis(MeliuzCashbackSettings.intervalSeconds(appContext));
        long elapsed = Math.max(0, System.currentTimeMillis() - new MeliuzCashbackStore(appContext).lastSuccess());
        scheduler.startDelayed(this::check, interval, Math.max(0, interval - elapsed));
        scheduler.updateInterval(interval);
    }

    synchronized void stop() { scheduler.stop(); checking = false; }

    synchronized void checkNow(Context context) {
        start(context);
        scheduler.request(0);
    }

    synchronized void rescheduleIfRunning(Context context) {
        if (scheduler.isStarted()) {
            if (!MeliuzCashbackSettings.isEnabled(context)) stop();
            else scheduler.updateInterval(TimeUnit.SECONDS.toMillis(MeliuzCashbackSettings.intervalSeconds(context)));
        }
    }

    private void check() {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context) || !MeliuzCashbackSettings.isEnabled(context)) return;
        String configuration = MeliuzCashbackSettings.signature(context);
        checking = true;
        broadcast(context);
        try {
            JSONArray stores = MeliuzCashbackClient.fetch();
            if (!MonitorRunPolicy.canRun(context) || !configuration.equals(MeliuzCashbackSettings.signature(context))) return;
            MeliuzCashbackStore repository = new MeliuzCashbackStore(context);
            JSONArray previousStores = repository.snapshot().optJSONArray("stores");
            boolean changed = previousStores == null || !previousStores.toString().equals(stores.toString());
            List<JSONObject> notifications = repository.record(stores,
                    MeliuzCashbackSettings.minimum(context), System.currentTimeMillis());
            // Save and publish the results before delivering Android notifications.
            broadcast(context);
            if (changed) CloudSyncStore.markLocalChanged(context);
            for (JSONObject store : notifications) {
                if (!MonitorRunPolicy.canRun(context)
                        || !configuration.equals(MeliuzCashbackSettings.signature(context))) break;
                notify(context, store);
            }
        } catch (Exception error) {
            if (MonitorRunPolicy.canRun(context) && configuration.equals(MeliuzCashbackSettings.signature(context))) {
                new MeliuzCashbackStore(context).failedAttempt();
            }
        } finally {
            // An obsolete run must not clear the progress of its replacement.
            if (CoalescingCheckScheduler.isCurrentRun()) { checking = false; broadcast(context); }
        }
    }

    static void broadcast(Context context) {
        context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
    }

    private void notify(Context context, JSONObject store) {
        try {
            if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return;
            int id = ("meliuz:" + store.optLong("id")).hashCode();
            PendingIntent openApp = PendingIntent.getActivity(context, id,
                    new Intent(context, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String text = context.getString(R.string.meliuz_notification,
                    MeliuzCashbackUi.rate(context, store));
            AlertSoundController.configureNotificationChannel(context);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(id, new NotificationCompat.Builder(context,
                    AlertSoundController.getChannelId(context))
                    .setSmallIcon(R.drawable.ic_notification_offer)
                    .setContentTitle(store.optString("name") + " · Méliuz")
                    .setContentText(text).setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openApp).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSound(AlertSoundController.getSoundUri(context)).build());
            AlertSoundController.playSelectedSound(context);
        } catch (RuntimeException denied) {
            // Notification permission does not invalidate a successful cashback read.
        }
    }
}
