package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

final class SourceCheckStatus {
    private SourceCheckStatus() { }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("source_check_status", Context.MODE_PRIVATE);
    }
    static void begin(Context context, long id) {
        prefs(context).edit()
                .putBoolean(id + ":failed", false)
                .putBoolean(id + ":running", true)
                .putInt(id + ":attempts", 0)
                .remove(id + ":listing_failures")
                .remove(id + ":retry_until")
                .putLong(id + ":started_at", SystemClock.elapsedRealtime())
                .apply();
    }
    static void failed(Context context, long id, Exception error) {
        Throwable root = PropertyQueryFailure.cause(error);
        int reason = root instanceof java.net.SocketTimeoutException ? R.string.source_error_timeout
                : root instanceof java.net.UnknownHostException ? R.string.source_error_connection
                : R.string.source_error_response;
        prefs(context).edit().putBoolean(id + ":failed", true)
                .remove(id + ":retry_until")
                .putLong(id + ":failure", System.currentTimeMillis())
                .putString(id + ":reason", context.getString(reason))
                .putString(id + ":failure_detail", PropertyQueryFailure.detail(error)).apply();
    }

    static void attempt(Context context, long id, int attempt) {
        prefs(context).edit().putInt(id + ":attempts", attempt).apply();
    }

    static void listingFailed(Context context, long id, String listingId, Exception error, int attempts) {
        SharedPreferences preferences = prefs(context);
        try {
            org.json.JSONObject failures = new org.json.JSONObject(
                    preferences.getString(id + ":listing_failures", "{}"));
            failures.put(listingId, new org.json.JSONObject()
                    .put("reason", PropertyQueryFailure.detail(error)).put("attempts", attempts)
                    .put("at", System.currentTimeMillis()));
            preferences.edit().putString(id + ":listing_failures", failures.toString()).apply();
        } catch (org.json.JSONException ignored) { }
    }

    static int pendingListingCount(Context context, long id) {
        try {
            return new org.json.JSONObject(prefs(context).getString(id + ":listing_failures", "{}")).length();
        } catch (org.json.JSONException ignored) { return 0; }
    }
    static void finish(Context context, long id, long intervalMs) {
        SharedPreferences preferences = prefs(context);
        long startedAt = preferences.getLong(id + ":started_at", 0L);
        long duration = startedAt <= 0L ? 0L
                : Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(id + ":running", false)
                .remove(id + ":retry_until")
                .remove(id + ":started_at")
                .putLong(id + ":next", System.currentTimeMillis() + intervalMs);
        if (duration > 0L) {
            editor.putLong(id + ":last_duration", duration);
        }
        if (!preferences.getBoolean(id + ":failed", false)) {
            editor.putLong(id + ":success", System.currentTimeMillis());
        }
        editor.apply();
    }
    static boolean isRunning(Context context, long id) {
        return prefs(context).getBoolean(id + ":running", false);
    }
    static void retryIn(Context context, long id, int seconds) {
        prefs(context).edit().putLong(id + ":retry_until",
                SystemClock.elapsedRealtime() + seconds * 1000L).apply();
    }
    static int getRetrySeconds(Context context, long id) {
        long until = prefs(context).getLong(id + ":retry_until", 0L);
        return until <= 0L ? 0 : (int) Math.max(0L,
                (until - SystemClock.elapsedRealtime() + 999L) / 1000L);
    }

    static long getLastResultAt(Context context, long id) {
        SharedPreferences preferences = prefs(context);
        return Math.max(
                preferences.getLong(id + ":success", 0L),
                preferences.getLong(id + ":failure", 0L)
        );
    }

    static boolean hasFailure(Context context, long id) {
        return prefs(context).getBoolean(id + ":failed", false);
    }

    static long getCurrentDurationMillis(Context context, long id) {
        long startedAt = prefs(context).getLong(id + ":started_at", 0L);
        return startedAt <= 0L ? 0L
                : Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
    }

    static long getLastDurationMillis(Context context, long id) {
        return prefs(context).getLong(id + ":last_duration", 0L);
    }

    static void cancel(Context context, long id) {
        prefs(context).edit().putBoolean(id + ":running", false)
                .remove(id + ":retry_until")
                .remove(id + ":started_at").apply();
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
            String detail = preferences.getString(id + ":failure_detail", "");
            if (!detail.isEmpty()) result += "\n" + detail;
        }
        int attempts = preferences.getInt(id + ":attempts", 0);
        if (attempts > 0) result += "\n" + context.getString(R.string.source_attempts, attempts);
        try {
            org.json.JSONObject failures = new org.json.JSONObject(
                    preferences.getString(id + ":listing_failures", "{}"));
            java.util.Iterator<String> ids = failures.keys();
            while (ids.hasNext()) {
                String listingId = ids.next();
                org.json.JSONObject item = failures.optJSONObject(listingId);
                if (item != null) result += "\n" + context.getString(R.string.source_listing_failure,
                        listingId, item.optInt("attempts"), item.optString("reason"));
            }
        } catch (org.json.JSONException ignored) { }
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
