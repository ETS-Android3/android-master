package mega.privacy.android.app.fragments.settingsFragments.cookie

import android.content.Context
import android.content.Intent
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import mega.privacy.android.app.MegaApplication
import mega.privacy.android.app.R
import mega.privacy.android.app.activities.settingsActivities.CookiePreferencesActivity
import mega.privacy.android.app.fragments.settingsFragments.cookie.usecase.CheckCookieBannerEnabledUseCase
import mega.privacy.android.app.fragments.settingsFragments.cookie.usecase.GetCookieSettingsUseCase
import mega.privacy.android.app.fragments.settingsFragments.cookie.usecase.UpdateCookieSettingsUseCase
import mega.privacy.android.app.utils.ContextUtils.isValid
import mega.privacy.android.app.utils.LogUtil
import mega.privacy.android.app.utils.StringResourcesUtils
import mega.privacy.android.app.utils.StringUtils.toSpannedHtmlText
import javax.inject.Inject

/**
 * Cookie dialog handler class to manage Cookie Dialog visibility based on view's lifecycle.
 */
class CookieDialogHandler @Inject constructor(
    private val getCookieSettingsUseCase: GetCookieSettingsUseCase,
    private val updateCookieSettingsUseCase: UpdateCookieSettingsUseCase,
    private val checkCookieBannerEnabledUseCase: CheckCookieBannerEnabledUseCase
) : LifecycleObserver {

    private val disposable = CompositeDisposable()
    private var dialog: AlertDialog? = null

    /**
     * Show cookie dialog if needed.
     *
     * @param context   View context for the Dialog to be shown.
     * @param recreate  Dismiss current dialog and create a new instance.
     */
    @JvmOverloads
    fun showDialogIfNeeded(context: Context, recreate: Boolean = false) {
        if (recreate) dialog?.dismiss()

        checkDialogSettings { showDialog ->
            if (showDialog) {
                createDialog(context)
            } else {
                dialog?.dismiss()
            }
        }
    }

    /**
     * Check SDK flag and existing cookie settings.
     *
     * @param action    Action to be invoked with the Boolean result
     */
    private fun checkDialogSettings(action: (Boolean) -> Unit) {
        disposable.clear()

        checkCookieBannerEnabledUseCase.check()
            .concatMap { getCookieSettingsUseCase.shouldShowDialog() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { showDialog ->
                    action.invoke(showDialog)
                },
                onError = { error ->
                    LogUtil.logError(error.message)
                }
            )
            .addTo(disposable)
    }

    private fun createDialog(context: Context) {
        if (dialog?.isShowing == true || !context.isValid()) return

        dialog = MaterialAlertDialogBuilder(context)
            .setCancelable(false)
            .setView(R.layout.dialog_cookie_alert)
            .setPositiveButton(StringResourcesUtils.getString(R.string.preference_cookies_accept)) { _, _ ->
                acceptAllCookies(context)
            }
            .setNegativeButton(StringResourcesUtils.getString(R.string.settings_about_cookie_settings)) { _, _ ->
                context.startActivity(Intent(context, CookiePreferencesActivity::class.java))
            }
            .create()
            .apply {
                setOnShowListener {
                    val message = StringResourcesUtils.getString(R.string.dialog_cookie_alert_message)
                        .replace("[A]", "<a href='https://mega.nz/cookie'>")
                        .replace("[/A]", "</a>")
                        .toSpannedHtmlText()

                    findViewById<TextView>(R.id.message)?.apply {
                        movementMethod = LinkMovementMethod.getInstance()
                        text = message
                    }
                }
            }.also { it.show() }
    }

    private fun acceptAllCookies(context: Context) {
        updateCookieSettingsUseCase.acceptAll()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onComplete = {
                    if (context.isValid()) {
                        (context.applicationContext as MegaApplication).checkEnabledCookies()
                    }
                },
                onError = { error ->
                    LogUtil.logError(error.message)
                }
            )
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        if (dialog?.isShowing == true) {
            checkDialogSettings { showDialog ->
                if (!showDialog) dialog?.dismiss()
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        disposable.clear()
        dialog?.dismiss()
        dialog = null
    }
}
