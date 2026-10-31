package br.com.droidboaoferta;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AlertouActivity {
    private static final long SPLASH_DELAY_MS = 550L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Class<?> destination = TelegramOnboarding.isRequired(this)
                    ? TelegramSetupActivity.class
                    : MainActivity.class;
            startActivity(new Intent(this, destination));
            finish();
            overridePendingTransition(0, 0);
        }, SPLASH_DELAY_MS);
    }
}
