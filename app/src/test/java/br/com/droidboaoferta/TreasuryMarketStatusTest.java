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

    @Test public void readsIndicatorColorForEveryStatusWithoutChoosingItFromTheLabel() {
        String[] labels = {"Mercado aberto", "Mercado fechado", "Mercado suspenso", "Em manutenção"};
        String[] colors = {"#21DE72", "#FFC914", "#FF8900", "#FFD033"};
        for (int i = 0; i < labels.length; i++) {
            String html = button(labels[i] + "<svg class='m-indicator'><rect fill='" + colors[i] + "'/></svg>");
            assertEquals(colors[i], TreasuryMarketStatus.indicatorColorFromOfficialPage(html));
        }
        assertEquals("#123456", TreasuryMarketStatus.indicatorColorFromOfficialPage(
                button("Mercado fechado<svg class='m-indicator'><rect fill='#123456'/></svg>")));
    }

    @Test public void cssIndicatorFillOverridesSvgAttributeAndNeverUsesTheFontColor() {
        String html = "<style>:root { --indicator: #808080; }"
                + ".open-modal-status-mercado {color: #07172C;}"
                + "svg.m-fechado rect {fill: var(--indicator) !important;}</style>"
                + button("Mercado fechado<svg class='m-fechado'><rect fill='#21DE72'/></svg>");
        assertEquals("#808080", TreasuryMarketStatus.indicatorColorFromOfficialPage(html));
    }

    @Test public void missingOrConflictingIndicatorColorIsNotInvented() {
        assertEquals("", TreasuryMarketStatus.indicatorColorFromOfficialPage(button("Mercado fechado")));
        assertEquals("", TreasuryMarketStatus.indicatorColorFromOfficialPage(
                "<style>.m-fechado rect {fill: var(--missing);}</style>"
                        + button("Mercado fechado<svg class='m-fechado'><rect fill='#21DE72'/></svg>")));
        assertEquals("", TreasuryMarketStatus.indicatorColorFromOfficialPage(
                "<style>.m-fechado rect {fill: #123456;} .m-fechado rect {fill: #654321;}</style>"
                        + button("Mercado fechado<svg class='m-fechado'><rect fill='#21DE72'/></svg>")));
    }
}
