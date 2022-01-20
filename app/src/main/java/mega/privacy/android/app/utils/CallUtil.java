package mega.privacy.android.app.utils;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.view.MenuItem;
import android.view.View;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;

import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.OpenLinkActivity;
import mega.privacy.android.app.R;
import mega.privacy.android.app.interfaces.SnackbarShower;
import mega.privacy.android.app.listeners.CreateChatListener;
import mega.privacy.android.app.listeners.LoadPreviewListener;
import mega.privacy.android.app.lollipop.megachat.AppRTCAudioManager;
import mega.privacy.android.app.meeting.listeners.DisableAudioVideoCallListener;
import mega.privacy.android.app.meeting.listeners.StartChatCallListener;
import mega.privacy.android.app.lollipop.AddContactActivityLollipop;
import mega.privacy.android.app.lollipop.ContactInfoActivityLollipop;
import mega.privacy.android.app.lollipop.InviteContactActivity;
import mega.privacy.android.app.lollipop.ManagerActivityLollipop;
import mega.privacy.android.app.lollipop.controllers.ChatController;
import mega.privacy.android.app.lollipop.megachat.ChatActivityLollipop;
import mega.privacy.android.app.meeting.activity.MeetingActivity;
import mega.privacy.android.app.objects.PasscodeManagement;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaChatApi;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaChatCall;
import nz.mega.sdk.MegaChatPeerList;
import nz.mega.sdk.MegaChatRequestListenerInterface;
import nz.mega.sdk.MegaChatRoom;
import nz.mega.sdk.MegaChatSession;
import nz.mega.sdk.MegaHandleList;
import nz.mega.sdk.MegaUser;

import static android.content.Context.NOTIFICATION_SERVICE;
import static android.view.View.GONE;
import static mega.privacy.android.app.meeting.activity.MeetingActivity.*;
import static mega.privacy.android.app.utils.AlertsAndWarnings.showOverDiskQuotaPaywallWarning;
import static mega.privacy.android.app.utils.AvatarUtil.*;
import static mega.privacy.android.app.utils.ChatUtil.getStatusBitmap;
import static mega.privacy.android.app.utils.ChatUtil.getTitleChat;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.ContactUtil.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.permission.PermissionUtils.*;
import static mega.privacy.android.app.utils.StringResourcesUtils.getString;
import static mega.privacy.android.app.utils.TextUtil.isTextEmpty;
import static mega.privacy.android.app.utils.Util.*;
import static nz.mega.sdk.MegaApiJava.STORAGE_STATE_PAYWALL;
import static nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE;
import static nz.mega.sdk.MegaChatCall.CALL_STATUS_USER_NO_PRESENT;

public class CallUtil {
    /**
     * Method for opening the Meeting Activity when the meeting is outgoing or in progress call
     *
     * @param context Context*
     */
    public static void openMeetingToCreate(Context context) {
        logDebug("Open create a meeting screen");
        Intent meetingIntent = new Intent(context, MeetingActivity.class);
        meetingIntent.setAction(MEETING_ACTION_CREATE);
        context.startActivity(meetingIntent);
    }

    /**
     * Method for opening the Meeting Activity when the meeting is outgoing or in progress call
     *
     * @param context            Context
     * @param chatId             chat ID
     * @param meetingName        Meeting Name
     * @param link               Meeting's link
     * @param passcodeManagement To disable passcode.
     */
    public static void openMeetingToJoin(Context context, long chatId, String meetingName, String link, long publicChatHandle, boolean isRejoin, PasscodeManagement passcodeManagement) {
        logDebug("Open join a meeting screen:: chatId = " + chatId);
        passcodeManagement.setShowPasscodeScreen(true);
        MegaApplication.getChatManagement().setOpeningMeetingLink(chatId, true);
        Intent meetingIntent = new Intent(context, MeetingActivity.class);
        if (isRejoin) {
            meetingIntent.setAction(MEETING_ACTION_JOIN);
            meetingIntent.putExtra(MEETING_PUBLIC_CHAT_HANDLE, publicChatHandle);
        } else {
            meetingIntent.setAction(MEETING_ACTION_JOIN);
        }
        meetingIntent.putExtra(MEETING_CHAT_ID, chatId);
        meetingIntent.putExtra(MEETING_NAME, meetingName);
        meetingIntent.setData(Uri.parse(link));
        context.startActivity(meetingIntent);
    }

    /**
     * Method for starting the Meeting Activity when the meeting is in progress call.
     *
     * @param context            Context
     * @param chatId             chat ID
     * @param passcodeManagement To disable passcode.
     */
    public static void openMeetingToStart(Context context, long chatId, PasscodeManagement passcodeManagement) {
        logDebug("Open join a meeting screen. Chat id is " + chatId);
        passcodeManagement.setShowPasscodeScreen(true);
        Intent meetingIntent = new Intent(context, MeetingActivity.class);
        meetingIntent.setAction(MEETING_ACTION_START);
        meetingIntent.putExtra(MEETING_CHAT_ID, chatId);
        context.startActivity(meetingIntent);
    }

    /**
     * Method for opening the Meeting Activity when the meeting is outgoing or in progress call
     *
     * @param context            Context
     * @param chatId             chat ID
     * @param passcodeManagement To disable passcode.
     */
    public static void openMeetingRinging(Context context, long chatId, PasscodeManagement passcodeManagement) {
        logDebug("Open incoming call screen. Chat id is " + chatId);
        passcodeManagement.setShowPasscodeScreen(true);
        MegaApplication.getInstance().openCallService(chatId);
        Intent meetingIntent = new Intent(context, MeetingActivity.class);
        meetingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        meetingIntent.setAction(MEETING_ACTION_RINGING);
        meetingIntent.putExtra(MEETING_CHAT_ID, chatId);
        context.startActivity(meetingIntent);
    }

    /**
     * Method for opening the Meeting Activity when the meeting is outgoing or in progress call
     *
     * @param context            Context
     * @param chatId             chat ID
     * @param passcodeManagement To disable passcode.
     */
    public static void openMeetingInProgress(Context context, long chatId, boolean isNewTask, PasscodeManagement passcodeManagement) {
        logDebug("Open in progress call screen. Chat id is " + chatId);
        passcodeManagement.setShowPasscodeScreen(true);
        if (isNewTask) {
            MegaApplication.getInstance().openCallService(chatId);
        }

        Intent meetingIntent = new Intent(context, MeetingActivity.class);
        meetingIntent.setAction(MEETING_ACTION_IN);
        meetingIntent.putExtra(MEETING_CHAT_ID, chatId);
        meetingIntent.putExtra(MEETING_IS_GUEST, MegaApplication.getInstance().getMegaApi().isEphemeralPlusPlus());
        if (isNewTask) {
            logDebug("New task");
            meetingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            meetingIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        context.startActivity(meetingIntent);
    }

    /**
     * Method for opening the Meeting Activity when the meeting is outgoing or in progress call with audio or video enable.
     *
     * @param context            Context
     * @param chatId             chat ID
     * @param isAudioEnable      it the audio is ON
     * @param isVideoEnable      it the video is ON
     * @param passcodeManagement To disable passcode.
     */
    public static void openMeetingWithAudioOrVideo(Context context, long chatId, boolean isAudioEnable, boolean isVideoEnable, PasscodeManagement passcodeManagement) {
        logDebug("Open call with audio or video. Chat id is " + chatId);
        passcodeManagement.setShowPasscodeScreen(true);
        MegaApplication.getInstance().openCallService(chatId);
        Intent meetingIntent = new Intent(context, MeetingActivity.class);
        meetingIntent.setAction(MEETING_ACTION_IN);
        meetingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        meetingIntent.putExtra(MEETING_CHAT_ID, chatId);
        meetingIntent.putExtra(MEETING_AUDIO_ENABLE, isAudioEnable);
        meetingIntent.putExtra(MEETING_VIDEO_ENABLE, isVideoEnable);
        context.startActivity(meetingIntent);
    }

    /**
     * Method for opening the Meeting Activity in guest mode
     *
     * @param context            Context
     * @param meetingName        Meeting Name
     * @param chatId             chat ID
     * @param link               Meeting's link
     * @param passcodeManagement To disable passcode.
     */
    public static void openMeetingGuestMode(Context context, String meetingName, long chatId, String link, PasscodeManagement passcodeManagement) {
        logDebug("Open meeting in guest mode. Chat id is " + chatId);
        passcodeManagement.setShowPasscodeScreen(true);
        MegaApplication.getChatManagement().setOpeningMeetingLink(chatId, true);
        MegaApplication.getInstance().setIsLoggingRunning(true);

        Intent intent = new Intent(context, MeetingActivity.class);
        intent.setAction(MEETING_ACTION_GUEST);
        if (!isTextEmpty(meetingName)) {
            intent.putExtra(MEETING_NAME, meetingName);
        }
        intent.putExtra(MEETING_CHAT_ID, chatId);
        intent.putExtra(MEETING_IS_GUEST, true);
        intent.setData(Uri.parse(link));
        context.startActivity(intent);
    }

    /**
     * Retrieve if there's a call in progress that you're participating in.
     *
     * @return True if you're on a call in progress. Otherwise false.
     */
    public static boolean participatingInACall() {
        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        MegaHandleList listCallsInitial = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_INITIAL);
        MegaHandleList listCallsConnecting = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_CONNECTING);
        MegaHandleList listCallsJoining = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_JOINING);
        MegaHandleList listCallsInProgress = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_IN_PROGRESS);

        return listCallsInitial.size() > 0 || listCallsConnecting.size() > 0 || listCallsJoining.size() > 0 || listCallsInProgress.size() > 0;
    }

    /**
     * Retrieve if there's a call in progress that you're participating in or a incoming call.
     *
     * @return True if you're on a call in progress o exists a incoming call. Otherwise false.
     */
    public static boolean existsAnOngoingOrIncomingCall() {
        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        MegaHandleList listCallsUserNoPresent = megaChatApi.getChatCalls(CALL_STATUS_USER_NO_PRESENT);
        MegaHandleList listCallsUserTerminatingUserParticipation = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_TERMINATING_USER_PARTICIPATION);
        MegaHandleList listCallsDestroy = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_DESTROYED);
        MegaHandleList listCalls = megaChatApi.getChatCalls();

        if ((listCalls.size() - listCallsDestroy.size()) == 0) {
            logDebug("No calls in progress");
            return false;
        }

        if ((listCalls.size() - listCallsDestroy.size()) == (listCallsUserNoPresent.size() + listCallsUserTerminatingUserParticipation.size())) {
            logDebug("I'm not participating in any of the calls there");
            return false;
        }

        return true;
    }

    /**
     * Retrieve the id of a chat that has a call in progress different that current one
     *
     * @param currentChatId the chat ID of the current call
     * @return A long data type. It's the id of chat
     */
    public static long getAnotherCallParticipating(Long currentChatId) {
        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        MegaHandleList listCallsInProgress = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_IN_PROGRESS);
        if (listCallsInProgress != null && listCallsInProgress.size() > 0) {
            for (int i = 0; i < listCallsInProgress.size(); i++) {
                if(listCallsInProgress.get(i) != currentChatId){
                    return listCallsInProgress.get(i);
                }
            }
        }

        MegaHandleList listCallsJoining = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_JOINING);
        if (listCallsJoining != null && listCallsJoining.size() > 0) {
            for (int i = 0; i < listCallsJoining.size(); i++) {
                if(listCallsJoining.get(i) != currentChatId){
                    return listCallsJoining.get(i);
                }
            }
        }

        MegaHandleList listCallsConnecting = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_CONNECTING);
        if (listCallsConnecting != null && listCallsConnecting.size() > 0) {
            for (int i = 0; i < listCallsConnecting.size(); i++) {
                if (listCallsConnecting.get(i) != currentChatId) {
                    return listCallsConnecting.get(i);
                }
            }
        }

        return MEGACHAT_INVALID_HANDLE;
    }

    /**
     * Opens the call that is in progress.
     *
     * @param context            From which the action is done.
     * @param passcodeManagement To disable passcode.
     */
    public static void returnActiveCall(Context context, PasscodeManagement passcodeManagement) {
        ArrayList<Long> currentCalls = getCallsParticipating();

        if (currentCalls != null && !currentCalls.isEmpty()) {
            for (Long chatIdCall : currentCalls) {
                MegaChatCall call = MegaApplication.getInstance().getMegaChatApi().getChatCall(chatIdCall);
                if (call != null) {
                    openMeetingInProgress(context, chatIdCall, false, passcodeManagement);
                    break;
                }
            }
        }
    }

    /**
     * Opens the call that is in progress.
     *
     * @param context            From which the action is done.
     * @param chatId             ID chat.
     * @param passcodeManagement To disable passcode.
     */
    public static void returnCall(Context context, long chatId, PasscodeManagement passcodeManagement) {
        ArrayList<Long> currentCalls = getCallsParticipating();
        if (currentCalls == null || currentCalls.isEmpty())
            return;

        for (Long chatIdCall : currentCalls) {
            if (chatIdCall == chatId) {
                openMeetingInProgress(context, chatId, false, passcodeManagement);
                return;
            }
        }
    }

    /**
     * Method to know if I can join a one-to-one call
     *
     * @param chatId The chat id of the call I want to join
     * @return True, if I can join. False, if not
     */
    public static boolean checkIfCanJoinOneToOneCall(long chatId) {
        MegaChatRoom chatRoom = MegaApplication.getInstance().getMegaChatApi().getChatRoom(chatId);
        MegaChatCall call = MegaApplication.getInstance().getMegaChatApi().getChatCall(chatId);

        if (chatRoom == null || call == null) {
            logError("Chat room or call is null");
            return false;
        }

        if (!chatRoom.isGroup() && !chatRoom.isMeeting() && call.getStatus() == CALL_STATUS_USER_NO_PRESENT) {
            MegaHandleList listPeers = call.getPeeridParticipants();
            if (listPeers != null && listPeers.size() > 0) {
                for (int i = 0; i < listPeers.size(); i++) {
                    if (listPeers.get(i) == MegaApplication.getInstance().getMegaApi().getMyUserHandleBinary()) {
                        logDebug("I am already participating in this one-to-one call");
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Method to get the session of an individual call.
     *
     * @return The session.
     */
    public static MegaChatSession getSessionIndividualCall(MegaChatCall callChat) {
        if (callChat == null)
            return null;

        return callChat.getMegaChatSession(callChat.getSessionsClientid().get(0));
    }

    /**
     * Method for knowing if the session is on hold.
     *
     * @return True if it's on hold. False if it's not.
     */
    public static boolean isSessionOnHold(long chatId) {
        MegaChatRoom chat = MegaApplication.getInstance().getMegaChatApi().getChatRoom(chatId);
        if (chat == null || chat.isGroup())
            return false;

        MegaChatSession session = getSessionIndividualCall(MegaApplication.getInstance().getMegaChatApi().getChatCall(chatId));
        if (session == null)
            return false;

        return session.isOnHold();
    }

    private static void createCallBanner(Context context, long chatId, final RelativeLayout callInProgressLayout, final Chronometer callInProgressChrono, final TextView callInProgressText) {
        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();

        MegaChatCall call = megaChatApi.getChatCall(chatId);
        if (call == null)
            return;

        callInProgressText.setText(context.getString(R.string.call_in_progress_layout));
        callInProgressLayout.setBackgroundColor(ColorUtils.getThemeColor(context, R.attr.colorSecondary));

        if (MegaApplication.getChatManagement().isRequestSent(call.getCallId())) {
            activateChrono(false, callInProgressChrono, null);
        } else {
            activateChrono(true, callInProgressChrono, call);
        }

        callInProgressLayout.setVisibility(View.VISIBLE);

        if (context instanceof ManagerActivityLollipop) {
            ((ManagerActivityLollipop) context).changeAppBarElevation(true,
                    ManagerActivityLollipop.ELEVATION_CALL_IN_PROGRESS);
        }
        if (context instanceof ContactInfoActivityLollipop) {
            ((ContactInfoActivityLollipop) context).changeToolbarLayoutElevation();
        }
    }

    /**
     * Show or hide the "Tap to return to call" banner
     *
     * @param context              from which the action is done
     * @param callInProgressLayout RelativeLayout to be shown or hidden
     * @param callInProgressChrono Chronometer of the banner to be updated.
     * @param callInProgressText   Text of the banner to be updated
     */
    public static void showCallLayout(Context context, final RelativeLayout callInProgressLayout, final Chronometer callInProgressChrono, final TextView callInProgressText) {
        if (callInProgressLayout == null) {
            return;
        }

        ArrayList<Long> currentChatCallsList = getCallsParticipating();
        if (!participatingInACall() || currentChatCallsList == null || !isScreenInPortrait(context)) {
            hideCallInProgressLayout(context, callInProgressLayout, callInProgressChrono);
            return;
        }

        MegaChatCall currentCallInProgress = getCallInProgress();
        if (currentCallInProgress != null) {
            createCallBanner(context, currentCallInProgress.getChatid(), callInProgressLayout, callInProgressChrono, callInProgressText);
            return;
        }

        ArrayList<Long> calls = getCallsParticipating();
        if (calls != null && !calls.isEmpty()) {
            for (long chatId : calls) {
                MegaChatCall call = MegaApplication.getInstance().getMegaChatApi().getChatCall(chatId);
                if (call != null && call.isOnHold()) {
                    createCallBanner(context, chatId, callInProgressLayout, callInProgressChrono, callInProgressText);
                    break;
                }
            }
            return;
        }

        hideCallInProgressLayout(context, callInProgressLayout, callInProgressChrono);
    }

    /**
     * This method is used to hide the current call banner.
     *
     * @param context              The Activity context.
     * @param callInProgressLayout RelativeLayout to be hidden
     * @param callInProgressChrono Chronometer of the banner.
     */
    private static void hideCallInProgressLayout(Context context, final RelativeLayout callInProgressLayout, final Chronometer callInProgressChrono) {
        callInProgressLayout.setVisibility(View.GONE);
        activateChrono(false, callInProgressChrono, null);
        if (context instanceof ManagerActivityLollipop) {
            ((ManagerActivityLollipop) context).changeAppBarElevation(false,
                    ManagerActivityLollipop.ELEVATION_CALL_IN_PROGRESS);
        }
        if (context instanceof ContactInfoActivityLollipop) {
            ((ContactInfoActivityLollipop) context).changeToolbarLayoutElevation();
        }
    }

    private static void createCallMenuItem(MegaChatCall call, final MenuItem returnCallMenuItem, final LinearLayout layoutCallMenuItem, final Chronometer chronometerMenuItem) {
        Context context = MegaApplication.getInstance().getBaseContext();
        int callStatus = call.getStatus();
        layoutCallMenuItem.setBackground(ContextCompat.getDrawable(context, R.drawable.dark_rounded_chat_own_message));

        if(chronometerMenuItem == null)
            return;

        if (callStatus == MegaChatCall.CALL_STATUS_IN_PROGRESS || callStatus == MegaChatCall.CALL_STATUS_JOINING) {
            if (chronometerMenuItem.getVisibility() == View.VISIBLE) return;
            chronometerMenuItem.setVisibility(View.VISIBLE);
            chronometerMenuItem.setBase(SystemClock.elapsedRealtime() - (call.getDuration() * 1000));
            chronometerMenuItem.start();
            chronometerMenuItem.setFormat(" %s");
        } else {
            if (chronometerMenuItem.getVisibility() == View.GONE) return;
            chronometerMenuItem.stop();
            chronometerMenuItem.setVisibility(View.GONE);
        }
        returnCallMenuItem.setVisible(true);
    }

    /**
     * This method shows or hides the toolbar icon to return a call when a call is in progress
     * and it is in Cloud Drive section, Recents section, Incoming section, Outgoing section or in the chats list.
     *
     * @param returnCallMenuItem  The MenuItem.
     * @param layoutCallMenuItem  The layout of MenuItem.
     * @param chronometerMenuItem The chronometer.
     */
    public static void setCallMenuItem(final MenuItem returnCallMenuItem, final LinearLayout layoutCallMenuItem, final Chronometer chronometerMenuItem) {
        Context context = MegaApplication.getInstance().getBaseContext();
        if (!isScreenInPortrait(context) && participatingInACall()) {
            MegaChatCall currentCall = getCallInProgress();
            if (currentCall != null) {
                createCallMenuItem(currentCall, returnCallMenuItem, layoutCallMenuItem, chronometerMenuItem);
                return;
            }

            ArrayList<Long> calls = getCallsParticipating();
            if (calls != null && !calls.isEmpty()) {
                for (long chatId : calls) {
                    MegaChatCall call = MegaApplication.getInstance().getMegaChatApi().getChatCall(chatId);
                    if (call != null && call.isOnHold()) {
                        createCallMenuItem(call, returnCallMenuItem, layoutCallMenuItem, chronometerMenuItem);
                        break;
                    }
                }
                return;
            }

        }
        hideCallMenuItem(chronometerMenuItem, returnCallMenuItem);
    }

    /**
     * This method is used to hide the current call menu item.
     *
     * @param chronometerMenuItem Chronometer of the MenuItem.
     * @param returnCallMenuItem  MenuItem to be hidden.
     */
    public static void hideCallMenuItem(final Chronometer chronometerMenuItem, final MenuItem returnCallMenuItem) {
        if (chronometerMenuItem != null) {
            chronometerMenuItem.stop();
        }
        if (returnCallMenuItem != null) {
            returnCallMenuItem.setVisible(false);
        }
    }

    /**
     * This method is used to hide the current call banner and update the toolbar elevation.
     *
     * @param context              The Activity context.
     * @param callInProgressChrono Chronometer of the banner.
     * @param callInProgressLayout RelativeLayout to be hidden.
     */
    public static void hideCallWidget(Context context, final Chronometer callInProgressChrono, final RelativeLayout callInProgressLayout) {
        if (callInProgressChrono != null) {
            activateChrono(false, callInProgressChrono, null);
        }
        if (callInProgressLayout != null && callInProgressLayout.getVisibility() == View.VISIBLE) {
            callInProgressLayout.setVisibility(View.GONE);
            if (context instanceof ManagerActivityLollipop) {
                ((ManagerActivityLollipop) context).changeAppBarElevation(false,
                        ManagerActivityLollipop.ELEVATION_CALL_IN_PROGRESS);
            }
            if (context instanceof ContactInfoActivityLollipop) {
                ((ContactInfoActivityLollipop) context).changeToolbarLayoutElevation();
            }
        }
    }

    /**
     * Know if a call in a specific chat is established.
     *
     * @param chatId Id of a chat room that has a call.
     * @return True if the call is established. Otherwise false.
     */
    public static boolean isEstablishedCall(long chatId) {
        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        if (megaChatApi.getChatCall(chatId) == null) return false;

        return megaChatApi.getChatCall(chatId).getStatus() == MegaChatCall.CALL_STATUS_IN_PROGRESS;
    }

    /**
     * Method to activate or deactivate the chronometer of a call without displaying the chronometer separator.
     *
     * @param activateChrono True, if it must be activated. False, if it must be deactivated
     * @param chronometer    The chronometer
     * @param call           The MegaChatCall
     */
    public static void activateChrono(boolean activateChrono, final Chronometer chronometer, MegaChatCall call) {
        activateChrono(activateChrono, chronometer, call, false);
    }

    /**
     * Method to activate or deactivate the chronometer of a call.
     *
     * @param activateChrono True, if it must be activated. False, if it must be deactivated.
     * @param chronometer  The chronometer
     * @param call The MegaChatCall
     * @param isNecessaryToShowChronoSeparator True, if the chronometer separator needs to be shown. False, otherwise
     */
    public static void activateChrono(boolean activateChrono, final Chronometer chronometer, MegaChatCall call, boolean isNecessaryToShowChronoSeparator) {
        if (chronometer == null)
            return;

        if (!activateChrono) {
            chronometer.stop();
            chronometer.setVisibility(View.GONE);
            return;
        }

        if (call != null) {
            chronometer.setBase(SystemClock.elapsedRealtime() - (call.getDuration()* 1000));
            chronometer.start();
            chronometer.setFormat(isNecessaryToShowChronoSeparator ? "· %s" : " %s");
            chronometer.setVisibility(View.VISIBLE);
        }
    }

    public static String milliSecondsToTimer(long milliseconds) {
        String minutesString;
        String secondsString;
        String finalTime = "";
        int hours = (int) (milliseconds / (1000 * 60 * 60));
        int minutes = (int) (milliseconds % (1000 * 60 * 60)) / (1000 * 60);
        int seconds = (int) ((milliseconds % (1000 * 60 * 60)) % (1000 * 60) / 1000);

        if (minutes < 10) {
            minutesString = "0" + minutes;
        } else {
            minutesString = "" + minutes;
        }
        if (seconds < 10) {
            secondsString = "0" + seconds;
        } else {
            secondsString = "" + seconds;
        }
        if (hours > 0) {
            if (hours < 10) {
                finalTime = "0" + hours + ":";
            } else {
                finalTime = "" + hours + ":";
            }
        }
        return finalTime + minutesString + ":" + secondsString;
    }

    public static String callStatusToString(int status) {
        switch (status) {
            case MegaChatCall.CALL_STATUS_INITIAL:
                return "CALL_STATUS_INITIAL";
            case CALL_STATUS_USER_NO_PRESENT:
                return "CALL_STATUS_USER_NO_PRESENT";
            case MegaChatCall.CALL_STATUS_CONNECTING:
                return "CALL_STATUS_CONNECTING";
            case MegaChatCall.CALL_STATUS_JOINING:
                return "CALL_STATUS_JOINING";
            case MegaChatCall.CALL_STATUS_IN_PROGRESS:
                return "CALL_STATUS_IN_PROGRESS";
            case MegaChatCall.CALL_STATUS_TERMINATING_USER_PARTICIPATION:
                return "CALL_STATUS_TERMINATING_USER_PARTICIPATION";
            case MegaChatCall.CALL_STATUS_DESTROYED:
                return "CALL_STATUS_DESTROYED";
            default:
                return String.valueOf(status);
        }
    }

    public static String sessionStatusToString(int status) {
        switch (status) {
            case MegaChatSession.SESSION_STATUS_INVALID:
                return "SESSION_STATUS_INVALID";
            case MegaChatSession.SESSION_STATUS_IN_PROGRESS:
                return "SESSION_STATUS_IN_PROGRESS";
            case MegaChatSession.SESSION_STATUS_DESTROYED:
                return "SESSION_STATUS_DESTROYED";
            default:
                return String.valueOf(status);
        }
    }

    /**
     * Method for showing the appropriate string depending on the value of termination code for the call
     *
     * @param termCode The termination code
     * @return The appropriate string
     */
    public static String terminationCodeForCallToString(int termCode) {
        switch (termCode) {
            case MegaChatCall.TERM_CODE_INVALID:
                return "TERM_CODE_INVALID";
            case MegaChatCall.TERM_CODE_HANGUP:
                return "TERM_CODE_HANGUP";
            case MegaChatCall.TERM_CODE_TOO_MANY_PARTICIPANTS:
                return "TERM_CODE_TOO_MANY_PARTICIPANTS";
            case MegaChatCall.TERM_CODE_ERROR:
                return "TERM_CODE_ERROR";
            case MegaChatCall.TERM_CODE_REJECT:
                return "TERM_CODE_REJECT";
            default:
                return String.valueOf(termCode);
        }
    }

    /**
     * Method for showing the appropriate string depending on the value of end call reason
     *
     * @param endCallReason The end call reason
     * @return The appropriate string
     */
    public static String endCallReasonToString(int endCallReason) {
        switch (endCallReason) {
            case MegaChatCall.END_CALL_REASON_INVALID:
                return "END_CALL_REASON_INVALID";
            case MegaChatCall.END_CALL_REASON_ENDED:
                return "END_CALL_REASON_ENDED";
            case MegaChatCall.END_CALL_REASON_REJECTED:
                return "END_CALL_REASON_REJECTED";
            case MegaChatCall.END_CALL_REASON_NO_ANSWER:
                return "END_CALL_REASON_NO_ANSWER";
            case MegaChatCall.END_CALL_REASON_FAILED:
                return "END_CALL_REASON_FAILED";
            case MegaChatCall.END_CALL_REASON_CANCELLED:
                return "END_CALL_REASON_CANCELLED";
            default:
                return String.valueOf(endCallReason);
        }
    }

    public static boolean isStatusConnected(Context context, long chatId) {
        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        return checkConnection(context) && megaChatApi.getConnectionState() == MegaChatApi.CONNECTED && megaChatApi.getChatConnectionState(chatId) == MegaChatApi.CHAT_CONNECTION_ONLINE;
    }

    public static boolean checkConnection(Context context) {
        if (!isOnline(context)) {
            if (context instanceof ContactInfoActivityLollipop) {
                ((ContactInfoActivityLollipop) context).showSnackbar(SNACKBAR_TYPE, context.getString(R.string.error_server_connection_problem), -1);
            }
            return false;
        }
        return true;
    }

    /**
     * Enabling or disabling local video in a call
     *
     * @param isEnabled True, if video should be enabled. False, if video should be disabled.
     * @param chatId    Chat ID of the call
     * @param listener  MegaChatRequestListenerInterface
     */
    public static void enableOrDisableLocalVideo(boolean isEnabled, long chatId, MegaChatRequestListenerInterface listener) {
        if (isEnabled) {
            MegaApplication.getInstance().getMegaChatApi().enableVideo(chatId, listener);
        } else {
            MegaApplication.getInstance().getMegaChatApi().disableVideo(chatId, listener);
        }
    }

    /**
     * Method to get the call in progress that is not on hold.
     *
     * @return MegaChatCall the call in progress
     */
    public static MegaChatCall getCallInProgress() {
        ArrayList<Long> listCalls = CallUtil.getCallsParticipating();
        if (listCalls != null && listCalls.size() > 0) {
            for (Long chatId : listCalls) {
                MegaChatCall call = MegaApplication.getInstance().getMegaChatApi().getChatCall(chatId);
                if (call != null && !call.isOnHold()) {
                    return call;
                }
            }
        }

        return null;
    }

    public static void disableLocalCamera() {
       MegaChatCall call = getCallInProgress();
       if(call != null){
           enableOrDisableLocalVideo(false, call.getChatid(), new DisableAudioVideoCallListener(MegaApplication.getInstance()));
       }
    }

    public static long isNecessaryDisableLocalCamera() {
        MegaChatCall call = getCallInProgress();
        if (call == null || !call.hasLocalVideo()) {
            return MEGACHAT_INVALID_HANDLE;
        }

        return call.getChatid();
    }

    /**
     * When there is a video call in progress with the video enabled of the current account logged-in,
     * alerts the user if they are sure they want to perform the action in which the camera is involved,
     * since their camera will be disabled in the call.
     *
     * @param activity   current Activity involved
     * @param action     the action to perform. These are the possibilities:
     *                   ACTION_TAKE_PICTURE, TAKE_PICTURE_PROFILE_CODE, ACTION_OPEN_QR
     * @param openScanQR if the action is ACTION_OPEN_QR, it specifies whether to open the "Scan QR" section.
     *                   True if it should open the "Scan QR" section, false otherwise.
     */
    public static void showConfirmationOpenCamera(Activity activity, String action, boolean openScanQR) {
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    logDebug("Open camera and lost the camera in the call");
                    disableLocalCamera();
                    if (activity instanceof ChatActivityLollipop && action.equals(ACTION_TAKE_PICTURE)) {
                        ((ChatActivityLollipop) activity).controlCamera();
                    }
                    if (activity instanceof ManagerActivityLollipop) {
                        switch (action) {
                            case ACTION_OPEN_QR:
                                ((ManagerActivityLollipop) activity).openQR(openScanQR);
                                break;
                            case ACTION_TAKE_PICTURE:
                                takePicture(activity, TAKE_PHOTO_CODE);
                                break;
                            case ACTION_TAKE_PROFILE_PICTURE:
                                takePicture(activity, TAKE_PICTURE_PROFILE_CODE);
                                break;
                        }
                    }
                    if (activity instanceof AddContactActivityLollipop && action.equals(ACTION_OPEN_QR)) {
                        ((AddContactActivityLollipop) activity).initScanQR();
                    }
                    if (activity instanceof InviteContactActivity && action.equals(ACTION_OPEN_QR)) {
                        ((InviteContactActivity) activity).initScanQR();
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        };
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_Mega_MaterialAlertDialog);
        String message = activity.getString(R.string.confirmation_open_camera_on_chat);
        builder.setTitle(R.string.title_confirmation_open_camera_on_chat);
        builder.setMessage(message).setPositiveButton(R.string.context_open_link, dialogClickListener).setNegativeButton(R.string.general_cancel, dialogClickListener).show();
    }

    /**
     * Method to get the default avatar in calls.
     *
     * @param context Context of the Activity.
     * @param peerId  User handle from whom the avatar is obtained.
     * @return Bitmap with the default avatar created.
     */
    public static Bitmap getDefaultAvatarCall(Context context, long peerId) {
        return AvatarUtil.getDefaultAvatar(getColorAvatar(peerId), getUserNameCall(context, peerId),
                dp2px(AVATAR_SIZE_CALLS, context.getResources().getDisplayMetrics()), true);
    }

    /**
     * Method to get the image avatar in calls.
     *
     * @param chat   Chat room identifier where the call is.
     * @param peerId User handle from whom the avatar is obtained.
     * @return Bitmap with the image avatar created.
     */
    public static Bitmap getImageAvatarCall(MegaChatRoom chat, long peerId) {
        String mail = getUserMailCall(chat, peerId);
        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();

        String userHandleString = MegaApiAndroid.userHandleToBase64(peerId);
        String myUserHandleEncoded = MegaApiAndroid.userHandleToBase64(megaChatApi.getMyUserHandle());
        if (userHandleString.equals(myUserHandleEncoded)) {
            return getAvatarBitmap(mail);
        }

        return isTextEmpty(mail) ? getAvatarBitmap(userHandleString)
                : getUserAvatar(userHandleString, mail);
    }

    /**
     * Method to get the email from a handle.
     *
     * @param chat   Chat room identifier.
     * @param peerId User handle from whom the email is obtained.
     * @return The email.
     */
    public static String getUserMailCall(MegaChatRoom chat, long peerId) {
        if (peerId == MegaApplication.getInstance().getMegaChatApi().getMyUserHandle()) {
            return MegaApplication.getInstance().getMegaChatApi().getMyEmail();
        } else {
            return chat.getPeerEmailByHandle(peerId);
        }
    }

    /**
     * Method to get the name from a handle.
     *
     * @param context Activity context.
     * @param peerId  User handle from whom the name is obtained.
     * @return The name.
     */
    public static String getUserNameCall(Context context, long peerId) {
        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        if (peerId == megaChatApi.getMyUserHandle()) {
            return megaChatApi.getMyFullname();
        }

        String nickname = getNicknameContact(peerId);
        if (nickname != null) {
            return nickname;
        }

        return new ChatController(context).getParticipantFullName(peerId);
    }

    /**
     * Method to get the call on hold if it's different than the current call.
     *
     * @param callId The current call ID.
     * @return The call on hold.
     */
    public static MegaChatCall getAnotherCallOnHold(long callId) {
        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        MegaHandleList listCallsInProgress = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_IN_PROGRESS);
        if (listCallsInProgress != null && listCallsInProgress.size() > 0) {
            for (int i = 0; i < listCallsInProgress.size(); i++) {
                MegaChatCall call = megaChatApi.getChatCall(listCallsInProgress.get(i));
                if (call != null && call.isOnHold() && call.getCallId() != callId) {
                    return call;
                }
            }
        }

        MegaHandleList listCallsJoining = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_JOINING);
        if (listCallsJoining != null && listCallsJoining.size() > 0) {
            for (int i = 0; i < listCallsJoining.size(); i++) {
                MegaChatCall call = megaChatApi.getChatCall(listCallsJoining.get(i));
                if (call != null && call.isOnHold() && call.getCallId() != callId) {
                    return call;
                }
            }
        }

        return null;
    }

    /**
     * Retrieve the calls I'm participating in.
     *
     * @return The list of chats IDs with call.
     */
    public static ArrayList<Long> getCallsParticipating() {
        ArrayList<Long> listCalls = new ArrayList<>();
        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();

        MegaHandleList listCallsInProgress = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_IN_PROGRESS);
        if (listCallsInProgress != null && listCallsInProgress.size() > 0) {
            for (int i = 0; i < listCallsInProgress.size(); i++) {
                listCalls.add(listCallsInProgress.get(i));
            }
        }
        MegaHandleList listCallsJoining = megaChatApi.getChatCalls(MegaChatCall.CALL_STATUS_JOINING);
        if (listCallsJoining != null && listCallsJoining.size() > 0) {
            for (int i = 0; i < listCallsJoining.size(); i++) {
                listCalls.add(listCallsJoining.get(i));
            }
        }

        if (listCalls.isEmpty())
            return null;

        return listCalls;
    }

    /**
     * Method to retrieve the chat ID with an active call.
     *
     * @param currentChatId The chat ID with call.
     */
    public static long isAnotherActiveCall(long currentChatId) {
        ArrayList<Long> chatsIDsWithCallActive = getCallsParticipating();
        if (chatsIDsWithCallActive == null || chatsIDsWithCallActive.isEmpty()) {
            return currentChatId;
        }

        MegaChatCall currentCall = MegaApplication.getInstance().getMegaChatApi().getChatCall(currentChatId);
        if (currentCall != null && currentCall.isOnHold()) {
            logDebug("Current call ON HOLD, look for other");
            for (Long anotherChatId : chatsIDsWithCallActive) {
                if (anotherChatId != currentChatId) {
                    MegaChatCall call = MegaApplication.getInstance().getMegaChatApi().getChatCall(anotherChatId);
                    if (call != null && !call.isOnHold()) {
                        logDebug("Another call ACTIVE");
                        return anotherChatId;
                    }
                }
            }
        }
        logDebug("Current call ACTIVE, look for other");
        return currentChatId;
    }

    /**
     * Method to check if there is a call and that it is not on hold before answering it.
     *
     * @param currentChatId The current call.
     * @return The call in progress.
     */
    public static long existsAnotherCall(long currentChatId) {
        ArrayList<Long> chatsIDsWithCallActive = getCallsParticipating();
        if (chatsIDsWithCallActive == null || chatsIDsWithCallActive.isEmpty()) {
            return currentChatId;
        }
        for (Long anotherChatId : chatsIDsWithCallActive) {
            if (anotherChatId != currentChatId) {
                MegaChatCall call = MegaApplication.getInstance().getMegaChatApi().getChatCall(anotherChatId);
                if (call != null && !call.isOnHold()) {
                    return anotherChatId;
                }
            }
        }
        return currentChatId;
    }

    public static PendingIntent getPendingIntentMeetingInProgress(Context context, long chatIdCallToAnswer, int requestCode, boolean isGuest) {
        Intent intentMeeting = new Intent(context, MeetingActivity.class);
        intentMeeting.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intentMeeting.setAction(MEETING_ACTION_IN);
        intentMeeting.putExtra(MEETING_CHAT_ID, chatIdCallToAnswer);
        intentMeeting.putExtra(MEETING_IS_GUEST, isGuest);
        return PendingIntent.getActivity(context, requestCode, intentMeeting, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent getPendingIntentMeetingRinging(Context context, long chatIdCallToAnswer, int requestCode) {
        Intent intentMeeting = new Intent(context, MeetingActivity.class);
        intentMeeting.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intentMeeting.setAction(MEETING_ACTION_RINGING);
        intentMeeting.putExtra(MEETING_CHAT_ID, chatIdCallToAnswer);
        return PendingIntent.getActivity(context, requestCode, intentMeeting, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Method for knowing if the call start button should be enabled or not.
     *
     * @return True, if it should be enabled or false otherwise.
     */
    public static boolean isCallOptionEnabled() {
        return !participatingInACall();
    }

    /**
     * Method to control when a call should be started, whether the chat room should be created or is already created.
     *
     * @param activity           The Activity.
     * @param snackbarShower     The interface to show snackbar.
     * @param user               The mega User.
     * @param passcodeManagement To disable passcode.
     */
    public static void startNewCall(Activity activity, SnackbarShower snackbarShower,
                                    MegaUser user, PasscodeManagement passcodeManagement) {
        if (user == null)
            return;

        MegaChatApiAndroid megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        MegaChatRoom chat = megaChatApi.getChatRoomByUser(user.getHandle());

        MegaChatPeerList peers = MegaChatPeerList.createInstance();
        if (chat == null) {
            logDebug("Chat doesn't exist");
            ArrayList<MegaChatRoom> chats = new ArrayList<>();
            ArrayList<MegaUser> usersNoChat = new ArrayList<>();
            usersNoChat.add(user);
            CreateChatListener listener = new CreateChatListener(
                    CreateChatListener.START_AUDIO_CALL, chats, usersNoChat, activity,
                    snackbarShower);
            if (peers != null) {
                peers.addPeer(user.getHandle(), MegaChatPeerList.PRIV_STANDARD);
                megaChatApi.createChat(false, peers, listener);
            }
        } else if (megaChatApi.getChatCall(chat.getChatId()) != null) {
            logDebug("There is a call, open it");
            openMeetingInProgress(activity, chat.getChatId(), true, passcodeManagement);
        } else if (isStatusConnected(activity, chat.getChatId())) {
            logDebug("There is no call, start it");
            MegaApplication.setUserWaitingForCall(user.getHandle());
            startCallWithChatOnline(activity, chat);
        }
    }

    /**
     * Method to control if the chat is online in order to start a call.
     *
     * @param activity The Activity.
     * @param chatRoom The chatRoom.
     */
    public static void startCallWithChatOnline(Activity activity, MegaChatRoom chatRoom) {
        if (checkPermissionsCall(activity, START_CALL_PERMISSIONS)) {
            MegaApplication.getChatManagement().setSpeakerStatus(chatRoom.getChatId(), false);
            MegaApplication.getInstance().getMegaChatApi().startChatCall(chatRoom.getChatId(), false, true, new StartChatCallListener(activity));
            MegaApplication.setIsWaitingForCall(false);
        }
    }

    /**
     * Method for obtaining the necessary permissions in one call.
     *
     * @param activity       The activity.
     * @param typePermission The type of permission
     * @return True, if you have both permits. False, otherwise.
     */
    public static boolean checkPermissionsCall(Activity activity, int typePermission) {
        boolean hasCameraPermission = hasPermissions(MegaApplication.getInstance().getBaseContext(), Manifest.permission.CAMERA);
        if (!hasCameraPermission) {
            if (activity == null)
                return false;

            if (activity instanceof ManagerActivityLollipop) {
                ((ManagerActivityLollipop) activity).setTypesCameraPermission(typePermission);
            }
            requestPermission(activity, REQUEST_CAMERA, Manifest.permission.CAMERA);
            return false;
        }

        boolean hasRecordAudioPermission = hasPermissions(MegaApplication.getInstance().getBaseContext(), Manifest.permission.RECORD_AUDIO);
        if (!hasRecordAudioPermission) {
            if (activity == null)
                return false;

            if (activity instanceof ManagerActivityLollipop) {
                ((ManagerActivityLollipop) activity).setTypesCameraPermission(typePermission);
            }
            requestPermission(activity, REQUEST_RECORD_AUDIO, Manifest.permission.RECORD_AUDIO);
            return false;
        }

        return true;
    }

    public static void addChecksForACall(long chatId, boolean speakerStatus) {
        MegaApplication.getChatManagement().setSpeakerStatus(chatId, speakerStatus);
    }

    /**
     * Method for removing the incoming call notification.
     *
     * @param callIdIncomingCall The call ID
     */
    public static void clearIncomingCallNotification(long callIdIncomingCall) {
        logDebug("Clear the notification in call: " + callIdIncomingCall);
        if (callIdIncomingCall == MEGACHAT_INVALID_HANDLE)
            return;

        try {
            NotificationManager notificationManager = (NotificationManager) MegaApplication.getInstance().getBaseContext().getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null)
                notificationManager.cancel(getCallNotificationId(callIdIncomingCall));
        } catch (Exception e) {
            logError("EXCEPTION", e);
        }
    }

    /**
     * Method for getting the call notification ID.
     *
     * @param callId The call ID.
     * @return The notification ID.
     */
    public static int getCallNotificationId(long callId) {
        String notificationCallId = MegaApiAndroid.userHandleToBase64(callId);
        return notificationCallId.hashCode() + NOTIFICATION_CALL_IN_PROGRESS;
    }

    /**
     * Method to check if the chat is online
     *
     * @param newState The state of chat
     * @param chatRoom The MegaChatRoom
     * @return True, if the chat is connected and a call can be started. False, otherwise
     */
    public static boolean isChatConnectedInOrderToInitiateACall(int newState, MegaChatRoom chatRoom) {
        return MegaApplication.isWaitingForCall() && newState == MegaChatApi.CHAT_CONNECTION_ONLINE
                && chatRoom != null && chatRoom.getPeerHandle(0) != MEGACHAT_INVALID_HANDLE &&
                chatRoom.getPeerHandle(0) == MegaApplication.getUserWaitingForCall();
    }

    /**
     * Method to display a dialogue informing the user that he/she cannot start or join a meeting while on a call in progress.
     *
     * @param context            Context of Activity
     * @param message            String with the text to show in the dialogue
     * @param passcodeManagement To disable passcode.
     */
    public static void showConfirmationInACall(Context context, String message, PasscodeManagement passcodeManagement) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setMessage(message)
                .setPositiveButton(R.string.general_ok, (dialog, which) -> {
                    if (context instanceof OpenLinkActivity) {
                        returnActiveCall(context, passcodeManagement);
                    }
                })
                .show();
    }

    /**
     * Method to know if the meeting hint should be shown
     *
     * @param context The Activity context
     * @param value   The identifier of that preference
     * @return True, if it must be shown. False, if not
     */
    public static boolean shouldShowMeetingHint(Context context, String value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return !sharedPreferences.getBoolean(value, false);
    }

    /**
     * Set the hint to be shown
     *
     * @param context The Activity context
     * @param value   The identifier of that preference
     */
    public static void hintShown(Context context, String value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putBoolean(value, true).apply();
    }

    /**
     * Method to know if a meeting has ended
     *
     * @param list MegaHandleList with the call ID
     * @return True, if the meeting is finished. False, if not.
     */
    public static boolean isMeetingEnded(MegaHandleList list) {
        return list == null || list.get(0) == MEGACHAT_INVALID_HANDLE;
    }

    /**
     * Method to know if I am participating in this meeting
     *
     * @param chatId Chat ID of the meeting
     * @return True, f I am participating in this meeting. False, if not.
     */
    public static boolean amIParticipatingInThisMeeting(long chatId) {
        MegaChatCall call = MegaApplication.getInstance().getMegaChatApi().getChatCall(chatId);
        return call != null && call.getStatus() != MegaChatCall.CALL_STATUS_DESTROYED &&
                call.getStatus() != MegaChatCall.CALL_STATUS_TERMINATING_USER_PARTICIPATION &&
                call.getStatus() != CALL_STATUS_USER_NO_PRESENT;
    }

    /**
     * Method to know if I am participating in another call
     *
     * @param chatId Chat ID of the current call
     * @return True, f I am participating in another call. False, if not.
     */
    public static boolean amIParticipatingInAnotherCall(long chatId) {
        return CallUtil.getAnotherCallParticipating(chatId) != MEGACHAT_INVALID_HANDLE;
    }

    /**
     * Method for processing a meeting link when meeting is in progress
     *
     * @param context               The context of Activity
     * @param activity              The Activity
     * @param chatId                the Chat Id of the meeting link
     * @param isFromOpenChatPreview True, if I come from doing openChatPreview. False, if I came from doing checkChatLink.
     * @param link                  The meeting link
     * @param list                  The MegaHandleList with the call ID
     * @param titleChat             The title of the chat
     * @param passcodeManagement    To disable passcode.
     */
    public static void checkMeetingInProgress(Context context, LoadPreviewListener.OnPreviewLoadedCallback activity, long chatId, boolean isFromOpenChatPreview, String link, MegaHandleList list, String titleChat, boolean alreadyExist, long publicChatHandle, PasscodeManagement passcodeManagement) {
        if (amIParticipatingInThisMeeting(chatId)) {
            logDebug("I am participating in the meeting of this meeting link");
            returnCall(context, chatId, passcodeManagement);
            return;
        }

        if (amIParticipatingInAnotherCall(chatId)) {
            logDebug("I am participating in another call");
            showConfirmationInACall(context, StringResourcesUtils.getString(R.string.text_join_call), passcodeManagement);
            return;
        }

        if (isFromOpenChatPreview) {
            MegaChatCall call = MegaApplication.getInstance().getMegaChatApi().getChatCall(chatId);
            if (call == null || call.getStatus() == CALL_STATUS_USER_NO_PRESENT) {
                logDebug("Call id: " + list.get(0) + ". It's a meeting, open to join");
                CallUtil.openMeetingToJoin(context, chatId, titleChat, link, alreadyExist ? publicChatHandle : MEGACHAT_INVALID_HANDLE, alreadyExist, passcodeManagement);
            } else {
                logDebug("Call id: " + list.get(0) + ". Return to call");
                returnCall(context, chatId, passcodeManagement);
            }
            return;
        }

        logDebug("Open chat preview");
        MegaApplication.getInstance().getMegaChatApi().openChatPreview(link, new LoadPreviewListener(context, activity, CHECK_LINK_TYPE_MEETING_LINK));
    }

    /**
     * Method that performs the necessary actions when there is an incoming call.
     *
     * @param listAllCalls       List of all current calls
     * @param incomingCallChatId Chat ID of incoming call
     * @param callStatus         Call Status
     */
    public static void incomingCall(MegaHandleList listAllCalls, long incomingCallChatId, int callStatus) {
        logDebug("Chat ID of incoming call is " + incomingCallChatId);
        if (!MegaApplication.getInstance().getMegaApi().isChatNotifiable(incomingCallChatId) ||
                MegaApplication.getChatManagement().isNotificationShown(incomingCallChatId)){
            logDebug("The chat is not notifiable or the notification is already being displayed");
            return;
        }

        MegaChatRoom chatRoom = MegaApplication.getInstance().getMegaChatApi().getChatRoom(incomingCallChatId);
        if (chatRoom == null) {
            logError("The chat does not exist");
            return;
        }

        if (!chatRoom.isMeeting() || !MegaApplication.getChatManagement().isOpeningMeetingLink(incomingCallChatId)) {
            logError("It is necessary to check the number of current calls");
            controlNumberOfCalls(listAllCalls, callStatus, incomingCallChatId);
        }
    }

    /**
     * Method that performs the necessary actions when there is an outgoing call or incoming call.
     *
     * @param chatId           Chat ID
     * @param callId           Call ID
     * @param typeAudioManager audio Manager type
     */
    public static void ongoingCall(long chatId, long callId, int typeAudioManager) {
        AppRTCAudioManager rtcAudioManager = MegaApplication.getInstance().getAudioManager();
        if (rtcAudioManager != null && rtcAudioManager.getTypeAudioManager() == typeAudioManager)
            return;

        MegaChatRoom chatRoom = MegaApplication.getInstance().getMegaChatApi().getChatRoom(chatId);
        if (chatRoom == null) {
            logError("The chat does not exist");
            return;
        }

        logDebug("Controlling outgoing/in progress call");
        if (typeAudioManager == AUDIO_MANAGER_CALL_OUTGOING && chatRoom.isMeeting()) {
            clearIncomingCallNotification(callId);
            typeAudioManager = AUDIO_MANAGER_CALL_IN_PROGRESS;
        }

        MegaApplication.getInstance().createOrUpdateAudioManager(MegaApplication.getChatManagement().getSpeakerStatus(chatId), typeAudioManager);
    }

    /**
     * Method to control whether there is one or more calls.
     *
     * @param listAllCalls List of all current calls
     * @param callStatus   Call Status
     * @param incomingCallChatId Chat ID of incoming call
     */
    private static void controlNumberOfCalls(MegaHandleList listAllCalls, int callStatus, long incomingCallChatId) {
        if (listAllCalls.size() == 1) {
            MegaApplication.getInstance().checkOneCall(incomingCallChatId);
        } else {
            MegaApplication.getInstance().checkSeveralCall(listAllCalls, callStatus, true, incomingCallChatId);
        }
    }

    /**
     * Check if an incoming call is a one-to-one call
     *
     * @param chatRoom MegaChatRoom of the call
     * @return True, if it is a one-to-one call. False, if it is a group call or meeting
     */
    public static boolean isOneToOneCall(MegaChatRoom chatRoom) {
        return !chatRoom.isGroup() && !chatRoom.isMeeting();
    }

    /**
     * Get incoming call notification title
     *
     * @param chatRoom MegaChatRoom of the call
     * @return Notification title
     */
    public static String getIncomingCallNotificationTitle(MegaChatRoom chatRoom) {
        return getString(isOneToOneCall(chatRoom)
                ? R.string.title_notification_incoming_individual_audio_call
                : R.string.title_notification_incoming_group_call);
    }

    /**
     * Method to create collapsed or expanded remote views for a customised incoming call notification.
     *
     * @param layoutId     ID of layout
     * @param chatToAnswer MegaChatRoom of the call
     * @param avatarIcon   Bitmap with the chat Avatar
     * @return The RemoteViews created
     */
    public static RemoteViews collapsedAndExpandedIncomingCallNotification(Context context, int layoutId, MegaChatRoom chatToAnswer, Bitmap avatarIcon) {
        Bitmap statusIcon = CallUtil.isOneToOneCall(chatToAnswer) ? getStatusBitmap(MegaApplication.getInstance().getMegaChatApi().getUserOnlineStatus(chatToAnswer.getPeerHandle(0))) : null;
        String titleChat = getTitleChat(chatToAnswer);
        String titleCall = CallUtil.getIncomingCallNotificationTitle(chatToAnswer);

        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
        views.setTextViewText(R.id.chat_title, titleChat);
        views.setTextViewText(R.id.call_title, titleCall);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || avatarIcon == null) {
            views.setViewVisibility(R.id.avatar_layout, GONE);
        } else {
            views.setImageViewBitmap(R.id.avatar_image, avatarIcon);
            views.setViewVisibility(R.id.avatar_layout, View.VISIBLE);
        }

        if (statusIcon != null) {
            views.setImageViewBitmap(R.id.chat_status, statusIcon);
            views.setViewVisibility(R.id.chat_status, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.chat_status, GONE);
        }

        return views;
    }

    /**
     * Method to control when an attempt is made to initiate a call from a contact option
     *
     * @param context The Activity context
     * @param passcodeManagement    To disable passcode.
     * @return True, if the call can be started. False, otherwise.
     */
    public static boolean canCallBeStartedFromContactOption(Activity context, PasscodeManagement passcodeManagement) {
        if (MegaApplication.getInstance().getStorageState() == STORAGE_STATE_PAYWALL) {
            showOverDiskQuotaPaywallWarning();
            return false;
        }

        if (CallUtil.participatingInACall()) {
            showConfirmationInACall(context, StringResourcesUtils.getString(R.string.ongoing_call_content), passcodeManagement);
            return false;
        }

        return checkPermissionsCall(context, INVALID_TYPE_PERMISSIONS);
    }
}
