package br.com.droidboaoferta;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Reads existing monitor signals; never starts a check or changes configuration. */
final class AppStatusIndicator {
    private final Activity activity;
    private final TextView indicator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Dialog dialog;
    private LinearLayout details;
    private String lastDetails = "";
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 3000L);
        }
    };

    AppStatusIndicator(Activity activity) {
        this.activity = activity;
        indicator = activity.findViewById(R.id.app_status_indicator);
        indicator.setOnClickListener(view -> showDetails());
    }

    void start() {
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    void stop() {
        handler.removeCallbacks(refresh);
        if (dialog != null) dialog.dismiss();
    }

    private void render() {
        List<Row> rows = new ArrayList<>();
        List<Interest> interests = new InterestRepository(activity).getAll();
        boolean enabled = MonitorServiceController.isEnabled(activity);
        boolean running = enabled && OfferMonitorService.isRunning()
                && MonitorServiceController.shouldRun(activity);
        boolean price = false;
        for (Interest interest : interests) price |= interest.isPrice();
        int monitorText = !enabled ? R.string.app_status_paused
                : interests.isEmpty() ? R.string.app_status_no_alerts
                : !MonitorServiceController.shouldRun(activity) ? R.string.app_status_no_sources
                : !running ? R.string.app_status_service_stopped : R.string.app_status_monitor_active;
        rows.add(new Row(s(R.string.app_status_monitor), s(monitorText), running));
        if (price && MonitorServiceController.selectedGroupCount(activity) == 0
                && !VivoOutletSource.isConfigured(activity) && !PelandoSource.isConfigured(activity)
                && !PromobitSource.isConfigured(activity) && !KabumOfferSource.isConfigured(activity)
                && !MotorolaOfferSource.isConfigured(activity)) {
            rows.add(new Row(s(R.string.alerts_screen_title), s(R.string.app_status_no_sources), false));
        }

        if (MonitorServiceController.selectedGroupCount(activity) > 0) {
            TelegramClientManager telegram = TelegramClientManager.getInstance();
            boolean connected = telegram.isConnectionReady();
            int state = !price ? R.string.app_status_source_idle
                    : !running ? monitorText
                    : connected ? R.string.app_status_connected
                    : telegram.getState() == TelegramClientManager.State.READY
                    || telegram.getState() == TelegramClientManager.State.STARTING
                    ? R.string.app_status_reconnecting : R.string.app_status_login;
            rows.add(new Row(s(R.string.app_status_telegram), s(state), !price || running && connected));
        }
        if (VivoOutletSource.isConfigured(activity)) addSite(rows, R.string.vivo_outlet_source_title,
                VivoOutletSource.getLastSuccessfulCheckAt(activity), VivoOutletSource.hasLastCheckFailed(activity),
                VivoOutletSource.getCheckIntervalMinutes(activity) * 60000L, price, running, monitorText);
        if (PelandoSource.isConfigured(activity)) addSite(rows, R.string.pelando_source_title,
                PelandoSource.getLastSuccessfulCheckAt(activity), PelandoSource.hasLastCheckFailed(activity),
                PelandoSource.getCheckIntervalSeconds(activity) * 1000L, price, running, monitorText);
        if (PromobitSource.isConfigured(activity)) addSite(rows, R.string.promobit_source_title,
                PromobitSource.getLastSuccessfulCheckAt(activity), PromobitSource.hasLastCheckFailed(activity),
                PromobitSource.getCheckIntervalSeconds(activity) * 1000L, price, running, monitorText);
        if (KabumOfferSource.isConfigured(activity)) addSite(rows, R.string.kabum_offer_source_title,
                KabumOfferSource.getLastSuccessfulCheckAt(activity), KabumOfferSource.hasLastCheckFailed(activity),
                KabumOfferSource.getCheckIntervalSeconds(activity) * 1000L, price, running, monitorText);
        if (MotorolaOfferSource.isConfigured(activity)) addSite(rows, R.string.motorola_offer_source_title,
                MotorolaOfferSource.getLastSuccessfulCheckAt(activity), MotorolaOfferSource.hasLastCheckFailed(activity),
                MotorolaOfferSource.getCheckIntervalMinutes(activity) * 60000L, price, running, monitorText);
        SharedPreferences checks = activity.getSharedPreferences("source_check_status", Context.MODE_PRIVATE);
        for (Interest interest : interests) {
            if (!interest.isProperty() && !interest.isCoupon()) continue;
            String key = interest.getId() + ":";
            long success = checks.getLong(key + "success", 0L);
            boolean failed = checks.getLong(key + "failure", 0L) > success;
            boolean fresh = success > 0 && checks.getLong(key + "next", 0L) + 120000L > System.currentTimeMillis();
            String name = interest.isCoupon() ? s(R.string.coupon_alerts_list_title)
                    : interest.getPropertyName().isEmpty() ? interest.getTerm() : interest.getPropertyName();
            rows.add(new Row(name, !running ? s(monitorText) : s(failed ? R.string.app_status_source_error
                    : fresh ? R.string.app_status_source_ok : R.string.app_status_source_waiting)
                    + "\n" + SourceCheckStatus.summary(activity, interest.getId()), running && fresh && !failed));
        }
        boolean healthy = running;
        for (Row row : rows) healthy &= row.healthy;
        int label = !enabled ? R.string.app_status_stopped : interests.isEmpty() ? R.string.app_status_empty
                : !running ? R.string.app_status_stopped
                : healthy ? R.string.app_status_active : R.string.app_status_attention;
        int color = interests.isEmpty() && enabled ? R.color.text_secondary
                : !running ? R.color.action_red : healthy ? R.color.action_green : R.color.action_yellow;
        indicator.setText("● " + s(label));
        indicator.setTextColor(activity.getColor(color));
        indicator.setContentDescription(activity.getString(R.string.app_status_accessibility, s(label)));
        if (details != null) {
            StringBuilder fingerprint = new StringBuilder();
            for (Row row : rows) fingerprint.append(row.title).append(row.summary).append(row.healthy);
            if (!lastDetails.equals(fingerprint.toString())) {
                lastDetails = fingerprint.toString();
                details.removeAllViews();
                for (Row row : rows) {
                    TextView title = text("● " + row.title, 15);
                    int rowColor = row.summary.startsWith(s(R.string.app_status_source_idle))
                            ? R.color.text_secondary : !running ? R.color.action_red
                            : row.healthy ? R.color.action_green : R.color.action_yellow;
                    title.setTextColor(activity.getColor(rowColor));
                    title.setPadding(0, dp(16), 0, dp(4));
                    details.addView(title);
                    details.addView(text(row.summary, 14));
                }
            }
        }
    }

    private void addSite(List<Row> rows, int name, long success, boolean failed, long interval,
                         boolean needed, boolean running, int monitorText) {
        boolean fresh = success > 0 && System.currentTimeMillis() - success <= interval + 120000L;
        int message = !needed ? R.string.app_status_source_idle : !running ? monitorText
                : failed ? R.string.app_status_source_error : fresh ? R.string.app_status_source_ok
                : R.string.app_status_source_waiting;
        String summary = s(message);
        if (success > 0) summary += "\n" + activity.getString(R.string.app_status_last_check,
                new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date(success)));
        rows.add(new Row(s(name), summary, !needed || running && fresh && !failed));
    }

    private void showDetails() {
        dialog = new Dialog(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(16));
        content.setBackgroundResource(R.drawable.bg_dialog);
        content.addView(text(s(R.string.app_status_title), 20));
        content.addView(text(s(R.string.app_status_scope), 13));
        details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        content.addView(details);
        TextView close = text(s(R.string.action_close), 16);
        close.setMinHeight(dp(48));
        close.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        close.setTextColor(activity.getColor(R.color.action));
        close.setOnClickListener(view -> dialog.dismiss());
        content.addView(close);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        dialog.setContentView(scroll);
        dialog.setOnDismissListener(value -> { details = null; lastDetails = ""; dialog = null; });
        render();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(activity.getResources().getDisplayMetrics().widthPixels - dp(40),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(activity.getColor(R.color.text_primary));
        return view;
    }

    private String s(int resource) { return activity.getString(resource); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private static final class Row {
        final String title, summary;
        final boolean healthy;
        Row(String title, String summary, boolean healthy) {
            this.title = title;
            this.summary = summary;
            this.healthy = healthy;
        }
    }
}
