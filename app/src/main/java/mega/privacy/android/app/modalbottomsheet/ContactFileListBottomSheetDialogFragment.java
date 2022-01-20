package mega.privacy.android.app.modalbottomsheet;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;

import mega.privacy.android.app.MimeTypeList;
import mega.privacy.android.app.R;
import mega.privacy.android.app.interfaces.ActionNodeCallback;
import mega.privacy.android.app.interfaces.SnackbarShower;
import mega.privacy.android.app.lollipop.ContactFileListActivityLollipop;
import mega.privacy.android.app.lollipop.ContactInfoActivityLollipop;
import mega.privacy.android.app.lollipop.FileInfoActivityLollipop;
import nz.mega.sdk.MegaNode;
import nz.mega.sdk.MegaShare;

import static mega.privacy.android.app.modalbottomsheet.ModalBottomSheetUtil.setNodeThumbnail;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.MegaApiUtils.*;
import static mega.privacy.android.app.utils.MegaNodeDialogUtil.showRenameNodeDialog;
import static mega.privacy.android.app.utils.MegaNodeUtil.manageEditTextFileIntent;
import static mega.privacy.android.app.utils.MegaNodeUtil.showConfirmationLeaveIncomingShare;
import static mega.privacy.android.app.utils.Util.*;
import static nz.mega.sdk.MegaApiJava.INVALID_HANDLE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ContactFileListBottomSheetDialogFragment extends BaseBottomSheetDialogFragment implements View.OnClickListener {

    private MegaNode node = null;

    private ContactFileListActivityLollipop contactFileListActivity;
    private ContactInfoActivityLollipop contactInfoActivity;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = View.inflate(getContext(), R.layout.bottom_sheet_contact_file_list, null);
        itemsLayout = contentView.findViewById(R.id.item_list_bottom_sheet_contact_file);

        if (requireActivity() instanceof ContactFileListActivityLollipop) {
            contactFileListActivity = (ContactFileListActivityLollipop) requireActivity();
        } else if (requireActivity() instanceof ContactInfoActivityLollipop) {
            contactInfoActivity = (ContactInfoActivityLollipop) requireActivity();
        }

        if (savedInstanceState != null) {
            long handle = savedInstanceState.getLong(HANDLE, INVALID_HANDLE);
            node = megaApi.getNodeByHandle(handle);
        } else if (requireActivity() instanceof ContactFileListActivityLollipop) {
            node = contactFileListActivity.getSelectedNode();
        } else if (requireActivity() instanceof ContactInfoActivityLollipop) {
            node = contactInfoActivity.getSelectedNode();
        }

        return contentView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (node == null) {
            logWarning("Node NULL");
            return;
        }

        ImageView nodeThumb = contentView.findViewById(R.id.contact_file_list_thumbnail);
        TextView nodeName = contentView.findViewById(R.id.contact_file_list_name_text);
        TextView nodeInfo = contentView.findViewById(R.id.contact_file_list_info_text);
        RelativeLayout nodeIconLayout = contentView.findViewById(R.id.contact_file_list_relative_layout_icon);
        ImageView nodeIcon = contentView.findViewById(R.id.contact_file_list_icon);
        TextView optionDownload = contentView.findViewById(R.id.download_option);
        TextView optionInfo = contentView.findViewById(R.id.properties_option);
        TextView optionLeave = contentView.findViewById(R.id.leave_option);
        TextView optionCopy = contentView.findViewById(R.id.copy_option);
        TextView optionMove = contentView.findViewById(R.id.move_option);
        TextView  optionRename = contentView.findViewById(R.id.rename_option);
        TextView optionRubbish = contentView.findViewById(R.id.rubbish_bin_option);

        optionDownload.setOnClickListener(this);
        optionInfo.setOnClickListener(this);
        optionCopy.setOnClickListener(this);
        optionMove.setOnClickListener(this);
        optionRename.setOnClickListener(this);
        optionLeave.setOnClickListener(this);
        optionRubbish.setOnClickListener(this);

        LinearLayout separatorInfo = contentView.findViewById(R.id.separator_info);
        LinearLayout separatorDownload = contentView.findViewById(R.id.separator_download);
        LinearLayout separatorModify = contentView.findViewById(R.id.separator_modify);

        nodeName.setMaxWidth(scaleWidthPx(200, getResources().getDisplayMetrics()));
        nodeInfo.setMaxWidth(scaleWidthPx(200, getResources().getDisplayMetrics()));

        nodeName.setText(node.getName());

        boolean firstLevel = node.isInShare();
        long parentHandle = INVALID_HANDLE;
        if (requireActivity() instanceof ContactFileListActivityLollipop) {
            parentHandle = contactFileListActivity.getParentHandle();
        }

        int accessLevel = megaApi.getAccess(node);

        optionInfo.setText(R.string.general_info);
        if (node.isFolder()) {
            nodeThumb.setImageResource(R.drawable.ic_folder_incoming);
            nodeInfo.setText(getMegaNodeFolderInfo(node));

            if (firstLevel || parentHandle == INVALID_HANDLE) {
                optionLeave.setVisibility(View.VISIBLE);

                switch (accessLevel) {
                    case MegaShare.ACCESS_FULL:
                        nodeIcon.setImageResource(R.drawable.ic_shared_fullaccess);
                        break;

                    case MegaShare.ACCESS_READ:
                        nodeIcon.setImageResource(R.drawable.ic_shared_read);
                        break;

                    case MegaShare.ACCESS_READWRITE:
                        nodeIcon.setImageResource(R.drawable.ic_shared_read_write);
                        break;
                }

                nodeIconLayout.setVisibility(View.VISIBLE);
            } else {
                optionLeave.setVisibility(View.GONE);
                nodeIconLayout.setVisibility(View.GONE);
            }
        } else {
            long nodeSize = node.getSize();
            nodeInfo.setText(getSizeString(nodeSize));
            nodeIconLayout.setVisibility(View.GONE);
            setNodeThumbnail(requireContext(), node, nodeThumb);
            optionLeave.setVisibility(View.GONE);

            if (MimeTypeList.typeForName(node.getName()).isOpenableTextFile(node.getSize())
                    && accessLevel >= MegaShare.ACCESS_READWRITE) {
                LinearLayout optionEdit = contentView.findViewById(R.id.edit_file_option);
                optionEdit.setVisibility(View.VISIBLE);
                optionEdit.setOnClickListener(this);
            }
        }

        switch (accessLevel) {
            case MegaShare.ACCESS_FULL:
                optionMove.setVisibility(View.GONE);
                optionRename.setVisibility(View.VISIBLE);

                if (firstLevel || parentHandle == INVALID_HANDLE) {
                    optionRubbish.setVisibility(View.GONE);
                } else {
                    optionRubbish.setVisibility(View.VISIBLE);
                }

                break;

            case MegaShare.ACCESS_READ:
            case MegaShare.ACCESS_READWRITE:
                optionMove.setVisibility(View.GONE);
                optionRename.setVisibility(View.GONE);
                optionRubbish.setVisibility(View.GONE);
                break;
        }

        if (optionInfo.getVisibility() == View.GONE || (optionDownload.getVisibility() == View.GONE && optionCopy.getVisibility() == View.GONE
                && optionMove.getVisibility() == View.GONE && optionLeave.getVisibility() == View.GONE
                && optionRename.getVisibility() == View.GONE && optionRubbish.getVisibility() == View.GONE)) {
            separatorInfo.setVisibility(View.GONE);
        } else {
            separatorInfo.setVisibility(View.VISIBLE);
        }

        if (optionDownload.getVisibility() == View.GONE || (optionCopy.getVisibility() == View.GONE && optionMove.getVisibility() == View.GONE
                && optionRename.getVisibility() == View.GONE && optionLeave.getVisibility() == View.GONE && optionRubbish.getVisibility() == View.GONE)) {
            separatorDownload.setVisibility(View.GONE);
        } else {
            separatorDownload.setVisibility(View.VISIBLE);
        }

        if ((optionCopy.getVisibility() == View.GONE
                && optionMove.getVisibility() == View.GONE && optionRename.getVisibility() == View.GONE) || (optionLeave.getVisibility() == View.GONE && optionRubbish.getVisibility() == View.GONE)) {
            separatorModify.setVisibility(View.GONE);
        } else {
            separatorModify.setVisibility(View.VISIBLE);
        }

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onClick(View v) {
        if (node == null) {
            logWarning("The selected node is NULL");
            return;
        }

        ArrayList<Long> handleList = new ArrayList<>();
        handleList.add(node.getHandle());

        switch (v.getId()) {
            case R.id.download_option:
                if (requireActivity() instanceof ContactFileListActivityLollipop) {
                    contactFileListActivity.downloadFile(Collections.singletonList(node));
                } else if (requireActivity() instanceof ContactInfoActivityLollipop) {
                    contactInfoActivity.downloadFile(Collections.singletonList(node));
                }
                break;

            case R.id.properties_option:
                Intent i = new Intent(requireContext(), FileInfoActivityLollipop.class);
                i.putExtra(HANDLE, node.getHandle());
                i.putExtra("from", FROM_INCOMING_SHARES);
                i.putExtra(INTENT_EXTRA_KEY_FIRST_LEVEL, node.isInShare());
                i.putExtra(NAME, node.getName());
                startActivity(i);
                break;

            case R.id.leave_option:
                showConfirmationLeaveIncomingShare(requireActivity(),
                        (SnackbarShower) requireActivity(), node);
                break;

            case R.id.rename_option:
                showRenameNodeDialog(requireActivity(), node, (SnackbarShower) getActivity(),
                        (ActionNodeCallback) getActivity());
                break;

            case R.id.move_option:
                if (requireActivity() instanceof ContactFileListActivityLollipop) {
                    contactFileListActivity.showMoveLollipop(handleList);
                } else if (requireActivity() instanceof ContactInfoActivityLollipop) {
                    contactInfoActivity.showMoveLollipop(handleList);
                }
                break;

            case R.id.copy_option:
                if (requireActivity() instanceof ContactFileListActivityLollipop) {
                    contactFileListActivity.showCopyLollipop(handleList);
                } else if (requireActivity() instanceof ContactInfoActivityLollipop) {
                    contactInfoActivity.showCopyLollipop(handleList);
                }
                break;

            case R.id.rubbish_bin_option:
                if (requireActivity() instanceof ContactFileListActivityLollipop) {
                    contactFileListActivity.askConfirmationMoveToRubbish(handleList);
                } else if (requireActivity() instanceof ContactInfoActivityLollipop) {
                    contactInfoActivity.askConfirmationMoveToRubbish(handleList);
                }
                break;

            case R.id.edit_file_option:
                manageEditTextFileIntent(requireContext(), node, CONTACT_FILE_ADAPTER);
                break;
        }

        setStateBottomSheetBehaviorHidden();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        long handle = node.getHandle();
        outState.putLong(HANDLE, handle);
    }
}
