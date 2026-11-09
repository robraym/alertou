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
        // A lista oficial do QuintoAndar já traz preço e metragem. A confirmação
        // individual serve para o histórico, mas não pode ocultar esse preço na
        // tela principal. Só uma indisponibilidade confirmada troca o valor.
        return currency.format(history == null ? offer.getPrice()
                : history.getLatestPrice(offer.getPrice()));
    }

}
