package br.com.droidboaoferta;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/** Draws a fixed label and a masked, green rolling value while a source is being checked. */
public final class RollingStatusTextView extends AppCompatTextView {
    private static final long DIGIT_INTERVAL_MS = 180L;
    private static final long DIGIT_STAGGER_MS = 46L;

    private String fixedLabel = "";
    private String rollingValue = "";
    private boolean rolling;
    private long startedAt;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            invalidate();
            if (rolling && isAttachedToWindow()) postDelayed(this, 16L);
        }
    };

    public RollingStatusTextView(Context context) { super(context); }
    public RollingStatusTextView(Context context, AttributeSet attrs) { super(context, attrs); }
    public RollingStatusTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void showRollingValue(String label, String value) {
        String nextLabel = label == null ? "" : label;
        String nextValue = value == null ? "" : value;
        boolean continuing = rolling && fixedLabel.equals(nextLabel) && rollingValue.equals(nextValue);
        fixedLabel = nextLabel;
        rollingValue = nextValue;
        rolling = true;
        if (!continuing) {
            startedAt = android.os.SystemClock.uptimeMillis();
            removeCallbacks(ticker);
            post(ticker);
        }
        invalidate();
    }

    @Override public void setText(CharSequence text, BufferType type) {
        rolling = false;
        removeCallbacks(ticker);
        super.setText(text, type);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!rolling) {
            super.onDraw(canvas);
            return;
        }
        Paint paint = getPaint();
        int savedColor = paint.getColor();
        int savedAlpha = paint.getAlpha();
        float x = getPaddingLeft();
        float baseline = getBaseline();
        paint.setColor(getContext().getColor(R.color.text_primary));
        canvas.drawText(fixedLabel, x, baseline, paint);
        x += paint.measureText(fixedLabel);
        paint.setColor(getContext().getColor(R.color.action));
        long elapsed = android.os.SystemClock.uptimeMillis() - startedAt;
        int digitPosition = 0;
        for (int index = 0; index < rollingValue.length(); index++) {
            char character = rollingValue.charAt(index);
            String value = String.valueOf(character);
            float width = paint.measureText(value);
            if (x + width > getWidth() - getPaddingRight()) break;
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
        paint.setColor(savedColor);
        paint.setAlpha(savedAlpha);
    }

}
