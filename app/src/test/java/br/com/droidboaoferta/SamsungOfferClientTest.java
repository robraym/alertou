package br.com.droidboaoferta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class SamsungOfferClientTest {
    @Test
    public void readsPublicProductSchemaFromOfficialOffersPage() {
        String html = "<script type=\"application/ld+json\">"
                + "{\"@context\":\"https://schema.org\",\"@type\":\"Product\","
                + "\"sku\":\"SM-123\",\"name\":\"Galaxy Test\","
                + "\"url\":\"https://shop.samsung.com/br/galaxy-test/p\","
                + "\"offers\":{\"price\":\"1299.90\"}}"
                + "</script>";

        List<ExternalProductDeal> deals = SamsungOfferClient.parseOffers(html);

        assertEquals(1, deals.size());
        assertEquals("Galaxy Test", deals.get(0).getTitle());
        assertEquals(1299.90d, deals.get(0).getPrice(), 0.001d);
        assertTrue(deals.get(0).getLink().contains("samsung.com"));
    }
}
