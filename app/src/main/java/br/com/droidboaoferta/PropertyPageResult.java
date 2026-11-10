package br.com.droidboaoferta;

import java.util.Collections;
import java.util.List;

final class PropertyPageResult {
    private final String condominiumName;
    private final boolean hasCondominiumName;
    private final List<PropertyPageListing> saleListings;
    private final int saleListingsCount;
    private final boolean hasReliableSaleListingsCount;

    PropertyPageResult(String condominiumName, List<PropertyPageListing> saleListings) {
        this(condominiumName, saleListings, saleListings == null ? 0 : saleListings.size());
    }

    PropertyPageResult(String condominiumName, List<PropertyPageListing> saleListings,
                       int saleListingsCount) {
        this(condominiumName, saleListings, saleListingsCount, false);
    }

    PropertyPageResult(String condominiumName, List<PropertyPageListing> saleListings,
                       int saleListingsCount, boolean hasReliableSaleListingsCount) {
        String normalizedName = normalizeCondominiumName(condominiumName);
        this.hasCondominiumName = !normalizedName.isEmpty();
        this.condominiumName = !hasCondominiumName
                ? "Condomínio monitorado"
                : normalizedName;
        this.saleListings = saleListings == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(saleListings);
        this.saleListingsCount = Math.max(0, saleListingsCount);
        this.hasReliableSaleListingsCount = hasReliableSaleListingsCount;
    }

    String getCondominiumName() {
        return condominiumName;
    }

    boolean hasCondominiumName() {
        return hasCondominiumName;
    }

    static String normalizeCondominiumName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceFirst("(?i)^condom[ií]nio\\s+", "").trim();
    }

    List<PropertyPageListing> getSaleListings() {
        return saleListings;
    }

    /**
     * Total de anúncios de venda informado pela fonte. Pode ser maior que a lista
     * carregada na primeira página.
     */
    int getSaleListingsCount() {
        return saleListingsCount;
    }

    /** True only when the QuintoAndar payload explicitly supplied its official total. */
    boolean hasReliableSaleListingsCount() {
        return hasReliableSaleListingsCount;
    }
}
