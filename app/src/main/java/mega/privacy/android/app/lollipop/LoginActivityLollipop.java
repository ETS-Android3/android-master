package mega.privacy.android.app.lollipop;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import mega.privacy.android.app.BaseActivity;
import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.EphemeralCredentials;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.R;
import mega.privacy.android.app.interfaces.OnKeyboardVisibilityListener;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaApiJava;
import nz.mega.sdk.MegaError;
import nz.mega.sdk.MegaRequest;
import nz.mega.sdk.MegaRequestListenerInterface;
import nz.mega.sdk.MegaTransfer;

import static mega.privacy.android.app.constants.BroadcastConstants.*;
import static mega.privacy.android.app.constants.IntentConstants.EXTRA_FIRST_LOGIN;
import static mega.privacy.android.app.fragments.settingsFragments.startSceen.util.StartScreenUtil.setStartScreenTimeStamp;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.Util.*;
import static mega.privacy.android.app.utils.JobUtil.*;

public class LoginActivityLollipop extends BaseActivity implements MegaRequestListenerInterface {

    float scaleH, scaleW;
    float density;
    DisplayMetrics outMetrics;
    Display display;

    RelativeLayout relativeContainer;

    boolean cancelledConfirmationProcess = false;

    //Fragments
    TourFragmentLollipop tourFragment;
    LoginFragmentLollipop loginFragment;
    CreateAccountFragmentLollipop createAccountFragment;

    ActionBar aB;
    int visibleFragment;

    static LoginActivityLollipop loginActivity;

    Intent intentReceived = null;

    public String accountBlocked = null;

    DatabaseHandler dbH;

    Handler handler = new Handler();
    private MegaApiAndroid megaApi;
    private MegaApiAndroid megaApiFolder;

    boolean waitingForConfirmAccount = false;
    String emailTemp = null;
    String passwdTemp = null;
    String sessionTemp = null;
    String firstNameTemp = null;
    String lastNameTemp = null;

    static boolean isBackFromLoginPage;
    static boolean isFetchingNodes;

    private static boolean clipboardRead;

    private BroadcastReceiver updateMyAccountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                int actionType = intent.getIntExtra(ACTION_TYPE, INVALID_ACTION);

                if (actionType == UPDATE_PAYMENT_METHODS) {
                    logDebug("BROADCAST TO UPDATE AFTER UPDATE_PAYMENT_METHODS");
                }
            }
        }
    };

    private BroadcastReceiver onAccountUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;

            if (intent.getAction().equals(ACTION_ON_ACCOUNT_UPDATE) && waitingForConfirmAccount) {
                waitingForConfirmAccount = false;
                visibleFragment = LOGIN_FRAGMENT;
                showFragment(visibleFragment);
            }
        }
    };

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        intentReceived = intent;
        setIntent(intentReceived);

        visibleFragment = intentReceived.getIntExtra(VISIBLE_FRAGMENT, LOGIN_FRAGMENT);
        if (visibleFragment == LOGIN_FRAGMENT) {
            loginFragment = new LoginFragmentLollipop();
        }

        showFragment(visibleFragment);
    }

    @Override
    protected void onDestroy() {
        logDebug("onDestroy");

        app.setIsLoggingRunning(false);

        unregisterReceiver(updateMyAccountReceiver);
        unregisterReceiver(onAccountUpdateReceiver);

        super.onDestroy();
    }

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        logDebug("onCreate");
        super.onCreate(savedInstanceState);

        loginActivity = this;

        app.setIsLoggingRunning(true);

        display = getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);
        density = getResources().getDisplayMetrics().density;

        aB = getSupportActionBar();
        hideAB();

        scaleW = getScaleW(outMetrics, density);
        scaleH = getScaleH(outMetrics, density);

        dbH = DatabaseHandler.getDbHandler(getApplicationContext());
        if (megaApi == null) {
            megaApi = ((MegaApplication) getApplication()).getMegaApi();
        }

        if (megaApiFolder == null) {
            megaApiFolder = ((MegaApplication) getApplication()).getMegaApiFolder();
        }

        setContentView(R.layout.activity_login);
        relativeContainer = (RelativeLayout) findViewById(R.id.relative_container_login);

        intentReceived = getIntent();
        if (savedInstanceState != null) {
            logDebug("Bundle is NOT NULL");
            visibleFragment = savedInstanceState.getInt(VISIBLE_FRAGMENT, LOGIN_FRAGMENT);
        } else {
            if (intentReceived != null) {
                visibleFragment = intentReceived.getIntExtra(VISIBLE_FRAGMENT, LOGIN_FRAGMENT);
                logDebug("There is an intent! VisibleFragment: " + visibleFragment);
            } else {
                visibleFragment = LOGIN_FRAGMENT;
            }
        }

        if (dbH.getEphemeral() != null) {
            visibleFragment = CONFIRM_EMAIL_FRAGMENT;

            EphemeralCredentials ephemeralCredentials = dbH.getEphemeral();

            emailTemp = ephemeralCredentials.getEmail();
            passwdTemp = ephemeralCredentials.getPassword();
            sessionTemp = ephemeralCredentials.getSession();
            firstNameTemp = ephemeralCredentials.getFirstName();
            lastNameTemp = ephemeralCredentials.getLastName();

            megaApi.resumeCreateAccount(sessionTemp, this);
        }

        registerReceiver(updateMyAccountReceiver, new IntentFilter(BROADCAST_ACTION_INTENT_UPDATE_ACCOUNT_DETAILS));

        IntentFilter filter = new IntentFilter(BROADCAST_ACTION_INTENT_ON_ACCOUNT_UPDATE);
        filter.addAction(ACTION_ON_ACCOUNT_UPDATE);
        registerReceiver(onAccountUpdateReceiver, filter);

        isBackFromLoginPage = false;
        showFragment(visibleFragment);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home: {
                switch (visibleFragment) {
                    case LOGIN_FRAGMENT: {
                        if (loginFragment != null && loginFragment.isAdded()) {
//                            loginFragment.returnToLogin();
                            onBackPressed();
                        }
                        break;
                    }
                }
                break;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    public void showSnackbar(String message) {
        showSnackbar(relativeContainer, message);
    }

    public void showFragment(int visibleFragment) {
        logDebug("visibleFragment: " + visibleFragment);
        this.visibleFragment = visibleFragment;
        switch (visibleFragment) {
            case LOGIN_FRAGMENT: {
                logDebug("Show LOGIN_FRAGMENT");
                if (loginFragment == null) {
                    loginFragment = new LoginFragmentLollipop();
                }
                if ((passwdTemp != null) && (emailTemp != null)) {
                    loginFragment.setEmailTemp(emailTemp);
                    loginFragment.setPasswdTemp(passwdTemp);
                }

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_container_login, loginFragment);
                ft.commitNowAllowingStateLoss();

                setDrawUnderStatusBar(this, false);
                break;
            }
            case CREATE_ACCOUNT_FRAGMENT: {
                logDebug("Show CREATE_ACCOUNT_FRAGMENT");
                if (createAccountFragment == null || cancelledConfirmationProcess) {
                    createAccountFragment = new CreateAccountFragmentLollipop();
                    cancelledConfirmationProcess = false;
                }

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_container_login, createAccountFragment);
                ft.commitNowAllowingStateLoss();

                setDrawUnderStatusBar(this, false);
                break;

            }
            case TOUR_FRAGMENT: {
                logDebug("Show TOUR_FRAGMENT");

                if (ACTION_RESET_PASS.equals(intentReceived.getAction())) {
                    tourFragment = TourFragmentLollipop.newInstance(intentReceived.getDataString(), null);
                } else if (ACTION_PARK_ACCOUNT.equals(intentReceived.getAction())) {
                    tourFragment = TourFragmentLollipop.newInstance(null, intentReceived.getDataString());
                } else {
                    tourFragment = TourFragmentLollipop.newInstance(null, null);
                }

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_container_login, tourFragment).commitNowAllowingStateLoss();

                setDrawUnderStatusBar(this, true);
                break;
            }
            case CONFIRM_EMAIL_FRAGMENT: {
                ConfirmEmailFragmentLollipop confirmEmailFragment = new ConfirmEmailFragmentLollipop();

                if (passwdTemp != null && emailTemp != null) {
                    confirmEmailFragment.setEmailTemp(emailTemp);
                    confirmEmailFragment.setPasswdTemp(passwdTemp);
                    confirmEmailFragment.setFirstNameTemp(firstNameTemp);
                }

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_container_login, confirmEmailFragment).commitNowAllowingStateLoss();

                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.executePendingTransactions();

                setDrawUnderStatusBar(this, false);
                break;
            }
        }

        if (((MegaApplication) getApplication()).isEsid()) {
            showAlertLoggedOut();
        }
    }

    @Override
    protected boolean shouldSetStatusBarTextColor() {
        return false;
    }

    public Intent getIntentReceived() {
        return intentReceived;
    }

    public void showAlertIncorrectRK() {
        logDebug("showAlertIncorrectRK");
        final androidx.appcompat.app.AlertDialog.Builder dialogBuilder = new androidx.appcompat.app.AlertDialog.Builder(this);

        dialogBuilder.setTitle(getString(R.string.incorrect_MK_title));
        dialogBuilder.setMessage(getString(R.string.incorrect_MK));
        dialogBuilder.setCancelable(false);

        dialogBuilder.setPositiveButton(getString(R.string.general_ok), new android.content.DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        androidx.appcompat.app.AlertDialog alert = dialogBuilder.create();
        alert.show();
    }

    public void showAlertLoggedOut() {
        logDebug("showAlertLoggedOut");
        ((MegaApplication) getApplication()).setEsid(false);
        if (!isFinishing()) {
            final androidx.appcompat.app.AlertDialog.Builder dialogBuilder = new androidx.appcompat.app.AlertDialog.Builder(this);

            dialogBuilder.setTitle(getString(R.string.title_alert_logged_out));
            dialogBuilder.setMessage(getString(R.string.error_server_expired_session));

            dialogBuilder.setPositiveButton(getString(R.string.general_ok), new android.content.DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            androidx.appcompat.app.AlertDialog alert = dialogBuilder.create();
            alert.show();
        }
    }

    public void startCameraUploadService(boolean firstTimeCam, int time) {
        logDebug("firstTimeCam: " + firstNameTemp + "time: " + time);
        if (firstTimeCam) {
            setStartScreenTimeStamp(this);
            Intent intent = new Intent(this, ManagerActivityLollipop.class);
            intent.putExtra(EXTRA_FIRST_LOGIN, true);
            startActivity(intent);
            finish();
        } else {
            logDebug("Start the Camera Uploads service");
            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    logDebug("Now I start the service");
                    scheduleCameraUploadJob(LoginActivityLollipop.this);
                }
            }, time);
        }
    }

    public void showConfirmationCancelAllTransfers() {
        logDebug("showConfirmationCancelAllTransfers");

        setIntent(null);
        //Show confirmation message
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        logDebug("Pressed button positive to cancel transfer");
                        if (megaApi != null) {
                            megaApi.cancelTransfers(MegaTransfer.TYPE_DOWNLOAD);
                            if (megaApiFolder != null) {
                                megaApiFolder.cancelTransfers(MegaTransfer.TYPE_DOWNLOAD);
                            }
                        } else {
                            logWarning("megaAPI is null");
                            if (megaApiFolder != null) {
                                megaApiFolder.cancelTransfers(MegaTransfer.TYPE_DOWNLOAD);
                            }
                        }

                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Mega_MaterialAlertDialog);

        builder.setMessage(getResources().getString(R.string.cancel_all_transfer_confirmation));
        builder.setPositiveButton(R.string.cancel_all_action, dialogClickListener);
        builder.setNegativeButton(R.string.general_dismiss, dialogClickListener);

        builder.show();
    }

    @Override
    public void onBackPressed() {
        logDebug("onBackPressed");
        retryConnectionsAndSignalPresence();

        int valueReturn = -1;

        switch (visibleFragment) {
            case LOGIN_FRAGMENT: {
                if (loginFragment != null) {
                    valueReturn = loginFragment.onBackPressed();
                }
                break;
            }
            case CREATE_ACCOUNT_FRAGMENT: {
                showFragment(TOUR_FRAGMENT);
                break;
            }
            case TOUR_FRAGMENT:
            case CONFIRM_EMAIL_FRAGMENT: {
                valueReturn = 0;
                break;
            }
        }

        if (valueReturn == 0) {
            super.onBackPressed();
        }
    }

    @Override
    public void onResume() {
        logDebug("onResume");
        super.onResume();
        setAppFontSize(this);
        Intent intent = getIntent();

        if (intent != null) {
            if (intent.getAction() != null) {
                if (intent.getAction().equals(ACTION_CANCEL_CAM_SYNC)) {
                    logDebug("ACTION_CANCEL_CAM_SYNC");
                    String title = getString(R.string.cam_sync_syncing);
                    String text = getString(R.string.cam_sync_cancel_sync);
                    MaterialAlertDialogBuilder builder = getCustomAlertBuilder(this, title, text, null);
                    builder.setPositiveButton(getString(R.string.general_yes),
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    stopRunningCameraUploadService(LoginActivityLollipop.this);
                                    dbH.setCamSyncEnabled(false);
                                }
                            });
                    builder.setNegativeButton(getString(R.string.general_no), null);
                    final AlertDialog dialog = builder.create();
                    try {
                        dialog.show();
                    } catch (Exception ex) {
                        logError("Exception", ex);
                    }
                } else if (intent.getAction().equals(ACTION_CANCEL_DOWNLOAD)) {
                    showConfirmationCancelAllTransfers();
                } else if (intent.getAction().equals(ACTION_OVERQUOTA_TRANSFER)) {
                    showGeneralTransferOverQuotaWarning();
                }
                intent.setAction(null);
            }
        }

        setIntent(null);
    }

    @Override
    public void showConfirmationEnableLogsKarere() {
        if (loginFragment != null) {
            loginFragment.numberOfClicksKarere = 0;
        }

        loginActivity = this;
        super.showConfirmationEnableLogsKarere();
    }

    @Override
    public void showConfirmationEnableLogsSDK() {
        if (loginFragment != null) {
            loginFragment.numberOfClicksSDK = 0;
        }

        loginActivity = this;
        super.showConfirmationEnableLogsSDK();
    }

    public void setWaitingForConfirmAccount(boolean waitingForConfirmAccount) {
        this.waitingForConfirmAccount = waitingForConfirmAccount;
    }

    public boolean getWaitingForConfirmAccount() {
        return this.waitingForConfirmAccount;
    }

    public void setFirstNameTemp(String firstNameTemp) {
        this.firstNameTemp = firstNameTemp;
    }

    public void setLastNameTemp(String lastNameTemp) {
        this.lastNameTemp = lastNameTemp;
    }

    public String getFirstNameTemp() {
        return this.firstNameTemp;
    }

    public void setPasswdTemp(String passwdTemp) {
        this.passwdTemp = passwdTemp;
    }

    public String getPasswdTemp() {
        return this.passwdTemp;
    }

    public void setEmailTemp(String emailTemp) {
        this.emailTemp = emailTemp;
        if (dbH != null) {
            if (dbH.getEphemeral() != null) {
                EphemeralCredentials ephemeralCredentials = dbH.getEphemeral();
                ephemeralCredentials.setEmail(emailTemp);
                dbH.clearEphemeral();
                dbH.saveEphemeral(ephemeralCredentials);
            }
        }
    }

    public String getEmailTemp() {
        return this.emailTemp;
    }

    @Override
    public void onRequestStart(MegaApiJava api, MegaRequest request) {
        logDebug("onRequestStart - " + request.getRequestString());
    }

    @Override
    public void onRequestUpdate(MegaApiJava api, MegaRequest request) {
        logDebug("onRequestUpdate - " + request.getRequestString());
    }

    @Override
    public void onRequestFinish(MegaApiJava api, MegaRequest request, MegaError e) {
        logDebug("onRequestFinish - " + request.getRequestString() + "_" + e.getErrorCode());

        if (request.getType() == MegaRequest.TYPE_LOGOUT) {

            if (accountBlocked != null) {
                showSnackbar(accountBlocked);
            }
            accountBlocked = null;

        } else if (request.getType() == MegaRequest.TYPE_CREATE_ACCOUNT) {
            try {
                if (request.getParamType() == 1) {
                    if (e.getErrorCode() == MegaError.API_OK) {
                        waitingForConfirmAccount = true;
                        visibleFragment = CONFIRM_EMAIL_FRAGMENT;
                        showFragment(visibleFragment);

                    } else {
                        cancelConfirmationAccount();
                    }
                } // In case getParamType == 3 (creating ephemeral account ++) we don't need to trigger a fetch nodes (sdk performs internal)

                if (request.getParamType() == 4) {
                    if (e.getErrorCode() == MegaError.API_OK && megaApi != null) {
                        //Resuming ephemeral account ++, we need to trigger a fetch nodes
                        megaApi.fetchNodes();
                    }
                }
            } catch (Exception exc) {
                logError("Exception", exc);
            }
        }
    }

    public void cancelConfirmationAccount() {
        logDebug("cancelConfirmationAccount");
        dbH.clearEphemeral();
        dbH.clearCredentials();
        cancelledConfirmationProcess = true;
        waitingForConfirmAccount = false;
        passwdTemp = null;
        emailTemp = null;
        visibleFragment = TOUR_FRAGMENT;
        showFragment(visibleFragment);
    }

    @Override
    public void onRequestTemporaryError(MegaApiJava api, MegaRequest request, MegaError e) {
        logWarning("onRequestTemporaryError - " + request.getRequestString());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        logDebug("onSaveInstanceState");

        super.onSaveInstanceState(outState);

        outState.putInt(VISIBLE_FRAGMENT, visibleFragment);
    }

    @Override
    protected void onPause() {
        logDebug("onPause");
        super.onPause();
    }

    public void showAB(Toolbar tB) {
        setSupportActionBar(tB);
        aB = getSupportActionBar();
        aB.show();
        aB.setHomeButtonEnabled(true);
        aB.setDisplayHomeAsUpEnabled(true);

        if (visibleFragment == LOGIN_FRAGMENT) {
            setDrawUnderStatusBar(this, false);
        }
    }

    public void setKeyboardVisibilityListener(final OnKeyboardVisibilityListener onKeyboardVisibilityListener) {
        final View parentView = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        if (parentView == null) {
            logWarning("Cannot set the keyboard visibility listener. Parent view is NULL.");
            return;
        }

        parentView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            private boolean alreadyOpen;
            private final int defaultKeyboardHeightDP = 100;
            private final int EstimatedKeyboardDP = defaultKeyboardHeightDP + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? 48 : 0);
            private final Rect rect = new Rect();

            @Override
            public void onGlobalLayout() {
                int estimatedKeyboardHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, EstimatedKeyboardDP, parentView.getResources().getDisplayMetrics());
                parentView.getWindowVisibleDisplayFrame(rect);
                int heightDiff = parentView.getRootView().getHeight() - (rect.bottom - rect.top);
                boolean isShown = heightDiff >= estimatedKeyboardHeight;

                if (isShown == alreadyOpen) {
                    return;
                }
                alreadyOpen = isShown;
                onKeyboardVisibilityListener.onVisibilityChanged(isShown);
            }
        });
    }

    public void hideAB() {
        if (aB != null) {
            aB.hide();
        }
    }
}
