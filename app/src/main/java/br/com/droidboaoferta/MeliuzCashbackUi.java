package br.com.droidboaoferta;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Cashback has its own units, settings and history; it never enters the product-price list. */
final class MeliuzCashbackUi {
    static String number(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    static String rate(android.content.Context context, JSONObject store) {
        String value = number(store.optDouble("amount"));
        if ("percentage".equals(store.optString("type"))) {
            return context.getString(store.optBoolean("category")
                    ? R.string.meliuz_rate_up_to : R.string.meliuz_rate, value);
        }
        if ("none".equals(store.optString("type"))) return context.getString(R.string.meliuz_no_cashback);
        return context.getString(R.string.meliuz_fixed_cashback,
                CurrencyTextFormatter.displayFormatter().format(store.optDouble("amount")));
    }

    static void mountSettings(Activity activity, LinearLayout slot) {
        slot.removeAllViews();
        LinearLayout card = card(activity);
        LinearLayout row = horizontal(activity);
        int iconResource = slot.getId() == R.id.container_meliuz_settings
                ? R.drawable.ic_add_cashback_alert : R.drawable.ic_cashback;
        ImageButton sourceIcon = icon(activity, iconResource, R.string.meliuz_settings,
                () -> showSettings(activity, () -> mountSettings(activity, slot)));
        sourceIcon.setPadding(dp(activity, 7), dp(activity, 7), dp(activity, 7), dp(activity, 7));
        sourceIcon.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams sourceIconParams = new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36));
        sourceIconParams.rightMargin = dp(activity, 8);
        row.addView(sourceIcon, sourceIconParams);
        LinearLayout text = vertical(activity);
        text.addView(label(activity, activity.getString(R.string.meliuz_title), 16, false));
        text.addView(label(activity, activity.getString(MeliuzCashbackSettings.isEnabled(activity)
                ? R.string.meliuz_enabled_summary : R.string.meliuz_disabled_summary,
                number(MeliuzCashbackSettings.minimum(activity))), 13, true));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(icon(activity, R.drawable.ic_edit, R.string.meliuz_settings,
                () -> showSettings(activity, () -> mountSettings(activity, slot))));
        card.addView(row);
        card.setOnClickListener(v -> showSettings(activity, () -> mountSettings(activity, slot)));
        slot.addView(card);
    }

    static View dashboard(Activity activity, String search) {
        MeliuzCashbackStore repository = new MeliuzCashbackStore(activity);
        JSONObject snapshot = repository.snapshot();
        List<JSONObject> matches = matches(snapshot.optJSONArray("stores"),
                MeliuzCashbackSettings.minimum(activity), search);
        LinearLayout card = card(activity);
        card.setClipToOutline(true);
        card.setPadding(0, dp(activity, 2), 0, dp(activity, 2));
        LinearLayout header = horizontal(activity);
        header.setPadding(dp(activity, 6), dp(activity, 2), 0, dp(activity, 3));
        ImageButton refresh = icon(activity, R.drawable.ic_refresh_cashback, R.string.meliuz_refresh,
                () -> MeliuzCashbackMonitor.getInstance().checkNow(activity));
        refresh.setPadding(dp(activity, 7), dp(activity, 7), dp(activity, 7), dp(activity, 7));
        refresh.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36));
        refreshParams.rightMargin = dp(activity, 8);
        header.addView(refresh, refreshParams);
        LinearLayout titles = vertical(activity);
        TextView title = label(activity, activity.getString(R.string.meliuz_title), 16, false);
        title.setPadding(0, 0, 0, 0);
        titles.addView(title);
        TextView count = label(activity, activity.getString(R.string.meliuz_matches, matches.size(),
                number(MeliuzCashbackSettings.minimum(activity))), 13, true);
        count.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
        countParams.topMargin = dp(activity, 3);
        titles.addView(count, countParams);
        boolean checking = MeliuzCashbackMonitor.getInstance().isChecking();
        String status = checking ? activity.getString(R.string.meliuz_checking)
                : !MonitorServiceController.isEnabled(activity) ? activity.getString(R.string.meliuz_paused)
                : repository.failed() ? activity.getString(R.string.meliuz_failed)
                : snapshot.optLong("observed_at") > 0 ? date(snapshot.optLong("observed_at"))
                : activity.getString(R.string.meliuz_waiting);
        addDashboardMetadata(activity, titles, status);
        if ((checking || repository.failed()) && snapshot.optLong("observed_at") > 0) {
            addDashboardMetadata(activity, titles, date(snapshot.optLong("observed_at")));
        }
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        // Keep the rows attached, as in the other dashboard cards. A tap only changes
        // visibility; it must not wait for a broadcast or a full database refresh.
        LinearLayout content = vertical(activity);
        content.setPadding(dp(activity, 6), 0, dp(activity, 6), dp(activity, 4));
        boolean expanded = MeliuzCashbackSettings.preferences(activity).getBoolean("expanded", true);
        ImageButton toggle = icon(activity, R.drawable.ic_chevron_right,
                expanded ? R.string.meliuz_collapse : R.string.meliuz_expand, () -> { });
        toggle.setRotation(expanded ? 90 : 0);
        toggle.setPadding(dp(activity, 7), dp(activity, 7), dp(activity, 7), dp(activity, 7));
        toggle.setScaleType(ImageView.ScaleType.CENTER);
        header.addView(toggle, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));
        View.OnClickListener toggleListener = view -> {
            boolean expand = content.getVisibility() != View.VISIBLE;
            MeliuzCashbackSettings.preferences(activity).edit().putBoolean("expanded", expand).apply();
            content.setVisibility(expand ? View.VISIBLE : View.GONE);
            toggle.setRotation(expand ? 90f : 0f);
            toggle.setContentDescription(activity.getString(expand
                    ? R.string.meliuz_collapse : R.string.meliuz_expand));
        };
        header.setBackgroundResource(R.drawable.bg_row_pressed);
        header.setOnClickListener(toggleListener);
        header.setFocusable(true);
        toggle.setOnClickListener(toggleListener);
        card.addView(header);
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        for (JSONObject store : matches) {
            if (content.getChildCount() > 0) divider(activity, content, 0);
            LinearLayout row = horizontal(activity);
            row.setMinimumHeight(dp(activity, 36));
            row.setPadding(0, dp(activity, 5), 0, dp(activity, 5));
            TextView name = label(activity, store.optString("name"), 13, false);
            name.setPadding(0, 0, 0, 0);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            row.setBackgroundResource(R.drawable.bg_row_pressed);
            row.setOnClickListener(v -> showStore(activity, store));
            row.setFocusable(true);
            TextView badge = label(activity, rate(activity, store), 10.5f, false);
            badge.setTextColor(ContextCompat.getColor(activity, R.color.action_green));
            badge.setTypeface(null, android.graphics.Typeface.BOLD);
            badge.setSingleLine(true);
            badge.setBackgroundResource(R.drawable.bg_property_new_badge);
            badge.setPadding(dp(activity, 6), dp(activity, 1), dp(activity, 6), dp(activity, 1));
            badge.setContentDescription(store.optString("name") + ". " + rate(activity, store)
                    + ". " + activity.getString(R.string.meliuz_history));
            badge.setOnClickListener(v -> showHistory(activity, store, repository));
            badge.setFocusable(true);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
            badgeParams.leftMargin = dp(activity, 7);
            row.addView(badge, badgeParams);
            content.addView(row);
        }
        if (matches.isEmpty()) {
            TextView empty = label(activity, activity.getString(snapshot.optLong("observed_at") > 0
                    ? R.string.meliuz_no_matches : R.string.meliuz_waiting), 13, true);
            empty.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
            content.addView(empty);
        }
        card.addView(content);
        return card;
    }

    static List<JSONObject> matches(JSONArray stores, double minimum, String search) {
        List<JSONObject> result = new ArrayList<>();
        String query = search.trim().toLowerCase(Locale.ROOT);
        if (stores != null) for (int i = 0; i < stores.length(); i++) {
            JSONObject store = stores.optJSONObject(i);
            if (store != null && MeliuzCashbackClient.qualifies(store, minimum)
                    && (query.isEmpty() || "méliuz cashback".contains(query)
                    || store.optString("name").toLowerCase(Locale.ROOT).contains(query))) result.add(store);
        }
        result.sort((a, b) -> {
            int comparison = Double.compare(b.optDouble("amount"), a.optDouble("amount"));
            return comparison == 0 ? a.optString("name").compareToIgnoreCase(b.optString("name")) : comparison;
        });
        return result;
    }

    static void showSettings(Activity activity, Runnable changed) {
        LinearLayout content = dialogContent(activity);
        content.addView(label(activity, activity.getString(R.string.meliuz_settings), 21, false));
        TextView description = label(activity, activity.getString(R.string.meliuz_description), 14, true);
        description.setPadding(0, dp(activity, 8), 0, dp(activity, 12));
        content.addView(description);
        SwitchCompat enabled = new SwitchCompat(activity);
        enabled.setText(R.string.meliuz_monitor_enabled);
        enabled.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
        enabled.setChecked(MeliuzCashbackSettings.isEnabled(activity));
        enabled.setMinHeight(dp(activity, 48));
        content.addView(enabled);
        content.addView(label(activity, activity.getString(R.string.meliuz_minimum), 13, true));
        EditText minimum = input(activity, number(MeliuzCashbackSettings.minimum(activity)), true);
        content.addView(minimum);
        content.addView(label(activity, activity.getString(R.string.meliuz_interval), 13, true));
        EditText interval = input(activity, Integer.toString(MeliuzCashbackSettings.intervalSeconds(activity) / 60), false);
        content.addView(interval);
        Dialog dialog = dialog(activity, content);
        LinearLayout actions = horizontal(activity);
        actions.setGravity(Gravity.END);
        actions.addView(action(activity, R.string.action_cancel, dialog::dismiss));
        actions.addView(action(activity, R.string.action_save, () -> {
            double value;
            int minutes;
            try { value = Double.parseDouble(minimum.getText().toString().trim().replace(',', '.')); }
            catch (Exception invalid) { value = -1; }
            if (!Double.isFinite(value) || value < 0 || value > 100) {
                minimum.setError(activity.getString(R.string.meliuz_invalid_minimum)); return;
            }
            try { minutes = Integer.parseInt(interval.getText().toString().trim()); }
            catch (Exception invalid) { minutes = -1; }
            if (minutes < 5 || minutes > 1440) {
                interval.setError(activity.getString(R.string.meliuz_invalid_interval)); return;
            }
            MeliuzCashbackSettings.save(activity, enabled.isChecked(), value, minutes * 60);
            dialog.dismiss();
            changed.run();
            if (enabled.isChecked()) {
                if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity,
                        android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(activity,
                            new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1203);
                }
                MeliuzCashbackMonitor.getInstance().checkNow(activity);
            }
        }));
        content.addView(actions);
        show(activity, dialog);
    }

    private static void showStore(Activity activity, JSONObject store) {
        LinearLayout content = dialogContent(activity);
        content.addView(label(activity, store.optString("name"), 21, false));
        content.addView(label(activity, rate(activity, store), 17, false));
        TextView rules = label(activity, activity.getString(R.string.meliuz_rules), 14, true);
        rules.setPadding(0, dp(activity, 12), 0, dp(activity, 12));
        content.addView(rules);
        Dialog dialog = dialog(activity, content);
        content.addView(action(activity, R.string.meliuz_open_store, () -> {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                        MeliuzCashbackClient.BASE + "/desconto/" + store.optString("uri"))));
                dialog.dismiss();
            } catch (android.content.ActivityNotFoundException ignored) { }
        }));
        content.addView(action(activity, R.string.meliuz_close, dialog::dismiss));
        show(activity, dialog);
    }

    private static void showHistory(Activity activity, JSONObject store, MeliuzCashbackStore repository) {
        LinearLayout content = dialogContent(activity);
        content.setBackgroundResource(R.drawable.bg_dialog);
        content.addView(label(activity, store.optString("name"), 21, false));
        content.addView(label(activity, activity.getString(R.string.meliuz_history_description), 13, true));
        JSONArray points = repository.history().optJSONArray(Long.toString(store.optLong("id")));
        List<JSONObject> readings = new ArrayList<>();
        if (points != null) for (int i = 0; i < points.length(); i++) {
            JSONObject point = points.optJSONObject(i);
            if (point != null) readings.add(point);
        }
        content.addView(HistoryReadingsView.create(activity, readings, (point, previous) -> {
            TextView item = previous instanceof TextView ? (TextView) previous : label(activity, "", 14, false);
            item.setText(rate(activity, point) + "\n" + date(point.optLong("observed_at")));
            item.setPadding(0, dp(activity, 8), 0, dp(activity, 8));
            return item;
        }), new LinearLayout.LayoutParams(-1, 0, 1));
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        content.addView(action(activity, R.string.meliuz_close, dialog::dismiss));
        show(activity, dialog);
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(
                activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 32),
                activity.getResources().getDisplayMetrics().heightPixels - dp(activity, 72));
    }

    private static String date(long at) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR")).format(new Date(at));
    }

    private static LinearLayout card(Activity activity) {
        LinearLayout card = vertical(activity);
        card.setBackgroundResource(R.drawable.bg_card_compact);
        card.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = activity.getResources().getDimensionPixelSize(R.dimen.screen_card_gap);
        card.setLayoutParams(params);
        return card;
    }

    private static LinearLayout dialogContent(Activity activity) {
        LinearLayout layout = vertical(activity);
        int padding = dp(activity, 24);
        layout.setPadding(padding, padding, padding, dp(activity, 12));
        return layout;
    }

    private static Dialog dialog(Activity activity, LinearLayout content) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundResource(R.drawable.bg_dialog);
        scroll.addView(content);
        dialog.setContentView(scroll);
        return dialog;
    }

    private static void show(Activity activity, Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 32),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private static EditText input(Activity activity, String value, boolean decimal) {
        EditText input = new EditText(activity);
        input.setText(value);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        input.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
        input.setTextSize(17);
        input.setMinHeight(dp(activity, 52));
        input.setBackgroundResource(R.drawable.bg_input);
        input.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 6);
        params.bottomMargin = dp(activity, 14);
        input.setLayoutParams(params);
        return input;
    }

    private static TextView action(Activity activity, int resource, Runnable click) {
        TextView text = label(activity, activity.getString(resource), 14, false);
        text.setTextColor(AccentColorController.getPrimaryColor(activity));
        text.setGravity(Gravity.CENTER);
        text.setMinHeight(dp(activity, 48));
        text.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
        text.setBackgroundResource(R.drawable.bg_row_pressed);
        text.setOnClickListener(v -> click.run());
        return text;
    }

    private static ImageButton icon(Activity activity, int drawable, int description, Runnable click) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(drawable);
        button.setColorFilter(AccentColorController.getPrimaryColor(activity));
        button.setContentDescription(activity.getString(description));
        button.setBackgroundResource(R.drawable.bg_icon_circle);
        button.setPadding(dp(activity, 9), dp(activity, 9), dp(activity, 9), dp(activity, 9));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40));
        params.leftMargin = dp(activity, 3);
        params.rightMargin = dp(activity, 8);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> click.run());
        return button;
    }

    private static void addDashboardMetadata(Activity activity, LinearLayout parent, String text) {
        TextView metadata = label(activity, text, 11.5f, true);
        metadata.setPadding(0, 0, 0, 0);
        metadata.setIncludeFontPadding(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 3);
        parent.addView(metadata, params);
    }

    private static TextView label(Activity activity, String text, float size, boolean secondary) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(ContextCompat.getColor(activity, secondary ? R.color.text_secondary : R.color.text_primary));
        label.setPadding(0, dp(activity, 1), 0, dp(activity, 1));
        return label;
    }

    private static LinearLayout vertical(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout horizontal(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private static void divider(Activity activity, LinearLayout parent) {
        divider(activity, parent, 5);
    }

    private static void divider(Activity activity, LinearLayout parent, int topMargin) {
        View divider = new View(activity);
        divider.setBackgroundColor(ContextCompat.getColor(activity, R.color.divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 1));
        params.topMargin = dp(activity, topMargin);
        parent.addView(divider, params);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
