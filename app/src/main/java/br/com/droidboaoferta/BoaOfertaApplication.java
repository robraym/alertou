package br.com.droidboaoferta;

import android.app.Application;

public class BoaOfertaApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RetiredAlertCleanup.run(this);
        ThemeController.applySavedTheme(this);
        CloudSyncStore.ensureCouponSyncGuarantee(this);
        PropertyHistoryRepository.publishPendingChanges(this);
    }
}
