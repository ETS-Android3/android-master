package mega.privacy.android.app.mediaplayer.trackinfo

import mega.privacy.android.app.utils.LocationInfo
import java.io.File

/**
 * This class hold UI info for an audio node.
 *
 * @property thumbnail the thumbnail of this node
 * @property availableOffline if this node is available in offline
 * @property size the human readable size of this node
 * @property location the human readable location of this node
 * @property added the human readable added time of this node
 * @property lastModified the human readable last modified time of this node
 */
data class AudioNodeInfo(
    val thumbnail: File,
    val availableOffline: Boolean,
    val size: String,
    val location: LocationInfo,
    val added: String,
    val lastModified: String,
)
