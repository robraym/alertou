package br.com.droidboaoferta;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

final class AccentColorController {
    static final String MODE_BLUE = "blue";
    static final String MODE_GREEN = "green";
    static final String MODE_YELLOW = "yellow";
    static final String MODE_PURPLE = "purple";
    static final String MODE_ORANGE = "orange";
    static final String MODE_PINK = "pink";
    static final String MODE_TEAL = "teal";
    static final String MODE_RED = "red";
    static final String MODE_MATRIX = "matrix";
    static final String MODE_WHITE = "white";
    private static final String LEGACY_MODE_ELECTRIC = "electric";
    static final String MODE_INDIGO = "indigo";
    static final String MODE_MAGENTA = "magenta";

    static final String MODE_SKY = "sky";
    static final String MODE_AMBER = "amber";
    static final String MODE_MINT = "mint";
    static final String MODE_LAVENDER = "lavender";
    static final String MODE_PEACH = "peach";
    static final String MODE_EMERALD = "emerald";
    static final String MODE_RUBY = "ruby";
    static final String MODE_OLIVE = "olive";
    static final String MODE_MOSS = "moss";
    static final String MODE_JADE = "jade";
    static final String MODE_PISTACHIO = "pistachio";
    static final String MODE_CYAN = "cyan";
    static final String MODE_LIME = "lime";
    static final String MODE_CORAL = "coral";
    static final String MODE_BRONZE = "bronze";
    static final String MODE_SILVER = "silver";
    private static final String PREFS = "app_preferences";
    private static final String ACCENT_COLOR = "accent_color";

    private AccentColorController() {
    }

    static String getSavedMode(Context context) {
        return normalize(prefs(context).getString(ACCENT_COLOR, MODE_GREEN));
    }

    static void saveMode(Context context, String mode) {
        String normalizedMode = normalize(mode);
        if (normalizedMode.equals(getSavedMode(context))) {
            return;
        }
        long changedAt = System.currentTimeMillis();
        prefs(context).edit().putString(ACCENT_COLOR, normalizedMode).apply();
        CloudSyncStore.syncAccentColorChanged(context, changedAt);
    }

    static void apply(Activity activity) {
        activity.getTheme().applyStyle(getStyleResource(getSavedMode(activity)), true);
    }

    static int getSummaryResource(Context context, String mode) {
        switch (normalize(mode)) {
            case MODE_GREEN:
                return R.string.accent_color_green;
            case MODE_YELLOW:
                return R.string.accent_color_yellow;
            case MODE_PURPLE:
                return R.string.accent_color_purple;
            case MODE_ORANGE:
                return R.string.accent_color_orange;
            case MODE_PINK:
                return R.string.accent_color_pink;
            case MODE_TEAL:
                return R.string.accent_color_teal;
            case MODE_RED:
                return R.string.accent_color_red;
            case MODE_MATRIX:
                return R.string.accent_color_matrix;
            case MODE_WHITE:
                return ThemeController.MODE_LIGHT.equals(ThemeController.getSavedMode(context))
                        ? R.string.accent_color_graphite
                        : R.string.accent_color_white;
            case MODE_INDIGO:
                return R.string.accent_color_indigo;
            case MODE_MAGENTA:
                return R.string.accent_color_magenta;
            case MODE_SILVER:
                return R.string.accent_color_silver;
            case MODE_BRONZE:
                return R.string.accent_color_bronze;
            case MODE_CORAL:
                return R.string.accent_color_coral;
            case MODE_LIME:
                return R.string.accent_color_lime;
            case MODE_CYAN:
                return R.string.accent_color_cyan;
            case MODE_AMBER:
                return R.string.accent_color_amber;
            case MODE_MINT:
                return R.string.accent_color_mint;
            case MODE_LAVENDER:
                return R.string.accent_color_lavender;
            case MODE_PEACH:
                return R.string.accent_color_peach;
            case MODE_EMERALD:
                return R.string.accent_color_emerald;
            case MODE_OLIVE:
                return R.string.accent_color_olive;
            case MODE_MOSS:
                return R.string.accent_color_moss;
            case MODE_JADE:
                return R.string.accent_color_jade;
            case MODE_PISTACHIO:
                return R.string.accent_color_pistachio;
            case MODE_RUBY:
                return R.string.accent_color_ruby;
            case MODE_SKY:
                return R.string.accent_color_sky;
            case MODE_BLUE:
            default:
                return R.string.accent_color_blue;
        }
    }

    static int getPrimaryColor(Context context, String mode) {
        return context.getColor(getPrimaryColorResource(mode));
    }

    /**
     * Resolves the current accent without relying on a color resource whose value
     * depends on a theme attribute. This also keeps programmatically created
     * icons and text in sync with the color shown in Perfil.
     */
    static int getPrimaryColor(Context context) {
        return getPrimaryColor(context, getSavedMode(context));
    }

    static int getSoftColor(Context context, String mode) {
        return context.getColor(getSoftColorResource(mode));
    }

    static String normalize(String mode) {
        if (LEGACY_MODE_ELECTRIC.equals(mode)) {
            return MODE_WHITE;
        }
        if (MODE_OLIVE.equals(mode)
                || MODE_MOSS.equals(mode)
                || MODE_JADE.equals(mode)
                || MODE_PISTACHIO.equals(mode)
                || MODE_AMBER.equals(mode)
                || MODE_MINT.equals(mode)
                || MODE_LAVENDER.equals(mode)
                || MODE_PEACH.equals(mode)
                || MODE_EMERALD.equals(mode)
                || MODE_RUBY.equals(mode)
                || MODE_SKY.equals(mode)
                || MODE_CYAN.equals(mode)
                || MODE_LIME.equals(mode)
                || MODE_CORAL.equals(mode)
                || MODE_BRONZE.equals(mode)
                || MODE_SILVER.equals(mode)
                || MODE_BLUE.equals(mode)
                || MODE_GREEN.equals(mode)
                || MODE_YELLOW.equals(mode)
                || MODE_PURPLE.equals(mode)
                || MODE_ORANGE.equals(mode)
                || MODE_PINK.equals(mode)
                || MODE_TEAL.equals(mode)
                || MODE_RED.equals(mode)
                || MODE_MATRIX.equals(mode)
                || MODE_WHITE.equals(mode)
                || MODE_INDIGO.equals(mode)
                || MODE_MAGENTA.equals(mode)) {
            return mode;
        }
        return MODE_GREEN;
    }

    private static int getStyleResource(String mode) {
        switch (normalize(mode)) {
            case MODE_GREEN:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Green;
            case MODE_YELLOW:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Yellow;
            case MODE_PURPLE:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Purple;
            case MODE_ORANGE:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Orange;
            case MODE_PINK:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Pink;
            case MODE_TEAL:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Teal;
            case MODE_RED:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Red;
            case MODE_MATRIX:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Matrix;
            case MODE_WHITE:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_White;
            case MODE_INDIGO:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Indigo;
            case MODE_MAGENTA:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Magenta;
            case MODE_SILVER:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Silver;
            case MODE_BRONZE:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Bronze;
            case MODE_CORAL:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Coral;
            case MODE_LIME:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Lime;
            case MODE_CYAN:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Cyan;
            case MODE_AMBER:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Amber;
            case MODE_MINT:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Mint;
            case MODE_LAVENDER:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Lavender;
            case MODE_PEACH:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Peach;
            case MODE_EMERALD:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Emerald;
            case MODE_OLIVE:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Olive;
            case MODE_MOSS:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Moss;
            case MODE_JADE:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Jade;
            case MODE_PISTACHIO:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Pistachio;
            case MODE_RUBY:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Ruby;
            case MODE_SKY:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Sky;
            case MODE_BLUE:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Blue;
            default:
                return R.style.ThemeOverlay_DroidBoaOferta_Accent_Green;
        }
    }

    private static int getPrimaryColorResource(String mode) {
        switch (normalize(mode)) {
            case MODE_GREEN:
                return R.color.action_green;
            case MODE_YELLOW:
                return R.color.action_yellow;
            case MODE_PURPLE:
                return R.color.action_purple;
            case MODE_ORANGE:
                return R.color.action_orange;
            case MODE_PINK:
                return R.color.action_pink;
            case MODE_TEAL:
                return R.color.action_teal;
            case MODE_RED:
                return R.color.action_red;
            case MODE_MATRIX:
                return R.color.action_matrix;
            case MODE_WHITE:
                return R.color.action_white;
            case MODE_INDIGO:
                return R.color.action_indigo;
            case MODE_MAGENTA:
                return R.color.action_magenta;
            case MODE_SILVER:
                return R.color.action_silver;
            case MODE_BRONZE:
                return R.color.action_bronze;
            case MODE_CORAL:
                return R.color.action_coral;
            case MODE_LIME:
                return R.color.action_lime;
            case MODE_CYAN:
                return R.color.action_cyan;
            case MODE_AMBER:
                return R.color.action_amber;
            case MODE_MINT:
                return R.color.action_mint;
            case MODE_LAVENDER:
                return R.color.action_lavender;
            case MODE_PEACH:
                return R.color.action_peach;
            case MODE_EMERALD:
                return R.color.action_emerald;
            case MODE_OLIVE:
                return R.color.action_olive;
            case MODE_MOSS:
                return R.color.action_moss;
            case MODE_JADE:
                return R.color.action_jade;
            case MODE_PISTACHIO:
                return R.color.action_pistachio;
            case MODE_RUBY:
                return R.color.action_ruby;
            case MODE_SKY:
                return R.color.action_sky;
            case MODE_BLUE:
                return R.color.action_blue;
            default:
                return R.color.action_green;
        }
    }

    private static int getSoftColorResource(String mode) {
        switch (normalize(mode)) {
            case MODE_GREEN:
                return R.color.action_green_soft;
            case MODE_YELLOW:
                return R.color.action_yellow_soft;
            case MODE_PURPLE:
                return R.color.action_purple_soft;
            case MODE_ORANGE:
                return R.color.action_orange_soft;
            case MODE_PINK:
                return R.color.action_pink_soft;
            case MODE_TEAL:
                return R.color.action_teal_soft;
            case MODE_RED:
                return R.color.action_red_soft;
            case MODE_MATRIX:
                return R.color.action_matrix_soft;
            case MODE_WHITE:
                return R.color.action_white_soft;
            case MODE_INDIGO:
                return R.color.action_indigo_soft;
            case MODE_MAGENTA:
                return R.color.action_magenta_soft;
            case MODE_SILVER:
                return R.color.action_silver_soft;
            case MODE_BRONZE:
                return R.color.action_bronze_soft;
            case MODE_CORAL:
                return R.color.action_coral_soft;
            case MODE_LIME:
                return R.color.action_lime_soft;
            case MODE_CYAN:
                return R.color.action_cyan_soft;
            case MODE_AMBER:
                return R.color.action_amber_soft;
            case MODE_MINT:
                return R.color.action_mint_soft;
            case MODE_LAVENDER:
                return R.color.action_lavender_soft;
            case MODE_PEACH:
                return R.color.action_peach_soft;
            case MODE_EMERALD:
                return R.color.action_emerald_soft;
            case MODE_OLIVE:
                return R.color.action_olive_soft;
            case MODE_MOSS:
                return R.color.action_moss_soft;
            case MODE_JADE:
                return R.color.action_jade_soft;
            case MODE_PISTACHIO:
                return R.color.action_pistachio_soft;
            case MODE_RUBY:
                return R.color.action_ruby_soft;
            case MODE_SKY:
                return R.color.action_sky_soft;
            case MODE_BLUE:
                return R.color.action_blue_soft;
            default:
                return R.color.action_green_soft;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
