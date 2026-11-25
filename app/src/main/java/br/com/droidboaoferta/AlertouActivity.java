package br.com.droidboaoferta;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

abstract class AlertouActivity extends AppCompatActivity {
    private String appliedAccentMode;
    private String appliedFontMode;
    private int appliedFontSize;
    private FontController.Binding fontBinding;
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
        int fontPercent = FontSizeController.getPercent(base);
        if (smallestWidthDp < 600 && fontPercent == 100) return base;

        // 600dp (foldable/tablet) starts at 125%; the scale tops out at 140%.
        float scale = Math.min(1.40f, 1.25f + (smallestWidthDp - 600) * 0.00125f);
        Configuration scaledConfiguration = new Configuration(configuration);
        int densityDpi = configuration.densityDpi > 0
                ? configuration.densityDpi : base.getResources().getDisplayMetrics().densityDpi;
        if (smallestWidthDp >= 600) scaledConfiguration.densityDpi = Math.round(densityDpi * scale);
        scaledConfiguration.fontScale = configuration.fontScale * fontPercent / 100f;
        return base.createConfigurationContext(scaledConfiguration);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        appliedAccentMode = AccentColorController.getSavedMode(this);
        AccentColorController.apply(this);
        appliedFontMode = FontController.getSavedMode(this);
        appliedFontSize = FontSizeController.getPercent(this);
        FontController.applyTheme(this, appliedFontMode);
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new SoftGlowBackground(this));
        fontBinding = FontController.bind(getWindow().getDecorView(), appliedFontMode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appStatusIndicator == null && findViewById(R.id.app_status_indicator) != null) {
            appStatusIndicator = new AppStatusIndicator(this);
        }
        if (appStatusIndicator != null) appStatusIndicator.start();
        String savedAccentMode = AccentColorController.getSavedMode(this);
        if (!savedAccentMode.equals(appliedAccentMode)
                || !FontController.getSavedMode(this).equals(appliedFontMode)
                || appliedFontSize != FontSizeController.getPercent(this)) {
            recreate();
        }
    }

    @Override
    protected void onPause() {
        if (appStatusIndicator != null) appStatusIndicator.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (fontBinding != null) fontBinding.close();
        super.onDestroy();
    }
}
