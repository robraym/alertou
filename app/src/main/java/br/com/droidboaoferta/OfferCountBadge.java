package br.com.droidboaoferta;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Keeps the registered total and the offer badge on the same dashboard row. */
final class OfferCountBadge {
    static void appendTo(TextView total, String offers) {
        Context context = total.getContext();
        LinearLayout parent = (LinearLayout) total.getParent();
        int index = parent.indexOfChild(total);
        ViewGroup.LayoutParams originalParams = total.getLayoutParams();
        parent.removeView(total);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        total.setSingleLine(true);
        total.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(total, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView badge = new TextView(context);
        badge.setText(offers);
        badge.setTextSize(11);
        badge.setSingleLine(true);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        int color = AccentColorController.getPrimaryColor(context);
        badge.setTextColor(color);
        float density = context.getResources().getDisplayMetrics().density;
        int horizontalPadding = Math.round(6 * density);
        int verticalPadding = Math.round(3 * density);
        badge.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 22));
        background.setCornerRadius(7 * density);
        background.setStroke(Math.max(1, Math.round(density)),
                androidx.core.graphics.ColorUtils.setAlphaComponent(color, 80));
        badge.setBackground(background);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.leftMargin = horizontalPadding;
        row.addView(badge, badgeParams);
        parent.addView(row, index, originalParams);
    }
}
