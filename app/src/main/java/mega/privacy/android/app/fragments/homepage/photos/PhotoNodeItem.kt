package mega.privacy.android.app.fragments.homepage.photos

import android.text.Spanned
import mega.privacy.android.app.fragments.homepage.NodeItem
import mega.privacy.android.app.utils.Constants.INVALID_POSITION
import nz.mega.sdk.MegaNode
import java.io.File

data class PhotoNodeItem(
    val type: Int,
    var photoIndex: Int,       // Index of real photo node
    override var node: MegaNode? = null,
    override var index: Int = INVALID_POSITION,      // Index of Node including TYPE_TITLE node (RecyclerView Layout position)
    override var modifiedDate: String = "",
    var formattedDate: Spanned? = null,
    override var thumbnail: File? = null,
    override var selected: Boolean = false,
    override var uiDirty: Boolean = true   // Force refresh the newly created Node list item
) : NodeItem(node, index, false, modifiedDate, thumbnail, selected, uiDirty) {

    companion object {
        const val TYPE_TITLE = 0   // The datetime header
        const val TYPE_PHOTO = 1
    }
}