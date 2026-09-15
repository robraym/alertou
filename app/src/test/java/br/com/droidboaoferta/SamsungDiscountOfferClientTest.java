package br.com.droidboaoferta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.Set;

import org.junit.Test;

public class SamsungDiscountOfferClientTest {
    @Test
    public void acceptsBothSamsungSkuLinkFormatsInPageOrder() {
        Set<String> skuIds = SamsungDiscountOfferClient.extractSkuIds(
                "<a href='https://shop.samsung.com/br/fold8/p?idsku=15346'>Fold8</a>"
                        + "<a href='?skuId=16032'>TV</a>");

        assertEquals(2, skuIds.size());
        Iterator<String> iterator = skuIds.iterator();
        assertEquals("15346", iterator.next());
        assertEquals("16032", iterator.next());
        assertTrue(skuIds.contains("15346"));
    }
}
