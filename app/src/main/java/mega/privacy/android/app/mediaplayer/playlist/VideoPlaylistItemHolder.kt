package mega.privacy.android.app.mediaplayer.playlist

import mega.privacy.android.app.databinding.ItemVideoPlaylistBinding

/**
 * ViewHolder for playlist item, for video. Because video playlist is
 * always in dark theme, so we need separate layout file and view holder class.
 */
class VideoPlaylistItemHolder(private val binding: ItemVideoPlaylistBinding) :
    PlaylistViewHolder(binding) {
    override fun bind(paused: Boolean, item: PlaylistItem, itemOperation: PlaylistItemOperation) {
        binding.item = item
        binding.highlight = item.type == PlaylistItem.TYPE_NEXT
        binding.itemOperation = itemOperation
    }
}
