package br.com.droidboaoferta;

import java.util.List;

public class ArchivedOffersActivity extends StoredOffersActivity {
    @Override
    int getTitleResource() {
        return R.string.archived_screen_title;
    }

    @Override
    int getEmptyTextResource() {
        return R.string.archived_empty;
    }

    @Override
    int getBottomNavigationItem() {
        return BottomNavigationController.ITEM_ARCHIVED;
    }

    @Override
    int getCardTitleResource() {
        return R.string.archived_card_title;
    }

    @Override
    int getCardTitleIcon() {
        return R.drawable.ic_archive;
    }

    @Override
    List<ObservedOffer> getOffers(OfferRepository repository) {
        return repository.getArchived();
    }

    @Override
    boolean hasHeaderAction() {
        return true;
    }

    @Override
    int getHeaderActionIcon() {
        return R.drawable.ic_unarchive;
    }

    @Override
    int getHeaderActionDescription() {
        return R.string.action_restore_all_offers;
    }

    @Override
    int getHeaderEmptyActionMessage() {
        return R.string.archived_empty_action;
    }

    @Override
    void runHeaderAction(OfferRepository repository) {
        repository.unarchiveAll();
    }

    @Override
    int getHeaderConfirmationTitle() {
        return R.string.restore_all_saved_dialog_title;
    }

    @Override
    int getHeaderConfirmationMessage() {
        return R.string.restore_all_saved_dialog_message;
    }

    @Override
    boolean hasLongPressActions() {
        return true;
    }

    @Override
    int getLongPressPrimaryActionDescription() {
        return R.string.action_restore_offer;
    }

    @Override
    int getLongPressPrimaryConfirmationTitle() {
        return R.string.restore_saved_offer_dialog_title;
    }

    @Override
    int getLongPressPrimaryConfirmationMessage() {
        return R.string.restore_saved_offer_dialog_message;
    }

    @Override
    void runLongPressPrimaryAction(OfferRepository repository, String id) {
        repository.unarchive(id);
    }

    @Override
    boolean hasDeleteAction() {
        return false;
    }

    @Override
    int getDeleteConfirmationTitle() {
        return R.string.trash_saved_offer_dialog_title;
    }

    @Override
    int getDeleteConfirmationMessage() {
        return R.string.trash_saved_offer_dialog_message;
    }

    @Override
    void deleteOffer(OfferRepository repository, String id) {
        repository.trashArchived(id);
    }
}
