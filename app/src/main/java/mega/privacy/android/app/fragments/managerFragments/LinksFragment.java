package mega.privacy.android.app.fragments.managerFragments;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import mega.privacy.android.app.R;
import mega.privacy.android.app.fragments.MegaNodeBaseFragment;
import mega.privacy.android.app.lollipop.adapters.MegaNodeAdapter;
import mega.privacy.android.app.utils.CloudStorageOptionControlUtil;
import mega.privacy.android.app.utils.ColorUtils;
import mega.privacy.android.app.utils.MegaNodeUtil;
import nz.mega.sdk.MegaNode;

import static mega.privacy.android.app.lollipop.ManagerActivityLollipop.LINKS_TAB;
import static mega.privacy.android.app.lollipop.adapters.MegaNodeAdapter.*;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.MegaNodeUtil.areAllFileNodes;
import static nz.mega.sdk.MegaApiJava.*;
import static nz.mega.sdk.MegaError.API_OK;
import static nz.mega.sdk.MegaShare.ACCESS_FULL;

public class LinksFragment extends MegaNodeBaseFragment {

    public static LinksFragment newInstance() {
        return new LinksFragment();
    }

    @Override
    public void activateActionMode() {
        if (!adapter.isMultipleSelect()) {
            super.activateActionMode();

            if (getActivity() != null) {
                actionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(new ActionBarCallBack(LINKS_TAB));
            }
        }
    }

    @Override
    protected int viewerFrom() {
        return VIEWER_FROM_LINKS;
    }

    private class ActionBarCallBack extends BaseActionBarCallBack {

        public ActionBarCallBack(int currentTab) {
            super(currentTab);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            super.onPrepareActionMode(mode, menu);

            CloudStorageOptionControlUtil.Control control =
                new CloudStorageOptionControlUtil.Control();

            if (selected.size() == 1) {
                control.manageLink().setVisible(true)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

                control.removeLink().setVisible(true);
            } else {
                control.removeLink().setVisible(true)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            control.shareOut().setVisible(true)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            if (areAllFileNodes(selected)) {
                control.sendToChat().setVisible(true)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            if (selected.size() == 1
                && megaApi.checkAccess(selected.get(0), ACCESS_FULL).getErrorCode() == API_OK) {
                control.rename().setVisible(true);
                if (control.alwaysActionCount() < CloudStorageOptionControlUtil.MAX_ACTION_COUNT) {
                    control.rename().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                } else {
                    control.rename().setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                }
            }

            control.copy().setVisible(true);
            if (control.alwaysActionCount() < CloudStorageOptionControlUtil.MAX_ACTION_COUNT) {
                control.copy().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            } else {
                control.copy().setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }

            control.selectAll().setVisible(notAllNodesSelected());
            control.trash().setVisible(MegaNodeUtil.canMoveToRubbish(selected));

            CloudStorageOptionControlUtil.applyControl(menu, control);

            return true;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        if (megaApi.getRootNode() == null) {
            return null;
        }

        View v = getListView(inflater, container);

        if (adapter == null) {
            adapter = new MegaNodeAdapter(context, this, nodes,
                    managerActivity.getParentHandleLinks(), recyclerView, LINKS_ADAPTER,
                    ITEM_VIEW_TYPE_LIST, sortByHeaderViewModel);
        }

        adapter.setListFragment(recyclerView);

        if (managerActivity.getParentHandleLinks() == INVALID_HANDLE) {
            logWarning("ParentHandle -1");
            findNodes();
            adapter.setParentHandle(INVALID_HANDLE);
        } else {
            managerActivity.hideTabs(true, LINKS_TAB);
            MegaNode parentNode = megaApi.getNodeByHandle(managerActivity.getParentHandleLinks());
            logDebug("ParentHandle to find children: " + managerActivity.getParentHandleLinks());

            nodes = megaApi.getChildren(parentNode, getLinksOrderCloud(
                    sortOrderManagement.getOrderCloud(), managerActivity.isFirstNavigationLevel()));

            adapter.setNodes(nodes);
        }

        adapter.setMultipleSelect(false);
        recyclerView.setAdapter(adapter);

        return v;
    }

    private void findNodes() {
        setNodes(megaApi.getPublicLinks(getLinksOrderCloud(
                sortOrderManagement.getOrderCloud(), managerActivity.isFirstNavigationLevel())));
    }

    @Override
    public void setNodes(ArrayList<MegaNode> nodes) {
        this.nodes = nodes;
        adapter.setNodes(nodes);
        setEmptyView();
        visibilityFastScroller();
    }

    @Override
    protected void setEmptyView() {
        String textToShow = null;

        if (megaApi.getRootNode().getHandle() == managerActivity.getParentHandleOutgoing()
                || managerActivity.getParentHandleOutgoing() == -1) {
            ColorUtils.setImageViewAlphaIfDark(context, emptyImageView, ColorUtils.DARK_IMAGE_ALPHA);
            emptyImageView.setImageResource(R.drawable.ic_zero_data_public_links);
            textToShow = context.getString(R.string.context_empty_links);
        }

        setFinalEmptyView(textToShow);
    }

    @Override
    public int onBackPressed() {
        if (adapter == null
                || managerActivity.getParentHandleLinks() == INVALID_HANDLE
                || managerActivity.getDeepBrowserTreeLinks() <= 0) {
            return 0;
        }

        managerActivity.decreaseDeepBrowserTreeLinks();

        if (managerActivity.getDeepBrowserTreeLinks() == 0) {
            managerActivity.setParentHandleLinks(INVALID_HANDLE);
            managerActivity.hideTabs(false, LINKS_TAB);
            findNodes();
        } else if (managerActivity.getDeepBrowserTreeLinks() > 0) {
            MegaNode parentNodeLinks = megaApi.getNodeByHandle(managerActivity.getParentHandleLinks());
            if (parentNodeLinks != null) {
                parentNodeLinks = megaApi.getParentNode(parentNodeLinks);
                if (parentNodeLinks != null) {
                    managerActivity.setParentHandleLinks(parentNodeLinks.getHandle());
                    setNodes(megaApi.getChildren(parentNodeLinks, getLinksOrderCloud(
                            sortOrderManagement.getOrderCloud(), managerActivity.isFirstNavigationLevel())));
                }
            }
        } else {
            managerActivity.setDeepBrowserTreeLinks(0);
        }

        int lastVisiblePosition = 0;
        if (!lastPositionStack.empty()) {
            lastVisiblePosition = lastPositionStack.pop();
        }
        if (lastVisiblePosition >= 0) {
            mLayoutManager.scrollToPositionWithOffset(lastVisiblePosition, 0);
        }

        managerActivity.showFabButton();
        managerActivity.setToolbarTitle();
        managerActivity.supportInvalidateOptionsMenu();

        return 1;
    }

    @Override
    public void itemClick(int position) {
        if (adapter.isMultipleSelect()) {
            logDebug("multiselect ON");
            adapter.toggleSelection(position);

            List<MegaNode> selectedNodes = adapter.getSelectedNodes();
            if (selectedNodes.size() > 0) {
                updateActionModeTitle();
            }
        } else if (nodes.get(position).isFolder()) {
            lastPositionStack.push(mLayoutManager.findFirstCompletelyVisibleItemPosition());
            managerActivity.hideTabs(true, LINKS_TAB);
            managerActivity.increaseDeepBrowserTreeLinks();
            managerActivity.setParentHandleLinks(nodes.get(position).getHandle());
            managerActivity.supportInvalidateOptionsMenu();
            managerActivity.setToolbarTitle();

            setNodes(megaApi.getChildren(nodes.get(position), getLinksOrderCloud(
                    sortOrderManagement.getOrderCloud(), managerActivity.isFirstNavigationLevel())));
            recyclerView.scrollToPosition(0);
            checkScroll();
            managerActivity.showFabButton();
        } else {
            openFile(nodes.get(position), LINKS_ADAPTER, position);
        }
    }

    @Override
    public void refresh() {
        hideActionMode();

        if (managerActivity.getParentHandleLinks() == INVALID_HANDLE
                || megaApi.getNodeByHandle(managerActivity.getParentHandleLinks()) == null) {
            findNodes();
        } else {
            MegaNode parentNodeLinks = megaApi.getNodeByHandle(managerActivity.getParentHandleLinks());
            setNodes(megaApi.getChildren(parentNodeLinks, getLinksOrderCloud(
                    sortOrderManagement.getOrderCloud(), managerActivity.isFirstNavigationLevel())));
        }
    }

    public static int getLinksOrderCloud(int orderCloud, boolean isFirstNavigationLevel) {
        if (!isFirstNavigationLevel) {
            return orderCloud;
        }

        switch (orderCloud) {
            case ORDER_MODIFICATION_ASC:
                return ORDER_LINK_CREATION_ASC;

            case ORDER_MODIFICATION_DESC:
                return ORDER_LINK_CREATION_DESC;

            default:
                return orderCloud;
        }
    }
}
