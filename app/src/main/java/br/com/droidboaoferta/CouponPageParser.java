package br.com.droidboaoferta;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CouponPageParser {
    private static final Pattern PUBLIC_COUPON = Pattern.compile(
            "(?i)cupom\\s+de\\s+r\\$\\s*([0-9][0-9.]*?(?:,[0-9]{1,2})?)\\s+de\\s+desconto"
                    + ".{0,300}?use\\s+o\\s+cupom\\s+([a-z0-9_-]{3,40})"
    );
    private static final Pattern SAMSUNG_COUPON_CARD = Pattern.compile(
            "(?is)ft25-flip-card__eyebrow-text[^>]*>\\s*([a-z0-9_-]{3,40})\\s*<"
                    + ".{0,5000}?ft25-flip-card__card-description[^>]*>\\s*(.*?)\\s*<"
    );
    private static final Pattern SAMSUNG_PERCENTAGE = Pattern.compile(
            "(?i)(?:at[eé]\\s*)?([0-9]{1,2}(?:,[0-9]{1,2})?)\\s*%\\s*off"
    );
    private static final Pattern SAMSUNG_LIVE_COPY_BUTTON = Pattern.compile(
            "(?is)cupomTitle[^>]*>.*?</h1>\\s*<button[^>]*>\\s*([a-z0-9_-]{3,40})"
                    + "\\s*<p[^>]*>\\s*copiar"
    );

    private CouponPageParser() {
    }

    static List<CouponPageCoupon> parse(String html) {
        if (html == null || html.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String text = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
        text = Normalizer.normalize(text, Normalizer.Form.NFC);

        List<CouponPageCoupon> coupons = new ArrayList<>();
        Matcher matcher = PUBLIC_COUPON.matcher(text);
        while (matcher.find()) {
            double value = parseBrazilianNumber(matcher.group(1));
            String code = matcher.group(2).toUpperCase(Locale.ROOT);
            if (value > 0d && coupons.stream().noneMatch(item -> item.getCode().equals(code))) {
                coupons.add(new CouponPageCoupon(code, value));
            }
        }
        coupons.sort(Comparator.comparingDouble(CouponPageCoupon::getValue).reversed());
        return coupons;
    }

    static CouponPageCoupon findHighest(String html) {
        List<CouponPageCoupon> coupons = parse(html);
        return coupons.isEmpty() ? null : coupons.get(0);
    }

    static CouponPageCoupon findHighestSamsungCoupon(String html) {
        if (html == null || html.trim().isEmpty()) return null;
        LinkedHashMap<String, CouponPageCoupon> coupons = new LinkedHashMap<>();
        Matcher card = SAMSUNG_COUPON_CARD.matcher(html);
        while (card.find()) {
            String code = card.group(1).trim().toUpperCase(Locale.ROOT);
            String description = plainText(card.group(2));
            Matcher percentage = SAMSUNG_PERCENTAGE.matcher(description);
            if (!percentage.find()) continue;
            double value = parseBrazilianNumber(percentage.group(1));
            if (value > 0d) {
                coupons.put(code, new CouponPageCoupon(code, value,
                        CouponPageCoupon.DiscountKind.PERCENTAGE));
            }
        }
        if (coupons.isEmpty()) return null;
        List<CouponPageCoupon> ordered = new ArrayList<>(coupons.values());
        ordered.sort(Comparator.comparingDouble(CouponPageCoupon::getValue).reversed());
        return ordered.get(0);
    }

    static CouponPageCoupon findSamsungLiveCoupon(String html) {
        if (html == null || html.trim().isEmpty()) return null;
        Matcher button = SAMSUNG_LIVE_COPY_BUTTON.matcher(html);
        if (!button.find()) return null;
        String code = button.group(1).trim().toUpperCase(Locale.ROOT);
        return code.isEmpty() ? null : new CouponPageCoupon(code, 0d,
                CouponPageCoupon.DiscountKind.CODE_ONLY);
    }

    private static String plainText(String value) {
        return value.replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static double parseBrazilianNumber(String value) {
        try {
            return Double.parseDouble(value.replace(".", "").replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }
}
