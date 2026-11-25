package br.com.droidboaoferta;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Same soft accent glow as the main screen in Falai. */
final class SoftGlowBackground extends Drawable {
    private final Paint base = new Paint(), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent, radiusLimit;

    SoftGlowBackground(Context context) {
        base.setColor(context.getColor(R.color.background));
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        accent = androidx.core.graphics.ColorUtils.setAlphaComponent(
                AccentColorController.getPrimaryColor(context), dark ? 35 : 24);
        radiusLimit = Math.round(260 * context.getResources().getDisplayMetrics().density);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        float radius = Math.min(bounds.width() * .65f, radiusLimit);
        glow.setShader(radius > 0 ? new RadialGradient(bounds.exactCenterX(),
                bounds.top + bounds.height() * .48f, radius,
                new int[]{accent, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP) : null);
    }

    @Override public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), base);
        if (glow.getShader() != null) canvas.drawRect(getBounds(), glow);
    }

    @Override public void setAlpha(int alpha) {
        base.setAlpha(alpha); glow.setAlpha(alpha); invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        base.setColorFilter(filter); glow.setColorFilter(filter); invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
