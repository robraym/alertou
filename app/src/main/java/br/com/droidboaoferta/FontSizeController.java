package br.com.droidboaoferta;

import android.content.Context;

final class FontSizeController {
    static final String KEY = "font_size_percent";
    static final int MIN = 80;
    static final int MAX = 130;

    static boolean isSupported(int percent) {
        return percent >= MIN && percent <= MAX;
    }

    static int getPercent(Context context) {
        int value = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).getInt(KEY, 100);
        return isSupported(value) ? value : 100;
    }

    static void save(Context context, int percent) {
        if (!isSupported(percent)) throw new IllegalArgumentException("Unsupported font size");
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit().putInt(KEY, percent).apply();
        SettingsBackup.changed(context);
    }
}
