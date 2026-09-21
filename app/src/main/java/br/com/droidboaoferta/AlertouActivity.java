package br.com.droidboaoferta;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

abstract class AlertouActivity extends AppCompatActivity {
    private String appliedAccentMode;
    private AppStatusIndicator appStatusIndicator;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(createResponsiveContext(newBase));
    }

    /**
     * Keeps the compact phone layout unchanged and enlarges the entire interface
     * together on wide screens, including programmatically created views.
     */
    private static Context createResponsiveContext(Context base) {
        Configuration configuration = base.getResources().getConfiguration();
        int smallestWidthDp = configuration.smallestScreenWidthDp;
        if (smallestWidthDp < 600) return base;

        // 600dp (foldable/tablet) starts at 125%; the scale tops out at 140%.
        float scale = Math.min(1.40f, 1.25f + (smallestWidthDp - 600) * 0.00125f);
        Configuration scaledConfiguration = new Configuration(configuration);
        int densityDpi = configuration.densityDpi > 0
                ? configuration.densityDpi : base.getResources().getDisplayMetrics().densityDpi;
        scaledConfiguration.densityDpi = Math.round(densityDpi * scale);
        return base.createConfigurationContext(scaledConfiguration);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        appliedAccentMode = AccentColorController.getSavedMode(this);
        AccentColorController.apply(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appStatusIndicator == null && findViewById(R.id.app_status_indicator) != null) {
            appStatusIndicator = new AppStatusIndicator(this);
        }
        if (appStatusIndicator != null) appStatusIndicator.start();
        String savedAccentMode = AccentColorController.getSavedMode(this);
        if (!savedAccentMode.equals(appliedAccentMode)) {
            recreate();
        }
    }

    @Override
    protected void onPause() {
        if (appStatusIndicator != null) appStatusIndicator.stop();
        super.onPause();
    }
}
