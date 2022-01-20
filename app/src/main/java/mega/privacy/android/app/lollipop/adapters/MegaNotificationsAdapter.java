package mega.privacy.android.app.lollipop.adapters;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.R;
import mega.privacy.android.app.listeners.GetUserEmailListener;
import mega.privacy.android.app.lollipop.managerSections.NotificationsFragmentLollipop;
import mega.privacy.android.app.lollipop.megachat.NonContactInfo;
import mega.privacy.android.app.utils.ColorUtils;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaNode;
import nz.mega.sdk.MegaUserAlert;

import static mega.privacy.android.app.utils.FileUtil.*;
import static mega.privacy.android.app.utils.ContactUtil.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.StringResourcesUtils.getQuantityString;
import static mega.privacy.android.app.utils.StringResourcesUtils.getString;
import static mega.privacy.android.app.utils.TextUtil.replaceFormatNotificationText;
import static mega.privacy.android.app.utils.TimeUtils.*;
import static mega.privacy.android.app.utils.Util.*;


public class MegaNotificationsAdapter extends RecyclerView.Adapter<MegaNotificationsAdapter.ViewHolderNotifications> implements OnClickListener, GetUserEmailListener.OnUserEmailUpdateCallback {

	public static int MAX_WIDTH_FIRST_LINE_NEW_LAND =306;
	public static int MAX_WIDTH_FIRST_LINE_SEEN_LAND =336;

	public static int MAX_WIDTH_FIRST_LINE_NEW_PORT =276;
	public static int MAX_WIDTH_FIRST_LINE_SEEN_PORT =328;

	private Context context;
	private int positionClicked;
	private ArrayList<MegaUserAlert> notifications;
	private RecyclerView listFragment;
	private MegaApiAndroid megaApi;

	DisplayMetrics outMetrics;

	private NotificationsFragmentLollipop fragment;

	public MegaNotificationsAdapter(Context _context, NotificationsFragmentLollipop _fragment, ArrayList<MegaUserAlert> _notifications, RecyclerView _listView) {
		this.context = _context;
		this.notifications = _notifications;
		this.fragment = _fragment;
		this.positionClicked = -1;


		if (megaApi == null){
			megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
		}

		Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
		outMetrics = new DisplayMetrics ();
		display.getMetrics(outMetrics);

		listFragment = _listView;
	}

	/*private view holder class*/
    public static class ViewHolderNotifications extends RecyclerView.ViewHolder{
    	public ViewHolderNotifications(View v) {
			super(v);
		}

		LinearLayout itemLayout;

		ImageView sectionIcon;
    	TextView sectionText;

    	ImageView titleIcon;
    	TextView titleText;
    	TextView newText;

    	TextView descriptionText;
    	TextView dateText;

    	LinearLayout separator;
	}

	ViewHolderNotifications holder = null;

	@Override
	public ViewHolderNotifications onCreateViewHolder(ViewGroup parent, int viewType) {
		logDebug("onCreateViewHolder");

		View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_list, parent, false);

		holder = new ViewHolderNotifications(v);

		holder.itemLayout = (LinearLayout) v.findViewById(R.id.notification_list_item_layout);

		holder.sectionIcon = (ImageView) v.findViewById(R.id.notification_title_icon);
		holder.sectionText = (TextView) v.findViewById(R.id.notification_title_text);

		holder.titleIcon = (ImageView) v.findViewById(R.id.notification_first_line_icon);
		holder.titleText = (TextView) v.findViewById(R.id.notification_first_line_text);
		holder.newText = (TextView) v.findViewById(R.id.notification_new_label);

		holder.descriptionText = (TextView) v.findViewById(R.id.notifications_text);
		holder.dateText = (TextView) v.findViewById(R.id.notifications_date);

		holder.separator = (LinearLayout) v.findViewById(R.id.notifications_separator);

		holder.itemLayout.setTag(holder);
		holder.itemLayout.setOnClickListener(this);

		v.setTag(holder);

		return holder;
	}

	boolean isHolderNull(int pos, MegaNotificationsAdapter.ViewHolderNotifications holder) {
		if (holder ==  null) {
			holder = (MegaNotificationsAdapter.ViewHolderNotifications) listFragment.findViewHolderForAdapterPosition(pos);
			if (holder == null) {
				notifyItemChanged(pos);
				return true;
			}
		}

		return false;
	}

	/**
	 * Method for getting a user's email from MegaUserAlert.
	 *
	 * @param pos    position in adapter
	 * @param holder ViewHolderNotifications
	 * @param alert  MegaUserAlert
	 * @return The email
	 */
	private String getEmail(int pos, ViewHolderNotifications holder, MegaUserAlert alert) {
		String email = alert.getEmail();
		if (email != null) {
			return email;
		}

		NonContactInfo nonContact = MegaApplication.getInstance().getDbH().findNonContactByHandle(alert.getUserHandle() + "");
		if (nonContact != null && nonContact.getEmail() != null) {
			return nonContact.getEmail();
		}

		if (isHolderNull(pos, holder)) {
			return null;
		}

		megaApi.getUserEmail(alert.getUserHandle(), new GetUserEmailListener(context, this, pos));
		return context.getString(R.string.unknown_name_label);
	}

	/**
	 * Method to correctly show the title or description text of a notification when the user's email needs to be displayed.
	 *
	 * @param position position in adapter
	 * @param holder   ViewHolderNotifications
	 * @param alert    MegaUserAlert
	 * @param email    Email of a user
	 */
	private void setTitleOrDescriptionTextWithEmail(int position, ViewHolderNotifications holder, MegaUserAlert alert, String email) {
		if (isHolderNull(position, holder)) {
			return;
		}

		String textToShow;
		switch (alert.getType()) {
			case MegaUserAlert.TYPE_INCOMINGPENDINGCONTACT_REQUEST:
				textToShow = getString(R.string.notification_new_contact_request, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_INCOMINGPENDINGCONTACT_CANCELLED:
				textToShow = getString(R.string.subtitle_contact_request_notification_cancelled, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_CONTACTCHANGE_CONTACTESTABLISHED:
				textToShow = getString(R.string.notification_new_contact, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_CONTACTCHANGE_ACCOUNTDELETED:
				textToShow = getString(R.string.subtitle_account_notification_deleted, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_INCOMINGPENDINGCONTACT_REMINDER:
				textToShow = getString(R.string.notification_reminder_contact_request, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_CONTACTCHANGE_DELETEDYOU:
				textToShow = getString(R.string.subtitle_contact_notification_deleted, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_CONTACTCHANGE_BLOCKEDYOU:
				textToShow = getString(R.string.subtitle_contact_notification_blocked, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_UPDATEDPENDINGCONTACTOUTGOING_ACCEPTED:
				textToShow = getString(R.string.subtitle_outgoing_contact_request_accepted, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_UPDATEDPENDINGCONTACTOUTGOING_DENIED:
				textToShow = getString(R.string.subtitle_outgoing_contact_request_denied, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_UPDATEDPENDINGCONTACTINCOMING_IGNORED:
				textToShow = getString(R.string.subtitle_incoming_contact_request_ignored, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_UPDATEDPENDINGCONTACTINCOMING_ACCEPTED:
				textToShow = getString(R.string.subtitle_incoming_contact_request_accepted, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_UPDATEDPENDINGCONTACTINCOMING_DENIED:
				textToShow = getString(R.string.subtitle_incoming_contact_request_denied, getNicknameForNotificationsSection(context, email));
				holder.descriptionText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_NEWSHARE:
				textToShow = getString(R.string.notification_new_shared_folder, getNicknameForNotificationsSection(context, email));
				holder.titleText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_DELETEDSHARE:
				if (alert.getNumber(0) == 0) {
					MegaNode node = megaApi.getNodeByHandle(alert.getNodeHandle());
					if (node != null) {
						textToShow = getString(R.string.notification_left_shared_folder_with_name, getNicknameForNotificationsSection(context, email), node.getName());
					} else {
						textToShow = getString(R.string.notification_left_shared_folder, getNicknameForNotificationsSection(context, email));
					}
				} else {
					textToShow = getString(R.string.notification_deleted_shared_folder, getNicknameForNotificationsSection(context, email));
				}

				holder.titleText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_NEWSHAREDNODES:
				int numFiles = (int) alert.getNumber(1);
				int numFolders = (int) alert.getNumber(0);
				if (numFolders > 0 && numFiles > 0) {
					String numFilesString = getQuantityString(R.plurals.num_files_with_parameter, numFiles, numFiles);
					String numFoldersString = getQuantityString(R.plurals.num_folders_with_parameter, numFolders, numFolders);
					textToShow = getString(R.string.subtitle_notification_added_folders_and_files, getNicknameForNotificationsSection(context, email), numFoldersString, numFilesString);
				} else if (numFolders > 0) {
					textToShow = getQuantityString(R.plurals.subtitle_notification_added_folders, numFolders, getNicknameForNotificationsSection(context, email), numFolders);
				} else {
					textToShow = getQuantityString(R.plurals.subtitle_notification_added_files, numFiles, getNicknameForNotificationsSection(context, email), numFiles);
				}

				holder.titleText.setText(replaceFormatNotificationText(context, textToShow));
				break;

			case MegaUserAlert.TYPE_REMOVEDSHAREDNODES:
				int itemCount = (int) alert.getNumber(0);
				textToShow = getQuantityString(R.plurals.subtitle_notification_deleted_items, itemCount,
						getNicknameForNotificationsSection(context, email), itemCount);
				holder.titleText.setText(replaceFormatNotificationText(context, textToShow));
				break;
		}
	}

	@Override
	public void onBindViewHolder(ViewHolderNotifications holder, int position) {
		logDebug("Position: " + position);

		MegaUserAlert alert = (MegaUserAlert) getItem(position);
		int alertType = alert.getType();
		String section = alert.getHeading();

		final LinearLayout.LayoutParams params;

		switch (alertType){

			case MegaUserAlert.TYPE_INCOMINGPENDINGCONTACT_REQUEST:{
				//New contact request
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_contact_request_notification));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(this);

				break;
			}
			case MegaUserAlert.TYPE_INCOMINGPENDINGCONTACT_CANCELLED:{
				//Contact request cancelled
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_contact_request_notification_cancelled));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(null);

				break;
			}
			case MegaUserAlert.TYPE_CONTACTCHANGE_CONTACTESTABLISHED:{
				//New contact
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_acceptance_contact_request_notification));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(this);

				break;
			}
			case MegaUserAlert.TYPE_CONTACTCHANGE_ACCOUNTDELETED:{
				//Account deleted android100@yopmail.com
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_account_notification_deleted));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(null);

				break;
			}
			case MegaUserAlert.TYPE_INCOMINGPENDINGCONTACT_REMINDER:{
				//Contact request reminder
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_contact_request_notification));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(this);
				break;
			}
			case MegaUserAlert.TYPE_CONTACTCHANGE_DELETEDYOU:{
				//Contact deleted you
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_contact_notification_deleted));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(null);

				break;
			}
			case MegaUserAlert.TYPE_CONTACTCHANGE_BLOCKEDYOU:{
				//Contact blocked you
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_contact_notification_blocked));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(null);

				break;
			}
			case MegaUserAlert.TYPE_UPDATEDPENDINGCONTACTOUTGOING_ACCEPTED:{
				//Outgoing contact request accepted by the other user
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_outgoing_contact_request));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(this);
				break;
			}
			case MegaUserAlert.TYPE_UPDATEDPENDINGCONTACTOUTGOING_DENIED:{
				//Outgoing contact request denied by the other user
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_outgoing_contact_request));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(null);
				break;
			}
			case MegaUserAlert.TYPE_UPDATEDPENDINGCONTACTINCOMING_IGNORED: {
				//Incoming contact request ignored by me
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_incoming_contact_request));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(null);
				break;
			}
			case MegaUserAlert.TYPE_UPDATEDPENDINGCONTACTINCOMING_ACCEPTED:{
				//Incoming contact request accepted by me
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_incoming_contact_request));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(this);
				break;
			}
			case MegaUserAlert.TYPE_UPDATEDPENDINGCONTACTINCOMING_DENIED:{
				//Incoming contact request denied by me
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.section_contacts).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.jade_600_jade_300));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				holder.titleText.setText(context.getString(R.string.title_incoming_contact_request));

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.VISIBLE);

				//Description set to max, adjust title
				holder.titleText.setMaxLines(1);
				if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
				}
				else{
					holder.descriptionText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					if(alert.getSeen()==false){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(null);
				break;
			}
			case MegaUserAlert.TYPE_NEWSHARE:{
				//New shared folder
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.title_incoming_shares_explorer).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.orange_400_orange_300));
				holder.sectionIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_y_arrow_in));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.GONE);

				//Description not shown, adjust title
				holder.titleText.setMaxLines(3);
				if(alert.getSeen()==false){
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
				}
				else{
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				//Allow navigation to the folder
				if(alert.getNodeHandle()!=-1 && megaApi.getNodeByHandle(alert.getNodeHandle())!=null){
					holder.itemLayout.setOnClickListener(this);
				}

				break;
			}
			case MegaUserAlert.TYPE_DELETEDSHARE:{
				//Removed shared folder
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.title_incoming_shares_explorer).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.orange_400_orange_300));
				holder.sectionIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_y_arrow_in));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				//TYPE_DELETEDSHARE (0: value 1 if access for this user was removed by the share owner, otherwise
				//value 0 if someone left the folder)

				if (alert.getNumber(0) == 0) {
					MegaNode node = megaApi.getNodeByHandle(alert.getNodeHandle());
					holder.itemLayout.setOnClickListener(node != null ? this : null);
				}

				holder.descriptionText.setVisibility(View.GONE);

				//Description not shown, adjust title
				holder.titleText.setMaxLines(3);
				if(alert.getSeen()==false){
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
				}
				else{
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				break;
			}
			case MegaUserAlert.TYPE_NEWSHAREDNODES:{
				//New files added to a shared folder
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.title_incoming_shares_explorer).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.orange_400_orange_300));
				holder.sectionIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_y_arrow_in));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.GONE);

				//Description not shown, adjust title
				holder.titleText.setMaxLines(3);
				if(alert.getSeen()==false){
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
				}
				else{
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				//Allow navigation to the folder
				if(alert.getNodeHandle()!=-1 && megaApi.getNodeByHandle(alert.getNodeHandle())!=null){
					holder.itemLayout.setOnClickListener(this);
				}

				break;
			}
			case MegaUserAlert.TYPE_REMOVEDSHAREDNODES:{
				//New files added to a shared folder
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = context.getString(R.string.title_incoming_shares_explorer).toUpperCase();
				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.orange_400_orange_300));
				holder.sectionIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_y_arrow_in));
				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

				setTitleOrDescriptionTextWithEmail(position, holder, alert, getEmail(position, holder, alert));

				holder.descriptionText.setVisibility(View.GONE);

				//Description not shown, adjust title
				holder.titleText.setMaxLines(3);
				if(alert.getSeen()==false){
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
				}
				else{
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				//Allow navigation to the folder
				if(alert.getNodeHandle()!=-1 && megaApi.getNodeByHandle(alert.getNodeHandle())!=null){
					holder.itemLayout.setOnClickListener(this);
				}

				break;
			}
			case MegaUserAlert.TYPE_PAYMENT_SUCCEEDED:{
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = alert.getHeading().toUpperCase();

				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.red_600_red_300));

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

				holder.titleText.setText(alert.getTitle());
				holder.descriptionText.setVisibility(View.GONE);

				//Description not shown, adjust title
				holder.titleText.setMaxLines(3);
				if(alert.getSeen()==false){
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
				}
				else{
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(this);

				break;
			}
			case MegaUserAlert.TYPE_PAYMENT_FAILED:{
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = alert.getHeading().toUpperCase();;

				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.red_600_red_300));

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

				holder.titleText.setText(alert.getTitle());

				holder.descriptionText.setVisibility(View.GONE);

				//Description not shown, adjust title
				holder.titleText.setMaxLines(3);
				if(alert.getSeen()==false){
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
				}
				else{
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(this);

				break;
			}
			case MegaUserAlert.TYPE_PAYMENTREMINDER:{
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = alert.getHeading();

				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.red_600_red_300));

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

				holder.titleText.setText(alert.getTitle());
				holder.descriptionText.setVisibility(View.GONE);

				//Description not shown, adjust title
				holder.titleText.setMaxLines(3);
				if(alert.getSeen()==false){
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
				}
				else{
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				holder.itemLayout.setOnClickListener(this);
				break;
			}
			case MegaUserAlert.TYPE_TAKEDOWN:{
				//Link takedown android100
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

				section = section.toUpperCase();

				holder.sectionIcon.setVisibility(View.GONE);
				holder.titleIcon.setVisibility(View.GONE);

				holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.red_600_red_300));

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

				String name = alert.getName();
				String path = alert.getPath();
				String textToShow = "";
				if (path != null && isFile(path)) {
					textToShow = String.format(context.getString(R.string.subtitle_file_takedown_notification), toCDATA(name));
				}
				else{
					textToShow = String.format(context.getString(R.string.subtitle_folder_takedown_notification), toCDATA(name));
				}

				try{
					textToShow = textToShow.replace("[A]", "<font color=\'"
							+ ColorUtils.getColorHexString(context, R.color.grey_900_grey_100)
							+ "\'>");
					textToShow = textToShow.replace("[/A]", "</font>");
					textToShow = textToShow.replace("[B]", "<font color=\'"
							+ ColorUtils.getColorHexString(context, R.color.grey_500_grey_400)
							+ "\'>");
					textToShow = textToShow.replace("[/B]", "</font>");
				}
				catch (Exception e){}
				Spanned result = null;
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
					result = Html.fromHtml(textToShow,Html.FROM_HTML_MODE_LEGACY);
				} else {
					result = Html.fromHtml(textToShow);
				}
				holder.titleText.setText(result);
				holder.descriptionText.setVisibility(View.GONE);

				//Description not shown, adjust title
				holder.titleText.setMaxLines(3);
				if(alert.getSeen()==false){
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
				}
				else{
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				//Allow navigation to the folder
				if(alert.getNodeHandle()!=-1 && megaApi.getNodeByHandle(alert.getNodeHandle())!=null){
					holder.itemLayout.setOnClickListener(this);
				}

				break;
			}
			case MegaUserAlert.TYPE_TAKEDOWN_REINSTATED:{
                //Link takedown reinstated android100
				section = section.toUpperCase();

				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.VISIBLE);

                holder.sectionIcon.setVisibility(View.GONE);
                holder.titleIcon.setVisibility(View.GONE);

                holder.sectionText.setTextColor(ContextCompat.getColor(context, R.color.red_600_red_300));

				holder.titleText.setVisibility(View.VISIBLE);
				holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

                String name = alert.getName();
                String path = alert.getPath();
				String textToShow = "";
				if (path != null && isFile(path)) {
					textToShow = String.format(context.getString(R.string.subtitle_file_takedown_reinstated_notification), toCDATA(name));
				}
				else{
					textToShow = String.format(context.getString(R.string.subtitle_folder_takedown_reinstated_notification), toCDATA(name));
				}

                try{
					textToShow = textToShow.replace("[A]", "<font color=\'"
							+ ColorUtils.getColorHexString(context, R.color.grey_900_grey_100)
							+ "\'>");
					textToShow = textToShow.replace("[/A]", "</font>");
					textToShow = textToShow.replace("[B]", "<font color=\'"
							+ ColorUtils.getColorHexString(context, R.color.grey_500_grey_400)
							+ "\'>");
					textToShow = textToShow.replace("[/B]", "</font>");
                }
                catch (Exception e){}
                Spanned result = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    result = Html.fromHtml(textToShow,Html.FROM_HTML_MODE_LEGACY);
                } else {
                    result = Html.fromHtml(textToShow);
                }
				holder.titleText.setText(result);
				holder.descriptionText.setVisibility(View.GONE);

				//Description not shown, adjust title
				holder.titleText.setMaxLines(3);
				if(alert.getSeen()==false){
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_NEW_PORT, outMetrics));
					}
				}
				else{
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_LAND, outMetrics));
					}
					else{
						holder.titleText.setMaxWidth(scaleWidthPx(MAX_WIDTH_FIRST_LINE_SEEN_PORT, outMetrics));
					}
				}

				//Allow navigation to the folder
				if(alert.getNodeHandle()!=-1 && megaApi.getNodeByHandle(alert.getNodeHandle())!=null){
					holder.itemLayout.setOnClickListener(this);
				}

				break;
			}
			default:{
				//Hide
				params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				params.height = 0;
				holder.itemLayout.setLayoutParams(params);
				holder.itemLayout.setVisibility(View.GONE);

				holder.itemLayout.setOnClickListener(null);
				break;
			}
		}

		holder.sectionText.setText(section);

		String date = formatDateAndTime(context,alert.getTimestamp(0), DATE_LONG_FORMAT);
		holder.dateText.setText(date);

		if(alert.getSeen()==false){
			holder.newText.setVisibility(View.VISIBLE);
			holder.itemLayout.setBackgroundColor(ColorUtils.getThemeColor(context, android.R.attr.colorBackground));

			if(position<(notifications.size()-1)){
				MegaUserAlert nextAlert = (MegaUserAlert) getItem(position+1);
				if(nextAlert.getSeen()==false){
					LinearLayout.LayoutParams textParams = (LinearLayout.LayoutParams)holder.separator.getLayoutParams();
					textParams.setMargins(scaleWidthPx(16, outMetrics), 0, scaleWidthPx(16, outMetrics), 0);
					holder.separator.setLayoutParams(textParams);
				}
				else{
					LinearLayout.LayoutParams textParams = (LinearLayout.LayoutParams)holder.separator.getLayoutParams();
					textParams.setMargins(0, 0, 0, 0);
					holder.separator.setLayoutParams(textParams);
				}
			}
			else{
				logDebug("Last element of the notifications");
				LinearLayout.LayoutParams textParams = (LinearLayout.LayoutParams)holder.separator.getLayoutParams();
				textParams.setMargins(0, 0, 0, 0);
				holder.separator.setLayoutParams(textParams);
			}
		}
		else{
			holder.newText.setVisibility(View.GONE);
			holder.itemLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.grey_020_grey_800));

			LinearLayout.LayoutParams textParams = (LinearLayout.LayoutParams)holder.separator.getLayoutParams();
			textParams.setMargins(scaleWidthPx(16, outMetrics), 0, scaleWidthPx(16, outMetrics), 0);
			holder.separator.setLayoutParams(textParams);
		}


//		holder.imageButtonThreeDots.setTag(holder);
//		holder.imageButtonThreeDots.setOnClickListener(this);
	}

	@Override
    public int getItemCount() {
        return notifications.size();
    }

	public Object getItem(int position) {
		logDebug("Position: " + position);
		return notifications.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	public int getPositionClicked() {
		return positionClicked;
	}

	public void setPositionClicked(int p) {
		logDebug("Position: " + p);
		positionClicked = p;
		notifyDataSetChanged();
	}


	@Override
	public void onClick(View v) {
		logDebug("onClick");

		ViewHolderNotifications holder = (ViewHolderNotifications) v.getTag();
		int currentPosition = holder.getAdapterPosition();
		try {
//			MegaNotificationsAdapter notificationsAdapter = (MegaNotificationsAdapter) getItem(currentPosition);

			switch (v.getId()){
				case R.id.notification_list_item_layout:{
					logDebug("notification_list_item_layout");
					if (fragment != null){
						fragment.itemClick(currentPosition);
					}
					break;
				}
			}
		} catch (IndexOutOfBoundsException e) {
			logError("EXCEPTION" ,e);
		}
	}
	
	public void setNotifications (ArrayList<MegaUserAlert> notifications){
		logDebug("setNotifications");
		this.notifications = notifications;
		positionClicked = -1;
		notifyDataSetChanged();
	}

	public RecyclerView getListFragment() {
		return listFragment;
	}

	public void setListFragment(RecyclerView listFragment) {
		this.listFragment = listFragment;
	}

	@Override
	public void onUserEmailUpdate(@Nullable String email, long handleUser, int position) {
		MegaNotificationsAdapter.ViewHolderNotifications view = (MegaNotificationsAdapter.ViewHolderNotifications) listFragment.findViewHolderForLayoutPosition(position);
		if (view != null) {
			MegaUserAlert alert = (MegaUserAlert) getItem(position);
			if (handleUser != alert.getUserHandle()) {
				return;
			}

			setTitleOrDescriptionTextWithEmail(position, view, alert, email);
		}
	}
}
