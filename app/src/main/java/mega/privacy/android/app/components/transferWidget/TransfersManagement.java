package mega.privacy.android.app.components.transferWidget;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.Build;
import android.os.Handler;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

import mega.privacy.android.app.AndroidCompletedTransfer;
import mega.privacy.android.app.DownloadService;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.R;
import mega.privacy.android.app.UploadService;
import mega.privacy.android.app.lollipop.megachat.ChatUploadService;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaApiJava;
import nz.mega.sdk.MegaTransfer;

import static mega.privacy.android.app.components.transferWidget.TransferWidget.NO_TYPE;
import static mega.privacy.android.app.constants.BroadcastConstants.*;
import static mega.privacy.android.app.utils.Util.isOnline;
import static mega.privacy.android.app.utils.Constants.ACTION_RESTART_SERVICE;
import static mega.privacy.android.app.utils.LogUtil.logWarning;
import static mega.privacy.android.app.utils.SDCardUtils.checkSDCardCompletedTransfers;
import static mega.privacy.android.app.utils.StringResourcesUtils.getString;

import static nz.mega.sdk.MegaTransfer.TYPE_DOWNLOAD;
import static nz.mega.sdk.MegaTransfer.TYPE_UPLOAD;

public class TransfersManagement {
    private static final long INVALID_VALUE = -1;
    private static final int WAIT_TIME_TO_SHOW_WARNING = 60000;
    private static final  int WAIT_TIME_TO_SHOW_NETWORK_WARNING = 30000;
    private static final int WAIT_TIME_TO_RESTART_SERVICES = 5000;

    private CountDownTimer networkTimer;

    private long transferOverQuotaTimestamp;
    private boolean hasNotToBeShowDueToTransferOverQuota;
    private boolean isCurrentTransferOverQuota;
    private boolean isOnTransfersSection;
    private boolean failedTransfers;
    private boolean transferOverQuotaNotificationShown;
    private boolean isTransferOverQuotaBannerShown;
    private boolean resumeTransfersWarningHasAlreadyBeenShown;
    private boolean shouldShowNetworkWarning;

    private final ArrayList<String> pausedTransfers = new ArrayList<>();

    public TransfersManagement() {
        resetTransferOverQuotaTimestamp();
    }

    /**
     * Checks if the queue of transfers is paused or all the current in-progress transfers are individually.
     *
     * @return True if the queue of transfers or all the current in-progress transfers are paused, false otherwise.
     */
    public boolean areTransfersPaused() {
        MegaApiAndroid megaApi = MegaApplication.getInstance().getMegaApi();
        int totalTransfers = megaApi.getNumPendingDownloads() + megaApi.getNumPendingUploads();

        return megaApi.areTransfersPaused(TYPE_DOWNLOAD) || megaApi.areTransfersPaused(TYPE_UPLOAD) || totalTransfers == pausedTransfers.size();
    }

    /**
     * Removes a resumed transfer.
     *
     * @param transferTag   tag of the resumed transfer
     */
    public void removePausedTransfers(int transferTag) {
        pausedTransfers.remove(Integer.toString(transferTag));
    }

    /**
     * Adds a paused transfer.
     *
     * @param transferTag   tag of the paused transfer
     */
    public void addPausedTransfers(int transferTag) {
        String tag = Integer.toString(transferTag);
        if (pausedTransfers.contains(tag)) {
            return;
        }

        pausedTransfers.add(Integer.toString(transferTag));
    }

    /**
     * Checks if a transfer is paused.
     * If so, adds it to the paused transfers list.
     * If not, do nothing.
     *
     * @param transferTag Identifier of the MegaTransfer to check.
     */
    public void checkIfTransferIsPaused(int transferTag) {
        MegaTransfer transfer = MegaApplication.getInstance().getMegaApi().getTransferByTag(transferTag);
        checkIfTransferIsPaused(transfer);
    }

    /**
     * Checks if a transfer is paused.
     * If so, adds it to the paused transfers list.
     * If not, do nothing.
     *
     * @param transfer MegaTransfer to check.
     */
    public void checkIfTransferIsPaused(MegaTransfer transfer) {
        if (transfer != null && transfer.getState() == MegaTransfer.STATE_PAUSED) {
            addPausedTransfers(transfer.getTag());
        }
    }

    /**
     * Clears the paused transfers list.
     */
    public void resetPausedTransfers() {
        pausedTransfers.clear();
    }

    /**
     * Sets the current time as timestamp to avoid show duplicated transfer over quota warnings.
     */
    public void setTransferOverQuotaTimestamp() {
        this.transferOverQuotaTimestamp = System.currentTimeMillis();
    }

    /**
     * Sets the transfer over quota time stamp as invalid.
     */
    public void resetTransferOverQuotaTimestamp() {
        this.transferOverQuotaTimestamp = INVALID_VALUE;
    }

    /**
     * Checks if a transfer over quota warning has to be shown.
     * It will be shown if transferOverQuotaTimestamp has not been initialized yet
     * or if more than a minute has passed since the last time it was shown.
     *
     * @return  True if the warning has to be shown, false otherwise
     */
    public boolean shouldShowTransferOverQuotaWarning() {
        return transferOverQuotaTimestamp == INVALID_VALUE || transferOverQuotaTimestamp - System.currentTimeMillis() > WAIT_TIME_TO_SHOW_WARNING;
    }

    /**
     * Checks if it is on transfer over quota.
     *
     * @return True if it is on transfer over quota, false otherwise.
     */
    public static boolean isOnTransferOverQuota() {
        return MegaApplication.getInstance().getMegaApi().getBandwidthOverquotaDelay() > 0;
    }

    /**
     * Sets if the widget has to be shown depending on if it is on transfer over quota
     * and the Transfers section has been opened from the transfers widget.
     * Also sets if the "transfer over quota" banner has to be shown due to the same reason.
     *
     * @param hasNotToBeShowDueToTransferOverQuota  true if it is on transfer over quota and the Transfers section
     *                                              has been opened from the transfers widget, false otherwise
     */
    public void setHasNotToBeShowDueToTransferOverQuota(boolean hasNotToBeShowDueToTransferOverQuota) {
        this.hasNotToBeShowDueToTransferOverQuota = hasNotToBeShowDueToTransferOverQuota;
        setTransferOverQuotaBannerShown(hasNotToBeShowDueToTransferOverQuota);
    }

    /**
     * Checks if the transfers widget has to be shown.
     * If the widget does not have to be shown means that:
     * the user is in transfer over quota, there is not any upload transfer in progress
     * and they already opened the transfers section by clicking the widget.
     *
     * @return True if the widget does not have to be shown, false otherwise
     */
    public boolean hasNotToBeShowDueToTransferOverQuota() {
        return hasNotToBeShowDueToTransferOverQuota && MegaApplication.getInstance().getMegaApi().getNumPendingUploads() <= 0;
    }

    /**
     * Sends a broadcast to update the transfer widget where needed.
     *
     * @param transferType  the transfer type.
     */
    public static void launchTransferUpdateIntent(int transferType) {
        MegaApplication.getInstance().sendBroadcast(new Intent(BROADCAST_ACTION_INTENT_TRANSFER_UPDATE)
                .putExtra(TRANSFER_TYPE, transferType));
    }

    /**
     * Adds the completed transfer to the DB and
     * sends a broadcast to update the completed transfers tab.
     *
     * @param completedTransfer AndroidCompletedTransfer to add.
     */
    public static void addCompletedTransfer(AndroidCompletedTransfer completedTransfer) {
        MegaApplication app = MegaApplication.getInstance();

        long id = app.getDbH().setCompletedTransfer(completedTransfer);
        completedTransfer.setId(id);
        app.sendBroadcast(new Intent(BROADCAST_ACTION_TRANSFER_FINISH)
                .putExtra(COMPLETED_TRANSFER, completedTransfer));
    }

    /**
     * Starts a CountDownTimer after show warnings related to no internet connection.
     * If the timer finishes, launches a Broadcast to update the widget.
     */
    public void startNetworkTimer() {
        networkTimer = new CountDownTimer(WAIT_TIME_TO_SHOW_NETWORK_WARNING,
                WAIT_TIME_TO_SHOW_NETWORK_WARNING) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                if (isOnline(MegaApplication.getInstance())) {
                    return;
                }

                setShouldShowNetworkWarning(true);
                launchTransferUpdateIntent(NO_TYPE);
            }
        };

        networkTimer.start();
    }

    /**
     * Cancels the CountDownTimer to show warnings related to no internet connection.
     */
    public void resetNetworkTimer() {
        if (networkTimer != null) {
            networkTimer.cancel();
            setShouldShowNetworkWarning(false);
            launchTransferUpdateIntent(NO_TYPE);
        }
    }

    /**
     * Check if there are resumed pending transfers.
     * Before start to check if there are pending transfers, it has to wait a time
     * WAIT_TIME_TO_RESTART_SERVICES. This time is for the transfer resumption to be enabled
     * since there is no possibility to listen any response of the request to know when it finishes.
     *
     */
    public static void checkResumedPendingTransfers() {
        MegaApplication app = MegaApplication.getInstance();
        MegaApiJava megaApi = app.getMegaApi();

        if (megaApi.getRootNode() != null) {
            checkSDCardCompletedTransfers();
        }

        if (app.getDbH().getTransferQueueStatus()) {
            //Queue of transfers should be paused.
            megaApi.pauseTransfers(true);
        }

        new Handler().postDelayed(() -> {
            try {
                if (megaApi.getNumPendingDownloads() > 0) {
                    Intent downloadServiceIntent = new Intent(app, DownloadService.class)
                            .setAction(ACTION_RESTART_SERVICE);

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                            && !app.isActivityVisible()) {
                        app.startForegroundService(downloadServiceIntent);
                    } else {
                        app.startService(downloadServiceIntent);
                    }
                }

                if (megaApi.getNumPendingUploads() > 0) {
                    Intent uploadServiceIntent = new Intent(app, UploadService.class)
                            .setAction(ACTION_RESTART_SERVICE);
                    Intent chatUploadServiceIntent = new Intent(app, ChatUploadService.class)
                            .setAction(ACTION_RESTART_SERVICE);

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                            && !app.isActivityVisible()) {
                        app.startForegroundService(uploadServiceIntent);
                        app.startForegroundService(chatUploadServiceIntent);
                    } else {
                        app.startService(uploadServiceIntent);
                        app.startService(chatUploadServiceIntent);
                    }
                }
            } catch (Exception e) {
                logWarning("Exception checking pending transfers", e);
            }
        }, WAIT_TIME_TO_RESTART_SERVICES);
    }

    /**
     * Creates the initial notification when a service starts.
     *
     * @param notificationChannelId   Identifier of the notification channel.
     * @param notificationChannelName Name of the notification channel.
     * @param mNotificationManager    NotificationManager to create the notification.
     * @param mBuilder                Builder to create the notification.
     * @return The initial notification created.
     */
    public static Notification createInitialServiceNotification(String notificationChannelId,
                                                                String notificationChannelName,
                                                                NotificationManager mNotificationManager,
                                                                NotificationCompat.Builder mBuilderCompat,
                                                                Notification.Builder mBuilder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notificationChannelId, notificationChannelName, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setShowBadge(true);
            channel.setSound(null, null);
            mNotificationManager.createNotificationChannel(channel);

            mBuilderCompat
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setColor(ContextCompat.getColor(MegaApplication.getInstance(), R.color.red_600_red_300))
                    .setContentTitle(getString(R.string.download_preparing_files))
                    .setAutoCancel(true);

            return mBuilderCompat.build();
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_stat_notify)
                    .setColor(ContextCompat.getColor(MegaApplication.getInstance(), R.color.red_600_red_300))
                    .setContentTitle(getString(R.string.download_preparing_files))
                    .setAutoCancel(true);

            return mBuilder.build();
        }
    }

    /**
     * Checks if a service is already running.
     *
     * @param serviceClass Service it wants to know if its is already running.
     * @return True if the service is already running, false otherwise.
     */
    public static boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) MegaApplication.getInstance()
                .getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the transfer over quota has occurred at this moment
     * or it occurred in other past moment.
     *
     * @return  True if the transfer over quota has occurred at this moment, false otherwise.
     */
    public boolean isCurrentTransferOverQuota() {
        return isCurrentTransferOverQuota;
    }

    public void setCurrentTransferOverQuota(boolean currentTransferOverQuota) {
        isCurrentTransferOverQuota = currentTransferOverQuota;
    }

    public void setIsOnTransfersSection(boolean isOnTransfersSection) {
        this.isOnTransfersSection = isOnTransfersSection;
    }

    public boolean isOnTransfersSection() {
        return isOnTransfersSection;
    }

    public void setFailedTransfers(boolean failedTransfers) {
        this.failedTransfers = failedTransfers;
    }

    public boolean thereAreFailedTransfers() {
        return failedTransfers;
    }

    public void setTransferOverQuotaNotificationShown(boolean transferOverQuotaNotificationShown) {
        this.transferOverQuotaNotificationShown = transferOverQuotaNotificationShown;
    }

    public boolean isTransferOverQuotaNotificationShown() {
        return transferOverQuotaNotificationShown;
    }

    public void setTransferOverQuotaBannerShown(boolean transferOverQuotaBannerShown) {
        isTransferOverQuotaBannerShown = transferOverQuotaBannerShown;
    }

    public boolean isTransferOverQuotaBannerShown() {
        return isTransferOverQuotaBannerShown;
    }

    public void setResumeTransfersWarningHasAlreadyBeenShown(boolean resumeTransfersWarningHasAlreadyBeenShown) {
        this.resumeTransfersWarningHasAlreadyBeenShown = resumeTransfersWarningHasAlreadyBeenShown;
    }

    public boolean isResumeTransfersWarningHasAlreadyBeenShown() {
        return resumeTransfersWarningHasAlreadyBeenShown;
    }

    public void setShouldShowNetworkWarning(boolean shouldShowNetworkWarning) {
        this.shouldShowNetworkWarning = shouldShowNetworkWarning;
    }

    public boolean shouldShowNetWorkWarning() {
        return shouldShowNetworkWarning;
    }
}
