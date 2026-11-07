package br.com.droidboaoferta;

import android.content.Context;

import java.text.NumberFormat;

final class PropertyOfferDisplay {
    private PropertyOfferDisplay() { }

    static String formatPrice(Context context, ObservedOffer offer,
                              PropertyHistoryEntry history, NumberFormat currency) {
        if (history != null && history.isUnavailable()) {
            return context.getString(R.string.property_unavailable);
        }
        if (!isPropertyZipOffer(context, offer)
                && ((history != null && history.isPendingValidation())
                || (history == null && offer.getId().startsWith("property|")
                && PropertyPageClient.isQuintoAndarListingUrl(offer.getLink())))) {
            return context.getString(R.string.property_price_pending);
        }
        return currency.format(history == null ? offer.getPrice()
                : history.getLatestPrice(offer.getPrice()));
    }

    private static boolean isPropertyZipOffer(Context context, ObservedOffer offer) {
        if (context == null || offer == null || !offer.getId().startsWith("property|")) {
            return false;
        }
        for (Interest interest : new InterestRepository(context).getAll()) {
            if (interest.getId() == offer.getInterestId()) return interest.isPropertyZip();
        }
        return false;
    }

}
