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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class PropertyPageMonitor {
    static final String ACTION_MARKET_REFERENCE_PROGRESS =
            "br.com.droidboaoferta.PROPERTY_MARKET_REFERENCE_PROGRESS";
    private static final String STATUS_PREFS = "property_market_check_status";
    private static final String KEY_LAST_DURATION = "last_duration";
    private static final String PREFS = "property_page_monitor";
    private static final String NOTIFIED_PRICES_PREFIX = "notified_prices_";
    private static final double PRICE_VERIFICATION_MARGIN = 1.10d;
    private static final PropertyPageMonitor INSTANCE = new PropertyPageMonitor();

    private Context appContext;
    private final CoalescingCheckScheduler condominiumScheduler = new CoalescingCheckScheduler();
    // Compatibilidade com os fluxos internos antigos, que permanecem sem uso nas consultas agendadas.
    private final CoalescingCheckScheduler scheduler = condominiumScheduler;
    private volatile boolean marketReferencesRunning;
    private volatile long checkingMarketReferenceInterestId;
    private volatile int checkingMarketReferencePosition;
    private volatile int checkingMarketReferenceTotal;
    private volatile long marketReferencesStartedAt;
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
        manualCondominiumCheckRunning = false;
        manualCondominiumCheckStartedAt = 0L;
        manualCondominiumInterestId = 0L;
        manualCondominiumPosition = 0;
        manualCondominiumTotal = 0;
    }

    boolean isCheckingMarketReferences() {
        return marketReferencesRunning
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

    boolean isCheckingPropertyCondominium() {
        return manualCondominiumCheckRunning || (marketReferencesRunning
                && checkingMarketReferenceInterestId != 0L);
    }

    long getCurrentMarketReferencesDurationMillis() {
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
        requestCategoryCheck();
    }

    synchronized void checkAlertsNow(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        reconcileSchedulers();
        requestCategoryCheck();
    }

    synchronized void checkAlertNow(Context context, long interestId) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        Interest interest = findPropertyInterest(interestId);
        if (interest == null || !PropertyMarketReferenceSettings.isVisible(appContext)) return;
        reconcileScheduler();
        schedulerFor().request(0, () -> checkInterestSafely(interestId));
    }

    synchronized void rescheduleIfRunning(Context context) {
        appContext = context.getApplicationContext();
        if (!MonitorRunPolicy.canRun(appContext)) return;
        reconcileSchedulers();
    }

    private void reconcileSchedulers() {
        reconcileScheduler();
    }

    private void reconcileScheduler() {
        CoalescingCheckScheduler scheduler = schedulerFor();
        if (!PropertyMarketReferenceSettings.isVisible(appContext)) {
            scheduler.stop();
            return;
        }
        long intervalMillis = TimeUnit.SECONDS.toMillis(
                PropertyMarketReferenceSettings.getCheckIntervalSeconds(appContext));
        if (!scheduler.isStarted()) {
            scheduler.start(() -> checkCategorySafely(), intervalMillis);
        } else {
            scheduler.updateInterval(intervalMillis);
        }
    }

    private void requestCategoryCheck() {
        if (!PropertyMarketReferenceSettings.isVisible(appContext)) return;
        CoalescingCheckScheduler scheduler = schedulerFor();
        if (scheduler.isStarted()) scheduler.request(0, () -> checkCategorySafely());
    }


    private CoalescingCheckScheduler schedulerFor() {
        return condominiumScheduler;
    }

    private void requestSelectedChecks(List<Long> order) {
        List<Long> condominium = new ArrayList<>();
        for (Long interestId : order) {
            Interest interest = findPropertyInterest(interestId);
            if (interest == null) continue;
            (condominium).add(interestId);
        }
        if (!condominium.isEmpty()) {
            reconcileScheduler();
            condominiumScheduler.request(0,
                    () -> checkSelectedInterestsSafely(condominium));
        }
    }

    synchronized void cancelCurrentCheck(Context context) {
        appContext = context.getApplicationContext();
        CoalescingCheckScheduler scheduler = schedulerFor();
        scheduler.stop();
        for (Interest interest : new InterestRepository(appContext).getAll()) {
            if (interest.isProperty()
                    && SourceCheckStatus.isRunning(appContext, interest.getId())) {
                SourceCheckStatus.cancel(appContext, interest.getId());
            }
        }
        {
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
        sendProgressBroadcast(appContext);
        if (MonitorRunPolicy.canRun(appContext)
                && PropertyMarketReferenceSettings.isVisible(appContext)) {
            long intervalMillis = TimeUnit.SECONDS.toMillis(
                    PropertyMarketReferenceSettings.getCheckIntervalSeconds(appContext));
            scheduler.startDelayed(() -> checkCategorySafely(), intervalMillis,
                    intervalMillis);
        }
    }

    private Interest findPropertyInterest(long interestId) {
        for (Interest interest : new InterestRepository(appContext).getAll()) {
            if (interest.getId() == interestId && interest.isProperty()) return interest;
        }
        return null;
    }

    private void checkCategorySafely() {
        Context context = appContext;
        if (!MonitorRunPolicy.canRun(context)
                || !PropertyMarketReferenceSettings.isVisible(context)) return;
        long startedAt = SystemClock.elapsedRealtime();
        List<Interest> interests = new ArrayList<>();
        for (Interest interest : orderPropertyInterests(new InterestRepository(context).getAll(),
                Collections.emptyList())) {
            if (interest.isProperty()
                    && MonitorRunPolicy.isCurrent(context, interest)) {
                interests.add(interest);
            }
        }
        {
            marketReferencesRunning = true;
            marketReferencesStartedAt = startedAt;
            checkingMarketReferenceInterestId = 0L;
            checkingMarketReferencePosition = 0;
            checkingMarketReferenceTotal = interests.size();
        }
        try {
            for (Interest interest : interests) {
                if (!PropertyMarketReferenceSettings.isVisible(context)
                        || !MonitorRunPolicy.isCurrent(context, interest)) break;
                {
                    checkingMarketReferenceInterestId = interest.getId();
                    checkingMarketReferencePosition++;
                }
                SourceCheckStatus.begin(context, interest.getId());
                sendProgressBroadcast(context);
                try {
                    checkInterest(context, interest, true);
                } catch (Exception error) {
                    if (MonitorRunPolicy.canRun(context)) {
                        SourceCheckStatus.failed(context, interest.getId(), error);
                    }
                } finally {
                    if (MonitorRunPolicy.isCurrent(context, interest)) {
                        SourceCheckStatus.finish(context, interest.getId(), TimeUnit.SECONDS.toMillis(
                                PropertyMarketReferenceSettings.getCheckIntervalSeconds(context)));
                    } else {
                        SourceCheckStatus.cancel(context, interest.getId());
                    }
                    sendProgressBroadcast(context);
                    notifyPropertyInterestCompleted(context);
                }
            }
            if (CoalescingCheckScheduler.isCurrentRun()) {
                PropertyHistoryRepository.publishPendingChanges(context);
            }
        } finally {
            // Uma execução cancelada pode continuar bloqueada na rede por alguns
            // segundos. Ela não pode apagar o progresso de uma execução nova.
            if (!CoalescingCheckScheduler.isCurrentRun()) return;
            {
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
        PropertyHistoryRepository history = new PropertyHistoryRepository(context);
        history.clearMetadataAttemptsForInterest(interestId);
        history.clearMarketListingCountHistory(interestId);
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
                    context, orderedInterests);
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
                if (interest.isPropertyCondominium()) {
                    checkingMarketReferencePosition++;
                }
            }
            SourceCheckStatus.begin(context, interest.getId());
            // This is only a visual step change. It must not recreate the full list.
            sendProgressBroadcast(context);
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
                notifyPropertyInterestCompleted(context);
            }
        }
        if (CoalescingCheckScheduler.isCurrentRun()) {
            PropertyHistoryRepository.publishPendingChanges(context);
        }
        // Also refresh status when all results are unavailable or no new offer was emitted.
        context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                .setPackage(context.getPackageName()));
        } finally {
            if (!CoalescingCheckScheduler.isCurrentRun()) return;
            if (includeMarketReferences) {
                saveLastMarketCheckDuration(context,
                        Math.max(0L, SystemClock.elapsedRealtime() - marketCheckStartedAt));
                checkingMarketReferenceInterestId = 0L;
                checkingMarketReferencePosition = 0;
                checkingMarketReferenceTotal = 0;
                marketReferencesStartedAt = 0L;
                marketReferencesRunning = false;
                context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                        .setPackage(context.getPackageName()));
            }
        }
    }

    private int countCurrentPropertyInterests(Context context, List<Interest> interests) {
        int total = 0;
        for (Interest interest : interests) {
            if (MonitorRunPolicy.isCurrent(context, interest)
                    && (interest.isPropertyCondominium())) {
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
                .edit().putLong(KEY_LAST_DURATION,
                        durationMillis).apply();
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
        PropertyPageResult result = fetchPropertyPageWithRetry(context, interest,
                interest.getTerm());
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
        // O número da direita é o total oficial atual do condomínio, sem filtros de
        // preço/metragem. Ele precisa aparecer assim que a página principal respondeu,
        // enquanto os anúncios individuais ainda estão sendo verificados.
        if (interest.isPropertyCondominium()
                && isConfirmedMarketListingCount(context, interest, result, historyRepository)) {
            historyRepository.recordMarketListingCount(interest.getId(),
                    result.getSaleListingsCount(), observedAt);
            // Não passe pelo bloqueio de eventos de oferta: a contagem precisa chegar à
            // interface enquanto a consulta individual ainda está em andamento.
            notifyPropertyInterestCompleted(context);
        }
        List<PropertyPageListing> matches = new ArrayList<>();
        List<String> noLongerEligibleOfferIds = new ArrayList<>();
        Map<String, Integer> historyChanges = new HashMap<>();
        Map<String, PropertyPageListing> candidates = new java.util.LinkedHashMap<>();
        java.util.Set<String> currentPageListingIds = new java.util.HashSet<>();
        boolean marketReferenceEnabled = includeMarketReferences
                && PropertyMarketReferenceSettings.isEnabled(context);
        PropertyPageListing lowestMarketListing = null;
        int lowestMarketHistoryChange = PropertyHistoryRepository.UNCHANGED;
        for (PropertyPageListing listing : result.getSaleListings()) {
            candidates.put(listing.getId(), listing);
            currentPageListingIds.add(listing.getId());
        }
        if (interest.isPropertyCondominium()) {
}
        // A busca do condomínio pode omitir anúncios ainda ativos. Reconfira na
        // página individual os candidatos elegíveis guardados no histórico antes
        // de reconstruir a lista visível.
        if (interest.isPropertyCondominium()) {
            for (PropertyPageListing tracked : historyRepository.getTrackedListings(interest.getId())) {
                if (!candidates.containsKey(tracked.getId())
                        && tracked.matchesArea(interest.getMinimumArea(), interest.getMaximumArea())
                        && tracked.getSalePrice() <= interest.getMaximumPrice() * PRICE_VERIFICATION_MARGIN) {
                    candidates.put(tracked.getId(), tracked);
                }
            }
        }
        List<PropertyPageListing> listingsToCheck = new ArrayList<>(candidates.values());
        Map<String, PropertyListingMetadata> metadataByListing =
                fetchListingMetadataInParallel(context, interest, listingsToCheck,
                        currentPageListingIds, historyRepository, marketReferenceEnabled);
        for (PropertyPageListing listing : listingsToCheck) {
            if (!MonitorRunPolicy.isCurrent(context, interest)) return;
            boolean previouslyObserved = historyRepository.contains(
                    interest.getId(), listing);
            boolean matchesArea = listing.matchesArea(
                    interest.getMinimumArea(), interest.getMaximumArea());
            if (!previouslyObserved && !matchesArea) continue;
            PropertyListingMetadata metadata = null;
            boolean requiresIdentity = PropertyPageClient.isQuintoAndarListingUrl(listing.getUrl());
            boolean reusedVerifiedSnapshot = requiresIdentity
                    && currentPageListingIds.contains(listing.getId())
                    && historyRepository.hasVerifiedUnchangedListing(interest.getId(), listing);
            if (!reusedVerifiedSnapshot) metadata = metadataByListing.get(listing.getId());
            if (!MonitorRunPolicy.isCurrent(context, interest)) return;
            if (requiresIdentity && metadata != null && metadata.isUnavailableFor(listing.getId())) {
                historyRepository.markUnavailable(interest.getId(), listing, observedAt);
                forgetUnavailableNotification(context, interest.getId(), listing.getId());
                continue;
            }
            // O preço histórico só serve para localizar a página. Sem confirmação
            // atual, não podemos restaurar uma oferta antiga na tela.
            if (!currentPageListingIds.contains(listing.getId())
                    && requiresIdentity && (metadata == null || !metadata.isVerifiedFor(listing.getId()))) {
                continue;
            }
            PropertyPageListing currentListing = reusedVerifiedSnapshot
                    ? listing : resolveCurrentListing(listing, metadata);
            // A listagem do condomínio já foi devolvida pela fonte oficial com ID,
            // área e preço. A página individual é uma confirmação adicional: se ela
            // falhar, não pode fazer todos os anúncios válidos desaparecerem da
            // lista. Só descartamos quando a própria página confirma indisponibilidade.
            if (currentListing == null && requiresIdentity) {
                currentListing = listing;
            }
            if (currentListing == null) continue;
            matchesArea = currentListing.matchesArea(
                    interest.getMinimumArea(), interest.getMaximumArea());
            boolean eligible = currentListing.matches(interest.getMinimumArea(),
                    interest.getMaximumArea(), interest.getMaximumPrice());
            int historyChange = PropertyHistoryRepository.UNCHANGED;
            if (!reusedVerifiedSnapshot
                    && (previouslyObserved || eligible || (marketReferenceEnabled && matchesArea))) {
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
        // O limite não pode escolher os primeiros itens vindos da fonte: essa ordem
        // é de relevância do QuintoAndar e poderia excluir um imóvel mais barato.
        // Primeiro aplicamos os filtros do alerta, ordenamos pelo preço e só então
        // mantemos a quantidade configurada para este condomínio.
        matches.sort(Comparator.comparingDouble(PropertyPageListing::getSalePrice));
        if (interest.isProperty() && matches.size() > interest.getPropertyListingLimit()) {
            matches = new ArrayList<>(matches.subList(0, interest.getPropertyListingLimit()));
        }
        OfferRepository repository = new OfferRepository(context);
        boolean removedStaleOffer = false;
        // Para condomínio, uma resposta parcial da fonte não confirma que os

        if (!noLongerEligibleOfferIds.isEmpty()) {
            for (String id : noLongerEligibleOfferIds) {
                removedStaleOffer |= repository.clearRecentOffer(id);
            }
        }
        boolean changedMarketReference = includeMarketReferences
                && upsertMarketReferenceIfNeeded(
                        context,
                        repository,
                        interest,
                        propertyName,
                        lowestMarketListing,
                        lowestMarketHistoryChange,
                        observedAt);
        if (matches.isEmpty()) {
            if (removedStaleOffer || changedMarketReference) {
                notifyPropertyOfferChanges(context);
            }
            return;
        }
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

        NumberFormat areaFormat = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        areaFormat.setMaximumFractionDigits(1);
        String sourceName = PropertyPageClient.getSourceName(interest.getTerm());
        java.util.Set<String> changedListingIds = new java.util.HashSet<>();
        for (PropertyPageListing listing : changed) {
            changedListingIds.add(listing.getId());
        }
        boolean restoredMissingOffer = false;
        for (PropertyPageListing listing : matches) {
            String offerId = "property|" + interest.getId() + "|" + listing.getId();
            // A observação pode já existir no histórico, mas a lista visível pode ter
            // sido limpa por uma versão anterior. Restaure-a sem disparar novo alerta.
            if (!changedListingIds.contains(listing.getId())
                    && repository.containsRecentOffer(offerId)) {
                continue;
            }
            repository.add(new ObservedOffer(
                    offerId,
                    interest.getId(),
                    propertyName,
                    context.getString(
                            R.string.property_offer_source,
                            sourceName,
                            areaFormat.format(listing.getArea())),
                    listing.getSalePrice(),
                    interest.getMaximumPrice(),
                    observedAt,
                    listing.getUrl(),
                    "",
                    ""));
            restoredMissingOffer = true;
        }
        if (!changed.isEmpty()) {
            showNotification(context, interest, result, changed);
        }
        if (removedStaleOffer || changedMarketReference || restoredMissingOffer
                || !changed.isEmpty()) {
            notifyPropertyOfferChanges(context);
        }
    }

    /**
     * A validação da página individual complementa a listagem oficial do condomínio. Faça no
     * máximo três por vez: antes, vinte leituras sequenciais podiam acumular vários minutos
     * quando uma página demorava até o timeout.
     */
    private Map<String, PropertyListingMetadata> fetchListingMetadataInParallel(
            Context context, Interest interest, List<PropertyPageListing> candidates,
            java.util.Set<String> currentPageListingIds, PropertyHistoryRepository historyRepository,
            boolean marketReferenceEnabled) {
        Map<String, PropertyListingMetadata> result = new HashMap<>();
        List<PropertyPageListing> requests = new ArrayList<>();
        for (PropertyPageListing listing : candidates) {
            boolean previouslyObserved = historyRepository.contains(interest.getId(), listing);
            boolean matchesArea = listing.matchesArea(interest.getMinimumArea(), interest.getMaximumArea());
            boolean requiresIdentity = PropertyPageClient.isQuintoAndarListingUrl(listing.getUrl());
            boolean reusedVerifiedSnapshot = requiresIdentity
                    && currentPageListingIds.contains(listing.getId())
                    && historyRepository.hasVerifiedUnchangedListing(interest.getId(), listing);
            if (requiresIdentity && !reusedVerifiedSnapshot && shouldVerifyIndividualPrice(
                    previouslyObserved, listing.getSalePrice(), interest.getMaximumPrice(),
                    marketReferenceEnabled && matchesArea)) {
                requests.add(listing);
            }
        }
        if (requests.isEmpty()) return result;
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(3, requests.size()));
        Map<String, Future<PropertyListingMetadata>> futures = new HashMap<>();
        try {
            for (PropertyPageListing listing : requests) {
                futures.put(listing.getId(), executor.submit(
                        () -> fetchListingMetadataWithRetry(context, interest, listing)));
            }
            for (PropertyPageListing listing : requests) {
                try {
                    result.put(listing.getId(), futures.get(listing.getId()).get());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return result;
                } catch (Exception error) {
                    if (!MonitorRunPolicy.isCurrent(context, interest)) return result;
                    Throwable failure = error.getCause();
                    int attempts = failure instanceof PropertyQueryFailure.Verification
                            ? ((PropertyQueryFailure.Verification) failure).attempts : 1;
                    SourceCheckStatus.listingFailed(context, interest.getId(), listing.getId(), error, attempts);
                    result.put(listing.getId(), PropertyListingMetadata.empty());
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private PropertyListingMetadata fetchListingMetadataWithRetry(Context context, Interest interest,
                                                                  PropertyPageListing listing) throws Exception {
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (!MonitorRunPolicy.isCurrent(context, interest)) throw new InterruptedException("Cancelled");
            try {
                PropertyListingMetadata metadata = PropertyPageClient.fetchListingMetadata(listing.getUrl());
                if (!metadata.isVerifiedFor(listing.getId()) && !metadata.isUnavailableFor(listing.getId())) {
                    throw new IllegalStateException("Listing identity could not be confirmed");
                }
                return metadata;
            } catch (InterruptedException interrupted) {
                throw interrupted;
            } catch (Exception error) {
                if (attempt == 2 || !PropertyQueryFailure.retryable(error)) {
                    throw new PropertyQueryFailure.Verification(error, attempt);
                }
                Thread.sleep(300L);
            }
        }
        throw new IllegalStateException("Verification did not complete");
    }

    private PropertyPageResult fetchPropertyPageWithRetry(Context context, Interest interest,
                                                           String url) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (!MonitorRunPolicy.isCurrent(context, interest)) {
                throw new InterruptedException("Property check cancelled");
            }
            try {
                SourceCheckStatus.attempt(context, interest.getId(), attempt);
                PropertyPageResult result = PropertyPageClient.fetch(url);
                if (interest.isPropertyCondominium() && result.getSaleListings().isEmpty()
                        && !(result.hasReliableSaleListingsCount() && result.getSaleListingsCount() == 0)) {
                    throw new IllegalStateException("Empty condominium listings response");
                }
                return result;
            } catch (Exception error) {
                lastError = error;
                if (attempt == 3) break;
                SourceCheckStatus.retryIn(context, interest.getId(), 5);
                for (int second = 5; second > 0; second--) {
                    sendProgressBroadcast(context);
                    Thread.sleep(1000L);
                    if (!MonitorRunPolicy.isCurrent(context, interest)) {
                        throw new InterruptedException("Property check cancelled");
                    }
                }
                SourceCheckStatus.retryIn(context, interest.getId(), 0);
                sendProgressBroadcast(context);
            }
        }
        throw lastError;
    }

    /**
     * A missing or partially rendered page used to be interpreted as zero listings and was
     * persisted in the market-count history. A sharp drop is now accepted only after a second
     * independent read returns the exact same official total.
     */
    private boolean isConfirmedMarketListingCount(Context context, Interest interest,
                                                  PropertyPageResult result,
                                                  PropertyHistoryRepository historyRepository) {
        if (!result.hasReliableSaleListingsCount()) {
            return false;
        }
        int candidateCount = result.getSaleListingsCount();
        PropertyMarketListingCountHistory history =
                historyRepository.getMarketListingCountHistory(interest.getId());
        int previousCount = history.getLatestCount();
        if (!PropertyHistoryRepository.isSuspiciousCountDrop(previousCount, candidateCount)) {
            return true;
        }
        try {
            PropertyPageResult confirmation = PropertyPageClient.fetch(interest.getTerm());
            return confirmation.hasReliableSaleListingsCount()
                    && confirmation.getSaleListingsCount() == candidateCount;
        } catch (Exception ignored) {
            // Keep the last confirmed value. The next scheduled check will try again.
            return false;
        }
    }

    /**
     * A market refresh can update many condominium listings. Rebuilding Alertou for every listing
     * blocks touch handling; the final broadcast at the end of the refresh renders all data.
     */
    private void notifyPropertyOfferChanges(Context context) {
        if (marketReferencesRunning || manualCondominiumCheckRunning) {
            return;
        }
        context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                .setPackage(context.getPackageName()));
    }

    /**
     * Atualiza a tela após cada condomínio concluído. As validações de anúncios internos
     * continuam sem renderizações intermediárias, evitando engasgos durante a consulta.
     */
    private void notifyPropertyInterestCompleted(Context context) {
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
        ObservedOffer previousReference = repository.getPropertyMarketReference(interest.getId());
        if (listing != null && !listing.matches(interest.getMinimumArea(),
                interest.getMaximumArea(), interest.getMaximumPrice())) {
            return previousReference != null && repository.clearRecentOffer(previousReference.getId());
        }
        if (listing == null) {
            // A consulta pode falhar parcialmente (por exemplo, sem metadados do anúncio).
            // Preserve a última referência válida para não fazer um dos alertas sumir da lista.
            return previousReference != null
                    && !OfferRepository.matchesInterestPriceLimit(previousReference, interest)
                    && repository.clearRecentOffer(previousReference.getId());
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
                        areaFormat.format(listing.getArea())),
                listing.getSalePrice(),
                interest.getMaximumPrice(),
                observedAt,
                listing.getUrl(),
                "",
                "");
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
        int selectedCondominiumTotal = countSelectedPropertyInterests(context, interestIds);
        int selectedCondominiumPosition = 0;
        boolean checkingCondominium = false;
        try {
            for (Long interestId : interestIds) {
                for (Interest interest : new InterestRepository(context).getAll()) {
                    if (interest.getId() != interestId || !interest.isProperty()) continue;
                    if (!PropertyMarketReferenceSettings.isVisible(context)) break;
                    if (interest.isPropertyCondominium()) {
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
                    sendProgressBroadcast(context);
                    try {
                        checkInterest(context, interest, true);
                    } catch (Exception error) {
                        SourceCheckStatus.failed(context, interest.getId(), error);
                    } finally {
                        SourceCheckStatus.finish(context, interest.getId(),
                                TimeUnit.SECONDS.toMillis(
                                        PropertyMarketReferenceSettings.getCheckIntervalSeconds(context)));
                        sendProgressBroadcast(context);
                        notifyPropertyInterestCompleted(context);
                    }
                    break;
                }
            }
            if (CoalescingCheckScheduler.isCurrentRun()) {
                PropertyHistoryRepository.publishPendingChanges(context);
            }
        } finally {
            if (!CoalescingCheckScheduler.isCurrentRun()) return;
            if (checkingCondominium) {
                manualCondominiumCheckRunning = false;
                manualCondominiumCheckStartedAt = 0L;
                manualCondominiumInterestId = 0L;
                manualCondominiumPosition = 0;
                manualCondominiumTotal = 0;
                sendProgressBroadcast(context);
            }
            context.sendBroadcast(new Intent(OfferMonitor.ACTION_OFFER_FOUND)
                    .setPackage(context.getPackageName()));
        }
    }

    private int countSelectedPropertyInterests(Context context, List<Long> interestIds) {
        int total = 0;
        java.util.HashSet<Long> selected = new java.util.HashSet<>(interestIds);
        for (Interest interest : new InterestRepository(context).getAll()) {
            if (selected.contains(interest.getId()) && interest.isProperty()) {
                total++;
            }
        }
        return total;
    }

    private static void sendProgressBroadcast(Context context) {
        context.sendBroadcast(new Intent(ACTION_MARKET_REFERENCE_PROGRESS)
                .setPackage(context.getPackageName()));
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
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
                        currency.format(first.getSalePrice()))
                : context.getString(
                        R.string.property_notification_explanation_many,
                        result.getCondominiumName());

        AlertSoundController.configureNotificationChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context,
                AlertSoundController.getChannelId(context))
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
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
