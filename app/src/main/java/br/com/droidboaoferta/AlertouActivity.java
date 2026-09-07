package br.com.droidboaoferta;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

abstract class AlertouActivity extends AppCompatActivity {
    private String appliedAccentMode;
    private AppStatusIndicator appStatusIndicator;

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
