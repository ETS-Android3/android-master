package mega.privacy.android.app.lollipop;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.text.HtmlCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Looper;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;
import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.MegaPreferences;
import mega.privacy.android.app.R;
import mega.privacy.android.app.components.CustomizedGridLayoutManager;
import mega.privacy.android.app.components.PositionDividerItemDecoration;
import mega.privacy.android.app.components.scrollBar.FastScroller;
import mega.privacy.android.app.fragments.homepage.EventObserver;
import mega.privacy.android.app.fragments.homepage.SortByHeaderViewModel;
import mega.privacy.android.app.search.callback.SearchActionsCallback;
import mega.privacy.android.app.search.usecase.SearchNodesUseCase;
import mega.privacy.android.app.globalmanagement.SortOrderManagement;
import mega.privacy.android.app.lollipop.adapters.MegaExplorerLollipopAdapter;
import mega.privacy.android.app.lollipop.adapters.RotatableAdapter;
import mega.privacy.android.app.lollipop.managerSections.RotatableFragment;
import mega.privacy.android.app.utils.ColorUtils;
import mega.privacy.android.app.utils.StringResourcesUtils;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaApiJava;
import nz.mega.sdk.MegaCancelToken;
import nz.mega.sdk.MegaNode;

import static mega.privacy.android.app.lollipop.FileExplorerActivityLollipop.CLOUD_FRAGMENT;
import static mega.privacy.android.app.search.usecase.SearchNodesUseCase.TYPE_CLOUD_EXPLORER;
import static mega.privacy.android.app.utils.Constants.SCROLLING_UP_DIRECTION;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.TextUtil.formatEmptyScreenText;
import static mega.privacy.android.app.utils.Util.*;
import static nz.mega.sdk.MegaApiJava.INVALID_HANDLE;

@AndroidEntryPoint
public class CloudDriveExplorerFragmentLollipop extends RotatableFragment implements
		OnClickListener, CheckScrollInterface, SearchActionsCallback {

	@Inject
	SortOrderManagement sortOrderManagement;
	@Inject
	SearchNodesUseCase searchNodesUseCase;

	private Context context;
	private MegaApiAndroid megaApi;
	private ArrayList<MegaNode> nodes;
	private ArrayList<MegaNode> searchNodes;
	private DisplayMetrics metrics;

	private long parentHandle = -1;

	private MegaExplorerLollipopAdapter adapter;
    private FastScroller fastScroller;

	private int modeCloud;
	private boolean selectFile=false;
	private MegaPreferences prefs;
	private DatabaseHandler dbH;
	private ActionMode actionMode;

	private RelativeLayout contentLayout;
	private LinearLayout optionsBar;
	private RecyclerView recyclerView;
	private LinearLayoutManager mLayoutManager;
	private CustomizedGridLayoutManager gridLayoutManager;

	private ImageView emptyImageView;
	private LinearLayout emptyTextView;
	private TextView emptyTextViewFirst;

	private Button optionButton;
	private Button cancelButton;
	private FloatingActionButton fabSelect;

	private ArrayList<Long> nodeHandleMoveCopy;

	private Stack<Integer> lastPositionStack;

	private Handler handler;

	private int order;

	private MegaCancelToken searchCancelToken;
	private ProgressBar searchProgressBar;
	private boolean shouldResetNodes = true;

	private Spanned emptyRootText;
	private Spanned emptyGeneralText;

	@Override
	protected RotatableAdapter getAdapter() {
		return adapter;
	}

	public void activateActionMode(){
		logDebug("activateActionMode");

		if (!adapter.isMultipleSelect()){
			adapter.setMultipleSelect(true);
			actionMode = ((AppCompatActivity)context).startSupportActionMode(new ActionBarCallBack());

			if(isMultiselect()) {
				activateButton(true);
			}
		}
	}

	@Override
	public void multipleItemClick(int position) {
		adapter.toggleSelection(position);
	}

	@Override
	public void reselectUnHandledSingleItem(int position) {
	}

	private class ActionBarCallBack implements ActionMode.Callback {

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			logDebug("onActionItemClicked");
			List<MegaNode> documents = adapter.getSelectedNodes();

			switch(item.getItemId()){

				case R.id.cab_menu_select_all:{
					selectAll();
					break;
				}
				case R.id.cab_menu_unselect_all:{
					clearSelections();
					hideMultipleSelect();
					break;
				}
			}
			return false;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			logDebug("onCreateActionMode");
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.file_explorer_multiaction, menu);
			((FileExplorerActivityLollipop) context).hideTabs(true, CLOUD_FRAGMENT);
			checkScroll();
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode arg0) {
			if (!((FileExplorerActivityLollipop) context).shouldReopenSearch()) {
				((FileExplorerActivityLollipop) context).hideTabs(false, CLOUD_FRAGMENT);
				((FileExplorerActivityLollipop) context).clearQuerySearch();
				getNodes();
				setNodes(nodes);
			}
			clearSelections();
			adapter.setMultipleSelect(false);
			checkScroll();
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			logDebug("onPrepareActionMode");
			List<MegaNode> selected = adapter.getSelectedNodes();

			if (selected.size() != 0) {
				MenuItem unselect = menu.findItem(R.id.cab_menu_unselect_all);
				MegaNode nodeS = megaApi.getNodeByHandle(parentHandle);

				if(selected.size() == megaApi.getNumChildFiles(nodeS)){
					menu.findItem(R.id.cab_menu_select_all).setVisible(false);
					unselect.setTitle(getString(R.string.action_unselect_all));
					unselect.setVisible(true);

				}else{
					if(modeCloud==FileExplorerActivityLollipop.SELECT){
						if(selectFile){
							if(((FileExplorerActivityLollipop)context).isMultiselect()){
								MegaNode node = megaApi.getNodeByHandle(parentHandle);
								if(selected.size() == megaApi.getNumChildFiles(node)){
									menu.findItem(R.id.cab_menu_select_all).setVisible(false);
								}else{
									menu.findItem(R.id.cab_menu_select_all).setVisible(true);
								}
							}
						}
					}else{
						menu.findItem(R.id.cab_menu_select_all).setVisible(true);
					}

					unselect.setTitle(getString(R.string.action_unselect_all));
					unselect.setVisible(true);

//					menu.findItem(R.id.cab_menu_select_all).setVisible(true);
//					unselect.setTitle(getString(R.string.action_unselect_all));
//					unselect.setVisible(true);

				}
			}
			else{
				menu.findItem(R.id.cab_menu_select_all).setVisible(true);
				menu.findItem(R.id.cab_menu_unselect_all).setVisible(false);
			}

			return false;
		}
	}


	public static CloudDriveExplorerFragmentLollipop newInstance() {
		logDebug("newInstance");
		CloudDriveExplorerFragmentLollipop fragment = new CloudDriveExplorerFragmentLollipop();
		return fragment;
	}

	@Override
	public void onCreate (Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		logDebug("onCreate");

		if (megaApi == null){
			megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
		}
		if (megaApi.getRootNode() == null){
			return;
		}

		parentHandle = -1;
		dbH = DatabaseHandler.getDbHandler(context);
		lastPositionStack = new Stack<>();
		handler = new Handler();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		handler.removeCallbacksAndMessages(null);
	}

	@Override
	public void checkScroll () {
		if (recyclerView == null) {
			return;
		}

		((FileExplorerActivityLollipop) context).changeActionBarElevation(
				recyclerView.canScrollVertically(SCROLLING_UP_DIRECTION)
						|| (adapter != null && adapter.isMultipleSelect()),
				CLOUD_FRAGMENT);
	}

	/**
	 * Shows the Sort by panel.
	 *
	 * @param unit Unit event.
	 * @return Null.
	 */
	private Unit showSortByPanel(Unit unit) {
		((FileExplorerActivityLollipop) context).showSortByPanel();
		return null;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
		logDebug("onCreateView");

		SortByHeaderViewModel sortByHeaderViewModel = new ViewModelProvider(this)
				.get(SortByHeaderViewModel.class);

		sortByHeaderViewModel.getShowDialogEvent().observe(getViewLifecycleOwner(),
				new EventObserver<>(this::showSortByPanel));

		View v = inflater.inflate(R.layout.fragment_fileexplorerlist, container, false);
		Display display = getActivity().getWindowManager().getDefaultDisplay();

		metrics = new DisplayMetrics();
		display.getMetrics(metrics);

		contentLayout = v.findViewById(R.id.content_layout);
		searchProgressBar = v.findViewById(R.id.progressbar);

		optionsBar = v.findViewById(R.id.options_explorer_layout);
		optionButton = v.findViewById(R.id.action_text);
		optionButton.setOnClickListener(this);

		cancelButton = v.findViewById(R.id.cancel_text);
		cancelButton.setOnClickListener(this);
		cancelButton.setText(StringResourcesUtils.getString(R.string.general_cancel));
		fabSelect = v.findViewById(R.id.fab_select);
		fabSelect.setOnClickListener(this);

        fastScroller = v.findViewById(R.id.fastscroll);
		if (((FileExplorerActivityLollipop) context).isList()) {
			recyclerView = v.findViewById(R.id.file_list_view_browser);
			v.findViewById(R.id.file_grid_view_browser).setVisibility(View.GONE);
			mLayoutManager = new LinearLayoutManager(context);
			recyclerView.setLayoutManager(mLayoutManager);
			recyclerView.addItemDecoration(new PositionDividerItemDecoration(requireContext(), getOutMetrics()));
		}
		else {
			recyclerView = v.findViewById(R.id.file_grid_view_browser);
			v.findViewById(R.id.file_list_view_browser).setVisibility(View.GONE);
			gridLayoutManager = (CustomizedGridLayoutManager) recyclerView.getLayoutManager();
		}

		recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);
				checkScroll();
			}
		});

		emptyImageView = v.findViewById(R.id.file_list_empty_image);
		emptyTextView = v.findViewById(R.id.file_list_empty_text);
		emptyTextViewFirst = v.findViewById(R.id.file_list_empty_text_first);

		modeCloud = ((FileExplorerActivityLollipop)context).getMode();
		selectFile = ((FileExplorerActivityLollipop)context).isSelectFile();

		parentHandle = ((FileExplorerActivityLollipop)context).getParentHandleCloud();

		if (parentHandle != INVALID_HANDLE && megaApi.getRootNode() != null
				&& parentHandle != megaApi.getRootNode().getHandle()) {
			((FileExplorerActivityLollipop) context).hideTabs(true, CLOUD_FRAGMENT);
		}

		if (modeCloud == FileExplorerActivityLollipop.SELECT_CAMERA_FOLDER) {
			setParentHandle(-1);
		} else if (parentHandle == -1) {
			setParentHandle(megaApi.getRootNode().getHandle());
		}

		MegaPreferences prefs = getPreferences(context);
		order = prefs != null && prefs.getPreferredSortCloud() != null
				? Integer.parseInt(prefs.getPreferredSortCloud())
				: MegaApiJava.ORDER_DEFAULT_ASC;

		getNodes();
		setParentHandle(parentHandle);

		switch (modeCloud) {
			case FileExplorerActivityLollipop.MOVE:
				optionButton.setText(StringResourcesUtils.getString(R.string.context_move));

				MegaNode parentMove= ((FileExplorerActivityLollipop) context).parentMoveCopy();
				activateButton(parentMove == null || parentMove.getHandle() != parentHandle);
				break;

			case FileExplorerActivityLollipop.COPY:
				optionButton.setText(StringResourcesUtils.getString(R.string.context_copy));

				MegaNode parentCopy = ((FileExplorerActivityLollipop) context).parentMoveCopy();
				activateButton(parentCopy == null || parentCopy.getHandle() != parentHandle);
				break;

			case FileExplorerActivityLollipop.UPLOAD:
				optionButton.setText(StringResourcesUtils.getString(R.string.context_upload));
				break;

			case FileExplorerActivityLollipop.IMPORT:
				optionButton.setText(StringResourcesUtils.getString(R.string.add_to_cloud));
				break;

			case FileExplorerActivityLollipop.SAVE:
				optionButton.setText(StringResourcesUtils.getString(R.string.save_action));
				break;

			case FileExplorerActivityLollipop.SELECT:
				optionsBar.setVisibility(View.GONE);
				activateButton(shouldShowOptionsBar(megaApi.getNodeByHandle(parentHandle)));
				//No break; needed: the text should be set with SELECT mode

			default:
				optionButton.setText(StringResourcesUtils.getString(R.string.general_select));
				break;
		}

		if (adapter == null){
			adapter = new MegaExplorerLollipopAdapter(context, this, nodes, parentHandle,
					recyclerView, selectFile, sortByHeaderViewModel);
		}
		else{
			adapter.setListFragment(recyclerView);
			adapter.setParentHandle(parentHandle);
			adapter.setSelectFile(selectFile);
		}

		if (gridLayoutManager != null) {
			gridLayoutManager.setSpanSizeLookup(adapter.getSpanSizeLookup(gridLayoutManager.getSpanCount()));
		}

		recyclerView.setAdapter(adapter);
		fastScroller.setRecyclerView(recyclerView);
        setNodes(nodes);

        if (((FileExplorerActivityLollipop) context).shouldRestartSearch()) {
        	setWaitingForSearchedNodes(true);
            search(((FileExplorerActivityLollipop) context).getQuerySearch());
        }
		return v;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		emptyRootText = HtmlCompat.fromHtml(formatEmptyScreenText(requireContext(),
				StringResourcesUtils.getString(R.string.context_empty_cloud_drive)),
				HtmlCompat.FROM_HTML_MODE_LEGACY);

		emptyGeneralText = HtmlCompat.fromHtml(formatEmptyScreenText(requireContext(),
				StringResourcesUtils.getString(R.string.file_browser_empty_folder_new)),
				HtmlCompat.FROM_HTML_MODE_LEGACY);

		updateEmptyScreen();
		super.onViewCreated(view, savedInstanceState);
	}

	private void getNodes() {
		MegaNode chosenNode = megaApi.getNodeByHandle(parentHandle);

		if (chosenNode != null && chosenNode.getType() != MegaNode.TYPE_ROOT) {
			nodes = megaApi.getChildren(chosenNode, order);
			logDebug("chosenNode is: " + chosenNode.getName());
			return;
		}

		MegaNode rootNode = megaApi.getRootNode();
		if (rootNode != null) {
			setParentHandle(rootNode.getHandle());
			nodes = megaApi.getChildren(rootNode, order);
		}
	}

	private void showEmptyScreen() {
		if (adapter == null) {
			return;
		}

		if (adapter.getItemCount() == 0) {
			recyclerView.setVisibility(View.GONE);
			emptyImageView.setVisibility(View.VISIBLE);
			emptyTextView.setVisibility(View.VISIBLE);
			updateEmptyScreen();
		} else {
			recyclerView.setVisibility(View.VISIBLE);
			emptyImageView.setVisibility(View.GONE);
			emptyTextView.setVisibility(View.GONE);
		}
	}

	private void updateEmptyScreen() {
		if (megaApi.getRootNode().getHandle() == parentHandle) {
			emptyImageView.setImageResource(isScreenInPortrait(context)
					? R.drawable.ic_empty_cloud_drive : R.drawable.cloud_empty_landscape);

			emptyTextViewFirst.setText(emptyRootText);
		} else {
			emptyImageView.setImageResource(isScreenInPortrait(context)
					? R.drawable.ic_zero_portrait_empty_folder : R.drawable.ic_zero_landscape_empty_folder);

			emptyTextViewFirst.setText(emptyGeneralText);
		}

		ColorUtils.setImageViewAlphaIfDark(context, emptyImageView, ColorUtils.DARK_IMAGE_ALPHA);
	}

	@Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        context = activity;
    }

	@Override
	public void onClick(View v) {
		logDebug("onClick");

		switch (v.getId()) {
			case R.id.fab_select:
			case R.id.action_text: {
				dbH.setLastCloudFolder(Long.toString(parentHandle));

				if (((FileExplorerActivityLollipop) context).isMultiselect()) {
					logDebug("Send several files to chat");
					if (adapter.getSelectedItemCount() > 0) {
						long handles[] = adapter.getSelectedHandles();
						((FileExplorerActivityLollipop) context).buttonClick(handles);
					} else {
						((FileExplorerActivityLollipop) context).showSnackbar(getString(R.string.no_files_selected_warning));
					}

				} else {
					((FileExplorerActivityLollipop) context).buttonClick(parentHandle);
				}
				break;

			}
			case R.id.cancel_text: {
				((FileExplorerActivityLollipop) context).finishActivity();
			}
			break;
		}
	}

	public void navigateToFolder(long handle) {
		logDebug("Handle: " + handle);

		int lastFirstVisiblePosition = 0;
		if (((FileExplorerActivityLollipop) context).isList()) {
			lastFirstVisiblePosition = mLayoutManager.findFirstCompletelyVisibleItemPosition();
		}
		else {
			lastFirstVisiblePosition = gridLayoutManager.findFirstCompletelyVisibleItemPosition();
		}

		logDebug("Push to stack " + lastFirstVisiblePosition + " position");
		lastPositionStack.push(lastFirstVisiblePosition);

		setParentHandle(handle);
		nodes.clear();
		setNodes(nodes);
		recyclerView.scrollToPosition(0);

		if((modeCloud == FileExplorerActivityLollipop.MOVE) || (modeCloud == FileExplorerActivityLollipop.COPY)){
			activateButton(true);
		}
	}

    public void itemClick(View view, int position) {
		logDebug("Position: " + position);

		ArrayList<MegaNode> clickNodes;

		if (searchNodes != null) {
			clickNodes = searchNodes;
			shouldResetNodes = false;
			((FileExplorerActivityLollipop) context).setQueryAfterSearch();
			((FileExplorerActivityLollipop) context).collapseSearchView();
		}
		else {
			clickNodes = nodes;
		}

		if (position < 0 || position >= clickNodes.size()) return;

		MegaNode n = clickNodes.get(position);

		if (n.isFolder()){
		    searchNodes = null;
			((FileExplorerActivityLollipop) context).setShouldRestartSearch(false);

			if(selectFile && ((FileExplorerActivityLollipop)context).isMultiselect() && adapter.isMultipleSelect()){
					hideMultipleSelect();
			}

			((FileExplorerActivityLollipop) context).hideTabs(true, CLOUD_FRAGMENT);

			int lastFirstVisiblePosition = 0;
			if (((FileExplorerActivityLollipop)context).isList()) {
				lastFirstVisiblePosition = mLayoutManager.findFirstCompletelyVisibleItemPosition();
			}
			else {
				lastFirstVisiblePosition = gridLayoutManager.findFirstCompletelyVisibleItemPosition();
			}

			logDebug("Push to stack " + lastFirstVisiblePosition + " position");
			lastPositionStack.push(lastFirstVisiblePosition);

			if (modeCloud == FileExplorerActivityLollipop.SELECT) {
				activateButton(!selectFile);
			}

			setParentHandle(n.getHandle());
			setNodes(megaApi.getChildren(n, order));

			recyclerView.scrollToPosition(0);

			if (adapter.getItemCount() == 0 && (modeCloud == FileExplorerActivityLollipop.MOVE || modeCloud == FileExplorerActivityLollipop.COPY)) {
				activateButton(true);
			} else if (modeCloud == FileExplorerActivityLollipop.MOVE || modeCloud == FileExplorerActivityLollipop.COPY) {
				MegaNode parent = ((FileExplorerActivityLollipop) context).parentMoveCopy();
				if (parent != null && parent.getHandle() == parentHandle) {
					activateButton(false);
				} else {
					activateButton(true);
				}
			}

		}
		else if(selectFile) {
			if(((FileExplorerActivityLollipop)context).isMultiselect()){
				if (adapter.getSelectedItemCount() == 0) {
					activateActionMode();
					adapter.toggleSelection(position);
					updateActionModeTitle();
				} else {
					adapter.toggleSelection(position);

					List<MegaNode> selectedNodes = adapter.getSelectedNodes();
					if (selectedNodes.size() > 0){
						updateActionModeTitle();
					}
				}
			}
			else{
				//Send file
				((FileExplorerActivityLollipop) context).buttonClick(n.getHandle());
			}
		}

		shouldResetNodes = true;
	}

	private boolean shouldShowOptionsBar(MegaNode parentNode) {
		if (selectFile) {
			return false;
		}

		MegaNode rootNode = megaApi.getRootNode();
		return rootNode != null && parentNode != null && parentNode.getHandle() != rootNode.getHandle();
	}

	public int onBackPressed(){
		logDebug("onBackPressed");
		if(selectFile) {
			if(((FileExplorerActivityLollipop)context).isMultiselect()){
				if(adapter.isMultipleSelect()){
					hideMultipleSelect();
				}
			}
		}

		MegaNode parentNode = megaApi.getParentNode(megaApi.getNodeByHandle(parentHandle));

		if (parentNode != null){
			if (modeCloud == FileExplorerActivityLollipop.SELECT) {
				activateButton(shouldShowOptionsBar(parentNode));
			}

            setParentHandle(parentNode.getHandle());

			if (parentNode.getType() == MegaNode.TYPE_ROOT) {
				((FileExplorerActivityLollipop) context).hideTabs(false, CLOUD_FRAGMENT);
			}

            ((FileExplorerActivityLollipop) context).changeTitle();

			if((modeCloud == FileExplorerActivityLollipop.MOVE) || (modeCloud == FileExplorerActivityLollipop.COPY)){
				MegaNode parent = ((FileExplorerActivityLollipop)context).parentMoveCopy();
				if(parent != null){
					if(parent.getHandle() == parentNode.getHandle()) {
						activateButton(false);
					}else{
						activateButton(true);
					}
				}else{
					activateButton(true);

				}
			}

			recyclerView.setVisibility(View.VISIBLE);
			emptyImageView.setVisibility(View.GONE);
			emptyTextView.setVisibility(View.GONE);

			setNodes(megaApi.getChildren(parentNode, order));
			int lastVisiblePosition = 0;
			if(!lastPositionStack.empty()){
				lastVisiblePosition = lastPositionStack.pop();
				logDebug("Pop of the stack " + lastVisiblePosition + " position");
			}
			logDebug("Scroll to " + lastVisiblePosition + " position");

			if(lastVisiblePosition>=0){
				if (((FileExplorerActivityLollipop) context).isList()) {
					mLayoutManager.scrollToPositionWithOffset(lastVisiblePosition, 0);
				}
				else {
					gridLayoutManager.scrollToPositionWithOffset(lastVisiblePosition, 0);
				}
			}

			return 2;
		}
		else{
			return 0;
		}
	}

	public long getParentHandle() {
		return parentHandle;
	}

	public void setParentHandle(long parentHandle){
		logDebug("Parent handle: " + parentHandle);
		this.parentHandle = parentHandle;
		if (adapter != null){
			adapter.setParentHandle(parentHandle);
		}
		((FileExplorerActivityLollipop)context).setParentHandleCloud(parentHandle);
		((FileExplorerActivityLollipop) context).changeTitle();
	}

	public void setNodes(ArrayList<MegaNode> nodes){
		this.nodes = nodes;
		if (adapter != null){
			adapter.setNodes(nodes);
			showEmptyScreen();
		}
	}

	private void selectAll(){
		logDebug("selectAll");

		if (adapter != null){
			adapter.selectAll();

			new Handler(Looper.getMainLooper()).post(() -> updateActionModeTitle());
		}
	}

	/*
	 * Clear all selected items
	 */
	private void clearSelections() {
		if(adapter.isMultipleSelect()){
			adapter.clearSelections();
		}
		if (modeCloud == FileExplorerActivityLollipop.SELECT) {
			activateButton(false);
		}
	}

	@Override
	protected void updateActionModeTitle() {
		if (actionMode == null || getActivity() == null) {
			return;
		}

		List<MegaNode> documents = adapter.getSelectedNodes();

		if (documents == null) return;

		int files = 0;
		int folders = 0;
		for (MegaNode document : documents) {
			if (document.isFile()) {
				files++;
			} else if (document.isFolder()) {
				folders++;
			}
		}


		Resources res = getActivity().getResources();

		String title;
		int sum=files+folders;

		if (files == 0 && folders == 0) {
			title = Integer.toString(sum);
		} else if (files == 0) {
			title = Integer.toString(folders);
		} else if (folders == 0) {
			title = Integer.toString(files);
		} else {
			title = Integer.toString(sum);
		}
		actionMode.setTitle(title);


		try {
			actionMode.invalidate();
		} catch (NullPointerException e) {
			logError("Invalidate error", e);
			e.printStackTrace();
		}
	}

	/*
	 * Disable selection
	 */
	public void hideMultipleSelect() {
		logDebug("hideMultipleSelect");
		adapter.setMultipleSelect(false);
		adapter.clearSelections();
		if (actionMode != null) {
			actionMode.finish();
		}

		if(isMultiselect()){
			activateButton(false);
		}

	}

	public RecyclerView getRecyclerView(){
		return recyclerView;
	}

	private void activateButton(boolean show) {
		if (modeCloud == FileExplorerActivityLollipop.SELECT) {
			int visibility = show ? View.VISIBLE : View.GONE;

			if (selectFile) {
				fabSelect.setVisibility(visibility);
			} else {
				optionsBar.setVisibility(visibility);
			}
		} else {
			optionButton.setEnabled(show);
		}
	}

	public void orderNodes (int order) {
		this.order = order;
		if (parentHandle == -1) {
			nodes = megaApi.getChildren(megaApi.getRootNode(), order);
		}
		else {
			nodes = megaApi.getChildren(megaApi.getNodeByHandle(parentHandle), order);
		}

		setNodes(nodes);
	}

	private boolean isMultiselect() {
		return modeCloud == FileExplorerActivityLollipop.SELECT && selectFile && ((FileExplorerActivityLollipop) context).isMultiselect();
	}

	public void search (String s) {
		if (megaApi == null || s == null || !shouldResetNodes) {
			return;
		}
		if (getParentHandle() == -1) {
			setParentHandle(megaApi.getRootNode().getHandle());
		}
		MegaNode parent = megaApi.getNodeByHandle(getParentHandle());

		if (parent == null) {
			logWarning("Parent null when search");
			return;
		}

		searchCancelToken = initNewSearch();
		searchNodesUseCase.get(s, INVALID_HANDLE, getParentHandle(), TYPE_CLOUD_EXPLORER, searchCancelToken)
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe((searchedNodes, throwable) -> {
					if (throwable == null) {
						finishSearch(searchedNodes);
					}
				});
	}

	@Override
	public MegaCancelToken initNewSearch() {
		updateSearchProgressView(true);
		cancelPreviousSearch();
		return MegaCancelToken.createInstance();
	}

	@Override
	public void updateSearchProgressView(boolean inProgress) {
		if (contentLayout == null || searchProgressBar == null || recyclerView == null) {
			logWarning("Cannot set search progress view, one or more parameters are NULL.");
			return;
		}

		contentLayout.setEnabled(!inProgress);
		contentLayout.setAlpha(inProgress ? 0.4f : 1f);
		searchProgressBar.setVisibility(inProgress ? View.VISIBLE: View.GONE);
		recyclerView.setVisibility(inProgress ? View.GONE : View.VISIBLE);
	}

	@Override
	public void cancelPreviousSearch() {
		if (searchCancelToken != null) {
			searchCancelToken.cancel();
		}
	}

	@Override
	public void finishSearch(@NonNull ArrayList<MegaNode> searchedNodes) {
		updateSearchProgressView(false);
		setSearchNodes(searchedNodes);
	}

	public void setSearchNodes(ArrayList<MegaNode> nodes) {
		if (adapter == null) return;
		searchNodes = nodes;
		((FileExplorerActivityLollipop) context).setShouldRestartSearch(true);
		adapter.setNodes(searchNodes);
		showEmptyScreen();

		if (isWaitingForSearchedNodes()) {
			reDoTheSelectionAfterRotation();
		}
	}

	public void closeSearch(boolean collapsedByClick) {
		updateSearchProgressView(false);
		cancelPreviousSearch();
		if (!collapsedByClick) {
            searchNodes = null;
        }
		if (shouldResetNodes) {
			getNodes();
			setNodes(nodes);
		}
	}

	public FastScroller getFastScroller() {
	    return fastScroller;
    }

	public boolean isFolderEmpty() {
		return adapter == null || adapter.getItemCount() <= 0;
	}
}
