package br.com.droidboaoferta;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Keeps diagnostics presentation out of the account/settings activity. */
final class SourceDiagnosticsDialog {
    private SourceDiagnosticsDialog() { }
    static void show(Activity activity) {
        Dialog dialog = new Dialog(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundResource(R.drawable.bg_dialog);
        TextView title = text(activity, activity.getString(R.string.source_diagnostics_title), 20);
        content.addView(title);
        boolean any = false;
        for (Interest interest : new InterestRepository(activity).getAll()) {
            if (!interest.isProperty() && !interest.isCoupon()) continue;
            any = true;
            String name = interest.isCoupon() ? activity.getString(R.string.coupon_alerts_list_title)
                    : interest.getPropertyName().isEmpty() ? interest.getTerm() : interest.getPropertyName();
            TextView item = text(activity, name + "\n" + SourceCheckStatus.summary(activity, interest.getId()), 14);
            item.setPadding(0, padding, 0, 0);
            content.addView(item);
        }
        if (!any) content.addView(text(activity, activity.getString(R.string.source_diagnostics_empty), 14));
        TextView close = text(activity, activity.getString(R.string.action_close), 16);
        close.setGravity(Gravity.END);
        close.setPadding(0, padding, 0, 0);
        close.setTextColor(activity.getColor(R.color.action));
        close.setOnClickListener(view -> dialog.dismiss());
        content.addView(close);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        dialog.setContentView(scroll);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(activity.getResources().getDisplayMetrics().widthPixels - padding * 2,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
    private static TextView text(Activity activity, String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(activity.getColor(R.color.text_primary));
        view.setTextSize(size);
        return view;
    }
}
