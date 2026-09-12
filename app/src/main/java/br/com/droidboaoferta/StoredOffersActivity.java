package br.com.droidboaoferta;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

abstract class StoredOffersActivity extends AlertouActivity {
    private static final String OFFER_PREFS = "offer_preferences";
    private static final String SAVED_SORT_ORDER = "saved_sort_order";
    private static final String TRASH_SORT_ORDER = "trash_sort_order";
    private static final int SORT_RECENT = 0;
    private static final int SORT_NAME = 1;
    private static final int SORT_PRICE_ASCENDING = 2;
    private static final int SORT_PRICE_DESCENDING = 3;
    private OfferRepository offerRepository;
    private LinearLayout offersContainer;
    private EditText searchInput;
    private FloatingSearchController floatingSearchController;
    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderOffers();
        }
    };

    abstract int getTitleResource();

    abstract int getEmptyTextResource();

    abstract List<ObservedOffer> getOffers(OfferRepository repository);

    boolean hasLeadingAction() {
        return false;
    }

    int getLeadingActionIcon() {
        return 0;
    }

    int getLeadingActionDescription() {
        return 0;
    }

    int getLeadingActionBackground() {
        return R.drawable.bg_icon_circle;
    }

    void runLeadingAction(OfferRepository repository, String id) {
    }

    boolean hasSecondaryAction() {
        return false;
    }

    int getSecondaryActionIcon() {
        return 0;
    }

    int getSecondaryActionDescription() {
        return 0;
    }

    int getSecondaryActionBackground() {
        return R.drawable.bg_icon_circle;
    }

    void runSecondaryAction(OfferRepository repository, String id) {
    }

    boolean hasDeleteAction() {
        return true;
    }

    int getDeleteConfirmationTitle() {
        return R.string.delete_offer_dialog_title;
    }

    int getDeleteConfirmationMessage() {
        return R.string.delete_offer_dialog_message;
    }

    boolean hasHeaderAction() {
        return false;
    }

    int getHeaderActionIcon() {
        return 0;
    }

    int getHeaderActionDescription() {
        return 0;
    }

    int getHeaderActionBackground() {
        return R.drawable.bg_icon_circle;
    }

    int getCardTitleResource() {
        return getTitleResource();
    }

    int getCardTitleIcon() {
        return R.drawable.ic_offer_tag;
    }

    int getHeaderConfirmationTitle() {
        return 0;
    }

    int getHeaderConfirmationMessage() {
        return 0;
    }

    int getHeaderEmptyActionMessage() {
        return R.string.trash_empty_action;
    }

    void runHeaderAction(OfferRepository repository) {
    }

    boolean hasSectionAction() {
        return false;
    }

    int getSectionActionIcon() {
        return 0;
    }

    int getSectionActionBackground() {
        return R.drawable.bg_icon_circle;
    }

    int getSectionActionDescription() {
        return 0;
    }

    int getSectionActionColor() {
        return R.color.action;
    }

    int getSectionConfirmationTitle() {
        return 0;
    }

    int getSectionConfirmationMessage() {
        return 0;
    }

    void runSectionAction(OfferRepository repository, List<ObservedOffer> offers) {
    }

    boolean hasLongPressActions() {
        return false;
    }

    int getLongPressPrimaryActionDescription() {
        return 0;
    }

    int getLongPressPrimaryActionIcon() {
        return R.drawable.ic_unarchive;
    }

    int getLongPressPrimaryConfirmationTitle() {
        return 0;
    }

    int getLongPressPrimaryConfirmationMessage() {
        return 0;
    }

    void runLongPressPrimaryAction(OfferRepository repository, String id) {
    }

    abstract void deleteOffer(OfferRepository repository, String id);

    int getBottomNavigationItem() {
        return BottomNavigationController.ITEM_NONE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offer_list);
        BottomNavigationController.setup(
                this,
                getBottomNavigationItem(),
                R.id.navigation_animated_content
        );

        offerRepository = new OfferRepository(this);
        offersContainer = findViewById(R.id.container_offers);
        floatingSearchController = FloatingSearchController.attach(
                this,
                "stored_" + getBottomNavigationItem(),
                R.id.floating_search_dismiss_surface
        );
        searchInput = floatingSearchController.getInput();
        ((TextView) findViewById(R.id.text_screen_title)).setText(getTitleResource());
        findViewById(R.id.button_profile).setOnClickListener(view -> startActivity(
                new Intent(this, ProfileActivity.class)
        ));
        ImageButton sortButton = findViewById(R.id.button_sort_offers);
        sortButton.setContentDescription(getString(getSortTitleResource()));
        sortButton.setOnClickListener(view -> showSortDialog());
        searchInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                renderOffers();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        TelegramClientManager.getInstance().start(this);
        ContextCompat.registerReceiver(
                this,
                syncReceiver,
                new IntentFilter(TelegramClientManager.ACTION_CLOUD_SYNC_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onStop() {
        floatingSearchController.collapse(false);
        unregisterReceiver(syncReceiver);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationController.resetInitialFocus(this);
        renderOffers();
    }

    private void renderOffers() {
        offersContainer.removeAllViews();
        List<ObservedOffer> offers = getOffers(offerRepository);
        List<ObservedOffer> visibleOffers = new java.util.ArrayList<>(
                filterOffers(offers, searchInput.getText().toString())
        );
        sortOffers(visibleOffers);
        if (visibleOffers.isEmpty()) {
            offersContainer.addView(createEmptyText());
            return;
        }

        List<ObservedOffer> coupons = new java.util.ArrayList<>();
        List<ObservedOffer> properties = new java.util.ArrayList<>();
        List<ObservedOffer> products = new java.util.ArrayList<>();
        for (ObservedOffer offer : visibleOffers) {
            if (isPropertyOffer(offer)) {
                properties.add(offer);
            } else if (isCouponOffer(offer)) {
                coupons.add(offer);
            } else {
                products.add(offer);
            }
        }
        addOfferSection(R.string.coupon_alerts_list_title, R.drawable.ic_coupon_alert, coupons);
        addOfferSection(R.string.property_alerts_list_title, R.drawable.ic_property_alert, properties);
        addOfferSection(R.string.product_alerts_list_title, R.drawable.ic_price_alert, products);
    }

    private void addOfferSection(int titleResource, int iconResource, List<ObservedOffer> offers) {
        if (offers.isEmpty()) {
            return;
        }
        String preferenceKey = "stored_section_" + getBottomNavigationItem() + "_" + titleResource;
        boolean expanded = getSharedPreferences(OFFER_PREFS, MODE_PRIVATE)
                .getBoolean(preferenceKey, true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_compact);
        card.setPadding(dp(6), dp(4), dp(6), dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(R.drawable.bg_row_pressed);
        header.setClickable(true);
        header.setFocusable(true);
        header.setPadding(dp(4), dp(2), 0, dp(3));

        if (hasSectionAction()) {
            ImageButton action = new ImageButton(this);
            action.setImageResource(getSectionActionIcon());
            action.setBackgroundResource(getSectionActionBackground());
            action.setContentDescription(getString(getSectionActionDescription()));
            action.setPadding(dp(8), dp(8), dp(8), dp(8));
            action.setScaleType(ImageView.ScaleType.CENTER);
            action.setOnClickListener(view -> showSectionConfirmationDialog(offers));
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(36), dp(36));
            actionParams.rightMargin = dp(8);
            header.addView(action, actionParams);
        } else {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconResource);
            icon.setBackgroundResource(R.drawable.bg_icon_circle);
            icon.setContentDescription(getString(titleResource));
            icon.setPadding(dp(7), dp(7), dp(7), dp(7));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
            iconParams.rightMargin = dp(8);
            header.addView(icon, iconParams);
        }

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
        count.setText(getResources().getQuantityString(
                R.plurals.dashboard_offer_section_count, offers.size(), offers.size()));
        count.setTextColor(getColor(R.color.action));
        count.setTextSize(14);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.leftMargin = dp(6);
        titleLine.addView(count, countParams);
        header.addView(titleLine, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        ImageButton toggle = new ImageButton(this);
        toggle.setImageResource(R.drawable.ic_chevron_right);
        toggle.setBackgroundResource(R.drawable.bg_icon_circle);
        toggle.setContentDescription(getString(expanded
                ? R.string.alerts_section_collapse : R.string.alerts_section_expand));
        toggle.setPadding(dp(7), dp(7), dp(7), dp(7));
        toggle.setScaleType(ImageView.ScaleType.CENTER);
        toggle.setRotation(expanded ? 90f : 0f);
        header.addView(toggle, new LinearLayout.LayoutParams(dp(32), dp(32)));

        View.OnClickListener toggleSection = view -> {
            getSharedPreferences(OFFER_PREFS, MODE_PRIVATE).edit()
                    .putBoolean(preferenceKey, !expanded)
                    .apply();
            renderOffers();
        };
        header.setOnClickListener(toggleSection);
        toggle.setOnClickListener(toggleSection);
        card.addView(header);

        if (expanded) {
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            addOfferRows(content, offers);
            card.addView(content);
        }
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.screen_card_gap);
        offersContainer.addView(card, cardParams);
    }

    private void showSectionConfirmationDialog(List<ObservedOffer> offers) {
        int title = getSectionConfirmationTitle();
        int message = getSectionConfirmationMessage();
        if (title == 0 || message == 0) {
            runSectionAction(offerRepository, offers);
            renderOffers();
            return;
        }
        showStyledConfirmation(title, getString(message), getSectionActionDescription(),
                getSectionActionColor(), () -> {
                    runSectionAction(offerRepository, offers);
                    renderOffers();
                });
    }

    private void addOfferRows(LinearLayout container, List<ObservedOffer> offers) {
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        String previousGroup = null;
        for (int index = 0; index < offers.size(); index++) {
            ObservedOffer offer = offers.get(index);
            String group = OfferDateFormatter.getGroupKey(offer.getObservedAt());
            if (!group.equals(previousGroup)) {
                if (previousGroup != null) {
                    container.addView(createDateGroupDivider());
                }
                container.addView(createOfferGroupHeader(
                        OfferDateFormatter.formatGroupLabel(this, offer.getObservedAt()),
                        previousGroup != null
                ));
                previousGroup = group;
            } else {
                container.addView(createOfferDivider());
            }
            LinearLayout row = createOfferRow(
                    offer,
                    currency.format(offer.getPrice()),
                    OfferDateFormatter.formatTime(offer.getObservedAt()),
                    offer.getSource()
            );
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            container.addView(row, params);
        }
    }

    private boolean isPropertyOffer(ObservedOffer offer) {
        return offer.getId().startsWith("property|")
                || PropertyMarketReferenceSettings.isReference(offer);
    }

    private boolean isCouponOffer(ObservedOffer offer) {
        return offer.getId().startsWith("coupon|")
                || OfferTextParser.normalize(offer.getSource()).contains("cupom");
    }

    private TextView createOfferGroupHeader(String label, boolean hasPreviousGroup) {
        TextView header = new TextView(this);
        header.setText(label);
        header.setTextColor(getColor(R.color.text_secondary));
        header.setTextSize(13);
        int titleStart = hasLeadingAction() ? 48 : 6;
        header.setPadding(dp(titleStart), dp(hasPreviousGroup ? 10 : 8), dp(8), dp(5));
        return header;
    }

    private List<ObservedOffer> filterOffers(List<ObservedOffer> offers, String query) {
        String normalizedQuery = OfferTextParser.normalize(query);
        if (normalizedQuery.isEmpty()) {
            return offers;
        }
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        List<ObservedOffer> filtered = new java.util.ArrayList<>();
        for (ObservedOffer offer : offers) {
            String text = offer.getInterest() + " " + offer.getSource() + " "
                    + currency.format(offer.getPrice()) + " " + offer.getPrice();
            if (OfferTextParser.normalize(text).contains(normalizedQuery)) {
                filtered.add(offer);
            }
        }
        return filtered;
    }

    private void sortOffers(List<ObservedOffer> offers) {
        int sortOrder = getSharedPreferences(OFFER_PREFS, MODE_PRIVATE)
                .getInt(getSortPreferenceKey(), SORT_RECENT);
        PropertyHistoryRepository propertyHistoryRepository =
                sortOrder == SORT_RECENT ? new PropertyHistoryRepository(this) : null;
        Comparator<ObservedOffer> comparator;
        if (sortOrder == SORT_NAME) {
            comparator = (first, second) -> {
                int byName = OfferTextParser.normalize(first.getInterest())
                        .compareTo(OfferTextParser.normalize(second.getInterest()));
                return byName != 0 ? byName : Long.compare(second.getObservedAt(), first.getObservedAt());
            };
        } else if (sortOrder == SORT_PRICE_ASCENDING) {
            comparator = (first, second) -> {
                int byPrice = Double.compare(first.getPrice(), second.getPrice());
                return byPrice != 0 ? byPrice : Long.compare(second.getObservedAt(), first.getObservedAt());
            };
        } else if (sortOrder == SORT_PRICE_DESCENDING) {
            comparator = (first, second) -> {
                int byPrice = Double.compare(second.getPrice(), first.getPrice());
                return byPrice != 0 ? byPrice : Long.compare(second.getObservedAt(), first.getObservedAt());
            };
        } else {
            comparator = (first, second) -> {
                long firstRecentAt = getRecentSortTimestamp(first, propertyHistoryRepository);
                long secondRecentAt = getRecentSortTimestamp(second, propertyHistoryRepository);
                int byRecent = Long.compare(secondRecentAt, firstRecentAt);
                return byRecent != 0 ? byRecent
                        : Long.compare(second.getObservedAt(), first.getObservedAt());
            };
        }
        offers.sort(comparator);
    }

    private long getRecentSortTimestamp(ObservedOffer offer,
                                        PropertyHistoryRepository propertyHistoryRepository) {
        if (offer == null || (!offer.getId().startsWith("property|")
                && !PropertyMarketReferenceSettings.isReference(offer))) {
            return offer == null ? 0L : offer.getObservedAt();
        }
        PropertyHistoryEntry history = propertyHistoryRepository == null
                ? null : propertyHistoryRepository.getForOffer(offer);
        if (history != null && history.getFirstPublicationAt() > 0L) {
            return history.getFirstPublicationAt();
        }
        return offer.getObservedAt();
    }

    private String getSortPreferenceKey() {
        return getBottomNavigationItem() == BottomNavigationController.ITEM_TRASH
                ? TRASH_SORT_ORDER : SAVED_SORT_ORDER;
    }

    private int getSortTitleResource() {
        return getBottomNavigationItem() == BottomNavigationController.ITEM_TRASH
                ? R.string.trash_sort_title : R.string.saved_sort_title;
    }

    private void showSortDialog() {
        int selected = getSharedPreferences(OFFER_PREFS, MODE_PRIVATE)
                .getInt(getSortPreferenceKey(), SORT_RECENT);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getSortTitleResource())
                .setSingleChoiceItems(R.array.offers_sort_options, selected, (dialog, which) -> {
                    getSharedPreferences(OFFER_PREFS, MODE_PRIVATE).edit()
                            .putInt(getSortPreferenceKey(), which)
                            .apply();
                    dialog.dismiss();
                    renderOffers();
                })
                .show();
    }

    private void showHeaderConfirmationDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackgroundResource(R.drawable.bg_dialog);

        TextView title = new TextView(this);
        title.setText(getHeaderConfirmationTitle());
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(21);
        content.addView(title);

        TextView message = new TextView(this);
        message.setText(getHeaderConfirmationMessage());
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
            performHeaderAction();
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

    private void performHeaderAction() {
        runHeaderAction(offerRepository);
        renderOffers();
    }

    private LinearLayout createOfferRow(ObservedOffer offer, String price, String time, String source) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(getColor(R.color.card));
        row.setMinimumHeight(dp(52));
        boolean secondaryAtEnd = hasSecondaryAction() && !hasDeleteAction();
        row.setPadding(dp(6), dp(7), secondaryAtEnd ? 0 : dp(6), dp(7));

        if (hasLeadingAction()) {
            ImageButton leading = createActionButton(
                    getLeadingActionIcon(),
                    getLeadingActionBackground(),
                    R.color.action,
                    getLeadingActionDescription()
            );
            leading.setOnClickListener(view -> {
                runLeadingAction(offerRepository, offer.getId());
                renderOffers();
            });
            LinearLayout.LayoutParams leadingParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            leadingParams.rightMargin = dp(8);
            row.addView(leading, leadingParams);
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        LinearLayout mainLine = new LinearLayout(this);
        mainLine.setGravity(Gravity.CENTER_VERTICAL);
        mainLine.setOrientation(LinearLayout.HORIZONTAL);

        TextView titleView = new TextView(this);
        titleView.setText(offer.getInterest());
        titleView.setTextColor(getColor(R.color.text_primary));
        titleView.setTextSize(14);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        mainLine.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView priceView = new TextView(this);
        priceView.setText(PropertyOfferDisplay.formatPrice(this, offer,
                new PropertyHistoryRepository(this).getForOffer(offer),
                NumberFormat.getCurrencyInstance(new Locale("pt", "BR"))));
        priceView.setTextColor(getColor(R.color.text_primary));
        priceView.setTextSize(14);
        priceView.setSingleLine(true);
        priceView.setPadding(dp(6), 0, 0, 0);
        mainLine.addView(priceView);
        texts.addView(mainLine);

        LinearLayout metaLine = new LinearLayout(this);
        metaLine.setOrientation(LinearLayout.HORIZONTAL);
        metaLine.setGravity(Gravity.CENTER_VERTICAL);
        metaLine.setPadding(0, dp(2), 0, 0);

        TextView timeView = new TextView(this);
        timeView.setText(time);
        timeView.setTextColor(getColor(R.color.action));
        timeView.setTextSize(11.5f);
        timeView.setSingleLine(true);
        metaLine.addView(timeView);

        TextView sourceView = new TextView(this);
        sourceView.setText(source);
        sourceView.setTextColor(getColor(R.color.text_secondary));
        sourceView.setTextSize(11.5f);
        sourceView.setSingleLine(true);
        sourceView.setEllipsize(TextUtils.TruncateAt.END);
        sourceView.setPadding(dp(4), 0, 0, 0);
        metaLine.addView(sourceView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        texts.addView(metaLine);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (hasSecondaryAction()) {
            ImageButton secondary = createActionButton(
                    getSecondaryActionIcon(),
                    getSecondaryActionBackground(),
                    R.color.action,
                    getSecondaryActionDescription()
            );
            secondary.setOnClickListener(view -> {
                runSecondaryAction(offerRepository, offer.getId());
                renderOffers();
            });
            LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            secondaryParams.leftMargin = dp(8);
            secondaryParams.rightMargin = secondaryAtEnd ? 0 : dp(4);
            row.addView(secondary, secondaryParams);
        }

        if (hasDeleteAction()) {
            ImageButton delete = createActionButton(
                    R.drawable.ic_delete,
                    R.drawable.bg_icon_danger,
                    R.color.danger,
                    R.string.action_delete_offer
            );
            delete.setOnClickListener(view -> {
                showDeleteConfirmationDialog(offer);
            });
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            deleteParams.leftMargin = dp(8);
            deleteParams.rightMargin = dp(4);
            row.addView(delete, deleteParams);
        }

        if (!offer.getTelegramPostLink().isEmpty()) {
            row.setOnClickListener(view -> startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(offer.getTelegramPostLink()))
            ));
        }
        if (hasLongPressActions()) {
            row.setOnLongClickListener(view -> {
                showLongPressActionsDialog(offer);
                return true;
            });
        }
        return row;
    }

    private void showLongPressActionsDialog(ObservedOffer offer) {
        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(10));
        content.setBackgroundResource(R.drawable.bg_dialog);

        TextView title = new TextView(this);
        title.setText(offer.getInterest());
        title.setTextColor(getColor(R.color.text_secondary));
        title.setTextSize(14);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(8), 0, dp(8), dp(8));
        content.addView(title);

        addLongPressMenuAction(content,
                getLongPressPrimaryActionIcon(),
                R.drawable.bg_icon_circle,
                R.color.action,
                getLongPressPrimaryActionDescription(),
                () -> {
                    dialog.dismiss();
                    showLongPressPrimaryConfirmationDialog(offer);
                });

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.divider));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.setMargins(dp(48), 0, dp(8), 0);
        content.addView(divider, dividerParams);

        addLongPressMenuAction(content,
                R.drawable.ic_delete,
                R.drawable.bg_icon_danger,
                R.color.danger,
                R.string.action_delete_offer,
                () -> {
                    dialog.dismiss();
                    showDeleteConfirmationDialog(offer);
                });

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
            params.width = getResources().getDisplayMetrics().widthPixels - dp(52);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.38f;
            shownWindow.setAttributes(params);
            shownWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void addLongPressMenuAction(LinearLayout content, int iconResource,
                                        int backgroundResource, int colorResource,
                                        int labelResource, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_row_pressed);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(6), dp(4), dp(10), dp(4));
        row.setMinimumHeight(dp(48));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(getColor(colorResource));
        icon.setBackgroundResource(backgroundResource);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView label = new TextView(this);
        label.setText(labelResource);
        label.setTextColor(getColor(colorResource));
        label.setTextSize(16);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        labelParams.leftMargin = dp(12);
        row.addView(label, labelParams);
        row.setOnClickListener(view -> action.run());
        content.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void showLongPressPrimaryConfirmationDialog(ObservedOffer offer) {
        int title = getLongPressPrimaryConfirmationTitle();
        int message = getLongPressPrimaryConfirmationMessage();
        if (title == 0 || message == 0) {
            runLongPressPrimaryAction(offerRepository, offer.getId());
            renderOffers();
            return;
        }
        showStyledConfirmation(title, getString(message, offer.getInterest()),
                getLongPressPrimaryActionDescription(), R.color.action, () -> {
                    runLongPressPrimaryAction(offerRepository, offer.getId());
                    renderOffers();
                });
    }

    private View createOfferDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        params.leftMargin = dp(10);
        params.rightMargin = dp(6);
        divider.setLayoutParams(params);
        return divider;
    }

    private View createDateGroupDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.section_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(2)
        );
        params.setMargins(dp(6), dp(12), dp(6), dp(4));
        divider.setLayoutParams(params);
        return divider;
    }

    private void showDeleteConfirmationDialog(ObservedOffer offer) {
        showStyledConfirmation(getDeleteConfirmationTitle(),
                getString(getDeleteConfirmationMessage(), offer.getInterest()),
                R.string.action_delete_offer, R.color.danger, () -> {
                    deleteOffer(offerRepository, offer.getId());
                    renderOffers();
                });
    }

    private void showStyledConfirmation(int titleResource, String messageText, int actionResource,
                                        int actionColor, Runnable action) {
        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(12));
        content.setBackgroundResource(R.drawable.bg_dialog);

        TextView title = new TextView(this);
        title.setText(titleResource);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(18);
        content.addView(title);

        TextView message = new TextView(this);
        message.setText(messageText);
        message.setTextColor(getColor(R.color.text_secondary));
        message.setTextSize(14);
        message.setPadding(0, dp(6), 0, dp(10));
        content.addView(message);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        TextView cancel = createDialogAction(R.string.action_cancel);
        cancel.setTextColor(getColor(R.color.text_secondary));
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel);
        TextView confirm = createDialogAction(actionResource);
        confirm.setTextColor(getColor(actionColor));
        confirm.setOnClickListener(view -> {
            dialog.dismiss();
            action.run();
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
            params.width = getResources().getDisplayMetrics().widthPixels - dp(52);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.38f;
            shownWindow.setAttributes(params);
            shownWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private ImageButton createActionButton(int iconResource, int backgroundResource, int colorResource,
                                           int descriptionResource) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconResource);
        button.setColorFilter(getColor(colorResource));
        button.setBackgroundResource(backgroundResource);
        button.setContentDescription(getString(descriptionResource));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        return button;
    }

    private TextView createEmptyText() {
        TextView text = new TextView(this);
        text.setText(getEmptyTextResource());
        text.setTextColor(getColor(R.color.text_secondary));
        text.setTextSize(13);
        text.setPadding(dp(10), dp(8), dp(10), dp(10));
        return text;
    }

    private TextView createDialogAction(int textResource) {
        TextView action = new TextView(this);
        action.setText(textResource);
        action.setTextColor(getColor(R.color.action));
        action.setTextSize(15);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(18), dp(10), 0, dp(10));
        return action;
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
