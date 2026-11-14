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
