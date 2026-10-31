package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

/** Keeps Telegram sign-in mandatory only for a genuinely new installation. */
final class TelegramOnboarding {
    private static final String PREFS = "telegram_onboarding";
    private static final String INITIALIZED = "initialized";
    private static final String REQUIRED = "required";

    private TelegramOnboarding() {
    }

    static boolean isRequired(Context context) {
        SharedPreferences preferences = preferences(context);
        if (!preferences.getBoolean(INITIALIZED, false)) {
            preferences.edit()
                    .putBoolean(INITIALIZED, true)
                    .putBoolean(REQUIRED, isFreshInstall(context))
                    .apply();
        }
        return preferences.getBoolean(REQUIRED, false);
    }

    static void complete(Context context) {
        preferences(context).edit().putBoolean(REQUIRED, false).apply();
    }

    private static boolean isFreshInstall(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0
            );
            return packageInfo.firstInstallTime == packageInfo.lastUpdateTime;
        } catch (Exception ignored) {
            // Do not block an existing installation if Android cannot provide the metadata.
            return false;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
