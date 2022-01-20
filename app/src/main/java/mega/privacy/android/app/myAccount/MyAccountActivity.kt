package mega.privacy.android.app.myAccount

import android.app.NotificationManager
import android.content.*
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import mega.privacy.android.app.R
import mega.privacy.android.app.activities.PasscodeActivity
import mega.privacy.android.app.constants.BroadcastConstants
import mega.privacy.android.app.constants.IntentConstants.Companion.ACTION_OPEN_ACHIEVEMENTS
import mega.privacy.android.app.constants.IntentConstants.Companion.EXTRA_ACCOUNT_TYPE
import mega.privacy.android.app.constants.IntentConstants.Companion.EXTRA_MASTER_KEY
import mega.privacy.android.app.databinding.ActivityMyAccountBinding
import mega.privacy.android.app.databinding.DialogErrorInputEditTextBinding
import mega.privacy.android.app.databinding.DialogErrorPasswordInputEditTextBinding
import mega.privacy.android.app.interfaces.SnackbarShower
import mega.privacy.android.app.lollipop.ChangePasswordActivityLollipop
import mega.privacy.android.app.upgradeAccount.UpgradeAccountActivity
import mega.privacy.android.app.utils.AlertDialogUtil.isAlertDialogShown
import mega.privacy.android.app.utils.AlertDialogUtil.quitEditTextError
import mega.privacy.android.app.utils.AlertDialogUtil.setEditTextError
import mega.privacy.android.app.utils.ColorUtils
import mega.privacy.android.app.utils.Constants.*
import mega.privacy.android.app.utils.LogUtil.logError
import mega.privacy.android.app.utils.MenuUtils.toggleAllMenuItemsVisibility
import mega.privacy.android.app.utils.StringResourcesUtils
import mega.privacy.android.app.utils.Util.*
import nz.mega.documentscanner.utils.ViewUtils.hideKeyboard
import nz.mega.sdk.MegaError.API_OK
import java.util.*

class MyAccountActivity : PasscodeActivity(), MyAccountFragment.MessageResultCallback,
    SnackbarShower {

    companion object {
        private const val KILL_SESSIONS_SHOWN = "KILL_SESSIONS_SHOWN"
        private const val CANCEL_SUBSCRIPTIONS_SHOWN = "CANCEL_SUBSCRIPTIONS_SHOWN"
        private const val TYPED_FEEDBACK = "TYPED_FEEDBACK"
        private const val CONFIRM_CANCEL_SUBSCRIPTIONS_SHOWN = "CONFIRM_CANCEL_SUBSCRIPTIONS_SHOWN"
        private const val CONFIRM_CANCEL_ACCOUNT_SHOWN = "CONFIRM_CANCEL_ACCOUNT_SHOWN"
        private const val CONFIRM_CHANGE_EMAIL_SHOWN = "CONFIRM_CHANGE_EMAIL_SHOWN"
        private const val CONFIRM_RESET_PASSWORD_SHOWN = "CONFIRM_RESET_PASSWORD_SHOWN"
        private const val TYPE_CHANGE_EMAIL = 1
        private const val TYPE_CANCEL_ACCOUNT = 2
    }

    private val viewModel: MyAccountViewModel by viewModels()

    private lateinit var navController: NavController
    private lateinit var binding: ActivityMyAccountBinding

    private var menu: Menu? = null

    private var killSessionsConfirmationDialog: AlertDialog? = null
    private var cancelSubscriptionsDialog: AlertDialog? = null
    private var cancelSubscriptionsConfirmationDialog: AlertDialog? = null
    private var confirmCancelAccountDialog: AlertDialog? = null
    private var confirmChangeEmailDialog: AlertDialog? = null
    private var confirmResetPasswordDialog: AlertDialog? = null
    private var cancelSubscriptionsFeedback: String? = null

    private val updateMyAccountReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val actionType = intent.getIntExtra(
                BroadcastConstants.ACTION_TYPE,
                BroadcastConstants.INVALID_ACTION
            )

            when (actionType) {
                UPDATE_ACCOUNT_DETAILS -> viewModel.updateAccountDetails()
                UPDATE_CREDIT_CARD_SUBSCRIPTION -> refreshMenuOptionsVisibility()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (shouldRefreshSessionDueToSDK()) {
            return
        }

        binding = ActivityMyAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupView()
        setupObservers()
        manageIntentExtras()

        if (savedInstanceState != null) {
            when {
                savedInstanceState.getBoolean(KILL_SESSIONS_SHOWN, false) -> {
                    showConfirmationKillSessions()
                }
                savedInstanceState.getBoolean(CANCEL_SUBSCRIPTIONS_SHOWN, false) -> {
                    cancelSubscriptionsFeedback = savedInstanceState.getString(TYPED_FEEDBACK)
                    showCancelSubscriptions()
                }
                savedInstanceState.getBoolean(CONFIRM_CANCEL_SUBSCRIPTIONS_SHOWN, false) -> {
                    showConfirmationCancelSubscriptions()
                }
                savedInstanceState.getBoolean(CONFIRM_CANCEL_ACCOUNT_SHOWN, false) -> {
                    showConfirmCancelAccountDialog()
                }
                savedInstanceState.getBoolean(CONFIRM_CHANGE_EMAIL_SHOWN, false) -> {
                    showConfirmChangeEmailDialog()
                }
                savedInstanceState.getBoolean(CONFIRM_RESET_PASSWORD_SHOWN, false) -> {
                    showConfirmResetPasswordDialog()
                }
            }
        }
    }

    private fun manageIntentExtras() {
        val accountType = intent.getIntExtra(EXTRA_ACCOUNT_TYPE, INVALID_VALUE)
        if (accountType != INVALID_VALUE) {
            startActivity(
                Intent(this, UpgradeAccountActivity::class.java)
                    .putExtra(EXTRA_ACCOUNT_TYPE, accountType)
            )

            intent.removeExtra(EXTRA_ACCOUNT_TYPE)
        }

        when (intent.action) {
            ACTION_OPEN_ACHIEVEMENTS -> {
                navController.navigate(R.id.action_my_account_to_achievements)
                intent.action = null
            }
            ACTION_CANCEL_ACCOUNT -> {
                intent.dataString?.let { link ->
                    viewModel.confirmCancelAccount(link) { result ->
                        showConfirmCancelAccountQueryResult(result)
                    }
                }

                intent.action = null
            }
            ACTION_CHANGE_MAIL -> {
                intent.dataString?.let { link ->
                    viewModel.confirmChangeEmail(link) { result ->
                        showConfirmChangeEmailQueryResult(result)
                    }
                }

                intent.action = null
            }
            ACTION_RESET_PASS -> {
                showConfirmResetPasswordDialog()
                intent.action = null
            }
            ACTION_PASS_CHANGED -> {
                viewModel.finishPasswordChange(
                    intent.getIntExtra(RESULT, API_OK),
                    ::showGeneralAlert,
                    ::showErrorAlert
                )

                intent.action = null
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KILL_SESSIONS_SHOWN, isAlertDialogShown(killSessionsConfirmationDialog))

        if (isAlertDialogShown(cancelSubscriptionsDialog)) {
            outState.putBoolean(CANCEL_SUBSCRIPTIONS_SHOWN, true)
            outState.putString(TYPED_FEEDBACK, cancelSubscriptionsFeedback)
        }

        outState.putBoolean(
            CONFIRM_CANCEL_SUBSCRIPTIONS_SHOWN,
            isAlertDialogShown(cancelSubscriptionsConfirmationDialog)
        )

        outState.putBoolean(
            CONFIRM_CANCEL_ACCOUNT_SHOWN,
            isAlertDialogShown(confirmCancelAccountDialog)
        )

        outState.putBoolean(
            CONFIRM_CHANGE_EMAIL_SHOWN,
            isAlertDialogShown(confirmChangeEmailDialog)
        )

        outState.putBoolean(
            CONFIRM_RESET_PASSWORD_SHOWN,
            isAlertDialogShown(confirmResetPasswordDialog)
        )

        super.onSaveInstanceState(outState)
    }

    @Suppress("deprecation") // TODO Migrate to registerForActivityResult()
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        viewModel.manageActivityResult(this, requestCode, resultCode, data, this)
    }

    override fun onBackPressed() {
        if (!navController.navigateUp()) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        app.refreshAccountInfo()
    }

    override fun onPostResume() {
        super.onPostResume()
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(
                NOTIFICATION_STORAGE_OVERQUOTA
            )
        } catch (e: Exception) {
            logError("Exception NotificationManager - remove all notifications", e)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(updateMyAccountReceiver)

        killSessionsConfirmationDialog?.dismiss()
        cancelSubscriptionsDialog?.dismiss()
        cancelSubscriptionsConfirmationDialog?.dismiss()
        confirmCancelAccountDialog?.dismiss()
        confirmChangeEmailDialog?.dismiss()
        confirmResetPasswordDialog?.dismiss()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
            R.id.action_kill_all_sessions -> showConfirmationKillSessions()
            R.id.action_change_pass -> navController.navigate(R.id.action_my_account_to_change_password)
            R.id.action_export_MK -> navController.navigate(R.id.action_my_account_to_export_recovery_key)
            R.id.action_refresh -> viewModel.refresh(this)
            R.id.action_upgrade_account -> navController.navigate(R.id.action_my_account_to_upgrade)
            R.id.action_cancel_subscriptions -> showCancelSubscriptions()
            R.id.action_logout -> viewModel.logout(this)
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_my_account, menu)
        this.menu = menu

        refreshMenuOptionsVisibility()

        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Sets the right Toolbar options depending on current situation.
     */
    private fun refreshMenuOptionsVisibility() {
        val menu = this.menu ?: return

        if (!isOnline(this)) {
            menu.toggleAllMenuItemsVisibility(false)
            return
        }

        when (navController.currentDestination?.id) {
            R.id.my_account -> {
                menu.toggleAllMenuItemsVisibility(true)

                if (viewModel.thereIsNoSubscription()) {
                    menu.findItem(R.id.action_cancel_subscriptions).isVisible = false
                }

                if (viewModel.isBusinessAccount()) {
                    menu.findItem(R.id.action_upgrade_account).isVisible = false
                }

                updateActionBar(ContextCompat.getColor(this, R.color.grey_020_grey_087))
            }
            else -> {
                menu.toggleAllMenuItemsVisibility(false)
                updateActionBar(ContextCompat.getColor(this, R.color.white))
            }
        }
    }

    private fun setupView() {
        updateInfo()
        setSupportActionBar(binding.toolbar)

        supportActionBar?.apply {
            title = null
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
                .navController

        navController.addOnDestinationChangedListener { _, _, _ ->
            refreshMenuOptionsVisibility()

            supportActionBar?.setHomeAsUpIndicator(
                ColorUtils.tintIcon(
                    this,
                    when (navController.currentDestination?.id) {
                        R.id.my_account -> R.drawable.ic_arrow_back_white
                        else -> R.drawable.ic_close_white
                    }
                )
            )
        }
    }

    /**
     * Updates the action bar by changing the Toolbar and status bar color.
     *
     * @param background Color to set as background.
     */
    private fun updateActionBar(background: Int) {
        if (!isDarkMode(this)) {
            window?.statusBarColor = background
            binding.toolbar.setBackgroundColor(background)
        }
    }

    /**
     * Changes the ActionBar elevation depending on the withElevation value received.
     *
     * @param withElevation True if should set elevation, false otherwise.
     */
    private fun changeElevation(withElevation: Boolean) {
        val isDark = isDarkMode(this)
        val darkAndElevation = withElevation && isDark
        val background = ContextCompat.getColor(this, R.color.grey_020_grey_087)

        if (darkAndElevation) {
            ColorUtils.changeStatusBarColorForElevation(this, true)
        } else {
            window?.statusBarColor = background
        }

        val elevation = resources.getDimension(R.dimen.toolbar_elevation)

        binding.toolbar.setBackgroundColor(
            if (darkAndElevation) ColorUtils.getColorForElevation(this, elevation)
            else background
        )

        supportActionBar?.elevation =
            if (withElevation && !isDark) elevation else 0F
    }

    /**
     * Checks and refreshes account info.
     */
    private fun updateInfo() {
        viewModel.checkVersions()
        app.refreshAccountInfo()
    }

    private fun setupObservers() {
        registerReceiver(
            updateMyAccountReceiver, IntentFilter(
                BROADCAST_ACTION_INTENT_UPDATE_ACCOUNT_DETAILS
            )
        )

        viewModel.checkElevation().observe(this, ::changeElevation)
    }

    /**
     * Shows the result of the kill sessions action.
     *
     * @param success True if the request finishes with success, false otherwise.
     */
    private fun showKillSessionsResult(success: Boolean) {
        showSnackbar(
            StringResourcesUtils.getString(
                if (success) R.string.success_kill_all_sessions
                else R.string.error_kill_all_sessions
            )
        )
    }

    /**
     * Shows the result of the cancel subscriptions action.
     *
     * @param success True if the request finishes with success, false otherwise.
     */
    private fun showCancelSubscriptionsResult(success: Boolean) {
        showSnackbar(
            StringResourcesUtils.getString(
                if (success) R.string.cancel_subscription_ok
                else R.string.cancel_subscription_error
            )
        )

        app.askForCCSubscriptions()
    }

    /**
     * Shows a confirmation dialog before kill sessions.
     */
    private fun showConfirmationKillSessions() {
        if (isAlertDialogShown(killSessionsConfirmationDialog)) {
            return
        }

        killSessionsConfirmationDialog = MaterialAlertDialogBuilder(this)
            .setTitle(StringResourcesUtils.getString(R.string.confirmation_close_sessions_title))
            .setMessage(StringResourcesUtils.getString(R.string.confirmation_close_sessions_text))
            .setPositiveButton(StringResourcesUtils.getString(R.string.contact_accept)) { _, _ ->
                viewModel.killSessions { success -> showKillSessionsResult(success) }
            }.setNegativeButton(StringResourcesUtils.getString(R.string.general_cancel), null)
            .show()
    }

    /**
     * Shows the dialog to fill before cancel subscriptions.
     */
    private fun showCancelSubscriptions() {
        if (isAlertDialogShown(cancelSubscriptionsDialog)) {
            return
        }

        val builder = MaterialAlertDialogBuilder(this)

        cancelSubscriptionsDialog =
            builder.setView(R.layout.dialog_cancel_subscriptions)
                .setPositiveButton(
                    StringResourcesUtils.getString(R.string.send_cancel_subscriptions),
                    null
                )
                .setNegativeButton(StringResourcesUtils.getString(R.string.general_dismiss), null)
                .create()

        cancelSubscriptionsDialog?.apply {
            setOnShowListener {
                val feedbackEditText = findViewById<EditText>(R.id.dialog_cancel_feedback)
                feedbackEditText?.apply {
                    setText(cancelSubscriptionsFeedback)

                    doAfterTextChanged {
                        cancelSubscriptionsFeedback = text.toString()
                    }
                }

                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    if (cancelSubscriptionsFeedback?.isEmpty() == true) {
                        showSnackbar(StringResourcesUtils.getString(R.string.reason_cancel_subscriptions))
                    } else {
                        showConfirmationCancelSubscriptions()
                    }
                }
            }

            show()
        }
    }

    /**
     * Shows a confirmation dialog before cancel subscriptions.
     */
    private fun showConfirmationCancelSubscriptions() {
        if (isAlertDialogShown(cancelSubscriptionsConfirmationDialog)) {
            return
        }

        cancelSubscriptionsConfirmationDialog = MaterialAlertDialogBuilder(this)
            .setMessage(StringResourcesUtils.getString(R.string.confirmation_cancel_subscriptions))
            .setPositiveButton(StringResourcesUtils.getString(R.string.general_yes)) { _, _ ->
                viewModel.cancelSubscriptions(cancelSubscriptionsFeedback) { success ->
                    showCancelSubscriptionsResult(success)
                }
            }.setNegativeButton(StringResourcesUtils.getString(R.string.general_no), null)
            .show()
    }

    private fun showConfirmCancelAccountQueryResult(result: String) {
        if (matchRegexs(result, CANCEL_ACCOUNT_LINK_REGEXS)) {
            showConfirmCancelAccountDialog()
        } else {
            showErrorAlert(StringResourcesUtils.getString(R.string.general_error_word))
        }
    }

    private fun showConfirmCancelAccountDialog() {
        val errorInputBinding = DialogErrorPasswordInputEditTextBinding.inflate(layoutInflater)
        confirmCancelAccountDialog = MaterialAlertDialogBuilder(this)
            .setTitle(StringResourcesUtils.getString(R.string.delete_account))
            .setMessage(StringResourcesUtils.getString(R.string.delete_account_text_last_step))
            .setView(errorInputBinding.root)
            .setNegativeButton(StringResourcesUtils.getString(R.string.general_dismiss), null)
            .setPositiveButton(StringResourcesUtils.getString(R.string.delete_account), null)
            .create()

        showConfirmDialog(
            confirmCancelAccountDialog,
            TYPE_CANCEL_ACCOUNT,
            errorInputBinding.editLayout,
            errorInputBinding.textField,
            errorInputBinding.errorIcon
        )
    }

    private fun showConfirmChangeEmailQueryResult(result: String) {
        if (matchRegexs(result, VERIFY_CHANGE_MAIL_LINK_REGEXS)) {
            showConfirmChangeEmailDialog()
        } else {
            showErrorAlert(StringResourcesUtils.getString(R.string.general_error_word))
        }
    }

    private fun showConfirmChangeEmailDialog() {
        val errorInputBinding = DialogErrorInputEditTextBinding.inflate(layoutInflater)
        confirmChangeEmailDialog = MaterialAlertDialogBuilder(this)
            .setTitle(StringResourcesUtils.getString(R.string.change_mail_title_last_step))
            .setMessage(StringResourcesUtils.getString(R.string.change_mail_text_last_step))
            .setView(errorInputBinding.root)
            .setNegativeButton(StringResourcesUtils.getString(R.string.general_cancel), null)
            .setPositiveButton(StringResourcesUtils.getString(R.string.change_pass), null)
            .create()

        showConfirmDialog(
            confirmChangeEmailDialog,
            TYPE_CHANGE_EMAIL,
            errorInputBinding.editLayout,
            errorInputBinding.textField,
            errorInputBinding.errorIcon
        )
    }

    private fun showConfirmDialog(
        dialog: AlertDialog?,
        dialogType: Int,
        editLayout: TextInputLayout,
        textField: EditText,
        errorIcon: ImageView
    ) {
        dialog?.apply {
            setOnShowListener {
                quitEditTextError(editLayout, errorIcon)

                editLayout.hint =
                    StringResourcesUtils.getString(R.string.edit_text_insert_pass)
                        .capitalize(Locale.ROOT)

                textField.apply {
                    setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                        }

                        true
                    }

                    doAfterTextChanged {
                        quitEditTextError(editLayout, errorIcon)
                    }

                    requestFocus()
                    showKeyboardDelayed(this)
                }

                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val password = textField.text.toString()

                    if (password.isEmpty()) {
                        setEditTextError(
                            StringResourcesUtils.getString(R.string.invalid_string),
                            editLayout,
                            errorIcon
                        )
                    } else {
                        when (dialogType) {
                            TYPE_CANCEL_ACCOUNT -> {
                                viewModel.finishConfirmCancelAccount(password) { message ->
                                    showErrorAlert(message)
                                }
                            }
                            TYPE_CHANGE_EMAIL -> {
                                viewModel.finishConfirmChangeEmail(
                                    password,
                                    ::showEmailChangeSuccess,
                                    ::showErrorAlert
                                )
                            }
                        }

                        textField.hideKeyboard()
                        dismiss()
                    }
                }
            }

            show()
        }
    }

    private fun showEmailChangeSuccess(newEmail: String) {
        showSnackbar(StringResourcesUtils.getString(R.string.email_changed, newEmail))
    }

    private fun showConfirmResetPasswordDialog() {
        confirmResetPasswordDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_dialog_insert_MK)
            .setMessage(R.string.text_reset_pass_logged_in)
            .setPositiveButton(
                R.string.pin_lock_enter
            ) { _, _ ->
                startActivity(
                    Intent(this, ChangePasswordActivityLollipop::class.java)
                        .setAction(ACTION_RESET_PASS_FROM_LINK)
                        .setData(intent.data)
                        .putExtra(EXTRA_MASTER_KEY, viewModel.getMasterKey())
                )
            }
            .setNegativeButton(R.string.general_cancel, null)
            .show()
    }

    private fun showGeneralAlert(message: String) {
        showAlert(this, message, null)
    }

    private fun showErrorAlert(message: String) {
        showAlert(
            this,
            message,
            StringResourcesUtils.getString(R.string.general_error_word)
        )
    }

    fun showSnackbar(text: String) {
        showSnackbar(binding.root, text)
    }

    override fun show(message: String) {
        showSnackbar(message)
    }

    override fun showSnackbar(type: Int, content: String?, chatId: Long) {
        content?.let { showSnackbar(it) }
    }
}