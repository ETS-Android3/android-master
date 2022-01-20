package mega.privacy.android.app.components.saver

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.parcelize.Parcelize
import mega.privacy.android.app.MimeTypeList
import mega.privacy.android.app.R
import mega.privacy.android.app.interfaces.SnackbarShower
import mega.privacy.android.app.interfaces.showSnackbar
import mega.privacy.android.app.utils.FileUtil.copyUriToFile
import mega.privacy.android.app.utils.LogUtil
import mega.privacy.android.app.utils.LogUtil.logError
import mega.privacy.android.app.utils.MegaApiUtils
import mega.privacy.android.app.utils.RunOnUIThreadUtils.post
import mega.privacy.android.app.utils.SDCardOperator
import mega.privacy.android.app.utils.StringResourcesUtils.getString
import nz.mega.sdk.MegaApiAndroid
import java.io.File

@Parcelize
class UriSaving(
    private val uri: Uri,
    private val name: String,
    private val size: Long,
    private val fromMediaViewer: Boolean
) : Saving() {

    override fun totalSize() = size

    override fun hasUnsupportedFile(context: Context): Boolean {
        unsupportedFileName = name
        val checkIntent = Intent(Intent.ACTION_GET_CONTENT)
        checkIntent.type = MimeTypeList.typeForName(name).type
        try {
            !MegaApiUtils.isIntentAvailable(context, checkIntent)
        } catch (e: Exception) {
            LogUtil.logWarning("isIntentAvailable error", e)
            return true
        }

        return false
    }

    override fun fromMediaViewer() = fromMediaViewer

    override fun downloadToGallery() = false

    override fun doDownload(
        megaApi: MegaApiAndroid,
        megaApiFolder: MegaApiAndroid,
        parentPath: String,
        externalSDCard: Boolean,
        sdCardOperator: SDCardOperator?,
        snackbarShower: SnackbarShower?,
    ): AutoPlayInfo {
        if (externalSDCard && sdCardOperator != null) {
            try {
                sdCardOperator.copyUri(parentPath, uri, name)
            } catch (e: Exception) {
                logError("Error copy uri to the sd card path with exception", e);
            }
        } else {
            copyUriToFile(uri, File(parentPath, name))
        }

        post {
            snackbarShower?.showSnackbar(getString(R.string.copy_already_downloaded))
        }

        return AutoPlayInfo.NO_AUTO_PLAY
    }
}
