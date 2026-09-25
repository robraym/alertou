package br.com.droidboaoferta;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
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
    static final String ACTION_MARKET_REFERENCE_PROGRESS =
            "br.com.droidboaoferta.PROPERTY_MARKET_REFERENCE_PROGRESS";
    static final String EXTRA_PROGRESS_IS_ZIP = "property_zip_progress";
    private static final String STATUS_PREFS = "property_market_check_status";
    private static final String KEY_LAST_DURATION = "last_duration";
    private static final String PREFS = "property_page_monitor";
    private static final String NOTIFIED_PRICES_PREFIX = "notified_prices_";
    private static final double PRICE_VERIFICATION_MARGIN = 1.10d;
    private static final PropertyPageMonitor INSTANCE = new PropertyPageMonitor();

    private Context appContext;
    private final CoalescingCheckScheduler condominiumScheduler = new CoalescingCheckScheduler();
    private final CoalescingCheckScheduler zipScheduler = new CoalescingCheckScheduler();
    // Compatibilidade com os fluxos internos antigos, que permanecem sem uso nas consultas agendadas.
    private final CoalescingCheckScheduler scheduler = condominiumScheduler;
    private volatile boolean marketReferencesRunning;
    private volatile boolean zipReferencesRunning;
    private volatile long checkingMarketReferenceInterestId;
    private volatile int checkingMarketReferencePosition;
    private volatile int checkingMarketReferenceTotal;
    private volatile int checkingPropertyZipPosition;
    private volatile int checkingPropertyZipTotal;
    private volatile boolean checkingPropertyZip;
    private volatile int checkingPropertyZipListingPosition;
    private volatile int checkingPropertyZipListingTotal;
    private volatile long marketReferencesStartedAt;
    private volatile long zipReferencesStartedAt;
    private volatile boolean manualZipCheckRunning;
    private volatile long manualZipCheckStartedAt;
    private volatile int manualZipListingPosition;
    private volatile int manualZipListingTotal;
    private volatile boolean manualCondominiumCheckRunning;
    private volatile long manualCondominiumCheckStartedAt;
    private volatile long manualCondominiumInterestId;
    private volatile int manualCondominiumPosition;
    private volatile int manualCondominiumTotal;

    private PropertyPageMonitor() {
    }

    static PropertyPageMonitor getInstance() {
        return INSTANCE;
    }

    synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        reconcileSchedulers();
    }

    synchronized void stop() {
        condominiumScheduler.stop();
        zipScheduler.stop();
        manualZipCheckRunning = false;
        manualZipCheckStartedAt = 0L;
        manualZipListingPosition = 0;
        manualZipListingTotal = 0;
        manualCondominiumCheckRunning = false;
        manualCondominiumCheckStartedAt = 0L;
        manualCondominiumInterestId = 0L;
        manualCondominiumPosition = 0;
        manualCondominiumTotal = 0;
    }

    boolean isCheckingMarketReferences() {
        return marketReferencesRunning || zipReferencesRunning || manualZipCheckRunning
                || manualCondominiumCheckRunning;
    }

    long getCheckingMarketReferenceInterestId() {
        return marketReferencesRunning ? checkingMarketReferenceInterestId
                : (manualCondominiumCheckRunning ? manualCondominiumInterestId : 0L);
    }

    int getCheckingMarketReferencePosition() {
        return marketReferencesRunning ? checkingMarketReferencePosition
                : (manualCondominiumCheckRunning ? manualCondominiumPosition : 0);
    }

    int getCheckingMarketReferenceTotal() {
        return marketReferencesRunning ? checkingMarketReferenceTotal
                : (manualCondominiumCheckRunning ? manualCondominiumTotal : 0);
    }

    int getCheckingPropertyZipPosition() {
        return zipReferencesRunning ? checkingPropertyZipPosition : 0;
    }

    int getCheckingPropertyZipTotal() {
        return zipReferencesRunning ? checkingPropertyZipTotal : 0;
    }

    boolean isCheckingPropertyZip() {
        return manualZipCheckRunning || zipReferencesRunning;
    }

    boolean isCheckingPropertyCondominium() {
        return manualCondominiumCheckRunning || (marketReferencesRunning
                && checkingMarketReferenceInterestId != 0L);
    }

    int getCheckingPropertyZipListingPosition() {
        if (manualZipCheckRunning) return manualZipListingPosition;
        return isCheckingPropertyZip() ? checkingPropertyZipListingPosition : 0;
    }

    int getCheckingPropertyZipListingTotal() {
        if (manualZipCheckRunning) return manualZipListingTotal;
        return isCheckingPropertyZip() ? checkingPropertyZipListingTotal : 0;
    }

    long getCurrentMarketReferencesDurationMillis() {
        return getCurrentMarketReferencesDurationMillis(false);
    }

    long getCurrentMarketReferencesDurationMillis(boolean zip) {
        if (zip && zipReferencesRunning && zipReferencesStartedAt > 0L) {
            return Math.max(0L, SystemClock.elapsedRealtime() - zipReferencesStartedAt);
        }
        if (manualZipCheckRunning && manualZipCheckStartedAt > 0L) {
            return Math.max(0L, SystemClock.elapsedRealtime() - manualZipCheckStartedAt);
        }
        if (manualCondominiumCheckRunning && manualCondominiumCheckStartedAt > 0L) {
            return Math.max(0L, SystemClock.elapsedRealtime() - manualCondominiumCheckStartedAt);
        }
        long startedAt = marketReferencesStartedAt;
        return !marketReferencesRunning || startedAt <= 0L ? 0L
                : Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
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
        if (!order.isEmpty()) {
            requestSelectedChecks(order);
            return;
        }
        reconcileSchedulers();
        requestCategoryCheck(false);
        requestCategoryCheck(true);
    }

    synchronized void checkAlertsNow(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        reconcileSchedulers();
        requestCategoryCheck(false);
        requestCategoryCheck(true);
    }

    synchronized void checkAlertNow(Context context, long interestId) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        Interest interest = findPropertyInterest(interestId);
        if (interest == null || !PropertyMarketReferenceSettings.isVisible(appContext,
                interest.isPropertyZip())) return;
        reconcileScheduler(interest.isPropertyZip());
        schedulerFor(interest.isPropertyZip()).request(0, () -> checkInterestSafely(interestId));
    }

    synchronized void rescheduleIfRunning(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        reconcileSchedulers();
    }

    private void reconcileSchedulers() {
        reconcileScheduler(false);
        reconcileScheduler(true);
    }

    private void reconcileScheduler(boolean zip) {
        CoalescingCheckScheduler scheduler = schedulerFor(zip);
        if (!PropertyMarketReferenceSettings.isVisible(appContext, zip)) {
            scheduler.stop();
            return;
        }
        long intervalMillis = TimeUnit.SECONDS.toMillis(
                PropertyMarketReferenceSettings.getCheckIntervalSeconds(appContext, zip));
        if (!scheduler.isStarted()) {
            scheduler.start(() -> checkCategorySafely(zip), intervalMillis);
        } else {
            scheduler.updateInterval(intervalMillis);
        }
    }

    private void requestCategoryCheck(boolean zip) {
        if (!PropertyMarketReferenceSettings.isVisible(appContext, zip)) return;
        CoalescingCheckScheduler scheduler = schedulerFor(zip);
        if (scheduler.isStarted()) scheduler.request(0, () -> checkCategorySafely(zip));
    }

    private CoalescingCheckScheduler schedulerFor(boolean zip) {
        return zip ? zipScheduler : condominiumScheduler;
    }

    private void requestSelectedChecks(List<Long> order) {
        List<Long> condominium = new ArrayList<>();
        List<Long> zip = new ArrayList<>();
        for (Long interestId : order) {
            Interest interest = findPropertyInterest(interestId);
            if (interest == null) continue;
            (interest.isPropertyZip() ? zip : condominium).add(interestId);
        }
        if (!condominium.isEmpty()) {
            reconcileScheduler(false);
            condominiumScheduler.request(0,
                    () -> checkSelectedInterestsSafely(condominium));
        }
        if (!zip.isEmpty()) {
            reconcileScheduler(true);
            zipScheduler.request(0, () -> checkSelectedInterestsSafely(zip));
        }
    }

    synchronized void cancelCurrentCheck(Context context, boolean zip) {
        appContext = context.getApplicationContext();
        CoalescingCheckScheduler scheduler = schedulerFor(zip);
        scheduler.stop();
        for (Interest interest : new InterestRepository(appContext).getAll()) {
            if (interest.isProperty() && interest.isPropertyZip() == zip
                    && SourceCheckStatus.isRunning(appContext, interest.getId())) {
                SourceCheckStatus.cancel(appContext, interest.getId());
            }
        }
        if (zip) {
            checkingPropertyZip = false;
            checkingPropertyZipPosition = 0;
            checkingPropertyZipTotal = 0;
            checkingPropertyZipListingPosition = 0;
            checkingPropertyZipListingTotal = 0;
            zipReferencesStartedAt = 0L;
            zipReferencesRunning = false;
            manualZipCheckRunning = false;
            manualZipCheckStartedAt = 0L;
            manualZipListingPosition = 0;
            manualZipListingTotal = 0;
        } else {
            checkingMarketReferenceInterestId = 0L;
            checkingMarketReferencePosition = 0;
            checkingMarketReferenceTotal = 0;
            marketReferencesStartedAt = 0L;
            marketReferencesRunning = false;
            manualCondominiumCheckRunning = false;
            manualCondominiumCheckStartedAt = 0L;
            manualCondominiumInterestId = 0L;
            manualCondominiumPosition = 0;
            manualCondominiumTotal = 0;
        }
        sendProgressBroadcast(appContext, zip);
        if (MonitorRunPolicy.canRun(appContext)
                && PropertyMarketReferenceSettings.isVisible(appContext, zip)) {
            long intervalMillis = TimeUnit.SECONDS.toMillis(
                    PropertyMarketReferenceSettings.getCheckIntervalSeconds(appContext, zip));
            scheduler.startDelayed(() -> checkCategorySafely(zip), intervalMillis,
                    intervalMillis);
        }
    }

    private Interest findPropertyInterest(long interestId) {
        for (Interest interest : new InterestRepository(appContext).getAll()) {
            if (interest.getId() == interestId && interest.isProperty()) return interest;
        }
        return null;
    }

    private void checkCategorySafely(boolean zip) {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context)
                || !PropertyMarketReferenceSettings.isVisible(context, zip)) return;
        long startedAt = SystemClock.elapsedRealtime();
        List<Interest> interests = new ArrayList<>();
        for (Interest interest : orderPropertyInterests(new InterestRepository(context).getAll(),
                Collections.emptyList())) {
            if (interest.isProperty() && interest.isPropertyZip() == zip
                    && MonitorRunPolicy.isCurrent(context, interest)) {
                interests.add(interest);
            }
        }
        if (zip) {
            zipReferencesRunning = true;
            zipReferencesStartedAt = startedAt;
            checkingPropertyZip = true;
            checkingPropertyZipPosition = 0;
            checkingPropertyZipTotal = interests.size();
            checkingPropertyZipListingPosition = 0;
            checkingPropertyZipListingTotal = 0;
        } else {
            marketReferencesRunning = true;
            marketReferencesStartedAt = startedAt;
            checkingMarketReferenceInterestId = 0L;
            checkingMarketReferencePosition = 0;
            checkingMarketReferenceTotal = interests.size();
        }
        try {
            for (Interest interest : interests) {
                if (!PropertyMarketReferenceSettings.isVisible(context, zip)
                        || !MonitorRunPolicy.isCurrent(context, interest)) break;
                if (zip) {
                    checkingPropertyZipPosition++;
                    checkingPropertyZipListingPosition = 0;
                    checkingPropertyZipListingTotal = 0;
                } else {
                    checkingMarketReferenceInterestId = interest.getId();
                    checkingMarketReferencePosition++;
                }
                SourceCheckStatus.begin(context, interest.getId());
                sendProgressBroadcast(context, zip);
                try {
                    checkInterest(context, interest, true);
                } catch (Exception error) {
                    if (MonitorRunPolicy.canRun(context)) {
                        SourceCheckStatus.failed(context, interest.getId(), error);
                    }
                } finally {
                    if (MonitorRunPolicy.isCurrent(context, interest)) {
                        SourceCheckStatus.finish(context, interest.getId(), TimeUnit.SECONDS.toMillis(
                                PropertyMarketReferenceSettings.getCheckIntervalSeconds(context, zip)));
                    } else {
                        SourceCheckStatus.cancel(context, interest.getId());
                    }
                    sendProgressBroadcast(context, zip);
                }
            }
            PropertyHistoryRepository.publishPendingChanges(context);
        } finally {
            if (zip) {
                checkingPropertyZip = false;
                checkingPropertyZipPosition = 0;
                checkingPropertyZipTotal = 0;
                checkingPropertyZipListingPosition = 0;
                checkingPropertyZipListingTotal = 0;
                zipReferencesStartedAt = 0L;
                zipReferencesRunning = false;
            } else {
                saveLastMarketCheckDuration(context,
                        Math.max(0L, SystemClock.elapsedRealtime() - startedAt));
                checkingMarketReferenceInterestId = 0L;
                checkingMarketReferencePosition = 0;
                checkingMarketReferenceTotal = 0;
                marketReferencesStartedAt = 0L;
                marketReferencesRunning = false;
            }
            context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                    .setPackage(context.getPackageName()));
        }
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
        long marketCheckStartedAt = includeMarketReferences ? SystemClock.elapsedRealtime() : 0L;
        List<Interest> orderedInterests = orderPropertyInterests(
                new InterestRepository(context).getAll(), preferredInterestOrder);
        // Uma consulta disparada pelo botão de uma seção deve consultar somente os alertas
        // daquela seção. A ordem preferida não pode fazer os demais imóveis rodarem depois.
        if (preferredInterestOrder != null && !preferredInterestOrder.isEmpty()) {
            orderedInterests.removeIf(interest -> !preferredInterestOrder.contains(interest.getId()));
        }
        if (includeMarketReferences) {
            marketReferencesRunning = true;
            marketReferencesStartedAt = marketCheckStartedAt;
            checkingMarketReferenceInterestId = 0L;
            checkingMarketReferencePosition = 0;
            checkingMarketReferenceTotal = countCurrentPropertyInterests(
                    context, orderedInterests, false);
            checkingPropertyZipPosition = 0;
            checkingPropertyZipTotal = countCurrentPropertyInterests(
                    context, orderedInterests, true);
            checkingPropertyZip = false;
            checkingPropertyZipListingPosition = 0;
            checkingPropertyZipListingTotal = 0;
        }
        try {
        for (Interest interest : orderedInterests) {
            if (!interest.isProperty()) {
                continue;
            }
            if (!MonitorRunPolicy.isCurrent(context, interest)) continue;
            boolean includeMarketReferenceForInterest = includeMarketReferences;
            if (includeMarketReferenceForInterest) {
                checkingMarketReferenceInterestId = interest.getId();
                checkingPropertyZip = interest.isPropertyZip();
                checkingPropertyZipListingPosition = 0;
                checkingPropertyZipListingTotal = 0;
                if (interest.isPropertyZip()) {
                    checkingPropertyZipPosition++;
                } else if (interest.isPropertyCondominium()) {
                    checkingMarketReferencePosition++;
                }
            }
            SourceCheckStatus.begin(context, interest.getId());
            // This is only a visual step change. It must not recreate the full list.
            sendProgressBroadcast(context, interest.isPropertyZip());
            try {
                checkInterest(context, interest, includeMarketReferenceForInterest);
            } catch (Exception error) {
                if (MonitorRunPolicy.canRun(context)) SourceCheckStatus.failed(context, interest.getId(), error);
            } finally {
                if (MonitorRunPolicy.isCurrent(context, interest)) {
                    SourceCheckStatus.finish(context, interest.getId(),
                            TimeUnit.SECONDS.toMillis(PropertyMarketReferenceSettings.getCheckIntervalSeconds(context)));
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
                saveLastMarketCheckDuration(context,
                        Math.max(0L, SystemClock.elapsedRealtime() - marketCheckStartedAt));
                checkingMarketReferenceInterestId = 0L;
                checkingMarketReferencePosition = 0;
                checkingMarketReferenceTotal = 0;
                checkingPropertyZipPosition = 0;
                checkingPropertyZipTotal = 0;
                checkingPropertyZip = false;
                checkingPropertyZipListingPosition = 0;
                checkingPropertyZipListingTotal = 0;
                marketReferencesStartedAt = 0L;
                marketReferencesRunning = false;
                context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                        .setPackage(context.getPackageName()));
            }
        }
    }

    private int countCurrentPropertyInterests(Context context, List<Interest> interests,
                                              boolean zipInterests) {
        int total = 0;
        for (Interest interest : interests) {
            if (MonitorRunPolicy.isCurrent(context, interest)
                    && (zipInterests ? interest.isPropertyZip()
                    : interest.isPropertyCondominium())) {
                total++;
            }
        }
        return total;
    }

    static long getLastMarketCheckDurationMillis(Context context) {
        return context.getApplicationContext().getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_DURATION, 0L);
    }

    private static void saveLastMarketCheckDuration(Context context, long durationMillis) {
        context.getApplicationContext().getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_DURATION, durationMillis).apply();
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
                            TimeUnit.SECONDS.toMillis(
                                    PropertyMarketReferenceSettings.getCheckIntervalSeconds(context)));
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
        int lowestMarketHistoryChange = PropertyHistoryRepository.UNCHANGED;
        for (PropertyPageListing listing : historyRepository.getTrackedListings(interest.getId())) {
            candidates.put(listing.getId(), listing);
        }
        for (PropertyPageListing listing : result.getSaleListings()) {
            candidates.put(listing.getId(), listing);
        }
        List<PropertyPageListing> addressCandidates = new ArrayList<>();
        for (PropertyPageListing listing : candidates.values()) {
            if (matchesPropertyZipAddress(interest, listing)) {
                addressCandidates.add(listing);
            }
        }
        updatePropertyZipListingProgress(context, interest, 0, addressCandidates.size());
        int addressCandidatePosition = 0;
        for (PropertyPageListing listing : addressCandidates) {
            if (!MonitorRunPolicy.isCurrent(context, interest)) return;
            updatePropertyZipListingProgress(context, interest,
                    ++addressCandidatePosition, addressCandidates.size());
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
            if (!matchesPropertyZipAddress(interest, currentListing)) continue;
            matchesArea = currentListing.matchesArea(
                    interest.getMinimumArea(), interest.getMaximumArea());
            boolean eligible = currentListing.matches(interest.getMinimumArea(),
                    interest.getMaximumArea(), interest.getMaximumPrice());
            int historyChange = PropertyHistoryRepository.UNCHANGED;
            if (previouslyObserved || eligible || (marketReferenceEnabled && matchesArea)) {
                historyChange = historyRepository.recordObservation(
                        interest.getId(), currentListing, observedAt, metadata);
                historyChanges.put(currentListing.getId(), historyChange);
            }
            if (marketReferenceEnabled && matchesArea
                    && (lowestMarketListing == null
                    || currentListing.getSalePrice() < lowestMarketListing.getSalePrice())) {
                lowestMarketListing = currentListing;
                lowestMarketHistoryChange = historyChange;
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
                        lowestMarketHistoryChange,
                        observedAt
                );
        if (matches.isEmpty()) {
            if (removedStaleOffer || changedMarketReference) {
                notifyPropertyOfferChanges(context);
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
                notifyPropertyOfferChanges(context);
            }
            return;
        }

        NumberFormat areaFormat = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        areaFormat.setMaximumFractionDigits(1);
        String sourceName = PropertyPageClient.getSourceName(interest.getTerm());
        for (PropertyPageListing listing : changed) {
            boolean zipInterest = interest.isPropertyZip();
            repository.add(new ObservedOffer(
                    "property|" + interest.getId() + "|" + listing.getId(),
                    interest.getId(),
                    zipInterest && !listing.getDescription().isEmpty()
                            ? listing.getDescription()
                            : propertyName,
                    context.getString(
                            R.string.property_offer_source,
                            sourceName,
                            areaFormat.format(listing.getArea())
                    ),
                    listing.getSalePrice(),
                    interest.getMaximumPrice(),
                    observedAt,
                    listing.getUrl(),
                    "",
                    zipInterest ? listing.getAddress() : ""
            ));
        }
        showNotification(context, interest, result, changed);
        notifyPropertyOfferChanges(context);
    }

    /**
     * A market refresh can update many CEP listings. Rebuilding Alertou for every listing
     * blocks touch handling; the final broadcast at the end of the refresh renders all data.
     */
    private void notifyPropertyOfferChanges(Context context) {
        if (marketReferencesRunning || manualZipCheckRunning || manualCondominiumCheckRunning) {
            return;
        }
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
                                                  int listingHistoryChange,
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
        boolean zipInterest = interest.isPropertyZip();
        ObservedOffer marketReference = new ObservedOffer(
                PropertyMarketReferenceSettings.createOfferId(interest.getId(), listing.getId()),
                interest.getId(),
                zipInterest && !listing.getDescription().isEmpty()
                        ? listing.getDescription()
                        : propertyName,
                context.getString(
                        R.string.property_market_reference_source,
                        sourceName,
                        areaFormat.format(listing.getArea())
                ),
                listing.getSalePrice(),
                interest.getMaximumPrice(),
                observedAt,
                listing.getUrl(),
                "",
                zipInterest ? listing.getAddress() : ""
        );
        ObservedOffer previousReference = repository.getPropertyMarketReference(interest.getId());
        boolean lowerReferenceReplacement = isNewLowestMarketReference(
                previousReference, marketReference);
        boolean shouldNotifyNewLowest = isNewLowestMarketReference(
                previousReference, marketReference, listingHistoryChange);
        boolean changed = previousReference == null
                || !previousReference.getId().equals(marketReference.getId())
                || Double.compare(previousReference.getPrice(), marketReference.getPrice()) != 0;
        repository.replacePropertyMarketReference(marketReference);
        // A referência anterior continua disponível no histórico mesmo se o anúncio
        // mais barato já era conhecido. O que muda é apenas a notificação.
        if (lowerReferenceReplacement) {
            PropertyMarketWinnerStore.record(context, marketReference, previousReference);
        }
        if (shouldNotifyNewLowest) {
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

    static boolean isNewLowestMarketReference(ObservedOffer previous,
                                              ObservedOffer replacement,
                                              int replacementHistoryChange) {
        return isNewLowestMarketReference(previous, replacement)
                && (replacementHistoryChange == PropertyHistoryRepository.CREATED
                || replacementHistoryChange == PropertyHistoryRepository.CHANGED);
    }

    private void checkSelectedInterestsSafely(List<Long> interestIds) {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context)) return;
        int selectedCondominiumTotal = countSelectedPropertyInterests(context, interestIds, false);
        int selectedCondominiumPosition = 0;
        boolean checkingCondominium = false;
        boolean checkingZip = false;
        try {
            for (Long interestId : interestIds) {
                for (Interest interest : new InterestRepository(context).getAll()) {
                    if (interest.getId() != interestId || !interest.isProperty()) continue;
                    if (!PropertyMarketReferenceSettings.isVisible(context,
                            interest.isPropertyZip())) break;
                    if (interest.isPropertyZip()) {
                        if (!checkingZip) {
                            checkingZip = true;
                            manualZipCheckStartedAt = SystemClock.elapsedRealtime();
                        }
                        manualZipCheckRunning = true;
                        manualZipListingPosition = 0;
                        manualZipListingTotal = 0;
                    } else if (interest.isPropertyCondominium()) {
                        if (!checkingCondominium) {
                            checkingCondominium = true;
                            manualCondominiumCheckStartedAt = SystemClock.elapsedRealtime();
                        }
                        manualCondominiumCheckRunning = true;
                        manualCondominiumInterestId = interest.getId();
                        manualCondominiumPosition = ++selectedCondominiumPosition;
                        manualCondominiumTotal = selectedCondominiumTotal;
                    }
                    SourceCheckStatus.begin(context, interest.getId());
                    sendProgressBroadcast(context, interest.isPropertyZip());
                    try {
                        checkInterest(context, interest, true);
                    } catch (Exception error) {
                        SourceCheckStatus.failed(context, interest.getId(), error);
                    } finally {
                        SourceCheckStatus.finish(context, interest.getId(),
                                TimeUnit.SECONDS.toMillis(
                                        PropertyMarketReferenceSettings.getCheckIntervalSeconds(context,
                                                interest.isPropertyZip())));
                        sendProgressBroadcast(context, interest.isPropertyZip());
                    }
                    break;
                }
            }
            PropertyHistoryRepository.publishPendingChanges(context);
        } finally {
            if (checkingZip) {
                manualZipCheckRunning = false;
                manualZipCheckStartedAt = 0L;
                manualZipListingPosition = 0;
                manualZipListingTotal = 0;
                sendProgressBroadcast(context, true);
            }
            if (checkingCondominium) {
                manualCondominiumCheckRunning = false;
                manualCondominiumCheckStartedAt = 0L;
                manualCondominiumInterestId = 0L;
                manualCondominiumPosition = 0;
                manualCondominiumTotal = 0;
                sendProgressBroadcast(context, false);
            }
            context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                    .setPackage(context.getPackageName()));
        }
    }

    private int countSelectedPropertyInterests(Context context, List<Long> interestIds,
                                                boolean zipInterests) {
        int total = 0;
        java.util.HashSet<Long> selected = new java.util.HashSet<>(interestIds);
        for (Interest interest : new InterestRepository(context).getAll()) {
            if (selected.contains(interest.getId()) && interest.isProperty()
                    && interest.isPropertyZip() == zipInterests) {
                total++;
            }
        }
        return total;
    }

    static boolean matchesPropertyZipAddress(Interest interest, PropertyPageListing listing) {
        if (interest == null || !interest.isPropertyZip()) return true;
        if (listing == null) return false;
        String requestedStreet = normalizeStreetForPropertyZip(interest.getPropertyStreet());
        String listingAddress = normalizeStreetForPropertyZip(listing.getAddress());
        return !requestedStreet.isEmpty()
                && !listingAddress.isEmpty()
                && listingAddress.contains(requestedStreet);
    }

    private void updatePropertyZipListingProgress(Context context, Interest interest,
                                                  int position, int total) {
        if (!interest.isPropertyZip()) return;
        if (manualZipCheckRunning) {
            manualZipListingPosition = Math.max(0, position);
            manualZipListingTotal = Math.max(0, total);
        } else {
            checkingPropertyZipListingPosition = Math.max(0, position);
            checkingPropertyZipListingTotal = Math.max(0, total);
        }
        sendProgressBroadcast(context, true);
    }

    private static void sendProgressBroadcast(Context context, boolean propertyZip) {
        context.sendBroadcast(new Intent(ACTION_MARKET_REFERENCE_PROGRESS)
                .putExtra(EXTRA_PROGRESS_IS_ZIP, propertyZip)
                .setPackage(context.getPackageName()));
    }

    private static String normalizeStreetForPropertyZip(String value) {
        String normalized = OfferTextParser.normalize(value)
                .replaceAll("\\b(dr|drs)\\b", "doutor")
                .replaceAll("\\b(prof|profa)\\b", "professor")
                .replaceAll("\\b(rua|r|avenida|av|alameda|travessa|estrada|rodovia)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    static PropertyPageListing resolveCurrentListing(PropertyPageListing listing,
                                                       PropertyListingMetadata metadata) {
        String normalizedUrl = PropertyPageClient.normalizeListingUrl(listing.getUrl());
        if (normalizedUrl == null) return null;
        if ("Loft".equals(PropertyPageClient.getSourceName(normalizedUrl))) return listing;
        if (metadata == null || !metadata.isVerifiedFor(listing.getId())) return null;
        return new PropertyPageListing(listing.getId(), metadata.getArea(), metadata.getSalePrice(),
                listing.getDescription(), listing.getUrl(), listing.isNewAd(),
                listing.isGoodPrice(), listing.getAddress());
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
        NumberFormat currency = CurrencyTextFormatter.displayFormatter();
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
        NumberFormat currency = CurrencyTextFormatter.displayFormatter();
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
