package mega.privacy.android.app.lollipop.megachat.chatAdapters;

import android.app.Activity;
import android.content.Context;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import android.util.DisplayMetrics;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.facebook.common.util.UriUtil;

import java.util.ArrayList;
import java.util.List;

import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.R;
import mega.privacy.android.app.lollipop.megachat.ChatFileStorageFragment;

import static mega.privacy.android.app.utils.LogUtil.*;

public class MegaChatFileStorageAdapter extends RecyclerView.Adapter<MegaChatFileStorageAdapter.ViewHolderBrowser> implements OnClickListener, View.OnLongClickListener {

    Context context;
    ActionBar aB;
    ArrayList<String> uriImages;
    Object fragment;
    DisplayMetrics outMetrics;
    boolean multipleSelect;
    int padding = 6;
    DatabaseHandler dbH;
    private SparseBooleanArray selectedItems;
    private int dimPhotos;
    private RecyclerView recyclerViewFragment;

    public MegaChatFileStorageAdapter(Context _context, Object fragment, RecyclerView recyclerView, ActionBar aB, ArrayList<String> _uriImages, int dimPhotos) {
        this.context = _context;
        this.fragment = fragment;
        this.recyclerViewFragment = recyclerView;
        this.uriImages = _uriImages;
        this.dimPhotos = dimPhotos;
        dbH = DatabaseHandler.getDbHandler(context);
        this.aB = aB;
    }

    public void toggleSelection(int pos) {
        if (selectedItems.get(pos, false)) {
            selectedItems.delete(pos);
            ((ChatFileStorageFragment) fragment).removePosition(pos);
        } else {
            selectedItems.put(pos, true);
            ((ChatFileStorageFragment) fragment).addPosition(pos);
        }

        updateSelectedItem(pos);

        if (selectedItems.size() <= 0) {
            ((ChatFileStorageFragment) fragment).hideMultipleSelect();
        }

    }

    public void toggleAllSelection(int pos) {
        final int positionToflip = pos;

        if (selectedItems.get(pos, false)) {
            selectedItems.delete(pos);
            ((ChatFileStorageFragment) fragment).removePosition(pos);

        } else {
            selectedItems.put(pos, true);
            ((ChatFileStorageFragment) fragment).addPosition(pos);

        }
        logDebug("Adapter type is GRID");
        if (selectedItems.size() <= 0) {
            ((ChatFileStorageFragment) fragment).hideMultipleSelect();
        }
        notifyItemChanged(positionToflip);
    }

    public void clearSelections() {
        for (int i = 0; i < this.getItemCount(); i++) {
            if (isItemChecked(i)) {
                toggleAllSelection(i);
            }
        }
        notifyDataSetChanged();
    }

    private boolean isItemChecked(int position) {
        return selectedItems.get(position);
    }

    public int getSelectedItemCount() {
        return selectedItems.size();
    }

    public List<Integer> getSelectedItems() {
        List<Integer> items = new ArrayList<Integer>(selectedItems.size());
        for (int i = 0; i < selectedItems.size(); i++) {
            items.add(selectedItems.keyAt(i));
        }
        return items;
    }

    public void setNodes(ArrayList<String> uriImages) {
        this.uriImages = uriImages;
        notifyDataSetChanged();
    }

    public void setDimensionPhotos(int dimPhotos) {
        this.dimPhotos = dimPhotos;
        notifyDataSetChanged();
    }

    public int getSpanSizeOfPosition(int position) {
        return 1;
    }

    @Override
    public ViewHolderBrowser onCreateViewHolder(ViewGroup parent, int viewType) {

        Display display = ((Activity) context).getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_storage_grid, parent, false);
        ViewHolderBrowserGrid holderGrid = new ViewHolderBrowserGrid(v);

        holderGrid.itemLayout = v.findViewById(R.id.file_storage_grid_item_layout);
        holderGrid.thumbLayout = v.findViewById(R.id.file_storage_grid_thumbnail_layout);
        holderGrid.thumbLayout.setPadding(padding, padding, padding, padding);

        ViewGroup.LayoutParams params = holderGrid.thumbLayout.getLayoutParams();
        params.height = dimPhotos;
        params.width = dimPhotos;
        holderGrid.thumbLayout.setLayoutParams(params);
        holderGrid.thumbLayout.setVisibility(View.GONE);

        holderGrid.photo = v.findViewById(R.id.file_storage_grid_thumbnail);
        holderGrid.photoSelectedIcon = v.findViewById(R.id.thumbnail_selected_image);
        holderGrid.photoSelectedStroke = v.findViewById(R.id.thumbnail_selected_stroke);

        holderGrid.photoSelectedStroke.setVisibility(View.GONE);
        holderGrid.photoSelectedIcon.setVisibility(View.GONE);

        holderGrid.photoSelectedIcon.setMaxHeight(dimPhotos);
        holderGrid.photoSelectedIcon.setMaxWidth(dimPhotos);

        holderGrid.itemLayout.setTag(holderGrid);
        holderGrid.itemLayout.setOnClickListener(this);
        holderGrid.itemLayout.setOnLongClickListener(this);

        v.setTag(holderGrid);

        return holderGrid;
    }

    @Override
    public void onBindViewHolder(ViewHolderBrowser holder, int position) {
        ViewHolderBrowserGrid holderGrid = (ViewHolderBrowserGrid) holder;
        onBindViewHolderGrid(holderGrid, position);
    }

    public void onBindViewHolderGrid(ViewHolderBrowserGrid holder, int position) {
        holder.thumbLayout.setVisibility(View.VISIBLE);
        holder.photo.setVisibility(View.VISIBLE);
        holder.photo.setImageURI(UriUtil.parseUriOrNull(uriImages.get(position)));

        if (!multipleSelect || !this.isItemChecked(position)) {
            holder.photoSelectedIcon.setVisibility(View.GONE);
            holder.photoSelectedStroke.setVisibility(View.GONE);
            return;
        }
        holder.photoSelectedIcon.setVisibility(View.VISIBLE);
        holder.photoSelectedStroke.setVisibility(View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        if (uriImages != null) {
            return uriImages.size();
        }
        return 0;
    }

    public Object getItem(int position) {
        if (uriImages != null) {
            return uriImages.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public String getItemAt(int position) {

        try {
            if (uriImages != null) {
                return uriImages.get(position);
            }
        } catch (IndexOutOfBoundsException e) {
        }
        return null;
    }

    @Override
    public void onClick(View v) {
        logDebug("onClick()");
        ViewHolderBrowser holder = (ViewHolderBrowser) v.getTag();
        int currentPosition = holder.getAdapterPosition();
        logDebug("Current position: " + currentPosition);
        if (currentPosition < 0) {
            logWarning("Current position error - not valid value");
            return;
        }
        if (!isMultipleSelect()) {
            setMultipleSelect(true);
        }
        ((ChatFileStorageFragment) fragment).itemClick(currentPosition);
    }

    @Override
    public boolean onLongClick(View view) {
        ViewHolderBrowser holder = (ViewHolderBrowser) view.getTag();
        int currentPosition = holder.getAdapterPosition();

        if (!isMultipleSelect()) {
            setMultipleSelect(true);
        }
        ((ChatFileStorageFragment) fragment).itemClick(currentPosition);
        return true;
    }

    public boolean isMultipleSelect() {
        return multipleSelect;
    }

    public void setMultipleSelect(boolean multipleSelect) {
        if (this.multipleSelect != multipleSelect) {
            this.multipleSelect = multipleSelect;
            ((ChatFileStorageFragment) fragment).updateIconSend(this.multipleSelect);
        }
        if (this.multipleSelect) {
            selectedItems = new SparseBooleanArray();
        }
    }

    /**
     * Method to update the selected or deselected item.
     *
     * @param position The Int with the position of the item to be updated.
     */
    public void updateSelectedItem(int position) {
        ViewHolderBrowserGrid holderGrid = (ViewHolderBrowserGrid) recyclerViewFragment.findViewHolderForAdapterPosition(position);

        if (holderGrid == null) {
            notifyItemChanged(position);
            return;
        }

        int visibility = !multipleSelect || !isItemChecked(position) ? View.GONE : View.VISIBLE;
        holderGrid.photoSelectedIcon.setVisibility(visibility);
        holderGrid.photoSelectedStroke.setVisibility(visibility);
    }

    /* public static view holder class */
    public static class ViewHolderBrowser extends ViewHolder {
        public RelativeLayout itemLayout;

        public ViewHolderBrowser(View v) {
            super(v);
        }
    }

    public static class ViewHolderBrowserGrid extends ViewHolderBrowser {
        public ImageView photo;
        public RelativeLayout thumbLayout;
        public ImageView photoSelectedIcon;
        public ImageView photoSelectedStroke;

        public ViewHolderBrowserGrid(View v) {
            super(v);
        }
    }
}
