package mega.privacy.android.app.fragments.offline

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import mega.privacy.android.app.MegaApplication
import mega.privacy.android.app.MegaOffline
import mega.privacy.android.app.MimeTypeThumbnail
import mega.privacy.android.app.R
import mega.privacy.android.app.databinding.FragmentOfflineFileInfoBinding
import mega.privacy.android.app.utils.*
import mega.privacy.android.app.utils.ColorUtils.getColorForElevation
import mega.privacy.android.app.utils.ColorUtils.getThemeColor
import mega.privacy.android.app.utils.LogUtil.logError
import mega.privacy.android.app.utils.Util.getStatusBarHeight
import kotlin.math.abs

@AndroidEntryPoint
class OfflineFileInfoFragment : Fragment() {
    private val args: OfflineFileInfoFragmentArgs by navArgs()
    private var binding by autoCleared<FragmentOfflineFileInfoBinding>()
    private val viewModel: OfflineFileInfoViewModel by viewModels()

    private lateinit var upArrow: Drawable

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOfflineFileInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        upArrow =
            ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_back_white)!!.mutate()
        binding.toolbar.setNavigationOnClickListener { requireActivity().finish() }

        observeLiveData()
        viewModel.loadNode(args.handle)
    }

    private fun observeLiveData() {
        viewModel.node.observe(viewLifecycleOwner) {
            if (it == null) {
                logError("OfflineFileInfoFragment observed null node")

                requireActivity().finish()
            } else {
                if (Util.isDarkMode(requireActivity())) {
                    binding.collapseToolbar.setContentScrimColor(
                        getColorForElevation(
                            requireActivity(),
                            resources.getDimension(R.dimen.toolbar_elevation)
                        )
                    )
                }

                if (it.node.isFolder || it.thumbnail == null) {
                    binding.toolbarNodeIcon.setImageResource(
                        if (it.node.isFolder) {
                            R.drawable.ic_folder_list
                        } else {
                            MimeTypeThumbnail.typeForName(it.node.name).iconResourceId
                        }
                    )

                    binding.toolbarNodeIcon.isVisible = true
                    RunOnUIThreadUtils.post {
                        // since collapseToolbar fits system window, we need center the node
                        // icon in the area exclude status bar, like below:
                        // ----------------
                        // |  status bar  |
                        // ----------------
                        // |              |
                        // |desired center|
                        // |              |
                        // ----------------
                        // so the desired center position is:
                        // statusBarHeight + (totalHeight - statusBarHeight) / 2
                        // and the top margin of node icon is: desiredCenter - iconHeight / 2

                        val desiredCenter = getStatusBarHeight() +
                                (binding.collapseToolbar.measuredHeight - getStatusBarHeight()) / 2

                        val params =
                            binding.toolbarNodeIcon.layoutParams as FrameLayout.LayoutParams
                        params.topMargin =
                            desiredCenter - binding.toolbarNodeIcon.measuredHeight / 2
                        binding.toolbarNodeIcon.layoutParams = params
                    }

                    if (it.node.isFolder) {
                        binding.containsTitle.isVisible = true
                        binding.containsValue.isVisible = true
                    }

                    binding.collapseToolbar.setStatusBarScrimColor(
                        getThemeColor(requireContext(), R.attr.colorPrimaryVariant)
                    )
                    setColorFilterBlack()
                } else {
                    binding.toolbarFilePreview.isVisible = true
                    binding.preview.setImageURI(Uri.fromFile(it.thumbnail))

                    binding.collapseToolbar.setCollapsedTitleTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.grey_087_white_087
                        )
                    )

                    binding.collapseToolbar.setExpandedTitleColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.white_alpha_087
                        )
                    )

                    binding.collapseToolbar.setStatusBarScrimColor(
                        getThemeColor(requireContext(), R.attr.colorPrimaryVariant)
                    )

                    binding.appBar.addOnOffsetChangedListener(OnOffsetChangedListener { appBarLayout, offset ->
                        if (offset == 0) {
                            // Expanded
                            setColorFilterWhite()
                        } else if (offset < 0 && abs(offset) >= appBarLayout.totalScrollRange / 2) {
                            // Collapsed
                            setColorFilterBlack()
                        } else {
                            setColorFilterWhite()
                        }
                    })
                }

                binding.collapseToolbar.title = it.node.name

                binding.availableOfflineSwitch.setOnClickListener { _ ->
                    binding.availableOfflineSwitch.isChecked = true
                    removeFromOffline(it.node) {
                        requireActivity().finish()
                    }
                }
            }
        }

        viewModel.totalSize.observe(viewLifecycleOwner) {
            binding.totalSizeValue.text = it
        }

        viewModel.contains.observe(viewLifecycleOwner) {
            binding.containsValue.text = it
        }

        viewModel.added.observe(viewLifecycleOwner) {
            binding.addedValue.text = it
        }
    }

    private fun removeFromOffline(node: MegaOffline, onConfirmed: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.confirmation_delete_from_save_for_offline)
            .setPositiveButton(R.string.general_remove) { _, _ ->
                OfflineUtils.removeOffline(
                    node,
                    MegaApplication.getInstance().dbH,
                    requireContext()
                )
                onConfirmed()
            }
            .setNegativeButton(R.string.general_cancel, null)
            .show()
    }

    private fun setColorFilterBlack() {
        upArrow.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                requireContext(),
                R.color.grey_087_white_087
            ), PorterDuff.Mode.SRC_IN
        )
        binding.toolbar.navigationIcon = upArrow
    }

    private fun setColorFilterWhite() {
        upArrow.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                requireContext(),
                R.color.white_alpha_087
            ), PorterDuff.Mode.SRC_IN
        )
        binding.toolbar.navigationIcon = upArrow
    }
}
