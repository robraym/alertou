package br.com.droidboaoferta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ClaroOfferClientTest {
    @Test
    public void readsTheOfficialCatalogProductCard() {
        String html = "<div id=\"product-000000000000019500\" "
                + "data-link-redirect=\"/celulares/galaxy-a57/p/000000000000019500\">"
                + "<input name=\"productName\" value=\"SAMSUNG&#x20;GALAXY&#x20;A57&#x20;5G\"/>"
                + "<input name=\"productPrice\" value=\"2.199\"/>";

        List<ExternalProductDeal> deals = ClaroOfferClient.parseOffers(html);

        assertEquals(1, deals.size());
        assertEquals("SAMSUNG GALAXY A57 5G", deals.get(0).getTitle());
        assertEquals(2199d, deals.get(0).getPrice(), 0.001d);
        assertTrue(deals.get(0).getLink().startsWith("https://planoscelular.claro.com.br/"));
    }
}
