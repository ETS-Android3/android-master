package mega.privacy.android.app.meeting.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import mega.privacy.android.app.R
import mega.privacy.android.app.databinding.BottomSheetMeetingParticipantBinding
import mega.privacy.android.app.lollipop.controllers.ChatController
import mega.privacy.android.app.meeting.activity.MeetingActivityViewModel
import mega.privacy.android.app.meeting.adapter.Participant
import mega.privacy.android.app.meeting.listenAction
import mega.privacy.android.app.modalbottomsheet.BaseBottomSheetDialogFragment
import mega.privacy.android.app.utils.ContactUtil
import mega.privacy.android.app.utils.StringResourcesUtils
import nz.mega.sdk.*

/**
 * The fragment shows options for different roles when click the three dots
 */
@AndroidEntryPoint
class MeetingParticipantBottomSheetDialogFragment : BaseBottomSheetDialogFragment() {
    private val bottomViewModel: MeetingParticipantBottomSheetDialogViewModel by viewModels()
    private val sharedViewModel: MeetingActivityViewModel by activityViewModels()
    private val inMeetingViewModel: InMeetingViewModel by lazy { (parentFragment as InMeetingFragment).inMeetingViewModel }

    // Get from activity
    private var isModerator = false
    private var isGuest = false
    private var isSpeakerMode = false

    // Get from participant
    private var isContact = false
    private var isMe = false

    private lateinit var participantItem: Participant
    private lateinit var binding: BottomSheetMeetingParticipantBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (arguments?.getSerializable(EXTRA_PARTICIPANT) != null) {
            participantItem = arguments?.getSerializable(EXTRA_PARTICIPANT) as Participant
            isMe = participantItem.isMe
            isContact = participantItem.isContact
        }

        isGuest = arguments?.getBoolean(EXTRA_IS_GUEST) == true
        isModerator = arguments?.getBoolean(EXTRA_IS_MODERATOR) == true
        isSpeakerMode = arguments?.getBoolean(EXTRA_IS_SPEAKER_MODE) == true

        bottomViewModel.initValue(
            isModerator,
            isGuest,
            isSpeakerMode,
            participantItem
        )

        binding =
            BottomSheetMeetingParticipantBinding.inflate(LayoutInflater.from(context), null, false)
                .apply {
                    lifecycleOwner = this@MeetingParticipantBottomSheetDialogFragment
                    viewModel = bottomViewModel
                    participant = participantItem
                }

        contentView = binding.root
        itemsLayout = binding.itemsLayout

        return contentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bottomViewModel.setShowingName(binding.name)
        initItemAction(binding)
        initAvatar(participantItem)
        super.onViewCreated(view, savedInstanceState)
    }

    /**
     * Init the action for different items
     */
    private fun initItemAction(binding: BottomSheetMeetingParticipantBinding) {
        listenAction(binding.addContact) {
            (parentFragment as InMeetingFragment).addContact(participantItem.peerId)
        }

        listenAction(binding.contactInfo) { onContactInfoOrEditProfile() }

        listenAction(binding.sendMessage) {
            // Open chat page
            bottomViewModel.sendMessage(requireActivity())
        }

        listenAction(binding.pingToSpeaker) {
            // Pin to speaker view
            inMeetingViewModel.onItemClick(participantItem)
        }

        listenAction(binding.makeModerator) {
            // Make moderator
            sharedViewModel.giveModeratorPermissions(
                participantItem.peerId
            )
        }

        listenAction(binding.removeParticipant) {
            // Remove participant
            sharedViewModel.currentChatId.value?.let {
                onRemoveParticipant(it)
            }
        }
    }

    /**
     * Open contact info page or edit profile page
     */
    private fun onContactInfoOrEditProfile() {
        if (!bottomViewModel.showEditProfile()) {
            ContactUtil.openContactInfoActivity(
                context,
                ChatController(context).getParticipantEmail(participantItem.peerId)
            )
        } else {
            editProfile()
        }
    }

    /**
     * Shows an alert dialog to confirm the deletion of a participant.
     *
     * @param chatId the current meeting id
     */
    private fun onRemoveParticipant(chatId: Long) {
        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_Mega_MaterialAlertDialog
        ).apply {
            setMessage(
                StringResourcesUtils.getString(
                    R.string.confirmation_remove_chat_contact,
                    participantItem.name
                )
            )
            setPositiveButton(R.string.general_remove) { _, _ ->
               removeParticipant(chatId)
            }
            setNegativeButton(R.string.general_cancel, null)
            show()
        }
    }

    /**
     * Remove participant from this meeting
     *
     * @param chatId the current meeting id
     */
    private fun removeParticipant(chatId: Long) {
        if (chatId != MegaChatApiJava.MEGACHAT_INVALID_HANDLE) {
            megaChatApi.removeFromChat(chatId, participantItem.peerId, null)
        }
    }

    /**
     * Open edit profile page
     */
    private fun editProfile() {
        bottomViewModel.editProfile(requireActivity())
        dismissAllowingStateLoss()
    }

    /**
     * Init avatar for the participant
     *
     * @param participant the target participant
     */
    private fun initAvatar(participant: Participant) {
        binding.avatar.setImageBitmap(sharedViewModel.getAvatarBitmapByPeerId(participant.peerId))
    }

    companion object {
        private const val EXTRA_PARTICIPANT = "extra_participant"
        private const val EXTRA_IS_GUEST = "extra_is_guest"
        private const val EXTRA_IS_MODERATOR = "extra_is_moderator"
        private const val EXTRA_IS_SPEAKER_MODE = "extra_is_speaker_mode"
        const val EXTRA_FROM_MEETING = "extra_from_meeting"

        /**
         * Get the participant object
         */
        fun newInstance(
            isGuest: Boolean,
            isModerator: Boolean,
            isSpeakerMode: Boolean,
            participant: Participant
        ): MeetingParticipantBottomSheetDialogFragment {
            val args = Bundle()

            args.putSerializable(EXTRA_PARTICIPANT, participant)
            args.putBoolean(EXTRA_IS_GUEST, isGuest)
            args.putBoolean(EXTRA_IS_MODERATOR, isModerator)
            args.putBoolean(EXTRA_IS_SPEAKER_MODE, isSpeakerMode)
            val fragment = MeetingParticipantBottomSheetDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
