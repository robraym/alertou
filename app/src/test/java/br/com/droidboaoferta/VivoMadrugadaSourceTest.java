package br.com.droidboaoferta;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VivoMadrugadaSourceTest {
    @Test public void acceptsTheManuallyConfiguredAddress() {
        assertEquals("https://novo-endereco.exemplo/vitrine",
                VivoOutletSource.normalizeUrl("https://novo-endereco.exemplo/vitrine"));
        assertEquals("https://novo-endereco.exemplo/vitrine",
                VivoMadrugadaSource.normalizeUrl("https://novo-endereco.exemplo/vitrine"));
    }

    @Test public void retainsValidHttpAddresses() {
        assertEquals("http://novo-endereco.exemplo/vitrine",
                VivoOutletSource.normalizeApiUrl("http://novo-endereco.exemplo/vitrine"));
    }
}
