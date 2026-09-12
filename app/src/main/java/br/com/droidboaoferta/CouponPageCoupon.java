package br.com.droidboaoferta;

final class CouponPageCoupon {
    enum DiscountKind {
        AMOUNT,
        PERCENTAGE,
        CODE_ONLY
    }

    private final String code;
    private final double value;
    private final DiscountKind discountKind;

    CouponPageCoupon(String code, double value) {
        this(code, value, DiscountKind.AMOUNT);
    }

    CouponPageCoupon(String code, double value, DiscountKind discountKind) {
        this.code = code == null ? "" : code.trim();
        this.value = value;
        this.discountKind = discountKind == null ? DiscountKind.AMOUNT : discountKind;
    }

    String getCode() {
        return code;
    }

    double getValue() {
        return value;
    }

    boolean hasMonetaryValue() {
        return discountKind == DiscountKind.AMOUNT;
    }

    boolean hasPercentageValue() {
        return discountKind == DiscountKind.PERCENTAGE;
    }
}
