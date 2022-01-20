package mega.privacy.android.app.meeting;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.jeremyliao.liveeventbus.LiveEventBus;

import java.io.File;
import java.util.ArrayList;

import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.R;
import mega.privacy.android.app.lollipop.controllers.ChatController;
import mega.privacy.android.app.utils.StringResourcesUtils;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaApiJava;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaChatCall;
import nz.mega.sdk.MegaChatRoom;

import static mega.privacy.android.app.constants.EventConstants.EVENT_CALL_ANSWERED_IN_ANOTHER_CLIENT;
import static mega.privacy.android.app.constants.EventConstants.EVENT_CALL_ON_HOLD_CHANGE;
import static mega.privacy.android.app.constants.EventConstants.EVENT_CALL_STATUS_CHANGE;
import static mega.privacy.android.app.constants.EventConstants.EVENT_CHAT_TITLE_CHANGE;
import static mega.privacy.android.app.constants.EventConstants.EVENT_ENTER_IN_MEETING;
import static mega.privacy.android.app.utils.AvatarUtil.*;
import static mega.privacy.android.app.utils.CacheFolderManager.*;
import static mega.privacy.android.app.utils.CallUtil.*;
import static mega.privacy.android.app.utils.ChatUtil.*;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.FileUtil.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.TextUtil.isTextEmpty;
import static nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE;

public class CallService extends Service{

    MegaApplication app;
    MegaApiAndroid megaApi;
    MegaChatApiAndroid megaChatApi;
    private long currentChatId;

    private NotificationCompat.Builder mBuilderCompat;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mBuilderCompatO;

    private final String notificationChannelId = NOTIFICATION_CHANNEL_INPROGRESS_MISSED_CALLS_ID;

    private ChatController chatC;

    /**
     * If is in meeting fragment.
     */
    private boolean isInMeeting = true;

    private final Observer<MegaChatCall> callStatusObserver = call -> {
        int callStatus = call.getStatus();
        logDebug("Call status " + callStatusToString(callStatus)+" current chat = "+currentChatId);
        switch (callStatus) {
            case MegaChatCall.CALL_STATUS_USER_NO_PRESENT:
            case MegaChatCall.CALL_STATUS_IN_PROGRESS:
                updateNotificationContent();
                break;
            case MegaChatCall.CALL_STATUS_TERMINATING_USER_PARTICIPATION:
            case MegaChatCall.CALL_STATUS_DESTROYED:
                removeNotification(call.getChatid());
                break;
        }
    };

    private final Observer<MegaChatCall> callOnHoldObserver = call -> checkAnotherActiveCall();

    private final Observer<MegaChatRoom> titleMeetingChangeObserver = chat -> {
        if (currentChatId == chat.getChatId() && chat.isGroup()) {
            updateNotificationContent();
        }
    };

    private final Observer<Boolean> isInMeetingObserver = b -> {
        isInMeeting = b;
        updateNotificationContent();
    };

    private final Observer<Long> callAnsweredInAnotherClientObserver = chatId -> {
        if (currentChatId == chatId) {
            stopSelf();
        }
    };

    public void onCreate() {
        super.onCreate();
        app = (MegaApplication) getApplication();
        megaApi = app.getMegaApi();
        megaChatApi = app.getMegaChatApi();

        chatC = new ChatController(this);

        mBuilderCompat = new NotificationCompat.Builder(this, notificationChannelId);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        LiveEventBus.get(EVENT_CALL_STATUS_CHANGE, MegaChatCall.class).observeForever(callStatusObserver);
        LiveEventBus.get(EVENT_CALL_ON_HOLD_CHANGE, MegaChatCall.class).observeForever(callOnHoldObserver);
        LiveEventBus.get(EVENT_CHAT_TITLE_CHANGE, MegaChatRoom.class).observeForever(titleMeetingChangeObserver);
        LiveEventBus.get(EVENT_ENTER_IN_MEETING, Boolean.class).observeForever(isInMeetingObserver);
        LiveEventBus.get(EVENT_CALL_ANSWERED_IN_ANOTHER_CLIENT, Long.class).observeForever(callAnsweredInAnotherClientObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
        }

        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                currentChatId = extras.getLong(CHAT_ID, MEGACHAT_INVALID_HANDLE);
                logDebug("Chat handle to call: " + currentChatId);
            }
        }

        if(currentChatId == MEGACHAT_INVALID_HANDLE)
            stopSelf();

        if (MegaApplication.getOpenCallChatId() != currentChatId) {
            MegaApplication.setOpenCallChatId(currentChatId);
        }
        showCallInProgressNotification();
        return START_NOT_STICKY;
    }

    private void checkAnotherActiveCall(){
        long activeCall = isAnotherActiveCall(currentChatId);
        if(currentChatId == activeCall){
            updateNotificationContent();
        }else{
            updateCall(activeCall);
        }
    }

    private void updateNotificationContent() {
        logDebug("Updating notification");
        MegaChatRoom chat = megaChatApi.getChatRoom(currentChatId);
        MegaChatCall call = megaChatApi.getChatCall(currentChatId);
        if (call == null || chat == null) return;

        int notificationId = getCallNotificationId(call.getCallId());

        Notification notif;
        String contentText = "";
        PendingIntent intentCall = null;

        if (call.getStatus() == MegaChatCall.CALL_STATUS_USER_NO_PRESENT && call.isRinging()) {
            contentText = StringResourcesUtils.getString(R.string.title_notification_incoming_call);
            intentCall = getPendingIntentMeetingRinging(this, currentChatId, notificationId + 1);
        } else if (call.getStatus() == MegaChatCall.CALL_STATUS_IN_PROGRESS) {
            if(call.isOnHold()) {
                contentText = StringResourcesUtils.getString(R.string.call_on_hold);
            } else {
                contentText = StringResourcesUtils.getString(R.string.title_notification_call_in_progress);
            }

            // Quit in meeting page. Then can return to the call by tapping the notification.
            if(!isInMeeting) {
                boolean isGuest = megaApi.isEphemeralPlusPlus();
                intentCall = getPendingIntentMeetingInProgress(this, currentChatId, notificationId + 1, isGuest);
            } else {
                // An empty PendingIntent, tapping it can collapse status bar.
                intentCall = PendingIntent.getBroadcast(this, 0, new Intent(""),PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }

        String title = getTitleChat(chat);
        Bitmap largeIcon = null;
        if(chat.isGroup()){
            largeIcon = createDefaultAvatar(-1, title);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (largeIcon != null) {
                mBuilderCompatO.setLargeIcon(largeIcon);
            }
            mBuilderCompatO.setContentTitle(title);

            if (!isTextEmpty(contentText)) {
                mBuilderCompatO.setContentText(contentText);
            }

            mBuilderCompatO.setContentIntent(intentCall);

            notif = mBuilderCompatO.build();
        } else {
            if (largeIcon != null) {
                mBuilderCompat.setLargeIcon(largeIcon);
            }
            mBuilderCompat.setContentTitle(title);

            if (!isTextEmpty(contentText)) {
                mBuilderCompat.setContentText(contentText);
            }

            mBuilderCompat.setContentIntent(intentCall);

            notif = mBuilderCompat.build();
        }

        startForeground(notificationId, notif);
    }

    private void showCallInProgressNotification() {
        logDebug("Showing the notification");
        int notificationId = getCurrentCallNotifId();
        if (notificationId == INVALID_CALL) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notificationChannelId, NOTIFICATION_CHANNEL_INPROGRESS_MISSED_CALLS_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setShowBadge(true);
            channel.setSound(null, null);
            mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.createNotificationChannel(channel);

            mBuilderCompatO = new NotificationCompat.Builder(this, notificationChannelId);
            mBuilderCompatO
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setAutoCancel(false)
                    // Only use the action as indicator.
                    .addAction(R.drawable.ic_phone_white, StringResourcesUtils.getString(R.string.button_notification_call_in_progress), null)
                    .setOngoing(false)
                    .setColor(ContextCompat.getColor(this, R.color.red_600_red_300));

            String title;
            String email;
            long userHandle;
            MegaChatRoom chat = megaChatApi.getChatRoom(currentChatId);
            if (chat != null) {
                title = getTitleChat(chat);

                if (chat.isGroup()) {
                    Bitmap largeIcon = createDefaultAvatar(-1, title);
                    if (largeIcon != null) {
                        mBuilderCompatO.setLargeIcon(largeIcon);
                    }

                } else {
                    userHandle = chat.getPeerHandle(0);
                    email = chatC.getParticipantEmail(chat.getPeerHandle(0));
                    Bitmap largeIcon = setProfileContactAvatar(userHandle, title, email);
                    if (largeIcon != null) {
                        mBuilderCompatO.setLargeIcon(largeIcon);
                    }
                }

                mBuilderCompatO.setContentTitle(title);
                updateNotificationContent();
            } else {
                mBuilderCompatO.setContentTitle(StringResourcesUtils.getString(R.string.title_notification_call_in_progress));
                mBuilderCompatO.setContentText(StringResourcesUtils.getString(R.string.action_notification_call_in_progress));
                startForeground(notificationId, mBuilderCompatO.build());
            }
        } else {
            mBuilderCompat = new NotificationCompat.Builder(this, notificationChannelId);
            mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

            mBuilderCompat
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setAutoCancel(false)
                    .addAction(R.drawable.ic_phone_white, StringResourcesUtils.getString(R.string.button_notification_call_in_progress), null)
                    .setOngoing(false);

            mBuilderCompat.setColor(ContextCompat.getColor(this, R.color.red_600_red_300));

            String title;
            String email;
            long userHandle;
            MegaChatRoom chat = megaChatApi.getChatRoom(currentChatId);
            if (chat != null) {
                title = getTitleChat(chat);

                if (chat.isGroup()) {
                    Bitmap largeIcon = createDefaultAvatar(-1, title);
                    if (largeIcon != null) {
                        mBuilderCompat.setLargeIcon(largeIcon);
                    }
                } else {
                    userHandle = chat.getPeerHandle(0);
                    email = chatC.getParticipantEmail(chat.getPeerHandle(0));
                    Bitmap largeIcon = setProfileContactAvatar(userHandle, title, email);
                    if (largeIcon != null) {
                        mBuilderCompat.setLargeIcon(largeIcon);
                    }
                }

                mBuilderCompat.setContentTitle(title);
                updateNotificationContent();
            } else {
                mBuilderCompat.setContentTitle(StringResourcesUtils.getString(R.string.title_notification_call_in_progress));
                mBuilderCompat.setContentText(StringResourcesUtils.getString(R.string.action_notification_call_in_progress));
                startForeground(notificationId, mBuilderCompat.build());
            }
        }
    }

    private void updateCall(long newChatIdCall) {
        stopForeground(true);
        cancelNotification();
        currentChatId = newChatIdCall;

        if (MegaApplication.getOpenCallChatId() != currentChatId) {
            MegaApplication.setOpenCallChatId(currentChatId);
        }
        showCallInProgressNotification();
    }

    private void removeNotification(long chatId) {
        ArrayList<Long> listCalls = getCallsParticipating();
        if (listCalls == null || listCalls.size() == 0) {
            stopNotification(chatId);
            return;
        }

        for(long chatCall:listCalls){
            if(chatCall != currentChatId){
                updateCall(chatCall);
                return;
            }
        }

        stopNotification(currentChatId);
    }

    /**
     * Method for cancelling a notification that is being displayed.
     *
     * @param chatId That chat ID of a call.
     */
    private void stopNotification(long chatId) {
        stopForeground(true);
        mNotificationManager.cancel(getCallNotifId(chatId));
        stopSelf();
    }

    public Bitmap setProfileContactAvatar(long userHandle, String fullName, String email) {
        File avatar = buildAvatarFile(getApplicationContext(), email + ".jpg");

        if (isFileAvailable(avatar)) {
            if (avatar.length() > 0) {
                BitmapFactory.Options bOpts = new BitmapFactory.Options();
                bOpts.inPurgeable = true;
                bOpts.inInputShareable = true;
                Bitmap bitmap = BitmapFactory.decodeFile(avatar.getAbsolutePath(), bOpts);
                bitmap = getCircleBitmap(bitmap);
                if (bitmap != null) {
                    return bitmap;
                } else {
                    return createDefaultAvatar(userHandle, fullName);
                }
            } else {
                return createDefaultAvatar(userHandle, fullName);
            }
        } else {
            return createDefaultAvatar(userHandle, fullName);
        }
    }

    private Bitmap getCircleBitmap(Bitmap bitmap) {
        final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);

        final int color = Color.RED;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawOval(rectF, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        bitmap.recycle();

        return output;
    }

    private Bitmap createDefaultAvatar(long userHandle, String fullName) {
        int color;
        if (userHandle != -1) {
            color = getColorAvatar(userHandle);
        } else {
            color = getSpecificAvatarColor(AVATAR_GROUP_CHAT_COLOR);
        }

        return getDefaultAvatar(color, fullName, AVATAR_SIZE, true);
    }

    /**
     * Method for getting the call notification ID from the chat ID.
     *
     * @return call notification ID.
     */
    private int getCurrentCallNotifId() {
        MegaChatCall call = megaChatApi.getChatCall(currentChatId);
        if (call == null)
            return INVALID_CALL;

        return getCallNotificationId(call.getCallId());
    }

    private void cancelNotification(){
        int notificationId = getCurrentCallNotifId();
        if(notificationId == INVALID_CALL)
            return;

        mNotificationManager.cancel(notificationId);
    }

    /**
     * Method to get the notification id of a particular call
     *
     * @param chatId That chat ID of the call.
     * @return The id of the notification.
     */
    private int getCallNotifId(long chatId) {
        return (MegaApiJava.userHandleToBase64(chatId)).hashCode();
    }

    @Override
    public void onDestroy() {
        LiveEventBus.get(EVENT_CALL_STATUS_CHANGE, MegaChatCall.class).removeObserver(callStatusObserver);
        LiveEventBus.get(EVENT_CALL_ON_HOLD_CHANGE, MegaChatCall.class).removeObserver(callOnHoldObserver);
        LiveEventBus.get(EVENT_CHAT_TITLE_CHANGE, MegaChatRoom.class).removeObserver(titleMeetingChangeObserver);
        LiveEventBus.get(EVENT_ENTER_IN_MEETING, Boolean.class).removeObserver(isInMeetingObserver);
        LiveEventBus.get(EVENT_CALL_ANSWERED_IN_ANOTHER_CLIENT, Long.class).removeObserver(callAnsweredInAnotherClientObserver);

        cancelNotification();
        MegaApplication.setOpenCallChatId(MEGACHAT_INVALID_HANDLE);

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
