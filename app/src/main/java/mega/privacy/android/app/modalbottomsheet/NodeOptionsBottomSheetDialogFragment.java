package mega.privacy.android.app.modalbottomsheet;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.jeremyliao.liveeventbus.LiveEventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mega.privacy.android.app.MegaOffline;
import mega.privacy.android.app.MimeTypeList;
import mega.privacy.android.app.R;
import mega.privacy.android.app.interfaces.SnackbarShower;
import mega.privacy.android.app.lollipop.FileContactListActivityLollipop;
import mega.privacy.android.app.lollipop.FileInfoActivityLollipop;
import mega.privacy.android.app.lollipop.ManagerActivityLollipop;
import mega.privacy.android.app.lollipop.controllers.NodeController;
import mega.privacy.android.app.utils.MegaNodeUtil;
import mega.privacy.android.app.utils.StringResourcesUtils;
import nz.mega.sdk.MegaNode;
import nz.mega.sdk.MegaShare;
import nz.mega.sdk.MegaUser;

import static mega.privacy.android.app.lollipop.ManagerActivityLollipop.INCOMING_TAB;
import static mega.privacy.android.app.lollipop.ManagerActivityLollipop.LINKS_TAB;
import static mega.privacy.android.app.lollipop.ManagerActivityLollipop.OUTGOING_TAB;
import static mega.privacy.android.app.modalbottomsheet.ModalBottomSheetUtil.*;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.FileUtil.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.MegaApiUtils.*;
import static mega.privacy.android.app.utils.MegaNodeUtil.*;
import static mega.privacy.android.app.utils.OfflineUtils.*;
import static mega.privacy.android.app.utils.StringResourcesUtils.getQuantityString;
import static mega.privacy.android.app.utils.Util.*;
import static mega.privacy.android.app.utils.ContactUtil.*;
import static nz.mega.sdk.MegaApiJava.INVALID_HANDLE;

public class NodeOptionsBottomSheetDialogFragment extends BaseBottomSheetDialogFragment implements View.OnClickListener {
    /** The "modes" are defined to allow the client to specify the dialog style more flexibly.
    At the same time, compatible with old code. For which mode corresponds to which dialog style,
     please refer to the code */
    /** No definite mode, map the drawerItem to a specific mode */
    public static final int MODE0 = 0;
    /** For Cloud Drive */
    public static final int MODE1 = 1;
    /** For Rubbish Bin */
    public static final int MODE2 = 2;
    /** For Inbox */
    public static final int MODE3 = 3;
    /** For Shared items */
    public static final int MODE4 = 4;
    /** For Search */
    public static final int MODE5 = 5;
    /** For Recents */
    public static final int MODE6 = 6;

    private static final String SAVED_STATE_KEY_MODE = "MODE";

    private int mMode;

    private MegaNode node = null;
    private NodeController nC;

    private TextView nodeInfo;

    private ManagerActivityLollipop.DrawerItem drawerItem;

    public NodeOptionsBottomSheetDialogFragment(int mode) {
        if (mode >= MODE0 && mode <= MODE6) {
            mMode = mode;
        }
    }

    public NodeOptionsBottomSheetDialogFragment() {
        mMode = MODE0;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = View.inflate(getContext(), R.layout.bottom_sheet_node_item, null);
        itemsLayout = contentView.findViewById(R.id.items_layout_bottom_sheet_node);

        if (savedInstanceState != null) {
            long handle = savedInstanceState.getLong(HANDLE, INVALID_HANDLE);
            node = megaApi.getNodeByHandle(handle);
            if (requireActivity() instanceof ManagerActivityLollipop) {
                drawerItem = ((ManagerActivityLollipop) requireActivity()).getDrawerItem();
            }
            mMode = savedInstanceState.getInt(SAVED_STATE_KEY_MODE, MODE0);
        } else {
            if (requireActivity() instanceof ManagerActivityLollipop) {
                node = ((ManagerActivityLollipop) requireActivity()).getSelectedNode();
                drawerItem = ((ManagerActivityLollipop) requireActivity()).getDrawerItem();
            }
        }

        nC = new NodeController(requireActivity());

        return contentView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ImageView nodeThumb = contentView.findViewById(R.id.node_thumbnail);
        TextView nodeName = contentView.findViewById(R.id.node_name_text);

        nodeInfo = contentView.findViewById(R.id.node_info_text);
        ImageView nodeVersionsIcon = contentView.findViewById(R.id.node_info_versions_icon);
        RelativeLayout nodeIconLayout = contentView.findViewById(R.id.node_relative_layout_icon);
        ImageView nodeIcon = contentView.findViewById(R.id.node_icon);
        ImageView permissionsIcon = contentView.findViewById(R.id.permissions_icon);

        LinearLayout optionEdit = contentView.findViewById(R.id.edit_file_option);

        TextView optionInfo = contentView.findViewById(R.id.properties_option);
        // option Versions
        LinearLayout optionVersionsLayout = contentView.findViewById(R.id.option_versions_layout);
        TextView versions = contentView.findViewById(R.id.versions);
//      optionFavourite
        TextView optionFavourite = contentView.findViewById(R.id.favorite_option);
//      optionLabel
        LinearLayout optionLabel = contentView.findViewById(R.id.option_label_layout);
        TextView optionLabelCurrent = contentView.findViewById(R.id.option_label_current);
//      optionGallery
        TextView optionGallery = contentView.findViewById(R.id.gallery_option);
//      counterSave
        TextView optionDownload = contentView.findViewById(R.id.download_option);
        LinearLayout optionOffline = contentView.findViewById(R.id.option_offline_layout);
        SwitchMaterial offlineSwitch = contentView.findViewById(R.id.file_properties_switch);
//      counterShares
        TextView optionLink = contentView.findViewById(R.id.link_option);
        TextView optionRemoveLink = contentView.findViewById(R.id.remove_link_option);
        TextView optionShare = contentView.findViewById(R.id.share_option);
        TextView optionShareFolder = contentView.findViewById(R.id.share_folder_option);
        TextView optionClearShares = contentView.findViewById(R.id.clear_share_option);
        TextView optionSendChat = contentView.findViewById(R.id.send_chat_option);
//      counterModify
        TextView optionRename = contentView.findViewById(R.id.rename_option);
        TextView optionMove = contentView.findViewById(R.id.move_option);
        TextView optionCopy = contentView.findViewById(R.id.copy_option);
        TextView optionRestoreFromRubbish = contentView.findViewById(R.id.restore_option);
//      counterOpen
        TextView optionOpenFolder = contentView.findViewById(R.id.open_folder_option);
        TextView optionOpenWith = contentView.findViewById(R.id.open_with_option);
//      counterRemove
        TextView optionLeaveShares = contentView.findViewById(R.id.leave_share_option);
        TextView optionRubbishBin = contentView.findViewById(R.id.rubbish_bin_option);
        TextView optionRemove = contentView.findViewById(R.id.remove_option);

        optionEdit.setOnClickListener(this);
        optionLabel.setOnClickListener(this);
        optionGallery.setOnClickListener(this);
        optionFavourite.setOnClickListener(this);
        optionDownload.setOnClickListener(this);
        optionOffline.setOnClickListener(this);
        optionInfo.setOnClickListener(this);
        optionLink.setOnClickListener(this);
        optionRemoveLink.setOnClickListener(this);
        optionShare.setOnClickListener(this);
        optionShareFolder.setOnClickListener(this);
        optionClearShares.setOnClickListener(this);
        optionLeaveShares.setOnClickListener(this);
        optionRename.setOnClickListener(this);
        optionSendChat.setOnClickListener(this);
        optionMove.setOnClickListener(this);
        optionCopy.setOnClickListener(this);
        optionRubbishBin.setOnClickListener(this);
        optionRestoreFromRubbish.setOnClickListener(this);
        optionRemove.setOnClickListener(this);
        optionOpenFolder.setOnClickListener(this);
        optionOpenWith.setOnClickListener(this);

        TextView viewInFolder = contentView.findViewById(R.id.view_in_folder_option);
        if (mMode == MODE6) {
            viewInFolder.setVisibility(View.VISIBLE);
            viewInFolder.setOnClickListener(this);
        } else {
            viewInFolder.setVisibility(View.GONE);
            viewInFolder.setOnClickListener(null);
        }

        int counterOpen = 2;
        int counterSave = 2;
        int counterShares = 6;
        int counterModify = 4;

        LinearLayout separatorOpen = contentView.findViewById(R.id.separator_open_options);
        LinearLayout separatorDownload = contentView.findViewById(R.id.separator_download_options);
        LinearLayout separatorShares = contentView.findViewById(R.id.separator_share_options);
        LinearLayout separatorModify = contentView.findViewById(R.id.separator_modify_options);

        nodeIconLayout.setVisibility(View.GONE);
        permissionsIcon.setVisibility(View.GONE);

        if (!isScreenInPortrait(requireContext())) {
            logDebug("Landscape configuration");
            nodeName.setMaxWidth(scaleWidthPx(275, getResources().getDisplayMetrics()));
            nodeInfo.setMaxWidth(scaleWidthPx(275, getResources().getDisplayMetrics()));
        } else {
            nodeName.setMaxWidth(scaleWidthPx(210, getResources().getDisplayMetrics()));
            nodeInfo.setMaxWidth(scaleWidthPx(210, getResources().getDisplayMetrics()));
        }

        if (node == null) return;

        if (node.isFile()) {
            optionOpenWith.setVisibility(View.VISIBLE);
        } else {
            counterOpen--;
            optionOpenWith.setVisibility(View.GONE);
        }

        if (isOnline(requireContext())) {
            nodeName.setText(node.getName());
            if (node.isFolder()) {
                optionVersionsLayout.setVisibility(View.GONE);
                nodeInfo.setText(getMegaNodeFolderInfo(node));
                nodeVersionsIcon.setVisibility(View.GONE);

                nodeThumb.setImageResource(getFolderIcon(node, drawerItem));

                if (isEmptyFolder(node)) {
                    counterSave--;
                    optionOffline.setVisibility(View.GONE);
                }

                counterShares--;
                optionSendChat.setVisibility(View.GONE);
            } else {
                nodeInfo.setText(getFileInfo(node));
                if (megaApi.hasVersions(node)) {
                    optionVersionsLayout.setVisibility(View.VISIBLE);
                    versions.setText(String.valueOf(megaApi.getNumVersions(node)));
                } else {
                    optionVersionsLayout.setVisibility(View.GONE);
                }
                if (megaApi.hasVersions(node)) {
                    nodeVersionsIcon.setVisibility(View.VISIBLE);
                } else {
                    nodeVersionsIcon.setVisibility(View.GONE);
                }

                setNodeThumbnail(requireContext(), node, nodeThumb);
                optionSendChat.setVisibility(View.VISIBLE);
            }
        }

        int accessLevel = megaApi.getAccess(node);

        if (accessLevel != MegaShare.ACCESS_OWNER) {
            counterShares--;
            optionShare.setVisibility(View.GONE);
        }

        if (mMode == MODE0) {
            mapDrawerItemToMode(drawerItem);
        }

        optionInfo.setText(R.string.general_info);

        switch (mMode) {
            case MODE1:
                logDebug("show Cloud bottom sheet");

                if (node.isFolder()) {
                    optionShareFolder.setVisibility(View.VISIBLE);
                    if (isOutShare(node)) {
                        optionShareFolder.setText(R.string.manage_share);
                        optionClearShares.setVisibility(View.VISIBLE);
                    } else {
                        optionShareFolder.setText(R.string.context_share_folder);
                        counterShares--;
                        optionClearShares.setVisibility(View.GONE);
                    }
                } else {
                    if (MimeTypeList.typeForName(node.getName()).isOpenableTextFile(node.getSize())) {
                        optionEdit.setVisibility(View.VISIBLE);
                    }

                    counterShares--;
                    optionShareFolder.setVisibility(View.GONE);
                    counterShares--;
                    optionClearShares.setVisibility(View.GONE);
                }

                if (node.isExported()) {
                    //Node has public link
                    optionLink.setText(R.string.edit_link_option);
                    optionRemoveLink.setVisibility(View.VISIBLE);
                } else {
                    optionLink.setText(StringResourcesUtils.getQuantityString(R.plurals.get_links, 1));
                    counterShares--;
                    optionRemoveLink.setVisibility(View.GONE);
                }

                offlineSwitch.setChecked(availableOffline(requireContext(), node));
                optionInfo.setVisibility(View.VISIBLE);
                optionRubbishBin.setVisibility(View.VISIBLE);
                optionLink.setVisibility(View.VISIBLE);

                optionRename.setVisibility(View.VISIBLE);
                optionMove.setVisibility(View.VISIBLE);
                optionCopy.setVisibility(View.VISIBLE);

                optionLabel.setVisibility(View.VISIBLE);
                optionFavourite.setVisibility(View.VISIBLE);

                MimeTypeList nodeMime = MimeTypeList.typeForName(node.getName());
                if (!isAndroid11OrUpper() && (nodeMime.isImage() || nodeMime.isVideo())) {
                    optionGallery.setVisibility(View.VISIBLE);
                } else {
                    optionGallery.setVisibility(View.GONE);
                }

                //Hide
                optionRemove.setVisibility(View.GONE);
                optionLeaveShares.setVisibility(View.GONE);
                counterOpen--;
                optionOpenFolder.setVisibility(View.GONE);
                counterModify--;
                optionRestoreFromRubbish.setVisibility(View.GONE);
                break;

            case MODE2:
                logDebug("show Rubbish bottom sheet");

                long restoreHandle = node.getRestoreHandle();
                if (restoreHandle != INVALID_HANDLE) {
                    MegaNode restoreNode = megaApi.getNodeByHandle(restoreHandle);
                    if ((!megaApi.isInRubbish(node)) || restoreNode == null || megaApi.isInRubbish(restoreNode)) {
                        counterModify--;
                        optionRestoreFromRubbish.setVisibility(View.GONE);
                    } else {
                        optionRestoreFromRubbish.setVisibility(View.VISIBLE);
                    }
                } else {
                    counterModify--;
                    optionRestoreFromRubbish.setVisibility(View.GONE);
                }

                nodeIconLayout.setVisibility(View.GONE);

                optionRemove.setVisibility(View.VISIBLE);
                optionInfo.setVisibility(View.VISIBLE);
                optionFavourite.setVisibility(View.VISIBLE);
                optionLabel.setVisibility(View.VISIBLE);

                //Hide
                counterOpen--;
                optionOpenWith.setVisibility(View.GONE);
                counterModify--;
                optionMove.setVisibility(View.GONE);
                counterModify--;
                optionRename.setVisibility(View.GONE);
                counterModify--;
                optionCopy.setVisibility(View.GONE);
                counterShares--;
                optionClearShares.setVisibility(View.GONE);
                optionLeaveShares.setVisibility(View.GONE);
                optionRubbishBin.setVisibility(View.GONE);
                counterShares--;
                optionShare.setVisibility(View.GONE);
                counterShares--;
                optionShareFolder.setVisibility(View.GONE);
                counterShares--;
                optionLink.setVisibility(View.GONE);
                counterShares--;
                optionRemoveLink.setVisibility(View.GONE);
                counterOpen--;
                optionOpenFolder.setVisibility(View.GONE);
                counterSave--;
                optionDownload.setVisibility(View.GONE);
                optionGallery.setVisibility(View.GONE);
                counterSave--;
                optionOffline.setVisibility(View.GONE);
                counterShares--;
                optionSendChat.setVisibility(View.GONE);
                break;

            case MODE3:

                if (!node.isFolder()) {
                    if (MimeTypeList.typeForName(node.getName()).isOpenableTextFile(node.getSize())) {
                        optionEdit.setVisibility(View.VISIBLE);
                    }
                }

                if (node.isExported()) {
                    //Node has public link
                    optionLink.setText(R.string.edit_link_option);
                    optionRemoveLink.setVisibility(View.VISIBLE);
                } else {
                    optionLink.setText(StringResourcesUtils.getQuantityString(R.plurals.get_links, 1));
                    counterShares--;
                    optionRemoveLink.setVisibility(View.GONE);
                }

                optionDownload.setVisibility(View.VISIBLE);

                offlineSwitch.setChecked(availableOffline(requireContext(), node));
                optionInfo.setVisibility(View.VISIBLE);
                optionFavourite.setVisibility(View.VISIBLE);
                optionLabel.setVisibility(View.VISIBLE);
                optionRubbishBin.setVisibility(View.VISIBLE);
                optionLink.setVisibility(View.VISIBLE);

                optionRubbishBin.setVisibility(View.VISIBLE);
                optionRename.setVisibility(View.VISIBLE);
                optionMove.setVisibility(View.VISIBLE);
                optionCopy.setVisibility(View.VISIBLE);

                //Hide
                counterShares--;
                optionClearShares.setVisibility(View.GONE);
                optionRemove.setVisibility(View.GONE);
                optionLeaveShares.setVisibility(View.GONE);
                counterOpen--;
                optionOpenFolder.setVisibility(View.GONE);
                counterShares--;
                optionShareFolder.setVisibility(View.GONE);
                counterModify--;
                optionRestoreFromRubbish.setVisibility(View.GONE);

                break;

            case MODE4:

                int tabSelected = ((ManagerActivityLollipop) requireActivity()).getTabItemShares();
                if (tabSelected == 0) {
                    logDebug("showOptionsPanelIncoming");
                    long incomingParentHandle = ((ManagerActivityLollipop) requireActivity()).getParentHandleIncoming();
                    boolean isParentNode = node.getHandle() == incomingParentHandle;

                    if (node.isFolder()) {
                        counterShares--;
                        optionSendChat.setVisibility(View.GONE);
                    } else {
                        if (MimeTypeList.typeForName(node.getName()).isOpenableTextFile(node.getSize())
                                && accessLevel >= MegaShare.ACCESS_READWRITE) {
                            optionEdit.setVisibility(View.VISIBLE);
                        }

                        optionSendChat.setVisibility(View.VISIBLE);
                    }

                    nodeIconLayout.setVisibility(View.GONE);

                    counterOpen--;
                    optionOpenFolder.setVisibility(View.GONE);
                    optionDownload.setVisibility(View.VISIBLE);
                    offlineSwitch.setChecked(availableOffline(requireContext(), node));
                    optionInfo.setVisibility(View.VISIBLE);
                    optionRemove.setVisibility(View.GONE);
                    counterShares--;
                    optionShareFolder.setVisibility(View.GONE);

                    int dBT = ((ManagerActivityLollipop) requireActivity()).getDeepBrowserTreeIncoming();
                    logDebug("DeepTree value:" + dBT);

                    if (dBT > FIRST_NAVIGATION_LEVEL && !isParentNode) {
                        optionLeaveShares.setVisibility(View.GONE);
                    } else {
                        //Show the owner of the shared folder
                        showOwnerSharedFolder();
                        optionLeaveShares.setVisibility(View.VISIBLE);
                        permissionsIcon.setVisibility(View.VISIBLE);

                        switch (accessLevel) {
                            case MegaShare.ACCESS_FULL:
                                logDebug("LEVEL 0 - access FULL");
                                permissionsIcon.setImageResource(R.drawable.ic_shared_fullaccess);
                                break;

                            case MegaShare.ACCESS_READ:
                                logDebug("LEVEL 0 - access read");
                                permissionsIcon.setImageResource(R.drawable.ic_shared_read);
                                break;

                            case MegaShare.ACCESS_READWRITE:
                                logDebug("LEVEL 0 - readwrite");
                                permissionsIcon.setImageResource(R.drawable.ic_shared_read_write);
                                break;
                        }
                    }

                    switch (accessLevel) {
                        case MegaShare.ACCESS_FULL:
                            logDebug("access FULL");
                            counterShares--;
                            optionLink.setVisibility(View.GONE);
                            counterShares--;
                            optionRemoveLink.setVisibility(View.GONE);
                            counterShares--;
                            optionClearShares.setVisibility(View.GONE);
                            optionRename.setVisibility(View.VISIBLE);

                            if (dBT > FIRST_NAVIGATION_LEVEL && !isParentNode) {
                                optionRubbishBin.setVisibility(View.VISIBLE);
                                optionMove.setVisibility(View.VISIBLE);
                            } else {
                                optionRubbishBin.setVisibility(View.GONE);
                                counterModify--;
                                optionMove.setVisibility(View.GONE);
                            }

                            optionLabel.setVisibility(View.VISIBLE);
                            optionFavourite.setVisibility(View.VISIBLE);

                            break;

                        case MegaShare.ACCESS_READ:
                            logDebug("access read");
                            optionLabel.setVisibility(View.GONE);
                            optionFavourite.setVisibility(View.GONE);
                            counterShares--;
                            optionLink.setVisibility(View.GONE);
                            counterShares--;
                            optionRemoveLink.setVisibility(View.GONE);
                            counterModify--;
                            optionRename.setVisibility(View.GONE);
                            counterShares--;
                            optionClearShares.setVisibility(View.GONE);
                            counterModify--;
                            optionMove.setVisibility(View.GONE);
                            optionRubbishBin.setVisibility(View.GONE);
                            break;

                        case MegaShare.ACCESS_READWRITE:
                            logDebug("readwrite");
                            optionLabel.setVisibility(View.GONE);
                            optionFavourite.setVisibility(View.GONE);
                            counterShares--;
                            optionLink.setVisibility(View.GONE);
                            counterShares--;
                            optionRemoveLink.setVisibility(View.GONE);
                            counterModify--;
                            optionRename.setVisibility(View.GONE);
                            counterShares--;
                            optionClearShares.setVisibility(View.GONE);
                            counterModify--;
                            optionMove.setVisibility(View.GONE);
                            optionRubbishBin.setVisibility(View.GONE);
                            break;
                    }
                } else if (tabSelected == 1) {
                    logDebug("showOptionsPanelOutgoing");

                    if (node.isFolder()) {
                        optionShareFolder.setVisibility(View.VISIBLE);
                        optionShareFolder.setText(R.string.manage_share);
                    } else {
                        if (MimeTypeList.typeForName(node.getName()).isOpenableTextFile(node.getSize())) {
                            optionEdit.setVisibility(View.VISIBLE);
                        }

                        counterShares--;
                        optionShareFolder.setVisibility(View.GONE);
                    }

                    if (node.isExported()) {
                        //Node has public link
                        optionLink.setText(R.string.edit_link_option);
                        optionRemoveLink.setVisibility(View.VISIBLE);
                    } else {
                        optionLink.setText(StringResourcesUtils.getQuantityString(R.plurals.get_links, 1));
                        counterShares--;
                        optionRemoveLink.setVisibility(View.GONE);
                    }

                    if (((ManagerActivityLollipop) requireActivity()).getDeepBrowserTreeOutgoing() == FIRST_NAVIGATION_LEVEL) {
                        optionClearShares.setVisibility(View.VISIBLE);

                        //Show the number of contacts who shared the folder
                        ArrayList<MegaShare> sl = megaApi.getOutShares(node);
                        if (sl != null) {
                            if (sl.size() != 0) {
                                nodeInfo.setText(getQuantityString(R.plurals.general_num_shared_with,
                                        sl.size(), sl.size()));
                            }
                        }
                    } else {
                        counterShares--;
                        optionClearShares.setVisibility(View.GONE);
                    }

                    optionDownload.setVisibility(View.VISIBLE);
                    offlineSwitch.setChecked(availableOffline(requireContext(), node));
                    optionInfo.setVisibility(View.VISIBLE);
                    optionRename.setVisibility(View.VISIBLE);
                    counterModify--;
                    optionMove.setVisibility(View.GONE);
                    optionCopy.setVisibility(View.VISIBLE);
                    optionRubbishBin.setVisibility(View.VISIBLE);

                    optionLabel.setVisibility(View.VISIBLE);
                    optionFavourite.setVisibility(View.VISIBLE);

                    //Hide
                    optionRemove.setVisibility(View.GONE);
                    optionLeaveShares.setVisibility(View.GONE);
                    counterOpen--;
                    optionOpenFolder.setVisibility(View.GONE);
                } else if (tabSelected == 2) {
                    if (node.isFolder()) {
                        optionShareFolder.setVisibility(View.VISIBLE);
                        if (isOutShare(node)) {
                            optionShareFolder.setText(R.string.manage_share);
                        } else {
                            optionShareFolder.setText(R.string.context_share_folder);
                        }
                    } else {
                        if (MimeTypeList.typeForName(node.getName()).isOpenableTextFile(node.getSize())) {
                            optionEdit.setVisibility(View.VISIBLE);
                        }

                        counterShares--;
                        optionShareFolder.setVisibility(View.GONE);
                    }

                    if (node.isExported()) {
                        //Node has public link
                        optionLink.setText(R.string.edit_link_option);
                        optionRemoveLink.setVisibility(View.VISIBLE);
                    } else {
                        optionLink.setText(StringResourcesUtils.getQuantityString(R.plurals.get_links, 1));
                        counterShares--;
                        optionRemoveLink.setVisibility(View.GONE);
                    }

                    if (node.isShared()) {
                        optionClearShares.setVisibility(View.VISIBLE);
                    } else {
                        counterShares--;
                        optionClearShares.setVisibility(View.GONE);
                    }

                    offlineSwitch.setChecked(availableOffline(requireContext(), node));
                    optionInfo.setVisibility(View.VISIBLE);
                    optionRubbishBin.setVisibility(View.VISIBLE);
                    optionLink.setVisibility(View.VISIBLE);

                    optionRename.setVisibility(View.VISIBLE);
                    counterModify--;
                    optionMove.setVisibility(View.GONE);
                    optionCopy.setVisibility(View.VISIBLE);

                    optionLabel.setVisibility(View.VISIBLE);
                    optionFavourite.setVisibility(View.VISIBLE);

                    //Hide
                    optionRemove.setVisibility(View.GONE);
                    optionLeaveShares.setVisibility(View.GONE);
                    counterOpen--;
                    optionOpenFolder.setVisibility(View.GONE);
                }

                counterModify--;
                optionRestoreFromRubbish.setVisibility(View.GONE);

                break;

            case MODE5:
                if (megaApi.isInRubbish(node)) {
                    MegaNode restoreNode = megaApi.getNodeByHandle(node.getRestoreHandle());

                    if (!megaApi.isInRubbish(node) || restoreNode == null || megaApi.isInRubbish(restoreNode)) {
                        counterModify--;
                        optionRestoreFromRubbish.setVisibility(View.GONE);
                    } else {
                        optionRestoreFromRubbish.setVisibility(View.VISIBLE);
                    }

                    nodeIconLayout.setVisibility(View.GONE);

                    optionRemove.setVisibility(View.VISIBLE);
                    optionInfo.setVisibility(View.VISIBLE);
                    optionLabel.setVisibility(View.VISIBLE);
                    optionFavourite.setVisibility(View.VISIBLE);

                    //Hide
                    counterOpen--;
                    optionOpenWith.setVisibility(View.GONE);
                    counterModify--;
                    optionMove.setVisibility(View.GONE);
                    counterModify--;
                    optionRename.setVisibility(View.GONE);
                    counterModify--;
                    optionCopy.setVisibility(View.GONE);
                    counterShares--;
                    optionClearShares.setVisibility(View.GONE);
                    optionLeaveShares.setVisibility(View.GONE);
                    optionRubbishBin.setVisibility(View.GONE);
                    counterShares--;
                    optionShare.setVisibility(View.GONE);
                    counterShares--;
                    optionShareFolder.setVisibility(View.GONE);
                    counterShares--;
                    optionLink.setVisibility(View.GONE);
                    counterShares--;
                    optionRemoveLink.setVisibility(View.GONE);
                    counterOpen--;
                    optionOpenFolder.setVisibility(View.GONE);
                    counterSave--;
                    optionDownload.setVisibility(View.GONE);
                    optionGallery.setVisibility(View.GONE);
                    counterSave--;
                    optionOffline.setVisibility(View.GONE);
                    counterShares--;
                    optionSendChat.setVisibility(View.GONE);

                    break;
                }

                if (node.isFolder()) {
                    optionShareFolder.setVisibility(View.VISIBLE);
                } else {
                    if (MimeTypeList.typeForName(node.getName()).isOpenableTextFile(node.getSize())
                            && accessLevel >= MegaShare.ACCESS_READWRITE) {
                        optionEdit.setVisibility(View.VISIBLE);
                    }

                    counterShares--;
                    optionShareFolder.setVisibility(View.GONE);
                }

                int dBT = nC.getIncomingLevel(node);
                if (nC.nodeComesFromIncoming(node)) {
                    logDebug("dBT: " + dBT);
                    if (node.isFolder()) {
                        counterShares--;
                        optionSendChat.setVisibility(View.GONE);
                    } else {
                        optionSendChat.setVisibility(View.VISIBLE);
                    }

                    nodeIconLayout.setVisibility(View.VISIBLE);

                    logDebug("Node: " + node.getName() + " " + accessLevel);
                    optionDownload.setVisibility(View.VISIBLE);
                    offlineSwitch.setChecked(availableOffline(requireContext(), node));
                    optionInfo.setVisibility(View.VISIBLE);
                    optionRemove.setVisibility(View.GONE);
                    counterShares--;
                    optionShareFolder.setVisibility(View.GONE);
                    counterModify--;
                    optionRestoreFromRubbish.setVisibility(View.GONE);

                    logDebug("DeepTree value:" + dBT);
                    if (dBT > FIRST_NAVIGATION_LEVEL) {
                        optionLeaveShares.setVisibility(View.GONE);
                        nodeIconLayout.setVisibility(View.GONE);
                    } else {
                        //Show the owner of the shared folder
                        showOwnerSharedFolder();
                        optionLeaveShares.setVisibility(View.VISIBLE);

                        switch (accessLevel) {
                            case MegaShare.ACCESS_FULL:
                                logDebug("LEVEL 0 - access FULL");
                                nodeIcon.setImageResource(R.drawable.ic_shared_fullaccess);
                                break;

                            case MegaShare.ACCESS_READ:
                                logDebug("LEVEL 0 - access read");
                                nodeIcon.setImageResource(R.drawable.ic_shared_read);
                                break;

                            case MegaShare.ACCESS_READWRITE:
                                logDebug("LEVEL 0 - readwrite");
                                nodeIcon.setImageResource(R.drawable.ic_shared_read_write);
                                break;
                        }
                    }

                    switch (accessLevel) {
                        case MegaShare.ACCESS_FULL:
                            logDebug("access FULL");
                            counterShares--;
                            optionLink.setVisibility(View.GONE);
                            counterShares--;
                            optionRemoveLink.setVisibility(View.GONE);
                            counterShares--;
                            optionClearShares.setVisibility(View.GONE);
                            optionRename.setVisibility(View.VISIBLE);

                            if (dBT > FIRST_NAVIGATION_LEVEL) {
                                optionRubbishBin.setVisibility(View.VISIBLE);
                                optionMove.setVisibility(View.VISIBLE);

                            } else {
                                optionRubbishBin.setVisibility(View.GONE);
                                counterModify--;
                                optionMove.setVisibility(View.GONE);

                            }

                            optionLabel.setVisibility(View.VISIBLE);
                            optionFavourite.setVisibility(View.VISIBLE);

                            break;

                        case MegaShare.ACCESS_READ:
                            logDebug("access read");
                            optionLabel.setVisibility(View.GONE);
                            optionFavourite.setVisibility(View.GONE);
                            counterShares--;
                            optionLink.setVisibility(View.GONE);
                            counterShares--;
                            optionRemoveLink.setVisibility(View.GONE);
                            counterModify--;
                            optionRename.setVisibility(View.GONE);
                            counterShares--;
                            optionClearShares.setVisibility(View.GONE);
                            counterModify--;
                            optionMove.setVisibility(View.GONE);
                            optionRubbishBin.setVisibility(View.GONE);
                            break;

                        case MegaShare.ACCESS_READWRITE:
                            logDebug("readwrite");
                            optionLabel.setVisibility(View.GONE);
                            optionFavourite.setVisibility(View.GONE);
                            counterShares--;
                            optionLink.setVisibility(View.GONE);
                            counterShares--;
                            optionRemoveLink.setVisibility(View.GONE);
                            counterModify--;
                            optionRename.setVisibility(View.GONE);
                            counterShares--;
                            optionClearShares.setVisibility(View.GONE);
                            counterModify--;
                            optionMove.setVisibility(View.GONE);
                            optionRubbishBin.setVisibility(View.GONE);
                            break;
                    }
                } else {
                    if (node.isExported()) {
                        //Node has public link
                        optionLink.setText(R.string.edit_link_option);
                        optionRemoveLink.setVisibility(View.VISIBLE);
                    } else {
                        optionLink.setText(StringResourcesUtils.getQuantityString(R.plurals.get_links, 1));
                        counterShares--;
                        optionRemoveLink.setVisibility(View.GONE);
                    }

                    MegaNode parent = nC.getParent(node);
                    if (parent.getHandle() != megaApi.getRubbishNode().getHandle()) {
                        optionRubbishBin.setVisibility(View.VISIBLE);
                        optionRemove.setVisibility(View.GONE);
                    } else {
                        optionRubbishBin.setVisibility(View.GONE);
                        optionRemove.setVisibility(View.VISIBLE);
                    }

                    optionDownload.setVisibility(View.VISIBLE);

                    offlineSwitch.setChecked(availableOffline(requireContext(), node));
                    optionInfo.setVisibility(View.VISIBLE);
                    optionLink.setVisibility(View.VISIBLE);
                    optionRename.setVisibility(View.VISIBLE);
                    optionOpenFolder.setVisibility(View.VISIBLE);
                    optionLabel.setVisibility(View.VISIBLE);
                    optionFavourite.setVisibility(View.VISIBLE);

                    //Hide
                    counterModify--;
                    optionMove.setVisibility(View.GONE);
                    counterModify--;
                    optionCopy.setVisibility(View.GONE);
                    counterShares--;
                    optionClearShares.setVisibility(View.GONE);
                    optionLeaveShares.setVisibility(View.GONE);
                    counterModify--;
                    optionRestoreFromRubbish.setVisibility(View.GONE);
                }
                break;
            case MODE6:
                if (MimeTypeList.typeForName(node.getName()).isOpenableTextFile(node.getSize())
                        && accessLevel >= MegaShare.ACCESS_READWRITE) {
                    optionEdit.setVisibility(View.VISIBLE);
                }

                counterShares--;
                optionShareFolder.setVisibility(View.GONE);
                nodeIconLayout.setVisibility(View.GONE);
                counterShares--;
                optionLink.setVisibility(View.GONE);
                counterShares--;
                optionRemoveLink.setVisibility(View.GONE);
                counterShares--;
                optionClearShares.setVisibility(View.GONE);
                offlineSwitch.setChecked(availableOffline(requireContext(), node));

                optionSendChat.setVisibility(View.VISIBLE);

                optionRemove.setVisibility(View.GONE);
                optionLeaveShares.setVisibility(View.GONE);
                counterOpen--;
                optionOpenFolder.setVisibility(View.GONE);
                counterModify--;
                optionRestoreFromRubbish.setVisibility(View.GONE);

                switch (accessLevel) {
                    case MegaShare.ACCESS_READWRITE:
                    case MegaShare.ACCESS_READ:
                    case MegaShare.ACCESS_UNKNOWN:
                        optionLabel.setVisibility(View.GONE);
                        optionFavourite.setVisibility(View.GONE);
                        counterModify--;
                        optionRename.setVisibility(View.GONE);
                        counterModify--;
                        optionMove.setVisibility(View.GONE);
                        optionRubbishBin.setVisibility(View.GONE);
                        counterShares--;
                        optionLink.setVisibility(View.GONE);
                        nodeIconLayout.setVisibility(View.GONE);
                        optionLink.setText(StringResourcesUtils.getQuantityString(R.plurals.get_links, 1));
                        counterShares--;
                        optionRemoveLink.setVisibility(View.GONE);
                        break;

                    case MegaShare.ACCESS_FULL:
                    case MegaShare.ACCESS_OWNER:
                        optionLabel.setVisibility(View.VISIBLE);
                        optionFavourite.setVisibility(View.VISIBLE);
                        optionLink.setVisibility(View.VISIBLE);

                        if (node.isExported()) {
                            optionLink.setText(R.string.edit_link_option);
                            optionRemoveLink.setVisibility(View.VISIBLE);
                        } else {
                            optionLink.setText(StringResourcesUtils.getQuantityString(R.plurals.get_links, 1));
                            counterShares--;
                            optionRemoveLink.setVisibility(View.GONE);
                        }
                        break;
                }
                break;
        }

        separatorOpen.setVisibility(counterOpen <= 0 ? View.GONE : View.VISIBLE);
        separatorDownload.setVisibility(counterSave <= 0 ? View.GONE : View.VISIBLE);
        separatorShares.setVisibility(counterShares <= 0 ? View.GONE : View.VISIBLE);
        separatorModify.setVisibility(counterModify <= 0 ? View.GONE : View.VISIBLE);

        offlineSwitch.setOnCheckedChangeListener((v, isChecked) -> onClick(v));

        optionFavourite.setText(node.isFavourite() ? R.string.file_properties_unfavourite : R.string.file_properties_favourite);
        optionFavourite.setCompoundDrawablesWithIntrinsicBounds(node.isFavourite()
                        ? R.drawable.ic_remove_favourite
                        : R.drawable.ic_add_favourite,
                0, 0, 0);

        if (node.getLabel() != MegaNode.NODE_LBL_UNKNOWN) {
            int color = ResourcesCompat.getColor(getResources(), getNodeLabelColor(node.getLabel()), null);
            Drawable drawable = MegaNodeUtil.getNodeLabelDrawable(node.getLabel(), getResources());
            optionLabelCurrent.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, drawable, null);
            optionLabelCurrent.setText(MegaNodeUtil.getNodeLabelText(node.getLabel()));
            optionLabelCurrent.setTextColor(color);
            optionLabelCurrent.setVisibility(View.VISIBLE);
        } else {
            optionLabelCurrent.setVisibility(View.GONE);
        }

        super.onViewCreated(view, savedInstanceState);
    }

    private void showOwnerSharedFolder() {
        ArrayList<MegaShare> sharesIncoming = megaApi.getInSharesList();
        for (int j = 0; j < sharesIncoming.size(); j++) {
            MegaShare mS = sharesIncoming.get(j);
            if (mS.getNodeHandle() == node.getHandle()) {
                MegaUser user = megaApi.getContact(mS.getUser());
                if (user != null) {
                    nodeInfo.setText(getMegaUserNameDB(user));
                } else {
                    nodeInfo.setText(mS.getUser());
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (node == null) {
            logWarning("The selected node is NULL");
            return;
        }

        ArrayList<Long> handleList = new ArrayList<>();
        handleList.add(node.getHandle());

        Intent i;

        switch (v.getId()) {
            case R.id.download_option:
                ((ManagerActivityLollipop) requireActivity()).saveNodesToDevice(
                        Collections.singletonList(node), false, false, false, false);
                break;

            case R.id.favorite_option:
                megaApi.setNodeFavourite(node, !node.isFavourite());
                break;

            case R.id.option_label_layout:
                ((ManagerActivityLollipop) requireActivity()).showNodeLabelsPanel(node);
                break;

            case R.id.gallery_option:
                ((ManagerActivityLollipop) requireActivity()).saveNodesToGallery(Collections.singletonList(node));
                break;

            case R.id.file_properties_switch:
            case R.id.option_offline_layout:
                if (availableOffline(requireContext(), node)) {
                    MegaOffline mOffDelete = dbH.findByHandle(node.getHandle());
                    removeFromOffline(mOffDelete);
                } else {
                    saveForOffline();
                }
                break;

            case R.id.properties_option:
                i = new Intent(requireContext(), FileInfoActivityLollipop.class);
                i.putExtra(HANDLE, node.getHandle());

                if (drawerItem == ManagerActivityLollipop.DrawerItem.SHARED_ITEMS) {
                    if (((ManagerActivityLollipop) requireActivity()).getTabItemShares() == 0) {
                        i.putExtra("from", FROM_INCOMING_SHARES);
                        i.putExtra(INTENT_EXTRA_KEY_FIRST_LEVEL,
                                ((ManagerActivityLollipop) requireActivity()).getDeepBrowserTreeIncoming() <= FIRST_NAVIGATION_LEVEL);
                    } else if (((ManagerActivityLollipop) requireActivity()).getTabItemShares() == 1) {
                        i.putExtra("adapterType", OUTGOING_SHARES_ADAPTER);
                    }
                } else if (drawerItem == ManagerActivityLollipop.DrawerItem.INBOX) {
                    if (((ManagerActivityLollipop) requireActivity()).getTabItemShares() == 0) {
                        i.putExtra("from", FROM_INBOX);
                    }
                } else if (drawerItem == ManagerActivityLollipop.DrawerItem.SEARCH) {
                    if (nC.nodeComesFromIncoming(node)) {
                        i.putExtra("from", FROM_INCOMING_SHARES);
                        int dBT = nC.getIncomingLevel(node);
                        i.putExtra(INTENT_EXTRA_KEY_FIRST_LEVEL, dBT <= FIRST_NAVIGATION_LEVEL);
                    }
                }
                i.putExtra(NAME, node.getName());

                startActivityForResult(i, REQUEST_CODE_FILE_INFO);
                dismissAllowingStateLoss();
                break;

            case R.id.link_option:
                ((ManagerActivityLollipop) requireActivity()).showGetLinkActivity(node.getHandle());
                break;

            case R.id.view_in_folder_option:
                ((ManagerActivityLollipop) requireActivity()).viewNodeInFolder(node);
                break;

            case R.id.remove_link_option:
                ((ManagerActivityLollipop) requireActivity()).showConfirmationRemovePublicLink(node);
                break;

            case R.id.share_folder_option:
                if (isOutShare(node)) {
                    i = new Intent(requireContext(), FileContactListActivityLollipop.class);
                    i.putExtra(NAME, node.getHandle());
                    startActivity(i);
                } else {
                    nC.selectContactToShareFolder(node);
                }

                dismissAllowingStateLoss();
                break;

            case R.id.clear_share_option:
                ArrayList<MegaShare> shareList = megaApi.getOutShares(node);
                ((ManagerActivityLollipop) requireActivity()).showConfirmationRemoveAllSharingContacts(shareList, node);
                break;

            case R.id.leave_share_option:
                showConfirmationLeaveIncomingShare(requireActivity(),
                        (SnackbarShower) requireActivity(), node);
                break;

            case R.id.send_chat_option:
                ((ManagerActivityLollipop) requireActivity()).attachNodeToChats(node);
                dismissAllowingStateLoss();
                break;

            case R.id.rename_option:
                ((ManagerActivityLollipop) requireActivity()).showRenameDialog(node);

                break;

            case R.id.move_option:
                nC.chooseLocationToMoveNodes(handleList);
                dismissAllowingStateLoss();
                break;

            case R.id.copy_option:
                nC.chooseLocationToCopyNodes(handleList);
                dismissAllowingStateLoss();
                break;

            case R.id.rubbish_bin_option:
            case R.id.remove_option:
                ((ManagerActivityLollipop) requireActivity()).askConfirmationMoveToRubbish(handleList);
                break;

            case R.id.open_folder_option:
                nC.openFolderFromSearch(node.getHandle());
                dismissAllowingStateLoss();
                break;

            case R.id.open_with_option:
                openWith(requireActivity(), node);
                break;

            case R.id.restore_option:
                List<MegaNode> nodes = new ArrayList<>();
                nodes.add(node);
                ((ManagerActivityLollipop) requireActivity()).restoreFromRubbish(nodes);
                break;

            case R.id.share_option:
                shareNode(requireActivity(), node);
                break;

            case R.id.edit_file_option:
                manageEditTextFileIntent(requireContext(), node, getAdapterType());
                break;
        }

        setStateBottomSheetBehaviorHidden();
    }

    private void refreshView() {
        switch (drawerItem) {
            case CLOUD_DRIVE:
            case RUBBISH_BIN:
                ((ManagerActivityLollipop) requireActivity()).onNodesCloudDriveUpdate();
                break;

            case INBOX:
                ((ManagerActivityLollipop) requireActivity()).onNodesInboxUpdate();
                break;

            case SHARED_ITEMS:
                ((ManagerActivityLollipop) requireActivity()).onNodesSharedUpdate();
                break;

            case SEARCH:
                ((ManagerActivityLollipop) requireActivity()).onNodesSearchUpdate();
                break;

            case HOMEPAGE:
                LiveEventBus.get(EVENT_NODES_CHANGE).post(false);
                break;
        }
    }

    private void removeFromOffline(MegaOffline mOffDelete) {
        removeOffline(mOffDelete, dbH, requireContext());
        refreshView();
    }

    private void saveForOffline() {
        int adapterType;

        switch (drawerItem) {
            case INBOX:
                adapterType = FROM_INBOX;
                break;

            case SHARED_ITEMS:
                if (((ManagerActivityLollipop) requireActivity()).getTabItemShares() == 0) {
                    adapterType = FROM_INCOMING_SHARES;
                    break;
                }

            default:
                adapterType = FROM_OTHERS;
        }

        File offlineParent = getOfflineParentFile(requireActivity(), adapterType, node, megaApi);

        if (isFileAvailable(offlineParent)) {
            File offlineFile = new File(offlineParent, node.getName());

            if (isFileAvailable(offlineFile)) {
                if (isFileDownloadedLatest(offlineFile, node)
                        && offlineFile.length() == node.getSize()) {
                    // if the file matches to the latest on the cloud, do nothing
                    return;
                } else {
                    // if the file does not match the latest on the cloud, delete the old file offline database record
                    String parentName = getOfflineParentFileName(requireContext(), node).getAbsolutePath() + File.separator;
                    MegaOffline mOffDelete = dbH.findbyPathAndName(parentName, node.getName());
                    removeFromOffline(mOffDelete);
                }
            }
        }

        // Save the new file to offline
        saveOffline(offlineParent, node, requireActivity());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        long handle = node.getHandle();
        outState.putLong(HANDLE, handle);
        outState.putInt(SAVED_STATE_KEY_MODE, mMode);
    }

    private void mapDrawerItemToMode(ManagerActivityLollipop.DrawerItem drawerItem) {
        switch (drawerItem) {
            case CLOUD_DRIVE:
                mMode = MODE1;
                break;
            case RUBBISH_BIN:
                mMode = MODE2;
                break;
            case INBOX:
                mMode = MODE3;
                break;
            case SHARED_ITEMS:
                mMode = MODE4;
                break;
            case SEARCH:
                mMode = MODE5;
                break;
        }
    }

    private int getAdapterType() {
        switch (mMode) {
            case MODE1:
                return FILE_BROWSER_ADAPTER;

            case MODE2:
                return RUBBISH_BIN_ADAPTER;

            case MODE3:
                return INBOX_ADAPTER;

            case MODE4:
                switch (((ManagerActivityLollipop) requireActivity()).getTabItemShares()) {
                    case INCOMING_TAB:
                        return INCOMING_SHARES_ADAPTER;
                    case OUTGOING_TAB:
                        return OUTGOING_SHARES_ADAPTER;
                    case LINKS_TAB:
                        return LINKS_ADAPTER;
                }

            case MODE5:
                return SEARCH_ADAPTER;

            case MODE6:
                return RECENTS_ADAPTER;

            default:
                return INVALID_VALUE;
        }
    }
}
