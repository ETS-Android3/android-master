package mega.privacy.android.app.mediaplayer.playlist

import mega.privacy.android.app.R
import mega.privacy.android.app.utils.Constants.INVALID_SIZE
import mega.privacy.android.app.utils.Constants.INVALID_VALUE
import mega.privacy.android.app.utils.StringResourcesUtils.getString
import java.io.File
import java.util.*

/**
 * UI data class for playlist screen.
 *
 * @property nodeHandle node handle
 * @property nodeName node name
 * @property thumbnail thumbnail file path, null if not available
 * @property index the index used for seek to this item
 * @property type item type
 * @property size size of the node
 */
data class PlaylistItem(
    val nodeHandle: Long,
    val nodeName: String,
    val thumbnail: File?,
    val index: Int,
    val type: Int,
    val size: Long,
) {
    /**
     * Create a new instance with the specified index and item type,
     * and nullify thumbnail if it's not exist.
     *
     * @param index new index
     * @param type item type
     * @return the new instance
     */
    fun finalizeItem(index: Int, type: Int): PlaylistItem {
        return PlaylistItem(
            nodeHandle, nodeName,
            if (thumbnail?.exists() == true) thumbnail else null,
            index, type, size
        )
    }

    /**
     * Create a new instance with new node name.
     *
     * @param newName new node name
     * @return the new instance
     */
    fun updateNodeName(newName: String) =
        PlaylistItem(nodeHandle, newName, thumbnail, index, type, size)

    companion object {
        const val TYPE_PREVIOUS = 1
        const val TYPE_PREVIOUS_HEADER = 2
        const val TYPE_PLAYING = 3
        const val TYPE_PLAYING_HEADER = 4
        const val TYPE_NEXT = 5
        const val TYPE_NEXT_HEADER = 6

        // We can't use the same handle (INVALID_HANDLE) for multiple header items,
        // which will cause display issue when PlaylistItemDiffCallback use
        // handle for areItemsTheSame.
        // RandomUUID() can ensure non-repetitive values in practical purpose.
        private val previousHeaderHandle = UUID.randomUUID().leastSignificantBits
        private val playingHeaderHandle = UUID.randomUUID().leastSignificantBits
        private val nextHeaderHandle = UUID.randomUUID().leastSignificantBits

        /**
         * Create an item for headers.
         *
         * @param type item type
         * @param paused if the audio player is paused, only used for TYPE_PLAYING_HEADER
         */
        fun headerItem(type: Int, paused: Boolean = false): PlaylistItem {
            val name = getString(
                when (type) {
                    TYPE_PREVIOUS_HEADER -> R.string.general_previous
                    TYPE_NEXT_HEADER -> R.string.general_next
                    else -> {
                        if (paused) {
                            R.string.audio_player_now_playing_paused
                        } else {
                            R.string.audio_player_now_playing
                        }
                    }
                }
            )

            val handle = when (type) {
                TYPE_PREVIOUS_HEADER -> previousHeaderHandle
                TYPE_NEXT_HEADER -> nextHeaderHandle
                else -> playingHeaderHandle
            }

            return PlaylistItem(handle, name, null, INVALID_VALUE, type, INVALID_SIZE)
        }
    }
}
