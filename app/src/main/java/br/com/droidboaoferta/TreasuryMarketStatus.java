package br.com.droidboaoferta;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only the explicit status label published by the official site's status button. */
enum TreasuryMarketStatus {
    UNKNOWN, OPEN, CLOSED, SUSPENDED, MAINTENANCE;

    private static final Pattern STATUS_BUTTON = Pattern.compile(
            "<button\\b[^>]*\\bclass\\s*=\\s*([\"'])[^\"']*\\bopen-modal-status-mercado\\b"
                    + "[^\"']*\\1[^>]*>(.*?)</button>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    static TreasuryMarketStatus fromOfficialPage(String html) {
        if (html == null) return UNKNOWN;
        Matcher buttons = STATUS_BUTTON.matcher(html);
        TreasuryMarketStatus confirmed = UNKNOWN;
        while (buttons.find()) {
            String label = buttons.group(2).replaceAll("(?is)<svg\\b.*?</svg>", "")
                    .replaceAll("<[^>]*>", " ").replace("&nbsp;", " ")
                    .replace("&#160;", " ").replace("&ccedil;", "ç")
                    .replace("&atilde;", "ã").replaceAll("\\s+", " ")
                    .trim().toLowerCase(Locale.ROOT);
            TreasuryMarketStatus status;
            switch (label) {
                case "mercado aberto": status = OPEN; break;
                case "mercado fechado": status = CLOSED; break;
                case "mercado suspenso": status = SUSPENDED; break;
                case "em manutenção": status = MAINTENANCE; break;
                default: return UNKNOWN;
            }
            if (confirmed != UNKNOWN && confirmed != status) return UNKNOWN;
            confirmed = status;
        }
        return confirmed;
    }

    /** Reads the indicator's fill, including the CSS override used by the official page.
     * The status label is never used to choose a color. */
    static String indicatorColorFromOfficialPage(String html) {
        if (fromOfficialPage(html) == UNKNOWN) return "";
        StringBuilder styles = new StringBuilder();
        Matcher styleBlocks = Pattern.compile("(?is)<style\\b[^>]*>(.*?)</style>").matcher(html);
        while (styleBlocks.find()) styles.append(styleBlocks.group(1)).append('\n');
        String css = styles.toString().replaceAll("(?s)/\\*.*?\\*/", "");
        Matcher buttons = STATUS_BUTTON.matcher(html);
        String confirmed = "";
        while (buttons.find()) {
            String content = buttons.group(2);
            Matcher icon = Pattern.compile("(?is)<svg\\b[^>]*class\\s*=\\s*([\"'])"
                    + "[^\"']*\\b(m-[a-z-]+)\\b[^\"']*\\1[^>]*>").matcher(content);
            if (!icon.find()) return "";
            Matcher overrides = Pattern.compile("(?is)\\." + Pattern.quote(icon.group(2))
                    + "\\s+rect\\s*\\{([^{}]*)\\}").matcher(css);
            String color = "";
            boolean hasOverride = false;
            while (overrides.find()) {
                Matcher fill = Pattern.compile("(?is)(?:^|;)\\s*fill\\s*:\\s*([^;!}]+)")
                        .matcher(overrides.group(1));
                if (!fill.find()) continue;
                hasOverride = true;
                String resolved = resolveFill(fill.group(1).trim(), css);
                if (resolved.isEmpty() || (!color.isEmpty() && !color.equals(resolved))) return "";
                color = resolved;
            }
            if (!hasOverride) {
                Matcher rect = Pattern.compile("(?is)<rect\\b[^>]*\\bfill\\s*=\\s*([\"'])"
                        + "(#[0-9a-f]{3}(?:[0-9a-f]{3})?)\\1").matcher(content);
                if (!rect.find()) return "";
                color = resolveFill(rect.group(2), css);
            }
            if (color.isEmpty() || (!confirmed.isEmpty() && !confirmed.equals(color))) return "";
            confirmed = color;
        }
        return confirmed;
    }

    private static String resolveFill(String value, String css) {
        if (value.matches("(?i)#[0-9a-f]{6}")) return value.toUpperCase(Locale.ROOT);
        if (value.matches("(?i)#[0-9a-f]{3}")) {
            return ("#" + value.charAt(1) + value.charAt(1) + value.charAt(2)
                    + value.charAt(2) + value.charAt(3) + value.charAt(3)).toUpperCase(Locale.ROOT);
        }
        Matcher variable = Pattern.compile("var\\(\\s*(--[a-zA-Z0-9_-]+)\\s*\\)").matcher(value);
        if (!variable.matches()) return "";
        Matcher definitions = Pattern.compile(Pattern.quote(variable.group(1))
                + "\\s*:\\s*(#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3})\\s*[;}]").matcher(css);
        String color = "";
        while (definitions.find()) {
            String resolved = resolveFill(definitions.group(1), "");
            if (!color.isEmpty() && !color.equals(resolved)) return "";
            color = resolved;
        }
        return color;
    }
}
