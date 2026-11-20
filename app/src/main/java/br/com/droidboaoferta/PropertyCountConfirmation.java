package br.com.droidboaoferta;

final class PropertyCountConfirmation {
    interface Reader { PropertyPageResult read() throws Exception; }

    static boolean accept(int previousCount, PropertyPageResult candidate, Reader reader) {
        if (!consistent(candidate)) return false;
        if (previousCount == candidate.getSaleListingsCount()) return true;
        try {
            PropertyPageResult confirmation = reader.read();
            return consistent(confirmation)
                    && confirmation.getSaleListingsCount() == candidate.getSaleListingsCount()
                    && (!candidate.hasCondominiumName() || !confirmation.hasCondominiumName()
                    || candidate.getCondominiumName().equals(confirmation.getCondominiumName()));
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static boolean consistent(PropertyPageResult result) {
        return result != null && result.hasReliableSaleListingsCount()
                && result.getSaleListingsCount() >= result.getSaleListings().size();
    }
}
