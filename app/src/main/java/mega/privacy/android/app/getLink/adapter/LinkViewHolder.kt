package mega.privacy.android.app.getLink.adapter

import android.net.Uri
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.zhpan.bannerview.utils.BannerUtils.dp2px
import mega.privacy.android.app.MimeTypeList
import mega.privacy.android.app.R
import mega.privacy.android.app.databinding.ItemLinkBinding
import mega.privacy.android.app.getLink.data.LinkItem
import mega.privacy.android.app.utils.Constants.*
import mega.privacy.android.app.utils.StringResourcesUtils.getString

/**
 * RecyclerView.ViewHolder to draw data items in [LinksAdapter].
 *
 * @property binding ItemLinkBinding necessary to draw the data item.
 */
class LinkViewHolder(
    private val binding: ItemLinkBinding
) : RecyclerView.ViewHolder(binding.root) {

    private val processing by lazy { getString(R.string.link_request_status) }

    /**
     * Draws the data item.
     *
     * @param item [LinkItem.Data] containing the data to draw.
     */
    fun bind(item: LinkItem.Data) {
        val thumbSize: Int
        val margin: Int
        val node = item.node

        if (node.isFolder || item.thumbnail == null) {
            thumbSize = dp2px(ICON_SIZE_DP.toFloat())
            margin = dp2px(ICON_MARGIN_DP.toFloat())
        } else {
            thumbSize = dp2px(THUMB_SIZE_DP.toFloat())
            margin = dp2px(THUMB_MARGIN_DP.toFloat())
        }

        (binding.thumbnailImage.layoutParams as ConstraintLayout.LayoutParams).apply {
            height = thumbSize
            width = thumbSize
            setMargins(margin, margin, margin, margin)
        }

        when {
            node.isFolder -> {
                binding.thumbnailImage.setActualImageResource(R.drawable.ic_folder_list)
            }
            item.thumbnail != null -> {
                binding.thumbnailImage.setImageURI(Uri.fromFile(item.thumbnail))
            }
            else -> {
                binding.thumbnailImage.setActualImageResource(MimeTypeList.typeForName(node.name).iconResourceId)
            }
        }

        val link = item.link

        binding.nameText.text = item.name
        binding.linkText.text = link ?: processing
        binding.infoText.text = item.info
        binding.root.alpha = if (link == null) ALPHA_VIEW_DISABLED else ALPHA_VIEW_ENABLED
    }
}