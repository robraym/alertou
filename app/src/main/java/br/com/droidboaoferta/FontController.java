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
    static final String MODE_SERIF = "serif";
    static final String MODE_MONOSPACE = "monospace";
    static final String MODE_LIGHT = "light";
    static final String MODE_ANTON = "anton";
    static final String MODE_PACIFICO = "pacifico";
    static final String[] MODES = {MODE_DEFAULT, MODE_CONDENSED, "asapcondensed", "barlowsemicondensed",
            "firasanscondensed", "firasansextracondensed", "abel", "questrial", "tenorsans",
            "lusitana", "architectsdaughter", "rajdhani", MODE_SERIF, MODE_MONOSPACE, MODE_LIGHT};
    static final String KEY = "font_family";
    static final String PREVIEW_TAG = "font_family_preview";
    private static final java.util.Map<String, Typeface[]> bundledFaces = new java.util.HashMap<>();

    static boolean isSupported(String mode) {
        for (String supported : MODES) if (supported.equals(mode)) return true;
        return false;
    }

    static String getSavedMode(Context context) {
        return readMode(context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE));
    }

    static String readMode(SharedPreferences preferences) {
        String mode = preferences.getString(KEY, MODE_DEFAULT);
        return isSupported(mode) ? mode : MODE_DEFAULT;
    }

    static int summary(String mode) {
        if ("asapcondensed".equals(mode)) return R.string.font_asapcondensed;
        if ("barlowsemicondensed".equals(mode)) return R.string.font_barlowsemicondensed;
        if ("firasanscondensed".equals(mode)) return R.string.font_firasanscondensed;
        if ("firasansextracondensed".equals(mode)) return R.string.font_firasansextracondensed;
        if (MODE_CONDENSED.equals(mode)) return R.string.font_condensed;
        if (MODE_SERIF.equals(mode)) return R.string.font_serif;
        if (MODE_MONOSPACE.equals(mode)) return R.string.font_monospace;
        if (MODE_LIGHT.equals(mode)) return R.string.font_light;
        if (MODE_ANTON.equals(mode)) return R.string.font_anton;
        if (MODE_PACIFICO.equals(mode)) return R.string.font_pacifico;
        if ("abel".equals(mode)) return R.string.font_abel;
        if ("questrial".equals(mode)) return R.string.font_questrial;
        if ("tenorsans".equals(mode)) return R.string.font_tenorsans;
        if ("lusitana".equals(mode)) return R.string.font_lusitana;
        if ("architectsdaughter".equals(mode)) return R.string.font_architectsdaughter;
        if ("rajdhani".equals(mode)) return R.string.font_rajdhani;
        return R.string.font_default;
    }

    static int fontResource(String mode) {
        if ("asapcondensed".equals(mode)) return R.font.asapcondensed_regular;
        if ("barlowsemicondensed".equals(mode)) return R.font.barlowsemicondensed_regular;
        if ("firasanscondensed".equals(mode)) return R.font.firasanscondensed_regular;
        if ("firasansextracondensed".equals(mode)) return R.font.firasansextracondensed_regular;
        if (MODE_CONDENSED.equals(mode)) return R.font.app_condensed;
        if (MODE_ANTON.equals(mode)) return R.font.anton_regular;
        if (MODE_PACIFICO.equals(mode)) return R.font.pacifico_regular;
        if ("abel".equals(mode)) return R.font.abel_regular;
        if ("questrial".equals(mode)) return R.font.questrial_regular;
        if ("tenorsans".equals(mode)) return R.font.tenorsans_regular;
        if ("lusitana".equals(mode)) return R.font.lusitana_regular;
        if ("architectsdaughter".equals(mode)) return R.font.architectsdaughter_regular;
        if ("rajdhani".equals(mode)) return R.font.rajdhani_regular;
        return 0;
    }

    static synchronized Typeface typeface(Context context, String mode, int style) {
        if (fontResource(mode) == 0) {
            String family = MODE_SERIF.equals(mode) ? "serif"
                    : MODE_MONOSPACE.equals(mode) ? "monospace"
                    : MODE_LIGHT.equals(mode) ? "sans-serif-light" : "sans-serif";
            return Typeface.create(family, style);
        }
        Typeface[] faces = bundledFaces.get(mode);
        if (faces == null) {
            Typeface regular = ResourcesCompat.getFont(context, fontResource(mode));
            if (regular == null) throw new IllegalStateException("Bundled font unavailable");
            faces = new Typeface[4];
            // The new regular families are intended for comfortable reading;
            // do not synthesize a heavy weight from their single regular file.
            for (int i = 0; i < faces.length; i++) faces[i] = Typeface.create(regular,
                    MODE_CONDENSED.equals(mode) ? i : (i & Typeface.ITALIC));
            bundledFaces.put(mode, faces);
        }
        return faces[style & 3];
    }

    static void applyTheme(Context context, String mode) {
        if (MODE_CONDENSED.equals(mode)) {
            context.getTheme().applyStyle(R.style.ThemeOverlay_DroidBoaOferta_Font_Condensed, true);
        } else if (MODE_SERIF.equals(mode)) {
            context.getTheme().applyStyle(R.style.ThemeOverlay_DroidBoaOferta_Font_Serif, true);
        } else if (MODE_MONOSPACE.equals(mode)) {
            context.getTheme().applyStyle(R.style.ThemeOverlay_DroidBoaOferta_Font_Monospace, true);
        } else if (MODE_LIGHT.equals(mode)) {
            context.getTheme().applyStyle(R.style.ThemeOverlay_DroidBoaOferta_Font_Light, true);
        } else if (MODE_ANTON.equals(mode)) {
            context.getTheme().applyStyle(R.style.ThemeOverlay_DroidBoaOferta_Font_Anton, true);
        } else if (MODE_PACIFICO.equals(mode)) {
            context.getTheme().applyStyle(R.style.ThemeOverlay_DroidBoaOferta_Font_Pacifico, true);
        } else {
            int overlay = "abel".equals(mode) ? R.style.ThemeOverlay_DroidBoaOferta_Font_Abel
                    : "questrial".equals(mode) ? R.style.ThemeOverlay_DroidBoaOferta_Font_Questrial
                    : "tenorsans".equals(mode) ? R.style.ThemeOverlay_DroidBoaOferta_Font_TenorSans
                    : "lusitana".equals(mode) ? R.style.ThemeOverlay_DroidBoaOferta_Font_Lusitana
                    : "architectsdaughter".equals(mode) ? R.style.ThemeOverlay_DroidBoaOferta_Font_ArchitectsDaughter
                    : "rajdhani".equals(mode) ? R.style.ThemeOverlay_DroidBoaOferta_Font_Rajdhani
                    : "asapcondensed".equals(mode) ? R.style.ThemeOverlay_DroidBoaOferta_Font_AsapCondensed
                    : "barlowsemicondensed".equals(mode) ? R.style.ThemeOverlay_DroidBoaOferta_Font_BarlowSemiCondensed
                    : "firasanscondensed".equals(mode) ? R.style.ThemeOverlay_DroidBoaOferta_Font_FiraSansCondensed
                    : "firasansextracondensed".equals(mode) ? R.style.ThemeOverlay_DroidBoaOferta_Font_FiraSansExtraCondensed : 0;
            if (overlay != 0) context.getTheme().applyStyle(overlay, true);
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
        return !MODE_DEFAULT.equals(mode) && isSupported(mode) ? new Binding(root, mode) : null;
    }

    static final class Binding implements ViewTreeObserver.OnGlobalLayoutListener {
        private final View root;
        private final Typeface[] faces = new Typeface[4];

        Binding(View root, String mode) {
            this.root = root;
            for (int style = 0; style < faces.length; style++) {
                faces[style] = typeface(root.getContext(), mode, style);
            }
            root.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }

        @Override public void onGlobalLayout() { apply(root); }

        private void apply(View view) {
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                if (PREVIEW_TAG.equals(text.getTag())) return;
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
