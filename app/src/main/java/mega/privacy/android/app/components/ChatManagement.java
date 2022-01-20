package mega.privacy.android.app.components;

import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_USER_PRESENT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.preference.PreferenceManager;
import com.jeremyliao.liveeventbus.LiveEventBus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.listeners.ChatRoomListener;
import mega.privacy.android.app.meeting.listeners.DisableAudioVideoCallListener;
import mega.privacy.android.app.utils.CallUtil;
import mega.privacy.android.app.utils.VideoCaptureUtils;
import nz.mega.sdk.MegaChatCall;
import nz.mega.sdk.MegaChatListItem;
import nz.mega.sdk.MegaChatRequestListenerInterface;
import nz.mega.sdk.MegaChatRoom;

import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_JOINED_SUCCESSFULLY;
import static mega.privacy.android.app.constants.EventConstants.EVENT_ENABLE_OR_DISABLE_LOCAL_VIDEO_CHANGE;
import static mega.privacy.android.app.constants.EventConstants.EVENT_NOT_OUTGOING_CALL;
import static mega.privacy.android.app.utils.CallUtil.clearIncomingCallNotification;
import static mega.privacy.android.app.utils.CallUtil.existsAnOngoingOrIncomingCall;
import static mega.privacy.android.app.utils.CallUtil.participatingInACall;
import static mega.privacy.android.app.utils.Constants.KEY_IS_SHOWED_WARNING_MESSAGE;
import static mega.privacy.android.app.utils.LogUtil.logDebug;
import static mega.privacy.android.app.utils.LogUtil.logError;
import static mega.privacy.android.app.utils.TextUtil.isTextEmpty;
import static nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE;
import static nz.mega.sdk.MegaChatCall.CALL_STATUS_USER_NO_PRESENT;

public class ChatManagement {

    private final ChatRoomListener chatRoomListener;
    private final MegaApplication app;

    // List of chat ids to control if a chat is already joining.
    private final List<Long> joiningChatIds = new ArrayList<>();
    // List of chat ids to control if a chat is already leaving.
    private final List<Long> leavingChatIds = new ArrayList<>();
    // List of chats ids to control if I'm trying to join the call
    private final List<Long> joiningCallChatIds = new ArrayList<>();
    // List of chats with video activated in the call
    private final HashMap<Long, Boolean> hashMapVideo = new HashMap<>();
    // List of chats with speaker activated in the call
    private final HashMap<Long, Boolean> hashMapSpeaker = new HashMap<>();
    // List of outgoing calls
    private final HashMap<Long, Boolean> hashMapOutgoingCall = new HashMap<>();
    // List of chats in which a meeting is being opened via a link
    private final HashMap<Long, Boolean> hashOpeningMeetingLink = new HashMap<>();
    // List of group chats being participated in
    private final ArrayList<Long> currentActiveGroupChat = new ArrayList<>();
    // List of calls for which an incoming call notification has already been shown
    private final ArrayList<Long> notificationShown = new ArrayList<>();
    // List of pending messages ids in which its transfer is to be cancelled
    private final HashMap<Integer, Long> hashMapPendingMsgsToBeCancelled = new HashMap<>();
    // List of messages id to delete
    private final ArrayList<Long> msgsToBeDeleted = new ArrayList<>();

    // If this has a valid value, means there is a pending chat link to join.
    private String pendingJoinLink;

    private boolean inTemporaryState = false;
    private boolean isDisablingLocalVideo = false;
    private boolean isScreenOn = true;
    private boolean isScreenBroadcastRegister = false;

    public ChatManagement() {
        chatRoomListener = new ChatRoomListener();
        app = MegaApplication.getInstance();
    }

    /**
     * Method for open a particular chatRoom.
     *
     * @param chatId The chat ID.
     * @return True, if it has been done correctly. False if not.
     */
    public boolean openChatRoom(long chatId) {
        closeChatRoom(chatId);
        return app.getMegaChatApi().openChatRoom(chatId, chatRoomListener);
    }

    /**
     * Method for close a particular chatRoom.
     *
     * @param chatId The chat ID.
     */
    private void closeChatRoom(long chatId) {
        app.getMegaChatApi().closeChatRoom(chatId, chatRoomListener);
    }

    public String getPendingJoinLink() {
        return pendingJoinLink;
    }

    public boolean isPendingJoinLink() {
        return !isTextEmpty(pendingJoinLink);
    }

    public void setPendingJoinLink(String link) {
        pendingJoinLink = link;
    }

    public void addJoiningChatId(long joiningChatId) {
        joiningChatIds.add(joiningChatId);
    }

    public void removeJoiningChatId(long joiningChatId) {
        if (joiningChatIds.remove(joiningChatId)) {
            app.sendBroadcast(new Intent(BROADCAST_ACTION_JOINED_SUCCESSFULLY));
        }
    }

    public boolean isAlreadyJoining(long joinChatId) {
        return joiningChatIds.contains(joinChatId);
    }

    public void addLeavingChatId(long leavingChatId) {
        leavingChatIds.add(leavingChatId);
    }

    public void removeLeavingChatId(long leavingChatId) {
        leavingChatIds.remove(leavingChatId);
    }

    public boolean isAlreadyLeaving(long leaveChatId) {
        return leavingChatIds.contains(leaveChatId);
    }

    public void addJoiningCallChatId(long joiningCallChatId) {
        joiningCallChatIds.add(joiningCallChatId);
    }

    public void removeJoiningCallChatId(long joiningCallChatId) {
        joiningCallChatIds.remove(joiningCallChatId);
    }

    public boolean isAlreadyJoiningCall(long joiningCallChatId) {
        return joiningCallChatIds.contains(joiningCallChatId);
    }

    public boolean getSpeakerStatus(long chatId) {
        boolean entryExists = hashMapSpeaker.containsKey(chatId);
        if (entryExists) {
            return hashMapSpeaker.get(chatId);
        }

        setSpeakerStatus(chatId, false);
        return false;
    }

    public void setSpeakerStatus(long chatId, boolean speakerStatus) {
        hashMapSpeaker.put(chatId, speakerStatus);
    }

    public boolean getVideoStatus(long chatId) {
        boolean entryExists = hashMapVideo.containsKey(chatId);
        if (entryExists) {
            return hashMapVideo.get(chatId);
        }
        setVideoStatus(chatId, false);
        return false;
    }

    public void setVideoStatus(long chatId, boolean videoStatus) {
        hashMapVideo.put(chatId, videoStatus);
    }

    public void setPendingMessageToBeCancelled(int tag, long chatId) {
        if (getPendingMsgIdToBeCancelled(tag) == MEGACHAT_INVALID_HANDLE) {
            hashMapPendingMsgsToBeCancelled.put(tag, chatId);
        }
    }

    public long getPendingMsgIdToBeCancelled(int tag) {
        boolean entryExists = hashMapPendingMsgsToBeCancelled.containsKey(tag);
        if (entryExists) {
            return hashMapPendingMsgsToBeCancelled.get(tag);
        }

        return MEGACHAT_INVALID_HANDLE;
    }

    public void removePendingMsgToBeCancelled(int tag) {
        if (getPendingMsgIdToBeCancelled(tag) != MEGACHAT_INVALID_HANDLE) {
            hashMapPendingMsgsToBeCancelled.remove(tag);
        }
    }

    public void addMsgToBeDelete(long pMsgId) {
        if (!isMsgToBeDelete(pMsgId)) {
            msgsToBeDeleted.add(pMsgId);
        }
    }

    public boolean isMsgToBeDelete(long pMsgId) {
        if (msgsToBeDeleted.isEmpty())
            return false;

        return msgsToBeDeleted.contains(pMsgId);
    }

    public void removeMsgToDelete(long pMsgId) {
        if (isMsgToBeDelete(pMsgId)) {
            msgsToBeDeleted.remove(pMsgId);
        }
    }

    public boolean isOpeningMeetingLink(long chatId) {
        if (chatId != MEGACHAT_INVALID_HANDLE) {
            boolean entryExists = hashOpeningMeetingLink.containsKey(chatId);
            if (entryExists) {
                return hashOpeningMeetingLink.get(chatId);
            }
        }
        return false;
    }

    public void setOpeningMeetingLink(long chatId, boolean isOpeningMeetingLink) {
        if (chatId != MEGACHAT_INVALID_HANDLE) {
            hashOpeningMeetingLink.put(chatId, isOpeningMeetingLink);
        }
    }

    public boolean isRequestSent(long callId) {
        boolean entryExists = hashMapOutgoingCall.containsKey(callId);
        if (entryExists) {
            return hashMapOutgoingCall.get(callId);
        }

        return false;
    }

    public void setRequestSentCall(long callId, boolean isRequestSent) {
        if (isRequestSent(callId) == isRequestSent)
            return;

        hashMapOutgoingCall.put(callId, isRequestSent);
        if (!isRequestSent) {
            LiveEventBus.get(EVENT_NOT_OUTGOING_CALL, Long.class).post(callId);
        }
    }

    public void addCurrentGroupChat(long chatId) {
        if (currentActiveGroupChat.isEmpty() || !currentActiveGroupChat.contains(chatId)) {
            currentActiveGroupChat.add(chatId);
        }
    }

    public void addNotificationShown(long chatId) {
        if (isNotificationShown(chatId)){
            return;
        }

        notificationShown.add(chatId);
    }

    public void removeNotificationShown(long chatId) {
        if (isNotificationShown(chatId)) {
            notificationShown.remove(chatId);
        }
    }

    public boolean isNotificationShown(long chatId) {
        if (notificationShown.isEmpty())
            return false;

        return notificationShown.contains(chatId);
    }

    public void removeActiveChatAndNotificationShown(long chatId) {
        currentActiveGroupChat.remove(chatId);
        removeNotificationShown(chatId);
    }

    public void removeStatusVideoAndSpeaker(long chatId) {
        hashMapSpeaker.remove(chatId);
        hashMapVideo.remove(chatId);
    }

    public boolean isInTemporaryState() {
        return inTemporaryState;
    }

    public void setInTemporaryState(boolean inTemporaryState) {
        this.inTemporaryState = inTemporaryState;
    }

    public boolean isDisablingLocalVideo() {
        return isDisablingLocalVideo;
    }

    public void setDisablingLocalVideo(boolean disablingLocalVideo) {
        isDisablingLocalVideo = disablingLocalVideo;
    }

    /**
     * Method to control when a call has ended
     *
     * @param callId   Call ID
     * @param chatId   Chat ID
     */
    public void controlCallFinished(long callId, long chatId) {
        if (CallUtil.getCallsParticipating() == null || CallUtil.getCallsParticipating().size() == 0) {
            MegaApplication.getInstance().unregisterProximitySensor();
        }

        clearIncomingCallNotification(callId);
        removeValues(chatId);
        removeStatusVideoAndSpeaker(chatId);
        setRequestSentCall(callId, false);
        unregisterScreenReceiver();
    }

    /**
     * Method to remove the audio manager and reset video and speaker settings
     *
     * @param chatId Chat ID
     */
    public void removeValues(long chatId) {
        PreferenceManager.getDefaultSharedPreferences(MegaApplication.getInstance().getApplicationContext()).edit().remove(KEY_IS_SHOWED_WARNING_MESSAGE + chatId).apply();

        if (!existsAnOngoingOrIncomingCall()) {
            MegaApplication.getInstance().removeRTCAudioManager();
            MegaApplication.getInstance().removeRTCAudioManagerRingIn();
        } else if (participatingInACall()) {
            MegaApplication.getInstance().removeRTCAudioManagerRingIn();
        }
    }

    /**
     * Method to control whether or not to display the incoming call notification when I am added to a group and a call is in progress.
     *
     * @param chatId Chat ID of the new group chat
     */
    public void checkActiveGroupChat(Long chatId) {
        if (currentActiveGroupChat.isEmpty() || !currentActiveGroupChat.contains(chatId)) {
            MegaChatCall call = app.getMegaChatApi().getChatCall(chatId);
            if (call == null) {
                logError("Call is null");
                return;
            }

            if(call.getStatus() == CALL_STATUS_USER_NO_PRESENT){
                addCurrentGroupChat(chatId);
                checkToShowIncomingGroupCallNotification(call, chatId);
            }
        }
    }

    /**
     * Method that displays the incoming call notification when I am added to a group that has a call in progress
     *
     * @param call   The call in this chat room
     * @param chatId The chat ID
     */
    public void checkToShowIncomingGroupCallNotification(MegaChatCall call, long chatId) {
        if(call.isRinging() || isNotificationShown(chatId)){
            logDebug("Call is ringing or notification is shown");
            return;
        }

        MegaChatRoom chatRoom = app.getMegaChatApi().getChatRoom(chatId);
        if (chatRoom == null) {
            logError("Chat is null");
            return;
        }

        if (isOpeningMeetingLink(chatId)) {
            logDebug("Opening meeting link, don't show notification");
            return;
        }

        logDebug("Show incoming call notification");
        app.showOneCallNotification(call);
    }

    public void registerScreenReceiver(){
        if(isScreenBroadcastRegister)
            return;

        IntentFilter filterScreen = new IntentFilter();
        filterScreen.addAction(ACTION_SCREEN_OFF);
        filterScreen.addAction(ACTION_USER_PRESENT);
        MegaApplication.getInstance().registerReceiver(screenOnOffReceiver, filterScreen);
        isScreenBroadcastRegister = true;
    }

    public void unregisterScreenReceiver(){
        if(!isScreenBroadcastRegister)
            return;

        MegaApplication.getInstance().unregisterReceiver(screenOnOffReceiver);
        isScreenBroadcastRegister = false;
    }

    /**
     * Broadcast for controlling changes in screen.
     */
    BroadcastReceiver screenOnOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null)
                return;

            MegaChatCall callInProgress = CallUtil.getCallInProgress();
            if (callInProgress == null || (callInProgress.getStatus() != MegaChatCall.CALL_STATUS_JOINING &&
                    callInProgress.getStatus() != MegaChatCall.CALL_STATUS_IN_PROGRESS)) {
                return;
            }

            if (!getVideoStatus(callInProgress.getChatid())) {
                setInTemporaryState(false);
                return;
            }

            switch (intent.getAction()) {
                case ACTION_SCREEN_OFF:
                    MegaApplication.getInstance().muteOrUnmute(true);
                    setDisablingLocalVideo(false);
                    setInTemporaryState(true);
                    if (callInProgress.hasLocalVideo() && !isDisablingLocalVideo) {
                        logDebug("Screen locked, local video is going to be disabled");
                        isScreenOn = false;
                        CallUtil.enableOrDisableLocalVideo(false, callInProgress.getChatid(), new DisableAudioVideoCallListener(MegaApplication.getInstance()));
                    }
                    break;

                case ACTION_USER_PRESENT:
                    setDisablingLocalVideo(false);
                    setInTemporaryState(false);
                    if (!callInProgress.hasLocalVideo() && !isDisablingLocalVideo) {
                        logDebug("Screen unlocked, local video is going to be enabled");
                        isScreenOn = true;
                        CallUtil.enableOrDisableLocalVideo(true, callInProgress.getChatid(), new DisableAudioVideoCallListener(MegaApplication.getInstance()));
                    }
                    break;
            }
        }
    };

    /**
     * Method for checking when there are changes in the proximity sensor
     *
     * @param isNear True, if the device is close to the ear. False, if it is far away
     */
    public void controlProximitySensor(boolean isNear) {
        MegaChatCall call = CallUtil.getCallInProgress();
        if (call == null || (call.getStatus() != MegaChatCall.CALL_STATUS_JOINING &&
                call.getStatus() != MegaChatCall.CALL_STATUS_IN_PROGRESS) || !isScreenOn || !VideoCaptureUtils.isFrontCameraInUse())
            return;

        if (!getVideoStatus(call.getChatid())) {
            setInTemporaryState(false);
        } else if (isNear) {
            logDebug("Proximity sensor, video off");
            setDisablingLocalVideo(false);
            setInTemporaryState(true);
            LiveEventBus.get(EVENT_ENABLE_OR_DISABLE_LOCAL_VIDEO_CHANGE, Boolean.class).post(false);
            if (call.hasLocalVideo() && !isDisablingLocalVideo) {
                CallUtil.enableOrDisableLocalVideo(false, call.getChatid(), new DisableAudioVideoCallListener(MegaApplication.getInstance()));
            }
        } else {
            logDebug("Proximity sensor, video on");
            setDisablingLocalVideo(false);
            setInTemporaryState(false);
            LiveEventBus.get(EVENT_ENABLE_OR_DISABLE_LOCAL_VIDEO_CHANGE, Boolean.class).post(true);
            if (!call.hasLocalVideo() && !isDisablingLocalVideo) {
                CallUtil.enableOrDisableLocalVideo(true, call.getChatid(), new DisableAudioVideoCallListener(MegaApplication.getInstance()));
            }
        }
    }

    /**
     * Method to answer incoming call or joining in a in progress call
     *
     * @param chatId      The chat id of a chat related
     * @param enableVideo True, if the video should be enabled. False, otherwise
     * @param enableAudio True, if the audio should be enabled. False, otherwise
     * @param speakerStatus True, if the speaker should be enabled. False, otherwise
     * @param listener    MegaChatRequestListenerInterface
     */
    public void answerChatCall(long chatId, boolean enableVideo, boolean enableAudio, boolean speakerStatus, MegaChatRequestListenerInterface listener) {
        if (CallUtil.amIParticipatingInThisMeeting(chatId)) {
            logDebug("Already participating in this call");
            return;
        }

        if (isAlreadyJoiningCall(chatId)) {
            logDebug("AnswerChatCall already done");
            return;
        }

        CallUtil.addChecksForACall(chatId, speakerStatus);
        addJoiningCallChatId(chatId);
        logDebug("Answering call ...");
        app.getMegaChatApi().answerChatCall(chatId, enableVideo, enableAudio, listener);
    }
}
