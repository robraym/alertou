package br.com.droidboaoferta;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Graph for the number of listings published in a condominium. */
final class PropertyListingCountTrendView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final SimpleDateFormat date = new SimpleDateFormat("dd/MM", new Locale("pt", "BR"));
    private List<PropertyMarketListingCountPoint> points = new ArrayList<>();

    PropertyListingCountTrendView(Context context) {
        super(context);
        line.setColor(context.getColor(R.color.action_blue));
        line.setStrokeWidth(dp(3));
        line.setStyle(Paint.Style.STROKE);
        dot.setColor(AccentColorController.getPrimaryColor(context));
        label.setColor(context.getColor(R.color.text_secondary));
        label.setTextSize(dp(11));
        label.setTextAlign(Paint.Align.CENTER);
    }

    void setPoints(List<PropertyMarketListingCountPoint> values) {
        points = values == null ? new ArrayList<>() : values;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) return;
        float left = dp(48), right = getWidth() - dp(48), top = dp(42), bottom = getHeight() - dp(42);
        int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
        for (PropertyMarketListingCountPoint point : points) {
            minimum = Math.min(minimum, point.getCount());
            maximum = Math.max(maximum, point.getCount());
        }
        if (maximum == minimum) {
            maximum++;
            minimum--;
        }
        path.reset();
        int labelStep = Math.max(1, (int) Math.ceil(points.size() / 4d));
        for (int index = 0; index < points.size(); index++) {
            PropertyMarketListingCountPoint point = points.get(index);
            float x = points.size() == 1 ? (left + right) / 2f
                    : left + (right - left) * index / (points.size() - 1f);
            float y = bottom - (point.getCount() - minimum) * (bottom - top) / (maximum - minimum);
            if (index == 0) path.moveTo(x, y); else path.lineTo(x, y);
            canvas.drawCircle(x, y, dp(5), dot);
            if (index == 0 || index == points.size() - 1 || index % labelStep == 0) {
                canvas.drawText(getContext().getResources().getQuantityString(
                        R.plurals.property_market_listing_count, point.getCount(), point.getCount()),
                        x, y - dp(13), label);
                canvas.drawText(date.format(point.getObservedAt()), x, bottom + dp(25), label);
            }
        }
        canvas.drawPath(path, line);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
