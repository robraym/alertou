package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

final class OfferLinkValidationStore {
    private final SharedPreferences preferences;

    OfferLinkValidationStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences("offer_link_validation", Context.MODE_PRIVATE));
    }

    OfferLinkValidationStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    static boolean requiresValidation(ObservedOffer offer) {
        return offer != null && !offer.getId().startsWith("property|")
                && !PropertyMarketReferenceSettings.isReference(offer)
                && !offer.getId().startsWith("coupon|")
                && !offer.getId().startsWith("vivo|")
                && !offer.getId().startsWith("pelando|")
                && !offer.getId().startsWith("promobit|")
                && !offer.getId().startsWith("kabum|")
                && !offer.getId().startsWith("kabum_catalog|")
                && !offer.getId().startsWith("kabum_catalog_api|")
                && !offer.getId().startsWith("motorola|")
                && !offer.getId().startsWith("claro|")
                && !offer.getId().startsWith("samsung|")
                && !offer.getId().startsWith("samsung_discount|");
    }

    boolean canDisplay(ObservedOffer offer) {
        return !requiresValidation(offer) || "valid".equals(preferences.getString(offer.getId(), ""));
    }

    boolean setValidated(ObservedOffer offer, boolean valid) {
        String previous = preferences.getString(offer.getId(), "");
        String next = valid ? "valid" : "pending";
        if (previous.equals(next)) return false;
        preferences.edit().putString(offer.getId(), next).apply();
        return "valid".equals(previous) != valid;
    }
}
