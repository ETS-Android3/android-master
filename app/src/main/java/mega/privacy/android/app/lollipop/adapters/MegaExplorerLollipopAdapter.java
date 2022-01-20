package mega.privacy.android.app.lollipop.adapters;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.MegaPreferences;
import mega.privacy.android.app.MimeTypeList;
import mega.privacy.android.app.MimeTypeThumbnail;
import mega.privacy.android.app.R;
import mega.privacy.android.app.components.NewGridRecyclerView;
import mega.privacy.android.app.components.scrollBar.SectionTitleProvider;
import mega.privacy.android.app.databinding.SortByHeaderBinding;
import mega.privacy.android.app.fragments.homepage.SortByHeaderViewModel;
import mega.privacy.android.app.lollipop.CloudDriveExplorerFragmentLollipop;
import mega.privacy.android.app.lollipop.FileExplorerActivityLollipop;
import mega.privacy.android.app.lollipop.IncomingSharesExplorerFragmentLollipop;
import mega.privacy.android.app.lollipop.ManagerActivityLollipop;
import mega.privacy.android.app.utils.ColorUtils;
import mega.privacy.android.app.utils.MegaNodeUtil;
import mega.privacy.android.app.utils.ThumbnailUtilsLollipop;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaNode;
import nz.mega.sdk.MegaShare;
import nz.mega.sdk.MegaUser;

import static mega.privacy.android.app.lollipop.adapters.MegaNodeAdapter.ITEM_VIEW_TYPE_GRID;
import static mega.privacy.android.app.lollipop.adapters.MegaNodeAdapter.ITEM_VIEW_TYPE_HEADER;
import static mega.privacy.android.app.lollipop.adapters.MegaNodeAdapter.ITEM_VIEW_TYPE_LIST;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.FileUtil.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.MegaApiUtils.*;
import static mega.privacy.android.app.utils.MegaNodeUtil.*;
import static mega.privacy.android.app.utils.TimeUtils.*;
import static mega.privacy.android.app.utils.Util.*;
import static mega.privacy.android.app.utils.ContactUtil.*;
import static nz.mega.sdk.MegaApiJava.INVALID_HANDLE;

import org.jetbrains.annotations.NotNull;

public class MegaExplorerLollipopAdapter extends RecyclerView.Adapter<MegaExplorerLollipopAdapter.ViewHolderExplorerLollipop> implements View.OnClickListener, View.OnLongClickListener, SectionTitleProvider, RotatableAdapter {
	public static int MAX_WIDTH_FILENAME_LAND=500;
	public static int MAX_WIDTH_FILENAME_PORT=235;

	Context context;
	MegaApiAndroid megaApi;
	MegaPreferences prefs;

	ArrayList<Integer> imageIds;
	ArrayList<String> names;
	ArrayList<MegaNode> nodes;

    DatabaseHandler dbH = null;

	Object fragment;

	long parentHandle = -1;
	boolean selectFile = false;

	boolean multipleSelect;
	private SparseBooleanArray selectedItems;

	RecyclerView listFragment;

	private int placeholderCount;

	private DisplayMetrics outMetrics;

    private SortByHeaderViewModel sortByViewModel;

    /*public static view holder class*/
    public class ViewHolderExplorerLollipop extends RecyclerView.ViewHolder{
		public RelativeLayout itemLayout;
		public int currentPosition;
		public long document;


    	public ViewHolderExplorerLollipop(View itemView) {
			super(itemView);
		}
    }

	public class ViewHolderListExplorerLollipop extends ViewHolderExplorerLollipop{
		public ImageView imageView;
		public ImageView permissionsIcon;
        public TextView textViewFileName;
		public TextView textViewFileSize;


		public ViewHolderListExplorerLollipop(View itemView) {
			super(itemView);
		}
	}

	public class ViewHolderGridExplorerLollipop extends ViewHolderExplorerLollipop{
		public RelativeLayout folderLayout;
		public RelativeLayout thumbnailFolderLayout;
		public ImageView folderIcon;
		public TextView folderName;
		public RelativeLayout fileLayout;
        public RelativeLayout thumbnailFileLayout;
        public ImageView fileThumbnail;
        public ImageView fileSelectedIcon;
        public ImageView fileIcon;
        public TextView fileName;
        public RelativeLayout videoLayout;
        public TextView videoDuration;
        public ImageView videoIcon;


		public ViewHolderGridExplorerLollipop(View itemView) {
			super(itemView);
		}
	}

    public class ViewHolderSortBy extends ViewHolderExplorerLollipop {

        private final SortByHeaderBinding binding;

        private ViewHolderSortBy(SortByHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(SortByHeaderViewModel sortByHeaderViewModel) {
            binding.setSortByHeaderViewModel(sortByHeaderViewModel);
            binding.setOrderNameStringId(sortByHeaderViewModel.getOrderMap()
                    .get(fragment instanceof IncomingSharesExplorerFragmentLollipop
                            && parentHandle == INVALID_HANDLE
                            ? sortByHeaderViewModel.getOrder().getSecond()
                            : sortByHeaderViewModel.getOrder().getFirst()));
        }
    }
	
	ViewHolderExplorerLollipop holder = null;    

	public MegaExplorerLollipopAdapter(Context _context, Object fragment, ArrayList<MegaNode> _nodes,
                                       long _parentHandle, RecyclerView listView, boolean selectFile,
                                       SortByHeaderViewModel sortByHeaderViewModel){
		this.context = _context;
		this.nodes = _nodes;
		this.parentHandle = _parentHandle;
		this.listFragment = listView;
		this.selectFile = selectFile;
		this.imageIds = new ArrayList<Integer>();
		this.names = new ArrayList<String>();
		this.fragment = fragment;
		this.sortByViewModel = sortByHeaderViewModel;

		if (megaApi == null){
			megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
		}

		dbH = DatabaseHandler.getDbHandler(context);

        Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics ();
        display.getMetrics(outMetrics);

	}
	
	@Override
	public int getItemCount() {
		if (nodes == null){
			nodes = new ArrayList<MegaNode>();
		}

		return nodes.size();
	}

    @Override
    public int getItemViewType(int position) {
        return !nodes.isEmpty() && position == 0 ? ITEM_VIEW_TYPE_HEADER
                : ((FileExplorerActivityLollipop) context).isList()
                ? ITEM_VIEW_TYPE_LIST : ITEM_VIEW_TYPE_GRID;
    }

	public Object getItem(int position) {
		return nodes.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	@Override
	public ViewHolderExplorerLollipop onCreateViewHolder(ViewGroup parent, int viewType) {
	    View v;

	    if (viewType == ITEM_VIEW_TYPE_LIST) {
	        logDebug("onCreateViewHolder list");
			v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_explorer, parent, false);
			ViewHolderListExplorerLollipop holder = new ViewHolderListExplorerLollipop(v);

			holder.itemLayout = v.findViewById(R.id.file_explorer_item_layout);
			holder.imageView = v.findViewById(R.id.file_explorer_thumbnail);
			holder.textViewFileName = v.findViewById(R.id.file_explorer_filename);
            holder.textViewFileSize = v.findViewById(R.id.file_explorer_filesize);
			holder.permissionsIcon = v.findViewById(R.id.file_explorer_permissions);
            holder.textViewFileName.setOnClickListener(this);
            holder.textViewFileName.setTag(holder);
			v.setTag(holder);
			return holder;
		} else if (viewType == ITEM_VIEW_TYPE_GRID){
		    logDebug("onCreateViewHolder grid");
			v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_explorer_grid, parent, false);
			ViewHolderGridExplorerLollipop holder =  new ViewHolderGridExplorerLollipop(v);

			holder.itemLayout = v.findViewById(R.id.file_explorer_grid_layout);
            holder.folderLayout = v.findViewById(R.id.file_explorer_grid_folder_layout);
            holder.thumbnailFolderLayout = v.findViewById(R.id.file_explorer_grid_folder_thumbnail_layout);
            holder.folderIcon = v.findViewById(R.id.file_explorer_grid_folder_icon);
            holder.folderName= v.findViewById(R.id.file_explorer_grid_folder_filename);
            holder.fileLayout = v.findViewById(R.id.file_explorer_grid_file_layout);
            holder.thumbnailFileLayout = v.findViewById(R.id.file_explorer_grid_file_thumbnail_layout);
            holder.fileThumbnail = v.findViewById(R.id.file_explorer_grid_file_thumbnail);
            holder.fileSelectedIcon = v.findViewById(R.id.file_explorer_grid_file_selected);
            holder.fileIcon = v.findViewById(R.id.file_explorer_grid_file_icon);
            holder.fileName= v.findViewById(R.id.file_grid_filename_for_file);
            holder.videoLayout = v.findViewById(R.id.file_explorer_grid_file_videoinfo_layout);
            holder.videoDuration= v.findViewById(R.id.file_explorer_grid_file_title_video_duration);
            holder.videoIcon = v.findViewById(R.id.file_explorer_grid_file_video_icon);

			v.setTag(holder);
			return holder;
		} else {
            SortByHeaderBinding binding = SortByHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolderSortBy(binding);
        }
	}

	@Override
	public void onBindViewHolder(ViewHolderExplorerLollipop holder, int position){
	    switch (getItemViewType(position)) {
            case ITEM_VIEW_TYPE_HEADER:
                ((ViewHolderSortBy) holder).bind(sortByViewModel);
                break;

            case ITEM_VIEW_TYPE_LIST:
                ViewHolderListExplorerLollipop holderList = (ViewHolderListExplorerLollipop) holder;
                onBindViewHolderList(holderList, position);
                break;

            case ITEM_VIEW_TYPE_GRID:
                ViewHolderGridExplorerLollipop holderGrid = (ViewHolderGridExplorerLollipop) holder;
                onBindViewHolderGrid(holderGrid, position);
                break;
        }
	}

	private void setImageParams (ImageView image, int size, int marginSize) {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) image.getLayoutParams();
        params.width = params.height = dp2px(size);
        int margin = dp2px(marginSize);
        params.setMargins(margin, margin, margin, margin);
        image.setLayoutParams(params);
    }

    private void onBindViewHolderList(ViewHolderListExplorerLollipop holder, int position) {
        MegaNode node = (MegaNode) getItem(position);
        if (node == null) {
            return;
        }

        holder.currentPosition = position;

        holder.document = node.getHandle();

        holder.textViewFileName.setText(node.getName());

        holder.imageView.setAlpha(1.0f);
        holder.textViewFileName.setTextColor(ColorUtils.getThemeColor(context, android.R.attr.textColorPrimary));

        if (node.isFolder()){
            setImageParams(holder.imageView, ICON_SIZE_DP, ICON_MARGIN_DP);

            holder.itemView.setOnLongClickListener(null);
            holder.itemView.setOnClickListener(this);
            holder.permissionsIcon.setVisibility(View.GONE);
            holder.textViewFileSize.setText(getMegaNodeFolderInfo(node));
            holder.imageView.setImageResource(getFolderIcon(node, ManagerActivityLollipop.DrawerItem.CLOUD_DRIVE));

            if(node.isInShare()){
                if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
                    holder.textViewFileName.setMaxWidth(scaleWidthPx(260, outMetrics));
                    holder.textViewFileSize.setMaxWidth(scaleWidthPx(260, outMetrics));

                }
                else{
                    holder.textViewFileName.setMaxWidth(scaleWidthPx(200, outMetrics));
                    holder.textViewFileSize.setMaxWidth(scaleWidthPx(200, outMetrics));
                }
                ArrayList<MegaShare> sharesIncoming = megaApi.getInSharesList();
                for(int j=0; j<sharesIncoming.size();j++){
                    MegaShare mS = sharesIncoming.get(j);
                    if(mS.getNodeHandle()==node.getHandle()){
                        MegaUser user= megaApi.getContact(mS.getUser());
                        if(user!=null){
                            holder.textViewFileSize.setText(getMegaUserNameDB(user));
                        }
                        else{
                            holder.textViewFileSize.setText(mS.getUser());
                        }
                    }
                }

                //Check permissions
                holder.permissionsIcon.setVisibility(View.VISIBLE);
                int accessLevel = megaApi.getAccess(node);
                if(accessLevel== MegaShare.ACCESS_FULL){
                    holder.permissionsIcon.setImageResource(R.drawable.ic_shared_fullaccess);
                }
                else if(accessLevel== MegaShare.ACCESS_READ){
                    holder.permissionsIcon.setImageResource(R.drawable.ic_shared_read);
                }
                else{
                    holder.permissionsIcon.setImageResource(R.drawable.ic_shared_read_write);
                }
            }
        }
        else{
            holder.permissionsIcon.setVisibility(View.GONE);

            holder.textViewFileSize.setText(MegaNodeUtil.getFileInfo(node));
            holder.imageView.setImageResource(MimeTypeList.typeForName(node.getName()).getIconResourceId());
            setImageParams(holder.imageView, ICON_SIZE_DP, ICON_MARGIN_DP);

            if(selectFile){
                holder.itemView.setOnClickListener(this);
                holder.itemView.setOnLongClickListener(this);

                if (isMultipleSelect() && isItemChecked(position)) {
                    holder.imageView.setImageResource(R.drawable.ic_select_folder);
                    logDebug("Do not show thumb");
                    return;
                } else {
                    holder.imageView.setImageResource(MimeTypeList.typeForName(node.getName()).getIconResourceId());
                    holder.itemLayout.setBackground(null);
                }
            } else{
                holder.imageView.setAlpha(.4f);
                holder.textViewFileName.setTextColor(ColorUtils.getThemeColor(context, android.R.attr.textColorSecondary));
                holder.itemView.setOnClickListener(null);
                holder.itemView.setOnLongClickListener(null);
            }


            Bitmap thumb = ThumbnailUtilsLollipop.getThumbnailFromCache(node);
            if (thumb == null) {
                thumb = ThumbnailUtilsLollipop.getThumbnailFromFolder(node, context);
                if (thumb == null) {
                    try {
                        if (node.hasThumbnail()) {
                            thumb = ThumbnailUtilsLollipop.getThumbnailFromMegaExplorerLollipop(node, context, holder, megaApi, this);
                        }
                        else {
                            ThumbnailUtilsLollipop.createThumbnailExplorerLollipop(context, node, holder, megaApi, this);
                        }
                    } catch (Exception e) {}
                }
            }

            if (thumb != null) {
                setImageParams(holder.imageView, THUMB_SIZE_DP, THUMB_MARGIN_DP);
                holder.imageView.setImageBitmap(
                        ThumbnailUtilsLollipop.getRoundedBitmap(context, thumb, dp2px(THUMB_CORNER_RADIUS_DP)));
            }
        }
    }

    private void onBindViewHolderGrid(ViewHolderGridExplorerLollipop holder, int position) {
	    logDebug("onBindViewHolderGrid");
        Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics ();
        display.getMetrics(outMetrics);

        holder.currentPosition = position;

        MegaNode node = (MegaNode) getItem(position);

        if (node == null) {
            holder.fileLayout.setVisibility(View.GONE);
            holder.folderLayout.setVisibility(View.INVISIBLE);
            holder.itemLayout.setVisibility(View.INVISIBLE);
            return;
        }

        holder.document = node.getHandle();
        holder.itemLayout.setVisibility(View.VISIBLE);

        if (node.isFolder()){
            holder.folderLayout.setVisibility(View.VISIBLE);
            holder.fileLayout.setVisibility(View.GONE);
            holder.folderName.setText(node.getName());

            holder.itemView.setOnLongClickListener(null);

            holder.folderIcon.setImageResource(getFolderIcon(node, ManagerActivityLollipop.DrawerItem.CLOUD_DRIVE));
            holder.itemView.setOnClickListener(this);
            holder.folderName.setTextColor(ColorUtils.getThemeColor(context, android.R.attr.textColorPrimary));
        }
        else{
            holder.folderLayout.setVisibility(View.GONE);
            holder.fileLayout.setVisibility(View.VISIBLE);
            holder.fileName.setText(node.getName());
            holder.fileThumbnail.setVisibility(View.GONE);
            holder.fileIcon.setImageResource(MimeTypeThumbnail.typeForName(node.getName()).getIconResourceId());

            if (isVideoFile(node.getName())) {
                holder.videoLayout.setVisibility(View.VISIBLE);
                logDebug(node.getName() + " DURATION: " + node.getDuration());
                String duration = getVideoDuration(node.getDuration());
                if (duration != null && !duration.isEmpty())  {
                    holder.videoDuration.setText(duration);
                    holder.videoDuration.setVisibility(View.VISIBLE);
                }
                else {
                    holder.videoDuration.setVisibility(View.GONE);
                }
            }
            else {
                holder.videoLayout.setVisibility(View.GONE);
            }

            Bitmap thumb = ThumbnailUtilsLollipop.getThumbnailFromCache(node);
            if (thumb == null) {
                thumb = ThumbnailUtilsLollipop.getThumbnailFromFolder(node, context);
                if (thumb == null) {
                    try {
                        if (node.hasThumbnail()) {
                            thumb = ThumbnailUtilsLollipop.getThumbnailFromMegaExplorerLollipop(node, context, holder, megaApi, this);
                        }
                        else {
                            ThumbnailUtilsLollipop.createThumbnailExplorerLollipop(context, node, holder, megaApi, this);
                        }
                    } catch (Exception e) {}
                }
            }
            if (thumb != null) {
                holder.fileThumbnail.setImageBitmap(ThumbnailUtilsLollipop.getRoundedRectBitmap(context,thumb,2));
                holder.fileThumbnail.setVisibility(View.VISIBLE);
                holder.fileIcon.setVisibility(View.GONE);
            } else {
                holder.fileThumbnail.setVisibility(View.GONE);
                holder.fileIcon.setVisibility(View.VISIBLE);
            }

            if(selectFile){
                holder.fileThumbnail.setAlpha(1.0f);
                holder.fileName.setTextColor(ColorUtils.getThemeColor(context, android.R.attr.textColorPrimary));
                holder.itemView.setOnClickListener(this);
                holder.itemView.setOnLongClickListener(this);

                if (isMultipleSelect() && isItemChecked(position)) {
                    holder.itemLayout.setBackground(ContextCompat.getDrawable(context,R.drawable.background_item_grid_selected));
                    holder.fileSelectedIcon.setImageResource(R.drawable.ic_select_folder);

                } else {
                    holder.itemLayout.setBackground(ContextCompat.getDrawable(context,R.drawable.background_item_grid));
                    holder.fileSelectedIcon.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
                }
            } else{
                holder.fileThumbnail.setAlpha(.4f);
                holder.fileName.setTextColor(ColorUtils.getThemeColor(context, android.R.attr.textColorPrimary));
                holder.itemView.setOnClickListener(null);
                holder.itemView.setOnLongClickListener(null);
            }
        }
    }

    public void toggleSelection(int pos) {
        logDebug("toggleSelection: " + pos);
        startAnimation(pos, putOrDeletePostion(pos));
    }

    private boolean putOrDeletePostion(int pos) {
        if (selectedItems.get(pos,false)) {
            logDebug("delete pos: " + pos);
            selectedItems.delete(pos);
            return true;
        } else {
            logDebug("PUT pos: " + pos);
            selectedItems.put(pos,true);
            return false;
        }
    }

    private void hideMultipleSelect() {
        if (selectedItems.size() <= 0) {
            if (fragment instanceof CloudDriveExplorerFragmentLollipop) {
                ((CloudDriveExplorerFragmentLollipop) fragment).hideMultipleSelect();
            } else if (fragment instanceof IncomingSharesExplorerFragmentLollipop) {
                ((IncomingSharesExplorerFragmentLollipop) fragment).hideMultipleSelect();
            }
        }
    }

    private void startAnimation (final int pos, final boolean delete) {

        if (((FileExplorerActivityLollipop) context).isList()) {
            logDebug("adapter type is LIST");
            ViewHolderListExplorerLollipop view = (ViewHolderListExplorerLollipop) listFragment.findViewHolderForLayoutPosition(pos);
            if (view != null) {
                logDebug("Start animation: " + pos);
                Animation flipAnimation = AnimationUtils.loadAnimation(context,R.anim.multiselect_flip);
                flipAnimation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                        if (!delete) {
                            notifyItemChanged(pos);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        hideMultipleSelect();
                        if (delete) {
                            notifyItemChanged(pos);
                        }
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });
                view.imageView.startAnimation(flipAnimation);
            }
            else {
                logDebug("view is null - not animation");
                hideMultipleSelect();
                notifyItemChanged(pos);
            }
        } else {
            logDebug("adapter type is GRID");
            ViewHolderGridExplorerLollipop view = (ViewHolderGridExplorerLollipop) listFragment.findViewHolderForLayoutPosition(pos);
            if (view != null) {
                logDebug("Start animation: " + pos);
                Animation flipAnimation = AnimationUtils.loadAnimation(context,R.anim.multiselect_flip);
                if (!delete) {
                    notifyItemChanged(pos);
                    flipAnimation.setDuration(250);
                }
                flipAnimation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                        if (!delete) {
                            notifyItemChanged(pos);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        hideMultipleSelect();
                        notifyItemChanged(pos);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });
                view.fileSelectedIcon.startAnimation(flipAnimation);
            }
            else {
                logDebug("view is null - not animation");
                hideMultipleSelect();
                notifyItemChanged(pos);
            }
        }
    }

    public void selectAll() {
        for (int i = 0; i < nodes.size(); i++) {
            MegaNode node = nodes.get(i);
            if (node != null && !node.isFolder() && !isItemChecked(i)) {
                toggleSelection(i);
            }
        }
    }

	public void clearSelections() {
		logDebug("clearSelections");
		for (int i= 0; i<this.getItemCount();i++){
			if(isItemChecked(i)){
				toggleSelection(i);
			}
		}
	}

	private boolean isItemChecked(int position) {
	    if (selectedItems == null) {
	        return false;
        }
		return selectedItems.get(position);
	}

	public int getSelectedItemCount() {
		if(selectedItems!=null){
			return selectedItems.size();
		}
		return 0;
	}

	@Override
    public List<Integer> getSelectedItems() {

        if (selectedItems != null) {
            List<Integer> items = new ArrayList<Integer>(selectedItems.size());
            for (int i = 0; i < selectedItems.size(); i++) {
                items.add(selectedItems.keyAt(i));
            }
            return items;
        }

        return null;
    }

    @Override
    public int getFolderCount() {
        return getNumberOfFolders(nodes);
    }

    @Override
    public int getPlaceholderCount() {
        return placeholderCount;
    }

    @Override
    public int getUnhandledItem() {
        return -1;
    }

    /*
	 * Get list of all selected nodes
	 */
	public List<MegaNode> getSelectedNodes() {
	    if (selectedItems != null) {
            ArrayList<MegaNode> nodes = new ArrayList<MegaNode>();

            for (int i = 0; i < selectedItems.size(); i++) {
                if (selectedItems.valueAt(i) == true) {
                    MegaNode document = getNodeAt(selectedItems.keyAt(i));
                    if (document != null) {
                        nodes.add(document);
                    }
                }
            }
            return nodes;
        }

	    return null;
	}

	public long[] getSelectedHandles() {

		long handles[] = new long[selectedItems.size()];

		int k=0;
		for (int i = 0; i < selectedItems.size(); i++) {
			if (selectedItems.valueAt(i) == true) {
				MegaNode document = getNodeAt(selectedItems.keyAt(i));
				if (document != null){
					handles[k] = document.getHandle();
					k++;
				}
			}
		}
		return handles;
	}

	/*
 * Get document at specified position
 */
    private MegaNode getNodeAt(int position) {
		try {
			if (nodes != null) {
				return nodes.get(position);
			}
		} catch (IndexOutOfBoundsException e) {
		}
		return null;
	}

	public boolean isMultipleSelect() {
		return multipleSelect;
	}

	public void setMultipleSelect(boolean multipleSelect) {
		logDebug("multipleSelect: " + multipleSelect);
		if (this.multipleSelect != multipleSelect) {
			this.multipleSelect = multipleSelect;
		}
		if(this.multipleSelect){
			selectedItems = new SparseBooleanArray();
		}
	}

	public void setNodes(ArrayList<MegaNode> nodes){
		this.nodes = insertPlaceHolderNode(nodes);
		notifyDataSetChanged();
		visibilityFastScroller();
	}

	public long getParentHandle(){
		return parentHandle;
	}

	public void setParentHandle(long parentHandle){
		this.parentHandle = parentHandle;
	}

	public boolean isSelectFile() {
		return selectFile;
	}

	public void setSelectFile(boolean selectFile) {
		this.selectFile = selectFile;
	}

	@Override
	public void onClick(View v) {
		clickItem(v);
	}

	@Override
	public boolean onLongClick(View v) {
		clickItem(v);
		return true;
	}

    private void clickItem(View v) {
		ViewHolderExplorerLollipop holder = (ViewHolderExplorerLollipop) v.getTag();
		if (holder == null) {
			return;
		}

		if (fragment instanceof CloudDriveExplorerFragmentLollipop)  {
			((CloudDriveExplorerFragmentLollipop) fragment).itemClick(v, holder.getAdapterPosition());
		}
		else if (fragment instanceof IncomingSharesExplorerFragmentLollipop) {
			((IncomingSharesExplorerFragmentLollipop) fragment).itemClick(v, holder.getAdapterPosition());
		}
	}

    private ArrayList<MegaNode> insertPlaceHolderNode(ArrayList<MegaNode> nodes) {
	    if (((FileExplorerActivityLollipop) context).isList()) {
            if (!nodes.isEmpty()) {
                placeholderCount = 1;
                nodes.add(0, null);
            } else {
                placeholderCount = 0;
            }

	        return nodes;
        }

        int folderCount = getNumberOfFolders(nodes);
        int spanCount = 2;

        if (listFragment instanceof NewGridRecyclerView) {
            spanCount = ((NewGridRecyclerView)listFragment).getSpanCount();
        }

        placeholderCount = (folderCount % spanCount) == 0 ? 0 : spanCount - (folderCount % spanCount);

        if (folderCount > 0 && placeholderCount != 0 && !((FileExplorerActivityLollipop) context).isList()) {
            //Add placeholder at folders' end.
            for (int i = 0;i < placeholderCount;i++) {
                try {
                    nodes.add(folderCount + i,null);
                } catch (IndexOutOfBoundsException e) {
                    logError("Inserting placeholders [nodes.size]: " + nodes.size() + " [folderCount+i]: " + (folderCount + i), e);
                }
            }
        }

        if (!nodes.isEmpty()) {
            placeholderCount++;
            nodes.add(0, null);
        }

        return nodes;
    }

    private void visibilityFastScroller() {
	    int visibility;
        if (getItemCount() < MIN_ITEMS_SCROLLBAR) {
            visibility = View.GONE;
        }
        else {
            visibility = View.VISIBLE;
        }

        if (fragment instanceof IncomingSharesExplorerFragmentLollipop) {
            ((IncomingSharesExplorerFragmentLollipop) fragment).getFastScroller().setVisibility(visibility);
        }
        else if (fragment instanceof CloudDriveExplorerFragmentLollipop) {
            ((CloudDriveExplorerFragmentLollipop) fragment).getFastScroller().setVisibility(visibility);
        }
    }

    @Override
    public String getSectionTitle(int position) {
	    MegaNode node = (MegaNode) getItem(position);

        if (node != null && node.getName() != null && !node.getName().isEmpty()) {
            return node.getName().substring(0,1);
        }
        return null;
    }

    public void setListFragment(RecyclerView listFragment) {
        this.listFragment = listFragment;
    }

    @NotNull
    public final GridLayoutManager.SpanSizeLookup getSpanSizeLookup(final int spanCount) {
        return (GridLayoutManager.SpanSizeLookup) (new GridLayoutManager.SpanSizeLookup() {
            public int getSpanSize(int position) {
                return getItemViewType(position) == ITEM_VIEW_TYPE_HEADER ? spanCount : 1;
            }
        });
    }
}
