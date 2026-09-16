package br.com.droidboaoferta;

import android.content.SharedPreferences;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfferInvalidationRepositoryTest {
    @Test
    public void invalidatedStoreProductStaysBlockedAfterAnotherRead() {
        OfferInvalidationRepository repository = new OfferInvalidationRepository(TestPreferences.create());
        ObservedOffer first = storeOffer("kabum_catalog|7_123", 3999, 1L);
        repository.markInvalid(first);

        assertTrue(repository.isInvalidated(storeOffer("kabum_catalog|7_123", 3799, 2L)));
        assertFalse(repository.isInvalidated(storeOffer("kabum_catalog|7_456", 3999, 2L)));
    }

    @Test
    public void invalidatedTelegramPublicationUsesItsPermanentPostLink() {
        OfferInvalidationRepository repository = new OfferInvalidationRepository(TestPreferences.create());
        ObservedOffer first = new ObservedOffer(7L, "S25", "Canal de ofertas", 3999, 9000,
                1L, "https://loja.exemplo/s25", "https://t.me/ofertas/123", "Galaxy S25 256 GB");
        repository.markInvalid(first);

        ObservedOffer repeated = new ObservedOffer(7L, "S25", "Canal de ofertas", 3799, 9000,
                2L, "https://loja.exemplo/s25", "https://t.me/ofertas/123", "Galaxy S25 256 GB");
        assertTrue(repository.isInvalidated(repeated));
    }

    private static ObservedOffer storeOffer(String id, double price, long observedAt) {
        return new ObservedOffer(id, 7L, "S25", "KaBuM Catálogo", price, 9000,
                observedAt, "https://www.kabum.com.br/produto/123", "", "Galaxy S25 256 GB");
    }
}
