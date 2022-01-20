package mega.privacy.android.app.contacts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import dagger.hilt.android.AndroidEntryPoint
import mega.privacy.android.app.R
import mega.privacy.android.app.activities.PasscodeActivity
import mega.privacy.android.app.contacts.requests.ContactRequestsFragment
import mega.privacy.android.app.databinding.ActivityContactsBinding
import mega.privacy.android.app.interfaces.SnackbarShower
import mega.privacy.android.app.utils.ExtraUtils.extraNotNull
import mega.privacy.android.app.utils.StringResourcesUtils
import mega.privacy.android.app.utils.Util
import java.util.*

/**
 * Activity that represents the entire UI around contacts for the current user.
 * It includes the navigation between child fragments related to contacts and
 * behaves as the start point for the navigation.
 */
@AndroidEntryPoint
class ContactsActivity : PasscodeActivity(), SnackbarShower {

    companion object {
        private const val EXTRA_SHOW_GROUPS = "EXTRA_SHOW_GROUPS"
        private const val EXTRA_SHOW_SENT_REQUESTS = "EXTRA_SHOW_SENT_REQUESTS"
        private const val EXTRA_SHOW_RECEIVED_REQUESTS = "EXTRA_SHOW_RECEIVED_REQUESTS"

        /**
         * Show Contact list screen
         */
        @JvmStatic
        fun getListIntent(context: Context): Intent =
            Intent(context, ContactsActivity::class.java)

        /**
         * Show Contact group list screen
         */
        @JvmStatic
        fun getGroupsIntent(context: Context): Intent =
            Intent(context, ContactsActivity::class.java).apply {
                putExtra(EXTRA_SHOW_GROUPS, true)
            }

        /**
         * Show Contact sent requests screen
         */
        @JvmStatic
        fun getSentRequestsIntent(context: Context): Intent =
            Intent(context, ContactsActivity::class.java).apply {
                putExtra(EXTRA_SHOW_SENT_REQUESTS, true)
                putExtra(ContactRequestsFragment.EXTRA_IS_OUTGOING, true)
            }

        /**
         * Show Contact received requests screen
         */
        @JvmStatic
        fun getReceivedRequestsIntent(context: Context): Intent =
            Intent(context, ContactsActivity::class.java).apply {
                putExtra(EXTRA_SHOW_RECEIVED_REQUESTS, true)
                putExtra(ContactRequestsFragment.EXTRA_IS_OUTGOING, false)
            }
    }

    private lateinit var binding: ActivityContactsBinding
    private val showGroups by extraNotNull(EXTRA_SHOW_GROUPS, false)
    private val showSentRequests by extraNotNull(EXTRA_SHOW_SENT_REQUESTS, false)
    private val showReceivedRequests by extraNotNull(EXTRA_SHOW_RECEIVED_REQUESTS, false)
    private val toolbarElevation by lazy { resources.getDimension(R.dimen.toolbar_elevation) }
    private val appBarConfiguration by lazy {
        AppBarConfiguration(
            topLevelDestinationIds = setOf(),
            fallbackOnNavigateUpListener = {
                onBackPressed()
                true
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (shouldRefreshSessionDueToSDK() || shouldRefreshSessionDueToKarere()) {
            return
        }

        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupNavigation()
    }

    private fun setupNavigation() {
        val navController = getNavController()

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)

        navController.apply {
            navController.setGraph(
                navController.navInflater.inflate(R.navigation.nav_contacts).apply {
                    startDestination = when {
                        showGroups -> R.id.contact_groups
                        showSentRequests || showReceivedRequests -> R.id.contact_requests
                        else -> R.id.contact_list
                    }
                },
                intent.extras
            )

            addOnDestinationChangedListener { _, _, _ ->
                supportActionBar?.title = StringResourcesUtils.getString(
                    when (this.currentDestination?.id) {
                        R.id.contact_requests -> R.string.section_requests
                        R.id.contact_groups -> R.string.section_groups
                        else -> R.string.section_contacts
                    }
                ).toUpperCase(Locale.getDefault())
            }
        }
    }

    override fun showSnackbar(type: Int, content: String?, chatId: Long) {
        showSnackbar(type, binding.root, content, chatId)
    }

    /**
     * Show toolbar elevation
     *
     * @param show  Flag to either show or hide toolbar elevation
     */
    fun showElevation(show: Boolean) {
        binding.toolbar.elevation = if (show) toolbarElevation else 0F
        if (Util.isDarkMode(this)) {
            val color = if (show) R.color.action_mode_background else R.color.dark_grey
            window.statusBarColor = ContextCompat.getColor(this, color)
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        getNavController().navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    private fun getNavController(): NavController =
        (supportFragmentManager.findFragmentById(R.id.contacts_nav_host_fragment) as NavHostFragment).navController
}
