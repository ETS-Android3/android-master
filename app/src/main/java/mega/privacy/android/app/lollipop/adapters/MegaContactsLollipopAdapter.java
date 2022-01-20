package mega.privacy.android.app.lollipop.adapters;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.MegaContactAdapter;
import mega.privacy.android.app.R;
import mega.privacy.android.app.components.MarqueeTextView;
import mega.privacy.android.app.components.RoundedImageView;
import mega.privacy.android.app.components.scrollBar.SectionTitleProvider;
import mega.privacy.android.app.components.twemoji.EmojiTextView;
import mega.privacy.android.app.lollipop.AddContactActivityLollipop;
import mega.privacy.android.app.lollipop.ManagerActivityLollipop;
import mega.privacy.android.app.lollipop.listeners.UserAvatarListener;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaApiJava;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaUser;

import static mega.privacy.android.app.utils.AvatarUtil.getColorAvatar;
import static mega.privacy.android.app.utils.AvatarUtil.getDefaultAvatar;
import static mega.privacy.android.app.utils.AvatarUtil.getUserAvatar;
import static mega.privacy.android.app.utils.CacheFolderManager.buildAvatarFile;
import static mega.privacy.android.app.utils.ChatUtil.StatusIconLocation;
import static mega.privacy.android.app.utils.ChatUtil.setContactLastGreen;
import static mega.privacy.android.app.utils.ChatUtil.setContactStatus;
import static mega.privacy.android.app.utils.Constants.AVATAR_SIZE;
import static mega.privacy.android.app.utils.Constants.AVATAR_SIZE_GRID;
import static mega.privacy.android.app.utils.Constants.MAX_WIDTH_CONTACT_NAME_GRID_LAND;
import static mega.privacy.android.app.utils.Constants.MAX_WIDTH_CONTACT_NAME_GRID_PORT;
import static mega.privacy.android.app.utils.Constants.MAX_WIDTH_CONTACT_NAME_LAND;
import static mega.privacy.android.app.utils.Constants.MAX_WIDTH_CONTACT_NAME_PORT;
import static mega.privacy.android.app.utils.FileUtil.JPG_EXTENSION;
import static mega.privacy.android.app.utils.FileUtil.isFileAvailable;
import static mega.privacy.android.app.utils.LogUtil.logDebug;
import static mega.privacy.android.app.utils.LogUtil.logError;
import static mega.privacy.android.app.utils.ThumbnailUtilsLollipop.getRoundedRectBitmap;
import static mega.privacy.android.app.utils.Util.dp2px;
import static mega.privacy.android.app.utils.Util.isScreenInPortrait;
import static mega.privacy.android.app.utils.Util.scaleWidthPx;


public class MegaContactsLollipopAdapter extends RecyclerView.Adapter<MegaContactsLollipopAdapter.ViewHolderContacts> implements OnClickListener, View.OnLongClickListener, SectionTitleProvider {
	
	public static final int ITEM_VIEW_TYPE_LIST_ADD_CONTACT = 0;
	public static final int ITEM_VIEW_TYPE_LIST_GROUP_CHAT = 1;
	private Context context;
	private int positionClicked;
	private ArrayList<MegaContactAdapter> contacts;
	private RecyclerView listFragment;
	private MegaApiAndroid megaApi;
	private MegaChatApiAndroid megaChatApi;
	private boolean multipleSelect;
	private DatabaseHandler dbH = null;
	private SparseBooleanArray selectedItems;
	private int adapterType;

	DisplayMetrics outMetrics;

	public MegaContactsLollipopAdapter(Context _context, ArrayList<MegaContactAdapter> _contacts, RecyclerView _listView, int adapterType) {
		this.context = _context;
		this.contacts = _contacts;
		this.positionClicked = -1;
		this.adapterType = adapterType;
		
		if (megaApi == null){
			megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
		}

		if (megaChatApi == null) {
			megaChatApi = ((MegaApplication) ((Activity) context).getApplication()).getMegaChatApi();
		}
		
		listFragment = _listView;
	}

	@Override
	public String getSectionTitle(int position) {
		return contacts.get(position).getFullName().substring(0, 1).toUpperCase();
	}

	/*private view holder class*/
    public static class ViewHolderContacts extends RecyclerView.ViewHolder{
    	public ViewHolderContacts(View v) {
			super(v);
		}

        EmojiTextView textViewContactName;
        MarqueeTextView textViewContent;
        RelativeLayout itemLayout;
        public String contactMail;
		ImageView verifiedIcon;
    }
    
    public class ViewHolderContactsList extends ViewHolderContacts{
    	public ViewHolderContactsList(View v) {
			super(v);
		}
    	public RoundedImageView imageView;
		ImageView contactStateIcon;
		RelativeLayout threeDotsLayout;
		RelativeLayout declineLayout;
    }
    
    public class ViewHolderContactsGrid extends ViewHolderContacts{
    	public ViewHolderContactsGrid(View v) {
			super(v);
		}
		public ImageView imageView;
    	LinearLayout contactNameLayout;
		ImageView contactStateIcon;
		ImageView contactSelectedIcon;
		ImageButton imageButtonThreeDots;
    }
    
	ViewHolderContactsList holderList = null;
	ViewHolderContactsGrid holderGrid = null;

	@Override
	public ViewHolderContacts onCreateViewHolder(ViewGroup parent, int viewType) {
		logDebug("onCreateViewHolder");
		
		Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
		outMetrics = new DisplayMetrics ();
	    display.getMetrics(outMetrics);

	    dbH = DatabaseHandler.getDbHandler(context);
	    if (viewType == MegaContactsLollipopAdapter.ITEM_VIEW_TYPE_LIST_ADD_CONTACT){
			View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact_list, parent, false);

			holderList = new ViewHolderContactsList(v);
			holderList.itemLayout = v.findViewById(R.id.contact_list_item_layout);
			holderList.imageView = v.findViewById(R.id.contact_list_thumbnail);
			holderList.verifiedIcon = v.findViewById(R.id.verified_icon);
			holderList.textViewContactName = v.findViewById(R.id.contact_list_name);
			holderList.textViewContent = v.findViewById(R.id.contact_list_content);
			holderList.declineLayout = v.findViewById(R.id.contact_list_decline);
			holderList.contactStateIcon = v.findViewById(R.id.contact_list_drawable_state);
			holderList.declineLayout.setVisibility(View.GONE);

			if(!isScreenInPortrait(context)){
				holderList.textViewContactName.setMaxWidthEmojis(dp2px(MAX_WIDTH_CONTACT_NAME_LAND, outMetrics));
				holderList.textViewContent.setMaxWidth(dp2px(MAX_WIDTH_CONTACT_NAME_LAND, outMetrics));
			}
			else{
				holderList.textViewContactName.setMaxWidthEmojis(dp2px(MAX_WIDTH_CONTACT_NAME_PORT, outMetrics));
				holderList.textViewContent.setMaxWidth(dp2px(MAX_WIDTH_CONTACT_NAME_PORT, outMetrics));
			}

			holderList.threeDotsLayout = v.findViewById(R.id.contact_list_three_dots_layout);

			//Right margin
			RelativeLayout.LayoutParams actionButtonParams = (RelativeLayout.LayoutParams)holderList.threeDotsLayout.getLayoutParams();
			actionButtonParams.setMargins(0, 0, scaleWidthPx(10, outMetrics), 0);
			holderList.threeDotsLayout.setLayoutParams(actionButtonParams);

			holderList.itemLayout.setTag(holderList);
			holderList.itemLayout.setOnClickListener(this);
			holderList.threeDotsLayout.setVisibility(View.GONE);

			v.setTag(holderList);

			return holderList;
		}
		else if (viewType == MegaContactsLollipopAdapter.ITEM_VIEW_TYPE_LIST_GROUP_CHAT) {
			View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact_list, parent, false);

			holderList = new ViewHolderContactsList(v);
			holderList.itemLayout = v.findViewById(R.id.contact_list_item_layout);
			holderList.imageView = v.findViewById(R.id.contact_list_thumbnail);
			holderList.verifiedIcon = v.findViewById(R.id.verified_icon);
			holderList.textViewContactName = v.findViewById(R.id.contact_list_name);
			holderList.textViewContent = v.findViewById(R.id.contact_list_content);
			holderList.contactStateIcon = v.findViewById(R.id.contact_list_drawable_state);
			holderList.declineLayout = v.findViewById(R.id.contact_list_decline);
			holderList.declineLayout.setVisibility(View.VISIBLE);

			if(!isScreenInPortrait(context)){
				holderList.textViewContactName.setMaxWidthEmojis(dp2px(MAX_WIDTH_CONTACT_NAME_LAND, outMetrics));
				holderList.textViewContent.setMaxWidth(dp2px(MAX_WIDTH_CONTACT_NAME_LAND, outMetrics));
			}
			else{
				holderList.textViewContactName.setMaxWidthEmojis(dp2px(MAX_WIDTH_CONTACT_NAME_PORT, outMetrics));
				holderList.textViewContent.setMaxWidth(dp2px(MAX_WIDTH_CONTACT_NAME_PORT, outMetrics));
			}

			holderList.threeDotsLayout = v.findViewById(R.id.contact_list_three_dots_layout);
			holderList.declineLayout.setTag(holderList);
			holderList.declineLayout.setOnClickListener(this);

			holderList.threeDotsLayout.setVisibility(View.GONE);

			v.setTag(holderList);

			return holderList;
		}
	    else{
	    	return null;
	    }
	}
	
	@Override
	public void onBindViewHolder(ViewHolderContacts holder, int position) {
		logDebug("Position: " + position);

		if (adapterType == MegaContactsLollipopAdapter.ITEM_VIEW_TYPE_LIST_ADD_CONTACT){
			ViewHolderContactsList holderList = (ViewHolderContactsList) holder;
			onBindViewHolderListAddContact(holderList, position);
		}
		else if (adapterType == MegaContactsLollipopAdapter.ITEM_VIEW_TYPE_LIST_GROUP_CHAT) {
			ViewHolderContactsList holderList = (ViewHolderContactsList) holder;
			onBindViewHolderListGroupChat(holderList, position);
		}
	}
	
	public void onBindViewHolderGrid (ViewHolderContactsGrid holder, int position){
		holder.imageView.setImageBitmap(null);
		MegaContactAdapter contact = (MegaContactAdapter) getItem(position);
		holder.verifiedIcon.setVisibility(!isItemChecked(position) && megaApi.areCredentialsVerified(contact.getMegaUser()) ? View.VISIBLE : View.GONE);
		holder.contactMail = contact.getMegaUser().getEmail();

		holder.contactStateIcon.setVisibility(View.VISIBLE);
		setContactStatus(megaChatApi.getUserOnlineStatus(contact.getMegaUser().getHandle()), holder.contactStateIcon, StatusIconLocation.STANDARD);
		
		if (multipleSelect && isItemChecked(position)) {
				holder.itemLayout.setBackgroundResource(R.drawable.background_item_grid_selected);
				holder.contactSelectedIcon.setImageResource(R.drawable.ic_chat_avatar_select);
				holder.contactSelectedIcon.setVisibility(View.VISIBLE);
		} else {
			holder.itemLayout.setBackgroundResource(R.drawable.background_item_grid);
			holder.contactSelectedIcon.setVisibility(View.INVISIBLE);
		}

		holder.textViewContactName.setText(contact.getFullName());

		createDefaultAvatar(holder, contact);

		UserAvatarListener listener = new UserAvatarListener(context, holder);

        File avatar = buildAvatarFile(context,holder.contactMail + ".jpg");
        Bitmap bitmap = null;
        if (isFileAvailable(avatar)) {
			if (avatar.length() > 0){
				BitmapFactory.Options bOpts = new BitmapFactory.Options();
				bOpts.inPurgeable = true;
				bOpts.inInputShareable = true;
				bitmap = BitmapFactory.decodeFile(avatar.getAbsolutePath(), bOpts);
				bitmap = getRoundedRectBitmap(context, bitmap, 3);
				if (bitmap == null) {
					avatar.delete();
                    megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
                }
				else{
					logDebug("Do not ask for user avatar - its in cache: " + avatar.getAbsolutePath());
					holder.imageView.setImageBitmap(bitmap);
				}
			}
			else{
                megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
			}
		}
		else{
            megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
		}

		holder.imageButtonThreeDots.setTag(holder);
		holder.imageButtonThreeDots.setOnClickListener(this);	
	}

	public void onBindViewHolderListAddContact(ViewHolderContactsList holder, int position){
		logDebug("Position: " + position);

		holder.imageView.setImageBitmap(null);

		MegaContactAdapter contact = (MegaContactAdapter) getItem(position);
		holder.verifiedIcon.setVisibility(!isItemChecked(position) && megaApi.areCredentialsVerified(contact.getMegaUser()) ? View.VISIBLE : View.GONE);
		holder.contactMail = contact.getMegaUser().getEmail();
		logDebug("Contact: " + contact.getMegaUser().getEmail() + ", Handle: " + contact.getMegaUser().getHandle());

		holder.contactStateIcon.setVisibility(View.VISIBLE);
		int userStatus = megaChatApi.getUserOnlineStatus(contact.getMegaUser().getHandle());
		setContactStatus(userStatus, holder.contactStateIcon, holder.textViewContent, StatusIconLocation.STANDARD);
		setContactLastGreen(context, userStatus, contact.getLastGreen(), holder.textViewContent);

		holder.textViewContactName.setText(contact.getFullName());

		if (contact.isSelected()) {
			holder.imageView.setImageResource(R.drawable.ic_chat_avatar_select);
		} else {

			Bitmap bitmap = getUserAvatar(MegaApiJava.userHandleToBase64(contact.getMegaUser().getHandle()), contact.getMegaUser().getEmail());
			if (bitmap != null) {
				holder.imageView.setImageBitmap(bitmap);
			} else {
				createDefaultAvatar(holder, contact);
				megaApi.getUserAvatar(contact.getMegaUser(), buildAvatarFile(context, contact.getMegaUser().getEmail() + JPG_EXTENSION).getAbsolutePath(), new UserAvatarListener(context, holder));
			}
		}
	}

	public void onBindViewHolderListGroupChat(ViewHolderContactsList holder, int position){
		logDebug("Position: " + position);

		holder.imageView.setImageBitmap(null);
		MegaContactAdapter contact = (MegaContactAdapter) getItem(position);
		holder.verifiedIcon.setVisibility(!isItemChecked(position) && megaApi.areCredentialsVerified(contact.getMegaUser()) ? View.VISIBLE : View.GONE);
		holder.contactMail = contact.getMegaUser().getEmail();
		if (holder.contactMail.equals(megaApi.getMyEmail())) {
			holder.declineLayout.setVisibility(View.GONE);
		}
		else {
			holder.declineLayout.setVisibility(View.VISIBLE);
		}
		logDebug("Contact: " + contact.getMegaUser().getEmail() + ", Handle: " + contact.getMegaUser().getHandle());

		holder.contactStateIcon.setVisibility(View.VISIBLE);
		int userStatus = megaChatApi.getUserOnlineStatus(contact.getMegaUser().getHandle());
		setContactStatus(userStatus, holder.contactStateIcon, holder.textViewContent, StatusIconLocation.STANDARD);
		setContactLastGreen(context, userStatus, contact.getLastGreen(), holder.textViewContent);

		holder.textViewContactName.setText(contact.getFullName());

		createDefaultAvatar(holder, contact);

		UserAvatarListener listener = new UserAvatarListener(context, holder);

		File avatar = buildAvatarFile(context, holder.contactMail + ".jpg");
		Bitmap bitmap = null;
		if (isFileAvailable(avatar)){
			if (avatar.length() > 0){
				BitmapFactory.Options bOpts = new BitmapFactory.Options();
				bOpts.inPurgeable = true;
				bOpts.inInputShareable = true;
				bitmap = BitmapFactory.decodeFile(avatar.getAbsolutePath(), bOpts);
				if (bitmap == null) {
					avatar.delete();
                    megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
				}
				else{
					holder.imageView.setImageBitmap(bitmap);
				}
			}
			else{
                megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
			}
		}
		else{
            megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
		}
	}
	
	public void onBindViewHolderList(ViewHolderContactsList holder, int position){
		logDebug("Position: " + position);
		holder.imageView.setImageBitmap(null);
		holder.declineLayout.setVisibility(View.GONE);
		
		MegaContactAdapter contact = (MegaContactAdapter) getItem(position);
		holder.verifiedIcon.setVisibility(!isItemChecked(position) && megaApi.areCredentialsVerified(contact.getMegaUser()) ? View.VISIBLE : View.GONE);

		holder.contactMail = contact.getMegaUser().getEmail();
		logDebug("Contact: " + contact.getMegaUser().getEmail() + ", Handle: " + contact.getMegaUser().getHandle());

		holder.contactStateIcon.setVisibility(View.VISIBLE);
        int userStatus = megaChatApi.getUserOnlineStatus(contact.getMegaUser().getHandle());
		setContactStatus(userStatus, holder.contactStateIcon, holder.textViewContent, StatusIconLocation.STANDARD);
		setContactLastGreen(context, userStatus, contact.getLastGreen(), holder.textViewContent);
		holder.textViewContactName.setText(contact.getFullName());

		if (!multipleSelect) {
			createDefaultAvatar(holder, contact);

			UserAvatarListener listener = new UserAvatarListener(context, holder);

			File avatar = buildAvatarFile(context, holder.contactMail + ".jpg");
			Bitmap bitmap = null;
			if (isFileAvailable(avatar)){
				if (avatar.length() > 0){
					BitmapFactory.Options bOpts = new BitmapFactory.Options();
					bOpts.inPurgeable = true;
					bOpts.inInputShareable = true;
					bitmap = BitmapFactory.decodeFile(avatar.getAbsolutePath(), bOpts);
					if (bitmap == null) {
						avatar.delete();
                        megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
					}
					else{
						logDebug("Do not ask for user avatar - its in cache: " + avatar.getAbsolutePath());
						holder.imageView.setImageBitmap(bitmap);
					}
				}
				else{
                    megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
				}
			}
			else{
                megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
			}
		} else {

			if(this.isItemChecked(position)){
				holder.imageView.setImageResource(R.drawable.ic_chat_avatar_select);
			}
			else{
				createDefaultAvatar(holder, contact);

				UserAvatarListener listener = new UserAvatarListener(context, holder);

				File avatar = buildAvatarFile(context, holder.contactMail + ".jpg");
				Bitmap bitmap = null;
				if (isFileAvailable(avatar)){
					if (avatar.length() > 0){
						BitmapFactory.Options bOpts = new BitmapFactory.Options();
						bOpts.inPurgeable = true;
						bOpts.inInputShareable = true;
						bitmap = BitmapFactory.decodeFile(avatar.getAbsolutePath(), bOpts);
						if (bitmap == null) {
							avatar.delete();
                            megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
						}
						else{
							holder.imageView.setImageBitmap(bitmap);
						}
					}
					else{
                        megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
					}
				}
				else{
                    megaApi.getUserAvatar(contact.getMegaUser(),buildAvatarFile(context,contact.getMegaUser().getEmail() + ".jpg").getAbsolutePath(),listener);
				}
			}
		}
		
		holder.threeDotsLayout.setTag(holder);
		holder.threeDotsLayout.setOnClickListener(this);
	}
	
	private void createDefaultAvatar(ViewHolderContacts holder, MegaContactAdapter contact){
		int color = getColorAvatar(contact.getMegaUser());
		String fullName = contact.getFullName();

		if (holder instanceof ViewHolderContactsList){
			Bitmap bit = getDefaultAvatar(color, fullName, AVATAR_SIZE, true);
			((ViewHolderContactsList)holder).imageView.setImageBitmap(bit);

		}
		else if (holder instanceof ViewHolderContactsGrid){
			Bitmap bit = getDefaultAvatar(color, fullName, AVATAR_SIZE_GRID, false);
			((ViewHolderContactsGrid)holder).imageView.setImageBitmap(bit);
		}

	}


	@Override
    public int getItemCount() {
        return contacts.size();
    }
	
	@Override
	public int getItemViewType(int position) {
		return adapterType;
	}
	
	public Object getItem(int position) {
		logDebug("Position: " + position);
		return contacts.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	public int getPositionClicked() {
		return positionClicked;
	}

	public void setPositionClicked(int p) {
		logDebug("Position: "  +p);
		positionClicked = p;
		notifyDataSetChanged();
	}
	
	public void setAdapterType(int adapterType){
		this.adapterType = adapterType;
	}
 
	public boolean isMultipleSelect() {
		return multipleSelect;
	}
	
	public void setMultipleSelect(boolean multipleSelect) {
		if (this.multipleSelect != multipleSelect) {
			this.multipleSelect = multipleSelect;
		}
		if(this.multipleSelect)
		{
			selectedItems = new SparseBooleanArray();
		}
	}

//	public void toggleAllSelection(int pos) {
//		log("toggleAllSelection: "+pos);
//		final int positionToflip = pos;
//
//		if (selectedItems.get(pos, false)) {
//			log("delete pos: "+pos);
//			selectedItems.delete(pos);
//		}
//		else {
//			log("PUT pos: "+pos);
//			selectedItems.put(pos, true);
//		}
//		notifyItemChanged(positionToflip);
//
//		if (adapterType == MegaNodeAdapter.ITEM_VIEW_TYPE_LIST){
//			log("adapter type is LIST");
//			MegaContactsLollipopAdapter.ViewHolderContactsList view = (MegaContactsLollipopAdapter.ViewHolderContactsList) listFragment.findViewHolderForLayoutPosition(pos);
//			if(view!=null){
//				log("Start animation: "+pos);
////				Animation flipAnimation = AnimationUtils.loadAnimation(context, R.anim.multiselect_flip);
//
////				view.imageView.startAnimation(flipAnimation);
//				ObjectAnimator imageViewObjectAnimator = ObjectAnimator.ofFloat(view.imageView ,
//						"rotationY", 360f, 0f);
//				imageViewObjectAnimator.setDuration(500);
//
//				imageViewObjectAnimator.addListener(new Animator.AnimatorListener() {
//					@Override
//					public void onAnimationStart(Animator animation) {
//						log("START animation--");
//					}
//
//					@Override
//					public void onAnimationEnd(Animator animation) {
//						if (selectedItems.size() <= 0){
//							log("Force hideMultiselect");
//							((ContactsFragmentLollipop) fragment).hideMultipleSelect();
//						}
//						log("END animation--");
//
//					}
//
//					@Override
//					public void onAnimationCancel(Animator animation) {
//
//					}
//
//					@Override
//					public void onAnimationRepeat(Animator animation) {
//
//					}
//				});
//
//				imageViewObjectAnimator.start();
//			}
//			else{
//				log("NULL view pos: "+positionToflip);
//                log("multiselect: "+isMultipleSelect());
//				notifyItemChanged(pos);
//			}
//		}
//		else {
//			log("adapter type is GRID");
//			if (selectedItems.size() <= 0){
//				((ContactsFragmentLollipop) fragment).hideMultipleSelect();
//			}
//			notifyItemChanged(positionToflip);
//		}
//	}
	
	public void toggleSelection(final int pos) {
		logDebug("Position: " + pos);

		final boolean delete;

		if (selectedItems.get(pos, false)) {
			logDebug("Delete pos: " + pos);
			selectedItems.delete(pos);
			delete = true;
		}
		else {
			logDebug("PUT pos: " + pos);
			selectedItems.put(pos, true);
			delete = false;
		}

		logDebug("Adapter type is GRID");
		MegaContactsLollipopAdapter.ViewHolderContactsGrid view = (MegaContactsLollipopAdapter.ViewHolderContactsGrid) listFragment.findViewHolderForLayoutPosition(pos);
		if(view!=null){
			logDebug("Start animation: " + pos);
			Animation flipAnimation = AnimationUtils.loadAnimation(context, R.anim.multiselect_flip);
			if (!delete) {
				notifyItemChanged(pos);
				flipAnimation.setDuration(200);
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
					notifyItemChanged(pos);
				}

				@Override
				public void onAnimationRepeat(Animation animation) {

				}
			});
			view.contactSelectedIcon.startAnimation(flipAnimation);
		}
		else{
			notifyItemChanged(pos);
		}
	}
	
	public void selectAll(){
		for (int i= 0; i<this.getItemCount();i++){
			if(!isItemChecked(i)){
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

	public void clearSelectionsNoAnimations() {
		logDebug("clearSelections");
		for (int i= 0; i<this.getItemCount();i++){
			if(isItemChecked(i)){
				selectedItems.delete(i);
				notifyItemChanged(i);
			}
		}
	}

	private boolean isItemChecked(int position) {
		if(selectedItems!=null){
			return selectedItems.get(position);
		}
		return false;
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
	
	/*
	 * Get list of all selected contacts
	 */
	public ArrayList<MegaUser> getSelectedUsers() {
		ArrayList<MegaUser> users = new ArrayList<MegaUser>();
		
		for (int i = 0; i < selectedItems.size(); i++) {
			if (selectedItems.valueAt(i) == true) {
				MegaUser u = getContactAt(selectedItems.keyAt(i));
				if (u != null){
					users.add(u);
				}
			}
		}
		return users;
	}	
	
	/*
	 * Get contact at specified position
	 */
	public MegaUser getContactAt(int position) {
		MegaContactAdapter megaContactAdapter = null;
		try {
			if (contacts != null) {
				megaContactAdapter = contacts.get(position);
				return megaContactAdapter.getMegaUser();
			}
		} catch (IndexOutOfBoundsException e) {
			logError("EXCEPTION", e);
		}
		return null;
	}
    
	@Override
	public void onClick(View v) {
		logDebug("adapterType: " + adapterType);

		ViewHolderContactsList holder = (ViewHolderContactsList) v.getTag();
		int currentPosition = holder.getAdapterPosition();
		try {
			MegaContactAdapter c = (MegaContactAdapter) getItem(currentPosition);
			switch (v.getId()){
				case R.id.contact_list_decline:
				case R.id.contact_list_item_layout: {
					logDebug("contact_list_item_layout");
					((AddContactActivityLollipop) context).itemClick(c.getMegaUser().getEmail(), adapterType);
					break;
				}
			}
		} catch (IndexOutOfBoundsException e) {
			logError("EXCEPTION", e);
		}
	}

	@Override
	public boolean onLongClick(View view) {
		logDebug("OnLongCLick");

		ViewHolderContacts holder = (ViewHolderContacts) view.getTag();
		int currentPosition = holder.getAdapterPosition();

		return true;
	}

	public MegaUser getDocumentAt(int position) {
		MegaContactAdapter megaContactAdapter = null;
		if(position < contacts.size())
		{
			megaContactAdapter = contacts.get(position);
			return megaContactAdapter.getMegaUser();
		}

		return null;
	}
	
	public void setContacts (ArrayList<MegaContactAdapter> contacts){
		this.contacts = contacts;
		positionClicked = -1;
		notifyDataSetChanged();
	}

	public void updateContactStatus(int position){
		logDebug("Position: " + position);

		notifyItemChanged(position);
	}

	public ArrayList<MegaContactAdapter> getContacts() {
		return contacts;
	}

	public RecyclerView getListFragment() {
		return listFragment;
	}

	public void setListFragment(RecyclerView listFragment) {
		this.listFragment = listFragment;
	}
}
