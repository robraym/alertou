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
        mountAlertSettings(activity, slot);
    }

    private static void mountAlertSettings(Activity activity, LinearLayout slot) {
        slot.removeAllViews();
        boolean registered = MeliuzCashbackSettings.isEnabled(activity);
        Runnable changed = () -> mountAlertSettings(activity, slot);
        LinearLayout card = card(activity);
        LinearLayout header = horizontal(activity);
        header.setPadding(dp(activity, 4), dp(activity, 2), 0, dp(activity, 3));
        ImageButton add = icon(activity, R.drawable.ic_add_cashback_alert, R.string.meliuz_add_alert,
                () -> showSettings(activity, changed));
        add.setPadding(dp(activity, 7), dp(activity, 7), dp(activity, 7), dp(activity, 7));
        add.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36));
        addParams.rightMargin = dp(activity, 8);
        header.addView(add, addParams);
        LinearLayout titles = vertical(activity);
        titles.addView(label(activity, activity.getString(R.string.meliuz_alerts_title), 16, false));
        titles.addView(label(activity, activity.getString(registered
                ? R.string.coupon_alerts_registered_count_one
                : R.string.coupon_alerts_registered_count_empty), 13, true));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout details = vertical(activity);
        boolean expanded = registered && MeliuzCashbackSettings.preferences(activity)
                .getBoolean("alerts_expanded", true);
        details.setVisibility(expanded ? View.VISIBLE : View.GONE);
        ImageButton toggle = icon(activity, R.drawable.ic_chevron_right,
                expanded ? R.string.alerts_section_collapse : R.string.alerts_section_expand, () -> { });
        toggle.setPadding(dp(activity, 7), dp(activity, 7), dp(activity, 7), dp(activity, 7));
        toggle.setScaleType(ImageView.ScaleType.CENTER);
        toggle.setRotation(expanded ? 90 : 0);
        header.addView(toggle, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));
        View.OnClickListener expand = view -> {
            if (!registered) { showSettings(activity, changed); return; }
            boolean show = details.getVisibility() != View.VISIBLE;
            details.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setRotation(show ? 90 : 0);
            toggle.setContentDescription(activity.getString(show
                    ? R.string.alerts_section_collapse : R.string.alerts_section_expand));
            MeliuzCashbackSettings.preferences(activity).edit().putBoolean("alerts_expanded", show).apply();
        };
        header.setBackgroundResource(R.drawable.bg_row_pressed);
        header.setOnClickListener(expand);
        toggle.setOnClickListener(expand);
        card.addView(header);
        if (registered) {
            LinearLayout row = horizontal(activity);
            row.setMinimumHeight(dp(activity, 48));
            row.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            ImageButton edit = icon(activity, R.drawable.ic_edit, R.string.meliuz_edit_alert,
                    () -> showSettings(activity, changed));
            edit.setBackgroundResource(R.drawable.bg_row_pressed);
            edit.setPadding(dp(activity, 7), dp(activity, 7), dp(activity, 7), dp(activity, 7));
            row.addView(edit, new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36)));
            TextView rule = label(activity, activity.getString(R.string.meliuz_alert_rule,
                    number(MeliuzCashbackSettings.minimum(activity))), 13, false);
            rule.setSingleLine(false);
            LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(0, -2, 1);
            ruleParams.leftMargin = dp(activity, 8);
            row.addView(rule, ruleParams);
            row.setBackgroundResource(R.drawable.bg_row_pressed);
            row.setOnClickListener(view -> showSettings(activity, changed));
            details.addView(row);
        }
        card.addView(details);
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
        header.setPadding(dp(activity, 6), dp(activity, 2), dp(activity, 6), dp(activity, 3));
        ImageButton refresh = icon(activity, R.drawable.ic_refresh_cashback, R.string.meliuz_refresh,
                () -> { });
        refresh.setOnClickListener(view -> {
            MonitorServiceController.update(activity);
            MeliuzCashbackMonitor.getInstance().checkNow(activity);
            if (MonitorRunPolicy.canRun(activity) && MeliuzCashbackSettings.canConsult(activity)) {
                updateRefreshIcon(activity, refresh, true);
            }
        });
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
        updateRefreshIcon(activity, refresh, checking);
        String status = checking ? activity.getString(R.string.meliuz_checking)
                : (!MonitorServiceController.isEnabled(activity) || MeliuzCashbackSettings.isPaused(activity)) ? activity.getString(R.string.meliuz_paused)
                : repository.failed() ? activity.getString(R.string.meliuz_failed)
                : snapshot.optLong("observed_at") > 0 ? checkDate(activity, snapshot.optLong("observed_at"))
                : activity.getString(R.string.meliuz_waiting);
        addDashboardMetadata(activity, titles, status);
        if ((checking || repository.failed()) && snapshot.optLong("observed_at") > 0) {
            addDashboardMetadata(activity, titles, checkDate(activity, snapshot.optLong("observed_at")));
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
        JSONObject histories = repository.history();
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
            double change = MeliuzCashbackChange.percentage(store,
                    histories.optJSONArray(Long.toString(store.optLong("id"))));
            if (Double.isFinite(change)) {
                NumberFormat percent = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
                percent.setMaximumFractionDigits(1);
                TextView badge = label(activity, activity.getString(change > 0
                        ? R.string.property_price_rise_badge_compact : R.string.property_price_drop_badge_compact,
                        percent.format(Math.abs(change))), 10.5f, false);
                badge.setTextColor(ContextCompat.getColor(activity, change > 0 ? R.color.action_green : R.color.danger));
                badge.setTypeface(null, android.graphics.Typeface.BOLD);
                badge.setSingleLine(true);
                badge.setBackgroundResource(change > 0 ? R.drawable.bg_property_new_badge
                        : R.drawable.bg_property_price_rise_badge);
                badge.setPadding(dp(activity, 6), dp(activity, 1), dp(activity, 6), dp(activity, 1));
                badge.setContentDescription(store.optString("name") + ". " + badge.getText()
                        + ". " + activity.getString(R.string.meliuz_history));
                badge.setOnClickListener(v -> showHistory(activity, store, repository));
                badge.setFocusable(true);
                LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
                badgeParams.leftMargin = dp(activity, 7);
                row.addView(badge, badgeParams);
            }
            TextView current = label(activity, rate(activity, store), 13, false);
            current.setPadding(0, 0, 0, 0);
            current.setSingleLine(true);
            current.setOnClickListener(v -> showHistory(activity, store, repository));
            current.setContentDescription(store.optString("name") + ". " + rate(activity, store)
                    + ". " + activity.getString(R.string.meliuz_history));
            current.setFocusable(true);
            LinearLayout.LayoutParams currentParams = new LinearLayout.LayoutParams(-2, -2);
            currentParams.leftMargin = dp(activity, 6);
            row.addView(current, currentParams);
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

    private static void updateRefreshIcon(Activity activity, ImageButton icon, boolean checking) {
        // A card can be detached and reused by the dashboard cache. Stop its animator
        // while detached and restart only when the checking card is attached again.
        Object previous = icon.getTag();
        if (previous instanceof View.OnAttachStateChangeListener) {
            View.OnAttachStateChangeListener previousListener = (View.OnAttachStateChangeListener) previous;
            previousListener.onViewDetachedFromWindow(icon);
            icon.removeOnAttachStateChangeListener(previousListener);
        }
        icon.animate().cancel();
        icon.setRotation(0f);
        icon.setImageResource(checking ? R.drawable.ic_sync : R.drawable.ic_refresh_cashback);
        icon.setContentDescription(activity.getString(checking
                ? R.string.meliuz_checking : R.string.meliuz_refresh));
        if (!checking) { icon.setTag(null); return; }
        android.animation.ObjectAnimator spin = android.animation.ObjectAnimator.ofFloat(
                icon, View.ROTATION, 0f, 360f);
        spin.setDuration(900L);
        spin.setInterpolator(new android.view.animation.LinearInterpolator());
        spin.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { spin.start(); }
            @Override public void onViewDetachedFromWindow(View view) { spin.cancel(); }
        };
        icon.setTag(listener);
        icon.addOnAttachStateChangeListener(listener);
        if (icon.isAttachedToWindow()) spin.start();
    }

    static void showSettings(Activity activity, Runnable changed) {
        boolean editing = MeliuzCashbackSettings.isEnabled(activity);
        LinearLayout content = dialogContent(activity);
        content.addView(label(activity, activity.getString(editing
                ? R.string.meliuz_edit_alert : R.string.meliuz_add_alert), 22, false));
        TextView description = label(activity, activity.getString(R.string.meliuz_alert_description), 15, true);
        description.setPadding(0, dp(activity, 6), 0, dp(activity, 16));
        content.addView(description);
        content.addView(label(activity, activity.getString(R.string.meliuz_minimum), 13, true));
        EditText minimum = input(activity, number(MeliuzCashbackSettings.minimum(activity)), true);
        minimum.setHint(R.string.meliuz_minimum);
        content.addView(minimum);
        Dialog dialog = dialog(activity, content);
        LinearLayout actions = horizontal(activity);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(activity, 14), 0, 0);
        if (editing) {
            ImageButton remove = icon(activity, R.drawable.ic_trash_outline, R.string.action_remove_interest,
                    () -> { dialog.dismiss(); confirmRemoveAlert(activity, changed); });
            remove.setColorFilter(ContextCompat.getColor(activity, R.color.danger));
            remove.setBackgroundResource(R.drawable.bg_icon_danger);
            remove.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
            actions.addView(remove, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
            actions.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
        }
        actions.addView(editorAction(activity, R.string.action_cancel, dialog::dismiss));
        addPrimaryAction(activity, actions, R.string.action_save, () -> {
            double value;
            try { value = Double.parseDouble(minimum.getText().toString().trim().replace(',', '.')); }
            catch (Exception invalid) { value = -1; }
            if (!Double.isFinite(value) || value < 0 || value > 100) {
                minimum.setError(activity.getString(R.string.meliuz_invalid_minimum)); return;
            }
            MeliuzCashbackSettings.save(activity, true, value, MeliuzCashbackSettings.intervalSeconds(activity));
            dialog.dismiss();
            changed.run();
            if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(activity,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1203);
            }
            MeliuzCashbackMonitor.getInstance().checkNow(activity);
        });
        content.addView(actions);
        show(activity, dialog);
    }

    private static void confirmRemoveAlert(Activity activity, Runnable changed) {
        LinearLayout content = dialogContent(activity);
        content.addView(label(activity, activity.getString(R.string.meliuz_remove_alert_title), 22, false));
        TextView summary = label(activity, activity.getString(R.string.meliuz_remove_alert_message), 15, true);
        summary.setPadding(0, dp(activity, 6), 0, dp(activity, 16));
        content.addView(summary);
        Dialog dialog = dialog(activity, content);
        LinearLayout actions = horizontal(activity);
        actions.setGravity(Gravity.END);
        actions.addView(editorAction(activity, R.string.action_cancel, dialog::dismiss));
        TextView confirm = addPrimaryAction(activity, actions, R.string.action_remove_interest, () -> {
            MeliuzCashbackSettings.save(activity, false, MeliuzCashbackSettings.minimum(activity),
                    MeliuzCashbackSettings.intervalSeconds(activity));
            dialog.dismiss();
            changed.run();
        });
        confirm.setBackgroundResource(R.drawable.bg_button_danger);
        content.addView(actions);
        show(activity, dialog);
    }

    private static TextView editorAction(Activity activity, int resource, Runnable click) {
        TextView action = label(activity, activity.getString(resource), 15, false);
        action.setTextColor(AccentColorController.getPrimaryColor(activity));
        action.setGravity(Gravity.CENTER);
        action.setSingleLine(true);
        action.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
        action.setOnClickListener(view -> click.run());
        return action;
    }

    private static TextView addPrimaryAction(Activity activity, LinearLayout actions, int resource, Runnable click) {
        TextView save = editorAction(activity, resource, click);
        save.setTextColor(ContextCompat.getColor(activity, R.color.button_text));
        save.setBackgroundResource(R.drawable.bg_button_primary);
        save.setMinWidth(dp(activity, 96));
        save.setPadding(dp(activity, 20), 0, dp(activity, 20), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(activity, 42));
        params.leftMargin = dp(activity, 10);
        actions.addView(save, params);
        return save;
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
        content.addView(label(activity, activity.getString(R.string.meliuz_history_title), 21, false));
        TextView storeName = label(activity, store.optString("name"), 15, false);
        storeName.setPadding(0, dp(activity, 6), 0, 0);
        content.addView(storeName);
        TextView description = label(activity, activity.getString(R.string.meliuz_history_description), 13, true);
        description.setPadding(0, dp(activity, 3), 0, dp(activity, 10));
        content.addView(description);
        JSONArray points = repository.history().optJSONArray(Long.toString(store.optLong("id")));
        List<JSONObject> readings = new ArrayList<>();
        if (points != null) for (int i = 0; i < points.length(); i++) {
            JSONObject point = points.optJSONObject(i);
            if (point != null) readings.add(point);
        }
        content.addView(label(activity, activity.getString(R.string.meliuz_history_chart_title), 16, false));
        List<PercentageTrendView.Point> graphPoints = new ArrayList<>();
        for (JSONObject point : readings) {
            double amount = "percentage".equals(point.optString("type"))
                    ? point.optDouble("amount", Double.NaN) : Double.NaN;
            graphPoints.add(new PercentageTrendView.Point(point.optLong("observed_at"), amount));
        }
        PercentageTrendView graph = new PercentageTrendView(activity);
        graph.setValues(graphPoints);
        content.addView(graph, new LinearLayout.LayoutParams(-1, dp(activity, readings.size() <= 2 ? 140 : 180)));
        TextView readingsTitle = label(activity, activity.getString(R.string.property_history_readings_title), 16, false);
        readingsTitle.setPadding(0, dp(activity, 8), 0, dp(activity, 4));
        content.addView(readingsTitle);
        java.util.Map<String, HistoryValueRange> ranges = new java.util.HashMap<>();
        for (JSONObject point : readings) {
            String type = point.optString("type");
            if (ranges.containsKey(type) || "none".equals(type)) continue;
            double[] amounts = new double[readings.size()];
            for (int i = 0; i < readings.size(); i++) amounts[i] =
                    type.equals(readings.get(i).optString("type"))
                    ? readings.get(i).optDouble("amount", Double.NaN) : Double.NaN;
            ranges.put(type, new HistoryValueRange(amounts));
        }
        content.addView(HistoryReadingsView.create(activity, readings, (point, previous) -> {
            LinearLayout row;
            if (previous instanceof LinearLayout) {
                row = (LinearLayout) previous;
            } else {
                row = horizontal(activity);
                row.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
                row.addView(label(activity, "", 12.5f, true), new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(label(activity, "", 12.5f, false));
            }
            HistoryValueRange range = ranges.get(point.optString("type"));
            int position = range == null ? 0 : range.position(point.optDouble("amount", Double.NaN));
            int color = ContextCompat.getColor(activity, position < 0 ? R.color.danger
                    : position > 0 ? R.color.action_green : R.color.text_primary);
            TextView date = (TextView) row.getChildAt(0);
            date.setText(new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR"))
                    .format(new Date(point.optLong("observed_at"))));
            date.setTextColor(color);
            TextView value = (TextView) row.getChildAt(1);
            value.setText(rate(activity, point));
            value.setTextColor(color);
            return row;
        }), new LinearLayout.LayoutParams(-1, 0, 1));
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        LinearLayout actions = horizontal(activity);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(activity, 12), 0, 0);
        actions.addView(editorAction(activity, R.string.meliuz_close, dialog::dismiss));
        content.addView(actions);
        show(activity, dialog);
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(
                activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 44),
                activity.getResources().getDisplayMetrics().heightPixels - dp(activity, 72));
    }

    private static String checkDate(android.content.Context context, long at) {
        return OfferDateFormatter.formatGroupLabel(context, at) + ", " + OfferDateFormatter.formatTime(at);
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
        layout.setPadding(padding, dp(activity, 22), padding, dp(activity, 16));
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
            window.setLayout(activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 44),
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
        input.setTextSize(16);
        input.setMinHeight(dp(activity, 52));
        input.setBackgroundResource(R.drawable.bg_input);
        input.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 52));
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
