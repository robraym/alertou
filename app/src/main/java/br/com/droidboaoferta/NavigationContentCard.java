package br.com.droidboaoferta;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/** Continuous outline with rounded shoulders joining the page to its selected tab. */
final class NavigationContentCard extends Drawable {
    private final ViewGroup parent;
    private final View navigation;
    private final View selected;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectangle = new RectF();
    private final Rect selectedBounds = new Rect();
    private final Path contour = new Path();
    private final float density;

    static void attach(View navigation, View selected) {
        if (!(navigation.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) navigation.getParent();
        if (parent.getChildCount() > 0) {
            View header = parent.getChildAt(0);
            int headerGap = Math.round(12 * parent.getResources().getDisplayMetrics().density);
            header.setPadding(header.getPaddingLeft(), header.getPaddingTop() + headerGap,
                    header.getPaddingRight(), header.getPaddingBottom());
        }
        View content = parent.findViewById(R.id.floating_search_content_area);
        if (content == null) content = parent.findViewById(R.id.scroll_store_sources);
        if (content != null) {
            int gap = Math.round(8 * parent.getResources().getDisplayMetrics().density);
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop() + gap,
                    content.getPaddingRight(), content.getPaddingBottom());
        }
        NavigationContentCard card = new NavigationContentCard(parent, navigation, selected);
        Drawable original = parent.getBackground();
        // setBackground can replace padding with the drawable's padding. Preserve the
        // XML insets so the outline never ends up outside the screen.
        int left = parent.getPaddingLeft(), top = parent.getPaddingTop();
        int right = parent.getPaddingRight(), bottom = parent.getPaddingBottom();
        parent.setBackground(original == null ? card : new LayerDrawable(new Drawable[]{original, card}));
        parent.setPadding(left, top, right, bottom);
        navigation.setBackgroundColor(Color.TRANSPARENT);
        parent.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            card.keepSideInsets();
            card.alignNavigationWidth();
            card.invalidateSelf();
        });
        card.keepSideInsets();
        card.alignNavigationWidth();
    }

    private NavigationContentCard(ViewGroup parent, View navigation, View selected) {
        this.parent = parent;
        this.navigation = navigation;
        this.selected = selected;
        density = parent.getResources().getDisplayMetrics().density;
        fill.setColor(ColorUtils.blendARGB(parent.getContext().getColor(R.color.background),
                parent.getContext().getColor(R.color.icon_surface), 0.28f));
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(dp(1));
        outline.setColor(ColorUtils.setAlphaComponent(
                AccentColorController.getPrimaryColor(parent.getContext()), 80));
    }

    @Override public void draw(@NonNull Canvas canvas) {
        if (parent.getChildCount() < 2) return;
        float top = parent.getChildAt(0).getTop() + dp(4);
        boolean showTab = navigation.getVisibility() == View.VISIBLE && selected.getWidth() > 0;
        float bottom = showTab ? navigation.getTop() + dp(4) : parent.getHeight() - dp(8);
        // Bounds belong to this view, not its mutable system-inset padding.
        // Always keep the entire stroke visibly inside both screen edges.
        float side = sideInset();
        rectangle.set(side, top, parent.getWidth() - side, bottom);
        if (rectangle.height() <= 0 || rectangle.width() <= 0) return;
        if (showTab) {
            selected.getDrawingRect(selectedBounds);
            parent.offsetDescendantRectToMyCoords(selected, selectedBounds);
            buildContour(rectangle.left, rectangle.top, rectangle.right, rectangle.bottom,
                    selectedBounds.left, selectedBounds.right,
                    selectedBounds.bottom - dp(8));
            canvas.drawPath(contour, fill);
            canvas.drawPath(contour, outline);
        } else {
            canvas.drawRoundRect(rectangle, dp(20), dp(20), fill);
            canvas.drawRoundRect(rectangle, dp(20), dp(20), outline);
        }
    }

    private void keepSideInsets() {
        // fitsSystemWindows can reapply zero horizontal padding after attachment.
        int minimum = Math.round(dp(16));
        int left = Math.max(minimum, parent.getPaddingLeft());
        int right = Math.max(minimum, parent.getPaddingRight());
        if (left != parent.getPaddingLeft() || right != parent.getPaddingRight()) {
            parent.setPadding(left, parent.getPaddingTop(), right, parent.getPaddingBottom());
        }
    }

    private int sideInset() { return Math.round(dp(10) + outline.getStrokeWidth() / 2); }

    private void alignNavigationWidth() {
        if (!(navigation.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) navigation.getLayoutParams();
        int left = sideInset() - parent.getPaddingLeft();
        int right = sideInset() - parent.getPaddingRight();
        // The row spans exactly the card width, then XML weights divide that width
        // into five equal slots. The outline uses the real slot bounds, including
        // the edge tabs, so no button needs to be widened or shifted afterward.
        if (params.leftMargin != left || params.rightMargin != right) {
            params.leftMargin = left;
            params.rightMargin = right;
            navigation.setLayoutParams(params);
        }
    }

    private void buildContour(float left, float top, float right, float bottom,
                              float tabLeft, float tabRight, float tabBottom) {
        float radius = dp(20);
        boolean flushLeft = tabLeft <= left;
        boolean flushRight = tabRight >= right;
        float leftShoulder = Math.max(0, Math.min(dp(10), (tabLeft - left) / 2));
        float rightShoulder = Math.max(0, Math.min(dp(10), (right - tabRight) / 2));
        float bottomLeft = Math.min(radius, Math.max(0, tabLeft - leftShoulder - left));
        float bottomRight = Math.min(radius, Math.max(0, right - tabRight - rightShoulder));
        float tabRadius = Math.min(dp(16), (tabRight - tabLeft) / 2);
        contour.reset();
        contour.moveTo(left + radius, top);
        contour.lineTo(right - radius, top);
        contour.quadTo(right, top, right, top + radius);
        if (!flushRight) {
            contour.lineTo(right, bottom - bottomRight);
            contour.quadTo(right, bottom, right - bottomRight, bottom);
            contour.lineTo(tabRight + rightShoulder, bottom);
            contour.cubicTo(tabRight + rightShoulder * 0.35f, bottom,
                    tabRight, bottom + rightShoulder * 0.35f, tabRight, bottom + rightShoulder);
        }
        contour.lineTo(tabRight, tabBottom - tabRadius);
        contour.quadTo(tabRight, tabBottom, tabRight - tabRadius, tabBottom);
        contour.lineTo(tabLeft + tabRadius, tabBottom);
        contour.quadTo(tabLeft, tabBottom, tabLeft, tabBottom - tabRadius);
        if (!flushLeft) {
            contour.lineTo(tabLeft, bottom + leftShoulder);
            contour.cubicTo(tabLeft, bottom + leftShoulder * 0.35f,
                    tabLeft - leftShoulder * 0.35f, bottom, tabLeft - leftShoulder, bottom);
            contour.lineTo(left + bottomLeft, bottom);
            contour.quadTo(left, bottom, left, bottom - bottomLeft);
        }
        contour.lineTo(left, top + radius);
        contour.quadTo(left, top, left + radius, top);
        contour.close();
    }

    private float dp(float value) { return value * density; }
    @Override public void setAlpha(int alpha) {
        fill.setAlpha(alpha); outline.setAlpha(Math.round(alpha * 80f / 255f)); invalidateSelf();
    }
    @Override public void setColorFilter(@Nullable ColorFilter filter) {
        fill.setColorFilter(filter); outline.setColorFilter(filter); invalidateSelf();
    }
    @SuppressWarnings("deprecation")
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
