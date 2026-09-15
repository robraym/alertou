package br.com.droidboaoferta;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StoreSourceUrlTest {
    @Test public void acceptsAnyManuallyConfiguredStoreAddress() {
        assertEquals(MotorolaOfferSource.DEFAULT_URL,
                MotorolaOfferSource.normalizeUrl(MotorolaOfferSource.DEFAULT_URL));
        assertEquals(KabumOfferSource.DEFAULT_URL,
                KabumOfferSource.normalizeUrl(KabumOfferSource.DEFAULT_URL));
        assertEquals("https://novo-endereco.exemplo/consulta",
                MotorolaOfferSource.normalizeUrl("https://novo-endereco.exemplo/consulta"));
        assertEquals("https://novo-endereco.exemplo/consulta",
                KabumOfferSource.normalizeUrl("https://novo-endereco.exemplo/consulta"));
    }

    @Test public void acceptsTheOfficialSamsungProductApiTemplate() {
        assertEquals(SamsungDiscountOfferSource.DEFAULT_PRODUCT_API_URL,
                SamsungDiscountOfferSource.normalizeProductApiUrl(
                        SamsungDiscountOfferSource.DEFAULT_PRODUCT_API_URL));
        assertEquals("https://novo-endereco.exemplo/catalogo",
                SamsungDiscountOfferSource.normalizeProductApiUrl(
                        "https://novo-endereco.exemplo/catalogo"));
    }
}
