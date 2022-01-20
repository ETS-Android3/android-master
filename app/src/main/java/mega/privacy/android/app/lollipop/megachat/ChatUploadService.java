package mega.privacy.android.app.lollipop.megachat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import mega.privacy.android.app.AndroidCompletedTransfer;
import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.MimeTypeList;
import mega.privacy.android.app.R;
import mega.privacy.android.app.VideoDownsampling;
import mega.privacy.android.app.lollipop.ManagerActivityLollipop;
import mega.privacy.android.app.utils.StringResourcesUtils;
import mega.privacy.android.app.utils.ThumbnailUtilsLollipop;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaApiJava;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaChatApiJava;
import nz.mega.sdk.MegaChatError;
import nz.mega.sdk.MegaChatRequest;
import nz.mega.sdk.MegaChatRequestListenerInterface;
import nz.mega.sdk.MegaError;
import nz.mega.sdk.MegaNode;
import nz.mega.sdk.MegaNodeList;
import nz.mega.sdk.MegaRequest;
import nz.mega.sdk.MegaRequestListenerInterface;
import nz.mega.sdk.MegaTransfer;
import nz.mega.sdk.MegaTransferData;
import nz.mega.sdk.MegaTransferListenerInterface;

import static mega.privacy.android.app.components.transferWidget.TransfersManagement.addCompletedTransfer;
import static mega.privacy.android.app.components.transferWidget.TransfersManagement.createInitialServiceNotification;
import static mega.privacy.android.app.components.transferWidget.TransfersManagement.launchTransferUpdateIntent;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_CHAT_TRANSFER_START;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_INTENT_SHOWSNACKBAR_TRANSFERS_FINISHED;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_RESUME_TRANSFERS;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_RETRY_PENDING_MESSAGE;
import static mega.privacy.android.app.constants.BroadcastConstants.FILE_EXPLORER_CHAT_UPLOAD;
import static mega.privacy.android.app.constants.BroadcastConstants.PENDING_MESSAGE_ID;
import static mega.privacy.android.app.constants.SettingsConstants.VIDEO_QUALITY_ORIGINAL;
import static mega.privacy.android.app.utils.CacheFolderManager.*;
import static mega.privacy.android.app.utils.ChatUtil.*;
import static mega.privacy.android.app.utils.FileUtil.*;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.PreviewUtils.*;
import static mega.privacy.android.app.utils.TextUtil.isTextEmpty;
import static mega.privacy.android.app.utils.ThumbnailUtils.*;
import static mega.privacy.android.app.utils.Util.isOnMobileData;
import static nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE;


public class ChatUploadService extends Service implements MegaTransferListenerInterface, MegaRequestListenerInterface, MegaChatRequestListenerInterface {

	static final float DOWNSCALE_IMAGES_PX = 2000000f;

	public static String ACTION_CANCEL = "CANCEL_UPLOAD";
	public static String EXTRA_SIZE = "MEGA_SIZE";
	public static String EXTRA_CHAT_ID = "CHAT_ID";
	public static String EXTRA_ID_PEND_MSG = "ID_PEND_MSG";
	public static String EXTRA_NAME_EDITED = "MEGA_FILE_NAME_EDITED";
	public static String EXTRA_COMES_FROM_FILE_EXPLORER = "COMES_FROM_FILE_EXPLORER";
	public static String EXTRA_ATTACH_FILES = "ATTACH_FILES";
	public static String EXTRA_ATTACH_CHAT_IDS = "ATTACH_CHAT_IDS";
	public static String EXTRA_UPLOAD_FILES_FINGERPRINTS = "UPLOAD_FILES_FINGERPRINTS";
	public static String EXTRA_PEND_MSG_IDS = "PEND_MSG_IDS";
	public static final String EXTRA_PARENT_NODE = "EXTRA_PARENT_NODE";

	private boolean isForeground = false;
	private boolean canceled;

    private HashMap<String, String> fileNames = new HashMap<>();

	boolean sendOriginalAttachments=false;

	//0 - not overquota, not pre-overquota
	//1 - overquota
	//2 - pre-overquota
	int isOverquota = 0;

	ArrayList<PendingMessageSingle> pendingMessages;
	HashMap<String, Integer> mapVideoDownsampling;
	HashMap<Integer, MegaTransfer> mapProgressTransfers;

	MegaApplication app;
	MegaApiAndroid megaApi;
	MegaChatApiAndroid megaChatApi;
	int requestSent = 0;

	WifiLock lock;
	WakeLock wl;
	DatabaseHandler dbH = null;

	int transfersCount = 0;
	int numberVideosPending = 0;
	int totalVideos = 0;
	int totalUploadsCompleted = 0;
	int totalUploads = 0;
	private String type = "";

	MegaNode parentNode;

	VideoDownsampling videoDownsampling;

	private Notification.Builder mBuilder;
	private NotificationCompat.Builder mBuilderCompat;
	private NotificationManager mNotificationManager;

	private static boolean fileExplorerUpload;
	private static long snackbarChatHandle = MEGACHAT_INVALID_HANDLE;

	/** the receiver and manager for the broadcast to listen to the pause event */
	private BroadcastReceiver pauseBroadcastReceiver;

	@SuppressLint("NewApi")
	@Override
	public void onCreate() {
		super.onCreate();

		app = (MegaApplication)getApplication();

		megaApi = app.getMegaApi();
		megaChatApi = app.getMegaChatApi();
		megaApi.addTransferListener(this);
		pendingMessages = new ArrayList<>();

		dbH = DatabaseHandler.getDbHandler(getApplicationContext());

		isForeground = false;
		canceled = false;
		isOverquota = 0;

		mapVideoDownsampling = new HashMap<>();
		mapProgressTransfers = new HashMap<>();

		int wifiLockMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF;

        WifiManager wifiManager = (WifiManager) getApplicationContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		lock = wifiManager.createWifiLock(wifiLockMode, "MegaUploadServiceWifiLock");
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MegaUploadServicePowerLock");

		mBuilder = new Notification.Builder(ChatUploadService.this);
		mBuilderCompat = new NotificationCompat.Builder(ChatUploadService.this, NOTIFICATION_CHANNEL_CHAT_UPLOAD_ID);

		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		startForeground();

		// delay 1 second to refresh the pause notification to prevent update is missed
		pauseBroadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				new Handler().postDelayed(() -> {
					updateProgressNotification();
				}, 1000);
			}
		};
		registerReceiver(pauseBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_INTENT_UPDATE_PAUSE_NOTIFICATION));
	}

	private void startForeground() {
		if (megaApi.getNumPendingUploads() <= 0) {
			return;
		}

		try {
			startForeground(NOTIFICATION_CHAT_UPLOAD, createInitialServiceNotification(NOTIFICATION_CHANNEL_CHAT_UPLOAD_ID,
					NOTIFICATION_CHANNEL_CHAT_UPLOAD_NAME, mNotificationManager,
					new NotificationCompat.Builder(ChatUploadService.this, NOTIFICATION_CHANNEL_CHAT_UPLOAD_ID),
					mBuilder));
			isForeground = true;
		} catch (Exception e) {
			logWarning("Error starting foreground.", e);
			isForeground = false;
		}
	}

	private void stopForeground() {
		isForeground = false;
		stopForeground(true);
		mNotificationManager.cancel(NOTIFICATION_CHAT_UPLOAD);
		stopSelf();
	}

	@Override
	public void onDestroy(){
		logDebug("onDestroy");
		if((lock != null) && (lock.isHeld()))
			try{ lock.release(); } catch(Exception ex) {}
		if((wl != null) && (wl.isHeld()))
			try{ wl.release(); } catch(Exception ex) {}

		if(megaApi != null)
		{
			megaApi.removeRequestListener(this);
			megaApi.removeTransferListener(this);
		}

        if (megaChatApi != null){
            megaChatApi.saveCurrentState();
        }

		unregisterReceiver(pauseBroadcastReceiver);
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		logDebug("Flags: " + flags + ", Start ID: " + startId);

		canceled = false;

		if(intent == null){
			return START_NOT_STICKY;
		}

		if ((intent.getAction() != null)){
			if (intent.getAction().equals(ACTION_CANCEL)) {
				logDebug("Cancel intent");
				canceled = true;
				megaApi.cancelTransfers(MegaTransfer.TYPE_UPLOAD, this);
				return START_NOT_STICKY;
			}
		}

		isOverquota = 0;

		onHandleIntent(intent);

		return START_NOT_STICKY;
	}

	@SuppressWarnings("unchecked")
	protected void onHandleIntent(final Intent intent) {
		if (intent == null) return;

		if (intent.getAction() != null && intent.getAction().equals(ACTION_RESTART_SERVICE)) {
			MegaTransferData transferData = megaApi.getTransferData(null);
			if (transferData == null) {
				stopForeground();
				return;
			}

			int uploadsInProgress = transferData.getNumUploads();
			int voiceClipsInProgress = 0;

			for (int i = 0; i < uploadsInProgress; i++) {
				MegaTransfer transfer = megaApi.getTransferByTag(transferData.getUploadTag(i));
				if (transfer == null) {
					continue;
				}

				String data = transfer.getAppData();
				if (!isTextEmpty(data) && data.contains(APP_DATA_CHAT)) {
					mapProgressTransfers.put(transfer.getTag(), transfer);

					if (isVoiceClip(data)) {
						voiceClipsInProgress++;
					} else {
						MegaApplication.getTransfersManagement().checkIfTransferIsPaused(transfer);
					}
				}
			}

			transfersCount = totalUploads = mapProgressTransfers.size() - voiceClipsInProgress;

			if (totalUploads > 0) {
				updateProgressNotification();
			} else {
				stopForeground();
			}

			launchTransferUpdateIntent(MegaTransfer.TYPE_UPLOAD);
			return;
		} else if (ACTION_CHECK_COMPRESSING_MESSAGE.equals(intent.getAction())) {
			checkCompressingMessage(intent);
			return;
		}

		ArrayList<PendingMessageSingle> pendingMessageSingles = new ArrayList<>();
		parentNode = MegaNode.unserialize(intent.getStringExtra(EXTRA_PARENT_NODE));
		if (intent.hasExtra(EXTRA_NAME_EDITED)) {
            fileNames = (HashMap<String, String>) intent.getSerializableExtra(EXTRA_NAME_EDITED);
        }

		if (intent.getBooleanExtra(EXTRA_COMES_FROM_FILE_EXPLORER, false)) {
			fileExplorerUpload = true;
			HashMap<String, String> fileFingerprints = (HashMap<String, String>) intent.getSerializableExtra(EXTRA_UPLOAD_FILES_FINGERPRINTS);
			long[] idPendMsgs = intent.getLongArrayExtra(EXTRA_PEND_MSG_IDS);
			long[] attachFiles = intent.getLongArrayExtra(EXTRA_ATTACH_FILES);
			long[] idChats = intent.getLongArrayExtra(EXTRA_ATTACH_CHAT_IDS);
			boolean validIdChats = idChats != null && idChats.length > 0;
			boolean onlyOneChat = true;

			if (attachFiles != null && attachFiles.length > 0 && validIdChats) {
				for (long attachFile : attachFiles) {
					for (long idChat : idChats) {
						requestSent++;
						megaChatApi.attachNode(idChat, attachFile, this);
					}
				}
			}

			if (validIdChats) {
				if (idChats.length == 1) {
					snackbarChatHandle = idChats[0];
				} else {
					onlyOneChat = false;
				}
			}

			if (idPendMsgs != null && idPendMsgs.length > 0 && fileFingerprints != null && !fileFingerprints.isEmpty()) {
				for (Map.Entry<String, String> entry : fileFingerprints.entrySet()) {
					if (entry != null) {
						String fingerprint = entry.getKey();
						String path = entry.getValue();

						if (fingerprint == null || path == null) {
							logError("Error: Fingerprint: " + fingerprint + ", Path: " + path);
							continue;
						}

						totalUploads++;

						if (!wl.isHeld()) {
							wl.acquire();
						}

						if (!lock.isHeld()) {
							lock.acquire();
						}
						pendingMessageSingles.clear();
						for (int i = 0; i < idPendMsgs.length; i++) {
							PendingMessageSingle pendingMsg = null;
							if (idPendMsgs[i] != -1) {
								pendingMsg = dbH.findPendingMessageById(idPendMsgs[i]);
//									One transfer for file --> onTransferFinish() attach to all selected chats
								if (pendingMsg != null && pendingMsg.getChatId() != -1 && path.equals(pendingMsg.getFilePath()) && fingerprint.equals(pendingMsg.getFingerprint())) {
									pendingMessageSingles.add(pendingMsg);
									if (onlyOneChat) {
										if (snackbarChatHandle == MEGACHAT_INVALID_HANDLE) {
											snackbarChatHandle = pendingMsg.getChatId();
										} else if (snackbarChatHandle != pendingMsg.getChatId()) {
											onlyOneChat = false;
										}
									}
								}
							}
						}
						initUpload(pendingMessageSingles, null);
					}
				}
			}
		} else {
			long chatId = intent.getLongExtra(EXTRA_CHAT_ID, -1);
			type = intent.getStringExtra(EXTRA_TRANSFER_TYPE);
			long idPendMsg = intent.getLongExtra(EXTRA_ID_PEND_MSG, -1);
			PendingMessageSingle pendingMsg = null;
			if (idPendMsg != -1) {
				pendingMsg = dbH.findPendingMessageById(idPendMsg);
			}

			if (pendingMsg != null) {
				sendOriginalAttachments = dbH.getChatVideoQuality() == VIDEO_QUALITY_ORIGINAL;
				logDebug("sendOriginalAttachments is " + sendOriginalAttachments);

				if (chatId != -1) {
					logDebug("The chat ID is: " + chatId);

					if (type == null || !type.equals(APP_DATA_VOICE_CLIP)) {
						totalUploads++;
					}

					if (!wl.isHeld()) {
						wl.acquire();
					}

					if (!lock.isHeld()) {
						lock.acquire();
					}
					pendingMessageSingles.clear();
					pendingMessageSingles.add(pendingMsg);
					initUpload(pendingMessageSingles, type);
				}
			} else {
				logError("Error the chatId is not correct: " + chatId);
			}
		}
	}

	void initUpload (ArrayList<PendingMessageSingle> pendingMsgs, String type) {
		logDebug("initUpload");

		PendingMessageSingle pendingMsg = pendingMsgs.get(0);
		File file = new File(pendingMsg.getFilePath());

		if (MimeTypeList.typeForName(file.getName()).isImage() && !MimeTypeList.typeForName(file.getName()).isGIF() && isOnMobileData(this)) {
			String uploadPath;
			File compressedFile = checkImageBeforeUpload(file);

			if (isFileAvailable(compressedFile)) {
				String fingerprint = megaApi.getFingerprint(compressedFile.getAbsolutePath());

				for (PendingMessageSingle pendMsg : pendingMsgs) {
					if (fingerprint != null) {
						pendMsg.setFingerprint(fingerprint);
					}

					pendingMessages.add(pendMsg);
				}

				uploadPath = compressedFile.getAbsolutePath();
			} else {
				pendingMessages.addAll(pendingMsgs);
				uploadPath = pendingMsg.getFilePath();
			}

			startUpload(pendingMsg.id, type, fileNames.get(pendingMsg.name), uploadPath);
		} else if (MimeTypeList.typeForName(file.getName()).isMp4Video() && !sendOriginalAttachments) {
			logDebug("DATA connection is Mp4Video");

			try {
				File chatTempFolder = getCacheFolder(getApplicationContext(), CHAT_TEMPORAL_FOLDER);
				File outFile = buildChatTempFile(getApplicationContext(), file.getName());
				int index = 0;

				if (outFile != null) {
					while (outFile.exists()) {
						if (index > 0) {
							outFile = new File(chatTempFolder.getAbsolutePath(), file.getName());
						}

						index++;
						String outFilePath = outFile.getAbsolutePath();
						String[] splitByDot = outFilePath.split("\\.");
						String ext = "";

						if (splitByDot != null && splitByDot.length > 1) {
							ext = splitByDot[splitByDot.length - 1];
						}

						String fileName = outFilePath.substring(outFilePath.lastIndexOf(File.separator) + 1);

						fileName = ext.length() > 0
								? fileName.replace("." + ext, "_" + index + MP4_EXTENSION)
								: fileName.concat("_" + index + MP4_EXTENSION);

						outFile = new File(chatTempFolder.getAbsolutePath(), fileName);
					}

					outFile.createNewFile();
				}

				if (outFile == null) {
					addPendingMessagesAndStartUpload(pendingMsg.getId(), type,
							fileNames.get(pendingMsg.getName()), pendingMsg.getFilePath(), pendingMsgs);
				} else {
					totalVideos++;
					numberVideosPending++;

					for (PendingMessageSingle pendMsg : pendingMsgs) {
						pendMsg.setVideoDownSampled(outFile.getAbsolutePath());
						pendingMessages.add(pendMsg);
					}

					mapVideoDownsampling.put(outFile.getAbsolutePath(), 0);

					if (videoDownsampling == null) {
						videoDownsampling = new VideoDownsampling(this);
					}

					videoDownsampling.changeResolution(file, outFile.getAbsolutePath(),
							pendingMsg.getId(), dbH.getChatVideoQuality());
				}

			} catch (Throwable throwable) {
				logError("EXCEPTION: Video cannot be downsampled", throwable);
				addPendingMessagesAndStartUpload(pendingMsg.getId(), type,
						fileNames.get(pendingMsg.getName()), pendingMsg.getFilePath(), pendingMsgs);
			}
		} else {
			addPendingMessagesAndStartUpload(pendingMsg.getId(), type,
					fileNames.get(pendingMsg.getName()), pendingMsg.getFilePath(), pendingMsgs);
		}

		if (megaApi.areTransfersPaused(MegaTransfer.TYPE_UPLOAD)
				&& !MegaApplication.getTransfersManagement().isResumeTransfersWarningHasAlreadyBeenShown()) {
			sendBroadcast(new Intent(BROADCAST_ACTION_RESUME_TRANSFERS));
		}
	}

	/**
	 * Adds pending messages to general list and starts the upload.
	 *
	 * @param idPendingMessage Identifier of pending message.
	 * @param type             Type of upload file.
	 * @param fileName         Name of the file if set, null otherwise.
	 * @param localPath        Local path of the file to upload.
	 * @param pendingMsgs	   List of pending Messages.
	 */
	private void addPendingMessagesAndStartUpload(long idPendingMessage, String type, String fileName,
												  String localPath, ArrayList<PendingMessageSingle> pendingMsgs) {
		pendingMessages.addAll(pendingMsgs);
		startUpload(idPendingMessage, type, fileName, localPath);
	}

	/**
	 * Starts the upload.
	 *
	 * @param idPendingMessage Identifier of pending message.
	 * @param type             Type of upload file.
	 * @param fileName         Name of the file if set, null otherwise.
	 * @param localPath        Local path of the file to upload.
	 */
	private void startUpload(long idPendingMessage, String type, String fileName, String localPath) {
		String data = APP_DATA_CHAT + APP_DATA_INDICATOR + idPendingMessage;

		if (type != null && type.equals(APP_DATA_VOICE_CLIP)) {
			data = APP_DATA_VOICE_CLIP + APP_DATA_SEPARATOR + data;
		}

		if (!isTextEmpty(fileName)) {
			megaApi.startUploadForChat(localPath, parentNode, data, false, fileName);
		} else {
			megaApi.startUploadForChat(localPath, parentNode, data, false);
		}
	}

	/*
	 * Stop uploading service
	 */
	private void cancel() {
		logDebug("cancel");
		canceled = true;
		stopForeground();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/*
	 * No more intents in the queue
	 */
	private void onQueueComplete() {
		logDebug("onQueueComplete");
		//Review when is called

		if((lock != null) && (lock.isHeld()))
			try{ lock.release(); } catch(Exception ex) {}
		if((wl != null) && (wl.isHeld()))
			try{ wl.release(); } catch(Exception ex) {}

		if(isOverquota!=0){
			showStorageOverquotaNotification();
		}

		logDebug("Reset figures of chatUploadService");
		numberVideosPending=0;
		totalVideos=0;
		totalUploads = 0;
		totalUploadsCompleted = 0;

		if(megaApi.getNumPendingUploads()<=0){
			megaApi.resetTotalUploads();
		}

		if (fileExplorerUpload) {
			fileExplorerUpload = false;
			sendBroadcast(new Intent(BROADCAST_ACTION_INTENT_SHOWSNACKBAR_TRANSFERS_FINISHED)
							.putExtra(FILE_EXPLORER_CHAT_UPLOAD, true)
							.putExtra(CHAT_ID, snackbarChatHandle));
			snackbarChatHandle = MEGACHAT_INVALID_HANDLE;
		}

		logDebug("Stopping service!!");
		MegaApplication.getTransfersManagement().setResumeTransfersWarningHasAlreadyBeenShown(false);
		stopForeground();
		logDebug("After stopSelf");

		try{
			deleteCacheFolderIfEmpty(getApplicationContext(), TEMPORAL_FOLDER);
		}
		catch (Exception e){
			logError("EXCEPTION: pathSelfie not deleted", e);
		}

		try{
			deleteCacheFolderIfEmpty(getApplicationContext(), CHAT_TEMPORAL_FOLDER);
		}
		catch (Exception e){
			logError("EXCEPTION: pathVideoDownsampling not deleted", e);
		}
	}

	public void updateProgressDownsampling(int percentage, String key){
		mapVideoDownsampling.put(key, percentage);
		updateProgressNotification();
	}

	public void finishDownsampling(String returnedFile, boolean success, long idPendingMessage){
		logDebug("success: " + success + ", idPendingMessage: " + idPendingMessage);
		numberVideosPending--;

		File downFile = null;
		String fileName = null;

		if(success){
			mapVideoDownsampling.put(returnedFile, 100);
			downFile = new File(returnedFile);

			for(int i=0; i<pendingMessages.size();i++){
				PendingMessageSingle pendMsg = pendingMessages.get(i);

				if (idPendingMessage == pendMsg.id) {
                    fileName = fileNames.get(pendMsg.name);
                }

				if(pendMsg.getVideoDownSampled()!=null && pendMsg.getVideoDownSampled().equals(returnedFile)){
					String fingerPrint = megaApi.getFingerprint(returnedFile);
					if (fingerPrint != null) {
						pendMsg.setFingerprint(fingerPrint);
					}
				}
			}
		}
		else{
			mapVideoDownsampling.remove(returnedFile);

			for(int i=0; i<pendingMessages.size();i++){
				PendingMessageSingle pendMsg = pendingMessages.get(i);

                if (idPendingMessage == pendMsg.id) {
                    fileName = fileNames.get(pendMsg.name);
                }

				if(pendMsg.getVideoDownSampled()!=null){
					if(pendMsg.getVideoDownSampled().equals(returnedFile)){
						pendMsg.setVideoDownSampled(null);

						downFile = new File(pendMsg.getFilePath());
						logDebug("Found the downFile");
					}
				}
				else{
					logError("Error message could not been downsampled");
				}
			}
			if(downFile!=null){
				mapVideoDownsampling.put(downFile.getAbsolutePath(), 100);
			}
		}

		if (downFile != null) {
			startUpload(idPendingMessage, null, fileName, downFile.getPath());
		}
	}

	@SuppressLint("NewApi")
	private void updateProgressNotification() {
		long progressPercent = 0;
		Collection<MegaTransfer> transfers = mapProgressTransfers.values();

		if (sendOriginalAttachments) {
			long total = 0;
			long inProgress = 0;

			for (MegaTransfer currentTransfer : transfers) {
				if (!isVoiceClip(currentTransfer.getAppData())) {
					if (currentTransfer.getState() == MegaTransfer.STATE_COMPLETED) {
						total = total + currentTransfer.getTotalBytes();
						inProgress = inProgress + currentTransfer.getTotalBytes();
					} else {
						total = total + currentTransfer.getTotalBytes();
						inProgress = inProgress + currentTransfer.getTransferredBytes();
					}
				}
			}

			if (total > 0) {
				progressPercent = (inProgress * 100) / total;
			}
		} else {
			if (totalVideos > 0) {
				for (MegaTransfer currentTransfer : transfers) {
					if (!isVoiceClip(currentTransfer.getAppData())) {
						long individualInProgress = currentTransfer.getTransferredBytes();
						long individualTotalBytes = currentTransfer.getTotalBytes();
						long individualProgressPercent = 0;

						if (currentTransfer.getState() == MegaTransfer.STATE_COMPLETED) {
							if (MimeTypeList.typeForName(currentTransfer.getFileName()).isMp4Video()) {
								individualProgressPercent = 50;
							} else {
								individualProgressPercent = 100;
							}
						} else if (individualTotalBytes > 0) {
							if (MimeTypeList.typeForName(currentTransfer.getFileName()).isMp4Video()) {
								individualProgressPercent = individualInProgress * 50 / individualTotalBytes;
							} else {
								individualProgressPercent = individualInProgress * 100 / individualTotalBytes;
							}
						}
						progressPercent = progressPercent + individualProgressPercent / totalUploads;
					}
				}

				Collection<Integer> values = mapVideoDownsampling.values();
				int simplePercentage = 50 / totalUploads;
				for (Integer value : values) {
					int downsamplingPercent = simplePercentage * value / 100;
					progressPercent = progressPercent + downsamplingPercent;
				}
			} else {
				long total = 0;
				long inProgress = 0;

				for (MegaTransfer currentTransfer : transfers) {
					if (!isVoiceClip(currentTransfer.getAppData())) {
						total = total + currentTransfer.getTotalBytes();
						inProgress = inProgress + currentTransfer.getTransferredBytes();
					}
				}
				inProgress = inProgress * 100;
				if (total <= 0) {
					progressPercent = 0;
				} else {
					progressPercent = inProgress / total;
				}
			}
		}

		logDebug("Progress: " + progressPercent);

		String message;
		if (isOverquota != 0) {
			message = getString(R.string.overquota_alert_title);
		} else {
			int inProgress = totalUploadsCompleted == totalUploads
					? totalUploadsCompleted
					: totalUploadsCompleted + 1;

			int videosCompressed = getVideosCompressed();

			if (megaApi.areTransfersPaused(MegaTransfer.TYPE_UPLOAD)) {
				message = StringResourcesUtils.getString(R.string.upload_service_notification_paused,
						inProgress, totalUploads);
			} else if (thereAreChatUploads() || videosCompressed == mapVideoDownsampling.size()) {
				message = StringResourcesUtils.getString(R.string.upload_service_notification, inProgress, totalUploads);
			} else {
				message = StringResourcesUtils.getString(R.string.title_compress_video,
						videosCompressed + 1, mapVideoDownsampling.size());
			}
		}

        Intent intent = new Intent(ChatUploadService.this, ManagerActivityLollipop.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

		switch (isOverquota) {
			case 0:
			default:
				intent.setAction(ACTION_SHOW_TRANSFERS);
				intent.putExtra(OPENED_FROM_CHAT, true);
				break;
			case 1:
				intent.setAction(ACTION_OVERQUOTA_STORAGE);
				break;
			case 2:
				intent.setAction(ACTION_PRE_OVERQUOTA_STORAGE);
				break;
		}

		String actionString = isOverquota == 0 ? getString(R.string.chat_upload_title_notification) :
				getString(R.string.general_show_info);

		PendingIntent pendingIntent = PendingIntent.getActivity(ChatUploadService.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		Notification notification;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_CHAT_UPLOAD_ID, NOTIFICATION_CHANNEL_CHAT_UPLOAD_NAME, NotificationManager.IMPORTANCE_DEFAULT);
			channel.setShowBadge(true);
			channel.setSound(null, null);
			mNotificationManager.createNotificationChannel(channel);

			mBuilderCompat
					.setSmallIcon(R.drawable.ic_stat_notify)
					.setProgress(100, (int) progressPercent, false)
					.setContentIntent(pendingIntent)
					.setOngoing(true).setContentTitle(message)
					.setContentText(actionString)
					.setOnlyAlertOnce(true)
					.setColor(ContextCompat.getColor(this, R.color.red_600_red_300));

			notification = mBuilderCompat.build();
		} else {
			mBuilder
					.setSmallIcon(R.drawable.ic_stat_notify)
					.setProgress(100, (int) progressPercent, false)
					.setContentIntent(pendingIntent)
					.setOngoing(true).setContentTitle(message)
					.setContentText(actionString)
					.setOnlyAlertOnce(true)
					.setColor(ContextCompat.getColor(this, R.color.red_600_red_300));

			notification = mBuilder.build();
		}

		if (!isForeground) {
			logDebug("Starting foreground");
			try {
				startForeground(NOTIFICATION_CHAT_UPLOAD, notification);
				isForeground = true;
			} catch (Exception e) {
				logError("startForeground EXCEPTION", e);
				isForeground = false;
			}
		} else {
			mNotificationManager.notify(NOTIFICATION_CHAT_UPLOAD, notification);
		}
	}

	@Override
	public void onTransferStart(MegaApiJava api, MegaTransfer transfer) {

		if(transfer.getType()==MegaTransfer.TYPE_UPLOAD) {
			logDebug("onTransferStart: " + transfer.getNodeHandle());

			String appData = transfer.getAppData();

			if(appData==null) return;

			if(appData.contains(APP_DATA_CHAT)){
				launchTransferUpdateIntent(MegaTransfer.TYPE_UPLOAD);
				logDebug("This is a chat upload: " + appData);
				if(!isVoiceClip(appData)) {
					transfersCount++;
				}

				if(transfer.isStreamingTransfer()){
					return;
				}

				long id = getPendingMessageIdFromAppData(appData);

				sendBroadcast(new Intent(BROADCAST_ACTION_CHAT_TRANSFER_START)
						.putExtra(PENDING_MESSAGE_ID, id));

				//Update status and tag on db
				dbH.updatePendingMessageOnTransferStart(id, transfer.getTag());
				mapProgressTransfers.put(transfer.getTag(), transfer);
				if (!transfer.isFolderTransfer() && !isVoiceClip(appData)){
					updateProgressNotification();
				}
			}
		}
	}

	@Override
	public void onTransferUpdate(MegaApiJava api, MegaTransfer transfer) {

		if(transfer.getType()==MegaTransfer.TYPE_UPLOAD) {
			launchTransferUpdateIntent(MegaTransfer.TYPE_UPLOAD);
			logDebug("onTransferUpdate: " + transfer.getNodeHandle());

			String appData = transfer.getAppData();

			if(appData!=null && appData.contains(APP_DATA_CHAT)){
				if(transfer.isStreamingTransfer()){
					return;
				}

				if (!transfer.isFolderTransfer()){
					if (canceled) {
						logWarning("Transfer cancel: " + transfer.getNodeHandle());

						if((lock != null) && (lock.isHeld()))
							try{ lock.release(); } catch(Exception ex) {}
						if((wl != null) && (wl.isHeld()))
							try{ wl.release(); } catch(Exception ex) {}

						megaApi.cancelTransfer(transfer);
						ChatUploadService.this.cancel();
						logDebug("After cancel");
						return;
					}

					launchTransferUpdateIntent(MegaTransfer.TYPE_UPLOAD);

					if(isOverquota!=0){
						logWarning("After overquota error");
						isOverquota = 0;
					}
					mapProgressTransfers.put(transfer.getTag(), transfer);

					if(!isVoiceClip(appData)) {
						updateProgressNotification();
					}

				}
			}
		}
	}



	@Override
	public void onTransferTemporaryError(MegaApiJava api, MegaTransfer transfer, MegaError e) {
		logWarning(transfer.getNodeHandle() + "\nUpload Temporary Error: " + e.getErrorString() + "__" + e.getErrorCode());
		if((transfer.getType()==MegaTransfer.TYPE_UPLOAD)) {
			switch (e.getErrorCode())
			{
				case MegaError.API_EOVERQUOTA:
				case MegaError.API_EGOINGOVERQUOTA:
					if (e.getErrorCode() == MegaError.API_EOVERQUOTA) {
						isOverquota = 1;
					}else if (e.getErrorCode() == MegaError.API_EGOINGOVERQUOTA) {
						isOverquota = 2;
					}

					if (e.getValue() != 0) {
						logWarning("TRANSFER OVERQUOTA ERROR: " + e.getErrorCode());
					}else {
						logWarning("STORAGE OVERQUOTA ERROR: " + e.getErrorCode());

						if (!isVoiceClip(transfer.getAppData())) {
							updateProgressNotification();
						}
					}

					break;
			}
		}
	}


	@Override
	public void onTransferFinish(MegaApiJava api, MegaTransfer transfer,MegaError error) {

		if (error.getErrorCode() == MegaError.API_EBUSINESSPASTDUE) {
			sendBroadcast(new Intent(BROADCAST_ACTION_INTENT_BUSINESS_EXPIRED));
		}

		if(transfer.getType()==MegaTransfer.TYPE_UPLOAD) {
			logDebug("onTransferFinish: " + transfer.getNodeHandle());
			String appData = transfer.getAppData();

			if(appData!=null && appData.contains(APP_DATA_CHAT)){
				if(transfer.isStreamingTransfer()){
					return;
				}
				if(!isVoiceClip(appData)) {
					transfersCount--;
					totalUploadsCompleted++;
				}

				addCompletedTransfer(new AndroidCompletedTransfer(transfer, error));
				mapProgressTransfers.put(transfer.getTag(), transfer);
				launchTransferUpdateIntent(MegaTransfer.TYPE_UPLOAD);

				if (canceled) {
					logWarning("Upload cancelled: " + transfer.getNodeHandle());

					if ((lock != null) && (lock.isHeld()))
						try {
							lock.release();
						} catch (Exception ex) {
						}
					if ((wl != null) && (wl.isHeld()))
						try {
							wl.release();
						} catch (Exception ex) {
						}

					ChatUploadService.this.cancel();
					logDebug("After cancel");

					if(isVoiceClip(appData)) {
						File localFile = buildVoiceClipFile(this, transfer.getFileName());
						if (isFileAvailable(localFile) && !localFile.getName().equals(transfer.getFileName())) {
							localFile.delete();
						}
					}else {
						//Delete recursively all files and folder-??????
						deleteCacheFolderIfEmpty(getApplicationContext(), TEMPORAL_FOLDER);
					}
				}
				else{
					if (error.getErrorCode() == MegaError.API_OK) {
						logDebug("Upload OK: " + transfer.getNodeHandle());

						if(isVideoFile(transfer.getPath())){
							logDebug("Is video!!!");

							File previewDir = getPreviewFolder(this);
							File preview = new File(previewDir, MegaApiAndroid.handleToBase64(transfer.getNodeHandle()) + ".jpg");
							File thumbDir = getThumbFolder(this);
							File thumb = new File(thumbDir, MegaApiAndroid.handleToBase64(transfer.getNodeHandle()) + ".jpg");
							megaApi.createThumbnail(transfer.getPath(), thumb.getAbsolutePath());
							megaApi.createPreview(transfer.getPath(), preview.getAbsolutePath());

							attachNodes(transfer);
						}
						else if (MimeTypeList.typeForName(transfer.getPath()).isImage()){
							logDebug("Is image!!!");

							File previewDir = getPreviewFolder(this);
							File preview = new File(previewDir, MegaApiAndroid.handleToBase64(transfer.getNodeHandle()) + ".jpg");
							megaApi.createPreview(transfer.getPath(), preview.getAbsolutePath());

							File thumbDir = getThumbFolder(this);
							File thumb = new File(thumbDir, MegaApiAndroid.handleToBase64(transfer.getNodeHandle()) + ".jpg");
							megaApi.createThumbnail(transfer.getPath(), thumb.getAbsolutePath());

							attachNodes(transfer);
						}
						else if (MimeTypeList.typeForName(transfer.getPath()).isPdf()) {
							logDebug("Is pdf!!!");

							try{
								ThumbnailUtilsLollipop.createThumbnailPdf(this, transfer.getPath(), megaApi, transfer.getNodeHandle());
							}
							catch(Exception e){
								logError("Pdf thumbnail could not be created", e);
							}

							int pageNumber = 0;
							FileOutputStream out = null;

							try {

								PdfiumCore pdfiumCore = new PdfiumCore(this);
								MegaNode pdfNode = megaApi.getNodeByHandle(transfer.getNodeHandle());

								if (pdfNode == null){
									logError("pdf is NULL");
									return;
								}

								File previewDir = getPreviewFolder(this);
								File preview = new File(previewDir, MegaApiAndroid.handleToBase64(transfer.getNodeHandle()) + ".jpg");
								File file = new File(transfer.getPath());

								PdfDocument pdfDocument = pdfiumCore.newDocument(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY));
								pdfiumCore.openPage(pdfDocument, pageNumber);
								int width = pdfiumCore.getPageWidthPoint(pdfDocument, pageNumber);
								int height = pdfiumCore.getPageHeightPoint(pdfDocument, pageNumber);
								Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
								pdfiumCore.renderPageBitmap(pdfDocument, bmp, pageNumber, 0, 0, width, height);
								Bitmap resizedBitmap = resizeBitmapUpload(bmp, width, height);
								out = new FileOutputStream(preview);
								boolean result = resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // bmp is your Bitmap instance
								if(result){
									logDebug("Compress OK!");
									File oldPreview = new File(previewDir, transfer.getFileName()+".jpg");
									if (oldPreview.exists()){
										oldPreview.delete();
									}
								}
								else{
									logDebug("Not Compress");
								}
								//Attach node one the request finish
								requestSent++;
								megaApi.setPreview(pdfNode, preview.getAbsolutePath(), this);

								pdfiumCore.closeDocument(pdfDocument);

								updatePdfAttachStatus(transfer);

							} catch(Exception e) {
								logError("Pdf preview could not be created", e);
								attachNodes(transfer);
							} finally {
								try {
									if (out != null)
										out.close();
								} catch (Exception e) {
								}
							}
						}else if(isVoiceClip(transfer.getAppData())){
							logDebug("Is voice clip");
							attachVoiceClips(transfer);
						}
						else{
							logDebug("NOT video, image or pdf!");
							attachNodes(transfer);
						}
					}
					else{
						logError("Upload Error: " + transfer.getNodeHandle() + "_" + error.getErrorCode() + "___" + error.getErrorString());

						if(error.getErrorCode() == MegaError.API_EEXIST){
							logWarning("Transfer API_EEXIST: " + transfer.getNodeHandle());
						}
						else{
							if (error.getErrorCode() == MegaError.API_EOVERQUOTA) {
								isOverquota = 1;
							}
							else if (error.getErrorCode() == MegaError.API_EGOINGOVERQUOTA) {
								isOverquota = 2;
							}

							long id = getPendingMessageIdFromAppData(appData);
							//Update status and tag on db
							dbH.updatePendingMessageOnTransferFinish(id, "-1", PendingMessageSingle.STATE_ERROR_UPLOADING);
							launchErrorToChat(id);

							if (totalUploadsCompleted==totalUploads && transfersCount==0 && numberVideosPending<=0 && requestSent<=0){
								onQueueComplete();
								return;
							}
						}
					}
					File tempPic = getCacheFolder(getApplicationContext(), TEMPORAL_FOLDER);
					logDebug("IN Finish: " + transfer.getNodeHandle());
					if (isFileAvailable(tempPic) && transfer.getPath() != null) {
						if (transfer.getPath().startsWith(tempPic.getAbsolutePath())) {
							File f = new File(transfer.getPath());
							f.delete();
						}
					} else {
						logError("transfer.getPath() is NULL or temporal folder unavailable");
					}
				}

				if (totalUploadsCompleted == totalUploads && transfersCount == 0 && numberVideosPending <= 0 && requestSent <= 0) {
					onQueueComplete();
				} else if (!isVoiceClip(appData)) {
					updateProgressNotification();
				}
			}
		}
	}

	/**
	 * Checks if pendingMessages list is empty.
	 * If not, do nothing.
	 * If so, means the service has been restarted after a transfers resumption or some error happened
	 * and tries to get the PendingMessageSingle related to the current MegaTransfer from DB.
	 * If the PendingMessageSingle exists, attaches it to the chat conversation.
	 *
	 * @param id       Identifier of PendingMessageSingle.
	 * @param transfer Current MegaTransfer.
	 * @return True if the list is empty, false otherwise.
	 */
	private boolean arePendingMessagesEmpty(long id, MegaTransfer transfer) {
		if (pendingMessages != null && !pendingMessages.isEmpty()) {
			return false;
		}

		attachMessageFromDB(id, transfer);

		return true;
	}

	/**
	 * Attaches a message to a chat conversation getting it from DB.
	 *
	 * @param id       Identifier of PendingMessageSingle.
	 * @param transfer Current MegaTransfer.
	 */
	private void attachMessageFromDB(long id, MegaTransfer transfer) {
		PendingMessageSingle pendingMessage = dbH.findPendingMessageById(id);
		if (pendingMessage != null) {
			pendingMessages.add(pendingMessage);
			attach(pendingMessage, transfer);
		} else {
			logError("Message not found and not attached.");
		}
	}

	public void attachNodes(MegaTransfer transfer){
		logDebug("attachNodes()");
		//Find the pending message
		String appData = transfer.getAppData();
		long id = getPendingMessageIdFromAppData(appData);
		//Update status and nodeHandle on db
		dbH.updatePendingMessageOnTransferFinish(id, transfer.getNodeHandle()+"", PendingMessageSingle.STATE_ATTACHING);

		if (arePendingMessagesEmpty(id, transfer)) {
			return;
		}

		String fingerprint = megaApi.getFingerprint(transfer.getPath());

		boolean msgNotFound = true;
		for (PendingMessageSingle pendMsg : pendingMessages) {
			if (pendMsg.getId() == id || pendMsg.getFingerprint().equals(fingerprint)) {
				attach(pendMsg, transfer);
				msgNotFound = false;
			}
		}

		if (msgNotFound) {
			//Message not found, try to attach from DB
			attachMessageFromDB(id, transfer);
		}
	}

	public void attach (PendingMessageSingle pendMsg, MegaTransfer transfer) {
		if (megaChatApi != null) {
			logDebug("attach");
			requestSent++;
			pendMsg.setNodeHandle(transfer.getNodeHandle());
			pendMsg.setState(PendingMessageSingle.STATE_ATTACHING);
			megaChatApi.attachNode(pendMsg.getChatId(), transfer.getNodeHandle(), this);

			if(isVideoFile(transfer.getPath())){
				String pathDownsampled = pendMsg.getVideoDownSampled();
				if(transfer.getPath().equals(pathDownsampled)){
					//Delete the local temp video file
					File f = new File(transfer.getPath());

					if (f.exists()) {
						boolean deleted = f.delete();
						if(!deleted){
							logError("ERROR: Local file not deleted!");
						}
					}
				}
			}

		}
	}

	public void attachVoiceClips(MegaTransfer transfer){
		logDebug("attachVoiceClips()");
		//Find the pending message
		long id = getPendingMessageIdFromAppData(transfer.getAppData());
		//Update status and nodeHandle on db
		dbH.updatePendingMessageOnTransferFinish(id, transfer.getNodeHandle()+"", PendingMessageSingle.STATE_ATTACHING);

		if (arePendingMessagesEmpty(id, transfer)) {
			return;
		}

		for (PendingMessageSingle pendMsg : pendingMessages) {
			if (pendMsg.getId() == id) {
				pendMsg.setNodeHandle(transfer.getNodeHandle());
				pendMsg.setState(PendingMessageSingle.STATE_ATTACHING);
				megaChatApi.attachVoiceMessage(pendMsg.getChatId(), transfer.getNodeHandle(), this);
				return;
			}
		}

		//Message not found, try to attach from DB
		attachMessageFromDB(id, transfer);
	}



	public void updatePdfAttachStatus(MegaTransfer transfer){
		logDebug("updatePdfAttachStatus");
		//Find the pending message
		for(int i=0; i<pendingMessages.size();i++){
			PendingMessageSingle pendMsg = pendingMessages.get(i);

			if(pendMsg.getFilePath().equals(transfer.getPath())){
				if(pendMsg.getNodeHandle()==-1){
					logDebug("Set node handle to the pdf file: " + transfer.getNodeHandle());
					pendMsg.setNodeHandle(transfer.getNodeHandle());
				}
				else{
					logError("Set node handle error");
				}
			}
		}

		//Upadate node handle in db
		long id = getPendingMessageIdFromAppData(transfer.getAppData());
		//Update status and nodeHandle on db
		dbH.updatePendingMessageOnTransferFinish(id, transfer.getNodeHandle()+"", PendingMessageSingle.STATE_ATTACHING);

		if (pendingMessages != null && !pendingMessages.isEmpty()) {
			for (PendingMessageSingle pendMsg : pendingMessages) {
				if (pendMsg.getId() == id) {
					return;
				}
			}
		}

		//Message not found, try to get it from DB.
		PendingMessageSingle pendingMessage = dbH.findPendingMessageById(id);
		if (pendingMessage != null) {
			pendingMessages.add(pendingMessage);
		} else {
			logError("Message not found, not added");
		}
	}

	public void attachPdfNode(long nodeHandle){
		logDebug("Node Handle: " + nodeHandle);
		//Find the pending message
		for(int i=0; i<pendingMessages.size();i++){
			PendingMessageSingle pendMsg = pendingMessages.get(i);

			if(pendMsg.getNodeHandle()==nodeHandle){
				if(megaChatApi!=null){
					logDebug("Send node: " + nodeHandle + " to chat: " + pendMsg.getChatId());
					requestSent++;
					MegaNode nodePdf = megaApi.getNodeByHandle(nodeHandle);
					if(nodePdf.hasPreview()){
						logDebug("The pdf node has preview");
					}
					megaChatApi.attachNode(pendMsg.getChatId(), nodeHandle, this);
				}
			}
			else{
				logError("PDF attach error");
			}
		}
	}

	@Override
	public void onRequestStart(MegaApiJava api, MegaRequest request) {
		logDebug("onRequestStart: " + request.getName());
		if (request.getType() == MegaRequest.TYPE_COPY){
			updateProgressNotification();
		}
		else if (request.getType() == MegaRequest.TYPE_SET_ATTR_FILE) {
			logDebug("TYPE_SET_ATTR_FILE");
		}
	}

	@Override
	public void onRequestFinish(MegaApiJava api, MegaRequest request, MegaError e) {
		logDebug("UPLOAD: onRequestFinish "+request.getRequestString());

		//Send the file without preview if the set attribute fails
		if(request.getType() == MegaRequest.TYPE_SET_ATTR_FILE && request.getParamType()==MegaApiJava.ATTR_TYPE_PREVIEW){
			requestSent--;
			long handle = request.getNodeHandle();
			MegaNode node = megaApi.getNodeByHandle(handle);
			if(node!=null){
				String nodeName = node.getName();
				if(MimeTypeList.typeForName(nodeName).isPdf()){
					attachPdfNode(handle);
				}
			}
		}

		if (e.getErrorCode()==MegaError.API_OK) {
			logDebug("onRequestFinish OK");
		}
		else {
			logError("onRequestFinish:ERROR: " + e.getErrorCode());

			if(e.getErrorCode()==MegaError.API_EOVERQUOTA){
				logWarning("OVERQUOTA ERROR: "+e.getErrorCode());
				isOverquota = 1;
			}
			else if(e.getErrorCode()==MegaError.API_EGOINGOVERQUOTA){
				logWarning("PRE-OVERQUOTA ERROR: "+e.getErrorCode());
				isOverquota = 2;
			}
			onQueueComplete();
		}
	}

	@Override
	public void onRequestTemporaryError(MegaApiJava api, MegaRequest request,
			MegaError e) {
		logWarning("onRequestTemporaryError: " + request.getName());
	}

	@Override
	public void onRequestUpdate(MegaApiJava api, MegaRequest request) {
		logDebug("onRequestUpdate: " + request.getName());
	}

	@Override
	public boolean onTransferData(MegaApiJava api, MegaTransfer transfer, byte[] buffer)
	{
		return true;
	}

	@Override
	public void onRequestStart(MegaChatApiJava api, MegaChatRequest request) {

	}

	@Override
	public void onRequestUpdate(MegaChatApiJava api, MegaChatRequest request) {

	}

	@Override
	public void onRequestFinish(MegaChatApiJava api, MegaChatRequest request, MegaChatError e) {

		if(request.getType() == MegaChatRequest.TYPE_ATTACH_NODE_MESSAGE){
            requestSent--;
			if(e.getErrorCode()==MegaChatError.ERROR_OK){
				logDebug("Attachment sent correctly");
				MegaNodeList nodeList = request.getMegaNodeList();

				//Find the pending message
				for(int i=0; i<pendingMessages.size();i++){
					PendingMessageSingle pendMsg = pendingMessages.get(i);

					//Check node handles - if match add to DB the karere temp id of the message
					long nodeHandle = pendMsg.getNodeHandle();
					MegaNode node = nodeList.get(0);
					if(node.getHandle()==nodeHandle){
						logDebug("The message MATCH!!");
						long tempId = request.getMegaChatMessage().getTempId();
						logDebug("The tempId of the message is: " + tempId);
						dbH.updatePendingMessageOnAttach(pendMsg.getId(), tempId+"", PendingMessageSingle.STATE_SENT);
						pendingMessages.remove(i);
						break;
					}
				}
			}
			else{
				logWarning("Attachment not correctly sent: " + e.getErrorCode()+" " + e.getErrorString());
				MegaNodeList nodeList = request.getMegaNodeList();

				//Find the pending message
				for(int i=0; i<pendingMessages.size();i++){
					PendingMessageSingle pendMsg = pendingMessages.get(i);
					//Check node handles - if match add to DB the karere temp id of the message
					long nodeHandle = pendMsg.getNodeHandle();
					MegaNode node = nodeList.get(0);
					if(node.getHandle()==nodeHandle){
						MegaApplication.getChatManagement().removeMsgToDelete(pendMsg.getId());
						logDebug("The message MATCH!!");
						dbH.updatePendingMessageOnAttach(pendMsg.getId(), -1+"", PendingMessageSingle.STATE_ERROR_ATTACHING);

						launchErrorToChat(pendMsg.getId());
						break;
					}
				}
			}
		}

		if (totalUploadsCompleted==totalUploads && transfersCount==0 && numberVideosPending<=0 && requestSent<=0){
			onQueueComplete();
		}
	}

	public void launchErrorToChat(long id){
		logDebug("ID: " + id);

		//Find the pending message
		for(int i=0; i<pendingMessages.size();i++) {
			PendingMessageSingle pendMsg = pendingMessages.get(i);
			if(pendMsg.getId() == id){
				long openChatId = MegaApplication.getOpenChatId();
				if(pendMsg.getChatId()==openChatId){
					logWarning("Error update activity");
					Intent intent;
					intent = new Intent(this, ChatActivityLollipop.class);
					intent.setAction(ACTION_UPDATE_ATTACHMENT);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					intent.putExtra("ID_MSG", pendMsg.getId());
					intent.putExtra("IS_OVERQUOTA", isOverquota);
					startActivity(intent);
				}
			}
		}
	}

	@Override
	public void onRequestTemporaryError(MegaChatApiJava api, MegaChatRequest request, MegaChatError e) {

	}

	private void showStorageOverquotaNotification(){
		logDebug("showStorageOverquotaNotification");

		String contentText = getString(R.string.download_show_info);
		String message = getString(R.string.overquota_alert_title);

		Intent intent = new Intent(this, ManagerActivityLollipop.class);

		intent.setAction(isOverquota == OVERQUOTA_STORAGE_STATE
				? ACTION_OVERQUOTA_STORAGE
				: ACTION_PRE_OVERQUOTA_STORAGE);

		Notification notification;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_CHAT_UPLOAD_ID, NOTIFICATION_CHANNEL_CHAT_UPLOAD_NAME, NotificationManager.IMPORTANCE_DEFAULT);
			channel.setShowBadge(true);
			channel.setSound(null, null);
			mNotificationManager.createNotificationChannel(channel);

			mBuilderCompat
					.setSmallIcon(R.drawable.ic_stat_notify)
					.setColor(ContextCompat.getColor(this,R.color.red_600_red_300))
					.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, intent, 0))
					.setAutoCancel(true).setTicker(contentText)
					.setContentTitle(message).setContentText(contentText)
					.setOngoing(false);

			notification = mBuilderCompat.build();
		}
		else {
			mBuilder
					.setColor(ContextCompat.getColor(this,R.color.red_600_red_300))
					.setSmallIcon(R.drawable.ic_stat_notify)
					.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, intent, 0))
					.setAutoCancel(true).setTicker(contentText)
					.setContentTitle(message).setContentText(contentText)
					.setOngoing(false);
			mBuilderCompat.setColor(ContextCompat.getColor(this,R.color.red_600_red_300));

			notification = mBuilder.build();
		}

		mNotificationManager.notify(NOTIFICATION_STORAGE_OVERQUOTA, notification);
	}

	private boolean isVoiceClip(String appData) {
		return !isTextEmpty(appData) && appData.contains(APP_DATA_VOICE_CLIP);
	}

	/**
	 * Checks if there are chat uploads in progress, regardless of the voice clips.
	 * @return True if there are chat uploads in progress, false otherwise.
	 */
	private boolean thereAreChatUploads() {
		if (megaApi.getNumPendingUploads() > 0) {
			MegaTransferData transferData = megaApi.getTransferData(null);
			if (transferData == null) {
				return false;
			}

			for (int i = 0; i < transferData.getNumUploads(); i++) {
				MegaTransfer transfer = megaApi.getTransferByTag(transferData.getUploadTag(i));
				if (transfer == null) {
					continue;
				}

				String data = transfer.getAppData();
				if (!isTextEmpty(data) && data.contains(APP_DATA_CHAT) && !isVoiceClip(data)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Gets the number of videos already compressed.
	 *
	 * @return Number of videos already compressed.
	 */
	private int getVideosCompressed() {
		int videosCompressed = 0;

		for (Integer percentage : mapVideoDownsampling.values()) {
			if (percentage == 100) {
				videosCompressed++;
			}
		}

		return videosCompressed;
	}

	/**
	 * Checks if a video pending message path is valid and is compressing:
	 *  - If the path is not valid or the video is already compressing, does nothing.
	 *  - If not, launches a broadcast to retry the upload.
	 *
	 * @param intent Intent containing the pending message with all the needed info.
	 */
	private void checkCompressingMessage(Intent intent) {
		String fileName = intent.getStringExtra(INTENT_EXTRA_KEY_FILE_NAME);

		if (isTextEmpty(fileName)) {
			logWarning("fileName is not valid, no check is needed.");
			return;
		}

		try {
			fileName = fileName.substring(0, fileName.lastIndexOf("."));
		} catch (Exception e) {
			logWarning("Exception getting file name without extension.", e);
		}

		for (String downSamplingPath: mapVideoDownsampling.keySet()) {
			if (downSamplingPath.contains(fileName)) {
				//Video message already compressing
				return;
			}
		}

		//Video message not compressing, need to retry upload
		long chatId = intent.getLongExtra(CHAT_ID, MEGACHAT_INVALID_HANDLE);
		long pendingMsgId = intent.getLongExtra(INTENT_EXTRA_PENDING_MESSAGE_ID, MEGACHAT_INVALID_HANDLE);

		sendBroadcast(new Intent(BROADCAST_ACTION_RETRY_PENDING_MESSAGE)
				.putExtra(INTENT_EXTRA_PENDING_MESSAGE_ID, pendingMsgId)
				.putExtra(CHAT_ID, chatId));
	}
}