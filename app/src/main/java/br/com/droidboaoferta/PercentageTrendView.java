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

/** Shared percentage graph used by purchase rates and store cashback. */
class PercentageTrendView extends View {
    static final class Point {
        private final long observedAt;
        private final double value;
        Point(long observedAt, double value) { this.observedAt = observedAt; this.value = value; }
        long getObservedAt() { return observedAt; }
        double getValue() { return value; }
    }

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final SimpleDateFormat date = new SimpleDateFormat("dd/MM", new Locale("pt", "BR"));
    private final NumberFormat number = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
    private List<Point> points = new ArrayList<>();

    PercentageTrendView(Context context) {
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

    void setValues(List<Point> values) {
        points = values == null ? new ArrayList<>() : values;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) return;
        float left = dp(42), right = getWidth() - dp(42), top = dp(34), bottom = getHeight() - dp(38);
        double minimum = Double.MAX_VALUE, maximum = -Double.MAX_VALUE;
        int first = -1, last = -1;
        for (int i = 0; i < points.size(); i++) {
            if (Double.isFinite(points.get(i).getValue())) { if (first < 0) first = i; last = i; }
        }
        if (first < 0) return;
        for (Point point : points) {
            if (!Double.isFinite(point.getValue())) continue;
            minimum = Math.min(minimum, point.getValue());
            maximum = Math.max(maximum, point.getValue());
        }
        if (Double.compare(maximum, minimum) == 0) { maximum += .1d; minimum -= .1d; }
        path.reset();
        boolean connected = false;
        for (int index = 0; index < points.size(); index++) {
            Point point = points.get(index);
            if (!Double.isFinite(point.getValue())) { connected = false; continue; }
            float x = points.size() == 1 ? (left + right) / 2f : left + (right - left) * index / (points.size() - 1f);
            float y = (float) (bottom - (point.getValue() - minimum) * (bottom - top) / (maximum - minimum));
            if (!connected) path.moveTo(x, y); else path.lineTo(x, y);
            connected = true;
            canvas.drawCircle(x, y, dp(5), dot);
            canvas.drawText(number.format(point.getValue()) + "%", x, y - dp(12), label);
            if (index == first || index == last) canvas.drawText(date.format(point.getObservedAt()), x, bottom + dp(24), label);
        }
        canvas.drawPath(path, line);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
