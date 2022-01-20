package mega.privacy.android.app.activities.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import mega.privacy.android.app.lollipop.FileExplorerActivityLollipop
import mega.privacy.android.app.utils.Constants
import nz.mega.sdk.MegaUser

class SelectFolderToShareActivityContract : ActivityResultContract<List<MegaUser>, Intent?>() {

    override fun createIntent(context: Context, users: List<MegaUser>): Intent {
        val emails = users.map { it.email } as ArrayList<String>

        return Intent(context, FileExplorerActivityLollipop::class.java).apply {
            action = FileExplorerActivityLollipop.ACTION_SELECT_FOLDER_TO_SHARE
            putStringArrayListExtra(Constants.SELECTED_CONTACTS, emails)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Intent? =
        when {
            resultCode == Activity.RESULT_OK && intent?.extras != null -> intent
            else -> null
        }
}
