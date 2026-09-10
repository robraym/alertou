package br.com.droidboaoferta;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class OfferRepository {
    private static final String PREFS = "offer_preferences";
    private static final String KEY_OFFERS = "recent_offers";
    private static final String KEY_ARCHIVED_OFFERS = "archived_offers";
    private static final String KEY_TRASHED_OFFERS = "trashed_offers";
    private static final String KEY_PROCESSED_MESSAGES = "processed_messages";
    private static final int MAX_OFFERS = 30;
    private static final int MAX_PROCESSED_MESSAGES = 500;

    private final Context context;
    private final SharedPreferences preferences;

    OfferRepository(SharedPreferences preferences) {
        this.context = null;
        this.preferences = preferences;
    }

    OfferRepository(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean markOfferProcessed(long chatId, long messageId, long interestId) {
        synchronized (OfferStorage.LOCK) {
            String key = chatId + ":" + messageId + ":" + interestId;
            List<String> processed = readProcessedMessages();
            if (processed.contains(key)) {
                return false;
            }
            processed.add(0, key);
            if (processed.size() > MAX_PROCESSED_MESSAGES) {
                processed = new ArrayList<>(processed.subList(0, MAX_PROCESSED_MESSAGES));
            }
            preferences.edit().putString(KEY_PROCESSED_MESSAGES, new JSONArray(processed).toString()).apply();
            return true;
        }
    }

    void add(ObservedOffer offer) {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> offers = new ArrayList<>(getRecentForValidation());
            offers.removeIf(item -> item.getId().equals(offer.getId())
                    || isSameObservedOffer(item, offer));
            offers.add(0, offer);
            saveOffers(KEY_OFFERS, trimOffers(sortByObservedAt(offers)));
            long changedAt = System.currentTimeMillis();
            CloudSyncStore.rememberRecentChanged(context, changedAt);
        }
    }

    void clearProcessedForInterest(long interestId) {
        synchronized (OfferStorage.LOCK) {
            List<String> processed = new ArrayList<>(readProcessedMessages());
            String suffix = ":" + interestId;
            processed.removeIf(item -> item.endsWith(suffix));
            preferences.edit().putString(KEY_PROCESSED_MESSAGES, new JSONArray(processed).toString()).apply();
        }
    }

    void clearRecentForInterest(long interestId) {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            boolean changed = recent.removeIf(offer -> offer.getInterestId() == interestId);
            if (changed) {
                saveOffers(KEY_OFFERS, recent);
                long changedAt = System.currentTimeMillis();
                CloudSyncStore.rememberRecentChanged(context, changedAt);
            }
        }
    }

    /** Keeps one current lowest-price reference for each property alert. */
    void replacePropertyMarketReference(ObservedOffer offer) {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> offers = new ArrayList<>(getRecentForValidation());
            offers.removeIf(item -> PropertyMarketReferenceSettings.isReference(item)
                    && item.getInterestId() == offer.getInterestId());
            offers.add(0, offer);
            saveOffers(KEY_OFFERS, trimOffers(sortByObservedAt(offers)));
            CloudSyncStore.rememberRecentChanged(context, System.currentTimeMillis());
        }
    }

    ObservedOffer getPropertyMarketReference(long interestId) {
        synchronized (OfferStorage.LOCK) {
            for (ObservedOffer offer : getRecentForValidation()) {
                if (PropertyMarketReferenceSettings.isReference(offer)
                        && offer.getInterestId() == interestId) {
                    return offer;
                }
            }
            return null;
        }
    }

    void clearRecentForPropertyAlert(long interestId) {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            boolean changed = recent.removeIf(offer -> offer.getInterestId() == interestId
                    && !PropertyMarketReferenceSettings.isReference(offer));
            if (changed) {
                saveOffers(KEY_OFFERS, recent);
                CloudSyncStore.rememberRecentChanged(context, System.currentTimeMillis());
            }
        }
    }

    boolean clearRecentOffer(String id) {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            boolean changed = recent.removeIf(offer -> offer.getId().equals(id));
            if (changed) {
                saveOffers(KEY_OFFERS, recent);
                CloudSyncStore.rememberRecentChanged(context, System.currentTimeMillis());
            }
            return changed;
        }
    }

    boolean clearPropertyMarketReferences() {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            boolean changed = recent.removeIf(PropertyMarketReferenceSettings::isReference);
            if (changed) {
                saveOffers(KEY_OFFERS, recent);
                CloudSyncStore.rememberRecentChanged(context, System.currentTimeMillis());
            }
            return changed;
        }
    }

    boolean clearPropertyMarketReferences(long interestId) {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            boolean changed = recent.removeIf(offer ->
                    PropertyMarketReferenceSettings.isReference(offer)
                            && offer.getInterestId() == interestId);
            if (changed) {
                saveOffers(KEY_OFFERS, recent);
                CloudSyncStore.rememberRecentChanged(context, System.currentTimeMillis());
            }
            return changed;
        }
    }

    void reconcileRecentWithInterests(List<Interest> interests) {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            List<ObservedOffer> reconciled = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (ObservedOffer offer : recent) {
                Interest matchingInterest = findMatchingInterest(offer, interests);
                boolean propertyMarketReference = PropertyMarketReferenceSettings.isReference(offer);
                if (matchingInterest == null
                        || (propertyMarketReference && !PropertyMarketReferenceSettings.isEnabled(context))
                        || (!propertyMarketReference && !matchingInterest.isCoupon()
                        && offer.getPrice() > matchingInterest.getMaximumPrice())
                        || (matchingInterest.isCoupon()
                        && offer.getPrice() < matchingInterest.getMaximumPrice())
                        || !OfferEligibility.canDisplay(offer, now)) {
                    continue;
                }
                reconciled.add(new ObservedOffer(
                        offer.getId(),
                        matchingInterest.getId(),
                        getReconciledInterestName(offer, matchingInterest),
                        offer.getSource(),
                        offer.getPrice(),
                        matchingInterest.getMaximumPrice(),
                        offer.getObservedAt(),
                        getReconciledOfferLink(offer, matchingInterest),
                        offer.getTelegramPostLink()
                ));
            }
            reconciled = trimOffers(sortByObservedAt(reconciled));
            if (areSameOfferLists(recent, reconciled)) {
                return;
            }
            saveOffers(KEY_OFFERS, reconciled);
            long changedAt = System.currentTimeMillis();
            CloudSyncStore.rememberRecentChanged(context, changedAt);
        }
    }

    private String getReconciledInterestName(ObservedOffer offer, Interest matchingInterest) {
        if (matchingInterest.isPrice()) {
            return matchingInterest.getTerm();
        }
        if (matchingInterest.isProperty()) {
            String propertyName = PropertyPageResult.normalizeCondominiumName(
                    matchingInterest.getPropertyName());
            return propertyName.isEmpty()
                    ? context.getString(R.string.property_interest_unknown_name)
                    : propertyName;
        }
        return offer.getInterest();
    }

    private String getReconciledOfferLink(ObservedOffer offer, Interest matchingInterest) {
        if (matchingInterest.isProperty()) {
            String normalizedUrl = PropertyPageClient.normalizeListingUrl(offer.getLink());
            if (normalizedUrl != null) {
                return normalizedUrl;
            }
            String listingUrl = PropertyPageClient.buildListingUrlFromOfferId(offer.getId());
            if (!listingUrl.isEmpty()) {
                return listingUrl;
            }
        }
        return offer.getLink();
    }

    private boolean areSameOfferLists(List<ObservedOffer> first, List<ObservedOffer> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!hasSameStoredValues(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasSameStoredValues(ObservedOffer first, ObservedOffer second) {
        return first.getId().equals(second.getId())
                && first.getInterestId() == second.getInterestId()
                && first.getInterest().equals(second.getInterest())
                && first.getSource().equals(second.getSource())
                && Double.compare(first.getPrice(), second.getPrice()) == 0
                && Double.compare(first.getMaximumPrice(), second.getMaximumPrice()) == 0
                && first.getObservedAt() == second.getObservedAt()
                && first.getLink().equals(second.getLink())
                && first.getTelegramPostLink().equals(second.getTelegramPostLink());
    }

    void archive(String id) {
        synchronized (OfferStorage.LOCK) {
            if (moveOffer(id, KEY_OFFERS, KEY_ARCHIVED_OFFERS)) {
                long changedAt = System.currentTimeMillis();
                CloudSyncStore.rememberRecentChanged(context, changedAt);
                CloudSyncStore.rememberArchivedChanged(context, changedAt);
                CloudSyncStore.markLocalChanged(context);
            }
        }
    }

    void unarchive(String id) {
        synchronized (OfferStorage.LOCK) {
            if (moveOffer(id, KEY_ARCHIVED_OFFERS, KEY_OFFERS)) {
                long changedAt = System.currentTimeMillis();
                CloudSyncStore.rememberRecentChanged(context, changedAt);
                CloudSyncStore.rememberArchivedChanged(context, changedAt);
                CloudSyncStore.markLocalChanged(context);
            }
        }
    }

    void unarchiveAll() {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> archived = new ArrayList<>(readOffers(KEY_ARCHIVED_OFFERS));
            if (archived.isEmpty()) {
                return;
            }
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            for (ObservedOffer offer : archived) {
                recent.removeIf(item -> item.getId().equals(offer.getId()));
                recent.add(0, offer);
            }
            preferences.edit().putString(KEY_OFFERS, OfferStorage.encode(sortByObservedAt(recent)))
                    .putString(KEY_ARCHIVED_OFFERS, "[]").apply();
            long changedAt = System.currentTimeMillis();
            CloudSyncStore.rememberRecentChanged(context, changedAt);
            CloudSyncStore.rememberArchivedChanged(context, changedAt);
            CloudSyncStore.markLocalChanged(context);
        }
    }

    void unarchive(List<ObservedOffer> offers) {
        synchronized (OfferStorage.LOCK) {
            if (offers == null || offers.isEmpty()) {
                return;
            }
            Set<String> ids = new HashSet<>();
            for (ObservedOffer offer : offers) {
                ids.add(offer.getId());
            }
            List<ObservedOffer> archived = new ArrayList<>(readOffers(KEY_ARCHIVED_OFFERS));
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            boolean changed = false;
            for (ObservedOffer offer : archived) {
                if (!ids.contains(offer.getId())) {
                    continue;
                }
                recent.removeIf(item -> item.getId().equals(offer.getId()));
                recent.add(0, offer);
                changed = true;
            }
            if (!changed) {
                return;
            }
            archived.removeIf(offer -> ids.contains(offer.getId()));
            preferences.edit().putString(KEY_OFFERS, OfferStorage.encode(sortByObservedAt(recent)))
                    .putString(KEY_ARCHIVED_OFFERS, OfferStorage.encode(archived)).apply();
            long changedAt = System.currentTimeMillis();
            CloudSyncStore.rememberRecentChanged(context, changedAt);
            CloudSyncStore.rememberArchivedChanged(context, changedAt);
            CloudSyncStore.markLocalChanged(context);
        }
    }

    void trash(String id) {
        synchronized (OfferStorage.LOCK) {
            if (moveOffer(id, KEY_OFFERS, KEY_TRASHED_OFFERS)) {
                long changedAt = System.currentTimeMillis();
                CloudSyncStore.rememberRecentChanged(context, changedAt);
                CloudSyncStore.rememberTrashChanged(context, changedAt);
            }
        }
    }

    boolean trashAllRecent() {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            if (recent.isEmpty()) {
                return false;
            }
            List<ObservedOffer> trashed = new ArrayList<>(readOffers(KEY_TRASHED_OFFERS));
            for (ObservedOffer offer : recent) {
                trashed.removeIf(item -> item.getId().equals(offer.getId()));
                trashed.add(0, offer);
            }
            preferences.edit().putString(KEY_OFFERS, "[]")
                    .putString(KEY_TRASHED_OFFERS, OfferStorage.encode(sortByObservedAt(trashed))).apply();
            long changedAt = System.currentTimeMillis();
            CloudSyncStore.rememberRecentChanged(context, changedAt);
            CloudSyncStore.rememberTrashChanged(context, changedAt);
            return true;
        }
    }

    boolean trashRecent(List<String> ids) {
        synchronized (OfferStorage.LOCK) {
            if (ids == null || ids.isEmpty()) {
                return false;
            }
            Set<String> targetIds = new HashSet<>(ids);
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            List<ObservedOffer> remaining = new ArrayList<>();
            List<ObservedOffer> trashed = new ArrayList<>(readOffers(KEY_TRASHED_OFFERS));
            boolean changed = false;
            for (ObservedOffer offer : recent) {
                if (targetIds.contains(offer.getId())) {
                    trashed.removeIf(item -> item.getId().equals(offer.getId()));
                    trashed.add(0, offer);
                    changed = true;
                } else {
                    remaining.add(offer);
                }
            }
            if (!changed) {
                return false;
            }
            preferences.edit().putString(KEY_OFFERS, OfferStorage.encode(remaining))
                    .putString(KEY_TRASHED_OFFERS, OfferStorage.encode(sortByObservedAt(trashed))).apply();
            long changedAt = System.currentTimeMillis();
            CloudSyncStore.rememberRecentChanged(context, changedAt);
            CloudSyncStore.rememberTrashChanged(context, changedAt);
            return true;
        }
    }

    void trashArchived(String id) {
        synchronized (OfferStorage.LOCK) {
            if (moveOffer(id, KEY_ARCHIVED_OFFERS, KEY_TRASHED_OFFERS)) {
                long changedAt = System.currentTimeMillis();
                CloudSyncStore.rememberArchivedChanged(context, changedAt);
                CloudSyncStore.rememberTrashChanged(context, changedAt);
                CloudSyncStore.markLocalChanged(context);
            }
        }
    }

    void restoreTrashed(String id) {
        synchronized (OfferStorage.LOCK) {
            if (moveOffer(id, KEY_TRASHED_OFFERS, KEY_OFFERS)) {
                long changedAt = System.currentTimeMillis();
                CloudSyncStore.rememberRecentChanged(context, changedAt);
                CloudSyncStore.rememberTrashChanged(context, changedAt);
            }
        }
    }

    void restoreAllTrashed() {
        synchronized (OfferStorage.LOCK) {
            List<ObservedOffer> trashed = new ArrayList<>(readOffers(KEY_TRASHED_OFFERS));
            if (trashed.isEmpty()) {
                return;
            }
            List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
            for (ObservedOffer offer : trashed) {
                recent.removeIf(item -> item.getId().equals(offer.getId()));
                recent.add(0, offer);
            }
            preferences.edit().putString(KEY_OFFERS, OfferStorage.encode(sortByObservedAt(recent)))
                    .putString(KEY_TRASHED_OFFERS, "[]").apply();
            long changedAt = System.currentTimeMillis();
            CloudSyncStore.rememberRecentChanged(context, changedAt);
            CloudSyncStore.rememberTrashChanged(context, changedAt);
        }
    }

    void deleteArchived(String id) {
        synchronized (OfferStorage.LOCK) {
            if (removeOffer(id, KEY_ARCHIVED_OFFERS)) {
                CloudSyncStore.rememberArchivedChanged(context, System.currentTimeMillis());
                CloudSyncStore.markLocalChanged(context);
            }
        }
    }

    void deleteTrashed(String id) {
        synchronized (OfferStorage.LOCK) {
            if (removeOffer(id, KEY_TRASHED_OFFERS)) {
                CloudSyncStore.rememberTrashChanged(context, System.currentTimeMillis());
            }
        }
    }

    void clearTrashed() {
        synchronized (OfferStorage.LOCK) {
            saveOffers(KEY_TRASHED_OFFERS, new ArrayList<>());
            CloudSyncStore.rememberTrashChanged(context, System.currentTimeMillis());
        }
    }

    void deleteTrashed(List<ObservedOffer> offers) {
        synchronized (OfferStorage.LOCK) {
            if (offers == null || offers.isEmpty()) {
                return;
            }
            Set<String> ids = new HashSet<>();
            for (ObservedOffer offer : offers) {
                ids.add(offer.getId());
            }
            List<ObservedOffer> trashed = new ArrayList<>(readOffers(KEY_TRASHED_OFFERS));
            if (!trashed.removeIf(offer -> ids.contains(offer.getId()))) {
                return;
            }
            saveOffers(KEY_TRASHED_OFFERS, trashed);
            CloudSyncStore.rememberTrashChanged(context, System.currentTimeMillis());
        }
    }

    List<ObservedOffer> getRecent() {
        List<ObservedOffer> recent = getRecentForValidation();
        OfferLinkValidationStore validation = new OfferLinkValidationStore(context);
        recent.removeIf(offer -> !validation.canDisplay(offer));
        return recent;
    }

    List<ObservedOffer> getRecentForValidation() {
        List<ObservedOffer> recent = new ArrayList<>(readOffers(KEY_OFFERS));
        long now = System.currentTimeMillis();
        recent.removeIf(offer -> !OfferEligibility.canDisplay(offer, now));
        return recent;
    }

    List<ObservedOffer> getArchived() {
        return readOffers(KEY_ARCHIVED_OFFERS);
    }

    List<ObservedOffer> getTrashed() {
        return readOffers(KEY_TRASHED_OFFERS);
    }

    private boolean moveOffer(String id, String fromKey, String toKey) {
        List<ObservedOffer> from = new ArrayList<>(readOffers(fromKey));
        ObservedOffer target = null;
        for (ObservedOffer offer : from) {
            if (offer.getId().equals(id)) {
                target = offer;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        from.removeIf(offer -> offer.getId().equals(id));
        List<ObservedOffer> to = new ArrayList<>(readOffers(toKey));
        to.removeIf(offer -> offer.getId().equals(id));
        to.add(0, target);
        preferences.edit().putString(fromKey, OfferStorage.encode(from))
                .putString(toKey, OfferStorage.encode(sortByObservedAt(to))).apply();
        return true;
    }

    private boolean removeOffer(String id, String key) {
        List<ObservedOffer> offers = new ArrayList<>(readOffers(key));
        boolean removed = offers.removeIf(offer -> offer.getId().equals(id));
        if (!removed) {
            return false;
        }
        saveOffers(key, offers);
        return true;
    }

    private List<ObservedOffer> trimOffers(List<ObservedOffer> offers) {
        if (offers.size() > MAX_OFFERS) {
            return new ArrayList<>(offers.subList(0, MAX_OFFERS));
        }
        return sortByObservedAt(offers);
    }

    private List<ObservedOffer> sortByObservedAt(List<ObservedOffer> offers) {
        offers.sort(Comparator.comparingLong(ObservedOffer::getObservedAt).reversed());
        return offers;
    }

    private void saveOffers(String key, List<ObservedOffer> offers) {
        preferences.edit().putString(key, OfferStorage.encode(offers)).apply();
    }

    private List<ObservedOffer> readOffers(String key) {
        return OfferStorage.read(preferences, key);
    }

    private Interest findMatchingInterest(ObservedOffer offer, List<Interest> interests) {
        for (Interest interest : interests) {
            if (offer.getInterestId() != 0L && offer.getInterestId() == interest.getId()) {
                return interest;
            }
        }
        String normalizedOfferTerm = OfferTextParser.normalize(offer.getInterest());
        for (Interest interest : interests) {
            if (normalizedOfferTerm.equals(OfferTextParser.normalize(interest.getTerm()))) {
                return interest;
            }
        }
        return null;
    }

    private boolean isSameObservedOffer(ObservedOffer first, ObservedOffer second) {
        return first.getInterestId() == second.getInterestId()
                && normalize(first.getInterest()).equals(normalize(second.getInterest()))
                && normalize(first.getSource()).equals(normalize(second.getSource()))
                && Double.compare(first.getPrice(), second.getPrice()) == 0
                && first.getObservedAt() == second.getObservedAt()
                && first.getLink().equals(second.getLink());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> readProcessedMessages() {
        List<String> processed = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_PROCESSED_MESSAGES, "[]"));
            for (int index = 0; index < array.length(); index++) {
                processed.add(array.getString(index));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return processed;
    }
}
