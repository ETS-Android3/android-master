package mega.privacy.android.app.listeners

import android.content.Intent
import com.jeremyliao.liveeventbus.LiveEventBus
import mega.privacy.android.app.MegaApplication
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.app.utils.Constants.*
import mega.privacy.android.app.utils.LogUtil
import nz.mega.sdk.*
import nz.mega.sdk.MegaChatApi.INIT_ONLINE_SESSION
import android.util.Pair
import mega.privacy.android.app.constants.EventConstants.EVENT_CHAT_CONNECTION_STATUS
import mega.privacy.android.app.constants.EventConstants.EVENT_CHAT_TITLE_CHANGE
import mega.privacy.android.app.constants.EventConstants.EVENT_PRIVILEGES_CHANGE

class GlobalChatListener(private val application: MegaApplication) : MegaChatListenerInterface {
    override fun onChatListItemUpdate(api: MegaChatApiJava?, item: MegaChatListItem?) {
        if (item != null) {
            if (item.hasChanged(MegaChatListItem.CHANGE_TYPE_OWN_PRIV) || item.hasChanged(
                    MegaChatListItem.CHANGE_TYPE_PARTICIPANTS
                )
            ) {
                LiveEventBus.get(
                    EVENT_PRIVILEGES_CHANGE,
                    MegaChatListItem::class.java
                ).post(item)
            }

            if (item.hasChanged(MegaChatListItem.CHANGE_TYPE_TITLE)) {
                api?.let {
                    it.getChatRoom(item.chatId)?.let { chat ->
                        LiveEventBus.get(
                            EVENT_CHAT_TITLE_CHANGE,
                            MegaChatRoom::class.java
                        ).post(chat)
                    }
                }
            }

            application.onChatListItemUpdate(api, item)
        }
    }

    override fun onChatInitStateUpdate(api: MegaChatApiJava?, newState: Int) {
        if (newState == INIT_ONLINE_SESSION) {
            api?.let {
                val list = it.chatListItems
                if (!list.isNullOrEmpty()) {
                    for (i in 0 until list.size) {
                        MegaApplication.getChatManagement().addCurrentGroupChat(list[i].chatId)
                    }
                }
            }
        }
    }

    override fun onChatOnlineStatusUpdate(
        api: MegaChatApiJava?,
        userhandle: Long,
        status: Int,
        inProgress: Boolean
    ) {
        if (userhandle == api?.myUserHandle) {
            LiveEventBus.get(EVENT_CHAT_STATUS_CHANGE, Int::class.java).post(status)
        }
    }

    override fun onChatPresenceConfigUpdate(
        api: MegaChatApiJava?,
        config: MegaChatPresenceConfig?
    ) {
        if (config?.isPending == false) {
            LogUtil.logDebug("Launch local broadcast")
            val intent = Intent(Constants.BROADCAST_ACTION_INTENT_SIGNAL_PRESENCE)
            application.sendBroadcast(intent)
        }
    }

    override fun onChatConnectionStateUpdate(api: MegaChatApiJava?, chatid: Long, newState: Int) {
        val chatRoom = api!!.getChatRoom(chatid)
        if (newState == MegaChatApi.CHAT_CONNECTION_ONLINE && chatRoom != null) {
            val chatIdAndState = Pair.create(chatid, newState)
            LiveEventBus.get(
                EVENT_CHAT_CONNECTION_STATUS,
                Pair::class.java
            ).post(chatIdAndState)
        }
    }

    override fun onChatPresenceLastGreen(api: MegaChatApiJava?, userhandle: Long, lastGreen: Int) {
    }
}
