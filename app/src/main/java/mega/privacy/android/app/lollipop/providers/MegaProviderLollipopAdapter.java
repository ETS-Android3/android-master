package mega.privacy.android.app.lollipop.providers;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.MimeTypeList;
import mega.privacy.android.app.R;
import mega.privacy.android.app.utils.ThumbnailUtilsLollipop;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaNode;
import nz.mega.sdk.MegaShare;
import nz.mega.sdk.MegaUser;

import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.MegaApiUtils.*;
import static mega.privacy.android.app.utils.MegaNodeUtil.getFileInfo;
import static mega.privacy.android.app.utils.ThumbnailUtilsLollipop.getRoundedBitmap;
import static mega.privacy.android.app.utils.Util.*;
import static mega.privacy.android.app.utils.ContactUtil.*;

public class MegaProviderLollipopAdapter extends RecyclerView.Adapter<MegaProviderLollipopAdapter.ViewHolderLollipopProvider> implements OnClickListener, View.OnLongClickListener{
	
	final public static int CLOUD_EXPLORER = 0;
	final public static int INCOMING_SHARES_EXPLORER = 1;

	Context context;
	MegaApiAndroid megaApi;

	int positionClicked;
	ArrayList<Integer> imageIds;
	ArrayList<String> names;
	ArrayList<MegaNode> nodes;

	DatabaseHandler dbH = null;
	
	long parentHandle = -1;
	
	Object fragment;
	RecyclerView listFragment;
	ImageView emptyImageViewFragment;

	boolean multipleSelect;
	private SparseBooleanArray selectedItems;
	int type;


	/*public static view holder class*/
    public class ViewHolderLollipopProvider extends RecyclerView.ViewHolder{
    	public ViewHolderLollipopProvider(View v) {
			super(v);
		}
		public ImageView imageView;
		public ImageView permissionsIcon;
    	public TextView textViewFileName;
    	public TextView textViewFileSize;
    	public RelativeLayout itemLayout;
    	public int currentPosition;
    	public long document;
    }
	
	public MegaProviderLollipopAdapter(Context _context, Object fragment, ArrayList<MegaNode> _nodes, long _parentHandle, RecyclerView listView, ImageView emptyImageView, int type){
		this.context = _context;
		this.nodes = _nodes;
		this.parentHandle = _parentHandle;
		this.listFragment = listView;
		this.emptyImageViewFragment = emptyImageView;
		this.fragment = fragment;
		this.positionClicked = -1;
		this.imageIds = new ArrayList<Integer>();
		this.names = new ArrayList<String>();
		this.type = type;
		
		if (megaApi == null){
			megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
		}

		dbH = DatabaseHandler.getDbHandler(context);
	}
	
	ViewHolderLollipopProvider holder = null;
	
	@Override
	public int getItemCount() {
		return nodes.size();
	}

	public Object getItem(int position) {
		return nodes.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public ViewHolderLollipopProvider onCreateViewHolder(ViewGroup parent, int viewType) {
		logDebug("onCreateViewHolder");
		
		Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
		DisplayMetrics outMetrics = new DisplayMetrics ();
	    display.getMetrics(outMetrics);


		View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_explorer, parent, false);

		holder = new ViewHolderLollipopProvider(v);
		holder.itemLayout = (RelativeLayout) v.findViewById(R.id.file_explorer_item_layout);
		holder.itemLayout.setOnClickListener(this);
		holder.itemLayout.setOnLongClickListener(this);
		holder.imageView = (ImageView) v.findViewById(R.id.file_explorer_thumbnail);
		holder.textViewFileName = (TextView) v.findViewById(R.id.file_explorer_filename);
		holder.textViewFileName.setOnClickListener(this);
		holder.textViewFileName.setOnLongClickListener(this);
		holder.textViewFileName.setTag(holder);

		holder.textViewFileSize = (TextView) v.findViewById(R.id.file_explorer_filesize);
		holder.permissionsIcon = (ImageView) v.findViewById(R.id.file_explorer_permissions);

		if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
			holder.textViewFileSize.setMaxWidth(scaleWidthPx(260, outMetrics));
		}else{
			holder.textViewFileSize.setMaxWidth(scaleWidthPx(200, outMetrics));
		}
			
		v.setTag(holder);

		return holder;
	}	
	
	@Override
	public void onBindViewHolder(ViewHolderLollipopProvider holder, int position) {
		logDebug("onBindViewHolder");
		
		holder.currentPosition = position;
		
		MegaNode node = (MegaNode) getItem(position);
		holder.document = node.getHandle();
		holder.textViewFileName.setText(node.getName());
		
		setViewAlpha(holder.imageView, 1);

		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.imageView.getLayoutParams();

		if (node.isFolder()) {
			params.height = params.width = dp2px(ICON_SIZE_DP);
			int margin = dp2px(ICON_MARGIN_DP);
			params.setMargins(margin, margin, margin, margin);

			holder.imageView.setImageResource(R.drawable.ic_folder_list);
			holder.textViewFileSize.setText(getMegaNodeFolderInfo(node));

			if (node.isInShare()) {
				ArrayList<MegaShare> sharesIncoming = megaApi.getInSharesList();
				for (int j = 0; j < sharesIncoming.size(); j++) {
					MegaShare mS = sharesIncoming.get(j);
					if (mS.getNodeHandle() == node.getHandle()) {
						MegaUser user = megaApi.getContact(mS.getUser());
						if (user != null) {
							holder.textViewFileSize.setText(getMegaUserNameDB(user));
						} else {
							holder.textViewFileSize.setText(mS.getUser());
						}
					}
				}

				holder.permissionsIcon.setVisibility(View.VISIBLE);
				int accessLevel = megaApi.getAccess(node);

				if (accessLevel == MegaShare.ACCESS_FULL) {
					holder.permissionsIcon.setImageResource(R.drawable.ic_shared_fullaccess);
				} else if (accessLevel == MegaShare.ACCESS_READ) {
					holder.permissionsIcon.setImageResource(R.drawable.ic_shared_read);
				} else {
					holder.permissionsIcon.setImageResource(R.drawable.ic_shared_read_write);
				}
			} else {
				holder.permissionsIcon.setVisibility(View.GONE);
				holder.textViewFileSize.setText(getMegaNodeFolderInfo(node));
			}

			if (isMultipleSelect() && isItemChecked(position)) {
				holder.imageView.setImageResource(R.drawable.ic_select_folder);
			} else {
				holder.imageView.setImageResource(node.isInShare()
						? R.drawable.ic_folder_incoming_list
						: R.drawable.ic_folder_list);
			}
		} else {
			holder.permissionsIcon.setVisibility(View.GONE);

			holder.textViewFileSize.setText(getFileInfo(node));

			if (isMultipleSelect() && isItemChecked(position)) {
				params.height = params.width = dp2px(ICON_SIZE_DP);
				params.setMargins(dp2px(ICON_MARGIN_DP), 0, 0, 0);
				holder.imageView.setImageResource(R.drawable.ic_select_folder);
			} else {
				Bitmap thumb = ThumbnailUtilsLollipop.getThumbnailFromCache(node);

				if (thumb == null) {
					thumb = ThumbnailUtilsLollipop.getThumbnailFromFolder(node, context);

					if (thumb == null) {
						try {
							thumb = ThumbnailUtilsLollipop.getThumbnailFromMegaProvider(node, context, holder, megaApi, this);
						} catch (Exception e) {
							logWarning("Exception getting thumbnail.", e);
						} //Too many AsyncTasks
					}
				}
				int margin;

				if (thumb != null) {
					params.height = params.width = dp2px(THUMB_SIZE_DP);
					margin = dp2px(THUMB_MARGIN_DP);
					holder.imageView.setImageBitmap(getRoundedBitmap(context, thumb, dp2px(THUMB_CORNER_RADIUS_DP)));
				} else {
					params.height = params.width = dp2px(ICON_SIZE_DP);
					margin = dp2px(ICON_MARGIN_DP);
					holder.imageView.setImageResource(MimeTypeList.typeForName(node.getName()).getIconResourceId());
				}

				params.setMargins(margin, margin, margin, margin);
			}
		}

		holder.imageView.setLayoutParams(params);
	}

	@Override
	public void onClick(View v) {
		logDebug("onClick");
		ViewHolderLollipopProvider holder = (ViewHolderLollipopProvider) v.getTag();
		
		int currentPosition = holder.currentPosition;
		
		switch (v.getId()){
			case R.id.file_explorer_filename:
			case R.id.file_explorer_item_layout:{
				if(fragment instanceof CloudDriveProviderFragmentLollipop){
					((CloudDriveProviderFragmentLollipop)fragment).itemClick(currentPosition);	
				}
				else if (fragment instanceof IncomingSharesProviderFragmentLollipop){
					((IncomingSharesProviderFragmentLollipop)fragment).itemClick(currentPosition);	
				}											
				break;
			}
		}		
	}

	public int getPositionClicked (){
    	return positionClicked;
    }
    
    public void setPositionClicked(int p){
    	positionClicked = p;
    }
	
	public void setNodes(ArrayList<MegaNode> nodes){
		this.nodes = nodes;
		positionClicked = -1;	
		notifyDataSetChanged();
	}
	
	public long getParentHandle(){
		return parentHandle;
	}
	
	public void setParentHandle(long parentHandle){
		this.parentHandle = parentHandle;
	}

	public boolean isMultipleSelect (){
		return multipleSelect;
	}

	public void setMultipleSelect (boolean multipleSelect){
		logDebug("multipleSelect: " + multipleSelect);
		if (this.multipleSelect != multipleSelect) {
			this.multipleSelect = multipleSelect;
		}
		if(this.multipleSelect){
			selectedItems = new SparseBooleanArray();
		}
	}

	public void toggleAllSelection (int pos){
		logDebug("pos: " + pos);
		final int positionToflip = pos;

		if (selectedItems.get(pos, false)) {
			logDebug("Delete pos: " + pos);
			selectedItems.delete(pos);
		}
		else {
			logDebug("PUT pos: " + pos);
			selectedItems.put(pos, true);
		}

		MegaProviderLollipopAdapter.ViewHolderLollipopProvider view = (MegaProviderLollipopAdapter.ViewHolderLollipopProvider) listFragment.findViewHolderForLayoutPosition(pos);
		if(view != null){
			logDebug("Start animation: " + pos + " multiselection state: " + isMultipleSelect());
			Animation flipAnimation = AnimationUtils.loadAnimation(context, R.anim.multiselect_flip);
			flipAnimation.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {

				}

				@Override
				public void onAnimationEnd(Animation animation) {
					if(selectedItems.size() <= 0){
						if (type == INCOMING_SHARES_PROVIDER_ADAPTER){
							((IncomingSharesProviderFragmentLollipop) fragment).hideMultipleSelect();
						}
						else {
							((CloudDriveProviderFragmentLollipop) fragment).hideMultipleSelect();
						}
					}
					logDebug("Notified item changed");
					notifyItemChanged(positionToflip);
				}

				@Override
				public void onAnimationRepeat(Animation animation) {

				}
			});
			view.imageView.startAnimation(flipAnimation);
		}
		else {
			logWarning("NULL view pos: " + positionToflip);
			notifyItemChanged(pos);
		}
	}

	public void toggleSelection(int position) {
		logDebug("position: " + position);

		if(selectedItems.get(position, false)){
			logDebug("Delete pos: " + position);
			selectedItems.delete(position);
		}
		else{
			logDebug("PUT pos: " + position);
			selectedItems.put(position, true);
		}
		notifyItemChanged(position);

		MegaProviderLollipopAdapter.ViewHolderLollipopProvider view = (MegaProviderLollipopAdapter.ViewHolderLollipopProvider) listFragment.findViewHolderForLayoutPosition(position);
		if (view != null){
			logDebug("Start animation: " + position);
			Animation flipAnimation = AnimationUtils.loadAnimation(context, R.anim.multiselect_flip);
			flipAnimation.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {

				}

				@Override
				public void onAnimationEnd(Animation animation) {
					if(selectedItems.size() <= 0){
						if (type == INCOMING_SHARES_PROVIDER_ADAPTER){
							((IncomingSharesProviderFragmentLollipop) fragment).hideMultipleSelect();
						}
						else {
							((CloudDriveProviderFragmentLollipop) fragment).hideMultipleSelect();
						}
					}
				}

				@Override
				public void onAnimationRepeat(Animation animation) {

				}
			});

			view.imageView.startAnimation(flipAnimation);
		}
		else{
			logWarning("View is null - not animation");
		}
	}

	public List<MegaNode> getSelectedNodes(){
		ArrayList<MegaNode> nodes = new ArrayList<>();

		for(int i=0; i<selectedItems.size(); i++){
			if(selectedItems.valueAt(i) == true){
				MegaNode document = getNodeAt (selectedItems.keyAt(i));
				if(document != null){
					nodes.add(document);
				}
			}
		}

		return nodes;
	}

	public int getSelectedItemCount(){
		return selectedItems.size();
	}

	public List<Integer> getSelectedItems(){
		List<Integer> items = new ArrayList<>(selectedItems.size());
		for(int i=0; i<selectedItems.size(); i++){
			items.add(selectedItems.keyAt(i));
		}

		return items;
	}

	public MegaNode getNodeAt(int position) {
		try {
			if (nodes != null) {
				return nodes.get(position);
			}
		} catch (IndexOutOfBoundsException e) {
		}
		return null;
	}

	public void selectAll(){
		for(int i=0; i<this.getItemCount(); i++){
			if(!isItemChecked(i)){
				toggleAllSelection(i);
			}
		}
	}

	public void clearSelections() {
		logDebug("clearSelections");
		for (int i= 0; i<this.getItemCount();i++){
			if(isItemChecked(i)){
				toggleAllSelection(i);
			}
		}
	}

	private boolean isItemChecked(int position) {
		return selectedItems.get(position);
	}

	@Override
	public boolean onLongClick(View view) {
		logDebug("OnLongClick");

		ViewHolderLollipopProvider holder = (ViewHolderLollipopProvider) view.getTag();
		int currentPosition = holder.getAdapterPosition();

		if(type == INCOMING_SHARES_PROVIDER_ADAPTER){
			((IncomingSharesProviderFragmentLollipop) fragment).activateActionMode();
			((IncomingSharesProviderFragmentLollipop) fragment).itemClick(currentPosition);
		}
		else {
			((CloudDriveProviderFragmentLollipop) fragment).activateActionMode();
			((CloudDriveProviderFragmentLollipop) fragment).itemClick(currentPosition);
		}

		return true;
	}
}
