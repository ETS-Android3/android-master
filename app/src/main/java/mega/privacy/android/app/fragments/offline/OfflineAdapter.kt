package mega.privacy.android.app.fragments.offline

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import mega.privacy.android.app.MegaOffline
import mega.privacy.android.app.components.dragger.DragThumbnailGetter
import mega.privacy.android.app.databinding.OfflineItemGridFileBinding
import mega.privacy.android.app.databinding.OfflineItemGridFolderBinding
import mega.privacy.android.app.databinding.OfflineItemListBinding
import mega.privacy.android.app.databinding.SortByHeaderBinding
import mega.privacy.android.app.fragments.homepage.SortByHeaderViewModel

class OfflineAdapter(
    var isList: Boolean,
    private val sortByHeaderViewModel: SortByHeaderViewModel,
    private val listener: OfflineAdapterListener
) : ListAdapter<OfflineNode, OfflineViewHolder>(OfflineNodeDiffCallback()), DragThumbnailGetter {

    fun getOfflineNodes(): List<MegaOffline> = currentList.map { it.node }

    override fun getNodePosition(handle: Long) =
        currentList.indexOfFirst { it.node.handle == handle.toString() }

    override fun getThumbnail(viewHolder: ViewHolder) = when (viewHolder) {
        is OfflineGridFileViewHolder -> viewHolder.getThumbnailView()
        is OfflineListViewHolder -> viewHolder.getThumbnailView()
        else -> null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfflineViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            TYPE_GRID_FOLDER -> OfflineGridFolderViewHolder(
                OfflineItemGridFolderBinding.inflate(inflater, parent, false),
                listener, this::getItem
            )
            TYPE_GRID_FILE -> OfflineGridFileViewHolder(
                OfflineItemGridFileBinding.inflate(inflater, parent, false),
                listener, this::getItem
            )
            TYPE_HEADER -> OfflineSortedByViewHolder(
                SortByHeaderBinding.inflate(inflater, parent, false), sortByHeaderViewModel,
                listener, this::getItem
            )
            else -> OfflineListViewHolder(
                OfflineItemListBinding.inflate(inflater, parent, false),
                listener, this::getItem
            )
        }
    }

    override fun getItemViewType(position: Int): Int {
        val node = getItem(position)

        return when {
            node == OfflineNode.HEADER -> TYPE_HEADER
            isList -> TYPE_LIST
            node == OfflineNode.PLACE_HOLDER || node.node.isFolder -> TYPE_GRID_FOLDER
            else -> TYPE_GRID_FILE
        }
    }

    override fun getItemId(position: Int): Long {
        val node = getItem(position)

        return if (node == OfflineNode.HEADER || node == OfflineNode.PLACE_HOLDER) {
            // id for real node should be positive integer, let's use negative for placeholders
            -position.toLong()
        } else {
            node.node.id.toLong()
        }
    }

    override fun onBindViewHolder(holder: OfflineViewHolder, position: Int) {
        holder.bind(position, getItem(position))
    }

    companion object {
        const val TYPE_LIST = 1
        const val TYPE_GRID_FOLDER = 2
        const val TYPE_GRID_FILE = 3
        const val TYPE_HEADER = 4
    }
}

interface OfflineAdapterListener {
    fun onNodeClicked(position: Int, node: OfflineNode)

    fun onNodeLongClicked(position: Int, node: OfflineNode)

    fun onOptionsClicked(position: Int, node: OfflineNode)
}
