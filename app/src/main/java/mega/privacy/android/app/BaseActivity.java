package mega.privacy.android.app;

import static mega.privacy.android.app.constants.BroadcastConstants.ACTION_TRANSFER_OVER_QUOTA;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_COOKIE_SETTINGS_SAVED;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_INTENT_EVENT_ACCOUNT_BLOCKED;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_INTENT_SHOWSNACKBAR_TRANSFERS_FINISHED;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_INTENT_TAKEN_DOWN_FILES;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_INTENT_TRANSFER_UPDATE;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_RESUME_TRANSFERS;
import static mega.privacy.android.app.constants.BroadcastConstants.BROADCAST_ACTION_SHOW_SNACKBAR;
import static mega.privacy.android.app.constants.BroadcastConstants.DOWNLOAD_TRANSFER;
import static mega.privacy.android.app.constants.BroadcastConstants.DOWNLOAD_TRANSFER_OPEN;
import static mega.privacy.android.app.constants.BroadcastConstants.EVENT_NUMBER;
import static mega.privacy.android.app.constants.BroadcastConstants.EVENT_TEXT;
import static mega.privacy.android.app.constants.BroadcastConstants.FILE_EXPLORER_CHAT_UPLOAD;
import static mega.privacy.android.app.constants.BroadcastConstants.NODE_HANDLE;
import static mega.privacy.android.app.constants.BroadcastConstants.NODE_LOCAL_PATH;
import static mega.privacy.android.app.constants.BroadcastConstants.NODE_NAME;
import static mega.privacy.android.app.constants.BroadcastConstants.NUMBER_FILES;
import static mega.privacy.android.app.constants.BroadcastConstants.SNACKBAR_TEXT;
import static mega.privacy.android.app.constants.BroadcastConstants.TRANSFER_TYPE;
import static mega.privacy.android.app.constants.BroadcastConstants.UPLOAD_TRANSFER;
import static mega.privacy.android.app.constants.EventConstants.EVENT_MEETING_INCOMPATIBILITY_SHOW;
import static mega.privacy.android.app.constants.EventConstants.EVENT_PURCHASES_UPDATED;
import static mega.privacy.android.app.lollipop.LoginFragmentLollipop.NAME_USER_LOCKED;
import static mega.privacy.android.app.middlelayer.iab.BillingManager.RequestCode.REQ_CODE_BUY;
import static mega.privacy.android.app.utils.AlertsAndWarnings.showResumeTransfersWarning;
import static mega.privacy.android.app.utils.Constants.ACCOUNT_NOT_BLOCKED;
import static mega.privacy.android.app.utils.Constants.ACTION_SHOW_UPGRADE_ACCOUNT;
import static mega.privacy.android.app.utils.Constants.BROADCAST_ACTION_INTENT_BUSINESS_EXPIRED;
import static mega.privacy.android.app.utils.Constants.BROADCAST_ACTION_INTENT_SIGNAL_PRESENCE;
import static mega.privacy.android.app.utils.Constants.BROADCAST_ACTION_INTENT_SSL_VERIFICATION_FAILED;
import static mega.privacy.android.app.utils.Constants.CHAT_ID;
import static mega.privacy.android.app.utils.Constants.COPYRIGHT_ACCOUNT_BLOCK;
import static mega.privacy.android.app.utils.Constants.DISABLED_BUSINESS_ACCOUNT_BLOCK;
import static mega.privacy.android.app.utils.Constants.DISMISS_ACTION_SNACKBAR;
import static mega.privacy.android.app.utils.Constants.EVENT_PSA;
import static mega.privacy.android.app.utils.Constants.INVALID_VALUE;
import static mega.privacy.android.app.utils.Constants.INVITE_CONTACT_TYPE;
import static mega.privacy.android.app.utils.Constants.LOGIN_FRAGMENT;
import static mega.privacy.android.app.utils.Constants.MESSAGE_SNACKBAR_TYPE;
import static mega.privacy.android.app.utils.Constants.MULTIPLE_COPYRIGHT_ACCOUNT_BLOCK;
import static mega.privacy.android.app.utils.Constants.MUTE_NOTIFICATIONS_SNACKBAR_TYPE;
import static mega.privacy.android.app.utils.Constants.NOT_SPACE_SNACKBAR_TYPE;
import static mega.privacy.android.app.utils.Constants.OPEN_FILE_SNACKBAR_TYPE;
import static mega.privacy.android.app.utils.Constants.PERMISSIONS_TYPE;
import static mega.privacy.android.app.utils.Constants.REMOVED_BUSINESS_ACCOUNT_BLOCK;
import static mega.privacy.android.app.utils.Constants.SMS_VERIFICATION_ACCOUNT_BLOCK;
import static mega.privacy.android.app.utils.Constants.SNACKBAR_IMCOMPATIBILITY_TYPE;
import static mega.privacy.android.app.utils.Constants.SNACKBAR_TYPE;
import static mega.privacy.android.app.utils.Constants.VISIBLE_FRAGMENT;
import static mega.privacy.android.app.utils.Constants.WEAK_PROTECTION_ACCOUNT_BLOCK;
import static mega.privacy.android.app.utils.DBUtil.callToAccountDetails;
import static mega.privacy.android.app.utils.LogUtil.logDebug;
import static mega.privacy.android.app.utils.LogUtil.logError;
import static mega.privacy.android.app.utils.LogUtil.logInfo;
import static mega.privacy.android.app.utils.LogUtil.logWarning;
import static mega.privacy.android.app.utils.LogUtil.setStatusLoggerKarere;
import static mega.privacy.android.app.utils.LogUtil.setStatusLoggerSDK;
import static mega.privacy.android.app.utils.TextUtil.isTextEmpty;
import static mega.privacy.android.app.utils.TimeUtils.createAndShowCountDownTimer;
import static mega.privacy.android.app.utils.TimeUtils.getHumanizedTime;
import static mega.privacy.android.app.utils.Util.dp2px;
import static mega.privacy.android.app.utils.Util.getRootViewFromContext;
import static mega.privacy.android.app.utils.Util.isTopActivity;
import static mega.privacy.android.app.utils.Util.setAppFontSize;
import static mega.privacy.android.app.utils.Util.showErrorAlertDialog;
import static mega.privacy.android.app.utils.billing.PaymentUtils.getSkuDetails;
import static mega.privacy.android.app.utils.billing.PaymentUtils.getSubscriptionRenewalType;
import static mega.privacy.android.app.utils.billing.PaymentUtils.getSubscriptionType;
import static mega.privacy.android.app.utils.billing.PaymentUtils.updateAccountInfo;
import static mega.privacy.android.app.utils.billing.PaymentUtils.updatePricing;
import static mega.privacy.android.app.utils.billing.PaymentUtils.updateSubscriptionLevel;
import static mega.privacy.android.app.utils.permission.PermissionUtils.requestPermission;
import static nz.mega.sdk.MegaApiJava.BUSINESS_STATUS_EXPIRED;
import static nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.jeremyliao.liveeventbus.LiveEventBus;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;
import androidx.lifecycle.Observer;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import kotlin.Pair;
import mega.privacy.android.app.components.saver.AutoPlayInfo;
import mega.privacy.android.app.globalmanagement.MyAccountInfo;
import mega.privacy.android.app.interfaces.ActivityLauncher;
import mega.privacy.android.app.interfaces.PermissionRequester;
import mega.privacy.android.app.interfaces.SnackbarShower;
import mega.privacy.android.app.listeners.ChatLogoutListener;
import mega.privacy.android.app.lollipop.LoginActivityLollipop;
import mega.privacy.android.app.lollipop.ManagerActivityLollipop;
import mega.privacy.android.app.meeting.activity.MeetingActivity;
import mega.privacy.android.app.middlelayer.iab.BillingManager;
import mega.privacy.android.app.middlelayer.iab.BillingUpdatesListener;
import mega.privacy.android.app.middlelayer.iab.MegaPurchase;
import mega.privacy.android.app.middlelayer.iab.MegaSku;
import mega.privacy.android.app.psa.Psa;
import mega.privacy.android.app.psa.PsaWebBrowser;
import mega.privacy.android.app.service.iab.BillingManagerImpl;
import mega.privacy.android.app.service.iar.RatingHandlerImpl;
import mega.privacy.android.app.smsVerification.SMSVerificationActivity;
import mega.privacy.android.app.snackbarListeners.SnackbarNavigateOption;
import mega.privacy.android.app.utils.ColorUtils;
import mega.privacy.android.app.utils.MegaNodeUtil;
import mega.privacy.android.app.utils.StringResourcesUtils;
import mega.privacy.android.app.utils.Util;
import mega.privacy.android.app.utils.permission.PermissionUtils;
import nz.mega.sdk.MegaAccountDetails;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaChatApi;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaUser;

@AndroidEntryPoint
public class BaseActivity extends AppCompatActivity implements ActivityLauncher, PermissionRequester, SnackbarShower,
        BillingUpdatesListener {

    private static final String EXPIRED_BUSINESS_ALERT_SHOWN = "EXPIRED_BUSINESS_ALERT_SHOWN";
    private static final String TRANSFER_OVER_QUOTA_WARNING_SHOWN = "TRANSFER_OVER_QUOTA_WARNING_SHOWN";
    private static final String RESUME_TRANSFERS_WARNING_SHOWN = "RESUME_TRANSFERS_WARNING_SHOWN";

    @Inject
    MyAccountInfo myAccountInfo;

    private BillingManager billingManager;
    private List<MegaSku> skuDetailsList;

    private BaseActivity baseActivity;

    protected  MegaApplication app;

    protected MegaApiAndroid megaApi;
    protected MegaApiAndroid megaApiFolder;
    protected MegaChatApiAndroid megaChatApi;

    protected DatabaseHandler dbH;

    private AlertDialog sslErrorDialog;

    private boolean delaySignalPresence = false;

    private enum LogsType {
        SDK_LOGS, KARERE_LOGS
    }

    private boolean isGeneralTransferOverQuotaWarningShown;
    private AlertDialog transferGeneralOverQuotaWarning;
    private Snackbar snackbar;

    /**
     * Contains the info of a node that to be opened in-app.
     */
    private AutoPlayInfo autoPlayInfo;

    /**
     * Load the psa in the web browser fragment if the psa is a web one and this activity
     * is on the top of the task stack
     */
    private final Observer<Psa> psaObserver = psa -> {
        if (psa.getUrl() != null && isTopActivity(getClass().getName(), this)) {
            loadPsaInWebBrowser(psa);
        }
    };

    public BaseActivity() {
        app = MegaApplication.getInstance();

        //Will be checked again and initialized at `onCreate()`
        if (app != null) {
            megaApi = app.getMegaApi();
            megaApiFolder = app.getMegaApiFolder();
            megaChatApi = app.getMegaChatApi();
            dbH = app.getDbH();
        }
    }

    private AlertDialog expiredBusinessAlert;
    private boolean isExpiredBusinessAlertShown = false;

    private boolean isPaused = false;

    private DisplayMetrics outMetrics;

    //Indicates when the activity should finish due to some error
    private static boolean finishActivityAtError;

    private boolean isResumeTransfersWarningShown;
    private AlertDialog resumeTransfersWarning;

    private FrameLayout psaWebBrowserContainer;

    /**
     * Every sub-class activity has an embedded PsaWebBrowser fragment, either visible or invisible.
     * In order to show the newly retrieved PSA at any occasion
     */
    protected PsaWebBrowser psaWebBrowser;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        baseActivity = this;

        Display display = getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics ();
        display.getMetrics(outMetrics);

        super.onCreate(savedInstanceState);
        checkMegaObjects();

        registerReceiver(sslErrorReceiver,
                new IntentFilter(BROADCAST_ACTION_INTENT_SSL_VERIFICATION_FAILED));

        registerReceiver(signalPresenceReceiver,
                new IntentFilter(BROADCAST_ACTION_INTENT_SIGNAL_PRESENCE));

        registerReceiver(accountBlockedReceiver,
                new IntentFilter(BROADCAST_ACTION_INTENT_EVENT_ACCOUNT_BLOCKED));

        registerReceiver(businessExpiredReceiver,
                new IntentFilter(BROADCAST_ACTION_INTENT_BUSINESS_EXPIRED));

        registerReceiver(takenDownFilesReceiver,
                new IntentFilter(BROADCAST_ACTION_INTENT_TAKEN_DOWN_FILES));

        registerReceiver(transferFinishedReceiver,
                new IntentFilter(BROADCAST_ACTION_INTENT_SHOWSNACKBAR_TRANSFERS_FINISHED));

        IntentFilter filterTransfers = new IntentFilter(BROADCAST_ACTION_INTENT_TRANSFER_UPDATE);
        filterTransfers.addAction(ACTION_TRANSFER_OVER_QUOTA);
        registerReceiver(transferOverQuotaReceiver, filterTransfers);

        registerReceiver(showSnackbarReceiver,
                new IntentFilter(BROADCAST_ACTION_SHOW_SNACKBAR));

        registerReceiver(resumeTransfersReceiver,
                new IntentFilter(BROADCAST_ACTION_RESUME_TRANSFERS));

        registerReceiver(cookieSettingsReceiver,
                new IntentFilter(BROADCAST_ACTION_COOKIE_SETTINGS_SAVED));

        if (savedInstanceState != null) {
            isExpiredBusinessAlertShown = savedInstanceState.getBoolean(EXPIRED_BUSINESS_ALERT_SHOWN, false);
            if (isExpiredBusinessAlertShown) {
                showExpiredBusinessAlert();
            }

            isGeneralTransferOverQuotaWarningShown = savedInstanceState.getBoolean(TRANSFER_OVER_QUOTA_WARNING_SHOWN, false);
            if (isGeneralTransferOverQuotaWarningShown) {
                showGeneralTransferOverQuotaWarning();
            }

            isResumeTransfersWarningShown = savedInstanceState.getBoolean(RESUME_TRANSFERS_WARNING_SHOWN, false);
            if (isResumeTransfersWarningShown) {
                showResumeTransfersWarning(this);
            }
        }

        // Add an invisible full screen Psa web browser fragment to the activity.
        // Then show or hide it for browsing the PSA.
        addPsaWebBrowser();
        LiveEventBus.get(EVENT_PSA, Psa.class).observeStickyForever(psaObserver);

        if (shouldSetStatusBarTextColor()) {
            ColorUtils.setStatusBarTextColor(this);
        }
    }

    protected boolean shouldSetStatusBarTextColor() {
        return true;
    }

    /**
     * Create a fragment container and the web browser fragment, add them to the activity
     */
    private void addPsaWebBrowser() {
        // Execute after the sub-class activity finish its setContentView()
        uiHandler.post(() -> {
            psaWebBrowserContainer = new FrameLayout(BaseActivity.this);
            psaWebBrowserContainer.setId(R.id.psa_web_browser_container);

            ViewGroup contentView = findViewById(android.R.id.content);

            contentView.addView(psaWebBrowserContainer,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));

            psaWebBrowser = new PsaWebBrowser();

            // Don't put the fragment to the back stack. Since pressing back key just hide it,
            // never pop it up. onBackPressed() will let PSA browser to consume the back
            // key event anyway
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.psa_web_browser_container, psaWebBrowser)
                    .commitNowAllowingStateLoss();
        });
    }

    /**
     * Display the new url PSA in the web browser
     *
     * @param psa the psa to display
     */
    private void loadPsaInWebBrowser(Psa psa) {
        String url = psa.getUrl();
        if (psaWebBrowser == null || url == null) return;

        psaWebBrowser.loadPsa(url, psa.getId());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EXPIRED_BUSINESS_ALERT_SHOWN, isExpiredBusinessAlertShown);
        outState.putBoolean(TRANSFER_OVER_QUOTA_WARNING_SHOWN, isGeneralTransferOverQuotaWarningShown);
        outState.putBoolean(RESUME_TRANSFERS_WARNING_SHOWN, isResumeTransfersWarningShown);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        checkMegaObjects();
        isPaused = true;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        setAppFontSize(this);

        checkMegaObjects();
        isPaused = false;

        retryConnectionsAndSignalPresence();
    }

    /**
     * Checks if the current activity is in foreground.
     *
     * @return True if the current activity is in foreground, false otherwise.
     */
    protected boolean isActivityInForeground() {
        return !isPaused;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(sslErrorReceiver);
        unregisterReceiver(signalPresenceReceiver);
        unregisterReceiver(accountBlockedReceiver);
        unregisterReceiver(businessExpiredReceiver);
        unregisterReceiver(takenDownFilesReceiver);
        unregisterReceiver(transferFinishedReceiver);
        unregisterReceiver(showSnackbarReceiver);
        unregisterReceiver(transferOverQuotaReceiver);
        unregisterReceiver(resumeTransfersReceiver);

        if (transferGeneralOverQuotaWarning != null) {
            transferGeneralOverQuotaWarning.dismiss();
        }

        if (resumeTransfersWarning != null) {
            resumeTransfersWarning.dismiss();
        }

        LiveEventBus.get(EVENT_PSA, Psa.class).removeObserver(psaObserver);

        super.onDestroy();
    }

    /**
     * Method to check if exist all required objects (MegaApplication, MegaApiAndroid and MegaChatApiAndroid )
     * or create them if necessary.
     */
    private void checkMegaObjects() {

        if (app == null) {
            app = MegaApplication.getInstance();
        }

        if (app != null) {
            if (megaApi == null){
                megaApi = app.getMegaApi();
            }

            if (megaApiFolder == null) {
                megaApiFolder = app.getMegaApiFolder();
            }

            if (megaChatApi == null){
                megaChatApi = app.getMegaChatApi();
            }

            if (dbH == null) {
                dbH = app.getDbH();
            }
        }
    }

    /**
     * Broadcast receiver to manage the errors shown and actions when an account is blocked.
     */
    private BroadcastReceiver accountBlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null
                    || !intent.getAction().equals(BROADCAST_ACTION_INTENT_EVENT_ACCOUNT_BLOCKED))
                return;

            checkWhyAmIBlocked(intent.getLongExtra(EVENT_NUMBER, -1), intent.getStringExtra(EVENT_TEXT));
        }
    };

    /**
     * Broadcast receiver to manage a possible SSL verification error.
     */
    private BroadcastReceiver sslErrorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                logDebug("BROADCAST TO MANAGE A SSL VERIFICATION ERROR");
                if (sslErrorDialog != null && sslErrorDialog.isShowing()) return;
                showSSLErrorDialog();
            }
        }
    };

    /**
     * Broadcast to send presence after first launch of app
     */
    private BroadcastReceiver signalPresenceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                logDebug("BROADCAST TO SEND SIGNAL PRESENCE");
                if(delaySignalPresence && megaChatApi != null && megaChatApi.getPresenceConfig() != null && !megaChatApi.getPresenceConfig().isPending()){
                    delaySignalPresence = false;
                    retryConnectionsAndSignalPresence();
                }
            }
        }
    };

    private BroadcastReceiver businessExpiredReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                showExpiredBusinessAlert();
            }
        }
    };

    /**
     * Broadcast to show taken down files info
     */
    private BroadcastReceiver takenDownFilesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            logDebug("BROADCAST INFORM THERE ARE TAKEN DOWN FILES IMPLIED IN ACTION");
            int numberFiles = intent.getIntExtra(NUMBER_FILES, 1);
            Util.showSnackbar(baseActivity, getResources().getQuantityString(R.plurals.alert_taken_down_files, numberFiles, numberFiles));
        }
    };

    /**
     * Broadcast to show a snackbar when all the transfers finish
     */
    private BroadcastReceiver transferFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || isPaused) {
                return;
            }

            if (intent.getBooleanExtra(FILE_EXPLORER_CHAT_UPLOAD, false)) {
                Util.showSnackbar(baseActivity, MESSAGE_SNACKBAR_TYPE, null, intent.getLongExtra(CHAT_ID, MEGACHAT_INVALID_HANDLE));
                return;
            }

            int numTransfers = intent.getIntExtra(NUMBER_FILES, 1);
            String message = getResources().getQuantityString(R.plurals.download_finish, numTransfers, numTransfers);

            switch (intent.getStringExtra(TRANSFER_TYPE)) {
                case DOWNLOAD_TRANSFER:
                    Util.showSnackbar(baseActivity, message);
                    break;

                case UPLOAD_TRANSFER:
                    message = getResources().getQuantityString(R.plurals.upload_finish, numTransfers, numTransfers);
                    Util.showSnackbar(baseActivity, message);
                    break;

                case DOWNLOAD_TRANSFER_OPEN:
                    autoPlayInfo = new AutoPlayInfo(intent.getStringExtra(NODE_NAME), intent.getLongExtra(NODE_HANDLE, INVALID_VALUE), intent.getStringExtra(NODE_LOCAL_PATH), true);
                    showSnackbar(OPEN_FILE_SNACKBAR_TYPE, message, MEGACHAT_INVALID_HANDLE);
                    break;
            }
        }
    };

    /**
     * Open the downloaded file.
     */
    private void openDownloadedFile() {
        if(autoPlayInfo != null) {
            MegaNodeUtil.autoPlayNode(BaseActivity.this, autoPlayInfo, BaseActivity.this,BaseActivity.this);
            autoPlayInfo = null;
        }
    }

    private BroadcastReceiver showSnackbarReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isPaused || intent == null || intent.getAction() == null
                    || !intent.getAction().equals(BROADCAST_ACTION_SHOW_SNACKBAR))
                return;

            String message = intent.getStringExtra(SNACKBAR_TEXT);
            if (!isTextEmpty(message)) {
                Util.showSnackbar(baseActivity, message);
            }
        }
    };

    /**
     * Broadcast to show a warning when transfer over quota occurs.
     */
    private BroadcastReceiver transferOverQuotaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null
                    || !intent.getAction().equals(ACTION_TRANSFER_OVER_QUOTA)
                    || !isActivityInForeground()) {
                return;
            }

            showGeneralTransferOverQuotaWarning();
        }
    };

    /**
     * Broadcast to show a warning when it tries to upload files to a chat conversation
     * and the transfers are paused.
     */
    private BroadcastReceiver resumeTransfersReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null
                    || !intent.getAction().equals(BROADCAST_ACTION_RESUME_TRANSFERS)
                    || isResumeTransfersWarningShown()
                    || !isActivityInForeground()) {
                return;
            }

            MegaApplication.getTransfersManagement().setResumeTransfersWarningHasAlreadyBeenShown(true);
            showResumeTransfersWarning(baseActivity);
        }
    };

    /**
     * Broadcast to show a snackbar when the Cookie settings has been saved
     */
    protected BroadcastReceiver cookieSettingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isPaused || !isActivityInForeground() || intent == null) {
                return;
            }

            View view = getWindow().getDecorView().findViewById(android.R.id.content);
            if (view != null) {
                showSnackbar(view, StringResourcesUtils.getString(R.string.dialog_cookie_snackbar_saved));
            }
        }
    };

    /**
     * Method to display an alert dialog indicating that the MEGA SSL key
     * can't be verified (API_ESSL Error) and giving the user several options.
     */
    private void showSSLErrorDialog(){

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View v = inflater.inflate(R.layout.dialog_three_vertical_buttons, null);
        builder.setView(v);

        TextView title = v.findViewById(R.id.dialog_title);
        TextView text = v.findViewById(R.id.dialog_text);

        Button retryButton = v.findViewById(R.id.dialog_first_button);
        Button openBrowserButton = v.findViewById(R.id.dialog_second_button);
        Button dismissButton = v.findViewById(R.id.dialog_third_button);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.RIGHT;

        title.setText(R.string.ssl_error_dialog_title);
        text.setText(R.string.ssl_error_dialog_text);
        retryButton.setText(R.string.general_retry);
        openBrowserButton.setText(R.string.general_open_browser);
        dismissButton.setText(R.string.general_dismiss);

        sslErrorDialog = builder.create();
        sslErrorDialog.setCancelable(false);
        sslErrorDialog.setCanceledOnTouchOutside(false);

        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sslErrorDialog.dismiss();
                megaApi.reconnect();
                megaApiFolder.reconnect();
            }
        });

        openBrowserButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sslErrorDialog.dismiss();
                Uri uriUrl = Uri.parse("https://mega.nz/");
                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
                startActivity(launchBrowser);
            }
        });

        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sslErrorDialog.dismiss();
                megaApi.setPublicKeyPinning(false);
                megaApi.reconnect();
                megaApiFolder.setPublicKeyPinning(false);
                megaApiFolder.reconnect();
            }
        });

        sslErrorDialog.show();
    }

    protected void retryConnectionsAndSignalPresence(){
        logDebug("retryConnectionsAndSignalPresence");
        try{
            if (megaApi != null){
                megaApi.retryPendingConnections();
            }

            if (megaChatApi != null) {
                megaChatApi.retryPendingConnections(false, null);

                if (megaChatApi.getPresenceConfig() != null && !megaChatApi.getPresenceConfig().isPending()) {
                    delaySignalPresence = false;
                    if (!(this instanceof MeetingActivity) && megaChatApi.isSignalActivityRequired()) {
                        logDebug("Send signal presence");
                        megaChatApi.signalPresenceActivity();
                    }
                } else {
                    delaySignalPresence = true;
                }
            }
        }
        catch (Exception e){
            logWarning("Exception", e);
        }
    }

    @Override
    public void onBackPressed() {
        if (psaWebBrowser != null && psaWebBrowser.consumeBack()) return;
        retryConnectionsAndSignalPresence();
        super.onBackPressed();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN ){
            retryConnectionsAndSignalPresence();
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Method to display a simple Snackbar.
     *
     * @param view Layout where the snackbar is going to show.
     * @param s Text to shown in the snackbar
     */
    public void showSnackbar (View view, String s) {
        showSnackbar(SNACKBAR_TYPE, view, s, -1);
    }

    /**
     * Method to display a simple or action Snackbar.
     *
     * @param type There are three possible values to this param:
     *            - SNACKBAR_TYPE: creates a simple snackbar
     *            - MESSAGE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Chat section
     *            - NOT_SPACE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Storage-Settings section
     * @param view Layout where the snackbar is going to show.
     * @param s Text to shown in the snackbar
     */
    public void showSnackbar (int type, View view, String s) {
        showSnackbar(type, view, s, MEGACHAT_INVALID_HANDLE);
    }

    /**
     * Method to display a simple or action Snackbar.
     *
     * @param type   There are three possible values to this param:
     *               - SNACKBAR_TYPE: creates a simple snackbar
     *               - MESSAGE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Chat section
     *               - NOT_SPACE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Storage-Settings section
     *               - MUTE_NOTIFICATIONS_SNACKBAR_TYPE: creates an action snackbar which function is unmute chats notifications
     *               - INVITE_CONTACT_TYPE: creates an action snackbar which function is to send a contact invitation
     * @param view   Layout where the snackbar is going to show.
     * @param s      Text to shown in the snackbar
     * @param idChat Chat ID. If this param has a valid value the function of MESSAGE_SNACKBAR_TYPE ends in the specified chat.
     *               If the value is -1 (INVALID_HANLDE) the function ends in chats list view.
     */
    public void showSnackbar(int type, View view, String s, long idChat) {
        showSnackbar(type, view, null, s, idChat, null);
    }

    /**
     * Method to display a simple or action Snackbar.
     *
     * @param type   There are three possible values to this param:
     *               - SNACKBAR_TYPE: creates a simple snackbar
     *               - MESSAGE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Chat section
     *               - NOT_SPACE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Storage-Settings section
     *               - MUTE_NOTIFICATIONS_SNACKBAR_TYPE: creates an action snackbar which function is unmute chats notifications
     *               - INVITE_CONTACT_TYPE: creates an action snackbar which function is to send a contact invitation
     * @param view   Layout where the snackbar is going to show.
     * @param anchor Sets the view the Snackbar should be anchored above, null as default
     * @param s      Text to shown in the snackbar
     * @param idChat Chat ID. If this param has a valid value the function of MESSAGE_SNACKBAR_TYPE ends in the specified chat.
     *               If the value is -1 (INVALID_HANLDE) the function ends in chats list view.
     */
    public void showSnackbarWithAnchorView(int type, View view, View anchor, String s, long idChat) {
        showSnackbar(type, view, anchor, s, idChat, null);
    }

    /**
     * Method to display a simple or action Snackbar.
     *
     * @param type There are three possible values to this param:
     *            - SNACKBAR_TYPE: creates a simple snackbar
     *            - MESSAGE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Chat section
     *            - NOT_SPACE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Storage-Settings section
     *            - MUTE_NOTIFICATIONS_SNACKBAR_TYPE: creates an action snackbar which function is unmute chats notifications
     *            - INVITE_CONTACT_TYPE: creates an action snackbar which function is to send a contact invitation
     * @param view Layout where the snackbar is going to show.
     * @param anchor Sets the view the Snackbar should be anchored above, null as default
     * @param s Text to shown in the snackbar
     * @param idChat Chat ID. If this param has a valid value the function of MESSAGE_SNACKBAR_TYPE ends in the specified chat.
     *               If the value is -1 (INVALID_HANLDE) the function ends in chats list view.
     * @param userEmail Email of the user to be invited.
     */
    public void showSnackbar (int type, View view, View anchor, String s, long idChat, String userEmail) {
        logDebug("Show snackbar: " + s);
        Display  display = getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        try {
            switch (type) {
                case MESSAGE_SNACKBAR_TYPE:
                    snackbar = Snackbar.make(view, !isTextEmpty(s) ? s : getString(R.string.sent_as_message), Snackbar.LENGTH_LONG);
                    break;
                case NOT_SPACE_SNACKBAR_TYPE:
                    snackbar = Snackbar.make(view, R.string.error_not_enough_free_space, Snackbar.LENGTH_LONG);
                    break;
                case MUTE_NOTIFICATIONS_SNACKBAR_TYPE:
                    snackbar = Snackbar.make(view, R.string.notifications_are_already_muted, Snackbar.LENGTH_LONG);
                    break;
                case SNACKBAR_IMCOMPATIBILITY_TYPE:
                    snackbar = Snackbar.make(view, !isTextEmpty(s) ? s : getString(R.string.sent_as_message), Snackbar.LENGTH_INDEFINITE);
                    break;
                case DISMISS_ACTION_SNACKBAR:
                    snackbar = Snackbar.make(view, s, Snackbar.LENGTH_INDEFINITE);
                    break;
                default:
                    snackbar = Snackbar.make(view, s, Snackbar.LENGTH_LONG);
                    break;
            }
        } catch (Exception e) {
            logError("Error showing snackbar", e);
            return;
        }

        Snackbar.SnackbarLayout snackbarLayout = (Snackbar.SnackbarLayout) snackbar.getView();
        snackbarLayout.setBackgroundResource(R.drawable.background_snackbar);

        if (anchor != null) {
            snackbar.setAnchorView(anchor);
        }

        switch (type) {
            case SNACKBAR_TYPE: {
                TextView snackbarTextView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
                snackbarTextView.setMaxLines(5);
                snackbar.show();
                break;
            }
            case MESSAGE_SNACKBAR_TYPE: {
                snackbar.setAction(R.string.action_see, new SnackbarNavigateOption(view.getContext(), idChat));
                snackbar.show();
                break;
            }
            case NOT_SPACE_SNACKBAR_TYPE: {
                snackbar.setAction(R.string.action_settings, new SnackbarNavigateOption(view.getContext()));
                snackbar.show();
                break;
            }
            case MUTE_NOTIFICATIONS_SNACKBAR_TYPE:
                snackbar.setAction(R.string.general_unmute, new SnackbarNavigateOption(view.getContext(), type));
                snackbar.show();
                break;

            case PERMISSIONS_TYPE: {
                TextView snackbarTextView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
                snackbarTextView.setMaxLines(3);
                snackbar.setAction(R.string.action_settings, PermissionUtils.toAppInfo(getApplicationContext()));
                snackbar.show();
                break;
            }

            case INVITE_CONTACT_TYPE:
                snackbar.setAction(R.string.contact_invite, new SnackbarNavigateOption(view.getContext(), type, userEmail));
                snackbar.show();
                break;

            case SNACKBAR_IMCOMPATIBILITY_TYPE: {
                TextView snackbarTextView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
                snackbarTextView.setMaxLines(5);
                snackbar.setAction(R.string.general_ok, v -> {
                    snackbar.dismiss();
                    LiveEventBus.get(EVENT_MEETING_INCOMPATIBILITY_SHOW, Boolean.class).post(false);
                });
                snackbar.show();
                break;
            }

            case DISMISS_ACTION_SNACKBAR:
                snackbar.setAction(R.string.general_ok, new SnackbarNavigateOption(view.getContext(), type));
                snackbar.show();
                break;

            case OPEN_FILE_SNACKBAR_TYPE: {
                snackbar.setAction(R.string.action_see, (v) -> openDownloadedFile());
                snackbar.show();
                break;
            }
        }
    }

    /**
     * Get snackbar instance
     *
     * @return snackbar
     */
    public Snackbar getSnackbar() {
        return snackbar;
    }

    /**
     * Method to display a simple Snackbar.
     *
     * @param outMetrics DisplayMetrics of the current device
     * @param view Layout where the snackbar is going to show.
     * @param s Text to shown in the snackbar
     */
    public static void showSimpleSnackbar(DisplayMetrics outMetrics, View view, String s) {
        Snackbar snackbar = Snackbar.make(view, s, Snackbar.LENGTH_LONG);
        Snackbar.SnackbarLayout snackbarLayout = (Snackbar.SnackbarLayout) snackbar.getView();
        snackbarLayout.setBackgroundResource(R.drawable.background_snackbar);
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackbarLayout.getLayoutParams();
        params.setMargins(dp2px(8, outMetrics),0, dp2px(8, outMetrics), dp2px(8, outMetrics));
        snackbarLayout.setLayoutParams(params);
        TextView snackbarTextView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        snackbarTextView.setMaxLines(5);
        snackbar.show();
    }

    /**
     * Method to refresh the account details info if necessary.
     */
    protected void refreshAccountInfo(){
        logDebug("refreshAccountInfo");

        //Check if the call is recently
        logDebug("Check the last call to getAccountDetails");
        if(callToAccountDetails()){
            logDebug("megaApi.getAccountDetails SEND");
            app.askForAccountDetails();
        }
    }

    /**
     * This method is shown in a business account when the account is expired.
     * It informs that all the actions are only read.
     * The message is different depending if the account belongs to an admin or an user.
     *
     */
    protected void showExpiredBusinessAlert(){
        if (isPaused || (expiredBusinessAlert != null && expiredBusinessAlert.isShowing())) {
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Mega_MaterialAlertDialog);
        builder.setTitle(R.string.expired_business_title);

        if (megaApi.isMasterBusinessAccount()) {
            builder.setMessage(R.string.expired_admin_business_text);
        } else {
            String expiredString = getString(R.string.expired_user_business_text);
            try {
                expiredString = expiredString.replace("[B]", "<b><font color=\'"
                        + ColorUtils.getColorHexString(this, R.color.black_white)
                        + "\'>");
                expiredString = expiredString.replace("[/B]", "</font></b>");
            } catch (Exception e) {
                logWarning("Exception formatting string", e);
            }
            builder.setMessage(TextUtils.concat(HtmlCompat.fromHtml(expiredString, HtmlCompat.FROM_HTML_MODE_LEGACY), "\n\n" + getString(R.string.expired_user_business_text_2)));
        }

        builder.setNegativeButton(R.string.general_dismiss, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                isExpiredBusinessAlertShown = false;
                if (finishActivityAtError) {
                    finishActivityAtError = false;
                    finish();
                }
                dialog.dismiss();
            }
        });
        builder.setCancelable(false);
        expiredBusinessAlert = builder.create();
        expiredBusinessAlert.show();
        isExpiredBusinessAlertShown = true;
    }

    /**
     * Method to show an alert or error when the account has been suspended
     * for any reason
     *
     * @param eventNumber long that determines the event for which the account has been suspended
     * @param stringError string shown as an alert in case there is not any specific action for the event
     */
    public void checkWhyAmIBlocked(long eventNumber, String stringError) {
        Intent intent;

        switch (Long.toString(eventNumber)) {
            case ACCOUNT_NOT_BLOCKED:
//                I am not blocked
                break;
            case COPYRIGHT_ACCOUNT_BLOCK:
                megaChatApi.logout(new ChatLogoutListener(this, getString(R.string.account_suspended_breache_ToS)));
                break;
            case MULTIPLE_COPYRIGHT_ACCOUNT_BLOCK:
                megaChatApi.logout(new ChatLogoutListener(this, getString(R.string.account_suspended_multiple_breaches_ToS)));
                break;

            case DISABLED_BUSINESS_ACCOUNT_BLOCK:
                megaChatApi.logout(new ChatLogoutListener(this, getString(R.string.error_business_disabled)));
                break;

            case REMOVED_BUSINESS_ACCOUNT_BLOCK:
                megaChatApi.logout(new ChatLogoutListener(this, getString(R.string.error_business_removed)));
                break;

            case SMS_VERIFICATION_ACCOUNT_BLOCK:
                if (megaApi.smsAllowedState() == 0 || MegaApplication.isVerifySMSShowed()) return;

                MegaApplication.smsVerifyShowed(true);
                String gSession = megaApi.dumpSession();
                //For first login, keep the valid session,
                //after added phone number, the account can use this session to fastLogin
                if (gSession != null) {
                    MegaUser myUser = megaApi.getMyUser();
                    String myUserHandle = null;
                    String lastEmail = null;
                    if (myUser != null) {
                        lastEmail = myUser.getEmail();
                        myUserHandle = myUser.getHandle() + "";
                    }
                    UserCredentials credentials = new UserCredentials(lastEmail, gSession, "", "", myUserHandle);
                    dbH.saveCredentials(credentials);
                }

                logDebug("Show SMS verification activity.");
                intent = new Intent(getApplicationContext(), SMSVerificationActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(NAME_USER_LOCKED, true);
                startActivity(intent);
                break;

            case WEAK_PROTECTION_ACCOUNT_BLOCK:
                if (MegaApplication.isBlockedDueToWeakAccount() || MegaApplication.isWebOpenDueToEmailVerification()) {
                    break;
                }
                intent = new Intent(this, WeakAccountProtectionAlertActivity.class);
                startActivity(intent);
                break;

            default:
                showErrorAlertDialog(stringError, false, this);
        }
    }

    public DisplayMetrics getOutMetrics() {
        return outMetrics;
    }

    protected void setFinishActivityAtError(boolean finishActivityAtError) {
        BaseActivity.finishActivityAtError = finishActivityAtError;
    }

    protected boolean isBusinessExpired() {
        return megaApi.isBusinessAccount() && megaApi.getBusinessStatus() == BUSINESS_STATUS_EXPIRED;
    }

    /**
     * Shows a dialog to confirm enable the SDK logs.
     */
    protected void showConfirmationEnableLogsSDK() {
        showConfirmationEnableLogs(LogsType.SDK_LOGS);
    }

    /**
     * Shows a dialog to confirm enable the Karere logs.
     */
    protected void showConfirmationEnableLogsKarere() {
        showConfirmationEnableLogs(LogsType.KARERE_LOGS);
    }

    /**
     * Shows a dialog to confirm enable the SDK or Karere logs.
     *
     * @param logsType SDK_LOGS to confirm enable the SDK logs,
     *                 KARERE_LOGS to confirm enable the Karere logs.
     */
    protected void showConfirmationEnableLogs(LogsType logsType) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.enable_log_text_dialog)
                .setPositiveButton(R.string.general_enable, (dialog, which) -> {
                    switch (logsType) {
                        case SDK_LOGS:
                            setStatusLoggerSDK(baseActivity, true);
                            break;

                        case KARERE_LOGS:
                            setStatusLoggerKarere(baseActivity, true);
                            break;
                    }
                })
                .setNegativeButton(R.string.general_cancel, null)
                .show()
                .setCanceledOnTouchOutside(false);
    }

    /**
     * Shows a warning indicating transfer over quota occurred.
     */
    public void showGeneralTransferOverQuotaWarning() {
        if (MegaApplication.getTransfersManagement().isOnTransfersSection() || transferGeneralOverQuotaWarning != null) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Mega_MaterialAlertDialog);
        View dialogView = this.getLayoutInflater().inflate(R.layout.transfer_overquota_layout, null);
        builder.setView(dialogView)
                .setOnDismissListener(dialog -> {
                    isGeneralTransferOverQuotaWarningShown = false;
                    transferGeneralOverQuotaWarning = null;
                    MegaApplication.getTransfersManagement().resetTransferOverQuotaTimestamp();
                })
                .setCancelable(false);

        transferGeneralOverQuotaWarning = builder.create();
        transferGeneralOverQuotaWarning.setCanceledOnTouchOutside(false);

        TextView text = dialogView.findViewById(R.id.text_transfer_overquota);
        final int stringResource = MegaApplication.getTransfersManagement().isCurrentTransferOverQuota() ? R.string.current_text_depleted_transfer_overquota : R.string.text_depleted_transfer_overquota;
        text.setText(getString(stringResource, getHumanizedTime(megaApi.getBandwidthOverquotaDelay())));

        Button dismissButton = dialogView.findViewById(R.id.transfer_overquota_button_dissmiss);
        dismissButton.setOnClickListener(v -> transferGeneralOverQuotaWarning.dismiss());

        Button paymentButton = dialogView.findViewById(R.id.transfer_overquota_button_payment);

        final boolean isLoggedIn = megaApi.isLoggedIn() != 0 && dbH.getCredentials() != null;
        if (isLoggedIn) {
            boolean isFreeAccount = myAccountInfo.getAccountType() == MegaAccountDetails.ACCOUNT_TYPE_FREE;
            paymentButton.setText(getString(isFreeAccount ? R.string.my_account_upgrade_pro : R.string.plans_depleted_transfer_overquota));
        } else {
            paymentButton.setText(getString(R.string.login_text));
        }

        paymentButton.setOnClickListener(v -> {
            transferGeneralOverQuotaWarning.dismiss();

            if (isLoggedIn) {
                navigateToUpgradeAccount();
            } else {
                navigateToLogin();
            }
        });

        createAndShowCountDownTimer(stringResource, transferGeneralOverQuotaWarning, text);
        transferGeneralOverQuotaWarning.show();
        isGeneralTransferOverQuotaWarningShown = true;
    }

    /**
     * Launches an intent to navigate to Login screen.
     */
    protected void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivityLollipop.class);
        intent.putExtra(VISIBLE_FRAGMENT, LOGIN_FRAGMENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * Launches an intent to navigate to Upgrade Account screen.
     */
    public void navigateToUpgradeAccount() {
        Intent intent = new Intent(this, ManagerActivityLollipop.class);
        intent.setAction(ACTION_SHOW_UPGRADE_ACCOUNT);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        try {
            return super.registerReceiver(receiver, filter);
        } catch (IllegalStateException e) {
            logError("IllegalStateException registering receiver", e);
            return null;
        }
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        try {
            //If the receiver is not registered, it throws an IllegalArgumentException
            super.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            logWarning("IllegalArgumentException unregistering transfersUpdateReceiver", e);
        }
    }

    public boolean isResumeTransfersWarningShown() {
        return isResumeTransfersWarningShown;
    }

    public void setIsResumeTransfersWarningShown(boolean isResumeTransfersWarningShown) {
        this.isResumeTransfersWarningShown = isResumeTransfersWarningShown;
    }

    public void setResumeTransfersWarning(AlertDialog resumeTransfersWarning) {
        this.resumeTransfersWarning = resumeTransfersWarning;
    }

    public AlertDialog getResumeTransfersWarning() {
        return resumeTransfersWarning;
    }

    /**
     * Checks if should refresh session due to megaApi is null.
     *
     * @return True if should refresh session, false otherwise.
     */
    protected boolean shouldRefreshSessionDueToMegaApiIsNull() {
        if (megaApi == null) {
            logWarning("Refresh session - sdk");
            refreshSession();
            return true;
        }

        return false;
    }

    /**
     * Checks if should refresh session due to megaApi.
     *
     * @return True if should refresh session, false otherwise.
     */
    protected boolean shouldRefreshSessionDueToSDK() {
        if (shouldRefreshSessionDueToMegaApiIsNull()) {
            return true;
        }

        if (megaApi.getRootNode() == null) {
            logWarning("Refresh session - sdk");
            refreshSession();
            return true;
        }

        return false;
    }

    /**
     * Checks if should refresh session due to karere or init megaChatAp if the init state is not
     * the right one.
     *
     * @return True if should refresh session or megaChatApi cannot be recovered, false otherwise.
     */
    protected boolean shouldRefreshSessionDueToKarere() {
        if (megaChatApi == null) {
            logWarning("Refresh session - karere");
            refreshSession();
            return true;
        }

        int state = megaChatApi.getInitState();
        if (state == MegaChatApi.INIT_ERROR || state == MegaChatApi.INIT_NOT_DONE) {
            logWarning("MegaChatApi state: " + state);
            UserCredentials credentials = dbH.getCredentials();
            state = megaChatApi.init(credentials == null ? null : credentials.getSession());
            logDebug("result of init ---> " + state);

            if (state == MegaChatApi.INIT_ERROR) {
                // The megaChatApi cannot be recovered, then logout
                megaChatApi.logout(new ChatLogoutListener(this));
                return true;
            }
        }

        return false;
    }

    protected void refreshSession() {
        navigateToLogin();
        finish();
    }

    @Override
    public void launchActivity(@NotNull Intent intent) {
        startActivity(intent);
    }

    @Override
    @SuppressWarnings("deprecation") // TODO Migrate to registerForActivityResult()
    public void launchActivityForResult(@NotNull Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode);
    }

    @Override
    public void askPermissions(@NotNull String[] permissions, int requestCode) {
        requestPermission(this, requestCode, permissions);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent intent) {
        logDebug("Request code: " + requestCode + ", Result code:" + resultCode);

        if (requestCode == REQ_CODE_BUY) {
            if (resultCode == Activity.RESULT_OK) {
                int purchaseResult = billingManager.getPurchaseResult(intent);

                if (BillingManager.ORDER_STATE_SUCCESS == purchaseResult) {
                    billingManager.updatePurchase();
                } else {
                    logWarning("Purchase failed, error code: " + purchaseResult);
                }
            } else {
                logWarning("cancel subscribe");
            }
        } else {
            logWarning("No request code processed");
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    protected void initPayments() {
        billingManager = new BillingManagerImpl(this, this);
    }

    protected void destroyPayments() {
        if (billingManager != null) {
            billingManager.destroy();
        }
    }

    protected void launchPayment(String productId) {
        MegaSku skuDetails = getSkuDetails(skuDetailsList, productId);
        if (skuDetails == null) {
            logError("Cannot launch payment, MegaSku is null.");
            return;
        }

        MegaPurchase purchase = myAccountInfo.getActiveSubscription();
        String oldSku = purchase == null ? null : purchase.getSku();
        String token = purchase == null ? null : purchase.getToken();

        if (billingManager != null) {
            billingManager.initiatePurchaseFlow(oldSku, token, skuDetails);
        }
    }

    @Override
    public void onBillingClientSetupFinished() {
        logInfo("Billing client setup finished");

        billingManager.getInventory(skuList -> {
            skuDetailsList = skuList;
            myAccountInfo.setAvailableSkus(skuList);
            updatePricing(this);
        });
    }

    @Override
    public void onPurchasesUpdated(boolean isFailed, int resultCode, List<MegaPurchase> purchases) {
        if (isFailed) {
            logWarning("Update purchase failed, with result code: " + resultCode);
            return;
        }

        String title;
        String message;

        if (purchases != null && !purchases.isEmpty()) {
            MegaPurchase purchase = purchases.get(0);
            //payment may take time to process, we will not give privilege until it has been fully processed
            String sku = purchase.getSku();
            title = StringResourcesUtils.getString(R.string.title_user_purchased_subscription);

            if (billingManager.isPurchased(purchase)) {
                //payment has been processed
                logDebug("Purchase " + sku + " successfully, subscription type is: "
                        + getSubscriptionType(sku) + ", subscription renewal type is: "
                        + getSubscriptionRenewalType(sku));

                updateAccountInfo(this, purchases, myAccountInfo);
                message = StringResourcesUtils.getString(R.string.message_user_purchased_subscription);
                updateSubscriptionLevel(myAccountInfo, dbH, megaApi);
                new RatingHandlerImpl(this).updateTransactionFlag(true);
            } else {
                //payment is being processed or in unknown state
                logDebug("Purchase " + sku + " is being processed or in unknown state.");
                message = StringResourcesUtils.getString(R.string.message_user_payment_pending);
            }
        } else {
            //down grade case
            logDebug("Downgrade, the new subscription takes effect when the old one expires.");
            title = StringResourcesUtils.getString(R.string.my_account_upgrade_pro);
            message = StringResourcesUtils.getString(R.string.message_user_purchased_subscription_down_grade);
        }

        LiveEventBus.get(EVENT_PURCHASES_UPDATED, Pair.class).post(new Pair<>(title, message));
    }

    @Override
    public void onQueryPurchasesFinished(boolean isFailed, int resultCode, List<MegaPurchase> purchases) {
        if (isFailed || purchases == null) {
            logWarning("Query of purchases failed, result code is " + resultCode + ", is purchase null: " + (purchases == null));
            return;
        }

        updateAccountInfo(this, purchases, myAccountInfo);
        updateSubscriptionLevel(myAccountInfo, dbH, megaApi);
    }

    @Override
    public void showSnackbar(int type, @Nullable String content, long chatId) {
        View rootView = getRootViewFromContext(this);
        showSnackbar(type, rootView, content, chatId);
    }
}
