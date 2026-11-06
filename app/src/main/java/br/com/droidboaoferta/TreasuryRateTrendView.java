package br.com.droidboaoferta;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compact, readable trend graph for percentage purchase rates. */
final class TreasuryRateTrendView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final SimpleDateFormat date = new SimpleDateFormat("dd/MM", new Locale("pt", "BR"));
    private final NumberFormat number = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
    private List<TreasuryRatePoint> points = new ArrayList<>();

    TreasuryRateTrendView(Context context) {
        super(context);
        line.setColor(context.getColor(R.color.action_blue));
        line.setStrokeWidth(dp(3));
        line.setStyle(Paint.Style.STROKE);
        dot.setColor(AccentColorController.getPrimaryColor(context));
        label.setColor(context.getColor(R.color.text_secondary));
        label.setTextSize(dp(11));
        label.setTextAlign(Paint.Align.CENTER);
        number.setMaximumFractionDigits(2);
        number.setMinimumFractionDigits(0);
    }

    void setPoints(List<TreasuryRatePoint> values) {
        points = values == null ? new ArrayList<>() : values;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) return;
        float left = dp(42), right = getWidth() - dp(42), top = dp(34), bottom = getHeight() - dp(38);
        double minimum = Double.MAX_VALUE, maximum = -Double.MAX_VALUE;
        for (TreasuryRatePoint point : points) {
            minimum = Math.min(minimum, point.getRate());
            maximum = Math.max(maximum, point.getRate());
        }
        if (Double.compare(maximum, minimum) == 0) { maximum += .1d; minimum -= .1d; }
        path.reset();
        for (int index = 0; index < points.size(); index++) {
            TreasuryRatePoint point = points.get(index);
            float x = points.size() == 1 ? (left + right) / 2f : left + (right - left) * index / (points.size() - 1f);
            float y = (float) (bottom - (point.getRate() - minimum) * (bottom - top) / (maximum - minimum));
            if (index == 0) path.moveTo(x, y); else path.lineTo(x, y);
            canvas.drawCircle(x, y, dp(5), dot);
            canvas.drawText(number.format(point.getRate()) + "%", x, y - dp(12), label);
            if (index == 0 || index == points.size() - 1) canvas.drawText(date.format(point.getObservedAt()), x, bottom + dp(24), label);
        }
        canvas.drawPath(path, line);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
