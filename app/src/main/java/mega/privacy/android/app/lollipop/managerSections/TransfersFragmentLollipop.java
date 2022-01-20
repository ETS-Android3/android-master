package mega.privacy.android.app.lollipop.managerSections;

import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;

import mega.privacy.android.app.R;
import mega.privacy.android.app.fragments.managerFragments.TransfersBaseFragment;
import mega.privacy.android.app.fragments.managerFragments.actionMode.TransfersActionBarCallBack;
import mega.privacy.android.app.interfaces.MoveTransferInterface;
import mega.privacy.android.app.listeners.MoveTransferListener;
import mega.privacy.android.app.lollipop.adapters.MegaTransfersLollipopAdapter;
import mega.privacy.android.app.utils.ColorUtils;
import mega.privacy.android.app.lollipop.adapters.RotatableAdapter;
import nz.mega.sdk.MegaTransfer;

import static mega.privacy.android.app.lollipop.ManagerActivityLollipop.PENDING_TAB;
import static mega.privacy.android.app.utils.Constants.INVALID_POSITION;
import static mega.privacy.android.app.utils.Constants.SNACKBAR_TYPE;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.Util.*;
import static nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE;
import static nz.mega.sdk.MegaTransfer.STATE_COMPLETING;


public class TransfersFragmentLollipop extends TransfersBaseFragment implements MegaTransfersLollipopAdapter.SelectModeInterface, TransfersActionBarCallBack.TransfersActionCallback, MoveTransferInterface {

	private MegaTransfersLollipopAdapter adapter;

	private ArrayList<MegaTransfer> tL = new ArrayList<>();

	public static TransfersFragmentLollipop newInstance() {
		return new TransfersFragmentLollipop();
	}

	private ActionMode actionMode;

	private ItemTouchHelper itemTouchHelper;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {

		super.onCreateView(inflater, container, savedInstanceState);

		View v = initView(inflater, container);

		emptyImage.setImageResource(isScreenInPortrait(context) ? R.drawable.empty_transfer_portrait : R.drawable.empty_transfer_landscape);

		String textToShow = context.getString(R.string.transfers_empty_new);
		try {
			textToShow = textToShow.replace("[A]", "<font color=\'"
					+ ColorUtils.getColorHexString(context, R.color.grey_900_grey_100)
					+ "\'>");
			textToShow = textToShow.replace("[/A]", "</font>");
			textToShow = textToShow.replace("[B]", "<font color=\'"
					+ ColorUtils.getColorHexString(context, R.color.grey_300_grey_600)
					+ "\'>");
			textToShow = textToShow.replace("[/B]", "</font>");
		} catch (Exception e) {
			logWarning("Exception formatting string", e);
		}
		emptyText.setText(HtmlCompat.fromHtml(textToShow, HtmlCompat.FROM_HTML_MODE_LEGACY));

		setTransfers();

		adapter = new MegaTransfersLollipopAdapter(context, tL, listView, this);

		adapter.setMultipleSelect(false);
		listView.setAdapter(adapter);
		listView.setItemAnimator(noChangeRecyclerViewItemAnimator());

		itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
			private boolean addElevation = true;
			private boolean resetElevation = false;
			private MegaTransfer draggedTransfer;
			private int newPosition;


			@Override
			public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
				int posDragged = viewHolder.getAbsoluteAdapterPosition();
				newPosition = target.getAbsoluteAdapterPosition();

				if (draggedTransfer == null) {
					draggedTransfer = tL.get(posDragged);
				}

				Collections.swap(tL, posDragged, newPosition);
				adapter.moveItemData(tL, posDragged, newPosition);

				return false;
			}

			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

			}

			@Override
			public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
				// Add elevation when the item is picked
				if (addElevation) {
					recyclerView.post(() -> listView.removeItemDecoration(itemDecoration));
					ViewPropertyAnimator animator = viewHolder.itemView.animate();
					viewHolder.itemView.setTranslationZ(dp2px(2, outMetrics));
					viewHolder.itemView.setAlpha(0.95f);
					animator.start();

					addElevation = false;
				}

				// Remove elevation when the item is loose
				if (resetElevation){
					recyclerView.post(() -> listView.addItemDecoration(itemDecoration));
					ViewPropertyAnimator animator = viewHolder.itemView.animate();
					viewHolder.itemView.setTranslationZ(0);
					viewHolder.itemView.setAlpha(1f);
					animator.start();

					addElevation = true;
					resetElevation = false;
				}

				super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
			}

			@Override
			public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
				super.clearView(recyclerView, viewHolder);
				// Drag finished, elevation should be removed.
				resetElevation = true;
			}

			@Override
			public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
				super.onSelectedChanged(viewHolder, actionState);

				if (actionState != ItemTouchHelper.ACTION_STATE_IDLE
						|| draggedTransfer == null) {
					return;
				}

				startMovementRequest(draggedTransfer, newPosition);
				draggedTransfer = null;
			}
		});

		enableDragAndDrop();

		return v;
	}

	private void setTransfers() {
		tL.clear();

		for (int i = 0; i < managerActivity.transfersInProgress.size(); i++) {
			MegaTransfer transfer = megaApi.getTransferByTag(managerActivity.transfersInProgress.get(i));
			if (transfer != null && !transfer.isStreamingTransfer()) {
				tL.add(transfer);
			}
		}

		orderTransfersByDescendingPriority();

		setEmptyView(tL.size());
	}

	/**
	 * Updates the state of a transfer.
	 *
	 * @param transfer transfer to update
	 */
	public void transferUpdate(MegaTransfer transfer) {
		int transferPosition = tryToUpdateTransfer(transfer);

		if (transferPosition != INVALID_POSITION && adapter != null) {
			adapter.updateProgress(transferPosition, transfer);
		}
	}

	/**
	 * Tries to update a MegaTransfer in the transfers list.
	 *
	 * @param transfer The MegaTransfer to update.
	 * @return The position of the updated transfer if success, INVALID_POSITION otherwise.
	 */
	public int tryToUpdateTransfer(MegaTransfer transfer) {
		if (transfer == null) {
			return INVALID_POSITION;
		}

		try {
			ListIterator<MegaTransfer> li = tL.listIterator();

			while (li.hasNext()) {
				MegaTransfer next = (MegaTransfer) li.next();
				if (next != null && next.getTag() == transfer.getTag()) {
					int index = li.previousIndex();
					tL.set(index, transfer);
					return index;
				}
			}
		} catch (IndexOutOfBoundsException e) {
			logError("IndexOutOfBoundsException trying to update a transfer.", e);
		}

		return INVALID_POSITION;
	}

	/**
	 * Changes the status (play/pause) of the button of a transfer.
	 *
	 * @param tag	identifier of the transfer to change the status of the button
	 */
	public void changeStatusButton(int tag) {
		logDebug("tag: " + tag);

		ListIterator li = tL.listIterator();
		int index = 0;
		while (li.hasNext()) {
			MegaTransfer next = (MegaTransfer) li.next();
			if (next == null) continue;

			if (next.getTag() == tag) {
				index = li.previousIndex();
				break;
			}
		}
		MegaTransfer transfer = megaApi.getTransferByTag(tag);
		if (transfer != null) {
			tL.set(index, transfer);
			logDebug("The transfer with index : " + index + "has been paused/resumed, left: " + tL.size());

			adapter.notifyItemChanged(index);
		}
	}

	/**
	 * Removes a transfer when finishes.
	 *
	 * @param transferTag	identifier of the transfer to remove
	 */
	public void transferFinish(int transferTag) {
		for (int i = 0; i < tL.size(); i++) {
			MegaTransfer transfer = tL.get(i);
			if (transfer != null && transfer.getTag() == transferTag) {
				tL.remove(i);
				adapter.removeItemData(tL, i);
				break;
			}
		}

		if (tL.isEmpty()) {
			destroyActionMode();
			setEmptyView(tL.size());
			managerActivity.supportInvalidateOptionsMenu();
		}
	}

	/**
	 * Adds a transfer when starts.
	 *
	 * @param transfer	transfer to add
	 */
	public void transferStart(MegaTransfer transfer) {
		if (transfer.isStreamingTransfer()) {
			return;
		}

		if (tL.isEmpty()) {
			managerActivity.supportInvalidateOptionsMenu();
		}

		tL.add(transfer);
		orderTransfersByDescendingPriority();
		adapter.addItemData(tL, tL.indexOf(transfer));

		if (tL.size() == 1) {
			setEmptyView(tL.size());
		}
	}

	/**
	 * Checks if there is any transfer in progress.
	 *
	 * @return True if there is not any transfer, false otherwise.
	 */
	public boolean isEmpty() {
		return tL.isEmpty();
	}

	private void orderTransfersByDescendingPriority() {
		Collections.sort(tL, (t1, t2) -> t1.getPriority().compareTo(t2.getPriority()));
	}

	/**
	 * Launches the request to change the priority of a transfer.
	 *
	 * @param transfer    MegaTransfer to change its priority.
	 * @param newPosition The new position on the list.
	 */
	private void startMovementRequest(MegaTransfer transfer, int newPosition) {
		MoveTransferListener moveTransferListener = new MoveTransferListener(context, this);

		if (newPosition == 0) {
			megaApi.moveTransferToFirst(transfer, moveTransferListener);
		} else if (newPosition == tL.size() - 1) {
			megaApi.moveTransferToLast(transfer, moveTransferListener);
		} else {
			megaApi.moveTransferBefore(transfer, tL.get(newPosition + 1), moveTransferListener);
		}
	}

	private void enableDragAndDrop() {
		if (itemTouchHelper != null && listView != null) {
			itemTouchHelper.attachToRecyclerView(listView);
		}
	}

	private void disableDragAndDrop() {
		if (itemTouchHelper != null) {
			itemTouchHelper.attachToRecyclerView(null);
		}
	}

	private void reorderTransfersAfterFailedMovement() {
		orderTransfersByDescendingPriority();

		if (adapter != null) {
			adapter.setTransfers(tL);
		}
	}

	@Override
	public void checkScroll() {
		managerActivity.changeAppBarElevation((listView != null && listView.canScrollVertically(-1))
				|| (adapter != null && adapter.isMultipleSelect()));
	}

	@Override
	public void destroyActionMode() {
		if (actionMode != null) {
			actionMode.finish();
		}

		enableDragAndDrop();
	}

	@Override
	public void notifyItemChanged() {
		updateActionModeTitle();
	}

	@Override
	protected RotatableAdapter getAdapter() {
		return adapter;
	}

	@Override
	public void activateActionMode() {
		if (adapter != null && !adapter.isMultipleSelect()) {
			adapter.setMultipleSelect(true);
			actionMode = ((AppCompatActivity) context).startSupportActionMode(new TransfersActionBarCallBack(this));
			updateActionModeTitle();
			disableDragAndDrop();
		}
	}

	@Override
	public void multipleItemClick(int position) {
		adapter.toggleSelection(position);
	}

	@Override
	public void reselectUnHandledSingleItem(int position) {

	}

	@Override
	protected void updateActionModeTitle() {
		if (actionMode == null || getActivity() == null || adapter == null) {
			logWarning("RETURN: null values");
			return;
		}

		long count = adapter.getSelectedItemsCount();
		String title = count == 0 ? getString(R.string.title_select_transfers) : count + "";
		actionMode.setTitle(title.toUpperCase());
		actionMode.invalidate();
	}

	@Override
	public void onCreateActionMode() {
		checkScroll();
	}

	@Override
	public void onDestroyActionMode() {
		clearSelections();

		if (adapter != null) {
			adapter.hideMultipleSelect();
		}

		checkScroll();
	}

	@Override
	public void cancelTransfers() {
		if (adapter != null) {
			managerActivity.showConfirmationCancelSelectedTransfers(adapter.getSelectedTransfers());
		}
	}

	@Override
	public void selectAll() {
		if (adapter != null) {
			adapter.selectAll();
		}
	}

	@Override
	public void clearSelections() {
		if (adapter != null) {
			adapter.clearSelections();
		}
	}

	@Override
	public int getSelectedTransfers() {
		return adapter == null ? 0 : adapter.getSelectedItemsCount();
	}

	@Override
	public boolean areAllTransfersSelected() {
		return adapter != null && adapter.getSelectedItemsCount() == adapter.getItemCount();
	}

	@Override
	public void hideTabs(boolean hide) {
		managerActivity.hideTabs(hide, PENDING_TAB);
	}

	@Override
	public void movementFailed(int transferTag) {
		finishMovement(false, transferTag);
	}

	@Override
	public void movementSuccess(int transferTag) {
		finishMovement(true, transferTag);
	}

	/**
	 * Updates the UI in consequence after a transfer movement.
	 * The update depends on if the movement finished with or without success.
	 * If it finished with success, simply update the transfer in the transfers list and in adapter.
	 * If not, reverts the movement, leaving the transfer in the same position it has before made the change.
	 *
	 * @param success     True if the movement finished with success, false otherwise.
	 * @param transferTag Identifier of the transfer.
	 */
	private void finishMovement(boolean success, int transferTag) {
		MegaTransfer transfer = megaApi.getTransferByTag(transferTag);
		if (transfer == null || transfer.getState() >= STATE_COMPLETING) {
			logWarning("The transfer doesn't exist, finished or is finishing.");
			return;
		}

		int transferPosition = tryToUpdateTransfer(transfer);
		if (transferPosition == INVALID_POSITION) {
			logWarning("The transfer doesn't exist.");
			return;
		}

		if (success) {
			if (adapter != null) {
				adapter.notifyItemChanged(transferPosition);
			}

			return;
		}

		reorderTransfersAfterFailedMovement();
		managerActivity.showSnackbar(SNACKBAR_TYPE,
				getString(R.string.change_of_transfer_priority_failed, transfer.getFileName()),
				MEGACHAT_INVALID_HANDLE);

		if (adapter != null) {
			adapter.setTransfers(tL);
		}
	}

	public void checkSelectModeAfterChangeTabOrDrawerItem() {
		if (adapter != null && adapter.isMultipleSelect()) {
			destroyActionMode();
		}
	}
}
