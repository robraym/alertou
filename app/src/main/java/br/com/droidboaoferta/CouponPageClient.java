package br.com.droidboaoferta;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CouponPageClient {
    static final String MOTOROLA_COUPONS_URL =
            "https://www.motorola.com.br/cupons-de-desconto-motorola";
    static final String SAMSUNG_DISCOUNTS_URL = "https://shop.samsung.com/br/desconto-samsung";
    static final String SAMSUNG_COUPONS_URL = "https://www.samsung.com/br/offer/coupons/";
    static final String SAMSUNG_LIVE_SHOP_URL = "https://shop.samsung.com/br/live";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final ExecutorService REQUEST_EXECUTOR = Executors.newCachedThreadPool();

    interface Callback {
        void onResult(CouponPageCoupon coupon, int errorMessageResource);
    }

    private CouponPageClient() {
    }

    static boolean isSupported(String rawUrl) {
        return normalizeSupportedUrl(rawUrl) != null;
    }

    static String normalizeSupportedUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if (!"https".equals(scheme)) return null;
            if (("motorola.com.br".equals(host) || "www.motorola.com.br".equals(host))
                    && "/cupons-de-desconto-motorola".equals(path)) return MOTOROLA_COUPONS_URL;
            if ("shop.samsung.com".equals(host) && "/br/desconto-samsung".equals(path)) {
                return SAMSUNG_DISCOUNTS_URL;
            }
            if ("www.samsung.com".equals(host) && "/br/offer/coupons".equals(path)) {
                return SAMSUNG_COUPONS_URL;
            }
            if ("shop.samsung.com".equals(host) && "/br/live".equals(path)) {
                return SAMSUNG_LIVE_SHOP_URL;
            }
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isSamsung(String rawUrl) {
        String normalized = normalizeSupportedUrl(rawUrl);
        return SAMSUNG_DISCOUNTS_URL.equals(normalized) || SAMSUNG_COUPONS_URL.equals(normalized)
                || SAMSUNG_LIVE_SHOP_URL.equals(normalized);
    }

    static boolean usesPercentageValue(String rawUrl) {
        return SAMSUNG_COUPONS_URL.equals(normalizeSupportedUrl(rawUrl));
    }

    static void fetchHighestAsync(String rawUrl, Callback callback) {
        REQUEST_EXECUTOR.execute(() -> {
            CouponPageCoupon coupon = null;
            int error = 0;
            try {
                coupon = fetchHighest(rawUrl);
                if (coupon == null) {
                    error = R.string.highest_coupon_not_found;
                }
            } catch (Exception ignored) {
                error = R.string.highest_coupon_load_failed;
            }
            CouponPageCoupon result = coupon;
            int resultError = error;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result, resultError));
        });
    }

    static CouponPageCoupon fetchHighest(String rawUrl) throws Exception {
        String normalizedUrl = normalizeSupportedUrl(rawUrl);
        if (normalizedUrl == null) {
            throw new IllegalArgumentException("Unsupported coupon page");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(normalizedUrl).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(12_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        connection.setRequestProperty("User-Agent", "Alertou/1.0 Android");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status);
            }
            String page = readResponse(connection.getInputStream());
            if (SAMSUNG_COUPONS_URL.equals(normalizedUrl)) {
                return CouponPageParser.findHighestSamsungCoupon(page);
            }
            if (SAMSUNG_LIVE_SHOP_URL.equals(normalizedUrl)) {
                return CouponPageParser.findSamsungLiveCoupon(page);
            }
            return CouponPageParser.findHighest(page);
        } finally {
            connection.disconnect();
        }
    }

    private static String readResponse(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("Coupon page is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
