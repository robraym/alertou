package br.com.droidboaoferta;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads only the public Tesouro Direto purchase-rate feed. */
final class TreasuryDirectMonitor {
    static final String ACTION_RATES_CHANGED = "br.com.droidboaoferta.TREASURY_RATES_CHANGED";
    static final String ACTION_CHECK_STATE_CHANGED =
            "br.com.droidboaoferta.TREASURY_CHECK_STATE_CHANGED";
    static final String EXTRA_CHECKING = "treasury_checking";
    private static final String URL_RATES =
            "https://www.tesourodireto.com.br/o/rentabilidade/investir";
    private static final String PREFS = "treasury_direct_monitor";
    private static final String LAST_NOTIFIED_RATE_PREFIX = "last_notified_rate_";
    private static final String KEY_LAST_SUCCESS_AT = "last_success_at";
    private static final String KEY_LAST_DURATION = "last_duration";
    /** Timestamp published by Tesouro Direto, not the time Alertou downloaded the feed. */
    private static final String KEY_OFFICIAL_UPDATED_AT = "official_updated_at";
    /** Next opening timestamp published with the regular Tesouro Direto titles. */
    private static final String KEY_NEXT_MARKET_AVAILABILITY_AT = "next_market_availability_at";
    private static final Pattern RATE = Pattern.compile("([+-]?\\s*\\d+(?:,\\d+)?)%");
    private static final TreasuryDirectMonitor INSTANCE = new TreasuryDirectMonitor();

    private final CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();
    private Context appContext;
    private volatile boolean checking;

    static TreasuryDirectMonitor getInstance() { return INSTANCE; }

    synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        scheduler.start(this::checkSafely,
                TimeUnit.SECONDS.toMillis(TreasuryDirectSettings.getCheckIntervalSeconds(appContext)));
    }

    synchronized void stop() { scheduler.stop(); }

    synchronized void rescheduleIfRunning(Context context) {
        if (appContext == null) return;
        scheduler.updateInterval(TimeUnit.SECONDS.toMillis(
                TreasuryDirectSettings.getCheckIntervalSeconds(context)));
    }

    synchronized void checkNow(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        if (!scheduler.isStarted()) {
            start(appContext);
        }
        scheduler.request(0L);
    }

    boolean isChecking() { return checking; }

    static long getLastCheckAt(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SUCCESS_AT, 0L);
    }

    static long getLastCheckDurationMillis(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_DURATION, 0L);
    }

    static long getOfficialUpdatedAt(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_OFFICIAL_UPDATED_AT, 0L);
    }

    static boolean isOfficialMarketInMaintenance(Context context) {
        long nextAvailabilityAt = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_NEXT_MARKET_AVAILABILITY_AT, 0L);
        return nextAvailabilityAt > System.currentTimeMillis();
    }

    private void checkSafely() {
        Context context = appContext;
        if (context == null || !MonitorRunPolicy.canRun(context)) return;
        long startedAt = SystemClock.elapsedRealtime();
        boolean completed = false;
        checking = true;
        notifyCheckState(context);
        try {
            JSONArray bonds = new JSONObject(download()).optJSONArray("TesouroLegado");
            if (bonds == null) return;
            List<Interest> interests = new InterestRepository(context).getAll();
            TreasuryRateHistoryRepository historyRepository = new TreasuryRateHistoryRepository(context);
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            boolean recordedReading = false;
            boolean treasuryHistoryChanged = false;
            long officialUpdatedAt = findLatestMarketPricingAt(bonds);
            long nextMarketAvailabilityAt = findNextMarketAvailabilityAt(bonds, officialUpdatedAt);
            for (Interest interest : interests) {
                if (!interest.isTreasury()) continue;
                Double rate = findPurchaseRate(bonds, interest.getTerm());
                String key = LAST_NOTIFIED_RATE_PREFIX + interest.getId();
                if (rate == null) continue;
                treasuryHistoryChanged |= historyRepository.record(
                        interest.getId(), rate, System.currentTimeMillis());
                recordedReading = true;
                if (rate < interest.getMinimumTreasuryRate()) {
                    preferences.edit().remove(key).apply();
                    continue;
                }
                double last = Double.longBitsToDouble(preferences.getLong(key,
                        Double.doubleToRawLongBits(Double.NaN)));
                if (Double.compare(last, rate) == 0) continue;
                preferences.edit().putLong(key, Double.doubleToRawLongBits(rate)).apply();
                showNotification(context, interest, rate);
            }
            SharedPreferences.Editor officialState = preferences.edit();
            if (officialUpdatedAt > 0L) officialState.putLong(KEY_OFFICIAL_UPDATED_AT, officialUpdatedAt);
            if (nextMarketAvailabilityAt > 0L) {
                officialState.putLong(KEY_NEXT_MARKET_AVAILABILITY_AT, nextMarketAvailabilityAt);
            } else {
                officialState.remove(KEY_NEXT_MARKET_AVAILABILITY_AT);
            }
            officialState.apply();
            if (recordedReading) context.sendBroadcast(new Intent(ACTION_RATES_CHANGED)
                    .setPackage(context.getPackageName()));
            if (treasuryHistoryChanged) CloudSyncStore.markLocalChanged(context);
            completed = true;
        } catch (Exception ignored) {
            // The next scheduled check retries. A failed read must not alter alert state.
        } finally {
            if (completed) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putLong(KEY_LAST_SUCCESS_AT, System.currentTimeMillis())
                        .putLong(KEY_LAST_DURATION,
                                Math.max(0L, SystemClock.elapsedRealtime() - startedAt))
                        .apply();
            }
            checking = false;
            notifyCheckState(context);
        }
    }

    private void notifyCheckState(Context context) {
        context.sendBroadcast(new Intent(ACTION_CHECK_STATE_CHANGED)
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_CHECKING, checking));
    }

    private Double findPurchaseRate(JSONArray bonds, String title) {
        String wanted = normalize(title);
        for (int index = 0; index < bonds.length(); index++) {
            JSONObject bond = bonds.optJSONObject(index);
            if (bond == null || !wanted.equals(normalize(bond.optString("treasuryBondName")))) continue;
            Matcher matcher = RATE.matcher(bond.optString("investmentProfitabilityIndexerName"));
            if (!matcher.find()) return null;
            return Double.parseDouble(matcher.group(1).replace(" ", "").replace(',', '.'));
        }
        return null;
    }

    /** The feed repeats this official market-pricing timestamp for its available bonds. */
    static long findLatestMarketPricingAt(JSONArray bonds) {
        long latest = 0L;
        for (int index = 0; bonds != null && index < bonds.length(); index++) {
            JSONObject bond = bonds.optJSONObject(index);
            long updatedAt = parseOfficialDate(bond == null ? "" : bond.optString("lastMarketPricingDate"));
            latest = Math.max(latest, updatedAt);
        }
        return latest;
    }

    /**
     * The regular-title feed gives the next sale opening in startDate. When it is still in the
     * future, the official page is in its maintenance/closed state.
     */
    static long findNextMarketAvailabilityAt(JSONArray bonds, long officialUpdatedAt) {
        long next = 0L;
        for (int index = 0; bonds != null && index < bonds.length(); index++) {
            JSONObject bond = bonds.optJSONObject(index);
            long startsAt = parseOfficialDate(bond == null ? "" : bond.optString("startDate"));
            if (startsAt <= officialUpdatedAt) continue;
            next = next == 0L ? startsAt : Math.min(next, startsAt);
        }
        return next;
    }

    private static long parseOfficialDate(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
            parser.setLenient(false);
            parser.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
            Date parsed = parser.parse(value.trim());
            return parsed == null ? 0L : parsed.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String download() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(URL_RATES).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Alertou/1.0");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        } finally {
            connection.disconnect();
        }
    }

    private void showNotification(Context context, Interest interest, double rate) {
        Intent openApp = new Intent(context, AlertsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, interest.getId() > Integer.MAX_VALUE
                        ? (int) (interest.getId() % Integer.MAX_VALUE) : (int) interest.getId(), openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String value = String.format(new Locale("pt", "BR"), "%.2f%% a.a.", rate);
        String text = interest.getTerm() + " está em " + value
                + " (seu alerta: " + String.format(new Locale("pt", "BR"), "%.2f%% a.a.",
                interest.getMinimumTreasuryRate()) + ").";
        AlertSoundController.configureNotificationChannel(context);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
                AlertSoundController.getChannelId(context))
                .setSmallIcon(R.drawable.ic_notification_offer)
                .setContentTitle(interest.getTerm())
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending);
        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(("treasury|" + interest.getId()).hashCode(), notification.build());
        AlertSoundController.playSelectedSound(context);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
