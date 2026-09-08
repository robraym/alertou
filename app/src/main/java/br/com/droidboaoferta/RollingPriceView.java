package br.com.droidboaoferta;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.appcompat.widget.AppCompatTextView;

/** Keeps the normal price dimensions while each digit softly changes in its fixed position. */
final class RollingPriceView extends AppCompatTextView {
    private static final long DIGIT_INTERVAL_MS = 180L;
    private static final long DIGIT_STAGGER_MS = 46L;

    private final String price;
    private final long startedAt;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (isAttachedToWindow()) postDelayed(this, 16L);
        }
    };

    RollingPriceView(Context context, String price) {
        super(context);
        this.price = price;
        startedAt = android.os.SystemClock.uptimeMillis();
        setTextColor(context.getColor(R.color.text_primary));
        setTextSize(14);
        setSingleLine(true);
        setText(price);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(ticker);
        post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint paint = getPaint();
        paint.setColor(getCurrentTextColor());
        int savedAlpha = paint.getAlpha();
        float x = getPaddingLeft();
        float baseline = getBaseline();
        long elapsed = android.os.SystemClock.uptimeMillis() - startedAt;
        int digitPosition = 0;
        for (int index = 0; index < price.length(); index++) {
            char character = price.charAt(index);
            String value = String.valueOf(character);
            float width = paint.measureText(value);
            if (!Character.isDigit(character)) {
                paint.setAlpha(savedAlpha);
                canvas.drawText(value, x, baseline, paint);
                x += width;
                continue;
            }
            long digitElapsed = elapsed + digitPosition * DIGIT_STAGGER_MS;
            long step = digitElapsed / DIGIT_INTERVAL_MS;
            float transition = (digitElapsed % DIGIT_INTERVAL_MS)
                    / (float) DIGIT_INTERVAL_MS;
            int direction = digitPosition % 2 == 0 ? 1 : -1;
            int originalDigit = character - '0';
            String outgoing = String.valueOf(Math.floorMod(
                    originalDigit + direction * (int) step, 10));
            String incoming = String.valueOf(Math.floorMod(
                    originalDigit + direction * ((int) step + 1), 10));
            paint.setAlpha(Math.round(savedAlpha * (1f - transition)));
            canvas.drawText(outgoing, x, baseline, paint);
            paint.setAlpha(Math.round(savedAlpha * transition));
            canvas.drawText(incoming, x, baseline, paint);
            x += width;
            digitPosition++;
        }
        paint.setAlpha(savedAlpha);
    }
}
