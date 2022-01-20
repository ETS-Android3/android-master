package mega.privacy.android.app.modalbottomsheet.chatmodalbottomsheet;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;

import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.MegaOffline;
import mega.privacy.android.app.MimeTypeList;
import mega.privacy.android.app.R;
import mega.privacy.android.app.interfaces.SnackbarShower;
import mega.privacy.android.app.lollipop.controllers.ChatController;
import mega.privacy.android.app.lollipop.megachat.AndroidMegaChatMessage;
import mega.privacy.android.app.lollipop.megachat.NodeAttachmentHistoryActivity;
import mega.privacy.android.app.modalbottomsheet.BaseBottomSheetDialogFragment;
import mega.privacy.android.app.utils.StringResourcesUtils;
import nz.mega.sdk.MegaChatMessage;
import nz.mega.sdk.MegaChatRoom;
import nz.mega.sdk.MegaNode;
import nz.mega.sdk.MegaNodeList;

import static mega.privacy.android.app.modalbottomsheet.ModalBottomSheetUtil.setNodeThumbnail;
import static mega.privacy.android.app.utils.ChatUtil.*;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.OfflineUtils.availableOffline;
import static mega.privacy.android.app.utils.OfflineUtils.removeOffline;
import static mega.privacy.android.app.utils.Util.*;
import static nz.mega.sdk.MegaApiJava.INVALID_HANDLE;
import static nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class NodeAttachmentBottomSheetDialogFragment extends BaseBottomSheetDialogFragment implements View.OnClickListener {

    private MegaNode node;
    private MegaNodeList nodeList;
    private MegaChatMessage messageMega;
    private AndroidMegaChatMessage message;
    private long chatId;
    private long messageId;
    private long handle = INVALID_HANDLE;
    private ChatController chatC;
    private int positionMessage;
    private MegaChatRoom chatRoom;
    private ImageView nodeThumb;
    private TextView nodeName;
    private TextView nodeInfo;
    private RelativeLayout titleLayout;
    private LinearLayout titleSeparator;
    private LinearLayout optionView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = View.inflate(getContext(), R.layout.bottom_sheet_node_attachment_item, null);
        titleLayout = contentView.findViewById(R.id.node_attachment_title_layout);
        titleSeparator = contentView.findViewById(R.id.title_separator);
        itemsLayout = contentView.findViewById(R.id.items_layout);

        nodeThumb = contentView.findViewById(R.id.node_attachment_thumbnail);
        nodeName = contentView.findViewById(R.id.node_attachment_name_text);
        nodeInfo = contentView.findViewById(R.id.node_attachment_info_text);

        optionView = contentView.findViewById(R.id.option_view_layout);

        if (savedInstanceState != null) {
            chatId = savedInstanceState.getLong(CHAT_ID, MEGACHAT_INVALID_HANDLE);
            messageId = savedInstanceState.getLong(MESSAGE_ID, MEGACHAT_INVALID_HANDLE);
            handle = savedInstanceState.getLong(HANDLE, INVALID_HANDLE);
        } else if (requireActivity() instanceof NodeAttachmentHistoryActivity) {
            chatId = ((NodeAttachmentHistoryActivity) requireActivity()).chatId;
            messageId = ((NodeAttachmentHistoryActivity) requireActivity()).selectedMessageId;
        }

        logDebug("Chat ID: " + chatId + ", Message ID: " + messageId);
        messageMega = getMegaChatMessage(requireActivity(), megaChatApi, chatId, messageId);
        if (messageMega != null) {
            message = new AndroidMegaChatMessage(messageMega);
        }

        chatRoom = megaChatApi.getChatRoom(chatId);
        chatC = new ChatController(requireActivity());
        dbH = DatabaseHandler.getDbHandler(getActivity());

        return contentView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (message == null || message.getMessage() == null) {
            logWarning("Message is null");
            return;
        }

        nodeList = message.getMessage().getMegaNodeList();

        if (nodeList == null || nodeList.size() == 0) {
            logWarning("Error: nodeList is NULL or empty");
            return;
        }


        TextView optionViewText = contentView.findViewById(R.id.option_view_text);
        LinearLayout optionDownload = contentView.findViewById(R.id.option_download_layout);
        LinearLayout optionImport = contentView.findViewById(R.id.option_import_layout);
        LinearLayout optionSaveOffline = contentView.findViewById(R.id.option_save_offline_layout);
        SwitchMaterial offlineSwitch = contentView.findViewById(R.id.option_save_offline_switch);

        optionDownload.setOnClickListener(this);
        optionView.setOnClickListener(this);
        optionSaveOffline.setOnClickListener(this);
        optionImport.setOnClickListener(this);

        if (chatC.isInAnonymousMode()) {
            optionSaveOffline.setVisibility(View.GONE);
            optionImport.setVisibility(View.GONE);
        }

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            nodeName.setMaxWidth(scaleWidthPx(275, getResources().getDisplayMetrics()));
            nodeInfo.setMaxWidth(scaleWidthPx(275, getResources().getDisplayMetrics()));
        } else {
            nodeName.setMaxWidth(scaleWidthPx(210, getResources().getDisplayMetrics()));
            nodeInfo.setMaxWidth(scaleWidthPx(210, getResources().getDisplayMetrics()));
        }

        if (handle == INVALID_HANDLE) {
            node = nodeList.get(0);
        } else {
            node = getNodeByHandle(handle);
        }

        if (node == null) {
            logWarning("Node is NULL");
            return;
        }

        titleLayout.setVisibility(View.VISIBLE);
        titleSeparator.setVisibility(View.VISIBLE);

        if (handle == INVALID_HANDLE) {
            if (nodeList.size() == 1) {
                showSingleNodeSelected();
            } else {
                optionView.setVisibility(View.VISIBLE);

                long totalSize = 0;
                int count = 0;
                for (int i = 0; i < nodeList.size(); i++) {
                    MegaNode temp = nodeList.get(i);
                    count++;
                    totalSize = totalSize + temp.getSize();
                }
                nodeInfo.setText(getSizeString(totalSize));
                MegaNode node = nodeList.get(0);
                nodeThumb.setImageResource(MimeTypeList.typeForName(node.getName()).getIconResourceId());
                if (count == 1) {
                    nodeName.setText(node.getName());
                } else {
                    nodeName.setText(StringResourcesUtils.getQuantityString(R.plurals.new_general_num_files, count, count));
                }

                if (nodeList.size() == count) {
                    optionViewText.setText(getString(R.string.general_view));
                } else {
                    optionViewText.setText(getString(R.string.general_view_with_revoke, nodeList.size() - count));
                }
            }
        } else {
            showSingleNodeSelected();
        }

        offlineSwitch.setChecked(availableOffline(requireContext(), node));
        offlineSwitch.setOnCheckedChangeListener((v, isChecked) -> onClick(v));

        super.onViewCreated(view, savedInstanceState);
    }

    private void showSingleNodeSelected() {
        setNodeThumbnail(requireContext(), node, nodeThumb);
        nodeName.setText(node.getName());
        nodeInfo.setText(getSizeString(node.getSize()));
        optionView.setVisibility(View.GONE);
    }

    public MegaNode getNodeByHandle(long handle) {
        for (int i = 0; i < nodeList.size(); i++) {
            MegaNode node = nodeList.get(i);
            if (node.getHandle() == handle) {
                return node;
            }
        }
        return null;
    }

    @Override
    public void onClick(View v) {

        if (!isOnline(requireContext())) {
            ((NodeAttachmentHistoryActivity) requireActivity()).showSnackbar(SNACKBAR_TYPE, getString(R.string.error_server_connection_problem), INVALID_HANDLE);
            return;
        }

        ArrayList<AndroidMegaChatMessage> messagesSelected = new ArrayList<>();
        messagesSelected.add(message);

        switch (v.getId()) {
            case R.id.option_download_layout:
                logDebug("Download option");
                if (node == null) {
                    logWarning("The selected node is NULL");
                    return;
                }
                ((NodeAttachmentHistoryActivity) requireActivity()).downloadNodeList(nodeList);
                break;

            case R.id.option_import_layout:
                if (node == null) {
                    logWarning("The selected node is NULL");
                    return;
                }
                chatC.importNode(messageId, chatId, IMPORT_ONLY_OPTION);
                break;

            case R.id.option_save_offline_switch:
            case R.id.option_save_offline_layout:
                if (message == null) {
                    logWarning("Message is NULL");
                    return;
                }

                if (availableOffline(requireContext(), node)) {
                    MegaOffline mOffDelete = dbH.findByHandle(node.getHandle());
                    removeOffline(mOffDelete, dbH, requireContext());
                } else if (requireActivity() instanceof SnackbarShower) {
                    ArrayList<AndroidMegaChatMessage> messages = new ArrayList<>();
                    messages.add(message);
                    chatC.saveForOfflineWithAndroidMessages(messages,
                            megaChatApi.getChatRoom(chatId), (SnackbarShower) requireActivity());
                }
                break;
        }

        setStateBottomSheetBehaviorHidden();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(CHAT_ID, chatId);
        outState.putLong(MESSAGE_ID, messageId);
        outState.putLong(HANDLE, handle);
    }
}
