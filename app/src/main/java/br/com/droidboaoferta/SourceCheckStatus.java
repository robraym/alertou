package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

final class SourceCheckStatus {
    private SourceCheckStatus() { }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("source_check_status", Context.MODE_PRIVATE);
    }
    static void begin(Context context, long id) {
        prefs(context).edit()
                .putBoolean(id + ":failed", false)
                .putBoolean(id + ":running", true)
                .apply();
    }
    static void failed(Context context, long id, Exception error) {
        int reason = error instanceof java.net.SocketTimeoutException ? R.string.source_error_timeout
                : error instanceof java.net.UnknownHostException ? R.string.source_error_connection
                : R.string.source_error_response;
        prefs(context).edit().putBoolean(id + ":failed", true)
                .putLong(id + ":failure", System.currentTimeMillis())
                .putString(id + ":reason", context.getString(reason)).apply();
    }
    static void finish(Context context, long id, long intervalMs) {
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(id + ":running", false)
                .putLong(id + ":next", System.currentTimeMillis() + intervalMs);
        if (!preferences.getBoolean(id + ":failed", false)) {
            editor.putLong(id + ":success", System.currentTimeMillis());
        }
        editor.apply();
    }
    static boolean isRunning(Context context, long id) {
        return prefs(context).getBoolean(id + ":running", false);
    }
    static void cancel(Context context, long id) {
        prefs(context).edit().putBoolean(id + ":running", false).apply();
    }
    static String summary(Context context, long id) {
        SharedPreferences preferences = prefs(context);
        long success = preferences.getLong(id + ":success", 0);
        long failure = preferences.getLong(id + ":failure", 0);
        long next = preferences.getLong(id + ":next", 0);
        String result = context.getString(R.string.source_last_success, date(context, success));
        if (failure > 0) {
            result += "\n" + context.getString(R.string.source_last_failure, date(context, failure),
                    preferences.getString(id + ":reason", ""));
        }
        return result + "\n" + (MonitorServiceController.isEnabled(context)
                ? context.getString(R.string.source_next_check, date(context, next))
                : context.getString(R.string.source_checks_paused));
    }
    private static String date(Context context, long time) {
        if (time <= 0) return context.getString(R.string.source_check_not_recorded);
        return new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(time));
    }
}
