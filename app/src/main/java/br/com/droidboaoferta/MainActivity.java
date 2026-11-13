package br.com.droidboaoferta;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AlertouActivity {
    static final String EXTRA_RESTORED_CONFIGURATION =
            "br.com.droidboaoferta.extra.RESTORED_CONFIGURATION";
    static final String ACTION_PROPERTY_DISPLAY_SETTINGS_CHANGED =
            "br.com.droidboaoferta.PROPERTY_DISPLAY_SETTINGS_CHANGED";
    private final OfferSectionCache offerSectionCache = new OfferSectionCache();
    private String renderingSectionKey;
    private String renderingSectionFingerprint;
    private static final String OFFER_PREFS = "offer_preferences";
    private static final String HOME_SORT_ORDER = "home_sort_order";
    private static final String MONITOR_ENABLED = "monitor_enabled";
    private static final String SECTION_COUPONS_EXPANDED = "home_section_coupons_expanded";
    private static final String SECTION_PROPERTY_MARKET_EXPANDED = "home_section_property_market_expanded";
    private static final String SECTION_PROPERTIES_EXPANDED = "home_section_properties_expanded";
    private static final String SECTION_TREASURY_EXPANDED = "home_section_treasury_expanded";
    private static final String SECTION_PRODUCTS_EXPANDED = "home_section_products_expanded";
    private static final String STARTUP_PREFS = "startup_preferences";
    private static final String BATTERY_NOTICE_SHOWN = "battery_notice_shown";
    private static final int REQUEST_NOTIFICATIONS = 1201;
    private static final long DASHBOARD_STARTUP_TIMEOUT_MS = 6_000L;
    private static final int SORT_RECENT = 0;
    private static final int SORT_NAME = 1;
    private static final int SORT_PRICE_ASCENDING = 2;
    private static final int SORT_PRICE_DESCENDING = 3;
    private static final int SORT_PRICE_PER_SQUARE_METER_ASCENDING = 4;
    private static final int SORT_PRICE_PER_SQUARE_METER_DESCENDING = 5;
    private static final String PROPERTY_MARKET_SUMMARY_TAG = "property_market_summary";
    private static final String PROPERTY_MARKET_DURATION_TAG = "property_market_duration";
    private static final String PROPERTY_MARKET_CARD_TAG = "property_market_card";
    private static final String PROPERTY_MARKET_ACTION_TAG = "property_market_action";
    private static final String TREASURY_ACTION_TAG = "treasury_action";
    private static final String PROPERTY_MARKET_ROW_TAG_PREFIX = "property_market_row_";
    private static final String PROPERTY_MARKET_GROUP_TAG_PREFIX = "property_market_group_";
    private static final String OFFER_SECTION_CONTENT_TAG_PREFIX = "offer_section_content_";
    private static final String OFFER_SECTION_TOGGLE_TAG_PREFIX = "offer_section_toggle_";
    private final android.os.Handler dashboardHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService dashboardExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private long dashboardRenderGeneration;
    private boolean dashboardSnapshotLoading;
    private boolean dashboardSnapshotRefreshQueued;
    // Until the first database snapshot arrives, keep a real card on screen instead of an
    // empty dashboard below the title. Subsequent refreshes always preserve the current cards.
    private boolean dashboardInitialContentRendered;
    private boolean restoredInitialDashboard;
    private List<Interest> renderingDashboardInterests;
    private boolean dashboardUpdatePending;
    private boolean propertyProgressTickerScheduled;
    private final Runnable dashboardUpdate = () -> {
        dashboardUpdatePending = false;
        refreshDashboard(false);
    };
    private final Runnable propertyMarketProgressUpdate = new Runnable() {
        @Override
        public void run() {
            propertyProgressTickerScheduled = false;
            if (!isPropertyMarketUpdating()) return;
            if (PropertyPageMonitor.getInstance().isCheckingPropertyCondominium()) {
                refreshPropertyMarketProgressText();
            }
            schedulePropertyMarketProgressUpdate();
        }
    };
    private final BroadcastReceiver offerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PropertyPageMonitor.ACTION_MARKET_REFERENCE_PROGRESS.equals(intent.getAction())) {
                {
                    refreshPropertyMarketProgressVisuals();
                }
                schedulePropertyMarketProgressUpdate();
                return;
            }
            String action = intent.getAction();
            if (TreasuryDirectMonitor.ACTION_CHECK_STATE_CHANGED.equals(action)) {
                updateTreasuryRefreshIcon();
                return;
            }
            // These actions report connection/sync progress only. They can arrive in bursts and
            // do not change the offers displayed in this screen.
            if (MonitorStatusStore.ACTION_STATUS_CHANGED.equals(action)
                    || TelegramClientManager.ACTION_CLOUD_SYNC_CHANGED.equals(action)) {
                return;
            }
            if (!dashboardUpdatePending) {
                dashboardUpdatePending = true;
                dashboardHandler.postDelayed(dashboardUpdate, 250L);
            }
        }
    };

    private TextView statusTitle;
    private TextView statusSummary;
    private ImageButton monitorToggle;
    private TextView groupsSummary;
    private TextView alertsSummary;
    private LinearLayout offersContainer;
    private View dashboardStartupSplash;
    private ProgressBar dashboardStartupProgress;
    private TextView dashboardStartupStatus;
    private ValueAnimator dashboardStartupProgressAnimator;
    private final java.util.ArrayDeque<Integer> dashboardStartupStatusQueue =
            new java.util.ArrayDeque<>();
    private boolean dashboardStartupStatusShowing;
    private Runnable dashboardStartupCompletion;
    private final Runnable dashboardStartupTimeout = () -> {
        if (!dashboardInitialContentRendered && dashboardStartupSplash != null) {
            // Storage can remain locked briefly after restoring a large backup. Let the user
            // into the dashboard and apply the real snapshot when that read eventually returns.
            renderDashboardSnapshot(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyMap());
        }
    };
    private final Runnable dashboardStartupStatusAdvance = () -> {
        dashboardStartupStatusShowing = false;
        showNextDashboardStartupStatus();
    };
    private TextView propertyMarketSummaryView;
    private TextView propertyMarketDurationView;
    private View propertyMarketCardView;
    private ImageButton propertyMarketActionView;
    private final java.util.Map<TextView, Integer> propertyMarketRowOriginalTextColors =
            new java.util.WeakHashMap<>();
    private long highlightedPropertyMarketInterestId;
    private ImageView propertyMarketSpinningIcon;
    private ObjectAnimator propertyMarketRefreshAnimator;
    private ImageButton treasuryActionView;
    private ImageView treasurySpinningIcon;
    private ObjectAnimator treasuryRefreshAnimator;
    private EditText offersSearchInput;
    private FloatingSearchController floatingSearchController;
    private InterestRepository interestRepository;
    private OfferRepository offerRepository;
    private List<ObservedOffer> displayedOffers = Collections.emptyList();
    // Dashboard-only snapshot. Never parse the property-history JSON from a row or comparator.
    private java.util.Map<String, PropertyHistoryEntry> displayedPropertyHistory =
            Collections.emptyMap();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new OfferRepository(this).removeRetiredStoreOffers();
        setContentView(R.layout.activity_main);
        BottomNavigationController.setup(
                this,
                BottomNavigationController.ITEM_HOME,
                R.id.navigation_animated_content);

        interestRepository = new InterestRepository(this);
        offerRepository = new OfferRepository(this);
        offersContainer = findViewById(R.id.container_offers);
        dashboardStartupSplash = findViewById(R.id.dashboard_startup_splash);
        dashboardStartupProgress = findViewById(R.id.splash_progress);
        dashboardStartupStatus = findViewById(R.id.splash_loading_status);
        startDashboardStartupProgressAnimation();
        dashboardHandler.postDelayed(dashboardStartupTimeout, DASHBOARD_STARTUP_TIMEOUT_MS);
        if (getIntent().getBooleanExtra(EXTRA_RESTORED_CONFIGURATION, false)) {
            // The restore has already written the local state. Do not put a second startup
            // cover in front of the dashboard while its normal background refresh runs.
            dashboardStartupSplash.post(this::hideDashboardStartupSplash);
        }
        floatingSearchController = FloatingSearchController.attach(
                this,
                "home",
                R.id.floating_search_dismiss_surface);
        offersSearchInput = floatingSearchController.getInput();

        findViewById(R.id.button_profile).setOnClickListener(view -> startActivity(
                new Intent(this, ProfileActivity.class)));
        findViewById(R.id.button_sort_offers).setOnClickListener(view -> showOffersSortDialog());
        offersSearchInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                renderOffers(displayedOffers, displayedPropertyHistory);
            }
        });
        findViewById(android.R.id.content).post(this::showBatteryNoticeIfNeeded);
        if (getIntent().getBooleanExtra(EXTRA_RESTORED_CONFIGURATION, false)) {
            restoredInitialDashboard = true;
            RestoredDashboardSnapshot.Snapshot snapshot = RestoredDashboardSnapshot.take();
            if (snapshot != null) {
                dashboardStartupSplash.post(() -> renderDashboardSnapshot(
                        snapshot.interests, snapshot.offers, snapshot.propertyHistory));
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        TelegramClientManager clientManager = TelegramClientManager.getInstance();
        clientManager.start(this);
        clientManager.refreshCloudBackupSoon();
        IntentFilter filter = new IntentFilter(OfferMonitor.ACTION_OFFER_FOUND);
        filter.addAction(ACTION_PROPERTY_DISPLAY_SETTINGS_CHANGED);
        filter.addAction(MonitorStatusStore.ACTION_STATUS_CHANGED);
        filter.addAction(TelegramClientManager.ACTION_CLOUD_SYNC_CHANGED);
        filter.addAction(PropertyPageMonitor.ACTION_MARKET_REFERENCE_PROGRESS);
        filter.addAction(TreasuryDirectMonitor.ACTION_RATES_CHANGED);
        filter.addAction(TreasuryDirectMonitor.ACTION_CHECK_STATE_CHANGED);
        ContextCompat.registerReceiver(
                this,
                offerReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationController.resetInitialFocus(this);
        // Rebuilding every card here blocks the first touch after returning to Alertou.
        // Existing data is already kept in memory and normal monitor events refresh it.
        if (displayedOffers.isEmpty() && !restoredInitialDashboard) {
            refreshDashboard();
        }
    }

    @Override
    protected void onStop() {
        dashboardHandler.removeCallbacks(dashboardUpdate);
        dashboardHandler.removeCallbacks(propertyMarketProgressUpdate);
        if (propertyMarketRefreshAnimator != null) propertyMarketRefreshAnimator.cancel();
        if (treasuryRefreshAnimator != null) treasuryRefreshAnimator.cancel();
        if (propertyMarketSpinningIcon != null) {
            propertyMarketSpinningIcon.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        propertyMarketRefreshAnimator = null;
        propertyMarketSpinningIcon = null;
        treasuryRefreshAnimator = null;
        treasurySpinningIcon = null;
        treasuryActionView = null;
        dashboardUpdatePending = false;
        propertyProgressTickerScheduled = false;
        floatingSearchController.collapse(false);
        unregisterReceiver(offerReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        dashboardRenderGeneration++;
        dashboardExecutor.shutdownNow();
        super.onDestroy();
    }

    private void refreshDashboard() {
        refreshDashboard(true);
    }

    private void refreshDashboard(boolean updateMonitor) {
        // Reconciliation and history parsing can be expensive with many property listings. They
        // must not hold the main thread that handles scrolling and card taps.
        if (updateMonitor) MonitorServiceController.update(this);
        // Several property results can complete in sequence. Keep the current cards visible
        // while one snapshot is prepared, then process only one fresh snapshot afterwards.
        if (dashboardSnapshotLoading) {
            dashboardSnapshotRefreshQueued = true;
            return;
        }
        final long generation = ++dashboardRenderGeneration;
        dashboardSnapshotLoading = true;
        dashboardExecutor.execute(() -> {
            List<Interest> interests = null;
            List<ObservedOffer> offers = null;
            java.util.Map<String, PropertyHistoryEntry> propertyHistory = null;
            try {
                updateDashboardStartupStatus(R.string.dashboard_loading_alerts);
                interests = interestRepository.getAll();
                updateDashboardStartupStatus(R.string.dashboard_loading_offers);
                offerRepository.reconcileRecentWithInterests(interests);
                offers = offerRepository.getRecent();
                updateDashboardStartupStatus(R.string.dashboard_loading_history);
                propertyHistory = new PropertyHistoryRepository(this).getForOffers(offers);
            } catch (Exception ignored) {
                // Preserve the current cards if a transient storage read fails.
            } finally {
                List<Interest> snapshotInterests = interests;
                List<ObservedOffer> snapshotOffers = offers;
                java.util.Map<String, PropertyHistoryEntry> snapshotPropertyHistory = propertyHistory;
                dashboardHandler.post(() -> {
                    dashboardSnapshotLoading = false;
                    if (snapshotInterests != null && snapshotOffers != null
                            && snapshotPropertyHistory != null) {
                        updateDashboardStartupStatus(R.string.dashboard_loading_screen);
                        applyDashboardSnapshot(generation, snapshotInterests, snapshotOffers,
                                snapshotPropertyHistory);
                    } else if (!dashboardInitialContentRendered && dashboardStartupSplash != null) {
                        // A restore can briefly replace local storage while this first snapshot
                        // is read. Never leave the startup cover on screen indefinitely.
                        finishDashboardStartupProgress(() -> renderDashboardSnapshot(
                                Collections.emptyList(), Collections.emptyList(),
                                Collections.emptyMap()));
                    }
                    if (dashboardSnapshotRefreshQueued
                            && !isFinishing()
                            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                            || !isDestroyed())) {
                        dashboardSnapshotRefreshQueued = false;
                        refreshDashboard(false);
                    }
                });
            }
        });
    }

    private void applyDashboardSnapshot(long generation, List<Interest> interests,
                                        List<ObservedOffer> offers,
                                        java.util.Map<String, PropertyHistoryEntry> propertyHistory) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && isDestroyed()) || generation != dashboardRenderGeneration) {
            return;
        }
        if (!dashboardInitialContentRendered && dashboardStartupSplash != null) {
            finishDashboardStartupProgress(() -> renderDashboardSnapshot(
                    interests, offers, propertyHistory));
            return;
        }
        renderDashboardSnapshot(interests, offers, propertyHistory);
    }

    private void renderDashboardSnapshot(List<Interest> interests, List<ObservedOffer> offers,
                                         java.util.Map<String, PropertyHistoryEntry> propertyHistory) {
        dashboardInitialContentRendered = true;
        renderingDashboardInterests = interests;
        try {
            renderOffers(offers, propertyHistory);
        } finally {
            renderingDashboardInterests = null;
        }
        hideDashboardStartupSplash();
        schedulePropertyMarketProgressUpdate();

        boolean hasCouponAlert = false;
        boolean hasPriceAlert = false;
        boolean hasPropertyAlert = false;
        for (Interest interest : interests) {
            if (interest.isCoupon()) hasCouponAlert = true;
            else if (interest.isProperty()) hasPropertyAlert = true;
            else if (interest.isPrice()) hasPriceAlert = true;
        }
        if (isMonitorEnabled() && (hasCouponAlert || hasPropertyAlert
                || (hasPriceAlert && getSelectedGroupCount() > 0))) {
            requestNotificationPermissionIfNeeded();
        }
    }

    private void showBatteryNoticeIfNeeded() {
        SharedPreferences preferences = getSharedPreferences(STARTUP_PREFS, MODE_PRIVATE);
        if (preferences.getBoolean(BATTERY_NOTICE_SHOWN, false)) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            preferences.edit().putBoolean(BATTERY_NOTICE_SHOWN, true).apply();
            return;
        }
        preferences.edit().putBoolean(BATTERY_NOTICE_SHOWN, true).apply();

        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(12));
        content.setBackgroundResource(R.drawable.bg_dialog);

        TextView title = new TextView(this);
        title.setText(R.string.battery_notice_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(18);
        content.addView(title);

        TextView message = new TextView(this);
        message.setText(R.string.battery_notice_message);
        message.setTextColor(getColor(R.color.text_secondary));
        message.setTextSize(15);
        message.setPadding(0, dp(8), 0, dp(16));
        content.addView(message);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        TextView later = createDialogAction(R.string.action_not_now);
        later.setOnClickListener(view -> dialog.dismiss());
        actions.addView(later);
        TextView allow = createDialogAction(R.string.battery_notice_allow);
        allow.setOnClickListener(view -> {
            dialog.dismiss();
            openBatteryOptimizationRequest();
        });
        actions.addView(allow);
        content.addView(actions);

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(shownWindow.getAttributes());
            params.width = getResources().getDisplayMetrics().widthPixels - dp(44);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.65f;
            shownWindow.setAttributes(params);
            shownWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void openBatteryOptimizationRequest() {
        Intent intent = new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (RuntimeException exception) {
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void trashAllOffers() {
        if (offerRepository.getRecent().isEmpty()) {
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackgroundResource(R.drawable.bg_dialog);

        TextView title = new TextView(this);
        title.setText(R.string.trash_all_dialog_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(21);
        content.addView(title);

        TextView message = new TextView(this);
        message.setText(R.string.trash_all_dialog_message);
        message.setTextColor(getColor(R.color.text_secondary));
        message.setTextSize(15);
        message.setPadding(0, dp(8), 0, dp(16));
        content.addView(message);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        TextView cancel = createDialogAction(R.string.action_cancel);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel);
        TextView confirm = createDialogAction(R.string.action_confirm);
        confirm.setTextColor(getColor(R.color.danger));
        confirm.setOnClickListener(view -> {
            dialog.dismiss();
            performTrashAllOffers();
        });
        actions.addView(confirm);
        content.addView(actions);

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(shownWindow.getAttributes());
            params.width = getResources().getDisplayMetrics().widthPixels - dp(44);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.65f;
            shownWindow.setAttributes(params);
            shownWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void performTrashAllOffers() {
        if (offerRepository.trashAllRecent()) {
            refreshDashboard();
        }
    }

    private void trashOfferSection(List<ObservedOffer> offers) {
        if (offers == null || offers.isEmpty()) {
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackgroundResource(R.drawable.bg_dialog);

        TextView title = new TextView(this);
        title.setText(R.string.trash_offer_section_dialog_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(21);
        content.addView(title);

        TextView message = new TextView(this);
        message.setText(R.string.trash_offer_section_dialog_message);
        message.setTextColor(getColor(R.color.text_secondary));
        message.setTextSize(15);
        message.setPadding(0, dp(8), 0, dp(16));
        content.addView(message);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        TextView cancel = createDialogAction(R.string.action_cancel);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel);
        TextView confirm = createDialogAction(R.string.action_confirm);
        confirm.setTextColor(getColor(R.color.danger));
        confirm.setOnClickListener(view -> {
            dialog.dismiss();
            performTrashOfferSection(offers);
        });
        actions.addView(confirm);
        content.addView(actions);

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(shownWindow.getAttributes());
            params.width = getResources().getDisplayMetrics().widthPixels - dp(44);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.65f;
            shownWindow.setAttributes(params);
            shownWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void performTrashOfferSection(List<ObservedOffer> offers) {
        List<String> ids = new java.util.ArrayList<>();
        for (ObservedOffer offer : offers) {
            ids.add(offer.getId());
        }
        if (offerRepository.trashRecent(ids)) {
            refreshDashboard();
        }
    }

    private void renderInterests(List<Interest> interests) {
        if (interests.isEmpty()) {
            alertsSummary.setText(R.string.dashboard_no_interests);
            return;
        }
        alertsSummary.setText(getResources().getQuantityString(
                R.plurals.dashboard_alerts_configured,
                interests.size(),
                interests.size()));
    }

    private String buildActiveMonitorSummary(String groupCountText, String interestCountText) {
        MonitorStatusStore.Snapshot snapshot = MonitorStatusStore.read(this);
        String configuration = getString(
                R.string.dashboard_status_active_summary,
                groupCountText,
                interestCountText);
        String runtime = getTelegramConnectionText(snapshot);
        return configuration + "\n" + runtime;
    }

    private String getTelegramConnectionText(MonitorStatusStore.Snapshot snapshot) {
        TelegramClientManager.State state;
        try {
            state = TelegramClientManager.State.valueOf(snapshot.telegramState);
        } catch (IllegalArgumentException exception) {
            state = TelegramClientManager.State.STARTING;
        }
        if (state == TelegramClientManager.State.READY) {
            long connectedAt = snapshot.telegramConnectedAt == 0L
                    ? System.currentTimeMillis()
                    : snapshot.telegramConnectedAt;
            return getString(R.string.dashboard_telegram_connected_for, formatRelativeTime(connectedAt));
        }
        return getTelegramStateText(snapshot.telegramState);
    }

    private String getTelegramStateText(String stateName) {
        TelegramClientManager.State state;
        try {
            state = TelegramClientManager.State.valueOf(stateName);
        } catch (IllegalArgumentException exception) {
            state = TelegramClientManager.State.STARTING;
        }
        switch (state) {
            case READY:
                return getString(R.string.dashboard_telegram_ready);
            case MISSING_CREDENTIALS:
                return getString(R.string.dashboard_telegram_missing_credentials);
            case WAITING_PHONE:
            case WAITING_EMAIL:
            case WAITING_EMAIL_CODE:
            case WAITING_CODE:
            case WAITING_PASSWORD:
                return getString(R.string.dashboard_telegram_login_pending);
            case CLOSED:
                return getString(R.string.dashboard_telegram_closed);
            case UNSUPPORTED_AUTHORIZATION:
                return getString(R.string.dashboard_telegram_attention);
            case STARTING:
            default:
                return getString(R.string.dashboard_telegram_starting);
        }
    }

    private String getLastAnalysisText(MonitorStatusStore.Snapshot snapshot) {
        if (!snapshot.serviceRunning) {
            return getString(R.string.dashboard_monitor_starting);
        }
        if (snapshot.lastAnalyzedMessageAt > 0) {
            return getString(R.string.dashboard_last_analysis_format, formatRelativeTime(snapshot.lastAnalyzedMessageAt));
        }
        if (snapshot.lastSelectedMessageAt > 0) {
            return getString(R.string.dashboard_last_message_format, formatRelativeTime(snapshot.lastSelectedMessageAt));
        }
        return getString(R.string.dashboard_waiting_messages);
    }

    private String formatRelativeTime(long timestamp) {
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - timestamp);
        long minutes = elapsedMillis / 60000L;
        if (minutes < 1) {
            return getString(R.string.time_now);
        }
        if (minutes < 60) {
            return getResources().getQuantityString(R.plurals.time_minutes_ago, (int) minutes, (int) minutes);
        }
        long hours = minutes / 60L;
        if (hours < 24) {
            return getResources().getQuantityString(R.plurals.time_hours_ago, (int) hours, (int) hours);
        }
        long days = hours / 24L;
        int safeDays = (int) Math.min(days, Integer.MAX_VALUE);
        return getResources().getQuantityString(R.plurals.time_days_ago, safeDays, safeDays);
    }

    private void renderOffers(List<ObservedOffer> offers) {
        renderOffers(offers, new PropertyHistoryRepository(this).getForOffers(offers));
    }

    private void renderOffers(List<ObservedOffer> offers,
                              java.util.Map<String, PropertyHistoryEntry> propertyHistory) {
        offerSectionCache.begin();
        propertyMarketSummaryView = null;
        propertyMarketDurationView = null;
        propertyMarketCardView = null;
        propertyMarketActionView = null;
        displayedOffers = offers;
        displayedPropertyHistory = propertyHistory == null ? Collections.emptyMap() : propertyHistory;
        List<ObservedOffer> visibleOffers = new java.util.ArrayList<>(
                filterOffers(offers, offersSearchInput.getText().toString()));
        sortOffers(visibleOffers, displayedPropertyHistory);
        if (visibleOffers.isEmpty()) {
            offersContainer.removeAllViews();
            NumberFormat currency = CurrencyTextFormatter.displayFormatter();
            java.util.List<ObservedOffer> noOffers = java.util.Collections.emptyList();
            java.util.Map<Long, Interest> interestsById = getInterestsById();
            if (PropertyMarketReferenceSettings.isCondominiumVisible(this)) {
                addOfferSection(R.string.property_alerts_list_title, noOffers, currency,
                        SECTION_PROPERTY_MARKET_EXPANDED,
                        getPropertyMarketLastCheckSummary(noOffers), R.drawable.ic_property_alert);
            }
            addTreasurySection();
            if (offersContainer.getChildCount() == 0) {
                boolean awaitingLinks = offersSearchInput.getText().toString().trim().isEmpty()
                        && !offerRepository.getRecentForValidation().isEmpty();
                offersContainer.addView(createEmptyText(awaitingLinks
                        ? R.string.dashboard_waiting_offer_links : R.string.dashboard_no_offers));
            }
            offerSectionCache.end(offersContainer);
            return;
        }

        NumberFormat currency = CurrencyTextFormatter.displayFormatter();
        java.util.Map<Long, Interest> interestsById = getInterestsById();
        List<ObservedOffer> couponOffers = new java.util.ArrayList<>();
        List<ObservedOffer> propertyMarketOffers = new java.util.ArrayList<>();
        List<ObservedOffer> productOffers = new java.util.ArrayList<>();
        for (ObservedOffer offer : visibleOffers) {
            Interest propertyInterest = interestsById.get(offer.getInterestId());
            if (PropertyMarketReferenceSettings.isReference(offer)) {
                // Referências sem alerta correspondente são restos de cadastros antigos.
                // Elas não podem inflar a lista nem a contagem de condomínios atuais.
                if (propertyInterest == null || !propertyInterest.isProperty()) {
                    continue;
                }
                {
                    propertyMarketOffers.add(offer);
                }
            } else if (isPropertyOffer(offer)) {
                if (propertyInterest != null && propertyInterest.isPropertyCondominium()) {
                    propertyMarketOffers.add(offer);
                }
            } else if (isCouponOffer(offer)) {
                couponOffers.add(offer);
            } else {
                productOffers.add(offer);
            }
        }
        if (PropertyMarketReferenceSettings.isCondominiumVisible(this)) {
            addOfferSection(R.string.property_alerts_list_title, propertyMarketOffers, currency,
                    SECTION_PROPERTY_MARKET_EXPANDED,
                    getPropertyMarketLastCheckSummary(propertyMarketOffers), R.drawable.ic_property_alert);
        }
        addOfferSection(R.string.coupon_alerts_list_title, couponOffers, currency,
                SECTION_COUPONS_EXPANDED, "");
        addTreasurySection();
        addOfferSection(R.string.product_alerts_list_title, productOffers, currency,
                SECTION_PRODUCTS_EXPANDED, "");
        offerSectionCache.end(offersContainer);
    }

    /** Tesouro is not an ObservedOffer: its display comes only from confirmed official-rate reads. */
    private void addTreasurySection() {
        List<Interest> treasuryInterests = new java.util.ArrayList<>();
        for (Interest interest : interestRepository.getAll()) {
            if (interest.isTreasury()) treasuryInterests.add(interest);
        }
        if (treasuryInterests.isEmpty()) return;

        boolean expanded = isOfferSectionExpanded(SECTION_TREASURY_EXPANDED);
        TreasuryRateHistoryRepository historyRepository = new TreasuryRateHistoryRepository(this);
        StringBuilder fingerprint = new StringBuilder().append(expanded).append('|')
                .append(TreasuryDirectMonitor.getOfficialUpdatedAt(this)).append('|')
                .append(TreasuryDirectMonitor.getLastAttemptAt(this)).append('|')
                .append(TreasuryDirectMonitor.didLastAttemptFail(this));
        for (Interest interest : treasuryInterests) {
            List<TreasuryRatePoint> points = historyRepository.get(interest.getId());
            fingerprint.append('|').append(interest.getId()).append('|')
                    .append(interest.getTerm()).append('|').append(interest.getMinimumTreasuryRate());
            if (!points.isEmpty()) {
                TreasuryRatePoint point = points.get(points.size() - 1);
                fingerprint.append('|').append(point.getObservedAt()).append('|').append(point.getRate())
                        .append('|').append(points.size());
            }
        }
        renderingSectionKey = SECTION_TREASURY_EXPANDED;
        renderingSectionFingerprint = fingerprint.toString();
        View cached = offerSectionCache.find(renderingSectionKey, renderingSectionFingerprint);
        if (cached != null) {
            treasuryActionView = cached.findViewWithTag(TREASURY_ACTION_TAG);
            offerSectionCache.attach(offersContainer, renderingSectionKey,
                    renderingSectionFingerprint, cached);
            updateTreasuryRefreshIcon();
            return;
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_compact);
        card.setClipToOutline(true);
        card.setPadding(0, dp(2), 0, dp(2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(R.drawable.bg_row_pressed);
        header.setClickable(true);
        header.setFocusable(true);
        header.setPadding(dp(10), dp(2), 0, dp(3));

        ImageButton icon = new ImageButton(this);
        icon.setTag(TREASURY_ACTION_TAG);
        icon.setBackgroundResource(R.drawable.bg_icon_circle);
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        treasuryActionView = icon;
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconParams.rightMargin = dp(8);
        header.addView(icon, iconParams);
        icon.setOnClickListener(view -> refreshTreasuryRates());
        updateTreasuryRefreshIcon();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        header.addView(text, textParams);
        TextView title = new TextView(this);
        title.setText(R.string.treasury_direct_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(16);
        title.setSingleLine(true);
        text.addView(title);
        TextView count = new TextView(this);
        String titlesCount = getString(R.string.treasury_direct_alerts_count, treasuryInterests.size());
        String marketStatus = getTreasuryMarketStatus();
        android.text.SpannableString countAndStatus = new android.text.SpannableString(
                titlesCount + " · " + marketStatus);
        int statusStart = titlesCount.length() + 3;
        countAndStatus.setSpan(new android.text.style.ForegroundColorSpan(
                        getTreasuryMarketStatusColor()),
                statusStart, countAndStatus.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        count.setText(countAndStatus);
        count.setTextColor(getColor(R.color.text_secondary));
        count.setTextSize(13);
        count.setPadding(0, dp(3), 0, 0);
        text.addView(count);

        TextView status = new TextView(this);
        status.setTextColor(getColor(R.color.text_secondary));
        status.setTextSize(10.5f);
        status.setIncludeFontPadding(false);
        // The official date is important data. Let it use the whole line (and wrap on
        // exceptionally narrow devices) instead of truncating it beside an empty duration.
        status.setSingleLine(false);
        status.setText(getTreasuryLastCheckSummary());
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(3);
        text.addView(status, statusParams);

        ImageButton toggle = new ImageButton(this);
        toggle.setImageResource(R.drawable.ic_chevron_right);
        toggle.setColorFilter(AccentColorController.getPrimaryColor(this));
        toggle.setBackgroundResource(R.drawable.bg_icon_circle);
        toggle.setPadding(dp(7), dp(7), dp(7), dp(7));
        toggle.setRotation(expanded ? 90f : 0f);
        toggle.setContentDescription(getString(expanded ? R.string.alerts_section_collapse
                : R.string.alerts_section_expand));
        toggle.setTag(OFFER_SECTION_TOGGLE_TAG_PREFIX + SECTION_TREASURY_EXPANDED);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        toggleParams.rightMargin = 0;
        header.addView(toggle, toggleParams);
        View.OnClickListener toggleListener = view ->
                toggleOfferSection(SECTION_TREASURY_EXPANDED);
        header.setOnClickListener(toggleListener);
        toggle.setOnClickListener(toggleListener);
        card.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setTag(OFFER_SECTION_CONTENT_TAG_PREFIX + SECTION_TREASURY_EXPANDED);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6), 0, dp(6), dp(4));
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        // Monte as poucas linhas uma vez, mesmo fechado. A interação passa a
        // alternar apenas a visibilidade, sem reconstruir toda a tela Alertou.
        for (int index = 0; index < treasuryInterests.size(); index++) {
            Interest interest = treasuryInterests.get(index);
            if (index > 0) {
                content.addView(createOfferDivider());
            }
            content.addView(createTreasuryRateRow(interest,
                    historyRepository.get(interest.getId())));
        }
        card.addView(content);
        addOfferSectionCard(card);
    }

    private View createTreasuryRateRow(Interest interest, List<TreasuryRatePoint> points) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(0, dp(7), 0, dp(7));
        row.setBackgroundColor(getColor(R.color.card));
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(titleLine);
        TextView title = new TextView(this);
        title.setText(interest.getTerm());
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleLine.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TreasuryRatePoint latest = points.isEmpty() ? null : points.get(points.size() - 1);
        if (points.size() >= 2) {
            TreasuryRatePoint previous = points.get(points.size() - 2);
            titleLine.addView(createTreasuryRateChangeBadge(latest.getRate() - previous.getRate(),
                    previous.getRate(), view -> showTreasuryRateHistoryDialog(interest)));
        }
        TextView rate = new TextView(this);
        rate.setText(latest == null ? "—" : formatTreasuryRate(latest.getRate()) + "% a.a.");
        rate.setTextColor(getColor(R.color.text_primary));
        rate.setTextSize(13);
        rate.setSingleLine(true);
        titleLine.addView(rate);

        if (latest == null) {
            TextView waiting = new TextView(this);
            waiting.setText(R.string.treasury_direct_rate_waiting);
            waiting.setTextColor(getColor(R.color.text_secondary));
            waiting.setTextSize(11.5f);
            waiting.setSingleLine(true);
            waiting.setPadding(0, dp(2), 0, 0);
            row.addView(waiting);
        } else {
            LinearLayout metaLine = new LinearLayout(this);
            metaLine.setGravity(Gravity.CENTER_VERTICAL);
            metaLine.setPadding(0, dp(2), 0, 0);
            TextView time = new TextView(this);
            time.setText(new SimpleDateFormat("HH:mm", new Locale("pt", "BR"))
                    .format(new java.util.Date(latest.getObservedAt())));
            time.setTextColor(getColor(R.color.text_secondary));
            time.setTextSize(11.5f);
            time.setSingleLine(true);
            metaLine.addView(time);
            TextView threshold = new TextView(this);
            threshold.setText("• " + getString(R.string.treasury_direct_alert_threshold,
                    formatTreasuryRate(interest.getMinimumTreasuryRate())));
            threshold.setTextColor(getColor(R.color.text_secondary));
            threshold.setTextSize(11.5f);
            threshold.setSingleLine(true);
            threshold.setPadding(dp(4), 0, 0, 0);
            metaLine.addView(threshold);
            row.addView(metaLine);
        }
        row.setOnClickListener(view -> showTreasuryRateHistoryDialog(interest));
        return row;
    }

    private TextView createTreasuryRateChangeBadge(double change, double previousRate,
                                                    View.OnClickListener listener) {
        boolean increase = change >= 0d;
        double percentage = previousRate == 0d ? 0d : Math.abs(change / previousRate * 100d);
        NumberFormat number = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        number.setMaximumFractionDigits(1);
        TextView badge = new TextView(this);
        badge.setText((increase ? "↑ " : "↓ ") + number.format(percentage) + "%");
        badge.setTextColor(getColor(increase ? R.color.action_green : R.color.danger));
        badge.setTextSize(10.5f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setBackgroundResource(increase ? R.drawable.bg_property_new_badge
                : R.drawable.bg_property_price_rise_badge);
        badge.setPadding(dp(6), dp(1), dp(6), dp(1));
        badge.setClickable(true);
        badge.setOnClickListener(listener);
        badge.setContentDescription(getString(R.string.treasury_direct_rate_change_description));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(7);
        badge.setLayoutParams(params);
        return badge;
    }

    private String formatTreasuryRate(double value) {
        NumberFormat number = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(3);
        return number.format(value);
    }

    private void showTreasuryRateHistoryDialog(Interest interest) {
        List<TreasuryRatePoint> points = new TreasuryRateHistoryRepository(this).get(interest.getId());
        if (points.isEmpty()) {
            Toast.makeText(this, R.string.treasury_direct_rate_waiting, Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackgroundResource(R.drawable.bg_dialog);
        scroll.addView(content);
        TextView title = new TextView(this);
        title.setText(R.string.treasury_direct_history_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(21);
        content.addView(title);
        TextView bond = new TextView(this);
        bond.setText(interest.getTerm());
        bond.setTextColor(getColor(R.color.text_primary));
        bond.setTextSize(15);
        bond.setPadding(0, dp(6), 0, 0);
        content.addView(bond);
        TextView summary = new TextView(this);
        summary.setText(R.string.treasury_direct_history_summary);
        summary.setTextColor(getColor(R.color.text_secondary));
        summary.setTextSize(13);
        summary.setPadding(0, dp(3), 0, dp(10));
        content.addView(summary);
        TextView chartTitle = new TextView(this);
        chartTitle.setText(R.string.treasury_direct_history_chart_title);
        chartTitle.setTextColor(getColor(R.color.text_primary));
        chartTitle.setTextSize(16);
        content.addView(chartTitle);
        TreasuryRateTrendView graph = new TreasuryRateTrendView(this);
        graph.setPoints(points);
        content.addView(graph, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                dp(points.size() <= 2 ? 140 : 180)));
        TextView readings = new TextView(this);
        readings.setText(R.string.treasury_direct_history_readings_title);
        readings.setTextColor(getColor(R.color.text_primary));
        readings.setTextSize(16);
        readings.setPadding(0, dp(8), 0, dp(4));
        content.addView(readings);
        SimpleDateFormat date = new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR"));
        for (int index = points.size() - 1; index >= Math.max(0, points.size() - 8); index--) {
            TreasuryRatePoint point = points.get(index);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));
            TextView observed = new TextView(this);
            observed.setText(date.format(point.getObservedAt()));
            observed.setTextColor(getColor(R.color.text_secondary));
            observed.setTextSize(12.5f);
            row.addView(observed, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView rate = new TextView(this);
            rate.setText(formatTreasuryRate(point.getRate()) + "% a.a.");
            rate.setTextColor(getColor(R.color.text_primary));
            rate.setTextSize(12.5f);
            row.addView(rate);
            content.addView(row);
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(12), 0, 0);
        TextView close = createDialogAction(R.string.action_close);
        close.setOnClickListener(view -> dialog.dismiss());
        actions.addView(close);
        content.addView(actions);
        dialog.setContentView(scroll);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = getResources().getDisplayMetrics().widthPixels - dp(44);
            params.height = getResources().getDisplayMetrics().heightPixels - dp(72);
            params.dimAmount = .38f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private void addOfferSection(int titleResource, List<ObservedOffer> offers,
                                 NumberFormat currency,
                                 String preferenceKey,
                                 String sectionSummary) {
        addOfferSection(titleResource, offers, currency, preferenceKey,
                sectionSummary, 0);
    }

    private void addOfferSection(int titleResource, List<ObservedOffer> offers,
                                 NumberFormat currency,
                                 String preferenceKey,
                                 String sectionSummary,
                                 int sectionIconResource) {
        boolean propertyMarketSection = SECTION_PROPERTY_MARKET_EXPANDED.equals(preferenceKey);
        // Imóveis configurados nunca somem da tela por uma consulta vazia, incompleta
        // ou em andamento. A visibilidade é decidida somente no Perfil.
        if (offers.isEmpty() && !propertyMarketSection) {
            return;
        }
        boolean expanded = isOfferSectionExpanded(preferenceKey);
        renderingSectionKey = preferenceKey;
        renderingSectionFingerprint = OfferSectionCache.fingerprint(this, offers,
                displayedPropertyHistory, expanded,
                propertyMarketSection ? "" : sectionSummary);
        View cached = offerSectionCache.find(preferenceKey, renderingSectionFingerprint);
        if (cached != null) {
            if (propertyMarketSection) {
                propertyMarketCardView = cached;
                propertyMarketSummaryView = cached.findViewWithTag(PROPERTY_MARKET_SUMMARY_TAG);
                propertyMarketDurationView = cached.findViewWithTag(PROPERTY_MARKET_DURATION_TAG);
                propertyMarketActionView = cached.findViewWithTag(PROPERTY_MARKET_ACTION_TAG);
            }
            offerSectionCache.attach(offersContainer, preferenceKey, renderingSectionFingerprint, cached);
            if (propertyMarketSection) {
                refreshPropertyMarketProgressVisuals();
            }
            return;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_compact);
        // The offer rows have their own solid background for swipe feedback. Keep that
        // background inside the section's rounded outline instead of letting it visually
        // reach past the card edges.
        card.setClipToOutline(true);
        card.setClipChildren(true);
        card.setClipToPadding(true);
        card.setPadding(0, dp(2), 0, dp(2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(R.drawable.bg_row_pressed);
        header.setClickable(true);
        header.setFocusable(true);
        // Fontes is the visual reference for title icons: keep the same 10dp
        // distance from the card edge on every Alertou section.
        header.setPadding(dp(10), dp(2), 0, dp(3));

        boolean propertySection = propertyMarketSection
                || SECTION_PROPERTIES_EXPANDED.equals(preferenceKey);
        boolean countBelowTitle = propertySection
                || SECTION_PRODUCTS_EXPANDED.equals(preferenceKey);
        if (propertyMarketSection) {
            card.setTag(PROPERTY_MARKET_CARD_TAG);
            propertyMarketCardView = card;
            propertyMarketRowOriginalTextColors.clear();
            highlightedPropertyMarketInterestId = 0L;
        }
        ImageButton sectionAction = new ImageButton(this);
        sectionAction.setPadding(dp(7), dp(7), dp(7), dp(7));
        sectionAction.setScaleType(ImageView.ScaleType.CENTER);
        if (propertyMarketSection) {
            sectionAction.setTag(PROPERTY_MARKET_ACTION_TAG);
            propertyMarketActionView = sectionAction;
            boolean updatingPropertyMarket = PropertyPageMonitor.getInstance()
                    .isCheckingPropertyCondominium();
            sectionAction.setImageResource(updatingPropertyMarket
                    ? R.drawable.ic_sync : R.drawable.ic_property_alert);
            sectionAction.setBackgroundResource(R.drawable.bg_icon_circle);
            sectionAction.setContentDescription(getString(updatingPropertyMarket
                    ? R.string.action_stop_property_check
                    : R.string.action_refresh_property_market_prices));
            sectionAction.setOnClickListener(view -> refreshPropertyMarketPrices());
            if (updatingPropertyMarket) {
                animatePropertyMarketRefreshIcon(sectionAction);
            }
        } else {
            sectionAction.setImageResource(sectionIconResource != 0
                    ? sectionIconResource : R.drawable.ic_trash_outline);
            sectionAction.setBackgroundResource(sectionIconResource != 0
                    ? R.drawable.bg_icon_circle : R.drawable.bg_icon_danger);
            sectionAction.setContentDescription(sectionIconResource != 0
                    ? getString(titleResource)
                    : getString(R.string.action_trash_offer_section));
            if (sectionIconResource == 0) {
                sectionAction.setOnClickListener(view -> trashOfferSection(offers));
            } else {
                sectionAction.setOnClickListener(view -> toggleOfferSection(preferenceKey));
            }
        }
        if (sectionIconResource != 0 || propertyMarketSection) {
            sectionAction.setColorFilter(AccentColorController.getPrimaryColor(this));
        }
        LinearLayout.LayoutParams sectionActionParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        sectionActionParams.rightMargin = dp(8);
        header.addView(sectionAction, sectionActionParams);

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(titleResource);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(16);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleLine.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView count = new TextView(this);
        count.setTextColor(AccentColorController.getPrimaryColor(this));
        count.setTextSize(14);
        headerText.addView(titleLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (countBelowTitle) {
            count.setTextColor(getColor(R.color.text_secondary));
            count.setTextSize(13);
            count.setSingleLine(true);
            count.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams countLineParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            countLineParams.topMargin = dp(3);
            headerText.addView(count, countLineParams);
        } else {
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            countParams.leftMargin = dp(6);
            titleLine.addView(count, countParams);
        }

        if (!sectionSummary.isEmpty() || propertySection) {
            LinearLayout summaryLine = new LinearLayout(this);
            summaryLine.setGravity(Gravity.CENTER_VERTICAL);
            summaryLine.setOrientation(LinearLayout.HORIZONTAL);
            TextView summary = new TextView(this);
            String statusText = sectionSummary.isEmpty()
                    ? getString(R.string.property_alerts_status_empty) : sectionSummary;
            boolean propertyStatusActive = propertyMarketSection
                    ? PropertyPageMonitor.getInstance().isCheckingPropertyCondominium()
                    : false;
            summary.setTextColor(getColor(propertyStatusActive
                    ? R.color.action_green : R.color.text_secondary));
            summary.setTextSize(11.5f);
            summary.setIncludeFontPadding(false);
            summary.setSingleLine(!propertyMarketSection);
            if (propertyMarketSection) summary.setMaxLines(3);
            summary.setEllipsize(TextUtils.TruncateAt.END);
            if (propertyMarketSection) {
                summary.setTag(PROPERTY_MARKET_SUMMARY_TAG);
                propertyMarketSummaryView = summary;
            }
            summaryLine.addView(summary, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1));
            TextView duration = new TextView(this);
            duration.setTextColor(summary.getCurrentTextColor());
            duration.setTextSize(11.5f);
            duration.setIncludeFontPadding(false);
            duration.setSingleLine(true);
            if (propertyMarketSection) {
                duration.setTag(PROPERTY_MARKET_DURATION_TAG);
                propertyMarketDurationView = duration;
            }
            summaryLine.addView(duration);
            applyPropertyStatusLine(summary, duration, statusText);
            LinearLayout.LayoutParams summaryLineParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            summaryLineParams.topMargin = dp(3);
            headerText.addView(summaryLine, summaryLineParams);
        }

        header.addView(headerText, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1));

        ImageButton toggle = new ImageButton(this);
        toggle.setImageResource(R.drawable.ic_chevron_right);
        toggle.setColorFilter(AccentColorController.getPrimaryColor(this));
        toggle.setBackgroundResource(R.drawable.bg_icon_circle);
        toggle.setContentDescription(getString(expanded
                ? R.string.alerts_section_collapse
                : R.string.alerts_section_expand));
        toggle.setTag(OFFER_SECTION_TOGGLE_TAG_PREFIX + preferenceKey);
        toggle.setPadding(dp(7), dp(7), dp(7), dp(7));
        toggle.setScaleType(ImageView.ScaleType.CENTER);
        toggle.setRotation(expanded ? 90f : 0f);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        toggleParams.rightMargin = 0;

        header.addView(toggle, toggleParams);
        card.addView(header);
        header.setOnClickListener(view -> toggleOfferSection(preferenceKey));
        toggle.setOnClickListener(view -> toggleOfferSection(preferenceKey));

        LinearLayout content = new LinearLayout(this);
        content.setTag(OFFER_SECTION_CONTENT_TAG_PREFIX + preferenceKey);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6), 0, dp(6), dp(4));
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);

        boolean propertyInterestCards = propertyMarketSection;
        List<ObservedOffer> sectionOffers = propertyMarketSection ? groupPropertyCondominiumOffersByInterest(offers) : offers;
        java.util.Map<Long, Integer> propertyInterestOfferCounts = new java.util.HashMap<>();
        java.util.Map<Long, Integer> propertyInterestUnavailableCounts = new java.util.HashMap<>();
        if (propertyInterestCards) {
            for (ObservedOffer offer : sectionOffers) {
                if (isUnavailablePropertyOffer(offer)) {
                    int unavailableCount = propertyInterestUnavailableCounts.containsKey(offer.getInterestId())
                            ? propertyInterestUnavailableCounts.get(offer.getInterestId()) : 0;
                    propertyInterestUnavailableCounts.put(offer.getInterestId(), unavailableCount + 1);
                } else if (countsAsCurrentPropertyOffer(offer)) {
                    int offerCountForInterest = propertyInterestOfferCounts.containsKey(offer.getInterestId())
                            ? propertyInterestOfferCounts.get(offer.getInterestId()) : 0;
                    propertyInterestOfferCounts.put(offer.getInterestId(), offerCountForInterest + 1);
                }
            }
        }
        int sectionOfferCount = offers.size();
        if (propertyInterestCards) {
            sectionOfferCount = 0;
            for (int cardOfferCount : propertyInterestOfferCounts.values()) {
                sectionOfferCount += cardOfferCount;
            }
        }
        String countText = getResources().getQuantityString(R.plurals.dashboard_offer_section_count,
                sectionOfferCount, sectionOfferCount);
        count.setText(countBelowTitle ? stripCountBullet(countText) : countText);
        String previousGroup = null;
        String activePropertyCardKey = null;
        LinearLayout propertyCardOffers = null;
        int propertyCardOfferCount = 0;
        java.util.Set<Long> renderedPropertyInterestIds = new java.util.HashSet<>();
        for (int index = 0; index < sectionOffers.size(); index++) {
            ObservedOffer offer = sectionOffers.get(index);
            boolean propertyMarketReference = PropertyMarketReferenceSettings.isReference(offer);
            boolean newLowestMarketReference = propertyMarketReference
                    && PropertyMarketWinnerStore.isActive(this, offer);
            String group = propertyMarketSection ? getPropertyCondominiumCardKey(offer.getInterestId())
                    : OfferDateFormatter.getGroupKey(offer.getObservedAt());
            String groupLabel = propertyMarketSection
                    ? getPropertyCondominiumCardTitle(getInterestsById().get(offer.getInterestId()))
                    : OfferDateFormatter.formatGroupLabel(this, offer.getObservedAt());
            if (propertyInterestCards) {
                if (!group.equals(activePropertyCardKey)) {
                    if (activePropertyCardKey != null) {
                        content.addView(createOfferDivider());
                    }
                    activePropertyCardKey = group;
                    propertyCardOfferCount = 0;
                    propertyCardOffers = addPropertyInterestCard(content, group,
                            offer.getInterestId(), groupLabel,
                           propertyInterestOfferCounts.containsKey(offer.getInterestId())
                                    ? propertyInterestOfferCounts.get(offer.getInterestId()) : 0,
                            propertyInterestUnavailableCounts.containsKey(offer.getInterestId())
                                    ? propertyInterestUnavailableCounts.get(offer.getInterestId()) : 0);
                    renderedPropertyInterestIds.add(offer.getInterestId());
                } else if (propertyCardOffers != null && propertyCardOfferCount > 0) {
                    propertyCardOffers.addView(createOfferDivider());
                }
            } else if (!propertyMarketSection && !group.equals(previousGroup)) {
                if (previousGroup != null) {
                    content.addView(createDateGroupDivider());
                }
                content.addView(createOfferGroupHeader(groupLabel, previousGroup != null));
                previousGroup = group;
            } else if (index > 0) {
                content.addView(createOfferDivider());
            }
            String displayedTime = OfferDateFormatter.formatTime(offer.getObservedAt());
            PropertyHistoryEntry propertyHistory = displayedPropertyHistory.get(offer.getId());
            double propertyPriceChange = propertyHistory == null
                    ? 0d : propertyHistory.getLatestPriceChangeAmount();
            double propertyPriceChangePercentage = propertyHistory == null
                    ? 0d : propertyHistory.getLatestPriceChangePercentage();
            if (newLowestMarketReference) {
                PropertyMarketWinnerStore.WinnerInfo winner = PropertyMarketWinnerStore.get(this, offer);
                if (winner != null && !Double.isNaN(winner.previousPrice) && winner.previousPrice > 0d) {
                    propertyPriceChange = offer.getPrice() - winner.previousPrice;
                    propertyPriceChangePercentage = propertyPriceChange * 100d / winner.previousPrice;
                }
            }
            String displayedPrice = PropertyOfferDisplay.formatPrice(this, offer, propertyHistory, currency);
            String offerMoment = propertyMarketReference
                    ? new SimpleDateFormat("dd/MM/yy HH:mm", new Locale("pt", "BR"))
                    .format(new java.util.Date(offer.getObservedAt()))
                    : groupLabel + " " + displayedTime;
            String contentDescription = getString(
                    R.string.dashboard_offer_summary,
                    displayedPrice,
                    propertyMarketReference
                            ? getString(R.string.property_market_reference_content_description)
                            : offer.getSource(),
                    offerMoment);
            GroupSpeedRepository speed = new GroupSpeedRepository(this);
            boolean expired = speed.isOfferExpired(offer);
            LinearLayout row = createOfferRow(
                    offer,
                    offer.getInterest(),
                    offer.getInterestId(),
                    displayedPrice,
                    displayedTime,
                    getOfferSourceLabel(offer),
                    contentDescription,
                    getPropertyListingCode(offer),
                    expired,
                    propertyHistory != null && propertyHistory.isRecent(System.currentTimeMillis()),
                    propertyHistory != null && propertyHistory.isGoodPrice(),
                    propertyHistory == null ? 0L : propertyHistory.getFirstPublicationAt(),
                    propertyPriceChange,
                    propertyPriceChangePercentage,
                    propertyMarketReference,
                    newLowestMarketReference,
                    view -> showPropertyHistoryDialog(offer));
            FrameLayout swipeContainer = createSwipeContainer(row);
            attachSwipeActions(row, offer, expired);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            (propertyInterestCards && propertyCardOffers != null ? propertyCardOffers : content)
                    .addView(swipeContainer, rowParams);
            if (propertyInterestCards) {
                propertyCardOfferCount++;
            }
        }
        if (propertyInterestCards) {
            // O condomínio ou rua permanece visível mesmo quando a última rodada não
            // devolve anúncios individuais. Os cartões internos refletem exatamente
            // os alertas configurados no Perfil.
            for (Interest interest : interestRepository.getAll()) {
                boolean belongsToSection = interest.isPropertyCondominium();
                if (!belongsToSection || renderedPropertyInterestIds.contains(interest.getId())) {
                    continue;
                }
                if (activePropertyCardKey != null) {
                    content.addView(createOfferDivider());
                }
                activePropertyCardKey = getPropertyCondominiumCardKey(interest.getId());
                addPropertyInterestCard(content, activePropertyCardKey, interest.getId(),
                        getPropertyCondominiumCardTitle(interest), 0, 0);
            }
        }
        card.addView(content);

        addOfferSectionCard(card);
    }

    private void addOfferSectionCard(LinearLayout card) {
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.screen_card_gap);
        card.setLayoutParams(cardParams);
        offerSectionCache.attach(offersContainer, renderingSectionKey, renderingSectionFingerprint, card);
    }

    private String stripCountBullet(String text) {
        return text == null ? "" : text.replaceFirst("^\\s*•\\s*", "");
    }

    private java.util.Map<Long, Interest> getInterestsById() {
        java.util.Map<Long, Interest> interestsById = new java.util.HashMap<>();
        List<Interest> interests = renderingDashboardInterests != null
                ? renderingDashboardInterests : interestRepository.getAll();
        for (Interest interest : interests) {
            interestsById.put(interest.getId(), interest);
        }
        return interestsById;
    }

    private boolean isCondominiumInterest(java.util.Map<Long, Interest> interestsById,
                                          long interestId) {
        Interest interest = interestsById.get(interestId);
        return interest == null || interest.isPropertyCondominium();
    }

    private boolean isOfferSectionExpanded(String preferenceKey) {
        return getSharedPreferences(OFFER_PREFS, MODE_PRIVATE)
                .getBoolean(preferenceKey, true);
    }

    private void toggleOfferSection(String preferenceKey) {
        Object cardTag = SECTION_PROPERTY_MARKET_EXPANDED.equals(preferenceKey)
                ? PROPERTY_MARKET_CARD_TAG
                : null;
        View cachedCard = cardTag == null ? null : offersContainer.findViewWithTag(cardTag);
        if (cachedCard == null) {
            for (int index = 0; index < offersContainer.getChildCount(); index++) {
                View candidate = offersContainer.getChildAt(index);
                if (candidate.findViewWithTag(OFFER_SECTION_TOGGLE_TAG_PREFIX + preferenceKey) != null) {
                    cachedCard = candidate;
                    break;
                }
            }
        }
        boolean expand = !isOfferSectionExpanded(preferenceKey);
        getSharedPreferences(OFFER_PREFS, MODE_PRIVATE).edit()
                .putBoolean(preferenceKey, expand)
                .apply();
        if (cachedCard != null) {
            View content = cachedCard.findViewWithTag(
                    OFFER_SECTION_CONTENT_TAG_PREFIX + preferenceKey);
            View toggle = cachedCard.findViewWithTag(
                    OFFER_SECTION_TOGGLE_TAG_PREFIX + preferenceKey);
            if (content != null && toggle instanceof ImageButton) {
                content.setVisibility(expand ? View.VISIBLE : View.GONE);
                toggle.setRotation(expand ? 90f : 0f);
                toggle.setContentDescription(getString(expand
                        ? R.string.alerts_section_collapse : R.string.alerts_section_expand));
                return;
            }
        }
        renderOffers(displayedOffers, displayedPropertyHistory);
    }

    private TextView createOfferGroupHeader(String label, boolean hasPreviousGroup) {
        TextView header = new TextView(this);
        header.setText(label);
        header.setTextColor(getColor(R.color.text_secondary));
        header.setTextSize(13);
        header.setPadding(dp(6), dp(hasPreviousGroup ? 10 : 8), dp(8), dp(5));
        return header;
    }

    private LinearLayout addPropertyInterestCard(LinearLayout parent, String preferenceKey,
                                                 long interestId, String title,
                                                int returnedOfferCount,
                                                 int unavailableOfferCount) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_compact);
        card.setPadding(dp(6), dp(4), dp(6), dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        // The nested card already has 6dp of horizontal padding. Add 4dp here
        // so its title icon also starts 10dp from its own card edge.
        header.setPadding(dp(4), dp(3), dp(2), dp(3));
        header.setBackgroundResource(R.drawable.bg_row_pressed);
        header.setClickable(true);
        header.setFocusable(true);
        header.setTag((PROPERTY_MARKET_GROUP_TAG_PREFIX) + interestId);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_property_alert);
        icon.setColorFilter(AccentColorController.getPrimaryColor(this));
        icon.setBackgroundResource(R.drawable.bg_icon_circle);
        icon.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        iconParams.rightMargin = dp(7);
        header.addView(icon, iconParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(getColor(R.color.text_primary));
        label.setTextSize(13);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView returnedOffers = new TextView(this);
        String offerCountText = stripCountBullet(getResources().getQuantityString(
                R.plurals.dashboard_offer_section_count,
                returnedOfferCount, returnedOfferCount));
        if (unavailableOfferCount > 0) {
            offerCountText += " · " + getResources().getQuantityString(
                    R.plurals.property_unavailable_offer_count,
                    unavailableOfferCount, unavailableOfferCount);
        }
        returnedOffers.setText(offerCountText);
        returnedOffers.setTextColor(getColor(R.color.text_secondary));
        returnedOffers.setTextSize(12);
        returnedOffers.setIncludeFontPadding(false);
        returnedOffers.setPadding(0, dp(2), 0, 0);
        textColumn.addView(returnedOffers, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        header.addView(textColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        PropertyMarketListingCountHistory countHistory = new PropertyHistoryRepository(this)
                .getMarketListingCountHistory(interestId);
        if (!countHistory.getPoints().isEmpty()) {
            View countBadge = createPropertyListingCountBadge(countHistory.getLatestCount(),
                    view -> showPropertyMarketListingCountHistoryDialog(title, countHistory));
            LinearLayout.LayoutParams countBadgeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            countBadgeParams.leftMargin = dp(5);
            countBadgeParams.rightMargin = dp(6);
            header.addView(countBadge, countBadgeParams);
        }

        boolean expanded = getSharedPreferences(OFFER_PREFS, MODE_PRIVATE)
                .getBoolean(preferenceKey, true);
        ImageButton toggle = new ImageButton(this);
        toggle.setImageResource(R.drawable.ic_chevron_right);
        toggle.setColorFilter(AccentColorController.getPrimaryColor(this));
        toggle.setBackgroundResource(R.drawable.bg_icon_circle);
        toggle.setPadding(dp(7), dp(7), dp(7), dp(7));
        toggle.setScaleType(ImageView.ScaleType.CENTER);
        toggle.setRotation(expanded ? 90f : 0f);
        header.addView(toggle, new LinearLayout.LayoutParams(dp(32), dp(32)));
        card.addView(header);
        // A primeira pintura do card pode acontecer depois do último evento de progresso.
        // Aplique o estado atual aqui também, sem depender de uma nova notificação.
        boolean groupChecking = PropertyPageMonitor.getInstance().isCheckingPropertyCondominium()
                && PropertyPageMonitor.getInstance().getCheckingMarketReferenceInterestId() == interestId;
        if (groupChecking) {
            updatePropertyMarketRowTextColors(header, true);
        }

        LinearLayout offers = new LinearLayout(this);
        offers.setOrientation(LinearLayout.VERTICAL);
        offers.setVisibility(expanded ? View.VISIBLE : View.GONE);
        card.addView(offers);
        View.OnClickListener switchExpanded = view -> {
            boolean next = offers.getVisibility() != View.VISIBLE;
            getSharedPreferences(OFFER_PREFS, MODE_PRIVATE).edit()
                    .putBoolean(preferenceKey, next).apply();
            offers.setVisibility(next ? View.VISIBLE : View.GONE);
            toggle.setRotation(next ? 90f : 0f);
        };
        header.setOnClickListener(switchExpanded);
        toggle.setOnClickListener(switchExpanded);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(2);
        parent.addView(card, params);
        return offers;
    }

    private String getPropertyMarketLastCheckSummary(List<ObservedOffer> offers) {
        if (isPropertyMarketUpdating()) {
            long checkingInterestId = PropertyPageMonitor.getInstance()
                    .getCheckingMarketReferenceInterestId();
            int retrySeconds = SourceCheckStatus.getRetrySeconds(this, checkingInterestId);
            if (retrySeconds > 0) {
                return appendCheckDuration(getString(R.string.property_section_retry_countdown,
                        retrySeconds), PropertyPageMonitor.getInstance()
                        .getCurrentMarketReferencesDurationMillis());
            }
            String updatingSummary = getPropertyMarketUpdatingSummary(offers, checkingInterestId);
            if (!updatingSummary.isEmpty()) {
                return updatingSummary;
            }
            Interest checkingInterest = getInterestsById().get(checkingInterestId);
            if (checkingInterest != null && checkingInterest.isPropertyCondominium()) {
                return getString(R.string.property_market_reference_section_updating);
            }
        }
        if (hasPropertySectionFailure()) {
            return getPropertyFailureSummary();
        }
        long lastCheck = 0L;
        java.util.Map<Long, Interest> interestsById = getInterestsById();
        int pending = 0;
        for (Interest interest : interestsById.values()) {
            if (!interest.isPropertyCondominium()) continue;
            lastCheck = Math.max(lastCheck, SourceCheckStatus.getLastResultAt(this, interest.getId()));
            pending += SourceCheckStatus.pendingListingCount(this, interest.getId());
        }
        for (ObservedOffer offer : offers) {
            if (!PropertyMarketReferenceSettings.isReference(offer)
                    || !isCondominiumInterest(interestsById, offer.getInterestId())) {
                continue;
            }
            lastCheck = Math.max(lastCheck, offer.getObservedAt());
        }
        if (lastCheck <= 0L) return "";
        String summary = formatCompactCheckTime(lastCheck);
        if (pending > 0) summary += "\n" + getString(R.string.property_pending_verifications, pending);
        return appendCheckDuration(summary,
                PropertyPageMonitor.getLastMarketCheckDurationMillis(this));
    }

    private String getPropertySectionStatus(java.util.Map<Long, Interest> interestsById) {
        Interest running = null;
        Interest latest = null;
        long latestAt = 0L;
        for (Interest interest : interestsById.values()) {
            if (!interest.isPropertyCondominium()) {
                continue;
            }
            if (SourceCheckStatus.isRunning(this, interest.getId())) {
                running = interest;
                break;
            }
            long resultAt = SourceCheckStatus.getLastResultAt(this, interest.getId());
            if (resultAt > latestAt) {
                latestAt = resultAt;
                latest = interest;
            }
        }
        if (running != null) {
            int retrySeconds = SourceCheckStatus.getRetrySeconds(this, running.getId());
            if (retrySeconds > 0) {
                return appendCheckDuration(getString(R.string.property_section_retry_countdown,
                        retrySeconds), PropertyPageMonitor.getInstance()
                        .getCurrentMarketReferencesDurationMillis());
            }
            return appendCheckDuration(getString(
                    R.string.property_market_reference_section_updating_property,
                    getPropertyStatusName(running),
                    formatPropertyAreaRange(running)), PropertyPageMonitor.getInstance().getCurrentMarketReferencesDurationMillis());
        }
        if (latest != null) {
            if (SourceCheckStatus.hasFailure(this, latest.getId())) {
                return getString(R.string.property_section_check_failed_preserved);
            }
            return appendCheckDuration(formatCompactCheckTime(latestAt),
                    SourceCheckStatus.getLastDurationMillis(this, latest.getId()));
        }
        return getString(R.string.property_alerts_status_empty);
    }

    private boolean hasPropertySectionFailure() {
        for (Interest interest : getInterestsById().values()) {
            if ((!interest.isPropertyCondominium())) {
                continue;
            }
            if (SourceCheckStatus.hasFailure(this, interest.getId())) return true;
        }
        return false;
    }

    private String getPropertyFailureSummary() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Interest interest : getInterestsById().values()) {
            if (interest.isPropertyCondominium() && SourceCheckStatus.hasFailure(this, interest.getId())) {
                names.add(getPropertyStatusName(interest));
            }
        }
        return getString(R.string.property_failed_condominiums, TextUtils.join(", ", names));
    }

    private String formatCompactCheckTime(long time) {
        if (time <= 0L) {
            return getString(R.string.property_alerts_status_empty);
        }
        return OfferDateFormatter.formatGroupLabel(this, time)
                + ", "
                + OfferDateFormatter.formatTime(time);
    }

    private String getTreasuryLastCheckSummary() {
        long officialUpdatedAt = TreasuryDirectMonitor.getOfficialUpdatedAt(this);
        long lastAttemptAt = TreasuryDirectMonitor.getLastAttemptAt(this);
        String lastAttempt = lastAttemptAt <= 0L ? "" : getString(
                R.string.treasury_direct_last_checked,
                formatCompactCheckTime(lastAttemptAt));
        if (!lastAttempt.isEmpty() && TreasuryDirectMonitor.didLastAttemptFail(this)) {
            lastAttempt += "\n" + getString(R.string.app_status_source_error);
        }
        if (officialUpdatedAt > 0L) {
            String official = getString(R.string.treasury_direct_official_updated,
                    new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"))
                            .format(new java.util.Date(officialUpdatedAt)));
            return lastAttempt.isEmpty() ? official : official + "\n" + lastAttempt;
        }
        long lastCheckAt = lastAttemptAt > 0L ? lastAttemptAt
                : TreasuryDirectMonitor.getLastCheckAt(this);
        if (lastCheckAt <= 0L) return getString(R.string.property_alerts_status_empty);
        return appendCheckDuration(lastAttempt.isEmpty() ? formatCompactCheckTime(lastCheckAt) : lastAttempt,
                TreasuryDirectMonitor.getLastCheckDurationMillis(this));
    }

    private String getTreasuryMarketStatus() {
        if (TreasuryDirectMonitor.isOfficialMarketInMaintenance(this)) {
            return getString(R.string.treasury_direct_market_maintenance);
        }
        return TreasuryDirectMonitor.getLastCheckAt(this) > 0L
                ? getString(R.string.treasury_direct_market_available)
                : getString(R.string.treasury_direct_rate_waiting);
    }

    private int getTreasuryMarketStatusColor() {
        if (TreasuryDirectMonitor.isOfficialMarketInMaintenance(this)) {
            return getColor(R.color.action_yellow);
        }
        return TreasuryDirectMonitor.getLastCheckAt(this) > 0L
                ? getColor(R.color.action_green) : getColor(R.color.text_secondary);
    }

    private String getPropertyStatusName(Interest interest) {
        String name = PropertyPageResult.normalizeCondominiumName(interest.getPropertyName());
        return name.isEmpty() ? getString(R.string.property_interest_unknown_name) : name;
    }

    private String appendCheckDuration(String summary, long durationMillis) {
        if (durationMillis <= 0L) return summary;
        long seconds = Math.max(1L, Math.round(durationMillis / 1000d));
        String duration = seconds < 60L
                ? getString(R.string.check_duration_seconds, seconds)
                : getString(R.string.check_duration_minutes_seconds, seconds / 60L, seconds % 60L);
        return getString(R.string.check_duration_append, summary, duration);
    }

    private void applyPropertyStatusLine(TextView summary, TextView duration, String status) {
        if (summary == null) return;
        String value = status == null ? "" : status.trim();
        String time = "";
        int separator = value.lastIndexOf(" · ");
        if (separator >= 0) {
            String candidate = value.substring(separator + 3).trim();
            if (candidate.matches("\\d+ (?:min \\d+ )?s")) {
                time = candidate;
                value = value.substring(0, separator).trim();
            }
        }
        summary.setText(value);
        if (duration != null) {
            duration.setText(time);
            duration.setVisibility(time.isEmpty() ? View.GONE : View.VISIBLE);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) duration.getLayoutParams();
            if (params != null) {
                params.leftMargin = time.isEmpty() ? 0 : dp(8);
                duration.setLayoutParams(params);
            }
        }
    }

    private String getPropertyMarketUpdatingSummary(List<ObservedOffer> offers,
                                                     long checkingInterestId) {
        if (checkingInterestId == 0L) {
            return "";
        }
        for (ObservedOffer offer : offers) {
            if (PropertyMarketReferenceSettings.isReference(offer)
                    && offer.getInterestId() == checkingInterestId) {
                String area = getPropertyMarketReferenceArea(offer);
                if (!area.isEmpty()) {
                    return appendCheckDuration(getString(
                            R.string.property_market_reference_section_updating_property,
                            offer.getInterest(), area),
                            PropertyPageMonitor.getInstance()
                                    .getCurrentMarketReferencesDurationMillis());
                }
            }
        }
        for (Interest interest : interestRepository.getAll()) {
            if (interest.getId() != checkingInterestId) {
                continue;
            }
            if (!interest.isPropertyCondominium()) {
                return "";
            }
            String name = PropertyPageResult.normalizeCondominiumName(interest.getPropertyName());
            String area = formatPropertyAreaRange(interest);
            return name.isEmpty() || area.isEmpty() ? ""
                    : appendCheckDuration(getString(
                    R.string.property_market_reference_section_updating_property, name, area),
                    PropertyPageMonitor.getInstance().getCurrentMarketReferencesDurationMillis());
        }
        return "";
    }

    private void schedulePropertyMarketProgressUpdate() {
        if (!isPropertyMarketUpdating()) {
            dashboardHandler.removeCallbacks(propertyMarketProgressUpdate);
            propertyProgressTickerScheduled = false;
            return;
        }
        if (propertyProgressTickerScheduled) return;
        long now = android.os.SystemClock.uptimeMillis();
        long nextSecond = now + (1000L - now % 1000L);
        propertyProgressTickerScheduled = true;
        dashboardHandler.postAtTime(propertyMarketProgressUpdate, nextSecond);
    }



    private void refreshPropertyMarketProgressText() {
        if (propertyMarketSummaryView == null) {
            refreshDashboard(false);
            return;
        }
        applyPropertyStatusLine(propertyMarketSummaryView, propertyMarketDurationView,
                getPropertySectionStatus(getInterestsById()));
        int marketStatusColor = getColor(
                PropertyPageMonitor.getInstance().isCheckingPropertyCondominium()
                        ? R.color.action_green : hasPropertySectionFailure()
                        ? R.color.danger : R.color.text_secondary);
        propertyMarketSummaryView.setTextColor(marketStatusColor);
        if (propertyMarketDurationView != null) {
            propertyMarketDurationView.setTextColor(marketStatusColor);
        }
    }

    /** Updates only the two rows involved in a property-reference step. */
    private void refreshPropertyMarketProgressVisuals() {
        if (propertyMarketCardView == null || propertyMarketSummaryView == null) {
            // First step after opening Alertou: create the card once. Later steps reuse it.
            refreshDashboard(false);
            return;
        }
        refreshPropertyMarketProgressText();
        long checkingInterestId = PropertyPageMonitor.getInstance()
                .getCheckingMarketReferenceInterestId();
        if (highlightedPropertyMarketInterestId != 0L
                && highlightedPropertyMarketInterestId != checkingInterestId) {
            updateTaggedPropertyMarketRow(highlightedPropertyMarketInterestId, false);
            updateTaggedPropertyMarketGroup(highlightedPropertyMarketInterestId, false);
        }
        if (checkingInterestId != 0L
                && checkingInterestId != highlightedPropertyMarketInterestId) {
            updateTaggedPropertyMarketRow(checkingInterestId, true);
            updateTaggedPropertyMarketGroup(checkingInterestId, true);
        }
        highlightedPropertyMarketInterestId = checkingInterestId;
        updatePropertyMarketActionVisual();
    }

    private void updateTaggedPropertyMarketRow(long interestId, boolean checking) {
        View taggedRow = propertyMarketCardView.findViewWithTag(
                PROPERTY_MARKET_ROW_TAG_PREFIX + interestId);
        if (taggedRow instanceof LinearLayout) {
            updatePropertyMarketRowVisual((LinearLayout) taggedRow, checking);
        }
    }

    private void updateTaggedPropertyMarketGroup(long interestId, boolean checking) {
        View groupHeader = propertyMarketCardView.findViewWithTag(
                PROPERTY_MARKET_GROUP_TAG_PREFIX + interestId);
        if (groupHeader != null) updatePropertyMarketRowTextColors(groupHeader, checking);
    }

    private void updatePropertyMarketRowVisual(LinearLayout mainLine, boolean checking) {
        ViewParent parent = mainLine.getParent();
        if (!(parent instanceof View)) return;
        updatePropertyMarketRowTextColors((View) parent, checking);
    }

    private void updatePropertyMarketRowTextColors(View view, boolean checking) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (checking) {
                propertyMarketRowOriginalTextColors.putIfAbsent(text, text.getCurrentTextColor());
                text.setTextColor(getColor(R.color.action_green));
            } else {
                Integer original = propertyMarketRowOriginalTextColors.remove(text);
                if (original != null) text.setTextColor(original);
            }
            return;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                updatePropertyMarketRowTextColors(group.getChildAt(index), checking);
            }
        }
    }

    private void updatePropertyMarketActionVisual() {
        boolean checkingCondominium = PropertyPageMonitor.getInstance().isCheckingPropertyCondominium();
        if (propertyMarketActionView != null) {
            updatePropertyRefreshIcon(propertyMarketActionView, checkingCondominium,
                    R.drawable.ic_property_alert);
        }
    }

    private void updatePropertyRefreshIcon(ImageView icon, boolean checking,
                                           int restingIcon) {
        if (checking) {
            icon.setContentDescription(getString(R.string.action_stop_property_check));
            icon.setImageResource(R.drawable.ic_sync);
            animatePropertyMarketRefreshIcon(icon);
            return;
        }
        {
            if (propertyMarketRefreshAnimator != null) propertyMarketRefreshAnimator.cancel();
            propertyMarketRefreshAnimator = null;
            propertyMarketSpinningIcon = null;
        }
        icon.setLayerType(View.LAYER_TYPE_NONE, null);
        icon.setRotation(0f);
        icon.setImageResource(restingIcon);
        icon.setContentDescription(getString(R.string.action_refresh_property_market_prices));
    }

    private String getPropertyMarketReferenceArea(ObservedOffer offer) {
        String source = offer.getSource();
        int separator = source.lastIndexOf(" • ");
        return separator < 0 ? "" : source.substring(separator + 3).trim();
    }

    private String formatPropertyAreaRange(Interest interest) {
        double minimum = interest.getMinimumArea();
        double maximum = interest.getMaximumArea();
        if (maximum <= 0d) {
            return "";
        }
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        format.setMaximumFractionDigits(1);
        if (Double.compare(minimum, maximum) == 0 || minimum <= 0d) {
            return format.format(maximum) + " m²";
        }
        return format.format(minimum) + "–" + format.format(maximum) + " m²";
    }

    private boolean isPropertyMarketUpdating() {
        return PropertyPageMonitor.getInstance().isCheckingMarketReferences();
    }

    private void refreshPropertyMarketPrices() {
        PropertyPageMonitor monitor = PropertyPageMonitor.getInstance();
        if (monitor.isCheckingPropertyCondominium()) {
            monitor.cancelCurrentCheck(this);
            return;
        }
        List<ObservedOffer> orderedOffers = new java.util.ArrayList<>(filterOffers(
                displayedOffers, offersSearchInput.getText().toString()));
        sortOffers(orderedOffers);
        java.util.Map<Long, Interest> interestsById = getInterestsById();
        List<Long> visiblePropertyAlertIds = new java.util.ArrayList<>();
        for (ObservedOffer offer : orderedOffers) {
            if ((PropertyMarketReferenceSettings.isReference(offer) || isPropertyOffer(offer))
                    && !visiblePropertyAlertIds.contains(offer.getInterestId())) {
                visiblePropertyAlertIds.add(offer.getInterestId());
            }
        }
        for (Interest interest : interestRepository.getAll()) {
            if (interest.isProperty()
                    && !visiblePropertyAlertIds.contains(interest.getId())) {
                visiblePropertyAlertIds.add(interest.getId());
            }
        }
        // Uma consulta manual sempre entra na fila; ela não é descartada por uma automática em curso.
        monitor.checkNow(this, visiblePropertyAlertIds);
    }

    private void animatePropertyMarketRefreshIcon(ImageView icon) {
        if (propertyMarketSpinningIcon == icon && propertyMarketRefreshAnimator != null
                && propertyMarketRefreshAnimator.isRunning()) return;
        if (propertyMarketRefreshAnimator != null) propertyMarketRefreshAnimator.cancel();
        ObjectAnimator spin = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f);
        spin.setDuration(900L);
        spin.setInterpolator(new LinearInterpolator());
        spin.setRepeatCount(ValueAnimator.INFINITE);
        propertyMarketSpinningIcon = icon;
        propertyMarketRefreshAnimator = spin;
        icon.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        icon.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                if (propertyMarketSpinningIcon == icon) spin.start();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                if (propertyMarketSpinningIcon == icon) spin.cancel();
            }
        });
        if (icon.isAttachedToWindow()) spin.start();
    }

    private void refreshTreasuryRates() {
        MonitorServiceController.update(this);
        TreasuryDirectMonitor.getInstance().checkNow(this);
        updateTreasuryRefreshIcon();
    }

    private void updateTreasuryRefreshIcon() {
        if (treasuryActionView == null) return;
        boolean checking = TreasuryDirectMonitor.getInstance().isChecking();
        treasuryActionView.setContentDescription(getString(checking
                ? R.string.action_checking_treasury_rates
                : R.string.action_refresh_treasury_rates));
        if (checking) {
            treasuryActionView.setImageResource(R.drawable.ic_sync);
            treasuryActionView.setColorFilter(AccentColorController.getPrimaryColor(this));
            animateTreasuryRefreshIcon(treasuryActionView);
            return;
        }
        if (treasuryRefreshAnimator != null) treasuryRefreshAnimator.cancel();
        treasuryRefreshAnimator = null;
        treasurySpinningIcon = null;
        treasuryActionView.setRotation(0f);
        treasuryActionView.setImageResource(R.drawable.ic_tesouro_direto);
        treasuryActionView.setColorFilter(AccentColorController.getPrimaryColor(this));
    }

    private void animateTreasuryRefreshIcon(ImageView icon) {
        if (treasurySpinningIcon == icon && treasuryRefreshAnimator != null
                && treasuryRefreshAnimator.isRunning()) return;
        if (treasuryRefreshAnimator != null) treasuryRefreshAnimator.cancel();
        ObjectAnimator spin = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f);
        spin.setDuration(900L);
        spin.setInterpolator(new LinearInterpolator());
        spin.setRepeatCount(ValueAnimator.INFINITE);
        treasurySpinningIcon = icon;
        treasuryRefreshAnimator = spin;
        icon.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        icon.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                if (treasurySpinningIcon == icon) spin.start();
            }
            @Override public void onViewDetachedFromWindow(View view) {
                if (treasurySpinningIcon == icon) spin.cancel();
            }
        });
        if (icon.isAttachedToWindow()) spin.start();
    }

    private List<ObservedOffer> filterOffers(List<ObservedOffer> offers, String query) {
        String normalizedQuery = OfferTextParser.normalize(query);
        if (normalizedQuery.isEmpty()) {
            return offers;
        }
        NumberFormat currency = CurrencyTextFormatter.displayFormatter();
        List<ObservedOffer> filtered = new java.util.ArrayList<>();
        for (ObservedOffer offer : offers) {
            String text = offer.getDisplayTitle() + " " + offer.getInterest() + " " + offer.getSource() + " "
                    + currency.format(offer.getPrice()) + " " + offer.getPrice();
            if (OfferTextParser.normalize(text).contains(normalizedQuery)) {
                filtered.add(offer);
            }
        }
        return filtered;
    }

    private void sortOffers(List<ObservedOffer> offers) {
        sortOffers(offers, displayedPropertyHistory);
    }

    private void sortOffers(List<ObservedOffer> offers,
                            java.util.Map<String, PropertyHistoryEntry> propertyHistoryByOfferId) {
        int sortOrder = getSharedPreferences(OFFER_PREFS, MODE_PRIVATE)
                .getInt(HOME_SORT_ORDER, SORT_RECENT);
        Comparator<ObservedOffer> comparator;
        if (sortOrder == SORT_NAME) {
            comparator = (first, second) -> {
                int byName = OfferTextParser.normalize(first.getDisplayTitle())
                        .compareTo(OfferTextParser.normalize(second.getDisplayTitle()));
                return byName != 0 ? byName : Long.compare(second.getObservedAt(), first.getObservedAt());
            };
        } else if (sortOrder == SORT_PRICE_ASCENDING) {
            comparator = (first, second) -> {
                int availability = compareUnavailableLast(first, second, propertyHistoryByOfferId);
                if (availability != 0) return availability;
                int byPrice = Double.compare(first.getPrice(), second.getPrice());
                return byPrice != 0 ? byPrice : Long.compare(second.getObservedAt(), first.getObservedAt());
            };
        } else if (sortOrder == SORT_PRICE_DESCENDING) {
            comparator = (first, second) -> {
                int availability = compareUnavailableLast(first, second, propertyHistoryByOfferId);
                if (availability != 0) return availability;
                int byPrice = Double.compare(second.getPrice(), first.getPrice());
                return byPrice != 0 ? byPrice : Long.compare(second.getObservedAt(), first.getObservedAt());
            };
        } else if (sortOrder == SORT_PRICE_PER_SQUARE_METER_ASCENDING
                || sortOrder == SORT_PRICE_PER_SQUARE_METER_DESCENDING) {
            boolean ascending = sortOrder == SORT_PRICE_PER_SQUARE_METER_ASCENDING;
            comparator = (first, second) -> {
                int availability = compareUnavailableLast(first, second, propertyHistoryByOfferId);
                return availability != 0 ? availability
                        : comparePropertyUnitPrices(first, second, ascending);
            };
        } else {
            comparator = (first, second) -> {
                long firstRecentAt = getRecentSortTimestamp(first, propertyHistoryByOfferId);
                long secondRecentAt = getRecentSortTimestamp(second, propertyHistoryByOfferId);
                int byRecent = Long.compare(secondRecentAt, firstRecentAt);
                return byRecent != 0 ? byRecent
                        : Long.compare(second.getObservedAt(), first.getObservedAt());
            };
        }
        Comparator<ObservedOffer> selectedOrder = comparator;
        // Indisponível é estado, não critério de ordenação: ele sempre permanece no fim
        // do próprio grupo, seja qual for a ordenação escolhida pelo usuário.
        offers.sort((first, second) -> {
            int availability = compareUnavailableLast(first, second, propertyHistoryByOfferId);
            return availability != 0 ? availability : selectedOrder.compare(first, second);
        });
    }

    /** An unavailable property has no current comparable price, so it stays after active offers. */
    private int compareUnavailableLast(ObservedOffer first, ObservedOffer second,
                                       java.util.Map<String, PropertyHistoryEntry> propertyHistoryByOfferId) {
        PropertyHistoryEntry firstHistory = propertyHistoryByOfferId == null
                ? null : propertyHistoryByOfferId.get(first.getId());
        PropertyHistoryEntry secondHistory = propertyHistoryByOfferId == null
                ? null : propertyHistoryByOfferId.get(second.getId());
        boolean firstUnavailable = firstHistory != null && firstHistory.isUnavailable();
        boolean secondUnavailable = secondHistory != null && secondHistory.isUnavailable();
        return firstUnavailable == secondUnavailable ? 0 : firstUnavailable ? 1 : -1;
    }

    private int comparePropertyUnitPrices(ObservedOffer first, ObservedOffer second,
                                          boolean ascending) {
        double firstUnitPrice = getPropertyUnitPrice(first);
        double secondUnitPrice = getPropertyUnitPrice(second);
        boolean firstValid = firstUnitPrice > 0d;
        boolean secondValid = secondUnitPrice > 0d;
        if (firstValid && secondValid) {
            int byUnitPrice = ascending
                    ? Double.compare(firstUnitPrice, secondUnitPrice)
                    : Double.compare(secondUnitPrice, firstUnitPrice);
            return byUnitPrice != 0 ? byUnitPrice
                    : Long.compare(second.getObservedAt(), first.getObservedAt());
        }
        if (firstValid != secondValid) return firstValid ? -1 : 1;
        return Long.compare(second.getObservedAt(), first.getObservedAt());
    }

    private double getPropertyUnitPrice(ObservedOffer offer) {
        if (!isPropertyOffer(offer)) return -1d;
        double area = getPropertyAreaSquareMeters(offer.getSource());
        return area > 0d && offer.getPrice() > 0d ? offer.getPrice() / area : -1d;
    }

    private double getPropertyAreaSquareMeters(String source) {
        if (source == null) return -1d;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "([0-9]+(?:[,.][0-9]+)?)\\s*m²").matcher(source);
        double area = -1d;
        while (matcher.find()) {
            try {
                area = Double.parseDouble(matcher.group(1).replace(',', '.'));
            } catch (NumberFormatException ignored) {
                // Keep looking in case the source has another valid area.
            }
        }
        return area;
    }

    private String formatPropertyUnitPrice(ObservedOffer offer) {
        double unitPrice = getPropertyUnitPrice(offer);
        if (unitPrice <= 0d) return "";
        NumberFormat currency = CurrencyTextFormatter.displayFormatter();
        currency.setMaximumFractionDigits(0);
        currency.setMinimumFractionDigits(0);
        return currency.format(unitPrice) + "/m²";
    }

    private String getPropertyCondominiumCardKey(long interestId) {
        return "property_condominium_interest_expanded_" + interestId;
    }

    private String getPropertyCondominiumCardTitle(Interest interest) {
        if (interest == null) return getString(R.string.property_interest_unknown_name);
        String name = PropertyPageResult.normalizeCondominiumName(interest.getPropertyName());
        return name.isEmpty() ? getString(R.string.property_interest_unknown_name) : name;
    }

    private String abbreviatePropertyStreetTitle(String street) {
        return street.trim()
                .replaceFirst("(?i)^avenida\\s+", "Av. ")
                .replaceFirst("(?i)^rua\\s+", "R. ")
                .replaceFirst("(?i)^alameda\\s+", "Al. ")
                .replaceFirst("(?i)^travessa\\s+", "Tv. ")
                .replaceFirst("(?i)^estrada\\s+", "Est. ")
                .replaceFirst("(?i)^rodovia\\s+", "Rod. ")
                .replaceFirst("(?i)^(R\\.|Av\\.|Al\\.|Tv\\.|Est\\.|Rod\\.)\\s+doutor\\s+", "$1 Dr. ")
                .replaceFirst("(?i)^(R\\.|Av\\.|Al\\.|Tv\\.|Est\\.|Rod\\.)\\s+doutora\\s+", "$1 Dra. ");
    }

    private List<ObservedOffer> groupPropertyCondominiumOffersByInterest(
            List<ObservedOffer> offers) {
        java.util.LinkedHashMap<Long, List<ObservedOffer>> grouped = new java.util.LinkedHashMap<>();
        for (Interest interest : interestRepository.getAll()) {
            if (interest.isPropertyCondominium()) {
                grouped.put(interest.getId(), new java.util.ArrayList<>());
            }
        }
        for (ObservedOffer offer : offers) {
            List<ObservedOffer> condominiumOffers = grouped.get(offer.getInterestId());
            if (condominiumOffers != null) {
                condominiumOffers.add(offer);
            }
        }
        List<ObservedOffer> groupedOffers = new java.util.ArrayList<>();
        for (List<ObservedOffer> condominiumOffers : grouped.values()) {
            groupedOffers.addAll(condominiumOffers);
        }
        return groupedOffers;
    }

    private long getRecentSortTimestamp(ObservedOffer offer,
                                        java.util.Map<String, PropertyHistoryEntry> propertyHistoryByOfferId) {
        if (!isPropertyOffer(offer) && !PropertyMarketReferenceSettings.isReference(offer)) {
            return offer.getObservedAt();
        }
        PropertyHistoryEntry history = propertyHistoryByOfferId == null
                ? null : propertyHistoryByOfferId.get(offer.getId());
        if (history != null && history.getFirstPublicationAt() > 0L) {
            return history.getFirstPublicationAt();
        }
        return offer.getObservedAt();
    }

    private void showOffersSortDialog() {
        int selected = getSharedPreferences(OFFER_PREFS, MODE_PRIVATE)
                .getInt(HOME_SORT_ORDER, SORT_RECENT);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dashboard_sort_title)
                .setSingleChoiceItems(R.array.offers_sort_options, selected, (dialog, which) -> {
                    getSharedPreferences(OFFER_PREFS, MODE_PRIVATE).edit()
                            .putInt(HOME_SORT_ORDER, which)
                            .apply();
                    dialog.dismiss();
                    renderOffers(displayedOffers, displayedPropertyHistory);
                })
                .show();
    }

    private FrameLayout createSwipeContainer(View foreground) {
        FrameLayout container = new FrameLayout(this);
        container.setClipChildren(false);

        LinearLayout background = new LinearLayout(this);
        background.setGravity(Gravity.CENTER_VERTICAL);
        background.setOrientation(LinearLayout.HORIZONTAL);
        background.setPadding(dp(12), 0, dp(12), 0);

        ImageView trashIcon = createSwipeActionIcon(R.drawable.ic_trash_outline, R.drawable.bg_icon_danger);
        background.addView(trashIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        View spacer = new View(this);
        background.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        ImageView archiveIcon = createSwipeActionIcon(R.drawable.ic_archive, R.drawable.bg_icon_circle);
        background.addView(archiveIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        container.addView(background, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        container.addView(foreground, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        return container;
    }

    private ImageView createSwipeActionIcon(int iconResource, int backgroundResource) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setBackgroundResource(backgroundResource);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        return icon;
    }

    private LinearLayout createOfferRow(ObservedOffer offer, String title, long interestId, String price,
                                        String time, String source,
                                        String contentDescription, String propertyListingCode,
                                        boolean expired,
                                        boolean newPropertyAd, boolean propertyGoodPrice,
                                        long propertyPublishedAt,
                                        double propertyPriceChange,
                                        double propertyPriceChangePercentage,
                                        boolean propertyMarketReference,
                                        boolean newLowestMarketReference,
                                        View.OnClickListener propertyHistoryClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(getColor(R.color.card));
        row.setClickable(true);
        row.setFocusable(true);
        row.setMinimumHeight(dp(52));
        row.setPadding(0, dp(7), 0, dp(7));
        row.setContentDescription(contentDescription);
        java.util.Map<Long, Interest> interestsById = getInterestsById();

        LinearLayout mainLine = new LinearLayout(this);
        mainLine.setOrientation(LinearLayout.HORIZONTAL);
        mainLine.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleAndBadges = new LinearLayout(this);
        titleAndBadges.setGravity(Gravity.CENTER_VERTICAL);
        titleAndBadges.setOrientation(LinearLayout.HORIZONTAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getColor(expired ? R.color.text_secondary : R.color.text_primary));
        titleView.setTextSize(13);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setMaxEms(18);
        titleAndBadges.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (newPropertyAd) {
            TextView badge = createPropertyNewBadge();
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            badgeParams.leftMargin = dp(5);
            titleAndBadges.addView(badge, badgeParams);
        }


        if (Double.compare(propertyPriceChange, 0d) != 0) {
            TextView badge = createPropertyPriceChangeBadge(
                    propertyPriceChange, propertyPriceChangePercentage,
                    newLowestMarketReference, propertyHistoryClick);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            badgeParams.leftMargin = dp(5);
            titleAndBadges.addView(badge, badgeParams);
        }

        mainLine.addView(titleAndBadges, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (propertyMarketReference) {
            mainLine.setTag(PROPERTY_MARKET_ROW_TAG_PREFIX + interestId);
        }

        View priceView = createPrimaryPriceView(price, getColor(R.color.text_primary));
        priceView.setPadding(dp(6), 0, 0, 0);
        mainLine.addView(priceView);
        row.addView(mainLine);


        LinearLayout metaLine = new LinearLayout(this);
        metaLine.setOrientation(LinearLayout.HORIZONTAL);
        metaLine.setGravity(Gravity.CENTER_VERTICAL);
        metaLine.setPadding(0, dp(2), 0, 0);

        TextView timeView = new TextView(this);
        timeView.setText(time);
        timeView.setTextColor(getColor(R.color.text_secondary));
        timeView.setTextSize(11.5f);
        timeView.setSingleLine(true);
        metaLine.addView(timeView);

        String publication = propertyPublishedAt > 0L
                ? propertyMarketReference
                ? getString(R.string.property_published_line,
                        formatPropertyMarketPublishedDate(propertyPublishedAt))
                : getString(R.string.property_published_line,
                        formatPropertyPublishedLineDate(propertyPublishedAt)) : "";
        String sourceLabel = source;
        String propertyArea = "";
        String propertyUnitPrice = "";
        if (isPropertyOffer(offer)) {
            int areaSeparator = source.lastIndexOf(" • ");
            if (areaSeparator >= 0) {
                String candidate = source.substring(areaSeparator + 3).trim();
                if (candidate.contains("m²")) {
                    sourceLabel = source.substring(0, areaSeparator).trim();
                    propertyArea = candidate;
                    propertyUnitPrice = formatPropertyUnitPrice(offer);
                }
            }
        }
        TextView sourceView = new TextView(this);
        sourceView.setText("• " + sourceLabel);
        sourceView.setTextColor(getColor(R.color.text_secondary));
        sourceView.setTextSize(11.5f);
        sourceView.setSingleLine(true);
        sourceView.setEllipsize(TextUtils.TruncateAt.END);
        sourceView.setPadding(dp(4), 0, 0, 0);
        metaLine.addView(sourceView, new LinearLayout.LayoutParams(
                propertyArea.isEmpty() ? 0 : LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                propertyArea.isEmpty() ? 1 : 0));
        if (!propertyArea.isEmpty()) {
            TextView areaView = new TextView(this);
            areaView.setText(" • " + propertyArea);
            areaView.setTextColor(getColor(R.color.text_secondary));
            areaView.setTextSize(11.5f);
            areaView.setSingleLine(true);
            metaLine.addView(areaView);
        }
        if (!propertyUnitPrice.isEmpty()) {
            TextView unitPriceView = new TextView(this);
            unitPriceView.setText(" • " + propertyUnitPrice);
            unitPriceView.setTextColor(getColor(R.color.text_secondary));
            unitPriceView.setTextSize(11.5f);
            unitPriceView.setTypeface(null, android.graphics.Typeface.NORMAL);
            unitPriceView.setSingleLine(true);
            metaLine.addView(unitPriceView);
        }
        row.addView(metaLine);
        if (!publication.isEmpty() || (propertyGoodPrice)) {
            LinearLayout publicationLine = new LinearLayout(this);
            publicationLine.setGravity(Gravity.CENTER_VERTICAL);
            publicationLine.setPadding(0, dp(2), 0, 0);

            TextView publicationView = new TextView(this);
            publicationView.setText(publication);
            publicationView.setTextColor(getColor(R.color.text_secondary));
            publicationView.setTextSize(11.5f);
            publicationView.setSingleLine(true);
            publicationView.setEllipsize(TextUtils.TruncateAt.END);
            publicationLine.addView(publicationView);
            if (propertyGoodPrice) {
                TextView badge = createPropertyGoodPriceBadge();
                LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                badgeParams.leftMargin = dp(6);
                publicationLine.addView(badge, badgeParams);
            }
            row.addView(publicationLine);
        }
        if (!offer.getProductTitle().isEmpty()) {
            TextView productView = new TextView(this);
            productView.setText(offer.getProductTitle());
            productView.setTextColor(getColor(R.color.text_secondary));
            productView.setTextSize(11.5f);
            productView.setPadding(0, dp(2), 0, 0);
            row.addView(productView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        if (propertyPublishedAt > 0L || !propertyListingCode.isEmpty()) {
            String accessibleDetails = publication;
            if (!propertyListingCode.isEmpty()) {
                accessibleDetails += ". " + getString(R.string.property_listing_code, propertyListingCode);
            }
            row.setContentDescription(contentDescription + ". " + accessibleDetails);
        }
        if (expired) {
            TextView status = new TextView(this);
            status.setText(R.string.telegram_group_promotion_expired);
            status.setTextColor(getColor(R.color.text_secondary));
            status.setTextSize(11.5f);
            status.setPadding(0, dp(2), 0, 0);
            row.addView(status);
        }
        if (propertyMarketReference
                && PropertyPageMonitor.getInstance().isCheckingPropertyCondominium()
                && PropertyPageMonitor.getInstance().getCheckingMarketReferenceInterestId()
                == interestId) {
            updatePropertyMarketRowTextColors(row, true);
            highlightedPropertyMarketInterestId = interestId;
        }
        return row;
    }

    private String getOfferSourceLabel(ObservedOffer offer) {
        String source = offer.getSource();
        if (isPropertyOffer(offer)) {
            return source;
        }
        if (isTelegramOffer(offer)) {
            return getString(R.string.offer_source_telegram, source);
        }
        return source;
    }

    private boolean isTelegramOffer(ObservedOffer offer) {
        if (!offer.getTelegramPostLink().isEmpty()) {
            return true;
        }
        String id = offer.getId();
        return !(id.startsWith("vivo|")
                || id.startsWith("vivo_madrugada|")
                || id.startsWith("pelando|")
                || id.startsWith("promobit|")
                || id.startsWith("kabum|")
                || id.startsWith("motorola|")
                || id.startsWith("claro|")
                || id.startsWith("samsung|")
                || id.startsWith("samsung_discount|")
                || id.startsWith("coupon|")
                || id.startsWith("property|")
                || id.startsWith("market_reference|"));
    }

    private View createPrimaryPriceView(String value, int color) {
        if (value == null || !value.startsWith("R$")) {
            TextView text = new TextView(this);
            text.setText(value);
            text.setTextColor(color);
            text.setTextSize(14);
            text.setSingleLine(true);
            return text;
        }
        LinearLayout price = new LinearLayout(this);
        price.setGravity(Gravity.CENTER_VERTICAL);
        price.setOrientation(LinearLayout.HORIZONTAL);

        TextView symbol = new TextView(this);
        symbol.setText(R.string.currency_symbol);
        symbol.setTextColor(color);
        symbol.setTextSize(10);
        symbol.setSingleLine(true);
        price.addView(symbol);

        TextView amount = new TextView(this);
        amount.setText(value.substring(2).trim());
        amount.setTextColor(color);
        amount.setTextSize(14);
        amount.setSingleLine(true);
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        amountParams.leftMargin = -dp(2);
        price.addView(amount, amountParams);
        return price;
    }

    private TextView createPropertyNewBadge() {
        return createPropertyStatusBadge(R.string.property_new_ad_badge);
    }

    private TextView createPropertyGoodPriceBadge() {
        TextView badge = createPropertyStatusBadge(R.string.property_good_price_badge);
        badge.setText(getString(R.string.property_good_price_badge).replace('\n', ' '));
        badge.setTextSize(8.5f);
        badge.setPadding(dp(5), dp(1), dp(5), dp(1));
        return badge;
    }

    private TextView createPropertyStatusBadge(int labelResource) {
        TextView badge = new TextView(this);
        badge.setText(labelResource);
        badge.setTextColor(getColor(R.color.action_green));
        badge.setTextSize(10.5f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setSingleLine(true);
        badge.setBackgroundResource(R.drawable.bg_property_new_badge);
        badge.setPadding(dp(6), dp(1), dp(6), dp(1));
        return badge;
    }

    private TextView createPropertyPriceChangeBadge(double priceChange, double percentage,
                                                    boolean referenceReplaced,
                                                    View.OnClickListener listener) {
        TextView badge = new TextView(this);
        boolean increase = priceChange > 0d;
        NumberFormat percentageNumber = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        percentageNumber.setMaximumFractionDigits(1);
        String change = getString(increase
                        ? R.string.property_price_rise_badge_compact
                        : R.string.property_price_drop_badge_compact,
                percentageNumber.format(Math.abs(percentage)));
        badge.setText(referenceReplaced ? change + " ⇄" : change);
        badge.setContentDescription(formatPropertyPriceChange(priceChange, percentage)
                + ". " + getString(R.string.property_price_change_badge_description));
        badge.setTextColor(getColor(increase ? R.color.danger : R.color.action_green));
        badge.setTextSize(10.5f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setSingleLine(true);
        badge.setBackgroundResource(increase
                ? R.drawable.bg_property_price_rise_badge
                : R.drawable.bg_property_new_badge);
        badge.setPadding(dp(6), dp(1), dp(6), dp(1));
        badge.setClickable(true);
        badge.setFocusable(true);
        badge.setOnClickListener(listener);
        return badge;
    }

    private View createPropertyMarketListingCountChangeBadge(int change, int currentCount,
                                                              View.OnClickListener listener) {
        boolean increase = change >= 0;
        int color = getColor(increase ? R.color.action_green : R.color.danger);
        LinearLayout badge = new LinearLayout(this);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setBackgroundResource(increase
                ? R.drawable.bg_property_new_badge
                : R.drawable.bg_property_price_rise_badge);
        badge.setPadding(dp(6), dp(1), dp(5), dp(1));
        badge.setClickable(true);
        badge.setFocusable(true);
        badge.setOnClickListener(listener);

        if (change != 0) {
            TextView changeView = new TextView(this);
            changeView.setText(getString(increase
                    ? R.string.property_market_listing_count_change_increase
                    : R.string.property_market_listing_count_change_decrease, Math.abs(change)));
            changeView.setTextColor(color);
            changeView.setTextSize(10.5f);
            changeView.setTypeface(null, android.graphics.Typeface.BOLD);
            changeView.setSingleLine(true);
            badge.addView(changeView);
        }

        android.graphics.drawable.Drawable propertyIcon = ContextCompat.getDrawable(
                this, R.drawable.ic_property_alert);
        if (propertyIcon != null) {
            propertyIcon = propertyIcon.mutate();
            propertyIcon.setColorFilter(color,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            propertyIcon.setBounds(0, 0, dp(16), dp(16));
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(propertyIcon);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(16), dp(16));
            iconParams.leftMargin = dp(change == 0 ? 0 : 3);
            badge.addView(iconView, iconParams);
        }

        TextView countView = new TextView(this);
        countView.setText(String.valueOf(currentCount));
        countView.setTextColor(color);
        countView.setTextSize(10.5f);
        countView.setTypeface(null, android.graphics.Typeface.BOLD);
        countView.setSingleLine(true);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.leftMargin = dp(2);
        badge.addView(countView, countParams);
        return badge;
    }

    private TextView createPropertyListingCountBadge(int count, View.OnClickListener listener) {
        TextView badge = new TextView(this);
        String label = getResources().getQuantityString(
                R.plurals.property_market_listing_count, count, count);
        badge.setText(label);
        badge.setTextColor(getColor(R.color.action_green));
        badge.setTextSize(10.5f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setSingleLine(true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackgroundResource(R.drawable.bg_property_new_badge);
        badge.setPadding(dp(6), dp(1), dp(6), dp(1));
        badge.setClickable(true);
        badge.setFocusable(true);
        badge.setContentDescription(label);
        badge.setOnClickListener(listener);
        return badge;
    }

    private String formatPropertyPriceChange(double priceChange, double percentage) {
        double absoluteChange = Math.abs(priceChange);
        double absolutePercentage = Math.abs(percentage);
        NumberFormat number = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        NumberFormat percentageNumber = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        percentageNumber.setMaximumFractionDigits(1);
        boolean increase = priceChange > 0d;
        if (absoluteChange >= 1000d) {
            number.setMaximumFractionDigits(absoluteChange % 1000d == 0d ? 0 : 1);
            return getString(increase
                            ? R.string.property_price_rise_badge_thousands
                            : R.string.property_price_drop_badge_thousands,
                    number.format(absoluteChange / 1000d),
                    percentageNumber.format(absolutePercentage));
        }
        number.setMaximumFractionDigits(0);
        return getString(increase
                        ? R.string.property_price_rise_badge_reais
                        : R.string.property_price_drop_badge_reais,
                number.format(absoluteChange),
                percentageNumber.format(absolutePercentage));
    }

    private View createOfferDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1));
        params.leftMargin = dp(6);
        params.rightMargin = dp(6);
        divider.setLayoutParams(params);
        return divider;
    }

    private View createDateGroupDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.section_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(2));
        params.setMargins(dp(6), dp(12), dp(6), dp(4));
        divider.setLayoutParams(params);
        return divider;
    }

    private void attachSwipeActions(View row, ObservedOffer offer, boolean expired) {
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final long[] downAt = new long[1];
        final boolean[] swiping = new boolean[1];
        row.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    downAt[0] = event.getEventTime();
                    swiping[0] = false;
                    view.animate().cancel();
                    view.setTranslationX(0);
                    requestParentIntercept(view, false);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float moveX = event.getRawX() - downX[0];
                    float moveY = event.getRawY() - downY[0];
                    if (Math.abs(moveX) > dp(12) && Math.abs(moveX) > Math.abs(moveY)) {
                        swiping[0] = true;
                        requestParentIntercept(view, true);
                        float limitedMove = Math.max(-dp(96), Math.min(dp(96), moveX));
                        view.setTranslationX(limitedMove);
                    } else if (Math.abs(moveY) > dp(12) && Math.abs(moveY) > Math.abs(moveX)) {
                        requestParentIntercept(view, false);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    float deltaX = event.getRawX() - downX[0];
                    float deltaY = event.getRawY() - downY[0];
                    requestParentIntercept(view, false);
                    view.animate().translationX(0).setDuration(120).start();
                    if (Math.abs(deltaX) > dp(56) && Math.abs(deltaX) > Math.abs(deltaY) * 1.2f) {
                        if (deltaX < 0) {
                            offerRepository.archive(offer.getId());
                        } else {
                            offerRepository.trash(offer.getId());
                        }
                        refreshDashboard();
                        return true;
                    }
                    if (!swiping[0] && Math.abs(deltaX) < dp(10) && Math.abs(deltaY) < dp(10)
                            && isUnavailablePropertyOffer(offer)) {
                        showPropertyHistoryDialog(offer);
                    } else if (!swiping[0] && Math.abs(deltaX) < dp(10) && Math.abs(deltaY) < dp(10)
                            && expired) {
                        showPromotionValidityDialog(offer, true);
                    } else if (!swiping[0] && Math.abs(deltaX) < dp(10) && Math.abs(deltaY) < dp(10)
                            && event.getEventTime() - downAt[0] >= 500L
                            && isPropertyOffer(offer)) {
                        showPropertyHistoryDialog(offer);
                    } else if (!swiping[0] && Math.abs(deltaX) < dp(10) && Math.abs(deltaY) < dp(10)
                            && event.getEventTime() - downAt[0] >= 500L) {
                        showPromotionValidityDialog(offer, false);
                    } else if (!swiping[0]
                            && Math.abs(deltaX) < dp(10)
                            && Math.abs(deltaY) < dp(10)
                            && openCouponOffer(offer)) {
                        // O cupom foi copiado e a página oficial foi aberta.
                    } else if (!swiping[0]
                            && Math.abs(deltaX) < dp(10)
                            && Math.abs(deltaY) < dp(10)
                            && !offer.getTelegramPostLink().isEmpty()) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(offer.getTelegramPostLink())));
                    } else if (!swiping[0]
                            && Math.abs(deltaX) < dp(10)
                            && Math.abs(deltaY) < dp(10)
                            && offer.getLink() != null
                            && !offer.getLink().trim().isEmpty()) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(getOfferOpenLink(offer))));
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    requestParentIntercept(view, false);
                    view.animate().translationX(0).setDuration(120).start();
                    return true;
                default:
                    return true;
            }
        });
    }

    private boolean isPropertyOffer(ObservedOffer offer) {
        return offer != null && (offer.getId().startsWith("property|")
                || PropertyMarketReferenceSettings.isReference(offer));
    }

    private boolean isUnavailablePropertyOffer(ObservedOffer offer) {
        if (!isPropertyOffer(offer)) return false;
        PropertyHistoryEntry entry = new PropertyHistoryRepository(this).getForOffer(offer);
        return entry != null && entry.isUnavailable();
    }

    /** Every visible active listing is an offer; only unavailable rows are separated. */
    private boolean countsAsCurrentPropertyOffer(ObservedOffer offer) {
        if (!isPropertyOffer(offer)) return false;
        PropertyHistoryEntry entry = displayedPropertyHistory.get(offer.getId());
        return entry == null || !entry.isUnavailable();
    }

    private String getPropertyListingCode(ObservedOffer offer) {
        if (!isPropertyOffer(offer)) {
            return "";
        }
        String[] parts = offer.getId().split("\\|", -1);
        return parts.length == 3 ? parts[2].trim() : "";
    }

    private boolean isCouponOffer(ObservedOffer offer) {
        return offer != null && (offer.getId().startsWith("coupon|")
                || !extractCouponCode(offer.getSource()).isEmpty());
    }

    private String getOfferOpenLink(ObservedOffer offer) {
        if (isPropertyOffer(offer)) {
            String normalizedUrl = PropertyPageClient.normalizeListingUrl(offer.getLink());
            if (normalizedUrl != null) {
                return normalizedUrl;
            }
            String listingUrl = PropertyPageClient.buildListingUrlFromOfferId(offer.getId());
            if (!listingUrl.isEmpty()) {
                return listingUrl;
            }
        }
        return offer.getLink().trim();
    }

    private boolean openCouponOffer(ObservedOffer offer) {
        String couponCode = extractCouponCode(offer.getSource());
        if (couponCode.isEmpty()) {
            return false;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    getString(R.string.coupon_clipboard_label,
                            offer.getSource().startsWith("Samsung")
                                    ? getString(R.string.coupon_brand_samsung)
                                    : getString(R.string.coupon_brand_motorola)), couponCode));
        }
        Toast.makeText(
                this,
                getString(R.string.coupon_copied, couponCode),
                Toast.LENGTH_SHORT).show();
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(offer.getLink())));
        } catch (RuntimeException exception) {
            AppErrorStore.recordSerious(
                    this,
                    "Cupom",
                    getString(R.string.coupon_store_open_failed));
        }
        return true;
    }

    private String extractCouponCode(String source) {
        if (source == null) {
            return "";
        }
        String marker = "• cupom ";
        int markerIndex = source.toLowerCase(Locale.ROOT).indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        return source.substring(markerIndex + marker.length()).trim();
    }

    private void showPropertyNewLowestDialog(ObservedOffer offer) {
        PropertyMarketWinnerStore.WinnerInfo winner = PropertyMarketWinnerStore.get(this, offer);
        if (winner == null || Double.isNaN(winner.previousPrice)) {
            return;
        }
        NumberFormat currency = CurrencyTextFormatter.displayFormatter();
        NumberFormat percentage = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        percentage.setMaximumFractionDigits(1);
        String wonAt = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"))
                .format(new java.util.Date(winner.wonAt));
        String source = getPropertyMarketReferenceSource(offer);
        String area = getPropertyMarketReferenceArea(offer);
        double savings = Math.max(0d, winner.previousPrice - offer.getPrice());
        double savingsPercentage = winner.previousPrice <= 0d ? 0d
                : savings * 100d / winner.previousPrice;
        Dialog dialog = new Dialog(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackgroundResource(R.drawable.bg_dialog);
        scroll.addView(content);

        TextView title = new TextView(this);
        title.setText(R.string.property_price_dropped_dialog_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(21);
        content.addView(title);

        NumberFormat compactCurrency = CurrencyTextFormatter.displayFormatter();
        compactCurrency.setMaximumFractionDigits(0);
        TextView summary = new TextView(this);
        summary.setText(getString(R.string.property_price_dropped_dialog_summary,
                offer.getInterest(), compactCurrency.format(offer.getPrice()), source, area));
        summary.setTextColor(getColor(R.color.text_secondary));
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, dp(10));
        content.addView(summary);

        TextView explanation = new TextView(this);
        explanation.setText(R.string.property_price_dropped_dialog_explanation);
        explanation.setTextColor(getColor(R.color.text_secondary));
        explanation.setTextSize(13);
        explanation.setPadding(0, 0, 0, dp(8));
        content.addView(explanation);

        content.addView(createPropertyHistoryFact(R.string.property_price_dropped_at, wonAt));
        content.addView(createPropertyHistoryFact(R.string.property_price_dropped_previous,
                currency.format(winner.previousPrice)));
        content.addView(createPropertyHistoryFact(R.string.property_price_dropped_current,
                currency.format(offer.getPrice())));
        content.addView(createPropertyHistoryFact(R.string.property_price_dropped_savings,
                currency.format(savings) + " (" + percentage.format(savingsPercentage) + "%)"));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(12), 0, 0);
        TextView close = createDialogAction(R.string.action_close);
        close.setOnClickListener(view -> dialog.dismiss());
        actions.addView(close);
        content.addView(actions);

        dialog.setContentView(scroll);
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(shownWindow.getAttributes());
            params.width = getResources().getDisplayMetrics().widthPixels - dp(44);
            params.dimAmount = 0.38f;
            shownWindow.setAttributes(params);
            shownWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private String getPropertyMarketReferenceSource(ObservedOffer offer) {
        String source = offer.getSource();
        int separator = source.lastIndexOf(" • ");
        return separator < 0 ? source : source.substring(0, separator).trim();
    }

    private ObservedOffer recoverPreviousMarketReference(ObservedOffer offer,
                                                          PropertyHistoryRepository historyRepository) {
        ObservedOffer previous = PropertyMarketWinnerStore.getPreviousReference(this, offer);
        if (previous != null) return previous;
        PropertyMarketWinnerStore.WinnerInfo winner = PropertyMarketWinnerStore.get(this, offer);
        if (winner == null) return null;
        previous = historyRepository.findPreviousMarketReference(offer, winner.wonAt);
        if (previous != null) {
            PropertyMarketWinnerStore.rememberPreviousReference(this, offer, previous);
        }
        return previous;
    }

    private void showPropertyHistoryDialog(ObservedOffer offer) {
        showPropertyHistoryDialog(offer, false);
    }

    private void showPropertyMarketListingCountHistoryDialog(String condominiumName,
                                                              PropertyMarketListingCountHistory history) {
        if (history == null || history.getPoints().isEmpty()) return;
        Dialog dialog = new Dialog(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackgroundResource(R.drawable.bg_dialog);
        scroll.addView(content);

        TextView title = new TextView(this);
        title.setText(R.string.property_market_listing_count_history_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(21);
        content.addView(title);

        TextView condominium = new TextView(this);
        condominium.setText(condominiumName);
        condominium.setTextColor(getColor(R.color.text_primary));
        condominium.setTextSize(15);
        condominium.setPadding(0, dp(6), 0, 0);
        content.addView(condominium);

        TextView summary = new TextView(this);
        summary.setText(R.string.property_market_listing_count_history_summary);
        summary.setTextColor(getColor(R.color.text_secondary));
        summary.setTextSize(13);
        summary.setPadding(0, dp(3), 0, dp(10));
        content.addView(summary);

        PropertyListingCountTrendView trendView = new PropertyListingCountTrendView(this);
        trendView.setPoints(history.getPoints());
        content.addView(trendView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(history.getPoints().size() <= 2 ? 140 : 180)));

        TextView readingsTitle = new TextView(this);
        readingsTitle.setText(R.string.property_market_listing_count_readings_title);
        readingsTitle.setTextColor(getColor(R.color.text_primary));
        readingsTitle.setTextSize(16);
        readingsTitle.setPadding(0, dp(8), 0, dp(4));
        content.addView(readingsTitle);
        int firstIndex = Math.max(0, history.getPoints().size() - 8);
        for (int index = history.getPoints().size() - 1; index >= firstIndex; index--) {
            content.addView(createPropertyMarketListingCountPointRow(history.getPoints().get(index)));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(12), 0, 0);
        TextView close = createDialogAction(R.string.action_close);
        close.setOnClickListener(view -> dialog.dismiss());
        actions.addView(close);
        content.addView(actions);

        dialog.setContentView(scroll);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = getResources().getDisplayMetrics().widthPixels - dp(44);
            params.height = getResources().getDisplayMetrics().heightPixels - dp(72);
            params.dimAmount = 0.38f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private LinearLayout createPropertyMarketListingCountPointRow(
            PropertyMarketListingCountPoint point) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView date = new TextView(this);
        date.setText(formatPropertyHistoryDate(point.getObservedAt()));
        date.setTextColor(getColor(R.color.text_secondary));
        date.setTextSize(12.5f);
        row.addView(date, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView count = new TextView(this);
        count.setText(getResources().getQuantityString(R.plurals.property_market_listing_count,
                point.getCount(), point.getCount()));
        count.setTextColor(getColor(R.color.text_primary));
        count.setTextSize(12.5f);
        count.setSingleLine(true);
        row.addView(count);
        return row;
    }

    private void showPropertyHistoryDialog(ObservedOffer offer, boolean showListingAction) {
        PropertyHistoryRepository historyRepository = new PropertyHistoryRepository(this);
        PropertyHistoryEntry entry = historyRepository.getForOffer(offer);
        if (entry == null) {
            Toast.makeText(this, R.string.property_history_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackgroundResource(R.drawable.bg_dialog);
        scroll.addView(content);

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(offer.getInterest());
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(21);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleLine.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1));

        if (entry.isRecent(System.currentTimeMillis())) {
            TextView badge = createPropertyNewBadge();
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            badgeParams.leftMargin = dp(8);
            titleLine.addView(badge, badgeParams);
        }
        content.addView(titleLine);

        NumberFormat currency = CurrencyTextFormatter.displayFormatter();
        currency.setMaximumFractionDigits(0);
        TextView summary = new TextView(this);
        summary.setText(getString(
                R.string.property_history_current_summary,
                PropertyOfferDisplay.formatPrice(this, offer, entry, currency),
                offer.getSource()));
        summary.setTextColor(getColor(R.color.text_secondary));
        summary.setTextSize(14);
        summary.setPadding(0, dp(6), 0, dp(8));
        content.addView(summary);

        boolean referenceReplaced = PropertyMarketWinnerStore.isActive(this, offer);
        ObservedOffer previousReference = recoverPreviousMarketReference(offer, historyRepository);
        if (previousReference != null && historyRepository.getForOffer(previousReference) != null) {
            TextView previousButton = createInlineAction(R.string.property_history_previous_reference);
            previousButton.setOnClickListener(view -> {
                dialog.dismiss();
                showPropertyHistoryDialog(previousReference, true);
            });
            LinearLayout.LayoutParams previousButtonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            previousButtonParams.bottomMargin = dp(8);
            content.addView(previousButton, previousButtonParams);
        }
        if (showListingAction && !offer.getLink().trim().isEmpty()) {
            TextView openListing = createInlineAction(R.string.property_history_open_listing);
            openListing.setOnClickListener(view -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(offer.getLink())));
                } catch (RuntimeException exception) {
                    AppErrorStore.recordSerious(this, "Imóveis",
                            getString(R.string.property_history_open_listing_failed));
                }
            });
            LinearLayout.LayoutParams openListingParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            openListingParams.bottomMargin = dp(8);
            content.addView(openListing, openListingParams);
        }

        TextView code = new TextView(this);
        code.setText(getString(R.string.property_listing_code, entry.getListingId()));
        code.setTextColor(getColor(R.color.text_secondary));
        code.setTextSize(13);
        content.addView(code);
        if (entry.isUnavailable()) {
            content.addView(createPropertyHistoryNotice(R.string.property_unavailable_description));
            if (entry.hasLastKnownPrice()) {
                content.addView(createPropertyHistoryFact(
                        R.string.property_history_last_price_before_unavailable,
                        currency.format(entry.getLastKnownPrice())));
            }
        } else if (entry.isPendingValidation()) {
            content.addView(createPropertyHistoryNotice(R.string.property_price_pending_description));
        }
        if (entry.hasUnverifiedHistory()) {
            content.addView(createPropertyHistoryNotice(R.string.property_history_unverified_notice));
        }

        if (!entry.getTitle().trim().isEmpty()
                && !OfferTextParser.normalize(entry.getTitle())
                .equals(OfferTextParser.normalize(offer.getInterest()))) {
            TextView description = new TextView(this);
            description.setText(getString(R.string.property_history_listing_title, entry.getTitle()));
            description.setTextColor(getColor(R.color.text_secondary));
            description.setTextSize(13);
            description.setPadding(0, 0, 0, dp(10));
            content.addView(description);
        }

        if (entry.getFirstPublicationAt() > 0L) {
            content.addView(createPropertyHistoryFact(
                    R.string.property_history_published,
                    formatPropertyHistoryDate(entry.getFirstPublicationAt())));
        }
        content.addView(createPropertyHistoryFact(
                R.string.property_history_first_seen,
                formatPropertyHistoryDate(entry.getFirstSeenAt())));
        content.addView(createPropertyHistoryFact(
                R.string.property_history_last_seen,
                formatPropertyHistoryDate(entry.getLastSeenAt())));

        List<PropertyHistoryPoint> visibleHistoryPoints = getDistinctConsecutivePropertyHistoryPoints(
                entry.getPoints());
        if (referenceReplaced && previousReference != null) {
            PropertyMarketWinnerStore.WinnerInfo winner = PropertyMarketWinnerStore.get(this, offer);
            if (winner != null) {
                double previousArea = 0d;
                PropertyHistoryEntry previousHistory = historyRepository.getForOffer(previousReference);
                if (previousHistory != null && !previousHistory.getPoints().isEmpty()) {
                    previousArea = previousHistory.getPoints()
                            .get(previousHistory.getPoints().size() - 1).getArea();
                }
                List<PropertyHistoryPoint> comparisonPoints = new java.util.ArrayList<>();
                comparisonPoints.add(new PropertyHistoryPoint(
                        winner.wonAt,
                        previousReference.getPrice(),
                        previousArea,
                        true));
                if (visibleHistoryPoints.size() == 1) {
                    PropertyHistoryPoint currentPoint = visibleHistoryPoints.get(0);
                    comparisonPoints.add(new PropertyHistoryPoint(
                            winner.wonAt,
                            currentPoint.getPrice(),
                            currentPoint.getArea()));
                } else {
                    comparisonPoints.addAll(visibleHistoryPoints);
                }
                visibleHistoryPoints = comparisonPoints;
            }
        }
        if (!visibleHistoryPoints.isEmpty()) {
            TextView chartTitle = new TextView(this);
            chartTitle.setText(R.string.property_history_chart_title);
            chartTitle.setTextColor(getColor(R.color.text_primary));
            chartTitle.setTextSize(16);
            chartTitle.setPadding(0, dp(14), 0, dp(4));
            content.addView(chartTitle);

            PropertyPriceTrendView trendView = new PropertyPriceTrendView(this);
            trendView.setPoints(visibleHistoryPoints);
            content.addView(trendView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(visibleHistoryPoints.size() <= 2 ? 140 : 180)));

            TextView readingsTitle = new TextView(this);
            readingsTitle.setText(R.string.property_history_readings_title);
            readingsTitle.setTextColor(getColor(R.color.text_primary));
            readingsTitle.setTextSize(16);
            readingsTitle.setPadding(0, dp(8), 0, dp(4));
            content.addView(readingsTitle);

            int firstIndex = Math.max(0, visibleHistoryPoints.size() - 8);
            for (int index = visibleHistoryPoints.size() - 1; index >= firstIndex; index--) {
                content.addView(createPropertyHistoryPointRow(visibleHistoryPoints.get(index), currency));
            }
        } else {
            content.addView(createPropertyHistoryNotice(R.string.property_history_empty));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(12), 0, 0);
        TextView close = createDialogAction(R.string.action_close);
        close.setOnClickListener(view -> dialog.dismiss());
        actions.addView(close);
        content.addView(actions);

        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(shownWindow.getAttributes());
            params.width = getResources().getDisplayMetrics().widthPixels - dp(44);
            params.height = getResources().getDisplayMetrics().heightPixels - dp(72);
            params.dimAmount = 0.38f;
            shownWindow.setAttributes(params);
            shownWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private TextView createPropertyHistoryNotice(int resource) {
        TextView notice = new TextView(this);
        notice.setText(resource);
        notice.setTextColor(getColor(R.color.text_secondary));
        notice.setTextSize(13);
        notice.setPadding(0, dp(10), 0, dp(6));
        return notice;
    }

    private LinearLayout createPropertyHistoryFact(int labelResource, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));

        TextView label = new TextView(this);
        label.setText(labelResource);
        label.setTextColor(getColor(R.color.text_secondary));
        label.setTextSize(13);
        row.addView(label);

        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(getColor(R.color.text_primary));
        text.setTextSize(13);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Gravity.END);
        text.setPadding(dp(8), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1));
        return row;
    }

    private LinearLayout createPropertyHistoryPointRow(PropertyHistoryPoint point,
                                                       NumberFormat currency) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView date = new TextView(this);
        date.setText(formatPropertyHistoryDate(point.getObservedAt()));
        date.setTextColor(getColor(R.color.text_secondary));
        date.setTextSize(12.5f);
        row.addView(date, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1));

        TextView details = new TextView(this);
        details.setText(getString(
                R.string.property_history_point,
                currency.format(point.getPrice()),
                formatArea(point.getArea())));
        details.setTextColor(getColor(R.color.text_primary));
        details.setTextSize(12.5f);
        details.setSingleLine(true);
        row.addView(details);
        return row;
    }

    private String formatPropertyHistoryDate(long timestamp) {
        if (timestamp <= 0L) {
            return getString(R.string.property_history_unknown_date);
        }
        return new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR"))
                .format(new java.util.Date(timestamp));
    }

    private String formatPropertyPublishedLineDate(long timestamp) {
        return new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR"))
                .format(new java.util.Date(timestamp))
                .replace(".", "");
    }

    private List<PropertyHistoryPoint> getDistinctConsecutivePropertyHistoryPoints(
            List<PropertyHistoryPoint> points) {
        List<PropertyHistoryPoint> distinct = new java.util.ArrayList<>();
        for (PropertyHistoryPoint point : points) {
            if (distinct.isEmpty()) {
                distinct.add(point);
                continue;
            }
            int lastIndex = distinct.size() - 1;
            if (Double.compare(distinct.get(lastIndex).getPrice(), point.getPrice()) == 0) {
                // Preserve the newest reading while hiding an unchanged price.
                distinct.set(lastIndex, point);
            } else {
                distinct.add(point);
            }
        }
        return distinct;
    }

    private String formatPropertyMarketPublishedDate(long timestamp) {
        return new SimpleDateFormat("dd/MM/yy", new Locale("pt", "BR"))
                .format(new java.util.Date(timestamp));
    }

    private String formatArea(double area) {
        NumberFormat areaFormat = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        areaFormat.setMaximumFractionDigits(1);
        return areaFormat.format(area);
    }

    private void showPromotionValidityDialog(ObservedOffer offer, boolean expired) {
        GroupSpeedRepository speed = new GroupSpeedRepository(this);
        GroupPromotionExpiryRepository expiry = new GroupPromotionExpiryRepository(this);
        String product = offer.getInterest();
        long roundStartedAt = speed.getRoundStartedAt(offer);
        if (expired) {
            new AlertDialog.Builder(this)
                    .setTitle(product)
                    .setMessage(R.string.telegram_group_promotion_expired)
                    .setPositiveButton(R.string.telegram_group_promotion_resume_action, (dialog, which) -> {
                        boolean resumed = expiry.resumeForOffer(OfferTextParser.normalize(product),
                                offer.getObservedAt(), System.currentTimeMillis());
                        CloudSyncStore.syncPromotionExpiryChanged(this);
                        refreshDashboard();
                        if (resumed) {
                            Toast.makeText(this, R.string.telegram_group_promotion_resumed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackgroundResource(R.drawable.bg_dialog);

        TextView title = new TextView(this);
        title.setText(R.string.promotion_review_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(22);
        content.addView(title);

        TextView productView = new TextView(this);
        productView.setText(product);
        productView.setTextColor(getColor(R.color.text_primary));
        productView.setTextSize(16);
        productView.setMaxLines(2);
        productView.setEllipsize(TextUtils.TruncateAt.END);
        productView.setPadding(0, dp(7), 0, 0);
        content.addView(productView);

        TextView summary = new TextView(this);
        summary.setText(R.string.promotion_review_summary);
        summary.setTextColor(getColor(R.color.text_secondary));
        summary.setTextSize(14);
        summary.setPadding(0, dp(5), 0, dp(16));
        content.addView(summary);

        LinearLayout endedAction = createPromotionReviewAction(
                R.string.telegram_group_promotion_expire_action,
                R.string.telegram_group_promotion_expire_summary,
                R.color.action,
                R.drawable.bg_button_secondary);
        endedAction.setOnClickListener(view -> {
            expiry.markExpired(OfferTextParser.normalize(product), roundStartedAt,
                    offer.getObservedAt());
            CloudSyncStore.syncPromotionExpiryChanged(this);
            dialog.dismiss();
            refreshDashboard();
        });
        content.addView(endedAction);

        LinearLayout invalidAction = createPromotionReviewAction(
                R.string.promotion_invalid_action,
                R.string.promotion_invalid_summary,
                R.color.danger,
                R.drawable.bg_button_danger);
        LinearLayout.LayoutParams invalidParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        invalidParams.topMargin = dp(10);
        content.addView(invalidAction, invalidParams);
        invalidAction.setOnClickListener(view -> {
            speed.invalidateOffer(offer);
            new OfferInvalidationRepository(this).markInvalid(offer);
            offerRepository.trash(offer.getId());
            dialog.dismiss();
            refreshDashboard();
            Toast.makeText(this, R.string.promotion_invalid_confirmed,
                    Toast.LENGTH_SHORT).show();
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(10), 0, 0);
        TextView cancel = createDialogAction(R.string.action_cancel);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel);
        content.addView(actions);

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(shownWindow.getAttributes());
            params.width = getResources().getDisplayMetrics().widthPixels - dp(44);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.65f;
            shownWindow.setAttributes(params);
            shownWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private LinearLayout createPromotionReviewAction(int titleResource, int summaryResource,
                                                       int titleColor, int backgroundResource) {
        LinearLayout action = new LinearLayout(this);
        action.setOrientation(LinearLayout.VERTICAL);
        action.setPadding(dp(16), dp(12), dp(16), dp(12));
        action.setBackgroundResource(backgroundResource);
        action.setClickable(true);
        action.setFocusable(true);

        TextView title = new TextView(this);
        title.setText(titleResource);
        title.setTextColor(titleColor == R.color.action
                ? AccentColorController.getPrimaryColor(this)
                : getColor(titleColor));
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        action.addView(title);

        TextView summary = new TextView(this);
        summary.setText(summaryResource);
        summary.setTextColor(getColor(R.color.text_secondary));
        summary.setTextSize(13);
        summary.setPadding(0, dp(3), 0, 0);
        action.addView(summary);
        return action;
    }

    private void requestParentIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    private LinearLayout createInterestRow(String text, String contentDescription) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(3), dp(4), dp(3));

        TextView label = new TextView(this);
        label.setText(text);
        label.setContentDescription(contentDescription);
        label.setTextColor(getColor(R.color.text_primary));
        label.setTextSize(14);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, 0, dp(6), 0);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private TextView createEmptyText(int textResource) {
        TextView text = new TextView(this);
        text.setText(textResource);
        text.setTextColor(getColor(R.color.text_secondary));
        text.setTextSize(13);
        text.setPadding(dp(10), dp(8), dp(10), dp(10));
        return text;
    }

    private void hideDashboardStartupSplash() {
        dashboardHandler.removeCallbacks(dashboardStartupTimeout);
        dashboardHandler.removeCallbacks(dashboardStartupStatusAdvance);
        dashboardStartupStatusQueue.clear();
        dashboardStartupStatusShowing = false;
        dashboardStartupCompletion = null;
        cancelDashboardStartupProgressAnimation();
        if (dashboardStartupSplash != null) {
            dashboardStartupSplash.setVisibility(View.GONE);
            dashboardStartupSplash = null;
        }
        dashboardStartupProgress = null;
        dashboardStartupStatus = null;
    }

    private void startDashboardStartupProgressAnimation() {
        if (dashboardStartupProgress == null || dashboardInitialContentRendered) return;
        cancelDashboardStartupProgressAnimation();
        int start = dashboardStartupProgress.getProgress();
        dashboardStartupProgressAnimator = ValueAnimator.ofInt(start, 90);
        dashboardStartupProgressAnimator.setDuration(Math.max(1L, (90L - start) * 28L));
        dashboardStartupProgressAnimator.setInterpolator(new LinearInterpolator());
        dashboardStartupProgressAnimator.addUpdateListener(animation -> {
            if (dashboardStartupProgress != null) {
                dashboardStartupProgress.setProgress((Integer) animation.getAnimatedValue());
            }
        });
        dashboardStartupProgressAnimator.start();
    }

    private void finishDashboardStartupProgress(Runnable onFinished) {
        dashboardStartupCompletion = onFinished;
        if (dashboardStartupStatusShowing || !dashboardStartupStatusQueue.isEmpty()) {
            return;
        }
        animateDashboardStartupProgressToEnd();
    }

    private void animateDashboardStartupProgressToEnd() {
        if (dashboardStartupProgress == null) {
            runDashboardStartupCompletion();
            return;
        }
        cancelDashboardStartupProgressAnimation();
        int start = dashboardStartupProgress.getProgress();
        ValueAnimator finishAnimator = ValueAnimator.ofInt(start, 100);
        finishAnimator.setDuration(Math.max(180L, (100L - start) * 28L));
        finishAnimator.setInterpolator(new LinearInterpolator());
        finishAnimator.addUpdateListener(animation -> {
            if (dashboardStartupProgress != null) {
                dashboardStartupProgress.setProgress((Integer) animation.getAnimatedValue());
            }
        });
        finishAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (dashboardStartupProgressAnimator != animation) return;
                dashboardStartupProgressAnimator = null;
                if (dashboardStartupSplash != null) runDashboardStartupCompletion();
            }
        });
        dashboardStartupProgressAnimator = finishAnimator;
        finishAnimator.start();
    }

    private void cancelDashboardStartupProgressAnimation() {
        ValueAnimator animator = dashboardStartupProgressAnimator;
        dashboardStartupProgressAnimator = null;
        if (animator == null) return;
        // cancel() also dispatches onAnimationEnd(). Detach callbacks first so an
        // interrupted animation cannot complete startup or remove views reentrantly.
        animator.removeAllListeners();
        animator.removeAllUpdateListeners();
        animator.cancel();
    }

    private void updateDashboardStartupStatus(int statusResource) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            enqueueDashboardStartupStatus(statusResource);
        } else {
            dashboardHandler.post(() -> enqueueDashboardStartupStatus(statusResource));
        }
    }

    private void enqueueDashboardStartupStatus(int statusResource) {
        if (dashboardInitialContentRendered || dashboardStartupSplash == null) return;
        // The label is a real checkpoint, not a carousel. A queued, timed sequence can
        // repeat while storage is still busy and makes the splash look staged instead of
        // explaining the work that is actually pending.
        dashboardHandler.removeCallbacks(dashboardStartupStatusAdvance);
        dashboardStartupStatusQueue.clear();
        dashboardStartupStatusShowing = false;
        if (dashboardStartupStatus != null) {
            dashboardStartupStatus.setText(statusResource);
        }
    }

    private void showNextDashboardStartupStatus() {
        if (dashboardStartupStatusShowing) return;
        Integer statusResource = dashboardStartupStatusQueue.poll();
        if (statusResource == null) {
            if (dashboardStartupCompletion != null) animateDashboardStartupProgressToEnd();
            return;
        }
        if (dashboardStartupStatus != null) {
            dashboardStartupStatus.setText(statusResource);
        }
        dashboardStartupStatusShowing = true;
        dashboardHandler.postDelayed(dashboardStartupStatusAdvance, 450L);
    }

    private void runDashboardStartupCompletion() {
        Runnable completion = dashboardStartupCompletion;
        dashboardStartupCompletion = null;
        if (completion != null) completion.run();
    }

    private TextView createInlineAction(int textResource) {
        TextView action = new TextView(this);
        action.setText(textResource);
        action.setTextColor(AccentColorController.getPrimaryColor(this));
        action.setTextSize(14);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(12), dp(9), dp(12), dp(9));
        action.setBackgroundResource(R.drawable.bg_button_inline);
        return action;
    }

    private ImageButton createRemoveInterestButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_delete);
        button.setColorFilter(getColor(R.color.danger));
        button.setBackgroundResource(R.drawable.bg_icon_danger);
        button.setContentDescription(getString(R.string.action_remove_interest));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(7), dp(7), dp(7), dp(7));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(34), dp(34));
        params.leftMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    private void showInterestDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackgroundResource(R.drawable.bg_dialog);

        TextView title = new TextView(this);
        title.setText(R.string.interest_dialog_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(22);
        content.addView(title);

        TextView message = new TextView(this);
        message.setText(R.string.interest_dialog_summary);
        message.setTextColor(getColor(R.color.text_secondary));
        message.setTextSize(15);
        message.setPadding(0, dp(6), 0, dp(16));
        content.addView(message);

        EditText termInput = createDialogInput(
                R.string.interest_term_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        content.addView(termInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)));

        EditText priceInput = createDialogInput(
                R.string.interest_price_hint,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52));
        priceParams.topMargin = dp(12);
        content.addView(priceInput, priceParams);

        LowestPriceSuggestionView priceSuggestion = new LowestPriceSuggestionView(this);
        priceSuggestion.bind(termInput, priceInput);
        LinearLayout.LayoutParams suggestionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        suggestionParams.topMargin = dp(10);
        content.addView(priceSuggestion, suggestionParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(18), 0, 0);
        TextView cancel = createDialogAction(R.string.action_cancel);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel);
        TextView save = createDialogAction(R.string.action_save);
        save.setOnClickListener(view -> {
            String term = termInput.getText().toString().trim();
            String priceText = priceInput.getText().toString().trim().replace(',', '.');
            if (term.isEmpty()) {
                termInput.setError(getString(R.string.interest_term_required));
                return;
            }
            double maximumPrice;
            try {
                maximumPrice = Double.parseDouble(priceText);
            } catch (NumberFormatException exception) {
                priceInput.setError(getString(R.string.interest_price_required));
                return;
            }
            if (maximumPrice <= 0) {
                priceInput.setError(getString(R.string.interest_price_required));
                return;
            }
            long interestId = interestRepository.add(term, maximumPrice);
            getSharedPreferences(OFFER_PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(MONITOR_ENABLED, true)
                    .apply();
            CloudSyncStore.rememberMonitorChanged(this, System.currentTimeMillis());
            CloudSyncStore.markLocalChanged(this);
            OfferMonitor.getInstance().refreshInterestHistory(
                    this,
                    interestId,
                    term,
                    maximumPrice);
            dialog.dismiss();
            refreshDashboard();
        });
        actions.addView(save);
        content.addView(actions);

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(shownWindow.getAttributes());
            params.width = getResources().getDisplayMetrics().widthPixels - dp(44);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.65f;
            shownWindow.setAttributes(params);
            shownWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private EditText createDialogInput(int hintResource, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hintResource);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setTextColor(getColor(R.color.text_primary));
        input.setHintTextColor(getColor(R.color.text_secondary));
        input.setTextSize(16);
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setBackgroundResource(R.drawable.bg_input);
        return input;
    }

    private TextView createDialogAction(int textResource) {
        TextView action = new TextView(this);
        action.setText(textResource);
        action.setTextColor(AccentColorController.getPrimaryColor(this));
        action.setTextSize(15);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(18), dp(10), 0, dp(10));
        return action;
    }

    private void toggleMonitor() {
        boolean enabled = !isMonitorEnabled();
        long changedAt = System.currentTimeMillis();
        getSharedPreferences(OFFER_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(MONITOR_ENABLED, enabled)
                .apply();
        CloudSyncStore.rememberMonitorChanged(this, changedAt);
        CloudSyncStore.markLocalChanged(this);
        refreshDashboard();
    }

    private boolean isMonitorEnabled() {
        return MonitorServiceController.isEnabled(this);
    }

    private int getSelectedGroupCount() {
        return MonitorServiceController.selectedGroupCount(this);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence text, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence text, int start, int before, int count) {
        }
    }
}
