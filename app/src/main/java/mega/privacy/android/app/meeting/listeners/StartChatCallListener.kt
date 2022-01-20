package mega.privacy.android.app.meeting.listeners

import android.content.Context
import com.jeremyliao.liveeventbus.LiveEventBus
import mega.privacy.android.app.MegaApplication
import mega.privacy.android.app.R
import mega.privacy.android.app.constants.EventConstants.EVENT_ERROR_STARTING_CALL
import mega.privacy.android.app.interfaces.SnackbarShower
import mega.privacy.android.app.interfaces.showSnackbar
import mega.privacy.android.app.listeners.ChatBaseListener
import mega.privacy.android.app.utils.LogUtil.logDebug
import mega.privacy.android.app.utils.LogUtil.logError
import mega.privacy.android.app.utils.StringResourcesUtils.getString
import nz.mega.sdk.*

class StartChatCallListener(context: Context?) : ChatBaseListener(context) {

    private var callback: StartChatCallCallback? = null
    private var snackbarShower: SnackbarShower? = null

    constructor(
        context: Context?,
        snackbarShower: SnackbarShower,
    ) : this(context) {
        this.snackbarShower = snackbarShower
    }

    constructor(
        context: Context?,
        snackbarShower: SnackbarShower,
        callback: StartChatCallCallback
    ) : this(context) {
        this.callback = callback
        this.snackbarShower = snackbarShower
    }

    override fun onRequestFinish(api: MegaChatApiJava, request: MegaChatRequest, e: MegaChatError) {
        if (request.type != MegaChatRequest.TYPE_START_CHAT_CALL) {
            return
        }

        if (e.errorCode == MegaError.API_OK) {
            logDebug("Call started")
            MegaApplication.getChatManagement().setSpeakerStatus(request.chatHandle, request.flag)
            val call: MegaChatCall = api.getChatCall(request.chatHandle)
            if(call.isOutgoing){
                MegaApplication.getChatManagement().setRequestSentCall(call.callId, true)
            }

            callback?.onCallStarted(request.chatHandle, request.flag, request.paramType)
        } else {
            logError("Error Starting call, error code "+e.errorCode)
            snackbarShower?.showSnackbar(getString(R.string.call_error))
            LiveEventBus.get(EVENT_ERROR_STARTING_CALL,
                Long::class.java
            ).post(request.chatHandle)

            callback?.onCallFailed(request.chatHandle)
        }
    }

    interface StartChatCallCallback {
        fun onCallStarted(chatId: Long, enableVideo: Boolean, enableAudio: Int)
        fun onCallFailed(chatId: Long)
    }
}