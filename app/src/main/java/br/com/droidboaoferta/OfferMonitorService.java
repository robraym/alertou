package br.com.droidboaoferta;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class OfferMonitorService extends Service {
    private static volatile boolean running;
    private boolean telegramRecoveryInitialized;
    private Set<String> telegramRecoveryGroups = Collections.emptySet();
    private final StoreNetworkReconnectMonitor storeNetworkReconnectMonitor =
            new StoreNetworkReconnectMonitor();

    static boolean isRunning() {
        return running;
    }
    private static final String CHANNEL_MONITOR = "offer_monitor_status";
    private static final int NOTIFICATION_ID = 4101;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        MonitorStatusStore.setServiceRunning(this, true);
        createChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        updateMonitors();
        storeNetworkReconnectMonitor.start(this, this::retryStoreSourcesAfterReconnect);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateMonitors();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        OfferMonitor.getInstance().stop();
        CouponPageMonitor.getInstance().stop();
        PropertyPageMonitor.getInstance().stop();
        VivoOutletMonitor.getInstance().stop();
        PelandoMonitor.getInstance().stop();
        PromobitMonitor.getInstance().stop();
        KabumOfferMonitor.getInstance().stop();
        KabumCatalogMonitor.getInstance().stop();
        MotorolaOfferMonitor.getInstance().stop();
        ClaroOfferMonitor.getInstance().stop();
        SamsungOfferMonitor.getInstance().stop();
        SamsungDiscountOfferMonitor.getInstance().stop();
        MonitorStatusStore.setServiceRunning(this, false);
        storeNetworkReconnectMonitor.stop();
        super.onDestroy();
    }

    private void updateMonitors() {
        if (!MonitorServiceController.shouldRun(this)) {
            OfferMonitor.getInstance().stop();
            stopSelf();
            return;
        }
        boolean hasPriceAlert = false;
        boolean hasCouponAlert = false;
        boolean hasPropertyAlert = false;
        for (Interest interest : new InterestRepository(this).getAll()) {
            if (interest.isCoupon()) {
                hasCouponAlert = true;
            } else if (interest.isProperty()) {
                hasPropertyAlert = true;
            } else if (interest.isPrice()) {
                hasPriceAlert = true;
            }
        }
        if (hasPriceAlert && MonitorServiceController.selectedGroupCount(this) > 0) {
            OfferMonitor.getInstance().start(this);
            Set<String> selectedGroups = new HashSet<>(getSharedPreferences(
                    "telegram_preferences", MODE_PRIVATE).getStringSet(
                    "selected_groups", Collections.emptySet()));
            if (TelegramRecoveryRequestPolicy.shouldRequest(
                    telegramRecoveryInitialized, telegramRecoveryGroups, selectedGroups)) {
                telegramRecoveryInitialized = true;
                telegramRecoveryGroups = selectedGroups;
                TelegramClientManager.getInstance().requestMissedMessageRecovery();
            }
        } else {
            OfferMonitor.getInstance().stop();
            telegramRecoveryInitialized = false;
            telegramRecoveryGroups = Collections.emptySet();
        }
        if (hasPriceAlert && ((StoreSourceControl.isEnabled(this, R.string.vivo_outlet_source_title) && VivoOutletSource.isConfigured(this))
                || (StoreSourceControl.isEnabled(this, R.string.vivo_madrugada_source_title) && VivoMadrugadaSource.isConfigured(this)))) {
            VivoOutletMonitor.getInstance().start(this);
        } else {
            VivoOutletMonitor.getInstance().stop();
        }
        if (hasPriceAlert && StoreSourceControl.isEnabled(this, R.string.pelando_source_title) && PelandoSource.isConfigured(this)) {
            PelandoMonitor.getInstance().start(this);
        } else {
            PelandoMonitor.getInstance().stop();
        }
        if (hasPriceAlert && StoreSourceControl.isEnabled(this, R.string.promobit_source_title) && PromobitSource.isConfigured(this)) {
            PromobitMonitor.getInstance().start(this);
        } else {
            PromobitMonitor.getInstance().stop();
        }
        if (hasPriceAlert && StoreSourceControl.isEnabled(this, R.string.kabum_offer_source_title) && KabumOfferSource.isConfigured(this)) {
            KabumOfferMonitor.getInstance().start(this);
        } else {
            KabumOfferMonitor.getInstance().stop();
            KabumCatalogMonitor.getInstance().stop();
        }
        if (hasPriceAlert && StoreSourceControl.isEnabled(this, R.string.kabum_catalog_source_title) && KabumCatalogSource.isConfigured(this)) KabumCatalogMonitor.getInstance().start(this);
        else KabumCatalogMonitor.getInstance().stop();
        if (hasPriceAlert && StoreSourceControl.isEnabled(this, R.string.kabum_catalog_api_source_title) && KabumCatalogApiSource.isConfigured(this)) KabumCatalogApiMonitor.getInstance().start(this);
        else KabumCatalogApiMonitor.getInstance().stop();
        if (hasPriceAlert && StoreSourceControl.isEnabled(this, R.string.motorola_offer_source_title) && MotorolaOfferSource.isConfigured(this)) {
            MotorolaOfferMonitor.getInstance().start(this);
        } else {
            MotorolaOfferMonitor.getInstance().stop();
        }
        if (hasPriceAlert && StoreSourceControl.isEnabled(this, R.string.claro_offer_source_title) && ClaroOfferSource.isConfigured(this)) {
            ClaroOfferMonitor.getInstance().start(this);
        } else {
            ClaroOfferMonitor.getInstance().stop();
        }
        if (hasPriceAlert && StoreSourceControl.isEnabled(this, R.string.samsung_offer_source_title) && SamsungOfferSource.isConfigured(this)) {
            SamsungOfferMonitor.getInstance().start(this);
        } else {
            SamsungOfferMonitor.getInstance().stop();
        }
        if (hasPriceAlert && StoreSourceControl.isEnabled(this, R.string.samsung_discount_offer_source_title) && SamsungDiscountOfferSource.isConfigured(this)) {
            SamsungDiscountOfferMonitor.getInstance().start(this);
        } else {
            SamsungDiscountOfferMonitor.getInstance().stop();
        }
        if (hasCouponAlert) {
            CouponPageMonitor.getInstance().start(this);
        } else {
            CouponPageMonitor.getInstance().stop();
        }
        if (hasPropertyAlert) {
            PropertyPageMonitor.getInstance().start(this);
        } else {
            PropertyPageMonitor.getInstance().stop();
        }
    }

    private void retryStoreSourcesAfterReconnect() {
        if (!MonitorRunPolicy.canRun(this) || !hasPriceAlert()) return;
        if ((StoreSourceControl.isEnabled(this, R.string.vivo_outlet_source_title)
                && VivoOutletSource.isConfigured(this))
                || (StoreSourceControl.isEnabled(this, R.string.vivo_madrugada_source_title)
                && VivoMadrugadaSource.isConfigured(this))) {
            VivoOutletMonitor.getInstance().checkNow(this);
        }
        if (StoreSourceControl.isEnabled(this, R.string.pelando_source_title)
                && PelandoSource.isConfigured(this)) PelandoMonitor.getInstance().checkNow(this);
        if (StoreSourceControl.isEnabled(this, R.string.promobit_source_title)
                && PromobitSource.isConfigured(this)) PromobitMonitor.getInstance().checkNow(this);
        if (StoreSourceControl.isEnabled(this, R.string.kabum_offer_source_title)
                && KabumOfferSource.isConfigured(this)) KabumOfferMonitor.getInstance().checkNow(this);
        if (StoreSourceControl.isEnabled(this, R.string.kabum_catalog_source_title)
                && KabumCatalogSource.isConfigured(this)) KabumCatalogMonitor.getInstance().checkNow(this);
        if (StoreSourceControl.isEnabled(this, R.string.kabum_catalog_api_source_title)
                && KabumCatalogApiSource.isConfigured(this)) KabumCatalogApiMonitor.getInstance().checkNow(this);
        if (StoreSourceControl.isEnabled(this, R.string.motorola_offer_source_title)
                && MotorolaOfferSource.isConfigured(this)) MotorolaOfferMonitor.getInstance().checkNow(this);
        if (StoreSourceControl.isEnabled(this, R.string.claro_offer_source_title)
                && ClaroOfferSource.isConfigured(this)) ClaroOfferMonitor.getInstance().checkNow(this);
        if (StoreSourceControl.isEnabled(this, R.string.samsung_offer_source_title)
                && SamsungOfferSource.isConfigured(this)) SamsungOfferMonitor.getInstance().checkNow(this);
        if (StoreSourceControl.isEnabled(this, R.string.samsung_discount_offer_source_title)
                && SamsungDiscountOfferSource.isConfigured(this)) {
            SamsungDiscountOfferMonitor.getInstance().checkNow(this);
        }
    }

    private boolean hasPriceAlert() {
        for (Interest interest : new InterestRepository(this).getAll()) {
            if (interest.isPrice()) return true;
        }
        return false;
    }

    private Notification createNotification() {
        Intent openApp = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_notification_offer)
                .setContentTitle(getString(R.string.monitor_service_title))
                .setContentText(getString(R.string.monitor_service_summary))
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_MONITOR,
                getString(R.string.monitor_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.monitor_channel_description));
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }
}
