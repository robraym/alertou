package br.com.droidboaoferta;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.util.List;

public class KabumCatalogClientTest {
    @Test public void readsSkuPriceAndProductLinkFromSearchSchema() throws Exception {
        String page="<script id='productSchema' type='application/ld+json'>["
                +"{\"@type\":\"Product\",\"name\":\"Samsung Galaxy S25\",\"sku\":\"703060\","
                +"\"offers\":{\"price\":4332.22,\"url\":\"https://www.kabum.com.br/produto/703060/s25\"}}]"
                +"</script>";
        List<ExternalProductDeal> deals=KabumCatalogClient.parseOffers(page);
        assertEquals(1,deals.size()); assertEquals("703060",deals.get(0).getId());
        assertEquals(4332.22,deals.get(0).getPrice(),0.001);
        assertEquals("https://www.kabum.com.br/produto/703060/s25",deals.get(0).getLink());
    }
}
