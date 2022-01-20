package mega.privacy.android.app.textEditor

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mega.privacy.android.app.UploadService
import mega.privacy.android.app.arch.BaseRxViewModel
import mega.privacy.android.app.components.saver.NodeSaver
import mega.privacy.android.app.di.MegaApi
import mega.privacy.android.app.di.MegaApiFolder
import mega.privacy.android.app.interfaces.ActivityLauncher
import mega.privacy.android.app.interfaces.SnackbarShower
import mega.privacy.android.app.listeners.ExportListener
import mega.privacy.android.app.utils.*
import mega.privacy.android.app.utils.AlertsAndWarnings.showConfirmRemoveLinkDialog
import mega.privacy.android.app.utils.CacheFolderManager.buildTempFile
import mega.privacy.android.app.utils.ChatUtil.authorizeNodeIfPreview
import mega.privacy.android.app.utils.Constants.*
import mega.privacy.android.app.utils.FileUtil.*
import mega.privacy.android.app.utils.LinksUtil.showGetLinkActivity
import mega.privacy.android.app.utils.LogUtil.logError
import mega.privacy.android.app.utils.MegaNodeUtil.handleSelectFolderToCopyResult
import mega.privacy.android.app.utils.MegaNodeUtil.handleSelectFolderToImportResult
import mega.privacy.android.app.utils.MegaNodeUtil.handleSelectFolderToMoveResult
import mega.privacy.android.app.utils.MegaNodeUtil.shareLink
import mega.privacy.android.app.utils.MegaNodeUtil.shareNode
import mega.privacy.android.app.utils.MegaNodeUtil.showTakenDownNodeActionNotAvailableDialog
import mega.privacy.android.app.utils.RunOnUIThreadUtils.runDelay
import mega.privacy.android.app.utils.TextUtil.isTextEmpty
import nz.mega.sdk.*
import nz.mega.sdk.MegaApiJava.INVALID_HANDLE
import nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE
import java.io.*
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class TextEditorViewModel @Inject constructor(
    @MegaApi private val megaApi: MegaApiAndroid,
    @MegaApiFolder private val megaApiFolder: MegaApiAndroid,
    private val megaChatApi: MegaChatApiAndroid
) : BaseRxViewModel() {

    companion object {
        const val MODE = "MODE"
        const val CREATE_MODE = "CREATE_MODE"
        const val VIEW_MODE = "VIEW_MODE"
        const val EDIT_MODE = "EDIT_MODE"
        const val NON_UPDATE_FINISH_ACTION = 0
        const val SUCCESS_FINISH_ACTION = 1
        const val ERROR_FINISH_ACTION = 2
        const val SHOW_LINE_NUMBERS = "SHOW_LINE_NUMBERS"
    }

    private val textEditorData: MutableLiveData<TextEditorData> = MutableLiveData(TextEditorData())
    private val mode: MutableLiveData<String> = MutableLiveData()
    private val fileName: MutableLiveData<String> = MutableLiveData()
    private val pagination: MutableLiveData<Pagination> = MutableLiveData()

    private var needsReadContent = false
    private var isReadingContent = false
    private var errorSettingContent = false
    private var localFileUri: String? = null
    private var streamingFileURL: URL? = null
    private var showLineNumbers = false

    private lateinit var preferences: SharedPreferences

    fun onTextFileEditorDataUpdate(): LiveData<TextEditorData> = textEditorData

    fun getFileName(): LiveData<String> = fileName

    fun onContentTextRead(): LiveData<Pagination> = pagination

    fun getPagination(): Pagination? = pagination.value

    fun getNode(): MegaNode? = textEditorData.value?.node

    fun getNodeAccess(): Int = megaApi.getAccess(getNode())

    fun updateNode() {
        val node = textEditorData.value?.node ?: return

        textEditorData.value?.node = megaApi.getNodeByHandle(node.handle)
        textEditorData.notifyObserver()
    }

    private fun getFileUri(): Uri? = textEditorData.value?.fileUri

    private fun getFileSize(): Long? = textEditorData.value?.fileSize

    fun getAdapterType(): Int = textEditorData.value?.adapterType ?: INVALID_VALUE

    fun isEditableAdapter(): Boolean = textEditorData.value?.editableAdapter ?: false

    fun getMsgChat(): MegaChatMessage? = textEditorData.value?.msgChat

    fun getChatRoom(): MegaChatRoom? = textEditorData.value?.chatRoom

    fun getMode(): LiveData<String> = mode

    fun isViewMode(): Boolean = mode.value == VIEW_MODE

    private fun isEditMode(): Boolean = mode.value == EDIT_MODE

    fun isCreateMode(): Boolean = mode.value == CREATE_MODE

    private fun setViewMode() {
        mode.value = VIEW_MODE
    }

    fun setEditMode() {
        mode.value = EDIT_MODE
    }

    fun getCurrentText(): String? = pagination.value?.getCurrentPageText()

    fun setEditedText(text: String?) {
        pagination.value?.updatePage(text)
    }

    fun getNameOfFile(): String = fileName.value ?: ""

    fun needsReadContent(): Boolean = needsReadContent

    fun isReadingContent(): Boolean = isReadingContent

    fun needsReadOrIsReadingContent(): Boolean = needsReadContent || isReadingContent

    fun errorSettingContent() {
        errorSettingContent = true
    }

    fun thereIsErrorSettingContent(): Boolean = errorSettingContent

    fun thereIsNoErrorSettingContent(): Boolean = !errorSettingContent

    fun setShowLineNumbers(): Boolean {
        showLineNumbers = !showLineNumbers
        preferences.edit().putBoolean(SHOW_LINE_NUMBERS, showLineNumbers).apply()

        return shouldShowLineNumbers()
    }

    fun shouldShowLineNumbers(): Boolean = showLineNumbers

    fun canShowEditFab(): Boolean =
        isViewMode() && isEditableAdapter() && !needsReadOrIsReadingContent()
                && thereIsNoErrorSettingContent()

    /**
     * Checks if the file can be editable depending on the current adapter.
     */
    private fun setEditableAdapter() {
        textEditorData.value?.editableAdapter =
            if (isCreateMode()) true
            else getAdapterType() != OFFLINE_ADAPTER
                    && getAdapterType() != RUBBISH_BIN_ADAPTER && !megaApi.isInRubbish(getNode())
                    && getAdapterType() != FILE_LINK_ADAPTER
                    && getAdapterType() != FOLDER_LINK_ADAPTER
                    && getAdapterType() != ZIP_ADAPTER
                    && getAdapterType() != FROM_CHAT
                    && getAdapterType() != VERSIONS_ADAPTER
                    && getAdapterType() != INVALID_VALUE
                    && getNodeAccess() >= MegaShare.ACCESS_READWRITE
    }

    /**
     * Sets all necessary values from params if available.
     *
     * @param intent      Received intent.
     * @param mi          Current phone memory info in case is needed to read the file on streaming.
     * @param preferences Preference data.
     */
    fun setInitialValues(
        intent: Intent,
        mi: ActivityManager.MemoryInfo,
        preferences: SharedPreferences
    ) {
        val adapterType = intent.getIntExtra(INTENT_EXTRA_KEY_ADAPTER_TYPE, INVALID_VALUE)
        textEditorData.value?.adapterType = adapterType

        when (adapterType) {
            FROM_CHAT -> {
                val msgId = intent.getLongExtra(MESSAGE_ID, MEGACHAT_INVALID_HANDLE)
                val chatId = intent.getLongExtra(CHAT_ID, MEGACHAT_INVALID_HANDLE)

                if (msgId != MEGACHAT_INVALID_HANDLE && chatId != MEGACHAT_INVALID_HANDLE) {
                    textEditorData.value?.chatRoom = megaChatApi.getChatRoom(chatId)
                    var msgChat = megaChatApi.getMessage(chatId, msgId)

                    if (msgChat == null) {
                        msgChat = megaChatApi.getMessageFromNodeHistory(chatId, msgId)
                    }

                    if (msgChat != null) {
                        textEditorData.value?.msgChat = msgChat

                        textEditorData.value?.node = authorizeNodeIfPreview(
                            msgChat.megaNodeList.get(0),
                            megaChatApi,
                            megaApi,
                            chatId
                        )
                    }
                }
            }
            OFFLINE_ADAPTER, ZIP_ADAPTER -> {
                val filePath = intent.getStringExtra(INTENT_EXTRA_KEY_PATH)

                if (filePath != null) {
                    textEditorData.value?.fileUri = filePath.toUri()
                    textEditorData.value?.fileSize = File(filePath).length()
                }
            }
            FILE_LINK_ADAPTER -> {
                textEditorData.value?.node =
                    MegaNode.unserialize(intent.getStringExtra(EXTRA_SERIALIZE_STRING))
            }
            FOLDER_LINK_ADAPTER -> {
                val node = megaApiFolder.getNodeByHandle(
                    intent.getLongExtra(
                        INTENT_EXTRA_KEY_HANDLE,
                        INVALID_HANDLE
                    )
                )

                textEditorData.value?.node = megaApiFolder.authorizeNode(node)
            }
            else -> {
                textEditorData.value?.node = megaApi.getNodeByHandle(
                    intent.getLongExtra(
                        INTENT_EXTRA_KEY_HANDLE,
                        INVALID_HANDLE
                    )
                )
            }
        }

        textEditorData.value?.api =
            if (adapterType == FOLDER_LINK_ADAPTER) megaApiFolder else megaApi

        mode.value = intent.getStringExtra(MODE) ?: VIEW_MODE

        if (isViewMode() || isEditMode()) {
            needsReadContent = true
            initializeReadParams(mi)
        } else {
            pagination.value = Pagination()
        }

        setEditableAdapter()

        fileName.value = intent.getStringExtra(INTENT_EXTRA_KEY_FILE_NAME) ?: getNode()?.name ?: ""

        this.preferences = preferences
        showLineNumbers = preferences.getBoolean(SHOW_LINE_NUMBERS, false)
    }

    /**
     * Initializes the necessary params to read the file content.
     * If file is available locally, the local uri. If not, the streaming URL.
     *
     * @param mi Current phone memory info in case is needed to read the file on streaming.
     */
    private fun initializeReadParams(mi: ActivityManager.MemoryInfo) {
        localFileUri =
            if (getAdapterType() == OFFLINE_ADAPTER || getAdapterType() == ZIP_ADAPTER) getFileUri().toString()
            else getLocalFile(getNode())

        if (isTextEmpty(localFileUri)) {
            val api = textEditorData.value?.api ?: return

            if (api.httpServerIsRunning() == 0) {
                api.httpServerStart()
                textEditorData.value?.needStopHttpServer = true
            }

            api.httpServerSetMaxBufferSize(
                if (mi.totalMem > BUFFER_COMP) MAX_BUFFER_32MB
                else MAX_BUFFER_16MB
            )

            val uri = api.httpServerGetLocalLink(getNode())

            if (!isTextEmpty(uri)) {
                streamingFileURL = URL(uri)
            }
        }
    }

    /**
     * Starts the read action to get the content of the file.
     */
    fun readFileContent() {
        try {
            viewModelScope.launch { readFile() }
        } catch (e: Exception) {
            logError("Exception reading file content", e)
        }
    }

    /**
     * Continues the read action to get the content of the file.
     * Checks if the file is available to read locally. If not, it's read by streaming.
     */
    private suspend fun readFile() {
        withContext(Dispatchers.IO) {
            isReadingContent = true
            needsReadContent = false

            if (!isTextEmpty(localFileUri)) {
                val localFile = File(localFileUri!!)

                if (isFileAvailable(localFile)) {
                    readFile(BufferedReader(FileReader(localFile)))
                    return@withContext
                }
            }

            if (streamingFileURL == null) {
                logError("Error getting the file URL.")
                return@withContext
            }

            val connection: HttpURLConnection =
                streamingFileURL?.openConnection() as HttpURLConnection
            readFile(BufferedReader(InputStreamReader(connection.inputStream)))
        }
    }

    /**
     * Finishes the read action after get all necessary params to do it.
     *
     * @param br Necessary BufferReader to read the file.
     */
    private suspend fun readFile(br: BufferedReader) {
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()

            try {
                var line: String?

                while (br.readLine().also { line = it } != null) {
                    sb.appendLine(line)
                }

                br.close()
            } catch (e: IOException) {
                logError("Exception while reading text file.", e)
            }

            checkIfNeedsStopHttpServer()
            isReadingContent = false

            //Remove latest line break since it's not part of the file content
            val latestBreak = sb.lastIndexOf("\n")
            if (sb.isNotEmpty() && latestBreak != -1 && sb.length - latestBreak == 1) {
                sb.deleteRange(latestBreak, sb.length)
            }


            pagination.postValue(Pagination(sb.toString()))
            sb.clear()
        }
    }

    /**
     * Starts the save file content action by creating a temp file, setting the new or modified text,
     * and then uploading it to the Cloud.
     *
     * @param activity Current activity.
     * @param fromHome True if is creating file from Home page, false otherwise.
     */
    fun saveFile(activity: Activity, fromHome: Boolean) {
        if (!isFileEdited() && !isCreateMode()) {
            setViewMode()
            return
        }

        val tempFile = buildTempFile(activity, fileName.value)
        if (tempFile == null) {
            logError("Cannot get temporal file.")
            return
        }

        val fileWriter = FileWriter(tempFile.absolutePath)
        val out = BufferedWriter(fileWriter)
        out.write(pagination.value?.getEditedText() ?: "")
        out.close()

        if (!isFileAvailable(tempFile)) {
            logError("Cannot manage temporal file.")
            return
        }

        val uploadIntent = Intent(activity, UploadService::class.java)
            .putExtra(UploadService.EXTRA_UPLOAD_TXT, mode.value)
            .putExtra(FROM_HOME_PAGE, fromHome)
            .putExtra(UploadService.EXTRA_FILEPATH, tempFile.absolutePath)
            .putExtra(UploadService.EXTRA_NAME, fileName.value)
            .putExtra(UploadService.EXTRA_SIZE, tempFile.length())
            .putExtra(
                UploadService.EXTRA_PARENT_HASH,
                if (mode.value == CREATE_MODE && getNode() == null) {
                    megaApi.rootNode.handle
                } else if (mode.value == CREATE_MODE) {
                    getNode()?.handle
                } else {
                    getNode()?.parentHandle
                }
            )

        activity.startService(uploadIntent)
        activity.finish()
    }

    /**
     * Stops the http server if has been started before.
     */
    fun checkIfNeedsStopHttpServer() {
        if (textEditorData.value?.needStopHttpServer == true) {
            textEditorData.value?.api?.httpServerStop()
            textEditorData.value?.needStopHttpServer = false
        }
    }

    /**
     * Handle activity result.
     *
     * @param context          Current Context
     * @param requestCode      RequestCode of onActivityResult
     * @param resultCode       ResultCode of onActivityResult
     * @param data             Intent of onActivityResult
     * @param snackbarShower   Interface to show snackbar
     * @param activityLauncher Interface to start activity
     */
    fun handleActivityResult(
        context: Context,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        snackbarShower: SnackbarShower,
        activityLauncher: ActivityLauncher
    ) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return
        }

        when (requestCode) {
            REQUEST_CODE_SELECT_IMPORT_FOLDER -> {
                val toHandle = data.getLongExtra(INTENT_EXTRA_KEY_IMPORT_TO, INVALID_HANDLE)
                if (toHandle == INVALID_HANDLE) {
                    return
                }

                handleSelectFolderToImportResult(
                    resultCode,
                    toHandle,
                    getNode()!!,
                    snackbarShower,
                    activityLauncher
                )
            }
            REQUEST_CODE_SELECT_FOLDER_TO_MOVE -> handleSelectFolderToMoveResult(
                context,
                requestCode,
                resultCode,
                data,
                snackbarShower
            )
            REQUEST_CODE_SELECT_FOLDER_TO_COPY -> handleSelectFolderToCopyResult(
                context,
                requestCode,
                resultCode,
                data,
                snackbarShower,
                activityLauncher
            )
        }
    }

    /**
     * Checks if the content of the file has been modified.
     *
     * @return True if the content has been modified, false otherwise.
     */
    fun isFileEdited(): Boolean = pagination.value?.isEdited() == true

    /**
     * Manages the download action.
     *
     * @param nodeSaver Required object to save nodes.
     */
    fun downloadFile(nodeSaver: NodeSaver) {
        when (getAdapterType()) {
            OFFLINE_ADAPTER -> nodeSaver.saveOfflineNode(getNode()!!.handle, true)
            ZIP_ADAPTER -> nodeSaver.saveUri(
                getFileUri()!!,
                getNameOfFile(),
                getFileSize()!!,
                true
            )
            FROM_CHAT -> nodeSaver.saveNode(
                getNode()!!,
                highPriority = true,
                isFolderLink = true,
                fromMediaViewer = true
            )
            else -> nodeSaver.saveHandle(
                getNode()!!.handle,
                isFolderLink = getAdapterType() == FOLDER_LINK_ADAPTER,
                fromMediaViewer = true
            )
        }
    }

    /**
     * Manages the get or remove link action depending on if the node is already exported or not.
     *
     * @param context Current context.
     */
    fun manageLink(context: Context) {
        if (showTakenDownNodeActionNotAvailableDialog(getNode(), context)) {
            return
        }

        if (getNode()?.isExported == true) {
            showConfirmRemoveLinkDialog(context) {
                megaApi.disableExport(
                    getNode(),
                    ExportListener(context) { runDelay(100L) { updateNode() } })
            }
        } else {
            showGetLinkActivity(context as Activity, getNode()!!.handle)
        }
    }

    /**
     * Manages the share action.
     *
     * @param context     Current context.
     * @param urlFileLink Link if FILE_LINK_ADAPTER, empty otherwise.
     */
    fun share(context: Context, urlFileLink: String) {
        when (getAdapterType()) {
            OFFLINE_ADAPTER, ZIP_ADAPTER -> shareUri(
                context,
                getNameOfFile(),
                getFileUri()
            )
            FILE_LINK_ADAPTER -> shareLink(context, urlFileLink)
            else -> shareNode(context, getNode()!!) { updateNode() }
        }
    }

    fun previousClicked() {
        pagination.value?.previousPage()
        pagination.notifyObserver()
    }

    fun nextClicked() {
        pagination.value?.nextPage()
        pagination.notifyObserver()
    }
}