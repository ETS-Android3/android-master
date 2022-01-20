package mega.privacy.android.app.fragments.recent

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import mega.privacy.android.app.BucketSaved
import mega.privacy.android.app.MimeTypeList
import mega.privacy.android.app.R
import mega.privacy.android.app.components.SimpleDividerItemDecoration
import mega.privacy.android.app.components.dragger.DragToExitSupport.Companion.observeDragSupportEvents
import mega.privacy.android.app.components.dragger.DragToExitSupport.Companion.putThumbnailLocation
import mega.privacy.android.app.databinding.FragmentRecentBucketBinding
import mega.privacy.android.app.fragments.BaseFragment
import mega.privacy.android.app.lollipop.FullScreenImageViewerLollipop
import mega.privacy.android.app.lollipop.ManagerActivityLollipop
import mega.privacy.android.app.lollipop.PdfViewerActivityLollipop
import mega.privacy.android.app.lollipop.adapters.MultipleBucketAdapter
import mega.privacy.android.app.utils.*
import mega.privacy.android.app.utils.Constants.*
import mega.privacy.android.app.utils.LogUtil.logDebug
import mega.privacy.android.app.utils.MegaNodeUtil.manageTextFileIntent
import mega.privacy.android.app.utils.MegaNodeUtil.manageURLNode
import mega.privacy.android.app.utils.MegaNodeUtil.onNodeTapped
import mega.privacy.android.app.utils.Util.getMediaIntent
import mega.privacy.android.app.utils.Util.mutateIconSecondary
import nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE
import nz.mega.sdk.MegaNode
import java.util.*

@AndroidEntryPoint
class RecentsBucketFragment : BaseFragment() {

    private val viewModel by viewModels<RecentsBucketViewModel>()

    private val selectedBucketModel: SelectedBucketViewModel by activityViewModels()

    private lateinit var binding: FragmentRecentBucketBinding

    private lateinit var listView: RecyclerView

    private var adapter: MultipleBucketAdapter? = null

    private lateinit var bucket: BucketSaved

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRecentBucketBinding.inflate(inflater, container, false)
        listView = binding.multipleBucketView
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner

        val selectedBucket = selectedBucketModel.selected.value
        bucket = BucketSaved(selectedBucket)
        viewModel.bucket.value = selectedBucket

        viewModel.cachedActionList.value = selectedBucketModel.currentActionList.value

        viewModel.shouldCloseFragment.observe(viewLifecycleOwner) {
            if (it) Navigation.findNavController(view).popBackStack()
        }

        viewModel.items.observe(viewLifecycleOwner) {
            setupListView(it)
            setupHeaderView()
            setupFastScroller(it)
            setupToolbar()
            checkScroll()
        }

        observeDragSupportEvents(viewLifecycleOwner, listView, VIEWER_FROM_RECETS_BUCKET)
    }

    private fun setupListView(nodes: List<MegaNode>) {
        if (adapter == null) {
            adapter = MultipleBucketAdapter(activity, this, nodes, bucket.isMedia)
            listView.adapter = adapter

            if (bucket.isMedia) {
                val numCells: Int = if (Util.isScreenInPortrait(activity)) 4 else 6
                val gridLayoutManager =
                    GridLayoutManager(activity, numCells, GridLayoutManager.VERTICAL, false)

                listView.layoutManager = gridLayoutManager
            } else {
                val linearLayoutManager = LinearLayoutManager(activity)

                listView.layoutManager = linearLayoutManager
                listView.addItemDecoration(SimpleDividerItemDecoration(activity))
            }

            listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    checkScroll()
                }
            })

            listView.clipToPadding = false
            listView.setHasFixedSize(true)
        } else {
            adapter?.setNodes(nodes)
        }
    }

    private fun setupFastScroller(nodes: List<MegaNode>) {
        if (nodes.size >= MIN_ITEMS_SCROLLBAR) {
            binding.fastscroll.visibility = View.VISIBLE
            binding.fastscroll.setRecyclerView(listView)
        } else {
            binding.fastscroll.visibility = View.GONE
        }
    }

    private fun setupHeaderView() {
        if (!bucket.isMedia) {
            val folder = megaApi.getNodeByHandle(bucket.parentHandle) ?: return
            binding.folderNameText.text = folder.name

            binding.actionImage.setImageDrawable(
                mutateIconSecondary(
                    context,
                    if (bucket.isUpdate) R.drawable.ic_versions_small else R.drawable.ic_recents_up,
                    R.color.grey_054_white_054
                )
            )

            binding.dateText.text =
                TimeUtils.formatBucketDate(activity, bucket.timestamp)
            binding.headerInfoLayout.visibility = View.VISIBLE
        }
    }

    private fun setupToolbar() {
        (activity as ManagerActivityLollipop).setToolbarTitle(
            "${viewModel.items.value?.size} ${getString(R.string.general_files)}"
        )
    }

    private fun checkScroll() {
        val canScroll = listView.canScrollVertically(-1)
        (activity as ManagerActivityLollipop).changeAppBarElevation(canScroll)
    }

    private fun getNodesHandles(isImage: Boolean): LongArray? = viewModel.items.value?.filter {
        if (isImage) {
            MimeTypeList.typeForName(it.name).isImage
        } else {
            FileUtil.isAudioOrVideo(it) && FileUtil.isInternalIntent(it)
        }
    }?.map { it.handle }?.toLongArray()

    fun openFile(
        index: Int,
        node: MegaNode,
        isMedia: Boolean,
    ) {
        val mime = MimeTypeList.typeForName(node.name)
        val localPath =
            FileUtil.getLocalFile(node)
        logDebug("Open node: ${node.name} which mime is: ${mime.type}, local path is: $localPath")

        when {
            mime.isImage -> {
                openImage(index, node)
            }
            FileUtil.isAudioOrVideo(node) -> {
                openAudioVideo(index, node, isMedia, localPath)
            }
            mime.isURL -> {
                manageURLNode(context, megaApi, node)
            }
            mime.isPdf -> {
                openPdf(index, node, localPath)
            }
            mime.isOpenableTextFile(node.size) -> {
                manageTextFileIntent(requireContext(), node, RECENTS_ADAPTER)
            }
            else -> {
                onNodeTapped(
                    context,
                    node,
                    {
                        (requireActivity() as ManagerActivityLollipop).saveNodeByTap(it)
                    },
                    (requireActivity() as ManagerActivityLollipop),
                    (requireActivity() as ManagerActivityLollipop)
                )
            }
        }
    }

    private fun openPdf(
        index: Int,
        node: MegaNode,
        localPath: String?
    ) {
        val intent = Intent(context, PdfViewerActivityLollipop::class.java)
        intent.putExtra(INTENT_EXTRA_KEY_INSIDE, true)
        intent.putExtra(INTENT_EXTRA_KEY_ADAPTER_TYPE, RECENTS_BUCKET_ADAPTER)
        putThumbnailLocation(intent, listView, index, VIEWER_FROM_RECETS_BUCKET, adapter!!)

        val paramsSetSuccessfully =
            if (FileUtil.isLocalFile(node, megaApi, localPath)) {
                FileUtil.setLocalIntentParams(activity, node, intent, localPath, false,
                    requireActivity() as ManagerActivityLollipop)
            } else {
                FileUtil.setStreamingIntentParams(activity, node, megaApi, intent,
                    requireActivity() as ManagerActivityLollipop)
            }
        intent.putExtra(INTENT_EXTRA_KEY_HANDLE, node.handle)
        openOrDownload(intent, paramsSetSuccessfully, node.handle)
    }

    private fun openAudioVideo(
        index: Int,
        node: MegaNode,
        isMedia: Boolean,
        localPath: String?
    ) {
        val intent = if (FileUtil.isInternalIntent(node)) {
            getMediaIntent(activity, node.name)
        } else {
            Intent(Intent.ACTION_VIEW)
        }

        intent.putExtra(INTENT_EXTRA_KEY_ADAPTER_TYPE, RECENTS_BUCKET_ADAPTER)
        putThumbnailLocation(intent, listView, index, VIEWER_FROM_RECETS_BUCKET, adapter!!)
        intent.putExtra(INTENT_EXTRA_KEY_FILE_NAME, node.name)

        if (isMedia) {
            intent.putExtra(NODE_HANDLES, getNodesHandles(false))
            intent.putExtra(INTENT_EXTRA_KEY_IS_PLAYLIST, true)
        } else {
            intent.putExtra(INTENT_EXTRA_KEY_IS_PLAYLIST, false)
        }

        val paramsSetSuccessfully =
            if (FileUtil.isLocalFile(node, megaApi, localPath)) {
                FileUtil.setLocalIntentParams(activity, node, intent, localPath, false,
                    requireActivity() as ManagerActivityLollipop)
            } else {
                FileUtil.setStreamingIntentParams(activity, node, megaApi, intent,
                    requireActivity() as ManagerActivityLollipop)
            }

        if (paramsSetSuccessfully) {
            intent.putExtra(INTENT_EXTRA_KEY_HANDLE, node.handle)

            if (FileUtil.isOpusFile(node)) {
                intent.setDataAndType(intent.data, "audio/*")
            }
        }

        openOrDownload(intent, paramsSetSuccessfully, node.handle)
    }

    private fun openOrDownload(
        intent: Intent,
        paramsSetSuccessfully: Boolean,
        handle: Long
    ) {
        if (paramsSetSuccessfully && MegaApiUtils.isIntentAvailable(activity, intent)) {
            activity?.startActivity(intent)
            activity?.overridePendingTransition(0, 0)
        } else {
            (activity as ManagerActivityLollipop).showSnackbar(
                SNACKBAR_TYPE,
                getString(R.string.intent_not_available),
                MEGACHAT_INVALID_HANDLE
            )
            download(handle)
        }
    }

    private fun openImage(
        index: Int,
        node: MegaNode
    ) {
        val intent = Intent(activity, FullScreenImageViewerLollipop::class.java)

        intent.putExtra(INTENT_EXTRA_KEY_ADAPTER_TYPE, RECENTS_BUCKET_ADAPTER)

        intent.putExtra(INTENT_EXTRA_KEY_HANDLE, node.handle)
        putThumbnailLocation(intent, listView, index, VIEWER_FROM_RECETS_BUCKET, adapter!!)

        intent.putExtra(HANDLE, node.handle)
        intent.putExtra(NODE_HANDLES, getNodesHandles(true))

        startActivity(intent)
        activity?.overridePendingTransition(0, 0)
    }

    private fun download(handle: Long) {
        callManager { it.saveHandlesToDevice(listOf(handle), true, false, false, false) }
    }
}
