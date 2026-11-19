package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;

/** Local font families only. Font size, system font scale and text style stay unchanged. */
final class FontController {
    static final String MODE_DEFAULT = "default";
    static final String MODE_CONDENSED = "condensed";
    static final String KEY = "font_family";
    private static Typeface[] bundledFaces;

    static boolean isSupported(String mode) {
        return MODE_DEFAULT.equals(mode) || MODE_CONDENSED.equals(mode);
    }

    static String getSavedMode(Context context) {
        return readMode(context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE));
    }

    static String readMode(SharedPreferences preferences) {
        String mode = preferences.getString(KEY, MODE_DEFAULT);
        return isSupported(mode) ? mode : MODE_DEFAULT;
    }

    static int summary(String mode) {
        return MODE_CONDENSED.equals(mode) ? R.string.font_condensed : R.string.font_default;
    }

    static int fontResource(String mode) {
        return MODE_CONDENSED.equals(mode) ? R.font.app_condensed : 0;
    }

    static synchronized Typeface typeface(Context context, String mode, int style) {
        if (!MODE_CONDENSED.equals(mode)) return Typeface.create("sans", style);
        if (bundledFaces == null) {
            Typeface regular = ResourcesCompat.getFont(context, fontResource(mode));
            if (regular == null) throw new IllegalStateException("Bundled font unavailable");
            bundledFaces = new Typeface[4];
            for (int i = 0; i < bundledFaces.length; i++) bundledFaces[i] = Typeface.create(regular, i);
        }
        return bundledFaces[style & 3];
    }

    static void applyTheme(Context context, String mode) {
        if (MODE_CONDENSED.equals(mode)) {
            context.getTheme().applyStyle(R.style.ThemeOverlay_DroidBoaOferta_Font_Condensed, true);
        }
    }

    static void save(Context context, String mode) {
        if (!isSupported(mode)) throw new IllegalArgumentException("Unsupported font");
        if (mode.equals(getSavedMode(context))) return;
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                .edit().putString(KEY, mode).apply();
        SettingsBackup.changed(context);
    }

    /** Also covers dynamically replaced cards and setTypeface(null, BOLD) calls. */
    static Binding bind(View root, String mode) {
        return MODE_CONDENSED.equals(mode) ? new Binding(root) : null;
    }

    static final class Binding implements ViewTreeObserver.OnGlobalLayoutListener {
        private final View root;
        private final Typeface[] faces = new Typeface[4];

        Binding(View root) {
            this.root = root;
            for (int style = 0; style < faces.length; style++) {
                faces[style] = typeface(root.getContext(), MODE_CONDENSED, style);
            }
            root.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }

        @Override public void onGlobalLayout() { apply(root); }

        private void apply(View view) {
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                Typeface current = text.getTypeface();
                Typeface selected = faces[current == null ? Typeface.NORMAL : current.getStyle() & 3];
                if (!selected.equals(current)) text.setTypeface(selected);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i));
            }
        }

        void close() {
            if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
    }
}
