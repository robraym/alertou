package br.com.droidboaoferta;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class PropertyPageMonitor {
    private static final String PREFS = "property_page_monitor";
    private static final String NOTIFIED_PRICES_PREFIX = "notified_prices_";
    private static final double PRICE_VERIFICATION_MARGIN = 1.10d;
    private static final PropertyPageMonitor INSTANCE = new PropertyPageMonitor();

    private Context appContext;
    private final CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();
    private volatile boolean marketReferencesRunning;
    private volatile long checkingMarketReferenceInterestId;

    private PropertyPageMonitor() {
    }

    static PropertyPageMonitor getInstance() {
        return INSTANCE;
    }

    synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        scheduler.start(this::checkAllSafely, TimeUnit.MINUTES.toMillis(PropertyMarketReferenceSettings.getCheckIntervalMinutes(appContext)));
    }

    synchronized void stop() { scheduler.stop(); }

    boolean isCheckingMarketReferences() {
        return marketReferencesRunning;
    }

    long getCheckingMarketReferenceInterestId() {
        return marketReferencesRunning ? checkingMarketReferenceInterestId : 0L;
    }

    synchronized void checkNow(Context context) {
        checkNow(context, Collections.emptyList());
    }

    synchronized void checkNow(Context context, List<Long> preferredInterestOrder) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        List<Long> order = preferredInterestOrder == null
                ? Collections.emptyList()
                : new ArrayList<>(new LinkedHashSet<>(preferredInterestOrder));
        Runnable requestedCheck = () -> checkAllSafely(order);
        if (!scheduler.isStarted()) {
            scheduler.start(this::checkAllSafely,
                    TimeUnit.MINUTES.toMillis(
                            PropertyMarketReferenceSettings.getCheckIntervalMinutes(appContext)),
                    requestedCheck);
            return;
        }
        scheduler.request(0, requestedCheck);
    }

    synchronized void checkAlertsNow(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        if (!scheduler.isStarted()) {
            scheduler.start(this::checkAllSafely,
                    TimeUnit.MINUTES.toMillis(
                            PropertyMarketReferenceSettings.getCheckIntervalMinutes(appContext)),
                    () -> checkAllSafely(false));
            return;
        }
        scheduler.request(0, () -> checkAllSafely(false));
    }

    synchronized void checkAlertNow(Context context, long interestId) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        if (!scheduler.isStarted()) {
            scheduler.start(this::checkAllSafely,
                    TimeUnit.MINUTES.toMillis(
                            PropertyMarketReferenceSettings.getCheckIntervalMinutes(appContext)),
                    () -> checkInterestSafely(interestId));
            return;
        }
        scheduler.request(0, () -> checkInterestSafely(interestId));
    }

    synchronized void rescheduleIfRunning(Context context) {
        if (!scheduler.isStarted()) return;
        stop();
        start(context);
    }

    void clearState(Context context, long interestId) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(NOTIFIED_PRICES_PREFIX + interestId)
                .apply();
        new PropertyHistoryRepository(context).clearMetadataAttemptsForInterest(interestId);
    }

    private void checkAllSafely() {
        checkAllSafely(true);
    }

    private void checkAllSafely(boolean includeMarketReferences) {
        checkAllSafely(Collections.emptyList(), includeMarketReferences);
    }

    private void checkAllSafely(List<Long> preferredInterestOrder) {
        checkAllSafely(preferredInterestOrder, true);
    }

    private void checkAllSafely(List<Long> preferredInterestOrder,
                                boolean includeMarketReferences) {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context)) {
            return;
        }
        if (includeMarketReferences) {
            marketReferencesRunning = true;
            checkingMarketReferenceInterestId = 0L;
        }
        try {
        for (Interest interest : orderPropertyInterests(
                new InterestRepository(context).getAll(), preferredInterestOrder)) {
            if (!interest.isProperty()) {
                continue;
            }
            if (!MonitorRunPolicy.isCurrent(context, interest)) continue;
            if (includeMarketReferences) {
                checkingMarketReferenceInterestId = interest.getId();
            }
            SourceCheckStatus.begin(context, interest.getId());
            context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                    .setPackage(context.getPackageName()));
            try {
                checkInterest(context, interest, includeMarketReferences);
            } catch (Exception error) {
                if (MonitorRunPolicy.canRun(context)) SourceCheckStatus.failed(context, interest.getId(), error);
            } finally {
                if (MonitorRunPolicy.isCurrent(context, interest)) {
                    SourceCheckStatus.finish(context, interest.getId(),
                            TimeUnit.MINUTES.toMillis(PropertyMarketReferenceSettings.getCheckIntervalMinutes(context)));
                } else {
                    SourceCheckStatus.cancel(context, interest.getId());
                }
            }
        }
        PropertyHistoryRepository.publishPendingChanges(context);
        // Also refresh status when all results are unavailable or no new offer was emitted.
        context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                .setPackage(context.getPackageName()));
        } finally {
            if (includeMarketReferences) {
                checkingMarketReferenceInterestId = 0L;
                marketReferencesRunning = false;
                context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                        .setPackage(context.getPackageName()));
            }
        }
    }

    private List<Interest> orderPropertyInterests(List<Interest> interests,
                                                  List<Long> preferredInterestOrder) {
        if (preferredInterestOrder == null || preferredInterestOrder.isEmpty()) {
            preferredInterestOrder = getSavedPropertyMarketOrder();
        }
        if (preferredInterestOrder.isEmpty()) {
            return interests;
        }
        Map<Long, Interest> byId = new HashMap<>();
        for (Interest interest : interests) {
            if (interest.isProperty()) byId.put(interest.getId(), interest);
        }
        List<Interest> ordered = new ArrayList<>();
        for (Long id : preferredInterestOrder) {
            Interest interest = byId.remove(id);
            if (interest != null) ordered.add(interest);
        }
        for (Interest interest : interests) {
            if (interest.isProperty() && byId.remove(interest.getId()) != null) {
                ordered.add(interest);
            }
        }
        return ordered;
    }

    private List<Long> getSavedPropertyMarketOrder() {
        Context context = appContext;
        if (context == null) return Collections.emptyList();
        List<ObservedOffer> references = new ArrayList<>();
        for (ObservedOffer offer : new OfferRepository(context).getRecent()) {
            if (PropertyMarketReferenceSettings.isReference(offer)) {
                references.add(offer);
            }
        }
        int sortOrder = context.getSharedPreferences("offer_preferences", Context.MODE_PRIVATE)
                .getInt("home_sort_order", 0);
        PropertyHistoryRepository history = sortOrder == 0
                ? new PropertyHistoryRepository(context) : null;
        references.sort((first, second) -> {
            if (sortOrder == 1) {
                int byName = OfferTextParser.normalize(first.getInterest())
                        .compareTo(OfferTextParser.normalize(second.getInterest()));
                return byName != 0 ? byName
                        : Long.compare(second.getObservedAt(), first.getObservedAt());
            }
            if (sortOrder == 2 || sortOrder == 3) {
                int byPrice = sortOrder == 2
                        ? Double.compare(first.getPrice(), second.getPrice())
                        : Double.compare(second.getPrice(), first.getPrice());
                return byPrice != 0 ? byPrice
                        : Long.compare(second.getObservedAt(), first.getObservedAt());
            }
            long firstTime = getVisiblePropertyTime(first, history);
            long secondTime = getVisiblePropertyTime(second, history);
            int byTime = Long.compare(secondTime, firstTime);
            return byTime != 0 ? byTime
                    : Long.compare(second.getObservedAt(), first.getObservedAt());
        });
        List<Long> order = new ArrayList<>();
        for (ObservedOffer offer : references) {
            if (!order.contains(offer.getInterestId())) order.add(offer.getInterestId());
        }
        return order;
    }

    private long getVisiblePropertyTime(ObservedOffer offer,
                                        PropertyHistoryRepository history) {
        PropertyHistoryEntry entry = history == null ? null : history.getForOffer(offer);
        return entry != null && entry.getFirstPublicationAt() > 0L
                ? entry.getFirstPublicationAt()
                : offer.getObservedAt();
    }

    private void checkInterestSafely(long interestId) {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context)) return;
        for (Interest interest : new InterestRepository(context).getAll()) {
            if (interest.getId() != interestId || !interest.isProperty()) continue;
            if (!MonitorRunPolicy.isCurrent(context, interest)) return;
            SourceCheckStatus.begin(context, interest.getId());
            context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                    .setPackage(context.getPackageName()));
            try {
                checkInterest(context, interest, false);
            } catch (Exception error) {
                if (MonitorRunPolicy.canRun(context)) {
                    SourceCheckStatus.failed(context, interest.getId(), error);
                }
            } finally {
                if (MonitorRunPolicy.isCurrent(context, interest)) {
                    SourceCheckStatus.finish(context, interest.getId(),
                            TimeUnit.MINUTES.toMillis(
                                    PropertyMarketReferenceSettings.getCheckIntervalMinutes(context)));
                } else {
                    SourceCheckStatus.cancel(context, interest.getId());
                }
            }
            break;
        }
        PropertyHistoryRepository.publishPendingChanges(context);
        context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                .setPackage(context.getPackageName()));
    }

    private void checkInterest(Context context, Interest interest,
                               boolean includeMarketReferences) throws Exception {
        PropertyPageResult result = PropertyPageClient.fetch(interest.getTerm());
        if (!MonitorRunPolicy.isCurrent(context, interest)) return;
        String propertyName = PropertyPageResult.normalizeCondominiumName(
                interest.getPropertyName());
        if (propertyName.isEmpty() && result.hasCondominiumName()) {
            propertyName = result.getCondominiumName();
            new InterestRepository(context).updatePropertyName(
                    interest.getId(), propertyName);
        }
        if (propertyName.isEmpty()) {
            propertyName = result.getCondominiumName();
        }
        long observedAt = System.currentTimeMillis();
        PropertyHistoryRepository historyRepository = new PropertyHistoryRepository(context);
        List<PropertyPageListing> matches = new ArrayList<>();
        List<String> noLongerEligibleOfferIds = new ArrayList<>();
        Map<String, Integer> historyChanges = new HashMap<>();
        Map<String, PropertyPageListing> candidates = new java.util.LinkedHashMap<>();
        boolean marketReferenceEnabled = includeMarketReferences
                && PropertyMarketReferenceSettings.isEnabled(context);
        PropertyPageListing lowestMarketListing = null;
        for (PropertyPageListing listing : historyRepository.getTrackedListings(interest.getId())) {
            candidates.put(listing.getId(), listing);
        }
        for (PropertyPageListing listing : result.getSaleListings()) {
            candidates.put(listing.getId(), listing);
        }
        for (PropertyPageListing listing : candidates.values()) {
            if (!MonitorRunPolicy.isCurrent(context, interest)) return;
            boolean previouslyObserved = historyRepository.contains(
                    interest.getId(), listing);
            boolean matchesArea = listing.matchesArea(
                    interest.getMinimumArea(), interest.getMaximumArea());
            if (!previouslyObserved && !matchesArea) continue;
            PropertyListingMetadata metadata = null;
            boolean requiresIdentity = PropertyPageClient.isQuintoAndarListingUrl(listing.getUrl());
            if (requiresIdentity && shouldVerifyIndividualPrice(
                    previouslyObserved,
                    listing.getSalePrice(),
                    interest.getMaximumPrice(),
                    marketReferenceEnabled && matchesArea)) {
                try {
                    metadata = PropertyPageClient.fetchListingMetadata(listing.getUrl());
                } catch (Exception error) {
                    SourceCheckStatus.failed(context, interest.getId(), error);
                    metadata = PropertyListingMetadata.empty();
                }
            }
            if (!MonitorRunPolicy.isCurrent(context, interest)) return;
            if (requiresIdentity && metadata != null && metadata.isUnavailableFor(listing.getId())) {
                historyRepository.markUnavailable(interest.getId(), listing, observedAt);
                forgetUnavailableNotification(context, interest.getId(), listing.getId());
                continue;
            }
            PropertyPageListing currentListing = resolveCurrentListing(listing, metadata);
            if (currentListing == null) continue;
            matchesArea = currentListing.matchesArea(
                    interest.getMinimumArea(), interest.getMaximumArea());
            boolean eligible = currentListing.matches(interest.getMinimumArea(),
                    interest.getMaximumArea(), interest.getMaximumPrice());
            if (marketReferenceEnabled && matchesArea
                    && (lowestMarketListing == null
                    || currentListing.getSalePrice() < lowestMarketListing.getSalePrice())) {
                lowestMarketListing = currentListing;
            }
            if (previouslyObserved || eligible || (marketReferenceEnabled && matchesArea)) {
                historyChanges.put(currentListing.getId(), historyRepository.recordObservation(
                        interest.getId(), currentListing, observedAt, metadata));
            }
            if (previouslyObserved && !eligible) {
                noLongerEligibleOfferIds.add("property|" + interest.getId() + "|" + currentListing.getId());
            }
            if (eligible) {
                matches.add(currentListing);
            }
        }
        if (!MonitorRunPolicy.isCurrent(context, interest)) return;
        boolean removedStaleOffer = false;
        if (!noLongerEligibleOfferIds.isEmpty()) {
            OfferRepository repository = new OfferRepository(context);
            for (String id : noLongerEligibleOfferIds) {
                removedStaleOffer |= repository.clearRecentOffer(id);
            }
        }
        OfferRepository repository = new OfferRepository(context);
        boolean changedMarketReference = includeMarketReferences
                && upsertMarketReferenceIfNeeded(
                        context,
                        repository,
                        interest,
                        propertyName,
                        lowestMarketListing,
                        observedAt
                );
        if (matches.isEmpty()) {
            if (removedStaleOffer || changedMarketReference) {
                context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                        .setPackage(context.getPackageName()));
            }
            return;
        }
        matches.sort(Comparator.comparingDouble(PropertyPageListing::getSalePrice));

        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stateKey = NOTIFIED_PRICES_PREFIX + interest.getId();
        JSONObject notifiedPrices;
        try {
            notifiedPrices = new JSONObject(preferences.getString(stateKey, "{}"));
        } catch (Exception ignored) {
            notifiedPrices = new JSONObject();
        }

        List<PropertyPageListing> changed = new ArrayList<>();
        for (PropertyPageListing listing : matches) {
            double previousPrice = notifiedPrices.optDouble(listing.getId(), Double.NaN);
            int historyChange = historyChanges.getOrDefault(
                    listing.getId(), PropertyHistoryRepository.UNCHANGED);
            if (Double.isNaN(previousPrice)
                    || historyChange == PropertyHistoryRepository.CHANGED
                    || Double.compare(listing.getSalePrice(), previousPrice) != 0) {
                changed.add(listing);
            }
            try {
                notifiedPrices.put(listing.getId(), listing.getSalePrice());
            } catch (Exception ignored) {
            }
        }
        preferences.edit().putString(stateKey, notifiedPrices.toString()).apply();
        if (changed.isEmpty()) {
            if (removedStaleOffer || changedMarketReference) {
                context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                        .setPackage(context.getPackageName()));
            }
            return;
        }

        NumberFormat areaFormat = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        areaFormat.setMaximumFractionDigits(1);
        String sourceName = PropertyPageClient.getSourceName(interest.getTerm());
        for (PropertyPageListing listing : changed) {
            repository.add(new ObservedOffer(
                    "property|" + interest.getId() + "|" + listing.getId(),
                    interest.getId(),
                    propertyName,
                    context.getString(
                            R.string.property_offer_source,
                            sourceName,
                            areaFormat.format(listing.getArea())
                    ),
                    listing.getSalePrice(),
                    interest.getMaximumPrice(),
                    observedAt,
                    listing.getUrl(),
                    ""
            ));
        }
        showNotification(context, interest, result, changed);
        context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                .setPackage(context.getPackageName()));
    }

    static boolean shouldVerifyIndividualPrice(boolean previouslyObserved,
                                               double summaryPrice,
                                               double maximumPrice) {
        return shouldVerifyIndividualPrice(previouslyObserved, summaryPrice, maximumPrice, false);
    }

    static boolean shouldVerifyIndividualPrice(boolean previouslyObserved,
                                               double summaryPrice,
                                               double maximumPrice,
                                               boolean marketReferenceCandidate) {
        return previouslyObserved
                || marketReferenceCandidate
                || summaryPrice <= maximumPrice * PRICE_VERIFICATION_MARGIN;
    }

    private boolean upsertMarketReferenceIfNeeded(Context context,
                                                  OfferRepository repository,
                                                  Interest interest,
                                                  String propertyName,
                                                  PropertyPageListing listing,
                                                  long observedAt) {
        if (!PropertyMarketReferenceSettings.isEnabled(context)) {
            return repository.clearPropertyMarketReferences();
        }
        if (listing == null) {
            // A consulta pode falhar parcialmente (por exemplo, sem metadados do anúncio).
            // Preserve a última referência válida para não fazer um dos alertas sumir da lista.
            return false;
        }
        NumberFormat areaFormat = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        areaFormat.setMaximumFractionDigits(1);
        String sourceName = PropertyPageClient.getSourceName(interest.getTerm());
        ObservedOffer marketReference = new ObservedOffer(
                PropertyMarketReferenceSettings.createOfferId(interest.getId(), listing.getId()),
                interest.getId(),
                propertyName,
                context.getString(
                        R.string.property_market_reference_source,
                        sourceName,
                        areaFormat.format(listing.getArea())
                ),
                listing.getSalePrice(),
                interest.getMaximumPrice(),
                observedAt,
                listing.getUrl(),
                ""
        );
        ObservedOffer previousReference = repository.getPropertyMarketReference(interest.getId());
        boolean newLowest = isNewLowestMarketReference(previousReference, marketReference);
        boolean changed = previousReference == null
                || !previousReference.getId().equals(marketReference.getId())
                || Double.compare(previousReference.getPrice(), marketReference.getPrice()) != 0;
        repository.replacePropertyMarketReference(marketReference);
        if (newLowest) {
            PropertyMarketWinnerStore.record(context, marketReference, previousReference.getPrice());
            showNewLowestMarketNotification(context, interest, propertyName, listing,
                    previousReference.getPrice());
        }
        return changed;
    }

    static boolean isNewLowestMarketReference(ObservedOffer previous, ObservedOffer replacement) {
        return previous != null && replacement != null
                && !previous.getId().equals(replacement.getId())
                && replacement.getPrice() < previous.getPrice();
    }

    static PropertyPageListing resolveCurrentListing(PropertyPageListing listing,
                                                       PropertyListingMetadata metadata) {
        String normalizedUrl = PropertyPageClient.normalizeListingUrl(listing.getUrl());
        if (normalizedUrl == null) return null;
        if ("Loft".equals(PropertyPageClient.getSourceName(normalizedUrl))) return listing;
        if (metadata == null || !metadata.isVerifiedFor(listing.getId())) return null;
        return new PropertyPageListing(listing.getId(), metadata.getArea(), metadata.getSalePrice(),
                listing.getDescription(), listing.getUrl(), listing.isNewAd());
    }

    private void forgetUnavailableNotification(Context context, long interestId, String listingId) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = NOTIFIED_PRICES_PREFIX + interestId;
        try {
            JSONObject prices = new JSONObject(preferences.getString(key, "{}"));
            prices.remove(listingId);
            preferences.edit().putString(key, prices.toString()).apply();
        } catch (Exception ignored) {
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(Long.hashCode(interestId) ^ 0x51A7);
    }

    private void showNotification(Context context, Interest interest, PropertyPageResult result,
                                  List<PropertyPageListing> listings) {
        if (!MonitorRunPolicy.isCurrent(context, interest)) return;
        PropertyPageListing first = listings.get(0);
        String targetUrl = listings.size() == 1 ? first.getUrl() : interest.getTerm();
        Intent openPage = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                Long.hashCode(interest.getId()) ^ 0x51A7,
                openPage,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        NumberFormat areaFormat = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        areaFormat.setMaximumFractionDigits(1);
        String title = listings.size() == 1
                ? context.getString(R.string.property_notification_title)
                : context.getString(R.string.property_notification_title_many, listings.size());
        String explanation = listings.size() == 1
                ? context.getString(
                        R.string.property_notification_explanation,
                        result.getCondominiumName(),
                        areaFormat.format(first.getArea()),
                        currency.format(first.getSalePrice())
                )
                : context.getString(
                        R.string.property_notification_explanation_many,
                        result.getCondominiumName()
                );

        AlertSoundController.configureNotificationChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context,
                AlertSoundController.getChannelId(context)
        )
                .setSmallIcon(R.drawable.ic_notification_offer)
                .setContentTitle(title)
                .setContentText(explanation)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(explanation))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(AlertSoundController.getSoundUri(context))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        manager.notify(Long.hashCode(interest.getId()) ^ 0x51A7, builder.build());
        AlertSoundController.playSelectedSound(context);
    }

    private void showNewLowestMarketNotification(Context context, Interest interest,
                                                  String propertyName,
                                                  PropertyPageListing listing,
                                                  double previousPrice) {
        if (!MonitorRunPolicy.isCurrent(context, interest)) return;
        Intent openPage = new Intent(Intent.ACTION_VIEW, Uri.parse(listing.getUrl()));
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                Long.hashCode(interest.getId()) ^ 0x651A,
                openPage,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        NumberFormat areaFormat = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        areaFormat.setMaximumFractionDigits(1);
        String explanation = context.getString(R.string.property_new_lowest_notification_explanation,
                propertyName, areaFormat.format(listing.getArea()),
                currency.format(listing.getSalePrice()), currency.format(previousPrice));
        AlertSoundController.configureNotificationChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, AlertSoundController.getChannelId(context))
                .setSmallIcon(R.drawable.ic_notification_offer)
                .setContentTitle(context.getString(R.string.property_new_lowest_notification_title))
                .setContentText(explanation)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(explanation))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(AlertSoundController.getSoundUri(context))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(Long.hashCode(interest.getId()) ^ 0x651A, builder.build());
        AlertSoundController.playSelectedSound(context);
    }
}
