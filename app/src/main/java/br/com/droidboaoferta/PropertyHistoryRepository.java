package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class PropertyHistoryRepository {
    static final int UNCHANGED = 0;
    static final int CREATED = 1;
    static final int CHANGED = 2;
    private static final String PREFS = "property_history";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_MARKET_LISTING_COUNTS = "market_listing_counts";
    private static final int MARKET_LISTING_COUNT_SOURCE_VERSION = 3;
    private static final int ZIP_LISTING_COUNT_ADDRESS_VERSION = 7;
    private static final long METADATA_RETRY_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_POINTS = 60;

    private final SharedPreferences preferences;

    PropertyHistoryRepository(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    PropertyHistoryRepository(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    /** Stores the size of the full condominium result, before any alert filters. */
    synchronized void recordMarketListingCount(long interestId, int count, long observedAt) {
        recordMarketListingCount(interestId, count, observedAt,
                MARKET_LISTING_COUNT_SOURCE_VERSION);
    }

    synchronized boolean recordPropertyZipListingCount(long interestId, int count, long observedAt) {
        return recordMarketListingCount(interestId, count, observedAt, ZIP_LISTING_COUNT_ADDRESS_VERSION);
    }

    private boolean recordMarketListingCount(long interestId, int count, long observedAt,
                                          int sourceVersion) {
        if (interestId <= 0L || count < 0 || observedAt <= 0L) return false;
        JSONArray histories = readMarketListingCounts();
        JSONObject history = findMarketListingCountHistory(histories, interestId);
        if (history == null) {
            history = new JSONObject();
            histories.put(history);
        }
        JSONArray points = history.optJSONArray("points");
        if (points == null) points = new JSONArray();
        // Versões anteriores misturavam amostras da página pública. Estes pontos
        // não são comparáveis à leitura paginada da fonte usada a partir daqui.
        if (history.optInt("source_version", 0) < sourceVersion) {
            points = new JSONArray();
            history.remove("pending_count");
            history.remove("pending_observed_at");
        }
        boolean condominiumCount = sourceVersion == MARKET_LISTING_COUNT_SOURCE_VERSION;
        boolean removedLegacyPageLimit = condominiumCount && removeLegacyPageLimitedCount(points, count);
        boolean removedIsolatedDrop = condominiumCount && removeIsolatedUnconfirmedDrop(points);
        JSONObject latest = points.optJSONObject(points.length() - 1);
        if (latest != null && latest.optInt("count", -1) == count) {
            if (removedLegacyPageLimit || removedIsolatedDrop) {
                try {
                    history.put("interest_id", interestId)
                            .put("points", points)
                            .put("source_version", sourceVersion);
                    preferences.edit().putString(KEY_MARKET_LISTING_COUNTS, histories.toString()).apply();
                } catch (Exception ignored) {
                }
            }
            return false;
        }
        try {
            points.put(new JSONObject().put("observed_at", observedAt).put("count", count));
            while (points.length() > MAX_POINTS) points.remove(0);
            history.put("interest_id", interestId)
                    .put("points", points)
                    .put("source_version", sourceVersion);
            preferences.edit().putString(KEY_MARKET_LISTING_COUNTS, histories.toString()).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    synchronized PropertyMarketListingCountHistory getMarketListingCountHistory(long interestId) {
        return getMarketListingCountHistory(interestId, false);
    }

    synchronized PropertyMarketListingCountHistory getMarketListingCountHistory(long interestId,
                                                                                 boolean propertyZip) {
        JSONArray histories = readMarketListingCounts();
        JSONObject history = findMarketListingCountHistory(histories, interestId);
        List<PropertyMarketListingCountPoint> result = new ArrayList<>();
        JSONArray points = history == null ? null : history.optJSONArray("points");
        if (propertyZip && (history == null
                || history.optInt("source_version", 0) < ZIP_LISTING_COUNT_ADDRESS_VERSION)) {
            points = null;
        }
        if (!propertyZip && history != null
                && history.optInt("source_version", 0) < ZIP_LISTING_COUNT_ADDRESS_VERSION
                && (removeLegacyPageLimitedCount(points, findHighestCount(points))
                || removeIsolatedUnconfirmedDrop(points))) {
            try {
                history.put("points", points);
                preferences.edit().putString(KEY_MARKET_LISTING_COUNTS, histories.toString()).apply();
            } catch (Exception ignored) {
            }
        }
        for (int index = 0; points != null && index < points.length(); index++) {
            JSONObject point = points.optJSONObject(index);
            if (point == null) continue;
            long observedAt = point.optLong("observed_at", 0L);
            int count = point.optInt("count", -1);
            if (observedAt > 0L && count >= 0) {
                result.add(new PropertyMarketListingCountPoint(observedAt, count));
            }
        }
        return new PropertyMarketListingCountHistory(result);
    }

    /**
     * Antes da leitura do total oficial, o app gravava o limite da primeira página (20).
     * Remove somente esse primeiro ponto quando uma leitura posterior confirma total maior.
     */
    private boolean removeLegacyPageLimitedCount(JSONArray points, int confirmedCount) {
        if (points == null || points.length() == 0 || confirmedCount <= 20) return false;
        JSONObject first = points.optJSONObject(0);
        if (first == null || first.optInt("count", -1) != 20) return false;
        points.remove(0);
        return true;
    }

    private int findHighestCount(JSONArray points) {
        int highest = -1;
        for (int index = 0; points != null && index < points.length(); index++) {
            JSONObject point = points.optJSONObject(index);
            if (point != null) highest = Math.max(highest, point.optInt("count", -1));
        }
        return highest;
    }

    /**
     * Older versions accepted every parsed count, including a one-off empty response.  A point
     * surrounded by the same count cannot represent a stable market change, so discard it.
     */
    private boolean removeIsolatedUnconfirmedDrop(JSONArray points) {
        boolean changed = false;
        for (int index = 1; points != null && index < points.length() - 1; index++) {
            JSONObject previous = points.optJSONObject(index - 1);
            JSONObject current = points.optJSONObject(index);
            JSONObject next = points.optJSONObject(index + 1);
            if (previous == null || current == null || next == null) continue;
            int previousCount = previous.optInt("count", -1);
            int currentCount = current.optInt("count", -1);
            int nextCount = next.optInt("count", -1);
            if (previousCount == nextCount && isSuspiciousCountDrop(previousCount, currentCount)) {
                points.remove(index);
                changed = true;
                index--;
            }
        }
        return changed;
    }

    static boolean isSuspiciousCountDrop(int previousCount, int candidateCount) {
        if (previousCount <= 0 || candidateCount >= previousCount) return false;
        if (candidateCount == 0) return true;
        int decrease = previousCount - candidateCount;
        return decrease >= Math.max(3, (previousCount + 1) / 2);
    }

    synchronized void clearMarketListingCountHistory(long interestId) {
        JSONArray histories = readMarketListingCounts();
        JSONArray remaining = new JSONArray();
        boolean changed = false;
        for (int index = 0; index < histories.length(); index++) {
            JSONObject item = histories.optJSONObject(index);
            if (item != null && item.optLong("interest_id", 0L) == interestId) {
                changed = true;
            } else if (item != null) {
                remaining.put(item);
            }
        }
        if (changed) preferences.edit().putString(KEY_MARKET_LISTING_COUNTS, remaining.toString()).apply();
    }

    synchronized boolean shouldFetchMetadata(long interestId, PropertyPageListing listing, long now) {
        JSONObject item = find(readEntries(), interestId, listing.getId(), listing.getUrl());
        if (item == null) {
            return true;
        }
        if (item.optLong("first_publication_at", 0L) > 0L) {
            return false;
        }
        String normalizedUrl = PropertyPageClient.normalizeListingUrl(listing.getUrl());
        String previousUrl = PropertyPageClient.normalizeListingUrl(item.optString("url", ""));
        boolean urlChanged = normalizedUrl != null && !normalizedUrl.equals(previousUrl);
        return urlChanged || now - item.optLong("metadata_attempted_at", 0L) >= METADATA_RETRY_MS;
    }

    synchronized void clearMetadataAttemptsForInterest(long interestId) {
        synchronized (preferences) {
            JSONArray entries = readEntries();
            boolean changed = false;
            for (int index = 0; index < entries.length(); index++) {
                JSONObject item = entries.optJSONObject(index);
                if (item != null && item.optLong("interest_id", 0L) == interestId) {
                    item.remove("metadata_attempted_at");
                    changed = true;
                }
            }
            if (changed) {
                preferences.edit().putString(KEY_ENTRIES, entries.toString()).apply();
            }
        }
    }

    synchronized boolean contains(long interestId, PropertyPageListing listing) {
        return find(readEntries(), interestId, listing.getId(), listing.getUrl()) != null;
    }

    /**
     * A página resumida continua sendo conferida em toda consulta. Quando preço e metragem
     * são exatamente os já validados na página individual, é seguro reutilizar essa
     * validação e evitar outra requisição idêntica.
     */
    synchronized boolean hasVerifiedUnchangedListing(long interestId,
                                                      PropertyPageListing listing) {
        if (listing == null) return false;
        JSONObject item = find(readEntries(), interestId, listing.getId(), listing.getUrl());
        if (item == null || item.optInt("identity_validation_version", 0) != 1
                || !"available".equals(item.optString("validation_status", ""))) {
            return false;
        }
        JSONArray points = item.optJSONArray("points");
        JSONObject latest = points == null ? null : points.optJSONObject(points.length() - 1);
        return latest != null
                && Double.compare(latest.optDouble("price", Double.NaN),
                listing.getSalePrice()) == 0
                && Double.compare(latest.optDouble("area", Double.NaN),
                listing.getArea()) == 0;
    }

    synchronized int recordObservation(long interestId, PropertyPageListing listing,
                                       long observedAt,
                                       PropertyListingMetadata metadata) {
        return recordObservation(interestId, listing, observedAt, metadata, false);
    }

    synchronized int recordObservation(long interestId, PropertyPageListing listing,
                                       long observedAt,
                                       PropertyListingMetadata metadata,
                                       boolean publicZipPage) {
        synchronized (preferences) {
            if (PropertyPageClient.normalizeListingUrl(listing.getUrl()) == null) return UNCHANGED;
            boolean requiresIdentity = PropertyPageClient.isQuintoAndarListingUrl(listing.getUrl())
                    && !publicZipPage;
            if (requiresIdentity && (metadata == null || !metadata.isVerifiedFor(listing.getId())
                    || Double.compare(metadata.getSalePrice(), listing.getSalePrice()) != 0
                    || Double.compare(metadata.getArea(), listing.getArea()) != 0)) {
                return UNCHANGED;
            }
            JSONArray entries = readEntries();
            String identity = PropertyHistorySync.identity(listing.getUrl(), listing.getId());
            if (!identity.isEmpty()) {
                try {
                    entries = mergeEntriesForIdentity(entries, identity, interestId);
                } catch (org.json.JSONException ignored) {
                    return UNCHANGED;
                }
            }
            JSONObject item = find(entries, interestId, listing.getId(), listing.getUrl());
            boolean created = item == null;
            if (created) {
                item = new JSONObject();
                entries.put(item);
            }
            String before;
            try {
                before = PropertyHistorySync.changeKey(item);
                // A busca por CEP usa a própria página pública como leitura autoritativa.
                // Não deixe um histórico pendente do fluxo antigo bloquear a migração
                // do restante dos anúncios da rua.
                if (!publicZipPage) {
                    restoreCompatibleLegacyHistory(item, metadata);
                }
            } catch (org.json.JSONException ignored) {
                return UNCHANGED;
            }
            JSONArray points = item.optJSONArray("points");
            if (points == null) {
                points = new JSONArray();
            }
            JSONObject previous = points.optJSONObject(points.length() - 1);
            boolean changed = hasObservationChanged(previous, listing);
            try {
                item.put("interest_id", interestId)
                        .put("listing_id", listing.getId())
                        .put("title", listing.getDescription())
                        .put("url", normalizeListingUrl(listing.getUrl()))
                        .put("address", listing.getAddress())
                        .put("good_price", listing.isGoodPrice())
                        .put("first_seen_at", item.optLong("first_seen_at", observedAt))
                        .put("last_seen_at", observedAt)
                        // "Novo" indica que o Alertou encontrou o anúncio agora, não uma
                        // etiqueta permanente que a página de origem possa reutilizar.
                        .put("new_ad", created || item.optBoolean("new_ad", false));
                if (requiresIdentity || publicZipPage) {
                    item.put("identity_validation_version", 1)
                            .put("validation_status", "available");
                }
                if (metadata != null) {
                    item.put("metadata_attempted_at", observedAt);
                    if (metadata.getFirstPublicationAt() > 0L) {
                        item.put("first_publication_at", metadata.getFirstPublicationAt());
                    }
                    if (metadata.getLastPublicationAt() > 0L) {
                        item.put("last_publication_at", metadata.getLastPublicationAt());
                    }
                }
                if (changed) {
                    points.put(new JSONObject()
                            .put("observed_at", observedAt)
                            .put("price", listing.getSalePrice())
                            .put("area", listing.getArea())
                            .put("identity_verified", requiresIdentity || publicZipPage));
                    while (points.length() > MAX_POINTS) {
                        points.remove(0);
                    }
                }
                item.put("points", points);
                SharedPreferences.Editor editor = preferences.edit().putString(KEY_ENTRIES, entries.toString());
                if (!before.equals(PropertyHistorySync.changeKey(item))) editor.putBoolean("sync_dirty", true);
                editor.apply();
            } catch (Exception ignored) {
                return UNCHANGED;
            }
            return created ? CREATED : (changed ? CHANGED : UNCHANGED);
        }
    }

    static boolean hasObservationChanged(JSONObject previous, PropertyPageListing listing) {
        return previous == null
                || Double.compare(previous.optDouble("price", 0d), listing.getSalePrice()) != 0
                || Double.compare(previous.optDouble("area", 0d), listing.getArea()) != 0;
    }

    synchronized PropertyHistoryEntry getForOffer(ObservedOffer offer) {
        return getForOffer(readEntries(), offer);
    }

    /**
     * Reads the persisted history once per dashboard update instead of once per row comparison.
     */
    synchronized java.util.Map<String, PropertyHistoryEntry> getForOffers(
            List<ObservedOffer> offers) {
        java.util.Map<String, PropertyHistoryEntry> result = new java.util.HashMap<>();
        if (offers == null || offers.isEmpty()) return result;
        JSONArray entries = readEntries();
        for (ObservedOffer offer : offers) {
            PropertyHistoryEntry entry = getForOffer(entries, offer);
            if (entry != null) result.put(offer.getId(), entry);
        }
        return result;
    }

    private PropertyHistoryEntry getForOffer(JSONArray entries, ObservedOffer offer) {
        if (offer == null || (!offer.getId().startsWith("property|")
                && !PropertyMarketReferenceSettings.isReference(offer))) {
            return null;
        }
        String[] parts = offer.getId().split("\\|", 4);
        if (parts.length < 3) {
            return null;
        }
        try {
            long interestId = Long.parseLong(parts[1]);
            String identity = PropertyHistorySync.identity(offer.getLink(), parts[2]);
            if (!identity.isEmpty()) {
                JSONObject merged = mergedEntryForIdentity(entries, identity, interestId);
                if (merged != null) return toEntry(merged);
            }
            JSONObject exact = find(entries, interestId, parts[2]);
            if (exact != null && (identity.isEmpty() || identity.equals(PropertyHistorySync.identity(
                    exact.optString("url"), exact.optString("listing_id"))))) return toEntry(exact);
            for (int index = 0; !identity.isEmpty() && index < entries.length(); index++) {
                JSONObject item = entries.optJSONObject(index);
                if (item != null && identity.equals(PropertyHistorySync.identity(
                        item.optString("url"), item.optString("listing_id")))) return toEntry(item);
            }
            return null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    synchronized boolean isRecentOffer(ObservedOffer offer) {
        PropertyHistoryEntry entry = getForOffer(offer);
        return entry != null && entry.isRecent(System.currentTimeMillis());
    }

    synchronized ObservedOffer findPreviousMarketReference(ObservedOffer current, long beforeAt) {
        if (current == null || current.getInterestId() <= 0L) return null;
        JSONObject best = null;
        long bestSeenAt = 0L;
        String currentIdentity = PropertyHistorySync.identity(current.getLink(), "");
        JSONArray entries = readEntries();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject item = entries.optJSONObject(index);
            if (item == null || item.optLong("interest_id", 0L) != current.getInterestId()) continue;
            String itemIdentity = PropertyHistorySync.identity(item.optString("url", ""),
                    item.optString("listing_id", ""));
            if (!itemIdentity.isEmpty() && itemIdentity.equals(currentIdentity)) continue;
            long seenAt = item.optLong("last_seen_at", 0L);
            if (seenAt > bestSeenAt && (beforeAt <= 0L || seenAt <= beforeAt)) {
                best = item;
                bestSeenAt = seenAt;
            }
        }
        PropertyHistoryEntry entry = toEntry(best);
        if (entry == null || entry.getUrl().isEmpty() || entry.getPoints().isEmpty()) return null;
        PropertyHistoryPoint latest = entry.getPoints().get(entry.getPoints().size() - 1);
        if (latest.getPrice() <= 0d) return null;
        String source = PropertyPageClient.getSourceName(entry.getUrl());
        if (latest.getArea() > 0d) source += " • " + Math.round(latest.getArea()) + " m²";
        return new ObservedOffer(
                PropertyMarketReferenceSettings.createOfferId(current.getInterestId(), entry.getListingId()),
                current.getInterestId(), current.getInterest(), source, latest.getPrice(),
                current.getMaximumPrice(), entry.getLastSeenAt(), entry.getUrl(), "", entry.getTitle());
    }

    private JSONArray readEntries() {
        synchronized (preferences) {
            try {
                JSONArray entries = new JSONArray(preferences.getString(KEY_ENTRIES, "[]"));
                boolean changed = false;
                for (int index = 0; index < entries.length(); index++) {
                    changed |= restoreCompatibleLegacyHistory(entries.optJSONObject(index), null);
                }
                if (changed) {
                    preferences.edit().putString(KEY_ENTRIES, entries.toString()).apply();
                }
                return entries;
            } catch (Exception ignored) {
                return new JSONArray();
            }
        }
    }

    private JSONArray readMarketListingCounts() {
        try {
            return new JSONArray(preferences.getString(KEY_MARKET_LISTING_COUNTS, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private JSONObject findMarketListingCountHistory(JSONArray histories, long interestId) {
        for (int index = 0; index < histories.length(); index++) {
            JSONObject item = histories.optJSONObject(index);
            if (item != null && item.optLong("interest_id", 0L) == interestId) return item;
        }
        return null;
    }

    // Only an unavailable listing that has never been validated triggers this isolation.
    static boolean quarantineLegacyHistory(JSONObject item) throws org.json.JSONException {
        if (item == null || !PropertyPageClient.isQuintoAndarListingUrl(item.optString("url"))
                || item.optInt("identity_validation_version", 0) >= 1) {
            return false;
        }
        JSONObject legacy = new JSONObject(item.toString());
        item.put("legacy_unverified", legacy)
                .put("points", new JSONArray())
                .put("first_publication_at", 0L)
                .put("last_publication_at", 0L)
                .put("validation_status", "pending")
                .put("legacy_isolation_reason", "unavailable")
                .put("identity_validation_version", 1);
        return true;
    }

    // Repair the previous overly broad migration without restoring known-bad histories.
    static boolean restoreCompatibleLegacyHistory(JSONObject item, PropertyListingMetadata metadata)
            throws org.json.JSONException {
        if (item == null || !PropertyPageClient.isQuintoAndarListingUrl(item.optString("url"))
                || item.optBoolean("legacy_history_restored", false)) return false;
        JSONObject legacy = item.optJSONObject("legacy_unverified");
        if (legacy == null || item.has("legacy_isolation_reason")) return false;
        if ("unavailable".equals(item.optString("validation_status"))) {
            item.put("legacy_isolation_reason", "unavailable");
            return true;
        }
        JSONArray oldPoints = legacy.optJSONArray("points");
        JSONObject lastOld = oldPoints == null ? null : oldPoints.optJSONObject(oldPoints.length() - 1);
        if (lastOld == null) return false;
        JSONArray currentPoints = item.optJSONArray("points");
        boolean compatible = metadata != null && metadata.isVerifiedFor(item.optString("listing_id"))
                && samePriceAndArea(lastOld, metadata.getSalePrice(), metadata.getArea());
        if (!compatible && "available".equals(item.optString("validation_status"))) {
            for (int index = 0; currentPoints != null && index < currentPoints.length(); index++) {
                JSONObject point = currentPoints.optJSONObject(index);
                if (point != null && samePriceAndArea(lastOld,
                        point.optDouble("price"), point.optDouble("area"))) {
                    compatible = true;
                    break;
                }
            }
        }
        if (!compatible) return false;
        List<JSONObject> combined = new ArrayList<>();
        for (JSONArray array : new JSONArray[]{oldPoints, currentPoints}) {
            for (int index = 0; array != null && index < array.length(); index++) {
                JSONObject point = array.optJSONObject(index);
                if (point != null) combined.add(point);
            }
        }
        combined.sort(java.util.Comparator.comparingLong(point -> point.optLong("observed_at")));
        JSONArray restored = new JSONArray();
        JSONObject previous = null;
        for (JSONObject point : combined) {
            if (previous == null || !samePriceAndArea(previous,
                    point.optDouble("price"), point.optDouble("area"))) {
                restored.put(point);
                previous = point;
            }
        }
        item.put("points", restored).put("legacy_history_restored", true);
        for (String key : new String[]{"first_publication_at", "last_publication_at"}) {
            if (item.optLong(key, 0L) <= 0L) item.put(key, legacy.optLong(key, 0L));
        }
        return true;
    }

    private static boolean samePriceAndArea(JSONObject point, double price, double area) {
        return price > 0d && area > 0d
                && Double.compare(point.optDouble("price", Double.NaN), price) == 0
                && Double.compare(point.optDouble("area", Double.NaN), area) == 0;
    }

    synchronized void markUnavailable(long interestId, PropertyPageListing listing, long checkedAt) {
        synchronized (preferences) {
            JSONArray entries = readEntries();
            JSONObject item = find(entries, interestId, listing.getId(), listing.getUrl());
            if (item == null) {
                item = new JSONObject();
                entries.put(item);
            }
            try {
                String before = PropertyHistorySync.changeKey(item);
                quarantineLegacyHistory(item);
                if (item.optJSONObject("legacy_unverified") != null
                        && !item.optBoolean("legacy_history_restored", false)) {
                    item.put("legacy_isolation_reason", "unavailable");
                }
                item.put("interest_id", interestId).put("listing_id", listing.getId())
                        .put("url", listing.getUrl()).put("title", listing.getDescription())
                        .put("first_seen_at", item.optLong("first_seen_at", checkedAt))
                        .put("last_seen_at", checkedAt).put("validation_status", "unavailable")
                        .put("identity_validation_version", 1)
                        .put("last_summary_area", listing.getArea())
                        .put("last_summary_price", listing.getSalePrice());
                SharedPreferences.Editor editor = preferences.edit().putString(KEY_ENTRIES, entries.toString());
                if (!before.equals(PropertyHistorySync.changeKey(item))) editor.putBoolean("sync_dirty", true);
                editor.apply();
            } catch (org.json.JSONException ignored) {
            }
        }
    }

    synchronized List<PropertyPageListing> getTrackedListings(long interestId) {
        List<PropertyPageListing> listings = new ArrayList<>();
        JSONArray entries = readEntries();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject item = entries.optJSONObject(index);
            if (item == null || item.optLong("interest_id") != interestId
                    || !PropertyPageClient.isQuintoAndarListingUrl(item.optString("url"))) continue;
            JSONArray points = item.optJSONArray("points");
            if ((points == null || points.length() == 0) && item.optJSONObject("legacy_unverified") != null) {
                points = item.optJSONObject("legacy_unverified").optJSONArray("points");
            }
            JSONObject last = points == null ? null : points.optJSONObject(points.length() - 1);
            double area = last == null ? item.optDouble("last_summary_area", 0d) : last.optDouble("area");
            double price = last == null ? item.optDouble("last_summary_price", 0d) : last.optDouble("price");
            if (area > 0d && price > 0d) {
                listings.add(new PropertyPageListing(item.optString("listing_id"), area, price,
                        item.optString("title"), item.optString("url"), false, false,
                        item.optString("address")));
            }
        }
        return listings;
    }

    synchronized JSONArray exportForSync() {
        try {
            return PropertyHistorySync.merge(readEntries(), new JSONArray());
        } catch (org.json.JSONException ignored) {
            return new JSONArray();
        }
    }

    synchronized boolean mergeFromSync(JSONArray remote) {
        if (remote == null || remote.length() == 0) return false;
        synchronized (preferences) {
            try {
                JSONArray local = readEntries();
                JSONArray merged = PropertyHistorySync.merge(local, remote);
                // Malformed local legacy records are preserved but never exported or merged by identity.
                for (int i = 0; i < local.length(); i++) {
                    JSONObject item = local.optJSONObject(i);
                    if (item != null && PropertyHistorySync.identity(item.optString("url"),
                            item.optString("listing_id")).isEmpty()) merged.put(item);
                }
                if (PropertyHistorySync.contentKey(local).equals(PropertyHistorySync.contentKey(merged))) return false;
                preferences.edit().putString(KEY_ENTRIES, merged.toString()).apply();
                return true;
            } catch (org.json.JSONException ignored) {
                return false;
            }
        }
    }

    static void publishPendingChanges(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (consumePendingSync(prefs)) CloudSyncStore.markLocalChanged(context);
    }

    static boolean consumePendingSync(SharedPreferences prefs) {
        synchronized (prefs) {
            boolean migrated = prefs.getBoolean("sync_migrated_v1", false);
            boolean dirty = prefs.getBoolean("sync_dirty", false)
                    || (!migrated && !"[]".equals(prefs.getString(KEY_ENTRIES, "[]")));
            if (migrated && !dirty) return false;
            prefs.edit().putBoolean("sync_migrated_v1", true).putBoolean("sync_dirty", false).apply();
            return dirty;
        }
    }

    private JSONObject find(JSONArray entries, long interestId, String listingId) {
        return find(entries, interestId, listingId, null);
    }

    private JSONObject find(JSONArray entries, long interestId, String listingId, String url) {
        String identity = PropertyHistorySync.identity(url, listingId);
        for (int index = 0; index < entries.length(); index++) {
            JSONObject item = entries.optJSONObject(index);
            if (item != null && item.optLong("interest_id", 0L) == interestId
                    && listingId.equals(item.optString("listing_id", ""))
                    && (url == null || (!identity.isEmpty() && identity.equals(PropertyHistorySync.identity(
                    item.optString("url"), item.optString("listing_id")))))) {
                return item;
            }
        }
        return null;
    }

    private JSONArray mergeEntriesForIdentity(JSONArray entries, String identity, long preferredInterestId)
            throws org.json.JSONException {
        JSONArray matching = new JSONArray();
        JSONArray result = new JSONArray();
        boolean hasPreferred = false;
        for (int index = 0; index < entries.length(); index++) {
            JSONObject item = entries.optJSONObject(index);
            if (item == null) continue;
            if (identity.equals(PropertyHistorySync.identity(
                    item.optString("url"), item.optString("listing_id")))) {
                matching.put(item);
                hasPreferred |= item.optLong("interest_id", 0L) == preferredInterestId;
            } else {
                result.put(item);
            }
        }
        if (matching.length() == 0) {
            return entries;
        }
        JSONArray merged = PropertyHistorySync.merge(matching, new JSONArray());
        boolean preferredAdded = false;
        for (int index = 0; index < merged.length(); index++) {
            JSONObject item = merged.optJSONObject(index);
            if (item == null) continue;
            preferredAdded |= item.optLong("interest_id", 0L) == preferredInterestId;
            result.put(item);
        }
        if (!preferredAdded && !hasPreferred) {
            JSONObject base = merged.optJSONObject(0);
            if (base != null) {
                result.put(new JSONObject(base.toString()).put("interest_id", preferredInterestId));
            }
        }
        return result;
    }

    private JSONObject mergedEntryForIdentity(JSONArray entries, String identity, long preferredInterestId) {
        try {
            JSONArray merged = mergeEntriesForIdentity(entries, identity, preferredInterestId);
            JSONObject preferred = null;
            JSONObject fallback = null;
            for (int index = 0; index < merged.length(); index++) {
                JSONObject item = merged.optJSONObject(index);
                if (item == null || !identity.equals(PropertyHistorySync.identity(
                        item.optString("url"), item.optString("listing_id")))) continue;
                if (fallback == null) fallback = item;
                if (item.optLong("interest_id", 0L) == preferredInterestId) {
                    preferred = item;
                    break;
                }
            }
            return preferred == null ? fallback : preferred;
        } catch (org.json.JSONException ignored) {
            return null;
        }
    }

    private PropertyHistoryEntry toEntry(JSONObject item) {
        if (item == null) {
            return null;
        }
        List<PropertyHistoryPoint> points = new ArrayList<>();
        JSONArray values = item.optJSONArray("points");
        if (values != null) {
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value != null) {
                    points.add(new PropertyHistoryPoint(
                            value.optLong("observed_at", 0L),
                            value.optDouble("price", 0d),
                            value.optDouble("area", 0d)));
                }
            }
        }
        return new PropertyHistoryEntry(
                item.optLong("interest_id", 0L), item.optString("listing_id", ""),
                item.optString("title", ""), normalizeListingUrl(item.optString("url", "")),
                item.optLong("first_seen_at", 0L), item.optLong("last_seen_at", 0L),
                item.optLong("first_publication_at", 0L), item.optBoolean("new_ad", false),
                item.optBoolean("good_price", false),
                points, item.optString("validation_status", "available"),
                (item.optJSONObject("legacy_unverified") != null
                        && !item.optBoolean("legacy_history_restored", false))
                        || (item.optJSONArray("sync_quarantined_points") != null
                        && item.optJSONArray("sync_quarantined_points").length() > 0));
    }

    private String normalizeListingUrl(String url) {
        String normalizedUrl = PropertyPageClient.normalizeListingUrl(url);
        return normalizedUrl == null ? (url == null ? "" : url) : normalizedUrl;
    }
}
