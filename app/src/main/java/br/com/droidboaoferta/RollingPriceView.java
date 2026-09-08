package br.com.droidboaoferta;

import android.content.Context;

import androidx.appcompat.widget.AppCompatTextView;

/** Keeps a price on one normal text line while its individual digits change in place. */
final class RollingPriceView extends AppCompatTextView {
    private static final long FRAME_MS = 90L;

    private final String price;
    private final long startedAt;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long step = (android.os.SystemClock.uptimeMillis() - startedAt) / FRAME_MS;
            setText(createFrame(step));
            if (isAttachedToWindow()) postDelayed(this, FRAME_MS);
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

    private String createFrame(long step) {
        StringBuilder frame = new StringBuilder(price.length());
        int digitPosition = 0;
        for (int index = 0; index < price.length(); index++) {
            char character = price.charAt(index);
            if (!Character.isDigit(character)) {
                frame.append(character);
                continue;
            }
            int originalDigit = character - '0';
            int direction = digitPosition % 2 == 0 ? 1 : -1;
            int value = Math.floorMod(originalDigit + direction * (int) step, 10);
            frame.append(value);
            digitPosition++;
        }
        return frame.toString();
    }
}
