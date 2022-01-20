package mega.privacy.android.app.meeting.activity

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import com.jeremyliao.liveeventbus.LiveEventBus
import dagger.hilt.android.AndroidEntryPoint
import mega.privacy.android.app.BaseActivity
import mega.privacy.android.app.MegaApplication
import mega.privacy.android.app.R
import mega.privacy.android.app.constants.EventConstants.EVENT_ENTER_IN_MEETING
import mega.privacy.android.app.databinding.ActivityMeetingBinding
import mega.privacy.android.app.meeting.fragments.*
import mega.privacy.android.app.objects.PasscodeManagement
import mega.privacy.android.app.utils.Constants.REQUIRE_PASSCODE_INVALID
import mega.privacy.android.app.utils.PasscodeUtil
import nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE
import javax.inject.Inject

@AndroidEntryPoint
class MeetingActivity : BaseActivity() {

    companion object {
        /** The name of actions denoting set
        JOIN/CREATE/JOIN AS GUEST/In-meeting screen as the initial screen */
        const val MEETING_ACTION_JOIN = "join_meeting"
        const val MEETING_ACTION_REJOIN = "rejoin_meeting"
        const val MEETING_ACTION_CREATE = "create_meeting"
        const val MEETING_ACTION_GUEST = "join_meeting_as_guest"
        const val MEETING_ACTION_IN = "in_meeting"
        const val MEETING_ACTION_MAKE_MODERATOR = "make_moderator"
        const val MEETING_ACTION_RINGING = "ringing_meeting"
        const val MEETING_ACTION_RINGING_VIDEO_ON = "ringing_meeting_video_on"
        const val MEETING_ACTION_RINGING_VIDEO_OFF = "ringing_meeting_video_off"
        const val MEETING_ACTION_START = "start_meeting"

        /** The names of the Extra data being passed to the initial fragment */
        const val MEETING_NAME = "meeting_name"
        const val MEETING_LINK = "meeting_link"
        const val MEETING_CHAT_ID = "chat_id"
        const val MEETING_PUBLIC_CHAT_HANDLE = "public_chat_handle"
        const val MEETING_AUDIO_ENABLE = "audio_enable"
        const val MEETING_VIDEO_ENABLE = "video_enable"
        const val MEETING_IS_GUEST = "is_guest"
    }

    @Inject
    lateinit var passcodeUtil: PasscodeUtil

    @Inject
    lateinit var passcodeManagement: PasscodeManagement

    lateinit var binding: ActivityMeetingBinding
    private val meetingViewModel: MeetingActivityViewModel by viewModels()

    private var meetingAction: String? = null

    private var isGuest = false
    private var isLockingEnabled = false

    private fun View.setMarginTop(marginTop: Int) {
        val menuLayoutParams = this.layoutParams as ViewGroup.MarginLayoutParams
        menuLayoutParams.setMargins(0, marginTop, 0, 0)
        this.layoutParams = menuLayoutParams
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let {
            isGuest = intent.getBooleanExtra(
                MEETING_IS_GUEST,
                false
            )
        }

        if ((isGuest && shouldRefreshSessionDueToMegaApiIsNull()) ||
            (!isGuest && shouldRefreshSessionDueToSDK()) || shouldRefreshSessionDueToKarere()
        ) {
            intent?.let {
                it.getLongExtra(MEETING_CHAT_ID, MEGACHAT_INVALID_HANDLE).let { chatId ->
                    if (chatId != MEGACHAT_INVALID_HANDLE) {
                        //Notification of this call should be displayed again
                        MegaApplication.getChatManagement().removeNotificationShown(chatId)
                    }
                }
            }
            return
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 0x00000010

        binding = ActivityMeetingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        meetingAction = intent.action

        isGuest = intent.getBooleanExtra(
            MEETING_IS_GUEST,
            false
        )

        initActionBar()
        initNavigation()
        setStatusBarTranslucent()
    }

    @Suppress("DEPRECATION")
    private fun setStatusBarTranslucent() {
        val decorView: View = window.decorView

        decorView.setOnApplyWindowInsetsListener { v: View, insets: WindowInsets? ->
            val defaultInsets = v.onApplyWindowInsets(insets)

            binding.toolbar.setMarginTop(defaultInsets.systemWindowInsetTop)

            defaultInsets.replaceSystemWindowInsets(
                defaultInsets.systemWindowInsetLeft,
                0,
                defaultInsets.systemWindowInsetRight,
                defaultInsets.systemWindowInsetBottom
            )
        }

        ViewCompat.requestApplyInsets(decorView)
    }

    /**
     * Initialize Action Bar and set icon according to param
     */
    private fun initActionBar() {
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar ?: return
        actionBar.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.title = ""

        when (meetingAction) {
            MEETING_ACTION_CREATE, MEETING_ACTION_JOIN, MEETING_ACTION_GUEST -> {
                actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white)
            }
            MEETING_ACTION_IN -> actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }
    }

    /**
     * Initialize Navigation and set startDestination(initial screen)
     * according to the meeting action
     */
    private fun initNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val navGraph: NavGraph =
            navHostFragment.navController.navInflater.inflate(R.navigation.meeting)


        // The args to be passed to startDestination
        val bundle = Bundle()

        bundle.putLong(
            MEETING_CHAT_ID,
            intent.getLongExtra(MEETING_CHAT_ID, MEGACHAT_INVALID_HANDLE)
        )

        bundle.putLong(
            MEETING_PUBLIC_CHAT_HANDLE,
            intent.getLongExtra(MEETING_PUBLIC_CHAT_HANDLE, MEGACHAT_INVALID_HANDLE)
        )

        // Pass the meeting data to Join Meeting screen
        if (meetingAction == MEETING_ACTION_GUEST || meetingAction == MEETING_ACTION_JOIN) {
            bundle.putString(MEETING_LINK, intent.dataString)
            bundle.putString(MEETING_NAME, intent.getStringExtra(MEETING_NAME))
        }

        if (meetingAction == MEETING_ACTION_IN) {
            bundle.putBoolean(
                MEETING_AUDIO_ENABLE, intent.getBooleanExtra(
                    MEETING_AUDIO_ENABLE,
                    false
                )
            )
            bundle.putBoolean(
                MEETING_VIDEO_ENABLE, intent.getBooleanExtra(
                    MEETING_VIDEO_ENABLE,
                    false
                )
            )

            bundle.putBoolean(
                MEETING_IS_GUEST,
                isGuest
            )
        }

        if (meetingAction == MEETING_ACTION_START) {
            bundle.putString("action", MEETING_ACTION_START)

            bundle.putLong(
                MEETING_CHAT_ID,
                intent.getLongExtra(MEETING_CHAT_ID, MEGACHAT_INVALID_HANDLE)
            )
        }

        navGraph.startDestination = when (meetingAction) {
            MEETING_ACTION_CREATE -> R.id.createMeetingFragment
            MEETING_ACTION_JOIN, MEETING_ACTION_REJOIN -> R.id.joinMeetingFragment
            MEETING_ACTION_GUEST -> R.id.joinMeetingAsGuestFragment
            MEETING_ACTION_START, MEETING_ACTION_IN -> R.id.inMeetingFragment
            MEETING_ACTION_RINGING -> R.id.ringingMeetingFragment
            MEETING_ACTION_MAKE_MODERATOR -> R.id.makeModeratorFragment
            else -> R.id.createMeetingFragment
        }

        navController.setGraph(navGraph, bundle)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Get current fragment from navHostFragment
     */
    fun getCurrentFragment(): MeetingBaseFragment? {
        val navHostFragment: Fragment? =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        return navHostFragment?.childFragmentManager?.fragments?.get(0) as MeetingBaseFragment?
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        meetingViewModel.inviteToChat(this, requestCode, resultCode, intent)
        super.onActivityResult(requestCode, resultCode, intent)
    }

    fun showSnackbar(content: String) {
        showSnackbar(binding.navHostFragment, content)
    }

    override fun onPause() {
        super.onPause()

        val timeRequired = passcodeUtil.timeRequiredForPasscode()
        if (timeRequired != REQUIRE_PASSCODE_INVALID) {
            if (isLockingEnabled) {
                passcodeManagement.lastPause = System.currentTimeMillis() - timeRequired
            } else {
                passcodeUtil.pauseUpdate()
            }
        }

        sendQuitCallEvent()
    }

    override fun onResume() {
        super.onResume()

        isLockingEnabled = passcodeUtil.shouldLock()
        val currentFragment = getCurrentFragment()
        if (currentFragment is InMeetingFragment) {
            currentFragment.sendEnterCallEvent()
        }
    }

    override fun onBackPressed() {
        val currentFragment = getCurrentFragment()

        when (currentFragment) {
            is CreateMeetingFragment -> {
                currentFragment.releaseVideoAndHideKeyboard()
                removeRTCAudioManager()
            }
            is JoinMeetingFragment -> {
                currentFragment.releaseVideoDeviceAndRemoveChatVideoListener()
                removeRTCAudioManager()
            }
            is JoinMeetingAsGuestFragment -> {
                currentFragment.releaseVideoAndHideKeyboard()
                removeRTCAudioManager()
            }
            is InMeetingFragment -> {
                // Prevent guest from quitting the call by pressing back
                if (!isGuest) {
                    currentFragment.removeUI()
                    sendQuitCallEvent()
                }
            }
            is MakeModeratorFragment -> {
                currentFragment.cancel()
            }
        }

        if (currentFragment !is MakeModeratorFragment && (currentFragment !is InMeetingFragment || !isGuest)) {
            finish()
        }
    }

    /**
     * Method to remove the RTC Audio Manager when the call has not been finally established
     */
    private fun removeRTCAudioManager() {
        if (!meetingViewModel.isChatCreatedAndIParticipating()) {
            MegaApplication.getInstance().removeRTCAudioManager()
        }
    }

    private fun sendQuitCallEvent() = LiveEventBus.get(
        EVENT_ENTER_IN_MEETING,
        Boolean::class.java
    ).post(false)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (app.isAnIncomingCallRinging) {
                    app.muteOrUnmute(false)
                }
                false
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (app.isAnIncomingCallRinging) {
                    app.muteOrUnmute(true)
                }
                false
            }
            else -> super.dispatchKeyEvent(event)
        }
    }
}