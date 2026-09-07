package br.com.droidboaoferta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class VivoMadrugadaSourceTest {
    @Test public void acceptsTheOfficialMadrugadaCategory() {
        assertEquals("oferta-da-madrugada", VivoOutletSource.getCategoryCode(
                "https://store.vivo.com.br/oferta-da-madrugada/c?utm_source=store_vivo"));
        assertEquals(VivoMadrugadaSource.URL, VivoOutletSource.normalizeUrl(
                "https://store.vivo.com.br/oferta-da-madrugada/c/"));
    }

    @Test public void rejectsNonOfficialVivoAddresses() {
        assertNull(VivoOutletSource.getCategoryCode(
                "https://example.com/oferta-da-madrugada/c"));
    }
}
