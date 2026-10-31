package br.com.droidboaoferta;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;

import java.util.Collections;

final class MonitorServiceController {
    private static final String TELEGRAM_PREFS = "telegram_preferences";
    private static final String SELECTED_GROUPS = "selected_groups";
    private static final String OFFER_PREFS = "offer_preferences";
    private static final String MONITOR_ENABLED = "monitor_enabled";

    private MonitorServiceController() {
    }

    static void update(Context context) {
        Context appContext = context.getApplicationContext();
        Intent serviceIntent = new Intent(appContext, OfferMonitorService.class);
        if (!shouldRun(appContext)) {
            OfferMonitor.getInstance().stop();
            appContext.stopService(serviceIntent);
            return;
        }
        try {
            ContextCompat.startForegroundService(appContext, serviceIntent);
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            AppErrorStore.recordSerious(
                    appContext,
                    "Monitor de ofertas",
                    message == null ? "Não foi possível iniciar o monitor em segundo plano." : message
            );
        }
    }

    static boolean shouldRun(Context context) {
        if (!isEnabled(context)) {
            return false;
        }
        boolean hasPriceAlert = false;
        boolean hasCouponAlert = false;
        boolean hasPropertyAlert = false;
        for (Interest interest : new InterestRepository(context).getAll()) {
            if (interest.isCoupon()) {
                hasCouponAlert = true;
            } else if (interest.isProperty()) {
                hasPropertyAlert = true;
            } else if (interest.isPrice()) {
                hasPriceAlert = true;
            }
        }
        return hasCouponAlert || hasPropertyAlert
                || (hasPriceAlert && (selectedGroupCount(context) > 0
                || hasEnabledStoreSource(context)));
    }

    /** A configured but disabled source must not keep the foreground service alive. */
    private static boolean hasEnabledStoreSource(Context context) {
        return (StoreSourceControl.isEnabled(context, R.string.vivo_outlet_source_title)
                && VivoOutletSource.isConfigured(context))
                || (StoreSourceControl.isEnabled(context, R.string.vivo_madrugada_source_title)
                && VivoMadrugadaSource.isConfigured(context))
                || (StoreSourceControl.isEnabled(context, R.string.pelando_source_title)
                && PelandoSource.isConfigured(context))
                || (StoreSourceControl.isEnabled(context, R.string.promobit_source_title)
                && PromobitSource.isConfigured(context))
                || (StoreSourceControl.isEnabled(context, R.string.kabum_offer_source_title)
                && KabumOfferSource.isConfigured(context))
                || (StoreSourceControl.isEnabled(context, R.string.kabum_catalog_source_title)
                && KabumCatalogSource.isConfigured(context))
                || (StoreSourceControl.isEnabled(context, R.string.kabum_catalog_api_source_title)
                && KabumCatalogApiSource.isConfigured(context))
                || (StoreSourceControl.isEnabled(context, R.string.motorola_offer_source_title)
                && MotorolaOfferSource.isConfigured(context))
                || (StoreSourceControl.isEnabled(context, R.string.claro_offer_source_title)
                && ClaroOfferSource.isConfigured(context))
                || (StoreSourceControl.isEnabled(context, R.string.samsung_offer_source_title)
                && SamsungOfferSource.isConfigured(context))
                || (StoreSourceControl.isEnabled(context,
                R.string.samsung_discount_offer_source_title)
                && SamsungDiscountOfferSource.isConfigured(context));
    }

    static boolean isEnabled(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(OFFER_PREFS, Context.MODE_PRIVATE)
                .getBoolean(MONITOR_ENABLED, true);
    }

    static int selectedGroupCount(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(TELEGRAM_PREFS, Context.MODE_PRIVATE);
        return preferences.getStringSet(SELECTED_GROUPS, Collections.emptySet()).size();
    }
}
