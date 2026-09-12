package br.com.droidboaoferta;

import android.content.Context;

final class StoreDisplayName {
    private static final String PREFS = "store_display_names";

    private StoreDisplayName() { }

    static String get(Context context, int defaultNameResource) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(
                "title_" + defaultNameResource, context.getString(defaultNameResource));
    }

    static void save(Context context, int defaultNameResource, String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim().replaceAll("\\s+", " ");
        if (title.isEmpty()) title = context.getString(defaultNameResource);
        if (title.length() > 40) title = title.substring(0, 40);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title_" + defaultNameResource, title).apply();
        SettingsBackup.changed(context);
    }
}
