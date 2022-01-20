package mega.privacy.android.app.service.iar

import android.content.Context
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.tasks.Task
import mega.privacy.android.app.MegaApplication
import mega.privacy.android.app.middlelayer.iar.OnCompleteListener
import mega.privacy.android.app.middlelayer.iar.RatingHandler
import mega.privacy.android.app.utils.LogUtil

/**
 * Implement rating feature for Google play store using google APIs
 *
 * @param context Context for getting rating dialog
 */
class RatingHandlerImpl(context: Context) : RatingHandler(context) {

    override fun showReviewDialog(context: Context, listener: OnCompleteListener) {
        val manager: ReviewManager = ReviewManagerFactory.create(context)
        val request: Task<ReviewInfo> = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                MegaApplication.getInstance().currentActivity?.let {
                    val flow: Task<Void> = manager.launchReviewFlow(it, reviewInfo)
                    flow.addOnCompleteListener {
                        LogUtil.logDebug("Rating Task Complete")
                        listener.onComplete()
                    }
                }
            } else {
                LogUtil.logError("RatingTask is failed")
            }
        }
    }
}