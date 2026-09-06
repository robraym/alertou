package br.com.droidboaoferta;

import android.content.SharedPreferences;

/** Persist an unfinished feed before HTTP details, so process death and 304 cannot lose retries. */
final class FeedRetryState {
    private final SharedPreferences preferences;
    FeedRetryState(SharedPreferences preferences) { this.preferences = preferences; }
    boolean needsRetry() { return preferences.getBoolean("pending_details", false); }
    boolean shouldProcess(String signature, boolean force) {
        return force || needsRetry() || !signature.equals(preferences.getString("last_feed_signature", ""));
    }
    void begin() { preferences.edit().putBoolean("pending_details", true).apply(); }
    void complete(String signature, boolean allSucceeded) {
        if (allSucceeded) preferences.edit().putString("last_feed_signature", signature)
                .putBoolean("pending_details", false).apply();
    }
}
