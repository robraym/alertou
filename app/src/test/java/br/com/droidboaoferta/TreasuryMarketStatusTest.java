package br.com.droidboaoferta;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TreasuryMarketStatusTest {
    @Test public void readsTheExplicitLabelPublishedByTheOfficialStatusButton() {
        // Structure observed on the official page: label followed by a decorative SVG.
        String html = "<button class=\"open-modal-status-mercado\">\n Mercado fechado\n"
                + "<svg class=\"m-fechado\"><path d=\"M12 20\"/></svg></button>";
        assertEquals(TreasuryMarketStatus.CLOSED, TreasuryMarketStatus.fromOfficialPage(html));
    }

    @Test public void handlesLabelsAndEntityEncodingWithoutUsingIconColors() {
        assertEquals(TreasuryMarketStatus.OPEN, TreasuryMarketStatus.fromOfficialPage(
                button("<span>Mercado aberto</span>")));
        assertEquals(TreasuryMarketStatus.SUSPENDED, TreasuryMarketStatus.fromOfficialPage(
                button("Mercado&nbsp;suspenso")));
        assertEquals(TreasuryMarketStatus.MAINTENANCE, TreasuryMarketStatus.fromOfficialPage(
                button("Em manuten&ccedil;&atilde;o")));
    }

    @Test public void neverInfersStatusFromDatesRatesNoticesOrCss() {
        assertEquals(TreasuryMarketStatus.UNKNOWN, TreasuryMarketStatus.fromOfficialPage(
                "{\"startDate\":\"2026-11-13T09:30:00\",\"lastMarketPricingDate\":\"2026-11-13T18:00:00\"}"));
        assertEquals(TreasuryMarketStatus.UNKNOWN, TreasuryMarketStatus.fromOfficialPage(
                "<article>Mercado aberto</article><style>.m-fechado { color: green; }</style>"));
        assertEquals(TreasuryMarketStatus.UNKNOWN, TreasuryMarketStatus.fromOfficialPage(button("")));
    }

    @Test public void unknownMissingAndConflictingStatusRemainUnconfirmed() {
        assertEquals(TreasuryMarketStatus.UNKNOWN, TreasuryMarketStatus.fromOfficialPage(null));
        assertEquals(TreasuryMarketStatus.UNKNOWN, TreasuryMarketStatus.fromOfficialPage(""));
        assertEquals(TreasuryMarketStatus.UNKNOWN, TreasuryMarketStatus.fromOfficialPage(button("Indisponível")));
        assertEquals(TreasuryMarketStatus.UNKNOWN, TreasuryMarketStatus.fromOfficialPage(
                button("Mercado aberto") + button("Mercado fechado")));
    }

    private static String button(String label) {
        return "<button type='button' class='other open-modal-status-mercado'>" + label + "</button>";
    }

    @Test public void readsAllFourIndicatorColorsFromTheActualPortalStyles() {
        // Rules verified in the public Portal stylesheet and the logged-in page's CSSOM.
        String css = ".td-mercado-status--aberto:before {background-color: #237131; display: inline-block;}"
                + ".td-mercado-status--suspenso:before {background-color: #ce185d; display: inline-block;}"
                + ".td-mercado-status--encerrado:before {background-color: #333; display: inline-block;}"
                + ".td-mercado-status--sem:before {background-color: #ffc914; display: inline-block;}";
        assertEquals("#237131", TreasuryMarketStatus.OPEN.indicatorColorFromPortalStyles(css));
        assertEquals("#FFC914", TreasuryMarketStatus.CLOSED.indicatorColorFromPortalStyles(css));
        assertEquals("#CE185D", TreasuryMarketStatus.SUSPENDED.indicatorColorFromPortalStyles(css));
        assertEquals("#333333", TreasuryMarketStatus.MAINTENANCE.indicatorColorFromPortalStyles(css));
    }

    @Test public void neverUsesThePublicSiteIndicatorOrThePortalFontColor() {
        String css = "svg.m-fechado rect {fill: #808080;}"
                + ".td-mercado-status {color: #767676;}"
                + ".td-mercado-status--sem::before {background-color: #ffc914;}";
        assertEquals("#FFC914", TreasuryMarketStatus.CLOSED.indicatorColorFromPortalStyles(css));
        assertEquals("", TreasuryMarketStatus.CLOSED.indicatorColorFromPortalStyles(
                "svg.m-fechado rect {fill: #808080;}"));
    }

    @Test public void missingOrConflictingIndicatorColorIsNotInvented() {
        assertEquals("", TreasuryMarketStatus.CLOSED.indicatorColorFromPortalStyles(null));
        assertEquals("", TreasuryMarketStatus.CLOSED.indicatorColorFromPortalStyles(""));
        assertEquals("", TreasuryMarketStatus.CLOSED.indicatorColorFromPortalStyles(
                ".td-mercado-status--sem:before {background-color: var(--missing);}"));
        assertEquals("", TreasuryMarketStatus.CLOSED.indicatorColorFromPortalStyles(
                ".td-mercado-status--sem:before {background-color: #123456;}"
                        + ".td-mercado-status--sem:before {background-color: #654321;}"));
    }
}
