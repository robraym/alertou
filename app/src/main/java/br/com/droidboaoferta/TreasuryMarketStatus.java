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

    static String labelFromOfficialPage(String html) {
        if (html == null) return "";
        Matcher buttons = STATUS_BUTTON.matcher(html);
        String confirmed = "";
        while (buttons.find()) {
            String label = buttons.group(2).replaceAll("(?is)<svg\\b.*?</svg>", "")
                    .replaceAll("<[^>]*>", " ");
            label = decodeLabel(label).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
            if (label.isEmpty() || (!confirmed.isEmpty() && !confirmed.equals(label))) return "";
            confirmed = label;
        }
        return confirmed;
    }

    /** Classification is used only for the Portal indicator palette, never as display text. */
    static TreasuryMarketStatus fromOfficialPage(String html) {
        switch (labelFromOfficialPage(html).toLowerCase(Locale.ROOT)) {
            case "mercado aberto": return OPEN;
            case "mercado fechado": return CLOSED;
            case "mercado suspenso": return SUSPENDED;
            case "em manutenção":
            case "mercado em manutenção": return MAINTENANCE;
            default: return UNKNOWN;
        }
    }

    private static String decodeLabel(String value) {
        Matcher entities = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(value);
        StringBuffer decoded = new StringBuffer();
        while (entities.find()) {
            String encoded = entities.group(1);
            String replacement = entities.group();
            try {
                int code = encoded.startsWith("x") ? Integer.parseInt(encoded.substring(1), 16)
                        : Integer.parseInt(encoded);
                if (Character.isValidCodePoint(code)) replacement = new String(Character.toChars(code));
            } catch (IllegalArgumentException invalid) { /* Preserve malformed source text. */ }
            entities.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
        }
        entities.appendTail(decoded);
        return decoded.toString().replace("&nbsp;", " ").replace("&ccedil;", "ç")
                .replace("&atilde;", "ã").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    /** Portal do Investidor indicator selectors, verified against its rendered DOM and CSS. */
    String indicatorColorFromPortalStyles(String css) {
        if (css == null) return "";
        String indicator;
        switch (this) {
            case OPEN: indicator = "aberto"; break;
            case CLOSED: indicator = "sem"; break;
            case SUSPENDED: indicator = "suspenso"; break;
            case MAINTENANCE: indicator = "encerrado"; break;
            default: return "";
        }
        String stylesheet = css.replaceAll("(?s)/\\*.*?\\*/", "");
        Matcher rules = Pattern.compile("(?is)\\.td-mercado-status--" + indicator
                + "::?before\\s*\\{([^{}]*)\\}").matcher(stylesheet);
        String color = "";
        while (rules.find()) {
            Matcher background = Pattern.compile("(?i)(?:^|;)\\s*background-color\\s*:\\s*"
                    + "(#[0-9a-f]{6}|#[0-9a-f]{3})\\s*(?:!important\\s*)?(?:;|$)")
                    .matcher(rules.group(1));
            if (!background.find()) return "";
            String resolved = background.group(1).toUpperCase(Locale.ROOT);
            if (resolved.length() == 4) resolved = "#" + resolved.charAt(1) + resolved.charAt(1)
                    + resolved.charAt(2) + resolved.charAt(2) + resolved.charAt(3) + resolved.charAt(3);
            if (!color.isEmpty() && !color.equals(resolved)) return "";
            color = resolved;
        }
        return color;
    }
}
