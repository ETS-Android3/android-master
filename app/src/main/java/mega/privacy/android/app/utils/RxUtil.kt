package mega.privacy.android.app.utils

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Consumer
import java.util.concurrent.TimeUnit

object RxUtil {
    @JvmField
    val IGNORE = Action {}

    @JvmStatic
    fun logErr(context: String): Consumer<in Throwable> {
        return Consumer { throwable: Throwable? ->
            LogUtil.logError("$context onError", throwable)
        }
    }

    /**
     * Took from https://stackoverflow.com/a/56479387/5004910
     */
    fun <T> Flowable<T>.debounceImmediate(timeout: Long, unit: TimeUnit): Flowable<T> =
        publish { it.take(1).concatWith(it.debounce(timeout, unit)) }
}
