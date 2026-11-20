package br.com.droidboaoferta;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** Purchase rates use the same percentage renderer as the cashback history. */
final class TreasuryRateTrendView extends PercentageTrendView {
    TreasuryRateTrendView(Context context) { super(context); }

    void setPoints(List<TreasuryRatePoint> values) {
        List<Point> converted = new ArrayList<>();
        if (values != null) for (TreasuryRatePoint value : values) {
            converted.add(new Point(value.getObservedAt(), value.getRate()));
        }
        setValues(converted);
    }
}